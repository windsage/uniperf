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

void TestStabilityStageSemanticMigrationUsesObservation() {
    std::cout << "Testing StabilityStage Semantic Migration Uses Observation..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);

    StateVault vault;
    SceneSemantic committedScene;
    committedScene.kind = SceneKind::Playback;
    committedScene.phase = ScenePhase::Active;
    committedScene.visible = false;
    committedScene.audible = true;
    committedScene.continuousPerception = true;
    vault.UpdateScene(committedScene, 200);

    IntentContract committedIntent;
    committedIntent.level = IntentLevel::P2;
    vault.UpdateIntent(committedIntent, 2000);

    TemporalContract committedLease;
    committedLease.timeMode = TimeMode::Steady;
    committedLease.leaseStartTs = 1000000000LL;
    committedLease.leaseEndTs = 61000000000LL;
    vault.UpdateLease(committedLease, 2000);

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

    SchedEvent event;
    event.timestamp = 1010000000ULL;
    ctx.event = &event;

    SceneSemantic candidateScene;
    candidateScene.kind = SceneKind::Playback;
    candidateScene.phase = ScenePhase::Active;
    candidateScene.visible = true;
    candidateScene.audible = false;
    candidateScene.continuousPerception = false;
    ctx.SetSceneSemantic(candidateScene);

    IntentContract candidateIntent;
    candidateIntent.level = IntentLevel::P2;
    ctx.SetIntentContract(candidateIntent);

    StabilityStage stage;
    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetSceneSemantic().visible == committedScene.visible);
    assert(ctx.GetSceneSemantic().audible == committedScene.audible);

    std::cout << "  StabilityStage Semantic Migration Uses Observation: PASS" << std::endl;
}

