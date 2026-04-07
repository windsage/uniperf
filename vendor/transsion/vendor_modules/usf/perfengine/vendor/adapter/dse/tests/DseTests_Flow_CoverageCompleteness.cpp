#include <cassert>
#include <chrono>
#include <cstring>
#include <iostream>
#include <memory>
#include <thread>
#include <vector>

#include "core/stage/InheritanceStage.h"
#include "dse/Dse.h"
#include "platform/state/IPlatformStateBackend.h"
#include "platform/state/PlatformStateCommon.h"
#include "tests/DseTests_TestSupport.h"

#if defined(PERFENGINE_PLATFORM_QCOM)
#include "platform/qcom/QcomPlatformStateBackend.h"
#elif defined(PERFENGINE_PLATFORM_MTK)
#include "platform/mtk/MtkPlatformStateBackend.h"
#endif

using namespace vendor::transsion::perfengine::dse;

namespace {

struct CountingStateListener final : IStateListener {
    int callCount = 0;
    void OnStateChanged(Generation, SnapshotToken, uint32_t) noexcept override { ++callCount; }
};

void RestoreDefaultStateBackendFactory(PlatformVendor vendor) {
#if defined(PERFENGINE_PLATFORM_QCOM)
    if (vendor == PlatformVendor::Qcom) {
        PlatformRegistry::Instance().RegisterStateBackend(
            PlatformVendor::Qcom, []() { return new QcomPlatformStateBackend(); });
    }
#elif defined(PERFENGINE_PLATFORM_MTK)
    if (vendor == PlatformVendor::Mtk) {
        PlatformRegistry::Instance().RegisterStateBackend(
            PlatformVendor::Mtk, []() { return new MtkPlatformStateBackend(); });
    }
#endif
    (void)vendor;
}

struct FailingInitStateBackend final : IPlatformStateBackend {
    bool Init() noexcept override { return false; }
    bool ReadInitial(ConstraintSnapshot *, CapabilitySnapshot *) noexcept override { return false; }
    bool Start(IStateSink *) noexcept override { return false; }
    void Stop() noexcept override {}
    void UpdateMonitoringContext(const MonitoringContext &) noexcept override {}
    PlatformStateTraits GetTraits() const noexcept override { return {}; }
    const PlatformNodeSpec *GetNodeSpecs(size_t *count) const noexcept override {
        if (count) {
            *count = 0;
        }
        return nullptr;
    }
};

struct FailingReadInitialBackend final : IPlatformStateBackend {
    bool Init() noexcept override { return true; }
    bool ReadInitial(ConstraintSnapshot *, CapabilitySnapshot *) noexcept override { return false; }
    bool Start(IStateSink *) noexcept override { return false; }
    void Stop() noexcept override {}
    void UpdateMonitoringContext(const MonitoringContext &) noexcept override {}
    PlatformStateTraits GetTraits() const noexcept override { return {}; }
    const PlatformNodeSpec *GetNodeSpecs(size_t *count) const noexcept override {
        if (count) {
            *count = 0;
        }
        return nullptr;
    }
};

struct FailingStartBackend final : IPlatformStateBackend {
    bool Init() noexcept override { return true; }
    bool ReadInitial(ConstraintSnapshot *c, CapabilitySnapshot *cap) noexcept override {
        if (c) {
            InitializeConstraintSnapshotDefaults(*c);
        }
        if (cap) {
            PlatformCapability pc;
            PlatformRegistry::FillPlatformCapability(PlatformVendor::Unknown, pc);
            FillCapabilitySnapshotFromPlatformCapability(pc, *cap);
        }
        return true;
    }
    bool Start(IStateSink *) noexcept override { return false; }
    void Stop() noexcept override {}
    void UpdateMonitoringContext(const MonitoringContext &) noexcept override {}
    PlatformStateTraits GetTraits() const noexcept override { return {}; }
    const PlatformNodeSpec *GetNodeSpecs(size_t *count) const noexcept override {
        if (count) {
            *count = 0;
        }
        return nullptr;
    }
};

PlatformVendor ActiveVendorForStateBackendTests() {
#if defined(PERFENGINE_PLATFORM_QCOM)
    return PlatformVendor::Qcom;
#elif defined(PERFENGINE_PLATFORM_MTK)
    return PlatformVendor::Mtk;
#else
    return PlatformVendor::Unknown;
#endif
}

}    // namespace

