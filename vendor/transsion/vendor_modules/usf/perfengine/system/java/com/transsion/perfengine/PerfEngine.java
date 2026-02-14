package com.transsion.perfengine;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

// import com.transsion.perfengine.flags.Flags;
import vendor.transsion.hardware.perfengine.IEventListener;

/**
 * PerfEngine - Unified Performance Event Notification
 *
 * This class provides a unified interface for performance event notifications
 * across System and Vendor partitions. All calls are delegated to Vendor layer
 * via JNI and AIDL.
 *
 * Thread Safety: All methods are thread-safe.
 */
public final class PerfEngine {
    private static final String TAG = "PerfEngine";
    private static final boolean DEBUG = false;

    // Native library name (matches libperfengine-jni.so)
    private static final String JNI_LIBRARY = "perfengine-jni";
    private static final boolean ENABLE_PERFENGINE = false;

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
    private PerfEngine() {
        throw new AssertionError("PerfEngine cannot be instantiated");
    }

    // ==================== Event Notification Methods ====================

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
        // if (!Flags.enablePerfengine()) {
        if (!ENABLE_PERFENGINE) {
            if (DEBUG) {
                Log.d(TAG, "PerfEngine disabled by flag");
            }
            return;
        }
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
        // if (!Flags.enablePerfengine()) {
        if (!ENABLE_PERFENGINE) {
            if (DEBUG) {
                Log.d(TAG, "PerfEngine disabled by flag");
            }
            return;
        }
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

    // ==================== Event Listener Registration Methods ====================

    /**
     * Register an event listener to receive performance event notifications
     *
     * The listener will receive onEventStart() and onEventEnd() callbacks
     * whenever performance events occur.
     *
     * @param listener IEventListener implementation (must not be null)
     * @throws IllegalArgumentException if listener is null
     * @throws RemoteException if registration fails
     *
     * Usage:
     * <pre>
     * IEventListener listener = new IEventListener.Stub() {
     *     @Override
     *     public void onEventStart(int eventId, long timestamp, int numParams,
     *                              int[] intParams, String extraStrings) {
     *         // Handle event start
     *     }
     *
     *     @Override
     *     public void onEventEnd(int eventId, long timestamp, String extraStrings) {
     *         // Handle event end
     *     }
     * };
     *
     * PerfEngine.registerEventListener(listener);
     * </pre>
     */
    public static void registerEventListener(IEventListener listener) {
        registerEventListener(listener, new int[0]);
    }

    public static void registerEventListener(IEventListener listener, int[] eventFilter) {
        // if (!Flags.enablePerfengine()) {
        if (!ENABLE_PERFENGINE) {
            if (DEBUG) {
                Log.d(TAG, "PerfEngine disabled by flag");
            }
            return;
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        if (DEBUG)
            Log.d(TAG, "registerEventListener with filter size="
                               + (eventFilter != null ? eventFilter.length : 0));
        try {
            IBinder binder = listener.asBinder();
            if (binder == null) {
                throw new IllegalArgumentException("Listener binder is null");
            }
            // Call native method
            nativeRegisterEventListener(binder, eventFilter != null ? eventFilter : new int[0]);
            if (DEBUG) {
                Log.d(TAG, "Listener registered successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register event listener", e);
            throw new RuntimeException("Failed to register event listener", e);
        }
    }

    /**
     * Unregister a previously registered event listener
     *
     * After this call, the listener will no longer receive event notifications.
     *
     * @param listener IEventListener implementation to unregister (must not be null)
     * @throws IllegalArgumentException if listener is null
     * @throws RemoteException if unregistration fails
     */
    public static void unregisterEventListener(IEventListener listener) {
        // if (!Flags.enablePerfengine()) {
        if (!ENABLE_PERFENGINE) {
            if (DEBUG) {
                Log.d(TAG, "PerfEngine disabled by flag");
            }
            return;
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        if (DEBUG) {
            Log.d(TAG, "unregisterEventListener: " + listener);
        }

        try {
            // Get binder object
            IBinder binder = listener.asBinder();
            if (binder == null) {
                Log.e(TAG, "Failed to get binder from listener");
                throw new IllegalArgumentException("Listener binder is null");
            }

            // Call native method
            nativeUnregisterEventListener(binder);

            if (DEBUG) {
                Log.d(TAG, "Listener unregistered successfully");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister event listener", e);
            throw new RuntimeException("Failed to unregister event listener", e);
        }
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

    /**
     * Native method: Register event listener
     *
     * @param listenerBinder Binder object of IEventListener
     * @param eventFilter Array of event types to filter
     */
    private static native void nativeRegisterEventListener(
            IBinder listenerBinder, int[] eventFilter);

    /**
     * Native method: Unregister event listener
     *
     * @param listenerBinder Binder object of IEventListener
     */
    private static native void nativeUnregisterEventListener(IBinder listenerBinder);
}
