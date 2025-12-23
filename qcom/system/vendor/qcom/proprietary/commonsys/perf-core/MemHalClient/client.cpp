/******************************************************************************
  @file    client.cpp
  @brief   Android performance MemHal library

  DESCRIPTION

  ---------------------------------------------------------------------------

  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
  All rights reserved.
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
#include <cerrno>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include <cutils/properties.h>
#include "client.h"

#include <aidl/vendor/qti/MemHal/BnMemHal.h>
#include <pthread.h>
#include <log/log.h>
#include <android-base/file.h>
#include "MemHalResources.h"
using std::unique_lock;
using std::shared_lock;
using std::shared_timed_mutex;
using std::timed_mutex;
using std::defer_lock;
using CM = std::chrono::milliseconds;

using IMemHalAidl = ::aidl::vendor::qti::MemHal::IMemHal;
using ::ndk::SpAIBinder;

#define FAILED                 -1
#define SUCCESS                 0
#define LOWER_SAFELOAD_LIMIT 10
#define UPPER_SAFELOAD_LIMIT 40

static std::shared_ptr<IMemHalAidl> gMemHalAidl = nullptr;
static shared_timed_mutex gMemHal_lock;

void serviceDiedAidl(void* /*cookie*/) {
    try {
        unique_lock<shared_timed_mutex> write_lock(gMemHal_lock);
        gMemHalAidl = nullptr;
        QLOGI(LOG_TAG, "MemHal hal just died");
    } catch (std::exception &e) {
        QLOGE(LOG_TAG, "Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE(LOG_TAG, "Exception caught in %s", __func__);
    }
}

static bool getMemHalAidlAndLinkToDeath() {
    bool rc = true;

    try {
        shared_lock<shared_timed_mutex> read_lock(gMemHal_lock);
        if (gMemHalAidl == nullptr) {
            read_lock.unlock();
            {
                unique_lock<shared_timed_mutex> write_lock(gMemHal_lock);

                if (gMemHalAidl == nullptr) {
                    const std::string instance = std::string() + IMemHalAidl::descriptor + "/default";
                    auto MemHalBinder = ::ndk::SpAIBinder(AServiceManager_waitForService(instance.c_str()));

                    if (MemHalBinder.get() != nullptr) {
                        gMemHalAidl = IMemHalAidl::fromBinder(MemHalBinder);
                        if (AIBinder_isRemote(MemHalBinder.get())) {
                            auto deathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(AIBinder_DeathRecipient_new(&serviceDiedAidl));
                            auto status = ::ndk::ScopedAStatus::fromStatus(AIBinder_linkToDeath(MemHalBinder.get(), deathRecipient.get(), (void*) serviceDiedAidl));
                            if (!status.isOk()) {
                                gMemHalAidl = nullptr;
                                rc = false;
                                QLOGE(LOG_TAG, "linking MemHal aidl service to death failed: %d: %s", status.getStatus(), status.getMessage());
                            }
                        }
                    } else {
                        QLOGE(LOG_TAG, "MemHal aidl service doesn't exist");
                        rc = false;
                    }
                }
            }
        }
    } catch (std::exception &e) {
        rc = false;
        QLOGE(LOG_TAG, "Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        rc = false;
        QLOGE(LOG_TAG, "Exception caught in %s", __func__);
    }
    return rc;
}

std::string get_self_name() {
    std::string content = "";
    if (!android::base::ReadFileToString("/proc/self/cmdline", &content)) {
        content = "";
    }
    return content;
}



const char* MemHal_GetProp_extn(const std::string& in_propname, const std::string& in_defaultVal, const std::vector<int32_t>& in_req_details) {

    const char* rc = NULL;
    std::string selfName = get_self_name();
    QLOGI(LOG_TAG, "%s : inside %s of client ", selfName.c_str(), __func__);
    if (getMemHalAidlAndLinkToDeath() == false) {
        return rc;
    }

    try {
        std::string StringReturn = " ";
        shared_lock<shared_timed_mutex> read_lock(gMemHal_lock);
        if (gMemHalAidl != NULL) {
            gMemHalAidl->MemHal_GetProp(in_propname, in_defaultVal, in_req_details, &StringReturn);
            rc = strdup(StringReturn.c_str());
        }
        read_lock.unlock();
    } catch (std::exception &e) {
        QLOGE(LOG_TAG, "Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE(LOG_TAG, "Exception caught in %s", __func__);
    }
    return rc;
}
int32_t MemHal_SetProp_extn(const std::string& in_propname, const std::string& in_NewVal, const std::vector<int32_t>& in_req_details) {
    int32_t rc = -1;
    std::string selfName = get_self_name();
    QLOGI(LOG_TAG, "%s : inside %s of client ", selfName.c_str(), __func__);
    if (getMemHalAidlAndLinkToDeath() == false) {
        return rc;
    }

    try {
        int32_t intReturn = 0;
        shared_lock<shared_timed_mutex> read_lock(gMemHal_lock);
        if (gMemHalAidl != NULL) {
            gMemHalAidl->MemHal_SetProp(in_propname, in_NewVal, in_req_details, &intReturn);
            rc=intReturn;
        }
        read_lock.unlock();
    } catch (std::exception &e) {
        QLOGE(LOG_TAG, "Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE(LOG_TAG, "Exception caught in %s", __func__);
    }
    return rc;
}

int32_t MemHal_SubmitRequest_extn(const std::vector<int32_t>& in_in_list, const std::vector<int32_t>& in_req_details) {
    int32_t rc = -1;
    std::string selfName = get_self_name();
    QLOGI(LOG_TAG, "%s : inside %s of client ", selfName.c_str(), __func__);
    if (getMemHalAidlAndLinkToDeath() == false) {
        return rc;
    }

    try {
        int32_t intReturn = 0;
        if (in_in_list[0] == REQUEST_BOOST_MODE) {
            if (in_in_list[1] < LOWER_SAFELOAD_LIMIT || in_in_list[1] > UPPER_SAFELOAD_LIMIT) {
                QLOGE(LOG_TAG, "Safeload not in the range [%d, %d]", LOWER_SAFELOAD_LIMIT, UPPER_SAFELOAD_LIMIT);
                return rc;
            }
        }
        shared_lock<shared_timed_mutex> read_lock(gMemHal_lock);
        if (gMemHalAidl != NULL) {
            gMemHalAidl->MemHal_SubmitRequest(in_in_list, in_req_details, &intReturn);
            rc = intReturn;
        }
        read_lock.unlock();
    } catch (std::exception &e) {
        QLOGE(LOG_TAG, "Exception caught: %s in %s", e.what(), __func__);
    } catch (...) {
        QLOGE(LOG_TAG, "Exception caught in %s", __func__);
    }
    return rc;
}