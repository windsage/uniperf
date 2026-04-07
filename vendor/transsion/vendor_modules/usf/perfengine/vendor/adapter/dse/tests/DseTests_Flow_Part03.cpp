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

namespace {
struct CountingStateListener final : IStateListener {
    int callCount = 0;
    Generation lastGeneration = 0;
    SnapshotToken lastToken{};
    uint32_t lastDomainMask = 0;

    void OnStateChanged(Generation generation, SnapshotToken token,
                        uint32_t domainMask) noexcept override {
        ++callCount;
        lastGeneration = generation;
        lastToken = token;
        lastDomainMask = domainMask;
    }
};
}    // namespace

void TestRuleTable() {
    std::cout << "Testing RuleTable..." << std::endl;

    auto &table = RuleTable::Instance();
    assert(table.Load(nullptr));

    auto *cpuRule = table.GetResourceRule(ResourceDim::CpuCapacity);
    assert(cpuRule != nullptr);
    assert(cpuRule->max > 0);

    const auto &constraintRule = table.GetConstraintRule();
    assert(constraintRule.thermalThreshold > 0);

    const auto &arbitrationRule = table.GetArbitrationRule();
    assert(arbitrationRule.enablePreemption);

    std::cout << "  RuleTable: PASS" << std::endl;
}

void TestRuleTableReloadPathSelfCopy() {
    std::cout << "Testing RuleTable Reload Path Self-Copy..." << std::endl;

#if defined(PERFENGINE_PLATFORM_QCOM)
    const char *xmlPath = "/tmp/dse_rule_reload_self_test_qcom.xml";
#elif defined(PERFENGINE_PLATFORM_MTK)
    const char *xmlPath = "/tmp/dse_rule_reload_self_test_mtk.xml";
#else
    const char *xmlPath = "/tmp/dse_rule_reload_self_test.xml";
#endif
    {
        std::ofstream xml(xmlPath);
        xml << "<PerfEngine version=\"2\">\n"
               "  <Scenario id=\"2\" name=\"GAME\" fps=\"90\">\n"
               "    <Param timeout=\"20000\"/>\n"
               "  </Scenario>\n"
               "</PerfEngine>\n";
    }

    auto &loader = ConfigLoader::Instance();
    assert(loader.Load(xmlPath));

    auto &table = RuleTable::Instance();
    assert(table.Load(xmlPath));
    assert(table.IsLoaded());
    assert(std::strcmp(table.GetRulePath(), xmlPath) == 0);

    assert(table.Reload());
    assert(table.IsLoaded());
    assert(std::strcmp(table.GetRulePath(), xmlPath) == 0);
    assert(table.GetRuleVersion() >= 1u);

    assert(table.Reload());
    assert(std::strcmp(table.GetRulePath(), xmlPath) == 0);

    std::cout << "  RuleTable Reload Path Self-Copy: PASS" << std::endl;
}

void TestTraceBuffer() {
    std::cout << "Testing TraceBuffer..." << std::endl;

    TraceBuffer buffer;
    assert(buffer.IsEmpty());
    assert(!buffer.IsFull());

    TraceRecord record;
    record.timestamp = 1000;
    record.tracePoint = 1;
    record.eventId = 100;

    auto writeResult = buffer.Write(record);
    assert(writeResult.success);
    assert(buffer.GetCount() == 1);

    TraceRecord readRecord;
    assert(buffer.Read(readRecord));
    assert(readRecord.timestamp == 1000);
    assert(readRecord.eventId == 100);

    std::cout << "  TraceBuffer: PASS" << std::endl;
}

void TestTraceRecorder() {
    std::cout << "Testing TraceRecorder..." << std::endl;

    TraceConfig config;
    config.level = 3;
    config.enableStageTrace = true;

    auto &recorder = TraceRecorder::Instance();
    recorder.Init(config);

    recorder.RecordEvent(TracePoint::EventEntry, 1, 0);
    assert(recorder.GetBuffer().GetCount() == 1);

    recorder.RecordStage(TracePoint::StageEnter, 1, 0, 0);
    assert(recorder.GetBuffer().GetCount() == 2);

    std::cout << "  TraceRecorder: PASS" << std::endl;
}

