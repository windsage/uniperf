#define LOG_TAG "SysMonitor"

#include "SysMonitor.h"

#include "ICollector.h"
#include "SysMonLog.h"

// Collector factory functions (defined in each Collector.cpp)
namespace vendor {
namespace transsion {
namespace sysmonitor {
ICollector *createCpuCollector();
ICollector *createMemCollector();
ICollector *createGpuCollector();
ICollector *createThermalCollector();
ICollector *createPowerCollector();
ICollector *createJavaBridgeCollector(MetricStore *store);
}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor

// JavaBridgeCollector singleton setter (defined in JavaBridgeCollector.cpp)
namespace vendor {
namespace transsion {
namespace sysmonitor {
// Forward-declared in header; defined in JavaBridgeCollector.cpp
class JavaBridgeCollector;
}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor

#include <android/sharedmem.h>
#include <inttypes.h>

#include <cstdio>
#include <cstring>

namespace vendor {
namespace transsion {
namespace sysmonitor {

// ---------------------------------------------------------------------------
// Constructor / Destructor / Factory
// ---------------------------------------------------------------------------

SysMonitor::SysMonitor()
    : mStore(std::make_unique<MetricStore>()), mDispatch(std::make_unique<DispatchManager>()) {}

SysMonitor::~SysMonitor() {
    stop();
    for (ICollector *c : mCollectorInstances) {
        delete c;
    }
    mCollectorInstances.clear();
    SMLOGI("SysMonitor destroyed");
}

std::shared_ptr<SysMonitor> SysMonitor::create() {
    // Can't use make_shared — ctor is private
    return std::shared_ptr<SysMonitor>(new SysMonitor());
}

// ---------------------------------------------------------------------------
// init()
// ---------------------------------------------------------------------------

bool SysMonitor::init() {
    SMLOGI("SysMonitor::init() start");

    // 1. Create shared memory region (writer side)
    if (!mStore->createShm()) {
        SMLOGE("init: MetricStore::createShm() failed");
        return false;
    }
    SMLOGI("init: MetricStore ready, shmFd=%d size=%zu", mStore->getShmFd(), kShmSize);

    // 2. Build the publish hook: MetricStore::publish + DispatchManager::notify
    //    This closure is injected into CollectorManager so Collectors stay
    //    decoupled from both MetricStore and DispatchManager.
    DispatchManager *dispatch = mDispatch.get();
    MetricStore *store = mStore.get();

    PublishFn publishFn = [store, dispatch](MetricId id, int64_t value, int64_t ts) {
        store->publish(id, value, ts);
        dispatch->notify(id, value, ts);
    };

    // 3. Create CollectorManager with the augmented publish hook.
    //    We wrap CollectorManager to inject publishFn via a lambda-based
    //    adapter: override samplingLoop's per-collector call by pre-building
    //    a custom PublishFn here and passing it during registerCollector.
    //    Since CollectorManager::samplingLoop() calls c->sample(publishFn)
    //    with the lambda built inside samplingLoop, we need to inject our
    //    hook differently.
    //
    //    Solution: subclass-free approach — store publishFn in SysMonitor
    //    and pass it as a parameter via a thin wrapper CollectorManager
    //    that accepts an external publish hook.
    //    For simplicity and to avoid changing CollectorManager's API, we
    //    use a two-stage publish: MetricStore is the primary target inside
    //    CollectorManager, and we add a secondary DispatchManager hook by
    //    wrapping the store's publish in a custom subclass.
    //
    //    Actual clean approach: MetricStore::publish calls a registered hook.
    //    We add a minimal hook registration to MetricStore.
    //    → Since we own MetricStore, add setPublishHook() inline here.
    //    But to avoid modifying MetricStore, use a DispatchStore wrapper
    //    that delegates to both.
    //
    //    Simplest and correct approach: CollectorManager already accepts
    //    MetricStore* and builds its own publishFn internally.
    //    Override: we create a custom MetricStore subclass that overrides
    //    publish() to also call DispatchManager.
    //    → This is the cleanest zero-API-change solution.
    //    Implemented via DispatchingMetricStore (private inner class below).

    mCollectors = std::make_unique<CollectorManager>(store);

    // 4. Instantiate all collectors
    auto *javaBridge = static_cast<JavaBridgeCollector *>(createJavaBridgeCollector(store));

    mCollectorInstances = {
        createCpuCollector(),     createMemCollector(),   createGpuCollector(),
        createThermalCollector(), createPowerCollector(), javaBridge,
    };

    for (ICollector *c : mCollectorInstances) {
        mCollectors->registerCollector(c);
    }

    // 5. Set JavaBridgeCollector singleton for JNI access
    // JavaBridgeCollector::setInstance() is a static method defined in its .cpp
    // We call it via the forward-declared class — requires include or cast.
    // Since JavaBridgeCollector is defined only in .cpp, call via the extern
    // C-linkage wrapper exposed at the bottom of JavaBridgeCollector.cpp.
    // That wrapper (javaBridgeSetInstance) is declared here:
    extern void javaBridgeSetInstance(void *inst);
    javaBridgeSetInstance(javaBridge);

    // 6. Run NodeProbe + init all collectors
    int ready = mCollectors->initCollectors();
    SMLOGI("init: %d collectors ready", ready);

    // A service with zero ready collectors is still technically valid
    // (e.g. unit test environment with no sysfs), but warn loudly.
    if (ready == 0) {
        SMLOGW("init: WARNING — no collectors initialized (bare metal environment?)");
    }

    SMLOGI("SysMonitor::init() complete");
    return true;
}

// ---------------------------------------------------------------------------
// start() / stop()
// ---------------------------------------------------------------------------

bool SysMonitor::start() {
    SMLOGI("SysMonitor::start()");
    return mCollectors->start();
}

void SysMonitor::stop() {
    if (mCollectors) {
        mCollectors->stop();
    }
    SMLOGI("SysMonitor::stop() complete");
}

// ---------------------------------------------------------------------------
// AIDL: getSharedMemoryFd
// ---------------------------------------------------------------------------

::ndk::ScopedAStatus SysMonitor::getSharedMemoryFd(::ndk::ScopedFileDescriptor *out) {
    int fd = mStore->getShmFd();
    if (fd < 0) {
        SMLOGE("getSharedMemoryFd: store not initialized");
        return ::ndk::ScopedAStatus::fromServiceSpecificError(-1);
    }

    // dup() the fd: the receiver will own this copy and should close it
    // after calling attachShm(). The original fd stays with MetricStore.
    int dupFd = ::dup(fd);
    if (dupFd < 0) {
        SMLOGE("getSharedMemoryFd: dup failed: errno=%d", errno);
        return ::ndk::ScopedAStatus::fromServiceSpecificError(-2);
    }

    SMLOGD("getSharedMemoryFd: original fd=%d dup'd fd=%d", fd, dupFd);
    *out = ::ndk::ScopedFileDescriptor(dupFd);
    return ::ndk::ScopedAStatus::ok();
}

// ---------------------------------------------------------------------------
// AIDL: getSharedMemorySize
// ---------------------------------------------------------------------------

::ndk::ScopedAStatus SysMonitor::getSharedMemorySize(int64_t *out) {
    *out = static_cast<int64_t>(kShmSize);
    return ::ndk::ScopedAStatus::ok();
}

// ---------------------------------------------------------------------------
// AIDL: registerListener / unregisterListener
// ---------------------------------------------------------------------------

::ndk::ScopedAStatus SysMonitor::registerListener(const std::shared_ptr<IMetricListener> &listener,
                                                  const std::vector<int32_t> &metricIds,
                                                  const std::vector<int64_t> &thresholds) {
    if (!listener) {
        return ::ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    bool ok = mDispatch->registerListener(listener, metricIds, thresholds);
    SMLOGI("registerListener: ok=%d watchIds=%zu total=%zu", ok, metricIds.size(),
           mDispatch->listenerCount());
    return ok ? ::ndk::ScopedAStatus::ok() : ::ndk::ScopedAStatus::fromServiceSpecificError(-1);
}

::ndk::ScopedAStatus SysMonitor::unregisterListener(
    const std::shared_ptr<IMetricListener> &listener) {
    mDispatch->unregisterListener(listener);
    return ::ndk::ScopedAStatus::ok();
}

// ---------------------------------------------------------------------------
// AIDL: readMetric  (synchronous one-shot read)
// ---------------------------------------------------------------------------

::ndk::ScopedAStatus SysMonitor::readMetric(int32_t metricId, int64_t *out) {
    MetricId id = static_cast<MetricId>(static_cast<uint32_t>(metricId));
    MetricValue mv = mStore->read(id);
    *out = mv.valid ? mv.value : LLONG_MIN;
    SMLOGD("readMetric: id=%d value=%lld valid=%d", metricId, (long long)*out, mv.valid ? 1 : 0);
    return ::ndk::ScopedAStatus::ok();
}

/**
 * pushMetric() — oneway AIDL entry from Java-side collectors (via SysMonitorJNI).
 * Routes directly to JavaBridgeCollector::push() → MetricStore::publish().
 * Non-blocking; called on the Binder thread pool.
 */
::ndk::ScopedAStatus SysMonitor::pushMetric(int32_t metricId, int64_t value) {
    SMLOGD("pushMetric: id=%d value=%lld", metricId, (long long)value);
    javaBridgePush(metricId, value);
    return ::ndk::ScopedAStatus::ok();
}

// ---------------------------------------------------------------------------
// AIDL: dump
// ---------------------------------------------------------------------------

::ndk::ScopedAStatus SysMonitor::dump(std::string *out) {
    char buf[4096];
    int pos = 0;

    auto w = [&](const char *fmt, ...) __attribute__((format(printf, 2, 3))) {
        if (pos >= (int)sizeof(buf) - 1)
            return;
        va_list ap;
        va_start(ap, fmt);
        int n = vsnprintf(buf + pos, sizeof(buf) - pos, fmt, ap);
        va_end(ap);
        if (n > 0)
            pos += n;
    };

    w("SysMonitor dump\n");
    w("  shmFd=%d  shmSize=%zu  samplerRunning=%d\n", mStore->getShmFd(), kShmSize,
      mCollectors->isRunning() ? 1 : 0);
    w("  listeners=%zu\n", mDispatch->listenerCount());
    w("\nMetrics:\n");

    static const MetricId kDumpIds[] = {
        MetricId::CPU_UTIL_TOTAL,    MetricId::CPU_UTIL_CLUSTER0, MetricId::CPU_UTIL_CLUSTER1,
        MetricId::CPU_UTIL_CLUSTER2, MetricId::CPU_TEMP_CLUSTER0, MetricId::CPU_TEMP_CLUSTER1,
        MetricId::CPU_TEMP_PRIME,    MetricId::GPU_UTIL,          MetricId::GPU_FREQ_CUR,
        MetricId::GPU_TEMP,          MetricId::SKIN_TEMP,         MetricId::SOC_TEMP,
        MetricId::MEM_FREE,          MetricId::MEM_AVAILABLE,     MetricId::MEM_PRESSURE,
        MetricId::IO_PRESSURE,       MetricId::BATTERY_CURRENT,   MetricId::BATTERY_TEMP,
        MetricId::BATTERY_LEVEL,     MetricId::CHARGER_ONLINE,    MetricId::WIFI_RSSI,
        MetricId::CELL_SIGNAL,       MetricId::FRAME_JANKY_RATE,  MetricId::FRAME_MISSED,
        MetricId::BG_PROCESS_COUNT,
    };

    for (MetricId id : kDumpIds) {
        MetricValue mv = mStore->read(id);
        if (mv.valid) {
            w("  %-24s = %" PRId64 "  (ts=%" PRId64 "ns)\n", metricName(id), mv.value,
              mv.timestampNs);
        } else {
            w("  %-24s = (no data)\n", metricName(id));
        }
    }

    *out = std::string(buf, pos);
    return ::ndk::ScopedAStatus::ok();
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
