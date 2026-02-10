#include "ServiceBridge.h"

#include <utils/Trace.h>

#include "PerfLockCaller.h"
#include "TranLog.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "PerfEngine-ServiceBridge"
namespace vendor {
namespace transsion {
namespace perfengine {

// ==================== Constructor / Destructor ====================

ServiceBridge::ServiceBridge() {
    ATRACE_CALL();
    TLOGI("ServiceBridge initializing...");

    // 创建性能锁调用器
    mPerfLockCaller = std::make_unique<PerfLockCaller>();
    if (!mPerfLockCaller || !mPerfLockCaller->init()) {
        TLOGE("Failed to initialize PerfLockCaller");
        mPerfLockCaller.reset();
        return;
    }

    TLOGI("ServiceBridge initialized successfully");
}

ServiceBridge::~ServiceBridge() {
    TLOGI("ServiceBridge destroyed");

    // 清理监听器
    std::lock_guard<Mutex> lock(mListenerLock);
    mListeners.clear();
}

// ==================== Event Notification Implementation ====================

::ndk::ScopedAStatus ServiceBridge::notifyEventStart(
    int32_t eventId, int64_t timestamp, int32_t numParams, const std::vector<int32_t> &intParams,
    const std::optional<std::string> &extraStrings) {
    ATRACE_NAME("ServiceBridge::notifyEventStart");

    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        return ::ndk::ScopedAStatus::ok();
    }

    // 参数校验
    if (static_cast<int32_t>(intParams.size()) != numParams) {
        TLOGE("Parameter count mismatch: expected=%d, actual=%zu", numParams, intParams.size());
        return ::ndk::ScopedAStatus::ok();
    }

    if (numParams < 1) {
        TLOGE("Invalid parameter count: %d", numParams);
        return ::ndk::ScopedAStatus::ok();
    }

    // 提取 duration 和 packageName
    int32_t duration = getDuration(intParams);
    std::string packageName = extraStrings.value_or("");

    TLOGI("notifyEventStart: eventId=%d, duration=%d, pkg=%s", eventId, duration,
          packageName.c_str());

    // 1. 先广播给监听器 (在申请锁之前)
    broadcastEventStart(eventId, timestamp, numParams, intParams, extraStrings);

    // 2. 申请性能锁
    int32_t platformHandle =
        mPerfLockCaller->acquirePerfLock(eventId, duration, intParams, packageName);

    if (platformHandle < 0) {
        TLOGE("Failed to acquire perf lock for event %d", eventId);
        return ::ndk::ScopedAStatus::ok();
    }

    // 3. 保存事件信息
    {
        std::lock_guard<Mutex> lock(mEventLock);

        // 检查事件是否已存在
        auto it = mActiveEvents.find(eventId);
        if (it != mActiveEvents.end()) {
            TLOGW("Event %d already active, releasing old handle %d", eventId,
                  it->second.platformHandle);
            mPerfLockCaller->releasePerfLock(it->second.platformHandle);
        }

        // 存储新事件
        EventInfo info;
        info.platformHandle = platformHandle;
        info.startTime = timestamp;
        info.packageName = packageName;
        mActiveEvents[eventId] = info;
    }

    TLOGI("Event %d started, platformHandle=%d", eventId, platformHandle);

    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus ServiceBridge::notifyEventEnd(int32_t eventId, int64_t timestamp,
                                                   const std::optional<std::string> &extraStrings) {
    ATRACE_NAME("ServiceBridge::notifyEventEnd");

    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        return ::ndk::ScopedAStatus::ok();
    }

    std::string packageName = extraStrings.value_or("");

    TLOGI("notifyEventEnd: eventId=%d, pkg=%s", eventId, packageName.c_str());

    // 1. 先广播给监听器
    broadcastEventEnd(eventId, timestamp, extraStrings);

    // 2. 查找并释放事件
    std::lock_guard<Mutex> lock(mEventLock);

    auto it = mActiveEvents.find(eventId);
    if (it == mActiveEvents.end()) {
        TLOGW("Event %d not found in active events", eventId);
        return ::ndk::ScopedAStatus::ok();
    }

    // 释放性能锁
    mPerfLockCaller->releasePerfLock(it->second.platformHandle);

    // 计算事件持续时间(用于日志)
    int64_t duration = timestamp - it->second.startTime;
    TLOGI("Event %d ended, duration=%lld ms", eventId, (long long)(duration / 1000000));

    // 从活跃事件中移除
    mActiveEvents.erase(it);

