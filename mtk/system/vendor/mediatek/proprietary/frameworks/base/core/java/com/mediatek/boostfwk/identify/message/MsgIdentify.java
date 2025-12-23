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

package com.mediatek.boostfwk.identify.message;

import android.view.View;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.identify.BaseIdentify;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.message.MessageScenario;

import java.util.ArrayList;
import java.util.List;

public class MsgIdentify extends BaseIdentify{

    private static final String TAG = "MsgIdentify";

    private static volatile MsgIdentify sInstance = null;
    private boolean mIsAudioMsgBegin = false;
    private List<Integer> mSpAudioStepList = new ArrayList<Integer>();
    private final String[] mSpAudioMsgViewList = {
            "SoundWaveView",
            "LanguageChoiceLayout"};
    private List<AudioStateListener> mAudioStateListeners = new ArrayList<>();

    public static MsgIdentify getInstance() {
        if (null == sInstance) {
            synchronized (MsgIdentify.class) {
                if (null == sInstance) {
                    sInstance = new MsgIdentify();
                }
            }
        }
        return sInstance;
    }

    public interface AudioStateListener {
        public void onAudioMsgStatusUpdate(boolean isAudioMsgBegin);
    }

    public void registerAudioStateListener(AudioStateListener listener) {
        if (listener == null) {
            return;
        }
        mAudioStateListeners.add(listener);
    }

    public MsgIdentify() {
    }

    /**
     * Dispatcher the message refer actions to do.
     *
     * @param scenario MessageScenario obj
     */
    @Override
    public boolean dispatchScenario(BasicScenario basicScenario) {
        if (basicScenario == null) {
            LogUtil.mLogw(TAG, "No message scenario to dispatcher.");
            return false;
        }
        MessageScenario scenario = (MessageScenario)basicScenario;
        int action = scenario.getScenarioAction();
        //int status = scenario.getBoostStatus();
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "Message action dispatcher to = " + action);
        }
        switch(action) {
            case BoostFwkManager.Message.AUDIO_MSG:
                audioMsgStatusUpdate(scenario);
                break;
            default:
                LogUtil.mLogw(TAG, "Not found dispatcher message action.");
                break;
        }
        return true;
    }

    // Only support Wechat and QQ
    public void audioMsgStatusUpdate(MessageScenario scenario) {
        String viewName = scenario.getViewName();
        int visibilityMask = scenario.getVisibilityMask();
        boolean enabled = false;
        if (viewName.equals(MessageScenario.mAudioMsgViewList[0])) {
            enabled = audioMsgUpdate(visibilityMask);
        } else if (viewName.contains(MessageScenario.mAudioMsgViewList[1])) {
            enabled = specialAudioMsgUpdate(viewName, visibilityMask);
        }

        if (mIsAudioMsgBegin != enabled) {
            mIsAudioMsgBegin = enabled;
            for (AudioStateListener audioStateListener : mAudioStateListeners) {
                audioStateListener.onAudioMsgStatusUpdate(mIsAudioMsgBegin);
            }
            if (LogUtil.DEBUG) {
                LogUtil.mLogd(TAG, "audioMsgStatusUpdate. status = " + mIsAudioMsgBegin);
            }
        }
    }

    private boolean audioMsgUpdate(int visibilityMask) {
        return (visibilityMask == View.VISIBLE) ? true : false;
    }

    private boolean specialAudioMsgUpdate(String viewName, int visibilityMask) {
        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "specialAudioMsgUpdate. viewName = " + viewName
                + " visibilityMask = " + visibilityMask);
        }

        if (mIsAudioMsgBegin) { // voice dialog already visible
            if (viewName.trim().contains(mSpAudioMsgViewList[1]) && mSpAudioStepList.size() >= 3
                    && visibilityMask == View.GONE) {
                mSpAudioStepList.clear();
                return false;
            } else if (viewName.trim().contains(mSpAudioMsgViewList[0])
                    && visibilityMask == View.VISIBLE) {
                return true;
            }
            return true;
        }

        boolean enabled = false;
        if (viewName.trim().contains(mSpAudioMsgViewList[0])) {
            switch(mSpAudioStepList.size()) {
                case 0:
                    if (visibilityMask == View.VISIBLE) {
                        mSpAudioStepList.add(visibilityMask);
                    }
                    break;
                case 1:
                    if (visibilityMask == View.GONE && mSpAudioStepList.get(0) == View.VISIBLE) {
                        mSpAudioStepList.add(visibilityMask);
                    }
                    break;
                case 2:
                    if (visibilityMask == View.VISIBLE && mSpAudioStepList.get(1) == View.GONE) {
                        mSpAudioStepList.add(visibilityMask);
                        enabled = true;
                    }
                    break;
                default:
                    break;
            }
        }

        return enabled;
    }
}