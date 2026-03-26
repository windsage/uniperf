#include "ServiceBridge.h"

#define ATRACE_TAG (ATRACE_TAG_POWER | ATRACE_TAG_HAL)
#include <sched.h>
#include <sys/prctl.h>
#include <unistd.h>
#include <utils/Trace.h>

#include <cerrno>
#include <cstdio>
#include <cstring>

#include "PerfLockCaller.h"
#include "TranLog.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "TPE-Bridge"
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

    startWorkerPool();
    TLOGI("ServiceBridge initialized successfully");
}

ServiceBridge::~ServiceBridge() {
    TLOGI("ServiceBridge destroyed");

    stopWorkerPool();
    // 清理监听器
    std::lock_guard<Mutex> lock(mListenerLock);
    mListeners.clear();
}

// ==================== Worker Thread Pool ====================

void ServiceBridge::startWorkerPool() {
    mShutdown.store(false, std::memory_order_relaxed);
    mWorkerThreads.reserve(kWorkerThreadCount);

    for (int i = 0; i < kWorkerThreadCount; ++i) {
        mWorkerThreads.emplace_back([this, i]() {
            workerLoop(i + 1);    // index 从 1 开始，命名为 tpe_worker_1 ~ tpe_worker_4
        });
    }
    TLOGI("Worker pool started: %d threads", kWorkerThreadCount);
}

void ServiceBridge::stopWorkerPool() {
    {
        std::lock_guard<std::mutex> lock(mQueueMutex);
        mShutdown.store(true, std::memory_order_relaxed);
    }
    mQueueCv.notify_all();    // 唤醒所有等待的工作线程

    for (auto &t : mWorkerThreads) {
        if (t.joinable())
            t.join();
    }
    mWorkerThreads.clear();
    TLOGI("Worker pool stopped");
}

void ServiceBridge::workerLoop(int index) {
    // Set thread name: "tpe_worker_1" ~ "tpe_worker_4", max 15 chars
    char name[16];
    snprintf(name, sizeof(name), "tpe_worker_%d", index);
    prctl(PR_SET_NAME, name, 0, 0, 0);

    struct sched_param param;
    param.sched_priority = 1;
    int res = sched_setscheduler(0, SCHED_FIFO, &param);
    if (res == 0) {
        TLOGI("Worker thread '%s' started, tid=%d elevated to SCHED_FIFO successfully!", name, gettid());
    } else {
        TLOGW("sched_setscheduler failed for worker thread '%s',tid=%d , errno=%d, error=%s", name, gettid(), errno, strerror(errno));
    }

    TLOGI("Worker thread '%s' tid=%d started!", name, gettid());

    while (true) {
        EventTask task;
        {
            std::unique_lock<std::mutex> lock(mQueueMutex);
            // Block until there's a task or shutdown is requested
            mQueueCv.wait(lock, [this]() {
                return !mTaskQueue.empty() || mShutdown.load(std::memory_order_relaxed);
            });

            if (mShutdown.load(std::memory_order_relaxed) && mTaskQueue.empty()) {
                break;    // Drain remaining tasks before exit
            }

            task = std::move(mTaskQueue.front());
            mTaskQueue.pop();
        }

        // Execute task outside the lock
        if (task.type == EventTask::Type::START) {
            processEventStart(task);
        } else {
            processEventEnd(task);
        }
    }

    TLOGI("Worker thread '%s' tid=%d exiting", name, gettid());
}

void ServiceBridge::enqueueTask(EventTask task) {
    {
        std::lock_guard<std::mutex> lock(mQueueMutex);
        mTaskQueue.push(std::move(task));
    }
    mQueueCv.notify_one();
}

// ==================== Task Processing (runs on worker threads) ====================

