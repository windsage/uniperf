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

void TestIntentStageDeriveResourceRequestMatchesIntentRules() {
    std::cout << "Testing IntentStage DeriveResourceRequest vs IntentRule..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);
    SceneSemantic semantic{};
    semantic.kind = SceneKind::Unknown;
    semantic.phase = ScenePhase::Enter;
    semantic.visible = false;

    auto &loader = ConfigLoader::Instance();
    const uint32_t profileMask = MainProfileSpec{}.resourceMask;

    const IntentLevel levels[] = {IntentLevel::P1, IntentLevel::P2, IntentLevel::P3,
                                  IntentLevel::P4};
    for (IntentLevel level : levels) {
        IntentContract intent{};
        intent.level = level;
        const IntentRule *rule = loader.FindIntentRule(level);
        assert(rule != nullptr);
        const auto request =
            IntentStage::DeriveResourceRequest(semantic, intent, profileMask, loader);

        assert(request.min[ResourceDim::CpuCapacity] == rule->cpuMin);
        assert(request.target[ResourceDim::CpuCapacity] == rule->cpuTarget);
        assert(request.max[ResourceDim::CpuCapacity] == rule->cpuMax);
        assert(request.min[ResourceDim::MemoryCapacity] == rule->memMin);
        assert(request.target[ResourceDim::MemoryCapacity] == rule->memTarget);
        assert(request.max[ResourceDim::MemoryCapacity] == rule->memMax);
    }

    std::cout << "  IntentStage DeriveResourceRequest vs IntentRule: PASS" << std::endl;
}

void TestIntentStageDeterministicContracts() {
    std::cout << "Testing IntentStage Deterministic Contracts..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);

    IntentStage stage;
    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    SchedEvent event;
    event.action = static_cast<uint32_t>(EventSemanticAction::Scroll);
    event.timestamp = 1000000000ULL;
    ctx.event = &event;

    SceneSemantic semantic;
    semantic.kind = SceneKind::Scroll;
    semantic.phase = ScenePhase::Enter;
    semantic.visible = true;
    ctx.SetSceneSemantic(semantic);

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P1);
    assert(ctx.GetTemporalContract().timeMode == TimeMode::Burst);

    semantic.kind = SceneKind::Playback;
    semantic.phase = ScenePhase::Active;
    semantic.visible = false;
    semantic.audible = true;
    semantic.continuousPerception = true;
    ctx.SetSceneSemantic(semantic);
    event.action = static_cast<uint32_t>(EventSemanticAction::Playback);
    event.timestamp = 2000000000ULL;

    out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P2);
    assert(ctx.GetTemporalContract().timeMode == TimeMode::Steady);
    assert(ctx.GetResourceRequest().target[ResourceDim::GpuCapacity] == 0);

    semantic.kind = SceneKind::Background;
    semantic.phase = ScenePhase::Active;
    semantic.visible = false;
    semantic.audible = true;
    semantic.continuousPerception = true;
    ctx.SetSceneSemantic(semantic);
    event.action = static_cast<uint32_t>(EventSemanticAction::Background);
    event.timestamp = 3000000000ULL;

    out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P2);
    assert(ctx.GetTemporalContract().timeMode == TimeMode::Steady);

    std::cout << "  IntentStage Deterministic Contracts: PASS" << std::endl;
}

void TestEnvelopeStage() {
    std::cout << "Testing EnvelopeStage..." << std::endl;

    EnvelopeStage stage;
    assert(stage.Name() != nullptr);

    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    auto out = stage.Execute(ctx);
    assert(out.success);

    std::cout << "  EnvelopeStage: PASS" << std::endl;
}

