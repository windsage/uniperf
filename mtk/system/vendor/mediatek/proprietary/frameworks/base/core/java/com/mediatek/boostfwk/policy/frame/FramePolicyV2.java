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

import static android.view.DisplayEventReceiver.VSYNC_SOURCE_APP;

import android.content.Context;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.Trace;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.view.DisplayEventReceiver;
import android.view.MotionEvent;
import android.view.Choreographer;
import android.view.ThreadedRenderer;

import com.mediatek.boostfwk.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.info.ScrollState;
import com.mediatek.boostfwk.policy.scroll.ScrollPolicy;
import com.mediatek.boostfwk.scenario.frame.FrameScenario;
import com.mediatek.boostfwk.policy.frame.ScrollingFramePrefetcher;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.TasksUtil;
import com.mediatek.boostfwk.utils.Util;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.powerhalmgr.PowerHalMgr;
import com.mediatek.powerhalmgr.PowerHalMgrFactory;
import com.mediatek.powerhalwrapper.PowerHalWrapper;

import java.util.ArrayList;
import java.util.List;
//SPD: add for rescue when frame obtain view by song.tang 20240905 start
import com.mediatek.boostfwk.BoostFwkManager;
//SPD: add for rescue when frame obtain view by song.tang 20240905 end

/** @hide */
public class FramePolicyV2 extends BaseFramePolicy
        implements ScrollState.RefreshRateChangedListener{

    private static final boolean RESCUE_TO_NEXT_FRAME = true;
    private static final boolean ENBALE_FRAME_RESCUE = Config.isEnableFrameRescue();
    private static final Object LOCK = new Object();

    private static volatile FramePolicyV2 mInstance = null;

    private static final long DEFAULT_FRAME_ID = Integer.MIN_VALUE;
    private static final long PREFETCHER_FRAME_ID = -1;
    private static final long DEFAULT_FRAME_TIME = -1L;
    private static final int NON_RENDER_THREAD_TID = -1;
    private static final int MAX_WAITING_FRAME_COUNT = 5;
    private static final int RESCUE_COUNT_DOWN_THRESHOLD = 1;
    private static final double NO_DRAW_FRAME_VSYNC_RATIO = 0.1;
    private static final long NANOS_PER_MS = 1000000;
    private static final long CHECK_TIME_OFFSET_THRESHOLD_NS = 500000;

    //SBE frame step define
    private static final int FRAME_STEP_BASIC = -1;
    private static final int FRAME_STEP_DEFAULT = FRAME_STEP_BASIC;
    private static final int FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC = FRAME_STEP_BASIC + 1;
    private static final int FRAME_STEP_VSYNC_FOR_APP_TO_INPUT = FRAME_STEP_BASIC + 2;
    private static final int FRAME_STEP_DO_FRAME_INPUT = FRAME_STEP_BASIC + 3;
    private static final int FRAME_STEP_DO_FRAME_ANIMATION = FRAME_STEP_BASIC + 4;
    private static final int FRAME_STEP_DO_FRAME_TRAVERSAL = FRAME_STEP_BASIC + 5;

    //sbe rescue mode
    private final int SBE_RESUCE_MODE_END = 0;
    private final int SBE_RESUCE_MODE_START = 1;
    private final int SBE_RESUCE_MODE_TO_QUEUE_END = 2;
    private int mCurFrameRescueMode = SBE_RESUCE_MODE_START;

    private int mFrameHintMask = ActivityInfo.SBE_MASK_RECUE_HINT
            | ActivityInfo.SBE_MASK_FRAME_HINT;
    private int mRescueStrength = Config.FRAME_HINT_RESCUE_STRENGTH;
    private boolean mUpdateStrength = false;
    private int mRescueStrengthWhenHint = -1;

    private int mUnlockPowerHandle = 0;

    private static float mFrameIntervalTime = 0;
    private static float mLimitVsyncTime = 0;
    private static long mDelayStopHWUIHintTime = 0L;
    private static int mFrameStep = FRAME_STEP_DEFAULT;
    private static boolean mIsAnimationStepEnd = false;
    private static boolean mIsTraversalStepEnd = false;
    private static boolean mIsScrolling = false;

    //Render thread tid
    private int mRenderThreadTid = NON_RENDER_THREAD_TID;

    private DisplayEventReceiver mDisplayEventReceiver = null;
    private ScrollingFramePrefetcher mScrollingFramePrefetcher = null;

    //rescue info
    //MAX Cover 2 big jank in 120hz
    private final long RESCUE_DOFRAME_TIMEOUT_OUT_MS = 70;
    private final long RESCUE_WAIT_VSYNC_TIMEOUT_OUT_MS = 50;
    private boolean mRescueDoFrame = false;
    private boolean mRescueNextFrame = false;
    private boolean mRescueWhenWaitNextVsync = false;
    private volatile boolean mRescueRunning = false;
    private volatile boolean mUpdateFrameId = false;
    private volatile boolean mWaitNextVsync = false;
    private boolean mRescueCheckAgain= false;
    private boolean mHasNextFrame = false;
    private long mCurFrameStartTimeNanos = -1L;
    private long mCurFrameId = DEFAULT_FRAME_ID;
    private long mLastFrameEndFrameId = DEFAULT_FRAME_ID;
    private long mLastFrameEndVsyncWakeupTimeNanos = -1L;
    private long mLastFrameBeginVsyncDeadLineNanos = -1L;
    private long mRescuingFrameId = DEFAULT_FRAME_ID;
    private Handler mMainHandler = null;
    private long mMsgCheckTime = Long.MAX_VALUE;

    // this frameId which receive from vsync
    private long mVsyncFrameId = DEFAULT_FRAME_ID;
    private long mVsyncTimeNanos = -1L;
    private long mLastVsyncTimeNanos = -1L;

    private static final int RUNING_CHECK_STATUS_DEFAULT = 1;
    private static final int RUNING_CHECK_STATUS_WAITING = 2;
    private static final int RUNING_CHECK_STATUS_EXCUTED = 3;
    private static int mRunningCheckMsgStatus = RUNING_CHECK_STATUS_DEFAULT;
    private static final Runnable mRunningCheck = new Runnable() {
        @Override
        public void run() {
            if (LogUtil.DEBUG) {
                LogUtil.trace("msg run--");
            }
            mRunningCheckMsgStatus = RUNING_CHECK_STATUS_EXCUTED;
        }
    };

    private float mTouchRescueCheckPointOffset = 0.05f;
    private ScrollIdentify.TouchEventListener mTouchEventListener = null;

    //FPSGO no draw cmd
    private static final int PERF_RES_FPS_FPSGO_STOP_BOOST = 0x02004200;
    //FPSGO resuce
    private static final int PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE = 0x0203cA00;
    //Customize rescue strength
    private static final int PERF_RES_UX_SBE_RESCUE_ENHANS = 0x03004300;

    //Handler Message define
    public static final int MSG_FRAME_BEGIN = 1;
    public static final int MSG_FRAME_END = 2;
    public static final int MSG_RESCUE_HALF_VSYNC_CHECK = 3;
    public static final int MSG_ON_VSYNC = 4;
    public static final int MSG_RESUCE_ONE_VSYNC_CHECK = 5;
    public static final int MSG_RESCUE_TIME_OUT = 6;
    public static final int MSG_UPDATE_RESCUE_FRAME_ID = 7;
    public static final int MSG_REQUEST_VSYNC = 8;
    public static final int MSG_DELAY_STOP_HWUI_HINT = 9;

    @Override
    protected void handleMessageInternal(Message msg) {
        switch (msg.what) {
            case MSG_FRAME_BEGIN:
                break;
            case MSG_FRAME_END:
                onDoFrameEndInternal(mLastFrameEndFrameId);
                break;
            case MSG_RESCUE_HALF_VSYNC_CHECK:
                halfVsyncRescueCheck();
                break;
            case MSG_ON_VSYNC:
                if (msg.obj != null) {
                    VsyncInfo info = (VsyncInfo)msg.obj;
                    onVsyncInternal(info.frameId, info.vsyncWakeUpTime);
                }
                break;
            case MSG_RESUCE_ONE_VSYNC_CHECK:
                oneVsyncRescueCheck();
                break;
            case MSG_UPDATE_RESCUE_FRAME_ID:
                updateRescueFrameId();
                break;
            case MSG_REQUEST_VSYNC:
                requestVsyncInternal();
                break;
            case MSG_RESCUE_TIME_OUT:
                shutdownSBERescue("time out");
                break;
            case MSG_DELAY_STOP_HWUI_HINT:
                dleayStopHwuiHint();
                break;
            default:
                break;
        }
    }

    public static FramePolicyV2 getInstance() {
        if (null == mInstance) {
            synchronized (FramePolicyV2.class) {
                if (null == mInstance) {
                    mInstance = new FramePolicyV2();
                }
            }
        }
        return mInstance;
    }

    private FramePolicyV2() {
        super();
        if (Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_2) {
            mFrameHintMask = ActivityInfo.SBE_MASK_RECUE_HINT;
        } else {
            if (Config.preRunningRescue) {
                mMainHandler = Handler.createAsync(Looper.getMainLooper());
            }

            if (ENBALE_FRAME_RESCUE) {
                //this callback will receive after app handled
                //and SBE check scroll state
                mTouchEventListener = new ScrollIdentify.TouchEventListener() {
                    @Override
                    public void onTouchEvent(MotionEvent event) {
                        onTouchEventInternal(event);
                    }
                };
                ScrollIdentify.getInstance().registerTouchEventListener(mTouchEventListener);
            }
        }
    }

    private void onTouchEventInternal(MotionEvent event) {
        if (!mCoreServiceReady || event == null
                //if customize check point, do nothing
                || mCheckPoint != DEFAULT_CHECK_POINT
                //refresh rate is 90hz or move event, no need to handle
                || ScrollState.getRefreshRate() >= 90) {
            return;
        }

        //after vendor android U, sf set 60hz app duration as 16.6,
        //because UX give CPU capacity base on  1 vsync,
        //when app duration is 16.6, may meet the boundary,
        //so need rescue more faster
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mFrameIntervalTime != 0) {
                    updateLimitVsyncTime(mCheckPoint - mTouchRescueCheckPointOffset);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mFrameIntervalTime != 0) {
                    updateLimitVsyncTime(mCheckPoint);
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void initCoreServiceInternal() {
        String pkgName = ActivityInfo.getInstance().getPackageName();
        mDisplayEventReceiver = new DisplayEventReceiverImpl(
                    mWorkerHandler.getLooper(), VSYNC_SOURCE_APP);
        super.initCoreServiceInternal();
    }

    /*
     * For scrolling init only, frame check no need this.
     */
    @Override
    public boolean initLimitTime(float frameIntervalTime) {
        if (frameIntervalTime > 0 && frameIntervalTime != mFrameIntervalTime) {
            mFrameIntervalTime = frameIntervalTime;
            updateLimitVsyncTime(mCheckPoint);
            mDelayStopHWUIHintTime = (long)(frameIntervalTime * MAX_WAITING_FRAME_COUNT);
            initialRescueStrengthOnce(ScrollState.getRefreshRate());
        }
        //must scrolling and initialed
        //if mFrameIntervalTime != 0 means it has been initialed.
        return ENBALE_FRAME_RESCUE && mListenFrameHint && mFrameIntervalTime != 0;
    }

    @Override
    public void doFrameHint(boolean isBegin, long frameId,
            long vsyncWakeUpTime, long vsyncDeadlineNanos) {
        if (!mCoreServiceReady) {
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "vsync is begin = " + isBegin + " frame="+frameId);
        }
        if (isBegin) {
            onDoFrameBegin(frameId, vsyncDeadlineNanos);
        } else {
            onDoFrameEnd(vsyncWakeUpTime);
        }
    }

    private void onDoFrameBegin(long frameId, long vsyncDeadlineNanos) {
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("onDoFrameBegin=" + frameId);
        }

        updateBasicFrameInfo(frameId, SystemClock.uptimeNanos());
        setFrameStep(FRAME_STEP_VSYNC_FOR_APP_TO_INPUT);
        mRescuingFrameId = DEFAULT_FRAME_ID;
        if (Config.preRunningRescue && mMainHandler != null) {
            //msg not run and send a msg
            if (mRunningCheckMsgStatus == RUNING_CHECK_STATUS_WAITING) {
                LogUtil.trace("remove check ");
                mMainHandler.removeCallbacks(mRunningCheck);
            }
            mRunningCheckMsgStatus = RUNING_CHECK_STATUS_DEFAULT;
            mMsgCheckTime = Long.MAX_VALUE;
        }
        if (Config.BOOST_FWK_VERSION_2 >= Config.getBoostFwkVersion()) {
            mRescueDoFrame = false;
        }
        //SPD: add for rescue prefetcher frame by song.tang 20240903 start
        if(frameId == PREFETCHER_FRAME_ID) {
            mRescueNextFrame = true;
        }
        //SPD: add for rescue prefetcher frame by song.tang 20240903 end     
        if (mRescueWhenWaitNextVsync || (mRescueNextFrame && !mDisableFrameRescue)) {
            if (Config.BOOST_FWK_VERSION_2 < Config.getBoostFwkVersion()) {
                //ioctl drictly
                updateRescueFrameId();
            } else {
                mWorkerHandler.sendMessageAtFrontOfQueue(mWorkerHandler.obtainMessage(
                        MSG_UPDATE_RESCUE_FRAME_ID));
            }
        }

        if (mLastFrameBeginVsyncDeadLineNanos > 0 && frameId > 0
                && mLastFrameBeginVsyncDeadLineNanos == vsyncDeadlineNanos
                && mActivityInfo != null
                && !mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_DESIGN_MULTI_FRAME)) {
            mActivityInfo.mutilFramePage(true);
        }
        if (frameId > 0 && vsyncDeadlineNanos > 0) {
            mLastFrameBeginVsyncDeadLineNanos = vsyncDeadlineNanos;
        }
        LogUtil.traceEnd();
    }

    private void onDoFrameEnd(long vsyncWakeUpTime) {
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("onDoFrameEnd=" + mCurFrameId);
        }
        mFrameStep = FRAME_STEP_DEFAULT;
        mLastFrameEndFrameId = mCurFrameId;
        if (vsyncWakeUpTime > 0) {
            //if vsync wakeuptime is -1, do not using it.
            mLastFrameEndVsyncWakeupTimeNanos = vsyncWakeUpTime;
        }
        final long curFrameStartTimeNanos = mCurFrameStartTimeNanos;
        updateBasicFrameInfo(DEFAULT_FRAME_ID, DEFAULT_FRAME_TIME);

        if (Config.BOOST_FWK_VERSION_2 >= Config.getBoostFwkVersion()
                && mLastFrameEndFrameId != PREFETCHER_FRAME_ID) {
            mWorkerHandler.removeMessages(MSG_RESCUE_HALF_VSYNC_CHECK);
            mWorkerHandler.removeMessages(MSG_RESUCE_ONE_VSYNC_CHECK);
        }

        if (Config.preRunningRescue && mMainHandler != null) {
            long frameTime = SystemClock.uptimeNanos() - curFrameStartTimeNanos;
            if (LogUtil.DEBUG) {
                LogUtil.trace("Check should post check "
                        + frameTime + " " + mFrameIntervalTime);
            }
            if (frameTime > 0 && frameTime < (mFrameIntervalTime * NANOS_PER_MS)) {
                mMainHandler.removeCallbacks(mRunningCheck);
                boolean added = mMainHandler.post(mRunningCheck);
                mRunningCheckMsgStatus = RUNING_CHECK_STATUS_WAITING;
                mMsgCheckTime = System.currentTimeMillis();
                if (LogUtil.DEBUG && added) {
                    LogUtil.trace("post check ");
                }
            } else {
                mRunningCheckMsgStatus = RUNING_CHECK_STATUS_DEFAULT;
                mMsgCheckTime = Long.MAX_VALUE;
            }
        }

        if (Config.BOOST_FWK_VERSION_2 < Config.getBoostFwkVersion()) {
            mIsAnimationStepEnd = false;

            if (!mIsTraversalStepEnd && (mRescueDoFrame || mRescueWhenWaitNextVsync)) {
                //there is no draw step for this frame
                //end current frame rescue
                shutdownSBERescue("frame end-no draw");
            } else if (mRescueWhenWaitNextVsync) {
                //if rescue when waiting vsync,
                //mRescueWhenWaitNextVsync mark false at begining of this frame, but now still true,
                //means mutil thread cause doframe do not trigger update frame id
                //stop it
                shutdownSBERescue("do not update frame id");
            }

            mRescueWhenWaitNextVsync = false;
            mRescueDoFrame = false;
            mIsTraversalStepEnd = false;

            if (!mDisableFrameRescue) {
                mListenFrameHint = true;
            }
        } else {
            mWorkerHandler.sendMessageAtFrontOfQueue(
                    mWorkerHandler.obtainMessage(MSG_FRAME_END));

            mWaitNextVsync = false;
            if (mHasNextFrame && !mDisableFrameRescue) {
                requestVsync();
                mListenFrameHint = true;
            }
            mHasNextFrame = false;
        }
        if (mDisableFrameRescue) {
            mListenFrameHint = false;
            mWorkerHandler.sendEmptyMessageDelayed(MSG_DELAY_STOP_HWUI_HINT,
                    mDelayStopHWUIHintTime);
        }
        LogUtil.traceEnd();
    }

    private void dleayStopHwuiHint() {
        //incease user scrolling when stop, check it again^M
        if (!mListenFrameHint) {
            updateFrameTask(mFrameHintMask, false);
        }
    }

    private void updateFrameTask(int mask, boolean add) {
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "updateFrameTask mask=" + mask);
        }
        ThreadedRenderer.needFrameCompleteHint(ActivityInfo.updateSBEMask(mask, add));
    }

    private void updateBasicFrameInfo(long curFrameId, long frameTimeNanos) {
        mCurFrameId = curFrameId;
        mCurFrameStartTimeNanos = frameTimeNanos;
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
        //SPD: add for rescue when frame obtain view by song.tang 20240905 start
        if (step == BoostFwkManager.Draw.FRAME_OBTAIN_VIEW
                && mCurFrameId > 0 && !mRescueDoFrame) {
            //1. there is no frame begin or prefetcher frame
            //2. already rescue
            mWorkerHandler.removeMessages(MSG_RESCUE_HALF_VSYNC_CHECK);
            mWorkerHandler.removeMessages(MSG_RESUCE_ONE_VSYNC_CHECK);
            mRescueDoFrame = true;
            doSBERescue("obtainView");
            return;
        }
        //SPD: add for rescue when frame obtain view by song.tang 20240905 end     
        //Init frame step execute time.
        if (isBegin) {
            setFrameStep(mappingStepForDoFrame(step));
        } else {
            if (step == Choreographer.CALLBACK_ANIMATION) {
                mIsAnimationStepEnd = true;
            } else if (step == Choreographer.CALLBACK_TRAVERSAL) {
                mIsTraversalStepEnd = true;
            }
        }
    }

    private int mappingStepForDoFrame(int step) {
        switch(step) {
            case Choreographer.CALLBACK_INPUT:
                return FRAME_STEP_DO_FRAME_INPUT;
            case Choreographer.CALLBACK_ANIMATION:
                return FRAME_STEP_DO_FRAME_ANIMATION;
            case Choreographer.CALLBACK_TRAVERSAL:
                return FRAME_STEP_DO_FRAME_TRAVERSAL;
            default:
                return FRAME_STEP_DEFAULT;
        }
    }

    //control frame state
    private void setFrameStep(int step) {
        if (step > mFrameStep) {
            mFrameStep = step;
            if (LogUtil.DEBUG) {
                LogUtil.trace("new step=" + step + " frameId="+mCurFrameId);
            }
        }
    }

    @Override
    public void onRequestNextVsync() {
        if (!mListenFrameHint || !mCoreServiceReady) {
            return;
        }
        if (mScrollingFramePrefetcher == null) {
            mScrollingFramePrefetcher = ScrollingFramePrefetcher.getInstance();
        }
        if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
            requestVsync();
        } else {
            //doFrame not begin and not pre-animation
            if (mFrameStep < FRAME_STEP_VSYNC_FOR_APP_TO_INPUT
                        && !mScrollingFramePrefetcher.isPreAnimationRunning()) {
                requestVsync();
            } else {
                mHasNextFrame = true;
            }
        }
    }

    public void requestVsync() {
        if (!mCoreServiceReady) {
            return;
        }
        mWorkerHandler.removeMessages(MSG_REQUEST_VSYNC);
        mWorkerHandler.sendEmptyMessage(MSG_REQUEST_VSYNC);
    }

    @Override
    public void onScrollStateChange(boolean scrolling) {
        super.onScrollStateChange(scrolling);
        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG,"onScrollStateChange scroll=" + scrolling);
        }
        if (mListenFrameHint) {
            mWorkerHandler.removeMessages(MSG_DELAY_STOP_HWUI_HINT);
            updateFrameTask(mFrameHintMask, true);
        }
    }

    /**
     * For 2.0
     *  */
    private void onDoFrameEndInternal(long frameId) {
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("onDoFrameEndInternal frameId= " + frameId);
        }
        mIsAnimationStepEnd = false;

        if (!mIsTraversalStepEnd && (mRescueDoFrame || mRescueWhenWaitNextVsync)) {
            //there is no draw step for this frame
            //end current frame rescue
            shutdownSBERescue("frame end-no draw");
        }

        mRescueWhenWaitNextVsync = false;
        mIsTraversalStepEnd = false;

        LogUtil.traceEnd();
    }

    private void requestVsyncInternal() {
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("requestVsyncInternal frameId= " + mCurFrameId);
        }
        if (mDisplayEventReceiver != null) {
            mDisplayEventReceiver.scheduleVsync();
            mWaitNextVsync = true;
        }
        LogUtil.traceEnd();
    }

    private void onVsyncInternal(long frameId, long vsyncWakeupTimeNanos) {
        if (!mWaitNextVsync) {
            if (LogUtil.DEBUG) {
                LogUtil.trace("this vsync is later than doframe, the doframe is end");
            }
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("onVsyncInternal frameId= " + frameId + " " + mFrameStep);
        }
        mVsyncFrameId = frameId;
        mVsyncTimeNanos = SystemClock.uptimeNanos();

        if (mFrameStep != FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC) {
            setFrameStep(FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC);
            rescueCheck(vsyncWakeupTimeNanos);
        }
        mWaitNextVsync = false;
        LogUtil.traceEnd();
    }

    /**
     *
     * @param vsyncWakeupTimeNanos
     * @return true means this vsync do frame already excuted
     */
    private boolean invalidVsync(long vsyncWakeupTimeNanos) {
        return vsyncWakeupTimeNanos > 0
                && vsyncWakeupTimeNanos <= mLastFrameEndVsyncWakeupTimeNanos;
    }

    private void rescueCheck(long vsyncWakeupTimeNanos) {
        if (mDisableFrameRescue
                || (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2
                    && (invalidVsync(vsyncWakeupTimeNanos)
                    || mRescueDoFrame || mRescueWhenWaitNextVsync))) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "rescue check when mDisableFrameRescue="
                        + mDisableFrameRescue + " mRescueDoFrame=" + mRescueDoFrame
                        + " mRescueWhenWaitNextVsync=" + mRescueWhenWaitNextVsync
                        + " mLastVsyncWakeupTimeNanos=" + mLastFrameEndVsyncWakeupTimeNanos
                        + " vsyncWakeupTimeNanos=" + vsyncWakeupTimeNanos);
            }
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("rescueCheck frameId= " + mCurFrameId + " vsyncTime="
                    + mVsyncTimeNanos + " frameStartTime=" + mCurFrameStartTimeNanos
                    + " runningCheck=" + mRunningCheckMsgStatus + " " + mRescueRunning);
        }
        mLastVsyncTimeNanos = vsyncWakeupTimeNanos;

        final long vsyncTimeNanos = mVsyncTimeNanos;
        final long curFrameStartTimeNanos = mCurFrameStartTimeNanos;
        long frameOffset = 0L;
        float halfCheckTime = mLimitVsyncTime;
        float oneVsyncCheckTime = mFrameIntervalTime;
        // curVsync is late doFrame more than 0.5ms
        if (curFrameStartTimeNanos > 0
                && (frameOffset = vsyncTimeNanos - curFrameStartTimeNanos)
                        > CHECK_TIME_OFFSET_THRESHOLD_NS) {
            //recompute
            halfCheckTime = adjustCheckTime(halfCheckTime, frameOffset);
            oneVsyncCheckTime = adjustCheckTime(oneVsyncCheckTime, frameOffset);
        }

        mRescueRunning = false;
        if (Config.preRunningRescue
                //this is running case or frame prefetcher
                && (mCurFrameId == DEFAULT_FRAME_ID
                    || (mCurFrameId < 0 && mCurFrameId != DEFAULT_FRAME_ID))
                && mRunningCheckMsgStatus == RUNING_CHECK_STATUS_WAITING
                && (System.currentTimeMillis() - mMsgCheckTime) >= 1) {
            mRescueRunning = true;
            mMsgCheckTime = Long.MAX_VALUE;
        }

        //before send check msg, rm current time out message
        mWorkerHandler.removeMessages(MSG_RESCUE_TIME_OUT);

        //mutil thread cause run in this function, check again.
        if (mWaitNextVsync || mRescueCheckAgain) {
            if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                mWorkerHandler.removeMessages(MSG_RESCUE_HALF_VSYNC_CHECK);
                mWorkerHandler.removeMessages(MSG_RESUCE_ONE_VSYNC_CHECK);
            }
            if (mRescueRunning) {
                halfVsyncRescueCheck();
            } else {
                //rescue current frame if needed
                mWorkerHandler.sendMessageDelayed(
                        mWorkerHandler.obtainMessage(
                                MSG_RESCUE_HALF_VSYNC_CHECK), (long)(halfCheckTime));
            }
            //rescue next frame if needed
            mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(
                            MSG_RESUCE_ONE_VSYNC_CHECK), (long)(oneVsyncCheckTime));
        } else if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "rescue check when mWaitNextVsync="
                    + mWaitNextVsync);
        }
        LogUtil.traceEnd();
    }

    private float adjustCheckTime(float defaultCheckTime, float offset) {
        float checkTime = (defaultCheckTime * NANOS_PER_MS - offset) / NANOS_PER_MS;
        return checkTime > 0 ? checkTime : 0;
    }

    private void rescueCheckAgain() {
        mRescueCheckAgain = true;
        //follow vsync wakeup time
        rescueCheck(mLastVsyncTimeNanos < 0 ? System.nanoTime() : mLastVsyncTimeNanos);
        mRescueCheckAgain = false;
    }

    private void halfVsyncRescueCheck() {
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndLog(TAG, "halfVsyncRescueCheck=" + mFrameStep
                    +" frame=" + mCurFrameId + " " +mRescueRunning);
        }
        switch(mFrameStep) {
            case FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC:
                mRescueWhenWaitNextVsync = true;
                if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                    mRescueStrengthWhenHint = mActivityInfo.getLastFrameCapacity();
                }
                if (mRescueRunning) {
                    //check it last time, incase mutil thread
                    if (mCurFrameId == DEFAULT_FRAME_ID) {
                        doSBERescue( "rescue running");
                    } else {
                        rescueCheckAgain();
                        mRescueWhenWaitNextVsync = false;
                    }
                    mRescueRunning = false;
                } else {
                    doSBERescue( "waiting vsync");
                }
                mRescueStrengthWhenHint = -1;
                break;
            case FRAME_STEP_VSYNC_FOR_APP_TO_INPUT:
            case FRAME_STEP_DO_FRAME_INPUT:
            case FRAME_STEP_DO_FRAME_ANIMATION:
                if (!mIsAnimationStepEnd) {
                    mRescueDoFrame = true;
                    doSBERescue("animation end");
                }
                break;
            default:
                break;
        }
        LogUtil.traceEnd();
    }

    private void oneVsyncRescueCheck() {
        if (!mRescueDoFrame &&
                mFrameStep == FRAME_STEP_DO_FRAME_TRAVERSAL && !mIsTraversalStepEnd) {
            mRescueDoFrame = true;
            doSBERescue("traversal over vsync");
            mRescueNextFrame = RESCUE_TO_NEXT_FRAME;
        } else if (RESCUE_TO_NEXT_FRAME && mRescueDoFrame
                && (mFrameStep <= FRAME_STEP_DO_FRAME_TRAVERSAL
                && mFrameStep > FRAME_STEP_DEFAULT)) {
            //already rescue
            mRescueNextFrame = RESCUE_TO_NEXT_FRAME;
            //rescue more?
        }
        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "oneVsyncRescueCheck mFrameStep=" + mFrameStep
                    + " mRescueNextFrame="+mRescueNextFrame
                    +" mRescueDoFrame=" + mRescueDoFrame + " frameId=" + mCurFrameId);
        }
    }

    private void updateRescueFrameId() {
        if (mRescueWhenWaitNextVsync) {
            mRescueWhenWaitNextVsync = false;
            if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                mRescueStrengthWhenHint = 0;
            }
            mUpdateFrameId = true;
            doSBERescue("update frame id");
            mUpdateFrameId = false;
            mRescueDoFrame = true;
            mRescueStrengthWhenHint = -1;
        } else if (mRescueNextFrame) {
            mRescueNextFrame = false;
            doSBERescue("update frame id");
            mRescueDoFrame = true;
        }
    }

    private void doSBERescue(String tagMsg) {
        final long frameId = mCurFrameId;
        //this frame - doframe is end, do not rescue
        if (frameId == DEFAULT_FRAME_ID && mFrameStep != FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC) {
            LogUtil.traceAndLog(TAG, "do not rescue beacause frameID=DEFAULT_FRAME_ID");
            return;
        }
        mRescuingFrameId = frameId;
        if (mUpdateStrength) {
            mUpdateStrength = false;
            powerHintForRender(PERF_RES_UX_SBE_RESCUE_ENHANS, "change to " + mRescueStrength);
        }
        mCurFrameRescueMode = SBE_RESUCE_MODE_START;
        if (Config.BOOST_FWK_VERSION_2 < Config.getBoostFwkVersion()
                && mRescuingFrameId == PREFETCHER_FRAME_ID) {
            mRescueNextFrame = RESCUE_TO_NEXT_FRAME;
        }
        powerHintForRender(PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE,
                tagMsg +" curStep="+mFrameStep + " mode="+mCurFrameRescueMode);
        mWorkerHandler.removeMessages(MSG_RESCUE_TIME_OUT);
        mWorkerHandler.sendEmptyMessageDelayed(
                MSG_RESCUE_TIME_OUT, mRescueWhenWaitNextVsync ?
                RESCUE_WAIT_VSYNC_TIMEOUT_OUT_MS : RESCUE_DOFRAME_TIMEOUT_OUT_MS);
    }

    private void shutdownSBERescue(String tagMsg) {
        mCurFrameRescueMode = SBE_RESUCE_MODE_END;
        powerHintForRender(PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE,
                "shutdown " + tagMsg +" curStep="+mFrameStep + " mode="+mCurFrameRescueMode);
        clearRescueInfo();
    }

    private void clearRescueInfo() {
        mRescuingFrameId = DEFAULT_FRAME_ID;
        mRescueWhenWaitNextVsync = false;
        mRescueDoFrame = false;
        mRescueNextFrame = false;
        mWorkerHandler.removeMessages(MSG_RESCUE_TIME_OUT);
    }

    private void initialRescueStrengthOnce(float refreshRate) {
        //SPD:add for special app boost by song.tang 20240829 start
        if (Config.BOOST_FWK_VERSION_2 <= Config.getBoostFwkVersion()) {
            return;
        }
        //SPD:add for special app boost by song.tang 20240829 end    
        int old = mRescueStrength;
        int result = mRescueStrength;
        //Update String with current refreshRate
        if (refreshRate <= 65) {
            result = Config.FRAME_HINT_RESCUE_STRENGTH;
        } else if (refreshRate < 95) {
            result = Config.FRAME_HINT_RESCUE_STRENGTH;
        }
        if (result != old) {
            mRescueStrength = result;
            mUpdateStrength = true;
        }
    }

    @Override
    public void onDisplayRefreshRateChanged(int displayId,
            float refreshRate, float frameIntervalNanos) {
        initialRescueStrengthOnce(refreshRate);
    }

    private void powerHintForRender(int cmd, String tagMsg) {
        int renderThreadTid = getRenderThreadTid();
        if (renderThreadTid == Integer.MIN_VALUE) {
            clearRescueInfo();
            if (LogUtil.DEBUG) {
                LogUtil.traceAndLog(TAG, mCurFrameId + "cancel rescue: " + renderThreadTid);
            }
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, "hint for [" + tagMsg + "] renderId=" + renderThreadTid
                    + " frameId="+mRescuingFrameId + " strength="+mRescueStrengthWhenHint);
        }
        switch (cmd) {
            case PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE:
                final long rescueFrameId = mRescuingFrameId;
                if (rescueFrameId != DEFAULT_FRAME_ID
                        //for prefecher frame
                        && rescueFrameId == PREFETCHER_FRAME_ID
                        && mCurFrameId != PREFETCHER_FRAME_ID
                        //for original frame
                        && rescueFrameId <= mLastFrameEndFrameId
                        && mCurFrameRescueMode == SBE_RESUCE_MODE_START) {
                    //incase runnable cause rescue delay, then rescue can not stop
                    clearRescueInfo();
                    if (LogUtil.DEBUG) {
                        LogUtil.trace("do not rescue frameEndFrameId=" + mLastFrameEndFrameId);
                    }
                    break;
                }
                if (mCurFrameId != DEFAULT_FRAME_ID && mRescueRunning && !mUpdateFrameId) {
                    //if rescue running, frameId must default frame id
                    //incase mutil thread cause rescue, but this frame already running
                    clearRescueInfo();
                    if (LogUtil.DEBUG) {
                        LogUtil.trace("do not rescue running mCurFrameId=" + mCurFrameId);
                    }
                    mRescueRunning = false;
                    rescueCheckAgain();
                    break;
                }
                int strength = Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2 ?
                        mRescueStrengthWhenHint : Config.FRAME_HINT_RESCUE_STRENGTH;
                //SPD: add for update rescue strength by song.tang 20240521 start
                if (rescueFrameId == PREFETCHER_FRAME_ID) {
                    //if prefetcher frame, use max strength
                    strength = Config.FRAME_HINT_RESCUE_STRENGTH_MAX;
                }
                strength = Math.max(mRescueStrength, strength);
                LogUtil.traceMessage(TAG + "#mtkNotifySbeRescue"
                        + " hint for [" + tagMsg + "]"
                        + " rescueFrameId: " + rescueFrameId
                        + " strength: " + strength);
                //SPD: add for update rescue strength by song.tang 20240521 end                  
                //IOCTRL call directly, more faster.
                mPowerHalWrap.mtkNotifySbeRescue(renderThreadTid,
                        mCurFrameRescueMode,
                        strength,  rescueFrameId < 0  ? 1 : rescueFrameId);
                break;
            case PERF_RES_UX_SBE_RESCUE_ENHANS:
                int enhance[] = new int[] {PERF_RES_UX_SBE_RESCUE_ENHANS, mRescueStrength,
                        PERF_RES_POWERHAL_CALLER_TYPE, 1};
                perfLockAcquireUnlock(enhance);
                break;
            case PERF_RES_FPS_FPSGO_STOP_BOOST:
                int perf_lock_rsc[] = new int[] {cmd, renderThreadTid,
                        PERF_RES_POWERHAL_CALLER_TYPE, 1};
                perfLockAcquire(perf_lock_rsc);
                ScrollPolicy.getInstance().disableMTKScrollingPolicy(true);
                break;
            default:
                if (LogUtil.DEBUG) {
                    LogUtil.mLogd(TAG, "not surpport for cmd = " + cmd);
                }
                break;
        }
        LogUtil.traceEnd();
    }

    private double drawFrameDelayTime() {
        if (mFrameIntervalTime == 0) {
            return -1;
        }
        float refreshRate = Util.getRefreshRate();
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

    private void perfLockAcquireUnlock(int[] resList) {
        if (null != mPowerHalService) {
            mUnlockPowerHandle = mPowerHalService.perfLockAcquire(mUnlockPowerHandle, 0, resList);
        }
    }

    private static class VsyncInfo {
        long frameId;
        long vsyncWakeUpTime;
    }

    private static class DisplayEventReceiverImpl extends DisplayEventReceiver {

        VsyncInfo info = new VsyncInfo();

        public DisplayEventReceiverImpl(Looper looper, int vsyncSource) {
            super(looper, vsyncSource, 0);
        }

        @Override
        public void onVsync(long timestampNanos, long physicalDisplayId, int frame,
                VsyncEventData vsyncEventData) {
            if (mInstance != null) {
                if (Config.BOOST_FWK_VERSION_2 < Config.getBoostFwkVersion()) {
                    info.frameId = vsyncEventData.preferredFrameTimeline().vsyncId;
                    info.vsyncWakeUpTime = timestampNanos;
                    mInstance.mWorkerHandler.sendMessageAtFrontOfQueue(
                        mInstance.mWorkerHandler.obtainMessage(MSG_ON_VSYNC, info));
                } else {
                    mInstance.onVsyncInternal(
                            vsyncEventData.preferredFrameTimeline().vsyncId, timestampNanos);
                }
            }
        }
    }

    @Override
    public void onChange(Context c) {
        super.onChange(c);
        //SPD:add for special app boost by song.tang 20240829 start
        String packageName = (c == null ? "" : c.getPackageName());
        if (Config.SPECIAL_APP_LIST.contains(packageName)) {
            mCheckPoint = 0.3f;
            mRescueStrength = Config.FRAME_HINT_RESCUE_STRENGTH + Config.FRAME_HINT_RESCUE_STRENGTH_FLOOR;
            LogUtil.traceAndMLogd(TAG, "special app mCheckPoint: " + mCheckPoint);
        }
        if (Config.BOOST_FWK_VERSION_2 <= Config.getBoostFwkVersion()) {
            updateLimitVsyncTime(mCheckPoint);
        }
        //SPD:add for special app boost by song.tang 20240829 end
    }

    @Override
    public void onActivityPaused(Context c) {
        if (null != mPowerHalService) {
            mPowerHalService.perfLockRelease(mUnlockPowerHandle);
        }
        if (mListenFrameHint) {
            updateFrameTask(mFrameHintMask, false);
            mListenFrameHint = false;
        }
    }

    private void updateLimitVsyncTime(float checkPoint) {
        //In some high fresh rate, resuce needs to be more accurate,
        //so left 0.5ms as buffer time for vysnc hint dispatch to SBE.
        //The calculation formula is:
        //time_to_rescue = vysnc * CHECK_POINT - 0.5ms
        //Such as 120HZ, the resuce time should be:
        //8.33ms * 0.5 - 0.5ms = 3.665ms
        mLimitVsyncTime = (float)(mFrameIntervalTime * checkPoint - 0.5);
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "update 1/2 checkpoint:" + checkPoint
                    + " limit vsync time:" + mLimitVsyncTime);
        }
    }
}