void ServiceBridge::processEventStart(const EventTask &task) {
    char buf[64];
    snprintf(buf, sizeof(buf), "ServiceBridge::processEventStart eventId=0x%d", task.eventId);
    ATRACE_BEGIN(buf);

    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        ATRACE_END();
        return;
    }

    EventContext ctx =
        EventContext::parse(task.eventId, task.intParams, task.extraStrings.value_or(""));

    TLOGI("processEventStart: eventId=%d pkg='%s' act='%s'", ctx.eventId, ctx.package.c_str(),
          ctx.activity.c_str());

    // 1. Broadcast to listeners (oneway, non-blocking)
    broadcastEventStart(task.eventId, task.timestamp, task.numParams, task.intParams,
                        task.extraStrings);

    // 2. Acquire perf lock — this is the slow path, safe on worker thread
    int32_t platformHandle = mPerfLockCaller->acquirePerfLock(ctx);
    if (platformHandle < 0) {
        TLOGE("Failed to acquire perf lock for eventId=%d", task.eventId);
        ATRACE_END();
        return;
    }

    // 3. Track active event
    {
        std::lock_guard<Mutex> lock(mEventLock);
        auto it = mActiveEvents.find(task.eventId);
        if (it != mActiveEvents.end()) {
            TLOGW("Event %d already active, releasing old handle=%d", task.eventId,
                  it->second.platformHandle);
            mPerfLockCaller->releasePerfLock(it->second.platformHandle);
        }
        EventInfo info;
        info.platformHandle = platformHandle;
        info.startTime = task.timestamp;
        info.packageName = ctx.package;
        mActiveEvents[task.eventId] = info;
    }

    TLOGI("Event %d started, handle=%d", task.eventId, platformHandle);
    ATRACE_END();
}

void ServiceBridge::processEventEnd(const EventTask &task) {
    char buf[64];
    snprintf(buf, sizeof(buf), "ServiceBridge::processEventEnd eventId=0x%d", task.eventId);
    ATRACE_BEGIN(buf);

    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        ATRACE_END();
        return;
    }

    EventContext ctx = EventContext::parse(task.eventId, {}, task.extraStrings.value_or(""));

    TLOGI("processEventEnd: eventId=%d pkg='%s'", ctx.eventId, ctx.package.c_str());

    // 1. Broadcast to listeners
    broadcastEventEnd(task.eventId, task.timestamp, task.extraStrings);

    // 2. Release perf lock
    {
        std::lock_guard<Mutex> lock(mEventLock);
        auto it = mActiveEvents.find(task.eventId);
        if (it == mActiveEvents.end()) {
            TLOGW("Event %d not found in active events", task.eventId);
            ATRACE_END();
            return;
        }
        mPerfLockCaller->releasePerfLock(it->second.platformHandle);

        int64_t elapsedMs = (task.timestamp - it->second.startTime) / 1000000LL;
        TLOGI("Event %d ended: handle=%d elapsed=%lldms", task.eventId, it->second.platformHandle,
              (long long)elapsedMs);
        mActiveEvents.erase(it);
    }

    ATRACE_END();
}

// ==================== Event Notification Implementation ====================

::ndk::ScopedAStatus ServiceBridge::notifyEventStart(
    int32_t eventId, int64_t timestamp, int32_t numParams, const std::vector<int32_t> &intParams,
    const std::optional<std::string> &extraStrings) {
    char buf[64];
    snprintf(buf, sizeof(buf), "ServiceBridge::notifyEventStart 0x%x", eventId);
    ATRACE_NAME(buf);

    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        return ::ndk::ScopedAStatus::ok();
    }

    if (static_cast<int32_t>(intParams.size()) != numParams) {
        TLOGE("Param count mismatch: expected=%d actual=%zu", numParams, intParams.size());
        return ::ndk::ScopedAStatus::ok();
    }

    EventTask task;
    task.type = EventTask::Type::START;
    task.eventId = eventId;
    task.timestamp = timestamp;
    task.numParams = numParams;
    task.intParams = intParams;
    task.extraStrings = extraStrings;

    enqueueTask(std::move(task));

    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus ServiceBridge::notifyEventEnd(int32_t eventId, int64_t timestamp,
                                                   const std::optional<std::string> &extraStrings) {
    char buf[64];
    snprintf(buf, sizeof(buf), "ServiceBridge::notifyEventEnd 0x%x", eventId);
    ATRACE_NAME(buf);

    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        return ::ndk::ScopedAStatus::ok();
    }

    EventTask task;
    task.type = EventTask::Type::END;
    task.eventId = eventId;
    task.timestamp = timestamp;
    task.extraStrings = extraStrings;

    enqueueTask(std::move(task));

    return ::ndk::ScopedAStatus::ok();
}

// ==================== Listener Registration Implementation  ====================

