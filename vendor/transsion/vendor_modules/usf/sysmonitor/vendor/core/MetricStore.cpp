#define LOG_TAG "SMon-Store"

#include "MetricStore.h"

#include <android/sharedmem.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>

#include <cstring>

#include "SysMonLog.h"

namespace vendor {
namespace transsion {
namespace sysmonitor {

// ---------------------------------------------------------------------------
// Constructor / Destructor
// ---------------------------------------------------------------------------

MetricStore::MetricStore() = default;

MetricStore::~MetricStore() {
    if (mMappedAddr != nullptr) {
        ::munmap(mMappedAddr, kShmSize);
        mMappedAddr = nullptr;
        mHeader = nullptr;
        mSlots = nullptr;
        SMLOGD("Shared memory unmapped");
    }
    if (mShmFd >= 0 && mIsWriter) {
        // Only writer closes the fd; reader receives a dup'd fd and closes it
        // after attachShm() to avoid keeping it open unnecessarily.
        ::close(mShmFd);
        mShmFd = -1;
        SMLOGD("ashmem fd closed");
    }
}

// ---------------------------------------------------------------------------
// Writer: create
// ---------------------------------------------------------------------------

bool MetricStore::createShm() {
    if (mMappedAddr != nullptr) {
        SMLOGW("createShm: already mapped, skip");
        return true;
    }

    // ASharedMemory_create() is available since API 26 (Android 8.0).
    // Returns an fd backed by /dev/ashmem (kernel) or memfd (newer kernels).
    mShmFd = ASharedMemory_create("sysmonitor_metrics", kShmSize);
    if (mShmFd < 0) {
        SMLOGE("ASharedMemory_create failed: errno=%d (%s)", errno, strerror(errno));
        return false;
    }
    SMLOGD("ashmem created: fd=%d size=%zu bytes", mShmFd, kShmSize);

    if (!mapRegion(mShmFd, /*writable=*/true)) {
        ::close(mShmFd);
        mShmFd = -1;
        return false;
    }

    // Initialize header
    mHeader->magic = kShmMagic;
    mHeader->version = kShmVersion;
    mHeader->writerPid = static_cast<int32_t>(::getpid());
    mHeader->slotCount = static_cast<uint32_t>(kMetricSlotCount);

    // Zero-initialize all slots via placement-new array
    for (size_t i = 0; i < kMetricSlotCount; ++i) {
        new (&mSlots[i]) MetricSlot();
    }

    // Memory fence: ensure header + slot init is visible before any publish()
    std::atomic_thread_fence(std::memory_order_seq_cst);

    mIsWriter = true;
    SMLOGI("MetricStore ready (writer): fd=%d mapped@%p size=%zu", mShmFd, mMappedAddr, kShmSize);
    return true;
}

// ---------------------------------------------------------------------------
// Writer: publish (hot path — must be allocation-free)
// ---------------------------------------------------------------------------

void MetricStore::publish(MetricId id, int64_t value, int64_t timestampNs) {
    const uint32_t idx = metricIndex(id);
    if (__builtin_expect(idx == 0 || idx >= kMetricSlotCount, 0)) {
        SMLOGW("publish: invalid MetricId %u", idx);
        return;
    }
    if (__builtin_expect(mSlots == nullptr, 0)) {
        return;    // Not yet mapped; should not happen after createShm()
    }

    MetricSlot &slot = mSlots[idx];
    // Write value first, then timestamp, then mark valid.
    // Readers always check valid after reading both fields, so they either
    // see the previous complete write or the new complete write — never torn.
    slot.value.store(value, std::memory_order_relaxed);
    slot.timestampNs.store(timestampNs, std::memory_order_relaxed);
    slot.valid.store(1u, std::memory_order_release);    // release: pairs with reader's acquire

    SMLOGV("publish: id=%s(%u) value=%lld ts=%lld", metricName(id), idx, (long long)value,
           (long long)timestampNs);
}

// ---------------------------------------------------------------------------
// Reader: attach
// ---------------------------------------------------------------------------

bool MetricStore::attachShm(int fd) {
    if (mMappedAddr != nullptr) {
        SMLOGW("attachShm: already attached, skip");
        return true;
    }
    if (fd < 0) {
        SMLOGE("attachShm: invalid fd=%d", fd);
        return false;
    }

    // Verify reported size matches expectation
    size_t regionSize = ASharedMemory_getSize(fd);
    if (regionSize < kShmSize) {
        SMLOGE("attachShm: region too small: got %zu, need %zu", regionSize, kShmSize);
        return false;
    }

    if (!mapRegion(fd, /*writable=*/false)) {
        return false;
    }

    if (!validateHeader()) {
        ::munmap(mMappedAddr, kShmSize);
        mMappedAddr = nullptr;
        mHeader = nullptr;
        mSlots = nullptr;
        return false;
    }

    // fd ownership: mmap() internally increments the kernel file reference count,
    // so the mapping remains valid even after the fd is closed.
    // This instance does NOT own the fd — caller is responsible for closing it
    // after this call returns. Do NOT close fd here to avoid double-close:
    //   SysMonitor::getSharedMemoryFd() dup()'s the original fd before sending.
    //   SysMonitorJNI calls attachShm(fd) then close(fd) — that close is safe
    //   because it only closes the dup'd copy; the mapping persists independently.
    mShmFd = fd;    // stored for reference (e.g. ASharedMemory_getSize), not for ownership
    mIsWriter = false;
    SMLOGI("MetricStore ready (reader): fd=%d mapped@%p", fd, mMappedAddr);
    return true;
}

// ---------------------------------------------------------------------------
// Reader: read (hot path — lock-free)
// ---------------------------------------------------------------------------

MetricValue MetricStore::read(MetricId id) const {
    const uint32_t idx = metricIndex(id);
    if (__builtin_expect(idx == 0 || idx >= kMetricSlotCount, 0)) {
        return MetricValue{};
    }
    if (__builtin_expect(mSlots == nullptr, 0)) {
        return MetricValue{};
    }

    const MetricSlot &slot = mSlots[idx];

    // acquire load on valid: ensures we see value/timestamp written before
    // the writer's release store on valid.
    uint8_t v = slot.valid.load(std::memory_order_acquire);
    if (v == 0) {
        return MetricValue{};    // No sample yet
    }

    int64_t value = slot.value.load(std::memory_order_relaxed);
    int64_t ts = slot.timestampNs.load(std::memory_order_relaxed);
    return MetricValue(value, ts);
}

// ---------------------------------------------------------------------------
// Reader: snapshot all metrics
// ---------------------------------------------------------------------------

void MetricStore::snapshot(MetricValue *out, size_t size) const {
    if (__builtin_expect(mSlots == nullptr || out == nullptr, 0)) {
        return;
    }
    const size_t count = (size < kMetricSlotCount) ? size : kMetricSlotCount;
    for (size_t i = 0; i < count; ++i) {
        const MetricSlot &slot = mSlots[i];
        uint8_t v = slot.valid.load(std::memory_order_acquire);
        if (v == 0) {
            out[i] = MetricValue{};
        } else {
            out[i] = MetricValue(slot.value.load(std::memory_order_relaxed),
                                 slot.timestampNs.load(std::memory_order_relaxed));
        }
    }
}

// ---------------------------------------------------------------------------
// Debug dump
// ---------------------------------------------------------------------------

void MetricStore::dump() const {
#ifndef SMON_LOG_DISABLED
    if (mSlots == nullptr) {
        SMLOGI("dump: not mapped");
        return;
    }
    SMLOGI("MetricStore dump (writer=%d fd=%d):", mIsWriter ? 1 : 0, mShmFd);
    // Iterate known MetricIds for readable output
    static const MetricId kKnownIds[] = {
        MetricId::CPU_UTIL_CLUSTER0, MetricId::CPU_UTIL_CLUSTER1, MetricId::CPU_UTIL_CLUSTER2,
        MetricId::CPU_UTIL_TOTAL,    MetricId::CPU_TEMP_CLUSTER0, MetricId::CPU_TEMP_CLUSTER1,
        MetricId::CPU_TEMP_PRIME,    MetricId::GPU_UTIL,          MetricId::GPU_TEMP,
        MetricId::GPU_FREQ_CUR,      MetricId::SKIN_TEMP,         MetricId::SOC_TEMP,
        MetricId::MEM_FREE,          MetricId::MEM_AVAILABLE,     MetricId::MEM_CACHED,
        MetricId::MEM_SWAP_USED,     MetricId::MEM_PRESSURE,      MetricId::IO_PRESSURE,
        MetricId::BATTERY_CURRENT,   MetricId::BATTERY_TEMP,      MetricId::BATTERY_LEVEL,
        MetricId::CHARGER_ONLINE,    MetricId::WIFI_RSSI,         MetricId::CELL_SIGNAL,
        MetricId::FRAME_JANKY_RATE,  MetricId::FRAME_MISSED,      MetricId::BG_PROCESS_COUNT,
    };
    for (MetricId id : kKnownIds) {
        MetricValue mv = read(id);
        if (mv.valid) {
            SMLOGI("  %-26s = %lld (ts=%lldns)", metricName(id), (long long)mv.value,
                   (long long)mv.timestampNs);
        } else {
            SMLOGI("  %-26s = (no data)", metricName(id));
        }
    }
#endif    // !SMON_LOG_DISABLED
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

bool MetricStore::mapRegion(int fd, bool writable) {
    int prot = PROT_READ | (writable ? PROT_WRITE : 0);
    void *addr = ::mmap(nullptr, kShmSize, prot, MAP_SHARED, fd, 0);
    if (addr == MAP_FAILED) {
        SMLOGE("mmap failed: fd=%d size=%zu errno=%d (%s)", fd, kShmSize, errno, strerror(errno));
        return false;
    }
    mMappedAddr = addr;
    mHeader = reinterpret_cast<ShmHeader *>(addr);
    mSlots = reinterpret_cast<MetricSlot *>(reinterpret_cast<uint8_t *>(addr) + sizeof(ShmHeader));
    SMLOGD("mmap ok: addr=%p writable=%d", addr, writable ? 1 : 0);
    return true;
}

bool MetricStore::validateHeader() const {
    if (mHeader->magic != kShmMagic) {
        SMLOGE("validateHeader: bad magic 0x%08x (expected 0x%08x)", mHeader->magic, kShmMagic);
        return false;
    }
    if (mHeader->version != kShmVersion) {
        SMLOGE("validateHeader: version mismatch: got %u, expected %u", mHeader->version,
               kShmVersion);
        return false;
    }
    if (mHeader->slotCount < kMetricSlotCount) {
        SMLOGE("validateHeader: slotCount %u < expected %u", mHeader->slotCount,
               (uint32_t)kMetricSlotCount);
        return false;
    }
    SMLOGD("validateHeader ok: writerPid=%d slotCount=%u", mHeader->writerPid, mHeader->slotCount);
    return true;
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
