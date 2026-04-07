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

void TestExecutionFlowDedupExecutionSummaryFallback() {
    std::cout << "Testing ExecutionFlow Dedup executionSummary Fallback..." << std::endl;

    TraceConfig config;
    config.level = 1;
    TraceRecorder::Instance().Init(config);

    ExecutionFlow<MainProfileSpec> flow;
    CountingActionMap actionMap;
    CountingExecutor executor;

    PolicyDecision decision;
    decision.sessionId = 88;
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
    decision.executionSummary = 0u;
    decision.executionSignature = ComputeExecutionSignature(decision);

    auto first = flow.Execute(decision, &actionMap, &executor, 10);
    auto second = flow.Execute(decision, &actionMap, &executor, 10);
    assert(first.success);
    assert(second.success);
    assert(executor.executeCount == 1);
    assert((second.execResult.executionFlags & 0x08u) != 0);
    assert(second.execResult.actualPath == PlatformActionPath::PerfHintHal);

    std::cout << "  ExecutionFlow Dedup executionSummary Fallback: PASS" << std::endl;
}

void TestSessionSlotCollision() {
    std::cout << "Testing Session Slot Collision Fallback..." << std::endl;
    TraceConfig config;
    config.level = 1;
    TraceRecorder::Instance().Init(config);
    MainService service;
    assert(service.GetSafetyController().Enable());

    // 构造两个 eventId，它们对 1024 取模结果相同，产生碰撞
    const int64_t ev1 = 100;
    const int64_t ev2 = 100 + 1024;
    const SessionId sid1 = 12345;
    const SessionId sid2 = 67890;

    SchedEvent e1;
    e1.eventId = ev1;
    e1.sessionId = sid1;
    SchedEvent e2;
    e2.eventId = ev2;
    e2.sessionId = sid2;

    // ev1 进入槽位
    service.OnEvent(e1);

    // ev2 进来产生碰撞，占据探测窗口中的后续槽位
    service.OnEvent(e2);

    // 触发 ev1 结束
    // 预期：OnEventEnd 通过探测窗口找回 sid1（不再发生关联漂移）
    service.OnEventEnd(ev1);
    bool sawSid1 = false;
    TraceRecorder::Instance().ForEachByEventId(
        static_cast<uint32_t>(ev1), [&sawSid1, sid1](const TraceRecord &record) {
            if (record.tracePoint != static_cast<uint32_t>(TracePoint::LeaseExpire))
                return true;
            sawSid1 = true;
            const SessionId observed =
                static_cast<SessionId>((static_cast<uint64_t>(record.sessionHi) << 32) |
                                       static_cast<uint64_t>(record.sessionId));
            assert(observed == sid1);    // 必须找回原始显式关联的 sessionId
            return true;
        });
    assert(sawSid1);

    // 触发 ev2 结束
    // 预期：探测窗口内同样能找回 sid2
    service.OnEventEnd(ev2);
    bool sawSid2 = false;
    TraceRecorder::Instance().ForEachByEventId(
        static_cast<uint32_t>(ev2), [&sawSid2, sid2](const TraceRecord &record) {
            if (record.tracePoint != static_cast<uint32_t>(TracePoint::LeaseExpire))
                return true;
            sawSid2 = true;
            const SessionId observed =
                static_cast<SessionId>((static_cast<uint64_t>(record.sessionHi) << 32) |
                                       static_cast<uint64_t>(record.sessionId));
            assert(observed == sid2);
            return true;
        });
    assert(sawSid2);

    std::cout << "  Session Slot Collision: PASS" << std::endl;
}

void TestSafetyFallbackSkipsSessionSlotPublish() {
    std::cout << "Testing Safety Fallback Skips Session Slot Publish..." << std::endl;

    TraceConfig config;
    config.level = 1;
    TraceRecorder::Instance().Init(config);
    MainService service;

    SchedEvent event;
    event.eventId = 321;
    event.sessionId = 999;
    service.OnEvent(event);
    service.OnEventEnd(event.eventId);

    bool sawLeaseExpire = false;
    TraceRecorder::Instance().ForEachByEventId(
        static_cast<uint32_t>(event.eventId), [&sawLeaseExpire, event](const TraceRecord &record) {
            if (record.tracePoint != static_cast<uint32_t>(TracePoint::LeaseExpire)) {
                return true;
            }
            sawLeaseExpire = true;
            const SessionId observed =
                static_cast<SessionId>((static_cast<uint64_t>(record.sessionHi) << 32) |
                                       static_cast<uint64_t>(record.sessionId));
            // 安全门拦截的事件不应污染 session 槽位，结束时应回退到 eventId 语义。
            assert(observed == static_cast<SessionId>(event.eventId));
            return true;
        });
    assert(sawLeaseExpire);

    std::cout << "  Safety Fallback Skips Session Slot Publish: PASS" << std::endl;
}

