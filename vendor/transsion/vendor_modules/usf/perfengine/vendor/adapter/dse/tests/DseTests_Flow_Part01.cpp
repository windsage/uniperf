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

void TestResourceVector() {
    std::cout << "Testing ResourceVector..." << std::endl;

    ResourceVector v1;
    v1[ResourceDim::CpuCapacity] = 512;
    v1[ResourceDim::MemoryCapacity] = 256;

    ResourceVector v2;
    v2[ResourceDim::CpuCapacity] = 384;
    v2[ResourceDim::MemoryCapacity] = 512;

    auto minResult = ResourceVector::Min(v1, v2);
    assert(minResult[ResourceDim::CpuCapacity] == 384);
    assert(minResult[ResourceDim::MemoryCapacity] == 256);

    auto maxResult = ResourceVector::Max(v1, v2);
    assert(maxResult[ResourceDim::CpuCapacity] == 512);
    assert(maxResult[ResourceDim::MemoryCapacity] == 512);

    std::cout << "  ResourceVector: PASS" << std::endl;
}

void TestIntentLevel() {
    std::cout << "Testing IntentLevel..." << std::endl;

    assert(IntentLevelToString(IntentLevel::P1) != nullptr);
    assert(IntentLevelToString(IntentLevel::P2) != nullptr);

    std::cout << "  IntentLevel: PASS" << std::endl;
}

void TestTimeMode() {
    std::cout << "Testing TimeMode..." << std::endl;

    assert(TimeModeToString(TimeMode::Burst) != nullptr);
    assert(TimeModeToString(TimeMode::Steady) != nullptr);

    std::cout << "  TimeMode: PASS" << std::endl;
}

void TestStateVault() {
    std::cout << "Testing StateVault..." << std::endl;

    StateVault vault;
    assert(vault.GetGeneration() == 0);

    ConstraintSnapshot snapshot;
    snapshot.thermalLevel = 2;
    vault.UpdateConstraint(snapshot);

    assert(vault.GetGeneration() > 0);
    assert(vault.GetDirtyBits().HasConstraint());

    std::cout << "  StateVault: PASS" << std::endl;
}

void TestStateVaultReleaseSessionNonTransactionSingleSlot() {
    std::cout << "Testing StateVault ReleaseSession Non-Transaction Single Slot..." << std::endl;

    StateVault vault;
    IntentContract intent{};
    intent.level = IntentLevel::P2;
    vault.UpdateIntent(intent, 4242);

    TemporalContract lease{};
    lease.timeMode = TimeMode::Steady;
    lease.leaseStartTs = 1;
    lease.leaseEndTs = 2;
    vault.UpdateLease(lease, 4242);

    vault.ReleaseSession(4242);

    auto view = vault.Snapshot();
    assert(view.Intent().activeSessionId == 0);
    assert(view.Lease().activeLeaseId == 0);

    std::cout << "  StateVault ReleaseSession Non-Transaction Single Slot: PASS" << std::endl;
}

void TestStateVaultReadActiveSessionId() {
    std::cout << "Testing StateVault ReadActiveSessionId..." << std::endl;

    StateVault vault;

    // 测试1：初始状态应返回0
    assert(vault.ReadActiveSessionId() == 0);
    assert(vault.PeekActiveSessionIdFromCurrentSlot() == 0);

    // 测试2：设置intent后应返回正确的sessionId
    IntentContract intent;
    intent.level = IntentLevel::P1;
    const SessionId testSessionId = 12345;
    vault.UpdateIntent(intent, testSessionId);

    SessionId readId = vault.ReadActiveSessionId();
    assert(readId == testSessionId);
    assert(vault.PeekActiveSessionIdFromCurrentSlot() == testSessionId);

    // 测试3：UpdateIntent使用ReadActiveSessionId（boundSession=0时保持原值）
    IntentContract newIntent;
    newIntent.level = IntentLevel::P2;
    vault.UpdateIntent(newIntent, 0);    // boundSession=0，应保持原sessionId

    SessionId afterUpdateId = vault.ReadActiveSessionId();
    assert(afterUpdateId == testSessionId);    // 应保持原值

    // 测试4：boundSession非0时应更新
    const SessionId newSessionId = 67890;
    vault.UpdateIntent(newIntent, newSessionId);

    SessionId updatedId = vault.ReadActiveSessionId();
    assert(updatedId == newSessionId);

    std::cout << "  StateVault ReadActiveSessionId: PASS" << std::endl;
}

