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

package com.mediatek.boostfwk.policy.scroll;

import android.content.Context;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.Trace;
import android.hardware.SensorManager;
import android.view.Window;
import android.view.ViewConfiguration;
import com.transsion.hubcore.view.ITranSurfaceControl;

import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.info.RenderInfo;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.utils.TasksUtil;

import java.util.ArrayList;
import java.util.List;

import com.mediatek.boostfwk.identify.ime.IMEIdentify;
import com.mediatek.boostfwk.identify.scroll.ScrollIdentify;
import com.mediatek.powerhalmgr.PowerHalMgr;
import com.mediatek.powerhalmgr.PowerHalMgrFactory;
import com.mediatek.powerhalwrapper.PowerHalWrapper;

import com.mediatek.boostfwk.info.ScrollState;
import android.os.SystemProperties;
//T-HUB Core[SPD]:added for frame prefetcher by dewang.tan 20240310 start
import com.mediatek.boostfwk.policy.frame.TranScrollingFramePrefetcher;
//T-HUB Core[SPD]:added for frame prefetcher by dewang.tan 20240310 end
//T-HUB Core[SPD]:added for fling boost by dewang.tan 20240310 start
import android.app.ActivityTaskManager;
//T-HUB Core[SPD]:added for fling boost by dewang.tan 20240310 end
import android.util.Log;

/** @hide */
public class ScrollPolicy {

    private static final String TAG = "ScrollPolicy";

    private static volatile ScrollPolicy sInstance = null;
    private static Object lock = new Object();
    private static final String sTHREAD_NAME = TAG;
    private static final boolean ENABLE_SCROLL_COMMON_POLICY =
            Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_1
                    && Config.isEnableScrollCommonPolicy();
    private HandlerThread mWorkerThread = null;
    private WorkerHandler mWorkerHandler = null;

    private PowerHalMgr mPowerHalService = PowerHalMgrFactory.getInstance().makePowerHalMgr();
    private PowerHalWrapper mPowerHalWrap = null;
    private static int mPowerHandle = 0;
    // Power handle for uboost
    private static int mBoostHandle = 0;
    private static int mMapHandle = 0;
    private ArrayList<Integer> mPerfLockRscList = new ArrayList<>(10);

    // Release Target FPS duration time.
    private static int mReleaseFPSDuration = Config.getVerticalScrollDuration();
    // Render thread tid
    private static int RENDER_TID_NON_CHECK = Integer.MIN_VALUE;
    private static int NON_RENDER_THREAD_TID = -1;
    private static int mRenderThreadTid = RENDER_TID_NON_CHECK;
    private static int mWebviewRenderPid = RENDER_TID_NON_CHECK;
    private static int mFlutterRenderTid = RENDER_TID_NON_CHECK;
    //T-HUB Core[SPD]:added for fling boost by dewang.tan 20240310 start
    private static final int USE_GPU = 1;
    private static final int USE_NORMAL = 0;
    //T-HUB Core[SPD]:added for fling boost by dewang.tan 20240310 end
    // Release Target FPS CMD.
    private static final int PERF_RES_FPS_FSTB_TARGET_FPS_PID = 0x02000200;
    // FPSGO release control CMD
    private static final int PERF_RES_FPS_FPSGO_CTL = 0x02000300;
    // FPSGO release no control CMD (for kernel4.14/4.19)
    private static final int PERF_RES_FPS_FPSGO_NOCTL = 0x02000400;
    private static final int PERF_RES_POWERHAL_CALLER_TYPE = 0x0340c300;
    // SBE Scrolling policy CMD
    private static int MTKPOWER_HINT_UX_SCROLLING = 43;
    // SBE Scrolling fling policy CMD
    private static int MTKPOWER_HINT_UX_MOVE_SCROLLING = 45;
    // SBE Scrolling common CMD
    private static int MTKPOWER_HINT_UX_SCROLLING_COMMON = 49;
    // SBE Scrolling common CMD with pref_idel and sched_mask
    private static int MTKPOWER_HINT_UX_SCROLLING_COMMON_MASK = 112;
    // SBE Scrolling touch move boost CMD
    // MUST sync with vendor/mediatek/proprietary/hardware/power/include/mtkpower_hint.h
    private static int MTKPOWER_HINT_UX_TOUCH_MOVE = 56;
    // Uboost CMD
    private static final int PERF_RES_FPS_FPSGO_UBOOST = 0x02048700;
    // TouchBoost CMD
    private static final int PERF_RES_POWERHAL_TOUCH_BOOST_ENABLE = 0x03408500;
    //enable fpsgo rescue for webview & flutter
    private static final int PERF_RES_FPS_FBT_RESCUE_ENABLE_PID = 0x02000600;

    private static final int PERF_RES_SCHED_SBB_TASK_SET = 0x01444400;
    private static final int PERF_RES_SCHED_SBB_TASK_UNSET = 0x01444500;
    private static final int PERF_RES_SCHED_SBB_ACTIVE_RATIO = 0x01444600;

    //FPSGO mask define
    private static final int FPSGO_CONTROL = 1 << 0;
    // Special app design, not use android AOSP draw flow
    private static final int FPSGO_MAX_TARGET_FPS = 1 << 1;
    private static final int FPSGO_RESCUE_ENABLE = 1 << 2;
    private static final int FPSGO_RL_ENABLE = 1 << 3;
    private static final int FPSGO_GCC_DISABLE = 1 << 4;
    private static final int FPSGO_QUOTA_DISABLE = 1 << 5;
    private static final int FPSGO_RUNNING_CHECK = 1 << 6;
    private static final int FPSGO_RUNNING_QUERY = 1 << 7;
    private static final int FPSGO_NON_HWUI = 1 << 8;
    private static final int FPSGO_HWUI = 1 << 9;
    private static final int FPSGO_CLEAR_SCROLLING_INFO = 1 << 10;
    private static final int FPSGO_MOVING = 1 << 11;
    private static final int FPSGO_FLING = 1 << 12;
    private static final int FPSGO_SCROLLING_MAP = 1 << 13;

    private final int FPSGO_RUNNING_CHECK_RUNNING = 10001;

    private final int WEBVIEW_FLUTTER_FPSGO_CTRL_MASK = FPSGO_CONTROL |
            FPSGO_MAX_TARGET_FPS | FPSGO_RESCUE_ENABLE |
            FPSGO_GCC_DISABLE | FPSGO_QUOTA_DISABLE | FPSGO_NON_HWUI;
    private final int MAP_FPSGO_CTRL_MASK = FPSGO_CONTROL | FPSGO_RESCUE_ENABLE |
            FPSGO_GCC_DISABLE | FPSGO_QUOTA_DISABLE;

    private static long mCheckFPSTime = 100;
    private static long mGameModeCheckTime = 100;
    private static long mCheckCommonPolicyTime = 16;
    private static long mDelayReleaseFpsTime = Config.getFpsgoReleaseTime();
    private static int mPolicyExeCount = 0;
    private static int mFlingPolicyExeCount = 0;
    private static int mSpecialAppDesign = -1;
    private static int mLastScrollCommonCMD = MTKPOWER_HINT_UX_SCROLLING_COMMON;
    public static final int sFINGER_MOVE = 0;
    public static final int sFINGER_UP = 1;
    public static final int sFLING_VERTICAL = 2;
    public static final int sFLING_HORIZONTAL = 3;
    public static final int FINGER_DOWN = 4;
    public static boolean useFPSGo = false;
    private boolean waitingForReleaseFpsgo = false;
    private int waitingForReleaseFpsgoStep = -1;
    private boolean fpsgoUnderCtrlWhenFling = false;
    private boolean uboostEnable = false;

    private boolean mDisableScrollPolicy = false;
    private boolean mCommonPolicyEnabled = false;
    private boolean mScollingPolicyEnabled = false;
    private int mCurrentScrollStep = -1;
    private int scrollingFingStep = -1;
    private long mLastScrollTimeMS = -1L;
    // Some app use AOSP draw but not use Scroller, we cannot know how to end,
    // so we added a mIsRealAOSPPage, to check has scroller or not.
    private boolean mIsRealAOSPPage = false;
    private int mAOSPHintCount = 0;

    private final int MOVING_TIMES_THRESHOLD = 100;
    private int mIsAppMoving = 0;
    private long mLastUserDownTimeMS = -1L;

