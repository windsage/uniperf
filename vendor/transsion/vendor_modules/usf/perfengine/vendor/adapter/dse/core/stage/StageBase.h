#pragma once

/**
 * @file StageBase.h
 * @brief DSE 阶段基础设施定义
 *
 * 本文件定义了整机资源确定性调度抽象框架的阶段基础设施：
 * - 领域能力 (DomainCapability): 单一资源维度的平台能力描述
 * - 能力快照 (CapabilitySnapshot): 平台能力的完整快照
 * - 阶段中间产物 (StageIntermediates): 阶段间传递的唯一数据源 (SSOT)
 * - 阶段上下文 (StageContext): 阶段执行的完整上下文
 * - 阶段输出 (StageOutput): 阶段执行的标准化输出
 * - 阶段接口 (IStage): 阶段的统一抽象接口
 *
 * @see docs/整机资源确定性调度抽象框架.md §4 阶段编排
 */

#include <cstdint>

#include "config/ConfigLoader.h"
#include "config/RuleTable.h"
#include "core/state/StateDomains.h"
#include "core/types/ConstraintTypes.h"
#include "core/types/CoreTypes.h"
#include "trace/TraceRecorder.h"

namespace vendor::transsion::perfengine::dse {

class StateVault;
class StateView;
struct SceneRule;

/**
 * @note 平台能力快照的规范类型为 ConstraintTypes.h 中的 CapabilitySnapshot（与状态服务、
 * CapabilityProvider 一致）。阶段链不重复定义同名结构，避免 ODR/重定义错误。
 * @see 框架 §3.5 能力边界
 */

/**
 * @struct StageIntermediates
 * @brief 阶段中间产物：阶段间传递的唯一数据源 (SSOT)
 *
 * StageIntermediates 是框架实现 Single Source of Truth (SSOT) 原则的核心结构：
 * - 所有阶段只读写 intermediates，不直接修改全局状态
 * - 每个字段配有 has* 标志位，标识该字段是否已被填充
 * - 避免状态冗余，确保数据一致性
 *
 * 数据流向（按阶段顺序）：
 * 1. SceneStage → sceneSemantic
 * 2. IntentStage → intentContract
 * 3. InheritanceStage → temporalContract
 * 4. ResourceStage → resourceRequest
 * 5. EnvelopeStage → constraintAllowed, capabilityFeasible
 * 6. ArbitrationStage → arbitrationResult
 * 7. FastFinalizeStage → fastGrant (快路径)
 * 8. ConvergeFinalizeStage → decision (精修路径)
 *
 * @note 遵循 SSOT 原则，禁止在 StageContext 中冗余存储相同数据
 */
struct StageIntermediates {
    SceneSemantic sceneSemantic;              ///< 场景语义（SceneStage 输出）
    const SceneRule *sceneRule = nullptr;     ///< 场景规则缓存（canonical config view）
    IntentContract intentContract;            ///< 意图契约（IntentStage 输出）
    TemporalContract temporalContract;        ///< 时间契约（InheritanceStage 输出）
    ResourceRequest resourceRequest;          ///< 资源请求（ResourceStage 输出）
    ConstraintAllowed constraintAllowed;      ///< 约束边界（EnvelopeStage 输出）
    CapabilityFeasible capabilityFeasible;    ///< 能力上限（EnvelopeStage 输出）
    DependencyState dependencyState;          ///< 依赖状态（InheritanceStage 输出）

