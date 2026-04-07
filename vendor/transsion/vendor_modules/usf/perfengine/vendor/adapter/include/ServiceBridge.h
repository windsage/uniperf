// vendor/transsion/vendor_modules/usf/perfengine/vendor/adapter/include/ServiceBridge.h
// AIDL 服务桥接层 - 重命名自 PerfEngineAdapter

#ifndef SERVICE_BRIDGE_H
#define SERVICE_BRIDGE_H

#include <aidl/vendor/transsion/hardware/perfengine/BnPerfEngine.h>
#include <aidl/vendor/transsion/hardware/perfengine/IEventListener.h>
#include <android/binder_auto_utils.h>
#include <utils/Mutex.h>

#include <atomic>
#include <condition_variable>
#include <map>
#include <memory>
#include <mutex>
#include <queue>
#include <thread>
#include <unordered_set>
#include <vector>

#include "EventContext.h"

namespace vendor::transsion::perfengine::dse {
template <typename ProfileSpec>
class ResourceScheduleService;
struct SchedEvent;
struct EntryProfileSpec;
struct MainProfileSpec;
struct FlagshipProfileSpec;
class StateService;
class StateVault;
/** @see core/service/ResourceScheduleService.h — 约束/能力数据源接口 */
class ConstraintSnapshotProvider;
class CapabilityProvider;
}    // namespace vendor::transsion::perfengine::dse

namespace vendor {
namespace transsion {
namespace perfengine {

#if defined(DSE_PROFILE_FLAGSHIP)
using DseProfileSpec = dse::FlagshipProfileSpec;
#elif defined(DSE_PROFILE_MAIN)
using DseProfileSpec = dse::MainProfileSpec;
#else
using DseProfileSpec = dse::EntryProfileSpec;
#endif

using ::aidl::vendor::transsion::hardware::perfengine::BnPerfEngine;
using ::aidl::vendor::transsion::hardware::perfengine::IEventListener;
using ::android::Mutex;

class PerfLockCaller;

/**
 * ServiceBridge - AIDL 服务桥接层
 * 职责: AIDL 通信 + 监听器管理 + 事件分发
 *
 * 桥接层职责边界：
 * 1. AIDL 协议适配：将 AIDL 请求转换为内部事件
 * 2. 监听器管理：管理事件监听器的注册和通知
 * 3. 事件分发：将事件广播给所有注册的监听器
 * 4. DSE 主链路：事件优先路由到 DSE 决策引擎
 * 5. Legacy Fallback：DSE 失败时回退到 PerfLockCaller
 *
 * @note 遵循框架 §5 服务层设计，桥接层不包含决策逻辑
 */
class ServiceBridge : public BnPerfEngine {
public:
    ServiceBridge();
    ~ServiceBridge() override;

    ::ndk::ScopedAStatus notifyEventStart(int32_t eventId, int64_t timestamp, int32_t numParams,
                                          const std::vector<int32_t> &intParams,
                                          const std::optional<std::string> &extraStrings) override;

    ::ndk::ScopedAStatus notifyEventEnd(int32_t eventId, int64_t timestamp,
                                        const std::optional<std::string> &extraStrings) override;

    ::ndk::ScopedAStatus registerEventListener(const std::shared_ptr<IEventListener> &listener,
                                               const std::vector<int32_t> &eventFilter) override;

    ::ndk::ScopedAStatus unregisterEventListener(
        const std::shared_ptr<IEventListener> &listener) override;

private:
    // ==================== Event task dispatched to worker pool ====================
    struct EventTask {
        enum class Type { START, END };
        Type type;
        int32_t eventId;
        int64_t timestamp;
        // START only
        int32_t numParams;
        std::vector<int32_t> intParams;
        std::optional<std::string> extraStrings;
    };

    // ==================== Worker thread pool ====================
    static constexpr int kWorkerThreadCount = 4;
    static constexpr char kWorkerNamePrefix[] = "tpe_worker_";

    std::vector<std::thread> mWorkerThreads;
    std::queue<EventTask> mTaskQueue;
    std::mutex mQueueMutex;
    std::condition_variable mQueueCv;
    std::atomic<bool> mShutdown{false};

    void startWorkerPool();
    void stopWorkerPool();
    void workerLoop(int index);
    void enqueueTask(EventTask task);

    void processEventStart(const EventTask &task);
    void processEventEnd(const EventTask &task);

    // ==================== Active events ====================
    struct EventInfo {
        int32_t platformHandle;
        int64_t startTime;
        std::string packageName;
        bool dseHandled;
        bool failed = false;
    };

    std::map<int32_t, EventInfo> mActiveEvents;
    Mutex mEventLock;

    std::unique_ptr<PerfLockCaller> mPerfLockCaller;
    std::unique_ptr<::vendor::transsion::perfengine::dse::ResourceScheduleService<DseProfileSpec>>
        mDseService;

    // ==================== Listeners ====================
    struct DeathCookie {
        ServiceBridge *bridge;
        AIBinder *binder;
    };

    struct ListenerInfo {
        std::shared_ptr<IEventListener> listener;
        ::ndk::ScopedAIBinder_DeathRecipient deathRecipient;
        DeathCookie *cookie;
        std::unordered_set<int32_t> eventFilter;

        ListenerInfo(std::shared_ptr<IEventListener> l, ::ndk::ScopedAIBinder_DeathRecipient dr,
                     DeathCookie *c, std::unordered_set<int32_t> ef)
            : listener(std::move(l)),
              deathRecipient(std::move(dr)),
              cookie(c),
              eventFilter(std::move(ef)) {}
    };

    std::vector<ListenerInfo> mListeners;
    Mutex mListenerLock;

    std::unique_ptr<dse::ConstraintSnapshotProvider> mConstraintProvider;
    std::unique_ptr<dse::CapabilityProvider> mCapabilityProvider;
    std::unique_ptr<dse::StateService> mStateService;

    bool findListener(const std::shared_ptr<IEventListener> &listener);
    void removeListener(const std::shared_ptr<IEventListener> &listener);

    void broadcastEventStart(int32_t eventId, int64_t timestamp, int32_t numParams,
                             const std::vector<int32_t> &intParams,
                             const std::optional<std::string> &extraStrings);

    void broadcastEventEnd(int32_t eventId, int64_t timestamp,
                           const std::optional<std::string> &extraStrings);

    static void onListenerDied(void *cookie);

    dse::SchedEvent ConvertToSchedEvent(const EventContext &ctx, int64_t timestamp,
                                        int32_t numParams, const std::vector<int32_t> &intParams);

    bool TryDseHandling(const dse::SchedEvent &event);
    int32_t FallbackToLegacy(const dse::SchedEvent &event, const std::string &packageName);
};

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor

#endif    // SERVICE_BRIDGE_H
