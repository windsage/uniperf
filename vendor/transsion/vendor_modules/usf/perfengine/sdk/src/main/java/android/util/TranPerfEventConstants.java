/*
 * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated: 2026-03-03 19:13:28
 * Source: event_definitions.xml
 * Generator: tools/generate_events.py
 */

package android.util;

/**
 * PerfEngine Event ID Constants (SDK Version)
 *
 * This file contains only application-level events for third-party apps.
 * System events are internal to the framework.
 */
public final class TranPerfEventConstants {

    // ==================== Event ID Ranges ====================

    /** SystemUI application events */
    public static final int RANGE_APP_SYSTEMUI_START = 0x01000;
    public static final int RANGE_APP_SYSTEMUI_END   = 0x01FFF;

    /** Launcher application events */
    public static final int RANGE_APP_LAUNCHER_START = 0x02000;
    public static final int RANGE_APP_LAUNCHER_END   = 0x02FFF;

    /** Wallpaper application events */
    public static final int RANGE_APP_WALLPAPER_START = 0x03000;
    public static final int RANGE_APP_WALLPAPER_END   = 0x03FFF;

    /** Settings application events */
    public static final int RANGE_APP_SETTINGS_START = 0x04000;
    public static final int RANGE_APP_SETTINGS_END   = 0x04FFF;

    /** Reserved for future system apps */
    public static final int RANGE_APP_RESERVED_START = 0x05000;
    public static final int RANGE_APP_RESERVED_END   = 0x0FFFF;

    /** Third-party application events */
    public static final int RANGE_APP_THIRDPARTY_START = 0x10000;
    public static final int RANGE_APP_THIRDPARTY_END   = 0xEFFFF;

    // ==================== Application Events ====================

    /** Notification panel expand gesture started */
    public static final int EVENT_APP_SYSTEMUI_PANEL_EXPAND = 0x01001;

    /** New notification posted */
    public static final int EVENT_APP_SYSTEMUI_NOTIFICATION_POST = 0x01002;

    /** Quick settings panel toggled */
    public static final int EVENT_APP_SYSTEMUI_QUICK_SETTINGS = 0x01003;

    /** Status bar notification list scrolled */
    public static final int EVENT_APP_SYSTEMUI_STATUS_BAR_SCROLL = 0x01004;

    /** Keyguard unlock animation started */
    public static final int EVENT_APP_SYSTEMUI_KEYGUARD_UNLOCK = 0x01005;

    /** Swipe up gesture to home screen */
    public static final int EVENT_APP_LAUNCHER_SWIPE_UP = 0x02001;

    /** App folder opened */
    public static final int EVENT_APP_LAUNCHER_FOLDER_OPEN = 0x02002;

    /** Home screen widget resize started */
    public static final int EVENT_APP_LAUNCHER_WIDGET_RESIZE = 0x02003;

    /** App icon drag operation started */
    public static final int EVENT_APP_LAUNCHER_ICON_DRAG = 0x02004;

    /** App drawer opened */
    public static final int EVENT_APP_LAUNCHER_APP_DRAWER_OPEN = 0x02005;

    /** Wallpaper change operation started */
    public static final int EVENT_APP_WALLPAPER_CHANGE = 0x03001;

    /** Wallpaper preview rendering */
    public static final int EVENT_APP_WALLPAPER_PREVIEW = 0x03002;

    /** Live wallpaper frame rendering */
    public static final int EVENT_APP_WALLPAPER_LIVE_RENDER = 0x03003;

    /** Game level/stage loading started */
    public static final int EVENT_APP_GAME_LEVEL_START = 0x10001;

    /** Boss fight encounter started */
    public static final int EVENT_APP_GAME_BOSS_FIGHT = 0x10002;

    /** Video player decoder initialization */
    public static final int EVENT_APP_VIDEO_PLAYER_DECODE = 0x10003;

    /** Browser page load started */
    public static final int EVENT_APP_BROWSER_PAGE_LOAD = 0x10004;

    /** Camera capture shutter pressed */
    public static final int EVENT_APP_CAMERA_CAPTURE = 0x10005;

    /** Performance benchmark test */
    public static final int EVENT_INTERNAL_TEST_BENCHMARK = 0xF0001;

    /** Debug trace marker */
    public static final int EVENT_INTERNAL_DEBUG_TRACE = 0xF0002;

    private TranPerfEventConstants() {}
    // Prevent instantiation
}
