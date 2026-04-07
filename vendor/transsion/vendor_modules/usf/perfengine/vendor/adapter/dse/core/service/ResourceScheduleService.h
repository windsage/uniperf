#pragma once

/**
 * @file ResourceScheduleService.h
 * @brief DSE 资源调度服务定义
 *
 * 本文件定义了整机资源确定性调度抽象框架的服务层组件：
 * - 约束快照提供者 (ConstraintSnapshotProvider): 约束数据源接口
 * - 能力提供者 (CapabilityProvider): 平台能力数据源接口
 * - 资源调度服务 (ResourceScheduleService): 核心调度服务
 *
 * 服务层职责：
 * 1. 接收外部事件并触发决策流程
 * 2. 管理约束和能力的实时更新
 * 3. 协调编排器和执行流程
 * 4. 处理安全降级和回退逻辑
 *
 * @see docs/整机资源确定性调度抽象框架.md §5 服务层
 */

#include <cstdint>
#include <mutex>
#include <unordered_map>

#include "config/ConfigLoader.h"
#include "exec/ExecutionFlow.h"
#include "orchestration/Orchestrator.h"
#include "platform/PlatformRegistry.h"
#include "profile/ProfileSpec.h"
#include "safety/SafetyController.h"
#include "stage/StageBase.h"
#include "state/StateVault.h"
#include "trace/TraceRecorder.h"
#include "types/ConstraintTypes.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @class ConstraintSnapshotProvider
 * @brief 约束快照提供者：约束数据源接口
 *
 * ConstraintSnapshotProvider 定义了约束数据的获取接口：
 * - 热约束：温度等级、降频状态
 * - 电约束：电量等级、省电模式
 * - 内存约束：内存压力、PSI 等级
 * - 显示约束：熄屏状态
 *
 * @note 实现类通常从系统属性或内核接口读取约束状态
 */
class ConstraintSnapshotProvider {
public:
    virtual ~ConstraintSnapshotProvider() = default;

    /**
     * @brief 读取当前约束快照
     * @return 约束快照结构
     */
    virtual ConstraintSnapshot Read() = 0;
};

/**
 * @class CapabilityProvider
 * @brief 能力提供者：平台能力数据源接口
 *
 * CapabilityProvider 定义了平台能力数据的获取接口：
 * - CPU 频率范围和算力上限
 * - 内存带宽和容量
 * - GPU 算力和频率
 * - 存储 I/O 能力
 *
 * @note 实现类通常从平台 HAL 或内核接口读取能力信息
 */
class CapabilityProvider {
public:
    virtual ~CapabilityProvider() = default;

    /**
     * @brief 读取当前能力快照
     * @return 能力快照结构
     */
    virtual CapabilitySnapshot Read() = 0;
};