void TestSafetyControllerCoverageFlows() {
    std::cout << "Testing SafetyController Coverage Flows..." << std::endl;

    SafetyConfig grayOff;
    grayOff.defaultDisabled = false;
    grayOff.allowGrayscale = false;
    grayOff.grayscalePercent = 50;
    SafetyController noGray(grayOff);
    noGray.SetGrayscalePercent(50);
    assert(!noGray.ShouldUseGrayscale(7));

    SafetyConfig mid;
    mid.defaultDisabled = false;
    mid.allowGrayscale = true;
    SafetyController midCtl(mid);
    midCtl.SetGrayscalePercent(37);
    assert(!midCtl.ShouldUseGrayscale(99));    // 99 % 100 >= 37
    assert(midCtl.ShouldUseGrayscale(10));     // 10 % 100 < 37

    SafetyController edge(mid);
    edge.SetGrayscalePercent(0);
    assert(!edge.ShouldUseGrayscale(1));
    edge.SetGrayscalePercent(100);
    assert(edge.ShouldUseGrayscale(12345));

    SafetyController fb(mid);
    assert(fb.RequestFallback(FallbackReason::ThermalEmergency));
    assert(fb.ExitFallback());
    assert(!fb.ExitFallback());
    assert(fb.RequestFallback(FallbackReason::RuntimeError));
    assert(fb.Enable());
    assert(fb.GetFallbackReason() == FallbackReason::None);

    SafetyController dis(mid);
    assert(!dis.Enable());
    assert(dis.RequestFallback(FallbackReason::UserRequest));
    assert(dis.Disable());
    assert(dis.GetFallbackReason() == FallbackReason::None);

    SafetyController enFromFb(mid);
    assert(enFromFb.RequestFallback(FallbackReason::ConfigError));
    assert(enFromFb.Enable());
    assert(enFromFb.IsEnabled());

    SafetyConfig allowFb;
    allowFb.defaultDisabled = false;
    allowFb.allowExplicitFallback = true;
    SafetyController rf(allowFb);
    assert(!rf.Enable());
    assert(rf.RequestFallback(FallbackReason::SystemUpgrade));
    assert(rf.GetFallbackReason() == FallbackReason::SystemUpgrade);

    for (int s = 0; s <= 4; ++s) {
        const char *name = SafetyController::StateToString(static_cast<DseState>(s));
        assert(name != nullptr && std::strlen(name) > 0);
    }
    assert(std::strcmp(SafetyController::StateToString(static_cast<DseState>(99)), "Unknown") == 0);

    for (int r = 0; r <= 6; ++r) {
        const char *name = SafetyController::FallbackReasonToString(static_cast<FallbackReason>(r));
        assert(name != nullptr && std::strlen(name) > 0);
    }
    assert(std::strcmp(SafetyController::FallbackReasonToString(static_cast<FallbackReason>(99)),
                       "Unknown") == 0);

    SafetyConfig uid;
    uid.defaultDisabled = false;
    uid.enableUidBlacklist = true;
    SafetyController bl(uid);
    bl.AddUidToBlacklist(5000);
    assert(!bl.IsUidAllowed(5000));
    assert(bl.IsUidAuthorizedForHighIntent(100, 0));
    assert(bl.IsUidAuthorizedForHighIntent(100, 5));
    bl.SetEnableUidBlacklist(false);
    assert(!bl.IsUidAuthorizedForHighIntent(20000, 5));
    bl.SetEnableUidWhitelist(true);
    bl.AddUidToWhitelist(20000);
    assert(bl.IsUidAuthorizedForHighIntent(20000, 5));
    assert(!bl.IsUidAuthorizedForHighIntent(20001, 5));

    std::cout << "  SafetyController Coverage Flows: PASS" << std::endl;
}

