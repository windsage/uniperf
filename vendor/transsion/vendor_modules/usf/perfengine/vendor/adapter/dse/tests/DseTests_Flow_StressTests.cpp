#include <atomic>
#include <cassert>
#include <chrono>
#include <iostream>
#include <random>
#include <thread>
#include <vector>

#include "dse/Dse.h"
#include "tests/DseTests_TestSupport.h"

using namespace vendor::transsion::perfengine::dse;
void StressTestStateVault() {
    std::cout << "Stress Testing StateVault (10000 iterations)..." << std::endl;

    StateVault vault;
    auto start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < 10000; ++i) {
        ConstraintSnapshot snapshot;
        snapshot.thermalLevel = i % 10;
        vault.UpdateConstraint(snapshot);

        auto view = vault.Snapshot();
        (void)view.GetGeneration();
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);

    std::cout << "  StateVault Stress: PASS (" << duration.count() << " us for 10000 ops)"
              << std::endl;
}

void StressTestStateVaultTransactions() {
    std::cout << "Stress Testing StateVault Transactions (10000 commits)..." << std::endl;

    StateVault vault;
    auto start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < 10000; ++i) {
        vault.BeginTransaction();

        ConstraintSnapshot constraint;
        constraint.thermalLevel = i % 6;
        vault.UpdateConstraint(constraint);

        SceneSemantic scene;
        scene.kind = (i % 2 == 0) ? SceneKind::Launch : SceneKind::Playback;
        scene.phase = (i % 2 == 0) ? ScenePhase::Enter : ScenePhase::Active;
        scene.visible = true;
        scene.continuousPerception = (i % 2) != 0;
        vault.UpdateScene(scene, static_cast<SceneId>(i + 1));

        IntentContract intent;
        intent.level = (i % 2 == 0) ? IntentLevel::P1 : IntentLevel::P2;
        vault.UpdateIntent(intent, static_cast<SessionId>(i + 1));

        TemporalContract lease;
        lease.leaseStartTs = i;
        lease.leaseEndTs = i + 1000;
        lease.timeMode = (i % 2 == 0) ? TimeMode::Burst : TimeMode::Steady;
        vault.UpdateLease(lease, static_cast<LeaseId>(i + 1));

        CapabilitySnapshot capability;
        capability.supportedResources = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
        capability.domains[static_cast<uint32_t>(ResourceDim::CpuCapacity)].maxCapacity = 1024;
        capability.actionPathFlags[static_cast<uint32_t>(ResourceDim::CpuCapacity)] = 0x3;
        vault.UpdateCapability(capability);

        assert(vault.CommitTransaction());
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);

    std::cout << "  StateVault Transaction Stress: PASS (" << duration.count()
              << " us for 10000 commits)" << std::endl;
}

void StressTestPinAndSnapshotHotPath() {
    std::cout << "Stress Testing PinAndSnapshot Hot Path (100000 iterations)..." << std::endl;

    StateVault vault;
    for (int i = 0; i < 32; ++i) {
        ConstraintSnapshot snapshot;
        snapshot.thermalLevel = i % 6;
        vault.UpdateConstraint(snapshot);
    }

    StateView view;
    auto start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < 100000; ++i) {
        const auto token = vault.PinAndSnapshot(0, view);
        assert(token.generation == view.GetGeneration());
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);

    std::cout << "  PinAndSnapshot Hot Path: PASS (" << duration.count()
              << " us for 100000 iterations)" << std::endl;
}

void StressTestTraceBuffer() {
    std::cout << "Stress Testing TraceBuffer (ring buffer overflow)..." << std::endl;

    TraceBuffer buffer;
    size_t overflowCount = 0;

    for (size_t i = 0; i < TraceBuffer::kBufferSize * 2; ++i) {
        TraceRecord record;
        record.timestamp = i;
        record.eventId = static_cast<uint32_t>(i);

        auto result = buffer.Write(record);
        if (result.overwritten) {
            overflowCount++;
        }
    }

    assert(overflowCount > 0);
    assert(buffer.GetCount() == TraceBuffer::kBufferSize);

    std::cout << "  TraceBuffer Stress: PASS (overwritten " << overflowCount << " records)"
              << std::endl;
}

void StressTestOrchestrator() {
    std::cout << "Stress Testing Orchestrator (1000 events)..." << std::endl;

    StateVault vault;
    Orchestrator<MainProfileSpec> orchestrator(vault);

    auto start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < 1000; ++i) {
        SchedEvent event;
        event.action = i % 10;
        event.rawFlags = i;
        event.sessionId = i;

        auto fastGrant = orchestrator.RunFast(event);
        (void)fastGrant.grantedMask;

        auto decision = orchestrator.RunConverge(event);
        (void)decision.grant.resourceMask;
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);

    std::cout << "  Orchestrator Stress: PASS (" << duration.count() << " ms for 1000 events)"
              << std::endl;
}