namespace resource_schedule_detail {

constexpr uint32_t kRuleReasonMask = 0x000000FFu;
constexpr uint32_t kConstraintReasonMask = 0x0000FF00u;
constexpr uint32_t kCapabilityReasonMask = 0x00FF0000u;
constexpr uint32_t kContractReasonMask = 0xFF000000u;

inline uint8_t FirstReasonDim(uint32_t bits) noexcept {
    for (uint8_t dim = 0; dim < static_cast<uint8_t>(kResourceDimCount); ++dim) {
        if ((bits & (1u << dim)) != 0) {
            return dim;
        }
    }
    return 0;
}

inline FallbackContext BuildConvergeFallbackContext(const PolicyDecision &decision) {
    FallbackContext ctx;
    ctx.sessionId = decision.sessionId;
    ctx.profileId = decision.meta.profileId;
    ctx.generation = decision.meta.generation;
    ctx.ruleVersion = decision.meta.ruleVersion;
    ctx.resourceMask = decision.grant.resourceMask;
    ctx.timestamp = decision.meta.decisionTs;

    const uint32_t reasonBits = decision.reasonBits;
    if ((reasonBits & kContractReasonMask) != 0) {
        const uint8_t dim = FirstReasonDim((reasonBits & kContractReasonMask) >> 24);
        ctx.reason = DecisionFallbackReason::ResourceInsufficient;
        ctx.SetDetail(FallbackDomain::Resource, FallbackCategory::Violation, dim, 0, 0);
        return ctx;
    }

    if ((reasonBits & kCapabilityReasonMask) != 0) {
        const uint8_t dim = FirstReasonDim((reasonBits & kCapabilityReasonMask) >> 16);
        ctx.reason = DecisionFallbackReason::CapabilityMismatch;
        ctx.SetDetail(FallbackDomain::Capability, FallbackCategory::Mismatch, dim, 0, 0);
        return ctx;
    }

    if ((reasonBits & kConstraintReasonMask) != 0) {
        const uint8_t dim = FirstReasonDim((reasonBits & kConstraintReasonMask) >> 8);
        ctx.reason = DecisionFallbackReason::ConstraintViolation;
        ctx.SetDetail(FallbackDomain::Constraint, FallbackCategory::Violation, dim, 0, 0);
        return ctx;
    }

    if ((reasonBits & kRuleReasonMask) != 0) {
        const uint8_t dim = FirstReasonDim(reasonBits & kRuleReasonMask);
        ctx.reason = DecisionFallbackReason::ResourceInsufficient;
        ctx.SetDetail(FallbackDomain::Resource, FallbackCategory::Mismatch, dim, 0, 0);
        return ctx;
    }

    const uint8_t dim =
        FirstReasonDim(decision.request.resourceMask != 0 ? decision.request.resourceMask
                                                          : decision.grant.resourceMask);
    ctx.reason = DecisionFallbackReason::ResourceInsufficient;
    ctx.SetDetail(FallbackDomain::Resource, FallbackCategory::Insufficient, dim, 0, 0);
    return ctx;
}

}    // namespace resource_schedule_detail

/**
 * @class ResourceScheduleService
 * @brief 资源调度服务：核心调度服务
 *
 * ResourceScheduleService 是框架对外的主要服务接口，负责：
 * 1. 接收外部事件并触发决策流程
 * 2. 管理约束和能力的实时更新
 * 3. 协调编排器和执行流程
 * 4. 处理安全降级和回退逻辑
 *
 * 服务流程：
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    External Events                           │
 * │  (SchedEvent, ConstraintUpdate, CapabilityUpdate)           │
 * └─────────────────────────────────────────────────────────────┘
 *                              │
 *                              ▼
 *              ┌───────────────────────────────┐
 *              │     ResourceScheduleService   │
 *              │                               │
 *              │  ┌─────────────────────────┐  │
 *              │  │   SafetyController      │  │
 *              │  │   (安全检查)            │  │
 *              │  └─────────────────────────┘  │
 *              │              │                │
 *              │              ▼                │
 *              │  ┌─────────────────────────┐  │
 *              │  │   Orchestrator          │  │
 *              │  │   (决策编排)            │  │
 *              │  └─────────────────────────┘  │
 *              │              │                │
 *              │              ▼                │
 *              │  ┌─────────────────────────┐  │
 *              │  │   ExecutionFlow         │  │
 *              │  │   (执行下发)            │  │
 *              │  └─────────────────────────┘  │
 *              └───────────────────────────────┘
 *                              │
 *                              ▼
 *              ┌───────────────────────────────┐
 *              │      Platform Executors       │
 *              │  (PerfHint HAL, Kernel Ctrl) │
 *              └───────────────────────────────┘
 *
 * @tparam ProfileSpec 配置规格类型，定义决策路径和阶段序列
 *
 * @see 框架 §5 服务层
 */
template <typename ProfileSpec>
class ResourceScheduleService final {
public:
    /**
     * @brief 构造资源调度服务
     *
     * 初始化：
     * - 状态存储 (StateVault)
     * - 编排器 (Orchestrator)
     * - 安全控制器 (SafetyController)
     * - 平台上下文 (PlatformContext)
     */
    ResourceScheduleService() : orchestrator_(vault_) {
        // [核心加固 P0] 显式清零原子槽位表，防止读取到内存垃圾。
        for (size_t i = 0; i < kSessionSlots; ++i) {
            slotEventIds_[i].store(0, std::memory_order_relaxed);
            slotSessionIds_[i].store(0, std::memory_order_relaxed);
        }
        InitPlatformContext();
    }

