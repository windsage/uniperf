package com.transsion.usf.perfengine;

import static com.transsion.usf.perfengine.TranPerfEventConstants.*;

import android.os.IBinder;

/**
 * TranPerfEvent - Performance Event API (SDK Stub)
 *
 * This is a compile-only stub for SDK consumers.
 * The actual implementation is provided at runtime by the system framework
 * (compiled into framework.jar via transsion_framework_overlay_sources).
 *
 * Usage:
 * <pre>
 * // In build.gradle:
 * compileOnly 'com.transsion.perfengine:tranperfevent:x.y.z'
 *
 * // In code:
 * import com.transsion.usf.perfengine.TranPerfEvent;
 * import static com.transsion.usf.perfengine.TranPerfEventConstants.*;
 *
 * // Send event
 * TranPerfEvent.notifyEventStart(EVENT_SYS_APP_LAUNCH_CLOD, TranPerfEvent.now());
 *
 * // Register cross-process listener (create IEventListener.Stub, pass its IBinder)
 * TranPerfEvent.registerEventListener(myStub.asBinder());
 * </pre>
 *
 * Note: All methods throw RuntimeException("Stub!") to prevent this stub
 * from being accidentally packaged into an APK at runtime.
 */
public final class TranPerfEvent {
    // ==================== Listener Interfaces ====================

    /**
     * In-process simplified listener for system_server internal use.
     *
     * Callbacks are dispatched synchronously within the same process.
     * Zero Binder overhead. Not suitable for cross-process use.
     *
     * For cross-process listening, use {@link #registerEventListener(IBinder)}.
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

    /**
     * Register an in-process simplified listener.
     * Suitable for system_server internal components only.
     *
     * @param listener TrEventListener implementation; ignored if null
     */
    public static void registerListener(TrEventListener listener) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Unregister an in-process simplified listener.
     *
     * @param listener TrEventListener to remove
     */
    public static void unregisterListener(TrEventListener listener) {
        throw new RuntimeException("Stub!");
    }

    // ==================== Cross-process Listener Management ====================

    /**
     * Register a cross-process event listener, subscribe to ALL events.
     *
     * The caller creates an {@code IEventListener.Stub} and passes its
     * {@code IBinder} here. This keeps TranPerfEvent free of any direct
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
     */
    public static void registerEventListener(IBinder listenerBinder) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Register a cross-process event listener with event filter.
     *
     * @param listenerBinder IBinder of an IEventListener.Stub; must not be null
     * @param eventFilter    Array of eventIds to subscribe; empty = all events
     */
    public static void registerEventListener(IBinder listenerBinder, int[] eventFilter) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Unregister a previously registered cross-process listener.
     *
     * @param listenerBinder IBinder that was passed to registerEventListener
     */
    public static void unregisterEventListener(IBinder listenerBinder) {
        throw new RuntimeException("Stub!");
    }

    // ==================== Event Start Methods ====================

    /**
     * Notify event start (minimal).
     *
     * @param eventId   Event type identifier (see TranPerfEventConstants)
     * @param timestamp Event timestamp in nanoseconds; use {@link #now()}
     */
    public static void notifyEventStart(int eventId, long timestamp) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Notify event start with a string parameter (e.g. package name).
     *
     * @param eventId     Event type identifier
     * @param timestamp   Event timestamp in nanoseconds
     * @param extraString Optional string parameter; usually packageName
     */
    public static void notifyEventStart(int eventId, long timestamp, String extraString) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Notify event start with a string array parameter.
     *
     * @param eventId      Event type identifier
     * @param timestamp    Event timestamp in nanoseconds
     * @param stringParams String parameters array; joined with '\u001F' separator internally
     */
    public static void notifyEventStart(int eventId, long timestamp, String[] stringParams) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Notify event start with integer parameters.
     *
     * @param eventId   Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param numParams Number of valid entries in intParams
     * @param intParams Integer parameters array
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Notify event start with integer and string parameters (full signature).
     *
     * @param eventId      Event type identifier
     * @param timestamp    Event timestamp in nanoseconds
     * @param numParams    Number of valid entries in intParams
     * @param intParams    Integer parameters array
     * @param stringParams String parameters array
     */
    public static void notifyEventStart(
            int eventId, long timestamp, int numParams, int[] intParams, String[] stringParams) {
        throw new RuntimeException("Stub!");
    }

    // ==================== Event End Methods ====================

    /**
     * Notify event end (minimal).
     *
     * @param eventId   Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     */
    public static void notifyEventEnd(int eventId, long timestamp) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Notify event end with a string parameter.
     *
     * @param eventId     Event type identifier
     * @param timestamp   Event timestamp in nanoseconds
     * @param extraString Optional string parameter; usually packageName
     */
    public static void notifyEventEnd(int eventId, long timestamp, String extraString) {
        throw new RuntimeException("Stub!");
    }

    // ==================== Helper Methods ====================

    /**
     * Get current timestamp in nanoseconds.
     * Recommended for use as the timestamp argument to notifyEventStart/End.
     *
     * @return Current elapsed realtime in nanoseconds
     */
    public static long now() {
        throw new RuntimeException("Stub!");
    }

    // Private constructor — static utility class
    private TranPerfEvent() {
        throw new AssertionError("TranPerfEvent cannot be instantiated");
    }
}
