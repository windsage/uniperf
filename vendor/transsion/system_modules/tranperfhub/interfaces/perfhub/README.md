# TranPerfHub AIDL 接口文档

## 1. 模块概述

`vendor.transsion.hardware.perfhub` 是一个基于 AIDL 的硬件抽象层接口，用于在 **System 分区**（如 Framework、App）与 **Vendor 分区**（硬件服务）之间传递性能相关事件。

其核心目标是实现性能调度信息的实时同步：

* **生产者**：通过 `ITranPerfHub` 通知性能事件（如应用启动、列表滚动）。
* **消费者**：通过注册 `IEventListener` 监听并响应这些事件，从而进行频率调节、资源分配等优化。

---

## 2. 接口定义与作用

### 2.1 ITranPerfHub.aidl

这是服务的主接口，负责事件的注入与监听者的管理。

| 方法 | 类型 | 作用描述 |
| --- | --- | --- |
| `notifyEventStart` | `oneway` | 通知系统一个性能事件开始。包含事件 ID、时间戳、整型参数数组及额外字符串信息。 |
| `notifyEventEnd` | `oneway` | 通知系统一个性能事件结束。用于释放因 `notifyEventStart` 申请的资源。 |
| `registerEventListener` | `blocking` | 注册一个回调接口。注册成功后，该监听者将收到所有经过 PerfHub 的事件通知。 |
| `unregisterEventListener` | `blocking` | 注销回调接口。停止接收后续的事件通知。 |

### 2.2 IEventListener.aidl

这是由客户端（监听者）实现的异步回调接口。

| 方法 | 类型 | 作用描述 |
| --- | --- | --- |
| `onEventStart` | `oneway` | 当有性能事件触发时，服务端回调此方法通知监听者。 |
| `onEventEnd` | `oneway` | 当性能事件结束时，服务端回调此方法通知监听者。 |

---

## 3. 接口冻结流程

由于该接口目前处于开发阶段（位于 `aidl_api/.../current`），在正式发布前需要进行“冻结”操作，以确保版本兼容性。

### 步骤 1：检查接口稳定性

确保 `Android.bp` 中 `aidl_interface` 模块已声明 `stability: "vintf"`。

### 步骤 2：生成 API 转储

运行以下命令，将当前 `current` 目录下的接口定义生成一个带版本号的快照：

```bash
# 假设模块名为 vendor.transsion.hardware.perfhub
m vendor.transsion.hardware.perfhub-freeze-api

```

### 步骤 3：确认目录变化

执行上述命令后，系统会自动在 `aidl_api/vendor.transsion.hardware.perfhub/` 目录下创建一个新的版本号目录（例如 `1`），并将 `current` 目录的内容拷贝过去。

目录结构将变为：

```text
aidl_api/vendor.transsion.hardware.perfhub/
├── current (开发分支)
└── 1 (冻结的版本1)

```

### 步骤 4：更新 Android.bp

在 `Android.bp` 中更新版本号：

```blueprint
aidl_interface {
    name: "vendor.transsion.hardware.perfhub",
    vendor_available: true,
    srcs: ["vendor/transsion/hardware/perfhub/*.aidl"],
    stability: "vintf",
    backend: {
        cpp: { enabled: false },
        java: { enabled: true },
        ndk: { enabled: true },
    },
    versions_with_info: [
        {
            version: "1",
            imports: [],
        },
    ],
    frozen: true,
    owner: "transsion",
}

```
