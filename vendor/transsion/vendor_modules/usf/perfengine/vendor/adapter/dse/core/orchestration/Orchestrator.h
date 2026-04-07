#pragma once

/**
 * @file Orchestrator.h
 * @brief DSE 编排器定义
 *
 * 本文件定义了整机资源确定性调度抽象框架的核心编排组件：
 * - 状态同步事务 (StateSyncTransaction): 原子化状态更新机制
 * - 编排器 (Orchestrator): 双路径决策编排
 *
 * 编排器职责：
 * 1. 协调阶段执行顺序
 * 2. 管理状态快照和版本控制
 * 3. 实现快路径 (Fast Path) 和精修路径 (Converge Path) 的双路径决策
 * 4. 确保状态更新的原子性和一致性
 *
 * @see docs/整机资源确定性调度抽象框架.md §4 阶段编排
 */

#include <algorithm>
#include <utility>

#include "config/ConfigLoader.h"
#include "config/RuleTable.h"
#include "platform/state/StateTypes.h"
#include "profile/ProfileSpec.h"
#include "stage/StageRunner.h"
#include "state/StateVault.h"
#include "trace/TraceRecorder.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

namespace orchestrator_detail {
/** @brief 将纳秒安全压缩到 32 位整型，供 trace 元数据记录使用。 */
inline uint32_t NsToU32(uint64_t ns) {
    constexpr uint64_t kMax = 0xFFFFFFFFu;
    return static_cast<uint32_t>(std::min(ns, kMax));
}
/** @brief 计算单调时间差；若时间戳异常倒退则返回 0，避免 trace 溢出。 */
inline uint64_t ElapsedNsFrom(uint64_t beginNs, uint64_t endNs) {
    return (endNs >= beginNs) ? (endNs - beginNs) : 0;
}
}    // namespace orchestrator_detail

/**
 * @class StateSyncTransaction
 * @brief 状态同步事务：原子化状态更新机制
 *
 * StateSyncTransaction 实现了框架的原子化状态更新原则：
 * - 基于 Generation 的乐观锁机制
 * - 支持事务回滚
 * - 确保状态一致性
 *
 * 使用模式：
 * 1. 构造时验证 Generation 有效性
 * 2. 通过 Apply* 方法累积状态更新
 * 3. Commit() 提交或自动回滚
 *
 * @see 框架 §5.4 GenerationPinning
 */
class StateSyncTransaction {
public:
    /**
     * @brief 构造状态同步事务
     * @param vault 状态存储引用
     * @param expectedGen 期望的世代号
     *
     * 构造时验证世代号是否匹配，若不匹配则事务无效。
     */
    explicit StateSyncTransaction(StateVault &vault, Generation expectedGen)
        : vault_(vault), expectedGen_(expectedGen), committed_(false) {
        valid_ = vault_.BeginTransaction(expectedGen_);
    }

    /**
     * @brief 析构函数：自动回滚未提交的事务
     */
    ~StateSyncTransaction() {
        if (valid_ && !committed_) {
            vault_.RollbackTransaction();
        }
    }

    /**
     * @brief 检查事务是否有效
     * @return 事务有效性
     */
    bool IsValid() const { return valid_; }

    /**
     * @brief 应用场景语义更新
     * @param semantic 场景语义
     * @param sceneId 场景标识
     */
    void ApplyScene(const SceneSemantic &semantic, SceneId sceneId) {
        if (valid_) {
            vault_.UpdateScene(semantic, sceneId);
        }
    }

    /**
     * @brief 应用意图契约更新
     * @param contract 意图契约
     */
    void ApplyIntent(const IntentContract &contract, SessionId boundSession = 0) {
        if (valid_) {
            vault_.UpdateIntent(contract, boundSession);
        }
    }

    /**
     * @brief 应用租约更新
     * @param contract 时间契约
     * @param leaseId 租约标识
     */
    void ApplyLease(const TemporalContract &contract, LeaseId leaseId) {
        if (valid_) {
            vault_.UpdateLease(contract, leaseId);
        }
    }

    /**
     * @brief 应用依赖状态更新
     * @param dep 依赖状态
     */
    void ApplyDependency(const DependencyState &dep) {
        if (valid_) {
            vault_.UpdateDependency(dep);
        }
    }

