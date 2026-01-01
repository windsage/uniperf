package vendor.transsion.hardware.perfhub;

import vendor.transsion.hardware.perfhub.IEventListener;

/**
 * TranPerfHub AIDL Interface
 * 
 * Performance event notification interface for cross-process communication
 * between System and Vendor partitions.
 */
@VintfStability
interface ITranPerfHub {
    /**
     * Notify performance event start
     *
     * @param eventId Event type identifier (EVENT_APP_LAUNCH, EVENT_SCROLL, etc.)
     * @param timestamp Event timestamp in nanoseconds (SystemClock.elapsedRealtimeNanos())
     * @param numParams Number of integer parameters (intParams.length)
     * @param intParams Integer parameters array (max 10 elements recommended)
     * @param extraStrings Optional string parameters separated by '\x1F' (ASCII Unit Separator)
     *                     Example: "com.android.settings\x1F.MainActivity\x1Fcold_start"
     *                     Can be null if no string parameters needed.
     */
    oneway void notifyEventStart(
        int eventId,
        long timestamp,
        int numParams,
        in int[] intParams,
        @nullable @utf8InCpp String extraStrings
    );

    /**
     * Notify performance event end
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param extraStrings Optional string parameters (usually packageName)
     */
    oneway void notifyEventEnd(
        int eventId,
        long timestamp,
        @nullable @utf8InCpp String extraStrings
    );

    // ==================== Event Listener Registration ====================

    /**
     * Register an event listener to receive performance event notifications
     * The listener will receive onEventStart() and onEventEnd() callbacks
     * whenever performance events occur.
     *
     * @param listener IEventListener implementation
     *
     * Note: This is NOT a oneway call - blocks until registration completes
     *       to ensure listener is properly registered before returning.
     *
     * Thread-safe: Can be called from any thread
     * Death notification: If the listener process dies, it will be automatically
     *                     unregistered via Binder death notification.
     */
    void registerEventListener(IEventListener listener);

    /**
     * Unregister a previously registered event listener
     * After this call, the listener will no longer receive event notifications.
     *
     * @param listener IEventListener implementation to unregister
     *
     * Note: This is NOT a oneway call - blocks until unregistration completes.
     * Thread-safe: Can be called from any thread
     */
    void unregisterEventListener(IEventListener listener);
}