    return ::ndk::ScopedAStatus::ok();
}

// ==================== Listener Registration Implementation  ====================

::ndk::ScopedAStatus ServiceBridge::registerEventListener(
    const std::shared_ptr<IEventListener> &listener) {
    ATRACE_NAME("ServiceBridge::registerEventListener");

    if (!listener) {
        TLOGE("Null listener");
        return ::ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    std::lock_guard<Mutex> lock(mListenerLock);

    // 检查是否已注册
    if (findListener(listener)) {
        TLOGW("Listener already registered");
        return ::ndk::ScopedAStatus::ok();
    }

    // 创建 death recipient
    auto deathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(
        AIBinder_DeathRecipient_new(ServiceBridge::onListenerDied));

    // 关联到死亡通知
    binder_status_t status = AIBinder_linkToDeath(listener->asBinder().get(), deathRecipient.get(),
                                                  this);    // cookie = this pointer

    if (status != STATUS_OK) {
        TLOGE("Failed to link to death: %d", status);
        return ::ndk::ScopedAStatus::fromStatus(status);
    }

    // 添加到监听器列表
    mListeners.emplace_back(listener, std::move(deathRecipient));

    TLOGI("Listener registered, total listeners: %zu", mListeners.size());

    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus ServiceBridge::unregisterEventListener(
    const std::shared_ptr<IEventListener> &listener) {
    ATRACE_NAME("ServiceBridge::unregisterEventListener");

    if (!listener) {
        TLOGE("Null listener");
        return ::ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    std::lock_guard<Mutex> lock(mListenerLock);

    removeListener(listener);

    TLOGI("Listener unregistered, remaining listeners: %zu", mListeners.size());

    return ::ndk::ScopedAStatus::ok();
}

// ==================== Broadcasting Implementation  ====================

void ServiceBridge::broadcastEventStart(int32_t eventId, int64_t timestamp, int32_t numParams,
                                        const std::vector<int32_t> &intParams,
                                        const std::optional<std::string> &extraStrings) {
    std::lock_guard<Mutex> lock(mListenerLock);

    if (mListeners.empty()) {
        return;    // 没有监听器,跳过广播
    }

    TLOGI("Broadcasting eventStart to %zu listeners", mListeners.size());

    // 广播给所有监听器 (oneway 调用,非阻塞)
    for (auto &info : mListeners) {
        auto status =
            info.listener->onEventStart(eventId, timestamp, numParams, intParams, extraStrings);

        if (!status.isOk()) {
            TLOGW("Failed to notify listener: %s", status.getMessage());
            // 继续通知下一个监听器,即使这个失败
        }
    }
}

void ServiceBridge::broadcastEventEnd(int32_t eventId, int64_t timestamp,
                                      const std::optional<std::string> &extraStrings) {
    std::lock_guard<Mutex> lock(mListenerLock);

    if (mListeners.empty()) {
        return;
    }

    TLOGI("Broadcasting eventEnd to %zu listeners", mListeners.size());

    for (auto &info : mListeners) {
        auto status = info.listener->onEventEnd(eventId, timestamp, extraStrings);

        if (!status.isOk()) {
            TLOGW("Failed to notify listener: %s", status.getMessage());
        }
    }
}

// ==================== Listener Management Helpers  ====================

bool ServiceBridge::findListener(const std::shared_ptr<IEventListener> &listener) {
    // 必须在持有 mListenerLock 的情况下调用

    AIBinder *targetBinder = listener->asBinder().get();

    for (const auto &info : mListeners) {
        if (info.listener->asBinder().get() == targetBinder) {
            return true;
        }
    }

    return false;
}

void ServiceBridge::removeListener(const std::shared_ptr<IEventListener> &listener) {
    // 必须在持有 mListenerLock 的情况下调用

    AIBinder *targetBinder = listener->asBinder().get();

    for (auto it = mListeners.begin(); it != mListeners.end();) {
        if (it->listener->asBinder().get() == targetBinder) {
            // 在移除前取消死亡通知
            AIBinder_unlinkToDeath(it->listener->asBinder().get(), it->deathRecipient.get(), this);

            it = mListeners.erase(it);
        } else {
            ++it;
        }
    }
}

// ==================== Death Notification Callback  ====================

void ServiceBridge::onListenerDied(void *cookie) {
    TLOGW("Listener died, cleaning up...");

    auto *bridge = static_cast<ServiceBridge *>(cookie);
    if (!bridge) {
        TLOGE("Invalid bridge pointer in death callback");
        return;
    }

    // 注意: 无法从回调中识别具体哪个监听器死亡
    // 实际上,当进程死亡时,其所有 Binder 对象都会失效
    // 我们会在下次注册/注销时清理无效监听器
    // 或实现更复杂的跟踪机制(如果需要)

    TLOGI("Death notification received, listener will be cleaned up on next access");
}

// ==================== Helper Methods ====================

int32_t ServiceBridge::getDuration(const std::vector<int32_t> &intParams) const {
    // Duration 总是第一个参数
    return intParams.empty() ? 0 : intParams[0];
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