    private ActivityInfo mActivityInfo;

    //Follow AOSP Begin: OverScroller.java
    private float ppi = 0.f;
    private float mPhysicalCoeff = 155670.6f;
    private static final float INFLEXION = 0.35f; // Tension lines cross at (INFLEXION, 1)
    private float mFlingFriction = 0.015f; // Fling friction
    private static float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));
    //Follow AOSP End: OverScroller.java
    private int mScrollerDuration = 0; // Scroller fling duration
    private static int RUNNING_STATE_NO_CHECKED = -1;
    private static int RUNNING_STATE_NO_RUNNING = 0;
    private static int RUNNING_STATE_RUNNING = 1;
    private int mWebViewRunningState = RUNNING_STATE_NO_CHECKED;
    private int mFlutterRunningState = RUNNING_STATE_NO_CHECKED;


    // SBE Scrolling common CMD
    private static int RUNNING_CHECK_DELAY_TIME = 100; // Check last 100ms

    //add for fling uxscroll boost by wei.kang 20241202 start
    private static final boolean ENABLE_UXSCROLL_HINT
            = SystemProperties.getInt("ro.tr_perf.boostfwk.uxscroll_hint.feature.support", 0) > 0;
    //add for fling uxscroll boost by wei.kang 20241202 end

    private ActivityInfo.ActivityChangeListener mActivityChangeListener
            = new ActivityInfo.ActivityChangeListener() {
        @Override
        public void onChange(Context c) {
            resetScrollPolicyStatus(c);
        }

        @Override
        public void onActivityPaused(Context c) {
            resetScrollPolicyStatus(c);
        }
    };

    private void resetFlingState(Context c){
        mWebViewRunningState = RUNNING_STATE_NO_CHECKED;
        mFlutterRunningState = RUNNING_STATE_NO_CHECKED;
        mFlingFriction = ViewConfiguration.getScrollFriction();
        ppi = 0.f;
        if (mWorkerHandler != null) {
            mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_WEBVIEW_CHECK, null);
            mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_FLUTTER_CHECK, null);
        }
    }

    private static final long ONESECOND_MS = 1000L;

    //SPD:added for boost SF by song.tang 20230706 start
    private static final boolean SF_FLING_BOOST_SUPPORT = SystemProperties.getInt("ro.tr_perf.sf_fling_boost.feature.support", 1) > 0;
    //SPD:added for boost SF by song.tang 20230706 end

    //IME show, disable scroll policy
    private IMEIdentify.IMEStateListener mIMEStateListener =
            new IMEIdentify.IMEStateListener() {

        @Override
        public void onInit(Window window) {}

        @Override
        public void onVisibilityChange(boolean show) {
            mDisableScrollPolicy = show;
            if (show && ScrollState.onScrolling()) {
                ScrollState.setScrolling(false, "ime show");
                if (useFPSGo) {
                    useFPSGo = false;
                }
                if (mPolicyExeCount > 0) {
                    mWorkerHandler.sendEmptyMessage(WorkerHandler.MSG_SBE_POLICY_END);
                }
                if (mFlingPolicyExeCount > 0) {
                    mWorkerHandler.sendEmptyMessage(WorkerHandler.MSG_SBE_FLING_POLICY_END);
                }
            }
        }
    };

    public static ScrollPolicy getInstance() {
        if (null == sInstance) {
            synchronized (ScrollPolicy.class) {
                if (null == sInstance) {
                    sInstance = new ScrollPolicy();
                }
            }
        }
        return sInstance;
    }

    public ScrollPolicy() {
        initThread();
        IMEIdentify.getInstance().registerIMEStateListener(mIMEStateListener);
        mActivityInfo = ActivityInfo.getInstance();
        mActivityInfo.registerActivityListener(mActivityChangeListener);
    }

    private void initThread() {
        if (mWorkerThread != null && mWorkerThread.isAlive()
                && mWorkerHandler != null) {
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.mLogi(TAG, "re-init");
            }
        } else {
            mWorkerThread = new HandlerThread(sTHREAD_NAME);
            mWorkerThread.start();
            Looper looper = mWorkerThread.getLooper();
            if (looper == null) {
                LogUtil.mLogd(TAG, "Thread looper is null");
            } else {
                mWorkerHandler = new WorkerHandler(looper);
            }
        }
    }

    private class WorkerHandler extends Handler {
        public static final int MSG_RELEASE_BEGIN = 1;
        public static final int MSG_RELEASE_END = 2;
        public static final int MSG_RELEASE_FPS_CHECK = 3;
        public static final int MSG_RELEASE_FPS_TIMEOUT = 4;
        public static final int MSG_SBE_POLICY_BEGIN = 5;
        public static final int MSG_SBE_POLICY_END = 6;
        public static final int MSG_SBE_POLICY_FLAG_END = 7;
        public static final int MSG_SBE_FLING_POLICY_BEGIN = 8;
        public static final int MSG_SBE_FLING_POLICY_END = 9;
        public static final int MSG_SBE_FLING_POLICY_FLAG_END = 10;
        public static final int MSG_SBE_DELAY_RELEASE_FPSGO = 11;
        public static final int MSG_SBE_DISABLE_FPSGO_COUNT_DOWN  = 12;
        public static final int MSG_SBE_SCROLL_COMMON_POLICY_COUNT_DOWN = 13;
        public static final int MSG_SBE_DELAY_RELEASE_TARGET_FPS = 14;
        public static final int MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK = 15;
        public static final int MSG_SBE_TOUCH_MOVE_BOOST = 16;
        public static final int MSG_SBE_WEBVIEW_CHECK = 17;
        public static final int MSG_SBE_FLUTTER_CHECK = 18;
        public static final int MSG_SBE_DISABLE_TOUCH_BOOST = 19;

        WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RELEASE_BEGIN:
                    releaseTargetFPSInternel(true);
                    break;
                case MSG_RELEASE_END:
                    releaseTargetFPSInternel(false);
                    break;
                case MSG_RELEASE_FPS_CHECK:
                    break;
                case MSG_RELEASE_FPS_TIMEOUT:
                    releaseTargetFPSInternel(false);
                    break;
                case MSG_SBE_POLICY_BEGIN:
                    mtkScrollingPolicy(true);
                    break;
                case MSG_SBE_POLICY_END:
                    mtkScrollingPolicy(false);
                    break;
                case MSG_SBE_FLING_POLICY_BEGIN:
                    mtkScrollingFlingPolicy(true,msg.arg1);
                    break;
                case MSG_SBE_FLING_POLICY_END:
                    mtkScrollingFlingPolicy(false);
                    break;
                case MSG_SBE_POLICY_FLAG_END:
                    if (mPolicyExeCount > 0) {
                        mPolicyExeCount = 0;
                        enableFPSGo(false, sFINGER_MOVE);
                    }
                    break;
                case MSG_SBE_FLING_POLICY_FLAG_END:
                    if (mFlingPolicyExeCount > 0) {
                        mFlingPolicyExeCount = 0;
                        enableFPSGo(false, scrollingFingStep);
                    }
                    break;
                case MSG_SBE_DELAY_RELEASE_FPSGO:
                    waitingForReleaseFpsgo = false;
                    enableFPSGo(false, waitingForReleaseFpsgoStep);
                    waitingForReleaseFpsgoStep = -1;
                    break;
                case MSG_SBE_DISABLE_FPSGO_COUNT_DOWN:
                    useFPSGo = false;
                    enableFPSGo(false, scrollingFingStep);
                    break;
                case MSG_SBE_SCROLL_COMMON_POLICY_COUNT_DOWN:
                    scrollCommonPolicyCheck();
                    break;
                case MSG_SBE_DELAY_RELEASE_TARGET_FPS:
                    delayReleaseTargetFPSInternal();
                    break;
                case MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK:
                    gameModeOnWebviewCheck(msg.arg1);
                    break;
                case MSG_SBE_TOUCH_MOVE_BOOST:
                    checkTouchMoveBoostIfNeeded();
                    break;
                case MSG_SBE_WEBVIEW_CHECK:
                    updateSpecialPageType(ActivityInfo.PAGE_TYPE_WEBVIEW);
                    break;
                case MSG_SBE_FLUTTER_CHECK:
                    updateSpecialPageType(ActivityInfo.PAGE_TYPE_FLUTTER);
                    break;
                case MSG_SBE_DISABLE_TOUCH_BOOST:
                    disableTouchBoost();
                    break;
                default:
                    break;
            }
        }
    }

    public void scrollHint(int step, int specialAppDesign) {
        if (mDisableScrollPolicy) {
            LogUtil.traceAndMLogd(TAG, "scroll policy has been disable");
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, "scrollHint step=" + step
                + " pageType" + specialAppDesign + " mCurrentScrollStep" + mCurrentScrollStep);
        }
        mCurrentScrollStep = step;
        switch (step) {
            case FINGER_DOWN:
                mLastUserDownTimeMS = System.currentTimeMillis();
                mIsAppMoving = 0;
                mAOSPHintCount = 0;
                //remove game mode check first
                if (mWorkerHandler.hasMessages(WorkerHandler.MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK)) {
                    mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK);
                }

                if (Config.isEnableMoveBoost()) {
                    // start move boost if not started
                    if (mWorkerHandler.hasMessages(WorkerHandler.MSG_SBE_TOUCH_MOVE_BOOST)) {
                        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_TOUCH_MOVE_BOOST);
                    }
                    mWorkerHandler.sendEmptyMessageDelayed(
                        WorkerHandler.MSG_SBE_TOUCH_MOVE_BOOST,Config.TOUCH_BOOST_DURATION);
                }

                resetScrollerState();
                break;
            case sFINGER_MOVE:
                if (useFPSGo) {
                    //next touch reset flags
                    useFPSGo = false;
                }
                if (mSpecialAppDesign == -1) {
                    mSpecialAppDesign = specialAppDesign;
                }
                //remove game mode check first
                if (mWorkerHandler.hasMessages(WorkerHandler.MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK)) {
                    mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK);
                }
                if (mPolicyExeCount == 0) {
                    mWorkerHandler.sendMessage(
                        mWorkerHandler.obtainMessage(
                            WorkerHandler.MSG_SBE_POLICY_BEGIN, null));
                }
                mIsAppMoving++;

                if (Config.isEnableMoveBoost()) {
                    // remove move boost if not started
                    if (mWorkerHandler.hasMessages(WorkerHandler.MSG_SBE_TOUCH_MOVE_BOOST)) {
                        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_TOUCH_MOVE_BOOST);
                    }
                }
                break;
            case sFINGER_UP:
                mIsAppMoving = 0;
                if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_3) {
                    mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_DISABLE_TOUCH_BOOST, null);
                }
                mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_POLICY_BEGIN, null);
                mWorkerHandler.sendMessage(
                    mWorkerHandler.obtainMessage(
                        WorkerHandler.MSG_SBE_POLICY_END, null));

                if (Config.isEnableMoveBoost()) {
                    // remove move boost if not started
                    if (mWorkerHandler.hasMessages(WorkerHandler.MSG_SBE_TOUCH_MOVE_BOOST)) {
                        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_TOUCH_MOVE_BOOST);
                    }
                }
                break;
            case sFLING_VERTICAL:
            case sFLING_HORIZONTAL:
                scrollingFingStep = step;
                if (mFlingPolicyExeCount == 0) {
                    mWorkerHandler.sendMessage(
                        mWorkerHandler.obtainMessage(
                            WorkerHandler.MSG_SBE_FLING_POLICY_BEGIN, null));
                }
                break;
            default:
                break;
        }
        LogUtil.traceEnd();
    }

    public void scrollHint(int step, int specialAppDesign,int velocity) {
        if (mDisableScrollPolicy) {
            LogUtil.traceAndMLogd(TAG, "scroll policy has been disable");
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, "scrollHintWithVelocity step=" + step
                + " pageType " + specialAppDesign + " mCurrentScrollStep " + mCurrentScrollStep
                + " velocity = " + velocity);
        }
        mCurrentScrollStep = step;
        switch (step) {
            case sFLING_VERTICAL:
            case sFLING_HORIZONTAL:
                scrollingFingStep = step;
                if (mFlingPolicyExeCount == 0) {
                    mWorkerHandler.sendMessage(
                        mWorkerHandler.obtainMessage(
                            WorkerHandler.MSG_SBE_FLING_POLICY_BEGIN,velocity,0, null));
                }
                break;
            default:
                break;
        }
        LogUtil.traceEnd();
    }

    public void switchToFPSGo(boolean enableFPSGo) {
        if (mDisableScrollPolicy) {
            LogUtil.traceAndLog(TAG, "switchToFPSGo scroll policy has been disable");
            return;
        }
        useFPSGo = enableFPSGo;
        // if scrolling draw flow by AOSP, swtich to FPSGO control frame,
        // At the first, disable the mtk scrolling policy.
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, "switchToFPSGo " + (enableFPSGo ? "start" : "stop"));
        }
        if (!mIsRealAOSPPage && mActivityInfo.isAOSPPageDesign()) {
            mIsRealAOSPPage = true;
            mAOSPHintCount++;
        }
        if (enableFPSGo) {
            disableMTKScrollingPolicy(false);
        }
        LogUtil.traceEnd();
    }

    public void disableMTKScrollingPolicy(boolean needCheckBoostNow) {
        if (needCheckBoostNow && mPolicyExeCount == 0) {
            return;
        }
        mWorkerHandler.sendMessage(
            mWorkerHandler.obtainMessage(
                WorkerHandler.MSG_SBE_FLING_POLICY_END, null));
    }

    /**
     * Mtk move policy, detail config see powerscntbl.xml.
     * Customer can customeization by own requirment.
     *
     */
    private void mtkScrollingPolicy(boolean enable) {
        if (null == mPowerHalService) {
            LogUtil.mLogw(TAG, "mPowerHalService is null");
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, "mtkScrollingPolicy " + (enable ? "start" : "stop"));
        }
        if (enable) {
            if (mPolicyExeCount == 0) {
                // We think 60hz webview case FPSGO can cover it, so no need do move hint.
                if (Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_2 &&
                        mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW_60FPS)) {
                    mPolicyExeCount++;
                    ScrollState.setScrolling(false, "PAGE_TYPE_WEBVIEW_60FPS");
                    disableTouchBoost();
                    LogUtil.traceEnd();
                    return;
                }

                disableMTKScrollingFlingPolicyIfNeeded(false);

                //SPD: add for support aosp move scrolling by song.tang 20240619 start
                if (mActivityInfo.isSpecialPageDesign() || mActivityInfo.isAOSPPageDesign()) {
                    //SPD: add for support aosp move scrolling by song.tang 20240619 end
                    powerHintUxScrollPolicy(MTKPOWER_HINT_UX_MOVE_SCROLLING,
                            Config.getVerticalScrollDuration());
                }
                if (ScrollState.isScrolling()) {
                    ScrollState.setScrolling(false, "new touch down");
                    notifyHWUIScrollingType(false);
                }
                ScrollState.onScrollWhenMove(true);
                enableFPSGo(true, sFINGER_MOVE);
                mPolicyExeCount ++;
                if (Config.getBoostFwkVersion() >=  Config.BOOST_FWK_VERSION_3) {
                    delayDisableTouchBoost();
                } else {
                    disableTouchBoost();
                }
                mScollingPolicyEnabled = true;
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(
                            WorkerHandler.MSG_SBE_POLICY_FLAG_END, null),
                            Config.getVerticalScrollDuration() - mCheckFPSTime);
            }
        } else {
            ScrollState.onScrollWhenMove(false);
            mPolicyExeCount = 0;
            mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_POLICY_FLAG_END, null);
            powerHintUxScrollPolicy(MTKPOWER_HINT_UX_MOVE_SCROLLING, 0);
            mScollingPolicyEnabled = false;
            if (!useFPSGo) {
                //if useFpsgo means Policy want fpsgo ctrl when scrolling.
                delayControlFpsgo(sFINGER_MOVE, true);
            }
        }
        LogUtil.traceEnd();
    }

    public void disableMTKScrollingFlingPolicyIfNeeded(boolean disableFpsgo) {
        // Close fling boost if it enabled
        if (mFlingPolicyExeCount != 0) {
            LogUtil.traceAndMLogd(TAG, "disable Scrolling FlingPolicy");
            mWorkerHandler.removeMessages(
                    WorkerHandler.MSG_SBE_FLING_POLICY_FLAG_END, null);
            powerHintUxScrollPolicy(MTKPOWER_HINT_UX_SCROLLING, 0);
            if (disableFpsgo) {
                mWorkerHandler.sendEmptyMessage(WorkerHandler.MSG_SBE_FLING_POLICY_FLAG_END);
            } else {
                mFlingPolicyExeCount = 0;
            }
        }
    }

    /**
     * Mtk fling policy, detail config see powerscntbl.xml.
     * Customer can customeization by own requirment.
     *
     */
    private void mtkScrollingFlingPolicy(boolean enable) {
        mtkScrollingFlingPolicy(enable ,-1);
    }

    /**
     * Mtk fling scrolling policy, enable or disable the policy based on the given flag.
     * This method is optimized based on the given context to
     * ensure thread safety and minimal performance impact.
     *
     * @param enable Indicates whether to enable or disable the policy.
     * @param velocity The fling velocity.
     */
    private void mtkScrollingFlingPolicy(boolean enable , int velocity) {
        if (null == mPowerHalService) {
            LogUtil.mLogw(TAG, "mPowerHalService is null");
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndLog(TAG, "mtkScrollingFlingPolicyWithVelocity "
                    + (enable ? "start" : "stop")
                    + " " +mFlingPolicyExeCount + " "
                    + useFPSGo + " velocity = " + velocity
                    + " isFlutter " + mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_FLUTTER)
                    + " isWebView " + mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW)
                    + " pageType " + mActivityInfo.getPageType());
        }
        if (enable) {
            if (mFlingPolicyExeCount == 0 && !useFPSGo) {
                mIsRealAOSPPage = false;

                int duration = 0;
                if ((velocity > 0 || mScrollerDuration > 0)
                        && Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4) {
                    duration = scrollingFingStep == sFLING_HORIZONTAL ?
                        Config.getHorizontalScrollDuration() :
                        Math.min(getSplineFlingDuration(velocity),
                            Config.getVerticalScrollDuration());
                } else {
                    duration = scrollingFingStep == sFLING_HORIZONTAL ?
                        Config.getHorizontalScrollDuration() : Config.getVerticalScrollDuration();
                }
                if (LogUtil.DEBUG) {
                    LogUtil.traceAndLog(TAG, "FlingPolicyWithVelocity "
                            + " duration = " + duration);
                }

                ScrollState.onGestureFling(true);
                enableFPSGo(true, scrollingFingStep);
                mFlingPolicyExeCount ++;
                mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_FLING_POLICY_FLAG_END, null);
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(WorkerHandler.MSG_SBE_FLING_POLICY_FLAG_END,
                        null), duration);

                // Update page type when first fling
                if (velocity > 0 && Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4) {
                    // Check webview task once, after webview checking,
                    // we can check other task for flutter
                    startSpecialPageRunningCheck(ActivityInfo.PAGE_TYPE_WEBVIEW);
                }
            }
        } else {
            if (mActivityInfo.isAOSPPageDesign()
                    && !mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_DESIGN_COMPOSE)
                    && mAOSPHintCount == 0) {
                LogUtil.mLoge("SBE", "this page is abnormal implementation");
                if (LogUtil.DEBUG && mActivityInfo.getContext() != null) {
                    LogUtil.traceAndMLogd(TAG, "this page is abnormal implementation " +
                            "ac=" + mActivityInfo.getContext());
                }
            }
            ScrollState.onGestureFling(false);
            mFlingPolicyExeCount = 0;
            mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_FLING_POLICY_FLAG_END, null);
            if (useFPSGo) {
                enableFPSGo(true, scrollingFingStep);
            } else {
                delayControlFpsgo(scrollingFingStep, true);
            }
        }
        LogUtil.traceEnd();
    }

    // Get the duration of the fling animation based on the velocity of the fling gesture.
    private int getSplineFlingDuration(int velocity) {
        // If mScrollerDuration is set, use it as the fling duration
        // This value is set by the Scroller
        if (mScrollerDuration > 0) {
            return mScrollerDuration;
        }

        // For WebView
        if (mWebViewRunningState == RUNNING_STATE_RUNNING
                && mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW)
                && !mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_FLUTTER)) {

            if (ppi == 0.f) { // Reset ppi and mPhysicalCoeff only once
                Context context = mActivityInfo.getContext();
                if (context != null) {
                    ppi = context.getResources().getDisplayMetrics().density
                        * 160.0f;
                    if (ppi <= 0.f) {
                        // Default is 3s
                        ppi = 0.f;
                        return Config.getVerticalScrollDuration();
                    }

                    mPhysicalCoeff = SensorManager.GRAVITY_EARTH // g (m/s^2)
                        * 39.37f // inch/meter
                        * ppi
                        * 0.84f; // look and feel tuning
                } else {
                    return Config.getVerticalScrollDuration();
                }
            }

            final double l = getSplineDeceleration(velocity);
            final double decelMinusOne = DECELERATION_RATE - 1.0;
            return (int) (1000.0 * Math.exp(l / decelMinusOne));
        }

        // Default is 3s
        return Config.getVerticalScrollDuration();
    }

    private double getSplineDeceleration(int velocity) {
        return Math.log(INFLEXION * Math.abs(velocity) / (mFlingFriction * mPhysicalCoeff));
    }

    // Set duration of Scroller fling
    public void setScrollerDuration(int duration) {
        mScrollerDuration = duration;
    }

    //reset fling state when touch down
    private void resetScrollerState() {
        mScrollerDuration = 0;
    }

    //check if special page is running
    private void startSpecialPageRunningCheck(int pageType) {
        if (LogUtil.DEBUG) {
            LogUtil.traceAndLog(TAG, "startSpecialPageRunningCheck "
                    + " pageType = " + pageType);
        }

        switch (pageType) {
            case ActivityInfo.PAGE_TYPE_WEBVIEW: // check if webview is running
                getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                    TasksUtil.WEBVIEW_TASKS, 5,
                    android.os.Process.myPid(), FPSGO_RUNNING_CHECK, 1);
                mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_WEBVIEW_CHECK, null);
                //delay 100ms query result
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(WorkerHandler.MSG_SBE_WEBVIEW_CHECK, null),
                        RUNNING_CHECK_DELAY_TIME);
                break;
            case ActivityInfo.PAGE_TYPE_FLUTTER: // check if flutter is running
                getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.FLUTTER_RENDER_TASKS,
                    mActivityInfo.getFlutterRenderTask(), mActivityInfo.getFlutterRenderTaskNum(),
                    android.os.Process.myPid(), FPSGO_RUNNING_CHECK, 1);
                mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_FLUTTER_CHECK,null);
                //delay 100ms query result
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(WorkerHandler.MSG_SBE_FLUTTER_CHECK, null),
                        RUNNING_CHECK_DELAY_TIME);
                break;
            default:
                break;
        }
    }

    //Query webview running state in past 100ms
    private void updateSpecialPageType(int pageType) {
        int running = -1;
        switch (pageType) {
            case ActivityInfo.PAGE_TYPE_WEBVIEW: // check if webview is running
                //query those thread running state in 100ms
                running = getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                    TasksUtil.WEBVIEW_TASKS, 5,android.os.Process.myPid(),
                    FPSGO_RUNNING_QUERY, 1);

                // clear running record
                getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                    TasksUtil.WEBVIEW_TASKS, 5,
                    android.os.Process.myPid(), FPSGO_RUNNING_CHECK, 0);

                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, "webview running->" + running);
                }

                mActivityInfo.updateSpecialPageTypeIfNecessary(
                    running == FPSGO_RUNNING_CHECK_RUNNING ? true : false,
                        ActivityInfo.PAGE_TYPE_WEBVIEW);

                if (running == FPSGO_RUNNING_CHECK_RUNNING) {
                    if (mWebViewRunningState != RUNNING_STATE_RUNNING ){
                        mActivityInfo.tryUpdateWebViewRenderPid();
                        // try to control webview at first scroll
                        fpsgoCtrlPagesIfNeed(true);
                    }
                    mWebViewRunningState = RUNNING_STATE_RUNNING;
                } else {
                    mWebViewRunningState = RUNNING_STATE_NO_RUNNING;
                }
                // Check flutter task once
                startSpecialPageRunningCheck(ActivityInfo.PAGE_TYPE_FLUTTER);
                break;
            case ActivityInfo.PAGE_TYPE_FLUTTER: // check if flutter is running
                //query those thread running state in 100ms
                running = getPowerHalWrapper().mtkSbeSetWebviewPolicy(
                    TasksUtil.FLUTTER_RENDER_TASKS,
                    mActivityInfo.getFlutterRenderTask(),
                    mActivityInfo.getFlutterRenderTaskNum(),
                    android.os.Process.myPid(), FPSGO_RUNNING_QUERY, 1);

                getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.FLUTTER_RENDER_TASKS,
                    mActivityInfo.getFlutterRenderTask(),
                    mActivityInfo.getFlutterRenderTaskNum(),
                    android.os.Process.myPid(), FPSGO_RUNNING_CHECK, 0);

                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, "flutter running->" + running
                        + " task->" + mActivityInfo.getFlutterRenderTask());
                }

                mActivityInfo.updateSpecialPageTypeIfNecessary(
                    running == FPSGO_RUNNING_CHECK_RUNNING ? true : false,
                        ActivityInfo.PAGE_TYPE_FLUTTER);

                if (running == FPSGO_RUNNING_CHECK_RUNNING) {
                    if (mFlutterRunningState != RUNNING_STATE_RUNNING){
                        mActivityInfo.updateFlutterRenderTaskInfo();
                        // try to control flutter at first scroll
                        fpsgoCtrlPagesIfNeed(true);
                    }
                    mFlutterRunningState = RUNNING_STATE_RUNNING;
                } else {
                    mFlutterRunningState = RUNNING_STATE_NO_RUNNING;
                }
                break;
            default:
                break;
        }
    }


    private PowerHalWrapper getPowerHalWrapper() {
        if (mPowerHalWrap == null) {
            mPowerHalWrap = PowerHalWrapper.getInstance();
        }
        return mPowerHalWrap;
    }

    private void enableFPSGo(boolean enable, int step) {
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndLog(TAG, "enableFPSGo " + (enable ? "start" : "stop"));
        }
        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_DISABLE_FPSGO_COUNT_DOWN);
        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK);
        if (enable) {
            releaseFPSGOControl(true, step);
            //add for fling UX_SCROLL boost by wei.kang 20241202 start
            enableUXScrollCMD(true);
            //add for fling UX_SCROLL boost by wei.kang 20241202 end
            //call from switchfpsgo, max fpsgo ctrl time is 3s.
            if (useFPSGo) {
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(
                        WorkerHandler.MSG_SBE_DISABLE_FPSGO_COUNT_DOWN, null),
                            Config.getVerticalScrollDuration() - mCheckFPSTime);
            }
        } else {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "App Moving but no up, continue fpsgo "
                        + mIsAppMoving + " " + mLastUserDownTimeMS);
            }
            if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2
                    //500/8 = 62.5fps, incase user touch but do not move
                    && mIsAppMoving > MOVING_TIMES_THRESHOLD
                    && mLastUserDownTimeMS > 0
                    //50ms buffer for check time
                    && (System.currentTimeMillis() - mLastUserDownTimeMS) >
                            (Config.getVerticalScrollDuration() - mCheckFPSTime - 50)
                    && mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW)) {
                //trigger fpsgo check running
                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, "App Moving but no up, continue fpsgo, start check");
                }
                if (mPowerHalWrap == null) {
                    mPowerHalWrap = PowerHalWrapper.getInstance();
                }
                mPowerHalWrap.mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                  TasksUtil.WEBVIEW_TASKS, 5, android.os.Process.myPid(), FPSGO_RUNNING_CHECK, 1);
                //delay 100ms query result
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(
                            WorkerHandler.MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK, step, -1),
                    mGameModeCheckTime);
            } else {
                releaseFPSGOControl(false, step);
            }
            //add for fling UX_SCROLL boost by wei.kang 20241202 start
            enableUXScrollCMD(false);
            //add for fling UX_SCROLL boost by wei.kang 20241202 end
        }
        LogUtil.traceEnd();
    }

    public void gameModeOnWebviewCheck(int step) {
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "game mode checking on webview  " + mIsAppMoving);
        }
        if (mIsAppMoving == 0) {
            return;
        }
        //query those thread running state in 100ms
        int running = mPowerHalWrap.mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                TasksUtil.WEBVIEW_TASKS, 5,
                android.os.Process.myPid(), FPSGO_RUNNING_QUERY, 1);
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "game mode checking on webview running->" + running);
        }

        if (running == FPSGO_RUNNING_CHECK_RUNNING) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "game mode webview is running continue fpsgo");
            }
            //waiting another 3s
            mWorkerHandler.sendMessageDelayed(
                mWorkerHandler.obtainMessage(
                    WorkerHandler.MSG_SBE_DISABLE_FPSGO_COUNT_DOWN, null),
                        Config.getVerticalScrollDuration() - mCheckFPSTime);
        } else {
            //TODO: record fling step
            releaseFPSGOControl(false, step);
        }
    }

    private void delayControlFpsgo(int step, boolean enable) {
        if (!enable) {
            mWorkerHandler.removeMessages(
                WorkerHandler.MSG_SBE_DELAY_RELEASE_FPSGO, null);
            waitingForReleaseFpsgoStep = -1;
        } else if (!waitingForReleaseFpsgo) {
            //In order to reduce the binder transaction with powerhalservice, lead to fpsgo start control too late
            //delay 50ms release fpsgo, if next start fpsgo come soon, MSG_SBE_DELAY_RELEASE_FPSGO will be remove
            //Why 50ms? ---> from the 120hz trace, the worse case, fpsgo control start after 6 frames
            mWorkerHandler.sendMessageDelayed(
                mWorkerHandler.obtainMessage(
                    WorkerHandler.MSG_SBE_DELAY_RELEASE_FPSGO, null), mDelayReleaseFpsTime);
            waitingForReleaseFpsgoStep = step;
        }
        waitingForReleaseFpsgo = enable;
    }

    private void releaseFPSGOControl(boolean isBegin, int step) {
        releaseFPSGOControl(isBegin, step, null);
    }

    // Enable/disable FPSGO control and uboost
    // In order to reduce the binder transaction with powerhalservice, need excute commands together
    // 1. call from mtkScrollingPolicy or mtkScrollingFlingPolicy
    //    if fing -> excute PERF_RES_FPS_FPSGO_CTL, PERF_RES_FPS_FPSGO_NOCTL, PERF_RES_FPS_FPSGO_UBOOST
    //    others -> excute PERF_RES_FPS_FPSGO_CTL
    // 2. call from releaseTargetFPS
    //    excute PERF_RES_FPS_FPSGO_CTL, PERF_RES_FPS_FPSGO_NOCTL, PERF_RES_FPS_FSTB_TARGET_FPS_PID, PERF_RES_FPS_FPSGO_UBOOST
    private void releaseFPSGOControl(boolean isBegin, int step, int commands[]) {
        int renderThreadTid = getRenderThreadTid();
        String log = "";
        boolean isDisableFrameTracking = Config.isDisableFrameDecision();
        if (isBegin) {
            delayControlFpsgo(sFLING_VERTICAL, false);
            if (isDisableFrameTracking) {
                mPerfLockRscList.add(PERF_RES_FPS_FPSGO_CTL);
                mPerfLockRscList.add(renderThreadTid);
            }
            if (step == sFLING_VERTICAL || step == sFLING_HORIZONTAL) {
                //if fpsgo already in ctrl(kernel5.10) or noctrl(kernel4.14, kernel4.19)
                //do not excute them again.
                if (!fpsgoUnderCtrlWhenFling
                        && Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_2 ) {
                    //for kernel 4.14 & 4.19, supoort fpsgo_noctrl, for others is fpsgo_ctl
                    mPerfLockRscList.add(PERF_RES_FPS_FPSGO_NOCTL);
                    mPerfLockRscList.add(-renderThreadTid);
                } else {
                    if (isDisableFrameTracking) {
                        //reset array for clear commands.
                        mPerfLockRscList.set(0, 0);
                        mPerfLockRscList.set(1, 0);
                    }
                }
                fpsgoUnderCtrlWhenFling = true;
                uBoostAcquire();
            }
            mLastScrollTimeMS = System.currentTimeMillis();

            if (!mCommonPolicyEnabled
                    // for version 3, always call scrolling common policy
                    || Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                enableScrollingCommonCMD(true);
            }
            log = "start " + step;
        } else {
            if (isDisableFrameTracking) {
                mPerfLockRscList.add(PERF_RES_FPS_FPSGO_CTL);
                mPerfLockRscList.add(-renderThreadTid);
            }
            if (step == sFLING_VERTICAL || step == sFLING_HORIZONTAL) {
                //for kernel 4.14 & 4.19, supoort fpsgo_noctrl, for others is fpsgo_ctl
                if (Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_2) {
                    mPerfLockRscList.add(PERF_RES_FPS_FPSGO_NOCTL);
                    mPerfLockRscList.add(renderThreadTid);
                }
                fpsgoUnderCtrlWhenFling = false;
                uBoostRelease();
            }
            enableScrollingCommonCMD(false);
            mSpecialAppDesign = -1;
            log = "end " + step;
        }
        fpsgoCtrlPagesIfNeed(isBegin);
        if (commands != null) {
            for (int i = 0; i < commands.length; i++) {
                mPerfLockRscList.add(commands[i]);
            }
        }
        if (mPerfLockRscList.size() > 0) {
            mPerfLockRscList.add(PERF_RES_POWERHAL_CALLER_TYPE);
            mPerfLockRscList.add(1);
        }
        int size = mPerfLockRscList.size();
        int perf_lock_rsc[] = new int[size] ;
        boolean hasCMD = false;
        for (int i = 0; i < size; i++) {
            perf_lock_rsc[i] = (int)mPerfLockRscList.get(i);
            if (perf_lock_rsc[i] != 0) {
                hasCMD = true;
            }
        }
        if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
            //if all command is 0, means no cmd need hint powerhal, reduce powerhal call
            if (hasCMD) {
                controlFpsgoInternal(perf_lock_rsc, log);
            }
        } else {
            controlFpsgoInternal(perf_lock_rsc, log);
        }
        boolean scrollStateUpdated = ScrollState.setScrolling(isBegin, log);
        if (scrollStateUpdated) {
            notifyHWUIScrollingType(isBegin);
        }
        mPerfLockRscList.clear();
    }

    private void notifyHWUIScrollingType(boolean isBegin) {
        //for page no activity, do not enable dynamic rescue and ux gneral policy
        //incase impact game
        if (Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_3
                || mActivityInfo == null
                || mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY)) {
            return;
        }
        if (mPowerHalWrap == null) {
            mPowerHalWrap = PowerHalWrapper.getInstance();
        }

        //only AOSP page vertical scrolling hint fpsgo HWUI,
        //then enable dynamic rescue
        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_DESIGN)
                && !ScrollState.isStatus(ScrollState.SCROLL_STATUS_HORIZONTAL)
                && getRenderThreadTid() > 0) {
            if (LogUtil.DEBUG) {
                LogUtil.traceBeginAndMLogd(TAG, " HWUI for render"
                        + getRenderThreadTid() + " " + isBegin);
            }
            int mask = ScrollState.isScrollingWhenInput() ? FPSGO_MOVING : FPSGO_FLING;
            //notify fpsgo hwui scroll start and end
            mPowerHalWrap.mtkSbeSetWebviewPolicy("RenderThread", "", 0,
                        android.os.Process.myPid(), mask | FPSGO_HWUI, isBegin ? 1 : 0);
            LogUtil.traceEnd();
        }
    }

    private void fpsgoCtrlPagesIfNeed(boolean isBegin) {
        if (Config.enableScrollFloor) {
            return;
        }

        if (mPowerHalWrap == null) {
            mPowerHalWrap = PowerHalWrapper.getInstance();
        }

        if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4) {
            if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW)) {
                if (LogUtil.DEBUG) {
                    LogUtil.traceBeginAndMLogd(TAG, " PAGE_TYPE_WEBVIEW for render"
                            + TasksUtil.BLINK_WEBVIEW_RENDER_NAME + " " + TasksUtil.WEB_RENDER_NAME
                            + " " + isBegin);
                }

                List<RenderInfo> webviewRenders =
                        mActivityInfo.getRendersByPageType(ActivityInfo.PAGE_TYPE_WEBVIEW);

                if (webviewRenders != null && webviewRenders.size() > 0) {
                    for (RenderInfo render : webviewRenders) {
                        if (LogUtil.DEBUG) {
                            LogUtil.traceAndMLogd(TAG, " try ctrl render->" + render);
                        }
                        mPowerHalWrap.mtkSbeSetWebviewPolicy(render.getRenderName(),"", 0,
                                render.getRenderPid(), WEBVIEW_FLUTTER_FPSGO_CTRL_MASK,
                                isBegin ? 1 : 0);
                    }
                }

                mPowerHalWrap.mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME, "", 0,
                        android.os.Process.myPid(), WEBVIEW_FLUTTER_FPSGO_CTRL_MASK,
                        isBegin ? 1 : 0);
                LogUtil.traceEnd();
            }
        } else {
            if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW)) {
                if (LogUtil.DEBUG) {
                    LogUtil.traceBeginAndMLogd(TAG, " PAGE_TYPE_WEBVIEW for render"
                            + TasksUtil.BLINK_WEBVIEW_RENDER_NAME + " " + TasksUtil.WEB_RENDER_NAME
                            + mWebviewRenderPid + " " + isBegin);
                }
                if (getWebViewRenderPid() > 0) {
                    mPowerHalWrap.mtkSbeSetWebviewPolicy(TasksUtil.BLINK_WEBVIEW_RENDER_NAME, "", 0,
                            mWebviewRenderPid, WEBVIEW_FLUTTER_FPSGO_CTRL_MASK, isBegin ? 1 : 0);
                }
                mPowerHalWrap.mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME, "", 0,
                        android.os.Process.myPid(), WEBVIEW_FLUTTER_FPSGO_CTRL_MASK,
                        isBegin ? 1 : 0);
                LogUtil.traceEnd();
            }
        }

        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_FLUTTER)) {
            List<RenderInfo> renders =
                mActivityInfo.getRendersByPageType(ActivityInfo.PAGE_TYPE_FLUTTER);
            if (LogUtil.DEBUG) {
                LogUtil.traceBeginAndMLogd(TAG, " PAGE_TYPE_FLUTTER "
                    + android.os.Process.myPid() + " " + isBegin);
            }
            for (int i = 0; i < TasksUtil.FLUTTER_RENDER_TASK.length; i++) {
                mPowerHalWrap.mtkSbeSetWebviewPolicy(TasksUtil.FLUTTER_RENDER_TASK[i],
                        TasksUtil.FLUTTER_TASKS, 1, android.os.Process.myPid(),
                        WEBVIEW_FLUTTER_FPSGO_CTRL_MASK, isBegin ? 1 : 0);
            }
            //render find dynamic
            if (renders != null && renders.size() > 0) {
                for (RenderInfo render : renders) {
                    if (LogUtil.DEBUG) {
                        LogUtil.traceAndMLogd(TAG, " try ctrl render->" + render);
                    }
                    mPowerHalWrap.mtkSbeSetWebviewPolicy(render.getRenderName(),
                            render.getKnownDepNameStr(), render.getKnownDepSize(),
                            android.os.Process.myPid(), WEBVIEW_FLUTTER_FPSGO_CTRL_MASK,
                            isBegin ? 1 : 0);
                }
            }
            LogUtil.traceEnd();
        }

        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_MAP)
                && mActivityInfo.mGLTaskName != null) {
            if (LogUtil.DEBUG) {
                LogUtil.traceBeginAndMLogd(TAG, " PAGE_TYPE_SPECIAL_DESIGN_MAP "
                        + mActivityInfo.mGLTaskName + " " + isBegin);
            }
            mPowerHalWrap.mtkSbeSetWebviewPolicy(mActivityInfo.mGLTaskName, "", 0,
                    android.os.Process.myPid(), MAP_FPSGO_CTRL_MASK, isBegin ? 1 : 0);

            int perf_lock_rsc[];

            if (mActivityInfo.mGLTaskName.contains(TasksUtil.GL_MAP_TASKS[0])) {
                List<RenderInfo> renders =
                    mActivityInfo.getRendersByPageType(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_MAP);
                int tid = 0;
                if (renders != null
                        && renders.size() > 0
                        && renders.get(0).getKnownDepSize() > 0) {
                    tid = renders.get(0).getKnownDepTidList().get(0);
                }
                if (tid > 0) {
                    if (LogUtil.DEBUG && renders != null && renders.size() > 0) {
                        LogUtil.traceAndMLogd(TAG, " try ctrl render->" + renders.get(0));
                    }

                    if (isBegin) {
                        perf_lock_rsc = new int[] {PERF_RES_SCHED_SBB_TASK_SET, tid,
                                PERF_RES_SCHED_SBB_ACTIVE_RATIO, 70,
                                PERF_RES_POWERHAL_CALLER_TYPE, 1};
                    } else {
                        perf_lock_rsc = new int[] {PERF_RES_SCHED_SBB_TASK_UNSET, tid,
                                PERF_RES_POWERHAL_CALLER_TYPE, 1};
                        if (mMapHandle > 0 && null != mPowerHalService) {
                            mPowerHalService.perfLockRelease(mMapHandle);
                        }
                    }
                    acquirePowerhal(perf_lock_rsc, Config.getVerticalScrollDuration());
                }
            }
            LogUtil.traceEnd();
        }

        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_RO)) {
            List<RenderInfo> renders =
                mActivityInfo.getRendersByPageType(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_RO);
            //render find dynamic
            if (renders != null && renders.size() > 0) {
                for (RenderInfo render : renders) {
                    if (LogUtil.DEBUG) {
                        LogUtil.traceAndMLogd(TAG, " try ctrl render->" + render);
                    }
                    mPowerHalWrap.mtkSbeSetWebviewPolicy(render.getRenderName(),
                            render.getKnownDepNameStr(), render.getKnownDepSize(),
                            android.os.Process.myPid(), WEBVIEW_FLUTTER_FPSGO_CTRL_MASK,
                            isBegin ? 1 : 0);
                }
            }
        }
    }

    private void controlFpsgoInternal(int perf_lock_rsc[], String logStr) {
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, logStr +
                " control Fpsgo " + commands2String(perf_lock_rsc));
        }
        if (mActivityInfo.isSpecialPageDesign()
                || (mActivityInfo.isAOSPPageDesign()
                && Config.isDisableFrameDecision())) {
            perfLockAcquire(perf_lock_rsc);
        }
        LogUtil.traceEnd();
    }

    private String commands2String(int commands[]) {
        if (commands == null || commands.length == 0 || !LogUtil.DEBUG) {
            return "";
        }
        String cStr = "";
        int l = commands.length;
        for (int i = 0; i < l; i++) {
            switch(commands[i]) {
                case PERF_RES_FPS_FPSGO_CTL:
                    cStr += " PERF_RES_FPS_FPSGO_CTL ";
                    break;
                case PERF_RES_FPS_FPSGO_NOCTL:
                    cStr += " PERF_RES_FPS_FPSGO_NOCTL ";
                    break;
                case PERF_RES_FPS_FSTB_TARGET_FPS_PID:
                    cStr += " PERF_RES_FPS_FSTB_TARGET_FPS_PID ";
                    break;
                case PERF_RES_FPS_FBT_RESCUE_ENABLE_PID:
                    cStr += " PERF_RES_FPS_FBT_RESCUE_ENABLE_PID ";
                    break;
                default:
                    cStr += String.valueOf(commands[i]) + " ";
                    break;
            }
        }
        return cStr;
    }

    /**
     * Release Target FPS control when Scrolling.
     *
     */
    public void releaseTargetFPS(boolean release) {
        if (mDisableScrollPolicy) {
            LogUtil.traceAndMLogd(TAG, "releaseTargetFPS scroll policy has been disable");
            return;
        }
        int renderThreadTid = getRenderThreadTid();
        if (renderThreadTid == ActivityInfo.NON_RENDER_THREAD_TID) {
            LogUtil.mLogw(TAG, "cannot found render thread");
            return;
        }

        // renderThreadTid send to FPSGO to release TargetFPS
        // The positive number means enable this feature
        // The negative number means disable this feature
        if (release) {
            mWorkerHandler.sendMessage(
                mWorkerHandler.obtainMessage(
                        WorkerHandler.MSG_RELEASE_BEGIN, null));
        } else {
            mWorkerHandler.sendMessage(
                mWorkerHandler.obtainMessage(
                        WorkerHandler.MSG_RELEASE_END, null));
            //add for fling UX_SCROLL boost by wei.kang 20241202 start
            enableUXScrollCMD(false);
            //add for fling UX_SCROLL boost by wei.kang 20241202 end
        }
    }

    private void delayReleaseTargetFPSInternal() {
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, "delay release Target FPS");
        }
        int renderThreadTid = getRenderThreadTid();
        int perf_lock_rsc[] = new int[2];
        if (Config.isDisableFrameDecision()) {
            perf_lock_rsc[0] = PERF_RES_FPS_FSTB_TARGET_FPS_PID;
            perf_lock_rsc[1] = -renderThreadTid;
        } else {
            perf_lock_rsc[0] = perf_lock_rsc[1] = 0;
        }
        releaseFPSGOControl(false, sFLING_VERTICAL, perf_lock_rsc);
        LogUtil.traceEnd();
    }

    private void releaseTargetFPSInternel(boolean isBegin) {
        String logStr = "release Target FPS";
        int renderThreadTid = getRenderThreadTid();
        int perf_lock_rsc[] = new int[2];
        //for framedecision, fpsgo default tracking max fps
        if (Config.isDisableFrameDecision()) {
            perf_lock_rsc[0] = PERF_RES_FPS_FSTB_TARGET_FPS_PID;
            perf_lock_rsc[1] = isBegin ? renderThreadTid : -renderThreadTid;
        } else {
            perf_lock_rsc[0] = perf_lock_rsc[1] = 0;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, logStr + (isBegin ? "start" : "stop"));
        }
        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_DISABLE_FPSGO_COUNT_DOWN);
        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_DELAY_RELEASE_TARGET_FPS);
        if (Config.dealyReleaseFPSGO && !isBegin
                && mCurrentScrollStep == sFINGER_MOVE && mScollingPolicyEnabled) {
            //user touch when fling, AOSP will send finish message after scrolling policy enable
            //lead to policy disable and release FPSGO control
            //ignore this message
            LogUtil.trace("ignore release target fps when moving");
            LogUtil.traceEnd();
            return;
        } else {
            //if Scroller/OverScroller Control, add timeout checker
            if (useFPSGo && isBegin) {
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(
                        WorkerHandler.MSG_SBE_DISABLE_FPSGO_COUNT_DOWN, null),
                            Config.getVerticalScrollDuration() - mCheckFPSTime);
            }
            releaseFPSGOControl(isBegin, sFLING_VERTICAL, perf_lock_rsc);
        }
        LogUtil.traceEnd();
    }

    private void scrollCommonPolicyCheck() {
        //check last scroll over release duration or not
        if (mLastScrollTimeMS > 0
            && (Config.getVerticalScrollDuration()) > (System.currentTimeMillis() - mLastScrollTimeMS)) {
            enableScrollingCommonCMD(true);
        } else if (mCommonPolicyEnabled) {
            enableScrollingCommonCMD(false);
        }
    }

    private void resetScrollPolicyStatus(Context c) {
        //reset fling state
        resetFlingState(c);

        //reset fpsgo scroll state
        if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_3
                && getRenderThreadTid() > 0 && mPowerHalWrap != null) {
            if (LogUtil.DEBUG) {
                LogUtil.traceBeginAndMLogd(TAG, " notify fpsgo reset status");
            }
            //notify fpsgo hwui scroll start and end
            mPowerHalWrap.mtkSbeSetWebviewPolicy("RenderThread", "", 0,
                        android.os.Process.myPid(), FPSGO_CLEAR_SCROLLING_INFO, 1);
            LogUtil.traceEnd();
        }
    }

    private int getRenderThreadTid() {
        return mActivityInfo.getRenderThreadTid();
    }

    private int getWebViewRenderPid() {
        int tmpTid = mActivityInfo.getWebViewRenderPid();
        if (tmpTid > 0) {
            mWebviewRenderPid = tmpTid;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "getWebViewRenderTid webviewPid= " + mWebviewRenderPid);
        }
        return mWebviewRenderPid;
    }

    private void perfLockAcquire(int[] resList) {
        if (null != mPowerHalService) {
            mPowerHandle = mPowerHalService.perfLockAcquire(mPowerHandle,
                    Config.getVerticalScrollDuration(), resList);
            mPowerHalService.perfLockRelease(mPowerHandle);
        }
    }

    private void powerHintUxScrollPolicy(int cmd, int duration) {
        if (Config.enableScrollFloor) {
            mPowerHalService.mtkPowerHint(cmd, duration);
        }
    }

    /**
     * This feature only for define uboost powerhal cmd can use, such as 6983, others chip
     * can not response when powerhal called.
     * if uboost already enbale, not allow enable agagin.
     */
    private void uBoostAcquire() {
        //only support in version 1
        if (Config.getBoostFwkVersion() != Config.BOOST_FWK_VERSION_1) {
            return;
        }
        if (null != mPowerHalService && !uboostEnable) {
            uboostEnable = true;
            int perf_lock_rsc[] = new int[] {PERF_RES_FPS_FPSGO_UBOOST, 1,
                    PERF_RES_POWERHAL_CALLER_TYPE, 1};
            mBoostHandle = mPowerHalService.perfLockAcquire(mBoostHandle,
                    Config.getVerticalScrollDuration(), perf_lock_rsc);
        }
    }

    private void uBoostRelease() {
        //only support in version 1
        if (Config.getBoostFwkVersion() != Config.BOOST_FWK_VERSION_1) {
            return;
        }
        if (null != mPowerHalService && uboostEnable) {
            uboostEnable = false;
            mPowerHalService.perfLockRelease(mBoostHandle);
        }
    }

    private void delayDisableTouchBoost() {
        if (mWorkerHandler != null) {
            long disableTouchDelayTime = (long)(ONESECOND_MS / ScrollState.getRefreshRate()
                * Config.getDisableTouchDelay());
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "delayDisableTouchBoost: " + disableTouchDelayTime);
            }
            mWorkerHandler.sendEmptyMessageDelayed(
                WorkerHandler.MSG_SBE_DISABLE_TOUCH_BOOST,
                disableTouchDelayTime);
        }
    }

    private void acquirePowerhal(int perf_lock_rsc[], int duration) {
        if (null != mPowerHalService) {
            mMapHandle = mPowerHalService.perfLockAcquire(mMapHandle,
                    duration, perf_lock_rsc);
        }
    }

    private void disableTouchBoost() {
        if (Config.isInterruptTouchBoost() == Config.FEATURE_OPTION_OFF) {
           return;
        }
        int perf_lock_rsc[] = new int[] {PERF_RES_POWERHAL_TOUCH_BOOST_ENABLE, 0,
                PERF_RES_POWERHAL_CALLER_TYPE, 1};
        perfLockAcquire(perf_lock_rsc);

        if (Config.getBoostFwkVersion() >=  Config.BOOST_FWK_VERSION_3) {
            if (null != mPowerHalService) {
                mPowerHalService.mtkPowerHint(MTKPOWER_HINT_UX_TOUCH_MOVE, 0);
            }
        }
    }

    //add for fling uxscroll boost by wei.kang 20241202 start
    private void enableUXScrollCMD(boolean enable) {
        //must config the property of the enable uxscroll, otherwise, it won't take effect
        if(!ENABLE_UXSCROLL_HINT) {
            return;
        }
        int delayTime = enable ? Config.getVerticalScrollDuration() : 0;
        powerHintUxScrollPolicy(MTKPOWER_HINT_UX_SCROLLING, delayTime);
    }
    //add for fling uxscroll boost by wei.kang 20241202 end

    private void enableScrollingCommonCMD(boolean enable) {
        if (!ENABLE_SCROLL_COMMON_POLICY
                || Config.getScollPolicyType() == Config.MTK_SCROLL_POLICY_MODE_EAS) {
            return;
        }

        mCommonPolicyEnabled = enable;
        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_SCROLL_COMMON_POLICY_COUNT_DOWN);
        if (enable) {
            if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4) {
                mLastScrollCommonCMD = getScrollCommonCMD();
            }
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG,
                        "Enable MTKPOWER_HINT_UX_SCROLLING_COMMON cmd=" + mLastScrollCommonCMD);
            }
            mPowerHalService.mtkPowerHint(mLastScrollCommonCMD,
                    Config.getVerticalScrollDuration());
            //SPD:added for boost SF by song.tang 20230706 start
            checkBoostIfNeeded(Config.getVerticalScrollDuration());
            //SPD:added for boost SF by song.tang 20230706 end
            if (mActivityInfo.isSpecialPageDesign()
                    || !mIsRealAOSPPage || Config.checkCommonPolicyForALL) {
                //1.for special page there is no end time to disble this policy,
                //2.for AOSP page, if no scroller hint, also need checker
                //powerhal can not continue this Policy
                //so we need a count down to check continue or diabsle
                mWorkerHandler.sendEmptyMessageDelayed(
                        WorkerHandler.MSG_SBE_SCROLL_COMMON_POLICY_COUNT_DOWN,
                        Config.getVerticalScrollDuration() + mCheckCommonPolicyTime);
            }
        } else {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG,
                        "Disable MTKPOWER_HINT_UX_SCROLLING_COMMON cmd=" + mLastScrollCommonCMD);
            }
            mPowerHalService.mtkPowerHint(mLastScrollCommonCMD, 0);
            //SPD:added for boost SF by song.tang 20230706 start
            checkBoostIfNeeded(0);
            //SPD:added for boost SF by song.tang 20230706 end
        }
    }

    private int getScrollCommonCMD() {
        int cmd = MTKPOWER_HINT_UX_SCROLLING_COMMON;
        if (mActivityInfo == null) {
            return cmd;
        }
        int mode = 0;
        if (mActivityInfo.isAOSPPageDesign()) {
            mode = Config.getScrollCommonHwuiMode();
        } else {
            mode = Config.getScrollCommonNonHwuiMode();
        }
        float refreshRate = ScrollState.getRefreshRate();
        if (refreshRate < 61 && (mode & Config.SCROLLING_COMMON_MODE_60) != 0) {
            cmd = MTKPOWER_HINT_UX_SCROLLING_COMMON_MASK;
        } else if (refreshRate >= 61 && refreshRate < 91
                && (mode & Config.SCROLLING_COMMON_MODE_90) != 0) {
            cmd = MTKPOWER_HINT_UX_SCROLLING_COMMON_MASK;
        } else if (refreshRate >= 91 && (mode & Config.SCROLLING_COMMON_MODE_120) != 0) {
            cmd = MTKPOWER_HINT_UX_SCROLLING_COMMON_MASK;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "final scroll cmd=" + cmd
                    + " refreshRate=" + refreshRate + " mode=" + mode);
        }
        return cmd;
    }

    private void checkTouchMoveBoostIfNeeded() {
        if (null != mPowerHalService) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "Touch Move Boost : "
                    + Config.TOUCH_MOVE_BOOST_DURATION);
            }
            mPowerHalService.mtkPowerHint(MTKPOWER_HINT_UX_TOUCH_MOVE,
                Config.TOUCH_MOVE_BOOST_DURATION);
        }
    }

    //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20241223 start
    public void setFling(boolean isFling) {
        if (isFling && mWorkerHandler != null) {
            mWorkerHandler.post(() -> {
                try { // 使用子线程获取native耗时方法，避免主线程阻塞
                    TranScrollingFramePrefetcher.getInstance().updateComposition(ITranSurfaceControl.Instance().requiresClientComposition());
                } catch (NoSuchMethodError | UnsatisfiedLinkError e) { LogUtil.mLoge(TAG, "#updateFlingStatus, requiresClientComposition not found"); }
            });
        }
    }
    //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20241223 end

    //T-HUB Core[SPD]:added for scroll boost by dewang.tan  20250826 start
    public void checkBoostIfNeeded(int duration) {
        try {
            ActivityTaskManager.getService().boostInFling(
                    (SF_FLING_BOOST_SUPPORT && ITranSurfaceControl.Instance().requiresClientComposition()) ? USE_GPU : USE_NORMAL,
                    duration > 0, duration > 0 ? duration : 0);
        } catch (Exception | NoSuchMethodError e) {
            LogUtil.mLoge(TAG, "boost fling failed");
        }
    }
    //T-HUB Core[SPD]:added for scroll boost by dewang.tan  20250826 end


}