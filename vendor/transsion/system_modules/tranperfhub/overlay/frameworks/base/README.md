# TranPerfEvent - 性能事件 API

## 概述

`TranPerfEvent` 是 Android Framework 的一个性能事件通知模块，为应用和系统提供统一的性能事件接口。它允许开发者在应用生命周期的关键阶段（如应用启动、应用切换、滚动等）发送事件通知，并支持多种监听器来接收和处理这些事件。

**特点：**
- **线程安全** - 使用 `CopyOnWriteArrayList` 确保并发访问安全
- **多层监听** - 支持本地简化监听器和完整 AIDL 监听器
- **解耦架构** - 通过反射与 TranPerfHub 通信，避免直接依赖
- **低延迟** - 内置延迟监控机制

---

## 支持的事件类型

| 常量 | 值 | 说明 |
|------|-----|------|
| `EVENT_APP_LAUNCH` | 1 | 应用启动事件 |
| `EVENT_APP_SWITCH` | 2 | 应用切换事件 |
| `EVENT_SCROLL` | 3 | 滚动事件 |
| `EVENT_CAMERA_OPEN` | 4 | 相机打开事件 |
| `EVENT_GAME_START` | 5 | 游戏启动事件 |
| `EVENT_VIDEO_PLAY` | 6 | 视频播放事件 |
| `EVENT_ANIMATION` | 7 | 动画事件 |

---

## 监听器接口

### 1. TrEventListener（简化版 - Framework 内部使用）

简化的监听器接口，仅提供基本的事件信息。

```java
public interface TrEventListener {
    /**
     * 事件开始时调用
     * @param eventId 事件类型 ID
     * @param timestamp 事件时间戳（纳秒）
     * @param duration 持续时间参数（来自 intParams[0]）
     */
    void onEventStart(int eventId, long timestamp, int duration);

    /**
     * 事件结束时调用
     * @param eventId 事件类型 ID
     * @param timestamp 事件时间戳（纳秒）
     */
    void onEventEnd(int eventId, long timestamp);
}
```

**使用场景：** Framework 内部组件监听事件，只需要关键信息

---

### 2. PerfEventListener（完整版 - SDK 使用）

完整的监听器抽象类，提供所有事件数据，无需依赖 vendor 命名空间。

```java
public abstract static class PerfEventListener {
    public abstract void onEventStart(
            int eventId,           // 事件 ID
            long timestamp,        // 时间戳（纳秒）
            int numParams,         // 整数参数个数
            int[] intParams,       // 整数参数数组
            String extraStrings    // 额外字符串参数
    );
    
    public abstract void onEventEnd(
            int eventId,           // 事件 ID
            long timestamp,        // 时间戳（纳秒）
            String extraStrings    // 额外字符串参数
    );
}
```

**使用场景：** 第三方 SDK 和应用，需要完整的事件数据

---

## 使用示例

### 示例 1：发送事件（基础用法）

```java
// 获取当前时间戳
long ts = TranPerfEvent.now();

// 发送应用启动事件
TranPerfEvent.notifyEventStart(TranPerfEvent.EVENT_APP_LAUNCH, ts);

// ... 执行应用初始化 ...

// 发送事件结束
TranPerfEvent.notifyEventEnd(TranPerfEvent.EVENT_APP_LAUNCH, ts);
```

### 示例 2：发送带参数的事件

```java
long ts = TranPerfEvent.now();
int[] params = {100, 200, 300};  // 额外的整数参数
String[] strParams = {"com.example.app", "launch"};

// 发送带参数的事件
TranPerfEvent.notifyEventStart(
    TranPerfEvent.EVENT_APP_LAUNCH,
    ts,
    3,                    // 参数个数
    params,              // 整数参数
    strParams            // 字符串参数
);
```

### 示例 3：注册简化监听器（Framework 内部）

```java
TranPerfEvent.registerListener(new TranPerfEvent.TrEventListener() {
    @Override
    public void onEventStart(int eventId, long timestamp, int duration) {
        Log.d("PerfHub", "Event started: " + eventId + ", duration: " + duration);
    }

    @Override
    public void onEventEnd(int eventId, long timestamp) {
        Log.d("PerfHub", "Event ended: " + eventId);
    }
});
```

### 示例 4：注册完整监听器（SDK 推荐）

```java
TranPerfEvent.registerEventListener(new TranPerfEvent.PerfEventListener() {
    @Override
    public void onEventStart(int eventId, long timestamp, int numParams,
                             int[] intParams, String extraStrings) {
        Log.d("SDK", "Event: " + eventId);
        Log.d("SDK", "Timestamp: " + timestamp);
        Log.d("SDK", "Params: " + Arrays.toString(intParams));
        Log.d("SDK", "Extra: " + extraStrings);
    }

    @Override
    public void onEventEnd(int eventId, long timestamp, String extraStrings) {
        Log.d("SDK", "Event " + eventId + " finished");
    }
});
```

### 示例 5：AIDL 监听器（高级用法）

```java
// 直接注册 AIDL 监听器
TranPerfEvent.registerEventListener(new IEventListener.Stub() {
    @Override
    public void onEventStart(int eventId, long timestamp, int numParams,
                             int[] intParams, String extraStrings)
            throws RemoteException {
        // 处理事件开始
    }

    @Override
    public void onEventEnd(int eventId, long timestamp, String extraStrings)
            throws RemoteException {
        // 处理事件结束
    }
});
```

---

## API 参考

### 事件发送 API

#### notifyEventStart 重载

