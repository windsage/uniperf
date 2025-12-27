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
 * 3. Interface is simple and extensible
 * 
 * Compatibility:
 * - System: Android 16+
 * - Vendor: Android 14+
 */
interface ITranPerfHub {
    /**
     * Notify performance event start
     * 
     * @param eventType Event type
     *        1 = EVENT_APP_LAUNCH    (App launch)
     *        2 = EVENT_SCROLL         (Scrolling)
     *        3 = EVENT_ANIMATION      (Animation)
     *        4 = EVENT_WINDOW_SWITCH  (Window switch)
     *        5 = EVENT_TOUCH          (Touch)
     *        6 = EVENT_APP_SWITCH     (App switch)
     * 
     * @param eventParam Event parameter (business semantics)
     *        - EVENT_APP_LAUNCH: 0=cold start, 1=warm start
     *        - EVENT_SCROLL: scroll velocity (0-100)
     *        - Others: reserved for future use
     * 
     * @return handle Performance lock handle (>0 success, <=0 failure)
     *         Vendor layer maintains eventType → handle mapping
     */
    int notifyEventStart(int eventType, int eventParam);
    
    /**
     * Notify performance event end
     * 
     * @param eventType Event type
     *        Vendor layer finds corresponding handle by eventType and releases it
     */
    void notifyEventEnd(int eventType);
}