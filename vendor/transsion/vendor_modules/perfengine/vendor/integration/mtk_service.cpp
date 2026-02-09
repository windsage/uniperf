// mtk/vendor/vendor/mediatek/proprietary/hardware/power/service/main/service.cpp

#define LOG_TAG "mtkpower-service.mediatek"

#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <dlfcn.h>    // add for PerfEngine by chao.xu5
#include <hidl/LegacySupport.h>
#include <pthread.h>
#include <unistd.h>

#include "MtkPowerService.h"
#include "Power.h"
#include "PowerManager.h"
#include "libpowercloud.h"
#include "libpowerhal_wrap.h"
#include "power_timer.h"

using aidl::android::hardware::power::impl::mediatek::Power;
using aidl::vendor::mediatek::hardware::mtkpower::MtkPowerService;

using ::android::OK;
using ::android::status_t;
using ::android::hardware::configureRpcThreadpool;
using ::android::hardware::joinRpcThreadpool;
using ::android::hardware::registerPassthroughServiceImplementation;

pthread_mutex_t g_mutex;
pthread_cond_t g_cond;
bool powerd_done = false;

// add for PerfEngine by chao.xu5 start.
static void loadPerfEngine() {
    ALOGI("[PerfEngine] Loading adapter...");

    void *handle = dlopen("libperfengine-adapter.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        ALOGE("[PerfEngine] dlopen failed: %s", dlerror());
        return;
    }

    typedef bool (*InitFunc)(void);
    InitFunc initFunc = reinterpret_cast<InitFunc>(dlsym(handle, "PerfEngine_Initialize"));
    if (!initFunc) {
        ALOGE("[PerfEngine] dlsym failed: %s", dlerror());
        dlclose(handle);
        return;
    }

    if (!initFunc()) {
        ALOGE("[PerfEngine] PerfEngine_Initialize failed");
        dlclose(handle);
        return;
    }

    ALOGI("[PerfEngine] Adapter loaded successfully");
}
// add for PerfEngine by chao.xu5 end.

void *IPower_IMtkPower_Service(void *data) {
    ALOGI("Start AIDL android.hardware.power.IPower");

    ABinderProcess_setThreadPoolMaxThreadCount(0);
    std::shared_ptr<Power> vib_power = ndk::SharedRefBase::make<Power>();

    if (Power::descriptor == NULL) {
        ALOGE("IPower - descriptor is null!");
        return NULL;
    }

    const std::string instance_power = std::string() + Power::descriptor + "/default";
    binder_status_t status =
        AServiceManager_addService(vib_power->asBinder().get(), instance_power.c_str());
    if (status != STATUS_OK) {
        ALOGE("IPower - add service fail:%d", (int)status);
        return NULL;
    }

    ALOGI("Start AIDL vendor.mediatek.hardware.mtkpower.IMtkPowerService");

    std::shared_ptr<MtkPowerService> vib_mtkpower = ndk::SharedRefBase::make<MtkPowerService>();

    if (MtkPowerService::descriptor == NULL) {
        ALOGE("IMtkPowerService - descriptor is null!");
        return NULL;
    }

    const std::string instance_mtkpower = std::string() + MtkPowerService::descriptor + "/default";
    status = AServiceManager_addService(vib_mtkpower->asBinder().get(), instance_mtkpower.c_str());
    if (status != STATUS_OK) {
        ALOGE("IMtkPowerService - add service fail:%d", (int)status);
        return NULL;
    }

    // add for PerfEngine by chao.xu5 start.
    loadPerfEngine();
    // add for PerfEngine by chao.xu5 end.

    ABinderProcess_joinRpcThreadpool();

    return NULL;
}

int main() {
    pthread_t tid;
    int rc;
    pthread_attr_t attr;
    void *ret_join;
    const char buf[] = "HEHE!!!!";
    int state;

    android::base::SetDefaultTag("mtkpower@1.0-service");
    android::base::SetMinimumLogSeverity(android::base::VERBOSE);

    pthread_mutex_init(&g_mutex, NULL);
    pthread_cond_init(&g_cond, NULL);

    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

    pthread_create(&tid, &attr, &mtkPowerManager, NULL);

    pthread_mutex_lock(&g_mutex);
    while (!powerd_done) {
        pthread_cond_wait(&g_cond, &g_mutex);
    }
    pthread_mutex_unlock(&g_mutex);

    pthread_attr_destroy(&attr);

    ALOGI("[mtkpower] start IPower_IMtkPower_Service service");
    IPower_IMtkPower_Service(NULL);

    ALOGI("mtkpower-service - join PowerManager thread....");
    rc = pthread_join(tid, &ret_join);
    ALOGI("mtkpower-service - joined with return value: %p and state: %d", ret_join, rc);

    return 0;
}
