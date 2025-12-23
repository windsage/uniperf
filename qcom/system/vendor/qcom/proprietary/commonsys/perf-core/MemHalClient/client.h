/******************************************************************************
  @file    client.h
  @brief   Android performance MemHal library

  DESCRIPTION

  ---------------------------------------------------------------------------

  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
  All rights reserved.
  Confidential and Proprietary - Qualcomm Technologies, Inc.
  ---------------------------------------------------------------------------
******************************************************************************/
#ifdef __cplusplus
extern "C" {
#endif

#ifndef __MEMHALCLIENT_H__
#define __MEMHALCLIENT_H__

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG         "MEM-HAL-CLIENT-SYSTEM"
#include <android/log.h>
#define QLOGE(...)    ALOGE(__VA_ARGS__)
#define QLOGW(...)    ALOGW(__VA_ARGS__)
#define QLOGI(...)    ALOGI(__VA_ARGS__)
#define QLOGV(...)    ALOGV(__VA_ARGS__)
#define QCLOGE(...)   ALOGE(__VA_ARGS__)


 const char* MemHal_GetProp_extn(const std::string& in_propname, const std::string& in_defaultVal, const std::vector<int32_t>& in_req_details) ;
 int32_t MemHal_SetProp_extn(const std::string& in_propname, const std::string& in_NewVal, const std::vector<int32_t>& in_req_details) ;
 int32_t MemHal_SubmitRequest_extn(const std::vector<int32_t>& in_in_list, const std::vector<int32_t>& in_req_details) ;

#endif

#ifdef __cplusplus
}
#endif

