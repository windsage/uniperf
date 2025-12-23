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
package com.mediatek.boostfwk;

import android.util.SparseArray;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.identify.frame.FrameIdentify;
import com.mediatek.boostfwk.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwk.identify.launch.LaunchIdentify;
import com.mediatek.boostfwk.identify.message.MsgIdentify;
import com.mediatek.boostfwk.identify.ime.IMEIdentify;
import com.mediatek.boostfwk.identify.refreshrate.RefreshRateIdentify;

import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.utils.Util;
import com.mediatek.boostfwkV5.BoostModuleDispatcherV5;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.frame.FrameScenario;
import com.mediatek.boostfwk.scenario.scroll.ScrollScenario;
import com.mediatek.boostfwk.scenario.launch.LaunchScenario;
import com.mediatek.boostfwk.scenario.message.MessageScenario;
import com.mediatek.boostfwk.scenario.ime.IMEScenario;

import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.identify.BaseIdentify;

import java.util.ArrayList;


/**
 * Use to dispatcher boost to module.
 *
 * @hide
 */
public final class BoostModuleDispatcher extends BaseDispatcher{

    private static volatile BoostModuleDispatcher sInstance = null;
    private static final SparseArray<BaseIdentify> SCENARIO_DIENTIFY = new SparseArray();

    public static BoostModuleDispatcher getInstance() {
        if (null == sInstance) {
            synchronized (BoostModuleDispatcher.class) {
                if (null == sInstance) {
                    sInstance = new BoostModuleDispatcher();
                }
            }
        }
        return sInstance;
    }

    private boolean rigisterScenarioCallback(int scenarioId, BaseIdentify baseIdentity) {
        if (baseIdentity != null) {
            SCENARIO_DIENTIFY.put(scenarioId, baseIdentity);
            return true;
        }
        return false;
    }

    private BoostModuleDispatcher() {
        rigisterScenarioCallback(BoostFwkManager.UX_SCROLL, ScrollIdentify.getInstance());
        rigisterScenarioCallback(BoostFwkManager.UX_FRAME, FrameIdentify.getInstance());
        rigisterScenarioCallback(BoostFwkManager.UX_LAUNCH, LaunchIdentify.getInstance());
        rigisterScenarioCallback(BoostFwkManager.UX_IME, IMEIdentify.getInstance());
        rigisterScenarioCallback(BoostFwkManager.UX_MSG, MsgIdentify.getInstance());
        rigisterScenarioCallback(BoostFwkManager.UX_REFRESHRATE, RefreshRateIdentify.getInstance());
        rigisterScenarioCallback(BoostFwkManager.UX_VIEWEVENT, RefreshRateIdentify.getInstance());
    }

    @Override
    public void scenarioActionDispatcher(BasicScenario scenario) {
        if (scenario == null) {
            LogUtil.mLogw(TAG, "No scenario to dispatcher.");
            return;
        }

        int scenarioId = scenario.getScenario();
        BaseIdentify identify =  SCENARIO_DIENTIFY.get(scenarioId);
        if (identify == null) {
            LogUtil.mLogw(TAG, "Not found identify scenario.");
            return;
        }

        if (identify.isMainThreadOnly() && !Util.isMainThread()) {
            return;
        }

        identify.dispatchScenario(scenario);
    }
}
