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

void TestPinAndSnapshotHistoryReplayPath() {
    std::cout << "Testing PinAndSnapshot History Replay Path..." << std::endl;

    StateVault vault;
    StateView view;

    ConstraintSnapshot s1;
    s1.thermalLevel = 1;
    vault.UpdateConstraint(s1);
    const auto t1 = vault.PinAndSnapshot(0, view);
    const auto g1 = t1.generation;

    std::this_thread::sleep_for(std::chrono::microseconds(1));

    ConstraintSnapshot s2;
    s2.thermalLevel = 4;
    vault.UpdateConstraint(s2);
    const auto t2 = vault.PinAndSnapshot(0, view);
    const auto g2 = t2.generation;
    assert(g2 >= g1);

    // deterministicTs 早于最新提交时间，应该走历史回放分支。
    const auto replayToken = vault.PinAndSnapshot(t1.snapshotTs, view);
    // 语义验证：回放后的约束应与早期快照一致，而不是最新值。
    assert(view.Constraint().snapshot.thermalLevel == s1.thermalLevel);
    assert(replayToken.snapshotTs <= t2.snapshotTs);

    std::cout << "  PinAndSnapshot History Replay Path: PASS" << std::endl;
}

void TestSnapshotMatchesPinAndSnapshotCommittedView() {
    std::cout << "Testing Snapshot matches PinAndSnapshot(0)..." << std::endl;

    StateVault vault;
    ConstraintSnapshot snap;
    snap.thermalLevel = 3;
    vault.UpdateConstraint(snap);

    StateView viaSnapshot = vault.Snapshot();
    StateView viaPin;
    const auto token = vault.PinAndSnapshot(0, viaPin);

    assert(viaSnapshot.Constraint().snapshot.thermalLevel ==
           viaPin.Constraint().snapshot.thermalLevel);
    assert(token.snapshotTs >= 0);

    std::cout << "  Snapshot/PinAndSnapshot view parity: PASS" << std::endl;
}

void TestGetCommittedTokenPairsWithSnapshot() {
    std::cout << "Testing GetCommittedToken pairs with Snapshot..." << std::endl;

    StateVault vault;
    for (int i = 1; i <= 20; ++i) {
        ConstraintSnapshot s;
        s.thermalLevel = static_cast<uint32_t>(i % 7);
        vault.UpdateConstraint(s);
        const auto tok = vault.GetCommittedToken();
        auto view = vault.Snapshot();
        assert(tok.generation == view.GetGeneration());
    }

    std::cout << "  GetCommittedToken vs Snapshot: PASS" << std::endl;
}

void TestStateVaultEqualsSuppression() {
    std::cout << "Testing StateVault Equals Suppression..." << std::endl;

    StateVault vault;

    ConstraintSnapshot c1;
    c1.thermalLevel = 3;
    c1.thermalScaleQ10 = 777;
    c1.batteryLevel = 40;
    c1.batteryScaleQ10 = 888;
    c1.memoryPressure = 2;
    c1.psiLevel = 1;
    c1.psiScaleQ10 = 333;
    c1.powerState = PowerState::Doze;
    c1.batterySaver = true;
    c1.screenOn = false;
    c1.thermalSevere = true;

    const Generation g0 = vault.GetGeneration();
    vault.UpdateConstraint(c1);
    const Generation g1 = vault.GetGeneration();
    assert(g1 == g0 + 1);

    vault.UpdateConstraint(c1);
    const Generation g2 = vault.GetGeneration();
    assert(g2 == g1);    // no-op: identical constraint snapshot

    CapabilitySnapshot cap1;
    cap1.supportedResources = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    cap1.capabilityFlags = 0x12;
    cap1.domains[static_cast<size_t>(ResourceDim::CpuCapacity)].maxCapacity = 123;
    cap1.domains[static_cast<size_t>(ResourceDim::CpuCapacity)].flags = 9;
    cap1.actionPathFlags[static_cast<size_t>(ResourceDim::CpuCapacity)] = 7;

    vault.UpdateCapability(cap1);
    const Generation g3 = vault.GetGeneration();
    vault.UpdateCapability(cap1);
    const Generation g4 = vault.GetGeneration();
    assert(g4 == g3);    // no-op: identical capability snapshot

    // Both (constraint, capability) identical => full no-op.
    vault.UpdateConstraintAndCapability(c1, cap1);
    const Generation g5 = vault.GetGeneration();
    vault.UpdateConstraintAndCapability(c1, cap1);
    const Generation g6 = vault.GetGeneration();
    assert(g6 == g5);

    std::cout << "  StateVault Equals Suppression: PASS" << std::endl;
}

