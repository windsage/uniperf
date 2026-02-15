// vendor/transsion/hardware/sysmonitor/IMetricListener.aidl
package vendor.transsion.hardware.sysmonitor;

/**
 * IMetricListener - Callback interface for metric threshold notifications
 *
 * Implemented by consumers (e.g. perfengine-adapter) and registered via
 * ISysMonitor.registerListener().
 *
 * All callbacks are oneway (non-blocking from the service's perspective).
 */
@VintfStability
oneway interface IMetricListener {

    /**
     * Called when a watched metric crosses its configured threshold.
     *
     * Fired on the DispatchManager thread — implementations must return
     * quickly (< 1ms) to avoid blocking subsequent dispatches.
     *
     * @param metricId    MetricId (uint32) that triggered the notification.
     * @param value       Current sampled value.
     * @param timestampNs CLOCK_MONOTONIC nanoseconds when sampled.
     */
    void onThresholdExceeded(int metricId, long value, long timestampNs);

    /**
     * Called periodically for metrics registered with threshold == 0
     * (i.e. "notify on every update").
     *
     * @param metricId    MetricId (uint32).
     * @param value       Current sampled value.
     * @param timestampNs CLOCK_MONOTONIC nanoseconds when sampled.
     */
    void onMetricUpdated(int metricId, long value, long timestampNs);
}
