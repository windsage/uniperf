/*
 * Copyright (C) 2012 The Android Open Source Project
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

#define LOG_TAG "JNI_PQ"

#define MTK_LOG_ENABLE 1
#include <jni_pq.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <errno.h>
#include <utils/Log.h>
#include <utils/threads.h>
#include <cutils/log.h>
#include <cutils/properties.h>

#include "cust_color.h"
#include "cust_tdshp.h"
#include "cust_gamma.h"
#include <PQServiceCommon.h>
#include <vendor/mediatek/hardware/pq/2.3/IPictureQuality.h>
#include <aidl/vendor/mediatek/hardware/pq_aidl/IPictureQuality_AIDL.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

using android::hardware::hidl_array;
using vendor::mediatek::hardware::pq::V2_3::IPictureQuality;

using ::aidl::vendor::mediatek::hardware::pq_aidl::IPictureQuality_AIDL;
using ::aidl::vendor::mediatek::hardware::pq_aidl::Result;
using ::aidl::vendor::mediatek::hardware::pq_aidl::PQFeatureID;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getFeatureSwitch;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getExternalPanelNits;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getTuningField;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getESSLEDMinStep;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getESSOLEDMinStep;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getChameleonEnabled;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getChameleonStrength;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getBlueLightEnabled;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_getBlueLightStrength;
using ::aidl::vendor::mediatek::hardware::pq_aidl::parcelable_pqInt;
using ndk::ScopedAStatus;

#ifdef MTK_SYSTEM_AAL
#include "AALClient.h"
#endif

#define AAL_FUNCTION_DEFAULT                 "2"     // default is ESS only
#define DISP_CCORR_MATRIX_SIZE               (9)

#ifdef MTK_MIRAVISION_IMAGE_DC_SUPPORT
#include <PQDCHistogram.h>
#endif

using namespace android;

#define MIN_COLOR_WIN_SIZE (0x0)
#define MAX_COLOR_WIN_SIZE (0xFFFF)

#define UNUSED(expr) do { (void)(expr); } while (0)
#define JNI_PQ_CLASS_NAME "com/mediatek/pq/PictureQuality"

Mutex mLock;

static int CAPABILITY_MASK_COLOR       = 0x00000001;
static int CAPABILITY_MASK_SHARPNESS   = 0x00000002;
static int CAPABILITY_MASK_GAMMA       = 0x00000004;
static int CAPABILITY_MASK_DC          = 0x00000008;
#ifdef MTK_OD_SUPPORT
static int CAPABILITY_MASK_OD          = 0x00000010;
#endif
/////////////////////////////////////////////////////////////////////////////////
static int gAalSupport = -1;
static int gPQSupport = -1;
//SPD: add for PQE AR-SM-0004-021-001 by wenting.sheng 20250507 start
static int gPqeSupport = -1;
//SPD: add for PQE AR-SM-0004-021-001 by wenting.sheng 20250507 end

std::string getPQServiceName(void)
{
    if (IPictureQuality_AIDL::descriptor == nullptr) {
        std::string instance = std::string() + "/default";
        return instance;
    }
    else {
        std::string instance = std::string() + IPictureQuality_AIDL::descriptor + "/default";
        return instance;
    }
}

bool AalIsSupport(void)
{
    char value[PROP_VALUE_MAX];

    if (gAalSupport < 0) {
        if (__system_property_get("ro.vendor.mtk_aal_support", value) > 0) {
            gAalSupport = (int)strtoul(value, NULL, 0);
        }
    }

    return (gAalSupport > 0) ? true : false;
}

bool PQIsSupport(void)
{
    char value[PROP_VALUE_MAX] = {'\0'};

    if (gPQSupport < 0) {
        if (__system_property_get("ro.vendor.mtk_pq_support", value) > 0) {
            gPQSupport = (int)strtoul(value, NULL, 0);
        }
    }

    return (gPQSupport > 0) ? true : false;
}

//SPD: add for PQE AR-SM-0004-021-001 by wenting.sheng 20250507 start
//SPD: modify for PQE AR-SM-0004-021-001 by wenting.sheng 20250527 start
bool PqeIsSupport(void) {
    char value[PROP_VALUE_MAX] = {'\0'};
    bool enable = false;
    //SPD: modify for PQE AR-202507-002308 by wenting.sheng 20250721 start
    if (gPqeSupport < 0) {
        if (__system_property_get("ro.tr_perf.game.pq.support", value) > 0) {
            ALOGD("[JNI_PQ] tran_pq_support[%s]", value);
            enable = (strcmp(value, "1") == 0) || (strcmp(value, "true") == 0);
            gPqeSupport = enable ? 1 : 0;
        }
    }
    //SPD: modify for PQE AR-202507-002308 by wenting.sheng 20250721 end
    return (gPqeSupport > 0) ? true : false;

}
//SPD: modify for PQE AR-SM-0004-021-001 by wenting.sheng 20250527 end
//SPD: add for PQE AR-SM-0004-021-001 by wenting.sheng 20250507 end

static jint getCapability(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);

    UNUSED(env);
    UNUSED(thiz);

    int cap = (CAPABILITY_MASK_COLOR |
               CAPABILITY_MASK_SHARPNESS |
               CAPABILITY_MASK_GAMMA |
               CAPABILITY_MASK_DC);

#ifdef MTK_OD_SUPPORT
    cap |= CAPABILITY_MASK_OD;
#endif

    return cap;
}

static void setCameraPreviewMode(JNIEnv* env, jobject thiz, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setCameraPreviewMode(): MTK_PQ_SUPPORT disabled");
        return;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setDISPScenario(SCENARIO_ISP_PREVIEW, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setDISPScenario failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setDISPScenario(SCENARIO_ISP_PREVIEW, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setDISPScenario failed! (HIDL)");
        }
    } 
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);

    return;
}

static void setGalleryNormalMode(JNIEnv* env, jobject thiz, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setGalleryNormalMode(): MTK_PQ_SUPPORT disabled");
        return;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setDISPScenario(SCENARIO_PICTURE, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setDISPScenario failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setDISPScenario(SCENARIO_PICTURE, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setDISPScenario failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);

    return;
}

static void setVideoPlaybackMode(JNIEnv* env, jobject thiz, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setVideoPlaybackMode(): MTK_PQ_SUPPORT disabled");
        return ;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setDISPScenario(SCENARIO_VIDEO, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setDISPScenario failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setDISPScenario(SCENARIO_VIDEO, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setDISPScenario failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);

    return;
}

#ifdef MTK_MIRAVISION_IMAGE_DC_SUPPORT
static void Hist_set(JNIEnv* env, jobject obj, jint index, jint value)
{
    jclass clazz = env->FindClass(JNI_PQ_CLASS_NAME "$Hist");
    jmethodID setMethod = env->GetMethodID(clazz, "set", "(II)V");
    env->CallVoidMethod(obj, setMethod, index, value);

    env->DeleteLocalRef(clazz);
}
#endif

static void getDynamicContrastHistogram(JNIEnv* env, jclass clz, jbyteArray srcBuffer, jint srcWidth, jint srcHeight, jobject hist)
{
#ifdef MTK_MIRAVISION_IMAGE_DC_SUPPORT
    Mutex::Autolock autoLock(mLock);
    CPQDCHistogram *pDCHist = new CPQDCHistogram;
    DynCInput   input;
    DynCOutput  output;
    int i;

    input.pSrcFB = (unsigned char*)env->GetByteArrayElements(srcBuffer, 0);
    input.iWidth = srcWidth;
    input.iHeight = srcHeight;

    pDCHist->Main(input, &output);
    for (i = 0; i < DCHIST_INFO_NUM; i++)
    {
        Hist_set(env, hist, i, output.Info[i]);
    }

    env->ReleaseByteArrayElements(srcBuffer, (jbyte*)input.pSrcFB, 0);
    delete pDCHist;
#else
    ALOGE("[JNI_PQ] getDynamicContrastHistogram(), not supported!");

    UNUSED(env);
    UNUSED(srcBuffer);
    UNUSED(srcWidth);
    UNUSED(srcHeight);
    UNUSED(hist);
#endif
    UNUSED(clz);
}

static void Range_set(JNIEnv* env, jobject obj, jint min, jint max, jint defaultValue)
{
    jclass clazz = env->FindClass(JNI_PQ_CLASS_NAME "$Range");
    jmethodID setMethod = env->GetMethodID(clazz, "set", "(III)V");
    env->CallVoidMethod(obj, setMethod, min, max, defaultValue);
}

static jboolean enableColor(JNIEnv *env, jobject thiz, int isEnable);
static jboolean enableSharpness(JNIEnv *env, jobject thiz, int isEnable);
static jboolean enableDynamicContrast(JNIEnv *env, jobject thiz, int isEnable);
static jboolean enableGamma(JNIEnv *env, jobject thiz, int isEnable);
static jboolean enableOD(JNIEnv *env, jobject thiz, int isEnable);

/////////////////////////////////////////////////////////////////////////////////
static jboolean enablePQ(JNIEnv *env, jobject thiz, int isEnable)
{
    if (isEnable)
    {
        enableColor(env, thiz, 1);
        enableSharpness(env, thiz, 1);
        enableDynamicContrast(env, thiz, 1);
        enableOD(env, thiz, 1);
    }
    else
    {
        enableColor(env, thiz, 0);
        enableSharpness(env, thiz, 0);
        enableDynamicContrast(env, thiz, 0);
        enableOD(env, thiz, 0);
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

/////////////////////////////////////////////////////////////////////////////////
static jboolean enableColor(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableColor(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::DISPLAY_COLOR, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::DISPLAY_COLOR, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

/////////////////////////////////////////////////////////////////////////////////
static jboolean enableSharpness(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableSharpness(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::SHARPNESS, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::SHARPNESS, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

/////////////////////////////////////////////////////////////////////////////////
static jboolean enableDynamicContrast(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableDynamicContrast(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::DYNAMIC_CONTRAST, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::DYNAMIC_CONTRAST, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

static jboolean enableColorEffect(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableColorEffect(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(
                PQFeatureID::CONTENT_COLOR_VIDEO, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::CONTENT_COLOR_VIDEO, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

static jboolean enableDynamicSharpness(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableDynamicSharpness(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::DYNAMIC_SHARPNESS, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::DYNAMIC_SHARPNESS, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

static jboolean enableUltraResolution(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableUltraResolution(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::ULTRA_RESOLUTION, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::ULTRA_RESOLUTION, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

static jboolean enableISOAdaptiveSharpness(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableISOAdaptiveSharpness(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::ISO_ADAPTIVE_SHARPNESS, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::ISO_ADAPTIVE_SHARPNESS, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    } 
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

static jboolean enableContentColor(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableContentColor(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::CONTENT_COLOR, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::CONTENT_COLOR, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

/////////////////////////////////////////////////////////////////////////////////
static jboolean enableGamma(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableGamma(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::DISPLAY_GAMMA, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::DISPLAY_GAMMA, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

/////////////////////////////////////////////////////////////////////////////////
static jint getPictureMode(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    int index = -1;

    property_get(PQ_PIC_MODE_PROPERTY_STR, value, PQ_PIC_MODE_DEFAULT);
    index = static_cast<int>(strtol(value, nullptr, 10));
    ALOGD("[JNI_PQ] getPictureMode(), property get [%d]", index);

    UNUSED(env);
    UNUSED(thiz);

    return index;
}

/////////////////////////////////////////////////////////////////////////////////

static jboolean setPictureMode(JNIEnv *env, jobject thiz, int mode, int step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setPictureMode(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setPQMode(mode, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setPQMode failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setPQMode(mode, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setPQMode failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }


    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

/////////////////////////////////////////////////////////////////////////////////

//SPD: add for PQE LCQHLYYLR-65 by feng.an 20220410 start
//SPD: modify for PQE AR-SM-0004-021-001 by wenting.sheng 20250527 start
static jboolean setTranPictureMode(JNIEnv *env, jobject thiz, int mode, int sha, int sat, int con, int bri, int step)
{
    if(PqeIsSupport() == false) {
        ALOGD("[JNI_PQ] setTranPictureMode(): PQE_SUPPORT disabled");
        return JNI_FALSE;
    }
    else {
        if (PQIsSupport() == false) {
            ALOGE("[JNI_PQ] setTranPictureMode(): MTK_PQ_SUPPORT disabled");
            return JNI_FALSE;
        }

        Mutex::Autolock autoLock(mLock);
        sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
        std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

        if (service != nullptr)
        {
            ALOGD("[JNI_PQ] IPictureQuality_AIDL::setTranPQMode IPictureQuality_AIDL_service != nullptr !");
            Result result = Result::INVALID_STATE;
            ScopedAStatus ret = service->setTranPQMode(mode, sha, sat, con, bri, step, &result);
            if (!ret.isOk() || result != Result::OK)
            {
                ALOGE("[JNI_PQ] IPictureQuality_AIDL::setTranPQMode failed!");
                return JNI_FALSE;
            }
        }
        else if (service_hidl != nullptr)
        {
            ALOGD("[JNI_PQ] IPictureQuality_HIDL::setTranPQMode service_hidl != nullptr !");
            auto ret_hidl = service_hidl->setTranPQMode(mode, sha, sat, con, bri, step);

            if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                ALOGE("[JNI_PQ] IPictureQuality::setTranPQMode failed! (HIDL)");
                return JNI_FALSE;
            }
        }
        else
        {
            ALOGE("[JNI_PQ]setTranPictureMode  failed to get HW service");
            return JNI_FALSE;
        }
        UNUSED(env);
        UNUSED(thiz);
        return JNI_TRUE;
    }
}

static void nativeSetPQLogEnable(JNIEnv* env, jclass clz, jboolean isEnable)
{
    if(PqeIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetPQELogEnable(): TRAN_PQE_LOG_SUPPORT disabled");
    }
    else {
        if (PQIsSupport() == false) {
            ALOGE("[JNI_PQ] nativeSetPQLogEnable(): TRAN_PQ_LOG_SUPPORT disabled");
            return;
        }

        sp <IPictureQuality> service_hidl = IPictureQuality::tryGetService();
        std::shared_ptr <IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

        if (service != nullptr) {
            Result result = Result::INVALID_STATE;
            ScopedAStatus ret = service->setPQLogEnable(isEnable, &result);
            if (!ret.isOk() || result != Result::OK) {
                ALOGE("[JNI_PQ] IPictureQuality_AIDL::setPQLogEnable failed!");
                return;
            }
        } else if (service_hidl != nullptr) {
            auto ret_hidl = service_hidl->setPQLogEnable(isEnable);
            if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                ALOGE("[JNI_PQ] IPictureQuality::setPQLogEnable failed! (HIDL)");
                return;
            }
        } else {
            ALOGE("[JNI_PQ] failed to get HW service");
            return;
        }
    }
}
//SPD: modify for PQE AR-SM-0004-021-001 by wenting.sheng 20250527 end
//SPD: add for PQE LCQHLYYLR-65 by feng.an 20220410 end

//SDD:add for NFRFP-28747 by xiangbo.li 20241226 start
static void setFunction(JNIEnv *env, jobject thiz, int funcFlags, jboolean isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetFunction(): TRAN_PQ_LOG_SUPPORT disabled");
        return ;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        ALOGD("[JNI_PQ] IPictureQuality_AIDL::setFunction IPictureQuality_AIDL_service != nullptr !");
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFunction(funcFlags, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFunction failed!");
            return ;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }
}

static void setDisplayGamePQ(JNIEnv *env, jobject thiz, int featureFlag, int selTable, int featureParam2)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetDisplayGamePQ(): TRAN_PQ_LOG_SUPPORT disabled");
        return ;
    };

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        ALOGD("[JNI_PQ] IPictureQuality_AIDL::setDisplayGamePQ IPictureQuality_AIDL_service != nullptr !");
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setDisplayGamePQ(featureFlag, selTable, featureParam2, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setDisplayGamePQ failed!");
            return ;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }
}

static void setDisplayGamePQV2MPq(JNIEnv *env, jobject thiz, int pqId, int pqIdFlag, int featureFlag, int colorSelTable, int dreSelTable, int colorStep, int dreStep)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetDisplayGamePQV2MPq(): TRAN_PQ_LOG_SUPPORT disabled");
        return ;
    };

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        ALOGD("[JNI_PQ] IPictureQuality_AIDL::setDisplayGamePQV2MPq IPictureQuality_AIDL_service != nullptr !");
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setDisplayGamePQV2MPq(pqId, pqIdFlag, featureFlag, colorSelTable, dreSelTable, colorStep, dreStep, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setDisplayGamePQV2MPq failed!");
            return ;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }
}
//SDD:add for NFRFP-28747 by xiangbo.li 20241226 end

static jboolean setColorRegion(JNIEnv *env, jobject thiz, int isEnable, int startX, int startY, int endX, int endY)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setColorRegion(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    DISP_PQ_WIN_PARAM win_param;
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));
    if (service_hidl == nullptr && service == nullptr) {
        ALOGE("[JNI_PQ] failed to get HW service_hidl");
        return JNI_FALSE;
    }

    ALOGD("[JNI_PQ] setColorRegion(), en[%d], sX[%d], sY[%d], eX[%d], eY[%d]", isEnable, startX, startY, endX, endY);

    if (isEnable)
    {
        win_param.split_en = 1;
        win_param.start_x = startX;
        win_param.start_y = startY;
        win_param.end_x = endX;
        win_param.end_y = endY;
    }
    else
    {
        win_param.split_en = 0;
        win_param.start_x = MIN_COLOR_WIN_SIZE;
        win_param.start_y = MIN_COLOR_WIN_SIZE;
        win_param.end_x = MAX_COLOR_WIN_SIZE;
        win_param.end_y = MAX_COLOR_WIN_SIZE;
    }

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setColorRegion(win_param.split_en,win_param.start_x,win_param.start_y,win_param.end_x,win_param.end_y,&result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setColorRegion failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setColorRegion(win_param.split_en,win_param.start_x,win_param.start_y,win_param.end_x,win_param.end_y);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setColorRegion failed! (HIDL)");
            return JNI_FALSE;
        }
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

/////////////////////////////////////////////////////////////////////////////////
static void getContrastIndexRange(JNIEnv* env, jclass clz, jobject range)
{
    Mutex::Autolock autoLock(mLock);
    char value[] = PQ_CONTRAST_INDEX_DEFAULT;
    int index = static_cast<int>(strtol(value, nullptr, 10));
    Range_set(env, range, 0, PQ_CONTRAST_INDEX_RANGE_NUM - 1, index);

    UNUSED(clz);
}

static jint getContrastIndex(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    int index = -1;

    property_get(PQ_CONTRAST_PROPERTY_STR, value, PQ_CONTRAST_INDEX_DEFAULT);
    index = static_cast<int>(strtol(value, nullptr, 10));
    ALOGD("[JNI_PQ] getContrastIndex(), property get [%d]", index);

    UNUSED(env);
    UNUSED(thiz);

    return index;
}

static void setContrastIndex(JNIEnv *env, jobject thiz, int index, int step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setContrastIndex(): MTK_PQ_SUPPORT disabled");
        return;
    }

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setPQIndex(
                index, SCENARIO_PICTURE, TDSHP_FLAG_NORMAL, SET_PQ_CONTRAST, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setPQIndex failed!");
            return;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setPQIndex(index, SCENARIO_PICTURE, TDSHP_FLAG_NORMAL, SET_PQ_CONTRAST, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setPQIndex failed! (HIDL)");
            return;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);

}

/////////////////////////////////////////////////////////////////////////////////
static void getSaturationIndexRange(JNIEnv* env, jclass clz, jobject range)
{
    Mutex::Autolock autoLock(mLock);
    char value[] = PQ_GSAT_INDEX_DEFAULT;
    int index = static_cast<int>(strtol(value, nullptr, 10));
    Range_set(env, range, 0, PQ_GSAT_INDEX_RANGE_NUM - 1, index);

    UNUSED(clz);
}

static jint getSaturationIndex(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    int index = -1;

    property_get(PQ_GSAT_PROPERTY_STR, value, PQ_GSAT_INDEX_DEFAULT);
    index = static_cast<int>(strtol(value, nullptr, 10));
    ALOGD("[JNI_PQ] getSaturationIndex(), property get [%d]", index);

    UNUSED(env);
    UNUSED(thiz);

    return index;
}

static void setSaturationIndex(JNIEnv *env, jobject thiz, int index, int step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setSaturationIndex(): MTK_PQ_SUPPORT disabled");
        return;
    }

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    ALOGD("[JNI_PQ] setSaturationIndex...index[%d]", index);

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setPQIndex(
                index, SCENARIO_PICTURE, TDSHP_FLAG_NORMAL, SET_PQ_SAT_GAIN, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setPQIndex failed!");
            return;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setPQIndex(index, SCENARIO_PICTURE, TDSHP_FLAG_NORMAL, SET_PQ_SAT_GAIN, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setPQIndex failed! (HIDL)");
            return;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);
}

/////////////////////////////////////////////////////////////////////////////////
static void getPicBrightnessIndexRange(JNIEnv* env, jclass clz, jobject range)
{
    Mutex::Autolock autoLock(mLock);
    char value[] = PQ_PIC_BRIGHT_INDEX_DEFAULT;
    int index = static_cast<int>(strtol(value, nullptr, 10));
    Range_set(env, range, 0, PQ_PIC_BRIGHT_INDEX_RANGE_NUM - 1, index);

    UNUSED(clz);
}

static jint getPicBrightnessIndex(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    int index = -1;

    property_get(PQ_PIC_BRIGHT_PROPERTY_STR, value, PQ_PIC_BRIGHT_INDEX_DEFAULT);
    index = static_cast<int>(strtol(value, nullptr, 10));
    ALOGD("[JNI_PQ] getPicBrightnessIndex(), property get [%d]", index);

    UNUSED(env);
    UNUSED(thiz);

    return index;
}

static void setPicBrightnessIndex(JNIEnv *env, jobject thiz, int index, int step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setPicBrightnessIndex(): MTK_PQ_SUPPORT disabled");
        return;
    }

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    ALOGD("[JNI_PQ] setPicBrightnessIndex...index[%d]", index);

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setPQIndex(
                index, SCENARIO_PICTURE, TDSHP_FLAG_NORMAL, SET_PQ_BRIGHTNESS, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setPQIndex failed!");
            return;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setPQIndex(index, SCENARIO_PICTURE, TDSHP_FLAG_NORMAL, SET_PQ_BRIGHTNESS, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setPQIndex failed! (HIDL)");
            return;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);

}

/////////////////////////////////////////////////////////////////////////////////
static void getSharpnessIndexRange(JNIEnv* env, jclass clz, jobject range)
{
    Mutex::Autolock autoLock(mLock);
    char value[] = PQ_TDSHP_INDEX_DEFAULT;
    int index = static_cast<int>(strtol(value, nullptr, 10));
    Range_set(env, range, 0, PQ_TDSHP_INDEX_RANGE_NUM - 1, index);

    UNUSED(clz);
}

static jint getSharpnessIndex(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    int index = -1;

    property_get(PQ_TDSHP_PROPERTY_STR, value, PQ_TDSHP_STANDARD_DEFAULT);
    index = static_cast<int>(strtol(value, nullptr, 10));
    ALOGD("[JNI_PQ] getSharpnessIndex(), property get [%d]", index);

    UNUSED(env);
    UNUSED(thiz);

    return index;
}

static void setSharpnessIndex(JNIEnv *env, jobject thiz , int index)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setSharpnessIndex(): MTK_PQ_SUPPORT disabled");
        return;
    }

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    ALOGD("[JNI_PQ] setSharpnessIndex...index[%d]", index);

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setPQIndex(index, SCENARIO_PICTURE, TDSHP_FLAG_NORMAL,
                SET_PQ_SHP_GAIN, PQ_DEFAULT_TRANSITION_OFF_STEP, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setPQIndex failed!");
            return;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setPQIndex(index, SCENARIO_PICTURE, TDSHP_FLAG_NORMAL,
                SET_PQ_SHP_GAIN, PQ_DEFAULT_TRANSITION_OFF_STEP);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setPQIndex failed! (HIDL)");
            return;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);
}

/////////////////////////////////////////////////////////////////////////////////
static void getDynamicContrastIndexRange(JNIEnv* env, jclass clz, jobject range)
{
    Mutex::Autolock autoLock(mLock);
    char value[] = PQ_ADL_INDEX_DEFAULT;
    int index = static_cast<int>(strtol(value, nullptr, 10));
    Range_set(env, range, 0, PQ_ADL_INDEX_RANGE_NUM, index);

    UNUSED(clz);
}

static jint getDynamicContrastIndex(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    int index = -1;

    property_get(PQ_ADL_PROPERTY_STR, value, PQ_ADL_INDEX_DEFAULT);
    index = static_cast<int>(strtol(value, nullptr, 10));
    ALOGD("[JNI_PQ] getDynamicContrastIndex(), property get [%d]", index);

    UNUSED(env);
    UNUSED(thiz);

    return index;
}

static void setDynamicContrastIndex(JNIEnv *env, jobject thiz, int index)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setDynamicContrastIndex(): MTK_PQ_SUPPORT disabled");
        return;
    }

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::DYNAMIC_CONTRAST, index, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::DYNAMIC_CONTRAST, index);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);
}

static void getColorEffectIndexRange(JNIEnv* env, jclass clz, jobject range)
{
    Mutex::Autolock autoLock(mLock);
    char value[] = PQ_MDP_COLOR_EN_DEFAULT;
    int index = static_cast<int>(strtol(value, nullptr, 10));
    Range_set(env, range, 0, PQ_MDP_COLOR_EN_INDEX_RANGE_NUM, index);

    UNUSED(clz);
}

static jint getColorEffectIndex(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    int index = -1;

    property_get(PQ_MDP_COLOR_EN_STR, value, PQ_MDP_COLOR_EN_DEFAULT);
    index = static_cast<int>(strtol(value, nullptr, 10));
    ALOGD("[JNI_PQ] getColorEffectIndex(), property get [%d]", index);

    UNUSED(env);
    UNUSED(thiz);

    return index;
}

static void setColorEffectIndex(JNIEnv *env, jobject thiz, int index)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setColorEffectIndex(): MTK_PQ_SUPPORT disabled");
        return;
    }

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::CONTENT_COLOR_VIDEO, index, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::CONTENT_COLOR_VIDEO, index);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(thiz);
}
static void getGammaIndexRange(JNIEnv* env, jclass clz, jobject range)
{
    Mutex::Autolock autoLock(mLock);

    Range_set(env, range, 0, GAMMA_INDEX_MAX - 1, GAMMA_INDEX_DEFAULT);

    UNUSED(clz);
}


static void setGammaIndex(JNIEnv* env, jclass clz, jint index, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] setGammaIndex(): MTK_PQ_SUPPORT disabled");
        return;
    }

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setGammaIndex(index, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setGammaIndex failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setGammaIndex(index, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setGammaIndex failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }

    UNUSED(env);
    UNUSED(clz);
}

// OD
static jboolean enableOD(JNIEnv *env, jobject thiz, int isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] enableOD(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(
                PQFeatureID::DISPLAY_OVER_DRIVE, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::DISPLAY_OVER_DRIVE, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

//BSP: add for c3d ADJHBSY-4107 by meng.zhang 20220718 start
static jboolean nativeEnableC3D(JNIEnv* env, jclass clz, jboolean isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeEnableC3D(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(
                (PQFeatureID)PQFeatureID_C3D, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                (vendor::mediatek::hardware::pq::V2_0::PQFeatureID)PQFeatureID_C3D, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

    return JNI_TRUE;
}
static jboolean nativeIsC3DEnabled(JNIEnv* env, jclass clz)
{
    bool isEnabled = false;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeIsC3DEnabled(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getFeatureSwitch _aidl_return;
        ScopedAStatus ret = service->getFeatureSwitch((PQFeatureID)PQFeatureID_C3D, &_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            isEnabled = _aidl_return.value;
        } else {
            ALOGE("[JNI_PQ] nativeIsC3DEnabled() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getFeatureSwitch(
                (vendor::mediatek::hardware::pq::V2_0::PQFeatureID)PQFeatureID_C3D,
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, uint32_t value) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                isEnabled = value;
            } else {
                ALOGE("[JNI_PQ] nativeIsC3DEnabled() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

    return (isEnabled ? JNI_TRUE : JNI_FALSE);
}
//BSP: add for c3d ADJHBSY-4107 by meng.zhang 20220718 end

//TR: add for c3d ADJHBSY-4107 by xiaosong.wang 20220825 start
static jboolean nativeDeleteC3DCaliFile(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeDeleteC3DCaliFile(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->deleteC3dCaliFile(&result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::deleteC3dCaliFile failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->deleteC3dCaliFile( );
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] nativeDeleteC3DCaliFile failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }    

    UNUSED(env);
    UNUSED(clz);
    return JNI_TRUE;
}

static jint nativeGetC3dCaliFileSize(JNIEnv* env, jclass clz)
{
    int fileSize = 0;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetC3dCaliFileSize(): MTK_PQ_SUPPORT disabled");
        return 0;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
        std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        parcelable_pqInt _aidl_return;
        ScopedAStatus ret = service->getC3dCaliFileSize(&_aidl_return);
        fileSize = _aidl_return.retParam;
        result = _aidl_return.retval;
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getC3dCaliFileSize failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        fileSize = service_hidl->getC3dCaliFileSize( );
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return fileSize;
}

static jint nativeGetC3dCaliStatus(JNIEnv* env, jclass clz)
{
    int status = 0;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetC3dCaliStatus(): MTK_PQ_SUPPORT disabled");
        return 0;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        parcelable_pqInt _aidl_return;
        ScopedAStatus ret = service->getC3dCaliStatus(&_aidl_return);
        status = _aidl_return.retParam;
        result = _aidl_return.retval;
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getC3dCaliStatus failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        status = service_hidl->getC3dCaliStatus( );
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return status;
}

static jboolean nativeC3dCaliPass(JNIEnv* env, jclass clz, jint lcdIndex)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeC3dCaliPass(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->c3dCaliPass(lcdIndex, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::c3dCaliPass failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->c3dCaliPass( lcdIndex );
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] nativeC3dCaliPass failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }    

    UNUSED(env);
    UNUSED(clz);
    return JNI_TRUE;
}
//TR: add for c3d ADJHBSY-4107 by xiaosong.wang 20220825 end

//TR:add display ccm XLBSSB-2302 by xiaosongwang 20230202 start
static jint nativeGetCcorrCaliStatus(JNIEnv* env, jclass clz)
{
    int status = 0;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetCcorrCaliStatus(): MTK_PQ_SUPPORT disabled");
        return 0;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        parcelable_pqInt _aidl_return;
        ScopedAStatus ret = service->getCcorrCaliStatus(&_aidl_return);
        status = _aidl_return.retParam;
        result = _aidl_return.retval;
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getCcorrCaliStatus failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        status = service_hidl->getCcorrCaliStatus( );
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return status;
}

static jboolean nativeLoadCcorrCaliData(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeLoadCcorrCaliData(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->loadCcorrCaliData(&result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::loadCcorrCaliData failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->loadCcorrCaliData( );
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] nativeLoadCcorrCaliData failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return JNI_TRUE;
}

static jboolean nativeUseCcorrDefault(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeUseCcorrDefault(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->useCcorrDefault(&result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::useCcorrDefault failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->useCcorrDefault( );
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] nativeUseCcorrDefault failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return JNI_TRUE;
}

static jboolean nativeBackupCcorrCali(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeBackupCcorrCali(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->backupCcorrCali(&result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::backupCcorrCali failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->backupCcorrCali( );
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] nativeBackupCcorrCali failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return JNI_TRUE;
}

static jboolean nativeDeleteCcorrCaliFile(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeDeleteCcorrCaliFile(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->deleteCcorrCaliFile(&result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::deleteCcorrCaliFile failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->deleteCcorrCaliFile( );
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] nativeDeleteCcorrCaliFile failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return JNI_TRUE;
}

static jboolean nativeGetScreenStatus(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetScreenStatus(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->getScreenStatus(&result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getScreenStatus failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getScreenStatus( );
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] nativeGetScreenStatus failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return JNI_TRUE;
}

static jboolean nativeCcorrCaliPass(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeCcorrCaliPass(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->ccorrCaliPass(&result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::ccorrCaliPass failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->ccorrCaliPass( );
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] nativeCcorrCaliPass failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return JNI_TRUE;
}

static jint nativeGetLcdIndex(JNIEnv* env, jclass clz)
{
    int index = 0;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetLcdIndex(): MTK_PQ_SUPPORT disabled");
        return 0;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        parcelable_pqInt _aidl_return;
        ScopedAStatus ret = service->getLcdIndex(&_aidl_return);
        index = _aidl_return.retParam;
        result = _aidl_return.retval;
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getLcdIndex failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        index = service_hidl->getLcdIndex( );
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);
    return index;
}
//TR:add display ccm XLBSSB-2302 by xiaosongwang 20230202 end

static jboolean nativeEnableVideoHDR(JNIEnv* env, jclass clz, jboolean isEnable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeEnableVideoHDR(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(PQFeatureID::VIDEO_HDR, isEnable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::VIDEO_HDR, isEnable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

    return JNI_TRUE;
}


static jboolean nativeIsVideoHDREnabled(JNIEnv* env, jclass clz)
{
    bool isEnabled = false;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeIsVideoHDREnabled(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getFeatureSwitch _aidl_return;
        ScopedAStatus ret = service->getFeatureSwitch(PQFeatureID::VIDEO_HDR, &_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            isEnabled = _aidl_return.value;
        } else {
            ALOGE("[JNI_PQ] nativeIsVideoHDREnabled() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getFeatureSwitch(
                vendor::mediatek::hardware::pq::V2_0::PQFeatureID::VIDEO_HDR,
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, uint32_t value) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                isEnabled = value;
            } else {
                ALOGE("[JNI_PQ] nativeIsVideoHDREnabled() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

    return (isEnabled ? JNI_TRUE : JNI_FALSE);
}

static jboolean nativeEnableMdpDRE(JNIEnv *env, jobject thiz, jint enable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeEnableMdpDRE(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(
                (PQFeatureID)PQFeatureID_DRE, enable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                (vendor::mediatek::hardware::pq::V2_0::PQFeatureID)PQFeatureID_DRE, enable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

static jboolean nativeEnableMdpCCORR(JNIEnv *env, jobject thiz, jint enable)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeEnableMdpCCORR(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFeatureSwitch(
                (PQFeatureID)PQFeatureID_CCORR, enable, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFeatureSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFeatureSwitch(
                (vendor::mediatek::hardware::pq::V2_0::PQFeatureID)PQFeatureID_CCORR, enable);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFeatureSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }    

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

static jboolean nativeSetBlueLightStrength(JNIEnv* env, jclass clz, jint strength, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetBlueLightStrength(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setBlueLightStrength(strength, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setBlueLightStrength failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setBlueLightStrength(strength, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setBlueLightStrength failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

    return JNI_TRUE;
}


static jint nativeGetBlueLightStrength(JNIEnv* env, jclass clz)
{
    int32_t getStrength = 0;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetBlueLightStrength(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getBlueLightStrength _aidl_return;
        ScopedAStatus ret = service->getBlueLightStrength(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            getStrength = _aidl_return.strength;
        }
        else {
            ALOGE("[JNI_PQ] nativeGetBlueLightStrength() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getBlueLightStrength failed!");
            return 0;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getBlueLightStrength(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t strength) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                getStrength = strength;
            } else {
                ALOGE("[JNI_PQ] nativeGetBlueLightStrength() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getBlueLightStrength failed! (HIDL)");
            return 0;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

    return getStrength;
}


static jboolean nativeEnableBlueLight(JNIEnv* env, jclass clz, jboolean isEnable, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeEnableBlueLight(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->enableBlueLight(isEnable ? true : false, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::enableBlueLight failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->enableBlueLight(isEnable ? true : false, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::enableBlueLight failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }
    
    UNUSED(env);
    UNUSED(clz);

    return JNI_TRUE;
}


static jboolean nativeIsBlueLightEnabled(JNIEnv* env, jclass clz)
{
    bool getEnabled = false;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeIsBlueLightEnabled(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getBlueLightEnabled _aidl_return;
        ScopedAStatus ret = service->getBlueLightEnabled(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            getEnabled = _aidl_return.isEnabled;
        } else {
                ALOGE("[JNI_PQ] nativeIsBlueLightEnabled() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getBlueLightEnabled failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getBlueLightEnabled(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, bool isEnabled) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                getEnabled = isEnabled;
            } else {
                ALOGE("[JNI_PQ] nativeIsBlueLightEnabled() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getBlueLightEnabled failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }    

    UNUSED(env);
    UNUSED(clz);

    return (getEnabled ? JNI_TRUE : JNI_FALSE);
}

static jboolean nativeSetChameleonStrength(JNIEnv* env, jclass clz, jint strength, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetChameleonStrength(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setChameleonStrength(strength, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setChameleonStrength failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setChameleonStrength(strength, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setChameleonStrength failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }   

    return JNI_TRUE;

    UNUSED(env);
    UNUSED(clz);
}

static jint nativeGetChameleonStrength(JNIEnv* env, jclass clz)
{
    int32_t getStrength = 0;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetChameleonStrength(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getChameleonStrength _aidl_return;
        ScopedAStatus ret = service->getChameleonStrength(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            getStrength = _aidl_return.strength;
        }
        else {
            ALOGE("[JNI_PQ] nativeGetChameleonStrength() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getChameleonStrength failed!");
            return 0;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getChameleonStrength(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t strength) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                getStrength = strength;
            } else {
                ALOGE("[JNI_PQ] nativeGetChameleonStrength() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getChameleonStrength failed! (HIDL)");
            return 0;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return getStrength;

    UNUSED(env);
    UNUSED(clz);
}


static jboolean nativeEnableChameleon(JNIEnv* env, jclass clz, jboolean isEnable, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeEnableChameleon(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->enableChameleon(isEnable ? true : false, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::enableChameleon failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->enableChameleon(isEnable ? true : false, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::enableChameleon failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return JNI_TRUE;

    UNUSED(env);
    UNUSED(clz);
}


static jboolean nativeIsChameleonEnabled(JNIEnv* env, jclass clz)
{
    bool getEnabled = false;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeIsChameleonEnabled(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getChameleonEnabled _aidl_return;
        ScopedAStatus ret = service->getChameleonEnabled(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            getEnabled = _aidl_return.isEnabled;
        }
        else {
            ALOGE("[JNI_PQ] nativeIsChameleonEnabled() failed!");
        }

        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getChameleonEnabled failed!");
            return JNI_FALSE;
    }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getChameleonEnabled(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, bool isEnabled) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                getEnabled = isEnabled;
            } else {
                ALOGE("[JNI_PQ] nativeIsChameleonEnabled() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getChameleonEnabled failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return (getEnabled ? JNI_TRUE : JNI_FALSE);

    UNUSED(env);
    UNUSED(clz);
}

static jint nativeGetDefaultOffTransitionStep(JNIEnv* env, jclass clz)
{
    UNUSED(env);
    UNUSED(clz);

    return PQ_DEFAULT_TRANSITION_OFF_STEP;
}

static jint nativeGetDefaultOnTransitionStep(JNIEnv* env, jclass clz)
{
    UNUSED(env);
    UNUSED(clz);

    return PQ_DEFAULT_TRANSITION_ON_STEP;
}

static jboolean nativeSetGlobalPQSwitch(JNIEnv* env, jclass clz, jint globalPQSwitch)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetGlobalPQSwitch(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

#if defined(MTK_GLOBAL_PQ_SUPPORT)

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setGlobalPQSwitch(globalPQSwitch, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setGlobalPQSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setGlobalPQSwitch(globalPQSwitch);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setGlobalPQSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return JNI_TRUE;

#else

    UNUSED(globalPQSwitch);
    ALOGE("[JNI_PQ] nativeSetGlobalPQSwitch() not supported.");
    return JNI_FALSE;

#endif
}


static jint nativeGetGlobalPQSwitch(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetGlobalPQSwitch(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

#if defined(MTK_GLOBAL_PQ_SUPPORT)

    Mutex::Autolock autoLock(mLock);
    int32_t globalPQSwitch;
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    ALOGD("[JNI_PQ] globalPQSwitch = %d\n", globalPQSwitch);

    if (service != nullptr)
    {
        parcelable_getGlobalPQSwitch _aidl_return;
        ScopedAStatus ret = service->getGlobalPQSwitch(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            globalPQSwitch = _aidl_return.switch_value;
        }
        else {
            ALOGE("[JNI_PQ] nativeGetGlobalPQSwitch() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getGlobalPQSwitch failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getGlobalPQSwitch(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t switch_value) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                globalPQSwitch = switch_value;
            } else {
                ALOGE("[JNI_PQ] nativeGetGlobalPQSwitch() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getGlobalPQSwitch failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return globalPQSwitch;

#else

    ALOGE("[JNI_PQ] nativeGetGlobalPQSwitch() not supported.");
    return 0;

#endif
}

static jboolean nativeSetGlobalPQStrength(JNIEnv* env, jclass clz, jint globalPQStrength)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetGlobalPQStrength(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

#if defined(MTK_GLOBAL_PQ_SUPPORT)

    Mutex::Autolock autoLock(mLock);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setGlobalPQStrength(globalPQStrength, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setGlobalPQStrength failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setGlobalPQStrength(globalPQStrength);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setGlobalPQStrength failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return JNI_TRUE;

#else

    UNUSED(globalPQStrength);
    ALOGE("[JNI_PQ] nativeSetGlobalPQStrength() not supported.");
    return JNI_FALSE;

#endif
}


static jint nativeGetGlobalPQStrength(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetGlobalPQStrength(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

#if defined(MTK_GLOBAL_PQ_SUPPORT)

    Mutex::Autolock autoLock(mLock);
    uint32_t globalPQStrength;
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    ALOGD("[JNI_PQ] globalPQStrength = %d\n", globalPQStrength);

    if (service != nullptr)
    {
        parcelable_getGlobalPQStrength _aidl_return;
        ScopedAStatus ret = service->getGlobalPQStrength(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            globalPQStrength = _aidl_return.strength;
        }
        else {
            ALOGE("[JNI_PQ] nativeGetGlobalPQStrength() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getGlobalPQStrength failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getGlobalPQStrength(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t strength) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                globalPQStrength = strength;
            } else {
                ALOGE("[JNI_PQ] nativeGetGlobalPQStrength() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getGlobalPQStrength failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return globalPQStrength;

#else

    ALOGE("[JNI_PQ] nativeGetGlobalPQStrength() not supported.");
    return 0;

#endif
}


static jint nativeGetGlobalPQStrengthRange(JNIEnv* env, jclass clz)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetGlobalPQStrengthRange(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

#if defined(MTK_GLOBAL_PQ_SUPPORT)

    Mutex::Autolock autoLock(mLock);
    uint32_t globalPQStrengthRange;
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getGlobalPQStrengthRange _aidl_return;
        ScopedAStatus ret = service->getGlobalPQStrengthRange(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            globalPQStrengthRange = _aidl_return.strength_range;
        }
        else {
            ALOGE("[JNI_PQ] nativeGetGlobalPQStrength() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getGlobalPQStrength failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getGlobalPQStrengthRange(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t strength_range) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                globalPQStrengthRange = strength_range;
            } else {
                ALOGE("[JNI_PQ] nativeGetGlobalPQStrength() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getGlobalPQStrength failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    ALOGD("[JNI_PQ] globalPQStrengthRange = %d\n", globalPQStrengthRange);

    return globalPQStrengthRange;

#else

    ALOGE("[JNI_PQ] nativeGetGlobalPQStrengthRange() not supported.");
    return 0;

#endif
}

static jint nativeGetAALFunction(JNIEnv* env, jclass clz)
{
    UNUSED(env);
    UNUSED(clz);

    int mode = -1;

    if (AalIsSupport() == false) {
        ALOGE("nativeGetAALFunction(): MTK_AAL_SUPPORT disabled");
        return mode;
    }

    char value[PROPERTY_VALUE_MAX] = {'\0'};
    Mutex::Autolock autoLock(mLock);

#ifdef MTK_SYSTEM_AAL
    if (__system_property_get("persist.vendor.sys.aal.function", value) > 0) {
        mode = static_cast<int>(strtol(value, nullptr, 10));
    }
#else
    if (__system_property_get("persist.vendor.sys.mtkaal.function", value) > 0) {
        mode = static_cast<int>(strtol(value, nullptr, 10));
    }
#endif

    return mode;
}

static void nativeSetAALFunction(JNIEnv* env, jclass clz, jint func)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("nativeSetAALFunction(): MTK_AAL_SUPPORT disabled");
        return;
    }

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().setFunction(func);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetAALFunction(): MTK_PQ_SUPPORT disabled");
        return;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFunction(func, false, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFunction failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFunction(func, false);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFunction failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }    
#endif
}

static void nativeSetAALFunctionProperty(JNIEnv* env, jclass clz, jint func)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("nativeSetAALFunctionProperty(): MTK_AAL_SUPPORT disabled");
        return;
    }

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().setFunctionProperty(func);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetAALFunctionProperty(): MTK_PQ_SUPPORT disabled");
        return;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setFunction(func, true, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setFunction failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setFunction(func, true);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setFunction failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    } 
#endif
}

static void nativeSetSmartBacklightStrength(JNIEnv* env, jclass clz, jint value)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("nativeSetSmartBacklightStrength(): MTK_AAL_SUPPORT disabled");
        return;
    }

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().setSmartBacklightStrength(value);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetSmartBacklightStrength(): MTK_PQ_SUPPORT disabled");
        return;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setSmartBacklightStrength(value, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setSmartBacklightStrength failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setSmartBacklightStrength(value);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setSmartBacklightStrength failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }    
#endif
}

static void nativeSetReadabilityLevel(JNIEnv* env, jclass clz, jint value)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("nativeSetReadabilityLevel(): MTK_AAL_SUPPORT disabled");
        return;
    }

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().setReadabilityLevel(value);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetReadabilityLevel(): MTK_PQ_SUPPORT disabled");
        return;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setReadabilityLevel(value, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setReadabilityLevel failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setReadabilityLevel(value);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setReadabilityLevel failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }    
#endif
}

static void nativeSetLowBLReadabilityLevel(JNIEnv* env, jclass clz, jint value)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("nativeSetLowBLReadabilityLevel(): MTK_AAL_SUPPORT disabled");
        return;
    }

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().setLowBLReadabilityLevel(value);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetLowBLReadabilityLevel(): MTK_PQ_SUPPORT disabled");
        return;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setLowBLReadabilityLevel(value, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setLowBLReadabilityLevel failed!");
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setLowBLReadabilityLevel(value);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setLowBLReadabilityLevel failed! (HIDL)");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return;
    }   
#endif
}

static jboolean nativeSetESSLEDMinStep(JNIEnv* env, jclass clz, jint value)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("nativeSetESSLEDMinStep(): MTK_AAL_SUPPORT disabled");
        return JNI_FALSE;
    }

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().setESSLEDMinStep(value);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetESSLEDMinStep(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setESSLEDMinStep(value, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setESSLEDMinStep failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setESSLEDMinStep(value);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setESSLEDMinStep failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }    
#endif
    ALOGD("[JNI_PQ] Set ESSLEDMinStep = %d\n", value);

    return JNI_TRUE;
}


static jint nativeGetESSLEDMinStep(JNIEnv* env, jclass clz)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetESSLEDMinStep(): MTK_AAL_SUPPORT disabled");
        return 0;
    }

    uint32_t value = 0;

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().getESSLEDMinStep(&value);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetESSLEDMinStep(): MTK_PQ_SUPPORT disabled");
        return 0;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        parcelable_getESSLEDMinStep _aidl_return;
        ScopedAStatus ret = service->getESSLEDMinStep(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            value = _aidl_return.value;
        }
        else {
            ALOGE("[JNI_PQ] nativeGetESSLEDMinStep() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getESSLEDMinStep failed!");
            return 0;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getESSLEDMinStep(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, uint32_t getValue) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                value = getValue;
            } else {
                ALOGE("[JNI_PQ] nativeGetESSLEDMinStep() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getESSLEDMinStep failed! (HIDL)");
            return 0;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }
#endif
    ALOGD("[JNI_PQ] Get ESSLEDMinStep = %d\n", value);

    return value;
}

static jboolean nativeSetESSOLEDMinStep(JNIEnv* env, jclass clz, jint value)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("nativeSetESSOLEDMinStep(): MTK_AAL_SUPPORT disabled");
        return JNI_FALSE;
    }

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().setESSOLEDMinStep(value);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetESSOLEDMinStep(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setESSOLEDMinStep(value, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setESSOLEDMinStep failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setESSOLEDMinStep(value);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setESSOLEDMinStep failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }    
#endif
    ALOGD("[JNI_PQ] Set ESSOLEDMinStep = %d\n", value);

    return JNI_TRUE;
}


static jint nativeGetESSOLEDMinStep(JNIEnv* env, jclass clz)
{
    UNUSED(env);
    UNUSED(clz);

    if (AalIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetESSOLEDMinStep(): MTK_AAL_SUPPORT disabled");
        return JNI_FALSE;
    }

    uint32_t value = 0;

#ifdef MTK_SYSTEM_AAL
    AALClient::getInstance().getESSOLEDMinStep(&value);
#else
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetESSOLEDMinStep(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        parcelable_getESSOLEDMinStep _aidl_return;
        ScopedAStatus ret = service->getESSOLEDMinStep(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            value = _aidl_return.value;
        }
        else {
            ALOGE("[JNI_PQ] nativeGetESSOLEDMinStep() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getESSOLEDMinStep failed!");
            return 0;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getESSOLEDMinStep(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, uint32_t getValue) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                value = getValue;
            } else {
                ALOGE("[JNI_PQ] nativeGetESSOLEDMinStep() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getESSOLEDMinStep failed! (HIDL)");
            return 0;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }
#endif
    ALOGD("[JNI_PQ] Get ESSOLEDMinStep = %d\n", value);

    return value;
}

static jboolean nativeSetExternalPanelNits(JNIEnv* env, jclass clz, jint externalPanelNits)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetExternalPanelNits(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setExternalPanelNits(externalPanelNits, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setExternalPanelNits failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setExternalPanelNits(externalPanelNits);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setExternalPanelNits failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

    return JNI_TRUE;
}


static jint nativeGetExternalPanelNits(JNIEnv* env, jclass clz)
{
    uint32_t getExternalPanelNits = 0;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetExternalPanelNits(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getExternalPanelNits _aidl_return;
        ScopedAStatus ret = service->getExternalPanelNits(&_aidl_return);
        if (_aidl_return.retval == Result::OK) {
            getExternalPanelNits = _aidl_return.externalPanelNits;
        } else {
            ALOGE("[JNI_PQ] nativeGetExternalPanelNits() failed!");
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getExternalPanelNits failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getExternalPanelNits(
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, uint32_t externalPanelNits) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                getExternalPanelNits = externalPanelNits;
            } else {
                ALOGE("[JNI_PQ] nativeGetExternalPanelNits() failed! (HIDL)");
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getExternalPanelNits failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    UNUSED(clz);

    return getExternalPanelNits;
}


static jboolean nativeSetRGBGain(JNIEnv *env, jobject thiz, jint r_gain, jint g_gain, jint b_gain, jint step)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetRGBGain(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setRGBGain(r_gain, g_gain, b_gain, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setRGBGain failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setRGBGain(r_gain, g_gain, b_gain, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setRGBGain failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }    

    UNUSED(env);
    UNUSED(thiz);

    return JNI_TRUE;
}

static jintArray nativeGetRGBGain(JNIEnv *env, jobject thiz)
{
    jintArray arr;
    arr = env->NewIntArray(3);
    if (arr == NULL)
    {
        ALOGE("[JNI_PQ] nativeGetRGBGain error, out of memory");
        return NULL;
    }
    jint rgb_arr[3];
    for (int i = 0; i < 3; i++) {
         rgb_arr[i] = 1024;
     }

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetRGBGain(): MTK_PQ_SUPPORT disabled");
        return arr;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getTuningField _aidl_return;
        ScopedAStatus ret = service->getTuningField(MOD_RGB_GAIN, 0x0, &_aidl_return);
        if (_aidl_return.retval == Result::OK)
        {
            rgb_arr[0] = _aidl_return.value;
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getTuningField(MOD_RGB_GAIN, 0x0) failed!");
            return arr;
        }

        ret = service->getTuningField(MOD_RGB_GAIN, 0x1, &_aidl_return);
        if (_aidl_return.retval == Result::OK)
        {
            rgb_arr[1] = _aidl_return.value;
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getTuningField(MOD_RGB_GAIN, 0x1) failed!");
            return arr;
        }

        ret = service->getTuningField(MOD_RGB_GAIN, 0x2, &_aidl_return);
        if (_aidl_return.retval == Result::OK)
        {
            rgb_arr[2] = _aidl_return.value;
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getTuningField(MOD_RGB_GAIN, 0x2) failed!");
            return arr;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getTuningField(MOD_RGB_GAIN, 0x0,
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t value) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                rgb_arr[0] = value;
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getTuningField(MOD_RGB_GAIN, 0x0) failed! (HIDL)");
            return arr;
        }
        ret_hidl = service_hidl->getTuningField(MOD_RGB_GAIN, 0x1,
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t value) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                rgb_arr[1] = value;
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getTuningField(MOD_RGB_GAIN, 0x1) failed! (HIDL)");
            return arr;
        }
        ret_hidl = service_hidl->getTuningField(MOD_RGB_GAIN, 0x2,
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t value) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                rgb_arr[2] = value;
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getTuningField(MOD_RGB_GAIN, 0x2) failed! (HIDL)");
            return arr;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    env->SetIntArrayRegion(arr, 0, 3, rgb_arr);
    UNUSED(thiz);

    return arr;
}

static jboolean nativeSetCcorrMatrix(JNIEnv* env, jclass clz, jintArray matrix, jint step)
{
    UNUSED(clz);

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetCcorrMatrix(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    jsize arr_len;
    arr_len = env->GetArrayLength(matrix);
    if (arr_len != 9) {
        ALOGE("[JNI_PQ] nativeSetCcorrMatrix() input matrix size[%d] error!", arr_len);
        return JNI_FALSE;
    }

    int c_array[DISP_CCORR_MATRIX_SIZE] = { 0 };
    env->GetIntArrayRegion(matrix, 0, arr_len, c_array);

    if (service != nullptr)
    {
        std::array<std::array<int32_t,3>, 3> send_matrix;
        for (int32_t i = 0; i < 3; i++)
        {
            for (int32_t j = 0; j < 3; j++)
            {
                send_matrix[i][j] = c_array[i*3 + j];
            }
        }
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setColorMatrix3x3(send_matrix, step, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setCcorrMatrix failed!");
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        hidl_array<int32_t, 3, 3> send_matrix;
        for (int32_t i = 0; i < 3; i++)
        {
            for (int32_t j = 0; j < 3; j++)
            {
                send_matrix[i][j] = c_array[i*3 + j];
            }
        }    auto ret_hidl = service_hidl->setColorMatrix3x3(send_matrix, step);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setCcorrMatrix failed! (HIDL)");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static void nativeSetESS20Enable(JNIEnv* env, jclass clz, jboolean isEnable, jint ELVSSPNIndex,jboolean choice)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeSetRGBGain(): MTK_PQ_SUPPORT disabled");
    }

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        /***********************************/
        /***** isEnable:enable ess2.0 ******/
        /******** ELVSSPNIndex >=0 *********/
        int temp_data[3];

        if (isEnable == true) { //enable ess2.0
            temp_data[0] = 1;
        } else
            temp_data[0] = 0;

        temp_data[1] = ELVSSPNIndex;

        if (choice == true) { //parse gamma
            temp_data[2] = 1;
        } else
            temp_data[2] = 0;


        std::vector<uint8_t> input_data(temp_data,temp_data+3);

        std::optional<std::vector<uint8_t>> input_params = input_data;
        std::optional<std::vector<::ndk::ScopedFileDescriptor>> handle_params;

        aidl::vendor::mediatek::hardware::pq_aidl::parcelable_perform result_pq;

        ScopedAStatus ret = service->perform(0, 6, input_params, handle_params, &result_pq);
        if (!ret.isOk() || ( result_pq.error != 0)) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setESS20Enable failed!");
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
    }

    UNUSED(env);

}

