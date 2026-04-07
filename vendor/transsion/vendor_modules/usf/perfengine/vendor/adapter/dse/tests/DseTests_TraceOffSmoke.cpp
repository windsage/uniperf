// 仅用于 PERFENGINE_PRODUCTION=ON + stub 链接的冒烟目标；验证宏关闭时环形缓冲与录制实现不参与编译后
// API 仍可用。
#if PERFENGINE_ENABLE_DSE_TRACE
#error "Build this target only with PERFENGINE_PRODUCTION=ON (trace stub + hash)."
#endif

#include <cassert>
#include <cstdint>

#include "trace/TraceRecorder.h"
#include "types/ConstraintTypes.h"

namespace vendor::transsion::perfengine::dse {

static int RunTraceOffSmoke() {
    static_assert(TraceBuffer::kBufferSize == 0, "stub trace must not embed ring buffer storage");

    auto &recorder = TraceRecorder::Instance();
    TraceConfig cfg{};
    recorder.Init(cfg);

    assert(!recorder.ShouldRecord(1));
    assert(!recorder.IsReplayHashTraceEnabled());

    recorder.RecordEvent(TracePoint::EventEntry, 42, 7);
    assert(recorder.GetBuffer().GetCount() == 0);

    SchedEvent ev{};
    ev.timestamp = 123;
    ConstraintSnapshot c{};
    CapabilityFeasible cap{};
    (void)recorder.ComputeInputHash(ev);
    (void)recorder.ComputeStateHash(c, cap);
    const ReplayHash built = recorder.BuildReplayHash(
        0xFULL, ev, c, cap, static_cast<Generation>(2), 3, 4, 5, 6, IntentLevel::P2, nullptr);
    assert(built.decisionHash == 0xFULL);

    assert(!recorder.PersistToFile("/nonexistent/path/perfengine_trace_off_smoke.bin"));
    return 0;
}

}    // namespace vendor::transsion::perfengine::dse

int main() {
    return vendor::transsion::perfengine::dse::RunTraceOffSmoke();
}