    /**
     * @brief 处理调度事件
     * @param event 调度事件
     *
     * 事件处理流程：
     * 1. 安全检查：SafetyController.CanProcess()
     * 2. 路径选择：ShouldRunFast() 判断快路径或精修路径
     * 3. 决策执行：RunFast() 或 RunConverge()
     * 4. 结果下发：ExecuteDecision()
     *
     * @note 异常安全保证：noexcept，内部错误通过降级处理
     */
    void OnEvent(const SchedEvent &event) noexcept { OnEvent(event, GrayscaleSampleKey(event)); }

    /**
     * @brief 处理调度事件（复用上游已计算的灰度采样键）
     * @param event 调度事件
     * @param grayscaleSampleKey 上游桥接层已计算的灰度采样键
     */
    void OnEvent(const SchedEvent &event, uint32_t grayscaleSampleKey) noexcept {
        if (!safety_.CanProcessWithGrayscale(grayscaleSampleKey)) {
            RecordSafetyFallback(event);
            return;
        }

        // [核心加固 P0] 无损原子发布协议：使用 -1LL 作为 Busy 标记，防止读写竞态。
        const SessionId sid = ResolveSchedSessionId(event);
        const uint64_t hashIdx = static_cast<uint64_t>(event.eventId) % kSessionSlots;

        bool stored = false;
        for (size_t i = 0; i < kMaxProbes; ++i) {
            size_t target = (hashIdx + i) % kSessionSlots;
            int64_t expected = 0;
            // 1. 通过 CAS 抢占槽位，设置为 Busy 状态 (-1LL)
            if (slotEventIds_[target].compare_exchange_strong(expected, -1LL)) {
                // 2. 在 Busy 状态下写入正确的 SessionId
                slotSessionIds_[target].store(sid, std::memory_order_relaxed);
                // 3. 发布最终 EventId (使用 Release 语义确保 sid 写入可见)
                slotEventIds_[target].store(event.eventId, std::memory_order_release);
                stored = true;
                break;
            } else if (expected == event.eventId) {
                // 幂等更新路径：EventId 已匹配，直接发布新 SessionId
                slotSessionIds_[target].store(sid, std::memory_order_release);
                stored = true;
                break;
            }
        }

        // 若探测窗口全满（理论极低概率），则回退到安全降级，防止关联混乱。
        if (!stored) {
            RecordSafetyFallback(event);
            return;
        }

        if (ShouldRunFast(event)) {
            FastGrant grant;
            orchestrator_.RunFast(event, grant);
            ApplyFastGrant(grant, event, orchestrator_.GetLastFastSnapshotToken(),
                           orchestrator_.GetLastFastProfileId(),
                           orchestrator_.GetLastFastRuleVersion(),
                           orchestrator_.GetLastFastEffectiveIntent());
        } else {
            PolicyDecision decision;
            orchestrator_.RunConverge(event, decision);
            if (decision.grant.resourceMask == 0 || !decision.admitted) {
                RecordConvergeFallback(decision);
            } else {
                ExecuteDecision(decision);
            }
        }
    }

    /**
     * @brief 处理事件结束通知 - 租约终止与资源释放
     * @param eventId 事件标识符
     *
     * 租约终止处理遵循框架"契约衰减"原则：
     * 1. 标记租约结束，触发资源回收
     * 2. 记录租约持续时间用于分析
     * 3. 通知编排器释放相关状态
     *
     * @note 租约管理是时间维度的核心职责
     * @note 即使没有显式调用，租约也会自动衰减
     */
    void OnEventEnd(int64_t eventId) noexcept {
        SessionId sessionId = static_cast<SessionId>(eventId);
        const uint64_t hashIdx = static_cast<uint64_t>(eventId) % kSessionSlots;

        // [核心加固 P0] 顺序探测匹配。
        for (size_t i = 0; i < kMaxProbes; ++i) {
            size_t target = (hashIdx + i) % kSessionSlots;
            // 使用 Acquire 语义读取，确保后续读取 sid 时的内存一致性。
            if (slotEventIds_[target].load(std::memory_order_acquire) == eventId) {
                sessionId = slotSessionIds_[target].load(std::memory_order_relaxed);
                // 清理槽位。注意：必须先清 EventId 确保槽位可被后续抢占。
                slotEventIds_[target].store(0, std::memory_order_release);
                break;
            }
        }

        TraceRecorder::Instance().RecordEvent(TracePoint::LeaseExpire,
                                              static_cast<uint32_t>(eventId), sessionId);
        orchestrator_.ReleaseSession(sessionId);
    }

