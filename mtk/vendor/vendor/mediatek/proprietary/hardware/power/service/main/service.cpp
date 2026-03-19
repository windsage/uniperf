/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#define LOG_TAG "mtkpower-service.mediatek"
#include <hidl/LegacySupport.h>
#include <unistd.h>
#include <pthread.h>
#include "PowerManager.h"
#include "libpowerhal_wrap.h"
#include "MtkPowerService.h"
#include "Power.h"
#include "power_timer.h"
#include "libpowercloud.h"
#include <android/binder_manager.h>
#include <android/binder_process.h>
//SPD: add for PerfEngine by chao.xu5 start
#include <dlfcn.h>
#include <cutils/properties.h>
#define PERFENGINE_ENABLE_PROPERTY "persist.odm.perfengine.feature.support"
//SPD: add for PerfEngine by chao.xu5 end
using aidl::android::hardware::power::impl::mediatek::Power;
using aidl::vendor::mediatek::hardware::mtkpower::MtkPowerService;
using ::android::hardware::registerPassthroughServiceImplementation;
using ::android::hardware::configureRpcThreadpool;
using ::android::hardware::joinRpcThreadpool;
using ::android::OK;
using ::android::status_t;
pthread_mutex_t g_mutex;
pthread_cond_t  g_cond;
bool powerd_done = false;
//SPD: add for PerfEngine by chao.xu5 start.
static void loadPerfEngine() {
    ALOGI("[PerfEngine] Loading libperfengine-adapter.so...");
    // 1. 加载动态库
    void *handle = dlopen("libperfengine-adapter.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        ALOGE("[PerfEngine] Failed to dlopen: %s", dlerror());
        return;
    }
    ALOGI("[PerfEngine] Library loaded successfully");
    // 2. 查找初始化函数
    typedef bool (*InitFunc)(void);
    InitFunc initFunc = reinterpret_cast<InitFunc>(dlsym(handle, "PerfEngine_Initialize"));
    if (!initFunc) {
        ALOGE("[PerfEngine] Failed to find PerfEngine_Initialize: %s", dlerror());
        dlclose(handle);
        return;
    }
    ALOGI("[PerfEngine] Found PerfEngine_Initialize");
    // 3. 调用初始化函数
    //    此函数会:
    //    - 创建 ServiceBridge
    //    - 初始化 PerfLockCaller (检测平台, dlsym 查找函数)
    //    - 加载 XML 配置 (XmlConfigParser)
    //    - 初始化参数映射器 (ParamMapper)
    //    - 注册 AIDL 服务到 ServiceManager
    bool success = initFunc();
    if (success) {
        ALOGI("[PerfEngine] Initialization SUCCESS");
    } else {
        ALOGE("[PerfEngine] Initialization FAILED");
        dlclose(handle);
    }
}
//SPD: add for PerfEngine by chao.xu5 end.
void* IPower_IMtkPower_Service(void *data)
{
    ALOGI("Start AIDL android.hardware.power.IPower");
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    std::shared_ptr<Power> vib_power = ndk::SharedRefBase::make<Power>();
    if(Power::descriptor == NULL) {
        ALOGE("IPower - descriptor is null!");
        return NULL;
    }
    const std::string instance_power = std::string() + Power::descriptor + "/default";
    binder_status_t status = AServiceManager_addService(vib_power->asBinder().get(), instance_power.c_str());
    if (status != STATUS_OK) {
        ALOGE("IPower - add service fail:%d", (int)status);
        return NULL;
    }
    ALOGI("Start AIDL vendor.mediatek.hardware.mtkpower.IMtkPowerService");
    std::shared_ptr<MtkPowerService> vib_mtkpower = ndk::SharedRefBase::make<MtkPowerService>();
    if(MtkPowerService::descriptor == NULL) {
        ALOGE("IPower - descriptor is null!");
        return NULL;
    }
    const std::string instance_mtkpower = std::string() + MtkPowerService::descriptor + "/default";
    status = AServiceManager_addService(vib_mtkpower->asBinder().get(), instance_mtkpower.c_str());
    if (status != STATUS_OK) {
        ALOGE("IMtkPowerService - add service fail:%d", (int)status);
        return NULL;
    }
    ALOGI("IPower & IMtkPowerService joinRpcThreadpool");
    //SPD: add for PerfEngine by chao.xu5 start.
    char value[PROPERTY_VALUE_MAX];
    property_get(PERFENGINE_ENABLE_PROPERTY, value, "1");
    if (strcmp(value, "1") == 0) {
        ALOGI("[PerfEngine] PerfEngine is enabled, loading adapter...");
        loadPerfEngine();
    }else{
        ALOGI("[PerfEngine] PerfEngine is disabled, skip loading adapter.");
    }
    //SPD: add for PerfEngine by chao.xu5 end.
    ABinderProcess_joinThreadPool();
    return NULL;
}
int main() {
    pthread_t serviceAidlThread;
    pthread_t timerThread;
    pthread_attr_t attr;
    //SPD: add cloudEnigne for powerhal by fan.feng1 at 20220916 start
    registCloudctlListener();
    //SPD: add cloudEnigne for powerhal by fan.feng1 at 20220916 end
    pthread_attr_init(&attr);
    pthread_create(&serviceAidlThread, &attr, IPower_IMtkPower_Service, (void*)&serviceAidlThread);
    pthread_setname_np(serviceAidlThread, "AIDL_IPower_IMtkPower");
    createTimerThread(&timerThread);
    pthread_join(serviceAidlThread, NULL);
    pthread_join(timerThread, NULL);
    //SPD: add cloudEnigne for powerhal by fan.feng1 at 20220916 start
    unregistCloudctlListener();
    //SPD: add cloudEnigne for powerhal by fan.feng1 at 20220916 end
    return 0;
}