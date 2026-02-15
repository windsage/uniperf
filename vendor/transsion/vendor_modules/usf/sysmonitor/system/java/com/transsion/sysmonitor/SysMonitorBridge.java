package com.transsion.sysmonitor;

import android.util.Log;

/**
 * SysMonitorBridge — Java entry point for the SysMonitor system.
 *
 * Responsibilities:
 *   - Load libsysmonitor_jni.so once
 *   - Connect to sysmonitor-service via JNI on first use
 *   - Expose readMetric() for Java consumers (zero-copy via shm)
 *   - Expose push() for Java collectors to push sampled values
 *
 * Threading: all methods are thread-safe; native layer is lock-free.
 */
public final class SysMonitorBridge {
    private static final String TAG = "SysMonitorBridge";

    // MetricId constants — mirror MetricDef.h values
    public static final int METRIC_WIFI_RSSI = 0x0061;
    public static final int METRIC_CELL_SIGNAL = 0x0062;
    public static final int METRIC_FRAME_JANKY_RATE = 0x0071;
    public static final int METRIC_FRAME_MISSED = 0x0072;
    public static final int METRIC_BG_PROCESS_COUNT = 0x0081;

    public static final long INVALID_VALUE = Long.MIN_VALUE;

    // Singleton
    private static volatile boolean sLoaded = false;
    private static volatile boolean sReady = false;

    static {
        try {
            System.loadLibrary("sysmonitor_jni");
            sLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load libsysmonitor_jni: " + e.getMessage());
        }
    }

    private SysMonitorBridge() {}

    /**
     * Initialize connection to sysmonitor-service.
     * Safe to call multiple times — subsequent calls are no-ops.
     * Should be called from system service init (not main thread).
     *
     * @return true if connected and shared memory is mapped.
     */
    public static synchronized boolean init() {
        if (!sLoaded) {
            return false;
        }
        if (sReady) {
            return true;
        }
        sReady = nativeConnect();
        if (!sReady) {
            Log.w(TAG, "init: failed to connect to sysmonitor-service");
        }
        return sReady;
    }

    /** Returns true if connected and ready for reads/pushes. */
    public static boolean isReady() {
        return sReady && nativeIsReady();
    }

    /**
     * Read latest value for a metric (zero-copy from shared memory).
     * @param metricId  One of the METRIC_* constants or a raw MetricId value.
     * @return          Latest value, or INVALID_VALUE if not yet sampled.
     */
    public static long readMetric(int metricId) {
        if (!sReady)
            return INVALID_VALUE;
        return nativeRead(metricId);
    }

    /**
     * Push a Java-side metric value to the service.
     * Called by Java collectors (Wifi, Cell, Frame, BgProcess).
     *
     * @param metricId  One of the METRIC_* constants.
     * @param value     Sampled value.
     */
    public static void push(int metricId, long value) {
        if (!sReady)
            return;
        nativePush(metricId, value);
    }

    // -----------------------------------------------------------------------
    // JNI declarations
    // -----------------------------------------------------------------------
    private static native boolean nativeConnect();
    private static native boolean nativeIsReady();
    private static native long nativeRead(int metricId);
    private static native void nativePush(int metricId, long value);
}