    /**
     * @brief 应用稳定化状态更新
     * @param stability 稳定化状态
     */
    void ApplyStability(const StabilityState &stability) {
        if (valid_) {
            vault_.UpdateStability(stability);
        }
    }

    /**
     * @brief 提交事务
     * @return 提交是否成功
     *
     * 使用 CAS (Compare-And-Swap) 语义确保原子性：
     * - 验证世代号未被其他事务修改
     * - 成功则提交并递增世代号，失败则回滚
     *
     * @note 遵循框架 §5.4 GenerationPinning 原子状态快照机制
     */
    bool Commit() {
        if (!valid_) {
            return false;
        }
        auto result = vault_.CommitTransactionCas();
        if (!result.success) {
            valid_ = false;
            return false;
        }
        committed_ = true;
        return true;
    }

private:
    StateVault &vault_;         ///< 状态存储引用
    Generation expectedGen_;    ///< 期望的世代号
    bool valid_;                ///< 事务是否有效
    bool committed_;            ///< 是否已提交
};

/**
 * @class Orchestrator
 * @brief 编排器：双路径决策编排
 *
 * Orchestrator 是框架的核心编排组件，负责：
 * 1. 协调阶段执行顺序
 * 2. 管理状态快照和版本控制
 * 3. 实现快路径和精修路径的双路径决策
 *
 * 双路径决策模型：
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      SchedEvent                              │
 * └─────────────────────────────────────────────────────────────┘
 *                              │
 *                              ▼
 *              ┌───────────────────────────────┐
 *              │      IntentLevel 判定         │
 *              └───────────────────────────────┘
 *                              │
 *              ┌───────────────┴───────────────┐
 *              │                               │
 *              ▼                               ▼
 *     ┌─────────────────┐            ┌─────────────────┐
 *     │   Fast Path     │            │ Converge Path   │
 *     │   (P1 首响应)   │            │   (精修决策)    │
 *     └─────────────────┘            └─────────────────┘
 *              │                               │
 *              ▼                               ▼
 *     ┌─────────────────┐            ┌─────────────────┐
 *     │   FastGrant     │            │ PolicyDecision  │
 *     └─────────────────┘            └─────────────────┘
 *
 * 快路径 (Fast Path)：
 * - 目标：P1 首响应的快速保障
 * - 延迟：< 500μs
 * - 阶段：Scene → Intent → Resource → FastFinalize
 * - 输出：FastGrant（短租约）
 *
 * 精修路径 (Converge Path)：
 * - 目标：完整的资源决策
 * - 延迟：< 5ms
 * - 阶段：Scene → Intent → Inheritance → Resource → Envelope →
 *         Arbitration → Stability → ConvergeFinalize
 * - 输出：PolicyDecision（完整决策）
 *
 * @tparam ProfileSpec 配置规格类型，定义快路径和精修路径的阶段序列
 *
 * @see 框架 §4 阶段编排
 * @see 框架 §2.4 双路径决策
 */
template <typename ProfileSpec>
class Orchestrator {
public:
    /**
     * @brief 构造编排器
     * @param vault 状态存储引用
     */
    explicit Orchestrator(StateVault &vault) : vault_(vault), ruleTable_(RuleTable::Instance()) {
        static const ProfileSpec kSpec{};
        cachedProfileId_ = static_cast<uint32_t>(kSpec.kind) + 1;
        cachedResourceMask_ = kSpec.resourceMask;
    }

