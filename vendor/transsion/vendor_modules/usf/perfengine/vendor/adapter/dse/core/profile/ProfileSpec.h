#pragma once

#include <cstdint>

#include "stage/ArbitrationStage.h"
#include "stage/ConvergeFinalizeStage.h"
#include "stage/EnvelopeStage.h"
#include "stage/FastFinalizeStage.h"
#include "stage/InheritanceStage.h"
#include "stage/IntentStage.h"
#include "stage/ResourceStage.h"
#include "stage/SceneStage.h"
#include "stage/StabilityStage.h"
#include "stage/StageRunner.h"

#if defined(DSE_PROFILE_MAIN) && defined(DSE_PROFILE_FLAGSHIP)
#error "DSE build must enable at most one profile macro"
#endif

namespace vendor::transsion::perfengine::dse {

enum class ProfileKind : uint8_t { Entry = 0, Main = 1, Flagship = 2 };

struct ProfileSpecBase {
    ProfileKind kind = ProfileKind::Entry;
    uint32_t resourceMask = 0xFF;
    uint32_t traceLevel = 0;
    int64_t observationWindowMs = 100;
    int64_t minResidencyMs = 50;
    uint32_t steadyEnterMerges = 2;
    uint32_t exitHysteresisMerges = 1;
    bool enableInheritance = true;
    bool enableStability = true;
    uint32_t fastActionMask = 0x2F;
};

template <bool EnableInheritance, bool EnableStability>
struct ConvergeStageBuilder;

template <>
struct ConvergeStageBuilder<false, false> {
    using Stages = TypeList<SceneStage, IntentStage, EnvelopeStage, ResourceStage, ArbitrationStage,
                            ConvergeFinalizeStage>;
};

template <>
struct ConvergeStageBuilder<false, true> {
    using Stages = TypeList<SceneStage, IntentStage, StabilityStage, ResourceStage, EnvelopeStage,
                            ArbitrationStage, ConvergeFinalizeStage>;
};

template <>
struct ConvergeStageBuilder<true, false> {
    using Stages = TypeList<SceneStage, IntentStage, ResourceStage, InheritanceStage, EnvelopeStage,
                            ArbitrationStage, ConvergeFinalizeStage>;
};

template <>
struct ConvergeStageBuilder<true, true> {
    using Stages =
        TypeList<SceneStage, IntentStage, StabilityStage, ResourceStage, InheritanceStage,
                 EnvelopeStage, ArbitrationStage, ConvergeFinalizeStage>;
};

struct EntryProfileSpec : ProfileSpecBase {
    EntryProfileSpec() {
        kind = ProfileKind::Entry;
        resourceMask = 0x0F;
        traceLevel = 0;
        observationWindowMs = 50;
        minResidencyMs = 30;
        steadyEnterMerges = 1;
        exitHysteresisMerges = 0;
        enableInheritance = false;
        enableStability = false;
    }

    using FastStages =
        TypeList<SceneStage, IntentStage, EnvelopeStage, ResourceStage, FastFinalizeStage>;

    using ConvergeStages = typename ConvergeStageBuilder<false, false>::Stages;
};

struct MainProfileSpec : ProfileSpecBase {
    MainProfileSpec() {
        kind = ProfileKind::Main;
        resourceMask = 0x7F;
        traceLevel = 1;
        observationWindowMs = 100;
        minResidencyMs = 50;
        steadyEnterMerges = 2;
        exitHysteresisMerges = 1;
        enableInheritance = true;
        enableStability = true;
    }

    using FastStages =
        TypeList<SceneStage, IntentStage, EnvelopeStage, ResourceStage, FastFinalizeStage>;

    using ConvergeStages = typename ConvergeStageBuilder<true, true>::Stages;
};

struct FlagshipProfileSpec : ProfileSpecBase {
    FlagshipProfileSpec() {
        kind = ProfileKind::Flagship;
        resourceMask = 0xFF;
        traceLevel = 2;
        observationWindowMs = 200;
        minResidencyMs = 100;
        steadyEnterMerges = 3;
        exitHysteresisMerges = 2;
        enableInheritance = true;
        enableStability = true;
    }

    using FastStages =
        TypeList<SceneStage, IntentStage, EnvelopeStage, ResourceStage, FastFinalizeStage>;

    using ConvergeStages = typename ConvergeStageBuilder<true, true>::Stages;
};

template <ProfileKind Kind>
struct SelectProfile;

template <>
struct SelectProfile<ProfileKind::Entry> {
    using Type = EntryProfileSpec;
};

template <>
struct SelectProfile<ProfileKind::Main> {
    using Type = MainProfileSpec;
};

template <>
struct SelectProfile<ProfileKind::Flagship> {
    using Type = FlagshipProfileSpec;
};

}    // namespace vendor::transsion::perfengine::dse
