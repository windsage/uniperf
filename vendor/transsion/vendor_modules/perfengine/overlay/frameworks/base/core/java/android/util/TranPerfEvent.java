package android.util;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

import vendor.transsion.hardware.perfengine.IEventListener;

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
 * TranPerfEvent.notifyEventStart(EVENT_APP_LAUNCH, ts, 3, new int[]{1, 2, 3},
 * "com.android.settings");
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

    // ========================================================================
    // EVENT RANGE DEFINITIONS
    // Each range is allocated 1000 IDs to allow sub-categorization.
    // ========================================================================

    // ==================== Core / Base Events (0 - 999) ====================
    /** Most common system-level events */
    public static final int RANGE_CORE = 0;
    public static final int EVENT_ID_UNKNOWN = 0;

    // ==================== Interaction & UX (1000 - 4999) ====================
    /** Application launch (Cold, Warm, Hot start) */
    public static final int RANGE_LAUNCH = 1000;
    /** Scrolling scenarios (List fling, Page scroll) */
    public static final int RANGE_SCROLLING = 1100;
    /** Input response (Key click, Touch gesture) */
    public static final int RANGE_INPUT_TOUCH = 1200;
    /** Interface transition (Activity jump, Fragment switch) */
    public static final int RANGE_TRANSITION = 1300;

    // ==================== System Components (5000 - 9999) ====================
    /** SystemUI scenarios (Notification shade, Control Center, Keyguard) */
    public static final int RANGE_SYSTEMUI = 5000;
    /** Home screen scenarios (Launcher, Minus-one screen, App drawer) */
    public static final int RANGE_HOME_SCREEN = 6000;
    /** Multi-window & Split-screen (Task switching, Freeform) */
    public static final int RANGE_MULTI_WINDOW = 7000;

    // ==================== Multimedia & Communication (10000 - 19999) ====================
    /** Camera scenarios (Preview, Capture, Recording) */
    public static final int RANGE_CAMERA = 10000;
    /** Audio scenarios (Playback, Recording, Audio Effects) */
    public static final int RANGE_AUDIO = 11000;
    /** Video scenarios (Encoding, Decoding, Rendering) */
    public static final int RANGE_VIDEO = 12000;
    /** WebView & Browser (Page loading, JS execution) */
    public static final int RANGE_WEBVIEW = 13000;
    /** Telephony scenarios (Voice call, CS/IMS call setup) */
    public static final int RANGE_TELEPHONY = 14000;
    /** Video Call scenarios (VoLTE/ViLTE, VoIP, Screen sharing) */
    public static final int RANGE_VIDEO_CALL = 15000;

    // ==================== Vertical Domains (20000+) ====================
    /** Game scenarios (Frame drops, Asset loading) */
    public static final int RANGE_GAME = 20000;
    /** Location scenarios (GNSS signal, Network positioning) */
    public static final int RANGE_LOCATION = 21000;

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

    /**
     * 2. 完整事件监听器 (新增，用于 SDK 极致解耦)
     * 调用方继承此类，无需直接依赖 vendor.transsion 命名空间
     */
    public abstract static class PerfEventListener {
        public abstract void onEventStart(
                int eventId, long timestamp, int numParams, int[] intParams, String extraStrings);
        public abstract void onEventEnd(int eventId, long timestamp, String extraStrings);

        /** @hide */
        private Object mStub; // 存储 IEventListener.Stub 实例
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
     * @hide
     */
    @SystemApi
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
     * @hide
     */
    @SystemApi
    public static void unregisterListener(TrEventListener listener) {
        sSimplifiedListeners.remove(listener);
        if (DEBUG) {
            Log.d(TAG,
                    "Simplified listener unregistered, remaining: " + sSimplifiedListeners.size());
        }
    }

    /**
     * 3. 面向 SDK 的注册接口 (推荐)
     * 调用方只需 new TranPerfEvent.PerfEventListener() {}
     */
    public static void registerEventListener(PerfEventListener listener) {
        if (listener == null)
            return;
        try {
            // 动态包装成 AIDL Stub
            IEventListener.Stub stub = new IEventListener.Stub() {
                @Override
                public void onEventStart(int id, long ts, int n, int[] p, String s) {
                    listener.onEventStart(id, ts, n, p, s);
                }
                @Override
                public void onEventEnd(int id, long ts, String s) {
                    listener.onEventEnd(id, ts, s);
                }
            };
            listener.mStub = stub;
            registerEventListener(stub.asBinder());
        } catch (NoClassDefFoundError | Exception e) {
            Log.e(TAG, "Failed to wrap listener: IEventListener not found in this environment");
        }
    }

    public static void unregisterEventListener(PerfEventListener listener) {
        if (listener != null && listener.mStub instanceof IEventListener.Stub) {
            android.os.IBinder binder = ((IEventListener.Stub) listener.mStub).asBinder();
            unregisterEventListener(binder);
            listener.mStub = null;
        }
    }

    // ==================== Listener Management ====================

    /**
     * Register a full AIDL event listener (Advanced use)
     * * @param listenerBinder The IBinder object of the listener
     */
    public static void registerEventListener(android.os.IBinder listenerBinder) {
        if (listenerBinder == null) {
            Log.e(TAG, "Cannot register null binder");
            return;
        }

        // 将 IBinder 转换为真正的 AIDL 接口
        IEventListener listener = IEventListener.Stub.asInterface(listenerBinder);

        // 检查是否已经存在 (通过 Binder 比较)
        for (IEventListener existing : sAidlListeners) {
            if (existing.asBinder().equals(listenerBinder)) {
                Log.w(TAG, "AIDL listener already registered");
                return;
            }
        }

        // 添加到本地列表
        sAidlListeners.add(listener);

        // 注册到 PerfEngine
        try {
            if (!initReflection())
                return;
            if (sRegisterListenerMethod != null) {
                // 注意：反射调用时，如果 PerfEngine 那边接收的是 IEventListener，这里传入 listener
                // 对象即可
                sRegisterListenerMethod.invoke(null, listener);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register AIDL listener to PerfEngine", e);
        }
    }

    /**
     * Unregister a full AIDL event listener
     * * @param listenerBinder The IBinder object to remove
     */
    public static void unregisterEventListener(android.os.IBinder listenerBinder) {
        if (listenerBinder == null)
            return;

        IEventListener target = null;
        for (IEventListener listener : sAidlListeners) {
            if (listener.asBinder().equals(listenerBinder)) {
                target = listener;
                break;
            }
        }

        if (target != null) {
            sAidlListeners.remove(target);
            try {
                if (!initReflection())
                    return;
                if (sUnregisterListenerMethod != null) {
                    sUnregisterListenerMethod.invoke(null, target);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister AIDL listener", e);
            }
        }
    }

    // ==================== Reflection to PerfEngine ====================

    private static volatile boolean sReflectionInitialized = false;
    private static Class<?> sPerfEngineClass = null;
    private static Method sNotifyStartMethod = null;
    private static Method sNotifyEndMethod = null;
    private static Method sRegisterListenerMethod = null;
    private static Method sUnregisterListenerMethod = null;

    /**
     * Initialize reflection to PerfEngine (lazy)
     */
    private static boolean initReflection() {
        if (sReflectionInitialized) {
            return sPerfEngineClass != null;
        }

        synchronized (TranPerfEvent.class) {
            if (sReflectionInitialized) {
                return sPerfEngineClass != null;
            }

            try {
                sPerfEngineClass = Class.forName("com.transsion.perfengine.PerfEngine");

                // Event notification methods
                sNotifyStartMethod =
                        sPerfEngineClass.getDeclaredMethod("notifyEventStart", int.class, // eventId
                                long.class, // timestamp
                                int.class, // numParams
                                int[].class, // intParams
                                String.class // extraStrings
                        );

                sNotifyEndMethod =
                        sPerfEngineClass.getDeclaredMethod("notifyEventEnd", int.class, // eventId
                                long.class, // timestamp
                                String.class // extraStrings
                        );

                // Listener registration methods (新增)
                try {
                    Class<?> listenerClass =
                            Class.forName("vendor.transsion.hardware.perfengine.IEventListener");

                    sRegisterListenerMethod =
                            sPerfEngineClass.getDeclaredMethod("registerEventListener", listenerClass);

                    sUnregisterListenerMethod = sPerfEngineClass.getDeclaredMethod(
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
                Log.e(TAG, "Failed to initialize PerfEngine reflection", e);
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
        if (!Flags.enableTranperfengine()) {
            if (DEBUG) {
                Log.d(TAG, "PerfEngine disabled by flag");
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
        final int[] safeIntParams = (intParams != null) ? intParams : new int[0];
        // 1. Notify local AIDL listeners (完整参数)
        if (!sAidlListeners.isEmpty()) {
            for (IEventListener listener : sAidlListeners) {
                try {
                    listener.onEventStart(
                            eventId, timestamp, numParams, safeIntParams, extraStrings);
                } catch (RemoteException e) {
                    Log.e(TAG, "AIDL listener callback failed", e);
                }
            }
        }

        // 2. Notify local simplified listeners (精简参数)
        if (!sSimplifiedListeners.isEmpty()) {
            int duration =
                    (safeIntParams != null && safeIntParams.length > 0) ? safeIntParams[0] : 0;
            for (TrEventListener listener : sSimplifiedListeners) {
                try {
                    listener.onEventStart(eventId, timestamp, duration);
                } catch (Exception e) {
                    Log.e(TAG, "Simplified listener callback failed", e);
                }
            }
        }

        // 3. Call PerfEngine via reflection (远程通知)
        try {
            if (!initReflection()) {
                return;
            }

            sNotifyStartMethod.invoke(
                    null, eventId, timestamp, numParams, safeIntParams, extraStrings);

        } catch (Exception e) {
            Log.e(TAG, "Failed to call PerfEngine.notifyEventStart", e);
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
        if (!Flags.enableTranperfengine()) {
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

        // 3. Call PerfEngine via reflection (远程通知)
        try {
            if (!initReflection()) {
                return;
            }

            sNotifyEndMethod.invoke(null, eventId, timestamp, extraString);

        } catch (Exception e) {
            Log.e(TAG, "Failed to call PerfEngine.notifyEventEnd", e);
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
