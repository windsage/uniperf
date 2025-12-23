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

package com.mediatek.boostfwk.policy.frame;

import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.Trace;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.view.Choreographer;

import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.info.ScrollState;
import com.mediatek.boostfwk.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwk.policy.scroll.ScrollPolicy;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.utils.TasksUtil;
import com.mediatek.boostfwk.utils.Util;
import com.mediatek.powerhalmgr.PowerHalMgr;
import com.mediatek.powerhalmgr.PowerHalMgrFactory;
import com.mediatek.powerhalwrapper.PowerHalWrapper;

import java.util.ArrayList;
import java.util.List;

/** @hide */
public class FramePolicy extends BaseFramePolicy{

    private static volatile FramePolicy sInstance = null;

    private static final long RECUME_FRAME_ID = -10000L;
    private static final String sTHREAD_NAME = TAG;
    private static final int FRAME_STEP_BASIC = -1000;
    private static final int NON_FRAME_STEP = FRAME_STEP_BASIC;
    private static final int NON_RENDER_THREAD_TID = -1;
    private static final double NO_DRAW_FRAME_VSYNC_RATIO = 0.1;

    private static final int RECEIVE_VSYNC_TO_INPUT = FRAME_STEP_BASIC + 1;

    //sbe rescue mode
    private final int SBE_RESUCE_MODE_END = 0;
    private final int SBE_RESUCE_MODE_START = 1;
    private final int SBE_RESUCE_MODE_TO_QUEUE_END = 2;
    private int curFrameRescueMode = SBE_RESUCE_MODE_TO_QUEUE_END;

    private long mFrameId = -1;

    private static float mFrameIntervalTime = 0;
    private static float mLimitVsyncTime = 0;
    private static int mFrameStep = NON_FRAME_STEP;
    private static boolean isNoDraw = true;
    private static boolean mIsDoframeCheck = false;
    private static boolean mAnimAcquiredLock = false;
    private static boolean mTranversalAcquiredLock = false;
    private static boolean isTranversalDraw = false;
    private static boolean isAnimationStepEnd = false;
    private static boolean isTraversalStepEnd = false;

    //Render thread tid
    private int mRenderThreadTid = NON_RENDER_THREAD_TID;
    private long frameStartTime = -1L;
    private boolean underRescue = false;

    //FPSGO no draw cmd
    private static final int PERF_RES_FPS_FPSGO_STOP_BOOST = 0x02004200;
    //FPSGO resuce
    private static final int PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE = 0x0203cA00;

    //Handler message define
    public static final int MSG_FRAME_BEGIN = 1;
    public static final int MSG_FRAME_END = 2;
    public static final int MSG_STEP_CHECK = 3;
    public static final int MSG_NO_DRAW = 4;
    public static final int MSG_TRAVERSAL_RESUCE_CHECK = 5;

    @Override
    protected void handleMessageInternal(Message msg) {
        switch (msg.what) {
            case MSG_FRAME_BEGIN:
                doFrameHintInternel(true, (long) msg.obj);
                break;
            case MSG_FRAME_END:
                doFrameHintInternel(false, (long) msg.obj);
                break;
            case MSG_STEP_CHECK:
                doFrameStepHintInternel(mFrameStep);
                break;
            case MSG_NO_DRAW:
                frameDraw(false);
                break;
            case MSG_TRAVERSAL_RESUCE_CHECK:
                traversalRescueChecker();
                break;
            default:
                break;
        }
    }

    public static FramePolicy getInstance() {
        if (null == sInstance) {
            synchronized (FramePolicy.class) {
                if (null == sInstance) {
                    sInstance = new FramePolicy();
                }
            }
        }
        return sInstance;
    }

    public FramePolicy() {
        super();
    }

