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

package com.mediatek.boostfwkV5.identify.frame;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.animation.AnimScenario;
import com.mediatek.boostfwkV5.identify.BaseIdentify;
import com.mediatek.boostfwkV5.policy.frame.HWUIPolicyManager;
import com.mediatek.boostfwkV5.utils.Config;
import com.mediatek.boostfwkV5.utils.LogUtil;
import com.mediatek.boostfwkV5.utils.Util;

public class HWUIIdentify extends BaseIdentify {

    private static final String TAG = "HWUIIdentify";

    private static volatile HWUIIdentify sInstance = null;

    private HWUIPolicyManager mPolicyManager = null;

    public static HWUIIdentify getInstance() {
        if (null == sInstance) {
            synchronized (HWUIIdentify.class) {
                if (null == sInstance) {
                    sInstance = new HWUIIdentify();
                }
            }
        }
        return sInstance;
    }

    private HWUIIdentify() {
        if (mPolicyManager == null) {
            mPolicyManager = HWUIPolicyManager.getInstance();
        }
    }

    @Override
    public boolean dispatchScenario(BasicScenario scenario, Object... params) {
        if (scenario == null && params != null && params.length >= 2) {
            //case 1: scenario is null, params not empty
            //enableAnimationCtrl(int key, boolean enable)
            int hwuiKey = ((Integer)params[0]).intValue();
            boolean enable = ((Boolean)params[1]).booleanValue();

            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG,
                        "enableAnimationCtrl key: " + hwuiKey + " enable: " + enable);
            }

            if (enable) {
                mPolicyManager.startHWUICtrl(hwuiKey);
            } else {
                mPolicyManager.stopHWUICtrl(hwuiKey);
            }
            return true;
        } else if (scenario != null && params != null && params.length >= 1) {
            //case 2: scenario not null, params not empty
            //updateAnimScenario(int key, AnimScenario scenario)
            int hwuiKey = ((Integer)params[0]).intValue();
            AnimScenario animScenario = (AnimScenario) scenario;
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG,
                        "updateAnimScenario key: " + hwuiKey + " scenario: " + animScenario);
            }
            mPolicyManager.updateHWUIPolicy(hwuiKey, animScenario);
            return true;
        }

        return false;
    }
}