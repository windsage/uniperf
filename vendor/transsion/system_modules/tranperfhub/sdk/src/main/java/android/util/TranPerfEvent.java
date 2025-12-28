package android.util;

/**
 * TranPerfEvent - Performance Event API (SDK Stub)
 *
 * Lightweight SDK for third-party apps to send performance events.
 *
 * Usage:
 * dependencies {
 *     compileOnly 'com.transsion.perfhub:tranperfevent:1.0.0'
 * }
 *
 * At runtime, the system-provided implementation in framework.jar will be called.
 *
 * @hide
 */
public final class TranPerfEvent {
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

    // ==================== Event Listener Interface ====================

    /**
     * Listener interface for performance events.
     *
     * Note: Event listeners are only available in framework/system processes.
     * Third-party apps can only send events, not receive them.
     */
    public interface TrEventListener {
        /**
         * Called when a performance event starts.
         *
         * @param eventType Event type (EVENT_*)
         * @param eventParam Event-specific parameter
         */
        void onEventStart(int eventType, int eventParam);

        /**
         * Called when a performance event ends.
         *
         * @param eventType Event type (EVENT_*)
         */
        void onEventEnd(int eventType);
    }

    // ==================== Public API ====================

    /**
     * Notify that a performance event has started.
     *
     * This triggers performance optimizations in the system.
     *
     * @param eventType Event type (EVENT_*)
     * @param eventParam Event-specific parameter (use 0 if not applicable)
     */
    public static void notifyEventStart(int eventType, int eventParam) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Notify that a performance event has ended.
     *
     * @param eventType Event type (EVENT_*)
     */
    public static void notifyEventEnd(int eventType) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Register an event listener.
     *
     * Note: Only available in framework/system processes.
     * Third-party apps cannot use this API.
     *
     * @param listener Event listener
     * @throws UnsupportedOperationException if called from third-party app
     */
    public static void registerListener(TrEventListener listener) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Unregister an event listener.
     *
     * @param listener Event listener to remove
     */
    public static void unregisterListener(TrEventListener listener) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    // Prevent instantiation
    private TranPerfEvent() {}
}