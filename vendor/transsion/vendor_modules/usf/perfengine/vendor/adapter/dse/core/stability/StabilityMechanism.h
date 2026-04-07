#pragma once

#include <cstdint>

#include "state/StateDomains.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @struct StabilityParams
 * @brief 稳定化参数集合。
 *
 * 对应框架中的四类稳定化机制：
 * - observationWindowMs：稳定观察窗口
 * - minResidencyMs：最小驻留时间
 * - hysteresisEnterMs / hysteresisExitMs：阈值滞回
 * - updateThrottleMs：更新节流
 * - steadyEnterMerges / exitHysteresisMerges：合并次数门槛
 */
struct StabilityParams {
    int64_t observationWindowMs = 100;
    int64_t minResidencyMs = 50;
    int64_t hysteresisEnterMs = 0;
    int64_t hysteresisExitMs = 0;
    int64_t updateThrottleMs = 10;
    uint32_t steadyEnterMerges = 2;
    uint32_t exitHysteresisMerges = 1;
};

/**
 * @struct StabilityInput
 * @brief 稳定化判定输入。
 *
 * 该输入同时携带候选状态和已提交状态，用于判断：
 * - 是否发生迁移候选变化
 * - 是否应旁路 P1 首响应
 * - 是否满足进入/退出稳态的观察与驻留条件
 */
struct StabilityInput {
    int64_t currentTimeMs = 0;
    uint32_t mergeCount = 0;
    bool semanticChanged = false;
    bool intentChanged = false;
    bool bypassForP1 = false;
    SceneSemantic candidateSemantic;
    IntentContract candidateIntent;
    SceneSemantic committedSemantic;
    IntentContract committedIntent;
};

/**
 * @struct StabilityOutput
 * @brief 稳定化判定输出。
 *
 * 输出不仅描述“是否进入稳态”，还包含是否应 hold、是否节流、
 * 当前观察中的 pending 候选等状态，便于调用者把稳定化状态写回 Vault。
 */
struct StabilityOutput {
    bool shouldEnterSteady = false;
    bool shouldExitSteady = false;
    bool shouldThrottle = false;
    bool shouldHold = false;
    bool inObservation = false;
    int64_t nextUpdateMs = 0;
    int64_t lastUpdateMs = 0;
    int64_t observationStartTimeMs = 0;
    int64_t steadyEnterTimeMs = 0;
    bool inSteady = false;
    uint32_t steadyEnterMerges = 0;
    uint32_t exitHysteresisMerges = 0;
    uint32_t stableMergeCount = 0;
    uint32_t exitMergeCount = 0;
    bool pendingExit = false;
    SceneSemantic pendingSemantic;
    IntentContract pendingIntent;
};

/**
 * @class StabilityMechanism
 * @brief 迁移稳定化算法实现。
 *
 * 该类封装了框架中的稳定观察窗口、最小驻留、滞回与更新节流逻辑。
 * 它是纯状态机实现，不直接依赖平台或 StageContext，便于测试和回放。
 */
class StabilityMechanism {
public:
    explicit StabilityMechanism(const StabilityParams &params = StabilityParams{});

    /**
     * @brief 评估当前候选迁移是否可提交。
     * @param input 稳定化输入。
     * @return 稳定化输出。
     */
    StabilityOutput Evaluate(const StabilityInput &input);

    bool IsInSteady() const { return inSteady_; }
    bool IsInObservation() const { return inObservation_; }

    /** @brief 清空内部状态机。 */
    void Reset();
    /** @brief 更新稳定化参数。 */
    void UpdateParams(const StabilityParams &params);
    const StabilityParams &GetParams() const { return params_; }

    /** @brief 从持久化的 StabilityState 恢复内部状态。 */
    /** @param state 按值接收以便调用方 std::move，减少稳定化状态恢复时的多余拷贝。 */
    void RestoreState(StabilityState state);

private:
    /** @brief 检查是否满足观察窗口时长。 */
    bool CheckObservationWindow(const StabilityInput &input) const;
    /** @brief 检查当前稳态是否已满足最小驻留时间。 */
    bool CheckMinResidency(const StabilityInput &input) const;
    /** @brief 检查进入稳态前的滞回条件。 */
    bool CheckHysteresis(const StabilityInput &input) const;
    /** @brief 检查从稳态退出时的滞回条件。 */
    bool CheckExitHysteresis(const StabilityInput &input) const;
    /** @brief 检查本轮更新是否应被节流。 */
    bool CheckUpdateThrottle(const StabilityInput &input);
    /** @brief 判断当前候选是否与正在观察的候选相同。 */
    bool IsSameCandidate(const StabilityInput &input) const;
    /** @brief 开始观察一个新的候选迁移。 */
    void StartObservation(const StabilityInput &input);
    /** @brief 提交候选迁移并切换内部状态。 */
    void CommitCandidate(const StabilityInput &input);

    StabilityParams params_;
    bool inSteady_ = false;
    bool inObservation_ = false;
    int64_t observationStartTimeMs_ = 0;
    int64_t steadyEnterTimeMs_ = 0;
    int64_t lastUpdateTimeMs_ = 0;
    uint32_t stableMergeCount_ = 0;
    uint32_t lastMergeCount_ = 0;
    uint32_t exitMergeCount_ = 0;
    bool pendingExit_ = false;
    SceneSemantic pendingSemantic_{};
    IntentContract pendingIntent_{};
};

}    // namespace vendor::transsion::perfengine::dse
