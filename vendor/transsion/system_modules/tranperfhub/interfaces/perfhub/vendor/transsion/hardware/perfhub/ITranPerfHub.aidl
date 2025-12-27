package vendor.transsion.hardware.perfhub;

/**
 * TranPerfHub Vendor AIDL Interface
 * 
 * Version: 1
 * Stability: vintf (frozen after release)
 * 
 * Design Principles:
 * 1. System layer only passes business semantics (eventType + eventParam)
 * 2. Vendor layer handles platform adaptation and parameter mapping
 * 3. Use oneway for performance-critical event notifications
 * 4. Use synchronous only when return value is necessary
 * 
 * Compatibility:
 * - System: Android 16+
 * - Vendor: Android 14+
 */
interface ITranPerfHub {
    /**
     * Notify performance event start (asynchronous, fire-and-forget)
     * 
     * This method uses oneway for minimal latency. The caller will not wait
     * for vendor processing. Event tracking is managed internally by the
     * vendor service.
     * 
     * Performance: <100μs (just Binder send, no wait)
     * 
     * @param eventType Event type
     *        1 = EVENT_APP_LAUNCH
     *        2 = EVENT_SCROLL
     *        3 = EVENT_ANIMATION
     *        4 = EVENT_WINDOW_SWITCH
     *        5 = EVENT_TOUCH
     *        6 = EVENT_APP_SWITCH
     * 
     * @param eventParam Event parameter
     *        - EVENT_APP_LAUNCH: 0=cold start, 1=warm start
     *        - EVENT_SCROLL: scroll velocity (0-100)
     *        - Others: reserved
     */
    oneway void notifyEventStart(int eventType, int eventParam);
    
    /**
     * Notify performance event end (asynchronous, fire-and-forget)
     * 
     * @param eventType Event type
     */
    oneway void notifyEventEnd(int eventType);
}