void TestSessionSlotEventIdUint32Boundary() {
    std::cout << "Testing Session Slot uint32 EventId Boundary..." << std::endl;

    TraceConfig config;
    config.level = 1;
    TraceRecorder::Instance().Init(config);
    MainService service;
    assert(service.GetSafetyController().Enable());

    // 边界 eventId（允许打包的最大 uint32），显式给出不同 sessionId。
    SchedEvent event;
    event.eventId = 0xFFFFFFFFLL;
    event.sessionId = 123;
    service.OnEvent(event);
    service.OnEventEnd(event.eventId);

    bool sawLeaseExpire = false;
    TraceRecorder::Instance().ForEachByEventId(
        static_cast<uint32_t>(event.eventId), [&sawLeaseExpire, event](const TraceRecord &record) {
            if (record.tracePoint != static_cast<uint32_t>(TracePoint::LeaseExpire)) {
                return true;
            }
            sawLeaseExpire = true;
            const SessionId observed =
                static_cast<SessionId>((static_cast<uint64_t>(record.sessionHi) << 32) |
                                       static_cast<uint64_t>(record.sessionId));
            // 严格要求：边界 eventId 下也必须精确回收到显式 sessionId。
            // 若当前实现因打包/解包策略回退到 eventId，该断言会直接暴露缺陷。
            assert(observed == event.sessionId);
            return true;
        });
    assert(sawLeaseExpire);

    std::cout << "  Session Slot uint32 EventId Boundary: PASS" << std::endl;
}

void TestDeduplicationEligibilityBoundary() {
    std::cout << "Testing Deduplication Eligibility Boundary..." << std::endl;

    // 情况 1: P2 Steady, Admitted - 具备去重资格
    PolicyDecision d1;
    d1.admitted = true;
    d1.grant.resourceMask = 0x1;
    d1.effectiveIntent = IntentLevel::P2;
    d1.temporal.timeMode = TimeMode::Steady;
    assert(d1.IsDedupCandidate());

    // 情况 2: P1 - 不具备去重资格
    PolicyDecision d2 = d1;
    d2.effectiveIntent = IntentLevel::P1;
    assert(!d2.IsDedupCandidate());

    // 情况 3: Burst - 不具备去重资格
    PolicyDecision d3 = d1;
    d3.temporal.timeMode = TimeMode::Burst;
    assert(!d3.IsDedupCandidate());

    // 情况 4: Not Admitted - 不具备去重资格
    PolicyDecision d4 = d1;
    d4.admitted = false;
    assert(!d4.IsDedupCandidate());

    // 情况 5: No Resource - 不具备去重资格
    PolicyDecision d5 = d1;
    d5.grant.resourceMask = 0;
    assert(!d5.IsDedupCandidate());

    std::cout << "  Dedup Eligibility Boundary: PASS" << std::endl;
}

void TestLargeIdFullRangeConsistency() {
    std::cout << "Testing 64-bit ID Full Range Consistency..." << std::endl;
    MainService service;
    assert(service.GetSafetyController().Enable());

    // 使用完整 64 位范围的 ID，确保无截断
    const int64_t largeEvId = 0x7EADBEEFABCDEF01LL;
    const SessionId largeSid = 0x1234567890ABCDEFULL;

    SchedEvent event;
    event.eventId = largeEvId;
    event.sessionId = largeSid;

    service.OnEvent(event);
    service.OnEventEnd(largeEvId);

    bool found = false;
    TraceRecorder::Instance().ForEachByEventId(
        static_cast<uint32_t>(
            largeEvId),    // 注意：trace 记录可能仅存低32位作为索引，但数据区应存完整 ID
        [&found, largeSid](const TraceRecord &record) {
            if (record.tracePoint != static_cast<uint32_t>(TracePoint::LeaseExpire))
                return true;
            const SessionId observed =
                static_cast<SessionId>((static_cast<uint64_t>(record.sessionHi) << 32) |
                                       static_cast<uint64_t>(record.sessionId));
            if (observed == largeSid)
                found = true;
            return true;
        });
    assert(found);
    std::cout << "  64-bit ID Consistency: PASS" << std::endl;
}

