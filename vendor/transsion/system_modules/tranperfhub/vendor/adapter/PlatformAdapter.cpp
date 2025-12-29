#define LOG_TAG "TranPerfHub-Adapter"

#include "PlatformAdapter.h"

#include <android-base/logging.h>
#include <cutils/properties.h>
#include <dlfcn.h>
#include <utils/Log.h>

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

// QCOM 函数指针类型
typedef int (*qcom_perf_lock_acq_t)(int handle, int duration, int list[], int numArgs);
typedef int (*qcom_perf_lock_rel_t)(int handle);

// MTK 函数指针类型
typedef int (*mtk_perf_lock_acq_t)(int handle, int duration, int list[], int numArgs, int pid,
                                   int tid);
typedef void (*mtk_perf_lock_rel_t)(int handle);

// 全局单例
static PlatformAdapter *sInstance = nullptr;

PlatformAdapter &PlatformAdapter::getInstance() {
    if (sInstance == nullptr) {
        sInstance = new PlatformAdapter();
    }
    return *sInstance;
}

PlatformAdapter::PlatformAdapter()
    : mPlatform(PLATFORM_UNKNOWN), mQcomLibHandle(nullptr), mMtkLibHandle(nullptr) {
    ALOGI("PlatformAdapter initializing...");

    // 检测平台
    mPlatform = detectPlatform();

    // 初始化平台
    if (mPlatform == PLATFORM_QCOM) {
        initQcom();
    } else if (mPlatform == PLATFORM_MTK) {
        initMtk();
    }
}

PlatformAdapter::~PlatformAdapter() {
    if (mQcomLibHandle) {
        dlclose(mQcomLibHandle);
    }
    if (mMtkLibHandle) {
        dlclose(mMtkLibHandle);
    }
}

/**
 * 检测平台
 */
PlatformAdapter::Platform PlatformAdapter::detectPlatform() {
    char platform[PROP_VALUE_MAX] = {0};
    property_get("ro.board.platform", platform, "");

    ALOGI("Detected board platform: %s", platform);

    if (strstr(platform, "qcom") || strstr(platform, "msm")) {
        ALOGI("Platform: QCOM");
        return PLATFORM_QCOM;
    } else if (strstr(platform, "mt")) {
        ALOGI("Platform: MTK");
        return PLATFORM_MTK;
    }

    ALOGW("Platform: UNKNOWN");
    return PLATFORM_UNKNOWN;
}

/**
 * 初始化 QCOM 平台
 */
bool PlatformAdapter::initQcom() {
    ALOGI("Initializing QCOM platform...");

    // 加载 QCOM Client 库
    mQcomLibHandle = dlopen("libqti-perfd-client.so", RTLD_NOW);
    if (!mQcomLibHandle) {
        ALOGE("Failed to load libqti-perfd-client.so: %s", dlerror());
        return false;
    }

    // 获取函数指针
    qcom_perf_lock_acq_t perf_lock_acq =
        (qcom_perf_lock_acq_t)dlsym(mQcomLibHandle, "perf_lock_acq");
    qcom_perf_lock_rel_t perf_lock_rel =
        (qcom_perf_lock_rel_t)dlsym(mQcomLibHandle, "perf_lock_rel");

    if (!perf_lock_acq || !perf_lock_rel) {
        ALOGE("Failed to get QCOM function pointers");
        dlclose(mQcomLibHandle);
        mQcomLibHandle = nullptr;
        return false;
    }

    ALOGI("QCOM platform initialized successfully");
    return true;
}

/**
 * 初始化 MTK 平台
 */
bool PlatformAdapter::initMtk() {
    ALOGI("Initializing MTK platform...");

    // 加载 MTK Client 库
    mMtkLibHandle = dlopen("libpowerhal.so", RTLD_NOW);
    if (!mMtkLibHandle) {
        ALOGE("Failed to load libpowerhal.so: %s", dlerror());
        return false;
    }

    // 获取函数指针
    mtk_perf_lock_acq_t perfLockAcq = (mtk_perf_lock_acq_t)dlsym(mMtkLibHandle, "perfLockAcq");
    mtk_perf_lock_rel_t perfLockRel = (mtk_perf_lock_rel_t)dlsym(mMtkLibHandle, "perfLockRel");

    if (!perfLockAcq || !perfLockRel) {
        ALOGE("Failed to get MTK function pointers");
        dlclose(mMtkLibHandle);
        mMtkLibHandle = nullptr;
        return false;
    }

    ALOGI("MTK platform initialized successfully");
    return true;
}

