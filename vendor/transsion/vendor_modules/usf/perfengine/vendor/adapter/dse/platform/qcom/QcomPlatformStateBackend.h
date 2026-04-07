#pragma once

#include "platform/state/PollingPlatformStateBackend.h"

namespace vendor::transsion::perfengine::dse {

class QcomPlatformStateBackend final : public PollingPlatformStateBackend {
public:
    QcomPlatformStateBackend() = default;
    ~QcomPlatformStateBackend() override;

    const PlatformNodeSpec *GetNodeSpecs(size_t *count) const noexcept override;
    const PlatformNodeSpecExt *GetNodeSpecsExt(size_t *count) const noexcept override;

private:
    PlatformStateTraits BuildTraits() const noexcept override;
    bool ReadThermalState(ConstraintSnapshot &) override;
    bool ReadBatteryState(ConstraintSnapshot &) override;
    bool ReadMemoryState(ConstraintSnapshot &) override;
    bool ReadScreenState(ConstraintSnapshot &) override;
    bool ReadPowerState(ConstraintSnapshot &) override;

    static const PlatformNodeSpec kNodeSpecs[];
};

}    // namespace vendor::transsion::perfengine::dse
