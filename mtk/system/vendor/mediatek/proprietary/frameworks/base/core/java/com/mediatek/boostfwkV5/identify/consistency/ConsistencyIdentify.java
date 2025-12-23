/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2024. All rights reserved.
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

package com.mediatek.boostfwkV5.identify.consistency;

import android.view.View;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.consistency.ConsistencyScenario;
import com.mediatek.boostfwkV5.policy.consistency.ConsistencyPolicy;
import com.mediatek.boostfwkV5.utils.Config;
import com.mediatek.boostfwkV5.utils.LogUtil;
import com.mediatek.boostfwkV5.identify.BaseIdentify;

import java.util.ArrayList;
import java.util.List;

public class ConsistencyIdentify extends BaseIdentify{

    private static final String TAG = "ConsistencyIdentify";

    private static volatile ConsistencyIdentify sInstance = null;
    private static ConsistencyPolicy sPolicy = null;

    public static ConsistencyIdentify getInstance() {
        if (null == sInstance) {
            synchronized (ConsistencyIdentify.class) {
                if (null == sInstance) {
                    sInstance = new ConsistencyIdentify();
                }
            }
        }
        return sInstance;
    }

    public ConsistencyIdentify() {
    }

    @Override
    public boolean isMainThreadOnly() {
        return false;
    }

    /**
     * Dispatcher the consistency refer actions to do.
     *
     * @param scenario ConsistencyScenario obj
     */
    @Override
    public boolean dispatchScenario(BasicScenario basicScenario) {
        if (basicScenario == null) {
            LogUtil.mLogw(TAG, "No consistency scenario to dispatcher.");
            return false;
        }

        if (!Config.isEnableConsistency()) {
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.mLogd(TAG, "consistency feature disabled.");
            }
            return false;
        }

        ConsistencyScenario scenario = (ConsistencyScenario)basicScenario;
        int action = scenario.getScenarioAction();
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "consistency action dispatcher to = " + action +
                    ", pid=" + scenario.getHostPID() + ", enable=" + scenario.getBoostStatus());
        }
        ConsistencyScenario newScenario = copyScenarioObject(scenario);
        switch(action) {
            case BoostFwkManager.Consistency.NORMAL_MODE:
                doNormalModePolicy(newScenario, action);
                break;
            case BoostFwkManager.Consistency.APP_LAUNCH_RESPONSE:
                doAppLaunchResponsePolicy(newScenario, action);
                break;
            default:
                LogUtil.mLogw(TAG, "Not found dispatcher consistency action.");
                break;
        }
        return true;
    }

    private boolean checkCommonParam(ConsistencyScenario scenario) {
        int hostPid = scenario.getHostPID();
        int timeout = scenario.getScenarioTimeout();
        if (hostPid > 0 && timeout >= 0) {
            return true;
        }
        return false;
    }

    private boolean checkNormalModeParam(ConsistencyScenario scenario) {
        int minCap = scenario.getMinCapability();
        int maxCap = scenario.getMaxCapability();
        if ((minCap >=0 && maxCap >= 0) && (minCap <= maxCap)) {
            return true;
        }
        return false;
    }

    private boolean checkAPPLaunchResParam(ConsistencyScenario scenario) {
        int targetTime = scenario.getTargetTime();
        if (targetTime >=0) {
            return true;
        }
        return false;
    }

    private void doNormalModePolicy(ConsistencyScenario scenario, int type) {
        boolean paramPass = checkCommonParam(scenario) && checkNormalModeParam(scenario);
        if (paramPass) {
            if (sPolicy == null) {
                sPolicy = ConsistencyPolicy.getInstance();
            }
            sPolicy.enableNormalModePolicy(scenario, type);
        } else {
            LogUtil.mLogw(TAG, "Enable ConsistencyEngine failed, please check your input param. " +
                    "[such as : Min must less or equals than Max]");
        }
    }

    private void doAppLaunchResponsePolicy(ConsistencyScenario scenario, int type) {
        boolean paramPass = checkCommonParam(scenario) && checkAPPLaunchResParam(scenario);
        if (paramPass) {
            if (sPolicy == null) {
                sPolicy = ConsistencyPolicy.getInstance();
            }
            sPolicy.enableAPPLaunchResPolicy(scenario, type);
        } else {
            LogUtil.mLogw(TAG, "Enable ConsistencyEngine failed, please check your input param. " +
                    "[such as : Min must less or equals than Max]");
        }
    }

    private ConsistencyScenario copyScenarioObject(ConsistencyScenario scenario) {
        ConsistencyScenario newObject = new ConsistencyScenario();
        newObject.setBoostStatus(scenario.getBoostStatus());
        newObject.setHostPID(scenario.getHostPID());
        newObject.setMaxCapability(scenario.getMaxCapability());
        newObject.setMinCapability(scenario.getMinCapability());
        newObject.setScenarioAction(scenario.getScenarioAction());
        newObject.setScenarioTimeout(scenario.getScenarioTimeout());
        newObject.setTargetTime(scenario.getTargetTime());

        return newObject;
    }
}