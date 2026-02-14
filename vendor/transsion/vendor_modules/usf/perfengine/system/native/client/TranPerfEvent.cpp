/*
 * TranPerfEvent Native Implementation
 */

#define LOG_TAG "TPE-Event"

#include <aidl/vendor/transsion/hardware/perfengine/IEventListener.h>
#include <aidl/vendor/transsion/hardware/perfengine/IPerfEngine.h>
#include <android/binder_manager.h>
#include <perfengine/TranPerfEvent.h>
#include <time.h>
#include <utils/Log.h>

#include <sstream>

// ==================== Aconfig Flag Handling ====================

// System 和 Vendor 都使用 aconfig
// #include <com_transsion_perfengine_flags.h>
// #define CHECK_FLAG() (com::transsion::perfengine::flags::enable_perfengine())
#define CHECK_FLAG() (false)
// ==================== Constants ====================

static constexpr bool DEBUG = false;
static const char *SERVICE_NAME = "vendor.transsion.hardware.perfengine.IPerfEngine/default";

// ==================== AIDL Service Management ====================

using aidl::vendor::transsion::hardware::perfengine::IEventListener;
using aidl::vendor::transsion::hardware::perfengine::IPerfEngine;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

namespace android {
namespace transsion {

/**
 * Get AIDL service (lazy-loaded, cached)
 */
static std::shared_ptr<IPerfEngine> getService() {
    static std::shared_ptr<IPerfEngine> sService = nullptr;

    if (sService == nullptr) {
        const std::string instance = std::string() + IPerfEngine::descriptor + "/default";

        SpAIBinder binder(AServiceManager_checkService(instance.c_str()));
        if (binder.get() == nullptr) {
            ALOGE("Failed to get PerfEngine service: %s", SERVICE_NAME);
            return nullptr;
        }

        sService = IPerfEngine::fromBinder(binder);
        if (sService != nullptr && DEBUG) {
            ALOGD("PerfEngine service connected");
        }
    }

    return sService;
}

// ==================== Utility Methods ====================

/**
 * Get current timestamp in nanoseconds
 */
int64_t TranPerfEvent::now() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

/**
 * Join string array with '\x1F' separator
 */
std::string TranPerfEvent::joinStrings(const std::vector<std::string> &strings) {
    if (strings.empty()) {
        return "";
    }

    if (strings.size() == 1) {
        return strings[0];
    }

    std::ostringstream oss;
    for (size_t i = 0; i < strings.size(); i++) {
        if (i > 0) {
            oss << STRING_SEPARATOR;    // '\x1F'
        }
        oss << strings[i];
    }

    return oss.str();
}

// ==================== Internal AIDL Call ====================

/**
 * Internal implementation - all overloads route here
 */
int32_t TranPerfEvent::notifyEventStartInternal(int32_t eventId, int64_t timestamp,
                                                int32_t numParams,
                                                const std::vector<int32_t> &intParams,
                                                const std::string &extraStrings) {
    // Check feature flag
    if (!CHECK_FLAG()) {
        if (DEBUG) {
            ALOGD("PerfEngine disabled by flag");
        }
        return -1;
    }

    // Validate parameters
    if (numParams > 0 && static_cast<int32_t>(intParams.size()) != numParams) {
        ALOGE("Parameter count mismatch: expected=%d, actual=%zu", numParams, intParams.size());
        return -1;
    }

    // Get service
    auto service = getService();
    if (!service) {
        ALOGE("Service not available");
        return -1;
    }

    if (DEBUG) {
        ALOGD("notifyEventStart: eventId=%d, timestamp=%lld, numParams=%d, extraStrings=%s",
              eventId, (long long)timestamp, numParams, extraStrings.c_str());
    }

    // Call AIDL interface
    auto status = service->notifyEventStart(
        eventId, timestamp, numParams, intParams,
        extraStrings.empty() ? std::nullopt : std::make_optional(extraStrings));

    if (!status.isOk()) {
        ALOGE("AIDL call failed: %s", status.getMessage());
        return -1;
    }

    return 0;
}

// ==================== Event Start Overloads ====================

/**
 * Overload 1: eventId + timestamp
 */
int32_t TranPerfEvent::notifyEventStart(int32_t eventId, int64_t timestamp) {
    std::vector<int32_t> emptyParams;
    return notifyEventStartInternal(eventId, timestamp, 0, emptyParams, "");
}

/**
 * Overload 2: eventId + timestamp + string
 */
int32_t TranPerfEvent::notifyEventStart(int32_t eventId, int64_t timestamp,
                                        const std::string &extraString) {
    std::vector<int32_t> emptyParams;
    return notifyEventStartInternal(eventId, timestamp, 0, emptyParams, extraString);
}

/**
 * Overload 3: eventId + timestamp + string[]
 */
int32_t TranPerfEvent::notifyEventStart(int32_t eventId, int64_t timestamp,
                                        const std::vector<std::string> &stringParams) {
    std::string joinedStrings = joinStrings(stringParams);
    std::vector<int32_t> emptyParams;
    return notifyEventStartInternal(eventId, timestamp, 0, emptyParams, joinedStrings);
}

/**
 * Overload 4: eventId + timestamp + int[]
 */
int32_t TranPerfEvent::notifyEventStart(int32_t eventId, int64_t timestamp,
                                        const std::vector<int32_t> &intParams) {
    int32_t numParams = static_cast<int32_t>(intParams.size());
    return notifyEventStartInternal(eventId, timestamp, numParams, intParams, "");
}

/**
 * Overload 5: eventId + timestamp + int[] + string[]
 */
int32_t TranPerfEvent::notifyEventStart(int32_t eventId, int64_t timestamp,
                                        const std::vector<int32_t> &intParams,
                                        const std::vector<std::string> &stringParams) {
    int32_t numParams = static_cast<int32_t>(intParams.size());
    std::string joinedStrings = joinStrings(stringParams);
    return notifyEventStartInternal(eventId, timestamp, numParams, intParams, joinedStrings);
}

// ==================== Event End Methods ====================

/**
 * Notify event end (minimal)
 */
void TranPerfEvent::notifyEventEnd(int32_t eventId, int64_t timestamp) {
    notifyEventEnd(eventId, timestamp, "");
}

/**
 * Notify event end with string
 */
void TranPerfEvent::notifyEventEnd(int32_t eventId, int64_t timestamp,
                                   const std::string &extraString) {
    // Check feature flag
    if (!CHECK_FLAG()) {
        return;
    }

    // Get service
    auto service = getService();
    if (!service) {
        ALOGE("Service not available");
        return;
    }

    if (DEBUG) {
        ALOGD("notifyEventEnd: eventId=%d, timestamp=%lld, extraString=%s", eventId,
              (long long)timestamp, extraString.c_str());
    }

    // Call AIDL interface
    auto status = service->notifyEventEnd(
        eventId, timestamp, extraString.empty() ? std::nullopt : std::make_optional(extraString));

    if (!status.isOk()) {
        ALOGE("AIDL call failed: %s", status.getMessage());
    }
}

// ==================== Listener Registration Methods ====================

/**
 * Register event listener
 */
/**
 * Register event listener - subscribe to ALL events
 */
int32_t TranPerfEvent::registerEventListener(const std::shared_ptr<IEventListener> &listener) {
    return registerEventListener(listener, {});
}

/**
 * Register event listener with event filter
 */
int32_t TranPerfEvent::registerEventListener(const std::shared_ptr<IEventListener> &listener,
                                             const std::vector<int32_t> &eventFilter) {
    if (!CHECK_FLAG()) {
        if (DEBUG)
            ALOGD("PerfEngine disabled by flag");
        return -1;
    }
    if (!listener) {
        ALOGE("Listener is null");
        return -1;
    }

    auto service = getService();
    if (!service) {
        ALOGE("Service not available");
        return -1;
    }

    if (DEBUG) {
        ALOGD("registerEventListener: filter size=%zu", eventFilter.size());
    }

    auto status = service->registerEventListener(listener, eventFilter);
    if (!status.isOk()) {
        ALOGE("registerEventListener failed: %s", status.getMessage());
        return -1;
    }

    if (DEBUG)
        ALOGD("Listener registered successfully");
    return 0;
}

/**
 * Unregister event listener
 */
int32_t TranPerfEvent::unregisterEventListener(const std::shared_ptr<IEventListener> &listener) {
    // Check feature flag
    if (!CHECK_FLAG()) {
        if (DEBUG) {
            ALOGD("PerfEngine disabled by flag");
        }
        return -1;
    }

    // Validate parameter
    if (!listener) {
        ALOGE("Listener is null");
        return -1;
    }

    // Get service
    auto service = getService();
    if (!service) {
        ALOGE("Service not available");
        return -1;
    }

    if (DEBUG) {
        ALOGD("unregisterEventListener");
    }

    // Call AIDL interface
    auto status = service->unregisterEventListener(listener);

    if (!status.isOk()) {
        ALOGE("unregisterEventListener failed: %s", status.getMessage());
        return -1;
    }

    if (DEBUG) {
        ALOGD("Listener unregistered successfully");
    }

    return 0;
}

}    // namespace transsion
}    // namespace android
