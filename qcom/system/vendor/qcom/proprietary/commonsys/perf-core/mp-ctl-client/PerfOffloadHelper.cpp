/******************************************************************************
  @file    PerfOffloadHelper.cpp
  @brief   Implementation of PerfOffloadHelper

  DESCRIPTION

  ---------------------------------------------------------------------------
  Copyright (c) 2021 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
  ---------------------------------------------------------------------------
 ******************************************************************************/
#define LOG_TAG "ANDR-PERF-OH-SYS"
#include "PerfOffloadHelper.h"
#include <unistd.h>
#include <pthread.h>
#include <log/log.h>
#include <mutex>

#define PROP_NAME "vendor.debug.trace.perf"
#define QC_DEBUG_ERRORS 1
#ifdef QC_DEBUG_ERRORS
#define QLOGE(x,...) ALOGE("%s() %d: " x "", __FUNCTION__ , __LINE__, ##__VA_ARGS__);
#else
#define QLOGE(...)
#endif

#if QC_DEBUG
#define QLOGV(x,...) ALOGV("%s() %d: " x "", __FUNCTION__ , __LINE__, ##__VA_ARGS__);
#define QLOGI(x,...) ALOGI("%s() %d: " x "", __FUNCTION__ , __LINE__, ##__VA_ARGS__);
#define QLOGD(x,...) ALOGD("%s() %d: " x "", __FUNCTION__ , __LINE__, ##__VA_ARGS__);
#define QLOGW(x,...) ALOGW("%s() %d: " x "", __FUNCTION__ , __LINE__, ##__VA_ARGS__);
#define QLOGE(x,...) ALOGE("%s() %d: " x "", __FUNCTION__ , __LINE__, ##__VA_ARGS__);
#else
#define QLOGV(x,...)
#define QLOGI(x,...)
#define QLOGD(x,...)
#define QLOGW(x,...) ALOGW("%s() %d: " x "",  __FUNCTION__ , __LINE__, ##__VA_ARGS__);
#define QLOGE(x,...) ALOGE("%s() %d: " x "",  __FUNCTION__ , __LINE__, ##__VA_ARGS__);
#endif
PerfOffloadHelper::PerfOffloadHelper() {
    mHandle = 0;
    memset(mHandleMapping,0,sizeof(mHandleMapping));
}

int32_t PerfOffloadHelper::getNewOffloadHandle() {
    int32_t handleCpy = FAILED;
    try {
        {
            std::scoped_lock lck(mHandleMappingLck);
            handleCpy = (mHandle + 1) % MAX_OFFLOAD_REQ_SIZE;
            if (handleCpy < MAX_OFFLOAD_REQ_SIZE && handleCpy >= 0) {
                mHandleMapping[handleCpy] = {-1, NEW_HANDLE_STATE};
                mHandle = handleCpy;
            }
        }
        QLOGI("Requested new Offload Handle: %d", handleCpy);
    } catch (std::exception &e) {
        handleCpy = FAILED;
        QLOGE("Exception caught: %s in %s -Handle is invalid", e.what(), __func__);
    }
    return handleCpy;
}

int32_t PerfOffloadHelper::getPerfHandle(int32_t handle) {
    int32_t rc = FAILED;
    bool flag = true;
    if (MAX_OFFLOAD_REQ_SIZE <= handle || handle < 0) {
        QLOGE("Invalid handle req: %d", handle);
        return FAILED;
    }
    try {
        std::scoped_lock lck(mHandleMappingLck);
            rc = mHandleMapping[handle].mPerfHandle;
            if (rc == -1 && mHandleMapping[handle].mState == NEW_HANDLE_STATE) {
                mHandleMapping[handle].mState = RELEASE_REQ_STATE;
                flag = false;
            }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s -Handle is invalid", e.what(), __func__);
    }
    QLOGI("Perf Handle:[%d], OffloadHandle:[%d], flag:[%d]", rc, handle, flag);
    return rc;
}

int32_t PerfOffloadHelper::mapPerfHandle(int32_t handle, int32_t requestHandle) {
    int32_t returnState = REMOVE_HANDLE_STATE;
    if (MAX_OFFLOAD_REQ_SIZE <= handle || handle < 0) {
        QLOGE("Invalid handle map req: %d to %d", handle, requestHandle);
        return returnState;
    }
    try {
            std::scoped_lock lck(mHandleMappingLck);
            if (mHandleMapping[handle].mState == RELEASE_REQ_STATE) {
                returnState = RELEASE_REQ_STATE;
            }
            if (returnState != RELEASE_REQ_STATE) {
                mHandleMapping[handle] = {requestHandle, NEW_HANDLE_STATE};
            }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s ", e.what(), __func__);
    }
    QLOGI("Perf Handle:[%d], OffloadHandle:[%d], State:[%d]", requestHandle, handle, returnState);
    return returnState;
}

void PerfOffloadHelper::releaseHandle(int32_t handle) {
    if (MAX_OFFLOAD_REQ_SIZE <= handle || handle < 0) {
        QLOGE("Invalid handle release req: %d in %s ", handle, __func__);
        return;
    }

    try {
        std::scoped_lock lck(mHandleMappingLck);
        QLOGI("Perf Handle: %d mapped to OffloadHandle: %d is released", mHandleMapping[handle].mPerfHandle, handle);
        mHandleMapping[handle] = {-1, NEW_HANDLE_STATE};
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s ", e.what(), __func__);
    }
}

PerfOffloadHelper &PerfOffloadHelper::getPerfOffloadHelper() {
    static PerfOffloadHelper mPerfOffloadHelper;
    return mPerfOffloadHelper;
}

PerfOffloadHelper::~PerfOffloadHelper() {
}
