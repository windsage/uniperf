package com.transsion.usf.perfengine;

import static com.transsion.usf.perfengine.TranPerfEventConstants.*;

import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TranPerfEvent - Performance Event API
 *
 * @hide
 * Unified interface for performance event notifications.
 * Compiled into framework.jar via transsion_framework_overlay_sources.
 * JNI calls directly into libperfengine-jni.so (pre-loaded by ZygoteInit).
 *
 * Thread-safe.
 *
 * Usage:
 * <pre>
 * import com.transsion.usf.perfengine.TranPerfEvent;
 * import static com.transsion.usf.perfengine.TranPerfEventConstants.*;
 *
 * // Send event (no params)
 * TranPerfEvent.notifyEventStart(EVENT_SYS_APP_LAUNCH_COLD, TranPerfEvent.now());
 *
 * // Send event (with params)
 * TranPerfEvent.notifyEventStart(EVENT_SYS_APP_LAUNCH_COLD, TranPerfEvent.now(),
 *         1, new int[]{500}, "com.example.app");
 *
 * // Register in-process listener (system_server internal use only)
 * TranPerfEvent.registerListener(new TranPerfEvent.TrEventListener() {
 *     public void onEventStart(int eventId, long timestamp, int duration) { }
 *     public void onEventEnd(int eventId, long timestamp) { }
 * });
 *
 * // Register cross-process listener (caller creates IEventListener.Stub)
 * TranPerfEvent.registerEventListener(myListenerStub.asBinder());
 * </pre>
 */
public final class TranPerfEvent {
    private static final String TAG = "TranPerfEvent";

    // Compile-time debug switch — set false for production build
    private static final boolean DEBUG = true;

    // String separator (ASCII 31 - Unit Separator)
    private static final char STRING_SEPARATOR = '\u001F';

    // Latency warning threshold: 10ms
    private static final long LATENCY_THRESHOLD_NS = 10_000_000L;

    // Feature enable switch (replace with aconfig flag when ready)
    // private static final boolean ENABLE_PERFENGINE = Flags.enablePerfengine();
    private static final boolean ENABLE_PERFENGINE = true;

    private static final String PERF_ENGINE_PERMISSION =
            "com.transsion.permission.PERF_ENGINE_NOTIFY";

    private static final int MY_UID = android.os.Process.myUid();

    // uid < FIRST_APPLICATION_UID(10000) means a trusted system process
    private static final boolean IS_SYSTEM_PROCESS =
            MY_UID < android.os.Process.FIRST_APPLICATION_UID;

    // ==================== JNI ====================

    // Library is pre-loaded by ZygoteInit. All forked processes inherit the mapping.
    // Set true unconditionally; call sites catch UnsatisfiedLinkError defensively.
    @SuppressWarnings("unused") private static final boolean sLibraryLoaded = true;