namespace {
struct FixedConstraintProvider final : ConstraintSnapshotProvider {
    ConstraintSnapshot snap;
    explicit FixedConstraintProvider(const ConstraintSnapshot &s) : snap(s) {}
    ConstraintSnapshot Read() override { return snap; }
};

struct FixedCapabilityProvider final : CapabilityProvider {
    CapabilitySnapshot snap;
    explicit FixedCapabilityProvider(const CapabilitySnapshot &s) : snap(s) {}
    CapabilitySnapshot Read() override { return snap; }
};
}    // namespace

void TestResourceScheduleServiceStartNoDoubleSeed() {
    std::cout << "Testing ResourceScheduleService Start No-Double-Seed..." << std::endl;

    ResourceScheduleService<MainProfileSpec> service;

    ConstraintSnapshot c;
    c.thermalLevel = 7;
    c.thermalScaleQ10 = 1023;
    c.batteryLevel = 10;
    c.batteryScaleQ10 = 1001;
    c.memoryPressure = 1;
    c.psiLevel = 2;
    c.psiScaleQ10 = 222;
    c.powerState = PowerState::Active;
    c.batterySaver = false;
    c.screenOn = true;
    c.thermalSevere = false;

    CapabilitySnapshot cap;
    cap.supportedResources = (1u << static_cast<uint32_t>(ResourceDim::GpuCapacity));
    cap.capabilityFlags = 0x34;
    cap.domains[static_cast<size_t>(ResourceDim::GpuCapacity)].maxCapacity = 321;
    cap.domains[static_cast<size_t>(ResourceDim::GpuCapacity)].flags = 8;
    cap.actionPathFlags[static_cast<size_t>(ResourceDim::GpuCapacity)] = 6;

    // Simulate that StateService already committed canonical snapshots into the same vault.
    service.GetStateVault().UpdateConstraintAndCapability(c, cap);
    const Generation g1 = service.GetStateVault().GetGeneration();
    assert(g1 != 0);

    FixedConstraintProvider cp(c);
    FixedCapabilityProvider ap(cap);

    // Shared-vault mode: Start() must not re-seed and bump generation again.
    service.Start(&cp, &ap);
    const Generation g2 = service.GetStateVault().GetGeneration();
    assert(g2 == g1);

    std::cout << "  ResourceScheduleService Start No-Double-Seed: PASS" << std::endl;
}

void TestStateServiceMetricsStaleDropLatencyAndResync() {
    std::cout << "Testing StateService Metrics StaleDrop/Latency/Resync..." << std::endl;

    StateVault vault;
    StateServiceMetrics metrics;
    StateAggregator aggregator(vault, metrics);

    StateRuleSet rules;
    rules.ruleVersion = 1;
    rules.thermal.severeThreshold = 5;
    rules.battery.saverThreshold = 20;
    aggregator.SetRuleSet(rules);

    StateDelta d1;
    d1.domainMask = ToMask(StateDomain::Thermal) | ToMask(StateDomain::Battery) |
                    ToMask(StateDomain::MemoryPsi) | ToMask(StateDomain::Screen) |
                    ToMask(StateDomain::Power);
    d1.captureNs = 100;
    d1.constraint.thermalLevel = 6;
    d1.constraint.thermalScaleQ10 = 768;
    d1.constraint.thermalSevere = true;
    d1.constraint.batteryLevel = 10;
    d1.constraint.batteryScaleQ10 = 800;
    d1.constraint.batterySaver = true;
    d1.constraint.psiLevel = 2;
    d1.constraint.psiScaleQ10 = 640;
    d1.constraint.memoryPressure = 2;
    d1.constraint.screenOn = false;
    d1.constraint.powerState = PowerState::Sleep;
    d1.constraint.fieldMask =
        ConstraintDeltaPatch::kThermalLevel | ConstraintDeltaPatch::kThermalScale |
        ConstraintDeltaPatch::kThermalSevere | ConstraintDeltaPatch::kBatteryLevel |
        ConstraintDeltaPatch::kBatteryScale | ConstraintDeltaPatch::kBatterySaver |
        ConstraintDeltaPatch::kMemoryPressure | ConstraintDeltaPatch::kPsiLevel |
        ConstraintDeltaPatch::kPsiScale | ConstraintDeltaPatch::kScreenOn |
        ConstraintDeltaPatch::kPowerState;

    (void)aggregator.OnDelta(d1);

    const size_t stale0 = metrics.staleDropCount.load(std::memory_order_relaxed);
    const size_t resync0 = metrics.sourceResyncCount.load(std::memory_order_relaxed);
    const size_t latency0 = metrics.updateLatencyP99.load(std::memory_order_relaxed);
    (void)latency0;

    // older captureNs -> stale drop.
    StateDelta d2 = d1;
    d2.captureNs = 50;
    const bool accepted = aggregator.OnDelta(d2);
    assert(!accepted);

    const size_t stale1 = metrics.staleDropCount.load(std::memory_order_relaxed);
    const size_t resync1 = metrics.sourceResyncCount.load(std::memory_order_relaxed);
    const size_t latency = metrics.updateLatencyP99.load(std::memory_order_relaxed);

    assert(stale1 == stale0 + 1);
    assert(resync1 == resync0 + 1);
    assert(latency != 0);

    std::cout << "  StateService Metrics StaleDrop/Latency/Resync: PASS" << std::endl;
}