void TestInheritanceStageCoverageFlows() {
    std::cout << "Testing InheritanceStage Coverage Flows..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);

    InheritanceStage stage;
    StageContext badCtx;
    badCtx.intermediates = nullptr;
    StageOutput badOut = stage.Execute(badCtx);
    assert(!badOut.success);

    StageIntermediates inter;
    StageContext ctx;
    ctx.intermediates = &inter;
    ctx.profileResourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity));

    SchedEvent ev;
    ev.sceneId = 200;
    ev.timestamp = 5'000'000'000LL;
    ctx.event = &ev;

    StateVault vault;
    DependencyState vdep;
    vdep.active = true;
    vdep.holderSceneId = 200;
    vdep.requesterSceneId = 201;
    vdep.holderOriginalIntent = IntentLevel::P4;
    vdep.inheritedIntent = IntentLevel::P3;
    vdep.inheritStartTimeMs = 1000;
    vdep.inheritExpireTimeMs = 9000;
    vdep.depth = 1;
    vdep.kind = DependencyKind::Lock;
    vault.UpdateDependency(vdep);
    auto viewClamp = vault.Snapshot();
    ctx.state = &viewClamp;

    SceneSemantic sem;
    sem.kind = SceneKind::Playback;
    sem.phase = ScenePhase::Active;
    sem.continuousPerception = true;
    ctx.SetSceneSemantic(sem);

    IntentContract ic;
    ic.level = IntentLevel::P4;
    ctx.SetIntentContract(ic);

    TemporalContract tc;
    tc.timeMode = TimeMode::Burst;
    ctx.SetTemporalContract(tc);

    auto outClamp = stage.Execute(ctx);
    assert(outClamp.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P2);

    DependencyState depTrig;
    depTrig.active = true;
    depTrig.holderSceneId = 301;
    depTrig.requesterSceneId = 302;
    depTrig.holderOriginalIntent = IntentLevel::P4;
    depTrig.depth = 1;
    depTrig.kind = DependencyKind::Lock;
    ctx.SetDependencyState(depTrig);
    ic.level = IntentLevel::P1;
    ctx.SetIntentContract(ic);
    ev.sceneId = 302;
    ctx.state = nullptr;
    auto outReqOnly = stage.Execute(ctx);
    assert(outReqOnly.success);
    assert(ctx.GetDependencyState().active);

    ctx.SetIntentContract(ic);
    ev.sceneId = 301;
    auto outHolder = stage.Execute(ctx);
    assert(outHolder.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P1);

    DependencyState deep = depTrig;
    deep.depth = 5;
    ctx.SetDependencyState(deep);
    {
        InheritanceStage stageDepth;
        assert(!stageDepth.Execute(ctx).dirtyBits.HasDependency());
    }

    ctx.SetDependencyState(depTrig);
    ic.level = IntentLevel::P4;
    ctx.SetIntentContract(ic);
    (void)stage.Execute(ctx);

    inter = StageIntermediates{};
    vault.UpdateDependency(vdep);
    auto viewNull = vault.Snapshot();
    StageContext nullEvCtx;
    nullEvCtx.intermediates = &inter;
    nullEvCtx.profileResourceMask = ctx.profileResourceMask;
    nullEvCtx.state = &viewNull;
    nullEvCtx.SetSceneSemantic(sem);
    nullEvCtx.SetIntentContract(ic);
    nullEvCtx.event = nullptr;
    (void)stage.Execute(nullEvCtx);

    std::cout << "  InheritanceStage Coverage Flows: PASS" << std::endl;
}

