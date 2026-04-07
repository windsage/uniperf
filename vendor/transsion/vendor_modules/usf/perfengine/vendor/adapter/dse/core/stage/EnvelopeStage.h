#pragma once

#include "StageBase.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

struct EnvelopeStageOutput : StageOutput {
    ConstraintAllowed constraintAllowed;
};

class EnvelopeStage : public IStage {
public:
    const char *Name() const override { return "EnvelopeStage"; }

    StageOutput Execute(StageContext &ctx) override;

    static ConstraintAllowed ComputeConstraintAllowed(const ConstraintSnapshot &snapshot);
    static CapabilityFeasible ComputeCapabilityFeasible(StageContext &ctx);
    static uint32_t BuildConstraintSourceBits(const ConstraintSnapshot &snapshot);
    static ConstraintEnvelope BuildConstraintEnvelope(const ConstraintSnapshot &snapshot,
                                                      uint32_t resourceMask);

    static uint32_t ComputeThermalScaling(const ConstraintSnapshot &snapshot);
    static uint32_t ComputeBatteryScaling(const ConstraintSnapshot &snapshot);
    static uint32_t ComputeMemoryScaling(const ConstraintSnapshot &snapshot);

private:
    static ConstraintAllowed ComputeConstraintAllowedImpl(const ConstraintSnapshot &snapshot,
                                                          bool applyScreenConstraint);
    static void ApplyScreenConstraintForScene(const ConstraintSnapshot &snapshot,
                                              const SceneSemantic &semantic,
                                              const IntentContract &intent,
                                              ConstraintAllowed &allowed);
    static void ApplyScreenConstraint(const ConstraintSnapshot &snapshot,
                                      ConstraintAllowed &allowed);
};

}    // namespace vendor::transsion::perfengine::dse
