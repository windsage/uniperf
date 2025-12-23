/******************************************************************************
  @file    client.cpp
  @brief   Android performance PerfLock library

  DESCRIPTION

  ---------------------------------------------------------------------------

  Copyright (c) 2014,2017 Qualcomm Technologies, Inc.
  All Rights Reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
  ---------------------------------------------------------------------------
******************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <stdbool.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <vector>
#include <shared_mutex>
#include <exception>

#define LOG_TAG         "ANDR-PERF-CLIENT-SYS"
#include <log/log.h>
#include <cerrno>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <cutils/properties.h>
#include "client.h"
#include "mp-ctl.h"
#include <vendor/qti/hardware/perf/2.2/IPerf.h>
#include <vendor/qti/hardware/perf/2.3/IPerf.h>
#include <aidl/vendor/qti/hardware/perf2/BnPerf.h>
#include "PerfThreadPool.h"
#include "PerfOffloadHelper.h"

#define FAILED                  -1
#define SUCCESS                 0
#define THREADS_IN_POOL 4
#define DEFAULT_OFFLOAD_FLAG 0
#define SET_OFFLOAD_FLAG 1
#define VENDOR_HINT_PERF_INIT_CHECK 0x0000109E
#define INIT_NOT_COMPLETED -3
#define API_LEVEL_PROP_NAME "ro.board.first_api_level"
#define R_API_LEVEL 30
#define S_API_LEVEL 31
#define EXTRACT_OPCODE_TYPE 0x20000000
#define SHIFT_BIT_OPCODE_TYPE 29


using android::hardware::Return;
using android::hardware::Void;
using android::hardware::hidl_death_recipient;
using android::hardware::hidl_vec;
using android::hardware::hidl_string;
using android::hidl::base::V1_0::IBase;
using V2_2_IPerf = vendor::qti::hardware::perf::V2_2::IPerf;
using V2_3_IPerf = vendor::qti::hardware::perf::V2_3::IPerf;
using ::android::sp;
using ::android::wp;
using std::unique_lock;
using std::shared_lock;
using std::shared_timed_mutex;
using std::timed_mutex;
using std::defer_lock;
using CM = std::chrono::milliseconds;

using IPerfAidl = ::aidl::vendor::qti::hardware::perf2::IPerf;
using ::ndk::SpAIBinder;
using IPerfCallbackAidl = ::aidl::vendor::qti::hardware::perf2::IPerfCallback;

#define TIMEOUT 10
#define PERF_HINT_REL_VAL 1
enum HalType {
    UNKNOWN = 0,
    AIDL = 1,
    HIDL = 2,
};
static HalType halType = HalType::UNKNOWN;

static sp<V2_2_IPerf> gPerfHal = NULL;
static sp<V2_3_IPerf> gPerfHalV2_3 = NULL;
static shared_timed_mutex gPerf_lock;
static double perf_hal_ver = 0.0;
static int api_level = 0;
static bool perf_init_completed_flag = false;
static std::shared_ptr<IPerfAidl> gPerfAidl = nullptr;
static timed_mutex timeoutMutex;
static timed_mutex perfInitTimeoutMutex;
static ::ndk::ScopedAIBinder_DeathRecipient deathRecipient;

static bool is_perf_hal_alive();
static bool is_perf_init_completed();
static int get_api_level();
static int perf_hint_private(int hint, const char *pkg, int duration, int type, int offloadFlag, int numArgs, int list[]);
static void perf_event_private(int eventId, const char *pkg, int offloadFlag, int numArgs, int list[]);
static int perf_hint_acq_rel_private(int handle, int hint, const char *pkg, int duration, int type, int offloadFlag, int numArgs, int list[]);
#ifdef __cplusplus
extern "C" {
#endif
int perf_callback_register(const std::shared_ptr<IPerfCallbackAidl>&, int32_t);
#ifdef __cplusplus
}
#endif

void serviceDiedAidl(void* cookie) {
    try {
        unique_lock<shared_timed_mutex> write_lock(gPerf_lock);
        gPerfAidl = nullptr;
        QLOGE("perf hal just died");
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
}

struct PerfDeathRecipient : virtual public hidl_death_recipient
{
    virtual void serviceDied(uint64_t /*cookie*/, const wp<IBase>& /*who*/) override {
        try {
            unique_lock<shared_timed_mutex> write_lock(gPerf_lock);
            gPerfHal = NULL;
            gPerfHalV2_3 = NULL;
            ALOGE("IPerf serviceDied");
        } catch (std::exception &e) {
            QLOGE("Exception caught: %s in %s", e.what(), __func__);
        } catch (...) {
            QLOGE("Exception caught in %s", __func__);
        }
    }
};

