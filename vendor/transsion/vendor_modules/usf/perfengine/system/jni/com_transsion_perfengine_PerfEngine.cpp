/*
 * PerfEngine JNI Implementation
 *
 * 职责: AIDL 客户端，透传调用到 Vendor 层 PerfEngineAdapter
 */

#define LOG_TAG "PerfEngine-JNI"

#include <android_runtime/AndroidRuntime.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <utils/Log.h>
#include <utils/Mutex.h>

// Vendor AIDL 接口
#include <aidl/vendor/transsion/hardware/perfengine/IEventListener.h>
#include <aidl/vendor/transsion/hardware/perfengine/IPerfEngine.h>
#include <android/binder_ibinder.h>
#include <android/binder_ibinder_jni.h>
#include <android/binder_manager.h>

#include "com_transsion_perfengine_flags.h"

#define CHECK_FLAG() com::transsion::perfengine::flags::enable_perfengine()
using namespace android;

using aidl::vendor::transsion::hardware::perfengine::IEventListener;
using aidl::vendor::transsion::hardware::perfengine::IPerfEngine;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

// ==================== 全局变量 ====================

// Vendor AIDL 服务
static std::shared_ptr<IPerfEngine> gVendorService = nullptr;

// 互斥锁
static Mutex gServiceLock;

// Vendor 服务名称
static const char *VENDOR_SERVICE_NAME = "vendor.transsion.hardware.perfengine.IPerfEngine/default";

// Debug 开关
static constexpr bool DEBUG = false;

// ==================== AIDL 服务管理 ====================

/**
 * 获取 Vendor AIDL 服务
 */
static std::shared_ptr<IPerfEngine> getVendorService() {
    AutoMutex _l(gServiceLock);

    if (gVendorService != nullptr) {
        return gVendorService;
    }

    // 获取 Vendor AIDL 服务
    SpAIBinder binder = SpAIBinder(AServiceManager_checkService(VENDOR_SERVICE_NAME));

    if (binder.get() == nullptr) {
        ALOGE("Failed to get vendor service: %s", VENDOR_SERVICE_NAME);
        return nullptr;
    }

    gVendorService = IPerfEngine::fromBinder(binder);
    if (gVendorService == nullptr) {
        ALOGE("Failed to convert binder to IPerfEngine");
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
    ALOGW("Vendor service reset");
}

// ==================== JNI 方法实现 - Event Notification ====================

/**
 * Notify event start
 */
static void nativeNotifyEventStart(JNIEnv *env, jclass clazz, jint eventId, jlong timestamp,
                                   jint numParams, jintArray intParams, jstring extraStrings) {
    if (!CHECK_FLAG()) {
        if (DEBUG) {
            ALOGD("PerfEngine disabled by flag");
        }
        return;
    }
    if (DEBUG) {
        ALOGD("nativeNotifyEventStart: eventId=%d, timestamp=%lld, numParams=%d", eventId,
              (long long)timestamp, numParams);
    }

    // Validate parameters
    if (intParams == nullptr) {
        ALOGE("intParams is null");
        return;
    }

    jsize arrayLength = env->GetArrayLength(intParams);
    if (arrayLength != numParams) {
        ALOGE("Parameter count mismatch: expected=%d, actual=%d", numParams, arrayLength);
        return;
    }

    if (numParams < 1) {
        ALOGE("Invalid numParams: %d", numParams);
        return;
    }

    // 获取 Vendor 服务
    std::shared_ptr<IPerfEngine> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        return;
    }

    // 转换 intParams
    std::vector<int32_t> params;
    params.reserve(numParams);

    jint *paramsArray = env->GetIntArrayElements(intParams, nullptr);
    if (paramsArray != nullptr) {
        for (int i = 0; i < numParams; i++) {
            params.push_back(static_cast<int32_t>(paramsArray[i]));
        }
        env->ReleaseIntArrayElements(intParams, paramsArray, JNI_ABORT);
    } else {
        ALOGE("Failed to get intParams array");
        return;
    }

    // 转换 extraStrings
    std::optional<std::string> extraStr = std::nullopt;
    if (extraStrings != nullptr) {
        const char *str = env->GetStringUTFChars(extraStrings, nullptr);
        if (str != nullptr) {
            extraStr = std::string(str);
            env->ReleaseStringUTFChars(extraStrings, str);
        }
    }

    // 调用 AIDL 接口 (oneway, 不阻塞)
    ScopedAStatus status =
        service->notifyEventStart(eventId, timestamp, numParams, params, extraStr);

    if (!status.isOk()) {
        ALOGE("notifyEventStart failed: %s", status.getMessage());
        resetVendorService();
        return;
    }

    if (DEBUG) {
        ALOGD("Event started: eventId=%d", eventId);
    }
}

/**
 * Notify event end
 */
static void nativeNotifyEventEnd(JNIEnv *env, jclass clazz, jint eventId, jlong timestamp,
                                 jstring extraStrings) {
    if (!CHECK_FLAG()) {
        if (DEBUG) {
            ALOGD("PerfEngine disabled by flag");
        }
        return;
    }
    if (DEBUG) {
        ALOGD("nativeNotifyEventEnd: eventId=%d, timestamp=%lld", eventId, (long long)timestamp);
    }

    // 获取 Vendor 服务
    std::shared_ptr<IPerfEngine> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        return;
    }

    // 转换 extraStrings
    std::optional<std::string> extraStr = std::nullopt;
    if (extraStrings != nullptr) {
        const char *str = env->GetStringUTFChars(extraStrings, nullptr);
        if (str != nullptr) {
            extraStr = std::string(str);
            env->ReleaseStringUTFChars(extraStrings, str);
        }
    }

    // 调用 AIDL 接口 (oneway, 不阻塞)
    ScopedAStatus status = service->notifyEventEnd(eventId, timestamp, extraStr);

    if (!status.isOk()) {
        ALOGE("notifyEventEnd failed: %s", status.getMessage());
        resetVendorService();
        return;
    }

    if (DEBUG) {
        ALOGD("Event ended: eventId=%d", eventId);
    }
}