| 方法签名 | 说明 |
|---------|------|
| `notifyEventStart(int eventId, long timestamp)` | 基础事件 |
| `notifyEventStart(int eventId, long timestamp, String extraString)` | 带字符串参数 |
| `notifyEventStart(int eventId, long timestamp, String[] stringParams)` | 带字符串数组 |
| `notifyEventStart(int eventId, long timestamp, int numParams, int[] intParams)` | 带整数参数 |
| `notifyEventStart(int eventId, long timestamp, int numParams, int[] intParams, String[] stringParams)` | 完整参数 |

#### notifyEventEnd

```java
// 基础结束事件
public static void notifyEventEnd(int eventId, long timestamp)

// 带字符串参数的结束事件
public static void notifyEventEnd(int eventId, long timestamp, String extraString)
```

### 监听器管理 API

```java
// 注册简化监听器
TranPerfEvent.registerListener(TrEventListener listener)

// 取消注册简化监听器
TranPerfEvent.unregisterListener(TrEventListener listener)

// 注册完整监听器（推荐）
TranPerfEvent.registerEventListener(PerfEventListener listener)

// 取消注册完整监听器
TranPerfEvent.unregisterEventListener(PerfEventListener listener)

// 注册 AIDL 监听器（高级）
TranPerfEvent.registerEventListener(IBinder listenerBinder)

// 取消注册 AIDL 监听器（高级）
TranPerfEvent.unregisterEventListener(IBinder listenerBinder)
```

### 实用工具方法

```java
// 获取当前纳秒级时间戳（推荐用于事件记录）
long timestamp = TranPerfEvent.now();
```

---

## 工作原理

```
┌────────────────────────────────────────────────────────────┐
│                应用层 / Framework 层                       │
│      调用 TranPerfEvent.notifyEventStart/End()             │
└─────────────────────────────┬──────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────┐
│                 TranPerfEvent (事件中心)                   │
├────────────────────────────────────────────────────────────┤
│ 1. 检查 Flags.enableTranperfhub() 是否启用                 │
│ 2. 记录接收时间戳和延迟监控                                │
│ 3. 通知本地监听器（多线程安全）                            │
│ 4. 通过反射调用 TranPerfHub                                │
└────┬───────────────────────────────────────────────────────┘
     │
     ├─► 简化监听器（TrEventListener）
     │
     ├─► 完整监听器（PerfEventListener / IEventListener）
     │
     └─► TranPerfHub (通过反射)
         └─► 性能优化决策 / 系统调度
```

---

## 关键特性

### 1. 延迟监控

默认延迟阈值为 **10ms**。当事件到达延迟超过此阈值时会输出警告日志：

```
notifyEventStart: eventId=1, timestamp=123456, latency=15000000 ns (15.00 ms), ...
```

### 2. 线程安全

- 使用 `CopyOnWriteArrayList` 存储监听器
- 支持并发的事件发送和监听器注册/取消注册

### 3. Feature Flag 控制

通过 Android Feature Flags（aconfig）控制模块启用状态：

```java
if (!Flags.enableTranperfhub()) {
    return;  // 模块禁用，不发送事件
}
```

### 4. 反射解耦

使用反射调用 `com.transsion.perfhub.TranPerfHub`，避免编译时直接依赖。

---

## 调试与日志

启用 DEBUG 日志（修改源代码或运行时）：

```java
// 在代码中搜索 DEBUG 开关，设置为 true
private static final boolean DEBUG = true;
```

启用后会输出详细的事件通知日志：

```
D/TranPerfEvent: notifyEventStart: eventId=1, timestamp=..., latency=... ns, ...
D/TranPerfEvent: Simplified listener registered, total: 2
D/TranPerfEvent: Listener registration methods found
```

---

## 常见问题

### Q1: 应该使用哪个监听器接口？

| 场景 | 推荐 |
|------|------|
| Framework 内部组件 | `TrEventListener` |
| 第三方 SDK | `PerfEventListener` |
| 特殊场景（跨进程） | `IEventListener.Stub` (AIDL) |

### Q2: 如何获取正确的时间戳？

```java
// 推荐：使用专用方法
long ts = TranPerfEvent.now();

// 也可以：System.nanoTime() 或 SystemClock.elapsedRealtimeNanos()
long ts = System.nanoTime();

// 不推荐：System.currentTimeMillis()（精度不足）
```

### Q3: 事件没有被接收到怎么办？

检查以下几点：
1. Feature Flag 是否启用：`Flags.enableTranperfhub()`
2. 监听器是否正确注册
3. TranPerfHub 是否正常加载（查看反射初始化日志）

### Q4: 支持的字符串参数有限制吗？

字符串参数使用 ASCII 31（`\u001F`）作为分隔符，避免使用该字符。多个字符串会被自动连接。

---

## 实现细节

### 反射初始化

首次调用事件发送或监听器注册时，会进行延迟反射初始化：

```
TranPerfHub 类查找
  ↓
notifyEventStart/End 方法查找
  ↓
registerEventListener/unregisterEventListener 方法查找（可选）
  ↓
缓存反射结果，避免重复查找
```

### 异常处理

- `RemoteException`：远程 AIDL 调用失败
- `NoClassDefFoundError`：TranPerfHub 类未找到
- `ReflectionException`：反射调用失败

所有异常都会被捕获并记录到日志，不会导致应用崩溃。

---

## 性能指标

- **事件发送延迟**：< 1ms（在无监听器情况下）
- **监听器回调**：同步执行，避免阻塞关键路径
- **内存占用**：监听器以 `CopyOnWriteArrayList` 形式存储，写入时复制成本可接受

---

## 版本兼容性

- **最小 API 级别**：Android Framework 层
- **TranPerfHub**：需要通过反射找到，可选依赖

---

## 相关资源

- [TranPerfHub Adapter](../vendor/adapter/) - 服务端实现
- [AIDL 接口定义](../interfaces/perfhub/) - IEventListener AIDL 定义
- [系统集成](../system/tranperfhub_system.mk) - 系统集成配置
