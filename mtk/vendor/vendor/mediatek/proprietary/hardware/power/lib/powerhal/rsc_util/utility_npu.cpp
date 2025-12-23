/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#define LOG_TAG "libPowerHal"

#include <stdio.h>
#include <stdint.h>
#include <unistd.h>
#include <string.h>
#include <sys/ioctl.h>
#include <utils/Log.h>
#include <errno.h>
#include <sys/stat.h>
#include "common.h"
#include "perfservice.h"

#define PATH_NPU_OPP_NUM      "/proc/npu_hal/opp_num"
#define PATH_NPU_OPP_LIMIT    "/proc/npu_hal/opp_limit"

static int nSetFreqInit = 0;
static int nIsNpuFreqSupport = 0;
static int NpuLargestOpp = 0;
static int npu_minfreq_opp_now = -1;
static int npu_maxfreq_opp_now = -1;

static void setNpuFreq(void);

/* Get NPU OPP count from sysfs */
static void get_npu_largest_opp(int *p_count)
{
    *p_count = get_int_value(PATH_NPU_OPP_NUM);
}

static int checkNPUSupport(void)
{
    struct stat stat_buf;

    if(!nSetFreqInit) {
        nIsNpuFreqSupport = (0 == stat(PATH_NPU_OPP_NUM, &stat_buf)) ? 1 : 0;
        nSetFreqInit = 1;
    }

    if(!nIsNpuFreqSupport)
        return 0;

    return 1;
}

int npuInit(int level)
{
    if(checkNPUSupport()) {
        /* NPU info */
        get_npu_largest_opp(&NpuLargestOpp);
        if(NpuLargestOpp <= 0) {
            ALOGE("NpuLargestOpp error: %d", NpuLargestOpp);
            return -1;
        }

        /* Since NPU Opp table range is from 0 to NpuLargestOpp-1, */
        /* we use -1 to represent no limit (default)               */
        npu_minfreq_opp_now = -1; // Default: no limit
        npu_maxfreq_opp_now = -1; // Default: no limit
        ALOGI("NpuLargestOpp:%d", NpuLargestOpp);
    }

    return 0;
}

int setNPUFreqMinOPP(int level, void *scn)
{
    LOG_D("npu minfreq_opp level: %d", level);
    if(checkNPUSupport()) {
        npu_minfreq_opp_now = (level == -1) ? NpuLargestOpp : level;
        setNpuFreq();
    } else {
        LOG_E("npu freq not support!!");
        return -1;
    }
    return 0;
}

int setNPUFreqMaxOPP(int level, void *scn)
{
    LOG_D("npu maxfreq_opp level: %d", level);
    if(checkNPUSupport()) {
        npu_maxfreq_opp_now = (level == -1) ? 0 : level;
        setNpuFreq();
    } else {
        LOG_E("npu freq not support!!");
        return -1;
    }
    return 0;
}

static void setNpuFreq(void)
{
    int minfreqToSetOpp = npu_minfreq_opp_now;
    int maxfreqToSetOpp = npu_maxfreq_opp_now;
    int final_minfreq_opp = -1, final_maxfreq_opp = -1;

    // Check if max OPP > min OPP (lower OPP number = higher frequency)
    if (minfreqToSetOpp < maxfreqToSetOpp)
        minfreqToSetOpp = maxfreqToSetOpp;

    // Ensure values are within valid range
    final_minfreq_opp = (minfreqToSetOpp < 0 || minfreqToSetOpp > NpuLargestOpp) ? NpuLargestOpp : minfreqToSetOpp;
    final_maxfreq_opp = (maxfreqToSetOpp < 0 || maxfreqToSetOpp > NpuLargestOpp) ? 0 : maxfreqToSetOpp;

    ALOGI("[setNPUFreq] final min/max = (%d, %d)", final_minfreq_opp, final_maxfreq_opp);
    set_value(PATH_NPU_OPP_LIMIT, final_maxfreq_opp, final_minfreq_opp);
}