    /**
     * @brief 处理约束更新
     * @param snapshot 约束快照
     *
     * 约束更新处理：
     * 1. 提交 canonical ConstraintSnapshot
     * 2. 由 EnvelopeStage 在 pinned snapshot 上唯一生成 allowed
     * 3. 更新状态存储
     *
     * @note 约束维度始终高于意图等级
     * @note 推荐使用 OnConstraintAndCapabilityUpdate() 保证原子性
     */
    void OnConstraintUpdate(const ConstraintSnapshot &snapshot) noexcept {
        vault_.UpdateConstraint(snapshot);
    }

    /**
     * @brief 处理能力更新
     * @param snapshot 能力快照
     *
     * 能力更新处理：
     * 1. 提交 canonical CapabilitySnapshot
     * 2. ResourceStage 在 pinned snapshot 上唯一生成 CapabilityFeasible
     * 3. 更新状态存储
     *
     * @note 推荐使用 OnConstraintAndCapabilityUpdate() 保证原子性
     */
    void OnCapabilityUpdate(const CapabilitySnapshot &snapshot) noexcept {
        vault_.UpdateCapability(snapshot);
    }

    /**
     * @brief 同时处理约束和能力更新（原子操作）
     * @param constraintSnapshot 约束快照
     * @param capabilitySnapshot 能力快照
     *
     * 原子更新处理：
     * 1. 提交约束与能力的 canonical snapshot
     * 2. 在同一事务中更新约束和能力
     * 3. 只推进一次 generation，保证同轮同快照
     *
     * @note 这是推荐的方式，确保约束和能力在同一 generation 下一致
     */
    void OnConstraintAndCapabilityUpdate(const ConstraintSnapshot &constraintSnapshot,
                                         const CapabilitySnapshot &capabilitySnapshot) noexcept {
        vault_.UpdateConstraintAndCapability(constraintSnapshot, capabilitySnapshot);
    }

    /** @brief 全局发布点：清脏 + 递增 syncEpoch（批处理域更新完成后调一次） */
    void SyncStateUpdate() noexcept { vault_.SyncCommitBarrier(); }

    /**
     * @brief 获取当前状态世代号
     * @return 世代号
     */
    Generation GetStateGeneration() const { return vault_.GetGeneration(); }

    /**
     * @brief 获取安全控制器（可修改）
     * @return 安全控制器引用
     */
    SafetyController &GetSafetyController() { return safety_; }

    /**
     * @brief 获取安全控制器（只读）
     * @return 安全控制器常量引用
     */
    const SafetyController &GetSafetyController() const { return safety_; }

    /**
     * @brief 获取平台上下文
     * @return 平台上下文常量引用
     */
    const PlatformContext &GetPlatformContext() const { return platformContext_; }

    /**
     * @brief 获取最近执行结果
     * @return 执行结果常量引用
     */
    const ExecutionResult &GetLastExecutionResult() const { return lastExecutionResult_; }

    /**
     * @brief 启动状态服务
     * @param constraintProvider 约束快照提供者
     * @param capabilityProvider 能力快照提供者
     *
     * 启动流程：
     * 1. 注册约束和能力提供者
     * 2. 执行初始状态同步
     * 3. 启动定期状态更新线程
     *
     * @note 必须在事件处理前调用
     */
    void Start(ConstraintSnapshotProvider *constraintProvider,
               CapabilityProvider *capabilityProvider) {
        constraintProvider_ = constraintProvider;
        capabilityProvider_ = capabilityProvider;

        // In shared-vault mode, StateService 已经把 canonical Constraint/Capability 提交到同一个
        // StateVault。 ResourceScheduleService 只需要消费 pinned snapshot，因此这里避免“重复回灌”。
        // 非共享/空 vault 场景仍保留一次性 seed 行为，确保 baseline 可用。
        if (vault_.GetGeneration() != 0) {
            return;
        }

        if (constraintProvider_ && capabilityProvider_) {
            auto constraintSnapshot = constraintProvider_->Read();
            auto capabilitySnapshot = capabilityProvider_->Read();
            OnConstraintAndCapabilityUpdate(constraintSnapshot, capabilitySnapshot);
        } else if (constraintProvider_) {
            auto constraintSnapshot = constraintProvider_->Read();
            OnConstraintUpdate(constraintSnapshot);
        } else if (capabilityProvider_) {
            auto capabilitySnapshot = capabilityProvider_->Read();
            OnCapabilityUpdate(capabilitySnapshot);
        }
    }

