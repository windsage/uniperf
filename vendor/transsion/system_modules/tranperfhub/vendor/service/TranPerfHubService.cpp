#define LOG_TAG "TranPerfHub-Service"

#include "TranPerfHubService.h"

#include <android-base/logging.h>
#include <com_transsion_perfhub_flags.h>
#include <utils/Log.h>

#include "PlatformAdapter.h"

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

using android::AutoMutex;

TranPerfHubService::TranPerfHubService() {
    bool enabled = com::transsion::perfhub::flags::enable_tranperfhub();
    ALOGI("TranPerfHubService created, flag enabled: %s", enabled ? "true" : "false");
}

TranPerfHubService::~TranPerfHubService() {
    ALOGI("TranPerfHubService destroyed");
}

/**
 * Performance event start
 *
 * Flow:
 * 1. Call platform adapter to acquire performance lock
 * 2. Record eventType -> handle mapping
 * 3. Return handle
 */
ndk::ScopedAStatus TranPerfHubService::notifyEventStart(int32_t eventType, int32_t eventParam,
                                                        int32_t *_aidl_return) {
    if (!com::transsion::perfhub::flags::enable_tranperfhub()) {
        ALOGD("TranPerfHub disabled, skip event processing");
        return ndk::ScopedAStatus::ok();
    }

    ALOGD("notifyEventStart: eventType=%d, eventParam=%d", eventType, eventParam);

    // 1. Call platform adapter
    PlatformAdapter &adapter = PlatformAdapter::getInstance();
    int32_t handle = adapter.acquirePerfLock(eventType, eventParam);

    if (handle <= 0) {
        ALOGE("Failed to acquire perf lock: eventType=%d", eventType);
        *_aidl_return = -1;
        return ndk::ScopedAStatus::ok();
    }

    // 2. Record mapping
    {
        AutoMutex _l(mEventLock);

        // If this event already has a handle, release the old one first
        auto it = mEventHandles.find(eventType);
        if (it != mEventHandles.end()) {
            int32_t oldHandle = it->second;
            ALOGD("Releasing old handle: %d for eventType: %d", oldHandle, eventType);
            adapter.releasePerfLock(oldHandle);
        }

        // Update mapping
        mEventHandles[eventType] = handle;
    }

    ALOGD("Event started: eventType=%d, handle=%d", eventType, handle);

    *_aidl_return = handle;
    return ndk::ScopedAStatus::ok();
}

/**
 * Performance event end
 *
 * Flow:
 * 1. Find corresponding handle by eventType
 * 2. Call platform adapter to release performance lock
 * 3. Remove mapping record
 */
ndk::ScopedAStatus TranPerfHubService::notifyEventEnd(int32_t eventType) {
    if (!com::transsion::perfhub::flags::enable_tranperfhub()) {
        return ndk::ScopedAStatus::ok();
    }

    ALOGD("notifyEventEnd: eventType=%d", eventType);

    AutoMutex _l(mEventLock);

    // Find handle corresponding to event type
    auto it = mEventHandles.find(eventType);
    if (it == mEventHandles.end()) {
        ALOGW("No handle found for eventType: %d", eventType);
        return ndk::ScopedAStatus::ok();
    }

    int32_t handle = it->second;

    // Release performance lock
    PlatformAdapter &adapter = PlatformAdapter::getInstance();
    adapter.releasePerfLock(handle);

    // Remove mapping
    mEventHandles.erase(it);

    ALOGD("Event ended: eventType=%d, handle=%d", eventType, handle);

    return ndk::ScopedAStatus::ok();
}

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor
