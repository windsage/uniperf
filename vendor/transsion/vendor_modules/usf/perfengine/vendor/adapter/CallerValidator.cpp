/*
 * CallerValidator.cpp
 */

#include "CallerValidator.h"

#include <utils/Timers.h>

#include <atomic>

#include "TranLog.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "TPE-Validator"

namespace vendor {
namespace transsion {
namespace perfengine {

// Log throttle: at most 1 denial log per uid per 5 seconds
static bool shouldLogDenial(uid_t uid) {
    // Simple per-uid timestamp throttle, no lock needed for approximate behavior
    static std::atomic<int64_t> sLastLogTimeNs{0};
    static std::atomic<uid_t> sLastLogUid{0};

    int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    int64_t last = sLastLogTimeNs.load(std::memory_order_relaxed);

    // 5 second throttle window
    constexpr int64_t kThrottleNs = 5LL * 1000 * 1000 * 1000;

    if (uid != sLastLogUid.load(std::memory_order_relaxed) || (now - last) > kThrottleNs) {
        sLastLogTimeNs.store(now, std::memory_order_relaxed);
        sLastLogUid.store(uid, std::memory_order_relaxed);
        return true;
    }
    return false;
}

bool CallerValidator::isSystemCaller(uid_t uid) {
    // uid < 10000: root(0), system(1000), radio(1001), mediaserver, etc.
    // These are all trusted system processes.
    return uid < AID_APP_START;
}

bool CallerValidator::isValidEventId(int32_t eventId) {
    if (eventId < 0)
        return false;

    // SYS range
    if (eventId >= EVENT_SYS_RANGE_START && eventId <= EVENT_SYS_RANGE_END)
        return true;

    // APP range
    if (eventId >= EVENT_APP_RANGE_START && eventId <= EVENT_APP_RANGE_END)
        return true;

    // INTERNAL range (debug/test only, system caller checked separately)
    if (eventId >= EVENT_INTERNAL_RANGE_START && eventId <= EVENT_INTERNAL_RANGE_END)
        return true;

    return false;
}

bool CallerValidator::isEventAllowed(uid_t uid, int32_t eventId) {
    // Step 1: basic range sanity
    if (!isValidEventId(eventId)) {
        if (shouldLogDenial(uid))
            TLOGW("Event denied: invalid eventId=0x%x uid=%u", eventId, uid);
        return false;
    }

    // Step 2: system callers — unrestricted
    if (isSystemCaller(uid)) {
        return true;
    }

    // Step 3: app callers — only APP range permitted
    // Deny SYS range: prevents spoofing framework-level events
    if (eventId >= EVENT_SYS_RANGE_START && eventId <= EVENT_SYS_RANGE_END) {
        TLOGW("Event denied: app uid=%u attempted SYS event 0x%x — denied", uid, eventId);
        return false;
    }

    // Deny INTERNAL range: debug/test events not for app use
    if (eventId >= EVENT_INTERNAL_RANGE_START && eventId <= EVENT_INTERNAL_RANGE_END) {
        TLOGW("Event denied: app uid=%u attempted INTERNAL event 0x%x — denied", uid, eventId);
        return false;
    }

    // APP range: permitted
    return true;
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
