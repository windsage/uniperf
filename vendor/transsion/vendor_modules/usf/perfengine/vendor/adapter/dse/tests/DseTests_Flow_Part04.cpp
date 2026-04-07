#include <algorithm>
#include <atomic>
#include <cassert>
#include <chrono>
#include <cstring>
#include <fstream>
#include <iostream>
#include <random>
#include <thread>
#include <type_traits>
#include <vector>

#include "dse/Dse.h"
#include "tests/DseTests_TestSupport.h"

using namespace vendor::transsion::perfengine::dse;

void TestResourceStage() {
    std::cout << "Testing ResourceStage..." << std::endl;

    ResourceStage stage;
    assert(stage.Name() != nullptr);

    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    SchedEvent event;
    event.pid = 99;
    event.tid = 123;
    event.timestamp = 1000000000ULL;
    event.rawFlags = ResourceStage::kRawFlagHasLockDependency;
    ctx.event = &event;

    SceneSemantic semantic;
    semantic.kind = SceneKind::Scroll;
    semantic.phase = ScenePhase::Enter;
    semantic.visible = true;
    ctx.SetSceneSemantic(semantic);

    IntentContract intent;
    intent.level = IntentLevel::P1;
    ctx.SetIntentContract(intent);

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(out.reasonBits == 0x00010000);
    assert(!ctx.intermediates->hasDependencyState);

    DependencyHintPayload hint;
    hint.holderSceneId = 88;
    hint.requesterSceneId = 99;
    hint.holderOriginalIntent = IntentLevel::P4;
    hint.kind = DependencyKind::Lock;
    event.hint = &hint;
    event.hintSize = sizeof(hint);

    StageIntermediates intermediatesWithHint;
    StageContext ctxWithHint;
    ctxWithHint.intermediates = &intermediatesWithHint;
    ctxWithHint.state = &view;
    ctxWithHint.event = &event;
    ctxWithHint.SetSceneSemantic(semantic);
    ctxWithHint.SetIntentContract(intent);

    out = stage.Execute(ctxWithHint);
    assert(out.success);
    assert(out.reasonBits == 0);
    assert(ctxWithHint.intermediates->hasDependencyState);
    assert(ctxWithHint.GetDependencyState().holderSceneId == 88);
    assert(ctxWithHint.GetDependencyState().requesterSceneId == 99);

    std::cout << "  ResourceStage: PASS" << std::endl;
}

void TestArbitrationStage() {
    std::cout << "Testing ArbitrationStage..." << std::endl;

    ArbitrationStage stage;
    assert(stage.Name() != nullptr);

    ConvergeStageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;
    ctx.convergeIntermediates = &intermediates;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    ResourceRequest request;
    request.resourceMask = 0xFF;
    request.min[ResourceDim::CpuCapacity] = 800;
    request.target[ResourceDim::CpuCapacity] = 800;
    ctx.SetResourceRequest(request);

    IntentContract intent;
    intent.level = IntentLevel::P1;
    ctx.SetIntentContract(intent);

    ConstraintAllowed allowed;
    allowed.allowed[ResourceDim::CpuCapacity] = 600;
    ctx.SetConstraintAllowed(allowed);

    CapabilityFeasible feasible;
    feasible.feasible[ResourceDim::CpuCapacity] = 1024;
    ctx.SetCapabilityFeasible(feasible);

    auto out = stage.Execute(ctx);
    assert(out.success);

    const auto &result = ctx.GetArbitrationResult();
    assert(result.grantedMask != 0);
    auto layers = ComputeArbitrationLayers(request, allowed, feasible, intent.level);
    assert(layers.delivered[ResourceDim::CpuCapacity] == 600);

    std::cout << "  ArbitrationStage: PASS" << std::endl;
}