void TestStateAggregatorAndServiceCoverageFlows() {
    std::cout << "Testing StateAggregator/StateService Coverage Flows..." << std::endl;

    StateVault vault;
    StateServiceMetrics metrics;
    StateAggregator agg(vault, metrics);
    StateRuleSet rules;
    rules.ruleVersion = 1;
    rules.thermal.severeThreshold = 5;
    rules.battery.saverThreshold = 20;
    agg.SetRuleSet(rules);

    StateDelta emptyMask;
    emptyMask.domainMask = 0;
    assert(!agg.OnDelta(emptyMask));
    assert(agg.GetStaleDropCount() >= 1u);

    StateDelta stale;
    stale.domainMask = ToMask(StateDomain::Thermal);
    stale.captureNs = 100;
    stale.constraint.fieldMask = ConstraintDeltaPatch::kThermalLevel;
    stale.constraint.thermalLevel = 1;
    assert(agg.UpdateSync(stale, 0));
    StateDelta older = stale;
    older.captureNs = 50;
    older.constraint.thermalLevel = 9;
    assert(!agg.UpdateSync(older, 0));

    StateDelta severeByLevel;
    severeByLevel.domainMask = ToMask(StateDomain::Thermal);
    severeByLevel.captureNs = 200;
    severeByLevel.constraint.fieldMask = ConstraintDeltaPatch::kThermalLevel;
    severeByLevel.constraint.thermalLevel = 7;
    severeByLevel.constraint.thermalSevere = false;
    assert(agg.UpdateSync(severeByLevel, 0));
    assert(agg.GetCachedConstraint().thermalSevere);

    StateDelta capScale;
    capScale.domainMask = ToMask(StateDomain::Thermal) | ToMask(StateDomain::Battery) |
                          ToMask(StateDomain::MemoryPsi);
    capScale.captureNs = 300;
    capScale.constraint.fieldMask = ConstraintDeltaPatch::kThermalScale |
                                    ConstraintDeltaPatch::kBatteryScale |
                                    ConstraintDeltaPatch::kPsiScale;
    capScale.constraint.thermalScaleQ10 = 5000;
    capScale.constraint.batteryScaleQ10 = 5000;
    capScale.constraint.psiScaleQ10 = 5000;
    assert(!agg.UpdateSync(capScale, 0));
    assert(agg.GetCachedConstraint().thermalScaleQ10 == 1024);
    assert(agg.GetCachedConstraint().batteryScaleQ10 == 1024);
    assert(agg.GetCachedConstraint().psiScaleQ10 == 1024);

    assert(agg.GetLastCaptureNs(StateDomain::Thermal) == 300u);

    StateService svc(vault);
    CountingStateListener listener;
    svc.Subscribe(&listener);

    StateDelta svcDelta;
    svcDelta.domainMask = ToMask(StateDomain::Thermal);
    svcDelta.captureNs = 400;
    svcDelta.constraint.fieldMask = ConstraintDeltaPatch::kThermalLevel;
    svcDelta.constraint.thermalLevel = 2;
    assert(svc.UpdateSync(svcDelta, 10));
    assert(listener.callCount == 1);

    svc.Unsubscribe(&listener);
    StateDelta svcDelta2 = svcDelta;
    svcDelta2.captureNs = 500;
    svcDelta2.constraint.thermalLevel = 3;
    svc.OnDelta(svcDelta2);
    assert(listener.callCount == 1);

    std::vector<std::unique_ptr<CountingStateListener>> many;
    many.reserve(StateService::kMaxListenerCount + 2);
    for (size_t i = 0; i < StateService::kMaxListenerCount + 1; ++i) {
        many.push_back(std::make_unique<CountingStateListener>());
        svc.Subscribe(many.back().get());
    }

    StateService *heap = new StateService(vault);
    delete heap;

    IStateSink *sink = new StateService(vault);
    delete sink;

    const PlatformVendor vendor = ActiveVendorForStateBackendTests();
    if (vendor != PlatformVendor::Unknown) {
        PlatformRegistry::Instance().RegisterStateBackend(
            vendor, []() { return new FailingInitStateBackend(); });
        StateVault v2;
        StateService s2(v2);
        assert(!s2.Init(vendor));
        RestoreDefaultStateBackendFactory(vendor);

        PlatformRegistry::Instance().RegisterStateBackend(
            vendor, []() { return new FailingReadInitialBackend(); });
        StateVault v3;
        StateService s3(v3);
        assert(s3.Init(vendor));
        assert(!s3.Start());
        RestoreDefaultStateBackendFactory(vendor);

        PlatformRegistry::Instance().RegisterStateBackend(
            vendor, []() { return new FailingStartBackend(); });
        StateVault v4;
        StateService s4(v4);
        assert(s4.Init(vendor));
        assert(!s4.Start());
        RestoreDefaultStateBackendFactory(vendor);

        StateVault v5;
        StateService s5(v5);
        assert(s5.Init(vendor));
        if (s5.Start()) {
            assert(s5.WaitUntilReady(200));
            s5.Stop();
        }
    }

    StateVault v6;
    StateServiceMetrics mA;
    StateServiceMetrics mB;
    StateAggregator a1(v6, mA);
    StateAggregator a2(v6, mB);
    a1.SetRuleSet(rules);
    a2.SetRuleSet(rules);
    std::atomic<bool> stop{false};
    std::atomic<uint64_t> nextNs{50'000};
    auto makeDelta = [](uint64_t ns, int level) {
        StateDelta d;
        d.domainMask = ToMask(StateDomain::Thermal);
        d.captureNs = ns;
        d.constraint.fieldMask = ConstraintDeltaPatch::kThermalLevel;
        d.constraint.thermalLevel = static_cast<uint8_t>(level);
        return d;
    };
    std::thread th1([&] {
        while (!stop.load(std::memory_order_acquire)) {
            const uint64_t ns = nextNs.fetch_add(1, std::memory_order_acq_rel);
            (void)a1.UpdateSync(makeDelta(ns, 1), 1);
        }
    });
    std::thread th2([&] {
        while (!stop.load(std::memory_order_acquire)) {
            const uint64_t ns = nextNs.fetch_add(1, std::memory_order_acq_rel);
            (void)a2.UpdateSync(makeDelta(ns, 2), 1);
        }
    });
    std::this_thread::sleep_for(std::chrono::milliseconds(80));
    stop.store(true, std::memory_order_release);
    th1.join();
    th2.join();

    std::cout << "  StateAggregator/StateService Coverage Flows: PASS" << std::endl;
}

void TestDseCoreCoverageCompletenessFlows() {
    TestSafetyControllerCoverageFlows();
    TestInheritanceStageCoverageFlows();
    TestStateAggregatorAndServiceCoverageFlows();
}
