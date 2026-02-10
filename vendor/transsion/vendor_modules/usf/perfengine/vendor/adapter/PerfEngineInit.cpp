// 初始化函数 - 供 mtkpower-service / perf-hal-service dlopen 调用

#include <android/binder_manager.h>
#include <android/binder_process.h>

#include "ServiceBridge.h"
#include "TranLog.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "PerfEngine-Init"

using vendor::transsion::hardware::perfengine::ServiceBridge;

// 全局服务实例 (保持存活)
static std::shared_ptr<ServiceBridge> gServiceBridge;

/**
 * PerfEngine 初始化函数
 *
 * 此函数由 mtkpower-service 或 perf-hal-service 通过 dlsym 调用
 *
 * 职责:
 * 1. 创建 ServiceBridge 实例
 * 2. 注册 AIDL 服务到 ServiceManager
 * 3. 返回初始化结果
 *
 * @return true 成功, false 失败
 */
extern "C" bool PerfEngine_Initialize() {
    TLOGI("PerfEngine_Initialize called");

    // 1. 创建 ServiceBridge 实例
    gServiceBridge = ndk::SharedRefBase::make<ServiceBridge>();
    if (!gServiceBridge) {
        TLOGE("Failed to create ServiceBridge");
        return false;
    }

    // 2. 注册 AIDL 服务
    const std::string serviceName = std::string() + ServiceBridge::descriptor + "/default";
    binder_status_t status =
        AServiceManager_addService(gServiceBridge->asBinder().get(), serviceName.c_str());

    if (status != STATUS_OK) {
        TLOGE("Failed to register PerfEngine service: %d", status);
        gServiceBridge.reset();
        return false;
    }

    TLOGI("PerfEngine service registered: %s", serviceName.c_str());
    return true;
}

/**
 * PerfEngine 反初始化函数
 *
 * 注意: 通常不需要调用,因为服务生命周期与进程相同
 */
extern "C" void PerfEngine_Shutdown() {
    TLOGI("PerfEngine_Shutdown called");

    if (gServiceBridge) {
        gServiceBridge.reset();
        TLOGI("PerfEngine service destroyed");
    }
}