void TestStateVaultUpdateIntentOptimization() {
    std::cout << "Testing StateVault UpdateIntent Optimization..." << std::endl;

    StateVault vault;

    // 设置初始状态
    IntentContract intent1;
    intent1.level = IntentLevel::P1;
    vault.UpdateIntent(intent1, 100);

    // 验证初始sessionId
    assert(vault.ReadActiveSessionId() == 100);

    // 更新intent但保持sessionId（boundSession=0）
    IntentContract intent2;
    intent2.level = IntentLevel::P2;
    vault.UpdateIntent(intent2, 0);

    // 验证sessionId保持不变
    assert(vault.ReadActiveSessionId() == 100);

    // 通过Snapshot验证intent已更新
    auto view = vault.Snapshot();
    assert(view.Intent().contract.level == IntentLevel::P2);
    assert(view.Intent().activeSessionId == 100);

    // 更新sessionId
    vault.UpdateIntent(intent2, 200);
    assert(vault.ReadActiveSessionId() == 200);

    std::cout << "  StateVault UpdateIntent Optimization: PASS" << std::endl;
}

void TestStateVaultGetCurrentStateRefs() {
    std::cout << "Testing StateVault GetCurrentStateRefs..." << std::endl;

    StateVault vault;

    // 测试1：初始槽位已绑定空域快照，指针非空、约束为默认零值
    auto refs1 = vault.GetCurrentStateRefs();
    assert(refs1.constraint != nullptr);
    assert(refs1.intent != nullptr);
    assert(refs1.constraint->snapshot.thermalLevel == 0);

    // 测试2：设置constraint后应能获取指针
    ConstraintSnapshot constraint;
    constraint.thermalLevel = 3;
    vault.UpdateConstraint(constraint);

    auto refs2 = vault.GetCurrentStateRefs();
    assert(refs2.constraint != nullptr);
    assert(refs2.constraint->snapshot.thermalLevel == 3);

    // 测试3：设置多个状态后批量获取
    IntentContract intent;
    intent.level = IntentLevel::P1;
    vault.UpdateIntent(intent, 999);

    SceneSemantic semantic;
    semantic.kind = SceneKind::Launch;
    vault.UpdateScene(semantic, 1);

    auto refs3 = vault.GetCurrentStateRefs();
    assert(refs3.constraint != nullptr);
    assert(refs3.intent != nullptr);
    assert(refs3.scene != nullptr);

    // 测试4：验证数据一致性
    assert(refs3.intent->activeSessionId == 999);
    assert(refs3.intent->contract.level == IntentLevel::P1);
    assert(refs3.scene->semantic.kind == SceneKind::Launch);

    // 测试5：验证与公开 Snapshot 视图一致
    const auto snapshot = vault.Snapshot();
    assert(refs3.constraint->snapshot.thermalLevel == snapshot.Constraint().snapshot.thermalLevel);
    assert(refs3.intent->activeSessionId == snapshot.Intent().activeSessionId);
    assert(refs3.scene->semantic.kind == snapshot.Scene().semantic.kind);

    std::cout << "  StateVault GetCurrentStateRefs: PASS" << std::endl;
}