void TestStateAggregatorAtomicConstraintCapabilityCommit() {
    std::cout << "Testing StateAggregator Atomic Constraint+Capability Commit..." << std::endl;

    StateVault vault;
    StateServiceMetrics metrics;
    StateAggregator aggregator(vault, metrics);

    StateRuleSet rules;
    rules.ruleVersion = 1;
    rules.thermal.severeThreshold = 5;
    rules.battery.saverThreshold = 20;
    aggregator.SetRuleSet(rules);

    StateDelta delta;
    delta.domainMask = ToMask(StateDomain::Thermal) | ToMask(StateDomain::Capability);
    delta.captureNs = 100;
    delta.constraint.fieldMask = ConstraintDeltaPatch::kThermalLevel;
    delta.constraint.thermalLevel = 4;
    delta.capability.supportedResources = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    delta.capability.feasibleChangedMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    delta.capability.maxCapacityPatch[ResourceDim::CpuCapacity] = 777;
    delta.capability.domainFlags[static_cast<size_t>(ResourceDim::CpuCapacity)] = 0x11;

    const bool committed = aggregator.UpdateSync(delta, 0);
    assert(committed);
    assert(vault.GetGeneration() == 1);

    auto view = vault.Snapshot();
    assert(view.Constraint().snapshot.thermalLevel == 4);
    assert(view.Capability().snapshot.supportedResources ==
           (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)));
    assert(view.Capability()
               .snapshot.domains[static_cast<size_t>(ResourceDim::CpuCapacity)]
               .maxCapacity == 777);

    const Generation genAfterFirst = vault.GetGeneration();
    const bool committedAgain = aggregator.UpdateSync(delta, 0);
    assert(!committedAgain);
    assert(vault.GetGeneration() == genAfterFirst);
    assert(metrics.coalesceCount.load(std::memory_order_relaxed) == 1);

    std::cout << "  StateAggregator Atomic Constraint+Capability Commit: PASS" << std::endl;
}

void TestExplicitFallback() {
    std::cout << "Testing Explicit Fallback..." << std::endl;

    TraceRecorder::Instance().Clear();

    FallbackContext ctx;
    ctx.sessionId = 12345;
    ctx.reason = DecisionFallbackReason::PlatformUnavailable;
    ctx.detailBits = 0x0001;
    ctx.generation = 1;
    ctx.ruleVersion = 1;
    ctx.profileId = 1;

    TraceRecorder::Instance().RecordFallback(ctx);

    assert(TraceRecorder::Instance().GetBuffer().GetCount() > 0);

    std::cout << "  Explicit Fallback: PASS" << std::endl;
}