void TestArbitrationSinglePassMatchesLegacy() {
    std::cout << "Testing Arbitration Single-Pass Matches Legacy..." << std::endl;

    ResourceRequest request;
    request.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) |
                           (1u << static_cast<uint32_t>(ResourceDim::GpuCapacity));
    request.min[ResourceDim::CpuCapacity] = 700;
    request.target[ResourceDim::CpuCapacity] = 820;
    request.max[ResourceDim::CpuCapacity] = 900;
    request.min[ResourceDim::GpuCapacity] = 256;
    request.target[ResourceDim::GpuCapacity] = 640;
    request.max[ResourceDim::GpuCapacity] = 768;

    ConstraintAllowed allowed;
    allowed.allowed[ResourceDim::CpuCapacity] = 768;
    allowed.allowed[ResourceDim::GpuCapacity] = 512;

    CapabilityFeasible feasible;
    feasible.feasible[ResourceDim::CpuCapacity] = 1024;
    feasible.feasible[ResourceDim::GpuCapacity] = 700;

    ResourceRule cpuRule;
    cpuRule.min = 750;
    ResourceRule gpuRule;
    gpuRule.min = 400;

    const auto legacyLayers = ComputeArbitrationLayers(request, allowed, feasible, IntentLevel::P2);
    const auto legacyGrant =
        BuildGrantFromDelivered(legacyLayers.delivered, legacyLayers.reasonBits,
                                [&cpuRule, &gpuRule](ResourceDim dim) -> const ResourceRule * {
                                    switch (dim) {
                                        case ResourceDim::CpuCapacity:
                                            return &cpuRule;
                                        case ResourceDim::GpuCapacity:
                                            return &gpuRule;
                                        default:
                                            return nullptr;
                                    }
                                });

    const auto singlePassLayers =
        ComputeArbitrationLayers(request, allowed, feasible, IntentLevel::P2,
                                 [&cpuRule, &gpuRule](ResourceDim dim) -> const ResourceRule * {
                                     switch (dim) {
                                         case ResourceDim::CpuCapacity:
                                             return &cpuRule;
                                         case ResourceDim::GpuCapacity:
                                             return &gpuRule;
                                         default:
                                             return nullptr;
                                     }
                                 });

    assert(singlePassLayers.delivered == legacyLayers.delivered);
    assert(singlePassLayers.reasonBits == legacyLayers.reasonBits);
    assert(singlePassLayers.grantedMask == legacyGrant.grantedMask);
    assert(singlePassLayers.ruleReasonBits == legacyGrant.ruleReasonBits);

    std::cout << "  Arbitration Single-Pass Matches Legacy: PASS" << std::endl;
}

void TestInheritanceStage() {
    std::cout << "Testing InheritanceStage..." << std::endl;

    InheritanceStage stage;
    assert(stage.Name() != nullptr);

    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;
    ctx.profileResourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) |
                              (1u << static_cast<uint32_t>(ResourceDim::MemoryCapacity));

    StateVault vault;
    DependencyState dep;
    dep.active = true;
    dep.holderSceneId = 88;
    dep.requesterSceneId = 99;
    dep.holderOriginalIntent = IntentLevel::P4;
    dep.inheritedIntent = IntentLevel::P1;
    dep.inheritStartTimeMs = 1000;
    dep.inheritExpireTimeMs = 3000;
    dep.depth = 1;
    dep.kind = DependencyKind::Lock;
    vault.UpdateDependency(dep);
    auto view = vault.Snapshot();
    ctx.state = &view;

    SchedEvent event;
    event.sceneId = 88;
    event.pid = 188;
    event.timestamp = 1500000000ULL;
    ctx.event = &event;

    SceneSemantic semantic;
    semantic.kind = SceneKind::Background;
    semantic.phase = ScenePhase::Active;
    ctx.SetSceneSemantic(semantic);

    IntentContract intent;
    intent.level = IntentLevel::P4;
    ctx.SetIntentContract(intent);

    TemporalContract temporal;
    temporal.timeMode = TimeMode::Deferred;
    ctx.SetTemporalContract(temporal);

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P1);
    assert(ctx.GetTemporalContract().timeMode == TimeMode::Burst);
    assert(ctx.GetResourceRequest().resourceMask == ctx.profileResourceMask);

    // Expired inheritance should revoke dependency active state.
    DependencyState depExpired = dep;
    depExpired.inheritStartTimeMs = 100;
    depExpired.inheritExpireTimeMs = 200;
    vault.UpdateDependency(depExpired);
    auto viewExpired = vault.Snapshot();
    ctx.state = &viewExpired;
    event.timestamp = 1000000000ULL;    // 1000ms, beyond expire
    ctx.event = &event;
    ctx.SetIntentContract(intent);
    auto outExpired = stage.Execute(ctx);
    assert(outExpired.success);
    assert(ctx.GetDependencyState().active == false);

    std::cout << "  InheritanceStage: PASS" << std::endl;
}

