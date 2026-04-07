#pragma once

// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。
// Trace 环形缓冲（仅 PERFENGINE_ENABLE_DSE_TRACE=1 时参与编译，见 TraceBuffer.cpp）。

#include <atomic>
#include <cstddef>

#include "TraceTypes.h"

namespace vendor::transsion::perfengine::dse {

class TraceBuffer {
public:
    static constexpr size_t kBufferSize = 4096;

    TraceBuffer();
    ~TraceBuffer() = default;

    WriteResult Write(const TraceRecord &record);
    bool Read(TraceRecord &record);

    size_t GetCount() const { return count_.load(std::memory_order_relaxed); }
    size_t GetCapacity() const { return kBufferSize; }
    bool IsEmpty() const { return count_.load(std::memory_order_relaxed) == 0; }
    bool IsFull() const { return count_.load(std::memory_order_relaxed) >= kBufferSize; }

    const TraceRecord *GetRecords() const { return buffer_; }
    bool PeekLatest(size_t reverseIndex, TraceRecord &record) const;

    void Clear();
    void ResetReadCursor() { readCursor_.store(0, std::memory_order_relaxed); }

    size_t Dump(TraceRecord *buffer, size_t maxSize) const;

    TraceStats GetStats() const;
    void ResetStats();

private:
    TraceRecord buffer_[kBufferSize];
    std::atomic<size_t> writeCursor_{0};
    std::atomic<size_t> readCursor_{0};
    std::atomic<size_t> count_{0};

    std::atomic<size_t> totalWrites_{0};
    std::atomic<size_t> totalOverwrites_{0};
    std::atomic<size_t> totalLostRecords_{0};
};

}    // namespace vendor::transsion::perfengine::dse