// ==================== JNI 方法实现 - Listener Registration ====================

/**
 * Register event listener
 */
static void nativeRegisterEventListener(JNIEnv *env, jclass clazz, jobject listenerBinder) {
    if (!CHECK_FLAG()) {
        if (DEBUG) {
            ALOGD("PerfEngine disabled by flag");
        }
        return;
    }
    if (DEBUG) {
        ALOGD("nativeRegisterEventListener");
    }

    // Validate parameter
    if (listenerBinder == nullptr) {
        ALOGE("listenerBinder is null");
        jniThrowException(env, "java/lang/IllegalArgumentException", "Listener binder is null");
        return;
    }

    // 获取 Vendor 服务
    std::shared_ptr<IPerfEngine> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        jniThrowException(env, "java/lang/RuntimeException", "Vendor service not available");
        return;
    }

    // 将 Java Binder 转换为 Native AIBinder
    AIBinder *aiBinder = AIBinder_fromJavaBinder(env, listenerBinder);
    if (aiBinder == nullptr) {
        ALOGE("Failed to convert Java binder to AIBinder");
        jniThrowException(env, "java/lang/RuntimeException", "Failed to convert binder");
        return;
    }

    // 转换为 IEventListener
    SpAIBinder spBinder(aiBinder);    // Takes ownership
    std::shared_ptr<IEventListener> listener = IEventListener::fromBinder(spBinder);

    if (listener == nullptr) {
        ALOGE("Failed to convert AIBinder to IEventListener");
        jniThrowException(env, "java/lang/RuntimeException", "Failed to convert to IEventListener");
        return;
    }

    // 调用 AIDL 接口注册监听器
    ScopedAStatus status = service->registerEventListener(listener);

    if (!status.isOk()) {
        ALOGE("registerEventListener failed: %s", status.getMessage());
        jniThrowException(env, "android/os/RemoteException", status.getMessage());
        return;
    }

    if (DEBUG) {
        ALOGD("Listener registered successfully");
    }
}

/**
 * Unregister event listener
 */
static void nativeUnregisterEventListener(JNIEnv *env, jclass clazz, jobject listenerBinder) {
    if (!CHECK_FLAG()) {
        if (DEBUG) {
            ALOGD("PerfEngine disabled by flag");
        }
        return;
    }
    if (DEBUG) {
        ALOGD("nativeUnregisterEventListener");
    }

    // Validate parameter
    if (listenerBinder == nullptr) {
        ALOGE("listenerBinder is null");
        jniThrowException(env, "java/lang/IllegalArgumentException", "Listener binder is null");
        return;
    }

    // 获取 Vendor 服务
    std::shared_ptr<IPerfEngine> service = getVendorService();
    if (service == nullptr) {
        ALOGE("Vendor service not available");
        jniThrowException(env, "java/lang/RuntimeException", "Vendor service not available");
        return;
    }

    // 将 Java Binder 转换为 Native AIBinder
    AIBinder *aiBinder = AIBinder_fromJavaBinder(env, listenerBinder);
    if (aiBinder == nullptr) {
        ALOGE("Failed to convert Java binder to AIBinder");
        jniThrowException(env, "java/lang/RuntimeException", "Failed to convert binder");
        return;
    }

    // 转换为 IEventListener
    SpAIBinder spBinder(aiBinder);    // Takes ownership
    std::shared_ptr<IEventListener> listener = IEventListener::fromBinder(spBinder);

    if (listener == nullptr) {
        ALOGE("Failed to convert AIBinder to IEventListener");
        jniThrowException(env, "java/lang/RuntimeException", "Failed to convert to IEventListener");
        return;
    }

    // 调用 AIDL 接口取消注册监听器
    ScopedAStatus status = service->unregisterEventListener(listener);

    if (!status.isOk()) {
        ALOGE("unregisterEventListener failed: %s", status.getMessage());
        jniThrowException(env, "android/os/RemoteException", status.getMessage());
        return;
    }

    if (DEBUG) {
        ALOGD("Listener unregistered successfully");
    }
}

// ==================== JNI 方法注册 ====================

static const JNINativeMethod gMethods[] = {
    // Event notification
    {"nativeNotifyEventStart", "(IJII[ILjava/lang/String;)V", (void *)nativeNotifyEventStart},
    {"nativeNotifyEventEnd", "(IJLjava/lang/String;)V", (void *)nativeNotifyEventEnd},

    // Listener registration
    {"nativeRegisterEventListener", "(Landroid/os/IBinder;)V", (void *)nativeRegisterEventListener},
    {"nativeUnregisterEventListener", "(Landroid/os/IBinder;)V",
     (void *)nativeUnregisterEventListener},
};

namespace android {
/**
 * 注册 JNI 方法
 */
int register_com_transsion_perfengine_PerfEngine(JNIEnv *env) {
    const char *className = "com/transsion/perfengine/PerfEngine";

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

    ALOGD("JNI registered: %s (%zu methods)", className, NELEM(gMethods));
    return JNI_OK;
}
}    // namespace android