    /**
     * @brief 停止状态服务
     *
     * 停止定期状态更新线程，清理资源。
     */
    void Stop() {
        constraintProvider_ = nullptr;
        capabilityProvider_ = nullptr;
    }

    /**
     * @brief 手动触发状态同步
     *
     * 从提供者读取最新状态并更新。
     * 用于外部状态变化通知。
     */
    void SyncState() {
        if (constraintProvider_ && capabilityProvider_) {
            auto constraintSnapshot = constraintProvider_->Read();
            auto capabilitySnapshot = capabilityProvider_->Read();
            OnConstraintAndCapabilityUpdate(constraintSnapshot, capabilitySnapshot);
        } else if (constraintProvider_) {
            auto constraintSnapshot = constraintProvider_->Read();
            OnConstraintUpdate(constraintSnapshot);
        } else if (capabilityProvider_) {
            auto capabilitySnapshot = capabilityProvider_->Read();
            OnCapabilityUpdate(capabilitySnapshot);
        }
    }

    /**
     * @brief 获取状态存储（只读）
     * @return 状态存储常量引用
     */
    const StateVault &GetStateVault() const { return vault_; }

    /**
     * @brief 获取状态存储（可修改）
     * @return 状态存储引用
     */
    StateVault &GetStateVault() { return vault_; }

private:
    /**
     * @brief 初始化平台上下文
     *
     * 通过 PlatformRegistry 检测平台厂商并创建对应上下文。
     */
    void InitPlatformContext() {
        PlatformVendor vendor = PlatformRegistry::Instance().DetectVendor();
        platformContext_ = PlatformRegistry::Instance().CreateContext(vendor);
    }

    struct FastPathGateSemantic {
        bool hasAction = false;
        bool actionMaskMatched = false;
        bool p1ByActionRange = false;
        bool p1ByRawHint = false;
    };

    /**
     * @brief 判断 action 是否命中快路径 mask。
     * @param action 事件 action。
     * @param fastActionMask profile 定义的快路径 action 掩码。
     * @return 是否命中。
     *
     * @note 维持位掩码门控的历史语义，同时把判断集中到单点，便于审计。
     */
    bool IsActionInFastPathMask(uint32_t action, uint32_t fastActionMask) const {
        if (action == 0 || action > 32) {
            return false;
        }
        const uint32_t actionBit = (1u << (action - 1));
        return (fastActionMask & actionBit) != 0;
    }

    /**
     * 将事件事实映射为快路径门控所需的统一语义。
     * 注意：这里严格保持历史判定条件，不改变行为，仅消除语义分散。
     */
    FastPathGateSemantic BuildFastPathGateSemantic(const SchedEvent &event,
                                                   uint32_t fastActionMask) const {
        FastPathGateSemantic semantic;
        semantic.hasAction = (event.action != 0);
        semantic.actionMaskMatched =
            semantic.hasAction && IsActionInFastPathMask(event.action, fastActionMask);
        semantic.p1ByActionRange =
            (event.action == static_cast<uint32_t>(EventSemanticAction::Launch) ||
             event.action == static_cast<uint32_t>(EventSemanticAction::Transition) ||
             event.action == static_cast<uint32_t>(EventSemanticAction::Scroll) ||
             event.action == static_cast<uint32_t>(EventSemanticAction::Animation) ||
             event.action == static_cast<uint32_t>(EventSemanticAction::Camera));
        semantic.p1ByRawHint = ((event.rawHint & 0x0F) == 0x01);
        return semantic;
    }