void TestStabilityStageHoldSkipsResourceDeriveWhenRequestUnchanged() {
    std::cout << "Testing StabilityStage Hold Skips Resource Derive When Unchanged..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);

    StateVault vault;

    // committed：Playback/visible=true/intent level=P2
    SceneSemantic committedScene;
    committedScene.kind = SceneKind::Playback;
    committedScene.phase = ScenePhase::Active;
    committedScene.visible = true;
    committedScene.audible = false;
    committedScene.continuousPerception = false;
    vault.UpdateScene(committedScene, 1000);

    IntentContract committedIntent;
    committedIntent.level = IntentLevel::P2;
    vault.UpdateIntent(committedIntent, 2000);

    TemporalContract committedLease;
    committedLease.timeMode = TimeMode::Steady;
    committedLease.leaseStartTs = 1000000000LL;
    committedLease.leaseEndTs = 61000000000LL;
    vault.UpdateLease(committedLease, 2000);

    // 制造 throttled：让 StabilityMechanism 在候选变化时直接 hold，
    // 从而触发 StabilityStage::shouldHold 路径。
    StabilityState stabilityState;
    stabilityState.inSteady = true;
    stabilityState.lastUpdateMs = 1000;
    stabilityState.steadyEnterTimeMs = 1000;
    stabilityState.observationWindowMs = 100;
    stabilityState.minResidencyMs = 50;
    stabilityState.steadyEnterMerges = 2;
    stabilityState.exitHysteresisMerges = 1;
    stabilityState.totalMergeCount = 1;
    stabilityState.stableMergeCount = 1;
    stabilityState.exitMergeCount = 0;
    stabilityState.inObservation = false;
    stabilityState.pendingExit = false;
    vault.UpdateStability(stabilityState);

    auto view = vault.Snapshot();

    ConvergeStageIntermediates intermediates;
    StageContext ctx;
    ctx.state = &view;
    ctx.intermediates = &intermediates;
    ctx.convergeIntermediates = &intermediates;

    ctx.profileResourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) |
                              (1u << static_cast<uint32_t>(ResourceDim::MemoryCapacity));

    // candidate：只改变语义中不影响 ResourceRequest 的字段（phase/audible/continuousPerception）。
    // 对于 ResourceRequest 的推导逻辑，FillResourceRequest 仅读取：
    // - intent.level
    // - semantic.kind
    // - 以及 Playback 场景的 semantic.visible
    // 因此当这些“决定 request 的要素”与 committed 保持一致时，shouldHold 分支应跳过
    // DeriveResourceRequest 重算。
    SceneSemantic candidateScene = committedScene;
    candidateScene.phase = ScenePhase::Exit;
    candidateScene.audible = true;
    candidateScene.continuousPerception = true;
    ctx.SetSceneSemantic(candidateScene);

    IntentContract candidateIntent = committedIntent;    // level 不变 => request base 不变
    ctx.SetIntentContract(candidateIntent);

    TemporalContract candidateLease;
    candidateLease.timeMode = TimeMode::Burst;
    candidateLease.leaseStartTs = 0;
    candidateLease.leaseEndTs = 0;
    ctx.SetTemporalContract(candidateLease);

    auto &loader = ConfigLoader::Instance();
    const auto &params = loader.GetParams();
    const ResourceRequest candidateReq = IntentStage::DeriveResourceRequest(
        candidateScene, candidateIntent, ctx.profileResourceMask, loader, params);
    ctx.SetResourceRequest(candidateReq);

    SchedEvent event;
    event.timestamp =
        (stabilityState.lastUpdateMs + 1) * 1000000LL;    // delta=1ms < updateThrottleMs
    ctx.event = &event;

    StabilityStage stage;
    const auto out = stage.Execute(ctx);
    assert(out.success);

    // 语义约束：shouldHold 后 scene/intent/lease 应被回退到 committed
    assert(ctx.GetSceneSemantic().kind == committedScene.kind);
    assert(ctx.GetSceneSemantic().phase == ScenePhase::Active);
    assert(ctx.GetSceneSemantic().visible == committedScene.visible);
    assert(ctx.GetIntentContract().level == committedIntent.level);
    assert(ctx.GetTemporalContract().timeMode == committedLease.timeMode);

    // ResourceRequest：因 kind/visible/intent.level 未变，应保持不变
    const ResourceRequest expectedPrevReq = IntentStage::DeriveResourceRequest(
        committedScene, committedIntent, ctx.profileResourceMask, loader, params);
    assert(ResourceRequestEquals(ctx.GetResourceRequest(), candidateReq));
    assert(ResourceRequestEquals(ctx.GetResourceRequest(), expectedPrevReq));

    std::cout << "  StabilityStage Hold Skips Resource Derive When Unchanged: PASS" << std::endl;
}

void TestConvergeFinalizeStage() {
    std::cout << "Testing ConvergeFinalizeStage..." << std::endl;

    ConvergeFinalizeStage stage;
    assert(stage.Name() != nullptr);

    ConvergeStageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;
    ctx.convergeIntermediates = &intermediates;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    SchedEvent event{};
    event.eventId = 9001;
    event.sessionId = 9001;
    event.timestamp = 5000000000LL;
    ctx.event = &event;
    ctx.generation = view.GetGeneration();
    ctx.snapshotToken = 0xABCD1234u;
    ctx.profileId = static_cast<uint32_t>(MainProfileSpec{}.kind) + 1;
    ctx.ruleVersion = 7;

    ResourceRequest request;
    request.resourceMask = 0xFF;
    request.min[ResourceDim::CpuCapacity] = 400;
    request.target[ResourceDim::CpuCapacity] = 400;
    request.max[ResourceDim::CpuCapacity] = 500;
    ctx.SetResourceRequest(request);

    IntentContract intent;
    intent.level = IntentLevel::P2;
    ctx.SetIntentContract(intent);

    TemporalContract temporal;
    temporal.timeMode = TimeMode::Steady;
    temporal.leaseStartTs = event.timestamp;
    temporal.leaseEndTs = event.timestamp + 60000000000LL;
    ctx.SetTemporalContract(temporal);

    ConstraintAllowed allowed;
    allowed.allowed[ResourceDim::CpuCapacity] = 400;
    ctx.SetConstraintAllowed(allowed);

    CapabilityFeasible feasible;
    feasible.feasible[ResourceDim::CpuCapacity] = 1024;
    ctx.SetCapabilityFeasible(feasible);

    ArbitrationResult arbitration;
    arbitration.grantedMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    arbitration.delivered[ResourceDim::CpuCapacity] = 400;
    arbitration.reasonBits = 0;
    ctx.SetArbitrationResult(arbitration);

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(intermediates.hasDecision);

    const PolicyDecision &decision = ctx.GetDecision();
    assert(decision.executionSummary == ComputeExecutionSummary(decision));
    assert(decision.IsDedupCandidate());
    assert(decision.executionSignature == ComputeExecutionSignature(decision));
    assert(decision.meta.profileId == ctx.profileId);
    assert(decision.sessionId == event.sessionId);

    std::cout << "  ConvergeFinalizeStage: PASS" << std::endl;
}

