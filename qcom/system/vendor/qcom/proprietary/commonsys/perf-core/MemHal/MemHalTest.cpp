/******************************************************************************
  @file    MemHalTest.cpp
  @brief   Memory Hal test binary which loads hal client to make call to hal

  ---------------------------------------------------------------------------
  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
  All rights reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
  ---------------------------------------------------------------------------
 ******************************************************************************/
#define LOG_TAG           "MemHalAPI-TESTER-SYSTEM"
#include <dlfcn.h>
#include <errno.h>
#include <stdio.h>
#include <time.h>
#include <string>
#include <cutils/trace.h>
#include <cutils/properties.h>
#include <log/log.h>
#include <vector>
#include <android/log.h>
#include <MemHalResources.h>
#define QLOGE(...)    ALOGE(__VA_ARGS__)
#define QLOGW(...)    ALOGW(__VA_ARGS__)
#define QLOGI(...)    ALOGI(__VA_ARGS__)
#define QLOGV(...)    ALOGV(__VA_ARGS__)
#define QLOGD(...)    ALOGV(__VA_ARGS__)
#define QCLOGE(...)   ALOGE(__VA_ARGS__)

#define qcopt_lib_path "/system_ext/lib64/libqti-MemHal-client-system.so"

using namespace std;

static const char* (*MemHal_GetProp_extn)(const string& in_propname, const string& in_defaultVal, const vector<int32_t>& in_req_details) = NULL ;
static int32_t (*MemHal_SetProp_extn)(const string& in_propname, const string& in_NewVal, const vector<int32_t>& in_req_details) = NULL ;
static int32_t (*MemHal_SubmitRequest_extn)(const vector<int32_t>& in_in_list, const vector<int32_t>& in_req_details) = NULL ;

static void *libhandle = NULL;

static void initialize(void) {
    const char *rc = NULL;

    QLOGD(LOG_TAG, "initialize called");
    dlerror();
    QLOGI(LOG_TAG, "lib name %s", qcopt_lib_path);
    libhandle = dlopen(qcopt_lib_path, RTLD_NOW);
    if (!libhandle) {
        QLOGE(LOG_TAG, "Unable to open %s: %s", qcopt_lib_path, dlerror());
        //printf("Failed to get qcopt handle from lib %s. Refer logcat for error info", qcopt_lib_path);
        return;
    }
    QLOGD(LOG_TAG, "Opening handle to functions defined in client");

    *(void **) (&MemHal_GetProp_extn) = dlsym(libhandle, "MemHal_GetProp_extn");
    if ((rc = dlerror()) != NULL) {
        QLOGE(LOG_TAG, "Unable to get MemHal_GetProp_extn function handle. %s", dlerror());
        //printf("Unable to get MemHal_GetProp_extn function handle. Refer logcat for more info");
        MemHal_GetProp_extn = NULL;
    }
    if(MemHal_GetProp_extn != NULL)
    {
        QLOGD(LOG_TAG, "Succesfully Opened handle to function MemHal_GetProp_extn defined in client.");
    }

    *(void **) (&MemHal_SetProp_extn) = dlsym(libhandle, "MemHal_SetProp_extn");
    if ((rc = dlerror()) != NULL) {
        QLOGE(LOG_TAG, "Unable to get MemHal_SetProp_extn function handle. %s", dlerror());
        //printf("Unable to get MemHal_SetProp_extn function handle. Refer logcat for more info");
        MemHal_SetProp_extn = NULL;
    }
    if(MemHal_SetProp_extn != NULL)
    {
        QLOGD(LOG_TAG, "Succesfully Opened handle to function MemHal_SetProp_extn defined in client.");
    }

    *(void **) (&MemHal_SubmitRequest_extn) = dlsym(libhandle, "MemHal_SubmitRequest_extn");
    if ((rc = dlerror()) != NULL) {
        QLOGE(LOG_TAG, "Unable to get MemHal_SubmitRequest_extn function handle. %s", dlerror());
        //printf("Unable to get MemHal_SubmitRequest_extn function handle. Refer logcat for more info");
        MemHal_SubmitRequest_extn = NULL;
    }
    if(MemHal_SubmitRequest_extn != NULL)
    {
        QLOGD(LOG_TAG, "Succesfully Opened handle to function MemHal_SubmitRequest_extn defined in client.");
    }
    return;
}

