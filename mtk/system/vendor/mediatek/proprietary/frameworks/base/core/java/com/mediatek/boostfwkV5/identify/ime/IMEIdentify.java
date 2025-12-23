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

package com.mediatek.boostfwkV5.identify.ime;

import android.view.Window;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.ime.IMEScenario;

import com.mediatek.boostfwkV5.identify.BaseIdentify;
import com.mediatek.boostfwkV5.info.ActivityInfo;
import com.mediatek.boostfwkV5.utils.Config;
import com.mediatek.boostfwkV5.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;

public class IMEIdentify extends BaseIdentify {

    private static final String TAG = "IMEIdentify";

    private static volatile IMEIdentify mInstance = null;

    private List<IMEStateListener> mIMEStateListeners = new ArrayList<>();
    private boolean mImeShow = false;

    public static IMEIdentify getInstance() {
        if (null == mInstance) {
            synchronized (IMEIdentify.class) {
                if (null == mInstance) {
                    mInstance = new IMEIdentify();
                }
            }
        }
        return mInstance;
    }

    private IMEIdentify() {
    }

    public interface IMEStateListener {
        public void onInit(Window window);
        public void onVisibilityChange(boolean show);
    }

    @Override
    public boolean isMainThreadOnly() {
        return false;
    }

    @Override
    public boolean dispatchScenario(BasicScenario basicScenario) {
        if (basicScenario == null) {
            return false;
        }
        IMEScenario scenario = (IMEScenario)basicScenario;
        int action = scenario.getScenarioAction();
        switch(action) {
            case BoostFwkManager.IME.IME_SHOW:
                notifyVisibilityChange(true);
                break;
            case BoostFwkManager.IME.IME_HIDE:
                notifyVisibilityChange(false);
                break;
            case BoostFwkManager.IME.IME_INIT:
                notifyIMEInit(scenario.getWindowAndClear());
                break;
            default:
                if (Config.isBoostFwkLogEnable()) {
                    LogUtil.mLogd(TAG, "not support scnario action");
                }
                break;
        }
        return true;
    }

    private void notifyIMEInit(Window window) {
        if (window != null) {
            for (IMEStateListener imeStateListener : mIMEStateListeners) {
                imeStateListener.onInit(window);
            }
        }
    }

    private void notifyVisibilityChange(boolean show) {
        if (mImeShow == show) {
            return;
        }
        mImeShow = show;
        for (IMEStateListener imeStateListener : mIMEStateListeners) {
            imeStateListener.onVisibilityChange(show);
        }
    }

    public void registerIMEStateListener(IMEStateListener listener) {
        if (listener == null) {
            return;
        }
        mIMEStateListeners.add(listener);
    }
}