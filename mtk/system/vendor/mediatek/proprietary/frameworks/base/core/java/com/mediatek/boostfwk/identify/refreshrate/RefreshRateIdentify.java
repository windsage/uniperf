/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2022. All rights reserved.
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


package com.mediatek.boostfwk.identify.refreshrate;

import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.view.Window;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.identify.BaseIdentify;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.refreshrate.EventScenario;
import com.mediatek.boostfwk.scenario.refreshrate.RefreshRateScenario;
import com.mediatek.boostfwk.policy.refreshrate.RefreshRatePolicy;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.TasksUtil;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.identify.ime.IMEIdentify;
import com.mediatek.boostfwk.identify.ime.IMEIdentify.IMEStateListener;
import com.mediatek.boostfwk.identify.message.MsgIdentify;
import com.mediatek.boostfwk.identify.message.MsgIdentify.AudioStateListener;
import com.mediatek.boostfwk.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwk.identify.scroll.ScrollIdentify.ScrollEventListener;
import com.mediatek.boostfwk.identify.scroll.ScrollIdentify.TouchEventListener;
import com.mediatek.boostfwk.identify.frame.FrameIdentify;
import com.mediatek.boostfwk.identify.frame.FrameIdentify.FrameStateListener;

import java.util.ArrayList;

public class RefreshRateIdentify extends BaseIdentify implements IMEStateListener,
        AudioStateListener, TouchEventListener, FrameStateListener, ScrollEventListener {

    private static final String TAG = "M-ProMotion";
    private static volatile RefreshRateIdentify sInstance = null;
    private ActivityInfo activityInfo = null;

    private RefreshRatePolicy mRefreshRatePolicy;
    private boolean mIsVeriableRefreshRateSupported = false;
    private boolean mIsConfigInited = false;
    private boolean mIsSmoothFlingEnabled = false;
    private boolean mIsTouchScrollEnabled = false;
    private String mPackageName = null;

    public static RefreshRateIdentify getInstance() {
        if (null == sInstance) {
            synchronized (RefreshRateIdentify.class) {
                if (null == sInstance) {
                    sInstance = new RefreshRateIdentify();
                }
            }
        }
        return sInstance;
    }

    private RefreshRateIdentify() {
        mIsVeriableRefreshRateSupported = Config.isVariableRefreshRateSupported();
        mIsSmoothFlingEnabled = Config.isMSync3SmoothFlingEnabled();
        mIsTouchScrollEnabled = Config.isMSync3TouchScrollEnabled();

        if (mIsVeriableRefreshRateSupported) {
            LogUtil.mLogi(TAG,"M-ProMotion is enabled");
            mRefreshRatePolicy = new RefreshRatePolicy();
            IMEIdentify.getInstance().registerIMEStateListener(this);
            MsgIdentify.getInstance().registerAudioStateListener(this);
            ScrollIdentify.getInstance().registerTouchEventListener(this);
            ScrollIdentify.getInstance().registerScrollEventListener(this);
            FrameIdentify.getInstance().registerFrameStateListener(this);
        } else {
            LogUtil.mLogi(TAG,"M-ProMotion is disabled");
        }
    }

    private void configInit(RefreshRateScenario refreshRateScenario) {
        if (refreshRateScenario != null) {
            Context viewContext = null;
            if (refreshRateScenario.getScenarioContext() != null) {
                viewContext = refreshRateScenario.getScenarioContext();
            } else {
                mIsConfigInited = false;
                return;
            }

            // Use activity packagename first
            mPackageName = ActivityInfo.getInstance().getPackageName();
            if (mPackageName == null && viewContext != null) {
                mPackageName = viewContext.getPackageName();
            }

            // Not support system_server and systemui
            if (mPackageName == null
                        || Config.SYSTEM_PACKAGE_ARRAY.contains(mPackageName)) {
                mIsVeriableRefreshRateSupported = false;
                mRefreshRatePolicy.setVeriableRefreshRateSupported(false);
                LogUtil.mLogi(TAG,"App is not support");
            }

            // Not support game
            if (mIsVeriableRefreshRateSupported && TasksUtil.isGameAPP(mPackageName)) {
                mIsVeriableRefreshRateSupported= false;
                mRefreshRatePolicy.setVeriableRefreshRateSupported(false);
                LogUtil.mLogi(TAG,"Game is not support");
            }

            mIsConfigInited = true;
        }
    }

    @Override
    public boolean dispatchScenario(BasicScenario scenario) {
        if (mIsVeriableRefreshRateSupported) {
            LogUtil.traceBegin("Dispatch refresh rate scenario");

            if (scenario != null) {
                if (scenario.getScenario() == BoostFwkManager.UX_REFRESHRATE) {
                    RefreshRateScenario refreshRateScenario = (RefreshRateScenario)scenario;

                    if (!mIsConfigInited) {
                        configInit(refreshRateScenario);
                    }

                    Context activityContext = refreshRateScenario.getScenarioContext();
                    if (activityContext!= null && activityContext instanceof Activity) {
                        mRefreshRatePolicy.dispatchScenario(refreshRateScenario);
                        LogUtil.traceEnd();
                        return true;
                    }
                } else if (scenario.getScenario() == BoostFwkManager.UX_VIEWEVENT) {
                    EventScenario eventScenario = (EventScenario)scenario;
                    mRefreshRatePolicy.dispatchEvent(eventScenario);
                    LogUtil.traceEnd();
                    return true;
                }
            }

            LogUtil.traceEnd();
        }
        return false;
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
        if (mIsVeriableRefreshRateSupported) {
            mRefreshRatePolicy.onTouchEvent(event);
        }
    }

    @Override
    public void onInit(Window window) {
        if (mIsVeriableRefreshRateSupported) {
            mRefreshRatePolicy.onIMEInit(window);
        }
    }

    @Override
    public void onVisibilityChange(boolean show) {
        if (mIsVeriableRefreshRateSupported) {
            mRefreshRatePolicy.onIMEVisibilityChange(show);
        }
    }

    @Override
    public void onAudioMsgStatusUpdate(boolean isAudioMsgBegin){
        if (mIsVeriableRefreshRateSupported) {
            mRefreshRatePolicy.onVoiceDialogEvent(isAudioMsgBegin);
        }
    }

    @Override
    public void onFrameStateChange(long frameId, boolean isOnVsyncCheck){
        if (mIsVeriableRefreshRateSupported && isOnVsyncCheck) {
            mRefreshRatePolicy.onFrameStateChange(frameId);
        }
    }

    @Override
    public void onScrollEvent(){
        if (mIsVeriableRefreshRateSupported && mIsTouchScrollEnabled) {
            mRefreshRatePolicy.onScrollEvent();
        }
    }

}

