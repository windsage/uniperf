/*
 * TranPerfHub JNI Implementation
 * 
 * 职责: 纯 AIDL 客户端，透传调用到 Vendor 层
 */

#define LOG_TAG "TranPerfHub-JNI"

#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>

#include <utils/Log.h>
#include <utils/Mutex.h>

// Vendor AIDL 接口
#include <aidl/vendor/transsion/hardware/perfhub/ITranPerfHub.h>
#include <android/binder_manager.h>

using namespace android;

using aidl::vendor::transsion::hardware::perfhub::ITranPerfHub;
using ::ndk::SpAIBinder;
using ::ndk::ScopedAStatus;

// ==================== 全局变量 ====================

// Vendor AIDL 服务
static std::shared_ptr<ITranPerfHub> gVendorService = nullptr;

// 互斥锁
static Mutex gServiceLock;

// Vendor 服务名称
static const char* VENDOR_SERVICE_NAME = 
    "vendor.transsion.hardware.perfhub.ITranPerfHub/default";

// Debug 开关
static constexpr bool DEBUG = false;

// ==================== AIDL 服务管理 ====================

/**
 * 获取 Vendor AIDL 服务
 */
static std::shared_ptr<ITranPerfHub> getVendorService() {
    AutoMutex _l(gServiceLock);
    
    if (gVendorService != nullptr) {
        return gVendorService;
    }
    
    // 获取 Vendor AIDL 服务
    SpAIBinder binder = SpAIBinder(
        AServiceManager_checkService(VENDOR_SERVICE_NAME));
    
    if (binder.get() == nullptr) {
        ALOGE("Failed to get vendor service: %s", VENDOR_SERVICE_NAME);
        return nullptr;
    }
    
    gVendorService = ITranPerfHub::fromBinder(binder);
    if (gVendorService == nullptr) {
        ALOGE("Failed to convert binder to ITranPerfHub");
        return nullptr;
    }
    
    if (DEBUG) {
        ALOGD("Vendor service connected");
    }
    
    return gVendorService;
}

/**
 * 重置服务 (服务挂掉时调用)
 */
static void resetVendorService() {
    AutoMutex _l(gServiceLock);
    gVendorService = nullptr;
}

// ==================== JNI 方法实现 ====================

/**
 * 初始化
 * 
 * Java 调用: TranPerfHub.nativeInit()
 */
static void nativeInit(JNIEnv* env, jclass clazz) {
    if (DEBUG) {
        ALOGD("nativeInit called");
    }
    
    // 预加载服务 (可选)
    getVendorService();
}

/**
 * 获取性能锁
 * 
 * Java 调用: TranPerfHub.nativeAcquirePerfLock(sceneId, param)
 * 
 * @param sceneId 场景ID
 * @param param 场景参数
 * @return handle (>0 成功, <=0 失败)
 */
static jint nativeAcquirePerfLock(JNIEnv* env, jclass clazz, 
                                   jint sceneId, jint param) {
    if (DEBUG) {
        ALOGD("nativeAcquirePerfLock: sceneId=%d, param=%d", sceneId, param);
    }
    
    // 获取 Vendor 服务
    std::shared_ptr<ITranPerfHub> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        return -1;
    }
    
    // 调用 Vendor AIDL 接口 (只传 sceneId 和 param)
    int32_t handle = -1;
    ScopedAStatus status = service->notifySceneStart(
        static_cast<int32_t>(sceneId), 
        static_cast<int32_t>(param), 
        &handle);
    
    if (!status.isOk()) {
        ALOGE("notifySceneStart failed: %s", status.getDescription().c_str());
        resetVendorService();
        return -1;
    }
    
    if (DEBUG) {
        ALOGD("Acquired perf lock: sceneId=%d, handle=%d", sceneId, handle);
    }
    
    return static_cast<jint>(handle);
}

/**
 * 释放性能锁
 * 
 * Java 调用: TranPerfHub.nativeReleasePerfLock(handle)
 * 
 * @param handle 性能锁句柄
 */
static void nativeReleasePerfLock(JNIEnv* env, jclass clazz, jint handle) {
    if (DEBUG) {
        ALOGD("nativeReleasePerfLock: handle=%d", handle);
    }
    
    if (handle <= 0) {
        ALOGW("Invalid handle: %d", handle);
        return;
    }
    
    // 获取 Vendor 服务
    std::shared_ptr<ITranPerfHub> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        return;
    }
    
    // 注意: AIDL 接口传的是 sceneId，不是 handle
    // 需要在 Vendor 层维护 handle -> sceneId 的反向映射
    // 或者修改接口设计
    
    // 这里先简化处理，假设 handle 就是 sceneId (后续优化)
    ScopedAStatus status = service->notifySceneEnd(handle);
    
    if (!status.isOk()) {
        ALOGE("notifySceneEnd failed: %s", status.getDescription().c_str());
        resetVendorService();
        return;
    }
    
    if (DEBUG) {
        ALOGD("Released perf lock: handle=%d", handle);
    }
}

// ==================== JNI 方法注册 ====================

static const JNINativeMethod gMethods[] = {
    {
        "nativeInit",
        "()V",
        (void*)nativeInit
    },
    {
        "nativeAcquirePerfLock",
        "(II)I",
        (void*)nativeAcquirePerfLock
    },
    {
        "nativeReleasePerfLock",
        "(I)V",
        (void*)nativeReleasePerfLock
    },
};

/**
 * 注册 JNI 方法
 */
int register_com_transsion_perfhub_TranPerfHub(JNIEnv* env) {
    const char* className = "com/transsion/perfhub/TranPerfHub";
    
    jclass clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Can't find class: %s", className);
        return JNI_ERR;
    }
    
    int result = env->RegisterNatives(clazz, gMethods, NELEM(gMethods));
    if (result != JNI_OK) {
        ALOGE("Failed to register native methods");
        return result;
    }
    
    ALOGD("JNI registered: %s", className);
    return JNI_OK;
}