void TestConstraintEnvelopeUsesEffectiveScaling() {
    std::cout << "Testing ConstraintEnvelope Uses Effective Scaling..." << std::endl;

    ResourceScheduleService<MainProfileSpec> service;

    ConstraintSnapshot snapshot;
    snapshot.thermalLevel = 4;
    snapshot.thermalScaleQ10 = 0;
    snapshot.batteryLevel = 10;
    snapshot.batteryScaleQ10 = 0;
    snapshot.memoryPressure = 3;
    snapshot.psiLevel = 2;
    snapshot.psiScaleQ10 = 0;
    snapshot.batterySaver = true;
    snapshot.screenOn = true;
    snapshot.thermalSevere = true;

    service.OnConstraintUpdate(snapshot);

    auto view = service.GetStateVault().Snapshot();
    const auto envelope = EnvelopeStage::BuildConstraintEnvelope(view.Constraint().snapshot, 0);
    assert(envelope.thermalScalingQ10 == EnvelopeStage::ComputeThermalScaling(snapshot));
    assert(envelope.batteryScalingQ10 == EnvelopeStage::ComputeBatteryScaling(snapshot));
    assert(envelope.memoryScalingQ10 == EnvelopeStage::ComputeMemoryScaling(snapshot));

    std::cout << "  ConstraintEnvelope Uses Effective Scaling: PASS" << std::endl;
}

void TestReplayConsistency() {
    std::cout << "Testing Replay Consistency..." << std::endl;

    TraceConfig config;
    config.level = 3;
    config.enableDecisionTrace = true;
    config.enableReplayHash = true;

    auto &recorder = TraceRecorder::Instance();
    recorder.Init(config);

    SchedEvent event;
    event.eventId = 100;
    event.sessionId = static_cast<SessionId>(0x00000002000000C8ULL);
    event.sceneId = 0x1200;
    event.pid = 88;
    event.uid = 10001;
    event.timestamp = 1234567890;
    event.source = 1;
    event.action = 2;
    event.rawFlags = 0x10;
    event.rawHint = 0x20;

    PolicyDecision decisionRecord;
    decisionRecord.sessionId = event.sessionId;
    decisionRecord.meta.generation = 1;
    decisionRecord.meta.profileId = 1;
    decisionRecord.meta.ruleVersion = 1;
    decisionRecord.meta.decisionTs = event.timestamp;
    decisionRecord.effectiveIntent = IntentLevel::P1;
    decisionRecord.temporal.timeMode = TimeMode::Burst;
    decisionRecord.reasonBits = 0;
    decisionRecord.grant.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    decisionRecord.grant.delivered[ResourceDim::CpuCapacity] = 800;
    recorder.RecordPathEnd(TracePoint::ConvergePathEnd, static_cast<uint32_t>(event.eventId), 0,
                           decisionRecord.reasonBits, decisionRecord.meta.profileId,
                           decisionRecord.meta.ruleVersion, decisionRecord.effectiveIntent,
                           decisionRecord.sessionId);

    ConstraintSnapshot constraint;
    constraint.thermalLevel = 2;
    constraint.batteryLevel = 80;
    constraint.screenOn = true;

    CapabilityFeasible capability;
    capability.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    capability.feasible[ResourceDim::CpuCapacity] = 900;

    ReplayHash hash;
    hash.decisionHash = ComputeDecisionHash(decisionRecord);
    hash.inputHash = recorder.ComputeInputHash(event);
    hash.stateHash = recorder.ComputeStateHash(constraint, capability);
    hash.generation = decisionRecord.meta.generation;
    hash.timestamp = decisionRecord.meta.decisionTs;
    hash.snapshotToken = 0xDEADBEEFCAFEBABEULL;
    hash.sessionId = decisionRecord.sessionId;
    hash.profileId = 1;
    hash.ruleVersion = 1;
    hash.effectiveIntent = IntentLevel::P1;

    ReplayHashInputs expectedInputs;
    expectedInputs.inputHash = hash.inputHash;
    expectedInputs.stateHash = hash.stateHash;

    recorder.RecordReplayHash(hash);
    assert(recorder.VerifyReplayHash(hash, hash.decisionHash, expectedInputs));

    size_t replayParts = 0;
    const bool replayVisited =
        recorder.ForEachBySessionId(hash.sessionId, [&replayParts](const TraceRecord &record) {
            if (record.tracePoint == static_cast<uint32_t>(TracePoint::ReplayHashPart1) ||
                record.tracePoint == static_cast<uint32_t>(TracePoint::ReplayHashPart2)) {
                ++replayParts;
            }
            return true;
        });
    assert(replayVisited);
    assert(replayParts == 2);

    ReplayHash mismatch = hash;
    mismatch.inputHash ^= 0x1ULL;
    assert(!recorder.VerifyReplayHash(mismatch));

    CapabilityFeasible mismatchedCapability = capability;
    mismatchedCapability.feasible[ResourceDim::CpuCapacity] = 901;
    ReplayHashInputs mismatchedInputs = expectedInputs;
    mismatchedInputs.stateHash = recorder.ComputeStateHash(constraint, mismatchedCapability);
    assert(!recorder.VerifyReplayHash(hash, hash.decisionHash, mismatchedInputs));

    std::cout << "  Replay Consistency: PASS" << std::endl;
}

