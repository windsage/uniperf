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

import com.mediatek.boostfwk.BoostFwkManager;
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
import java.util.LinkedList;
import java.util.List;

/** @hide
 *  this version support those feature:
 *  1. if all rescue it obtian view or inflate view,
 *     moving checkin point to frame hint obtain view
 *     a. if pre-anim, thinking as running rescue
 *  2. if all rescue is traversal rescue in this page,
 *     moving traversal to 1/2 check point monitor scope
 *  3. all of teh rescue info are checking in 6 scrolling window,
 *     if any one of them is not suitable of condition, giving up.
 *  4. will notify fpsgo more info, and enable dynamic rescue
*/
public class FramePolicyV3 extends BaseFramePolicy
        implements ScrollState.RefreshRateChangedListener{

    private static final boolean RESCUE_TO_NEXT_FRAME = true;
    private static final boolean ENBALE_FRAME_RESCUE = Config.isEnableFrameRescue();
    private static final Object LOCK = new Object();

    private static volatile FramePolicyV3 mInstance = null;

    private static final long DEFAULT_FRAME_ID = Integer.MIN_VALUE;
    private static final long PREFETCHER_FRAME_ID = -1;
    private static final long DEFAULT_FRAME_TIME = -1L;
    private static final int NON_RENDER_THREAD_TID = -1;
    private static final int MAX_WAITING_FRAME_COUNT = 5;
    private static final int RESCUE_COUNT_DOWN_THRESHOLD = 1;
    private static final double NO_DRAW_FRAME_VSYNC_RATIO = 0.1;
    private static final long NANOS_PER_MS = 1000000;
    private static final float NANOS_PER_MS_F = 1000000.0f;
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

    //sbe rescue type,max is 16 bit
    private final int SBE_RESCUE_TYPE_WAITING_ANIMATION_END = 1 << 0;
    private final int SBE_RESCUE_TYPE_TRAVERSAL_OVER_VSYNC = 1 << 1;
    private final int SBE_RESCUE_TYPE_RUNNING = 1 << 2;
    private final int SBE_RESCUE_TYPE_COUNTINUE_RESCUE = 1 << 3;
    private final int SBE_RESCUE_TYPE_WAITING_VSYNC = 1 << 4;
    private final int SBE_RESCUE_TYPE_TRAVERSAL_DYNAMIC = 1 << 5;
    private final int SBE_RESCUE_TYPE_OI = 1 << 6;
    private final int SBE_RESCUE_TYPE_MAX_ENHANCE = 1 << 7;
    private final int SBE_RESCUE_TYPE_SECOND_RESCUE = 1 << 8;
    private final int SBE_RESCUE_TYPE_BUFFER_COUNT_FILTER = 1 << 9;
    private final int SBE_RESCUE_TYPE_PRE_ANIMATION = 1 << 10;
    private final int SBE_RESCUE_TYPE_ENABLE_MARGIN = 1 << 11;
    private int mCurFrameRescueType = 0;

    private int mFrameHintMask = ActivityInfo.SBE_MASK_RECUE_HINT
            | ActivityInfo.SBE_MASK_FRAME_HINT;
    private int mRescueStrength = Config.FRAME_HINT_RESCUE_STRENGTH;
    private boolean mUpdateStrength = false;
    private int mRescueStrengthWhenHint = -1;
    private int mFrameDrawnCount = 0;

    private int mUnlockPowerHandle = 0;

    private static float mFrameIntervalTime = 0;
    private static long mFrameIntervalTimeNS = Long.MAX_VALUE;
    private static float mHalfVsyncCheckTime = 0;
    private static float mOneVsyncCheckTime = 0;
    private static long mDelayStopHWUIHintTime = 0L;
    private static int mFrameStep = FRAME_STEP_DEFAULT;
    private static boolean mIsAnimationStepEnd = false;
    private static boolean mIsTraversalStepEnd = false;
    private static boolean mIsScrolling = false;
    private static boolean mIsPreAnimation = false;
    //mark as true when new page resume, false after a scrolling end
    private static boolean mNewPageStarted = false;

    //Render thread tid
    private int mRenderThreadTid = NON_RENDER_THREAD_TID;

    private DisplayEventReceiver mDisplayEventReceiver = null;
    private ScrollingFramePrefetcher mScrollingFramePrefetcher = null;

    //rescue info
    //MAX Cover 2 big jank in 120hz
    private final long RESCUE_DOFRAME_TIMEOUT_OUT_MS = 70;
    private final long RESCUE_WAIT_VSYNC_TIMEOUT_OUT_MS = 50;
    //TODO: why 3? dynamic compute?
    private final int RESCUE_BUFFER_COUNT_THRESHOLD = 2;
    private final int SECOND_RESCUE_THRESHOLD = 1;
    private final int OBTIANVIEW_INFLATE_THRESHOLD = 5;
    private final int TRAVERSAL_THRESHOLD = 3;
    private float mTraversalOverTimeThreshold = 1.5f;
    private int mBLASTBufferCount = 0;
    private boolean mEnableTraversalDynamicRescue = false;
    private boolean mEnableOIDynamicRescue = false;
    private boolean mRescueDoFrame = false;
    private boolean mRescueNextFrame = false;
    private boolean mRescueWhenWaitNextVsync = false;
    private boolean mRescuePreAnimation = false;
    private boolean mRescueCheckByBufferCount = false;
    private volatile boolean mRescueRunning = false;
    private volatile boolean mUpdateFrameId = false;
    private volatile boolean mWaitNextVsync = false;
    private boolean mRescueCheckAgain= false;
    private boolean mHasNextFrame = false;
    private long mCurFrameStartTimeNanos = -1L;
    private long mCurFrameTraversalStartTimeNanos = -1L;
    private volatile long mCurFrameId = DEFAULT_FRAME_ID;
    private long mLastFrameEndFrameId = DEFAULT_FRAME_ID;
    private long mLastValidFrameId = DEFAULT_FRAME_ID;
    private long mLastFrameEndVsyncWakeupTimeNanos = -1L;
    private long mLastFrameEndVsyncDeadLineNanos = -1L;
    private long mLastFrameBeginVsyncDeadLineNanos = -1L;
    private long mLastFrameDurMS = -1L;
    private long mRescuingFrameId = DEFAULT_FRAME_ID;
    private Handler mMainHandler = null;
    private long mMsgCheckTime = Long.MAX_VALUE;

    // this frameId which receive from vsync
    private long mVsyncFrameId = DEFAULT_FRAME_ID;
    private long mVsyncTimeNanos = -1L;
    private long mLastVsyncDeadLineTimeNanos = -1L;
    private float mCheckTimeOffset = 0.0f;
    private long mTraversalOvertimeNS = Long.MAX_VALUE;
    private long mRescueTargetTimeNS = 0;
    private long mBufferCountFilterBufferTimeNS = 0;
    private long mSecondRescueTimeMS = 0;
    private boolean mDisableBufferCountFilterDy = false;

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

    private final int RESCUE_SCROLLING_INFO_MAX = 6;
    private final int RESCUE_SCROLLING_INFO_MIN = 3;
    private static class RescueInfoOfScroll {
        private long statrtTime;
        private long endTime;
        private int mFrameCount = 0;
        private int mRescueCount = 0;
        private int mOIRescueCount = 0;
        private int mOIRescueMoreCount = 0;
        private int mOIHintCount = 0;
        private int mTraversalDropCount = 0;
        private int mTraversalOverCount = 0;
        private int mBufferFilterFrameDropCount = 0;
    }
    private volatile int mCurScrollingRescueCount = 0;
    private volatile int mCurScrollingTraversalOverCount = 0;
    private volatile int mCurScrollingOIHintCount = 0;
    private volatile int mCurScrollingOIRescueCount = 0;
    private volatile int mCurScrollingOIRescueMoreCount = 0;
    private volatile int mCurScrollingBufferFilterDropCount = 0;
    private volatile int mCurScrollingTraversalDropCount = 0;
    private RescueInfoOfScroll mCurRescueInfoOfScrolling = null;
    private LinkedList <RescueInfoOfScroll> mRescueInfoOfScrollList
            = new LinkedList <RescueInfoOfScroll>();

    //Handler Message define
    public static final int MSG_FRAME_BEGIN = 1;
    public static final int MSG_FRAME_END = 2;
    public static final int MSG_RESCUE_HALF_VSYNC_CHECK = 3;
    public static final int MSG_ON_VSYNC = 4;
    public static final int MSG_RESCUE_ONE_VSYNC_CHECK = 5;
    public static final int MSG_RESCUE_TIME_OUT = 6;
    public static final int MSG_UPDATE_RESCUE_FRAME_ID = 7;
    public static final int MSG_REQUEST_VSYNC = 8;
    public static final int MSG_DELAY_STOP_HWUI_HINT = 9;
    public static final int MSG_SCROLL_START = 10;
    public static final int MSG_SCROLL_END = 11;
    public static final int MSG_RESCUE_PRE_ANIMATION_CHECK = 12;
    public static final int MSG_RESCUE_SECOND_CHECK = 13;

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
                    onVsyncInternal(info.frameId, info.vsyncDeadlineTime);
                }
                break;
            case MSG_RESCUE_ONE_VSYNC_CHECK:
                oneVsyncRescueCheck();
                break;
            case MSG_UPDATE_RESCUE_FRAME_ID:
                updateRescueFrameId();
                break;
            case MSG_REQUEST_VSYNC:
                requestVsyncInternal();
                break;
            case MSG_RESCUE_TIME_OUT:
                checkWhenRescueTimeOut();
                break;
            case MSG_DELAY_STOP_HWUI_HINT:
                dleayStopHwuiHint();
                break;
            case MSG_SCROLL_START:
                onScrollStateChangeInternal(true);
                break;
            case MSG_SCROLL_END:
                onScrollStateChangeInternal(false);
                break;
            case MSG_RESCUE_PRE_ANIMATION_CHECK:
                preAnimationCheck();
                break;
            case MSG_RESCUE_SECOND_CHECK:
                secondRescueCheck();
                break;
            default:
                break;
        }
    }

    public static FramePolicyV3 getInstance() {
        if (null == mInstance) {
            synchronized (FramePolicyV3.class) {
                if (null == mInstance) {
                    mInstance = new FramePolicyV3();
                }
            }
        }
        return mInstance;
    }

    private FramePolicyV3() {
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

            if (Config.isBufferCountFilterFpsgo) {
                mFrameHintMask = (mFrameHintMask | ActivityInfo.SBE_MASK_BUFFER_COUNT_HINT);
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
            onDoFrameEnd(vsyncWakeUpTime, vsyncDeadlineNanos);
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
        //SPD: add for rescue prefetcher frame by song.tang 20240903 start
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
        mFrameDrawnCount = 0;

        LogUtil.traceEnd();
    }

    private void onDoFrameEnd(long vsyncWakeUpTime, long vsyncDeadlineNanos) {
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("onDoFrameEnd=" + mCurFrameId);
        }
        mFrameStep = FRAME_STEP_DEFAULT;
        mLastFrameEndFrameId = mCurFrameId;
        if (mCurFrameId > 0) {
            mLastValidFrameId = mCurFrameId;
        }

        if (vsyncWakeUpTime > 0) {
            //if vsync wakeuptime is -1, do not using it.
            mLastFrameEndVsyncWakeupTimeNanos = vsyncWakeUpTime;
            mLastFrameEndVsyncDeadLineNanos = vsyncDeadlineNanos;
        }
        final long curFrameStartTimeNanos = mCurFrameStartTimeNanos;
        updateBasicFrameInfo(DEFAULT_FRAME_ID, DEFAULT_FRAME_TIME);

        mRescueCheckByBufferCount = false;

        long curTimeNanos = SystemClock.uptimeNanos();
        long frameTimeNS = curTimeNanos - curFrameStartTimeNanos;
        mLastFrameDurMS = frameTimeNS > 0 ? frameTimeNS / NANOS_PER_MS : 0;

        //This Frame tirgger rescue
        if (mCurFrameRescueType != 0) {
            LogUtil.traceAndMLogd(TAG, generateRescueDebugStrFromType(mCurFrameRescueType));
            if (Config.isBufferCountFilterSBE && mBufferCountFilterBufferTimeNS > 0
                    && frameTimeNS >= mBufferCountFilterBufferTimeNS
                    && (mCurFrameRescueType & SBE_RESCUE_TYPE_TRAVERSAL_OVER_VSYNC) == 0) {
                mCurScrollingBufferFilterDropCount++;
                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, "buffer count lead to drop "
                            + frameTimeNS + " " + mBufferCountFilterBufferTimeNS);
                }
            }

            //check traversal overtime and only trigger traversal rescue, count it
            if ((mCurFrameRescueType & SBE_RESCUE_TYPE_TRAVERSAL_OVER_VSYNC) != 0) {
                long traversal2FrameEndTime = curTimeNanos - mCurFrameTraversalStartTimeNanos;
                //if buffer count lead to traversal rescue,
                //before rescue, rescue type will be clear, then could not in here
                if (traversal2FrameEndTime > mTraversalOvertimeNS) {
                    mCurScrollingTraversalDropCount++;
                }
            }
            if (mEnableTraversalDynamicRescue
                    && (mCurFrameRescueType & SBE_RESCUE_TYPE_TRAVERSAL_DYNAMIC) != 0) {
                //check if rescue traversal but no frame drop
                long traversal2FrameEndTime = curTimeNanos - mCurFrameTraversalStartTimeNanos;
                if (traversal2FrameEndTime < mFrameIntervalTimeNS) {
                    mEnableTraversalDynamicRescue = false;
                }
            }

            if (mEnableOIDynamicRescue
                    && (mCurFrameRescueType & SBE_RESCUE_TYPE_OI) != 0) {
                //check if ob rescue meet rescue more case.
                if (mLastFrameDurMS > 0 && mLastFrameDurMS < (mFrameIntervalTime / 2)) {
                    mCurScrollingOIRescueMoreCount++;
                }

                if (mCurScrollingOIRescueMoreCount >= OBTIANVIEW_INFLATE_THRESHOLD) {
                    //diable it now!!
                    mEnableOIDynamicRescue = false;
                    mCurScrollingOIHintCount = 0;
                }
            }
        }

        mCurFrameTraversalStartTimeNanos = 0;
        mBufferCountFilterBufferTimeNS = 0;

        mCurFrameRescueType = 0;

        if (mLastFrameEndFrameId != PREFETCHER_FRAME_ID) {
            clearAllCheckMsg();
        }

        if (Config.isSecondRescue) {
            mWorkerHandler.removeMessages(MSG_RESCUE_SECOND_CHECK);
        }

        if (Config.preRunningRescue && mMainHandler != null) {
            if (LogUtil.DEBUG) {
                LogUtil.trace("Check should post check "
                        + frameTimeNS + " " + mFrameIntervalTime);
            }
            if (frameTimeNS > 0 && frameTimeNS < (mFrameIntervalTime * NANOS_PER_MS)) {
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

            if ((!mIsTraversalStepEnd || mFrameDrawnCount == 0)
                    && (mRescueDoFrame || mRescueWhenWaitNextVsync)) {
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
        mFrameDrawnCount = 0;
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
        if (step == BoostFwkManager.Draw.FRAME_REAL_DRAW) {
            mFrameDrawnCount++;
            return;
        }
        if (step == BoostFwkManager.Draw.FRAME_OBTAIN_VIEW) {
            if (mCurFrameId > 0) {
                mCurScrollingOIHintCount++;

                if (mEnableOIDynamicRescue && !mRescueDoFrame) {
                    // 1. there is no frame begin or prefetcher frame
                    // 2. already rescue
                    onObtainViewOrInflate();
                }
            }
            return;
        }
        //Init frame step execute time.
        if (isBegin) {
            if (step == Choreographer.CALLBACK_ANIMATION
                    && mCurFrameId == DEFAULT_FRAME_ID) {
                //check if frameid not valid, mark as pre-animation start
                mIsPreAnimation = true;
                long delay = (long)mFrameIntervalTime - mLastFrameDurMS;
                if (delay > 0) {
                    mWorkerHandler.removeMessages(MSG_RESCUE_PRE_ANIMATION_CHECK);
                    mWorkerHandler.sendEmptyMessageDelayed(MSG_RESCUE_PRE_ANIMATION_CHECK, delay);
                    if (LogUtil.DEBUG) {
                        LogUtil.traceAndMLogd(TAG, "send msg to check pre-animation " + delay);
                    }
                }
            } else {
                setFrameStep(mappingStepForDoFrame(step));
                if (step == Choreographer.CALLBACK_TRAVERSAL) {
                    mCurFrameTraversalStartTimeNanos = SystemClock.uptimeNanos();
                }
            }
        } else {
            if (step == Choreographer.CALLBACK_ANIMATION) {
                mIsAnimationStepEnd = true;
                mIsPreAnimation = false;
                mWorkerHandler.removeMessages(MSG_RESCUE_PRE_ANIMATION_CHECK);
            } else if (step == Choreographer.CALLBACK_TRAVERSAL) {
                mIsTraversalStepEnd = true;
                //after traversal end, rescue check also done, sync info to fpsgo
                FrameDecision.getInstance().updateFrameStatus(mCurFrameRescueType, true);
                long tDur = SystemClock.uptimeNanos() - mCurFrameTraversalStartTimeNanos;
                if (mCurFrameTraversalStartTimeNanos > 0
                        && tDur > mFrameIntervalTimeNS + 1000000) {//9ms
                    mCurScrollingTraversalOverCount++;
                }
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
        if (mWorkerHandler.hasMessages(MSG_RESCUE_PRE_ANIMATION_CHECK)) {
            mWorkerHandler.removeMessages(MSG_RESCUE_PRE_ANIMATION_CHECK);
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

        mWorkerHandler.sendEmptyMessage(scrolling ? MSG_SCROLL_START : MSG_SCROLL_END);
    }

    private void onScrollStateChangeInternal(boolean scrolling) {
        if (scrolling) {
            if (mNewPageStarted) {
                mRescueInfoOfScrollList.clear();
            }
            mCurRescueInfoOfScrolling = new RescueInfoOfScroll();
        } else {
            mNewPageStarted = false;
            //add a new scrolling to list
            if (mCurRescueInfoOfScrolling != null) {
                if (mRescueInfoOfScrollList.size() >= RESCUE_SCROLLING_INFO_MAX) {
                    mRescueInfoOfScrollList.removeFirst();
                }
                // for mutil thread -> do not count in mainthread
                mCurRescueInfoOfScrolling.mRescueCount = mCurScrollingRescueCount;
                mCurRescueInfoOfScrolling.mOIRescueCount = mCurScrollingOIRescueCount;
                mCurRescueInfoOfScrolling.mOIRescueMoreCount = mCurScrollingOIRescueMoreCount;
                mCurRescueInfoOfScrolling.mOIHintCount = mCurScrollingOIHintCount;
                mCurRescueInfoOfScrolling.mBufferFilterFrameDropCount
                        = mCurScrollingBufferFilterDropCount;
                mCurRescueInfoOfScrolling.mTraversalDropCount
                        = mCurScrollingTraversalDropCount;
                mCurRescueInfoOfScrolling.mTraversalOverCount
                        = mCurScrollingTraversalOverCount;
                mCurScrollingRescueCount = 0;
                mCurScrollingOIRescueCount = 0;
                mCurScrollingOIHintCount = 0;
                mCurScrollingBufferFilterDropCount = 0;
                mCurScrollingTraversalDropCount = 0;
                mCurScrollingTraversalOverCount = 0;
                mCurScrollingOIRescueMoreCount = 0;

                mRescueInfoOfScrollList.addLast(mCurRescueInfoOfScrolling);
                if (mRescueInfoOfScrollList.size() >= RESCUE_SCROLLING_INFO_MIN) {
                    tryUpdateDyRescue();
                    //if disbale BufferCountFilterDy, then this page will not
                    //enable this feature
                    if (Config.isBufferCountFilterSBE && !mDisableBufferCountFilterDy) {
                        updateBufferCountFilter();
                    }
                }
            }
            mCurRescueInfoOfScrolling = null;
        }
    }

    private void updateBufferCountFilter() {
        int bufferCountDrop = 0;
        for (RescueInfoOfScroll scroll : mRescueInfoOfScrollList) {
            bufferCountDrop += scroll.mBufferFilterFrameDropCount;
        }
        mDisableBufferCountFilterDy = bufferCountDrop > 0;
    }

    private void onObtainViewOrInflate() {
        LogUtil.trace("onObtainViewOrInflate");
        //Dynamic rescue for obtainview & inflate
        clearAllCheckMsg();
        mRescueDoFrame = true;
        doSBERescue(SBE_RESCUE_TYPE_OI | SBE_RESCUE_TYPE_MAX_ENHANCE);
    }

    private void tryUpdateDyRescue() {
        int rescueCount = 0;
        int oiRescueCount = 0;
        int oiRescueMoreCount = 0;
        int oiHintCount = 0;
        int tDropCount = 0;
        int tOverCount = 0;
        for (RescueInfoOfScroll scrolling : mRescueInfoOfScrollList) {
            rescueCount += scrolling.mRescueCount;
            oiRescueCount += scrolling.mOIRescueCount;
            oiRescueMoreCount += scrolling.mOIRescueMoreCount;
            oiHintCount += scrolling.mOIHintCount;
            tDropCount += scrolling.mTraversalDropCount;
            tOverCount += scrolling.mTraversalOverCount;
        }
        mEnableOIDynamicRescue = false;
        if (Config.isOICheckpoint
                && oiHintCount > 0
                && oiRescueMoreCount < OBTIANVIEW_INFLATE_THRESHOLD
                && (oiHintCount - oiRescueCount) <= OBTIANVIEW_INFLATE_THRESHOLD) {
            mEnableOIDynamicRescue = true;
        }
        if (Config.isTraversalDynamicCheckpoint
                && !mEnableTraversalDynamicRescue
                && tDropCount > TRAVERSAL_THRESHOLD) {
            mEnableTraversalDynamicRescue = true;
        }
        float tCheck = Config.getTraversalRescueCheckPoint();
        if (tOverCount >= TRAVERSAL_THRESHOLD) {
            updateOneVsyncCheckTime(tCheck > 0.7f ? 0.7f : tCheck);
        } else {
            updateOneVsyncCheckTime(tCheck);
        }

        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "tryUpdateRescueCheckPoint rescueCount:" + rescueCount
                    + " oiRescueCount:" + oiRescueCount + " tRescueCount:" + tDropCount
                    + " oiHintCount:" + oiHintCount + " OIDy:" + mEnableOIDynamicRescue
                    + " trversalDy:" + mEnableTraversalDynamicRescue);
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

    private void onVsyncInternal(long frameId, long vsyncDeadlineNanos) {
        if (!mWaitNextVsync) {
            if (LogUtil.DEBUG) {
                LogUtil.trace("this vsync is later than doframe, the doframe is end");
            }
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("onVsyncInternal frameId= " + frameId + " " + mFrameStep
                    + " mRescueCheckByBufferCount=" + mRescueCheckByBufferCount);
        }
        mVsyncFrameId = frameId;
        mVsyncTimeNanos = SystemClock.uptimeNanos();

        if (mFrameStep != FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC) {
            setFrameStep(FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC);
            if (!mRescueCheckByBufferCount) {
                rescueCheck(vsyncDeadlineNanos);
            }
        }
        mWaitNextVsync = false;
        LogUtil.traceEnd();
    }

    private void clearAllCheckMsg() {
        if (mWorkerHandler != null) {
            LogUtil.trace("clearAllCheckMsg");
            mWorkerHandler.removeMessages(MSG_RESCUE_HALF_VSYNC_CHECK);
            mWorkerHandler.removeMessages(MSG_RESCUE_ONE_VSYNC_CHECK);
            mWorkerHandler.removeMessages(MSG_RESCUE_SECOND_CHECK);
        }
    }

    /**
     *
     * @param vsyncDeadlineNanos
     * @return true means this vsync doFrame already ended
     */
    private boolean invalidVsync(long vsyncDeadlineNanos) {
        return vsyncDeadlineNanos <= 0 ||
                vsyncDeadlineNanos <= mLastFrameEndVsyncDeadLineNanos;
    }

    /**
     * @param vsyncDeadlineNanos this frame deadline
     * will call from rescuecheckAgain and onVsync
     */
    private void rescueCheck(long vsyncDeadlineNanos) {
        if (mDisableFrameRescue
                || (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2
                    && (invalidVsync(vsyncDeadlineNanos)
                    || mRescueDoFrame || mRescueWhenWaitNextVsync))) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "rescue check when mDisableFrameRescue="
                        + mDisableFrameRescue + " mRescueDoFrame=" + mRescueDoFrame
                        + " mRescueWhenWaitNextVsync=" + mRescueWhenWaitNextVsync
                        + " mLastVsyncWakeupTimeNanos=" + mLastFrameEndVsyncDeadLineNanos
                        + " vsyncWakeupTimeNanos=" + vsyncDeadlineNanos);
            }
            if (invalidVsync(vsyncDeadlineNanos)) {
                //fix runnable lead to invalid vsync
                //then framestep is FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC,
                //lead to could not rescue running
                //reset to default frame step
                mFrameStep = FRAME_STEP_DEFAULT;
            }
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBegin("rescueCheck frameId= " + mCurFrameId + " vsyncTime="
                    + mVsyncTimeNanos + " frameStartTime=" + mCurFrameStartTimeNanos
                    + " runningCheck=" + mRunningCheckMsgStatus
                    + " " + mRescueRunning + " " + mCheckTimeOffset);
        }
        mLastVsyncDeadLineTimeNanos = vsyncDeadlineNanos;

        final long vsyncTimeNanos = mVsyncTimeNanos;
        final long curFrameStartTimeNanos = mCurFrameStartTimeNanos;
        long frameOffset = 0L;
        float halfCheckTime = mHalfVsyncCheckTime + mCheckTimeOffset;
        float oneVsyncCheckTime = mOneVsyncCheckTime + mCheckTimeOffset;
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

        if (LogUtil.DEBUG) {
            LogUtil.trace("send msg with half:" + halfCheckTime + " one:" + oneVsyncCheckTime);
        }

        //mutil thread cause run in this function, check again.
        if (mWaitNextVsync || mRescueCheckAgain) {
            if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                clearAllCheckMsg();
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
                            MSG_RESCUE_ONE_VSYNC_CHECK), (long)(oneVsyncCheckTime));
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
        //follow last vsync deadline time,
        //use to compare this vysnc`s doframe finish or not
        rescueCheck(mLastVsyncDeadLineTimeNanos < 0
                ? System.nanoTime() : mLastVsyncDeadLineTimeNanos);
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
                        int rescueType = SBE_RESCUE_TYPE_RUNNING;
                        if (mIsPreAnimation) {
                            rescueType |= SBE_RESCUE_TYPE_PRE_ANIMATION;
                        }
                        doSBERescue(rescueType);
                    } else {
                        rescueCheckAgain();
                        mRescueWhenWaitNextVsync = false;
                    }
                    mRescueRunning = false;
                } else {
                    doSBERescue(SBE_RESCUE_TYPE_WAITING_VSYNC);
                }
                mRescueStrengthWhenHint = -1;
                break;
            case FRAME_STEP_VSYNC_FOR_APP_TO_INPUT:
            case FRAME_STEP_DO_FRAME_INPUT:
            case FRAME_STEP_DO_FRAME_ANIMATION:
                if (!mIsAnimationStepEnd) {
                    mRescueDoFrame = true;
                    doSBERescue(SBE_RESCUE_TYPE_WAITING_ANIMATION_END);
                }
                break;
            case FRAME_STEP_DO_FRAME_TRAVERSAL:
                if (mEnableTraversalDynamicRescue && !mIsTraversalStepEnd) {
                    mRescueDoFrame = true;
                    doSBERescue(SBE_RESCUE_TYPE_TRAVERSAL_DYNAMIC);
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
            doSBERescue(SBE_RESCUE_TYPE_TRAVERSAL_OVER_VSYNC);
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

    private void preAnimationCheck() {
        //is preanmation and no frame start, no vsync check msg start
        //and do not check when running rescue
        if (mIsPreAnimation && mCurFrameId == DEFAULT_FRAME_ID
                && !mRescueWhenWaitNextVsync
                && !mWorkerHandler.hasMessages(MSG_RESCUE_ONE_VSYNC_CHECK)) {
            LogUtil.traceBegin("preAnimationCheck");
            mRescuePreAnimation = true;
            mRescueWhenWaitNextVsync = true;
            mRescueStrengthWhenHint = mActivityInfo.getLastFrameCapacity();
            doSBERescue(SBE_RESCUE_TYPE_RUNNING | SBE_RESCUE_TYPE_PRE_ANIMATION);
            mRescueStrengthWhenHint = -1;
            mRescuePreAnimation = false;
            LogUtil.traceEnd();
        }
    }

    /**
     * second rescue check
     * only rescue when do frame not end
     */
    private void secondRescueCheck() {
        //a frame start, no vsync check msg start
        if (mCurFrameId != DEFAULT_FRAME_ID) {
            LogUtil.traceBegin("secondRescueCheck");
            doSBERescue(SBE_RESCUE_TYPE_SECOND_RESCUE);
            LogUtil.traceEnd();
        }
    }

    private void updateRescueFrameId() {
        if (mRescueWhenWaitNextVsync) {
            mRescueWhenWaitNextVsync = false;
            if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                mRescueStrengthWhenHint = 0;
            }
            mUpdateFrameId = true;
            doSBERescue(SBE_RESCUE_TYPE_COUNTINUE_RESCUE);
            mUpdateFrameId = false;
            mRescueDoFrame = true;
            mRescueStrengthWhenHint = -1;
        } else if (mRescueNextFrame) {
            mRescueNextFrame = false;
            doSBERescue(SBE_RESCUE_TYPE_COUNTINUE_RESCUE);
            mRescueDoFrame = true;
        }
    }

    private void doSBERescue(int type) {
        final long frameId = mCurFrameId;
        //this frame - doframe is end, do not rescue
        if (frameId == DEFAULT_FRAME_ID && mFrameStep != FRAME_STEP_VSYNC_FOR_SBE_TO_APP_VSYNC
                && !mRescuePreAnimation) {
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
        String msg = "";
        if (LogUtil.DEBUG) {
            msg = generateRescueDebugStrFromType(type)
                    + " curStep=" + mFrameStep + " mode=" + mCurFrameRescueMode;
        }
        mCurFrameRescueType |= type;

        if (mCurRescueInfoOfScrolling != null) {
            mCurScrollingRescueCount++;
            // if running case using ob, do not record it.
            if (FrameDecision.getInstance().isObtianViewOrFlutter(mRescuingFrameId)) {
                mCurScrollingOIRescueCount++;
            }
        }

        powerHintForRender(PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE, msg);
        mWorkerHandler.removeMessages(MSG_RESCUE_TIME_OUT);
        mWorkerHandler.sendEmptyMessageDelayed(MSG_RESCUE_TIME_OUT, mRescueWhenWaitNextVsync ?
                RESCUE_WAIT_VSYNC_TIMEOUT_OUT_MS : RESCUE_DOFRAME_TIMEOUT_OUT_MS);
    }

    private void checkWhenRescueTimeOut() {
        if (mCurFrameId > 0
                && mRescueDoFrame
                && mCurFrameId == mRescuingFrameId) {
            //there is a frame is running, rescue to it frame end
            LogUtil.traceAndMLogd(TAG,
                    "checkWhenRescueTimeOut() rescue to frame end for frame->" + mCurFrameId);
            return;
        }
        shutdownSBERescue("time out");
    }

    private void shutdownSBERescue(String tagMsg) {
        mCurFrameRescueMode = SBE_RESUCE_MODE_END;
        powerHintForRender(PERF_RES_FPS_FBT_RESCUE_SBE_RESCUE,
                "shutdown " + tagMsg +" curStep="+mFrameStep + " mode="+mCurFrameRescueMode);
        clearRescueInfo();
    }

    private void clearRescueInfo() {
        mCurFrameRescueType = 0;
        mRescuingFrameId = DEFAULT_FRAME_ID;
        mRescueWhenWaitNextVsync = false;
        mRescueDoFrame = false;
        mRescueNextFrame = false;
        mWorkerHandler.removeMessages(MSG_RESCUE_TIME_OUT);
    }

    private String generateRescueDebugStrFromType(int type) {
        if (!LogUtil.DEBUG || type == 0) {
            return "";
        }
        String result = "Rescue for:";
        if ((type & SBE_RESCUE_TYPE_WAITING_ANIMATION_END) != 0) {
            result += " waiting animation end |";
        }
        if ((type & SBE_RESCUE_TYPE_COUNTINUE_RESCUE) != 0) {
            result += " update frame id |";
        }
        if ((type & SBE_RESCUE_TYPE_RUNNING) != 0) {
            result += " running |";
        }
        if ((type & SBE_RESCUE_TYPE_TRAVERSAL_OVER_VSYNC) != 0) {
            result += " traversal over vsync |";
        }
        if ((type & SBE_RESCUE_TYPE_WAITING_VSYNC) != 0) {
            result += " waiting vsync |";
        }
        if ((type & SBE_RESCUE_TYPE_TRAVERSAL_DYNAMIC) != 0) {
            result += " traversal dynamic |";
        }
        if ((type & SBE_RESCUE_TYPE_OI) != 0) {
            result += " obtainview |";
        }
        if ((type & SBE_RESCUE_TYPE_MAX_ENHANCE) != 0) {
            result += " max enhance |";
        }
        if ((type & SBE_RESCUE_TYPE_SECOND_RESCUE) != 0) {
            result += " second rescue |";
        }
        if ((type & SBE_RESCUE_TYPE_BUFFER_COUNT_FILTER) != 0) {
            result += " buffer count filter |";
        }
        if ((type & SBE_RESCUE_TYPE_PRE_ANIMATION) != 0) {
            result += " pre-animation |";
        }
        return result;
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
                LogUtil.traceAndMLogd(TAG, mCurFrameId + "cancel rescue: " + renderThreadTid);
            }
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, "hint for [" + tagMsg + "] renderId=" + renderThreadTid
                    + " cur_frameId=" + mCurFrameId + " re_frameId=" + mRescuingFrameId
                    + " strength="+mRescueStrengthWhenHint);
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

                if (Config.isBufferCountFilterSBE && !mDisableBufferCountFilterDy
                        && mCurFrameId != DEFAULT_FRAME_ID //there is a frame start
                        && mCurFrameRescueMode != SBE_RESUCE_MODE_END
                        && (mCurFrameRescueType & SBE_RESCUE_TYPE_OI) == 0
                        && (mCurFrameRescueType & SBE_RESCUE_TYPE_RUNNING) == 0) {
                    int bufferCount = mActivityInfo.getBufferCountFromProducer();
                    if(bufferCount > RESCUE_BUFFER_COUNT_THRESHOLD) {
                        rescueCheckAgainForBufferCountFilter(bufferCount);
                        break;
                    }
                }
                mCurFrameRescueType = updateRescueTypeWithPageType(mCurFrameRescueType);
                //do rescue
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
                long frameId = rescueFrameId <= 0 ? 1 : rescueFrameId;
                if (mCurFrameRescueMode == SBE_RESUCE_MODE_END) {
                    //using max value, so can end any rescue.
                    frameId = Long.MAX_VALUE;
                } else if (mCurFrameRescueMode == SBE_RESUCE_MODE_START
                        && (mCurFrameRescueType & SBE_RESCUE_TYPE_RUNNING) != 0
                        && mLastValidFrameId > 0) {
                    //use a bigger value. it bigger than last frame, but less than next frames
                    //if doframe hint lost, and do not update frame id,
                    //this frame rescue also can end after 2 frames.
                    frameId = mLastValidFrameId + 2;
                }

                //IOCTL call directly, more faster.
                mPowerHalWrap.mtkNotifySbeRescue(renderThreadTid,
                        mCurFrameRescueMode, strength, mCurFrameRescueType,
                        mRescueTargetTimeNS, frameId);

                //after rescue send, try rescue check again
                //will remove after frame begin
                if (Config.isSecondRescue && mSecondRescueTimeMS > 0
                        && mCurFrameId != DEFAULT_FRAME_ID //there is a frame start
                        && mCurFrameRescueMode != SBE_RESUCE_MODE_END
                        && (mCurFrameRescueType & SBE_RESCUE_TYPE_OI) == 0
                        && (mCurFrameRescueType & SBE_RESCUE_TYPE_RUNNING) == 0
                        && (mCurFrameRescueType & SBE_RESCUE_TYPE_SECOND_RESCUE) == 0) {
                    mWorkerHandler.removeMessages(MSG_RESCUE_SECOND_CHECK);
                    mWorkerHandler.sendEmptyMessageDelayed(
                            MSG_RESCUE_SECOND_CHECK, mSecondRescueTimeMS);
                }
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

    private boolean rescueCheckAgainForBufferCountFilter(int bufferCount) {
        mCheckTimeOffset = (bufferCount - RESCUE_BUFFER_COUNT_THRESHOLD)
                * mFrameIntervalTime - mHalfVsyncCheckTime;
        mBufferCountFilterBufferTimeNS = (long)(((bufferCount - 1)
                * mFrameIntervalTime - mHalfVsyncCheckTime) * 1000000.0f);
        if (LogUtil.DEBUG) {
            LogUtil.trace("do not rescue buffer count->"
                + bufferCount +" mCurFrameId=" + mCurFrameId + " " + mCheckTimeOffset);
        }
        clearRescueInfo();
        rescueCheckAgain();
        mRescueCheckByBufferCount = true;
        mCheckTimeOffset = 0;
        return false;
    }

    private int updateRescueTypeWithPageType(int rescueType) {
        if (mActivityInfo == null || !mActivityInfo.isAOSPPageDesign()) {
            return rescueType;
        }
        int mode = Config.getScrollCommonHwuiMode();
        float refreshRate = ScrollState.getRefreshRate();
        if ((refreshRate <= 61 && (mode & Config.SCROLLING_COMMON_MODE_60) == 0)
                || (refreshRate > 61 && refreshRate <= 91
                        && (mode & Config.SCROLLING_COMMON_MODE_90) == 0)
                || (refreshRate > 91 && (mode & Config.SCROLLING_COMMON_MODE_120) == 0)) {
            rescueType |= SBE_RESCUE_TYPE_ENABLE_MARGIN;
        }
        return rescueType;
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
        long vsyncDeadlineTime;
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
                    info.vsyncDeadlineTime = vsyncEventData.preferredFrameTimeline().deadline;
                    mInstance.mWorkerHandler.sendMessageAtFrontOfQueue(
                        mInstance.mWorkerHandler.obtainMessage(MSG_ON_VSYNC, info));
                } else {
                    mInstance.onVsyncInternal(
                            vsyncEventData.preferredFrameTimeline().vsyncId,
                            vsyncEventData.preferredFrameTimeline().deadline);
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
        //SPD:add for special app boost by song.tang 20240829 end
        if (Config.BOOST_FWK_VERSION_2 < Config.getBoostFwkVersion()) {
            updateLimitVsyncTime(mCheckPoint);
        }
        //reset after a new scrolling end
        mNewPageStarted = true;
        resetDynamicRescue();
        //if has a frame under rescue, shutdown it
        if (mRescueDoFrame || mRescueWhenWaitNextVsync) {
            shutdownSBERescue("page change");
        }
        //clear scrolling msg
        //and scrolling list will be clear when new page scrolling
        mWorkerHandler.removeMessages(MSG_SCROLL_START);
        mWorkerHandler.removeMessages(MSG_SCROLL_END);
    }

    private void resetDynamicRescue() {
        mEnableTraversalDynamicRescue = false;
        mEnableOIDynamicRescue = false;
        mDisableBufferCountFilterDy = false;
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

        clearAllCheckMsg();
        mDisableFrameRescue = true;
    }

    private void updateLimitVsyncTime(float checkPoint) {
        //In some high fresh rate, resuce needs to be more accurate,
        //so left 0.5ms as buffer time for vysnc hint dispatch to SBE.
        //The calculation formula is:
        //time_to_rescue = vysnc * CHECK_POINT - 0.5ms
        //Such as 120HZ, the resuce time should be:
        //8.33ms * 0.5 - 0.5ms = 3.665ms
        mHalfVsyncCheckTime = (float)(mFrameIntervalTime * checkPoint - 0.5);

        float traversalCheckPoint = Config.getTraversalRescueCheckPoint();
        updateOneVsyncCheckTime(traversalCheckPoint);
        mRescueTargetTimeNS = (long)((mFrameIntervalTime - mHalfVsyncCheckTime) * NANOS_PER_MS_F);

        mTraversalOverTimeThreshold = Config.getTraversalThreshold();
        if (mTraversalOverTimeThreshold <  traversalCheckPoint) {
            mTraversalOverTimeThreshold = traversalCheckPoint;
        }
        mTraversalOvertimeNS = (long)(mFrameIntervalTime
                * mTraversalOverTimeThreshold * NANOS_PER_MS_F);
        mFrameIntervalTimeNS = (long)(mFrameIntervalTime * NANOS_PER_MS_F);
        mSecondRescueTimeMS = (long)(mFrameIntervalTime * SECOND_RESCUE_THRESHOLD);
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "update 1/2 checkpoint:" + checkPoint
                    + " limit vsync time:" + mHalfVsyncCheckTime + " " + mRescueTargetTimeNS
                    + " " + mOneVsyncCheckTime + " " + mTraversalOvertimeNS);
        }
    }

    private void updateOneVsyncCheckTime(float traversalCheckPoint) {
        mOneVsyncCheckTime = (float)(mFrameIntervalTime * traversalCheckPoint);

        if (mOneVsyncCheckTime < mHalfVsyncCheckTime) {
            mOneVsyncCheckTime = (float)mFrameIntervalTime;
        }
    }
}