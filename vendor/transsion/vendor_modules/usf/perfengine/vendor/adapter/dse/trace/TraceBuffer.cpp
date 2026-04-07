#include "TraceBuffer.h"

namespace vendor::transsion::perfengine::dse {

TraceBuffer::TraceBuffer() {
    Clear();
}

WriteResult TraceBuffer::Write(const TraceRecord &record) {
    WriteResult result;
    result.success = true;
    result.overwritten = false;
    result.lostRecords = 0;

    size_t currentWrite = writeCursor_.load(std::memory_order_relaxed);
    size_t currentCount = count_.load(std::memory_order_relaxed);

    size_t index = currentWrite % kBufferSize;
    buffer_[index] = record;

    writeCursor_.store(currentWrite + 1, std::memory_order_release);

    if (currentCount >= kBufferSize) {
        result.overwritten = true;
        result.lostRecords = 1;

        totalOverwrites_.fetch_add(1, std::memory_order_relaxed);
        totalLostRecords_.fetch_add(1, std::memory_order_relaxed);

        size_t currentRead = readCursor_.load(std::memory_order_relaxed);
        if (currentRead > 0) {
            readCursor_.store(currentRead - 1, std::memory_order_release);
        }
    } else {
        count_.fetch_add(1, std::memory_order_release);
    }

    totalWrites_.fetch_add(1, std::memory_order_relaxed);

    return result;
}

bool TraceBuffer::Read(TraceRecord &record) {
    size_t currentCount = count_.load(std::memory_order_acquire);
    size_t currentRead = readCursor_.load(std::memory_order_relaxed);

    if (currentCount == 0 || currentRead >= currentCount) {
        return false;
    }

    size_t currentWrite = writeCursor_.load(std::memory_order_acquire);
    size_t index = (currentWrite - currentCount + currentRead) % kBufferSize;
    record = buffer_[index];

    readCursor_.store(currentRead + 1, std::memory_order_release);

    return true;
}

bool TraceBuffer::PeekLatest(size_t reverseIndex, TraceRecord &record) const {
    const size_t currentCount = count_.load(std::memory_order_acquire);
    if (currentCount == 0 || reverseIndex >= currentCount) {
        return false;
    }

    const size_t currentWrite = writeCursor_.load(std::memory_order_acquire);
    const size_t index = (currentWrite + kBufferSize - reverseIndex - 1) % kBufferSize;
    record = buffer_[index];
    return true;
}

void TraceBuffer::Clear() {
    writeCursor_.store(0, std::memory_order_relaxed);
    readCursor_.store(0, std::memory_order_relaxed);
    count_.store(0, std::memory_order_relaxed);
}

size_t TraceBuffer::Dump(TraceRecord *buffer, size_t maxSize) const {
    if (buffer == nullptr || maxSize == 0) {
        return 0;
    }

    size_t currentCount = count_.load(std::memory_order_acquire);
    size_t currentWrite = writeCursor_.load(std::memory_order_acquire);
    size_t toCopy = (currentCount < maxSize) ? currentCount : maxSize;

    for (size_t i = 0; i < toCopy; ++i) {
        size_t index = (currentWrite - currentCount + i) % kBufferSize;
        buffer[i] = buffer_[index];
    }

    return toCopy;
}

TraceStats TraceBuffer::GetStats() const {
    TraceStats stats;
    stats.totalWrites = totalWrites_.load(std::memory_order_relaxed);
    stats.totalOverwrites = totalOverwrites_.load(std::memory_order_relaxed);
    stats.totalLostRecords = totalLostRecords_.load(std::memory_order_relaxed);
    stats.currentCount = count_.load(std::memory_order_relaxed);
    stats.capacity = kBufferSize;
    return stats;
}

void TraceBuffer::ResetStats() {
    totalWrites_.store(0, std::memory_order_relaxed);
    totalOverwrites_.store(0, std::memory_order_relaxed);
    totalLostRecords_.store(0, std::memory_order_relaxed);
}

}    // namespace vendor::transsion::perfengine::dse
