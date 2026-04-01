/*
 * CallerValidator.h
 *
 * Vendor-side caller authentication for PerfEngine AIDL interface.
 * Validates caller UID and Event ID range before dispatching to PerfLockCaller.
 *
 * Design:
 *   - UID check:      system processes (uid < AID_APP) are always trusted.
 *                     App processes (uid >= AID_APP) are allowed but restricted
 *                     to APP-range event IDs only.
 *   - EventId check:  system-range events (0x00000~0x00FFF) are forbidden
 *                     for app-process callers.
 *
 * Thread-safe: all methods are stateless or use only immutable data.
 */

#pragma once

#include <sys/types.h>

#include <cstdint>

namespace vendor {
namespace transsion {
namespace perfengine {

class CallerValidator {
public:
    // ==================== UID Boundaries ====================

    // Mirrors android/os/Process.java: FIRST_APPLICATION_UID = 10000
    static constexpr uid_t AID_APP_START = 10000u;

    // ==================== Event ID Boundaries ====================

    // System-range: 0x00000 ~ 0x00FFF  (RANGE_SYS)
    static constexpr int32_t EVENT_SYS_RANGE_START = 0x00000;
    static constexpr int32_t EVENT_SYS_RANGE_END = 0x00FFF;

    // App-range:    0x01000 ~ 0xEFFFF  (RANGE_APP_*)
    static constexpr int32_t EVENT_APP_RANGE_START = 0x01000;
    static constexpr int32_t EVENT_APP_RANGE_END = 0xEFFFF;

    // Internal/debug range: 0xF0000 ~ 0xFFFFF
    static constexpr int32_t EVENT_INTERNAL_RANGE_START = 0xF0000;
    static constexpr int32_t EVENT_INTERNAL_RANGE_END = 0xFFFFF;

    /**
     * Check whether the calling UID is a trusted system process.
     * System processes have uid < AID_APP_START (10000).
     *
     * @param uid  Caller UID obtained via AIBinder_getCallingUid()
     * @return true if caller is a system process
     */
    static bool isSystemCaller(uid_t uid);

    /**
     * Check whether the given eventId is within a valid range.
     *
     * @param eventId  Event ID from notifyEventStart/End
     * @return true if eventId falls in any defined range
     */
    static bool isValidEventId(int32_t eventId);

    /**
     * Check whether the caller (identified by uid) is allowed to
     * submit the given eventId.
     *
     * Rules:
     *   - System callers (uid < 10000): all event IDs allowed
     *   - App callers   (uid >= 10000): only APP range (0x01000~0xEFFFF) allowed
     *                                   SYS range and INTERNAL range are forbidden
     *
     * @param uid      Caller UID
     * @param eventId  Event ID to validate
     * @return true if the combination is permitted
     */
    static bool isEventAllowed(uid_t uid, int32_t eventId);
};

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