static jboolean nativeEnablePaperMode(JNIEnv* env, jclass clz, jint isEnable, jint rangeLimit, jint baseValue, jint colorARange)
{
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeEnablePaperMode(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }
    ALOGD("[JNI_PQ] EnablePaperMode(), isEnable:[%d], rangeLimit:[%d], baseValue:[%d], baseValue:[%d]\n",
     isEnable, rangeLimit, baseValue, baseValue);
    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {

        int temp_data[4];

        if (isEnable == 1) { //enable paper display
            temp_data[0] = 1;
        } else
            temp_data[0] = 0;

        temp_data[1] = rangeLimit;
        temp_data[2] = baseValue;
        temp_data[3] = colorARange;


        std::vector<uint8_t> input_data(temp_data,temp_data+4);

        std::optional<std::vector<uint8_t>> input_params = input_data;
        std::optional<std::vector<::ndk::ScopedFileDescriptor>> handle_params;

        aidl::vendor::mediatek::hardware::pq_aidl::parcelable_perform result_pq;

        ScopedAStatus ret = service->perform(0, 29, input_params, handle_params, &result_pq);
        if (!ret.isOk() || ( result_pq.error != 0)) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::EnablePaperMode failed!");
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    UNUSED(env);
    return JNI_TRUE;

}

static jboolean nativeIsPaperModeEnabled(JNIEnv *env, jobject thiz)
{
    Mutex::Autolock autoLock(mLock);
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    char *endptr = NULL;
    int isEnable = -1;

    property_get(PQ_PAPER_EN_STR, value, PQ_PAPER_EN_DEFAULT);
    isEnable = strtol(value,&endptr,10);
    ALOGD("[JNI_PQ] getPaperMode(), property get [%d]", isEnable);

    UNUSED(env);
    UNUSED(thiz);

    return (isEnable > 0) ? true : false;
}

static jint nativeGetTuningField(JNIEnv *env, jclass clz, jint pqModule, jint field)
{
    UNUSED(clz);
    int32_t getValue = -1;

    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetTuningField(): MTK_PQ_SUPPORT disabled");
        return -1;
    }

    ALOGD("[JNI_PQ] IPictureQuality::getTuningField(%d, %d) call in",
        pqModule, field);

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        parcelable_getTuningField _aidl_return;
        ScopedAStatus ret = service->getTuningField(pqModule, field, &_aidl_return);
        if (_aidl_return.retval == Result::OK)
        {
            getValue = _aidl_return.value;
        }
        if (!ret.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::getTuningField(%d, %d) failed!",
                pqModule, field);
            return -1;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->getTuningField(pqModule, field,
            [&] (vendor::mediatek::hardware::pq::V2_0::Result retval, int32_t value) {
            if (retval == vendor::mediatek::hardware::pq::V2_0::Result::OK) {
                getValue = value;
            }
        });
        if (!ret_hidl.isOk()) {
            ALOGE("[JNI_PQ] IPictureQuality::getTuningField(%d, %d) failed! (HIDL)",
                pqModule, field);
            return -1;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return -1;
    }

    return getValue;
}

