package android.util;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;

import com.transsion.perfhub.flags.Flags;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

import vendor.transsion.hardware.perfhub.IEventListener;

/**
 * TranPerfEvent - Performance Event API
 *
 * Unified interface for performance event notifications.
 * This class is part of the Android Framework overlay.
 *
 * Thread-safe.
 *
 * Usage:
 * <pre>
 * // Send event
 * long ts = TranPerfEvent.now();
 * TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts, 3000, "com.android.settings");
 *
 * // Register simplified listener (Framework internal use)
 * TranPerfEvent.registerListener(new TrEventListener() {
 *     public void onEventStart(int eventId, long timestamp, int duration) {
 *         // Handle event
 *     }
 *     public void onEventEnd(int eventId, long timestamp) {
 *         // Handle event
 *     }
 * });
 *
 * // Register full AIDL listener (Advanced use)
 * TranPerfEvent.registerEventListener(new IEventListener.Stub() {
 *     public void onEventStart(int eventId, long timestamp, int numParams,
 *                              int[] intParams, String extraStrings) {
 *         // Handle full event data
 *     }
 *     public void onEventEnd(int eventId, long timestamp, String extraStrings) {
 *         // Handle event end
 *     }
 * });
 * </pre>
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

    // ==================== Listener Interfaces ====================

    /**
     * Simplified listener interface for Framework internal use
     *
     * Provides only essential event information for performance monitoring.
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

    // Simplified listeners (Framework internal)
    private static final CopyOnWriteArrayList<TrEventListener> sSimplifiedListeners =
            new CopyOnWriteArrayList<>();

    // Full AIDL listeners (Advanced use)
    private static final CopyOnWriteArrayList<IEventListener> sAidlListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Register a simplified event listener (Framework internal use)
     *
     * This listener receives only essential event information.
     *
     * @param listener TrEventListener implementation
     */
    public static void registerListener(TrEventListener listener) {
        if (listener != null && !sSimplifiedListeners.contains(listener)) {
            sSimplifiedListeners.add(listener);
            if (DEBUG) {
                Log.d(TAG, "Simplified listener registered, total: " + sSimplifiedListeners.size());
            }
        }
    }

    /**
     * Unregister a simplified event listener
     *
     * @param listener TrEventListener implementation to remove
     */
    public static void unregisterListener(TrEventListener listener) {
        sSimplifiedListeners.remove(listener);
        if (DEBUG) {
            Log.d(TAG,
                    "Simplified listener unregistered, remaining: " + sSimplifiedListeners.size());
        }
    }

    /**
     * Register a full AIDL event listener (Advanced use)
     *
     * This listener receives complete event information including all parameters.
     * The listener will also be registered to TranPerfHub for remote events.
     *
     * @param listener IEventListener implementation
     */
    public static void registerEventListener(IEventListener listener) {
        if (listener == null) {
            Log.e(TAG, "Cannot register null AIDL listener");
            return;
        }

        if (sAidlListeners.contains(listener)) {
            Log.w(TAG, "AIDL listener already registered");
            return;
        }

        // Add to local list
        sAidlListeners.add(listener);

        // Register to TranPerfHub for remote events
        try {
            if (!initReflection()) {
                Log.e(TAG, "Failed to initialize TranPerfHub reflection");
                return;
            }

            if (sRegisterListenerMethod != null) {
                sRegisterListenerMethod.invoke(null, listener);
                if (DEBUG) {
                    Log.d(TAG, "AIDL listener registered, total: " + sAidlListeners.size());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register AIDL listener to TranPerfHub", e);
        }
    }

    /**
     * Unregister a full AIDL event listener
     *
     * @param listener IEventListener implementation to remove
     */
    public static void unregisterEventListener(IEventListener listener) {
        if (listener == null) {
            return;
        }

        // Remove from local list
        sAidlListeners.remove(listener);

        // Unregister from TranPerfHub
        try {
            if (!initReflection()) {
                return;
            }

            if (sUnregisterListenerMethod != null) {
                sUnregisterListenerMethod.invoke(null, listener);
                if (DEBUG) {
                    Log.d(TAG, "AIDL listener unregistered, remaining: " + sAidlListeners.size());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister AIDL listener from TranPerfHub", e);
        }
    }

    // ==================== Reflection to TranPerfHub ====================

    private static volatile boolean sReflectionInitialized = false;
    private static Class<?> sPerfHubClass = null;
    private static Method sNotifyStartMethod = null;
    private static Method sNotifyEndMethod = null;
    private static Method sRegisterListenerMethod = null;
    private static Method sUnregisterListenerMethod = null;

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

                // Event notification methods
                sNotifyStartMethod =
                        sPerfHubClass.getDeclaredMethod("notifyEventStart", int.class, // eventId
                                long.class, // timestamp
                                int.class, // numParams
                                int[].class, // intParams
                                String.class // extraStrings
                        );

                sNotifyEndMethod =
                        sPerfHubClass.getDeclaredMethod("notifyEventEnd", int.class, // eventId
                                long.class, // timestamp
                                String.class // extraStrings
                        );

                // Listener registration methods (新增)
                try {
                    Class<?> listenerClass =
                            Class.forName("vendor.transsion.hardware.perfhub.IEventListener");

                    sRegisterListenerMethod =
                            sPerfHubClass.getDeclaredMethod("registerEventListener", listenerClass);

                    sUnregisterListenerMethod = sPerfHubClass.getDeclaredMethod(
                            "unregisterEventListener", listenerClass);

                    if (DEBUG) {
                        Log.d(TAG, "Listener registration methods found");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Listener registration methods not available: " + e.getMessage());
                    // Not fatal - event notification still works
                }

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

        // 1. Notify local AIDL listeners (完整参数)
        if (!sAidlListeners.isEmpty()) {
            for (IEventListener listener : sAidlListeners) {
                try {
                    listener.onEventStart(eventId, timestamp, numParams, intParams, extraStrings);
                } catch (RemoteException e) {
                    Log.e(TAG, "AIDL listener callback failed", e);
                }
            }
        }

        // 2. Notify local simplified listeners (精简参数)
        if (!sSimplifiedListeners.isEmpty()) {
            int duration = (intParams != null && intParams.length > 0) ? intParams[0] : 0;
            for (TrEventListener listener : sSimplifiedListeners) {
                try {
                    listener.onEventStart(eventId, timestamp, duration);
                } catch (Exception e) {
                    Log.e(TAG, "Simplified listener callback failed", e);
                }
            }
        }

        // 3. Call TranPerfHub via reflection (远程通知)
        try {
            if (!initReflection()) {
                return;
            }

            sNotifyStartMethod.invoke(null, eventId, timestamp, numParams, intParams, extraStrings);

        } catch (Exception e) {
            Log.e(TAG, "Failed to call TranPerfHub.notifyEventStart", e);
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

        // 1. Notify local AIDL listeners (完整参数)
        if (!sAidlListeners.isEmpty()) {
            for (IEventListener listener : sAidlListeners) {
                try {
                    listener.onEventEnd(eventId, timestamp, extraString);
                } catch (RemoteException e) {
                    Log.e(TAG, "AIDL listener callback failed", e);
                }
            }
        }

        // 2. Notify local simplified listeners
        if (!sSimplifiedListeners.isEmpty()) {
            for (TrEventListener listener : sSimplifiedListeners) {
                try {
                    listener.onEventEnd(eventId, timestamp);
                } catch (Exception e) {
                    Log.e(TAG, "Simplified listener callback failed", e);
                }
            }
        }

        // 3. Call TranPerfHub via reflection (远程通知)
        try {
            if (!initReflection()) {
                return;
            }

            sNotifyEndMethod.invoke(null, eventId, timestamp, extraString);

        } catch (Exception e) {
            Log.e(TAG, "Failed to call TranPerfHub.notifyEventEnd", e);
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
