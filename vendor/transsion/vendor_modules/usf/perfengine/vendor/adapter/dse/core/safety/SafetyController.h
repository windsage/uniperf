#pragma once

// 商用安全：默认关闭、灰度、显式回退；trace 与主开关分离（任务清单 §0.3、§14）。
// 包含调用方准入控制，验证 UID/PID 合法性。

#include <atomic>
#include <cstdint>
#include <unordered_set>

namespace vendor::transsion::perfengine::dse {

enum class DseState : uint8_t {
    Disabled = 0,
    Enabling = 1,
    Enabled = 2,
    Disabling = 3,
    Fallback = 4
};

enum class FallbackReason : uint8_t {
    None = 0,
    ConfigError = 1,
    PlatformUnsupported = 2,
    RuntimeError = 3,
    ThermalEmergency = 4,
    UserRequest = 5,
    SystemUpgrade = 6
};

struct SafetyConfig {
    bool defaultDisabled = true;
    bool allowGrayscale = true;
    uint32_t grayscalePercent = 0;
    bool allowExplicitFallback = true;
    bool traceSeparateFromMain = true;
    bool rejectSilentFallback = true;
    bool requireExplicitFallbackReason = true;
    bool enableUidWhitelist = false;
    bool enableUidBlacklist = false;
};

class SafetyController {
public:
    explicit SafetyController(const SafetyConfig &config = SafetyConfig{});

    bool IsEnabled() const { return state_.load() == DseState::Enabled; }
    bool IsDisabled() const { return state_.load() == DseState::Disabled; }
    bool IsFallback() const { return state_.load() == DseState::Fallback; }
    DseState GetState() const { return state_.load(); }

    bool CanProcess() const {
        auto s = state_.load();
        return s == DseState::Enabled || s == DseState::Enabling;
    }

    bool CanProcessWithGrayscale(uint32_t instanceId) const {
        auto s = state_.load();
        if (s == DseState::Enabled || s == DseState::Enabling) {
            return true;
        }
        if (s == DseState::Disabled && grayscalePercent_.load() > 0) {
            return ShouldUseGrayscale(instanceId);
        }
        return false;
    }

    bool Enable();
    bool Disable();
    bool RequestFallback(FallbackReason reason);
    bool ExitFallback();

    bool ShouldUseGrayscale(uint32_t instanceId) const;

    FallbackReason GetFallbackReason() const { return fallbackReason_; }
    uint32_t GetGrayscalePercent() const { return grayscalePercent_; }

    void SetGrayscalePercent(uint32_t percent);

    static const char *StateToString(DseState state);
    static const char *FallbackReasonToString(FallbackReason reason);

    bool IsUidAllowed(int32_t uid) const;
    /**
     * P1/P2 语义段（action 1–20，与 SchedEvent 统一语义层一致）：
     * 仅系统 UID（小于 10000）或显式白名单 UID 可触发；用于商用防伪造高意图事件。
     */
    bool IsUidAuthorizedForHighIntent(int32_t uid, uint32_t semanticAction) const;
    void AddUidToWhitelist(int32_t uid);
    void RemoveUidFromWhitelist(int32_t uid);
    void ClearWhitelist();

    void AddUidToBlacklist(int32_t uid);
    void RemoveUidFromBlacklist(int32_t uid);
    void ClearBlacklist();

    void SetEnableUidWhitelist(bool enable);
    void SetEnableUidBlacklist(bool enable);

private:
    SafetyConfig config_;
    std::atomic<DseState> state_{DseState::Disabled};
    std::atomic<uint32_t> grayscalePercent_{0};
    FallbackReason fallbackReason_{FallbackReason::None};
    std::unordered_set<int32_t> uidWhitelist_;
    std::unordered_set<int32_t> uidBlacklist_;
};

}    // namespace vendor::transsion::perfengine::dse