    /**
     * @brief 执行快路径决策
     * @param event 调度事件
     * @return 快速授权结果
     *
     * 快路径执行流程：
     * 1. 记录追踪点（FastPathStart）
     * 2. 获取状态快照（GenerationPinning）
     * 3. 初始化阶段上下文
     * 4. 执行快路径阶段序列
     * 5. 同步状态更新
     * 6. 记录追踪点（FastPathEnd）
     *
     * @note 快路径用于 P1 首响应，延迟要求 < 500μs
     */
    void RunFast(const SchedEvent &event, FastGrant &outGrant) {
        const uint64_t t0Ns = SystemTime::GetDeterministicNs();
        const SessionId sessionId = ResolveSchedSessionId(event);
        const SceneId sceneId = ResolveSchedSceneId(event);
        auto &traceRec = TraceRecorder::Instance();
        traceRec.RecordEvent(TracePoint::FastPathStart, static_cast<uint32_t>(event.eventId),
                             sessionId);

        FastStageIntermediates intermediates;
        StageContext ctx;
        ctx.event = &event;
        ctx.intermediates = &intermediates;
        ctx.fastIntermediates = &intermediates;
        ctx.vault = &vault_;

        StateView view;
        // GenerationPinning: 整条路径都只消费这一份快照，避免执行期间被并发写入扰动。
        auto token = vault_.PinAndSnapshot(event.timestamp, view);
        ctx.state = &view;
        ctx.generation = token.generation;
        ctx.snapshotToken = token.token;

        ctx.hasResolvedSessionId = true;
        ctx.resolvedSessionId = sessionId;
        ctx.hasResolvedSceneId = true;
        ctx.resolvedSceneId = sceneId;

        ctx.profileId = cachedProfileId_;
        ctx.profileResourceMask = cachedResourceMask_;
        ctx.ruleVersion = ruleTable_.GetRuleVersion();
        ctx.stageMask = 0;

        ctx.profileEffectiveIntent = IntentLevel::P3;

        auto &loader = ConfigLoader::Instance();
        ctx.configLoader = &loader;
        ctx.ruleTable = &ruleTable_;
        ctx.traceRecorder = &traceRec;
        if (traceRec.IsReplayHashTraceEnabled()) {
            ctx.precomputedInputHash = traceRec.ComputeInputHash(event);
            ctx.hasPrecomputedInputHash = true;
        }

        bool runSuccess = fastRunner_.Run(ctx);

        if (runSuccess) {
            // 只在阶段链完整成功时提交状态，避免半成品中间态污染 SSOT。
            DirtyBits dirtyBits = fastRunner_.GetAccumulatedDirtyBits();
            SyncStateUpdates(ctx, dirtyBits);
        }

        if (intermediates.hasIntentContract) {
            ctx.profileEffectiveIntent = intermediates.intentContract.level;
        }
        lastFastEffectiveIntent_ = ctx.profileEffectiveIntent;
        lastFastGeneration_ = ctx.generation;
        lastFastProfileId_ = ctx.profileId;
        lastFastRuleVersion_ = ctx.ruleVersion;
        lastFastSnapshotToken_ = token;

        auto &grant = ctx.MutableFastGrant();
        grant.sessionId = sessionId;

        const uint64_t t1Ns = SystemTime::GetDeterministicNs();
        lastFastPathLatencyNs_ = orchestrator_detail::ElapsedNsFrom(t0Ns, t1Ns);
        traceRec.RecordPathEnd(TracePoint::FastPathEnd, static_cast<uint32_t>(event.eventId),
                               orchestrator_detail::NsToU32(lastFastPathLatencyNs_),
                               grant.reasonBits, ctx.profileId, ctx.ruleVersion,
                               ctx.profileEffectiveIntent, sessionId);
        outGrant = std::move(grant);
    }

    FastGrant RunFast(const SchedEvent &event) {
        FastGrant grant;
        RunFast(event, grant);
        return grant;
    }

