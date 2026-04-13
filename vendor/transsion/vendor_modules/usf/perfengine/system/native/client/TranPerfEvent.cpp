/*
 * TranPerfEvent Native Implementation
 */

#define LOG_TAG "TPE-Event"

#include <aidl/vendor/transsion/hardware/perfengine/IEventListener.h>
#include <aidl/vendor/transsion/hardware/perfengine/IPerfEngine.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_status.h>
#include <perfengine/TranPerfEvent.h>
#include <time.h>
#include <utils/Log.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <sstream>
#include <utility>

// ==================== Aconfig Flag Handling ====================

// System 和 Vendor 都使用 aconfig
// #include <com_transsion_perfengine_flags.h>
// #define CHECK_FLAG() (com::transsion::perfengine::flags::enable_perfengine())
#define CHECK_FLAG() (true)
// ==================== Constants ====================

static constexpr bool DEBUG = true;
static const char *SERVICE_NAME = "vendor.transsion.hardware.perfengine.IPerfEngine/default";

// ==================== AIDL Service Management ====================

using aidl::vendor::transsion::hardware::perfengine::IEventListener;
using aidl::vendor::transsion::hardware::perfengine::IPerfEngine;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

namespace android {
namespace transsion {

namespace {

std::mutex g_service_mutex;
std::shared_ptr<IPerfEngine> g_service;

void clearServiceCache() {
    std::lock_guard<std::mutex> lock(g_service_mutex);
    std::shared_ptr<IPerfEngine> cleared;
    std::atomic_store_explicit(&g_service, cleared, std::memory_order_release);
}

bool isStaleBinderStatus(const ScopedAStatus &status) {
    const binder_status_t st = status.getStatus();
    return st == STATUS_DEAD_OBJECT; // st == STATUS_FAILED_TRANSACTION 
}

}    // namespace

/**
 * Get AIDL service (lazy-loaded, cached)
 */
static std::shared_ptr<IPerfEngine> getService() {
    std::shared_ptr<IPerfEngine> service =
        std::atomic_load_explicit(&g_service, std::memory_order_acquire);
    if (service != nullptr) {
        return service;
    }

    std::lock_guard<std::mutex> lock(g_service_mutex);
    service = std::atomic_load_explicit(&g_service, std::memory_order_acquire);
    if (service == nullptr) {
        const std::string instance = std::string() + IPerfEngine::descriptor + "/default";

        SpAIBinder binder(AServiceManager_checkService(instance.c_str()));
        if (binder.get() == nullptr) {
            ALOGE("Failed to get PerfEngine service: %s", SERVICE_NAME);
            return nullptr;
        }

        service = IPerfEngine::fromBinder(binder);
        if (service != nullptr && DEBUG) {
            ALOGD("PerfEngine service connected");
        }
        std::atomic_store_explicit(&g_service, service, std::memory_order_release);
    }

    return service;
}

namespace {

template <typename Call>
ScopedAStatus callServiceWithRetry(const Call &call) {
    for (int attempt = 0; attempt < 2; ++attempt) {
        auto service = getService();
        if (!service) {
            return ScopedAStatus::fromStatus(STATUS_NAME_NOT_FOUND);
        }

        ScopedAStatus status = call(service);
        if (status.isOk()) {
            return std::move(status);
        }

        if (attempt == 0 && isStaleBinderStatus(status)) {
            clearServiceCache();
            continue;
        }

        return std::move(status);
    }

    return ScopedAStatus::fromStatus(STATUS_UNKNOWN_ERROR);
}

}    // namespace

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
        ALOGD("notifyEventStart: eventId=0x%x, timestamp=%lld, numParams=%d, extraStrings=%s",
              eventId, (long long)timestamp, numParams, extraStrings.c_str());
    }

    ScopedAStatus status = callServiceWithRetry([&](const std::shared_ptr<IPerfEngine> &service) {
        return service->notifyEventStart(
            eventId, timestamp, numParams, intParams,
            extraStrings.empty() ? std::nullopt : std::make_optional(extraStrings));
    });

