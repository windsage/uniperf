# PerfEngine Event ID Reference

**Auto-generated:** 2026-04-02 15:16:27

**Source:** `event_definitions.xml`

---

## Event ID Encoding

Events use **16-bit hexadecimal encoding** (0x00000 - 0xFFFFF):

```
0xCTTNN
  │││└─ Number (event sequence within category)
  ││└── Type (sub-category)
  │└─── Category (system/app/internal)
  └──── Reserved
```

---

## Priority Scale

| Range | Meaning | Typical events |
|-------|---------|----------------|
| 9-10 | Critical — almost never overridden | Input, Boot |
| 7-8  | High — overrides normal/low | App launch, Activity switch, Camera |
| 4-6  | Normal — default level | Scroll, Fling, Animation, Video decode |
| 1-3  | Low — overridden by any higher event | Wallpaper render, Widget ops |

---

## Event ID Ranges

| Range Name | Start | End | Description |
|------------|-------|-----|-------------|
| `RANGE_SYS` | `0x00000` | `0x00FFF` | System-level events (Framework internal hooks) |
| `RANGE_APP_SYSTEMUI` | `0x01000` | `0x01FFF` | SystemUI application events |
| `RANGE_APP_LAUNCHER` | `0x02000` | `0x02FFF` | Launcher application events |
| `RANGE_APP_WALLPAPER` | `0x03000` | `0x03FFF` | Wallpaper application events |
| `RANGE_APP_SETTINGS` | `0x04000` | `0x04FFF` | Settings application events |
| `RANGE_APP_RESERVED` | `0x05000` | `0x0FFFF` | Reserved for future system apps |
| `RANGE_APP_THIRDPARTY` | `0x10000` | `0xEFFFF` | Third-party application events |
| `RANGE_INTERNAL` | `0xF0000` | `0xFFFFF` | Internal test/debug events |

---

## Event Definitions

