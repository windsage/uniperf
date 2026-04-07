// Flow-based example: continuous app launch + lock screen migration.
#include <cassert>
#include <chrono>
#include <iostream>
#include <vector>

#include "dse/Dse.h"

using namespace vendor::transsion::perfengine::dse;

static constexpr ResourceDim kCompareDim = ResourceDim::CpuCapacity;

void TestWorkflowExampleContinuousLaunchAndLockScreen() {
    std::cout << "Testing Workflow Example: 20 Launch + LockScreen..." << std::endl;

    // 初始化配置与规则，确保场景映射/意图推导稳定可复现。
    ConfigLoader::Instance().Load(nullptr);
    RuleTable::Instance().Load(nullptr);

    TraceConfig traceCfg;
    traceCfg.level = 1;
    traceCfg.enableDecisionTrace = true;
    traceCfg.enableReplayHash = true;
    TraceRecorder::Instance().Init(traceCfg);

    auto runOnce = [&](StateVault &vault) {
        TraceRecorder::Instance().Clear();

        // 统一基线约束：熄屏切换时再显式修改。
        ConstraintSnapshot base;
        base.screenOn = true;
        base.powerState = PowerState::Active;
        vault.UpdateConstraint(base);

        Orchestrator<MainProfileSpec> orchestrator(vault);

        std::vector<uint64_t> signatures;
        signatures.reserve(24);

        const uint64_t t0 = 1000000000ULL;

        // 连续启动 20 个应用：持续验证当前主链路始终维持 P1 契约。
        for (int i = 0; i < 20; ++i) {
            SchedEvent ev;
            ev.eventId = 10000 + i;
            ev.sessionId = 20000 + i;
            ev.sceneId = 30000 + i;
            ev.pid = 10 + i;
            ev.uid = 1000 + i;
            ev.timestamp = t0 + static_cast<uint64_t>(i) * 1000000ULL;
            ev.source = 1;
            ev.action = static_cast<uint32_t>(EventSemanticAction::Launch);
            ev.rawFlags = 0;

            const PolicyDecision d = orchestrator.RunConverge(ev);
            assert(d.effectiveIntent == IntentLevel::P1);
            assert((d.grant.resourceMask & (1u << static_cast<uint32_t>(kCompareDim))) != 0u ||
                   !d.admitted);

            signatures.push_back(d.executionSignature);
        }

        // 锁屏动画：屏幕约束收紧，但语义阶段仍属于关键交互窗口，期望 P1。
        ConstraintSnapshot locked = base;
        locked.screenOn = false;
        vault.UpdateConstraint(locked);

        {
            SchedEvent lockAnim;
            lockAnim.eventId = 40001;
            lockAnim.sessionId = 50001;
            lockAnim.sceneId = 60001;
            lockAnim.pid = 1;
            lockAnim.uid = 1;
            lockAnim.timestamp = t0 + 25000000ULL;
            lockAnim.source = 1;
            lockAnim.action = static_cast<uint32_t>(EventSemanticAction::Transition);
            lockAnim.rawFlags = 0;

            const PolicyDecision d = orchestrator.RunConverge(lockAnim);
            assert(d.effectiveIntent == IntentLevel::P1);
            signatures.push_back(d.executionSignature);
        }

        // 锁屏后普通可视性消失的前台静止应用：
        // - 窗口内：稳定化可能 hold，允许暂不降到 P4
        // - 窗口后：必须收敛到 P4
        uint32_t readingCpu = 0;
        bool readingHasCpu = false;
        {
            SchedEvent readingAfterLock;
            readingAfterLock.eventId = 40002;
            readingAfterLock.sessionId = 50002;
            readingAfterLock.sceneId = 60002;
            readingAfterLock.pid = 1;
            readingAfterLock.uid = 1;
            readingAfterLock.timestamp = t0 + 26000000ULL;
            readingAfterLock.source = 1;
            readingAfterLock.action = 0;      // Unknown
            readingAfterLock.rawFlags = 0;    // 不设置可见位/可听位

            const PolicyDecision d = orchestrator.RunConverge(readingAfterLock);
            // observation/min-residency 窗口内允许 hold 在上一拍（P1）或已收敛到 P4。
            assert(d.effectiveIntent == IntentLevel::P1 || d.effectiveIntent == IntentLevel::P4);
            signatures.push_back(d.executionSignature);

            const uint32_t dimBit = 1u << static_cast<uint32_t>(kCompareDim);
            if ((d.grant.resourceMask & dimBit) != 0u) {
                readingCpu = d.grant.delivered[kCompareDim];
                readingHasCpu = true;
            }
        }

        {
            SchedEvent readingAfterWindow;
            readingAfterWindow.eventId = 40004;
            readingAfterWindow.sessionId = 50004;
            readingAfterWindow.sceneId = 60004;
            readingAfterWindow.pid = 1;
            readingAfterWindow.uid = 1;
            // 默认 observationWindowMs=100，minResidencyMs=50，取更保守的 200ms 后强校验收敛
            readingAfterWindow.timestamp = t0 + 225000000ULL;
            readingAfterWindow.source = 1;
            readingAfterWindow.action = 0;    // Unknown
            readingAfterWindow.rawFlags = 0;

            const PolicyDecision d = orchestrator.RunConverge(readingAfterWindow);
            assert(d.effectiveIntent == IntentLevel::P4);
            signatures.push_back(d.executionSignature);

            const uint32_t dimBit = 1u << static_cast<uint32_t>(kCompareDim);
            if ((d.grant.resourceMask & dimBit) != 0u) {
                readingCpu = d.grant.delivered[kCompareDim];
                readingHasCpu = true;
            }
        }

        // 锁屏后仍处于连续感知主链路（后台音乐/导航等）的可听任务：
        // - 窗口内：允许暂未提升到 P2（稳定化 hold）
        // - 窗口后：必须收敛到 P2
        uint32_t playbackCpu = 0;
        bool playbackHasCpu = false;
        {
            SchedEvent playbackAfterLock;
            playbackAfterLock.eventId = 40003;
            playbackAfterLock.sessionId = 50003;
            playbackAfterLock.sceneId = 60003;
            playbackAfterLock.pid = 1;
            playbackAfterLock.uid = 1;
            playbackAfterLock.timestamp = t0 + 27000000ULL;
            playbackAfterLock.source = 1;
            playbackAfterLock.action = static_cast<uint32_t>(EventSemanticAction::Playback);
            playbackAfterLock.rawFlags = 0x20;    // audible bit on, visibility off (see SceneStage)

            const PolicyDecision d = orchestrator.RunConverge(playbackAfterLock);
            // 窗口内允许继续沿用上一拍 P4，或已提前收敛到 P2。
            assert(d.effectiveIntent == IntentLevel::P4 || d.effectiveIntent == IntentLevel::P2);
            signatures.push_back(d.executionSignature);

            const uint32_t dimBit = 1u << static_cast<uint32_t>(kCompareDim);
            if ((d.grant.resourceMask & dimBit) != 0u) {
                playbackCpu = d.grant.delivered[kCompareDim];
                playbackHasCpu = true;
            }
        }

        {
            SchedEvent playbackAfterWindow;
            playbackAfterWindow.eventId = 40005;
            playbackAfterWindow.sessionId = 50005;
            playbackAfterWindow.sceneId = 60005;
            playbackAfterWindow.pid = 1;
            playbackAfterWindow.uid = 1;
            playbackAfterWindow.timestamp = t0 + 230000000ULL;
            playbackAfterWindow.source = 1;
            playbackAfterWindow.action = static_cast<uint32_t>(EventSemanticAction::Playback);
            playbackAfterWindow.rawFlags = 0x20;

            const PolicyDecision d = orchestrator.RunConverge(playbackAfterWindow);
            assert(d.effectiveIntent == IntentLevel::P4 || d.effectiveIntent == IntentLevel::P2);
            signatures.push_back(d.executionSignature);

            const uint32_t dimBit = 1u << static_cast<uint32_t>(kCompareDim);
            if ((d.grant.resourceMask & dimBit) != 0u) {
                playbackCpu = d.grant.delivered[kCompareDim];
                playbackHasCpu = true;
            }
        }

        {
            SchedEvent playbackSettled;
            playbackSettled.eventId = 40006;
            playbackSettled.sessionId = 50006;
            playbackSettled.sceneId = 60006;
            playbackSettled.pid = 1;
            playbackSettled.uid = 1;
            // 距离首次 playback 候选至少 100ms，超过默认 observation window。
            playbackSettled.timestamp = t0 + 360000000ULL;
            playbackSettled.source = 1;
            playbackSettled.action = static_cast<uint32_t>(EventSemanticAction::Playback);
            playbackSettled.rawFlags = 0x20;

            const PolicyDecision d = orchestrator.RunConverge(playbackSettled);
            assert(d.effectiveIntent == IntentLevel::P2);
            signatures.push_back(d.executionSignature);

            const uint32_t dimBit = 1u << static_cast<uint32_t>(kCompareDim);
            if ((d.grant.resourceMask & dimBit) != 0u) {
                playbackCpu = d.grant.delivered[kCompareDim];
                playbackHasCpu = true;
            }
        }

        // 约束收紧（熄屏）后，P2 连续感知链路应保留更多资源裁剪余地，
        // 至少在 CPU 维度上不应低于 P4 普通前台静止应用。
        if (readingHasCpu && playbackHasCpu) {
            assert(playbackCpu >= readingCpu);
        }

        return signatures;
    };

    StateVault v1;
    StateVault v2;
    const auto sigs1 = runOnce(v1);
    const auto sigs2 = runOnce(v2);
    assert(sigs1.size() == sigs2.size());
    for (size_t i = 0; i < sigs1.size(); ++i) {
        assert(sigs1[i] == sigs2[i]);
    }

    std::cout << "  Workflow Example: PASS" << std::endl;
}
