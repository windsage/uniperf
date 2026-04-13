#include <android/binder_ibinder_platform.h>
#include <android/binder_interface_utils.h>
#include <android/binder_manager.h>
#include <sched.h>

#include "ServiceBridge.h"
#include "perf-utils/TranLog.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "TPE-Init"

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
    // AIDL BnPerfEngine 派生自 ndk::SharedRefBase：operator new 私有，必须通过 SharedRefBase::make
    // 创建；持有 std::shared_ptr 与 NDK Binder 服务生命周期一致（服务层仅做桥接与注册，决策在
    // DSE）。
    gServiceBridge = ndk::SharedRefBase::make<ServiceBridge>();
    if (!gServiceBridge) {
        TLOGE("Failed to create ServiceBridge");
        return false;
    }

    if (ServiceBridge::descriptor == nullptr) {
        TLOGE("ServiceBridge::descriptor is NULL!");
        return false;
    }

    // 2. 注册 AIDL 服务
    const std::string serviceName = std::string() + ServiceBridge::descriptor + "/default";
    TLOGI("Attempting to register service: %s", serviceName.c_str());

    auto existingService = AServiceManager_checkService(serviceName.c_str());
    if (existingService != nullptr) {
        TLOGW("Service already exists: %s", serviceName.c_str());
    }

    ndk::SpAIBinder serviceBinder = gServiceBridge->asBinder();
    if (serviceBinder.get() == nullptr) {
        TLOGE("serviceBinder is null");
        return false;
    }

    // PerfEngine 服务节点调度策略 SCHED_FIFO
    AIBinder_setMinSchedulerPolicy(serviceBinder.get(), SCHED_FIFO, 1);
    AIBinder_setInheritRt(serviceBinder.get(), true);

    binder_status_t status = AServiceManager_addService(serviceBinder.get(), serviceName.c_str());

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
 */
extern "C" void PerfEngine_Shutdown() {
    TLOGI("PerfEngine_Shutdown called");

    if (gServiceBridge) {
        gServiceBridge.reset();
        TLOGI("PerfEngine service destroyed");
    }
}
