#pragma once

#include <chrono>
#include <cstdint>

#include "core/types/ConstraintTypes.h"
#include "platform/PlatformBase.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @brief 平台原始时间戳的时钟域（与状态采集、NormalizeTimestamp 对齐）
 */
enum class SourceClock : uint8_t {
    Unknown,
    Monotonic,
    Boottime,
    Realtime,
    External,
};

class SystemTime {
public:
    static uint64_t GetDeterministicNs() noexcept {
        auto now = std::chrono::steady_clock::now();
        return static_cast<uint64_t>(
            std::chrono::duration_cast<std::chrono::nanoseconds>(now.time_since_epoch()).count());
    }

    static uint64_t GetBoottimeNs() noexcept { return GetDeterministicNs(); }

    static int64_t NormalizeTimestamp(uint64_t sourceNs, SourceClock sourceClock) {
        switch (sourceClock) {
            case SourceClock::Monotonic:
            case SourceClock::Boottime:
                return static_cast<int64_t>(sourceNs);
            case SourceClock::Realtime:
            case SourceClock::External:
            case SourceClock::Unknown:
            default:
                return -1;
        }
    }
};

enum class StateDomain : uint32_t {
    None = 0,
    Thermal = 1 << 0,
    Battery = 1 << 1,
    MemoryPsi = 1 << 2,
    Screen = 1 << 3,
    Power = 1 << 4,
    Capability = 1 << 5,
};

inline constexpr uint32_t ToMask(StateDomain domain) {
    return static_cast<uint32_t>(domain);
}

inline constexpr uint32_t kStateDomainCount = 6;

/** 将监听域映射到 [0, kStateDomainCount) 下标；未知域返回 0 */
inline constexpr uint32_t StateDomainToIndex(StateDomain domain) noexcept {
    switch (domain) {
        case StateDomain::Thermal:
            return 0;
        case StateDomain::Battery:
            return 1;
        case StateDomain::MemoryPsi:
            return 2;
        case StateDomain::Screen:
            return 3;
        case StateDomain::Power:
            return 4;
        case StateDomain::Capability:
            return 5;
        default:
            return 0;
    }
}

enum class WatchMode : uint8_t {
    Uevent,
    EpollFd,
    BinderCallback,
    PropertyTrigger,
    TimerPoll,
};

enum class NodeValueType : uint8_t {
    Integer,
    Boolean,
    StringEnum,
    StructuredText,
};

enum class MonitoringHintLevel : uint8_t {
    LatencyCritical,
    Interactive,
    Balanced,
    Idle,
};

struct MonitoringContext {
    MonitoringHintLevel hintLevel = MonitoringHintLevel::Balanced;
    uint32_t profileId = 0;
    bool interactive = true;
    bool screenOn = true;
};

struct PlatformNodeSpec {
    StateDomain domain = StateDomain::None;
    const char *path = nullptr;
    WatchMode watchMode = WatchMode::TimerPoll;
    NodeValueType valueType = NodeValueType::Integer;
    uint32_t pollMs = 0;
    uint32_t fallbackFlags = 0;
    bool required = false;
    bool bootRead = true;
};

struct PlatformNodeSpecExt {
    uint32_t minAndroidApi = 0;
    uint32_t maxAndroidApi = 0;
    const char *bspTag = nullptr;
    const char *modelAllowlist = nullptr;
    const char *modelDenylist = nullptr;
};

struct PlatformStateTraits {
    PlatformVendor vendor = PlatformVendor::Unknown;
    uint32_t supportedStateMask = 0;
    bool hasThermalUevent = false;
    bool hasPsiTrigger = false;
    bool hasDisplayCallback = false;
    bool hasHealthHal = false;
    bool capabilityHotReload = false;
};

struct RawStateEvent {
    StateDomain domain = StateDomain::None;
    uint64_t captureNs = 0;
    uint64_t sourceTimestampNs = 0;
    uint64_t perceptionLatencyNs = 0;
    SourceClock sourceClock = SourceClock::Unknown;
    uint32_t nodeIndex = 0;
    int32_t intValue = 0;
    bool boolValue = false;
    const char *textValue = nullptr;
};

struct ConstraintDeltaPatch {
    uint32_t fieldMask = 0;
    uint32_t thermalLevel = 0;
    uint32_t thermalScaleQ10 = 1024;
    uint32_t batteryLevel = 100;
    uint32_t batteryScaleQ10 = 1024;
    uint32_t memoryPressure = 0;
    uint32_t psiLevel = 0;
    uint32_t psiScaleQ10 = 1024;
    PowerState powerState = PowerState::Active;
    bool batterySaver = false;
    bool screenOn = true;
    bool thermalSevere = false;

    static constexpr uint32_t kThermalLevel = 1 << 0;
    static constexpr uint32_t kThermalScale = 1 << 1;
    static constexpr uint32_t kThermalSevere = 1 << 2;
    static constexpr uint32_t kBatteryLevel = 1 << 3;
    static constexpr uint32_t kBatteryScale = 1 << 4;
    static constexpr uint32_t kBatterySaver = 1 << 5;
    static constexpr uint32_t kMemoryPressure = 1 << 6;
    static constexpr uint32_t kPsiLevel = 1 << 7;
    static constexpr uint32_t kPsiScale = 1 << 8;
    static constexpr uint32_t kScreenOn = 1 << 9;
    static constexpr uint32_t kPowerState = 1 << 10;
};

struct CapabilityDeltaPatch {
    uint32_t supportedResources = 0;
    uint32_t feasibleChangedMask = 0;
    uint32_t actionChangedMask = 0;
    uint32_t capabilityFlags = 0;
    ResourceVector maxCapacityPatch{};
    uint32_t domainFlags[kResourceDimCount] = {0};
    uint32_t actionPathFlags[kResourceDimCount] = {0};
};

struct StateDelta {
    uint32_t domainMask = 0;
    uint64_t captureNs = 0;
    ConstraintDeltaPatch constraint{};
    CapabilityDeltaPatch capability{};
    uint32_t fallbackFlags = 0;
};

struct ThermalRule {
    uint32_t thresholds[8] = {0, 30, 60, 80, 95, 100, 100, 100};
    uint32_t severeThreshold = 90;
    uint32_t scalingCurveQ10[8] = {1024, 1024, 896, 768, 512, 256, 128, 64};
};

struct BatteryRule {
    uint32_t thresholds[5] = {0, 15, 30, 50, 100};
    uint32_t saverThreshold = 20;
    uint32_t scalingCurveQ10[5] = {1024, 1024, 896, 768, 512};
};

struct PsiRule {
    uint32_t thresholds[4] = {0, 20, 50, 100};
    uint32_t scalingCurveQ10[4] = {1024, 896, 512, 256};
};

struct StateRuleSet {
    uint32_t ruleVersion = 0;
    ThermalRule thermal;
    BatteryRule battery;
    PsiRule psi;
};

class IStateSink {
public:
    virtual ~IStateSink() = default;
    virtual void OnDelta(const StateDelta &) noexcept = 0;
    virtual bool UpdateSync(const StateDelta &, uint32_t timeoutMs) noexcept = 0;
};

class IStateListener {
public:
    virtual ~IStateListener() = default;
    virtual void OnStateChanged(Generation generation, SnapshotToken token,
                                uint32_t domainMask) noexcept = 0;
};

}    // namespace vendor::transsion::perfengine::dse