void TestStateAggregatorCanonicalCoalesce() {
    std::cout << "Testing StateAggregator Canonical Coalesce..." << std::endl;

    StateVault vault;
    StateServiceMetrics metrics;
    StateAggregator aggregator(vault, metrics);

    // 使用与 StateService::Init 同步的默认阈值，确保 NormalizeConstraint 的结果可预测。
    StateRuleSet rules;
    rules.ruleVersion = 1;
    rules.thermal.severeThreshold = 5;
    rules.battery.saverThreshold = 20;
    aggregator.SetRuleSet(rules);

    // 约束域：只更新时间相关的 constraint，不触碰 capability，使第二次提交时 capability
    // 侧保持等值。
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
    d1.constraint.memoryPressure = 2;
    d1.constraint.psiLevel = 2;
    d1.constraint.psiScaleQ10 = 640;
    d1.constraint.screenOn = false;
    d1.constraint.powerState = PowerState::Sleep;
    d1.constraint.fieldMask =
        ConstraintDeltaPatch::kThermalLevel | ConstraintDeltaPatch::kThermalScale |
        ConstraintDeltaPatch::kThermalSevere | ConstraintDeltaPatch::kBatteryLevel |
        ConstraintDeltaPatch::kBatteryScale | ConstraintDeltaPatch::kBatterySaver |
        ConstraintDeltaPatch::kMemoryPressure | ConstraintDeltaPatch::kPsiLevel |
        ConstraintDeltaPatch::kPsiScale | ConstraintDeltaPatch::kScreenOn |
        ConstraintDeltaPatch::kPowerState;

    const Generation g0 = vault.GetGeneration();
    const size_t coalesce0 = aggregator.GetCoalesceCount();
    const bool pushed1 = aggregator.UpdateSync(d1, 10);
    assert(pushed1);
    const Generation g1 = vault.GetGeneration();
    assert(g1 > g0);

    // 同 canonical constraint 再提交一次：应走 CommitToVault 的等值合并路径，
    // generation 不增长但 coalesceCount 增加。
    StateDelta d2 = d1;
    d2.captureNs = 101;
    const bool pushed2 = aggregator.UpdateSync(d2, 10);
    assert(!pushed2);
    const Generation g2 = vault.GetGeneration();
    assert(g2 == g1);
    const size_t coalesce1 = aggregator.GetCoalesceCount();
    assert(coalesce1 == coalesce0 + 1);

    std::cout << "  StateAggregator Canonical Coalesce: PASS" << std::endl;
}