void TestActionCompilerContractSemantics() {
    std::cout << "Testing ActionCompiler Contract Semantics..." << std::endl;

    ActionCompiler<MainProfileSpec> compiler;

    PolicyDecision p1Decision;
    p1Decision.admitted = true;
    p1Decision.effectiveIntent = IntentLevel::P1;
    p1Decision.temporal.timeMode = TimeMode::Burst;
    p1Decision.grant.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    p1Decision.grant.delivered[ResourceDim::CpuCapacity] = 640;
    p1Decision.request.resourceMask = p1Decision.grant.resourceMask;
    p1Decision.request.min[ResourceDim::CpuCapacity] = 768;
    p1Decision.request.target[ResourceDim::CpuCapacity] = 900;
    p1Decision.request.max[ResourceDim::CpuCapacity] = 1024;

    auto batch = compiler.Compile(p1Decision);
    assert(batch.actionCount == 1);
    assert(DecodeActionContractMode(batch.actions[0].flags) == ActionContractMode::Floor);
    assert(DecodeActionIntentLevel(batch.actions[0].flags) == IntentLevel::P1);
    assert(DecodeActionTimeMode(batch.actions[0].flags) == TimeMode::Burst);
    assert(batch.actions[0].value == 640);

    PolicyDecision p4Decision;
    p4Decision.admitted = true;
    p4Decision.effectiveIntent = IntentLevel::P4;
    p4Decision.temporal.timeMode = TimeMode::Deferred;
    p4Decision.grant.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    p4Decision.grant.delivered[ResourceDim::CpuCapacity] = 128;
    p4Decision.request.resourceMask = p4Decision.grant.resourceMask;
    p4Decision.request.min[ResourceDim::CpuCapacity] = 0;
    p4Decision.request.target[ResourceDim::CpuCapacity] = 64;
    p4Decision.request.max[ResourceDim::CpuCapacity] = 192;

    batch = compiler.Compile(p4Decision);
    assert(batch.actionCount == 1);
    assert(DecodeActionContractMode(batch.actions[0].flags) == ActionContractMode::Cap);
    assert(DecodeActionIntentLevel(batch.actions[0].flags) == IntentLevel::P4);
    assert(DecodeActionTimeMode(batch.actions[0].flags) == TimeMode::Deferred);
    assert(batch.actions[0].value == 128);
    assert(AbstractActionBatch::kMaxActions == kResourceDimCount);

    std::cout << "  ActionCompiler Contract Semantics: PASS" << std::endl;
}

void TestFastFinalizeStage() {
    std::cout << "Testing FastFinalizeStage..." << std::endl;

    FastFinalizeStage stage;
    assert(stage.Name() != nullptr);

    FastStageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;
    ctx.fastIntermediates = &intermediates;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    IntentContract intent;
    intent.level = IntentLevel::P1;
    ctx.SetIntentContract(intent);

    auto out = stage.Execute(ctx);
    assert(out.success);

    std::cout << "  FastFinalizeStage: PASS" << std::endl;
}

