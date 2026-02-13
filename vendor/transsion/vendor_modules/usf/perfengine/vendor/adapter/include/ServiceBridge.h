// vendor/transsion/vendor_modules/usf/perfengine/vendor/adapter/include/ServiceBridge.h
// AIDL 服务桥接层 - 重命名自 PerfEngineAdapter

#ifndef SERVICE_BRIDGE_H
#define SERVICE_BRIDGE_H

#include <aidl/vendor/transsion/hardware/perfengine/BnPerfEngine.h>
#include <aidl/vendor/transsion/hardware/perfengine/IEventListener.h>
#include <android/binder_auto_utils.h>
#include <utils/Mutex.h>

#include <map>
#include <memory>
#include <vector>

namespace vendor {
namespace transsion {
namespace perfengine {

using ::aidl::vendor::transsion::hardware::perfengine::BnPerfEngine;
using ::aidl::vendor::transsion::hardware::perfengine::IEventListener;
using ::android::Mutex;

class PerfLockCaller;

/**
 * ServiceBridge - AIDL 服务桥接层
 * 职责: AIDL 通信 + 监听器管理 + 事件分发
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

    ::ndk::ScopedAStatus registerEventListener(
        const std::shared_ptr<IEventListener> &listener) override;

    ::ndk::ScopedAStatus unregisterEventListener(
        const std::shared_ptr<IEventListener> &listener) override;

private:
    struct EventInfo {
        int32_t platformHandle;
        int64_t startTime;
        std::string packageName;
    };

    std::map<int32_t, EventInfo> mActiveEvents;
    Mutex mEventLock;

    std::unique_ptr<PerfLockCaller> mPerfLockCaller;

    struct ListenerInfo {
        std::shared_ptr<IEventListener> listener;
        ::ndk::ScopedAIBinder_DeathRecipient deathRecipient;

        ListenerInfo(std::shared_ptr<IEventListener> l, ::ndk::ScopedAIBinder_DeathRecipient dr)
            : listener(std::move(l)), deathRecipient(std::move(dr)) {}
    };

    std::vector<ListenerInfo> mListeners;
    Mutex mListenerLock;

    bool findListener(const std::shared_ptr<IEventListener> &listener);
    void removeListener(const std::shared_ptr<IEventListener> &listener);

    void broadcastEventStart(int32_t eventId, int64_t timestamp, int32_t numParams,
                             const std::vector<int32_t> &intParams,
                             const std::optional<std::string> &extraStrings);

    void broadcastEventEnd(int32_t eventId, int64_t timestamp,
                           const std::optional<std::string> &extraStrings);

    static void onListenerDied(void *cookie);
};

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor

#endif    // SERVICE_BRIDGE_H