    /*
     * For scrolling init only, frame check no need this.
     */
    @Override
    public boolean initLimitTime(float frameIntervalTime) {
        if (frameIntervalTime > 0 && frameIntervalTime != mFrameIntervalTime) {
            mFrameIntervalTime = frameIntervalTime;
            //In some high fresh rate, resuce needs to be more accurate,
            //so left 0.5ms as buffer time for vysnc hint dispatch to SBE.
            //The calculation formula is:
            //time_to_rescue = vysnc * CHECK_POINT - 0.5ms
            //Such as 120HZ, the resuce time should be:
            //8.33ms * 0.5 - 0.5ms = 3.665ms
            mLimitVsyncTime = (float)(frameIntervalTime * mCheckPoint - 0.5);
        }
        //must scrolling and initialed
        //if mFrameIntervalTime != 0 means it has been initialed.
        return mListenFrameHint && mFrameIntervalTime != 0;
    }

    @Override
    public void doFrameHint(boolean isBegin, long frameId, long vsyncWakeUpTime) {
        if (!mCoreServiceReady) {
            return;
        }

        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "vsync is begin = " + isBegin);
        }

        mIsDoframeCheck = isBegin;
        if (isBegin) {
            setFrameStep(RECEIVE_VSYNC_TO_INPUT);
            mWorkerHandler.sendMessage(
                mWorkerHandler.obtainMessage(MSG_FRAME_BEGIN, frameId));
        } else {
            if (!isNoDraw && isTranversalDraw) {
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(MSG_NO_DRAW, null), (long)drawFrameDelayTime());
            }
            mWorkerHandler.sendMessage(
                mWorkerHandler.obtainMessage(MSG_FRAME_END, frameId));
            //frame end check need next hint or not
            if (mDisableFrameRescue) {
                mListenFrameHint = false;
            }
        }
    }

    /**
     * Init and save this frame step time.
     * Frame step means doframe execute step, it include: input/animation/traversal
     */
    @Override
    public void doFrameStepHint(boolean isBegin, int step) {
        if (!mCoreServiceReady) {
            return;
        }
        //Init frame step execute time.
        if (isBegin) {
            setFrameStep(step);
            if (step == Choreographer.CALLBACK_TRAVERSAL) {
                mTranversalAcquiredLock = true;
                mWorkerHandler.sendMessage(
                    mWorkerHandler.obtainMessage(MSG_STEP_CHECK, null));
            }
        } else {
            if (step == Choreographer.CALLBACK_ANIMATION) {
                isAnimationStepEnd = true;
            }
            if (step == Choreographer.CALLBACK_TRAVERSAL) {
                isTraversalStepEnd = true;
            }
        }
    }

    //control frame state
    private void setFrameStep(int step) {
        if (step > mFrameStep) {
            mFrameStep = step;
        }
    }

    private void doFrameHintInternel(boolean isBegin, long frameId) {
        if (isBegin) {
            mFrameId = frameId;
            //For scrolling, check vsync~input/animation step is too long or not.
            if (mLimitVsyncTime != 0) {
                if (Config.isBoostFwkLogEnable()) {
                    LogUtil.mLogd(TAG, "scrolling!! try check animation and draw state.");
                }
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(MSG_STEP_CHECK), (long)mLimitVsyncTime);
                //traversal end checker
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(
                            MSG_TRAVERSAL_RESUCE_CHECK), (long)mFrameIntervalTime);
            }
        } else {
            mWorkerHandler.removeMessages(MSG_STEP_CHECK, null);
            mWorkerHandler.removeMessages(MSG_TRAVERSAL_RESUCE_CHECK, null);
            mAnimAcquiredLock = false;
            mTranversalAcquiredLock = false;
            isTranversalDraw = false;
            isAnimationStepEnd = false;
            mFrameStep = NON_FRAME_STEP;
            mFrameId = -1;
            underRescue = false;
            isTraversalStepEnd = false;
        }
    }

    private void doFrameStepHintInternel(int step) {
        if (!mIsDoframeCheck && step == NON_FRAME_STEP) {
            return;
        }

        switch(step) {
            case RECEIVE_VSYNC_TO_INPUT:
            case Choreographer.CALLBACK_INPUT:
            case Choreographer.CALLBACK_ANIMATION:
                if (!isAnimationStepEnd) {
                    powerHintForRender(PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE,
                            "animation end, curStep="+step);
                }
                if (!mAnimAcquiredLock && step != RECEIVE_VSYNC_TO_INPUT) {
                    if (Config.isBoostFwkLogEnable()) {
                        LogUtil.mLogd(TAG, "input/anim hint drop, enable rescue!");
                    }
                    frameDraw(true);
                    mAnimAcquiredLock = true;
                    break;
                }
            case Choreographer.CALLBACK_TRAVERSAL:
                if (mTranversalAcquiredLock && !mAnimAcquiredLock) {
                    if (Config.isBoostFwkLogEnable()) {
                        LogUtil.mLogd(TAG, "traversal step, enable rescue!");
                    }
                    frameDraw(true);
                    mTranversalAcquiredLock = false;
                    mAnimAcquiredLock = true;
                }
                break;
            default:
                break;
        }
    }

    private void traversalRescueChecker() {
        if (!underRescue && mFrameStep == Choreographer.CALLBACK_TRAVERSAL
                    && !isTraversalStepEnd) {
            powerHintForRender(PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE,
                    "traversal over vsync, curStep="+mFrameStep);
        }
    }

    private void frameDraw(boolean isDraw) {
        if (isDraw && mFrameId == -1) {
            if (LogUtil.DEBUG) {
                LogUtil.mLogd(TAG, "frame clear when rescue. mFrameId = " + mFrameId);
            }
            return;
        }

        if (isDraw) {
            if (isNoDraw) {
                if (LogUtil.DEBUG) {
                    LogUtil.trace("Draw, notify FPSGO draw" + mFrameId);
                }
            }
            mWorkerHandler.removeMessages(MSG_NO_DRAW, null);
            isNoDraw = false;
            isTranversalDraw = true;
        } else {
            powerHintForRender(PERF_RES_FPS_FPSGO_STOP_BOOST, "STOP: No draw");
            isNoDraw = true;
        }
    }

    private void powerHintForRender(int cmd, String tagMsg) {
        int renderThreadTid = getRenderThreadTid();
        if (LogUtil.DEBUG) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW,
                    "hint for [" + tagMsg + "] render id = " + renderThreadTid);
        }
        switch (cmd) {
            case PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE:
                underRescue = true;
                //IOCTRL call directly, more faster.
                mPowerHalWrap.mtkNotifySbeRescue(renderThreadTid,
                        curFrameRescueMode,
                        Config.FRAME_HINT_RESCUE_STRENGTH, RECUME_FRAME_ID);
                break;
            case PERF_RES_FPS_FPSGO_STOP_BOOST:
                int perf_lock_rsc[] = new int[] {cmd, renderThreadTid,
                        PERF_RES_POWERHAL_CALLER_TYPE, 1};
                perfLockAcquire(perf_lock_rsc);
                ScrollPolicy.getInstance().disableMTKScrollingPolicy(true);
                break;
            default:
                if (Config.isBoostFwkLogEnable()) {
                    LogUtil.mLogd(TAG, "not surpport for cmd = " + cmd);
                }
                break;
        }
        if (Config.isBoostFwkLogEnable()) {
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
    }

    private double drawFrameDelayTime() {
        if (mFrameIntervalTime == 0) {
            return -1;
        }
        float refreshRate = ScrollState.getRefreshRate();
        double delayCheckTime = mFrameIntervalTime * (refreshRate * NO_DRAW_FRAME_VSYNC_RATIO);
        return delayCheckTime;
    }

    private int getRenderThreadTid() {
        if (mRenderThreadTid == NON_RENDER_THREAD_TID) {
            mRenderThreadTid = ActivityInfo.getInstance().getRenderThreadTid();
        }
        return mRenderThreadTid;
    }

    private void perfLockAcquire(int[] resList) {
        if (null != mPowerHalService) {
            mPowerHandle = mPowerHalService.perfLockAcquire(mPowerHandle, 0, resList);
            mPowerHalService.perfLockRelease(mPowerHandle);
        }
    }
}