#include "StabilityMechanism.h"

#include <utility>

namespace vendor::transsion::perfengine::dse {

StabilityMechanism::StabilityMechanism(const StabilityParams &params) : params_(params) {}

// Evaluate 是稳定化状态机的唯一入口。
// 它会综合观察窗口、驻留、滞回和节流四类条件，决定候选迁移是否可以提交。
StabilityOutput StabilityMechanism::Evaluate(const StabilityInput &input) {
    StabilityOutput out;

    if (input.bypassForP1) {
        // P1 首响应必须旁路稳定化，避免交互起手被观察窗口阻塞。
        inSteady_ = false;
        inObservation_ = false;
        pendingExit_ = false;
        steadyEnterTimeMs_ = 0;
        pendingSemantic_ = SceneSemantic{};
        pendingIntent_ = IntentContract{};
        stableMergeCount_ = 0;
        exitMergeCount_ = 0;
        out.lastUpdateMs = input.currentTimeMs;
        lastUpdateTimeMs_ = input.currentTimeMs;
        lastMergeCount_ = input.mergeCount;
        out.inSteady = inSteady_;
        out.steadyEnterTimeMs = steadyEnterTimeMs_;
        out.steadyEnterMerges = params_.steadyEnterMerges;
        out.exitHysteresisMerges = params_.exitHysteresisMerges;
        return out;
    }

    const bool candidateChanged = input.semanticChanged || input.intentChanged;
    const bool throttled = candidateChanged && CheckUpdateThrottle(input);
    if (!candidateChanged) {
        // 候选未变化时，清理观察态，保持当前已提交状态。
        inObservation_ = false;
        pendingExit_ = false;
        stableMergeCount_ = 0;
        exitMergeCount_ = 0;
        pendingSemantic_ = SceneSemantic{};
        pendingIntent_ = IntentContract{};
    } else {
        if (!IsSameCandidate(input)) {
            // 候选变化意味着进入新的观察期。
            StartObservation(input);
        } else if (input.mergeCount > lastMergeCount_) {
            // 只有 mergeCount 前进时才累计合并次数，避免同一轮重复加计数。
            stableMergeCount_++;
        }

        if (inSteady_) {
            // 已在稳态时，新的候选变化被视为一次“退出当前稳态”的尝试。
            pendingExit_ = true;
            if (input.mergeCount > lastMergeCount_) {
                exitMergeCount_++;
            }
        } else if (exitMergeCount_ == 0) {
            exitMergeCount_ = 1;
        }

        out.shouldHold = true;
        const bool observationMet = CheckObservationWindow(input) && CheckHysteresis(input);
        const bool residencyMet = CheckMinResidency(input);
        const bool exitMet = (!pendingExit_) || CheckExitHysteresis(input);

        if (observationMet && residencyMet && exitMet && !throttled) {
            const bool wasPendingExit = pendingExit_;
            CommitCandidate(input);
            out.shouldEnterSteady = true;
            out.shouldExitSteady = wasPendingExit;
            out.shouldHold = false;
        }
    }

    out.shouldThrottle = throttled;
    if (out.shouldThrottle) {
        out.shouldHold = true;
    } else if (!out.shouldHold || !candidateChanged) {
        lastUpdateTimeMs_ = input.currentTimeMs;
    }

    lastMergeCount_ = input.mergeCount;
    out.inObservation = inObservation_;
    out.inSteady = inSteady_;
    out.observationStartTimeMs = observationStartTimeMs_;
    out.steadyEnterTimeMs = steadyEnterTimeMs_;
    out.pendingExit = pendingExit_;
    out.pendingSemantic = pendingSemantic_;
    out.pendingIntent = pendingIntent_;
    out.stableMergeCount = stableMergeCount_;
    out.exitMergeCount = exitMergeCount_;
    out.nextUpdateMs = lastUpdateTimeMs_ + params_.updateThrottleMs;
    out.lastUpdateMs = lastUpdateTimeMs_;
    out.steadyEnterMerges = params_.steadyEnterMerges;
    out.exitHysteresisMerges = params_.exitHysteresisMerges;
    return out;
}

void StabilityMechanism::Reset() {
    inSteady_ = false;
    inObservation_ = false;
    observationStartTimeMs_ = 0;
    steadyEnterTimeMs_ = 0;
    lastUpdateTimeMs_ = 0;
    stableMergeCount_ = 0;
    lastMergeCount_ = 0;
    exitMergeCount_ = 0;
    pendingExit_ = false;
    pendingSemantic_ = SceneSemantic{};
    pendingIntent_ = IntentContract{};
}

void StabilityMechanism::UpdateParams(const StabilityParams &params) {
    params_ = params;
}

// RestoreState 允许稳定化在多轮调度之间保持连续性，避免每次都从空状态重新观察。
void StabilityMechanism::RestoreState(StabilityState state) {
    inSteady_ = state.inSteady;
    inObservation_ = state.inObservation;
    observationStartTimeMs_ = state.observationStartTimeMs;
    steadyEnterTimeMs_ = state.steadyEnterTimeMs;
    lastUpdateTimeMs_ = state.lastUpdateMs;
    stableMergeCount_ = state.stableMergeCount;
    exitMergeCount_ = state.exitMergeCount;
    pendingExit_ = state.pendingExit;
    pendingSemantic_ = std::move(state.pendingSemantic);
    pendingIntent_ = std::move(state.pendingIntent);
}

bool StabilityMechanism::CheckObservationWindow(const StabilityInput &input) const {
    if (params_.observationWindowMs <= 0) {
        return true;
    }
    int64_t elapsed = input.currentTimeMs - observationStartTimeMs_;
    return elapsed >= params_.observationWindowMs;
}

// 最小驻留只对已经进入稳态的等级生效，防止刚收敛就立即被下一拍拉走。
bool StabilityMechanism::CheckMinResidency(const StabilityInput &input) const {
    if (!inSteady_ || params_.minResidencyMs <= 0) {
        return true;
    }
    int64_t residency = input.currentTimeMs - steadyEnterTimeMs_;
    return residency >= params_.minResidencyMs;
}

// 进入滞回既可以按时间，也可以按 merge 次数实现；两者都属于抑制抖动的手段。
bool StabilityMechanism::CheckHysteresis(const StabilityInput &input) const {
    if (params_.hysteresisEnterMs > 0) {
        int64_t elapsed = input.currentTimeMs >= observationStartTimeMs_
                              ? (input.currentTimeMs - observationStartTimeMs_)
                              : 0;
        if (elapsed < params_.hysteresisEnterMs) {
            return false;
        }
    }

    if (params_.steadyEnterMerges > 1) {
        return stableMergeCount_ >= params_.steadyEnterMerges;
    }

    return true;
}

// 退出滞回与进入滞回分离，便于形成回差区间，避免边界来回切换。
bool StabilityMechanism::CheckExitHysteresis(const StabilityInput &input) const {
    if (params_.hysteresisExitMs > 0 && inSteady_) {
        int64_t residency = input.currentTimeMs >= steadyEnterTimeMs_
                                ? (input.currentTimeMs - steadyEnterTimeMs_)
                                : 0;
        if (residency < params_.hysteresisExitMs) {
            return false;
        }
    }

    if (params_.exitHysteresisMerges > 1) {
        return exitMergeCount_ >= params_.exitHysteresisMerges;
    }

    return true;
}

bool StabilityMechanism::CheckUpdateThrottle(const StabilityInput &input) {
    if (params_.updateThrottleMs <= 0) {
        return false;
    }
    int64_t elapsed = input.currentTimeMs - lastUpdateTimeMs_;
    return elapsed < params_.updateThrottleMs;
}

bool StabilityMechanism::IsSameCandidate(const StabilityInput &input) const {
    return inObservation_ && SceneSemanticEquals(pendingSemantic_, input.candidateSemantic) &&
           pendingIntent_.level == input.candidateIntent.level;
}

// 开始观察时会重置 pending 候选，并把 merge 计数置为 1，
// 这样后续可以同时支持“时间窗口”和“合并次数”两种进入条件。
void StabilityMechanism::StartObservation(const StabilityInput &input) {
    inObservation_ = true;
    observationStartTimeMs_ = input.currentTimeMs;
    stableMergeCount_ = 1;
    exitMergeCount_ = inSteady_ ? 1 : 0;
    pendingSemantic_ = input.candidateSemantic;
    pendingIntent_ = input.candidateIntent;
}

void StabilityMechanism::CommitCandidate(const StabilityInput &input) {
    (void)input;
    // P1 是突发契约，不进入 steady；其他等级在提交后进入 steady/residency 链路。
    inSteady_ = (pendingIntent_.level != IntentLevel::P1);
    inObservation_ = false;
    steadyEnterTimeMs_ = input.currentTimeMs;
    pendingExit_ = false;
    stableMergeCount_ = 0;
    exitMergeCount_ = 0;
    pendingSemantic_ = SceneSemantic{};
    pendingIntent_ = IntentContract{};
    lastUpdateTimeMs_ = input.currentTimeMs;
}

}    // namespace vendor::transsion::perfengine::dse