void TestEnvelopeStageNeutralBatteryScaling() {
    std::cout << "Testing EnvelopeStage Neutral Battery Scaling..." << std::endl;

    ConstraintSnapshot snapshot;
    assert(EnvelopeStage::ComputeBatteryScaling(snapshot) == 1000);

    snapshot.batterySaver = true;
    assert(EnvelopeStage::ComputeBatteryScaling(snapshot) == 600);

    snapshot = ConstraintSnapshot{};
    snapshot.batteryLevel = 15;
    assert(EnvelopeStage::ComputeBatteryScaling(snapshot) == 850);

    std::cout << "  EnvelopeStage Neutral Battery Scaling: PASS" << std::endl;
}

void TestEnvelopeStageScreenOffContinuousP2() {
    std::cout << "Testing EnvelopeStage Screen-Off Continuous P2..." << std::endl;

    EnvelopeStage stage;
    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    StateVault vault;
    ConstraintSnapshot snapshot;
    snapshot.screenOn = false;
    snapshot.powerState = PowerState::Active;
    vault.UpdateConstraint(snapshot);
    auto view = vault.Snapshot();
    ctx.state = &view;

    ResourceRequest request;
    request.resourceMask = 0xFF;
    ctx.SetResourceRequest(request);

    SceneSemantic semantic;
    semantic.kind = SceneKind::Playback;
    semantic.phase = ScenePhase::Active;
    semantic.visible = false;
    semantic.audible = true;
    semantic.continuousPerception = true;
    ctx.SetSceneSemantic(semantic);

    IntentContract intent;
    intent.level = IntentLevel::P2;
    ctx.SetIntentContract(intent);

    auto out = stage.Execute(ctx);
    assert(out.success);
    const auto &allowed = ctx.GetConstraintAllowed();
    assert(allowed.allowed[ResourceDim::CpuCapacity] >= 256);
    assert(allowed.allowed[ResourceDim::NetworkBandwidth] >= 192);
    assert(allowed.allowed[ResourceDim::GpuCapacity] <= 128);

    std::cout << "  EnvelopeStage Screen-Off Continuous P2: PASS" << std::endl;
}

void TestEnvelopeStagePreservesCapabilityFeasible() {
    std::cout << "Testing EnvelopeStage Preserves Capability..." << std::endl;

    EnvelopeStage stage;
    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    StateVault vault;
    CapabilitySnapshot snapshot;
    snapshot.supportedResources = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) |
                                  (1u << static_cast<uint32_t>(ResourceDim::MemoryBandwidth));
    snapshot.capabilityFlags = kPlatformTraitPerfHintHal;
    snapshot.domains[static_cast<uint32_t>(ResourceDim::CpuCapacity)].maxCapacity = 512;
    snapshot.domains[static_cast<uint32_t>(ResourceDim::MemoryBandwidth)].maxCapacity = 640;
    vault.UpdateCapability(snapshot);
    auto view = vault.Snapshot();
    ctx.state = &view;

    ResourceRequest request;
    request.resourceMask = 0xFF;
    ctx.SetResourceRequest(request);

    SceneSemantic semantic;
    semantic.kind = SceneKind::Playback;
    semantic.phase = ScenePhase::Active;
    semantic.audible = true;
    semantic.continuousPerception = true;
    ctx.SetSceneSemantic(semantic);

    IntentContract intent;
    intent.level = IntentLevel::P2;
    ctx.SetIntentContract(intent);

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetCapabilityFeasible().feasible[ResourceDim::CpuCapacity] == 512);
    assert(ctx.GetCapabilityFeasible().feasible[ResourceDim::MemoryBandwidth] == 640);
    assert((ctx.GetCapabilityFeasible().capabilityFlags & kPlatformTraitPerfHintHal) != 0);

    std::cout << "  EnvelopeStage Preserves Capability: PASS" << std::endl;
}

