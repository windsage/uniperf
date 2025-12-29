package android.util;

import android.os.SystemClock;
import android.util.Log;

import com.transsion.perfhub.flags.Flags;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TranPerfEvent - Unified Performance Event API
 *
 * Provides a unified interface for performance optimization across different
 * chipset platforms (QCOM, MTK, UNISOC).
 */
public final class TranPerfEvent {
    private static final String TAG = "TranPerfEvent";
    private static final boolean DEBUG = false;

    // String separator for extraStrings
    private static final char STRING_SEPARATOR = '\u001F'; // ASCII Unit Separator

    // Performance monitoring threshold (1ms)
    private static final long LATENCY_THRESHOLD_NS = 1_000_000L; // 1ms in nanoseconds

    // ==================== Event Type Constants ====================

    /** App launch event */
    public static final int EVENT_APP_LAUNCH = 1;

    /** Scroll event */
    public static final int EVENT_SCROLL = 2;

    /** Animation event */
    public static final int EVENT_ANIMATION = 3;

    /** Window switch event */
    public static final int EVENT_WINDOW_SWITCH = 4;

    /** Touch event */
    public static final int EVENT_TOUCH = 5;

    /** App switch event */
    public static final int EVENT_APP_SWITCH = 6;

    // ==================== Event Parameter Constants ====================

    /** Cold start for app launch */
    public static final int PARAM_COLD_START = 0;

    /** Warm start for app launch */
    public static final int PARAM_WARM_START = 1;

    // ==================== Event Listeners ====================

    /**
     * Listener interface for performance events
     */
    public interface TrEventListener {
        void onEventStart(int eventId, long timestamp, int eventParam);
        void onEventEnd(int eventId, long timestamp);
    }

    private static final CopyOnWriteArrayList<TrEventListener> sListeners =
            new CopyOnWriteArrayList<>();

    // Prevent instantiation
    private TranPerfEvent() {}

    // ==================== Overloaded API Methods ====================

    /**
     * Overload 1: Timestamp only (no parameters)
     *
     * Usage: Simple events without any parameters
     * Example:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventStart(EVENT_TOUCH, ts);
     */
    public static void notifyEventStart(int eventId, long timestamp) {
        notifyEventStartInternal(eventId, timestamp, 0, null, null);
    }

    /**
     * Overload 2: Timestamp + single int parameter
     *
     * Usage: Events with one integer parameter
     * Example:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts, PARAM_COLD_START);
     */
    public static void notifyEventStart(int eventId, long timestamp, int param) {
        notifyEventStartInternal(eventId, timestamp, 1, new int[] {param}, null);
    }

    /**
     * Overload 3: Timestamp + int array parameters
     *
     * Usage: Events with multiple integer parameters
     * Example:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventStart(EVENT_SCROLL, ts, new int[]{velocity, duration});
     */
    public static void notifyEventStart(int eventId, long timestamp, int[] intParams) {
        notifyEventStartInternal(
                eventId, timestamp, intParams != null ? intParams.length : 0, intParams, null);
    }

    /**
     * Overload 4: Timestamp + single string parameter
     *
     * Usage: Events with one string parameter (e.g., packageName)
     * Example:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts, "com.android.settings");
     */
    public static void notifyEventStart(int eventId, long timestamp, String stringParam) {
        notifyEventStartInternal(eventId, timestamp, 0, null, stringParam);
    }

    /**
     * Overload 5: Timestamp + string array parameters
     *
     * Usage: Events with multiple string parameters
     * Example:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts,
     *       new String[]{"com.android.settings", ".MainActivity", "cold_start"});
     */
    public static void notifyEventStart(int eventId, long timestamp, String[] stringParams) {
        notifyEventStartInternal(eventId, timestamp, 0, null, joinStrings(stringParams));
    }

    /**
     * Overload 6: Timestamp + int parameter + string parameter
     *
     * Usage: Events with both int and string parameters
     * Example:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts,
     *       PARAM_COLD_START, "com.android.settings");
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int intParam, String stringParam) {
        notifyEventStartInternal(eventId, timestamp, 1, new int[] {intParam}, stringParam);
    }

    /**
     * Overload 7: Timestamp + int array + string parameter
     *
     * Usage: Events with int array and one string
     * Example:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts,
     *       new int[]{PARAM_COLD_START, pid}, "com.android.settings");
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int[] intParams, String stringParam) {
        notifyEventStartInternal(eventId, timestamp, intParams != null ? intParams.length : 0,
                intParams, stringParam);
    }

    /**
     * Overload 8: Timestamp + int array + string array (Full parameters)
     *
     * Usage: Events with both int array and string array
     * Example:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts,
     *       new int[]{PARAM_COLD_START, pid, uid},
     *       new String[]{"com.android.settings", ".MainActivity"});
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int[] intParams, String[] stringParams) {
        notifyEventStartInternal(eventId, timestamp, intParams != null ? intParams.length : 0,
                intParams, joinStrings(stringParams));
    }

    // ==================== Internal Implementation ====================

    /**
     * Internal implementation - All overloads route here
     */
    private static void notifyEventStartInternal(
            int eventId, long timestamp, int numParams, int[] intParams, String extraStrings) {
        // Check aconfig flag
        if (!Flags.enableTranperfhub()) {
            if (DEBUG) {
                Log.d(TAG, "TranPerfHub disabled by flag");
            }
            return;
        }

        // Record receive timestamp for latency monitoring
        long receiveTimestamp = SystemClock.elapsedRealtimeNanos();
        long latency = receiveTimestamp - timestamp;

        if (DEBUG || latency > LATENCY_THRESHOLD_NS) {
            Log.d(TAG, String.format("notifyEventStart: eventId=%d, timestamp=%d, latency=%d ns "
                                             + "(%.2f ms), "
                                             + "numParams=%d, extraStrings=%s",
                               eventId, timestamp, latency, latency / 1_000_000.0, numParams,
                               extraStrings));
        }

        // Call TranPerfHub via reflection
        int handle = acquirePerfLock(eventId, timestamp, numParams, intParams, extraStrings);

        // Notify listeners
        for (TrEventListener listener : sListeners) {
            try {
                listener.onEventStart(eventId, timestamp, numParams > 0 ? intParams[0] : 0);
            } catch (Exception e) {
                Log.e(TAG, "Listener callback failed", e);
            }
        }
    }

