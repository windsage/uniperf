/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2021. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.boostfwkV5.policy.launch;

import android.os.Trace;
import com.mediatek.boostframework.Performance;

public class LaunchPolicy {
    private static final String TAG = "SBE-LaunchPolicy";
    private Performance mPowerHalService;
    private static int mPowerHandle = 0;
    //Release Target launch duration time.
    private static int mReleaseLaunchDuration = 3000;
    //Release Target Launch hint end CMD.
    private static int PERF_RES_POWER_END_HINT_HOLD_TIME = 0x03410300;
    static final int PERF_RES_POWERHAL_CALLER_TYPE = 0x0340c300;
    //Release Target Launch process create CMD.
    private static int MTKPOWER_HINT_PROCESS_CREATE = 21;
    private static int MTKPOWER_HINT_PROCESS_CREATE_PERF_MODE = 57;

    public LaunchPolicy() {
        mPowerHalService = new Performance();
    }

    public void boostEnd(String pkgName) {
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "SBE boost end");

        int perf_lock_rsc[] = new int[] {PERF_RES_POWER_END_HINT_HOLD_TIME,
                MTKPOWER_HINT_PROCESS_CREATE, PERF_RES_POWER_END_HINT_HOLD_TIME,
                MTKPOWER_HINT_PROCESS_CREATE_PERF_MODE,
                PERF_RES_POWERHAL_CALLER_TYPE, 1};

        perfLockAcquire(perf_lock_rsc);

        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
    }

    private void perfLockAcquire(int[] resList) {
        if (mPowerHalService != null) {
            mPowerHandle = mPowerHalService.perfLockAcquire(mReleaseLaunchDuration, resList);
            mPowerHalService.perfLockRelease(mPowerHandle);
        }
    }

}