void TestEnvelopeStageDoesNotSynthesizeCapability() {
    std::cout << "Testing EnvelopeStage Does Not Synthesize Capability..." << std::endl;

    EnvelopeStage stage;
    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    SchedEvent event;
    event.timestamp = 1000000000ULL;
    ctx.event = &event;

    ResourceRequest request;
    request.resourceMask = (1u << static_cast<uint32_t>(ResourceDim::CpuCapacity)) |
                           (1u << static_cast<uint32_t>(ResourceDim::MemoryBandwidth));
    ctx.SetResourceRequest(request);

    SceneSemantic semantic;
    semantic.kind = SceneKind::Playback;
    semantic.phase = ScenePhase::Active;
    semantic.audible = true;
    semantic.continuousPerception = true;
    ctx.SetSceneSemantic(semantic);

    IntentContract intent;
    intent.level = IntentLevel::P2;
    ctx.SetIntentContract(intent);

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetCapabilityFeasible().resourceMask == 0);
    assert(ctx.GetCapabilityFeasible().feasible[ResourceDim::CpuCapacity] == 0);
    assert(ctx.GetCapabilityFeasible().feasible[ResourceDim::MemoryBandwidth] == 0);

    std::cout << "  EnvelopeStage Does Not Synthesize Capability: PASS" << std::endl;
}

void TestPsiConstraintBoundary() {
    std::cout << "Testing PSI Constraint Boundary Scaling..." << std::endl;

    // 1. 验证 Level 2 差异化收缩 (70% / 60% / 60%)
    {
        ConstraintSnapshot snapshot;
        snapshot.psiLevel = 2;
        snapshot.screenOn = true;
        snapshot.powerState = PowerState::Active;

        auto allowed = EnvelopeStage::ComputeConstraintAllowed(snapshot);

        // CPU: 1024 * 0.7 = 716
        assert(allowed.allowed[ResourceDim::CpuCapacity] == 716);
        // Mem: 1024 * 0.6 = 614
        assert(allowed.allowed[ResourceDim::MemoryCapacity] == 614);
        // Storage: 1024 * 0.6 = 614
        assert(allowed.allowed[ResourceDim::StorageBandwidth] == 614);
        // GPU: 1024 (不受 PSI 直接影响)
        assert(allowed.allowed[ResourceDim::GpuCapacity] == 1024);
    }

    // 2. 验证 Level 4 极端差异化收缩 (40% / 30% / 30%)
    {
        ConstraintSnapshot snapshot;
        snapshot.psiLevel = 4;
        snapshot.screenOn = true;
        snapshot.powerState = PowerState::Active;

        auto allowed = EnvelopeStage::ComputeConstraintAllowed(snapshot);

        // CPU: 1024 * 0.4 = 409
        assert(allowed.allowed[ResourceDim::CpuCapacity] == 409);
        // Mem: 1024 * 0.3 = 307
        assert(allowed.allowed[ResourceDim::MemoryCapacity] == 307);
        // Storage: 1024 * 0.3 = 307
        assert(allowed.allowed[ResourceDim::StorageBandwidth] == 307);
    }

    // 3. 验证与热约束的复合叠加顺序 (1024 * ThermalScaling * PsiScaling)
    {
        ConstraintSnapshot snapshot;
        snapshot.thermalLevel = 2;    // thermalScaling = 750 (75%)
        snapshot.psiLevel = 2;        // psiScaling = 70% / 60%
        snapshot.screenOn = true;
        snapshot.powerState = PowerState::Active;

        auto allowed = EnvelopeStage::ComputeConstraintAllowed(snapshot);

        // CPU: 1024 * 0.75 = 768; 768 * 0.7 = 537
        // 注意：代码逻辑是 v = v * tS / 1000; v = v * psiS / 100
        assert(allowed.allowed[ResourceDim::CpuCapacity] == 537);

        // Mem: 1024 * 0.75 = 768; 768 * 0.6 = 460
        assert(allowed.allowed[ResourceDim::MemoryCapacity] == 460);
    }

    std::cout << "  PSI Constraint Boundary Scaling: PASS" << std::endl;
}