void TestReplayDecisionHashFromExecutionSignatureMatchesDecisionHash() {
    std::cout << "Testing Replay DecisionHash From ExecutionSignature Matches..." << std::endl;

    PolicyDecision d;
    d.sessionId = 88;
    d.grant.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    d.grant.delivered[ResourceDim::CpuCapacity] = 800;
    d.reasonBits = 0x10;
    d.effectiveIntent = IntentLevel::P2;

    d.temporal.timeMode = TimeMode::Steady;
    d.temporal.leaseStartTs = 1000;
    d.temporal.leaseEndTs = 2000;
    d.temporal.renewalCount = 3;

    d.request.resourceMask = d.grant.resourceMask;
    d.request.min[ResourceDim::CpuCapacity] = 600;
    d.request.target[ResourceDim::CpuCapacity] = 800;
    d.request.max[ResourceDim::CpuCapacity] = 900;

    d.admitted = true;
    d.degraded = false;

    d.meta.generation = 1;
    d.meta.profileId = 2;
    d.meta.ruleVersion = 3;
    d.meta.platformActionPath = PlatformActionPath::PerfHintHal;
    d.meta.fallbackFlags = 0;
    d.meta.decisionTs = 123456;
    d.meta.stageMask = 0;
    d.meta.latencyNs = 42;

    assert(d.IsDedupCandidate());
    d.executionSignature = ComputeExecutionSignature(d);

    const uint64_t bySig = HashCombine(
        HashCombine(d.executionSignature, static_cast<uint64_t>(d.degraded)), d.meta.Hash());
    const uint64_t full = ComputeDecisionHash(d);
    assert(bySig == full);

    std::cout << "  Replay DecisionHash From ExecutionSignature Matches: PASS" << std::endl;
}

void TestReplayHashFromInputsMatchesLegacy() {
    std::cout << "Testing ReplayHash From Inputs Matches Legacy..." << std::endl;

    TraceConfig config;
    config.level = 3;
    config.enableReplayHash = true;
    auto &recorder = TraceRecorder::Instance();
    recorder.Init(config);

    SchedEvent event;
    event.eventId = 101;
    event.sessionId = static_cast<SessionId>(0x00000002000000C9ULL);
    std::strcpy(event.package, "com.test.app");
    std::strcpy(event.activity, ".Main");
    event.timestamp = 2234567890ULL;
    event.action = static_cast<uint32_t>(EventSemanticAction::Launch);

    ConstraintSnapshot constraint;
    constraint.thermalLevel = 1;
    constraint.batteryLevel = 90;

    CapabilityFeasible capability;
    capability.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    capability.feasible[ResourceDim::CpuCapacity] = 800;

    ReplayHashInputs expected;
    const ReplayHash legacy =
        recorder.BuildReplayHash(0x1234u, event, constraint, capability, 7, 0xABCDEFu,
                                 event.sessionId, 2, 3, IntentLevel::P1, &expected);

    const ReplayHash fromInputs = recorder.BuildReplayHashFromInputs(
        0x1234u, recorder.ComputeInputHash(event),
        recorder.ComputeStateHash(constraint, capability), static_cast<int64_t>(event.timestamp), 7,
        0xABCDEFu, event.sessionId, 2, 3, IntentLevel::P1);

    assert(legacy.decisionHash == fromInputs.decisionHash);
    assert(legacy.inputHash == fromInputs.inputHash);
    assert(legacy.stateHash == fromInputs.stateHash);
    assert(legacy.generation == fromInputs.generation);
    assert(legacy.timestamp == fromInputs.timestamp);
    assert(legacy.snapshotToken == fromInputs.snapshotToken);
    assert(legacy.sessionId == fromInputs.sessionId);
    assert(legacy.profileId == fromInputs.profileId);
    assert(legacy.ruleVersion == fromInputs.ruleVersion);
    assert(legacy.effectiveIntent == fromInputs.effectiveIntent);
    assert(expected.inputHash == fromInputs.inputHash);
    assert(expected.stateHash == fromInputs.stateHash);

    std::cout << "  ReplayHash From Inputs Matches Legacy: PASS" << std::endl;
}