::ndk::ScopedAStatus ServiceBridge::registerEventListener(
    const std::shared_ptr<IEventListener> &listener, const std::vector<int32_t> &eventFilter) {
    ATRACE_NAME("ServiceBridge::registerEventListener");

    if (!listener) {
        TLOGE("Null listener");
        return ::ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    std::lock_guard<Mutex> lock(mListenerLock);

    if (findListener(listener)) {
        TLOGW("Listener already registered");
        return ::ndk::ScopedAStatus::ok();
    }

    auto deathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(
        AIBinder_DeathRecipient_new(ServiceBridge::onListenerDied));

    auto *cookie = new DeathCookie{this, listener->asBinder().get()};
    binder_status_t status =
        AIBinder_linkToDeath(listener->asBinder().get(), deathRecipient.get(), cookie);

    if (status != STATUS_OK) {
        TLOGE("Failed to link to death: %d", status);
        delete cookie;
        return ::ndk::ScopedAStatus::fromStatus(status);
    }

    // Build filter set; empty vector means subscribe all
    std::unordered_set<int32_t> filterSet(eventFilter.begin(), eventFilter.end());

    if (filterSet.empty()) {
        TLOGI("Listener registered: subscribe ALL events, total=%zu", mListeners.size() + 1);
    } else {
        TLOGI("Listener registered: filter=%zu eventIds, total=%zu", filterSet.size(),
              mListeners.size() + 1);
    }

    mListeners.emplace_back(listener, std::move(deathRecipient), cookie, std::move(filterSet));

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
    std::vector<std::shared_ptr<IEventListener>> snapshot;
    {
        std::lock_guard<Mutex> lock(mListenerLock);
        if (mListeners.empty())
            return;
        snapshot.reserve(mListeners.size());
        for (auto &info : mListeners) {
            // Empty filter = subscribe all; otherwise check membership
            if (info.eventFilter.empty() || info.eventFilter.count(eventId) > 0) {
                snapshot.push_back(info.listener);
            }
        }
    }

    if (snapshot.empty()) {
        TLOGD("broadcastEventStart: no listeners for eventId=%d", eventId);
        return;
    }

    TLOGI("Broadcasting eventStart eventId=%d to %zu/%zu listeners", eventId, snapshot.size(),
          mListeners.size());

    for (auto &listener : snapshot) {
        auto status =
            listener->onEventStart(eventId, timestamp, numParams, intParams, extraStrings);
        if (!status.isOk()) {
            TLOGW("Failed to notify listener onEventStart: %s", status.getMessage());
        }
    }
}

void ServiceBridge::broadcastEventEnd(int32_t eventId, int64_t timestamp,
                                      const std::optional<std::string> &extraStrings) {
    std::vector<std::shared_ptr<IEventListener>> snapshot;
    {
        std::lock_guard<Mutex> lock(mListenerLock);
        if (mListeners.empty())
            return;
        snapshot.reserve(mListeners.size());
        for (auto &info : mListeners) {
            if (info.eventFilter.empty() || info.eventFilter.count(eventId) > 0) {
                snapshot.push_back(info.listener);
            }
        }
    }

    if (snapshot.empty()) {
        TLOGD("broadcastEventEnd: no listeners for eventId=%d", eventId);
        return;
    }

    TLOGI("Broadcasting eventEnd eventId=%d to %zu/%zu listeners", eventId, snapshot.size(),
          mListeners.size());

    for (auto &listener : snapshot) {
        auto status = listener->onEventEnd(eventId, timestamp, extraStrings);
        if (!status.isOk()) {
            TLOGW("Failed to notify listener onEventEnd: %s", status.getMessage());
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
    AIBinder *targetBinder = listener->asBinder().get();
    for (auto it = mListeners.begin(); it != mListeners.end();) {
        if (it->listener->asBinder().get() == targetBinder) {
            AIBinder_unlinkToDeath(it->listener->asBinder().get(), it->deathRecipient.get(),
                                   it->cookie);
            delete it->cookie;
            it = mListeners.erase(it);
        } else {
            ++it;
        }
    }
}

// ==================== Death Notification Callback  ====================

void ServiceBridge::onListenerDied(void *cookie) {
    auto *dc = static_cast<DeathCookie *>(cookie);
    if (!dc || !dc->bridge) {
        delete dc;
        return;
    }

    TLOGW("Listener died, binder=%p, removing...", dc->binder);

    {
        std::lock_guard<Mutex> lock(dc->bridge->mListenerLock);
        auto &listeners = dc->bridge->mListeners;
        for (auto it = listeners.begin(); it != listeners.end();) {
            if (it->listener->asBinder().get() == dc->binder) {
                // 已死亡，无需 unlinkToDeath
                it = listeners.erase(it);
                TLOGI("Dead listener removed, remaining: %zu", listeners.size());
            } else {
                ++it;
            }
        }
    }

    delete dc;
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