void TestSafetyController() {
    std::cout << "Testing SafetyController..." << std::endl;

    SafetyConfig config;
    config.defaultDisabled = true;
    config.allowGrayscale = true;
    config.grayscalePercent = 50;

    SafetyController controller(config);

    assert(controller.IsDisabled());
    assert(!controller.IsEnabled());
    assert(!controller.CanProcess());

    // Disabled + grayscale
    controller.SetGrayscalePercent(0);
    assert(!controller.ShouldUseGrayscale(11));
    controller.SetGrayscalePercent(120);    // clamp to 100
    assert(controller.GetGrayscalePercent() == 100);
    assert(controller.ShouldUseGrayscale(11));
    assert(controller.CanProcessWithGrayscale(11));

    // Enable path and duplicate enable reject
    assert(controller.Enable());
    assert(controller.IsEnabled());
    assert(!controller.Enable());
    assert(controller.CanProcess());

    // Fallback path
    assert(controller.RequestFallback(FallbackReason::ThermalEmergency));
    assert(controller.IsFallback());
    assert(controller.GetFallbackReason() == FallbackReason::ThermalEmergency);
    assert(!controller.RequestFallback(FallbackReason::RuntimeError));
    assert(!controller.CanProcessWithGrayscale(15));

    // Enable from fallback should clear fallback reason
    assert(controller.Enable());
    assert(controller.IsEnabled());
    assert(controller.GetFallbackReason() == FallbackReason::None);

    // Disable path and disable from fallback path
    assert(controller.Disable());
    assert(controller.IsDisabled());
    assert(!controller.Disable());
    assert(controller.Enable());
    assert(controller.RequestFallback(FallbackReason::UserRequest));
    assert(controller.Disable());
    assert(controller.IsDisabled());
    assert(controller.GetFallbackReason() == FallbackReason::None);

    // Exit fallback failure when not fallback
    assert(!controller.ExitFallback());

    // Explicit fallback disabled by config
    SafetyConfig noFallbackConfig = config;
    noFallbackConfig.allowExplicitFallback = false;
    SafetyController noFallback(noFallbackConfig);
    assert(noFallback.Enable());
    assert(!noFallback.RequestFallback(FallbackReason::ConfigError));

    // UID allowlist/denylist and high intent authorization flow
    SafetyConfig uidConfig;
    uidConfig.defaultDisabled = false;
    uidConfig.enableUidWhitelist = true;
    uidConfig.enableUidBlacklist = true;
    SafetyController uidController(uidConfig);
    uidController.AddUidToWhitelist(20001);
    uidController.AddUidToBlacklist(30001);

    assert(uidController.IsUidAllowed(20001));
    assert(!uidController.IsUidAllowed(30001));
    assert(!uidController.IsUidAllowed(100));    // whitelist enabled and non-empty
    assert(uidController.IsUidAuthorizedForHighIntent(20001, 2));
    assert(!uidController.IsUidAuthorizedForHighIntent(30001, 2));
    assert(!uidController.IsUidAuthorizedForHighIntent(30001, 99));    // blacklist first
    uidController.SetEnableUidWhitelist(false);
    assert(uidController.IsUidAuthorizedForHighIntent(20002, 99));    // non-high-intent bypass
    uidController.SetEnableUidWhitelist(true);

    uidController.RemoveUidFromWhitelist(20001);
    uidController.ClearWhitelist();
    uidController.SetEnableUidWhitelist(false);
    assert(uidController.IsUidAllowed(12345));

    uidController.RemoveUidFromBlacklist(30001);
    uidController.ClearBlacklist();
    uidController.SetEnableUidBlacklist(false);
    assert(uidController.IsUidAllowed(30001));

    // String conversion: keep API-level checks only.
    assert(std::strcmp(SafetyController::StateToString(DseState::Enabled), "Enabled") == 0);
    assert(std::strcmp(SafetyController::StateToString(static_cast<DseState>(255)), "Unknown") ==
           0);
    assert(std::strcmp(SafetyController::FallbackReasonToString(FallbackReason::RuntimeError),
                       "RuntimeError") == 0);
    assert(std::strcmp(SafetyController::FallbackReasonToString(static_cast<FallbackReason>(255)),
                       "Unknown") == 0);

    std::cout << "  SafetyController: PASS" << std::endl;
}

void TestStabilityMechanism() {
    std::cout << "Testing StabilityMechanism..." << std::endl;

    StabilityParams params;
    params.observationWindowMs = 100;
    params.minResidencyMs = 50;
    params.steadyEnterMerges = 2;

    StabilityMechanism stability(params);

    StabilityInput input;
    input.currentTimeMs = 0;
    input.mergeCount = 1;
    input.semanticChanged = true;
    input.intentChanged = true;
    input.committedIntent.level = IntentLevel::P3;
    input.candidateIntent.level = IntentLevel::P2;
    input.candidateSemantic.kind = SceneKind::Playback;

    auto out = stability.Evaluate(input);
    assert(!out.shouldEnterSteady);
    assert(out.shouldHold);

    input.currentTimeMs = 150;
    input.mergeCount = 2;
    input.semanticChanged = true;
    input.intentChanged = true;
    out = stability.Evaluate(input);
    assert(out.shouldEnterSteady);
    assert(!out.shouldHold);

    std::cout << "  StabilityMechanism: PASS" << std::endl;
}

