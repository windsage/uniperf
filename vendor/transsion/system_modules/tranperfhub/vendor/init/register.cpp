#define LOG_TAG "TranPerfHub-Register"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <utils/Log.h>

#include "TranPerfHubService.h"

using namespace vendor::transsion::hardware::perfhub;

/**
 * 注册 AIDL 服务
 *
 * 由 perf-hal-service 或 mtkpower-service 调用
 */
extern "C" void TranPerfHub_RegisterService() {
    ALOGI("TranPerfHub_RegisterService called");

    // 创建服务实例
    std::shared_ptr<TranPerfHubService> service = ndk::SharedRefBase::make<TranPerfHubService>();

    // 注册服务
    const std::string instance = std::string() + ITranPerfHub::descriptor + "/default";

    binder_status_t status =
        AServiceManager_addService(service->asBinder().get(), instance.c_str());

    if (status == STATUS_OK) {
        ALOGI("TranPerfHub service registered: %s", instance.c_str());
    } else {
        ALOGE("Failed to register TranPerfHub service: %d", status);
    }
}

/**
 * 自动初始化 (LD_PRELOAD 方式)
 *
 * 当 libtranperfhub-vendor.so 被加载时自动执行
 */
__attribute__((constructor)) static void TranPerfHub_AutoInit() {
    ALOGI("TranPerfHub plugin loaded, auto-registering service...");
    TranPerfHub_RegisterService();
}
