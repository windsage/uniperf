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

package com.mediatek.boostfwk.identify.frame;

import android.os.Process;
import android.view.Choreographer;
import android.view.ThreadedRenderer;
import android.view.ViewGroup;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.identify.BaseIdentify;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.Util;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.info.ScrollState;
import com.mediatek.boostfwk.policy.frame.BaseFramePolicy;
import com.mediatek.boostfwk.policy.frame.FrameDecision;
import com.mediatek.boostfwk.policy.frame.FramePolicy;
import com.mediatek.boostfwk.policy.frame.FramePolicyV2;
import com.mediatek.boostfwk.policy.frame.FramePolicyV3;
import com.mediatek.boostfwk.policy.frame.ScrollingFramePrefetcher;
import com.mediatek.boostfwk.policy.frame.TranScrollingFramePrefetcher;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.frame.FrameScenario;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class FrameIdentify extends BaseIdentify {

    private static final String TAG = "FrameIdentify";

    private static volatile FrameIdentify sInstance = null;
    private static boolean mIsOnVsyncCheck = false;
    private static boolean mIsDoFrameBegin = false;

    private static float mRefreshRate = 0;
    private static long mFrameIntervalMs = 0;
    private static long mLimitVsyncTime = 0;
    private static final int BOOST_FWK_VERSION = Config.getBoostFwkVersion();
    private BaseFramePolicy mFramePolicy = null;
    private ScrollingFramePrefetcher mScrollingFramePrefetcher = null;
    private List<FrameStateListener> mFrameStateListeners = new ArrayList<>();

    public static FrameIdentify getInstance() {
        if (null == sInstance) {
            synchronized (FrameIdentify.class) {
                if (null == sInstance) {
                    sInstance = new FrameIdentify();
                }
            }
        }
        return sInstance;
    }

    private FrameIdentify() {
        if (mFramePolicy == null) {
            if (BOOST_FWK_VERSION >= Config.BOOST_FWK_VERSION_4) {
                mFramePolicy = FramePolicyV3.getInstance();
            } else if (BOOST_FWK_VERSION >= Config.BOOST_FWK_VERSION_2) {
                mFramePolicy = FramePolicyV2.getInstance();
            } else {
                mFramePolicy = FramePolicy.getInstance();
            }
        }
        if (mScrollingFramePrefetcher == null) {
            mScrollingFramePrefetcher = ScrollingFramePrefetcher.getInstance();
        }
        //T-HUB Core[SPD]:added for frame prefetcher by song.tang 2025926 start
        if (TranScrollingFramePrefetcher.FEATURE_ENABLE) {
            TranScrollingFramePrefetcher.getInstance();
        }
        //T-HUB Core[SPD]:added for frame prefetcher by song.tang 2025926 end
    }

    /**
     * Dispatcher the ux frame refer actions to do.
     *
     * @param scenario FrameScenario obj
     */
    @Override
    public boolean dispatchScenario(BasicScenario basicScenario) {
        if (Config.disableFrameIdentify() || basicScenario == null) {
            return false;
        }
        FrameScenario scenario = (FrameScenario)basicScenario;

        int action = scenario.getScenarioAction();
        int status = scenario.getBoostStatus();
        int frameStep = scenario.getFrameStep();
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "Frame action dispatcher to = " + action
                    + " status = " + status + ", frame step = " + frameStep);
        }

        switch(action) {
            case BoostFwkManager.Draw.FRAME_DRAW:
                doFrameCheck(status, scenario.getFrameId(), scenario);
                break;
            case BoostFwkManager.Draw.FRAME_DRAW_STEP:
                doFrameStepCheck(status, frameStep, scenario);
                break;
            case BoostFwkManager.Draw.FRAME_REQUEST_VSYNC:
                doFrameRequestNextVsync();
                break;
            case BoostFwkManager.Draw.FRAME_RENDER_INFO:
                updateRenderInfo(scenario);
                break;
            case BoostFwkManager.Draw.FRAME_PREFETCHER:
                doScrollingFramePrefetcher(status, scenario);
                break;
            case BoostFwkManager.Draw.FRAME_PRE_ANIM:
                doScrollingFramePrefetcherPreAnim(scenario);
                break;
            case BoostFwkManager.Draw.FRAME_REAL_DRAW:
                notifyFramePerformDrawFinish(scenario);
                break;
            case BoostFwkManager.Draw.FRAME_OBTAIN_VIEW:
                notifyFrameUpdateView(scenario);
                break;
            default:
                LogUtil.mLogw(TAG, "Not found dispatcher frame action.");
                break;
        }
        return true;
    }

    private void notifyFramePerformDrawFinish(FrameScenario scenario) {
        if (BOOST_FWK_VERSION > Config.BOOST_FWK_VERSION_2) {
            FrameDecision.getInstance().perfromDraw();
            mFramePolicy.doFrameStepHint(true, BoostFwkManager.Draw.FRAME_REAL_DRAW);
        }
    }

    private void notifyFrameUpdateView(FrameScenario scenario) {
        if (BOOST_FWK_VERSION > Config.BOOST_FWK_VERSION_3) {
            FrameDecision.getInstance().onFrameStatus(scenario);
            mFramePolicy.doFrameStepHint(true, scenario.getScenarioAction());
        }
    }

    private void updateRenderInfo(FrameScenario scenario) {
        int renderThreadTid = scenario.getRenderThreadTid();
        if (renderThreadTid > 0) {
            ActivityInfo.getInstance().setRenderThreadTid(renderThreadTid);
            if (BOOST_FWK_VERSION > Config.BOOST_FWK_VERSION_2) {
                //get pacakge name from current scenario, incase non-activity application
                checkStartFrameTracking(scenario.getPackageName());
            }
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "init renderThreadTid=" + renderThreadTid);
        }
        ActivityInfo.getInstance().setThreadedRenderer(scenario.getThreadedRendererAndClear());
        ActivityInfo.getInstance().setBufferQueue(scenario.getViewBufferQueueAndClear());
    }

    private void checkStartFrameTracking(String pkgName) {
        //init frame decision once, incase can not receive scroll hint.
        FrameDecision decision = FrameDecision.getInstance();
        if (pkgName != null && pkgName != "" && !"android".equals(pkgName)
                && Config.getBoostFwkVersion() == Config.BOOST_FWK_VERSION_3
                && Config.FRAME_TRACKING_LIST.contains(pkgName)) {
            LogUtil.mLogd(TAG, pkgName);
            decision.setStartFrameTracking(true);
            decision.setStartListenFrameHint(true);
        }
    }

    private void doFrameCheck(int status, long frameId, FrameScenario scenario) {
        boolean init = doFrameInit();
        FrameDecision frameDecision = FrameDecision.getInstance();
        //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 start
        if (TranScrollingFramePrefetcher.FEATURE_ENABLE) {
            scenario.setFling(TranScrollingFramePrefetcher.getInstance().getFling());
            // modify for pre-animation by shijun.tang start
            scenario.setSFPEnable(true).setPreAnimEnable(TranScrollingFramePrefetcher.getInstance().isPreAnimation());
            // modify for pre-animation by shijun.tang end
        } else
            //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 end
        if (BOOST_FWK_VERSION > Config.BOOST_FWK_VERSION_1) {
            scenario.setIsListenFrameHint(init || frameDecision.isEnableFrameTracking());
            scenario.setFling(ScrollingFramePrefetcher.getInstance().getFling());
            scenario.setSFPEnable(ScrollingFramePrefetcher.FEATURE_ENABLE)
                    .setPreAnimEnable(ScrollingFramePrefetcher.PRE_ANIM_ENABLE);

        }
        mIsOnVsyncCheck = boostBeginEndCheck(status);

        //then notify frame tracking
        if (BOOST_FWK_VERSION > Config.BOOST_FWK_VERSION_2) {
            if (!mIsOnVsyncCheck) {
                scenario.resetTraversalCallbackCount();
            }
            frameDecision.onDoFrame(mIsOnVsyncCheck, frameId);
        }

        //notify frame rescue first
        //If not init, cannot do frame check for rescue
        if (init) {
            mFramePolicy.doFrameHint(mIsOnVsyncCheck, frameId,
                    scenario.getVsyncWakeupTimeNanos(), scenario.getVsyncDeadlineNanos());
        }

        // Dispatch frame state to listeners
        for (FrameStateListener frameStateListener : mFrameStateListeners) {
            frameStateListener.onFrameStateChange(frameId, mIsOnVsyncCheck);
        }
    }

    private void doFrameStepCheck(int status, int step, FrameScenario scenario) {
        if (!isBeginFrameAction()) {
            //maybe pre-animation
            if (BOOST_FWK_VERSION > Config.BOOST_FWK_VERSION_3
                    && Choreographer.CALLBACK_ANIMATION == step) {
                mFramePolicy.doFrameStepHint(boostBeginEndCheck(status), step);
            }
            return;
        }
        FrameDecision.getInstance().onDoFrameStep(boostBeginEndCheck(status), step, scenario);
        mFramePolicy.doFrameStepHint(boostBeginEndCheck(status), step);

        if (ScrollingFramePrefetcher.FEATURE_ENABLE
                && Choreographer.CALLBACK_TRAVERSAL == step
                && scenario.getTraversalCallbackCount() > 1
                && !ActivityInfo.getInstance().isMutilDrawPage()) {
            ActivityInfo.getInstance().markAsMutilDrawPage();
        }
    }

    private void doFrameRequestNextVsync() {
        mFramePolicy.onRequestNextVsync();
        mScrollingFramePrefetcher.onRequestNextVsync();
    }

    private boolean doFrameInit() {
        mRefreshRate = ScrollState.getRefreshRate();
        mFrameIntervalMs = (long) (1000 / mRefreshRate);
        return mFramePolicy.initLimitTime(mFrameIntervalMs);
    }

    private boolean isBeginFrameAction() {
        return mIsOnVsyncCheck;
    }

    private boolean boostBeginEndCheck(int status) {
        return status == BoostFwkManager.BOOST_BEGIN;
    }

    private void doScrollingFramePrefetcher(int status, FrameScenario scenario) {
        boolean isBegin = boostBeginEndCheck(status);
        if (isBegin) {
            mIsDoFrameBegin = true;
            //this is estimation frame, no on vsync step, initial frame.
            //current must scrolling, can init directly.
            if (!mIsOnVsyncCheck && scenario.getFrameId() == -1) {
                //ensure refresh interval has been init.
                scenario.setIsListenFrameHint(doFrameInit());
                mIsOnVsyncCheck = true;
                mFramePolicy.doFrameHint(true, scenario.getFrameId(), -1, -1);
                if (BOOST_FWK_VERSION > Config.BOOST_FWK_VERSION_2) {
                    FrameDecision.getInstance().onDoFrame(true, scenario.getFrameId());
                }

                for (FrameStateListener frameStateListener : mFrameStateListeners) {
                    frameStateListener.onFrameStateChange(scenario.getFrameId(),true);
                }
            }
            //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 start
            if (TranScrollingFramePrefetcher.getInstance().doScrollingFramePrefetcher(scenario)) {
                return;
            }
            //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 end
            mScrollingFramePrefetcher.doScrollingFramePrefetcher(scenario);
        } else if (mIsDoFrameBegin) {
            mIsDoFrameBegin = false;
            //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 start
            if (TranScrollingFramePrefetcher.getInstance().onFrameEnd(isBegin, scenario)) {
                return;
            }
            //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 end
            mScrollingFramePrefetcher.onFrameEnd(isBegin, scenario);
        }
    }

    private void doScrollingFramePrefetcherPreAnim(FrameScenario scenario) {
        // modify for pre-animation by shijun.tang start
        scenario.setPreAnim(TranScrollingFramePrefetcher.getInstance().isPreAnimation());
        // modify for pre-animation by shijun.tang end
    }

    public interface FrameStateListener {
        public void onFrameStateChange(long frameId, boolean isOnVsyncCheck);
    }

    public void registerFrameStateListener(FrameStateListener listener) {
        if (listener == null) {
            return;
        }
        mFrameStateListeners.add(listener);
    }
}