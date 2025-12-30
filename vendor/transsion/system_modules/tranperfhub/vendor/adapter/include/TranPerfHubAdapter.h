#ifndef TRANPERFHUB_ADAPTER_H
#define TRANPERFHUB_ADAPTER_H

#include <aidl/vendor/transsion/hardware/perfhub/BnTranPerfHub.h>
#include <utils/Mutex.h>

#include <map>
#include <memory>

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

using ::aidl::vendor::transsion::hardware::perfhub::BnTranPerfHub;
using ::android::Mutex;

class PlatformAdapter;

/**
 * TranPerfHub AIDL Service Implementation
 *
 * Responsibilities:
 *  - Implement ITranPerfHub AIDL interface
 *  - Manage event-to-handle mapping
 *  - Delegate platform-specific calls to PlatformAdapter
 *
 * Thread Safety:
 *  - AIDL methods are oneway (asynchronous)
 *  - Internal state protected by mutex
 */
class TranPerfHubAdapter : public BnTranPerfHub {
public:
    TranPerfHubAdapter();
    ~TranPerfHubAdapter() override;

    // AIDL interface implementation
    ::ndk::ScopedAStatus notifyEventStart(int32_t eventId, int64_t timestamp, int32_t numParams,
                                          const std::vector<int32_t> &intParams,
                                          const std::optional<std::string> &extraStrings) override;

    ::ndk::ScopedAStatus notifyEventEnd(int32_t eventId, int64_t timestamp,
                                        const std::optional<std::string> &extraStrings) override;

private:
    // Event handle management
    struct EventInfo {
        int32_t platformHandle;     // Platform-specific handle
        int64_t startTime;          // Event start timestamp
        std::string packageName;    // Associated package
    };

    std::map<int32_t, EventInfo> mActiveEvents;
    Mutex mEventLock;

    std::unique_ptr<PlatformAdapter> mPlatformAdapter;

    // Helper methods
    int32_t getDuration(const std::vector<int32_t> &intParams) const;
    void cleanupExpiredEvents();
};

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor

#endif    // TRANPERFHUB_ADAPTER_H