    bool hasSceneSemantic = false;       ///< sceneSemantic 是否有效
    bool hasSceneRule = false;           ///< sceneRule 查询是否已完成（允许为 nullptr）
    bool hasIntentContract = false;      ///< intentContract 是否有效
    bool hasTemporalContract = false;    ///< temporalContract 是否有效
    bool hasResourceRequest = false;     ///< resourceRequest 是否有效
    bool hasConstraintAllowed = false;     ///< constraintAllowed 是否有效
    bool hasCapabilityFeasible = false;    ///< capabilityFeasible 是否有效
    bool hasDependencyState = false;       ///< dependencyState 是否有效
};

struct FastStageIntermediates final : StageIntermediates {
    FastGrant fastGrant;          ///< 快速授权（FastFinalizeStage 输出）
    bool hasFastGrant = false;    ///< fastGrant 是否有效
};

struct ConvergeStageIntermediates final : StageIntermediates {
    ArbitrationResult arbitrationResult;    ///< 仲裁结果（ArbitrationStage 输出）
    StabilityState stabilityState;          ///< 稳定化状态（StabilityStage 输出）
    PolicyDecision decision;                ///< 策略决策（ConvergeFinalizeStage 输出）
    bool hasArbitrationResult = false;      ///< arbitrationResult 是否有效
    bool hasStabilityState = false;         ///< stabilityState 是否有效
    bool hasDecision = false;               ///< decision 是否有效
};

/**
 * @struct StageContext
 * @brief 阶段上下文：阶段执行的完整上下文
 *
 * StageContext 封装了阶段执行所需的全部上下文信息：
 * - 输入数据：event（调度事件）
 * - 状态视图：state（只读状态快照）
 * - 中间产物：intermediates（阶段间传递的数据）
 * - 元数据：generation, profileId, profileResourceMask, ruleVersion
 *
 * 设计原则：
 * 1. 只读状态：state 是只读的，阶段不应修改
 * 2. SSOT：intermediates 是阶段间传递的唯一数据源
 * 3. 版本控制：generation 用于状态一致性校验
 *
 * @note 遵循框架 §5.4 GenerationPinning 原则
 */
struct StageContext {
    const SchedEvent *event = nullptr;    ///< 输入事件（只读）
    const StateView *state = nullptr;     ///< 状态视图（只读快照）
    Generation generation = 0;            ///< 状态快照版本号
    uint64_t snapshotToken = 0;           ///< 快照令牌（绑定 GenerationPinning）

    // Resolved IDs cache:
    // Orchestrator 在进链前把可能需要合成/回退的 session/scene 解析一次，阶段热路径复用，
    // 避免在无显式 sceneId/sessionId 时重复 HashCombine。
    SessionId resolvedSessionId = 0;
    SceneId resolvedSceneId = 0;
    bool hasResolvedSessionId = false;
    bool hasResolvedSceneId = false;

    StageIntermediates *intermediates = nullptr;            ///< 阶段中间产物（SSOT）
    FastStageIntermediates *fastIntermediates = nullptr;    ///< 快路径扩展产物
    ConvergeStageIntermediates *convergeIntermediates = nullptr;    ///< 收敛路径扩展产物
    StateVault *vault = nullptr;    ///< 状态存储（用于写入）

    uint32_t profileId = 0;                                             ///< 配置档位 ID
    uint32_t profileResourceMask = ((1u << kResourceDimCount) - 1u);    ///< 档位允许的资源维度掩码
    uint32_t ruleVersion = 0;                                           ///< 规则版本号
    uint32_t stageMask = 0;                                             ///< 已执行阶段掩码
    IntentLevel profileEffectiveIntent = IntentLevel::P3;    ///< 配置档位有效意图等级

    /// 编排器在进链前注入，避免阶段热路径重复单例查询；nullptr 时回退 Instance()。
    ConfigLoader *configLoader = nullptr;
    RuleTable *ruleTable = nullptr;
    TraceRecorder *traceRecorder = nullptr;
    uint64_t precomputedInputHash = 0;
    bool hasPrecomputedInputHash = false;

    SessionId GetResolvedSessionId() const noexcept {
        return hasResolvedSessionId ? resolvedSessionId : ResolveSchedSessionId(event);
    }
    SceneId GetResolvedSceneId() const noexcept {
        return hasResolvedSceneId ? resolvedSceneId : ResolveSchedSceneId(event);
    }

private:
    /**
     * @brief 模板化设置中间产物
     * @tparam T 字段类型
     * @tparam HasFlag 标志位类型
     * @param field 字段指针
     * @param hasFlag 标志位指针
     * @param value 设置值
     *
     * 统一的设置逻辑，减少代码重复。
     */
    template <typename T, typename HasFlag>
    void SetIntermediate(T StageIntermediates::*field, HasFlag StageIntermediates::*hasFlag,
                         const T &value) {
        if (intermediates) {
            intermediates->*field = value;
            intermediates->*hasFlag = true;
        }
    }

    /**
     * @brief 模板化获取中间产物
     * @tparam T 字段类型
     * @tparam HasFlag 标志位类型
     * @param field 字段指针
     * @param hasFlag 标志位指针
     * @return 字段值或空值
     *
     * 统一的获取逻辑，减少代码重复。
     */
    template <typename T, typename HasFlag>
    const T &GetIntermediate(T StageIntermediates::*field,
                             HasFlag StageIntermediates::*hasFlag) const {
        static T empty{};
        return intermediates && (intermediates->*hasFlag) ? intermediates->*field : empty;
    }

