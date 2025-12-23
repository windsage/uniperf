/******************************************************************************
  @file    MemHalResources.h
  @brief   Header file for MemHal APIs implementaion

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

enum {
    MEMORY_ANON = 0x000010AA,
    MEMORY_ION_POOL = 0x000010AB,
    MEMORY_ANY = 0x000010AC,
    REQUEST_FREE_MEMORY = 0x00001100,
    REQUEST_BOOST_MODE = 0x00001101,
    REQUEST_MODIFY_SAFELOAD = REQUEST_BOOST_MODE,
    VENDOR_GET_PROPERTY_REQUEST = 0x00001102,
};

#ifdef __cplusplus
}
#endif