    /**
     * @brief 执行精修路径决策
     * @param event 调度事件
     * @return 完整策略决策
     *
     * 精修路径执行流程：
     * 1. 记录追踪点（ConvergePathStart）
     * 2. 获取状态快照（GenerationPinning）
     * 3. 初始化阶段上下文
     * 4. 执行精修路径阶段序列
     * 5. 同步状态更新
     * 6. 记录追踪点（ConvergePathEnd）
     *
     * @note 精修路径用于完整决策，延迟要求 < 5ms
     */
    void RunConverge(const SchedEvent &event, PolicyDecision &outDecision) {
        const uint64_t t0Ns = SystemTime::GetDeterministicNs();
        const SessionId sessionId = ResolveSchedSessionId(event);
        const SceneId sceneId = ResolveSchedSceneId(event);
        auto &traceRec = TraceRecorder::Instance();
        traceRec.RecordEvent(TracePoint::ConvergePathStart, static_cast<uint32_t>(event.eventId),
                             sessionId);

        ConvergeStageIntermediates intermediates;
        StageContext ctx;
        ctx.event = &event;
        ctx.intermediates = &intermediates;
        ctx.convergeIntermediates = &intermediates;
        ctx.vault = &vault_;

        StateView view;
        // 收敛路径与快路径一样基于固定快照运行，确保仲裁与稳定化结果可重放。
        auto token = vault_.PinAndSnapshot(event.timestamp, view);
        ctx.state = &view;
        ctx.generation = token.generation;
        ctx.snapshotToken = token.token;

        ctx.hasResolvedSessionId = true;
        ctx.resolvedSessionId = sessionId;
        ctx.hasResolvedSceneId = true;
        ctx.resolvedSceneId = sceneId;

        ctx.profileId = cachedProfileId_;
        ctx.profileResourceMask = cachedResourceMask_;
        ctx.ruleVersion = ruleTable_.GetRuleVersion();
        ctx.stageMask = 0;

        auto &loader = ConfigLoader::Instance();
        ctx.configLoader = &loader;
        ctx.ruleTable = &ruleTable_;
        ctx.traceRecorder = &traceRec;
        if (traceRec.IsReplayHashTraceEnabled()) {
            ctx.precomputedInputHash = traceRec.ComputeInputHash(event);
            ctx.hasPrecomputedInputHash = true;
        }

        bool runSuccess = convergeRunner_.Run(ctx);

        if (runSuccess) {
            // dirtyBits 让写回范围保持最小化，减少提交成本并降低状态竞争概率。
            DirtyBits dirtyBits = convergeRunner_.GetAccumulatedDirtyBits();
            SyncStateUpdates(ctx, dirtyBits);
        }

        const uint64_t t1Ns = SystemTime::GetDeterministicNs();
        lastConvergePathLatencyNs_ = orchestrator_detail::ElapsedNsFrom(t0Ns, t1Ns);
        auto &decision = ctx.MutableDecision();
        decision.meta.latencyNs = orchestrator_detail::NsToU32(lastConvergePathLatencyNs_);
        traceRec.RecordPathEnd(TracePoint::ConvergePathEnd, static_cast<uint32_t>(event.eventId),
                               orchestrator_detail::NsToU32(lastConvergePathLatencyNs_),
                               decision.reasonBits, decision.meta.profileId,
                               decision.meta.ruleVersion, decision.effectiveIntent, sessionId);
        outDecision = std::move(decision);
    }

    PolicyDecision RunConverge(const SchedEvent &event) {
        PolicyDecision decision;
        RunConverge(event, decision);
        return decision;
    }

    /**
     * @brief 获取最近快路径的世代号
     * @return 世代号
     */
    Generation GetLastFastGeneration() const { return lastFastGeneration_; }

    /**
     * @brief 获取最近快路径的配置档位 ID
     * @return 配置档位 ID
     */
    uint32_t GetLastFastProfileId() const { return lastFastProfileId_; }

    /**
     * @brief 获取最近快路径的规则版本号
     * @return 规则版本号
     */
    uint32_t GetLastFastRuleVersion() const { return lastFastRuleVersion_; }

    /**
     * @brief 获取最近快路径的有效意图等级
     * @return 意图等级
     */
    IntentLevel GetLastFastEffectiveIntent() const { return lastFastEffectiveIntent_; }

    /**
     * @brief 获取最近快路径的快照令牌
     * @return 快照令牌
     *
     * @note 遵循 GenerationPinning 原则，用于创建 DecisionMeta
     */
    const SnapshotToken &GetLastFastSnapshotToken() const { return lastFastSnapshotToken_; }

    /** 最近一次快路径端到端决策耗时（含 Pin/快照 + 阶段链），单位 ns；框架目标 500μs 量级 */
    uint64_t GetLastFastPathLatencyNs() const { return lastFastPathLatencyNs_; }
    /** 最近一次收敛路径端到端决策耗时，单位 ns；框架目标 5ms 量级 */
    uint64_t GetLastConvergePathLatencyNs() const { return lastConvergePathLatencyNs_; }