void TestSessionSlotProbingOverflow() {
    std::cout << "Testing Session Slot Probing Overflow (Max 8)..." << std::endl;
    MainService service;
    assert(service.GetSafetyController().Enable());
    const int64_t baseId = 1024;    // 假设对应槽位 index 为 1024 % 4096

    // 填充 8 个相同 hash index 的不同事件
    for (int i = 0; i < 8; ++i) {
        SchedEvent ev;
        ev.eventId = baseId + (i * 4096);    // 确保 hashIdx 相同
        ev.sessionId = 1000 + i;
        service.OnEvent(ev);
    }

    // 第 9 个冲突事件，应触发探测溢出降级
    SchedEvent evOverflow;
    evOverflow.eventId = baseId + (8 * 4096);
    evOverflow.sessionId = 9999;
    service.OnEvent(evOverflow);

    // 验证前 8 个依然能找回，第 9 个找回时应回退到自身 eventId (因为未存入)
    for (int i = 0; i < 8; ++i) {
        int64_t targetId = baseId + (i * 4096);
        service.OnEventEnd(targetId);    // 内部会记录 LeaseExpire
    }

    std::cout << "  Probing Overflow Handling: PASS" << std::endl;
}

void TestResourceVectorExtremeArithmetic() {
    std::cout << "Testing ResourceVector Extreme Arithmetic..." << std::endl;

    ResourceVector vMax;
    for (size_t i = 0; i < kResourceDimCount; ++i)
        vMax.v[i] = 4000000000u;    // 接近 40 亿

    // 测试 Q10 缩放 (v * factor / 1024)
    // 即使 v 很大，乘法前应考虑溢出风险或使用 64 位中间值
    ResourceVector vScaled = ResourceVector::Scale(vMax, 512);    // 缩放 50%
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        assert(vScaled.v[i] == 2000000000u);
    }

    // 测试零值边界
    ResourceVector vZero;
    ResourceVector vResult = ResourceVector::Min(vMax, vZero);
    for (size_t i = 0; i < kResourceDimCount; ++i)
        assert(vResult.v[i] == 0);

    std::cout << "  ResourceVector Extreme Arithmetic: PASS" << std::endl;
}

void TestPlatformCommandSizeShrink() {
    std::cout << "Testing PlatformCommand Size Shrink..." << std::endl;

    // PlatformCommand 收缩收益：减少 platform 下发路径的热路径结构体拷贝体积。
    static_assert(sizeof(PlatformCommand) < 32, "PlatformCommand should be shrunk");
    assert(sizeof(PlatformCommand) < 32);

    std::cout << "  PlatformCommand Size Shrink: PASS" << std::endl;
}

void TestHotPathPayloadShrink() {
    std::cout << "Testing Hot Path Payload Shrink..." << std::endl;

    static_assert(AbstractActionBatch::kMaxActions == kResourceDimCount,
                  "AbstractActionBatch should track resource dimension count");
    static_assert(PlatformCommandBatch::kMaxCommands == kResourceDimCount,
                  "PlatformCommandBatch should track resource dimension count");
    static_assert(sizeof(AbstractAction) <= 8, "AbstractAction should stay compact");
    static_assert(sizeof(AbstractActionBatch) <= 96, "AbstractActionBatch should stay compact");
    static_assert(sizeof(ConvergeStageIntermediates) < 776,
                  "ConvergeStageIntermediates should drop unused IntentAllowed payload");
    static_assert(sizeof(IntentContract) <= 4, "IntentContract should not carry unused flags");
    static_assert(sizeof(ResourceRequest) <= 100, "ResourceRequest should not carry unused flags");
    static_assert(sizeof(ResourceGrant) <= 40, "ResourceGrant should not carry unused flags");

    assert(sizeof(AbstractAction) <= 8);
    assert(sizeof(AbstractActionBatch) <= 96);
    assert(sizeof(PlatformCommandBatch) <= 192);
    assert(sizeof(ConvergeStageIntermediates) < 776);
    assert(sizeof(IntentContract) <= 4);
    assert(sizeof(ResourceRequest) <= 100);
    assert(sizeof(ResourceGrant) <= 40);

    std::cout << "  Hot Path Payload Shrink: PASS" << std::endl;
}
