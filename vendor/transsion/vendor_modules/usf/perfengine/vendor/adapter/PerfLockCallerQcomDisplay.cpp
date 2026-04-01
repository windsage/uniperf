/*
 * PerfLockCallerQcomDisplay.cpp
 *
 * QCOM-only: IDisplayConfig AIDL fallback for queryDisplayFps().
 * This file is compiled ONLY on QCOM targets via soong_config.
 * MTK/UNISOC builds never touch this file.
 */

#include <aidl/vendor/qti/hardware/display/config/Attributes.h>
#include <aidl/vendor/qti/hardware/display/config/DisplayType.h>
#include <aidl/vendor/qti/hardware/display/config/IDisplayConfig.h>
#include <android/binder_manager.h>

#include "perf-utils/TranLog.h"

using aidl::vendor::qti::hardware::display::config::Attributes;
using aidl::vendor::qti::hardware::display::config::DisplayType;
using aidl::vendor::qti::hardware::display::config::IDisplayConfig;
using ::ndk::SpAIBinder;

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "TPE-QcomDisp"

namespace vendor {
namespace transsion {
namespace perfengine {

/**
 * Query display refresh rate via QCOM IDisplayConfig AIDL.
 * Called only from PerfLockCaller::queryDisplayFps() as fallback
 * when /data/vendor/perfd/current_fps is unavailable.
 */
int32_t queryDisplayFpsViaAidl() {
    static constexpr int32_t BASE_FPS = 60;
    static constexpr int64_t NSEC_TO_SEC = 1000000000LL;

    SpAIBinder binder(
        AServiceManager_checkService("vendor.qti.hardware.display.config.IDisplayConfig/default"));
    if (binder.get() == nullptr) {
        TLOGW("IDisplayConfig not available, using BASE_FPS=%d", BASE_FPS);
        return BASE_FPS;
    }

    auto displayConfig = IDisplayConfig::fromBinder(binder);
    if (displayConfig == nullptr) {
        TLOGW("fromBinder failed, using BASE_FPS=%d", BASE_FPS);
        return BASE_FPS;
    }

    int32_t dpyIndex = -1;
    displayConfig->getActiveConfig(DisplayType::PRIMARY, &dpyIndex);
    if (dpyIndex < 0) {
        TLOGW("getActiveConfig failed, using BASE_FPS=%d", BASE_FPS);
        return BASE_FPS;
    }

    Attributes dpyAttr;
    displayConfig->getDisplayAttributes(dpyIndex, DisplayType::PRIMARY, &dpyAttr);
    if (dpyAttr.vsyncPeriod <= 0) {
        TLOGW("invalid vsyncPeriod=%d, using BASE_FPS=%d", dpyAttr.vsyncPeriod, BASE_FPS);
        return BASE_FPS;
    }

    int32_t fps = static_cast<int32_t>(
        static_cast<float>(NSEC_TO_SEC) / static_cast<float>(dpyAttr.vsyncPeriod) + 0.5f);
    TLOGI("IDisplayConfig vsyncPeriod=%d ns -> %d Hz", dpyAttr.vsyncPeriod, fps);
    return fps;
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