void TestUnsupportedResourceHandling() {
    std::cout << "Testing Unsupported Resource Handling..." << std::endl;

    PlatformCapability cap;
    PlatformRegistry::FillPlatformCapability(PlatformVendor::Unknown, cap);

#if defined(PERFENGINE_PLATFORM_QCOM) || defined(PERFENGINE_PLATFORM_MTK)
    ResourceDim unsupportedDim = ResourceDim::Reserved;
    uint32_t dimBit = 1 << static_cast<uint32_t>(unsupportedDim);
    bool isSupported = (cap.supportedResources & dimBit) != 0;
    assert(!isSupported);
#if defined(PERFENGINE_PLATFORM_QCOM)
    QcomActionMap actionMap(cap);
#else
    MtkActionMap actionMap(cap);
#endif
    AbstractActionBatch abstractBatch;
    abstractBatch.actionCount = 1;
    abstractBatch.actions[0].actionKey = static_cast<uint16_t>(ResourceDim::CpuCapacity);
    abstractBatch.actions[0].flags =
        EncodeActionFlags(true, ActionContractMode::Floor, IntentLevel::P1, TimeMode::Burst);
    abstractBatch.actions[0].value = 512;
    auto cmdBatch = actionMap.Map(abstractBatch);
    assert(cmdBatch.path == PlatformActionPath::NoopFallback);
    assert(cmdBatch.fallbackFlags != 0);
    std::cout << "  Unsupported Resource Handling: PASS (platform defined)" << std::endl;
#else
    assert(cap.supportedResources == 0);
    std::cout << "  Unsupported Resource Handling: PASS (no platform defined)" << std::endl;
#endif
}

void TestSessionTraceReplayBinding() {
    std::cout << "Testing Session/Trace/Replay Binding..." << std::endl;

    TraceConfig config;
    config.level = 3;
    auto &recorder = TraceRecorder::Instance();
    recorder.Init(config);

    const SessionId sessionId = static_cast<SessionId>(0x00000003000000C8ULL);

    constexpr int64_t kDecisionEventId = 777;
    constexpr uint32_t kProfileId = 7;
    constexpr uint32_t kRuleVersion = 11;
    recorder.RecordPathEnd(TracePoint::FastPathEnd, static_cast<uint32_t>(kDecisionEventId), 0, 0,
                           kProfileId, kRuleVersion, IntentLevel::P4, sessionId);

    ExecutionResult execution;
    execution.sessionId = sessionId;
    recorder.RecordExecution(execution);

    FallbackContext fallback;
    fallback.sessionId = sessionId;
    fallback.reason = DecisionFallbackReason::ExecutionRejected;
    recorder.RecordFallback(fallback);

    size_t recordCount = 0;
    bool sawDecision = false;
    const bool visited = recorder.ForEachBySessionId(
        sessionId, [&recordCount, &sawDecision](const TraceRecord &record) {
            ++recordCount;
            assert(record.sessionHi == 0x00000003u);
            assert(record.sessionId == 0x000000C8u);
            if (record.tracePoint == static_cast<uint32_t>(TracePoint::FastPathEnd)) {
                sawDecision = true;
                assert(record.eventId == 777u);
            }
            return true;
        });
    assert(visited);
    assert(recordCount == 3);
    assert(sawDecision);

    std::cout << "  Session/Trace/Replay Binding: PASS" << std::endl;
}