    template <typename T, typename HasFlag>
    T &MutableIntermediate(T StageIntermediates::*field, HasFlag StageIntermediates::*hasFlag) {
        static T empty{};
        if (!intermediates) {
            return empty;
        }
        intermediates->*hasFlag = true;
        return intermediates->*field;
    }

    template <typename T, typename HasFlag>
    void SetFastIntermediate(T FastStageIntermediates::*field,
                             HasFlag FastStageIntermediates::*hasFlag, const T &value) {
        if (fastIntermediates) {
            fastIntermediates->*field = value;
            fastIntermediates->*hasFlag = true;
        }
    }

    template <typename T, typename HasFlag>
    const T &GetFastIntermediate(T FastStageIntermediates::*field,
                                 HasFlag FastStageIntermediates::*hasFlag) const {
        static T empty{};
        return fastIntermediates && (fastIntermediates->*hasFlag) ? fastIntermediates->*field
                                                                  : empty;
    }

    template <typename T, typename HasFlag>
    void SetConvergeIntermediate(T ConvergeStageIntermediates::*field,
                                 HasFlag ConvergeStageIntermediates::*hasFlag, const T &value) {
        if (convergeIntermediates) {
            convergeIntermediates->*field = value;
            convergeIntermediates->*hasFlag = true;
        }
    }

    template <typename T, typename HasFlag>
    const T &GetConvergeIntermediate(T ConvergeStageIntermediates::*field,
                                     HasFlag ConvergeStageIntermediates::*hasFlag) const {
        static T empty{};
        return convergeIntermediates && (convergeIntermediates->*hasFlag)
                   ? convergeIntermediates->*field
                   : empty;
    }

public:
    /**
     * @brief 设置场景语义
     * @param semantic 场景语义结构
     */
    void SetSceneSemantic(const SceneSemantic &semantic) {
        SetIntermediate(&StageIntermediates::sceneSemantic, &StageIntermediates::hasSceneSemantic,
                        semantic);
    }

    /**
     * @brief 缓存场景规则查询结果
     * @param rule canonical SceneRule 指针；允许为 nullptr，表示已查询但无匹配规则
     */
    void SetSceneRule(const SceneRule *rule) {
        if (intermediates) {
            intermediates->sceneRule = rule;
            intermediates->hasSceneRule = true;
        }
    }

    /**
     * @brief 设置意图契约
     * @param contract 意图契约结构
     */
    void SetIntentContract(const IntentContract &contract) {
        SetIntermediate(&StageIntermediates::intentContract, &StageIntermediates::hasIntentContract,
                        contract);
    }

    IntentContract &MutableIntentContract() {
        return MutableIntermediate(&StageIntermediates::intentContract,
                                   &StageIntermediates::hasIntentContract);
    }

    /**
     * @brief 设置时间契约
     * @param contract 时间契约结构
     */
    void SetTemporalContract(const TemporalContract &contract) {
        SetIntermediate(&StageIntermediates::temporalContract,
                        &StageIntermediates::hasTemporalContract, contract);
    }

    TemporalContract &MutableTemporalContract() {
        return MutableIntermediate(&StageIntermediates::temporalContract,
                                   &StageIntermediates::hasTemporalContract);
    }

    /**
     * @brief 设置资源请求
     * @param request 资源请求结构
     */
    void SetResourceRequest(const ResourceRequest &request) {
        SetIntermediate(&StageIntermediates::resourceRequest,
                        &StageIntermediates::hasResourceRequest, request);
    }

    ResourceRequest &MutableResourceRequest() {
        return MutableIntermediate(&StageIntermediates::resourceRequest,
                                   &StageIntermediates::hasResourceRequest);
    }

    /**
     * @brief 设置约束边界
     * @param allowed 约束边界结构
     */
    void SetConstraintAllowed(const ConstraintAllowed &allowed) {
        SetIntermediate(&StageIntermediates::constraintAllowed,
                        &StageIntermediates::hasConstraintAllowed, allowed);
    }

