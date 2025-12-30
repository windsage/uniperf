#define LOG_TAG "TranPerfHub-Adapter"

#include "TranPerfHubAdapter.h"

#include <android-base/logging.h>
#include <utils/Trace.h>

#include "PlatformAdapter.h"

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

TranPerfHubAdapter::TranPerfHubAdapter() {
    ATRACE_CALL();
    LOG(INFO) << "TranPerfHub Adapter initializing...";

    // Create platform adapter
    mPlatformAdapter = std::make_unique<PlatformAdapter>();
    if (!mPlatformAdapter || !mPlatformAdapter->init()) {
        LOG(ERROR) << "Failed to initialize PlatformAdapter";
        mPlatformAdapter.reset();
        return;
    }

    LOG(INFO) << "TranPerfHub Adapter initialized successfully";
}

TranPerfHubAdapter::~TranPerfHubAdapter() {
    LOG(INFO) << "TranPerfHub Adapter destroyed";
}

::ndk::ScopedAStatus TranPerfHubAdapter::notifyEventStart(
    int32_t eventId, int64_t timestamp, int32_t numParams, const std::vector<int32_t> &intParams,
    const std::optional<std::string> &extraStrings) {
    ATRACE_NAME("TranPerfHub::notifyEventStart");

    if (!mPlatformAdapter) {
        LOG(ERROR) << "PlatformAdapter not initialized";
        return ::ndk::ScopedAStatus::ok();
    }

    // Validate parameters
    if (static_cast<int32_t>(intParams.size()) != numParams) {
        LOG(ERROR) << "Parameter count mismatch: expected=" << numParams
                   << ", actual=" << intParams.size();
        return ::ndk::ScopedAStatus::ok();
    }

    if (numParams < 1) {
        LOG(ERROR) << "Invalid parameter count: " << numParams;
        return ::ndk::ScopedAStatus::ok();
    }

    // Extract duration and package name
    int32_t duration = getDuration(intParams);
    std::string packageName = extraStrings.value_or("");

    LOG(INFO) << "notifyEventStart: eventId=" << eventId << ", duration=" << duration
              << ", pkg=" << packageName;

    // Acquire performance lock
    int32_t platformHandle =
        mPlatformAdapter->acquirePerfLock(eventId, duration, intParams, packageName);

    if (platformHandle < 0) {
        LOG(ERROR) << "Failed to acquire perf lock for event " << eventId;
        return ::ndk::ScopedAStatus::ok();
    }

    // Save event info
    {
        std::lock_guard<Mutex> lock(mEventLock);

        // Check if event already exists
        auto it = mActiveEvents.find(eventId);
        if (it != mActiveEvents.end()) {
            LOG(WARNING) << "Event " << eventId << " already active, "
                         << "releasing old handle " << it->second.platformHandle;
            mPlatformAdapter->releasePerfLock(it->second.platformHandle);
        }

        // Store new event
        EventInfo info;
        info.platformHandle = platformHandle;
        info.startTime = timestamp;
        info.packageName = packageName;
        mActiveEvents[eventId] = info;
    }

    LOG(INFO) << "Event " << eventId << " started, platformHandle=" << platformHandle;

    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus TranPerfHubAdapter::notifyEventEnd(
    int32_t eventId, int64_t timestamp, const std::optional<std::string> &extraStrings) {
    ATRACE_NAME("TranPerfHub::notifyEventEnd");

    if (!mPlatformAdapter) {
        LOG(ERROR) << "PlatformAdapter not initialized";
        return ::ndk::ScopedAStatus::ok();
    }

    std::string packageName = extraStrings.value_or("");

    LOG(INFO) << "notifyEventEnd: eventId=" << eventId << ", pkg=" << packageName;

    // Find and release event
    std::lock_guard<Mutex> lock(mEventLock);

    auto it = mActiveEvents.find(eventId);
    if (it == mActiveEvents.end()) {
        LOG(WARNING) << "Event " << eventId << " not found in active events";
        return ::ndk::ScopedAStatus::ok();
    }

    // Release performance lock
    mPlatformAdapter->releasePerfLock(it->second.platformHandle);

    // Calculate event duration for logging
    int64_t duration = timestamp - it->second.startTime;
    LOG(INFO) << "Event " << eventId << " ended, duration=" << (duration / 1000000) << "ms";

    // Remove from active events
    mActiveEvents.erase(it);

    return ::ndk::ScopedAStatus::ok();
}

int32_t TranPerfHubAdapter::getDuration(const std::vector<int32_t> &intParams) const {
    // Duration is always the first parameter
    return intParams.empty() ? 0 : intParams[0];
}

void TranPerfHubAdapter::cleanupExpiredEvents() {
    // TODO: Implement periodic cleanup of expired events
    // This can be triggered by a timer if needed
}

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor
