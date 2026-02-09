// qcom/vendor/vendor/qcom/proprietary/perf-core/perf-hal/service.cpp
// QCOM Perf HAL Service - 集成 PerfEngine

#define LOG_TAG "vendor.qti.hardware.perf2-service"
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <cutils/properties.h>
#include <dlfcn.h>    // add for PerfEngine by chao.xu5

// add for cloud control by chao.xu5 at Jul 31th, 2025 start.
#include "LibPerfCloud.h"
// add for cloud control by chao.xu5 at Jul 31th, 2025 end.
#include "Perf.h"
#include "PerfLog.h"

using aidl::vendor::qti::hardware::perf2::Perf;

// add for PerfEngine by chao.xu5 start.
/**
 * 加载 PerfEngine 适配器
 *
 * 原理:
 * 1. dlopen 加载 libperfengine-adapter.so
 * 2. 该 so 内部会使用 dlsym(RTLD_DEFAULT) 查找 perfmodule_submit_request
 * 3. 因为该函数已经在 perf-hal-service 进程中(通过 libqti-perfd.so),所以能找到
 * 4. 调用 so 中的初始化函数注册 AIDL 服务
 */
static void loadPerfEngine() {
    QLOGI(LOG_TAG, "[PerfEngine] Loading adapter...");

    void *handle = dlopen("libperfengine-adapter.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        QLOGE(LOG_TAG, "[PerfEngine] dlopen failed: %s", dlerror());
        return;
    }

    // 查找初始化函数
    typedef bool (*InitFunc)(void);
    InitFunc initFunc = reinterpret_cast<InitFunc>(dlsym(handle, "PerfEngine_Initialize"));
    if (!initFunc) {
        QLOGE(LOG_TAG, "[PerfEngine] dlsym PerfEngine_Initialize failed: %s", dlerror());
        dlclose(handle);
        return;
    }

    // 调用初始化(会注册 AIDL 服务到 ServiceManager)
    if (!initFunc()) {
        QLOGE(LOG_TAG, "[PerfEngine] PerfEngine_Initialize failed");
        dlclose(handle);
        return;
    }

    // 不 dlclose,保持 so 加载
    QLOGI(LOG_TAG, "[PerfEngine] Adapter loaded successfully");
}
// add for PerfEngine by chao.xu5 end.

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    std::shared_ptr<Perf> ser = ndk::SharedRefBase::make<Perf>();
    binder_status_t status = STATUS_OK;
    // add for cloud control by chao.xu5 at Jul 31th, 2025 start.
    RegistCloudctlListener();
    // add for cloud control by chao.xu5 at Jul 31th, 2025 end.

    const std::string instance = std::string() + Perf::descriptor + "/default";
    if (ser != NULL) {
        status = AServiceManager_addService(ser->asBinder().get(), instance.c_str());
    } else {
        QLOGE(LOG_TAG, "Couldn't get perf service object");
        return EXIT_FAILURE;
    }

    CHECK_EQ(status, STATUS_OK);

    ABinderProcess_startThreadPool();
    QLOGE(LOG_TAG, "Registered IPerf HAL service success!");

    // add for PerfEngine by chao.xu5 start.
    // QCOM 原生服务注册完成后,加载 PerfEngine 适配器
    loadPerfEngine();
    // add for PerfEngine by chao.xu5 end.

    ABinderProcess_joinThreadPool();

    // add for cloud control by chao.xu5 at Jul 31th, 2025 start.
    UnregistCloudctlListener();
    // add for cloud control by chao.xu5 at Jul 31th, 2025 end.
    return EXIT_FAILURE;
}
