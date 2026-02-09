/*
 * JNI OnLoad entry point
 */

#define LOG_TAG "PerfEngine-JNI"

#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <utils/Log.h>

namespace android {
extern int register_com_transsion_perfengine_PerfEngine(JNIEnv *env);
}

using namespace android;

/**
 * JNI_OnLoad
 *
 * 当库被加载时由虚拟机自动调用
 */
extern "C" jint JNI_OnLoad(JavaVM *vm, void * /* reserved */) {
    JNIEnv *env = NULL;

    if (vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        ALOGE("Failed to get JNIEnv");
        return JNI_ERR;
    }

    // 注册 PerfEngine 的 JNI 方法
    if (register_com_transsion_perfengine_PerfEngine(env) != JNI_OK) {
        ALOGE("Failed to register PerfEngine JNI methods");
        return JNI_ERR;
    }

    ALOGD("PerfEngine JNI library loaded successfully");
    return JNI_VERSION_1_6;
}
