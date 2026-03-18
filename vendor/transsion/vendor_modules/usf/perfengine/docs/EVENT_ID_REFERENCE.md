# PerfEngine Event ID Reference

**Auto-generated:** 2026-03-17 11:46:26

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
| `0x00001` | `EVENT_SYS_INPUT` | 10 | Input event (touch/key) detected | InputDispatcher.cpp |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `inputType` | `int` | No | Input device type: 0=touch, 1=key, 2=stylus |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00002` | `EVENT_SYS_APP_LAUNCH` | 8 | Application launch started | ActivityTaskManagerService.java |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `launchType` | `int` | No | Launch type: 0=cold start, 1=warm start, 2=hot start |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Target package name, e.g. com.android.settings |
| 1 | `activityName` | `String` | No | Target activity class name, e.g. .MainActivity |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00003` | `EVENT_SYS_BIND_APPLICATION` | 8 | Application bind (process created) | ActivityThread.java:handleBindApplication() |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Package being bound, e.g. com.android.settings |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00004` | `EVENT_SYS_ACTIVITY_SWITCH` | 7 | Activity switch/resume | ActivityRecord.java |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `transitType` | `int` | No | Transition type: 0=resume, 1=new task, 2=task switch |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Target package name |
| 1 | `activityName` | `String` | No | Target activity class name |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00005` | `EVENT_SYS_SCROLL` | 5 | Scroll gesture started | RecyclerView.java |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `scrollAxis` | `int` | No | Scroll axis: 0=vertical, 1=horizontal |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Package where scroll occurred |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00006` | `EVENT_SYS_FLING` | 5 | Fling gesture detected | GestureDetector.java |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `flingAxis` | `int` | No | Fling axis: 0=vertical, 1=horizontal |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Package where fling occurred |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00007` | `EVENT_SYS_ANIMATION` | 5 | Window animation started | WindowAnimator.java |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `animationType` | `int` | No | Animation type: 0=enter, 1=exit, 2=cross-fade |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00008` | `EVENT_SYS_CAMERA_OPEN` | 7 | Camera HAL open requested | CameraService.cpp |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `cameraId` | `int` | No | Camera hardware ID (0=rear main, 1=front) |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Package requesting camera |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x00009` | `EVENT_SYS_VIDEO_DECODE` | 6 | Video decoder initialization | MediaCodec.cpp |

**Integer Parameters (intParams)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `codecType` | `int` | No | Codec type: 0=H264, 1=H265, 2=VP9, 3=AV1 |

**String Parameters (extraStrings, separated by `\x1F`)**

| Index | Name | Type | Required | Description |
|-------|------|------|----------|-------------|
| 0 | `packageName` | `String` | No | Package using video decoder |

| Event ID | Constant Name | Priority | Description | Hook Point |
|----------|---------------|----------|-------------|------------|
| `0x0000A` | `EVENT_SYS_BOOT_COMPLETED` | 9 | System boot completed broadcast | ActivityManagerService.java |

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
import static android.util.TranPerfEventConstants.*;

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
