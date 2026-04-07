#pragma once

#include <array>
#include <cstdio>
#include <fstream>
#include <string>

#include "StateTypes.h"

namespace vendor::transsion::perfengine::dse {

inline constexpr uint32_t ResourceBit(ResourceDim dim) noexcept {
    return (1u << static_cast<uint32_t>(dim));
}

template <typename T, size_t N>
inline constexpr size_t StaticArrayCount(const T (&)[N]) noexcept {
    return N;
}

inline constexpr uint32_t kStandardStateDomainMask =
    ToMask(StateDomain::Thermal) | ToMask(StateDomain::Battery) | ToMask(StateDomain::MemoryPsi) |
    ToMask(StateDomain::Screen) | ToMask(StateDomain::Power);

/**
 * @brief 初始化约束快照默认值。
 *
 * 保证首帧采样不完整时仍然向上游提供完整、可比较的 canonical snapshot。
 */
inline void InitializeConstraintSnapshotDefaults(ConstraintSnapshot &snapshot) noexcept {
    snapshot = ConstraintSnapshot{};
    snapshot.thermalScaleQ10 = 1024;
    snapshot.batteryLevel = 100;
    snapshot.batteryScaleQ10 = 1024;
    snapshot.psiScaleQ10 = 1024;
    snapshot.powerState = PowerState::Active;
    snapshot.screenOn = true;
}

/** @brief 将摄氏温度映射到统一热等级。 */
inline uint32_t MapThermalLevelFromCelsius(int milliCelsius) noexcept {
    const int celsius = milliCelsius / 1000;
    if (celsius >= 90)
        return 5;
    if (celsius >= 80)
        return 4;
    if (celsius >= 70)
        return 3;
    if (celsius >= 55)
        return 2;
    if (celsius >= 42)
        return 1;
    return 0;
}

/** @brief 统一热等级到 Q10 缩放的映射。 */
inline uint32_t ThermalScaleFromLevel(uint32_t level) noexcept {
    switch (level) {
        case 5:
            return 256;
        case 4:
            return 512;
        case 3:
            return 640;
        case 2:
            return 768;
        case 1:
            return 896;
        default:
            return 1024;
    }
}

/**
 * @brief 构造当前项目首期平台共享的抽象能力描述。
 *
 * QCOM/MTK 当前验收范围相同，仅 vendor 标识不同；共享能力集合下沉到
 * platform/state 公共 helper，避免两套 backend 各自维护一份位图。
 */
inline PlatformCapability BuildStandardMobilePlatformCapability(PlatformVendor vendor) noexcept {
    PlatformCapability cap;
    cap.vendor = vendor;
    cap.supportedResources =
        ResourceBit(ResourceDim::CpuCapacity) | ResourceBit(ResourceDim::MemoryCapacity) |
        ResourceBit(ResourceDim::MemoryBandwidth) | ResourceBit(ResourceDim::GpuCapacity) |
        ResourceBit(ResourceDim::StorageBandwidth) | ResourceBit(ResourceDim::StorageIops) |
        ResourceBit(ResourceDim::NetworkBandwidth);
    cap.supportedActions = 0xFF;
    cap.hasPerfHintHal = true;
    cap.hasPmQos = true;
    cap.hasCgroupV2 = true;
    cap.hasEbpf = true;
    cap.hasGpuPreemption = false;
    return cap;
}

/** @brief 构造当前首期平台通用的状态监听能力描述。 */
inline PlatformStateTraits BuildStandardPlatformStateTraits(PlatformVendor vendor) noexcept {
    PlatformStateTraits traits;
    traits.vendor = vendor;
    traits.supportedStateMask = kStandardStateDomainMask;
    traits.hasThermalUevent = true;
    traits.hasPsiTrigger = true;
    traits.hasDisplayCallback = false;
    traits.hasHealthHal = true;
    traits.capabilityHotReload = false;
    return traits;
}

/** @brief 将平台注册表返回的抽象能力填充为 canonical capability snapshot。 */
inline void FillCapabilitySnapshotFromPlatformCapability(
    const PlatformCapability &platformCapability, CapabilitySnapshot &capability) noexcept {
    capability = CapabilitySnapshot{};
    capability.supportedResources = platformCapability.supportedResources;
    capability.capabilityFlags = ComputePlatformTraitFlags(platformCapability);
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        const uint32_t dimMask = (1u << i);
        capability.domains[i].maxCapacity =
            ((platformCapability.supportedResources & dimMask) != 0) ? 1024u : 0u;
    }
}

