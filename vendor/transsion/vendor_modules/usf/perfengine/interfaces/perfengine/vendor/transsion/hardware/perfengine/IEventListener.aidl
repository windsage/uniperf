package vendor.transsion.hardware.perfengine;

/**
 * Performance Event Listener Interface
 *
 * Implemented by processes that want to receive performance event notifications
 * from PerfEngine Adapter.
 *
 * All methods are oneway (fire-and-forget) to avoid blocking the adapter.
 *
 * Usage:
 *   1. Implement this interface in your process
 *   2. Register via IPerfEngine.registerEventListener()
 *   3. Receive event notifications asynchronously
 *   4. Unregister via IPerfEngine.unregisterEventListener() when done
 */
@VintfStability
interface IEventListener {
    /**
     * Called when a performance event starts
     *
     * @param eventId Event type identifier (EVENT_APP_LAUNCH, EVENT_SCROLL, etc.)
     * @param timestamp Event timestamp in nanoseconds (SystemClock.elapsedRealtimeNanos())
     * @param numParams Number of integer parameters (intParams.length)
     * @param intParams Integer parameters array
     *                  intParams[0] = duration (ms), 0 means manual release
     *                  intParams[1..n] = event-specific parameters
     * @param extraStrings Optional string parameters separated by '\x1F' (ASCII Unit Separator)
     *                     Example: "com.android.settings\x1F.MainActivity\x1Fcold_start"
     *                     Can be null if no string parameters needed.
     *
     * Note: This is a oneway call - no return value, non-blocking
     */
    oneway void onEventStart(
        int eventId,
        long timestamp,
        int numParams,
        in int[] intParams,
        @nullable @utf8InCpp String extraStrings
    );

    /**
     * Called when a performance event ends
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param extraStrings Optional string parameters (usually packageName)
     *
     * Note: This is a oneway call - no return value, non-blocking
     */
    oneway void onEventEnd(
        int eventId,
        long timestamp,
        @nullable @utf8InCpp String extraStrings
    );
}
