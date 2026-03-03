/*
 * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated: 2026-03-03 19:13:28
 * Source: event_definitions.xml
 * Generator: tools/generate_events.py
 *
 * This file provides a macro to import all PerfEngine event constants
 * into the TranPerfEvent class scope.
 *
 * Usage:
 *   #include "TranPerfEventImportMacro.h"
 *
 *   class TranPerfEvent {
 *   public:
 *       PERFENGINE_IMPORT_EVENT_CONSTANTS
 *       // ... other members ...
 *   };
 *
 * This expands to static constexpr aliases for all event IDs,
 * allowing usage like: TranPerfEvent::EVENT_SYS_APP_LAUNCH
 */

#ifndef VENDOR_TRANSSION_PERFENGINE_IMPORT_MACRO_H
#define VENDOR_TRANSSION_PERFENGINE_IMPORT_MACRO_H

/**
 * Import all event constants from PerfEventConstants namespace
 * into the current class scope.
 */
#define PERFENGINE_IMPORT_EVENT_CONSTANTS \
    static constexpr int32_t EVENT_SYS_INPUT = PerfEventConstants::EVENT_SYS_INPUT; \
    static constexpr int32_t EVENT_SYS_APP_LAUNCH = PerfEventConstants::EVENT_SYS_APP_LAUNCH; \
    static constexpr int32_t EVENT_SYS_BIND_APPLICATION = PerfEventConstants::EVENT_SYS_BIND_APPLICATION; \
    static constexpr int32_t EVENT_SYS_ACTIVITY_SWITCH = PerfEventConstants::EVENT_SYS_ACTIVITY_SWITCH; \
    static constexpr int32_t EVENT_SYS_SCROLL = PerfEventConstants::EVENT_SYS_SCROLL; \
    static constexpr int32_t EVENT_SYS_FLING = PerfEventConstants::EVENT_SYS_FLING; \
    static constexpr int32_t EVENT_SYS_ANIMATION = PerfEventConstants::EVENT_SYS_ANIMATION; \
    static constexpr int32_t EVENT_SYS_CAMERA_OPEN = PerfEventConstants::EVENT_SYS_CAMERA_OPEN; \
    static constexpr int32_t EVENT_SYS_VIDEO_DECODE = PerfEventConstants::EVENT_SYS_VIDEO_DECODE; \
    static constexpr int32_t EVENT_SYS_BOOT_COMPLETED = PerfEventConstants::EVENT_SYS_BOOT_COMPLETED; \
    static constexpr int32_t EVENT_APP_SYSTEMUI_PANEL_EXPAND = PerfEventConstants::EVENT_APP_SYSTEMUI_PANEL_EXPAND; \
    static constexpr int32_t EVENT_APP_SYSTEMUI_NOTIFICATION_POST = PerfEventConstants::EVENT_APP_SYSTEMUI_NOTIFICATION_POST; \
    static constexpr int32_t EVENT_APP_SYSTEMUI_QUICK_SETTINGS = PerfEventConstants::EVENT_APP_SYSTEMUI_QUICK_SETTINGS; \
    static constexpr int32_t EVENT_APP_SYSTEMUI_STATUS_BAR_SCROLL = PerfEventConstants::EVENT_APP_SYSTEMUI_STATUS_BAR_SCROLL; \
    static constexpr int32_t EVENT_APP_SYSTEMUI_KEYGUARD_UNLOCK = PerfEventConstants::EVENT_APP_SYSTEMUI_KEYGUARD_UNLOCK; \
    static constexpr int32_t EVENT_APP_LAUNCHER_SWIPE_UP = PerfEventConstants::EVENT_APP_LAUNCHER_SWIPE_UP; \
    static constexpr int32_t EVENT_APP_LAUNCHER_FOLDER_OPEN = PerfEventConstants::EVENT_APP_LAUNCHER_FOLDER_OPEN; \
    static constexpr int32_t EVENT_APP_LAUNCHER_WIDGET_RESIZE = PerfEventConstants::EVENT_APP_LAUNCHER_WIDGET_RESIZE; \
    static constexpr int32_t EVENT_APP_LAUNCHER_ICON_DRAG = PerfEventConstants::EVENT_APP_LAUNCHER_ICON_DRAG; \
    static constexpr int32_t EVENT_APP_LAUNCHER_APP_DRAWER_OPEN = PerfEventConstants::EVENT_APP_LAUNCHER_APP_DRAWER_OPEN; \
    static constexpr int32_t EVENT_APP_WALLPAPER_CHANGE = PerfEventConstants::EVENT_APP_WALLPAPER_CHANGE; \
    static constexpr int32_t EVENT_APP_WALLPAPER_PREVIEW = PerfEventConstants::EVENT_APP_WALLPAPER_PREVIEW; \
    static constexpr int32_t EVENT_APP_WALLPAPER_LIVE_RENDER = PerfEventConstants::EVENT_APP_WALLPAPER_LIVE_RENDER; \
    static constexpr int32_t EVENT_APP_GAME_LEVEL_START = PerfEventConstants::EVENT_APP_GAME_LEVEL_START; \
    static constexpr int32_t EVENT_APP_GAME_BOSS_FIGHT = PerfEventConstants::EVENT_APP_GAME_BOSS_FIGHT; \
    static constexpr int32_t EVENT_APP_VIDEO_PLAYER_DECODE = PerfEventConstants::EVENT_APP_VIDEO_PLAYER_DECODE; \
    static constexpr int32_t EVENT_APP_BROWSER_PAGE_LOAD = PerfEventConstants::EVENT_APP_BROWSER_PAGE_LOAD; \
    static constexpr int32_t EVENT_APP_CAMERA_CAPTURE = PerfEventConstants::EVENT_APP_CAMERA_CAPTURE; \
    static constexpr int32_t EVENT_INTERNAL_TEST_BENCHMARK = PerfEventConstants::EVENT_INTERNAL_TEST_BENCHMARK; \
    static constexpr int32_t EVENT_INTERNAL_DEBUG_TRACE = PerfEventConstants::EVENT_INTERNAL_DEBUG_TRACE;

#endif  // VENDOR_TRANSSION_PERFENGINE_IMPORT_MACRO_H