void TestStabilityMechanismRestoreStateByValue() {
    std::cout << "Testing StabilityMechanism RestoreState By Value..." << std::endl;

    StabilityParams params;
    StabilityMechanism mech(params);
    mech.Reset();
    assert(!mech.IsInSteady());

    StabilityState state{};
    state.inSteady = true;
    state.inObservation = false;
    state.lastUpdateMs = 42;
    state.steadyEnterTimeMs = 40;
    state.pendingSemantic.kind = SceneKind::Playback;
    state.pendingIntent.level = IntentLevel::P2;

    mech.RestoreState(std::move(state));
    assert(mech.IsInSteady());
    assert(!mech.IsInObservation());

    std::cout << "  StabilityMechanism RestoreState By Value: PASS" << std::endl;
}

void TestStabilityMechanismAppliesResidencyToP3() {
    std::cout << "Testing StabilityMechanism P3 Residency..." << std::endl;

    StabilityParams params;
    params.observationWindowMs = 0;
    params.minResidencyMs = 100;
    params.steadyEnterMerges = 1;
    params.updateThrottleMs = 0;

    StabilityMechanism stability(params);

    StabilityInput input;
    input.currentTimeMs = 0;
    input.mergeCount = 1;
    input.semanticChanged = true;
    input.intentChanged = true;
    input.committedIntent.level = IntentLevel::P4;
    input.candidateIntent.level = IntentLevel::P3;
    input.candidateSemantic.kind = SceneKind::Background;

    auto out = stability.Evaluate(input);
    assert(out.shouldEnterSteady);
    assert(!out.shouldHold);

    input.currentTimeMs = 50;
    input.mergeCount = 2;
    input.committedIntent.level = IntentLevel::P3;
    input.candidateIntent.level = IntentLevel::P4;
    input.candidateSemantic.kind = SceneKind::Download;

    out = stability.Evaluate(input);
    assert(out.shouldHold);
    assert(!out.shouldEnterSteady);

    std::cout << "  StabilityMechanism P3 Residency: PASS" << std::endl;
}

void TestIntentInheritance() {
    std::cout << "Testing IntentInheritance..." << std::endl;

    InheritancePolicy policy;
    policy.maxDurationMs = 2000;
    policy.allowP1Inherit = true;

    IntentInheritance inheritance(policy);

    InheritanceContext ctx;
    ctx.requesterIntent = IntentLevel::P1;
    ctx.holderOriginalIntent = IntentLevel::P3;

    assert(inheritance.ShouldTrigger(ctx));
    ctx.requesterIntent = IntentLevel::P3;
    assert(!inheritance.ShouldTrigger(ctx));    // only P1/P2 can trigger
    ctx.requesterIntent = IntentLevel::P1;

    auto result = inheritance.Trigger(1, 2, IntentLevel::P3, IntentLevel::P1, 1000);
    assert(result.active);
    assert(result.holderEffectiveIntent == IntentLevel::P1);

    assert(inheritance.ShouldRevoke(result, 3500));

    // Explicit-release policy should block time-based revoke.
    InheritancePolicy explicitReleasePolicy = policy;
    explicitReleasePolicy.requireExplicitRelease = true;
    IntentInheritance inheritance2(explicitReleasePolicy);
    auto result2 = inheritance2.Trigger(11, 22, IntentLevel::P4, IntentLevel::P2, 2000);
    assert(!inheritance2.ShouldRevoke(result2, 5000));
    inheritance2.Revoke(result2);
    assert(!result2.active);

    DependencyState dep{};
    dep.active = true;
    dep.holderSceneId = 77;
    dep.requesterSceneId = 78;
    dep.holderOriginalIntent = IntentLevel::P4;
    dep.inheritedIntent = IntentLevel::P2;
    dep.inheritStartTimeMs = 100;
    dep.inheritExpireTimeMs = 300;
    inheritance2.RestoreFromDependencyState(dep);
    assert(inheritance2.GetContext().active);
    assert(inheritance2.GetContext().holderSceneId == 77);
    assert(inheritance2.CheckHierarchyDepth(0));
    assert(!inheritance2.CheckHierarchyDepth(explicitReleasePolicy.maxHierarchyDepth));
    assert(inheritance2.ComputeEffectiveIntent(IntentLevel::P4, IntentLevel::P2) ==
           IntentLevel::P2);
    assert(inheritance2.ComputeEffectiveIntent(IntentLevel::P2, IntentLevel::P4) ==
           IntentLevel::P2);

    std::cout << "  IntentInheritance: PASS" << std::endl;
}