void TestPsiConstraintBoundaryScreenOffSleep() {
    std::cout << "Testing PSI Constraint (psiLevel>=2, ScreenOff, Sleep)..." << std::endl;

    ConstraintSnapshot snapshot;
    snapshot.psiLevel = 2;        // psiScaling = 70% / 60% / 60%
    snapshot.screenOn = false;    // applyScreenConstraint=true
    snapshot.powerState = PowerState::Sleep;

    const auto allowed = EnvelopeStage::ComputeConstraintAllowed(snapshot);

    // CPU: 1024 * 0.7 = 716; then screenOff(30%) => 214; then sleep(10%) => 21
    assert(allowed.allowed[ResourceDim::CpuCapacity] == 21);
    // Mem: 1024 * 0.6 = 614; then screenOff(30%) => 184; then sleep(10%) => 18
    assert(allowed.allowed[ResourceDim::MemoryCapacity] == 18);
    // Storage: 1024 * 0.6 = 614; then screenOff(30%) => 184; then sleep(10%) => 18
    assert(allowed.allowed[ResourceDim::StorageBandwidth] == 18);
    // GPU: 1024; then screenOff(30%) => 307; then sleep(10%) => 30
    assert(allowed.allowed[ResourceDim::GpuCapacity] == 30);

    std::cout << "  PSI(ScreenOff+Sleep) Boundary Scaling: PASS" << std::endl;
}

void TestPsiConstraintBoundaryScreenOffDoze() {
    std::cout << "Testing PSI Constraint (psiLevel>=2, ScreenOff, Doze)..." << std::endl;

    ConstraintSnapshot snapshot;
    snapshot.psiLevel = 2;        // psiScaling = 70% / 60% / 60%
    snapshot.screenOn = false;    // applyScreenConstraint=true
    snapshot.powerState = PowerState::Doze;

    const auto allowed = EnvelopeStage::ComputeConstraintAllowed(snapshot);

    // CPU: 716 -> screenOff(30%) => 214; then Doze(50%) => 107
    assert(allowed.allowed[ResourceDim::CpuCapacity] == 107);
    // Mem: 614 -> screenOff(30%) => 184; then Doze(50%) => 92
    assert(allowed.allowed[ResourceDim::MemoryCapacity] == 92);
    // Storage: 614 -> screenOff(30%) => 184; then Doze(50%) => 92
    assert(allowed.allowed[ResourceDim::StorageBandwidth] == 92);
    // GPU: 1024 -> screenOff(30%) => 307; then Doze(50%) => 153
    assert(allowed.allowed[ResourceDim::GpuCapacity] == 153);

    std::cout << "  PSI(ScreenOff+Doze) Boundary Scaling: PASS" << std::endl;
}

void TestPsiConstraintBoundaryScreenOffSleepLevel4() {
    std::cout << "Testing PSI Constraint (psiLevel=4, ScreenOff, Sleep)..." << std::endl;

    ConstraintSnapshot snapshot;
    snapshot.psiLevel = 4;        // psiScaling = 40% / 30% / 30%
    snapshot.screenOn = false;    // applyScreenConstraint=true
    snapshot.powerState = PowerState::Sleep;

    const auto allowed = EnvelopeStage::ComputeConstraintAllowed(snapshot);

    // CPU: 1024 * 0.4 = 409; then screenOff(30%) => 122; then sleep(10%) => 12
    assert(allowed.allowed[ResourceDim::CpuCapacity] == 12);
    // Mem: 1024 * 0.3 = 307; then screenOff(30%) => 92; then sleep(10%) => 9
    assert(allowed.allowed[ResourceDim::MemoryCapacity] == 9);
    // Storage: 1024 * 0.3 = 307; then screenOff(30%) => 92; then sleep(10%) => 9
    assert(allowed.allowed[ResourceDim::StorageBandwidth] == 9);
    // GPU: 1024 -> screenOff(30%) => 307; then sleep(10%) => 30
    assert(allowed.allowed[ResourceDim::GpuCapacity] == 30);

    std::cout << "  PSI(ScreenOff+Sleep, Level4) Boundary Scaling: PASS" << std::endl;
}