static jboolean nativeSetTuningField(JNIEnv *env, jclass clz, jint pqModule, jint field, jint value)
{
    UNUSED(clz);
    if (PQIsSupport() == false) {
        ALOGE("[JNI_PQ] nativeGetTuningField(): MTK_PQ_SUPPORT disabled");
        return JNI_FALSE;
    }

    ALOGD("[JNI_PQ] IPictureQuality::setTuningField(%d, %d, %d) call in",
        pqModule, field, value);

    sp<IPictureQuality> service_hidl = IPictureQuality::tryGetService();
    std::shared_ptr<IPictureQuality_AIDL> service = IPictureQuality_AIDL::fromBinder(
            ::ndk::SpAIBinder(AServiceManager_checkService(getPQServiceName().c_str())));

    if (service != nullptr)
    {
        Result result = Result::INVALID_STATE;
        ScopedAStatus ret = service->setTuningField(pqModule, field, value, &result);
        if (!ret.isOk() || result != Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality_AIDL::setTuningField(%d, %d, %d) failed!",
                pqModule, field, value);
            return JNI_FALSE;
        }
    }
    else if (service_hidl != nullptr)
    {
        auto ret_hidl = service_hidl->setTuningField(pqModule, field, value);
        if (!ret_hidl.isOk() || ret_hidl != vendor::mediatek::hardware::pq::V2_0::Result::OK) {
            ALOGE("[JNI_PQ] IPictureQuality::setTuningField(%d, %d, %d) failed!",
                pqModule, field, value);
            return JNI_FALSE;
        }
    }
    else
    {
        ALOGE("[JNI_PQ] failed to get HW service");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/////////////////////////////////////////////////////////////////////////////////

//JNI register
////////////////////////////////////////////////////////////////
static const char *classPathName = JNI_PQ_CLASS_NAME;

static JNINativeMethod g_methods[] = {

    // query features
    {"nativeGetCapability", "()I", (void*)getCapability},

    // Camera PQ switch
    {"nativeSetCameraPreviewMode", "(I)V", (void*)setCameraPreviewMode},
    {"nativeSetGalleryNormalMode", "(I)V", (void*)setGalleryNormalMode},
    {"nativeSetVideoPlaybackMode", "(I)V", (void*)setVideoPlaybackMode},
    // Image DC
    {"nativeGetDynamicContrastHistogram", "([BIIL" JNI_PQ_CLASS_NAME "$Hist;)V", (void*)getDynamicContrastHistogram},

    // MiraVision setting
    {"nativeEnablePQ", "(I)Z", (void*)enablePQ},
    {"nativeEnableColor", "(I)Z", (void*)enableColor},
    {"nativeEnableContentColor", "(I)Z", (void*)enableContentColor},
    {"nativeEnableSharpness", "(I)Z", (void*)enableSharpness},
    {"nativeEnableDynamicContrast", "(I)Z", (void*)enableDynamicContrast},
    {"nativeEnableDynamicSharpness", "(I)Z", (void*)enableDynamicSharpness},
    {"nativeEnableColorEffect", "(I)Z", (void*)enableColorEffect},
    {"nativeEnableGamma", "(I)Z", (void*)enableGamma},
    {"nativeEnableUltraResolution", "(I)Z", (void*)enableUltraResolution},
    {"nativeEnableISOAdaptiveSharpness", "(I)Z", (void*)enableISOAdaptiveSharpness},
    {"nativeGetPictureMode", "()I", (void*)getPictureMode},
    {"nativeSetPictureMode", "(II)Z", (void*)setPictureMode},
    {"nativeSetColorRegion", "(IIIII)Z", (void*)setColorRegion},
    {"nativeGetContrastIndexRange", "(L" JNI_PQ_CLASS_NAME "$Range;)V", (void*)getContrastIndexRange},
    {"nativeGetContrastIndex", "()I", (void*)getContrastIndex},
    {"nativeSetContrastIndex", "(II)V", (void*)setContrastIndex},
    {"nativeGetSaturationIndexRange", "(L" JNI_PQ_CLASS_NAME "$Range;)V", (void*)getSaturationIndexRange},
    {"nativeGetSaturationIndex", "()I", (void*)getSaturationIndex},
    {"nativeSetSaturationIndex", "(II)V", (void*)setSaturationIndex},
    {"nativeGetPicBrightnessIndexRange", "(L" JNI_PQ_CLASS_NAME "$Range;)V", (void*)getPicBrightnessIndexRange},
    {"nativeGetPicBrightnessIndex", "()I", (void*)getPicBrightnessIndex},
    {"nativeSetPicBrightnessIndex", "(II)V", (void*)setPicBrightnessIndex},
    {"nativeGetSharpnessIndexRange", "(L" JNI_PQ_CLASS_NAME "$Range;)V", (void*)getSharpnessIndexRange},
    {"nativeGetSharpnessIndex", "()I", (void*)getSharpnessIndex},
    {"nativeSetSharpnessIndex", "(I)V", (void*)setSharpnessIndex},
    {"nativeGetDynamicContrastIndexRange", "(L" JNI_PQ_CLASS_NAME "$Range;)V", (void*)getDynamicContrastIndexRange},
    {"nativeGetDynamicContrastIndex", "()I", (void*)getDynamicContrastIndex},
    {"nativeSetDynamicContrastIndex", "(I)V", (void*)setDynamicContrastIndex},
    {"nativeGetColorEffectIndexRange", "(L" JNI_PQ_CLASS_NAME "$Range;)V", (void*)getColorEffectIndexRange},
    {"nativeGetColorEffectIndex", "()I", (void*)getColorEffectIndex},
    {"nativeSetColorEffectIndex", "(I)V", (void*)setColorEffectIndex},
    {"nativeGetGammaIndexRange", "(L" JNI_PQ_CLASS_NAME "$Range;)V", (void*)getGammaIndexRange},
    {"nativeSetGammaIndex", "(II)V", (void*)setGammaIndex},
    {"nativeEnableOD", "(I)Z", (void*)enableOD},
    {"nativeSetBlueLightStrength", "(II)Z", (void*)nativeSetBlueLightStrength},
    {"nativeGetBlueLightStrength", "()I", (void*)nativeGetBlueLightStrength},
    {"nativeEnableBlueLight", "(ZI)Z", (void*)nativeEnableBlueLight},
    {"nativeIsBlueLightEnabled", "()Z", (void*)nativeIsBlueLightEnabled},
    {"nativeSetChameleonStrength", "(II)Z", (void*)nativeSetChameleonStrength},
    {"nativeGetChameleonStrength", "()I", (void*)nativeGetChameleonStrength},
    {"nativeEnableChameleon", "(ZI)Z", (void*)nativeEnableChameleon},
    {"nativeIsChameleonEnabled", "()Z", (void*)nativeIsChameleonEnabled},
    {"nativeGetDefaultOffTransitionStep", "()I", (void*)nativeGetDefaultOffTransitionStep},
    {"nativeGetDefaultOnTransitionStep", "()I", (void*)nativeGetDefaultOnTransitionStep},
    {"nativeSetGlobalPQSwitch", "(I)Z", (void*)nativeSetGlobalPQSwitch},
    {"nativeGetGlobalPQSwitch", "()I", (void*)nativeGetGlobalPQSwitch},
    {"nativeSetGlobalPQStrength", "(I)Z", (void*)nativeSetGlobalPQStrength},
    {"nativeGetGlobalPQStrength", "()I", (void*)nativeGetGlobalPQStrength},
    {"nativeGetGlobalPQStrengthRange", "()I", (void*)nativeGetGlobalPQStrengthRange},
    {"nativeEnableVideoHDR", "(Z)Z", (void*)nativeEnableVideoHDR},
//BSP: add for c3d ADJHBSY-4107 by meng.zhang 20220718 start
    {"nativeEnableC3D", "(Z)Z", (void*)nativeEnableC3D},
    {"nativeIsC3DEnabled", "()Z", (void*)nativeIsC3DEnabled},
//BSP: add for c3d ADJHBSY-4107 by meng.zhang 20220718 end
//TR: add for c3d ADJHBSY-4107 by xiaosong.wang 20220825 start
    {"nativeDeleteC3DCaliFile", "()Z", (void*)nativeDeleteC3DCaliFile},
    {"nativeGetC3dCaliFileSize", "()I", (void*)nativeGetC3dCaliFileSize},
    {"nativeGetC3dCaliStatus", "()I", (void*)nativeGetC3dCaliStatus},
    {"nativeC3dCaliPass", "(I)Z", (void*)nativeC3dCaliPass},
//TR: add for c3d ADJHBSY-4107 by xiaosong.wang 20220825 end
//TR:add display ccm XLBSSB-2302 by xiaosongwang 20230202 start
    {"nativeGetCcorrCaliStatus", "()I", (void*)nativeGetCcorrCaliStatus},
    {"nativeLoadCcorrCaliData", "()Z", (void*)nativeLoadCcorrCaliData},
    {"nativeUseCcorrDefault", "()Z", (void*)nativeUseCcorrDefault},
    {"nativeBackupCcorrCali", "()Z", (void*)nativeBackupCcorrCali},
    {"nativeDeleteCcorrCaliFile", "()Z", (void*)nativeDeleteCcorrCaliFile},
    {"nativeGetScreenStatus", "()Z", (void*)nativeGetScreenStatus},
    {"nativeCcorrCaliPass", "()Z", (void*)nativeCcorrCaliPass},
    {"nativeGetLcdIndex", "()I", (void*)nativeGetLcdIndex},
//TR:add display ccm XLBSSB-2302 by xiaosongwang 20230202 end
    {"nativeIsVideoHDREnabled", "()Z", (void*)nativeIsVideoHDREnabled},
    {"nativeEnableMdpDRE", "(I)Z", (void*)nativeEnableMdpDRE},
    {"nativeEnableMdpCCORR", "(I)Z", (void*)nativeEnableMdpCCORR},
    {"nativeGetAALFunction", "()I", (void*)nativeGetAALFunction},
    {"nativeSetAALFunction", "(I)V", (void*)nativeSetAALFunction},
    {"nativeSetAALFunctionProperty", "(I)V", (void*)nativeSetAALFunctionProperty},
    {"nativeSetSmartBacklightStrength", "(I)V", (void*)nativeSetSmartBacklightStrength},
    {"nativeSetReadabilityLevel", "(I)V", (void*)nativeSetReadabilityLevel},
    {"nativeSetLowBLReadabilityLevel", "(I)V", (void*)nativeSetLowBLReadabilityLevel},
    {"nativeSetESSLEDMinStep", "(I)Z", (void*)nativeSetESSLEDMinStep},
    {"nativeGetESSLEDMinStep", "()I", (void*)nativeGetESSLEDMinStep},
    {"nativeSetESSOLEDMinStep", "(I)Z", (void*)nativeSetESSOLEDMinStep},
    {"nativeGetESSOLEDMinStep", "()I", (void*)nativeGetESSOLEDMinStep},
    {"nativeSetExternalPanelNits", "(I)Z", (void*)nativeSetExternalPanelNits},
    {"nativeGetExternalPanelNits", "()I", (void*)nativeGetExternalPanelNits},
    {"nativeSetRGBGain", "(IIII)Z", (void*)nativeSetRGBGain},
    {"nativeGetRGBGain", "()[I", (void*)nativeGetRGBGain},
    {"nativeSetCcorrMatrix", "([II)Z", (void*)nativeSetCcorrMatrix},
    {"nativeSetESS20Enable", "(ZIZ)V", (void*)nativeSetESS20Enable},
    {"nativeGetTuningField", "(II)I", (void*)nativeGetTuningField},
    {"nativeSetTuningField", "(III)Z", (void*)nativeSetTuningField},
    //SPD: add for PQE LCQHLYYLR-65 by feng.an 20220410 start
    {"nativeSetTranPictureMode", "(IIIIII)Z", (void*)setTranPictureMode},
    {"nativeSetPQLogEnable", "(Z)V", (void*)nativeSetPQLogEnable},
    //SPD: add for PQE LCQHLYYLR-65 by feng.an 20220410 end
    //SDD:add for NFRFP-28747 by xiangbo.li 20241226 start
    {"nativeSetFunction", "(IZ)V", (void*)setFunction},
    {"nativeSetDisplayGamePQ", "(III)V", (void*)setDisplayGamePQ},
    {"nativeSetDisplayGamePQV2MPq", "(IIIIIII)V", (void*)setDisplayGamePQV2MPq},
    //SDD:add for NFRFP-28747 by xiangbo.li 20241226 end
    {"nativeEnablePaperMode", "(IIII)Z", (void*)nativeEnablePaperMode},
    {"nativeIsPaperModeEnabled", "()Z", (void*)nativeIsPaperModeEnabled},
};

/*
 * Register several native methods for one class.
 */
static int registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

// ----------------------------------------------------------------------------

/*
 * This is called by the VM when the shared library is first loaded.
 */

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;

    UNUSED(reserved);

    ALOGI("JNI_OnLoad");

    if (JNI_OK != vm->GetEnv((void **)&env, JNI_VERSION_1_4)) {
        ALOGE("ERROR: GetEnv failed");
        goto bail;
    }

    if (!registerNativeMethods(env, classPathName, g_methods, sizeof(g_methods) / sizeof(g_methods[0]))) {
        ALOGE("ERROR: registerNatives failed");
        goto bail;
    }

    result = JNI_VERSION_1_4;

bail:
    return result;
}
