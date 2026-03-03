/*
 * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated: 2026-03-03 19:13:28
 * Source: event_definitions.xml
 * Generator: tools/generate_events.py
 */

#ifndef VENDOR_TRANSSION_PERFENGINE_EVENT_CONSTANTS_H
#define VENDOR_TRANSSION_PERFENGINE_EVENT_CONSTANTS_H

#include <cstdint>

namespace android {
namespace transsion {
namespace PerfEventConstants {

// ==================== Event ID Ranges ====================

/** System-level events (Framework internal hooks) */
constexpr int32_t RANGE_SYS_START = 0x00000;
constexpr int32_t RANGE_SYS_END   = 0x00FFF;

/** SystemUI application events */
constexpr int32_t RANGE_APP_SYSTEMUI_START = 0x01000;
constexpr int32_t RANGE_APP_SYSTEMUI_END   = 0x01FFF;

/** Launcher application events */
constexpr int32_t RANGE_APP_LAUNCHER_START = 0x02000;
constexpr int32_t RANGE_APP_LAUNCHER_END   = 0x02FFF;

/** Wallpaper application events */
constexpr int32_t RANGE_APP_WALLPAPER_START = 0x03000;
constexpr int32_t RANGE_APP_WALLPAPER_END   = 0x03FFF;

/** Settings application events */
constexpr int32_t RANGE_APP_SETTINGS_START = 0x04000;
constexpr int32_t RANGE_APP_SETTINGS_END   = 0x04FFF;

/** Reserved for future system apps */
constexpr int32_t RANGE_APP_RESERVED_START = 0x05000;
constexpr int32_t RANGE_APP_RESERVED_END   = 0x0FFFF;

/** Third-party application events */
constexpr int32_t RANGE_APP_THIRDPARTY_START = 0x10000;
constexpr int32_t RANGE_APP_THIRDPARTY_END   = 0xEFFFF;

/** Internal test/debug events */
constexpr int32_t RANGE_INTERNAL_START = 0xF0000;
constexpr int32_t RANGE_INTERNAL_END   = 0xFFFFF;

// ==================== AppEvents ====================

/** Notification panel expand gesture started */
constexpr int32_t EVENT_APP_SYSTEMUI_PANEL_EXPAND = 0x01001;

/** New notification posted */
constexpr int32_t EVENT_APP_SYSTEMUI_NOTIFICATION_POST = 0x01002;

/** Quick settings panel toggled */
constexpr int32_t EVENT_APP_SYSTEMUI_QUICK_SETTINGS = 0x01003;

/** Status bar notification list scrolled */
constexpr int32_t EVENT_APP_SYSTEMUI_STATUS_BAR_SCROLL = 0x01004;

/** Keyguard unlock animation started */
constexpr int32_t EVENT_APP_SYSTEMUI_KEYGUARD_UNLOCK = 0x01005;

/** Swipe up gesture to home screen */
constexpr int32_t EVENT_APP_LAUNCHER_SWIPE_UP = 0x02001;

/** App folder opened */
constexpr int32_t EVENT_APP_LAUNCHER_FOLDER_OPEN = 0x02002;

/** Home screen widget resize started */
constexpr int32_t EVENT_APP_LAUNCHER_WIDGET_RESIZE = 0x02003;

/** App icon drag operation started */
constexpr int32_t EVENT_APP_LAUNCHER_ICON_DRAG = 0x02004;

/** App drawer opened */
constexpr int32_t EVENT_APP_LAUNCHER_APP_DRAWER_OPEN = 0x02005;

/** Wallpaper change operation started */
constexpr int32_t EVENT_APP_WALLPAPER_CHANGE = 0x03001;

/** Wallpaper preview rendering */
constexpr int32_t EVENT_APP_WALLPAPER_PREVIEW = 0x03002;

/** Live wallpaper frame rendering */
constexpr int32_t EVENT_APP_WALLPAPER_LIVE_RENDER = 0x03003;

/** Game level/stage loading started */
constexpr int32_t EVENT_APP_GAME_LEVEL_START = 0x10001;

/** Boss fight encounter started */
constexpr int32_t EVENT_APP_GAME_BOSS_FIGHT = 0x10002;

/** Video player decoder initialization */
constexpr int32_t EVENT_APP_VIDEO_PLAYER_DECODE = 0x10003;

/** Browser page load started */
constexpr int32_t EVENT_APP_BROWSER_PAGE_LOAD = 0x10004;

/** Camera capture shutter pressed */
constexpr int32_t EVENT_APP_CAMERA_CAPTURE = 0x10005;

// ==================== InternalEvents ====================

/** Performance benchmark test */
constexpr int32_t EVENT_INTERNAL_TEST_BENCHMARK = 0xF0001;

/** Debug trace marker */
constexpr int32_t EVENT_INTERNAL_DEBUG_TRACE = 0xF0002;

// ==================== SystemEvents ====================

/** Input event (touch/key) detected */
constexpr int32_t EVENT_SYS_INPUT = 0x00001;

/** Application launch started */
constexpr int32_t EVENT_SYS_APP_LAUNCH = 0x00002;

/** Application bind (process created) */
constexpr int32_t EVENT_SYS_BIND_APPLICATION = 0x00003;

/** Activity switch/resume */
constexpr int32_t EVENT_SYS_ACTIVITY_SWITCH = 0x00004;

/** Scroll gesture started */
constexpr int32_t EVENT_SYS_SCROLL = 0x00005;

/** Fling gesture detected */
constexpr int32_t EVENT_SYS_FLING = 0x00006;

/** Window animation started */
constexpr int32_t EVENT_SYS_ANIMATION = 0x00007;

/** Camera HAL open requested */
constexpr int32_t EVENT_SYS_CAMERA_OPEN = 0x00008;

/** Video decoder initialization */
constexpr int32_t EVENT_SYS_VIDEO_DECODE = 0x00009;

/** System boot completed broadcast */
constexpr int32_t EVENT_SYS_BOOT_COMPLETED = 0x0000A;

}  // namespace PerfEventConstants
}  // namespace transsion
}  // namespace android

#endif  // VENDOR_TRANSSION_PERFENGINE_EVENT_CONSTANTS_H