### System Events (Framework Internal)

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00001` | `EVENT_SYS_PROCESS_CREATE` | 8 | Process creation | ActivityManagerService.java |
| `0x00002` | `EVENT_SYS_APP_LAUNCH_COLD` | 8 | cold start | ActivityTaskManagerService.java |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Target package name, e.g. com.android.settings |
| 1 | `activityName` | `String` | No | Target activity class name, e.g. .MainActivity |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00003` | `EVENT_SYS_APP_LAUNCH_WARM` | 8 | warm start | ActivityTaskManagerService.java |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Target package name, e.g. com.android.settings |
| 1 | `activityName` | `String` | No | Target activity class name, e.g. .MainActivity |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00004` | `EVENT_SYS_APP_LAUNCH_HOT` | 8 | hot start | ActivityTaskManagerService.java |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Target package name, e.g. com.android.settings |
| 1 | `activityName` | `String` | No | Target activity class name, e.g. .MainActivity |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00005` | `EVENT_SYS_FIRST_DRAW` | 7 | First frame drawing | N/A |
| `0x00006` | `EVENT_SYS_ACTIVITY_SWITCH` | 7 | Activity switch | ActivityTaskManagerService.java |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Target package name, e.g. com.android.settings |
| 1 | `activityName` | `String` | No | Target activity class name, e.g. .MainActivity |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00007` | `EVENT_SYS_TOUCH` | 10 | Screen touch | InputDispatcher.cpp |
| `0x00008` | `EVENT_SYS_KEY_PRESS` | 10 | Button click | InputDispatcher.cpp |
| `0x00009` | `EVENT_SYS_FLING` | 5 | Sliding scene | OverScroller.cpp |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Package where scroll occurred |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x0000A` | `EVENT_SYS_SCROLL` | 5 | Drag scene | OverScroller.cpp |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Package where scroll occurred |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x0000B` | `EVENT_SYS_SCREEN_ON` | 6 | Screen-on scene | DisplayPowerController.cpp |
| `0x0000C` | `EVENT_SYS_SCREEN_OFF` | 6 | Screen-off scene | DisplayPowerController.cpp |
| `0x0000D` | `EVENT_SYS_SCREEN_DOZE` | 4 | Screen sleep | DisplayPowerController.cpp |
| `0x0000E` | `EVENT_SYS_SCREEN_UNLOCK` | 8 | Screen unlock | KeyguardController.cpp |
| `0x0000F` | `EVENT_SYS_SCREEN_ROTATE` | 5 | Screen rotation | DisplayManagerService.cpp |
| `0x00010` | `EVENT_SYS_IME_BOOST` | 5 | Input method pop-up/retract | InputMethodManagerService.cpp |
| `0x00011` | `EVENT_SYS_IME_INPUT` | 5 | Keyboard input | InputMethodManagerService.cpp |
| `0x00012` | `EVENT_SYS_REFRESH_UPDATE` | 4 | Refresh rate toggle | DisplayManagerService.cpp |
| `0x00013` | `EVENT_SYS_SPLIT_WINDOW` | 5 | Split screen scene | DisplayManagerService.cpp |
| `0x00014` | `EVENT_SYS_PIP_WINDOW` | 5 | Small window mode | DisplayManagerService.cpp |
| `0x00015` | `EVENT_SYS_AMIN_TRANSITION_LAUNCHER` | 6 | Startup animation (desktop startup) | ActivityTaskManagerService.java |
| `0x00016` | `EVENT_SYS_AMIN_TRANSITION` | 5 | Startup animation (non-desktop startup) | ActivityTaskManagerService.java |
| `0x00017` | `EVENT_SYS_AMIN_EXIT` | 5 | Exit animation | ActivityTaskManagerService.java |
| `0x00018` | `EVENT_SYS_AMIN_LITE` | 4 | Lightweight animation | ActivityTaskManagerService.java |
| `0x00019` | `EVENT_SYS_AMIN_MEDIUM` | 5 | Moderate animation | ActivityTaskManagerService.java |
| `0x0001A` | `EVENT_SYS_AMIN_HEAVY` | 6 | Complex animation | ActivityTaskManagerService.java |
| `0x0001B` | `EVENT_SYS_APP_INSTALL` | 3 | App installation | PackageManagerService.java |
| `0x0001C` | `EVENT_SYS_APP_UNINSTALL` | 3 | App uninstall | PackageManagerService.java |
| `0x0001D` | `EVENT_SYS_EXPENSIVE_RENDERING` | 6 | High load rendering/compositing | RenderEngine.cpp |
| `0x0001E` | `EVENT_SYS_IO_HEAVY` | 3 | High IO scenarios | File.cpp |
| `0x0001F` | `EVENT_SYS_FILE_OPERATION` | 2 | File operation | File.cpp |
| `0x00020` | `EVENT_SYS_DOWNLOAD` | 3 | Network download | File.cpp |
| `0x00021` | `EVENT_SYS_CAMERA_LAUNCH` | 7 | Camera on | CameraService.cpp |
| `0x00022` | `EVENT_SYS_VOICE_CALL` | 6 | Voice call | Phone.java |
| `0x00023` | `EVENT_SYS_VIDEO_CALL` | 6 | Video call | Phone.java |
| `0x00024` | `EVENT_SYS_VIDEO_DECODE` | 6 | Video decoding | MediaPlayerService.java |
| `0x00025` | `EVENT_SYS_VIDEO_PLAY` | 5 | Video playback | MediaPlayerService.java |
| `0x00026` | `EVENT_SYS_AUDIO_PLAY` | 5 | Audio playback | MediaPlayerService.java |
| `0x00027` | `EVENT_SYS_SCREEN_SHOT` | 4 | Screen capture | ScreenshotService.java |
| `0x00028` | `EVENT_SYS_SCREEN_RECORD` | 4 | Screen recording | ScreenRecorder.java |
| `0x00029` | `EVENT_SYS_VOICE_RECORD` | 3 | Audio recording | AudioRecorder.java |
| `0x0002A` | `EVENT_SYS_BT_CONNECT` | 3 | Bluetooth connection | BluetoothManagerService.java |
| `0x0002B` | `EVENT_SYS_GPS_CONNECT` | 3 | GPS connection | LocationManagerService.java |
| `0x0002C` | `EVENT_SYS_MTP_TRANSFER` | 2 | MTP transmission | StorageManagerService.java |
| `0x0002D` | `EVENT_SYS_LOW_POWER` | 4 | Low battery scenario | BatteryService.java |
| `0x0002E` | `EVENT_SYS_THERMAL_HIGH` | 4 | High temperature scene | ThermalManagerService.java |
| `0x0002F` | `EVENT_SYS_THERMAL_BENCH` | 3 | Benchmark mode | ThermalManagerService.java |
| `0x00030` | `EVENT_SYS_PERFORMANCE` | 4 | Performance mode | ThermalManagerService.java |
| `0x00031` | `EVENT_SYS_IDEL` | 1 | Idle mode | ThermalManagerService.java |
| `0x00032` | `EVENT_SYS_FINGERPRINT` | 7 | Fingerprint unlock | FingerprintService.java |
| `0x00033` | `EVENT_SYS_FACE` | 7 | Face unlock | FaceService.java |
| `0x00034` | `EVENT_SYS_ASSISTANT` | 6 | Voice assistant | VoiceInteractionService.java |
| `0x00035` | `EVENT_SYS_AI` | 5 | AI scenarios | AI.java |
| `0x00036` | `EVENT_SYS_BOOT` | 9 | Boot scene | BootReceiver.java |
| `0x00037` | `EVENT_SYS_SHUTDOWN` | 9 | Shutdown scenario | ShutdownReceiver.java |
| `0x00038` | `EVENT_SYS_DEX2OAT` | 2 | DEX2OAT | Dex2Oat.cpp |
| `0x00039` | `EVENT_SYS_GC` | 2 | GC | GC.cpp |
| `0x0003A` | `EVENT_SYS_GAME_MODE` | 10 | Game mode | GameModeService.java |

### Application Events

| Event ID | Constant Name | App | Priority | Description |
|----------|---------------|-----|----------|-------------|
| `0x01001` | `EVENT_APP_SYSTEMUI_PANEL_EXPAND` | SystemUI | 6 | Notification panel expand gesture started |
| `0x01002` | `EVENT_APP_SYSTEMUI_NOTIFICATION_POST` | SystemUI | 4 | New notification posted |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `notificationStyle` | `int` | No | Notification style: 0=normal, 1=heads-up, 2=fullscreen |

| Event ID | Constant Name | App | Priority | Description |
|----------|---------------|-----|----------|-------------|
| `0x01003` | `EVENT_APP_SYSTEMUI_QUICK_SETTINGS` | SystemUI | 5 | Quick settings panel toggled |
| `0x01004` | `EVENT_APP_SYSTEMUI_STATUS_BAR_SCROLL` | SystemUI | 4 | Status bar notification list scrolled |
| `0x01005` | `EVENT_APP_SYSTEMUI_KEYGUARD_UNLOCK` | SystemUI | 8 | Keyguard unlock animation started |
| `0x02001` | `EVENT_APP_LAUNCHER_SWIPE_UP` | Launcher | 6 | Swipe up gesture to home screen |
| `0x02002` | `EVENT_APP_LAUNCHER_FOLDER_OPEN` | Launcher | 4 | App folder opened |
| `0x02003` | `EVENT_APP_LAUNCHER_WIDGET_RESIZE` | Launcher | 3 | Home screen widget resize started |
| `0x02004` | `EVENT_APP_LAUNCHER_ICON_DRAG` | Launcher | 3 | App icon drag operation started |
| `0x02005` | `EVENT_APP_LAUNCHER_APP_DRAWER_OPEN` | Launcher | 5 | App drawer opened |
| `0x03001` | `EVENT_APP_WALLPAPER_CHANGE` | Wallpaper | 5 | Wallpaper change operation started |
| `0x03002` | `EVENT_APP_WALLPAPER_PREVIEW` | Wallpaper | 4 | Wallpaper preview rendering |
| `0x03003` | `EVENT_APP_WALLPAPER_LIVE_RENDER` | Wallpaper | 2 | Live wallpaper frame rendering |
| `0x10001` | `EVENT_APP_GAME_LEVEL_START` | ThirdParty | 7 | Game level/stage loading started |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `levelId` | `int` | No | Game level or stage ID |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Game package name |

| Event ID | Constant Name | App | Priority | Description |
|----------|---------------|-----|----------|-------------|
| `0x10002` | `EVENT_APP_GAME_BOSS_FIGHT` | ThirdParty | 6 | Boss fight encounter started |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `bossId` | `int` | No | Boss or encounter identifier |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Game package name |

| Event ID | Constant Name | App | Priority | Description |
|----------|---------------|-----|----------|-------------|
| `0x10003` | `EVENT_APP_VIDEO_PLAYER_DECODE` | ThirdParty | 6 | Video player decoder initialization |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `resolution` | `int` | No | Video resolution: 0=SD, 1=HD, 2=FHD, 3=4K |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Video player package name |

| Event ID | Constant Name | App | Priority | Description |
|----------|---------------|-----|----------|-------------|
| `0x10004` | `EVENT_APP_BROWSER_PAGE_LOAD` | ThirdParty | 5 | Browser page load started |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Browser package name |

| Event ID | Constant Name | App | Priority | Description |
|----------|---------------|-----|----------|-------------|
| `0x10005` | `EVENT_APP_CAMERA_CAPTURE` | ThirdParty | 7 | Camera capture shutter pressed |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `captureMode` | `int` | No | Capture mode: 0=photo, 1=burst, 2=HDR, 3=night |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Camera app package name |

| Event ID | Constant Name | App | Priority | Description |
|----------|---------------|-----|----------|-------------|

### Internal Events (Test/Debug)

| Event ID | Constant Name | Priority | Description |
|----------|---------------|----------|-------------|
| `0xF0001` | `EVENT_INTERNAL_TEST_BENCHMARK` | 1 | Performance benchmark test |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `benchmarkId` | `int` | No | Benchmark test case ID |

| Event ID | Constant Name | Priority | Description |
|----------|---------------|----------|-------------|
| `0xF0002` | `EVENT_INTERNAL_DEBUG_TRACE` | 1 | Debug trace marker |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `markerId` | `int` | No | Custom trace marker ID for debugging |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `label` | `String` | No | Human-readable trace label |

| Event ID | Constant Name | Priority | Description |
|----------|---------------|----------|-------------|

---

## Usage Examples

### Java (Framework)

```java
import static com.transsion.usf.perfengine.TranPerfEventConstants.*;

TranPerfEvent.notifyEventStart(EVENT_SYS_APP_LAUNCH, System.nanoTime());
```

### C++ (Native)

```cpp
#include <perfengine/TranPerfEvent.h>
using ::android::transsion::TranPerfEvent;

TranPerfEvent::notifyEventStart(TranPerfEvent::EVENT_SYS_SCROLL,
                               systemTime(SYSTEM_TIME_MONOTONIC));
```

---

*This document is auto-generated. Do not edit manually.*
