package android.util;

/**
 * TranPerfEvent - Performance Event API (SDK Stub)
 *
 * This is a stub implementation for SDK compilation.
 * The actual implementation is provided by the system overlay.
 *
 * @hide
 */
public final class TranPerfEvent {
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
     */
    public interface TrEventListener {
        void onEventStart(int eventId, long timestamp, int duration);
        void onEventEnd(int eventId, long timestamp);
    }

    // ==================== Listener Management (Simplified) ====================

    /**
     * Register a simplified event listener
     */
    public static void registerListener(TrEventListener listener) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Unregister a simplified event listener
     */
    public static void unregisterListener(TrEventListener listener) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Register a full AIDL event listener
     *
     * Note: The actual parameter type is vendor.transsion.hardware.perfhub.IEventListener,
     * but SDK cannot reference vendor AIDL directly. Use Object as placeholder.
     *
     * At runtime, the overlay implementation will handle the actual IEventListener type.
     */
    public static void registerEventListener(Object listener) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Unregister a full AIDL event listener
     *
     * Note: The actual parameter type is vendor.transsion.hardware.perfhub.IEventListener,
     * but SDK cannot reference vendor AIDL directly. Use Object as placeholder.
     */
    public static void unregisterEventListener(Object listener) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    // ==================== Event Start Methods ====================

    /**
     * Overload 1: eventId + timestamp
     */
    public static void notifyEventStart(int eventId, long timestamp) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Overload 2: eventId + timestamp + string
     */
    public static void notifyEventStart(int eventId, long timestamp, String extraString) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Overload 3: eventId + timestamp + string[]
     */
    public static void notifyEventStart(int eventId, long timestamp, String[] stringParams) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Overload 4: eventId + timestamp + numParams + int[]
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Overload 5: eventId + timestamp + numParams + int[] + string[]
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams, String[] stringParams) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    // ==================== Event End Methods ====================

    /**
     * Notify event end (minimal)
     */
    public static void notifyEventEnd(int eventId, long timestamp) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    /**
     * Notify event end with string parameter
     */
    public static void notifyEventEnd(int eventId, long timestamp, String extraString) {
        throw new RuntimeException("Stub! System implementation required.");
    }

    // ==================== Helper Methods ====================

    /**
     * Get current timestamp in nanoseconds
     */
    public static long now() {
        throw new RuntimeException("Stub! System implementation required.");
    }

    // Private constructor to prevent instantiation
    private TranPerfEvent() {
        throw new AssertionError("TranPerfEvent cannot be instantiated");
    }
}
