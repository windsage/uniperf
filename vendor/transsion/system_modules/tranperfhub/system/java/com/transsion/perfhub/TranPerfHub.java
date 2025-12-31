package com.transsion.perfhub;

import android.os.SystemClock;
import android.util.Log;

/**
 * TranPerfHub - Unified Performance Event Notification
 *
 * This class provides a unified interface for performance event notifications
 * across System and Vendor partitions. All calls are delegated to Vendor layer
 * via JNI and AIDL.
 *
 * Thread Safety: All methods are thread-safe.
 */
public final class TranPerfHub {
    private static final String TAG = "TranPerfHub";
    private static final boolean DEBUG = false;

    // Native library name (matches libtranperfhub-jni.so)
    private static final String JNI_LIBRARY = "tranperfhub-jni";

    // Load native library
    static {
        try {
            System.loadLibrary(JNI_LIBRARY);
            if (DEBUG) {
                Log.d(TAG, "Native library loaded: " + JNI_LIBRARY);
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + JNI_LIBRARY, e);
        }
    }

    // Private constructor to prevent instantiation
    private TranPerfHub() {
        throw new AssertionError("TranPerfHub cannot be instantiated");
    }

    /**
     * Notify performance event start
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds (use SystemClock.elapsedRealtimeNanos())
     * @param numParams Number of valid parameters in intParams array
     * @param intParams Integer parameters array
     *                  intParams[0] = duration (ms), 0 means manual release
     *                  intParams[1..n] = event-specific parameters
     * @param extraStrings Optional string parameter (usually packageName), can be null
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams, String extraStrings) {
        if (DEBUG) {
            Log.d(TAG, String.format("notifyEventStart: eventId=%d, timestamp=%d, numParams=%d, "
                                             + "extraStrings=%s",
                               eventId, timestamp, numParams, extraStrings));
        }

        // Validate parameters
        if (intParams == null || intParams.length != numParams) {
            Log.e(TAG, "Invalid parameters: intParams.length="
                               + (intParams != null ? intParams.length : "null")
                               + ", numParams=" + numParams);
            return;
        }

        if (numParams < 1) {
            Log.e(TAG, "Invalid numParams: " + numParams + " (must be >= 1)");
            return;
        }

        try {
            // Call native method
            nativeNotifyEventStart(eventId, timestamp, numParams, intParams, extraStrings);
        } catch (Exception e) {
            Log.e(TAG, "Failed to notify event start", e);
        }
    }

    /**
     * Notify performance event start (auto-fill timestamp)
     *
     * @param eventId Event type identifier
     * @param numParams Number of valid parameters in intParams array
     * @param intParams Integer parameters array
     * @param extraStrings Optional string parameter (usually packageName), can be null
     */
    public static void notifyEventStart(
            int eventId, int numParams, int[] intParams, String extraStrings) {
        long timestamp = SystemClock.elapsedRealtimeNanos();
        notifyEventStart(eventId, timestamp, numParams, intParams, extraStrings);
    }

    /**
     * Notify performance event end
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param extraStrings Optional string parameter (usually packageName), can be null
     */
    public static void notifyEventEnd(int eventId, long timestamp, String extraStrings) {
        if (DEBUG) {
            Log.d(TAG, String.format("notifyEventEnd: eventId=%d, timestamp=%d, extraStrings=%s",
                               eventId, timestamp, extraStrings));
        }

        try {
            // Call native method
            nativeNotifyEventEnd(eventId, timestamp, extraStrings);
        } catch (Exception e) {
            Log.e(TAG, "Failed to notify event end", e);
        }
    }

    /**
     * Notify performance event end (auto-fill timestamp)
     *
     * @param eventId Event type identifier
     * @param extraStrings Optional string parameter (usually packageName), can be null
     */
    public static void notifyEventEnd(int eventId, String extraStrings) {
        long timestamp = SystemClock.elapsedRealtimeNanos();
        notifyEventEnd(eventId, timestamp, extraStrings);
    }

    // ==================== Native Methods ====================

    /**
     * Native method: Notify event start
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param numParams Number of valid parameters in intParams
     * @param intParams Integer parameters array
     * @param extraStrings Optional string parameter
     */
    private static native void nativeNotifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams, String extraStrings);

    /**
     * Native method: Notify event end
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param extraStrings Optional string parameter
     */
    private static native void nativeNotifyEventEnd(
            int eventId, long timestamp, String extraStrings);
}
