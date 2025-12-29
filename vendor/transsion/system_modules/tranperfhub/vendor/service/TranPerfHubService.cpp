#define LOG_TAG "TranPerfHub-Service"

#include "TranPerfHubService.h"

#include <com_transsion_perfhub_flags.h>
#include <utils/Log.h>

#include "PlatformAdapter.h"
#include "StringUtils.h"

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

using android::AutoMutex;
using namespace vendor::transsion::perfhub::utils;

TranPerfHubService::TranPerfHubService() {
    bool enabled = com::transsion::perfhub::flags::enable_tranperfhub();
    ALOGI("TranPerfHubService created, flag enabled: %s", enabled ? "true" : "false");
}

TranPerfHubService::~TranPerfHubService() {
    ALOGI("TranPerfHubService destroyed");
}

/**
 * Notify event start
 */
ndk::ScopedAStatus TranPerfHubService::notifyEventStart(int32_t eventId, int64_t timestamp,
                                                        int32_t numParams,
                                                        const std::vector<int32_t> &intParams,
                                                        const std::string &extraStrings) {
    // Check flag
    if (!com::transsion::perfhub::flags::enable_tranperfhub()) {
        ALOGD("TranPerfHub disabled, skip event processing");
        return ndk::ScopedAStatus::ok();
    }

    // Record receive timestamp for latency analysis
    int64_t receiveTimestamp = android::elapsedRealtimeNano();
    int64_t latency = receiveTimestamp - timestamp;

    // Parse extraStrings
    std::vector<std::string> strings = splitStrings(extraStrings);

    ALOGD(
        "Event start: id=%d, timestamp=%lld, latency=%lld ns (%.2f ms), numParams=%d, "
        "numStrings=%zu",
        eventId, (long long)timestamp, (long long)latency, latency / 1000000.0, numParams,
        strings.size());

    // Warning if latency is too high
    if (latency > 5000000) {    // 5ms
        ALOGW("High latency detected: %lld ns (%.2f ms) for eventId=%d", (long long)latency,
              latency / 1000000.0, eventId);
    }

    // Extract common parameters
    std::string packageName = getStringAt(strings, 0);

    // Call PlatformAdapter
    PlatformAdapter &adapter = PlatformAdapter::getInstance();
    int32_t handle =
        adapter.acquirePerfLock(eventId, timestamp, numParams, intParams.data(), strings);

    if (handle <= 0) {
        ALOGE("Failed to acquire perf lock: eventId=%d", eventId);
        return ndk::ScopedAStatus::ok();
    }

    // Record mapping
    {
        AutoMutex _l(mEventLock);

        auto it = mEventHandles.find(eventId);
        if (it != mEventHandles.end()) {
            int32_t oldHandle = it->second;
            ALOGD("Releasing old handle: %d for eventId: %d", oldHandle, eventId);
            adapter.releasePerfLock(oldHandle);
        }

        mEventHandles[eventId] = handle;
    }

    ALOGD("Event started: eventId=%d, handle=%d", eventId, handle);

    return ndk::ScopedAStatus::ok();
}

/**
 * Notify event end
 */
ndk::ScopedAStatus TranPerfHubService::notifyEventEnd(int32_t eventId, int64_t timestamp,
                                                      const std::string &extraStrings) {
    if (!com::transsion::perfhub::flags::enable_tranperfhub()) {
        return ndk::ScopedAStatus::ok();
    }

    ALOGD("Event end: id=%d, timestamp=%lld", eventId, (long long)timestamp);

    AutoMutex _l(mEventLock);

    auto it = mEventHandles.find(eventId);
    if (it == mEventHandles.end()) {
        ALOGW("No handle found for eventId: %d", eventId);
        return ndk::ScopedAStatus::ok();
    }

    int32_t handle = it->second;

    PlatformAdapter &adapter = PlatformAdapter::getInstance();
    adapter.releasePerfLock(handle);

    mEventHandles.erase(it);

    ALOGD("Event ended: eventId=%d, handle=%d", eventId, handle);

    return ndk::ScopedAStatus::ok();
}

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor
