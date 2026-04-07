#include "QcomPlatformStateBackend.h"

#include "platform/state/PlatformStateCommon.h"

namespace vendor::transsion::perfengine::dse {
namespace {
// 该函数只填充抽象能力，不暴露具体节点路径或 HAL hint 编码。
void FillQcomCapability(PlatformCapability &cap) {
    cap = BuildStandardMobilePlatformCapability(PlatformVendor::Qcom);
}

// 状态后端工厂同样在平台层注册，StateService 只通过注册表获取抽象实例。
IPlatformStateBackend *CreateQcomStateBackend() {
    return new QcomPlatformStateBackend();
}

}    // namespace

const PlatformNodeSpec QcomPlatformStateBackend::kNodeSpecs[] = {
    {StateDomain::Thermal, "/sys/class/thermal/thermal_zone0/temp", WatchMode::TimerPoll,
     NodeValueType::Integer, 1000, 0, true, true},
    {StateDomain::Thermal, "/sys/devices/virtual/thermal/thermal_zone0/mode", WatchMode::TimerPoll,
     NodeValueType::StringEnum, 2000, 0, false, true},
    {StateDomain::Battery, "/sys/class/power_supply/battery/capacity", WatchMode::TimerPoll,
     NodeValueType::Integer, 5000, 0, true, true},
    {StateDomain::Battery, "/sys/class/power_supply/battery/status", WatchMode::TimerPoll,
     NodeValueType::StringEnum, 5000, 0, false, true},
    {StateDomain::MemoryPsi, "/proc/pressure/memory", WatchMode::TimerPoll,
     NodeValueType::StructuredText, 2000, 0, true, true},
    {StateDomain::Screen, "/sys/class/backlight/panel0-backlight/brightness", WatchMode::TimerPoll,
     NodeValueType::Integer, 1000, 0, false, true},
    {StateDomain::Power, "/sys/class/power_supply/battery/present", WatchMode::TimerPoll,
     NodeValueType::Boolean, 5000, 0, false, true},
    {StateDomain::Power, "/sys/class/power_supply/usb/online", WatchMode::TimerPoll,
     NodeValueType::Boolean, 2000, 0, false, true},
    {StateDomain::Thermal, "/sys/devices/virtual/thermal/thermal_zone0/trip_point_0_temp",
     WatchMode::TimerPoll, NodeValueType::Integer, 5000, 0, false, true},
    {StateDomain::Thermal, "/sys/devices/virtual/thermal/thermal_zone0/trip_point_1_temp",
     WatchMode::TimerPoll, NodeValueType::Integer, 5000, 0, false, true},
    {StateDomain::Battery, "/sys/class/power_supply/battery/health", WatchMode::TimerPoll,
     NodeValueType::StringEnum, 10000, 0, false, true},
    {StateDomain::MemoryPsi, "/proc/pressure/cpu", WatchMode::TimerPoll,
     NodeValueType::StructuredText, 2000, 0, false, true},
};

QcomPlatformStateBackend::~QcomPlatformStateBackend() {
    Stop();
}

PlatformStateTraits QcomPlatformStateBackend::BuildTraits() const noexcept {
    return BuildStandardPlatformStateTraits(PlatformVendor::Qcom);
}

const PlatformNodeSpec *QcomPlatformStateBackend::GetNodeSpecs(size_t *count) const noexcept {
    if (count) {
        *count = StaticArrayCount(kNodeSpecs);
    }
    return kNodeSpecs;
}

const PlatformNodeSpecExt *QcomPlatformStateBackend::GetNodeSpecsExt(size_t *count) const noexcept {
    static constexpr auto kNodeSpecsExt =
        BuildUniformNodeSpecExtArray<StaticArrayCount(kNodeSpecs)>("qcom");
    if (count) {
        *count = kNodeSpecsExt.size();
    }
    return kNodeSpecsExt.data();
}

bool QcomPlatformStateBackend::ReadThermalState(ConstraintSnapshot &snapshot) {
    return ReadThermalStateFromNode("/sys/class/thermal/thermal_zone0/temp", snapshot);
}

bool QcomPlatformStateBackend::ReadBatteryState(ConstraintSnapshot &snapshot) {
    return ReadBatteryStateFromNodes("/sys/class/power_supply/battery/capacity",
                                     "/sys/class/power_supply/battery/status", snapshot);
}

bool QcomPlatformStateBackend::ReadMemoryState(ConstraintSnapshot &snapshot) {
    return ReadMemoryStateFromPsiNode("/proc/pressure/memory", snapshot);
}

bool QcomPlatformStateBackend::ReadScreenState(ConstraintSnapshot &snapshot) {
    return ReadScreenStateFromBrightnessNode("/sys/class/backlight/panel0-backlight/brightness",
                                             snapshot);
}

bool QcomPlatformStateBackend::ReadPowerState(ConstraintSnapshot &snapshot) {
    return ReadPowerStateFromUsbNode("/sys/class/power_supply/usb/online", snapshot);
}

DSE_REGISTER_PLATFORM_CAPABILITY(Qcom, FillQcomCapability);
DSE_REGISTER_PLATFORM_STATE_BACKEND(Qcom, CreateQcomStateBackend);

}    // namespace vendor::transsion::perfengine::dse