static bool getPerfAidlAndLinkToDeath() {
    bool rc = true;
    try {
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl == nullptr) {
            read_lock.unlock();
            {
                unique_lock<shared_timed_mutex> write_lock(gPerf_lock);
                if (gPerfAidl == nullptr) {
                    const std::string instance = std::string() + IPerfAidl::descriptor + "/default";
                    auto perfBinder = ::ndk::SpAIBinder(AServiceManager_checkService(instance.c_str()));

                    if (perfBinder.get() != nullptr) {
                        gPerfAidl = IPerfAidl::fromBinder(perfBinder);
                        if (AIBinder_isRemote(perfBinder.get())) {

                            deathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(AIBinder_DeathRecipient_new(&serviceDiedAidl));
                            if (deathRecipient.get())
                                QLOGE("death recipent object is fine");
                            auto status = ::ndk::ScopedAStatus::fromStatus(AIBinder_linkToDeath(perfBinder.get(),
                                        deathRecipient.get(), (void*) serviceDiedAidl));
                            if (!status.isOk()) {
                                gPerfAidl = nullptr;
                                rc = false;
                                QLOGE("linking perf aidl service to death failed: %d: %s", status.getStatus(), status.getMessage());
                            }
                        }
                    } else {
                        QLOGE("perf aidl service doesn't exist");
                        rc = false;
                        halType = HalType::UNKNOWN;
                    }
                }
            }
        }
    } catch (std::exception &e) {
        rc = false;
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        rc = false;
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

static sp<PerfDeathRecipient> perfDeathRecipient = NULL;

static bool getPerfServiceAndLinkToDeath() {
    bool rc = true;

    try {
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfHal == NULL) {
            read_lock.unlock();
            {
                unique_lock<shared_timed_mutex> write_lock(gPerf_lock);
                if (gPerfHal == NULL) {
                    gPerfHal = V2_2_IPerf::tryGetService();
                    if (gPerfHal != NULL) {
                        perfDeathRecipient = new(std::nothrow) PerfDeathRecipient();
                        if (perfDeathRecipient != NULL) {
                            android::hardware::Return<bool> linked = gPerfHal->linkToDeath(perfDeathRecipient, 0);
                            if (!linked || !linked.isOk()) {
                                gPerfHal = NULL;
                                rc = false;
                                ALOGE("Unable to link to gPerfHal death notifications!");
                            }
                        } else {
                            gPerfHal = NULL;
                            rc = false;
                            ALOGE("Unable to link to gPerfHal death notifications! memory error");
                        }
                    } else {
                        rc = false;
                        halType = HalType::UNKNOWN;
                        QLOGE("IPerf:: Perf HAL Service 2.2 is not available.");
                    }
                }
            }
        }
    } catch (std::exception &e) {
        rc = false;
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        rc = false;
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

static bool getPerfServiceAndLinkToDeathV2_3() {
    bool rc = true;

    if (get_api_level() < S_API_LEVEL) {
        return false;
    }
    try {
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfHalV2_3 == NULL) {
            read_lock.unlock();
            {
                unique_lock<shared_timed_mutex> write_lock(gPerf_lock);
                if (gPerfHalV2_3 == NULL) {
                    gPerfHalV2_3 = V2_3_IPerf::tryGetService();
                    if (gPerfHalV2_3 != NULL) {
                        perfDeathRecipient = new(std::nothrow) PerfDeathRecipient();
                        if (perfDeathRecipient != NULL) {
                            android::hardware::Return<bool> linked = gPerfHalV2_3->linkToDeath(perfDeathRecipient, 0);
                            if (!linked || !linked.isOk()) {
                                gPerfHalV2_3 = NULL;
                                rc = false;
                                ALOGE("Unable to link to gPerfHalV2_3 death notifications!");
                            }
                        } else {
                            gPerfHalV2_3 = NULL;
                            rc = false;
                            ALOGE("Unable to link to gPerfHal 2.3 death notifications! memory error");
                        }
                    } else {
                        rc = false;
                        halType = HalType::UNKNOWN;
                        QLOGE("IPerf:: Perf HAL Service 2.3 is not available.");
                    }
                }
            }
        }
    } catch (std::exception &e) {
        rc = false;
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        rc = false;
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

static int processIntReturn(const Return<int32_t> &intReturn, const char* funcName) {
    int ret = FAILED;
    if (!intReturn.isOk()) {
        try {
            unique_lock<shared_timed_mutex> write_lock(gPerf_lock);
            gPerfHal = NULL;
            gPerfHalV2_3 = NULL;
            QLOGE("perf HAL service not available.");
        } catch (std::exception &e) {
            QLOGE("Exception caught: %s in %s", e.what(), __func__);
        } catch (...) {
            QLOGE("Exception caught in %s", __func__);
        }
    } else {
        ret = intReturn;
    }
    return ret;
}

static void processVoidReturn(const Return<void> &voidReturn, const char* funcName) {
    if (!voidReturn.isOk()) {
        try {
            unique_lock<shared_timed_mutex> write_lock(gPerf_lock);
            gPerfHal = NULL;
            gPerfHalV2_3 = NULL;
            QLOGE("perf HAL service not available.");
        } catch (std::exception &e) {
            QLOGE("Exception caught: %s in %s", e.what(), __func__);
        } catch (...) {
            QLOGE("Exception caught in %s", __func__);
        }
    }
}

bool isHalTypeAidl() {
    if (halType == HalType::UNKNOWN) {
        const std::string instance = std::string() + IPerfAidl::descriptor + "/default";
        halType = AServiceManager_isDeclared(instance.c_str()) ? HalType::AIDL : HalType::HIDL;
    }

    //extra check in case OEM declare both interface
    if (halType == HalType::AIDL && getPerfAidlAndLinkToDeath() == false)
        halType = HalType::HIDL;

    if (halType == HalType::AIDL)
        return true;
    else
        return false;
}

int perf_lock_acq(int handle, int duration, int list[], int numArgs)
{
    int rc = FAILED;

    QLOGI("inside %s of client", __func__);
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return rc;
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == false) {
            return rc;
        }
    }
    if (duration < 0 || numArgs <= 0 || numArgs > MAX_ARGS_PER_REQUEST)
        return FAILED;

    try {
        unique_lock<timed_mutex> lck(timeoutMutex,defer_lock);
        bool lck_acquired = lck.try_lock_for(CM(TIMEOUT));
        if (lck_acquired == false) {
            QLOGE("Process is Flooding request to PerfService");
            return rc;
        }

        std::vector<int32_t> boostsList;
        std::vector<int32_t> paramList;
        int32_t intReturn = 0;

        paramList.push_back(gettid());
        paramList.push_back(getpid());

        int32_t isMvOpcode = -1;
        uint32_t i = 0, j = 0;
        while (i < numArgs - 1) {
            int32_t opcode = list[i++];
            int32_t val = list[i++];

            boostsList.push_back(opcode);
            boostsList.push_back(val);

            isMvOpcode = (opcode & EXTRACT_OPCODE_TYPE) >> SHIFT_BIT_OPCODE_TYPE;
            QLOGI("isMvOpcode=%d opcode:%x value: %x", isMvOpcode, opcode, val);
            if(isMvOpcode == 1) {
                for(j=i; j<i+val;j++) {
                    paramList.push_back(list[j]);
                    QLOGI("isMvOpcode=%d opcode:%x value: %x", isMvOpcode, opcode, list[j]);
                }
                i+=val;
            }
        }

        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfLockAcquire(handle, duration, boostsList, paramList, &intReturn);
            read_lock.unlock();
            rc = intReturn;
        } else if (gPerfHal != NULL) {
            Return<int32_t> intReturn = gPerfHal->perfLockAcquire(handle, duration, boostsList, paramList);
            read_lock.unlock();
            rc = processIntReturn(intReturn, "perfLockAcquire");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

int perf_lock_rel(int handle)
{

    QLOGI("inside perf_lock_rel of client");
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return FAILED;
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == false) {
            return FAILED;
        }
    }
    if (handle == 0)
        return FAILED;

    try {
        std::vector<int32_t> paramList;
        paramList.push_back(gettid());
        paramList.push_back(getpid());

        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfLockRelease(handle, paramList);
            read_lock.unlock();
            return SUCCESS;
        } else if (gPerfHal != NULL) {
            Return<void> voidReturn = gPerfHal->perfLockRelease(handle, paramList);
            read_lock.unlock();
            processVoidReturn(voidReturn, "perfLockRelease");
            return SUCCESS;
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return FAILED;
}

int perf_hint_rel(int handle)
{

    QLOGI("inside perf_hint_rel of client");
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return FAILED;
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == false) {
            return FAILED;
        }
    }
    if (handle == 0)
        return FAILED;

    try {
        std::vector<int32_t> paramList;
        paramList.push_back(gettid());
        paramList.push_back(getpid());
        paramList.push_back(PERF_HINT_REL_VAL);

        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfLockRelease(handle, paramList);
            read_lock.unlock();
            return SUCCESS;
        } else if (gPerfHal != NULL) {
            Return<void> voidReturn = gPerfHal->perfLockRelease(handle, paramList);
            read_lock.unlock();
            processVoidReturn(voidReturn, "perfLockRelease");
            return SUCCESS;
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return FAILED;
}

int perf_hint(int hint, const char *pkg, int duration, int type)
{
    return perf_hint_private(hint, pkg, duration, type, DEFAULT_OFFLOAD_FLAG, 0, NULL);
}

int perf_hint_private(int hint, const char *pkg, int duration, int type, int offloadFlag, int numArgs, int list[])
{
    int rc = FAILED;

    QLOGI("inside perf_hint of client");
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return rc;
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == false) {
            return rc;
        }
    }

    if (numArgs > MAX_RESERVE_ARGS_PER_REQUEST || numArgs < 0) {
        QLOGE("Invalid number of arguments %d", numArgs);
        return FAILED;
    }
    try {
        unique_lock<timed_mutex> lck(timeoutMutex,defer_lock);
        bool lck_acquired = lck.try_lock_for(CM(TIMEOUT));
        if (lck_acquired == false) {
            QLOGE("Process is Flooding request to PerfService");
            return rc;
        }
        std::vector<int32_t> paramList;
        int32_t intReturn = 0;
        std::string pkg_s((pkg != NULL ? pkg: ""));
        paramList.assign(list, (list + numArgs));
        paramList.emplace(paramList.begin(),offloadFlag);
        paramList.emplace(paramList.begin(),getpid());
        paramList.emplace(paramList.begin(),gettid());
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfHint(hint, pkg_s, duration, type, paramList, &intReturn);
            read_lock.unlock();
            rc = intReturn;
        } else if (gPerfHal != NULL) {
            Return<int32_t> intReturn = gPerfHal->perfHint(hint,
                    pkg_s, duration, type, paramList);
            read_lock.unlock();
            rc = processIntReturn(intReturn, "perfHint");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

int perf_get_feedback(int req, const char *pkg) {

    QLOGI("inside %s of client", __func__);
    return perf_get_feedback_extn(req, pkg, 0, NULL);
}

PropVal perf_get_prop(const char *prop , const char *def_val) {

    pid_t client_tid = 0;
    PropVal return_value = {""};
    return_value.value[0] = '\0';
    char *out = NULL;

    QLOGI("inside %s of client", __func__);
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == true) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            out = (char *)malloc(PROP_VAL_LENGTH * sizeof(char));
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == true) {
            out = (char *)malloc(PROP_VAL_LENGTH * sizeof(char));
        }
    }

    if (out != NULL) {
        out[0] = '\0';
        client_tid = gettid();
        string aidlReturn = "";

        try {
            shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
            if (gPerfAidl != NULL) {
                gPerfAidl->perfGetProp(prop, def_val, &aidlReturn);
                read_lock.unlock();
                strlcpy(out, aidlReturn.c_str(), PROP_VAL_LENGTH);
                if (aidlReturn == "")
                    strlcpy(out, def_val, PROP_VAL_LENGTH);
            } else if (gPerfHal != NULL) {
                Return<void> voidReturn = gPerfHal->perfGetProp(prop, def_val,
                        [&out](const auto &res) { strlcpy(out,res.c_str(),PROP_VAL_LENGTH);});
                read_lock.unlock();
                processVoidReturn(voidReturn, "perfGetProp");
            } else if (def_val != NULL) {
                strlcpy(out, def_val, PROP_VAL_LENGTH);
            }
            strlcpy(return_value.value, out, PROP_VAL_LENGTH);
            QLOGI("VALUE RETURNED FOR PROPERTY %s :: %s", prop, return_value.value);
        } catch (std::exception &e) {
            QLOGE("Exception caught: %s in %s", e.what(), __func__);
        } catch (...) {
            QLOGE("Exception caught in %s", __func__);
        }
        free(out);
    } else if (def_val != NULL) {
        strlcpy(return_value.value, def_val, PROP_VAL_LENGTH);
    }
    return return_value;
}