void TestGenerationPinning() {
    std::cout << "Testing GenerationPinning (Same-Generation Snapshot)..." << std::endl;

    StateVault vault;

    ConstraintSnapshot snapshot;
    snapshot.thermalLevel = 2;
    vault.UpdateConstraint(snapshot);

    Generation gen1 = vault.GetGeneration();
    assert(gen1 > 0);

    auto view1 = vault.Snapshot();
    Generation viewGen1 = view1.GetGeneration();

    snapshot.thermalLevel = 3;
    vault.UpdateConstraint(snapshot);

    Generation gen2 = vault.GetGeneration();
    assert(gen2 > gen1);

    auto view2 = vault.Snapshot();
    Generation viewGen2 = view2.GetGeneration();

    assert(viewGen2 > viewGen1);

    std::cout << "  GenerationPinning: PASS" << std::endl;
}

void TestAtomicCommit() {
    std::cout << "Testing Atomic Commit (CAS)..." << std::endl;

    StateVault vault;

    Generation expected = vault.GetGeneration();

    bool success = vault.BeginTransaction(expected);
    assert(success);
    vault.RollbackTransaction();

    Generation stale = expected;
    vault.BeginTransaction();
    SceneSemantic semantic;
    semantic.kind = SceneKind::Launch;
    vault.UpdateScene(semantic, 1);
    vault.CommitTransaction();

    Generation newGen = vault.GetGeneration();
    assert(newGen > expected);

    success = vault.BeginTransaction(stale);
    assert(!success);
    assert(vault.GetGeneration() == newGen);

    std::cout << "  Atomic Commit: PASS" << std::endl;
}

void TestTransactionIntentUpdatePreservesSessionBinding() {
    std::cout << "Testing Transaction Intent Update Preserves Session Binding..." << std::endl;

    StateVault vault;

    IntentContract initial;
    initial.level = IntentLevel::P2;
    vault.UpdateIntent(initial, 42);

    vault.BeginTransaction();
    IntentContract updated;
    updated.level = IntentLevel::P1;
    vault.UpdateIntent(updated);
    assert(vault.CommitTransaction());

    auto view = vault.Snapshot();
    assert(view.Intent().contract.level == IntentLevel::P1);
    assert(view.Intent().activeSessionId == 42);

    std::cout << "  Transaction Intent Update Preserves Session Binding: PASS" << std::endl;
}

void TestTransactionReleaseSessionUsesCurrentState() {
    std::cout << "Testing Transaction Release Session Uses Current State..." << std::endl;

    StateVault vault;

    IntentContract intent;
    intent.level = IntentLevel::P2;
    vault.UpdateIntent(intent, 77);

    TemporalContract lease;
    lease.leaseStartTs = 1;
    lease.leaseEndTs = 100;
    lease.timeMode = TimeMode::Steady;
    vault.UpdateLease(lease, 77);

    vault.BeginTransaction();
    vault.ReleaseSession(77);
    assert(vault.CommitTransaction());

    auto view = vault.Snapshot();
    assert(view.Intent().activeSessionId == 0);
    assert(view.Lease().activeLeaseId == 0);

    std::cout << "  Transaction Release Session Uses Current State: PASS" << std::endl;
}

void TestPinAndSnapshotOptimisticPathConsistency() {
    std::cout << "Testing PinAndSnapshot Optimistic Path Consistency..." << std::endl;

    StateVault vault;
    std::atomic<bool> stop{false};
    std::atomic<int> mismatches{0};

    std::thread writer([&vault, &stop]() {
        int level = 0;
        while (!stop.load(std::memory_order_relaxed)) {
            ConstraintSnapshot snapshot;
            snapshot.thermalLevel = static_cast<uint32_t>(level % 6);
            vault.UpdateConstraint(snapshot);
            ++level;
        }
    });

    StateView view;
    for (int i = 0; i < 20000; ++i) {
        const auto token = vault.PinAndSnapshot(0, view);
        if (token.generation != view.GetGeneration()) {
            mismatches.fetch_add(1, std::memory_order_relaxed);
        }
    }

    stop.store(true, std::memory_order_relaxed);
    writer.join();

    assert(mismatches.load(std::memory_order_relaxed) == 0);
    std::cout << "  PinAndSnapshot Optimistic Path Consistency: PASS" << std::endl;
}
