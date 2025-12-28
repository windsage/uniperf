/*
 * TranPerfEvent Vendor Implementation
 */

#define LOG_TAG "TranPerfEvent-Vendor"

#include "TranPerfEvent.h"
#include "PlatformAdapter.h"

#include <utils/Log.h>
#include <utils/Mutex.h>
#include <map>
#include <com_transsion_perfhub_flags.h>

namespace vendor {
namespace transsion {
namespace perfhub {

using android::AutoMutex;
using android::Mutex;

// ==================== Internal State ====================

// Event type to handle mapping
static std::map<int32_t, int32_t> sEventHandles;
static Mutex sEventLock;

// Debug flag
static constexpr bool DEBUG = false;

// ==================== Public API Implementation ====================

/**
 * Notify event start
 */
int32_t TranPerfEvent::notifyEventStart(int32_t eventType, int32_t eventParam) {
    if (!com::transsion::perfhub::flags::enable_tranperfhub()) {
        ALOGD("TranPerfHub disabled, skip event");
        return -1;
    }

    if (DEBUG) {
        ALOGD("notifyEventStart: eventType=%d, eventParam=%d", eventType, eventParam);
    }

    // Directly call PlatformAdapter (in-process call)
    PlatformAdapter& adapter = PlatformAdapter::getInstance();
    int32_t handle = adapter.acquirePerfLock(eventType, eventParam);

    if (handle <= 0) {
        ALOGE("Failed to acquire perf lock: eventType=%d", eventType);
        return -1;
    }

    // Record mapping
    {
        AutoMutex _l(sEventLock);

        // If this event already has a handle, release the old one first
        auto it = sEventHandles.find(eventType);
        if (it != sEventHandles.end()) {
            int32_t oldHandle = it->second;
            ALOGD("Releasing old handle: %d for eventType: %d", oldHandle, eventType);
            adapter.releasePerfLock(oldHandle);
        }

        // Update mapping
        sEventHandles[eventType] = handle;
    }

    if (DEBUG) {
        ALOGD("Event started: eventType=%d, handle=%d", eventType, handle);
    }

    return handle;
}

/**
 * Notify event end
 */
void TranPerfEvent::notifyEventEnd(int32_t eventType) {
    if (!com::transsion::perfhub::flags::enable_tranperfhub()) {
        return;
    }

    if (DEBUG) {
        ALOGD("notifyEventEnd: eventType=%d", eventType);
    }

    AutoMutex _l(sEventLock);

    // Find handle by event type
    auto it = sEventHandles.find(eventType);
    if (it == sEventHandles.end()) {
        ALOGW("No handle found for eventType: %d", eventType);
        return;
    }

    int32_t handle = it->second;

    // Release performance lock
    PlatformAdapter& adapter = PlatformAdapter::getInstance();
    adapter.releasePerfLock(handle);

    // Remove mapping
    sEventHandles.erase(it);

    if (DEBUG) {
        ALOGD("Event ended: eventType=%d, handle=%d", eventType, handle);
    }
}

/**
 * Release by handle
 */
void TranPerfEvent::releaseHandle(int32_t handle) {
    if (DEBUG) {
        ALOGD("releaseHandle: handle=%d", handle);
    }

    if (handle <= 0) {
        ALOGW("Invalid handle: %d", handle);
        return;
    }

    // Release performance lock
    PlatformAdapter& adapter = PlatformAdapter::getInstance();
    adapter.releasePerfLock(handle);

    // Remove from mapping (find by value)
    {
        AutoMutex _l(sEventLock);

        for (auto it = sEventHandles.begin(); it != sEventHandles.end(); ++it) {
            if (it->second == handle) {
                sEventHandles.erase(it);
                break;
            }
        }
    }

    if (DEBUG) {
        ALOGD("Handle released: %d", handle);
    }
}

} // namespace perfhub
} // namespace transsion
} // namespace vendor