    /**
     * @brief 释放会话资源 - 租约终止处理
     * @param sessionId 会话标识符
     *
     * 租约终止处理遵循框架"契约衰减"原则：
     * 1. 清理会话相关的状态
     * 2. 触发资源回收
     *
     * @note 租约管理是时间维度的核心职责
     */
    void ReleaseSession(SessionId sessionId) { vault_.ReleaseSession(sessionId); }

private:
    StateVault &vault_;       ///< 状态存储引用
    RuleTable &ruleTable_;    ///< 规则表引用：避免热路径重复 RuleTable::Instance()
    uint32_t cachedProfileId_ = 0;       ///< ProfileSpec 派生的档位 ID（构造期缓存）
    uint32_t cachedResourceMask_ = 0;    ///< ProfileSpec 资源掩码（构造期缓存）

    StageRunner<typename ProfileSpec::FastStages> fastRunner_{};    ///< 快路径阶段运行器
    StageRunner<typename ProfileSpec::ConvergeStages> convergeRunner_{};    ///< 精修路径阶段运行器

    Generation lastFastGeneration_ = 0;                        ///< 最近快路径世代号
    uint32_t lastFastProfileId_ = 0;                           ///< 最近快路径配置档位 ID
    uint32_t lastFastRuleVersion_ = 0;                         ///< 最近快路径规则版本号
    IntentLevel lastFastEffectiveIntent_ = IntentLevel::P3;    ///< 最近快路径有效意图等级
    SnapshotToken lastFastSnapshotToken_;                      ///< 最近快路径快照令牌
    uint64_t lastFastPathLatencyNs_ = 0;        ///< 最近快路径端到端时延（ns）
    uint64_t lastConvergePathLatencyNs_ = 0;    ///< 最近收敛路径端到端时延（ns）

    /**
     * @brief 同步状态更新
     * @param ctx 阶段上下文
     * @param dirtyBits 脏标记
     *
     * 使用 StateSyncTransaction 原子化提交状态更新：
     * 1. 创建事务并验证世代号
     * 2. 根据 dirtyBits 决定更新哪些状态
     * 3. 提交事务（失败自动回滚）
     *
     * @note 遵循框架的原子化状态更新原则和 SSOT 原则
     */
    void SyncStateUpdates(StageContext &ctx, DirtyBits dirtyBits) {
        StateSyncTransaction txn(vault_, ctx.generation);
        if (!txn.IsValid()) {
            return;
        }

        if (!ctx.intermediates) {
            txn.Commit();
            return;
        }

        const SessionId boundSessionId =
            (ctx.event != nullptr &&
             ((dirtyBits.HasIntent() && ctx.intermediates->hasIntentContract) ||
              (dirtyBits.HasLease() && ctx.intermediates->hasTemporalContract)))
                ? ctx.GetResolvedSessionId()
                : 0;

        if (dirtyBits.HasStability() && ctx.HasStabilityState()) {
            txn.ApplyStability(ctx.GetStabilityState());
        }

        if (dirtyBits.HasScene() && ctx.intermediates->hasSceneSemantic && ctx.event) {
            txn.ApplyScene(ctx.GetSceneSemantic(), ctx.GetResolvedSceneId());
        }

        if (dirtyBits.HasIntent() && ctx.intermediates->hasIntentContract) {
            // intent 绑定到解析后的稳定 sessionId，而不是瞬时 eventId。
            txn.ApplyIntent(ctx.GetIntentContract(), boundSessionId);
        }

        if (dirtyBits.HasLease() && ctx.intermediates->hasTemporalContract && ctx.event) {
            // lease 与 session 共用同一绑定主键，保证 ReleaseSession / OnEventEnd 可正确回收。
            LeaseId leaseId = static_cast<LeaseId>(boundSessionId);
            txn.ApplyLease(ctx.GetTemporalContract(), leaseId);
        }

        if (dirtyBits.HasDependency() && ctx.intermediates->hasDependencyState) {
            txn.ApplyDependency(ctx.GetDependencyState());
        }

        txn.Commit();
    }
};

}    // namespace vendor::transsion::perfengine::dse
