#define LOG_TAG "TranPerfEvent-Vendor"

#include "TranPerfEvent.h"

#include <aidl/vendor/transsion/hardware/perfhub/ITranPerfHub.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <time.h>

using aidl::vendor::transsion::hardware::perfhub::ITranPerfHub;

namespace vendor {
namespace transsion {
namespace perfhub {

// 获取 AIDL 服务 (懒加载 + 缓存)
static std::shared_ptr<ITranPerfHub> getService() {
    static std::shared_ptr<ITranPerfHub> sService = nullptr;

    if (sService == nullptr) {
        const std::string instance = std::string() + ITranPerfHub::descriptor + "/default";

        ndk::SpAIBinder binder(AServiceManager_checkService(instance.c_str()));
        if (binder.get() == nullptr) {
            LOG(ERROR) << "Failed to get TranPerfHub service";
            return nullptr;
        }

        sService = ITranPerfHub::fromBinder(binder);
        LOG(INFO) << "TranPerfHub service connected";
    }

    return sService;
}

long TranPerfEvent::getCurrentTimestampNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

// ========================================
// AIDL 原始接口实现
// ========================================

void TranPerfEvent::notifyEventStart(int eventId, long timestamp, int numParams,
                                     const std::vector<int> &intParams,
                                     const std::string &extraStrings) {
    auto service = getService();
    if (!service) {
        LOG(ERROR) << "Service not available";
        return;
    }

    // 验证参数
    if (static_cast<int>(intParams.size()) != numParams) {
        LOG(ERROR) << "numParams mismatch: expected=" << numParams
                   << ", actual=" << intParams.size();
        return;
    }

    LOG(INFO) << "notifyEventStart: eventId=" << eventId << ", timestamp=" << timestamp
              << ", numParams=" << numParams << ", extraStrings=" << extraStrings;

    // 调用 AIDL 接口
    auto status = service->notifyEventStart(
        eventId, timestamp, numParams, intParams,
        extraStrings.empty() ? std::nullopt : std::make_optional(extraStrings));

    if (!status.isOk()) {
        LOG(ERROR) << "AIDL call failed: " << status.getMessage();
    }
}

void TranPerfEvent::notifyEventEnd(int eventId, long timestamp, const std::string &extraStrings) {
    auto service = getService();
    if (!service) {
        LOG(ERROR) << "Service not available";
        return;
    }

    LOG(INFO) << "notifyEventEnd: eventId=" << eventId << ", timestamp=" << timestamp
              << ", extraStrings=" << extraStrings;

    // 调用 AIDL 接口
    auto status = service->notifyEventEnd(
        eventId, timestamp, extraStrings.empty() ? std::nullopt : std::make_optional(extraStrings));

    if (!status.isOk()) {
        LOG(ERROR) << "AIDL call failed: " << status.getMessage();
    }
}

// ========================================
// 便捷接口实现
// ========================================

void TranPerfEvent::notifyEventStartAuto(int eventId, const std::vector<int> &intParams,
                                         const std::string &extraStrings) {
    long timestamp = getCurrentTimestampNs();
    int numParams = static_cast<int>(intParams.size());

    notifyEventStart(eventId, timestamp, numParams, intParams, extraStrings);
}

void TranPerfEvent::notifyEventEndAuto(int eventId, const std::string &extraStrings) {
    long timestamp = getCurrentTimestampNs();

    notifyEventEnd(eventId, timestamp, extraStrings);
}

}    // namespace perfhub
}    // namespace transsion
}    // namespace vendor
