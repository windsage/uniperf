// vendor/transsion/hardware/sysmonitor/ISysMonitor.aidl
package vendor.transsion.hardware.sysmonitor;

import vendor.transsion.hardware.sysmonitor.IMetricListener;

/**
 * ISysMonitor - Main service interface for the SysMonitor HAL
 *
 * Consumers (e.g. perfengine-adapter, stats modules) interact with
 * the sysmonitor-service process through this AIDL interface.
 *
 * Two usage patterns:
 *
 * 1. Zero-copy fast path (recommended for PerfEngine):
 *    Call getSharedMemoryFd() once → mmap the region → read MetricSlots directly.
 *    No IPC on the hot path after initial setup.
 *
 * 2. Subscription / push path (for event-driven consumers):
 *    Call registerListener() with a MetricId list + threshold.
 *    Service calls back IMetricListener.onThresholdExceeded() when triggered.
 */
@VintfStability
interface ISysMonitor {

    /**
     * Get the ashmem file descriptor for the shared MetricStore region.
     *
     * The returned ParcelFileDescriptor wraps the same ashmem fd that the
     * writer (sysmonitor-service) uses.  The caller maps it read-only:
     *   mmap(nullptr, size, PROT_READ, MAP_SHARED, fd, 0)
     *
     * After mapping, the caller calls MetricStore::attachShm(fd) and can
     * read any MetricSlot with zero IPC overhead.
     *
     * The returned fd is valid for the lifetime of the service.
     * If the service restarts, the caller must re-call this method.
     *
     * @return ParcelFileDescriptor wrapping the ashmem fd.
     */
    ParcelFileDescriptor getSharedMemoryFd();

    /**
     * Get the total size in bytes of the shared memory region.
     * Callers use this to validate the mapping before reading slots.
     *
     * @return Size in bytes (matches MetricStore::kShmSize).
     */
    long getSharedMemorySize();

    /**
     * Register a metric listener for threshold-based push notifications.
     *
     * The service calls IMetricListener.onThresholdExceeded() when any of
     * the watched metrics crosses its configured threshold.
     *
     * @param listener     Client-implemented callback object.
     * @param metricIds    List of MetricId values (uint32) to watch.
     *                     Empty list = watch all metrics (use with care).
     * @param thresholds   Parallel array of int64 threshold values.
     *                     Must have same length as metricIds, or be empty
     *                     (meaning: notify on every sample update).
     */
    void registerListener(in IMetricListener listener,
                          in int[] metricIds,
                          in long[] thresholds);

    /**
     * Unregister a previously registered listener.
     * Safe to call even if the listener was never registered.
     *
     * @param listener  The same binder object passed to registerListener().
     */
    void unregisterListener(in IMetricListener listener);

    /**
     * Read the latest value for a single metric synchronously.
     * Convenience API for one-shot queries; for high-frequency reads use
     * the shared memory path instead.
     *
     * @param metricId  MetricId (uint32) to query.
     * @return          Latest value, or Long.MIN_VALUE if not yet sampled.
     */
    long readMetric(in int metricId);

    /**
     * Push a metric value from a Java-side collector.
     * oneway: non-blocking from caller's perspective.
     * Server routes to JavaBridgeCollector::push() → MetricStore::publish().
     *
     * @param metricId  MetricId (uint32) — must be a Java-side metric:
     *                  WIFI_RSSI / CELL_SIGNAL / FRAME_JANKY_RATE /
     *                  FRAME_MISSED / BG_PROCESS_COUNT
     * @param value     Sampled value
     */
    oneway void pushMetric(in int metricId, in long value);

    /**
     * Dump current state to a string for debugging.
     * Equivalent to: adb shell dumpsys vendor.transsion.sysmonitor
     *
     * @return Multi-line human-readable status string.
     */
    @utf8InCpp String dump();
}
