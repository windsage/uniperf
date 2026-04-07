// Trace 开关与链接的翻译单元一致：本文件仅用于 PERFENGINE_ENABLE_DSE_TRACE=1 的单测目标。
#if !PERFENGINE_ENABLE_DSE_TRACE
#error \
    "DSE unit tests require full trace: configure with PERFENGINE_PRODUCTION=OFF (see CMakeLists.txt)."
#endif

#include <cassert>
#include <iostream>

#include "trace/TraceRecorder.h"

using namespace vendor::transsion::perfengine::dse;

void TestTraceBuildConfiguration() {
    std::cout << "Testing Trace build configuration (ENABLE=1, ring buffer linked)..." << std::endl;

    static_assert(TraceBuffer::kBufferSize > 0, "full trace build must link TraceBuffer.cpp");

    auto &recorder = TraceRecorder::Instance();
    assert(recorder.GetBuffer().GetCapacity() == TraceBuffer::kBufferSize);

    TraceConfig cfg{};
    cfg.level = 2;
    recorder.Init(cfg);
    recorder.Clear();

    assert(recorder.ShouldRecord(1));
    assert(recorder.IsReplayHashEnabled() == cfg.enableReplayHash);

    recorder.RecordEvent(TracePoint::EventEntry, 99, 0);
    assert(recorder.GetBuffer().GetCount() > 0);

    SchedEvent ev{};
    ev.timestamp = 1;
    ConstraintSnapshot constraint{};
    CapabilityFeasible capability{};
    const ReplayHash h =
        recorder.BuildReplayHash(0xAULL, ev, constraint, capability, static_cast<Generation>(1), 2,
                                 3, 4, 5, IntentLevel::P3, nullptr);
    (void)h;

    std::cout << "  Trace build configuration: PASS" << std::endl;
}
