# PerfEngine Event ID Reference

**Auto-generated:** 2026-03-03 11:40:41

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

| Event ID | Constant Name | Description | Hook Point |
|----------|---------------|-------------|------------|
| `0x00001` | `EVENT_SYS_INPUT` | Input event (touch/key) detected | InputDispatcher.cpp |
| `0x00002` | `EVENT_SYS_APP_LAUNCH` | Application launch started | ActivityTaskManagerService.java |
| `0x00003` | `EVENT_SYS_BIND_APPLICATION` | Application bind (process created) | ActivityThread.java:handleBindApplication() |
| `0x00004` | `EVENT_SYS_ACTIVITY_SWITCH` | Activity switch/resume | ActivityRecord.java |
| `0x00005` | `EVENT_SYS_SCROLL` | Scroll gesture started | RecyclerView.java |
| `0x00006` | `EVENT_SYS_FLING` | Fling gesture detected | GestureDetector.java |
| `0x00007` | `EVENT_SYS_ANIMATION` | Window animation started | WindowAnimator.java |
| `0x00008` | `EVENT_SYS_CAMERA_OPEN` | Camera HAL open requested | CameraService.cpp |
| `0x00009` | `EVENT_SYS_VIDEO_DECODE` | Video decoder initialization | MediaCodec.cpp |
| `0x0000A` | `EVENT_SYS_BOOT_COMPLETED` | System boot completed broadcast | ActivityManagerService.java |

### Application Events

| Event ID | Constant Name | App | Description |
|----------|---------------|-----|-------------|
| `0x01001` | `EVENT_APP_SYSTEMUI_PANEL_EXPAND` | SystemUI | Notification panel expand gesture started |
| `0x01002` | `EVENT_APP_SYSTEMUI_NOTIFICATION_POST` | SystemUI | New notification posted |
| `0x01003` | `EVENT_APP_SYSTEMUI_QUICK_SETTINGS` | SystemUI | Quick settings panel toggled |
| `0x01004` | `EVENT_APP_SYSTEMUI_STATUS_BAR_SCROLL` | SystemUI | Status bar notification list scrolled |
| `0x01005` | `EVENT_APP_SYSTEMUI_KEYGUARD_UNLOCK` | SystemUI | Keyguard unlock animation started |
| `0x02001` | `EVENT_APP_LAUNCHER_SWIPE_UP` | Launcher | Swipe up gesture to home screen |
| `0x02002` | `EVENT_APP_LAUNCHER_FOLDER_OPEN` | Launcher | App folder opened |
| `0x02003` | `EVENT_APP_LAUNCHER_WIDGET_RESIZE` | Launcher | Home screen widget resize started |
| `0x02004` | `EVENT_APP_LAUNCHER_ICON_DRAG` | Launcher | App icon drag operation started |
| `0x02005` | `EVENT_APP_LAUNCHER_APP_DRAWER_OPEN` | Launcher | App drawer opened |
| `0x03001` | `EVENT_APP_WALLPAPER_CHANGE` | Wallpaper | Wallpaper change operation started |
| `0x03002` | `EVENT_APP_WALLPAPER_PREVIEW` | Wallpaper | Wallpaper preview rendering |
| `0x03003` | `EVENT_APP_WALLPAPER_LIVE_RENDER` | Wallpaper | Live wallpaper frame rendering |
| `0x10001` | `EVENT_APP_GAME_LEVEL_START` | ThirdParty | Game level/stage loading started |
| `0x10002` | `EVENT_APP_GAME_BOSS_FIGHT` | ThirdParty | Boss fight encounter started |
| `0x10003` | `EVENT_APP_VIDEO_PLAYER_DECODE` | ThirdParty | Video player decoder initialization |
| `0x10004` | `EVENT_APP_BROWSER_PAGE_LOAD` | ThirdParty | Browser page load started |
| `0x10005` | `EVENT_APP_CAMERA_CAPTURE` | ThirdParty | Camera capture shutter pressed |

### Internal Events (Test/Debug)

| Event ID | Constant Name | Description |
|----------|---------------|-------------|
| `0xF0001` | `EVENT_INTERNAL_TEST_BENCHMARK` | Performance benchmark test |
| `0xF0002` | `EVENT_INTERNAL_DEBUG_TRACE` | Debug trace marker |

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
