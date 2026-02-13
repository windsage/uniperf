
#include "PerfEngineTypes.h"

#include <cutils/properties.h>

#include <cstring>

#include "TranLog.h"
#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "TPE-Plat"
namespace vendor {
namespace transsion {
namespace perfengine {

PlatformDetector &PlatformDetector::getInstance() {
    // C++11 magic statics: thread-safe, lazy initialization, zero overhead after first call
    static PlatformDetector instance;
    return instance;
}

PlatformDetector::PlatformDetector() {
    // Perform detection only once during construction
    mPlatform = performDetection();

    TLOGI("Platform initialized: %s", getPlatformName());
}

Platform PlatformDetector::performDetection() {
    char platform[PROPERTY_VALUE_MAX] = {0};
    char hardware[PROPERTY_VALUE_MAX] = {0};

    // Read critical properties (IPC cost: ~50μs each, but only once)
    property_get("ro.board.platform", platform, "");
    property_get("ro.hardware", hardware, "");

    TLOGI("Detection params: platform='%s', hardware='%s'", platform, hardware);

    // ==================== QCOM Detection (Priority 1) ====================
    // Check hardware first (most reliable for QCOM devices)
    if (strstr(hardware, "qcom") != nullptr) {
        return Platform::QCOM;
    }

    // Check platform patterns (ordered by popularity)
    // Use strncmp for fixed prefixes (faster than strstr)
    if (strncmp(platform, "sm", 2) == 0 ||     // Snapdragon 8xx series
        strncmp(platform, "msm", 3) == 0 ||    // Legacy Snapdragon
        strncmp(platform, "sdm", 3) == 0) {    // Snapdragon 6xx/7xx
        return Platform::QCOM;
    }

    // Check codenames (newer generation chips)
    if (strstr(platform, "volcano") != nullptr ||      // SM8750
        strstr(platform, "pineapple") != nullptr ||    // SM8650
        strstr(platform, "kalama") != nullptr ||       // SM8550
        strstr(platform, "taro") != nullptr ||         // SM8450
        strstr(platform, "lahaina") != nullptr ||      // SM8350
        strstr(platform, "kona") != nullptr) {         // SM8250
        return Platform::QCOM;
    }

    // ==================== MTK Detection (Priority 2) ====================
    // MTK uses consistent "mt" prefix
    if (strncmp(platform, "mt", 2) == 0 || strncmp(hardware, "mt", 2) == 0) {
        return Platform::MTK;
    }

    // ==================== UNISOC Detection (Priority 3) ====================
    // Check platform patterns (UNISOC uses multiple naming schemes)
    if (strncmp(platform, "ums", 3) == 0 ||    // UMS series
        strncmp(platform, "sp", 2) == 0) {     // Spreadtrum series
        return Platform::UNISOC;
    }

    // Check explicit brand name
    if (strstr(platform, "unisoc") != nullptr || strstr(hardware, "unisoc") != nullptr) {
        return Platform::UNISOC;
    }

    // ==================== Fallback ====================
    TLOGE("Platform detection failed - no matching pattern found");
    return Platform::UNKNOWN;
}

const char *PlatformDetector::getPlatformName() const {
    // Use static strings (no heap allocation)
    switch (mPlatform) {
        case Platform::QCOM:
            return "QCOM";
        case Platform::MTK:
            return "MTK";
        case Platform::UNISOC:
            return "UNISOC";
        case Platform::UNKNOWN:
        default:
            return "UNKNOWN";
    }
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
