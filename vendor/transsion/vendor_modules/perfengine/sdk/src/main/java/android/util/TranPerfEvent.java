package android.util;

import android.os.IBinder;

/**
 * TranPerfEvent - Performance Event API (SDK Stub)
 *
 * This is a stub implementation for SDK compilation.
 * The actual implementation is provided by the system framework overlay at runtime.
 * * Note: Methods throw RuntimeException to ensure that this stub is never
 * packaged into an APK; it should be used with 'compileOnly' dependency.
 */
public final class TranPerfEvent {
    // ==================== Event Type Constants ====================

    public static final int EVENT_APP_LAUNCH = 1;
    public static final int EVENT_APP_SWITCH = 2;
    public static final int EVENT_SCROLL = 3;
    public static final int EVENT_CAMERA_OPEN = 4;
    public static final int EVENT_GAME_START = 5;
    public static final int EVENT_VIDEO_PLAY = 6;
    public static final int EVENT_ANIMATION = 7;

    // ==================== Listener Interfaces ====================

    /**
     * Simplified listener interface for Framework internal use.
     */
    public interface TrEventListener {
        void onEventStart(int eventId, long timestamp, int duration);
        void onEventEnd(int eventId, long timestamp);
    }

    /**
     * Full event listener for SDK and external app use.
     * Implementation should inherit this class.
     */
    public abstract static class PerfEventListener {
        public abstract void onEventStart(
                int eventId, long timestamp, int numParams, int[] intParams, String extraStrings);
        public abstract void onEventEnd(int eventId, long timestamp, String extraStrings);

        /** @hide */
        private Object mStub;
    }

    // ==================== Listener Management ====================

    /**
     * Register a simplified listener (System internal use).
     */
    public static void registerListener(TrEventListener listener) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Unregister a simplified listener.
     */
    public static void unregisterListener(TrEventListener listener) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Recommended: Register a listener using the abstract class.
     */
    public static void registerEventListener(PerfEventListener listener) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Unregister a listener.
     */
    public static void unregisterEventListener(PerfEventListener listener) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Bottom-level: Register using a raw IBinder.
     */
    public static void registerEventListener(android.os.IBinder listenerBinder) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Bottom-level: Unregister using a raw IBinder.
     */
    public static void unregisterEventListener(android.os.IBinder listenerBinder) {
        throw new RuntimeException("Stub!");
    }

    // ==================== Event Start Methods ====================

    public static void notifyEventStart(int eventId, long timestamp) {
        throw new RuntimeException("Stub!");
    }

    public static void notifyEventStart(int eventId, long timestamp, String extraString) {
        throw new RuntimeException("Stub!");
    }

    public static void notifyEventStart(int eventId, long timestamp, String[] stringParams) {
        throw new RuntimeException("Stub!");
    }

    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams) {
        throw new RuntimeException("Stub!");
    }

    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams, String[] stringParams) {
        throw new RuntimeException("Stub!");
    }

    // ==================== Event End Methods ====================

    public static void notifyEventEnd(int eventId, long timestamp) {
        throw new RuntimeException("Stub!");
    }

    public static void notifyEventEnd(int eventId, long timestamp, String extraString) {
        throw new RuntimeException("Stub!");
    }

    // ==================== Helper Methods ====================

    public static long now() {
        throw new RuntimeException("Stub!");
    }

    private TranPerfEvent() {
        throw new AssertionError("TranPerfEvent cannot be instantiated");
    }
}