/* this function returns a malloced string which must be freed by the caller */
const char* perf_sync_request(int cmd) {
    const char *out = NULL;

    QLOGI("perf_sync_request cmd:%d", cmd);
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return out;
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == false) {
            return out;
        }
    }

    try {
        std::vector<int32_t> paramList;
        string aidlReturn = "";
        paramList.push_back(gettid());
        paramList.push_back(getpid());
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfSyncRequest(cmd, "", paramList, &aidlReturn);
            read_lock.unlock();
            out = strdup(aidlReturn.c_str());
        } else if (gPerfHal != NULL) {
            Return<void> voidReturn = gPerfHal->perfSyncRequest(cmd, "", paramList,
                    [&out](const auto &res) {
                    out = strdup(res.c_str());
                    });
            read_lock.unlock();
            processVoidReturn(voidReturn, "perf_sync_request");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return out;
}

int perf_lock_acq_rel(int handle, int duration, int list[], int numArgs, int reserveNumArgs) {
    int rc = FAILED;

    QLOGI("inside %s of client", __func__);
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return rc;
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == false) {
            return rc;
        }
    }

    if (duration < 0 || numArgs <= 0 || numArgs > MAX_ARGS_PER_REQUEST ||
            reserveNumArgs < 0 || reserveNumArgs > MAX_RESERVE_ARGS_PER_REQUEST ||
            (reserveNumArgs + numArgs) > (MAX_ARGS_PER_REQUEST + MAX_RESERVE_ARGS_PER_REQUEST) ||
            (reserveNumArgs + numArgs) <= 0) {
        return FAILED;
    }
    try {
        unique_lock<timed_mutex> lck(timeoutMutex,defer_lock);
        bool lck_acquired = lck.try_lock_for(CM(TIMEOUT));
        if (lck_acquired == false) {
            QLOGE("Process is Flooding request to PerfService");
            return rc;
        }
        std::vector<int32_t> boostsList;
        std::vector<int32_t> reservedList;
        int32_t intReturn = 0;

        reservedList.assign(list + numArgs, (list + numArgs+ reserveNumArgs));
        reservedList.emplace(reservedList.begin(),getpid());
        reservedList.emplace(reservedList.begin(),gettid());

        int32_t isMvOpcode = -1;
        uint32_t i = 0, j = 0;
        while (i < numArgs - 1) {
            int32_t opcode = list[i++];
            int32_t val = list[i++];

            boostsList.push_back(opcode);
            boostsList.push_back(val);

            isMvOpcode = (opcode & EXTRACT_OPCODE_TYPE) >> SHIFT_BIT_OPCODE_TYPE;
            QLOGI("isMvOpcode=%d opcode:%x value: %x", isMvOpcode, opcode, val);
            if(isMvOpcode == 1) {
                for(j=i; j<i+val;j++) {
                    reservedList.push_back(list[j]);
                    QLOGI("isMvOpcode=%d opcode:%x value: %x", isMvOpcode, opcode, list[j]);
                }
                i+=val;
            }
        }

        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfLockAcqAndRelease(handle, duration, boostsList, reservedList, &intReturn);
            read_lock.unlock();
            rc = intReturn;
        } else if (gPerfHal != NULL) {
            Return<int32_t> intReturn = gPerfHal->perfLockAcqAndRelease(handle, duration, boostsList, reservedList);
            read_lock.unlock();
            rc = processIntReturn(intReturn, "perfLockAcqAndRelease");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

int perf_get_feedback_extn(int req, const char *pkg, int numArgs, int list[]) {
    int rc = FAILED;

    QLOGI("inside %s of client", __func__);
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return rc;
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == false) {
            return rc;
        }
    }

    if (numArgs > MAX_RESERVE_ARGS_PER_REQUEST || numArgs < 0) {
        QLOGE("Invalid number of arguments %d", numArgs);
        return FAILED;
    }
    try {
        std::vector<int32_t> reservedList;
        int32_t intReturn = 0;
        std::string pkg_s((pkg != NULL ? pkg: ""));
        reservedList.assign(list, (list + numArgs));
        reservedList.emplace(reservedList.begin(),DEFAULT_OFFLOAD_FLAG);
        reservedList.emplace(reservedList.begin(),getpid());
        reservedList.emplace(reservedList.begin(),gettid());
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfGetFeedback(req, pkg_s, reservedList, &intReturn);
            read_lock.unlock();
            rc = intReturn;
        } else if (gPerfHal != NULL) {
            Return<int32_t> intReturn = gPerfHal->perfGetFeedback(req, pkg_s, reservedList);
            read_lock.unlock();
            rc = processIntReturn(intReturn, "perfGetFeedback");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

void perf_event(int eventId, const char *pkg, int numArgs, int list[]) {
    QLOGI("inside %s of client", __func__);
    perf_event_private(eventId, pkg, DEFAULT_OFFLOAD_FLAG, numArgs, list);
}

void perf_event_private(int eventId, const char *pkg, int offloadFlag, int numArgs, int list[]) {

    QLOGI("inside %s of client", __func__);
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return;
        }
    } else {
        if (getPerfServiceAndLinkToDeath() == false) {
            return;
        }
    }

    if (numArgs > MAX_RESERVE_ARGS_PER_REQUEST || numArgs < 0) {
        QLOGE("Invalid number of arguments %d", numArgs);
        return;
    }
    try {
        unique_lock<timed_mutex> lck(timeoutMutex,defer_lock);
        bool lck_acquired = lck.try_lock_for(CM(TIMEOUT));
        if (lck_acquired == false) {
            QLOGE("Process is Flooding request to PerfService");
            return;
        }
        std::vector<int32_t> reservedList;
        std::string pkg_s((pkg != NULL ? pkg: ""));
        reservedList.assign(list, (list + numArgs));
        reservedList.emplace(reservedList.begin(),offloadFlag);
        reservedList.emplace(reservedList.begin(),getpid());
        reservedList.emplace(reservedList.begin(),gettid());

        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfEvent(eventId, pkg_s, reservedList);
            read_lock.unlock();
        } else if (gPerfHal != NULL) {
            Return<void> voidReturn = gPerfHal->perfEvent(eventId, pkg_s, reservedList);
            read_lock.unlock();
            processVoidReturn(voidReturn, "perfEvent");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
}