void TestInheritanceStageSceneIdCache() {
    std::cout << "Testing InheritanceStage SceneId Cache..." << std::endl;

    InheritanceStage stage;
    assert(stage.Name() != nullptr);

    const int32_t uid = 1234;
    const char *package = "com.example.test";
    const char *activity = "MainActivity";
    const SceneId expectedSceneId = ComputeSyntheticSceneId(uid, package, activity);
    assert(expectedSceneId != 0);

    auto runCase = [&](bool cacheEnabled, SceneId cachedSceneId) {
        StageIntermediates intermediates;
        StageContext ctx;
        ctx.intermediates = &intermediates;
        ctx.profileResourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) |
                                  (1u << static_cast<uint32_t>(ResourceDim::MemoryCapacity));

        StateVault vault;
        DependencyState dep;
        dep.active = true;
        dep.holderSceneId = expectedSceneId;
        dep.requesterSceneId = 99;
        dep.holderOriginalIntent = IntentLevel::P4;
        dep.inheritedIntent = IntentLevel::P1;
        dep.inheritStartTimeMs = 1000;
        dep.inheritExpireTimeMs = 3000;
        dep.depth = 1;
        dep.kind = DependencyKind::Lock;
        vault.UpdateDependency(dep);
        auto view = vault.Snapshot();
        ctx.state = &view;

        SchedEvent event;
        event.sceneId = 0;    // Force synthetic scene id.
        event.uid = uid;
        std::strncpy(event.package, package, sizeof(event.package) - 1);
        std::strncpy(event.activity, activity, sizeof(event.activity) - 1);
        event.pid = 188;
        event.timestamp = 1500000000ULL;
        ctx.event = &event;

        SceneSemantic semantic;
        semantic.kind = SceneKind::Background;
        semantic.phase = ScenePhase::Active;
        ctx.SetSceneSemantic(semantic);

        IntentContract intent;
        intent.level = IntentLevel::P4;
        ctx.SetIntentContract(intent);

        TemporalContract temporal;
        temporal.timeMode = TimeMode::Deferred;
        ctx.SetTemporalContract(temporal);

        if (cacheEnabled) {
            ctx.hasResolvedSceneId = true;
            ctx.resolvedSceneId = cachedSceneId;
        }

        auto out = stage.Execute(ctx);
        assert(out.success);

        const bool matched = (cachedSceneId == expectedSceneId);
        if (!cacheEnabled) {
            // cache disabled: stage should compute synthetic scene id from event.
            assert(ctx.GetIntentContract().level == IntentLevel::P1);
        } else if (matched) {
            assert(ctx.GetIntentContract().level == IntentLevel::P1);
        } else {
            // cached scene id mismatch => requester triggers dependency update,
            // but holder-specific inheritance should not be applied.
            assert(ctx.GetIntentContract().level == IntentLevel::P4);
        }
    };

    runCase(false, 0);
    runCase(true, expectedSceneId);
    runCase(true, static_cast<SceneId>(expectedSceneId + 1));

    std::cout << "  InheritanceStage SceneId Cache: PASS" << std::endl;
}

void TestInheritanceStageSkipsNoopReapply() {
    std::cout << "Testing InheritanceStage Skips Noop Reapply..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);

    InheritanceStage stage;
    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;
    ctx.profileResourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) |
                              (1u << static_cast<uint32_t>(ResourceDim::MemoryCapacity));

    StateVault vault;
    DependencyState dep;
    dep.active = true;
    dep.holderSceneId = 88;
    dep.requesterSceneId = 99;
    dep.holderOriginalIntent = IntentLevel::P4;
    dep.inheritedIntent = IntentLevel::P1;
    dep.inheritStartTimeMs = 1000;
    dep.inheritExpireTimeMs = 5000;
    dep.depth = 1;
    dep.kind = DependencyKind::Lock;
    vault.UpdateDependency(dep);
    auto view = vault.Snapshot();
    ctx.state = &view;

    SchedEvent event;
    event.sceneId = 88;
    event.timestamp = 1500000000ULL;
    ctx.event = &event;

    SceneSemantic semantic;
    semantic.kind = SceneKind::Background;
    semantic.phase = ScenePhase::Active;
    ctx.SetSceneSemantic(semantic);

    IntentContract intent;
    intent.level = IntentLevel::P1;
    ctx.SetIntentContract(intent);

    TemporalContract temporal;
    temporal.timeMode = TimeMode::Burst;
    temporal.leaseStartTs = 1000000000LL;
    temporal.leaseEndTs = 2500000000LL;
    ctx.SetTemporalContract(temporal);

    auto &loader = ConfigLoader::Instance();
    ctx.SetResourceRequest(
        IntentStage::DeriveResourceRequest(semantic, intent, ctx.profileResourceMask, loader));

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P1);
    assert(ctx.GetTemporalContract().timeMode == TimeMode::Burst);
    const ResourceRequest expected =
        IntentStage::DeriveResourceRequest(semantic, intent, ctx.profileResourceMask, loader);
    assert(ResourceRequestEquals(ctx.GetResourceRequest(), expected));

    std::cout << "  InheritanceStage Skips Noop Reapply: PASS" << std::endl;
}

