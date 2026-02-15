#pragma once

#include <aidl/vendor/transsion/hardware/sysmonitor/BnSysMonitor.h>
#include <android/binder_auto_utils.h>

#include <memory>
#include <string>

#include "CollectorManager.h"
#include "DispatchManager.h"
#include "MetricStore.h"

// Forward declarations
namespace vendor {
namespace transsion {
namespace sysmonitor {
class ICollector;
void javaBridgePush(int metricIdInt, int64_t value);
}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor

/**
 * SysMonitor.h - Top-level facade + AIDL BnSysMonitor implementation
 *
 * Owns the full object graph:
 *   MetricStore  — shared memory region (writer side)
 *   CollectorManager — sampling thread + all ICollector instances
 *   DispatchManager  — subscription / threshold callbacks
 *
 * Lifecycle (called from sysmonitor_service.cpp main()):
 *   SysMonitor::create()  →  init()  →  start()
 *   [running until SIGTERM]
 *   stop()
 *
 * AIDL interface (ISysMonitor):
 *   getSharedMemoryFd()    → passes ashmem fd to reader processes
 *   getSharedMemorySize()  → kShmSize constant
 *   registerListener()     → forwarded to DispatchManager
 *   unregisterListener()   → forwarded to DispatchManager
 *   readMetric()           → single synchronous MetricStore::read()
 *   dump()                 → human-readable status string
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

using BnSysMonitor = ::aidl::vendor::transsion::hardware::sysmonitor::BnSysMonitor;
using IMetricListener = ::aidl::vendor::transsion::hardware::sysmonitor::IMetricListener;

class SysMonitor : public BnSysMonitor {
public:
    ~SysMonitor() override;

    /**
     * Factory: create and return a fully-constructed (but not yet started) instance.
     * Returns nullptr on allocation failure (OOM — extremely unlikely).
     */
    static std::shared_ptr<SysMonitor> create();

    /**
     * Initialize MetricStore (ashmem), instantiate all collectors.
     * Must be called before start().
     * @return true on success.
     */
    bool init();

    /**
     * Start the sampling thread. Requires init() to have succeeded.
     * @return true on success.
     */
    bool start();

    /**
     * Stop the sampling thread and join it. Safe to call multiple times.
     */
    void stop();

    // -----------------------------------------------------------------------
    // ISysMonitor AIDL implementation
    // -----------------------------------------------------------------------
    ::ndk::ScopedAStatus getSharedMemoryFd(::ndk::ScopedFileDescriptor *out) override;

    ::ndk::ScopedAStatus getSharedMemorySize(int64_t *out) override;

    ::ndk::ScopedAStatus registerListener(const std::shared_ptr<IMetricListener> &listener,
                                          const std::vector<int32_t> &metricIds,
                                          const std::vector<int64_t> &thresholds) override;

    ::ndk::ScopedAStatus unregisterListener(
        const std::shared_ptr<IMetricListener> &listener) override;

    ::ndk::ScopedAStatus readMetric(int32_t metricId, int64_t *out) override;
    ::ndk::ScopedAStatus pushMetric(int32_t metricId, int64_t value) override;
    ::ndk::ScopedAStatus dump(std::string *out) override;

private:
    SysMonitor();

    // Called by CollectorManager's PublishFn after each MetricStore::publish()
    // to trigger DispatchManager threshold checks.
    void onMetricPublished(MetricId id, int64_t value, int64_t timestampNs);

    std::unique_ptr<MetricStore> mStore;
    std::unique_ptr<CollectorManager> mCollectors;
    std::unique_ptr<DispatchManager> mDispatch;

    // Collector instances — owned here, registered into CollectorManager
    // (raw pointers; lifetime tied to SysMonitor)
    std::vector<ICollector *> mCollectorInstances;
};

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