    /** @brief 根据快路径门控语义判断事件是否属于 P1 首响应类型。 */
    bool IsP1IntentEvent(const FastPathGateSemantic &semantic) const {
        return semantic.p1ByActionRange || semantic.p1ByRawHint;
    }

    /**
     * @brief 判断是否应执行快路径
     * @param event 调度事件
     * @return 是否执行快路径
     *
     * 双门控统一语义：
     * 1) Action 门：action 有效且匹配 fastActionMask，且不在 safety fallback
     * 2) P1 门：满足 P1 语义（action 区间或 rawHint 首响应标志）
     */
    bool ShouldRunFast(const SchedEvent &event) const {
        static const ProfileSpec spec{};
        const auto semantic = BuildFastPathGateSemantic(event, spec.fastActionMask);

        if (!semantic.hasAction) {
            return false;
        }
        if (!semantic.actionMaskMatched) {
            return false;
        }
        if (safety_.IsFallback()) {
            return false;
        }
        if (!IsP1IntentEvent(semantic)) {
            return false;
        }
        return true;
    }

    /**
     * @brief 应用快速授权
     * @param grant 快速授权结果
     * @param event 调度事件
     * @param token 快照令牌
     * @param profileId 配置档位 ID
     * @param ruleVersion 规则版本号
     * @param effectiveIntent 有效意图等级
     *
     * 快路径直接执行 FastGrant，避免为 P1 首拍额外构造完整 PolicyDecision。
     */
    void ApplyFastGrant(const FastGrant &grant, const SchedEvent &event, const SnapshotToken &token,
                        uint32_t profileId, uint32_t ruleVersion, IntentLevel effectiveIntent) {
        const SessionId sessionId = (grant.sessionId != 0) ? grant.sessionId
                                                           : ResolveSchedSessionId(event);
        if (grant.grantedMask == 0) {
            RecordFallbackExecution(sessionId, token.generation, profileId, ruleVersion, 0x01);
            return;
        }

        const TimeMode timeMode = (effectiveIntent == IntentLevel::P1)   ? TimeMode::Burst
                                  : (effectiveIntent == IntentLevel::P2) ? TimeMode::Steady
                                  : (effectiveIntent == IntentLevel::P4) ? TimeMode::Deferred
                                                                         : TimeMode::Intermittent;
        auto meta = DecisionMeta::CreateFromSnapshotToken(token, profileId, ruleVersion, 0);
        {
            const uint64_t ns = orchestrator_.GetLastFastPathLatencyNs();
            constexpr uint64_t kMaxU32 = 0xFFFFFFFFu;
            meta.latencyNs = static_cast<uint32_t>(ns > kMaxU32 ? kMaxU32 : ns);
        }
        auto result =
            executionFlow_.ExecuteFastGrant(grant, sessionId, meta, effectiveIntent, timeMode,
                                            platformContext_.actionMap, platformContext_.executor);
        lastExecutionResult_ = std::move(result.execResult);
    }

    /**
     * @brief 执行决策
     * @param decision 策略决策
     *
     * 通过 ExecutionFlow 将决策下发到平台执行器。
     * ActionMap / Executor 任一缺失时，ExecutionFlow 会显式返回 fallback。
     */
    void ExecuteDecision(const PolicyDecision &decision) {
        auto &loader = ConfigLoader::Instance();
        const int64_t updateThrottleMs = loader.GetParams().updateThrottleMs;
        auto result = executionFlow_.Execute(decision, platformContext_.actionMap,
                                             platformContext_.executor, updateThrottleMs);

        lastExecutionResult_ = std::move(result.execResult);
    }

    /** @brief 统一初始化服务层的 NoopFallback 执行结果。 */
    void InitServiceFallbackResult(SessionId sessionId, uint32_t fallbackFlags,
                                   uint32_t executionFlags, const DecisionMeta *meta = nullptr) {
        lastExecutionResult_ = ExecutionResult{};
        lastExecutionResult_.sessionId = sessionId;
        if (meta != nullptr) {
            lastExecutionResult_.meta = *meta;
        }
        lastExecutionResult_.actualPath = PlatformActionPath::NoopFallback;
        lastExecutionResult_.fallbackFlags = fallbackFlags;
        lastExecutionResult_.executionFlags = executionFlags;
    }

