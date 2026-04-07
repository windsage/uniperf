#include "SafetyController.h"

namespace vendor::transsion::perfengine::dse {

SafetyController::SafetyController(const SafetyConfig &config) : config_(config) {
    if (config_.defaultDisabled) {
        state_.store(DseState::Disabled);
    } else {
        state_.store(DseState::Enabled);
    }
}

bool SafetyController::Enable() {
    DseState expected = DseState::Disabled;
    if (state_.compare_exchange_strong(expected, DseState::Enabling)) {
        state_.store(DseState::Enabled);
        return true;
    }

    expected = DseState::Fallback;
    if (state_.compare_exchange_strong(expected, DseState::Enabling)) {
        state_.store(DseState::Enabled);
        fallbackReason_ = FallbackReason::None;
        return true;
    }

    return false;
}

bool SafetyController::Disable() {
    DseState expected = DseState::Enabled;
    if (state_.compare_exchange_strong(expected, DseState::Disabling)) {
        state_.store(DseState::Disabled);
        return true;
    }

    expected = DseState::Fallback;
    if (state_.compare_exchange_strong(expected, DseState::Disabling)) {
        state_.store(DseState::Disabled);
        fallbackReason_ = FallbackReason::None;
        return true;
    }

    return false;
}

bool SafetyController::RequestFallback(FallbackReason reason) {
    if (!config_.allowExplicitFallback) {
        return false;
    }

    DseState expected = DseState::Enabled;
    if (state_.compare_exchange_strong(expected, DseState::Fallback)) {
        fallbackReason_ = reason;
        return true;
    }

    return false;
}

bool SafetyController::ExitFallback() {
    DseState expected = DseState::Fallback;
    if (state_.compare_exchange_strong(expected, DseState::Enabled)) {
        fallbackReason_ = FallbackReason::None;
        return true;
    }
    return false;
}

bool SafetyController::ShouldUseGrayscale(uint32_t instanceId) const {
    if (!config_.allowGrayscale) {
        return false;
    }

    uint32_t percent = grayscalePercent_.load(std::memory_order_relaxed);
    if (percent == 0) {
        return false;
    }

    if (percent >= 100) {
        return true;
    }

    uint32_t bucket = instanceId % 100;
    return bucket < percent;
}

void SafetyController::SetGrayscalePercent(uint32_t percent) {
    grayscalePercent_.store((percent > 100) ? 100 : percent, std::memory_order_relaxed);
}

const char *SafetyController::StateToString(DseState state) {
    switch (state) {
        case DseState::Disabled:
            return "Disabled";
        case DseState::Enabling:
            return "Enabling";
        case DseState::Enabled:
            return "Enabled";
        case DseState::Disabling:
            return "Disabling";
        case DseState::Fallback:
            return "Fallback";
        default:
            return "Unknown";
    }
}

const char *SafetyController::FallbackReasonToString(FallbackReason reason) {
    switch (reason) {
        case FallbackReason::None:
            return "None";
        case FallbackReason::ConfigError:
            return "ConfigError";
        case FallbackReason::PlatformUnsupported:
            return "PlatformUnsupported";
        case FallbackReason::RuntimeError:
            return "RuntimeError";
        case FallbackReason::ThermalEmergency:
            return "ThermalEmergency";
        case FallbackReason::UserRequest:
            return "UserRequest";
        case FallbackReason::SystemUpgrade:
            return "SystemUpgrade";
        default:
            return "Unknown";
    }
}

bool SafetyController::IsUidAllowed(int32_t uid) const {
    if (config_.enableUidBlacklist && uidBlacklist_.count(uid) > 0) {
        return false;
    }

    if (config_.enableUidWhitelist && !uidWhitelist_.empty()) {
        return uidWhitelist_.count(uid) > 0;
    }

    return true;
}

bool SafetyController::IsUidAuthorizedForHighIntent(int32_t uid, uint32_t semanticAction) const {
    if (!IsUidAllowed(uid)) {
        return false;
    }
    const bool highIntentSemantic = (semanticAction >= 1 && semanticAction <= 20);
    if (!highIntentSemantic) {
        return true;
    }
    if (uid >= 0 && uid < 10000) {
        return true;
    }
    return uidWhitelist_.count(uid) > 0;
}

void SafetyController::AddUidToWhitelist(int32_t uid) {
    uidWhitelist_.insert(uid);
}

void SafetyController::RemoveUidFromWhitelist(int32_t uid) {
    uidWhitelist_.erase(uid);
}

void SafetyController::ClearWhitelist() {
    uidWhitelist_.clear();
}

void SafetyController::AddUidToBlacklist(int32_t uid) {
    uidBlacklist_.insert(uid);
}

void SafetyController::RemoveUidFromBlacklist(int32_t uid) {
    uidBlacklist_.erase(uid);
}

void SafetyController::ClearBlacklist() {
    uidBlacklist_.clear();
}

void SafetyController::SetEnableUidWhitelist(bool enable) {
    config_.enableUidWhitelist = enable;
}

void SafetyController::SetEnableUidBlacklist(bool enable) {
    config_.enableUidBlacklist = enable;
}

}    // namespace vendor::transsion::perfengine::dse
