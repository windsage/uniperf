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

import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwkV5.BoostModuleDispatcherV5;
import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.animation.AnimScenario;

/**
 * Use to boost framework.
 *
 * @hide
 */
public final class BoostFwkManagerImpl extends BoostFwkManager {

    private static final Object INSTANCE_LOCK = new Object();

    private static volatile BoostFwkManagerImpl mInstance = null;

    private BaseDispatcher mBoostDispatcher = null;
    private boolean mDisableSBE = false;
    private int mSBEVersion = Config.BOOST_FWK_VERSION_1;

    public static BoostFwkManagerImpl getInstance() {
        if (null == mInstance) {
            synchronized (BoostFwkManagerImpl.class) {
                if (null == mInstance) {
                    mInstance = new BoostFwkManagerImpl();
                }
            }
        }
        return mInstance;
    }

    private BoostFwkManagerImpl() {
        mSBEVersion = Config.getBoostFwkVersion();
        if (mSBEVersion >= Config.BOOST_FWK_VERSION_5) {
            mBoostDispatcher = BoostModuleDispatcherV5.getInstance();
        } else {
            mBoostDispatcher = BoostModuleDispatcher.getInstance();
        }

        mDisableSBE = Config.disableSBE();
    }

    /** hide */
    @Override
    public void perfHint(BasicScenario scenario) {
        if (mDisableSBE || mBoostDispatcher == null) {
            return;
        }
        mBoostDispatcher.scenarioActionDispatcher(scenario);
    }

    /** hide */
    @Override
    public void perfHint(BasicScenario... scenarios) {
        if (mDisableSBE || mBoostDispatcher == null) {
            return;
        }

        for (BasicScenario basicScenario : scenarios) {
            if (basicScenario == null) {
                continue;
            }
            mBoostDispatcher.scenarioActionDispatcher(basicScenario);
        }
    }

    @Override
    public void enableAnimationCtrl(int key, boolean enable) {
        if (mDisableSBE || mBoostDispatcher == null) {
            return;
        }

        if (mSBEVersion >= Config.BOOST_FWK_VERSION_5) {
            mBoostDispatcher.scenarioActionDispatcher(
                    BoostFwkManager.UX_ANIMATION, key, enable);
        }
    }

    @Override
    public void updateAnimScenario(int key, AnimScenario scenario) {
        if (mDisableSBE
                || mBoostDispatcher == null
                || scenario == null) {
            return;
        }

        if (mSBEVersion >= Config.BOOST_FWK_VERSION_5) {
            mBoostDispatcher.scenarioActionDispatcher(scenario, key);
        }
    }
}