/**
 * 获取性能锁
 */
int32_t PlatformAdapter::acquirePerfLock(int32_t eventId, int32_t eventParam) {
    ALOGD("acquirePerfLock: eventId=%d, eventParam=%d", eventId, eventParam);

    if (mPlatform == PLATFORM_QCOM) {
        return qcomAcquirePerfLock(eventId, eventParam);
    } else if (mPlatform == PLATFORM_MTK) {
        return mtkAcquirePerfLock(eventId, eventParam);
    }

    ALOGE("Unsupported platform");
    return -1;
}

/**
 * 释放性能锁
 */
void PlatformAdapter::releasePerfLock(int32_t handle) {
    ALOGD("releasePerfLock: handle=%d", handle);

    if (mPlatform == PLATFORM_QCOM) {
        qcomReleasePerfLock(handle);
    } else if (mPlatform == PLATFORM_MTK) {
        mtkReleasePerfLock(handle);
    }
}

/**
 * QCOM 平台获取性能锁
 */
int32_t PlatformAdapter::qcomAcquirePerfLock(int32_t eventId, int32_t eventParam) {
    // TODO: Later implement parameter mapping (read from XML config)
    // For now use simple test parameters

    int list[4] = {
        0x40800000, 1400000,    // CPU Cluster0 Min Freq
        0x40800100, 1800000     // CPU Cluster1 Min Freq
    };
    int duration = 3000;    // 3 seconds

    qcom_perf_lock_acq_t perf_lock_acq =
        (qcom_perf_lock_acq_t)dlsym(mQcomLibHandle, "perf_lock_acq");

    if (!perf_lock_acq) {
        ALOGE("perf_lock_acq not found");
        return -1;
    }

    int handle = perf_lock_acq(0, duration, list, 4);
    ALOGD("QCOM perf_lock_acq returned handle: %d for eventId: %d", handle, eventId);

    return handle;
}

/**
 * QCOM 平台释放性能锁
 */
void PlatformAdapter::qcomReleasePerfLock(int32_t handle) {
    qcom_perf_lock_rel_t perf_lock_rel =
        (qcom_perf_lock_rel_t)dlsym(mQcomLibHandle, "perf_lock_rel");

    if (!perf_lock_rel) {
        ALOGE("perf_lock_rel not found");
        return;
    }

    perf_lock_rel(handle);
    ALOGD("QCOM perf_lock_rel called for handle: %d", handle);
}

/**
 * MTK 平台获取性能锁
 */
int32_t PlatformAdapter::mtkAcquirePerfLock(int32_t eventId, int32_t eventParam) {
    // TODO: 后续实现参数映射

    int list[4] = {
        0x00001100, 1400000,    // CPU Cluster0 Min Freq
        0x00001200, 1800000     // CPU Cluster1 Min Freq
    };
    int duration = 3000;

    mtk_perf_lock_acq_t perfLockAcq = (mtk_perf_lock_acq_t)dlsym(mMtkLibHandle, "perfLockAcq");

    if (!perfLockAcq) {
        ALOGE("perfLockAcq not found");
        return -1;
    }

    int handle = perfLockAcq(0, duration, list, 4, 0, 0);
    ALOGD("MTK perfLockAcq returned handle: %d", handle);

    return handle;
}

/**
 * MTK 平台释放性能锁
 */
void PlatformAdapter::mtkReleasePerfLock(int32_t handle) {
    mtk_perf_lock_rel_t perfLockRel = (mtk_perf_lock_rel_t)dlsym(mMtkLibHandle, "perfLockRel");

    if (!perfLockRel) {
        ALOGE("perfLockRel not found");
        return;
    }

    perfLockRel(handle);
    ALOGD("MTK perfLockRel called for handle: %d", handle);
}

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor
