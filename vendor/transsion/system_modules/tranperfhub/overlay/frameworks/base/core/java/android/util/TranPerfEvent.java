package android.util;

import android.os.SystemClock;

import com.transsion.perfhub.aconfig.Flags;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TranPerfEvent - Performance Event API
 *
 * Unified interface for performance event notifications.
 * This class is part of the Android Framework overlay.
 *
 * Thread-safe.
 */
public final class TranPerfEvent {
    private static final String TAG = "TranPerfEvent";
    private static final boolean DEBUG = false;

    // String separator (ASCII 31 - Unit Separator)
    private static final char STRING_SEPARATOR = '\u001F';

    // Latency threshold for warning (10ms)
    private static final long LATENCY_THRESHOLD_NS = 10_000_000L;

    // ==================== Event Type Constants ====================

    /** App launch event */
    public static final int EVENT_APP_LAUNCH = 1;

    /** App switch event */
    public static final int EVENT_APP_SWITCH = 2;

    /** Scroll event */
    public static final int EVENT_SCROLL = 3;

    /** Camera open event */
    public static final int EVENT_CAMERA_OPEN = 4;

    /** Game start event */
    public static final int EVENT_GAME_START = 5;

    /** Video play event */
    public static final int EVENT_VIDEO_PLAY = 6;

    /** Animation event */
    public static final int EVENT_ANIMATION = 7;

    // ==================== Listener Interface ====================

    /**
     * Listener interface for performance events
     */
    public interface TrEventListener {
        /**
         * Called when an event starts
         *
         * @param eventId Event type identifier
         * @param timestamp Event timestamp in nanoseconds
         * @param duration Duration parameter (from intParams[0])
         */
        void onEventStart(int eventId, long timestamp, int duration);

        /**
         * Called when an event ends
         *
         * @param eventId Event type identifier
         * @param timestamp Event timestamp in nanoseconds
         */
        void onEventEnd(int eventId, long timestamp);
    }

    // ==================== Listener Management ====================

    private static final CopyOnWriteArrayList<TrEventListener> sListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Register an event listener
     */
    public static void registerListener(TrEventListener listener) {
        if (listener != null && !sListeners.contains(listener)) {
            sListeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     */
    public static void unregisterListener(TrEventListener listener) {
        sListeners.remove(listener);
    }

    // ==================== Reflection to TranPerfHub ====================

    private static volatile boolean sReflectionInitialized = false;
    private static Class<?> sPerfHubClass = null;
    private static Method sNotifyStartMethod = null;
    private static Method sNotifyEndMethod = null;

    /**
     * Initialize reflection to TranPerfHub (lazy)
     */
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

                sNotifyStartMethod = sPerfHubClass.getDeclaredMethod("notifyEventStart",
                        int.class, // eventId
                        long.class, // timestamp
                        int.class, // numParams
                        int[].class, // intParams
                        String.class // extraStrings
                );

                sNotifyEndMethod = sPerfHubClass.getDeclaredMethod("notifyEventEnd",
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

    // ==================== Event Start Methods ====================

    /**
     * Overload 1: eventId + timestamp
     */
    public static void notifyEventStart(int eventId, long timestamp) {
        notifyEventStartInternal(eventId, timestamp, 0, null, null);
    }

    /**
     * Overload 2: eventId + timestamp + string
     */
    public static void notifyEventStart(int eventId, long timestamp, String extraString) {
        notifyEventStartInternal(eventId, timestamp, 0, null, extraString);
    }

    /**
     * Overload 3: eventId + timestamp + string[]
     */
    public static void notifyEventStart(int eventId, long timestamp, String[] stringParams) {
        notifyEventStartInternal(eventId, timestamp, 0, null, joinStrings(stringParams));
    }

    /**
     * Overload 4: eventId + timestamp + numParams + int[]
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams) {
        notifyEventStartInternal(eventId, timestamp, numParams, intParams, null);
    }

    /**
     * Overload 5: eventId + timestamp + numParams + int[] + string[]
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams, String[] stringParams) {
        notifyEventStartInternal(
                eventId, timestamp, numParams, intParams, joinStrings(stringParams));
    }

    /**
     * Internal implementation - all overloads route here
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
            Log.d(TAG,
                    String.format(
                            "notifyEventStart: eventId=%d, timestamp=%d, latency=%d ns (%.2f ms), "
                                    + "numParams=%d, extraStrings=%s",
                            eventId, timestamp, latency, latency / 1_000_000.0, numParams,
                            extraStrings));
        }

        // Call TranPerfHub via reflection
        try {
            if (!initReflection()) {
                return;
            }

            sNotifyStartMethod.invoke(null, eventId, timestamp, numParams, intParams, extraStrings);

        } catch (Exception e) {
            Log.e(TAG, "Failed to call TranPerfHub.notifyEventStart", e);
        }

        // Notify listeners
        if (!sListeners.isEmpty()) {
            int duration = (intParams != null && intParams.length > 0) ? intParams[0] : 0;
            for (TrEventListener listener : sListeners) {
                try {
                    listener.onEventStart(eventId, timestamp, duration);
                } catch (Exception e) {
                    Log.e(TAG, "Listener callback failed", e);
                }
            }
        }
    }

    // ==================== Event End Methods ====================

    /**
     * Notify event end (minimal)
     */
    public static void notifyEventEnd(int eventId, long timestamp) {
        notifyEventEnd(eventId, timestamp, null);
    }

    /**
     * Notify event end with string parameter
     */
    public static void notifyEventEnd(int eventId, long timestamp, String extraString) {
        // Check aconfig flag
        if (!Flags.enableTranperfhub()) {
            return;
        }

        // Record receive timestamp for latency monitoring
        long receiveTimestamp = SystemClock.elapsedRealtimeNanos();
        long latency = receiveTimestamp - timestamp;

        if (DEBUG || latency > LATENCY_THRESHOLD_NS) {
            Log.d(TAG, String.format(
                               "notifyEventEnd: eventId=%d, timestamp=%d, latency=%d ns (%.2f ms), "
                                       + "extraString=%s",
                               eventId, timestamp, latency, latency / 1_000_000.0, extraString));
        }

        // Call TranPerfHub via reflection
        try {
            if (!initReflection()) {
                return;
            }

            sNotifyEndMethod.invoke(null, eventId, timestamp, extraString);

        } catch (Exception e) {
            Log.e(TAG, "Failed to call TranPerfHub.notifyEventEnd", e);
        }

        // Notify listeners
        if (!sListeners.isEmpty()) {
            for (TrEventListener listener : sListeners) {
                try {
                    listener.onEventEnd(eventId, timestamp);
                } catch (Exception e) {
                    Log.e(TAG, "Listener callback failed", e);
                }
            }
        }
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

    /**
     * Join string array with separator '\u001F'
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

    // Private constructor to prevent instantiation
    private TranPerfEvent() {
        throw new AssertionError("TranPerfEvent cannot be instantiated");
    }
}