void TestConfigLoader() {
    std::cout << "Testing ConfigLoader..." << std::endl;

    auto &loader = ConfigLoader::Instance();
    assert(loader.Load(nullptr));

    const auto &params = loader.GetParams();
    assert(params.observationWindowMs > 0);
    assert(params.traceLevel == FlagshipProfileSpec{}.traceLevel);

    auto *sceneRule = loader.FindSceneRuleByKind(SceneKind::Launch);
    assert(sceneRule != nullptr);
    assert(sceneRule->kind == SceneKind::Launch);

    auto *intentRule = loader.FindIntentRule(IntentLevel::P1);
    assert(intentRule != nullptr);
    assert(intentRule->cpuTarget > 0);

    {
#if defined(PERFENGINE_PLATFORM_QCOM)
        const char *xmlPath = "/tmp/dse_config_loader_test_qcom.xml";
#elif defined(PERFENGINE_PLATFORM_MTK)
        const char *xmlPath = "/tmp/dse_config_loader_test_mtk.xml";
#else
        const char *xmlPath = "/tmp/dse_config_loader_test.xml";
#endif
        std::ofstream xml(xmlPath);
        xml << "<PerfEngine version=\"3\" platform=\"qcom\" chip=\"sm8650\">\n"
               "  <Scenario id=\"7\" name=\"VIDEO_PLAYBACK\" fps=\"60\" "
               "package=\"com.demo.player\">\n"
               "    <Param timeout=\"60000\" release=\"auto\"/>\n"
               "    <Param key=\"CPU_CLUSTER0_MIN_FREQ\" value=\"123456\"/>\n"
               "    <Param key=\"GPU_MIN_FREQ\" value=\"654321\"/>\n"
               "    <Param key=\"SCHED_BOOST\" value=\"1\"/>\n"
               "  </Scenario>\n"
               "</PerfEngine>\n";
        xml.close();

        assert(loader.Load(xmlPath));
        const SceneRule *xmlRule = loader.FindSceneRuleByKind(SceneKind::Playback);
        assert(xmlRule != nullptr);
        assert(xmlRule->defaultIntent == IntentLevel::P2);
        assert(xmlRule->defaultTimeMode == TimeMode::Steady);
    }

    std::cout << "  ConfigLoader: PASS" << std::endl;
}

void TestConfigLoaderReloadPathSelfCopy() {
    std::cout << "Testing ConfigLoader Reload Path Self-Copy..." << std::endl;

#if defined(PERFENGINE_PLATFORM_QCOM)
    const char *xmlPath = "/tmp/dse_config_reload_self_test_qcom.xml";
#elif defined(PERFENGINE_PLATFORM_MTK)
    const char *xmlPath = "/tmp/dse_config_reload_self_test_mtk.xml";
#else
    const char *xmlPath = "/tmp/dse_config_reload_self_test.xml";
#endif
    {
        std::ofstream xml(xmlPath);
        xml << "<PerfEngine version=\"2\">\n"
               "  <Scenario id=\"1\" name=\"SCROLL\" fps=\"60\">\n"
               "    <Param timeout=\"1000\"/>\n"
               "  </Scenario>\n"
               "</PerfEngine>\n";
    }

    auto &loader = ConfigLoader::Instance();
    assert(loader.Load(xmlPath));
    assert(loader.IsLoaded());
    const char *stored = loader.GetParams().configPath;
    assert(std::strcmp(stored, xmlPath) == 0);

    assert(loader.Reload());
    assert(loader.IsLoaded());
    assert(std::strcmp(loader.GetParams().configPath, xmlPath) == 0);

    assert(loader.Reload());
    assert(std::strcmp(loader.GetParams().configPath, xmlPath) == 0);

    std::cout << "  ConfigLoader Reload Path Self-Copy: PASS" << std::endl;
}