    if (status.getStatus() == STATUS_NAME_NOT_FOUND) {
        ALOGE("Service not available");
        return -1;
    }

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

    if (DEBUG) {
        ALOGD("notifyEventEnd: eventId=0x%x, timestamp=%lld, extraString=%s", eventId,
              (long long)timestamp, extraString.c_str());
    }

    ScopedAStatus status = callServiceWithRetry([&](const std::shared_ptr<IPerfEngine> &service) {
        return service->notifyEventEnd(
            eventId, timestamp,
            extraString.empty() ? std::nullopt : std::make_optional(extraString));
    });

    if (status.getStatus() == STATUS_NAME_NOT_FOUND) {
        ALOGE("Service not available");
        return;
    }

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

    if (DEBUG) {
        ALOGD("registerEventListener: filter size=%zu", eventFilter.size());
    }

    ScopedAStatus status = callServiceWithRetry([&](const std::shared_ptr<IPerfEngine> &service) {
        return service->registerEventListener(listener, eventFilter);
    });

    if (status.getStatus() == STATUS_NAME_NOT_FOUND) {
        ALOGE("Service not available");
        return -1;
    }

    if (!status.isOk()) {
        ALOGE("registerEventListener failed: %s", status.getMessage());
        return -1;
    }

    if (DEBUG) {
        ALOGD("Listener registered successfully");
    }
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

    if (DEBUG) {
        ALOGD("unregisterEventListener");
    }

    ScopedAStatus status = callServiceWithRetry([&](const std::shared_ptr<IPerfEngine> &service) {
        return service->unregisterEventListener(listener);
    });

    if (status.getStatus() == STATUS_NAME_NOT_FOUND) {
        ALOGE("Service not available");
        return -1;
    }

    if (!status.isOk()) {
        ALOGE("unregisterEventListener failed: %s", status.getMessage());
        return -1;
    }

    if (DEBUG) {
        ALOGD("Listener unregistered successfully");
    }
    return 0;
}

// ==================== Listener Registration (Binder Entry) ====================

int32_t TranPerfEvent::registerEventListenerFromBinder(AIBinder *listenerBinder) {
    return registerEventListenerFromBinder(listenerBinder, {});
}

int32_t TranPerfEvent::registerEventListenerFromBinder(AIBinder *listenerBinder,
                                                       const std::vector<int32_t> &eventFilter) {
    if (!CHECK_FLAG()) {
        if (DEBUG) {
            ALOGD("PerfEngine disabled by flag");
        }
        if (listenerBinder != nullptr) {
            AIBinder_decStrong(listenerBinder);
        }
        return -1;
    }

    if (listenerBinder == nullptr) {
        ALOGE("Listener binder is null");
        return -1;
    }

    // Adopt ownership of listenerBinder reference from caller.
    SpAIBinder spBinder(listenerBinder);
    std::shared_ptr<IEventListener> listener = IEventListener::fromBinder(spBinder);
    if (!listener) {
        ALOGE("Failed to convert binder to IEventListener");
        return -1;
    }

    return registerEventListener(listener, eventFilter);
}

int32_t TranPerfEvent::unregisterEventListenerFromBinder(AIBinder *listenerBinder) {
    if (!CHECK_FLAG()) {
        if (DEBUG) {
            ALOGD("PerfEngine disabled by flag");
        }
        if (listenerBinder != nullptr) {
            AIBinder_decStrong(listenerBinder);
        }
        return -1;
    }

    if (listenerBinder == nullptr) {
        ALOGE("Listener binder is null");
        return -1;
    }

    // Adopt ownership of listenerBinder reference from caller.
    SpAIBinder spBinder(listenerBinder);
    std::shared_ptr<IEventListener> listener = IEventListener::fromBinder(spBinder);
    if (!listener) {
        ALOGE("Failed to convert binder to IEventListener");
        return -1;
    }

    return unregisterEventListener(listener);
}

}    // namespace transsion
}    // namespace android
