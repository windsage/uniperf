/*
 * Copyright (C) 2025 Transsion Holdings Limited
 *
 * TranPerfEvent - Native API for Performance Events
 */

#ifndef TRANSSION_TRANPERFEVENT_H
#define TRANSSION_TRANPERFEVENT_H

#include <stdint.h>

#include <memory>
#include <string>
#include <vector>

// Forward declaration
namespace aidl {
namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {
class IEventListener;
}
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor
}    // namespace aidl

namespace android {
namespace transsion {

/**
 * TranPerfEvent - Native layer performance event API
 *
 * Thread-safe, supports both System and Vendor partitions.
 *
 * Usage:
 * #include <perfhub/TranPerfEvent.h>
 *
 * using namespace android::transsion;
 *
 * // Simple usage
 * int64_t ts = TranPerfEvent::now();
 * TranPerfEvent::notifyEventStart(EVENT_APP_LAUNCH, ts);
 *
 * // With package name
 * TranPerfEvent::notifyEventStart(EVENT_APP_LAUNCH, ts, "com.android.settings");
 *
 * // With string array
 * std::vector<std::string> strings = {"com.android.settings", ".MainActivity", "cold_start"};
 * TranPerfEvent::notifyEventStart(EVENT_APP_LAUNCH, ts, strings);
 *
 * // With int parameters
 * std::vector<int32_t> params = {3000, 1};  // duration=3s, cold_start=1
 * TranPerfEvent::notifyEventStart(EVENT_APP_LAUNCH, ts, params);
 *
 * // Full parameters
 * TranPerfEvent::notifyEventStart(EVENT_APP_LAUNCH, ts, params, strings);
 *
 * // Register listener
 * auto listener = ndk::SharedRefBase::make<MyEventListener>();
 * TranPerfEvent::registerEventListener(listener);
 */
class TranPerfEvent {
public:
    // ==================== Event Type Constants ====================

    /** App launch event */
    static constexpr int32_t EVENT_APP_LAUNCH = 1;

    /** App switch event */
    static constexpr int32_t EVENT_APP_SWITCH = 2;

    /** Scroll event */
    static constexpr int32_t EVENT_SCROLL = 3;

    /** Camera open event */
    static constexpr int32_t EVENT_CAMERA_OPEN = 4;

    /** Game start event */
    static constexpr int32_t EVENT_GAME_START = 5;

    /** Video play event */
    static constexpr int32_t EVENT_VIDEO_PLAY = 6;

    /** Animation event */
    static constexpr int32_t EVENT_ANIMATION = 7;

    // ==================== String Separator Constant ====================

    /** String array separator (ASCII 31 - Unit Separator) */
    static constexpr char STRING_SEPARATOR = '\x1F';

    // ==================== Event Start Methods ====================

    /**
     * Notify event start (minimal)
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @return 0 on success, -1 on failure
     */
    static int32_t notifyEventStart(int32_t eventId, int64_t timestamp);

    /**
     * Notify event start with single string
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param extraString String parameter (usually packageName)
     * @return 0 on success, -1 on failure
     */
    static int32_t notifyEventStart(int32_t eventId, int64_t timestamp,
                                    const std::string &extraString);

    /**
     * Notify event start with string array
     *
     * Strings will be joined with '\x1F' separator
     * Example: {"com.android.settings", ".MainActivity", "cold_start"}
     *          -> "com.android.settings\x1F.MainActivity\x1Fcold_start"
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param stringParams String array to be joined
     * @return 0 on success, -1 on failure
     */
    static int32_t notifyEventStart(int32_t eventId, int64_t timestamp,
                                    const std::vector<std::string> &stringParams);

    /**
     * Notify event start with int parameters
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param intParams Integer parameters array
     *                  intParams[0] = duration (ms), 0 means manual release
     *                  intParams[1..n] = event-specific parameters
     * @return 0 on success, -1 on failure
     */
    static int32_t notifyEventStart(int32_t eventId, int64_t timestamp,
                                    const std::vector<int32_t> &intParams);

    /**
     * Notify event start with int parameters and string array (full)
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param intParams Integer parameters array
     * @param stringParams String array to be joined with '\x1F'
     * @return 0 on success, -1 on failure
     */
    static int32_t notifyEventStart(int32_t eventId, int64_t timestamp,
                                    const std::vector<int32_t> &intParams,
                                    const std::vector<std::string> &stringParams);

    // ==================== Event End Methods ====================

    /**
     * Notify event end (minimal)
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     */
    static void notifyEventEnd(int32_t eventId, int64_t timestamp);

    /**
     * Notify event end with string
     *
     * @param eventId Event type identifier
     * @param timestamp Event timestamp in nanoseconds
     * @param extraString String parameter (usually packageName)
     */
    static void notifyEventEnd(int32_t eventId, int64_t timestamp, const std::string &extraString);

    // ==================== Listener Registration Methods ====================

    /**
     * Register an event listener to receive performance event notifications
     *
     * The listener will receive onEventStart() and onEventEnd() callbacks
     * whenever performance events occur.
     *
     * @param listener IEventListener implementation (must not be null)
     * @return 0 on success, -1 on failure
     *
     * Usage:
     * <pre>
     * class MyListener : public BnEventListener {
     *     ScopedAStatus onEventStart(...) override {
     *         // Handle event
     *         return ScopedAStatus::ok();
     *     }
     *     ScopedAStatus onEventEnd(...) override {
     *         // Handle event
     *         return ScopedAStatus::ok();
     *     }
     * };
     *
     * auto listener = ndk::SharedRefBase::make<MyListener>();
     * TranPerfEvent::registerEventListener(listener);
     * </pre>
     */
    static int32_t registerEventListener(
        const std::shared_ptr<::aidl::vendor::transsion::hardware::perfhub::IEventListener>
            &listener);

    /**
     * Unregister a previously registered event listener
     *
     * After this call, the listener will no longer receive event notifications.
     *
     * @param listener IEventListener implementation to unregister
     * @return 0 on success, -1 on failure
     */
    static int32_t unregisterEventListener(
        const std::shared_ptr<::aidl::vendor::transsion::hardware::perfhub::IEventListener>
            &listener);

    // ==================== Utility Methods ====================

    /**
     * Get current timestamp in nanoseconds (CLOCK_MONOTONIC)
     *
     * @return Current timestamp in nanoseconds
     */
    static int64_t now();

    /**
     * Join string array with separator '\x1F'
     *
     * @param strings String array to join
     * @return Joined string with '\x1F' separator
     *
     * Example:
     *   {"com.android.settings", ".MainActivity", "cold_start"}
     *   -> "com.android.settings\x1F.MainActivity\x1Fcold_start"
     */
    static std::string joinStrings(const std::vector<std::string> &strings);

private:
    // Internal full AIDL call
    static int32_t notifyEventStartInternal(int32_t eventId, int64_t timestamp, int32_t numParams,
                                            const std::vector<int32_t> &intParams,
                                            const std::string &extraStrings);

    // Prevent instantiation
    TranPerfEvent() = delete;
    ~TranPerfEvent() = delete;
};

}    // namespace transsion
}    // namespace android

#endif    // TRANSSION_TRANPERFEVENT_H
