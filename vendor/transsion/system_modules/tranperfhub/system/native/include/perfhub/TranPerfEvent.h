/*
 * Copyright (C) 2025 Transsion Holdings Limited
 *
 * TranPerfEvent - Native API for Performance Events
 */

#ifndef TRANSSION_TRANPERFEVENT_H
#define TRANSSION_TRANPERFEVENT_H

#include <stdint.h>

namespace android {
namespace transsion {

/**
 * TranPerfEvent - Native layer performance event API
 *
 * Usage:
 * #include <perfhub/TranPerfEvent.h>
 *
 * using namespace android::transsion;
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
     * @param eventId Event type (EVENT_*)
     * @param eventParam Event parameter (default 0)
     * @return handle (>0 success, <=0 failure)
     */
    static int32_t notifyEventStart(int32_t eventId, int32_t eventParam = 0);

    /**
     * Notify that a performance event has ended.
     *
     * @param eventId Event type (EVENT_*)
     */
    static void notifyEventEnd(int32_t eventId);

private:
    // Prevent instantiation
    TranPerfEvent() = delete;
    ~TranPerfEvent() = delete;
};

}    // namespace transsion
}    // namespace android

#endif    // TRANSSION_TRANPERFEVENT_H