    /**
     * @brief 设置能力上限
     * @param feasible 能力上限结构
     */
    void SetCapabilityFeasible(const CapabilityFeasible &feasible) {
        SetIntermediate(&StageIntermediates::capabilityFeasible,
                        &StageIntermediates::hasCapabilityFeasible, feasible);
    }

    /**
     * @brief 设置仲裁结果
     * @param result 仲裁结果结构
     */
    void SetArbitrationResult(const ArbitrationResult &result) {
        SetConvergeIntermediate(&ConvergeStageIntermediates::arbitrationResult,
                                &ConvergeStageIntermediates::hasArbitrationResult, result);
    }

    /**
     * @brief 设置快速授权
     * @param grant 快速授权结构
     */
    void SetFastGrant(const FastGrant &grant) {
        SetFastIntermediate(&FastStageIntermediates::fastGrant,
                            &FastStageIntermediates::hasFastGrant, grant);
    }

    /**
     * @brief 设置策略决策
     * @param decision 策略决策结构
     */
    void SetDecision(const PolicyDecision &decision) {
        SetConvergeIntermediate(&ConvergeStageIntermediates::decision,
                                &ConvergeStageIntermediates::hasDecision, decision);
    }

    /**
     * @brief 设置依赖状态
     * @param dep 依赖状态结构
     */
    void SetDependencyState(const DependencyState &dep) {
        SetIntermediate(&StageIntermediates::dependencyState,
                        &StageIntermediates::hasDependencyState, dep);
    }

    DependencyState &MutableDependencyState() {
        return MutableIntermediate(&StageIntermediates::dependencyState,
                                   &StageIntermediates::hasDependencyState);
    }

    /**
     * @brief 设置稳定化状态
     * @param stability 稳定化状态结构
     */
    void SetStabilityState(const StabilityState &stability) {
        SetConvergeIntermediate(&ConvergeStageIntermediates::stabilityState,
                                &ConvergeStageIntermediates::hasStabilityState, stability);
    }

    /**
     * @brief 获取场景语义
     * @return 场景语义引用，未设置时返回空结构
     */
    const SceneSemantic &GetSceneSemantic() const {
        return GetIntermediate(&StageIntermediates::sceneSemantic,
                               &StageIntermediates::hasSceneSemantic);
    }

    /**
     * @brief 获取缓存的场景规则
     * @return 已查询时返回规则指针，可为空；未查询时返回 nullptr
     */
    const SceneRule *GetSceneRule() const {
        return (intermediates && intermediates->hasSceneRule) ? intermediates->sceneRule : nullptr;
    }

    /** @brief 当前上下文是否已经完成 SceneRule 查询。 */
    bool HasSceneRule() const { return intermediates && intermediates->hasSceneRule; }

    /**
     * @brief 获取意图契约
     * @return 意图契约引用，未设置时返回空结构
     */
    const IntentContract &GetIntentContract() const {
        return GetIntermediate(&StageIntermediates::intentContract,
                               &StageIntermediates::hasIntentContract);
    }

    /**
     * @brief 获取时间契约
     * @return 时间契约引用，未设置时返回空结构
     */
    const TemporalContract &GetTemporalContract() const {
        return GetIntermediate(&StageIntermediates::temporalContract,
                               &StageIntermediates::hasTemporalContract);
    }

    /**
     * @brief 获取资源请求
     * @return 资源请求引用，未设置时返回空结构
     */
    const ResourceRequest &GetResourceRequest() const {
        return GetIntermediate(&StageIntermediates::resourceRequest,
                               &StageIntermediates::hasResourceRequest);
    }

    /**
     * @brief 获取约束边界
     * @return 约束边界引用，未设置时返回空结构
     */
    const ConstraintAllowed &GetConstraintAllowed() const {
        return GetIntermediate(&StageIntermediates::constraintAllowed,
                               &StageIntermediates::hasConstraintAllowed);
    }

    /**
     * @brief 获取能力上限
     * @return 能力上限引用，未设置时返回空结构
     */
    const CapabilityFeasible &GetCapabilityFeasible() const {
        return GetIntermediate(&StageIntermediates::capabilityFeasible,
                               &StageIntermediates::hasCapabilityFeasible);
    }

    /**
     * @brief 获取仲裁结果
     * @return 仲裁结果引用，未设置时返回空结构
     */
    const ArbitrationResult &GetArbitrationResult() const {
        return GetConvergeIntermediate(&ConvergeStageIntermediates::arbitrationResult,
                                       &ConvergeStageIntermediates::hasArbitrationResult);
    }