    /**
     * Join string array with separator
     */
    private static String joinStrings(String[] strings) {
        if (strings == null || strings.length == 0) {
            return null;
        }

        if (strings.length == 1) {
            return strings[0];
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            if (i > 0) {
                sb.append(STRING_SEPARATOR);
            }
            sb.append(strings[i] != null ? strings[i] : "");
        }
        return sb.toString();
    }

    /**
     * Acquire performance lock (calls TranPerfHub)
     */
    private static int acquirePerfLock(
            int eventId, long timestamp, int numParams, int[] intParams, String extraStrings) {
        // Ensure intParams is not null
        if (intParams == null) {
            intParams = new int[0];
        }

        // Call TranPerfHub via reflection
        try {
            // Lazy initialization of reflection
            if (!initReflection()) {
                return -1;
            }

            // TranPerfHub.acquirePerfLock(eventId, timestamp, numParams, intParams, extraStrings)
            return (int) sAcquireMethod.invoke(null, // static method
                    eventId, timestamp, numParams, intParams, extraStrings);

        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire perf lock", e);
            return -1;
        }
    }

    // ==================== Event End Methods ====================

    /**
     * Notify event end (with timestamp)
     *
     * Usage:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventEnd(EVENT_APP_LAUNCH, ts);
     */
    public static void notifyEventEnd(int eventId, long timestamp) {
        notifyEventEnd(eventId, timestamp, null);
    }

    /**
     * Notify event end with timestamp and string parameter
     *
     * Usage:
     *   long ts = SystemClock.elapsedRealtimeNanos();
     *   TranPerfEvent.notifyEventEnd(EVENT_APP_LAUNCH, ts, "com.android.settings");
     */
    public static void notifyEventEnd(int eventId, long timestamp, String extraStrings) {
        if (!Flags.enableTranperfhub()) {
            return;
        }

        // Record receive timestamp for latency monitoring
        long receiveTimestamp = SystemClock.elapsedRealtimeNanos();
        long latency = receiveTimestamp - timestamp;

        if (DEBUG || latency > LATENCY_THRESHOLD_NS) {
            Log.d(TAG, String.format(
                               "notifyEventEnd: eventId=%d, timestamp=%d, latency=%d ns (%.2f ms)",
                               eventId, timestamp, latency, latency / 1_000_000.0));
        }

        releasePerfLock(eventId, timestamp, extraStrings);

        // Notify listeners
        for (TrEventListener listener : sListeners) {
            try {
                listener.onEventEnd(eventId, timestamp);
            } catch (Exception e) {
                Log.e(TAG, "Listener callback failed", e);
            }
        }
    }

    /**
     * Release performance lock (calls TranPerfHub)
     */
    private static void releasePerfLock(int eventId, long timestamp, String extraStrings) {
        try {
            if (!initReflection()) {
                return;
            }

            // TranPerfHub.releasePerfLock(eventId, timestamp, extraStrings)
            sReleaseMethod.invoke(null, eventId, timestamp, extraStrings);

        } catch (Exception e) {
            Log.e(TAG, "Failed to release perf lock", e);
        }
    }

    // ==================== Reflection Initialization ====================

    private static Class<?> sPerfHubClass;
    private static java.lang.reflect.Method sAcquireMethod;
    private static java.lang.reflect.Method sReleaseMethod;
    private static boolean sReflectionInitialized = false;

    private static boolean initReflection() {
        if (sReflectionInitialized) {
            return sPerfHubClass != null;
        }

        synchronized (TranPerfEvent.class) {
            if (sReflectionInitialized) {
                return sPerfHubClass != null;
            }

            try {
                sPerfHubClass = Class.forName("com.transsion.perfhub.TranPerfHub");

                sAcquireMethod = sPerfHubClass.getDeclaredMethod("acquirePerfLock",
                        int.class, // eventId
                        long.class, // timestamp
                        int.class, // numParams
                        int[].class, // intParams
                        String.class // extraStrings
                );

                sReleaseMethod = sPerfHubClass.getDeclaredMethod("releasePerfLock",
                        int.class, // eventId
                        long.class, // timestamp
                        String.class // extraStrings
                );

                sReflectionInitialized = true;
                return true;

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize TranPerfHub reflection", e);
                sReflectionInitialized = true;
                return false;
            }
        }
    }

    // ==================== Listener Management ====================

    public static void registerListener(TrEventListener listener) {
        if (listener != null && !sListeners.contains(listener)) {
            sListeners.add(listener);
        }
    }

    public static void unregisterListener(TrEventListener listener) {
        sListeners.remove(listener);
    }

    // ==================== Helper Methods ====================

    /**
     * Get current timestamp in nanoseconds
     *
     * Usage: Helper method for callers who need a timestamp
     * Example:
     *   long ts = TranPerfEvent.now();
     *   TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts);
     */
    public static long now() {
        return SystemClock.elapsedRealtimeNanos();
    }
}
