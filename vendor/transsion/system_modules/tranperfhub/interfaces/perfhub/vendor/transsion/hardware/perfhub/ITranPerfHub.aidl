package vendor.transsion.hardware.perfhub;

/**
 * TranPerfHub AIDL Interface
 * 
 * Performance event notification interface for cross-process communication
 * between System and Vendor partitions.
 */
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
}