void TestProfileSpec() {
    std::cout << "Testing ProfileSpec..." << std::endl;

    EntryProfileSpec entry;
    assert(entry.kind == ProfileKind::Entry);
    assert(entry.resourceMask == 0x0F);
    assert(entry.traceLevel == 0);

    MainProfileSpec main;
    assert(main.kind == ProfileKind::Main);
    assert(main.resourceMask == 0x7F);
    assert(main.traceLevel == 1);

    FlagshipProfileSpec flagship;
    assert(flagship.kind == ProfileKind::Flagship);
    assert(flagship.resourceMask == 0xFF);
    assert(flagship.traceLevel == 2);

    std::cout << "  ProfileSpec: PASS" << std::endl;
}

void TestStageRunner() {
    std::cout << "Testing StageRunner..." << std::endl;

    StageRunner<TypeList<SceneStage, IntentStage>> runner;
    assert(runner.GetStageCount() == 2);
    assert(runner.GetStageName(0) != nullptr);
    assert(runner.GetStageName(1) != nullptr);

    std::cout << "  StageRunner: PASS" << std::endl;
}

void TestSceneStage() {
    std::cout << "Testing SceneStage..." << std::endl;

    SceneStage stage;
    assert(stage.Name() != nullptr);

    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    SchedEvent event;
    event.action = 1;
    event.rawFlags = 0x12;
    ctx.event = &event;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(intermediates.hasSceneSemantic);
    assert(ctx.GetSceneSemantic().kind == SceneKind::Launch);
    assert(ctx.GetSceneSemantic().phase == ScenePhase::Active);

    std::cout << "  SceneStage: PASS" << std::endl;
}

void TestSceneStageBackgroundAction() {
    std::cout << "Testing SceneStage Background Action..." << std::endl;

    SceneStage stage;
    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    SchedEvent event;
    event.action = static_cast<uint32_t>(EventSemanticAction::Background);
    event.rawFlags = 0x20;
    ctx.event = &event;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetSceneSemantic().kind == SceneKind::Background);
    assert(ctx.GetSceneSemantic().audible);
    assert(!ctx.GetSceneSemantic().visible);

    std::cout << "  SceneStage Background Action: PASS" << std::endl;
}

void TestIntentStage() {
    std::cout << "Testing IntentStage..." << std::endl;

    IntentStage stage;
    assert(stage.Name() != nullptr);

    StageIntermediates intermediates;
    StageContext ctx;
    ctx.intermediates = &intermediates;

    SchedEvent event;
    event.action = 1;
    ctx.event = &event;

    StateVault vault;
    auto view = vault.Snapshot();
    ctx.state = &view;

    SceneSemantic semantic;
    semantic.kind = SceneKind::Launch;
    ctx.SetSceneSemantic(semantic);

    auto out = stage.Execute(ctx);
    assert(out.success);
    assert(ctx.GetIntentContract().level == IntentLevel::P1);
    assert(ctx.GetTemporalContract().timeMode == TimeMode::Burst);

    std::cout << "  IntentStage: PASS" << std::endl;
}

void TestIntentStageDeriveResourceRequestExplicitLoader() {
    std::cout << "Testing IntentStage DeriveResourceRequest Explicit Loader..." << std::endl;

    ConfigLoader::Instance().Load(nullptr);
    SceneSemantic semantic{};
    semantic.kind = SceneKind::Launch;
    semantic.phase = ScenePhase::Enter;
    semantic.visible = true;

    IntentContract intent{};
    intent.level = IntentLevel::P1;

    auto &loader = ConfigLoader::Instance();
    const uint32_t mask = MainProfileSpec{}.resourceMask;
    const ResourceRequest withRef =
        IntentStage::DeriveResourceRequest(semantic, intent, mask, loader);
    const ResourceRequest withDefault = IntentStage::DeriveResourceRequest(semantic, intent, mask);
    assert(withRef.resourceMask == withDefault.resourceMask);
    assert(withRef.min[ResourceDim::CpuCapacity] == withDefault.min[ResourceDim::CpuCapacity]);
    assert(withRef.target[ResourceDim::CpuCapacity] ==
           withDefault.target[ResourceDim::CpuCapacity]);

    std::cout << "  IntentStage DeriveResourceRequest Explicit Loader: PASS" << std::endl;
}
