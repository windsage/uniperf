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
 * Java 调用: TranPerfHub.nativeAcquirePerfLock(eventType, eventParam)
 * 
 * @param eventType 事件类型
 * @param eventParam 事件参数
 * @return handle (>0 成功, <=0 失败)
 */
/**
 * 获取性能锁 (修改后)
 * 
 * 注意: AIDL 是异步的，无法返回真实 handle
 * 我们返回一个本地生成的伪 handle 用于 Java 层管理
 */
static jint nativeAcquirePerfLock(JNIEnv* env, jclass clazz, 
                                   jint eventType, jint eventParam) {
    if (DEBUG) {
        ALOGD("nativeAcquirePerfLock: eventType=%d, eventParam=%d", 
            eventType, eventParam);
    }
    
    // 获取 Vendor 服务
    std::shared_ptr<ITranPerfHub> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        return -1;
    }
    
    // 调用异步 AIDL 接口 (oneway, 立即返回)
    ScopedAStatus status = service->notifyEventStart(
        static_cast<int32_t>(eventType), 
        static_cast<int32_t>(eventParam));
    
    if (!status.isOk()) {
        ALOGE("notifyEventStart failed: %s", status.getDescription().c_str());
        resetVendorService();
        return -1;
    }
    
    // 返回 eventType 作为伪 handle
    // (Java 层用于管理，Vendor 层通过 eventType 查找真实 handle)
    if (DEBUG) {
        ALOGD("Event sent: eventType=%d", eventType);
    }
    
    return static_cast<jint>(eventType);
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
    
    // handle 就是 eventType 参考上面
    int32_t eventType = static_cast<int32_t>(handle);
    
    // 调用异步 AIDL 接口
    ScopedAStatus status = service->notifyEventEnd(eventType);
    
    if (!status.isOk()) {
        ALOGE("notifyEventEnd failed: %s", status.getDescription().c_str());
        resetVendorService();
        return;
    }
    
    if (DEBUG) {
        ALOGD("Event ended: eventType=%d", eventType);
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
