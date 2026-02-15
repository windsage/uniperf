#pragma once

#include <atomic>
#include <cstdint>
#include <cstring>

#include "MetricDef.h"

/**
 * MetricStore.h - Lock-free shared memory metric storage
 *
 * Architecture:
 *   Writer (sysmonitor-service): calls publish() on sampling thread
 *   Reader (perfengine-adapter): calls read() directly via mmap — zero IPC
 *
 * Memory layout (inside ashmem region):
 *   [SharedHeader][MetricSlot × kMetricSlotCount]
 *
 *   SharedHeader: magic + version + writer_pid
 *   MetricSlot:   value(atomic int64) + timestampNs(atomic int64) + valid(atomic bool)
 *
 * Lock-free guarantee:
 *   Each slot is independently atomic. A reader never blocks a writer.
 *   Stale reads are possible (last-write-wins), which is acceptable for
 *   all metrics in this module (no strict ordering required).
 *
 * ashmem fd lifecycle:
 *   1. Writer calls createShm() → gets fd, maps region, stores fd.
 *   2. Writer passes fd to reader via one-time AIDL call (Part 4).
 *   3. Reader calls attachShm(fd) → maps same region read-only.
 *   4. From this point, reader calls read() with zero IPC overhead.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

// ---------------------------------------------------------------------------
// On-wire layout (shared between writer and reader processes)
// All fields must be trivially copyable and have fixed size.
// ---------------------------------------------------------------------------

static constexpr uint32_t kShmMagic = 0x534D4F4E;    // "SMON"
static constexpr uint32_t kShmVersion = 1;

/**
 * Per-metric slot stored in shared memory.
 * Aligned to 64 bytes to avoid false sharing between adjacent slots.
 */
struct alignas(64) MetricSlot {
    std::atomic<int64_t> value;          // Raw metric value
    std::atomic<int64_t> timestampNs;    // CLOCK_MONOTONIC ns, 0 = never written
    std::atomic<uint8_t> valid;          // 1 = valid sample exists, 0 = no data

    // Pad to exactly 64 bytes (cacheline size on ARM64)
    // value(8) + timestampNs(8) + valid(1) + pad(47) = 64
    uint8_t _pad[47];

    MetricSlot() {
        value.store(0, std::memory_order_relaxed);
        timestampNs.store(0, std::memory_order_relaxed);
        valid.store(0, std::memory_order_relaxed);
    }
};
static_assert(sizeof(MetricSlot) == 64, "MetricSlot must be 64 bytes");

/**
 * Header at the start of the shared memory region.
 * Reader validates magic/version before accessing slots.
 */
struct ShmHeader {
    uint32_t magic;        // kShmMagic
    uint32_t version;      // kShmVersion
    int32_t writerPid;     // PID of the writer process (for debugging)
    uint32_t slotCount;    // Number of MetricSlot entries following this header
    uint8_t _pad[48];      // Pad header to 64 bytes
};
static_assert(sizeof(ShmHeader) == 64, "ShmHeader must be 64 bytes");

// Total shared memory size
static constexpr size_t kShmSize = sizeof(ShmHeader) + sizeof(MetricSlot) * kMetricSlotCount;

// ---------------------------------------------------------------------------
// MetricStore
// ---------------------------------------------------------------------------
class MetricStore {
public:
    MetricStore();
    ~MetricStore();

    // Prevent copy
    MetricStore(const MetricStore &) = delete;
    MetricStore &operator=(const MetricStore &) = delete;

    // -----------------------------------------------------------------------
    // Writer API (called from sysmonitor-service / CollectorManager)
    // -----------------------------------------------------------------------

    /**
     * Create and map a new ashmem region as writer.
     * Must be called once before any publish() calls.
     * @return true on success
     */
    bool createShm();

    /**
     * Publish a metric value. Called on the sampling thread (hot path).
     * Lock-free: single atomic store per field.
     *
     * @param id          MetricId to update
     * @param value       Raw value (unit defined by MetricDef.h)
     * @param timestampNs CLOCK_MONOTONIC nanoseconds of the sample
     */
    void publish(MetricId id, int64_t value, int64_t timestampNs);

    /**
     * Return the ashmem fd to pass to reader processes.
     * Valid only after createShm() succeeds.
     * Caller must NOT close this fd; MetricStore owns it.
     */
    int getShmFd() const { return mShmFd; }

    // -----------------------------------------------------------------------
    // Reader API (called from perfengine-adapter / other consumers)
    // -----------------------------------------------------------------------

    /**
     * Attach to an existing ashmem region created by the writer.
     * Maps the region read-only.
     *
     * @param fd  ashmem fd received from ISysMonitor AIDL call
     * @return true on success
     */
    bool attachShm(int fd);

    /**
     * Read the latest value for a metric.
     * Lock-free: single atomic load per field.
     *
     * @param id  MetricId to query
     * @return    MetricValue with valid=false if no sample exists yet
     */
    MetricValue read(MetricId id) const;

    /**
     * Snapshot all metrics at once (reduces per-call overhead for bulk reads).
     * Caller provides output array sized >= kMetricSlotCount.
     *
     * @param out  Output array, indexed by metricIndex(id)
     * @param size Array size, must be >= kMetricSlotCount
     */
    void snapshot(MetricValue *out, size_t size) const;

    // -----------------------------------------------------------------------
    // Common
    // -----------------------------------------------------------------------

    /** Returns true if the store is mapped and ready. */
    bool isReady() const { return mMappedAddr != nullptr; }

    /** Dump slot summary to logcat (debug builds only). */
    void dump() const;

private:
    /** Map the shared memory region after fd is obtained. */
    bool mapRegion(int fd, bool writable);

    /** Validate the header written by the writer process. */
    bool validateHeader() const;

    // Shared memory file descriptor (-1 = not created/attached)
    int mShmFd = -1;

    // Base address of the mapped region (nullptr = not mapped)
    void *mMappedAddr = nullptr;

    // Convenience pointers into the mapped region (set after mapping)
    ShmHeader *mHeader = nullptr;
    MetricSlot *mSlots = nullptr;

    // True = this instance is the writer (owns the ashmem region)
    bool mIsWriter = false;
};

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
