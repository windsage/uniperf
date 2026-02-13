#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <processgroup/sched_policy.h>
#include <sys/resource.h>

#include "ServiceBridge.h"
#include "TranLog.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "PerfEngine-Init"

using vendor::transsion::perfengine::ServiceBridge;

// 全局服务实例
static std::shared_ptr<ServiceBridge> gServiceBridge;

/**
 * PerfEngine 初始化函数
 *
 * 由 mtkpower-service 或 perf-hal-service 通过 dlsym 调用
 *
 * 完整流程:
 * 1. 创建 ServiceBridge 实例
 *    └─ ServiceBridge 构造函数:
 *       ├─ 创建 PerfLockCaller
 *       └─ PerfLockCaller::init():
 *          ├─ 检测平台 (QCOM/MTK/UNISOC)
 *          ├─ 初始化平台函数 (dlsym)
 *          ├─ 加载 XML 配置文件 (XmlConfigParser::loadConfig)
 *          └─ 初始化参数映射器 (ParamMapper::init)
 *
 * 2. 注册 AIDL 服务到 ServiceManager
 *
 * @return true 成功, false 失败
 */
extern "C" bool PerfEngine_Initialize() {
    TLOGI("PerfEngine_Initialize called");

    // 1. 创建 ServiceBridge 实例
    //    构造函数会自动初始化 PerfLockCaller, XmlConfigParser, ParamMapper
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

    // Elevate binder thread pool to RT-class for oneway dispatch latency
    set_sched_policy(0, SP_FOREGROUND);
    setpriority(PRIO_PROCESS, 0, -20);

    TLOGI("Binder thread pool priority elevated");
    return true;
}

/**
 * PerfEngine 反初始化函数
 */
extern "C" void PerfEngine_Shutdown() {
    TLOGI("PerfEngine_Shutdown called");

    if (gServiceBridge) {
        gServiceBridge.reset();
        TLOGI("PerfEngine service destroyed");
    }
}