void StressTestReplayHashHotPath() {
    std::cout << "Stress Testing ReplayHash Hot Path (100000 iterations)..." << std::endl;

    TraceConfig config;
    config.level = 2;
    config.enableReplayHash = true;
    config.enableDecisionTrace = true;

    auto &recorder = TraceRecorder::Instance();
    recorder.Init(config);

    SchedEvent event;
    event.eventId = 1;
    event.sessionId = 42;
    event.sceneId = 7;
    event.pid = 1234;
    event.uid = 10001;
    event.timestamp = 1;
    event.source = 1;
    event.action = 1;
    event.rawFlags = 0x10;

    ConstraintSnapshot constraint;
    constraint.thermalLevel = 1;
    constraint.batteryLevel = 80;
    constraint.screenOn = true;

    CapabilityFeasible capability;
    capability.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    capability.feasible[ResourceDim::CpuCapacity] = 900;

    auto start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < 100000; ++i) {
        event.eventId = static_cast<uint32_t>(i + 1);
        event.timestamp = static_cast<int64_t>(i + 1);
        const uint64_t decisionHash = static_cast<uint64_t>(i + 1) * 2654435761ULL;
        ReplayHashInputs replayInputs;
        const ReplayHash hash = recorder.BuildReplayHash(
            decisionHash, event, constraint, capability, static_cast<Generation>(i + 1),
            static_cast<uint64_t>(i + 1), event.sessionId, 1, 1, IntentLevel::P2, &replayInputs);
        assert(recorder.RecordReplayHashChecked(hash, decisionHash, replayInputs, event));
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);

    std::cout << "  ReplayHash Hot Path Stress: PASS (" << duration.count()
              << " us for 100000 iterations)" << std::endl;
}

void StressTestExecutionFlowDedupHotPath() {
    std::cout << "Stress Testing ExecutionFlow Dedup Hot Path (100000 iterations)..." << std::endl;

    TraceConfig config;
    config.level = 0;
    TraceRecorder::Instance().Init(config);

    PolicyDecision decision;
    decision.sessionId = 901;
    decision.grant.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    decision.grant.delivered[ResourceDim::CpuCapacity] = 768;
    decision.request.resourceMask = decision.grant.resourceMask;
    decision.request.min[ResourceDim::CpuCapacity] = 512;
    decision.request.target[ResourceDim::CpuCapacity] = 768;
    decision.request.max[ResourceDim::CpuCapacity] = 896;
    decision.effectiveIntent = IntentLevel::P2;
    decision.temporal.timeMode = TimeMode::Steady;
    decision.admitted = true;
    decision.reasonBits = 0x20;

    CountingActionMap actionMap;

    ExecutionFlow<MainProfileSpec> noDedupFlow;
    CountingExecutor noDedupExecutor;
    auto startNoDedup = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < 100000; ++i) {
        auto result = noDedupFlow.Execute(decision, &actionMap, &noDedupExecutor, 0);
        assert(result.success);
    }
    auto endNoDedup = std::chrono::high_resolution_clock::now();

    ExecutionFlow<MainProfileSpec> dedupFlow;
    CountingExecutor dedupExecutor;
    auto startDedup = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < 100000; ++i) {
        auto result = dedupFlow.Execute(decision, &actionMap, &dedupExecutor, 60000);
        assert(result.success);
    }
    auto endDedup = std::chrono::high_resolution_clock::now();

    const auto noDedupUs =
        std::chrono::duration_cast<std::chrono::microseconds>(endNoDedup - startNoDedup).count();
    const auto dedupUs =
        std::chrono::duration_cast<std::chrono::microseconds>(endDedup - startDedup).count();

    assert(noDedupExecutor.executeCount == 100000);
    assert(dedupExecutor.executeCount == 1);

    std::cout << "  ExecutionFlow Dedup Hot Path: PASS (" << noDedupUs << " us -> " << dedupUs
              << " us, executor invokes " << noDedupExecutor.executeCount << " -> "
              << dedupExecutor.executeCount << ")" << std::endl;
}

void StressTestSceneRuleLookupHotPath() {
    std::cout << "Stress Testing SceneRule Lookup Hot Path (1000000 iterations)..." << std::endl;

    auto &loader = ConfigLoader::Instance();
    assert(loader.Load(nullptr));

    constexpr SceneKind kKinds[] = {SceneKind::Launch,    SceneKind::Transition, SceneKind::Scroll,
                                    SceneKind::Animation, SceneKind::Gaming,     SceneKind::Camera,
                                    SceneKind::Playback,  SceneKind::Download};

    uint32_t leaseSum = 0;
    auto start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < 1000000; ++i) {
        const SceneRule *rule =
            loader.FindSceneRuleByKind(kKinds[i % (sizeof(kKinds) / sizeof(kKinds[0]))]);
        assert(rule != nullptr);
        leaseSum += static_cast<uint32_t>(rule->defaultLeaseMs);
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);

    assert(leaseSum != 0);
    std::cout << "  SceneRule Lookup Hot Path: PASS (" << duration.count()
              << " us for 1000000 iterations)" << std::endl;
}

void RunStressTests() {
    std::cout << "\n=== Stress Tests ===\n" << std::endl;
    StressTestStateVault();
    StressTestStateVaultTransactions();
    StressTestPinAndSnapshotHotPath();
    StressTestTraceBuffer();
    StressTestOrchestrator();
    StressTestReplayHashHotPath();
    StressTestExecutionFlowDedupHotPath();
    StressTestSceneRuleLookupHotPath();
}
