/*
 * Copyright (C) 2025 Transsion Holdings Limited
 *
 * TranPerfEvent - Vendor Native API
 */

#ifndef VENDOR_TRANSSION_TRANPERFEVENT_H
#define VENDOR_TRANSSION_TRANPERFEVENT_H

#include <stdint.h>

namespace vendor {
namespace transsion {
namespace perfhub {

/**
 * TranPerfEvent - Vendor layer performance event API
 * 
 * This API is for vendor HAL components to send performance events.
 * It directly calls PlatformAdapter without going through AIDL.
 * 
 * Usage:
 * #include <TranPerfEvent.h>
 * 
 * using namespace vendor::transsion::perfhub;
 * TranPerfEvent::notifyEventStart(TranPerfEvent::EVENT_APP_LAUNCH, 0);
 * TranPerfEvent::notifyEventEnd(TranPerfEvent::EVENT_APP_LAUNCH);
 */
class TranPerfEvent {
public:
    // ==================== Event Type Constants ====================
    
    /** App launch event */
    static constexpr int32_t EVENT_APP_LAUNCH = 1;
    
    /** Scroll event */
    static constexpr int32_t EVENT_SCROLL = 2;
    
    /** Animation event */
    static constexpr int32_t EVENT_ANIMATION = 3;
    
    /** Window switch event */
    static constexpr int32_t EVENT_WINDOW_SWITCH = 4;
    
    /** Touch event */
    static constexpr int32_t EVENT_TOUCH = 5;
    
    /** App switch event */
    static constexpr int32_t EVENT_APP_SWITCH = 6;
    
    // ==================== Event Parameter Constants ====================
    
    /** Cold start for app launch */
    static constexpr int32_t PARAM_COLD_START = 0;
    
    /** Warm start for app launch */
    static constexpr int32_t PARAM_WARM_START = 1;
    
    // ==================== Public API ====================
    
    /**
     * Notify that a performance event has started.
     * 
     * This API directly calls PlatformAdapter for optimal performance.
     * 
     * @param eventType Event type (EVENT_*)
     * @param eventParam Event parameter (default 0)
     * @return handle (>0 success, <=0 failure)
     */
    static int32_t notifyEventStart(int32_t eventType, int32_t eventParam = 0);
    
    /**
     * Notify that a performance event has ended.
     * 
     * @param eventType Event type (EVENT_*)
     */
    static void notifyEventEnd(int32_t eventType);
    
    /**
     * Notify event end by handle.
     * 
     * @param handle Performance lock handle returned by notifyEventStart()
     */
    static void releaseHandle(int32_t handle);

private:
    // Prevent instantiation
    TranPerfEvent() = delete;
    ~TranPerfEvent() = delete;
};

} // namespace perfhub
} // namespace transsion
} // namespace vendor

#endif // VENDOR_TRANSSION_TRANPERFEVENT_H