/** @brief 将完整约束快照展平成标准监听 delta。 */
inline StateDelta BuildStandardConstraintDelta(const ConstraintSnapshot &constraint,
                                               uint64_t captureNs) noexcept {
    StateDelta delta;
    delta.domainMask = kStandardStateDomainMask;
    delta.captureNs = captureNs;
    delta.constraint.thermalLevel = constraint.thermalLevel;
    delta.constraint.thermalScaleQ10 = constraint.thermalScaleQ10;
    delta.constraint.thermalSevere = constraint.thermalSevere;
    delta.constraint.batteryLevel = constraint.batteryLevel;
    delta.constraint.batteryScaleQ10 = constraint.batteryScaleQ10;
    delta.constraint.batterySaver = constraint.batterySaver;
    delta.constraint.memoryPressure = constraint.memoryPressure;
    delta.constraint.psiLevel = constraint.psiLevel;
    delta.constraint.psiScaleQ10 = constraint.psiScaleQ10;
    delta.constraint.screenOn = constraint.screenOn;
    delta.constraint.powerState = constraint.powerState;
    delta.constraint.fieldMask =
        ConstraintDeltaPatch::kThermalLevel | ConstraintDeltaPatch::kThermalScale |
        ConstraintDeltaPatch::kThermalSevere | ConstraintDeltaPatch::kBatteryLevel |
        ConstraintDeltaPatch::kBatteryScale | ConstraintDeltaPatch::kBatterySaver |
        ConstraintDeltaPatch::kMemoryPressure | ConstraintDeltaPatch::kPsiLevel |
        ConstraintDeltaPatch::kPsiScale | ConstraintDeltaPatch::kScreenOn |
        ConstraintDeltaPatch::kPowerState;
    return delta;
}

/** @brief 标准轮询间隔策略。 */
inline uint32_t GetStandardPollMs(const MonitoringContext &ctx) noexcept {
    return ctx.interactive ? 1000u : 5000u;
}

/** @brief 构造默认的节点扩展描述。 */
inline constexpr PlatformNodeSpecExt MakeDefaultNodeSpecExt(const char *bspTag) noexcept {
    return PlatformNodeSpecExt{29, 0, bspTag, nullptr, nullptr};
}

template <size_t N>
inline constexpr std::array<PlatformNodeSpecExt, N> BuildUniformNodeSpecExtArray(
    const char *bspTag) noexcept {
    std::array<PlatformNodeSpecExt, N> specs{};
    for (size_t i = 0; i < N; ++i) {
        specs[i] = MakeDefaultNodeSpecExt(bspTag);
    }
    return specs;
}

inline bool ReadThermalStateFromNode(const char *path, ConstraintSnapshot &snapshot) {
    std::ifstream file(path);
    if (!file.is_open()) {
        return false;
    }
    int temp = 0;
    file >> temp;
    snapshot.thermalLevel = MapThermalLevelFromCelsius(temp);
    snapshot.thermalScaleQ10 = ThermalScaleFromLevel(snapshot.thermalLevel);
    snapshot.thermalSevere = (snapshot.thermalLevel >= 5);
    return true;
}

inline bool ReadBatteryStateFromNodes(const char *capacityPath, const char *statusPath,
                                      ConstraintSnapshot &snapshot) {
    bool capacityRead = false;
    std::ifstream capacity(capacityPath);
    if (capacity.is_open()) {
        capacity >> snapshot.batteryLevel;
        capacityRead = true;
    }

    bool statusRead = false;
    std::ifstream status(statusPath);
    if (status.is_open()) {
        std::string statusStr;
        status >> statusStr;
        snapshot.batterySaver = (statusStr == "Discharging" && snapshot.batteryLevel <= 20);
        statusRead = true;
    }

    return capacityRead || statusRead;
}

inline bool ReadMemoryStateFromPsiNode(const char *path, ConstraintSnapshot &snapshot) {
    std::ifstream file(path);
    if (!file.is_open()) {
        return false;
    }
    std::string line;
    if (std::getline(file, line)) {
        size_t avg10Pos = line.find("avg10=");
        if (avg10Pos != std::string::npos) {
            float avg10 = 0.0f;
            std::sscanf(line.c_str() + avg10Pos + 6, "%f", &avg10);
            snapshot.psiLevel = static_cast<uint32_t>(avg10 * 10);
            snapshot.memoryPressure = snapshot.psiLevel > 50 ? 2 : (snapshot.psiLevel > 20 ? 1 : 0);
        }
    }
    return true;
}

inline bool ReadScreenStateFromBrightnessNode(const char *path, ConstraintSnapshot &snapshot) {
    std::ifstream file(path);
    if (!file.is_open()) {
        snapshot.screenOn = true;
        return false;
    }
    int brightness = 0;
    file >> brightness;
    snapshot.screenOn = (brightness > 0);
    return true;
}

inline bool ReadPowerStateFromUsbNode(const char *path, ConstraintSnapshot &snapshot) {
    std::ifstream usbOnline(path);
    if (!usbOnline.is_open()) {
        return false;
    }
    int online = 0;
    usbOnline >> online;
    snapshot.powerState = (online == 1) ? PowerState::Charging : PowerState::Discharging;
    return true;
}

}    // namespace vendor::transsion::perfengine::dse
