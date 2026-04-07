/*
 * PerfEngine JNI Implementation
 *
 * 职责: 仅做 JNI 接口透传与参数转换；binder client 统一下沉到 TranPerfEvent
 */

#define LOG_TAG "TPE-JNI"

#include <android/binder_ibinder.h>
#include <android/binder_ibinder_jni.h>
#include <android_runtime/AndroidRuntime.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <utils/Log.h>

#include <optional>
#include <string>
#include <vector>

// #include "com_transsion_perfengine_flags.h"
#include <perfengine/TranPerfEvent.h>

// #define CHECK_FLAG() com::transsion::perfengine::flags::enable_perfengine()
#define CHECK_FLAG() (true)
using namespace android;

using android::transsion::TranPerfEvent;

// Debug 开关
static constexpr bool DEBUG = true;

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

    if (numParams < 0) {
        ALOGE("Invalid numParams: %d", numParams);
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

    // 转发到 Native client (binder 通信在 TranPerfEvent 内部统一处理)
    if (extraStr.has_value() && !extraStr->empty()) {
        TranPerfEvent::notifyEventStart(static_cast<int32_t>(eventId),
                                        static_cast<int64_t>(timestamp), params,
                                        std::vector<std::string>{*extraStr});
    } else {
        TranPerfEvent::notifyEventStart(static_cast<int32_t>(eventId),
                                        static_cast<int64_t>(timestamp), params);
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

    // 转换 extraStrings
    std::string extraStr;
    if (extraStrings != nullptr) {
        const char *str = env->GetStringUTFChars(extraStrings, nullptr);
        if (str != nullptr) {
            extraStr = std::string(str);
            env->ReleaseStringUTFChars(extraStrings, str);
        }
    }

    // 转发到 Native client
    if (!extraStr.empty()) {
        TranPerfEvent::notifyEventEnd(static_cast<int32_t>(eventId),
                                      static_cast<int64_t>(timestamp), extraStr);
    } else {
        TranPerfEvent::notifyEventEnd(static_cast<int32_t>(eventId),
                                      static_cast<int64_t>(timestamp));
    }

    if (DEBUG) {
        ALOGD("Event ended: eventId=%d", eventId);
    }
}

// ==================== JNI 方法实现 - Listener Registration ====================

/**
 * Register event listener with event filter
 */
static void nativeRegisterEventListener(JNIEnv *env, jclass clazz, jobject listenerBinder,
                                        jintArray eventFilter) {
    if (!CHECK_FLAG()) {
        if (DEBUG)
            ALOGD("PerfEngine disabled by flag");
        return;
    }
    if (DEBUG)
        ALOGD("nativeRegisterEventListener");

    if (listenerBinder == nullptr) {
        ALOGE("listenerBinder is null");
        jniThrowException(env, "java/lang/IllegalArgumentException", "Listener binder is null");
        return;
    }

    AIBinder *aiBinder = AIBinder_fromJavaBinder(env, listenerBinder);
    if (aiBinder == nullptr) {
        ALOGE("Failed to convert Java binder to AIBinder");
        jniThrowException(env, "java/lang/RuntimeException", "Failed to convert binder");
        return;
    }

    // 将 jintArray 转换为 std::vector<int32_t>
    std::vector<int32_t> filter;
    if (eventFilter != nullptr) {
        jsize len = env->GetArrayLength(eventFilter);
        if (len > 0) {
            jint *elements = env->GetIntArrayElements(eventFilter, nullptr);
            if (elements != nullptr) {
                filter.assign(elements, elements + len);
                env->ReleaseIntArrayElements(eventFilter, elements, JNI_ABORT);
            }
        }
    }

    // 转发到 Native client。注意：TranPerfEvent 会接管 aiBinder 的引用所有权
    int32_t ret = TranPerfEvent::registerEventListenerFromBinder(aiBinder, filter);
    if (ret != 0) {
        ALOGE("registerEventListenerFromBinder failed: ret=%d", ret);
        jniThrowException(env, "android/os/RemoteException", "registerEventListener failed");
        return;
    }

    if (DEBUG)
        ALOGD("Listener registered successfully, filter size=%zu", filter.size());
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

    // 将 Java Binder 转换为 Native AIBinder
    AIBinder *aiBinder = AIBinder_fromJavaBinder(env, listenerBinder);
    if (aiBinder == nullptr) {
        ALOGE("Failed to convert Java binder to AIBinder");
        jniThrowException(env, "java/lang/RuntimeException", "Failed to convert binder");
        return;
    }

    // 转发到 Native client。注意：TranPerfEvent 会接管 aiBinder 的引用所有权
    int32_t ret = TranPerfEvent::unregisterEventListenerFromBinder(aiBinder);
    if (ret != 0) {
        ALOGE("unregisterEventListenerFromBinder failed: ret=%d", ret);
        jniThrowException(env, "android/os/RemoteException", "unregisterEventListener failed");
        return;
    }

    if (DEBUG) {
        ALOGD("Listener unregistered successfully");
    }
}

// ==================== JNI 方法注册 ====================
static const JNINativeMethod gMethods[] = {
    {"nativeNotifyEventStart", "(IJI[ILjava/lang/String;)V", (void *)nativeNotifyEventStart},
    {"nativeNotifyEventEnd", "(IJLjava/lang/String;)V", (void *)nativeNotifyEventEnd},
    {"nativeRegisterEventListener", "(Landroid/os/IBinder;[I)V",
     (void *)nativeRegisterEventListener},
    {"nativeUnregisterEventListener", "(Landroid/os/IBinder;)V",
     (void *)nativeUnregisterEventListener},
};

namespace android {
/**
 * 注册 JNI 方法
 */
int register_com_transsion_usf_perfengine_TranPerfEvent(JNIEnv *env) {
    const char *className = "com/transsion/usf/perfengine/TranPerfEvent";
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

    ALOGD("JNI registered: %s (%zu methods)", className, (size_t)NELEM(gMethods));
    return JNI_OK;
}
}    // namespace android
