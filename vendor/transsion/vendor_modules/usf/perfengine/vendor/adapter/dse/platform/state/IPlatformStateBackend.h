#pragma once

#include <cstdint>

#include "StateTypes.h"
#include "core/types/ConstraintTypes.h"

namespace vendor::transsion::perfengine::dse {

class IPlatformStateBackend {
public:
    virtual ~IPlatformStateBackend() = default;

    virtual bool Init() noexcept = 0;
    virtual bool ReadInitial(ConstraintSnapshot *, CapabilitySnapshot *) noexcept = 0;
    virtual bool Start(IStateSink *) noexcept = 0;
    virtual void Stop() noexcept = 0;
    virtual void UpdateMonitoringContext(const MonitoringContext &) noexcept = 0;

    virtual PlatformStateTraits GetTraits() const noexcept = 0;
    virtual const PlatformNodeSpec *GetNodeSpecs(size_t *count) const noexcept = 0;
    virtual const PlatformNodeSpecExt *GetNodeSpecsExt(size_t *count) const noexcept {
        if (count)
            *count = 0;
        return nullptr;
    }
};

}    // namespace vendor::transsion::perfengine::dse
