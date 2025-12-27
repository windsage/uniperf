package vendor.transsion.hardware.perfhub;

/**
 * TranPerfHub Vendor AIDL Interface
 * 
 * Version: 1
 * Stability: vintf (frozen after release)
 * 
 * Design Principles:
 * 1. System layer only passes business semantics (sceneId + param)
 * 2. Vendor layer handles platform adaptation and parameter mapping
 * 3. Interface is simple and extensible
 * 
 * Compatibility:
 * - System: Android 16+
 * - Vendor: Android 14+
 */
interface ITranPerfHub {
    /**
     * Notify scene start
     * 
     * @param sceneId Scene ID
     *        1 = APP_LAUNCH    (App launch)
     *        2 = SCROLL         (Scrolling)
     *        3 = ANIMATION      (Animation)
     *        4 = WINDOW_SWITCH  (Window switch)
     *        5 = TOUCH          (Touch)
     *        6 = APP_SWITCH     (App switch)
     * 
     * @param param Scene parameter (business semantics)
     *        - APP_LAUNCH: 0=cold start, 1=warm start
     *        - SCROLL: scroll velocity (0-100)
     *        - Others: reserved for future
     * 
     * @return handle Performance lock handle (>0 success, <=0 failure)
     *         Vendor layer maintains sceneId → handle mapping
     */
    int notifySceneStart(int sceneId, int param);
    
    /**
     * Notify scene end
     * 
     * @param sceneId Scene ID
     *        Vendor layer finds corresponding handle by sceneId and releases it
     */
    void notifySceneEnd(int sceneId);
}
