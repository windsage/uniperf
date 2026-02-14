#include "ServiceBridge.h"

#include <sched.h>
#include <sys/prctl.h>
#include <unistd.h>
#include <utils/Trace.h>

#include <cerrno>
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

    TLOGI("ServiceBridge initialized successfully");
}

ServiceBridge::~ServiceBridge() {
    TLOGI("ServiceBridge destroyed");

    // 清理监听器
    std::lock_guard<Mutex> lock(mListenerLock);
    mListeners.clear();
}

static void ensureBinderThreadSetup() {
    thread_local bool sSetup = false;
    if (sSetup)
        return;

    // Rename thread — max 15 chars (PR_SET_NAME limit)
    int ret = prctl(PR_SET_NAME, "tpe_binder", 0, 0, 0);
    if (ret != 0) {
        TLOGW("prctl PR_SET_NAME failed: %s", strerror(errno));
    } else {
        TLOGD("Binder thread tid=%d renamed to 'tpe_binder'", gettid());
    }

    // Elevate to RT
    struct sched_param param;
    param.sched_priority = 2;
    ret = sched_setscheduler(0, SCHED_FIFO, &param);
    if (ret == 0) {
        TLOGI("Binder thread tid=%d elevated to SCHED_FIFO prio=2", gettid());
    } else {
        TLOGW("sched_setscheduler failed: %s (tid=%d)", strerror(errno), gettid());
    }

    sSetup = true;
}
// ==================== Event Notification Implementation ====================

::ndk::ScopedAStatus ServiceBridge::notifyEventStart(
    int32_t eventId, int64_t timestamp, int32_t numParams, const std::vector<int32_t> &intParams,
    const std::optional<std::string> &extraStrings) {
    ATRACE_NAME("ServiceBridge::notifyEventStart");
    ensureBinderThreadSetup();

    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        return ::ndk::ScopedAStatus::ok();
    }

    if (static_cast<int32_t>(intParams.size()) != numParams) {
        TLOGE("Param count mismatch: expected=%d actual=%zu", numParams, intParams.size());
        return ::ndk::ScopedAStatus::ok();
    }

    // Single source of truth: parse all context here
    EventContext ctx = EventContext::parse(eventId, intParams, extraStrings.value_or(""));

    TLOGI("notifyEventStart: eventId=%d pkg='%s' act='%s' fps=%d duration=%d", ctx.eventId,
          ctx.package.c_str(), ctx.activity.c_str(), ctx.fps, ctx.duration);

    // 1. Broadcast to listeners first (non-blocking, oneway)
    broadcastEventStart(eventId, timestamp, numParams, intParams, extraStrings);

    // 2. Acquire perf lock
    int32_t platformHandle = mPerfLockCaller->acquirePerfLock(ctx);
    if (platformHandle < 0) {
        TLOGE("Failed to acquire perf lock for eventId=%d", eventId);
        return ::ndk::ScopedAStatus::ok();
    }

    // 3. Track active event
    {
        std::lock_guard<Mutex> lock(mEventLock);
        auto it = mActiveEvents.find(eventId);
        if (it != mActiveEvents.end()) {
            TLOGW("Event %d already active, releasing old handle=%d", eventId,
                  it->second.platformHandle);
            mPerfLockCaller->releasePerfLock(it->second.platformHandle);
        }
        EventInfo info;
        info.platformHandle = platformHandle;
        info.startTime = timestamp;
        info.packageName = ctx.package;
        mActiveEvents[eventId] = info;
    }

    TLOGI("Event %d started, handle=%d", eventId, platformHandle);
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus ServiceBridge::notifyEventEnd(int32_t eventId, int64_t timestamp,
                                                   const std::optional<std::string> &extraStrings) {
    ATRACE_NAME("ServiceBridge::notifyEventEnd");
    ensureBinderThreadSetup();

    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        return ::ndk::ScopedAStatus::ok();
    }

    // Parse context for logging; extraStrings on End carries same
    // positional convention as Start (e.g. package at strings[0])
    EventContext ctx = EventContext::parse(eventId, {}, extraStrings.value_or(""));

    TLOGI("notifyEventEnd: eventId=%d pkg='%s'", ctx.eventId, ctx.package.c_str());

    // 1. Broadcast to listeners first
    broadcastEventEnd(eventId, timestamp, extraStrings);

    // 2. Find, release, and remove active event
    {
        std::lock_guard<Mutex> lock(mEventLock);
        auto it = mActiveEvents.find(eventId);
        if (it == mActiveEvents.end()) {
            TLOGW("Event %d not found in active events", eventId);
            return ::ndk::ScopedAStatus::ok();
        }

        mPerfLockCaller->releasePerfLock(it->second.platformHandle);

        int64_t elapsedMs = (timestamp - it->second.startTime) / 1000000LL;
        TLOGI("Event %d ended: handle=%d elapsed=%lldms", eventId, it->second.platformHandle,
              (long long)elapsedMs);

        mActiveEvents.erase(it);
    }

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
            it = listeners.erase(it);
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
