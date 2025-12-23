/*
 * Copyright (C) 2011 The Android Open Source Project
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

#define LOG_TAG "ScnModule"
#include <log/log.h>
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

#include "jni.h"

#undef NELEM
#define NELEM(x) (sizeof(x)/sizeof(*(x)))

namespace android
{
static int inited = false;
static int first_inited = false;

#define LIB_FULL_NAME "libdetectscn.so"

static int (*scnmoduleNotifyAppisGame)(const char *, const char *, int32_t, int32_t) = NULL;

typedef int (*scnmodule_notify_app_is_game)(const char *, const char *, int32_t, int32_t);

static void load_detect_scn_api(void)
{
    void *handle, *func;

    handle = dlopen(LIB_FULL_NAME, RTLD_NOW);
    if (handle == NULL) {
        if (!first_inited)
            ALOGE("Can't load library: %s", dlerror());
        first_inited = true;
        return;
    }

    func = dlsym(handle, "ScnModule_notifyAppisGame");
    scnmoduleNotifyAppisGame = reinterpret_cast<scnmodule_notify_app_is_game>(func);

    if (scnmoduleNotifyAppisGame == NULL) {
        ALOGE("scnmoduleNotifyAppisGame error: %s\n", dlerror());
        dlclose(handle);
        return;
    }
    // only enter once
    inited = true;
}

static int
com_mediatek_scnmodule_scnmodule_nativeNotifyAppisGame(JNIEnv *env,
                   jobject /* thiz */, jstring packname, jstring actname, jint pid, jint status)
{
    int ret = -1;

    if (!inited)
        load_detect_scn_api();

    //ALOGI("NotifyAppState");
    if (scnmoduleNotifyAppisGame) {
        const char *nativePack = (packname) ? env->GetStringUTFChars(packname, 0) : NULL;
        const char *nativeAct = (actname) ? env->GetStringUTFChars(actname, 0) : NULL;

        ret = scnmoduleNotifyAppisGame(nativePack, nativeAct, pid, status);

        if (nativePack) {
            env->ReleaseStringUTFChars(packname, nativePack);
        }
        if (nativeAct) {
            env->ReleaseStringUTFChars(actname, nativeAct);
        }
    } else {
        ALOGE("[%s] scnmoduleNotifyAppisGame error: %s\n", __func__, dlerror());
    }

    return ret;
}

static JNINativeMethod sMethods[] = {
    {"nativeNotifyAppisGame",   "(Ljava/lang/String;Ljava/lang/String;II)I",
            (int *)com_mediatek_scnmodule_scnmodule_nativeNotifyAppisGame},
};

int register_com_mediatek_scnmodule_ScnModule(JNIEnv* env)
{
    jclass clazz = env->FindClass("com/mediatek/scnmodule/ScnModule");

    if (env->RegisterNatives(clazz, sMethods, NELEM(sMethods)) < 0) {
        ALOGE("RegisterNatives error");
        return JNI_ERR;
    }

    return JNI_OK;
}

}  // namespace android