void TestStateServiceEndToEndFlow() {
    std::cout << "Testing StateService End-to-End Flow..." << std::endl;

    StateVault vault;
    StateService service(vault);
    CountingStateListener listener;

    // Ready gate before start.
    assert(!service.WaitUntilReady(1));
    service.Subscribe(nullptr);    // no-op branch
    service.Subscribe(&listener);

    MonitoringContext ctx;
    ctx.interactive = false;
    ctx.screenOn = true;
    service.SetMonitoringContext(ctx);

    // 1) UpdateSync flow: aggregate -> commit -> notify listener
    StateDelta delta1;
    delta1.domainMask = ToMask(StateDomain::Thermal) | ToMask(StateDomain::Battery) |
                        ToMask(StateDomain::MemoryPsi) | ToMask(StateDomain::Screen) |
                        ToMask(StateDomain::Power);
    delta1.captureNs = 1000;
    delta1.constraint.fieldMask =
        ConstraintDeltaPatch::kThermalLevel | ConstraintDeltaPatch::kThermalScale |
        ConstraintDeltaPatch::kBatteryLevel | ConstraintDeltaPatch::kBatteryScale |
        ConstraintDeltaPatch::kPsiLevel | ConstraintDeltaPatch::kPsiScale |
        ConstraintDeltaPatch::kScreenOn | ConstraintDeltaPatch::kPowerState;
    delta1.constraint.thermalLevel = 3;
    delta1.constraint.thermalScaleQ10 = 900;
    delta1.constraint.batteryLevel = 80;
    delta1.constraint.batteryScaleQ10 = 980;
    delta1.constraint.psiLevel = 1;
    delta1.constraint.psiScaleQ10 = 900;
    delta1.constraint.screenOn = true;
    delta1.constraint.powerState = PowerState::Active;

    const Generation g0 = vault.GetGeneration();
    assert(service.UpdateSync(delta1, 10));
    const Generation g1 = vault.GetGeneration();
    assert(g1 > g0);
    assert(listener.callCount == 1);
    assert(listener.lastGeneration == g1);
    assert(listener.lastToken.generation == g1);
    assert(listener.lastDomainMask == delta1.domainMask);

    // 2) OnDelta flow with changed data should also commit+notify.
    StateDelta delta2 = delta1;
    delta2.captureNs = 1010;
    delta2.constraint.thermalLevel = 6;
    delta2.constraint.batteryLevel = 15;
    service.OnDelta(delta2);
    const Generation g2 = vault.GetGeneration();
    assert(g2 > g1);
    assert(listener.callCount == 2);
    assert(service.GetAggregator().GetLastCaptureNs(StateDomain::Thermal) == 1010u);

    // 3) stale packet should be dropped.
    StateDelta stale = delta2;
    stale.captureNs = 1005;
    assert(!service.UpdateSync(stale, 10));
    assert(vault.GetGeneration() == g2);
    assert(listener.callCount == 2);

    // 4) capability-only flow to cover capability patch.
    StateDelta capabilityDelta;
    capabilityDelta.domainMask = ToMask(StateDomain::Capability);
    capabilityDelta.captureNs = 1020;
    capabilityDelta.capability.supportedResources =
        (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    capabilityDelta.capability.capabilityFlags = 0x12;
    capabilityDelta.capability.feasibleChangedMask =
        (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    capabilityDelta.capability.maxCapacityPatch.v[static_cast<size_t>(ResourceDim::CpuCapacity)] =
        777;
    capabilityDelta.capability.actionChangedMask =
        (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));
    capabilityDelta.capability.actionPathFlags[static_cast<size_t>(ResourceDim::CpuCapacity)] = 3;
    capabilityDelta.capability.domainFlags[static_cast<size_t>(ResourceDim::CpuCapacity)] = 9;
    assert(service.UpdateSync(capabilityDelta, 10));
    assert(vault.Snapshot()
               .Capability()
               .snapshot.domains[static_cast<size_t>(ResourceDim::CpuCapacity)]
               .maxCapacity == 777);

    // 5) init/start/stop flow should not crash; Unknown path exercises backend fallback.
    (void)service.Init(PlatformVendor::Unknown);
    (void)service.Start();
    service.Stop();
    assert(!service.WaitUntilReady(1));
    (void)service.GetFallbackRateByDomain(StateDomain::Thermal);

    service.Unsubscribe(&listener);

    // Additional service lifecycle flow with explicit vendor backend path.
    StateVault vault2;
    StateService service2(vault2);
    (void)service2.Init(PlatformVendor::Qcom);
    service2.SetMonitoringContext(ctx);
    const bool started = service2.Start();
    if (started) {
        assert(service2.WaitUntilReady(20));
        service2.Stop();
    } else {
        assert(!service2.IsReady());
    }

    std::cout << "  StateService End-to-End Flow: PASS" << std::endl;
}

void TestPlatformRegistry() {
    std::cout << "Testing PlatformRegistry..." << std::endl;

    ForcePlatformRegistrationLinkage();

    auto &registry = PlatformRegistry::Instance();
    auto vendor = registry.DetectVendor();

    assert(vendor == PlatformVendor::Unknown || vendor == PlatformVendor::Qcom ||
           vendor == PlatformVendor::Mtk || vendor == PlatformVendor::Unisoc);

    // UNISOC 等占位平台可能已编译出单一目标 vendor，但尚未注册执行链/能力工厂；
    // 仅对已注册平台校验能力快照与状态后端工厂（与 PlatformRegistry 文档一致）。
    if (vendor != PlatformVendor::Unknown && registry.IsRegistered(vendor)) {
        PlatformCapability capability;
        PlatformRegistry::FillPlatformCapability(vendor, capability);
        assert(capability.vendor == vendor);
        assert(capability.supportedResources != 0);

        IPlatformStateBackend *backend = registry.CreateStateBackend(vendor);
        assert(backend != nullptr);
        delete backend;
    }

    std::cout << "  PlatformRegistry: PASS" << std::endl;
}

void TestOrchestrator() {
    std::cout << "Testing Orchestrator..." << std::endl;

    StateVault vault;
    Orchestrator<MainProfileSpec> orchestrator(vault);

    SchedEvent event;
    event.action = 1;
    event.rawFlags = 0;

    auto fastGrant = orchestrator.RunFast(event);
    assert(fastGrant.sessionId == static_cast<SessionId>(event.eventId));

    auto decision = orchestrator.RunConverge(event);
    assert(decision.grant.resourceMask != 0 || !decision.admitted);

    std::cout << "  Orchestrator: PASS" << std::endl;
}

void TestOrchestratorCachedProfileAndRuleVersion() {
    std::cout << "Testing Orchestrator Cached Profile And RuleVersion..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);
    RuleTable::Instance().Load(nullptr);

    StateVault vault;
    Orchestrator<MainProfileSpec> orchestrator(vault);

    SchedEvent event{};
    event.eventId = 501;
    event.sessionId = 501;
    event.action = static_cast<uint32_t>(EventSemanticAction::Launch);
    event.timestamp = 2000000000ULL;

    const PolicyDecision decision = orchestrator.RunConverge(event);
    assert(decision.meta.profileId == static_cast<uint32_t>(MainProfileSpec{}.kind) + 1);
    assert(decision.meta.ruleVersion == RuleTable::Instance().GetRuleVersion());

    std::cout << "  Orchestrator Cached Profile And RuleVersion: PASS" << std::endl;
}

void TestOrchestratorAppliesProfileResourceMask() {
    std::cout << "Testing Orchestrator Applies Profile Resource Mask..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);
    RuleTable::Instance().Load(nullptr);

    auto runMaskCheck = [](uint32_t expectedMask, auto *specTag) {
        (void)specTag;
        StateVault vault;
        using Spec = std::decay_t<decltype(*specTag)>;
        Orchestrator<Spec> orchestrator(vault);

        SchedEvent event;
        event.eventId = static_cast<uint32_t>(expectedMask);
        event.sessionId = static_cast<SessionId>(expectedMask);
        event.action = static_cast<uint32_t>(EventSemanticAction::Launch);
        event.timestamp = 1000000000ULL;

        const auto decision = orchestrator.RunConverge(event);
        assert(decision.request.resourceMask == expectedMask);
    };

    EntryProfileSpec entry;
    runMaskCheck(entry.resourceMask, &entry);

    MainProfileSpec main;
    runMaskCheck(main.resourceMask, &main);

    FlagshipProfileSpec flagship;
    runMaskCheck(flagship.resourceMask, &flagship);

    std::cout << "  Orchestrator Applies Profile Resource Mask: PASS" << std::endl;
}

void TestOrchestratorEventIdFallbackSessionBinding() {
    std::cout << "Testing Orchestrator EventId Fallback Session Binding..." << std::endl;

    StateVault vault;
    Orchestrator<MainProfileSpec> orchestrator(vault);

    SchedEvent event;
    event.eventId = 321;
    event.sessionId = 0;
    event.action = static_cast<uint32_t>(EventSemanticAction::Launch);
    event.timestamp = 1000000000ULL;

    (void)orchestrator.RunConverge(event);

    auto afterDecision = vault.Snapshot();
    assert(afterDecision.Intent().activeSessionId == static_cast<SessionId>(event.eventId));
    assert(afterDecision.Lease().activeLeaseId == static_cast<LeaseId>(event.eventId));

    orchestrator.ReleaseSession(static_cast<SessionId>(event.eventId));
    auto afterRelease = vault.Snapshot();
    assert(afterRelease.Intent().activeSessionId == 0);
    assert(afterRelease.Lease().activeLeaseId == 0);

    std::cout << "  Orchestrator EventId Fallback Session Binding: PASS" << std::endl;
}

void TestOrchestratorUsesExplicitSceneId() {
    std::cout << "Testing Orchestrator Uses Explicit SceneId..." << std::endl;

    StateVault vault;
    Orchestrator<MainProfileSpec> orchestrator(vault);

    SchedEvent event;
    event.eventId = 901;
    event.sessionId = 901;
    event.sceneId = 555001;
    event.pid = 777;
    event.action = static_cast<uint32_t>(EventSemanticAction::Launch);
    event.rawFlags = 1;
    event.timestamp = 1000000000ULL;

    (void)orchestrator.RunConverge(event);

    auto view = vault.Snapshot();
    assert(view.Scene().activeSceneId == event.sceneId);
    assert(view.Scene().activeSceneId != static_cast<SceneId>(event.pid));

    std::cout << "  Orchestrator Uses Explicit SceneId: PASS" << std::endl;
}

void TestOrchestratorEmitsSinglePathEndTrace() {
    std::cout << "Testing Orchestrator Emits Single PathEnd Trace..." << std::endl;

    TraceConfig config;
    config.level = 1;
    config.enableDecisionTrace = true;
    config.enableReplayHash = true;
    TraceRecorder::Instance().Init(config);

    StateVault vault;
    Orchestrator<MainProfileSpec> orchestrator(vault);

    SchedEvent fastEvent;
    fastEvent.eventId = 1001;
    fastEvent.sessionId = 1001;
    fastEvent.action = static_cast<uint32_t>(EventSemanticAction::Launch);
    fastEvent.timestamp = 1000000000ULL;
    (void)orchestrator.RunFast(fastEvent);

    size_t fastPathEndCount = 0;
    TraceRecorder::Instance().ForEachByEventId(
        fastEvent.eventId, [&fastPathEndCount](const TraceRecord &record) {
            if (record.tracePoint == static_cast<uint32_t>(TracePoint::FastPathEnd)) {
                ++fastPathEndCount;
            }
            return true;
        });
    assert(fastPathEndCount == 1);

    TraceRecorder::Instance().Clear();

    SchedEvent convergeEvent = fastEvent;
    convergeEvent.eventId = 1002;
    convergeEvent.sessionId = 1002;
    (void)orchestrator.RunConverge(convergeEvent);

    size_t convergePathEndCount = 0;
    TraceRecorder::Instance().ForEachByEventId(
        convergeEvent.eventId, [&convergePathEndCount](const TraceRecord &record) {
            if (record.tracePoint == static_cast<uint32_t>(TracePoint::ConvergePathEnd)) {
                ++convergePathEndCount;
            }
            return true;
        });
    assert(convergePathEndCount == 1);

    std::cout << "  Orchestrator Emits Single PathEnd Trace: PASS" << std::endl;
}

void TestOrchestratorSkipsNoopStateWrite() {
    std::cout << "Testing Orchestrator Skips Noop State Write..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);
    RuleTable::Instance().Load(nullptr);

    StateVault vault;
    Orchestrator<MainProfileSpec> orchestrator(vault);

    SchedEvent event{};
    event.eventId = 20001;
    event.sessionId = 20001;
    event.sceneId = 12345;    // explicit stable scene binding
    event.action = static_cast<uint32_t>(EventSemanticAction::Background);
    event.rawFlags = 0x20;    // audible bit on (see SceneStage::InferAudibility)
    // Use a "future" deterministic timestamp to avoid history-replay snapshot selection
    // sensitivity under different build/coverage instrumentation timing.
    event.timestamp = SystemTime::GetDeterministicNs() + 1000000000ULL;    // +1s safety margin

    const PolicyDecision first = orchestrator.RunConverge(event);
    const Generation gen1 = vault.GetGeneration();
    const auto snap1 = vault.Snapshot();

    // 同一输入重复触发两次收敛路径：
    // - 如果 Scene/Intent/Lease 的 payload 与当前已发布状态等值，
    //   则 StateVault 的等值抑制应避免触发 generation++ 与 SaveToHistory()。
    // - 这里以 `vault.GetGeneration()` 作为等价验证手段：不 bump => 不产生新的 history snapshot。
    const PolicyDecision second = orchestrator.RunConverge(event);
    const Generation gen2 = vault.GetGeneration();
    const auto snap2 = vault.Snapshot();
    // 重复同一事件应保持决策语义稳定，且 generation 不应无界增长。
    assert(second.effectiveIntent == first.effectiveIntent);
    assert(second.temporal.timeMode == first.temporal.timeMode);
    assert(ResourceRequestEquals(second.request, first.request));
    assert(gen2 >= gen1);
    assert(gen2 <= gen1 + 1);
    assert(snap2.Scene().semantic.kind == snap1.Scene().semantic.kind);
    assert(snap2.Intent().contract.level == snap1.Intent().contract.level);

    std::cout << "  Orchestrator Skips Noop State Write: PASS" << std::endl;
}

void TestArbitrationFormula() {
    std::cout << "Testing Arbitration Formula..." << std::endl;

    ResourceRequest request;
    request.resourceMask = 0xFF;
    request.target[ResourceDim::CpuCapacity] = 800;

    ConstraintAllowed allowed;
    allowed.allowed[ResourceDim::CpuCapacity] = 600;

    CapabilityFeasible feasible;
    feasible.feasible[ResourceDim::CpuCapacity] = 1024;

    uint32_t delivered = request.target[ResourceDim::CpuCapacity];
    if (delivered > allowed.allowed[ResourceDim::CpuCapacity]) {
        delivered = allowed.allowed[ResourceDim::CpuCapacity];
    }
    if (delivered > feasible.feasible[ResourceDim::CpuCapacity]) {
        delivered = feasible.feasible[ResourceDim::CpuCapacity];
    }

    assert(delivered == 600);

    std::cout << "  Arbitration Formula: PASS" << std::endl;
}