    /**
     * Native: notify event start
     * JNI: Java_com_transsion_usf_perfengine_TranPerfEvent_nativeNotifyEventStart
     */
    private static native void nativeNotifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams, String extraStrings);

    /**
     * Native: notify event end
     * JNI: Java_com_transsion_usf_perfengine_TranPerfEvent_nativeNotifyEventEnd
     */
    private static native void nativeNotifyEventEnd(
            int eventId, long timestamp, String extraStrings);

    /**
     * Native: register cross-process event listener
     * JNI: Java_com_transsion_usf_perfengine_TranPerfEvent_nativeRegisterEventListener
     */
    private static native void nativeRegisterEventListener(
            IBinder listenerBinder, int[] eventFilter);

    /**
     * Native: unregister cross-process event listener
     * JNI: Java_com_transsion_usf_perfengine_TranPerfEvent_nativeUnregisterEventListener
     */
    private static native void nativeUnregisterEventListener(IBinder listenerBinder);

    // ==================== Listener Interfaces ====================

    /**
     * In-process simplified listener for Framework internal use (e.g. system_server).
     *
     * Callbacks are dispatched synchronously in the same process that calls
     * notifyEventStart/End. Zero Binder overhead.
     *
     * Do NOT use this from a different process — use registerEventListener(IBinder) instead.
     *
     * @hide
     */
    public interface TrEventListener {
        /**
         * Called when a performance event starts.
         *
         * @param eventId   Event type identifier
         * @param timestamp Event timestamp in nanoseconds
         * @param duration  Hint from intParams[0]; 0 means manually released
         */
        void onEventStart(int eventId, long timestamp, int duration);

        /**
         * Called when a performance event ends.
         *
         * @param eventId   Event type identifier
         * @param timestamp Event timestamp in nanoseconds
         */
        void onEventEnd(int eventId, long timestamp);
    }

    // ==================== In-process Listener Management ====================

    // CopyOnWriteArrayList: lock-free reads on hot notify path, safe concurrent modification
    private static final CopyOnWriteArrayList<TrEventListener> sSimplifiedListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Register an in-process simplified listener.
     *
     * Suitable for system_server internal components that need low-latency
     * event notification within the same process.
     *
     * @param listener TrEventListener implementation; ignored if null or already registered
     * @hide
     */
    public static void registerListener(TrEventListener listener) {
        if (listener != null && !sSimplifiedListeners.contains(listener)) {
            sSimplifiedListeners.add(listener);
            if (DEBUG) {
                Log.d(TAG, "TrEventListener registered, total=" + sSimplifiedListeners.size());
            }
        }
    }

    /**
     * Unregister an in-process simplified listener.
     *
     * @param listener TrEventListener to remove
     * @hide
     */
    public static void unregisterListener(TrEventListener listener) {
        sSimplifiedListeners.remove(listener);
        if (DEBUG) {
            Log.d(TAG, "TrEventListener unregistered, remaining=" + sSimplifiedListeners.size());
        }
    }

    // ==================== Cross-process Listener Management ====================

    /**
     * Register a cross-process event listener, subscribe to ALL events.
     *
     * The caller is responsible for creating an IEventListener.Stub and
     * passing its IBinder here. This keeps TranPerfEvent free of any direct
     * dependency on vendor AIDL classes.
     *
     * Example:
     * <pre>
     * IEventListener.Stub stub = new IEventListener.Stub() {
     *     public void onEventStart(int id, long ts, int n, int[] p, String s) { ... }
     *     public void onEventEnd(int id, long ts, String s) { ... }
     *     public String getInterfaceHash() { return IEventListener.HASH; }
     *     public int getInterfaceVersion() { return IEventListener.VERSION; }
     * };
     * TranPerfEvent.registerEventListener(stub.asBinder());
     * </pre>
     *
     * @param listenerBinder IBinder of an IEventListener.Stub; must not be null
     * @hide
     */
    public static void registerEventListener(IBinder listenerBinder) {
        registerEventListener(listenerBinder, new int[0]);
    }

    /**
     * Register a cross-process event listener with event filter.
     *
     * @param listenerBinder IBinder of an IEventListener.Stub; must not be null
     * @param eventFilter    Array of eventIds to subscribe; empty array means all events
     * @hide
     */
    public static void registerEventListener(IBinder listenerBinder, int[] eventFilter) {
        if (listenerBinder == null) {
            Log.e(TAG, "registerEventListener: listenerBinder is null");
            return;
        }
        try {
            nativeRegisterEventListener(
                    listenerBinder, eventFilter != null ? eventFilter : new int[0]);
            if (DEBUG) {
                Log.d(TAG, "registerEventListener: registered to vendor service");
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "nativeRegisterEventListener: JNI not linked", e);
        }
    }

    /**
     * Unregister a previously registered cross-process listener.
     *
     * @param listenerBinder IBinder that was passed to registerEventListener
     * @hide
     */
    public static void unregisterEventListener(IBinder listenerBinder) {
        if (listenerBinder == null)
            return;
        try {
            nativeUnregisterEventListener(listenerBinder);
            if (DEBUG) {
                Log.d(TAG, "unregisterEventListener: unregistered from vendor service");
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "nativeUnregisterEventListener: JNI not linked", e);
        }
    }

    // ==================== Event Start Methods ====================

    /**
     * Overload 1: eventId + timestamp
     * @hide
     */
    public static void notifyEventStart(int eventId, long timestamp) {
        notifyEventStartInternal(eventId, timestamp, 0, null, null);
    }

    /**
     * Overload 2: eventId + timestamp + string
     * @hide
     */
    public static void notifyEventStart(int eventId, long timestamp, String extraString) {
        notifyEventStartInternal(eventId, timestamp, 0, null, extraString);
    }

    /**
     * Overload 3: eventId + timestamp + string[]
     * @hide
     */
    public static void notifyEventStart(int eventId, long timestamp, String[] stringParams) {
        notifyEventStartInternal(eventId, timestamp, 0, null, joinStrings(stringParams));
    }

    /**
     * Overload 4: eventId + timestamp + numParams + int[]
     * @hide
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams) {
        notifyEventStartInternal(eventId, timestamp, numParams, intParams, null);
    }

    /**
     * Overload 5: eventId + timestamp + numParams + int[] + string[]
     * @hide
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams, String[] stringParams) {
        notifyEventStartInternal(
                eventId, timestamp, numParams, intParams, joinStrings(stringParams));
    }

    /**
     * Internal implementation — all notifyEventStart overloads route here.
     */
    private static void notifyEventStartInternal(
            int eventId, long timestamp, int numParams, int[] intParams, String extraStrings) {
        // if (!Flags.enablePerfengine()) {
        if (!ENABLE_PERFENGINE) {
            if (DEBUG)
                Log.d(TAG, "PerfEngine disabled");
            return;
        }

        if (!checkCallerPermission()) {
            if (DEBUG) {
                Log.w(TAG, "notifyEventStart denied: uid=" + MY_UID + " eventId=0x"
                                   + Integer.toHexString(eventId));
            }
            return;
        }

        // Latency monitoring
        final long receiveTs = SystemClock.elapsedRealtimeNanos();
        final long latency = receiveTs - timestamp;
        if (DEBUG || latency > LATENCY_THRESHOLD_NS) {
            Log.d(TAG,
                    String.format(
                            "notifyEventStart: eventId=0x%x latency=%dns(%.2fms) numParams=%d extra=%s",
                            eventId, latency, latency / 1_000_000.0, numParams, extraStrings));
        }

        final int[] safeParams = (intParams != null) ? intParams : new int[0];
        // 1. Forward to vendor service via JNI
        try {
            nativeNotifyEventStart(eventId, timestamp, numParams, safeParams, extraStrings);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "nativeNotifyEventStart: JNI not linked", e);
        }

        // 2. Notify in-process simplified listeners (zero Binder, system_server internal)
        if (!sSimplifiedListeners.isEmpty()) {
            final int duration = (safeParams.length > 0) ? safeParams[0] : 0;
            for (TrEventListener listener : sSimplifiedListeners) {
                try {
                    listener.onEventStart(eventId, timestamp, duration);
                } catch (Exception e) {
                    Log.e(TAG, "TrEventListener.onEventStart failed", e);
                }
            }
        }

    }

    // ==================== Event End Methods ====================

    /**
     * Overload 1: eventId + timestamp
     * @hide
     */
    public static void notifyEventEnd(int eventId, long timestamp) {
        notifyEventEnd(eventId, timestamp, null);
    }

    /**
     * Overload 2: eventId + timestamp + string
     * @hide
     */
    public static void notifyEventEnd(int eventId, long timestamp, String extraString) {
        // if (!Flags.enablePerfengine()) {
        if (!ENABLE_PERFENGINE) {
            return;
        }

        if (!checkCallerPermission()) {
            if (DEBUG) {
                Log.w(TAG, "notifyEventEnd denied: uid=" + MY_UID + " eventId=0x"
                                   + Integer.toHexString(eventId));
            }
            return;
        }

        // Latency monitoring
        final long receiveTs = SystemClock.elapsedRealtimeNanos();
        final long latency = receiveTs - timestamp;
        if (DEBUG || latency > LATENCY_THRESHOLD_NS) {
            Log.d(TAG, String.format("notifyEventEnd: eventId=0x%x latency=%dns(%.2fms) extra=%s",
                               eventId, latency, latency / 1_000_000.0, extraString));
        }

        // 1. Forward to vendor service via JNI
        try {
            nativeNotifyEventEnd(eventId, timestamp, extraString);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "nativeNotifyEventEnd: JNI not linked", e);
        }

        // 2. Notify in-process simplified listeners
        if (!sSimplifiedListeners.isEmpty()) {
            for (TrEventListener listener : sSimplifiedListeners) {
                try {
                    listener.onEventEnd(eventId, timestamp);
                } catch (Exception e) {
                    Log.e(TAG, "TrEventListener.onEventEnd failed", e);
                }
            }
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Get current timestamp in nanoseconds.
     * Recommended for use as the timestamp argument to notifyEventStart/End.
     *
     * @hide
     */
    public static long now() {
        return SystemClock.elapsedRealtimeNanos();
    }

    /**
     * Get human-readable event name (for debugging).
     * @hide
     */
    public static String getEventName(int eventId) {
        if (eventId == EVENT_SYS_TOUCH)
            return "SYS_TOUCH";
        if (eventId == EVENT_SYS_APP_LAUNCH_COLD)
            return "SYS_APP_LAUNCH_COLD";
        if (eventId == EVENT_SYS_APP_LAUNCH_WARM)
            return "SYS_APP_LAUNCH_WARM";
        if (eventId == EVENT_SYS_ACTIVITY_SWITCH)
            return "SYS_ACTIVITY_SWITCH";
        if (eventId == EVENT_SYS_FLING)
            return "SYS_FLING";
        if (eventId == EVENT_SYS_SCROLL)
            return "SYS_SCROLL";
        if (eventId == EVENT_SYS_AMIN_TRANSITION)
            return "SYS_AMIN_TRANSITION";
        if (eventId == EVENT_SYS_CAMERA_LAUNCH)
            return "SYS_CAMERA_LAUNCH";
        return "UNKNOWN(0x" + Integer.toHexString(eventId) + ")";
    }

    /**
     * Join a string array with the unit-separator character '\u001F'.
     * Returns null if the array is null or empty.
     */
    private static String joinStrings(String[] strings) {
        if (strings == null || strings.length == 0)
            return null;
        if (strings.length == 1)
            return strings[0];
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            if (i > 0)
                sb.append(STRING_SEPARATOR);
            sb.append(strings[i] != null ? strings[i] : "");
        }
        return sb.toString();
    }

    /**
     * Check whether the calling process has PERF_ENGINE_NOTIFY permission.
     *
     * Fast path: system processes (uid < FIRST_APPLICATION_UID) are always trusted.
     * Slow path: app processes check via PackageManager.
     */
    private static boolean checkCallerPermission() {
        if (IS_SYSTEM_PROCESS) {
            return true;
        }
        try {
            android.app.Application app = android.app.ActivityThread.currentApplication();
            if (app == null) {
                Log.w(TAG, "checkCallerPermission: no Application context, denying uid=" + MY_UID);
                return false;
            }
            return app.checkCallingOrSelfPermission(PERF_ENGINE_PERMISSION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            Log.e(TAG, "checkCallerPermission exception, denying", e);
            return false;
        }
    }

    // Private constructor — static utility class
    private TranPerfEvent() {
        throw new AssertionError("TranPerfEvent cannot be instantiated");
    }
}