void TestStabilityStage() {
    std::cout << "Testing StabilityStage..." << std::endl;

    StabilityStage stage;
    assert(stage.Name() != nullptr);

    ConvergeStageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;
    ctx.convergeIntermediates = &intermediates;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    auto out = stage.Execute(ctx);
    assert(out.success);

    std::cout << "  StabilityStage: PASS" << std::endl;
}

void TestStabilityStageHoldsPreviousContract() {
    std::cout << "Testing StabilityStage Holds Previous Contract..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);

    StateVault vault;
    SceneSemantic committedScene;
    committedScene.kind = SceneKind::Playback;
    committedScene.phase = ScenePhase::Active;
    committedScene.visible = false;
    committedScene.audible = true;
    committedScene.continuousPerception = true;
    vault.UpdateScene(committedScene, 100);

    IntentContract committedIntent;
    committedIntent.level = IntentLevel::P2;
    vault.UpdateIntent(committedIntent, 1000);

    TemporalContract committedLease;
    committedLease.timeMode = TimeMode::Steady;
    committedLease.leaseStartTs = 1000000000LL;
    committedLease.leaseEndTs = 61000000000LL;
    vault.UpdateLease(committedLease, 1000);

    StabilityState stabilityState;
    stabilityState.inSteady = true;
    stabilityState.lastUpdateMs = 1000;
    stabilityState.steadyEnterTimeMs = 1000;
    stabilityState.observationWindowMs = 100;
    stabilityState.minResidencyMs = 50;
    vault.UpdateStability(stabilityState);

    auto view = vault.Snapshot();

    ConvergeStageIntermediates intermediates;
    StageContext ctx;
    ctx.state = &view;
    ctx.intermediates = &intermediates;
    ctx.convergeIntermediates = &intermediates;
    ctx.profileResourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) |
                              (1u << static_cast<uint32_t>(ResourceDim::MemoryCapacity));

    SchedEvent event;
    event.timestamp = 1010000000ULL;
    ctx.event = &event;

    SceneSemantic candidateScene;
    candidateScene.kind = SceneKind::Background;
    candidateScene.phase = ScenePhase::Active;
    ctx.SetSceneSemantic(candidateScene);

    IntentContract candidateIntent;
    candidateIntent.level = IntentLevel::P4;
    ctx.SetIntentContract(candidateIntent);

    StabilityStage stage;
    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P2);
    assert(ctx.GetTemporalContract().timeMode == TimeMode::Steady);
    assert(ctx.GetResourceRequest().resourceMask == ctx.profileResourceMask);

    std::cout << "  StabilityStage Holds Previous Contract: PASS" << std::endl;
}

void TestStabilityStageP1ExitUsesObservation() {
    std::cout << "Testing StabilityStage P1 Exit Uses Observation..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);

    StateVault vault;
    SceneSemantic committedScene;
    committedScene.kind = SceneKind::Scroll;
    committedScene.phase = ScenePhase::Enter;
    committedScene.visible = true;
    vault.UpdateScene(committedScene, 88);

    IntentContract committedIntent;
    committedIntent.level = IntentLevel::P1;
    vault.UpdateIntent(committedIntent, 1000);

    TemporalContract committedLease;
    committedLease.timeMode = TimeMode::Burst;
    committedLease.leaseStartTs = 1000000000LL;
    committedLease.leaseEndTs = 1800000000LL;
    vault.UpdateLease(committedLease, 1000);

    auto view = vault.Snapshot();

    ConvergeStageIntermediates intermediates;
    StageContext ctx;
    ctx.state = &view;
    ctx.intermediates = &intermediates;
    ctx.convergeIntermediates = &intermediates;

    SchedEvent event;
    event.timestamp = 1010000000ULL;
    ctx.event = &event;

    SceneSemantic candidateScene;
    candidateScene.kind = SceneKind::Background;
    candidateScene.phase = ScenePhase::Active;
    ctx.SetSceneSemantic(candidateScene);

    IntentContract candidateIntent;
    candidateIntent.level = IntentLevel::P4;
    ctx.SetIntentContract(candidateIntent);

    StabilityStage stage;
    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P1);
    assert(ctx.GetTemporalContract().timeMode == TimeMode::Burst);

    std::cout << "  StabilityStage P1 Exit Uses Observation: PASS" << std::endl;
}