void TestConvergeFallbackClassification() {
    std::cout << "Testing Converge Fallback Classification..." << std::endl;

    PolicyDecision decision;
    decision.sessionId = 1;
    decision.meta.profileId = 1;
    decision.meta.generation = 2;
    decision.meta.ruleVersion = 3;
    decision.request.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));

    decision.reasonBits = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) << 16;
    auto ctx = resource_schedule_detail::BuildConvergeFallbackContext(decision);
    assert(ctx.reason == DecisionFallbackReason::CapabilityMismatch);
    assert(FallbackDetailBits::GetDomain(ctx.detailBits) == FallbackDomain::Capability);
    assert(FallbackDetailBits::GetCategory(ctx.detailBits) == FallbackCategory::Mismatch);
    assert(FallbackDetailBits::GetResourceDim(ctx.detailBits) ==
           static_cast<uint8_t>(ResourceDim::CpuCapacity));

    decision.reasonBits = (1u << static_cast<uint32_t>(ResourceDim::MemoryBandwidth)) << 8;
    ctx = resource_schedule_detail::BuildConvergeFallbackContext(decision);
    assert(ctx.reason == DecisionFallbackReason::ConstraintViolation);
    assert(FallbackDetailBits::GetDomain(ctx.detailBits) == FallbackDomain::Constraint);
    assert(FallbackDetailBits::GetCategory(ctx.detailBits) == FallbackCategory::Violation);
    assert(FallbackDetailBits::GetResourceDim(ctx.detailBits) ==
           static_cast<uint8_t>(ResourceDim::MemoryBandwidth));

    decision.reasonBits = (1u << static_cast<uint32_t>(ResourceDim::GpuCapacity)) << 24;
    ctx = resource_schedule_detail::BuildConvergeFallbackContext(decision);
    assert(ctx.reason == DecisionFallbackReason::ResourceInsufficient);
    assert(FallbackDetailBits::GetDomain(ctx.detailBits) == FallbackDomain::Resource);
    assert(FallbackDetailBits::GetCategory(ctx.detailBits) == FallbackCategory::Violation);
    assert(FallbackDetailBits::GetResourceDim(ctx.detailBits) ==
           static_cast<uint8_t>(ResourceDim::GpuCapacity));

    std::cout << "  Converge Fallback Classification: PASS" << std::endl;
}

void TestExecutionFlowDedupThrottle() {
    std::cout << "Testing ExecutionFlow Dedup Throttle..." << std::endl;

    TraceConfig config;
    config.level = 1;
    TraceRecorder::Instance().Init(config);

    ExecutionFlow<MainProfileSpec> flow;
    CountingActionMap actionMap;
    CountingExecutor executor;

    PolicyDecision decision;
    decision.sessionId = 77;
    decision.grant.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    decision.grant.delivered[ResourceDim::CpuCapacity] = 800;
    decision.request.resourceMask = decision.grant.resourceMask;
    decision.request.min[ResourceDim::CpuCapacity] = 600;
    decision.request.target[ResourceDim::CpuCapacity] = 800;
    decision.request.max[ResourceDim::CpuCapacity] = 900;
    decision.effectiveIntent = IntentLevel::P2;
    decision.temporal.timeMode = TimeMode::Steady;
    decision.admitted = true;
    decision.reasonBits = 0x10;

    auto first = flow.Execute(decision, &actionMap, &executor, 10);
    auto second = flow.Execute(decision, &actionMap, &executor, 10);
    assert(first.success);
    assert(second.success);
    assert(executor.executeCount == 1);
    assert((second.execResult.executionFlags & 0x08u) != 0);
    assert(second.execResult.actualPath == PlatformActionPath::PerfHintHal);

    std::this_thread::sleep_for(std::chrono::milliseconds(15));
    auto third = flow.Execute(decision, &actionMap, &executor, 10);
    assert(third.success);
    assert(executor.executeCount == 2);
    assert((third.execResult.executionFlags & 0x08u) == 0);

    PolicyDecision burst = decision;
    burst.sessionId = 78;
    burst.effectiveIntent = IntentLevel::P1;
    burst.temporal.timeMode = TimeMode::Burst;
    burst.grant.delivered[ResourceDim::CpuCapacity] = 900;

    auto p1First = flow.Execute(burst, &actionMap, &executor, 10);
    auto p1Second = flow.Execute(burst, &actionMap, &executor, 10);
    assert(p1First.success);
    assert(p1Second.success);
    assert(executor.executeCount == 4);

    std::cout << "  ExecutionFlow Dedup Throttle: PASS" << std::endl;
}
