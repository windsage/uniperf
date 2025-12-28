/*
 * TranPerfEvent Native Implementation
 */

#define LOG_TAG "TranPerfEvent-Native"

#include <perfhub/TranPerfEvent.h>
#include <utils/Log.h>
#include <utils/Mutex.h>
#include <utils/SortedVector.h>

// Vendor AIDL interface
#include <aidl/vendor/transsion/hardware/perfhub/ITranPerfHub.h>
#include <android/binder_manager.h>

using namespace android;

using aidl::vendor::transsion::hardware::perfhub::ITranPerfHub;
using ::ndk::SpAIBinder;
using ::ndk::ScopedAStatus;

namespace android {
namespace transsion {

// ==================== Internal State ====================

// Vendor AIDL service
static std::shared_ptr<ITranPerfHub> sVendorService = nullptr;

// Mutex for service access
static Mutex sServiceLock;

// Event handle mapping (eventType -> handle)
static SortedVector<std::pair<int32_t, int32_t>> sEventHandles;
static Mutex sEventLock;

// Vendor service name
static const char* VENDOR_SERVICE_NAME = 
    "vendor.transsion.hardware.perfhub.ITranPerfHub/default";

// Debug flag
static constexpr bool DEBUG = false;

// ==================== AIDL Service Management ====================

/**
 * Get Vendor AIDL service
 */
static std::shared_ptr<ITranPerfHub> getVendorService() {
    AutoMutex _l(sServiceLock);
    
    if (sVendorService != nullptr) {
        return sVendorService;
    }
    
    // Get Vendor AIDL service
    SpAIBinder binder = SpAIBinder(
        AServiceManager_checkService(VENDOR_SERVICE_NAME));
    
    if (binder.get() == nullptr) {
        ALOGE("Failed to get vendor service: %s", VENDOR_SERVICE_NAME);
        return nullptr;
    }
    
    sVendorService = ITranPerfHub::fromBinder(binder);
    if (sVendorService == nullptr) {
        ALOGE("Failed to convert binder to ITranPerfHub");
        return nullptr;
    }
    
    if (DEBUG) {
        ALOGD("Vendor service connected");
    }
    
    return sVendorService;
}

/**
 * Reset service (when service dies)
 */
static void resetVendorService() {
    AutoMutex _l(sServiceLock);
    sVendorService = nullptr;
}

// ==================== Public API Implementation ====================

/**
 * Notify event start
 */
int32_t TranPerfEvent::notifyEventStart(int32_t eventType, int32_t eventParam) {
    if (DEBUG) {
        ALOGD("notifyEventStart: eventType=%d, eventParam=%d", eventType, eventParam);
    }
    
    // Get Vendor service
    std::shared_ptr<ITranPerfHub> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        return -1;
    }
    
    // Call Vendor AIDL interface
    int32_t handle = -1;
    ScopedAStatus status = service->notifyEventStart(eventType, eventParam, &handle);
    
    if (!status.isOk()) {
        ALOGE("notifyEventStart failed: %s", status.getDescription().c_str());
        resetVendorService();
        return -1;
    }
    
    if (handle > 0) {
        // Record event type -> handle mapping
        AutoMutex _l(sEventLock);
        
        // Remove old handle if exists
        for (size_t i = 0; i < sEventHandles.size(); i++) {
            if (sEventHandles[i].first == eventType) {
                int32_t oldHandle = sEventHandles[i].second;
                ALOGD("Releasing old handle: %d for eventType: %d", oldHandle, eventType);
                
                // Release old handle (best effort, ignore errors)
                service->notifyEventEnd(eventType);
                
                sEventHandles.removeAt(i);
                break;
            }
        }
        
        // Add new mapping
        sEventHandles.add(std::make_pair(eventType, handle));
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
    if (DEBUG) {
        ALOGD("notifyEventEnd: eventType=%d", eventType);
    }
    
    // Get Vendor service
    std::shared_ptr<ITranPerfHub> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        return;
    }
    
    // Call Vendor AIDL interface
    ScopedAStatus status = service->notifyEventEnd(eventType);
    
    if (!status.isOk()) {
        ALOGE("notifyEventEnd failed: %s", status.getDescription().c_str());
        resetVendorService();
        return;
    }
    
    // Remove mapping
    {
        AutoMutex _l(sEventLock);
        
        for (size_t i = 0; i < sEventHandles.size(); i++) {
            if (sEventHandles[i].first == eventType) {
                sEventHandles.removeAt(i);
                break;
            }
        }
    }
    
    if (DEBUG) {
        ALOGD("Event ended: eventType=%d", eventType);
    }
}

} // namespace transsion
} // namespace android
