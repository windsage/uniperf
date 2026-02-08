#define LOG_TAG "PerfHub-Types"

#include "PerfHubTypes.h"

#include <cutils/properties.h>

#include <cstring>

#include "TranLog.h"

namespace vendor {
namespace transsion {
namespace perfhub {

// ====================== PlatformDetector Implementation ======================

Platform PlatformDetector::detect() {
    char platform[PROPERTY_VALUE_MAX] = {0};

    // Read ro.board.platform property
    property_get("ro.board.platform", platform, "");

    TLOGI("Platform detection: ro.board.platform = '%s'", platform);

    // QCOM platform patterns
    if (strstr(platform, "qcom") != nullptr || strstr(platform, "msm") != nullptr ||
        strstr(platform, "sm") != nullptr || strstr(platform, "sdm") != nullptr ||
        strstr(platform, "kona") != nullptr || strstr(platform, "lahaina") != nullptr ||
        strstr(platform, "taro") != nullptr || strstr(platform, "kalama") != nullptr ||
        strstr(platform, "volcano") != nullptr || strstr(platform, "pineapple") != nullptr ||
        strstr(platform, "cliffs") != nullptr) {
        TLOGI("Detected platform: QCOM");
        return Platform::QCOM;
    }

    // MTK platform patterns
    if (strstr(platform, "mt") != nullptr) {
        TLOGI("Detected platform: MTK");
        return Platform::MTK;
    }

    // UNISOC platform patterns
    if (strstr(platform, "unisoc") != nullptr || strstr(platform, "ums") != nullptr ||
        strstr(platform, "sp") != nullptr) {
        TLOGI("Detected platform: UNISOC");
        return Platform::UNISOC;
    }

    TLOGE("Failed to detect platform: '%s'", platform);
    return Platform::UNKNOWN;
}

const char *PlatformDetector::getPlatformName(Platform platform) {
    switch (platform) {
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

}    // namespace perfhub
}    // namespace transsion
}    // namespace vendor