    /**
     * @brief 获取快速授权
     * @return 快速授权引用，未设置时返回空结构
     */
    const FastGrant &GetFastGrant() const {
        return GetFastIntermediate(&FastStageIntermediates::fastGrant,
                                   &FastStageIntermediates::hasFastGrant);
    }

    FastGrant &MutableFastGrant() { return fastIntermediates->fastGrant; }

    /**
     * @brief 获取策略决策
     * @return 策略决策引用，未设置时返回空结构
     */
    const PolicyDecision &GetDecision() const {
        return GetConvergeIntermediate(&ConvergeStageIntermediates::decision,
                                       &ConvergeStageIntermediates::hasDecision);
    }

    PolicyDecision &MutableDecision() { return convergeIntermediates->decision; }

    /**
     * @brief 获取依赖状态
     * @return 依赖状态引用，未设置时返回空结构
     */
    const DependencyState &GetDependencyState() const {
        return GetIntermediate(&StageIntermediates::dependencyState,
                               &StageIntermediates::hasDependencyState);
    }

    /**
     * @brief 获取稳定化状态
     * @return 稳定化状态引用，未设置时返回空结构
     */
    const StabilityState &GetStabilityState() const {
        return GetConvergeIntermediate(&ConvergeStageIntermediates::stabilityState,
                                       &ConvergeStageIntermediates::hasStabilityState);
    }

    bool HasStabilityState() const {
        return convergeIntermediates && convergeIntermediates->hasStabilityState;
    }

    /**
     * @brief 获取快照令牌
     * @return 完整的快照令牌结构体
     *
     * @note 遵循 GenerationPinning 原则，用于创建 DecisionMeta
     */
    SnapshotToken GetSnapshotToken() const {
        SnapshotToken token;
        token.generation = generation;
        token.snapshotTs = event ? event->timestamp : 0;
        token.token = snapshotToken;
        return token;
    }

    ConfigLoader &GetConfigLoader() const {
        return configLoader ? *configLoader : ConfigLoader::Instance();
    }

    RuleTable &GetRuleTable() const { return ruleTable ? *ruleTable : RuleTable::Instance(); }

    TraceRecorder &GetTraceRecorder() const {
        return traceRecorder ? *traceRecorder : TraceRecorder::Instance();
    }
};

/**
 * @struct StageOutput
 * @brief 阶段输出：阶段执行的标准化输出
 *
 * StageOutput 是所有阶段执行结果的统一封装：
 * - success: 执行是否成功
 * - dirtyBits: 标识哪些状态被修改
 * - reasonBits: 执行原因/错误码
 *
 * @note 使用 DirtyBits 而非直接修改状态，支持批量提交
 */
struct StageOutput {
    bool success = false;       ///< 执行是否成功
    DirtyBits dirtyBits;        ///< 脏标记：标识哪些状态被修改
    uint32_t reasonBits = 0;    ///< 原因位：执行原因或错误码
};

/**
 * @class IStage
 * @brief 阶段接口：阶段的统一抽象接口
 *
 * IStage 定义了所有阶段必须实现的接口：
 * - Name(): 返回阶段名称，用于日志和调试
 * - Execute(): 执行阶段逻辑，返回标准化输出
 *
 * 阶段编排原则：
 * 1. 每个阶段只做一件事（单一职责）
 * 2. 阶段间通过 StageIntermediates 传递数据
 * 3. 阶段执行顺序由 Orchestrator 控制
 *
 * @see 框架 §4 阶段编排
 */
class IStage {
public:
    virtual ~IStage() = default;

    /**
     * @brief 获取阶段名称
     * @return 阶段名称字符串
     */
    virtual const char *Name() const = 0;

    /**
     * @brief 执行阶段逻辑
     * @param ctx 阶段上下文
     * @return 阶段输出结果
     *
     * 实现要求：
     * - 从 ctx.intermediates 读取上游阶段输出
     * - 将本阶段输出写入 ctx.intermediates
     * - 通过 dirtyBits 标识状态修改
     * - 不应抛出异常，错误通过 reasonBits 返回
     */
    virtual StageOutput Execute(StageContext &ctx) = 0;
};

}    // namespace vendor::transsion::perfengine::dse