void test_usage() {
    QLOGI(LOG_TAG, "Test usage: MemHalTest <request type> \
    <arg1 for request type> [optional arg2 for request type]");
    QLOGI(LOG_TAG, "<request type> has following values- \n \
    REQUEST_FREE_MEMORY \n \
    REQUEST_BOOST_MODE \n \
    VENDOR_GET_PROPERTY_REQUEST \n \
    <arg1 for request type> has following values for respective request types- \n \
    REQUEST_FREE_MEMORY- \n \
        MEMORY_ANON \n \
        MEMORY_ION_POOL \n \
        MEMORY_ANY \n \
    REQUEST_BOOST_MODE- \n \
        <safeload value b/w 0.1 and 0.4> \n \
    VENDOR_GET_PROPERTY_REQUEST- \n \
        <property name as defined in perfconfigstore.xml> \n \
    [optional arg2 for request type] has following values for specific requests- \n \
    REQUEST_FREE_MEMORY- \n \
        <Amount of respective type of free memory required in MB. \n \
    VENDOR_GET_PROPERTY_REQUEST- \n \
        <default value to return incase property is not defined in perfconfigstore.xml>");
}

int main(int argc, char *argv[]) {
    //printf("MemHalTest Started with %d args\n", argc);
    QLOGD(LOG_TAG, "main: MemHal MemHalTest Started with %d args.", argc);
    initialize();
    int32_t rc = -1, req_type = -1, mem_type = -1;
    QLOGD(LOG_TAG, "main: Calling API'S defined in MemHal client from MemHalTest");
    vector<int32_t>in_list, paramList;
    paramList.push_back(getpid());
    paramList.push_back(gettid());
    QLOGI(LOG_TAG, "argv[0] = %s, argv[1] = %s, argv[2] = %s, argv[3] = %s", argv[0], argv[1], argv[2], argv[3]);

    if (argc < 3){
        QLOGE(LOG_TAG, "Memhaltest requires atleast 2 arguments. %d provided.", argc - 1);
        test_usage();
        return 0;
    }
    if (!strncmp(argv[1], "REQUEST_FREE_MEMORY", strlen("REQUEST_FREE_MEMORY")))
        req_type = REQUEST_FREE_MEMORY;
    else if (!strncmp(argv[1], "REQUEST_BOOST_MODE", strlen("REQUEST_BOOST_MODE")))
        req_type = REQUEST_BOOST_MODE;
    else if (!strncmp(argv[1], "VENDOR_GET_PROPERTY_REQUEST", strlen("VENDOR_GET_PROPERTY_REQUEST")))
        req_type = VENDOR_GET_PROPERTY_REQUEST;

    if (!strncmp(argv[2], "MEMORY_ANON", strlen("MEMORY_ANON")))
        mem_type = MEMORY_ANON;
    else if (!strncmp(argv[2], "MEMORY_ION_POOL", strlen("MEMORY_ION_POOL")))
        mem_type = MEMORY_ION_POOL;
    else if (!strncmp(argv[2], "MEMORY_ANY", strlen("MEMORY_ANY")))
        mem_type = MEMORY_ANY;

    switch (req_type) {
        case REQUEST_FREE_MEMORY:
            if (argc != 4) {
                QLOGE(LOG_TAG, "REQUEST_FREE_MEMORY requires 3 arguments");
                test_usage();
                return -1;
            }
            in_list.push_back(REQUEST_FREE_MEMORY);
            if (mem_type != -1)
                in_list.push_back(mem_type);
            else {
                QLOGE(LOG_TAG, "Invalid memtype to free = %s", argv[2]);
                test_usage();
                return -1;
            }
            in_list.push_back(atoi((argv[3])));
            break;
        case REQUEST_BOOST_MODE:
            in_list.push_back(REQUEST_BOOST_MODE);
            in_list.push_back(static_cast<int>(strtof(argv[2], NULL) * 100));
            break;
        case VENDOR_GET_PROPERTY_REQUEST:
            if(MemHal_GetProp_extn != NULL) {
            if (MemHal_GetProp_extn(argv[2], argv[3], paramList) != NULL) {
                QLOGD(LOG_TAG,"main: MemHal_GetProp_extn call success");
                return 0;
            }
            else {
                QLOGE(LOG_TAG,"main: MemHal_GetProp_extn call failed");
                return -1;
            }
        default:
            QLOGE(LOG_TAG,"Invalid request type = %s", argv[1]);
            test_usage();
            return -1;
        }
    }

    if(MemHal_SubmitRequest_extn != NULL){
        rc = MemHal_SubmitRequest_extn(in_list, paramList);
        if(rc != -1){
            QLOGD(LOG_TAG, "main: MemHalAsyncRequest_extn call success");
            //printf("main: MemHalAsyncRequest_extn call success\n");
        }
        else {
            QLOGE(LOG_TAG, "main: MemHalAsyncRequest_extn call failed");
            return -1;
        }

    }
    //printf("MemHalhal MemHalTest completed\n");
    return 0;
}