int perf_hint_acq_rel(int handle, int hint, const char *pkg, int duration, int type,
        int numArgs, int list[]) {
    QLOGI("inside %s of client", __func__);
    return perf_hint_acq_rel_private(handle, hint, pkg, duration, type, DEFAULT_OFFLOAD_FLAG, numArgs, list);
}

int perf_hint_acq_rel_private(int handle, int hint, const char *pkg, int duration, int type,
        int offloadFlag, int numArgs, int list[]) {
    int rc = FAILED;

    QLOGI("inside %s of client", __func__);
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return rc;
        }
    } else {
        if (getPerfServiceAndLinkToDeathV2_3() == false) {
            return rc;
        }
    }

    if (numArgs > MAX_RESERVE_ARGS_PER_REQUEST || numArgs < 0) {
        QLOGE("Invalid number of arguments %d", numArgs);
        return FAILED;
    }
    try {
        unique_lock<timed_mutex> lck(timeoutMutex,defer_lock);
        bool lck_acquired = lck.try_lock_for(CM(TIMEOUT));
        if (lck_acquired == false) {
            QLOGE("Process is Flooding request to PerfService");
            return rc;
        }
        std::vector<int32_t> reservedList;
        int32_t intReturn = 0;
        std::string pkg_s((pkg != NULL ? pkg: ""));
        reservedList.assign(list, (list + numArgs));
        reservedList.emplace(reservedList.begin(),offloadFlag);
        reservedList.emplace(reservedList.begin(),getpid());
        reservedList.emplace(reservedList.begin(),gettid());
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfHintAcqRel(handle, hint,
                    pkg_s, duration, type, reservedList, &intReturn);
            read_lock.unlock();
            rc = intReturn;
        } else if (gPerfHalV2_3 != NULL) {
            Return<int32_t> intReturn = gPerfHalV2_3->perfHintAcqRel(handle, hint,
                    pkg_s, duration, type, reservedList);
            read_lock.unlock();
            rc = processIntReturn(intReturn, "perfHintAcqRel");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

int perf_hint_renew(int handle, int hint, const char *pkg, int duration, int type,
        int numArgs, int list[]) {
    int rc = FAILED;

    QLOGI("inside %s of client", __func__);
    if (isHalTypeAidl()) {
        if (getPerfAidlAndLinkToDeath() == false) {
            QLOGI("getPerfAidlAndLinkToDeath failed");
            return rc;
        }
    } else {
        if (getPerfServiceAndLinkToDeathV2_3() == false) {
            return rc;
        }
    }

    if (numArgs > MAX_RESERVE_ARGS_PER_REQUEST || numArgs < 0) {
        QLOGE("Invalid number of arguments %d", numArgs);
        return FAILED;
    }
    try {
        unique_lock<timed_mutex> lck(timeoutMutex,defer_lock);
        bool lck_acquired = lck.try_lock_for(CM(TIMEOUT));
        if (lck_acquired == false) {
            QLOGE("Process is Flooding request to PerfService");
            return rc;
        }
        std::vector<int32_t> reservedList;
        int32_t intReturn = 0;
        std::string pkg_s((pkg != NULL ? pkg: ""));
        reservedList.assign(list, (list + numArgs));
        reservedList.emplace(reservedList.begin(),getpid());
        reservedList.emplace(reservedList.begin(),gettid());
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfAidl != NULL) {
            gPerfAidl->perfHintRenew(handle, hint,
                    pkg_s, duration, type, reservedList, &intReturn);
            read_lock.unlock();
            rc = intReturn;
        } else if (gPerfHalV2_3 != NULL) {
            Return<int32_t> intReturn = gPerfHalV2_3->perfHintRenew(handle, hint,
                    pkg_s, duration, type, reservedList);
            read_lock.unlock();
            rc = processIntReturn(intReturn, "perfHintRenew");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;
}

/*
below are thread based offload API's
*/
int perf_hint_offload(int hint, const char *pkg, int duration, int type, int listlen, int list[]) {
    QLOGI("inside %s of client", __func__);
    int rc = FAILED;
    char *pkg_t = NULL;
    int *list_t = NULL;

    if (listlen > MAX_RESERVE_ARGS_PER_REQUEST || listlen < 0) {
        QLOGE("Invalid number of arguments %d Max Accepted %d", listlen, MAX_RESERVE_ARGS_PER_REQUEST);
        return FAILED;
    }

    if (!is_perf_hal_alive() || !is_perf_init_completed()) {
        return rc;
    }

    PerfThreadPool &ptp = PerfThreadPool::getPerfThreadPool(THREADS_IN_POOL);
    PerfOffloadHelper &poh = PerfOffloadHelper::getPerfOffloadHelper();
    int offload_handle = poh.getNewOffloadHandle();
    if (offload_handle == FAILED) {
        QLOGE("Failed to create Offload Handle in %s", __func__);
        return rc;
    }

    if (pkg != NULL) {
        int len = strlen(pkg);
        pkg_t = (char*)calloc(len + 1,sizeof(char));
        if (pkg_t == NULL) {
            QLOGE("Memory alloc error in %s", __func__);
            return rc;
        }
        strlcpy(pkg_t, pkg, len);
        pkg_t[len] = '\0';
    }

    if (listlen > 0) {
        list_t = (int*)calloc(listlen, sizeof(int));
        if (list_t == NULL) {
            QLOGE("Memory alloc error in %s", __func__);
            if (pkg_t != NULL) {
                free(pkg_t);
                pkg_t = NULL;
            }
            return rc;
        }
        memcpy(list_t, list, sizeof(int) * listlen);
    }

    try {
        rc = ptp.placeTask([=]() mutable {
                    int handle = perf_hint_private(hint, pkg_t, duration, type, SET_OFFLOAD_FLAG, listlen, list_t);
                    PerfOffloadHelper &poh = PerfOffloadHelper::getPerfOffloadHelper();
                    int res = poh.mapPerfHandle(offload_handle, handle);
                    if (res == RELEASE_REQ_STATE) {
                        perf_lock_rel(handle);
                        poh.releaseHandle(offload_handle);
                    }
                    if (pkg_t != NULL) {
                       free(pkg_t);
                       pkg_t = NULL;
                    }
                    if (list_t != NULL) {
                        free(list_t);
                        list_t = NULL;
                    }
                });
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    if (rc == FAILED) {
        QLOGE("Failed to Offload hint: 0x%X", hint);
        if (pkg_t != NULL) {
            free(pkg_t);
            pkg_t = NULL;
        }
        if (list_t != NULL) {
            free(list_t);
            list_t = NULL;
        }
    } else {
        rc = offload_handle;
    }
    return rc;
}

int perf_lock_rel_offload(int handle) {
    QLOGI("inside %s of client", __func__);
    int rc = FAILED;
    if (!is_perf_hal_alive()) {
        return rc;
    }
    PerfThreadPool &ptp = PerfThreadPool::getPerfThreadPool(THREADS_IN_POOL);

    try {
        rc = ptp.placeTask([=]() {
                PerfOffloadHelper &poh = PerfOffloadHelper::getPerfOffloadHelper();
                int releaseHandle = poh.getPerfHandle(handle);
                    if (releaseHandle > 0) {
                        int p_handle = perf_lock_rel(releaseHandle);
                        poh.releaseHandle(handle);
                    }
                });
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    if (rc == FAILED) {
        QLOGE("Failed to Offload release for %d", handle);
    }
    return rc;
}

int perf_hint_acq_rel_offload(int handle, int hint, const char *pkg, int duration, int type, int numArgs, int list[]) {
    QLOGI("inside %s of client", __func__);
    int rc = FAILED;
    char *pkg_t = NULL;
    int *list_t = NULL;

    if (numArgs > MAX_RESERVE_ARGS_PER_REQUEST || numArgs < 0) {
        QLOGE("Invalid number of arguments %d Max Accepted %d", numArgs, MAX_RESERVE_ARGS_PER_REQUEST);
        return FAILED;
    }

    if (!is_perf_hal_alive() || !is_perf_init_completed()) {
        return rc;
    }

    PerfThreadPool &ptp = PerfThreadPool::getPerfThreadPool(THREADS_IN_POOL);
    PerfOffloadHelper &poh = PerfOffloadHelper::getPerfOffloadHelper();
    int offload_handle = poh.getNewOffloadHandle();
    if (offload_handle == FAILED) {
        QLOGE("Failed to create Offload Handle in %s", __func__);
        return rc;
    }

    if (pkg != NULL) {
        int len = strlen(pkg);
        pkg_t = (char*)calloc(len + 1,sizeof(char));
        if (pkg_t == NULL) {
            QLOGE("Memory alloc error in %s", __func__);
            return rc;
        }
        strlcpy(pkg_t, pkg, len);
        pkg_t[len] = '\0';
    }
    if (numArgs > 0) {
        list_t = (int*)calloc(numArgs, sizeof(int));
        if (list_t == NULL) {
            QLOGE("Memory alloc error in %s", __func__);
            if (pkg_t != NULL) {
                free(pkg_t);
                pkg_t = NULL;
            }
            return rc;
        }
        memcpy(list_t, list, sizeof(int) * numArgs);
    }

    try {
        rc = ptp.placeTask([=]() mutable {
                PerfOffloadHelper &poh = PerfOffloadHelper::getPerfOffloadHelper();
                int releaseHandle = poh.getPerfHandle(handle);
                QLOGI("%s releasing %d mapped to %d",__func__,releaseHandle, handle);
                int p_handle = perf_hint_acq_rel_private(releaseHandle, hint, pkg_t, duration, type, SET_OFFLOAD_FLAG, numArgs, list_t);
                int res = poh.mapPerfHandle(offload_handle, p_handle);
                if (res == RELEASE_REQ_STATE) {
                    perf_lock_rel(p_handle);
                    poh.releaseHandle(offload_handle);
                }
                if (releaseHandle > 0) {
                    poh.releaseHandle(handle);
                }

                if (pkg_t != NULL) {
                    free(pkg_t);
                    pkg_t = NULL;
                }
                if (list_t != NULL) {
                    free(list_t);
                    list_t = NULL;
                }
        });
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    if (rc == FAILED) {
        QLOGE("Failed to Offload hint: 0x%X", hint);
        if (pkg_t != NULL) {
            free(pkg_t);
            pkg_t = NULL;
        }
        if (list_t != NULL) {
            free(list_t);
            list_t = NULL;
        }
    } else {
        rc = offload_handle;
    }
    return rc;
}

int perf_event_offload(int eventId, const char *pkg, int numArgs, int list[]) {
    QLOGI("inside %s of client", __func__);
    int rc = FAILED;
    char *pkg_t = NULL;
    int *list_t = NULL;

    if (numArgs > MAX_RESERVE_ARGS_PER_REQUEST || numArgs < 0) {
        QLOGE("Invalid number of arguments %d", numArgs);
        return FAILED;
    }
    if (!is_perf_hal_alive() || !is_perf_init_completed()) {
        return rc;
    }

    if (pkg != NULL) {
        int len = strlen(pkg);
        pkg_t = (char*)calloc(len + 1,sizeof(char));
        if (pkg_t == NULL) {
            QLOGE("Memory alloc error in %s", __func__);
            return rc;
        }
        strlcpy(pkg_t, pkg, len);
        pkg_t[len] = '\0';
    }
    if (numArgs > 0) {
        list_t = (int*)calloc(numArgs, sizeof(int));
        if (list_t == NULL) {
            QLOGE("Memory alloc error in %s", __func__);
            if (pkg_t != NULL) {
                free(pkg_t);
                pkg_t = NULL;
            }
            return rc;
        }
        memcpy(list_t, list, sizeof(int) * numArgs);
    }

    PerfThreadPool &ptp = PerfThreadPool::getPerfThreadPool(THREADS_IN_POOL);
    try {
        rc = ptp.placeTask([=]() mutable {
                    perf_event_private(eventId, pkg_t, SET_OFFLOAD_FLAG, numArgs, list_t);
                    if (pkg_t != NULL) {
                        free(pkg_t);
                        pkg_t = NULL;
                    }
                    if (list_t != NULL) {
                        free(list_t);
                        list_t = NULL;
                    }
                });
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    if (rc == FAILED) {
        QLOGE("Failed to Offload event: 0x%X", eventId);
        if (pkg_t != NULL) {
            free(pkg_t);
            pkg_t = NULL;
        }
        if (list_t != NULL) {
            free(list_t);
            list_t = NULL;
        }
    }
    return rc;
}

static bool is_perf_hal_alive() {
    QLOGI("inside %s of client", __func__);
    if (isHalTypeAidl()) {
        return getPerfAidlAndLinkToDeath();
    } else {
        return (getPerfServiceAndLinkToDeathV2_3() || getPerfServiceAndLinkToDeath());
    }
    return false;
}

static bool is_perf_init_completed() {
    QLOGI("inside %s of client", __func__);
    int32_t handle = FAILED;
    if (perf_init_completed_flag == true) {
        return perf_init_completed_flag;
    }
    try {
        unique_lock<timed_mutex> lck(perfInitTimeoutMutex, defer_lock);
        bool lck_acquired = lck.try_lock_for(CM(TIMEOUT));
        if (lck_acquired == false) {
            QLOGE("Process is Flooding request to PerfService");
        } else {
            if (perf_init_completed_flag != true)  {
                handle = perf_hint(VENDOR_HINT_PERF_INIT_CHECK, NULL, 0, -1);
                if (handle != INIT_NOT_COMPLETED) {
                    perf_init_completed_flag = true;
                }
            }
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return perf_init_completed_flag;
}

int get_api_level() {
    QLOGI("inside %s of client", __func__);
    if (api_level == 0) {
        char prop_val[PROPERTY_VALUE_MAX];
        if (property_get(API_LEVEL_PROP_NAME, prop_val, "0")) {
            api_level = atoi(prop_val);
        }
    }
    QLOGI("%s %d", __func__,api_level);
    return api_level;
}

double get_perf_hal_ver() {
    QLOGI("inside %s of client", __func__);
    if (perf_hal_ver < 2.2) {
        if (get_api_level() > R_API_LEVEL) {
            perf_hal_ver = 2.3;
        } else {
            perf_hal_ver = 2.2;
        }
    }
    return perf_hal_ver;
}

//below are the deprecated API's from perf-hal 2.3 onwards
int perf_lock_use_profile(int handle, int profile)
{
    int rc = -1;
    pid_t client_tid = 0;

    QLOGI("inside perf_lock_use_profile of client");
    if (perf_hal_ver > 2.2 || get_perf_hal_ver() > 2.2) {
        QLOGE("%s is deprecated from perf-hal 2.3", __func__);
        return rc;
    }

    if (getPerfServiceAndLinkToDeath() == false) {
        return rc;
    }

    client_tid = gettid();
    try {
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfHal != NULL) {
            Return<int32_t> intReturn = gPerfHal->perfProfile(handle, profile, client_tid);
            read_lock.unlock();
            rc = processIntReturn(intReturn, "perfProfile");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
    return rc;

}

void perf_lock_cmd(int cmd)
{
    if (perf_hal_ver > 2.2 || get_perf_hal_ver() > 2.2) {
        QLOGE("%s is deprecated from perf-hal 2.3", __func__);
        return;
    }
    if (getPerfServiceAndLinkToDeath() == false) {
        return;
    }
    pid_t client_tid = 0;

    QLOGI("inside perf_lock_cmd of client");
    client_tid = gettid();
    try {
        shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
        if (gPerfHal != NULL) {
            Return<void> voidReturn = gPerfHal->perfLockCmd(cmd, client_tid);
            read_lock.unlock();
            processVoidReturn(voidReturn, "perfLockCmd");
        }
    } catch (std::exception &e) {
        QLOGE("Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE("Exception caught in %s", __func__);
    }
}

int perf_callback_register(const std::shared_ptr<IPerfCallbackAidl>& callBack, int32_t clientId) {
    int rc = FAILED;
    if (getPerfAidlAndLinkToDeath() == false) {
        return rc;
    }
    if (callBack != nullptr) {
        try {
            int32_t intReturn = 0;
            shared_lock<shared_timed_mutex> read_lock(gPerf_lock);
            if (gPerfAidl) {
                gPerfAidl->perfCallbackRegister(callBack, clientId, &intReturn);
                read_lock.unlock();
                rc = intReturn;
            } else {
                QLOGE(LOG_TAG, "NULL gPerfAidl when perf_callback_register");
            }
        } catch (std::exception &e) {
            QLOGE("Exception caught: %s in %s", e.what(), __func__);
        } catch (...) {
            QLOGE("Exception caught in %s", __func__);
        }
    } else {
        QLOGE(LOG_TAG, "Invalid empty registercallback");
    }
    return rc;
}

PropVal perf_wait_get_prop(const char *prop , const char *def_val) {

    pid_t client_tid = 0;
    PropVal return_value = {""};
    return_value.value[0] = '\0';
    char *out = NULL;
    static sp<V2_2_IPerf> gPerfHal_new = NULL;
    if (perf_hal_ver > 2.2 || get_perf_hal_ver() > 2.2) {
        QLOGE("%s is deprecated from perf-hal 2.3", __func__);
    } else {
        out = (char *)malloc(PROP_VAL_LENGTH * sizeof(char));
        gPerfHal_new = V2_2_IPerf::getService();
    }

    if (out != NULL) {
        out[0] = '\0';
        client_tid = gettid();
        if (gPerfHal_new != NULL) {
           Return<void> voidReturn = gPerfHal_new->perfGetProp(prop, def_val, [&out](const auto &res) { strlcpy(out,res.c_str(),PROP_VAL_LENGTH);});
        } else if (def_val != NULL) {
           strlcpy(out, def_val, PROP_VAL_LENGTH);
        }
        strlcpy(return_value.value, out, PROP_VAL_LENGTH);
        QLOGI("VALUE RETURNED FOR PROPERTY %s :: %s ",prop,return_value.value);
        free(out);
    } else if (def_val != NULL) {
        strlcpy(return_value.value, def_val, PROP_VAL_LENGTH);
    }
    return return_value;
}
//end of deprecated API's