    /** @brief 统一记录服务层 fallback trace，保持 session/meta 字段写法一致。 */
    void RecordServiceFallbackTrace(const FallbackContext &ctx) {
        TraceRecorder::Instance().RecordFallback(ctx);
    }

    /**
     * @brief 记录安全降级
     * @param event 调度事件
     *
     * 当安全控制器阻止处理时，记录降级信息。
     * 这类 fallback 与资源不足不同，属于策略外部的安全守门。
     */
    void RecordSafetyFallback(const SchedEvent &event) {
        const SessionId sessionId = ResolveSchedSessionId(event);
        InitServiceFallbackResult(sessionId, 0x01, 0x02);

        FallbackContext ctx;
        ctx.sessionId = sessionId;
        ctx.reason = DecisionFallbackReason::SafetyBlocked;
        ctx.detailBits = 0x02;

        RecordServiceFallbackTrace(ctx);
    }

    /**
     * @brief 记录执行降级
     * @param sessionId 会话标识
     * @param generation 世代号
     * @param profileId 配置档位 ID
     * @param ruleVersion 规则版本号
     * @param fallbackReason 降级原因
     *
     * 当资源不足导致无法执行时，记录降级信息。
     * 该记录同时携带 generation/profile/ruleVersion，便于复盘。
     */
    void RecordFallbackExecution(SessionId sessionId, Generation generation, uint32_t profileId,
                                 uint32_t ruleVersion, uint32_t fallbackReason) {
        const DecisionMeta meta =
            DecisionMeta::CreateFromContext(generation, profileId, ruleVersion, 0, 0);
        InitServiceFallbackResult(sessionId, fallbackReason, 0x01, &meta);

        FallbackContext ctx;
        ctx.sessionId = sessionId;
        ctx.profileId = profileId;
        ctx.generation = generation;
        ctx.ruleVersion = ruleVersion;
        ctx.reason = DecisionFallbackReason::ResourceInsufficient;
        ctx.detailBits = fallbackReason;

        RecordServiceFallbackTrace(ctx);
    }

    /**
     * @brief 记录精修路径 fallback。
     * @param decision 精修路径策略决策。
     *
     * degraded=true 时，原因更偏向约束裁剪；
     * 否则视为资源不足或能力不可交付。
     */
    void RecordConvergeFallback(const PolicyDecision &decision) {
        InitServiceFallbackResult(decision.sessionId, decision.reasonBits, 0x01, &decision.meta);

        FallbackContext ctx = resource_schedule_detail::BuildConvergeFallbackContext(decision);
        RecordServiceFallbackTrace(ctx);
    }

    StateVault vault_;                          ///< 状态存储
    Orchestrator<ProfileSpec> orchestrator_;    ///< 编排器
    ExecutionFlow<ProfileSpec> executionFlow_;    ///< 执行流（复用执行层去重/节流状态）
    SafetyController safety_;                     ///< 安全控制器
    PlatformContext platformContext_;             ///< 平台上下文
    ConstraintSnapshotProvider *constraintProvider_ = nullptr;    ///< 约束提供者
    CapabilityProvider *capabilityProvider_ = nullptr;            ///< 能力提供者
    ExecutionResult lastExecutionResult_;                         ///< 最近执行结果

    // [优化 P0/P1] 无损探测槽位表：支持 64 位 ID 且容忍取模碰撞。
    static constexpr size_t kSessionSlots = 4096;    ///< 扩大槽位容量，降低碰撞概率
    static constexpr size_t kMaxProbes = 8;          ///< 最大探测深度
    std::atomic<int64_t> slotEventIds_[kSessionSlots];
    std::atomic<SessionId> slotSessionIds_[kSessionSlots];
};

/// 入门级设备服务实例
using EntryService = ResourceScheduleService<EntryProfileSpec>;

/// 主流设备服务实例
using MainService = ResourceScheduleService<MainProfileSpec>;

/// 旗舰设备服务实例
using FlagshipService = ResourceScheduleService<FlagshipProfileSpec>;

}    // namespace vendor::transsion::perfengine::dse
