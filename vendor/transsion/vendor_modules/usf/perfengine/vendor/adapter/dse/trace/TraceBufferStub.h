#pragma once

// 无环形缓冲占位：与 TraceBuffer API 对齐，供 PERFENGINE_ENABLE_DSE_TRACE=0
// 时链接，不参与大缓冲编译。

#include <atomic>
#include <cstddef>

#include "TraceTypes.h"

namespace vendor::transsion::perfengine::dse {

class TraceBuffer {
public:
    static constexpr size_t kBufferSize = 0;

    TraceBuffer() = default;
    ~TraceBuffer() = default;

    WriteResult Write(const TraceRecord &record) {
        (void)record;
        WriteResult r{};
        r.success = false;
        return r;
    }

    bool Read(TraceRecord &record) {
        (void)record;
        return false;
    }

    size_t GetCount() const { return 0; }
    size_t GetCapacity() const { return 0; }
    bool IsEmpty() const { return true; }
    bool IsFull() const { return false; }

    const TraceRecord *GetRecords() const { return nullptr; }

    bool PeekLatest(size_t reverseIndex, TraceRecord &record) const {
        (void)reverseIndex;
        (void)record;
        return false;
    }

    void Clear() {}

    void ResetReadCursor() {}

    size_t Dump(TraceRecord *buffer, size_t maxSize) const {
        (void)buffer;
        (void)maxSize;
        return 0;
    }

    TraceStats GetStats() const {
        TraceStats s{};
        return s;
    }

    void ResetStats() {}
};

}    // namespace vendor::transsion::perfengine::dse
