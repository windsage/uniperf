#include "MtkPlatformStateBackend.h"

#include "platform/state/PlatformStateCommon.h"

namespace vendor::transsion::perfengine::dse {
namespace {
// 该函数只填充抽象能力，不暴露具体节点路径或 HAL hint 编码。
void FillMtkCapability(PlatformCapability &cap) {
    cap = BuildStandardMobilePlatformCapability(PlatformVendor::Mtk);
}

// 状态后端工厂同样在平台层注册，StateService 只通过注册表获取抽象实例。
IPlatformStateBackend *CreateMtkStateBackend() {
    return new MtkPlatformStateBackend();
}

}    // namespace

const PlatformNodeSpec MtkPlatformStateBackend::kNodeSpecs[] = {
    {StateDomain::Thermal, "/sys/devices/virtual/thermal/thermal_zone0/temp", WatchMode::TimerPoll,
     NodeValueType::Integer, 1000, 0, true, true},
    {StateDomain::Thermal, "/sys/devices/virtual/thermal/thermal_zone1/temp", WatchMode::TimerPoll,
     NodeValueType::Integer, 1000, 0, false, true},
    {StateDomain::Battery, "/sys/class/power_supply/battery/capacity", WatchMode::TimerPoll,
     NodeValueType::Integer, 5000, 0, true, true},
    {StateDomain::Battery, "/sys/class/power_supply/battery/status", WatchMode::TimerPoll,
     NodeValueType::StringEnum, 5000, 0, false, true},
    {StateDomain::MemoryPsi, "/proc/pressure/memory", WatchMode::TimerPoll,
     NodeValueType::StructuredText, 2000, 0, true, true},
    {StateDomain::Screen, "/sys/class/backlight/panel/brightness", WatchMode::TimerPoll,
     NodeValueType::Integer, 1000, 0, false, true},
    {StateDomain::Power, "/sys/class/power_supply/battery/present", WatchMode::TimerPoll,
     NodeValueType::Boolean, 5000, 0, false, true},
    {StateDomain::Power, "/sys/class/power_supply/usb/online", WatchMode::TimerPoll,
     NodeValueType::Boolean, 2000, 0, false, true},
    {StateDomain::Thermal, "/sys/devices/virtual/thermal/thermal_zone0/mode", WatchMode::TimerPoll,
     NodeValueType::StringEnum, 2000, 0, false, true},
    {StateDomain::MemoryPsi, "/proc/pressure/cpu", WatchMode::TimerPoll,
     NodeValueType::StructuredText, 2000, 0, false, true},
    {StateDomain::Battery, "/sys/class/power_supply/battery/health", WatchMode::TimerPoll,
     NodeValueType::StringEnum, 10000, 0, false, true},
    {StateDomain::Thermal, "/sys/devices/virtual/thermal/thermal_zone1/mode", WatchMode::TimerPoll,
     NodeValueType::StringEnum, 2000, 0, false, true},
};

MtkPlatformStateBackend::~MtkPlatformStateBackend() {
    Stop();
}

PlatformStateTraits MtkPlatformStateBackend::BuildTraits() const noexcept {
    return BuildStandardPlatformStateTraits(PlatformVendor::Mtk);
}

const PlatformNodeSpec *MtkPlatformStateBackend::GetNodeSpecs(size_t *count) const noexcept {
    if (count) {
        *count = StaticArrayCount(kNodeSpecs);
    }
    return kNodeSpecs;
}

const PlatformNodeSpecExt *MtkPlatformStateBackend::GetNodeSpecsExt(size_t *count) const noexcept {
    static constexpr auto kNodeSpecsExt =
        BuildUniformNodeSpecExtArray<StaticArrayCount(kNodeSpecs)>("mtk");
    if (count) {
        *count = kNodeSpecsExt.size();
    }
    return kNodeSpecsExt.data();
}

bool MtkPlatformStateBackend::ReadThermalState(ConstraintSnapshot &snapshot) {
    return ReadThermalStateFromNode("/sys/devices/virtual/thermal/thermal_zone0/temp", snapshot);
}

bool MtkPlatformStateBackend::ReadBatteryState(ConstraintSnapshot &snapshot) {
    return ReadBatteryStateFromNodes("/sys/class/power_supply/battery/capacity",
                                     "/sys/class/power_supply/battery/status", snapshot);
}

bool MtkPlatformStateBackend::ReadMemoryState(ConstraintSnapshot &snapshot) {
    return ReadMemoryStateFromPsiNode("/proc/pressure/memory", snapshot);
}

bool MtkPlatformStateBackend::ReadScreenState(ConstraintSnapshot &snapshot) {
    return ReadScreenStateFromBrightnessNode("/sys/class/backlight/panel/brightness", snapshot);
}

bool MtkPlatformStateBackend::ReadPowerState(ConstraintSnapshot &snapshot) {
    return ReadPowerStateFromUsbNode("/sys/class/power_supply/usb/online", snapshot);
}

DSE_REGISTER_PLATFORM_CAPABILITY(Mtk, FillMtkCapability);
DSE_REGISTER_PLATFORM_STATE_BACKEND(Mtk, CreateMtkStateBackend);

}    // namespace vendor::transsion::perfengine::dse
