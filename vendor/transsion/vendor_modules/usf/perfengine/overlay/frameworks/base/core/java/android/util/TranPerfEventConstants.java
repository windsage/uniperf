/*
 * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated: 2026-03-03 19:13:28
 * Source: event_definitions.xml
 * Generator: tools/generate_events.py
 */

package android.util;

/**
 * PerfEngine Event ID Constants
 *
 * Event ID encoding: 16-bit hex (0x00000 - 0xFFFFF)
 * - System events: 0x00000 - 0x00FFF
 * - App events: 0x01000 - 0xEFFFF
 * - Internal events: 0xF0000 - 0xFFFFF
 * @hide
 */
public final class TranPerfEventConstants {

    // ==================== Event ID Ranges ====================

    /** System-level events (Framework internal hooks) @hide */
    public static final int RANGE_SYS_START = 0x00000;
    /** @hide */
    public static final int RANGE_SYS_END   = 0x00FFF;

    /** SystemUI application events @hide */
    public static final int RANGE_APP_SYSTEMUI_START = 0x01000;
    /** @hide */
    public static final int RANGE_APP_SYSTEMUI_END   = 0x01FFF;

    /** Launcher application events @hide */
    public static final int RANGE_APP_LAUNCHER_START = 0x02000;
    /** @hide */
    public static final int RANGE_APP_LAUNCHER_END   = 0x02FFF;

    /** Wallpaper application events @hide */
    public static final int RANGE_APP_WALLPAPER_START = 0x03000;
    /** @hide */
    public static final int RANGE_APP_WALLPAPER_END   = 0x03FFF;

    /** Settings application events @hide */
    public static final int RANGE_APP_SETTINGS_START = 0x04000;
    /** @hide */
    public static final int RANGE_APP_SETTINGS_END   = 0x04FFF;

    /** Reserved for future system apps @hide */
    public static final int RANGE_APP_RESERVED_START = 0x05000;
    /** @hide */
    public static final int RANGE_APP_RESERVED_END   = 0x0FFFF;

    /** Third-party application events @hide */
    public static final int RANGE_APP_THIRDPARTY_START = 0x10000;
    /** @hide */
    public static final int RANGE_APP_THIRDPARTY_END   = 0xEFFFF;

    /** Internal test/debug events @hide */
    public static final int RANGE_INTERNAL_START = 0xF0000;
    /** @hide */
    public static final int RANGE_INTERNAL_END   = 0xFFFFF;

    // ==================== AppEvents ====================

    /** Notification panel expand gesture started @hide */
    public static final int EVENT_APP_SYSTEMUI_PANEL_EXPAND = 0x01001;

    /** New notification posted @hide */
    public static final int EVENT_APP_SYSTEMUI_NOTIFICATION_POST = 0x01002;

    /** Quick settings panel toggled @hide */
    public static final int EVENT_APP_SYSTEMUI_QUICK_SETTINGS = 0x01003;

    /** Status bar notification list scrolled @hide */
    public static final int EVENT_APP_SYSTEMUI_STATUS_BAR_SCROLL = 0x01004;

    /** Keyguard unlock animation started @hide */
    public static final int EVENT_APP_SYSTEMUI_KEYGUARD_UNLOCK = 0x01005;

    /** Swipe up gesture to home screen @hide */
    public static final int EVENT_APP_LAUNCHER_SWIPE_UP = 0x02001;

    /** App folder opened @hide */
    public static final int EVENT_APP_LAUNCHER_FOLDER_OPEN = 0x02002;

    /** Home screen widget resize started @hide */
    public static final int EVENT_APP_LAUNCHER_WIDGET_RESIZE = 0x02003;

    /** App icon drag operation started @hide */
    public static final int EVENT_APP_LAUNCHER_ICON_DRAG = 0x02004;

    /** App drawer opened @hide */
    public static final int EVENT_APP_LAUNCHER_APP_DRAWER_OPEN = 0x02005;

    /** Wallpaper change operation started @hide */
    public static final int EVENT_APP_WALLPAPER_CHANGE = 0x03001;

    /** Wallpaper preview rendering @hide */
    public static final int EVENT_APP_WALLPAPER_PREVIEW = 0x03002;

    /** Live wallpaper frame rendering @hide */
    public static final int EVENT_APP_WALLPAPER_LIVE_RENDER = 0x03003;

    /** Game level/stage loading started @hide */
    public static final int EVENT_APP_GAME_LEVEL_START = 0x10001;

    /** Boss fight encounter started @hide */
    public static final int EVENT_APP_GAME_BOSS_FIGHT = 0x10002;

    /** Video player decoder initialization @hide */
    public static final int EVENT_APP_VIDEO_PLAYER_DECODE = 0x10003;

    /** Browser page load started @hide */
    public static final int EVENT_APP_BROWSER_PAGE_LOAD = 0x10004;

    /** Camera capture shutter pressed @hide */
    public static final int EVENT_APP_CAMERA_CAPTURE = 0x10005;

    // ==================== InternalEvents ====================

    /** Performance benchmark test @hide */
    public static final int EVENT_INTERNAL_TEST_BENCHMARK = 0xF0001;

    /** Debug trace marker @hide */
    public static final int EVENT_INTERNAL_DEBUG_TRACE = 0xF0002;

    // ==================== SystemEvents ====================

    /** Input event (touch/key) detected @hide */
    public static final int EVENT_SYS_INPUT = 0x00001;

    /** Application launch started @hide */
    public static final int EVENT_SYS_APP_LAUNCH = 0x00002;

    /** Application bind (process created) @hide */
    public static final int EVENT_SYS_BIND_APPLICATION = 0x00003;

    /** Activity switch/resume @hide */
    public static final int EVENT_SYS_ACTIVITY_SWITCH = 0x00004;

    /** Scroll gesture started @hide */
    public static final int EVENT_SYS_SCROLL = 0x00005;

    /** Fling gesture detected @hide */
    public static final int EVENT_SYS_FLING = 0x00006;

    /** Window animation started @hide */
    public static final int EVENT_SYS_ANIMATION = 0x00007;

    /** Camera HAL open requested @hide */
    public static final int EVENT_SYS_CAMERA_OPEN = 0x00008;

    /** Video decoder initialization @hide */
    public static final int EVENT_SYS_VIDEO_DECODE = 0x00009;

    /** System boot completed broadcast @hide */
    public static final int EVENT_SYS_BOOT_COMPLETED = 0x0000A;

    private TranPerfEventConstants() {}
    // Prevent instantiation
}
