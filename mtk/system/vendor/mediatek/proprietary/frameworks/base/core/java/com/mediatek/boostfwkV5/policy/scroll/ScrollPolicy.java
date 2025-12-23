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

package com.mediatek.boostfwkV5.policy.scroll;

import android.content.Context;
import java.util.Map;
import java.util.HashMap;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.Trace;
import android.hardware.SensorManager;
import android.view.Window;
import android.view.ViewConfiguration;

import com.mediatek.boostfwk.BoostFwkManager;

import com.mediatek.boostframework.Performance;
import com.mediatek.boostfwkV5.identify.ime.IMEIdentify;
import com.mediatek.boostfwkV5.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwkV5.info.ScrollState;
import com.mediatek.boostfwkV5.info.ActivityInfo;
import com.mediatek.boostfwkV5.info.RenderInfo;
import com.mediatek.boostfwkV5.utils.Config;
import com.mediatek.boostfwkV5.utils.LogUtil;
import com.mediatek.boostfwkV5.utils.TasksUtil;

import com.mediatek.powerhalmgr.PowerHalMgr;
import com.mediatek.powerhalmgr.PowerHalMgrFactory;
import com.mediatek.powerhalwrapper.PowerHalWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashMap;
import java.util.Map;


/** @hide */
public class ScrollPolicy {

    private static final String TAG = "ScrollPolicy";

    private static volatile ScrollPolicy sInstance = null;
    private static Object lock = new Object();
    private static final String sTHREAD_NAME = TAG;

    // For custom scroller detection
    private static class ScrollerState {
        boolean hasMultipleActions;
        int actionCount;  // Count of PREFILING actions received
        private static final int MIN_ACTION_COUNT = 10;  // Minimum actions required

        ScrollerState() {
            hasMultipleActions = false;
            actionCount = 0;
        }

        void recordAction() {
            actionCount++;
        }

        boolean isValidScroller() {
            return actionCount >= MIN_ACTION_COUNT;
        }

        void reset() {
            actionCount = 0;
        }
    }

    private Map<String, ScrollerState> mScrollerStates = new HashMap<>();
    private static final long CUSTOM_SCROLLER_CHECK_DELAY = 100; //check if scrolling stopped
    private static final boolean ENABLE_SCROLL_COMMON_POLICY =
            Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_1
                    && Config.isEnableScrollCommonPolicy();
    private HandlerThread mWorkerThread = null;
    private WorkerHandler mWorkerHandler = null;

    private static final int MOVING_MODE_DURATION = 10000;//10S

    private Performance mPerformance = null;
    private PowerHalWrapper mPowerHalWrap = null;
    private PowerHalMgr mPowerHalService = PowerHalMgrFactory.getInstance().makePowerHalMgr();
    private static int mPowerHandle = 0;
    private ArrayList<Integer> mPerfLockRscList = new ArrayList<>(10);

    // Release Target FPS duration time.
    private static int mReleaseFPSDuration = Config.getVerticalScrollDuration();
    // Render thread tid
    private static int RENDER_TID_NON_CHECK = Integer.MIN_VALUE;
    private static int NON_RENDER_THREAD_TID = -1;
    private static int mRenderThreadTid = RENDER_TID_NON_CHECK;
    private static int mWebviewRenderPid = RENDER_TID_NON_CHECK;
    private static int mFlutterRenderTid = RENDER_TID_NON_CHECK;
    // Release Target FPS CMD.
    private static final int PERF_RES_FPS_FSTB_TARGET_FPS_PID = 0x02000200;
    // FPSGO release control CMD
    private static final int PERF_RES_FPS_FPSGO_CTL = 0x02000300;
    // FPSGO release no control CMD (for kernel4.14/4.19)
    private static final int PERF_RES_FPS_FPSGO_NOCTL = 0x02000400;
    // scrolling mode: performance > blance > normal
    // SBE Scrolling common CMD -> normal mode of scrolling
    private static int MTKPOWER_HINT_UX_SCROLLING_NORMAL_MODE = 49;
    // SBE Scrolling common CMD -> blance mode of scrolling
    private static int MTKPOWER_HINT_UX_SCROLLING_BALANCE_MODE = 61;
    // SBE Scrolling policy CMD -> perforamcne mode of scrolling
    private static int MTKPOWER_HINT_UX_SCROLLING_PERF_MODE = 45;
    // SBE Scrolling touch move boost CMD
    // MUST sync with vendor/mediatek/proprietary/hardware/power/include/mtkpower_hint.h
    private static int MTKPOWER_HINT_UX_TOUCH_MOVE = 56;
    private static int MTKPOWER_HINT_UX_SCROLLING_MOVING_MODE = 68;
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
    private static final int SBE_CPU_CONTROL = 1 << 0;
    // Special app design, not use android AOSP draw flow
    private static final int SBE_DISPLAY_TARGET_FPS = 1 << 1;
    // after refator, below fileds are not used(default set in ux sbe-kernel).
    // private static final int FPSGO_RESCUE_ENABLE = 1 << 2;
    // private static final int FPSGO_RL_ENABLE = 1 << 3;
    // private static final int FPSGO_GCC_DISABLE = 1 << 4;
    // private static final int FPSGO_QUOTA_DISABLE = 1 << 5;
    // replace with page type
    private static final int SBE_PAGE_WEBVIEW = 1 << 2;
    private static final int SBE_PAGE_FLUTTER = 1 << 3;
    //page in PIP and multi window mode
    private static final int SBE_PAGE_MULTI_WINDOW = 1 << 4;
    private static final int SBE_PAGE_APPBRAND = 1 << 5;
    private static final int SBE_RUNNING_CHECK = 1 << 6;
    private static final int SBE_RUNNING_QUERY = 1 << 7;
    private static final int SBE_PAGE_NON_HWUI = 1 << 8;
    private static final int SBE_PAGE_HWUI = 1 << 9;
    private static final int SBE_CLEAR_SCROLLING_INFO = 1 << 10;
    private static final int SBE_SCROLL_STATE_MOVING = 1 << 11;
    private static final int SBE_SCROLL_STATE_FLING = 1 << 12;
    private static final int SBE_SCROLL_STATE_SCROLLING = 1 << 13;
    private static final int SBE_CLEAR_RENDERS = 1 << 14;
    private static final int SBE_DISABLE_DPT = 1 << 15;
    private static final int SBE_AFFNITY_TASK = 1 << 16;
    private static final int SBE_PAGE_NO_ACTIVITY = 1 << 17;

    private final int FPSGO_RUNNING_CHECK_RUNNING = 10001;
    private final int SBE_TASK_RUNNING = 1 << 1;

    private final int WEBVIEW_FLUTTER_FPSGO_CTRL_MASK = SBE_CPU_CONTROL
            | SBE_DISPLAY_TARGET_FPS | SBE_PAGE_NON_HWUI;
    private final int MAP_FPSGO_CTRL_MASK = SBE_CPU_CONTROL;
    public static final int HWUI_CTRL_MASK = SBE_CPU_CONTROL
            | SBE_DISPLAY_TARGET_FPS | SBE_PAGE_HWUI;

    private static long mCheckFPSTime = 100;
    private static long mGameModeCheckTime = 100;
    private static long mCheckCommonPolicyTime = 16;
    private static long mDelayReleaseFpsTime = Config.getFpsgoReleaseTime();
    private static int mPolicyExeCount = 0;
    private static int mFlingPolicyExeCount = 0;
    private static int mSpecialAppDesign = -1;
    private static int mLastScrollCommonCMD = MTKPOWER_HINT_UX_SCROLLING_NORMAL_MODE;
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

    private int mTouchBoostPWRHandle = -1;
    private int mCommonPolicyPWRHandle = -1;
    private int mMovingPolicyPWRHandle = -1;
    private long mComminPolicyLastTime = 0;
    private int mWebviewCriticalTaskLen = 0;

    // SBE Scrolling common CMD
    private static int RUNNING_CHECK_DELAY_TIME = 100; // Check last 100ms

    private ActivityInfo.ActivityChangeListener mActivityChangeListener
            = new ActivityInfo.ActivityChangeListener() {
        @Override
        public void onChange(Context c) {
            resetFlingState(c);
            // Update internal token for validation
            if (c != null && mActivityInfo != null) {
                mActivityInfo.updateToken(c.getClass().getName());
            }
        }

        @Override
        public void onActivityPaused(Context c) {
            resetScrollPolicyStatus(c);
        }

        public void onAllActivityStop(Context c) {
            resetScrollRenders(c);
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
        mPerformance = new Performance();
        IMEIdentify.getInstance().registerIMEStateListener(mIMEStateListener);
        mActivityInfo = ActivityInfo.getInstance();
        mActivityInfo.registerActivityListener(mActivityChangeListener);
        mActivityInfo.updateTaskTid(mWorkerThread.getThreadId());
        mWebviewCriticalTaskLen = TasksUtil.WEBVIEW_CRITICAL_TASKS.split(",").length;
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
        // public static final int MSG_SBE_DELAY_RELEASE_TARGET_FPS = 14;
        public static final int MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK = 15;
        public static final int MSG_SBE_TOUCH_MOVE_BOOST = 16;
        public static final int MSG_SBE_WEBVIEW_CHECK = 17;
        public static final int MSG_SBE_FLUTTER_CHECK = 18;
        public static final int MSG_SBE_DISABLE_TOUCH_BOOST = 19;
        public static final int MSG_CHECK_CUSTOM_SCROLLER = 20;

        WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RELEASE_BEGIN:
                    releaseTargetFPSInternel(true, (boolean)(msg.obj));
                    break;
                case MSG_RELEASE_END:
                    releaseTargetFPSInternel(false, (boolean)(msg.obj));
                    break;
                case MSG_RELEASE_FPS_CHECK:
                    break;
                case MSG_RELEASE_FPS_TIMEOUT:
                    releaseTargetFPSInternel(false, true);
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
                case MSG_CHECK_CUSTOM_SCROLLER:
                    // When timeout occurs, stop monitoring all scrollers
                    onCustomScrollerStoped();
                    break;
                default:
                    break;
            }
        }
    }


    public boolean checkCustomScroller(String contextKey, int action) {
        if (contextKey == null) {
            return false;
        }

        // Special handling for VERTICAL action
        if (action == BoostFwkManager.Scroll.VERTICAL) {
            return false;
        }

        synchronized (lock) {
            // Get or create state for this context
            ScrollerState state = mScrollerStates.get(contextKey);
            if (state == null) {
                state = new ScrollerState();
                mScrollerStates.put(contextKey, state);
            }

            // If not PREFILING action, it's an AOSP Scroller
            if (action != BoostFwkManager.Scroll.PREFILING) {
                // Mark as AOSP Scroller and keep the state
                state.hasMultipleActions = true;
                return false;
            }

            // If already identified as AOSP Scroller, return false
            if (state.hasMultipleActions) {
                return false;
            }

            // Record this action
            state.recordAction();

            // Only schedule timeout check if we've reached minimum action count
            if (state.isValidScroller()) {
                // Reset timeout check
                mWorkerHandler.removeMessages(WorkerHandler.MSG_CHECK_CUSTOM_SCROLLER);
                mWorkerHandler.sendEmptyMessageDelayed(WorkerHandler.MSG_CHECK_CUSTOM_SCROLLER,
                        CUSTOM_SCROLLER_CHECK_DELAY);

                if (LogUtil.DEBUG) {
                    LogUtil.mLogd(TAG, "User Custom Scroller ，contextKey = " + contextKey);
                }
            }
            return true;
        }
    }

    public void startCustomScroller(String contextKey) {
        if (contextKey == null) {
            if (LogUtil.DEBUG) {
                LogUtil.mLogw(TAG, "startCustomScroller: contextKey is null");
            }
            return;
        }

        synchronized (lock) {
            // Create new state for this context if it doesn't exist
            mScrollerStates.computeIfAbsent(contextKey, k -> new ScrollerState());
            mWorkerHandler.removeMessages(WorkerHandler.MSG_CHECK_CUSTOM_SCROLLER);

            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "Add custom scroller state for "+ contextKey);
            }
        }
    }

    public void stopCustomScroller(String contextKey) {
        synchronized (lock) {
            mScrollerStates.remove(contextKey);
            // Only remove message if no more states are being monitored
            if (mScrollerStates.isEmpty()) {
                mWorkerHandler.removeMessages(WorkerHandler.MSG_CHECK_CUSTOM_SCROLLER);
            }

            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "Removed custom scroller state for "+ contextKey);
            }
        }
    }

    public void clearAllScrollerObserver() {
        synchronized (lock) {
            mScrollerStates.clear();
            mWorkerHandler.removeMessages(WorkerHandler.MSG_CHECK_CUSTOM_SCROLLER);

            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "Removed all custom scroller state");
            }
        }
    }

    private void onCustomScrollerStoped() {
        switchToFPSGo(false);
        synchronized (lock) {
            mScrollerStates.clear();
            mWorkerHandler.removeMessages(WorkerHandler.MSG_CHECK_CUSTOM_SCROLLER);
            mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_FLING_POLICY_FLAG_END, null);
        }

        if (mFlingPolicyExeCount > 0) {
            mFlingPolicyExeCount = 0;
            enableFPSGo(false, scrollingFingStep);
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "User Custom Scroll End");
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
        } else {
            mWorkerHandler.removeMessages(WorkerHandler.MSG_CHECK_CUSTOM_SCROLLER);
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
        if (null == mPerformance) {
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

                if (ScrollState.isScrolling()) {
                    ScrollState.setScrolling(false, "new touch down");
                    notifyScrollingType(false);
                }
                ScrollState.onScrollWhenMove(true);
                enableScrollingMovingCMD(true);
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
            enableScrollingMovingCMD(false);
            mPolicyExeCount = 0;
            mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_POLICY_FLAG_END, null);
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
        if (null == mPerformance) {
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
                if (velocity > 0 && Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4
                        && !mActivityInfo.checkSpecialToken()) {
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
                    android.os.Process.myPid(), SBE_RUNNING_CHECK, 1);
                mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_WEBVIEW_CHECK, null);
                //delay 100ms query result
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(WorkerHandler.MSG_SBE_WEBVIEW_CHECK, null),
                        RUNNING_CHECK_DELAY_TIME);
                break;
            case ActivityInfo.PAGE_TYPE_FLUTTER: // check if flutter is running
                getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.FLUTTER_RENDER_TASKS,
                    mActivityInfo.getFlutterRenderTask(), mActivityInfo.getFlutterRenderTaskNum(),
                    android.os.Process.myPid(), SBE_RUNNING_CHECK, 1);
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
                int result = getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                        TasksUtil.WEBVIEW_TASKS, 5, android.os.Process.myPid(),
                        SBE_RUNNING_QUERY, 1);

                //low 16bit for running status
                //high 16bit for hwui render loading
                running = result & 0xFFFF;
                mActivityInfo.updatePageRenderLoading((result >> 16) & 0xFFFF);

                // clear running record
                getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                    TasksUtil.WEBVIEW_TASKS, 5,
                    android.os.Process.myPid(), SBE_RUNNING_CHECK, 0);

                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, "webview running->" + running);
                }

                mActivityInfo.updateSpecialPageTypeIfNecessary(
                    running == SBE_TASK_RUNNING ? true : false,
                        ActivityInfo.PAGE_TYPE_WEBVIEW);

                if (running == SBE_TASK_RUNNING) {
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
                    android.os.Process.myPid(), SBE_RUNNING_QUERY, 1);
                running = running & 0xFFFF;

                getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.FLUTTER_RENDER_TASKS,
                    mActivityInfo.getFlutterRenderTask(),
                    mActivityInfo.getFlutterRenderTaskNum(),
                    android.os.Process.myPid(), SBE_RUNNING_CHECK, 0);

                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, "flutter running->" + running
                        + " task->" + mActivityInfo.getFlutterRenderTask());
                }

                mActivityInfo.updateSpecialPageTypeIfNecessary(
                    running == SBE_TASK_RUNNING ? true : false,
                        ActivityInfo.PAGE_TYPE_FLUTTER);

                if (running == SBE_TASK_RUNNING) {
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
                getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                        TasksUtil.WEBVIEW_TASKS, 5, android.os.Process.myPid(),
                        SBE_RUNNING_CHECK, 1);
                //delay 100ms query result
                mWorkerHandler.sendMessageDelayed(
                    mWorkerHandler.obtainMessage(
                            WorkerHandler.MSG_SBE_GAME_MODE_ON_WEBVIEW_CHECK, step, -1),
                    mGameModeCheckTime);
            } else {
                releaseFPSGOControl(false, step);
            }
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
        int running = getPowerHalWrapper().mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME,
                TasksUtil.WEBVIEW_TASKS, 5,
                android.os.Process.myPid(), SBE_RUNNING_QUERY, 1);
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "game mode checking on webview running->" + running);
        }

        if (running == SBE_TASK_RUNNING) {
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
            //In order to reduce the binder transaction with powerhalservice,
            //lead to fpsgo start control too late
            //delay 50ms release fpsgo, if next start fpsgo come soon,
            //MSG_SBE_DELAY_RELEASE_FPSGO will be remove
            //Why 50ms? ---> from the 120hz trace, the worse case,
            //fpsgo control start after 6 frames
            mWorkerHandler.sendMessageDelayed(
                mWorkerHandler.obtainMessage(
                    WorkerHandler.MSG_SBE_DELAY_RELEASE_FPSGO, null), mDelayReleaseFpsTime);
            waitingForReleaseFpsgoStep = step;
        }
        waitingForReleaseFpsgo = enable;
    }

    private void releaseFPSGOControl(boolean isBegin, int step) {
        releaseFPSGOControl(isBegin, step, true);
    }

    // Enable/disable FPSGO control
    private void releaseFPSGOControl(boolean isBegin, int step, boolean scrolling) {
        int renderThreadTid = getRenderThreadTid();
        String log;
        if (isBegin) {
            delayControlFpsgo(sFLING_VERTICAL, false);
            mLastScrollTimeMS = System.currentTimeMillis();
            enableScrollingCommonCMD(true);
            log = "start " + step;
        } else {
            enableScrollingCommonCMD(false);
            log = "end " + step;
        }
        fpsgoCtrlPagesIfNeed(isBegin);

        if (scrolling) {
            boolean scrollStateUpdated = ScrollState.setScrolling(isBegin, log);
            if (scrollStateUpdated) {
                notifyScrollingType(isBegin);
            }
        }
    }

    private void notifyScrollingType(boolean isBegin) {
        if (mActivityInfo == null) {
            return;
        }

        int mask = SBE_SCROLL_STATE_SCROLLING;

        //only AOSP page vertical scrolling hint fpsgo HWUI,
        //then enable dynamic rescue
        if (!ScrollState.isStatus(ScrollState.SCROLL_STATUS_HORIZONTAL)
                && getRenderThreadTid() > 0) {
            mask |= (ScrollState.isScrollingWhenInput() ?
                    SBE_SCROLL_STATE_MOVING : SBE_SCROLL_STATE_FLING) | SBE_PAGE_HWUI;
        }

        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW)) {
            mask |= SBE_PAGE_WEBVIEW;
        } else if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_FLUTTER)) {
            mask |= SBE_PAGE_FLUTTER;
        } else if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW_APPBRAND)) {
            mask |= (SBE_PAGE_APPBRAND | SBE_DISABLE_DPT);
        }

        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_MULTI_WINDOW)) {
            mask |= SBE_PAGE_MULTI_WINDOW;
        }

        if (mActivityInfo.isSystemAPP()
                || mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_FULL_SCREEN)) {
            mask |= SBE_DISABLE_DPT;
        }

        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_TRAVERSAL_HEAVY)) {
            mask |= SBE_AFFNITY_TASK;
        }

        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY)) {
            mask |= SBE_PAGE_NO_ACTIVITY;
        }

        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, " NOTFIY Scrolling"
                    + getRenderThreadTid() + " " + isBegin + " " + mask);
        }

        int num = 2;
        String tasks = mActivityInfo.getTaskTids();
        if (tasks == null) {
            num = 0;
            tasks = "";
        }
        //notify fpsgo scrolling info
        getPowerHalWrapper().mtkSbeSetWebviewPolicy("RenderThread", tasks, num,
                android.os.Process.myPid(), mask, isBegin ? 1 : 0);
        LogUtil.traceEnd();
    }

    private void fpsgoCtrlPagesIfNeed(boolean isBegin) {
        if (Config.enableScrollFloor) {
            return;
        }

        PowerHalWrapper powerHalWrapper = getPowerHalWrapper();

        if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4) {
            if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW)) {
                int mask = SBE_PAGE_WEBVIEW | WEBVIEW_FLUTTER_FPSGO_CTRL_MASK;
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
                            LogUtil.traceAndMLogd(TAG, " try ctrl render->" + render
                                    + " mWebviewCriticalTaskLen->" + mWebviewCriticalTaskLen);
                        }
                        powerHalWrapper.mtkSbeSetWebviewPolicy(render.getRenderName(),
                                TasksUtil.WEBVIEW_CRITICAL_TASKS, mWebviewCriticalTaskLen,
                                render.getRenderPid(), mask,
                                isBegin ? 1 : 0);
                    }
                }

                powerHalWrapper.mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME, "", 0,
                        android.os.Process.myPid(), mask,
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
                    powerHalWrapper.mtkSbeSetWebviewPolicy(TasksUtil.BLINK_WEBVIEW_RENDER_NAME,
                            "", 0, mWebviewRenderPid, WEBVIEW_FLUTTER_FPSGO_CTRL_MASK,
                            isBegin ? 1 : 0);
                }
                powerHalWrapper.mtkSbeSetWebviewPolicy(TasksUtil.WEB_RENDER_NAME, "", 0,
                        android.os.Process.myPid(), WEBVIEW_FLUTTER_FPSGO_CTRL_MASK,
                        isBegin ? 1 : 0);
                LogUtil.traceEnd();
            }
        }

        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_FLUTTER)) {
            List<RenderInfo> renders =
                mActivityInfo.getRendersByPageType(ActivityInfo.PAGE_TYPE_FLUTTER);
            int mask = SBE_PAGE_FLUTTER | WEBVIEW_FLUTTER_FPSGO_CTRL_MASK;
            if (LogUtil.DEBUG) {
                LogUtil.traceBeginAndMLogd(TAG, " PAGE_TYPE_FLUTTER "
                    + android.os.Process.myPid() + " " + isBegin);
            }
            for (int i = 0; i < TasksUtil.FLUTTER_RENDER_TASK.length; i++) {
                powerHalWrapper.mtkSbeSetWebviewPolicy(TasksUtil.FLUTTER_RENDER_TASK[i],
                        TasksUtil.FLUTTER_TASKS, 1, android.os.Process.myPid(),
                        mask, isBegin ? 1 : 0);
            }
            //render find dynamic
            if (renders != null && renders.size() > 0) {
                for (RenderInfo render : renders) {
                    if (LogUtil.DEBUG) {
                        LogUtil.traceAndMLogd(TAG, " try ctrl render->" + render);
                    }
                    powerHalWrapper.mtkSbeSetWebviewPolicy(render.getRenderName(),
                            render.getKnownDepNameStr(), render.getKnownDepSize(),
                            android.os.Process.myPid(), mask,
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
            powerHalWrapper.mtkSbeSetWebviewPolicy(mActivityInfo.mGLTaskName, "", 0,
                    android.os.Process.myPid(), MAP_FPSGO_CTRL_MASK, isBegin ? 1 : 0);

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
                        powerHalWrapper.mtkSbeSetSBB(tid, 1, 70);
                    } else {
                        powerHalWrapper.mtkSbeSetSBB(tid, 0, 0);
                    }
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
                    powerHalWrapper.mtkSbeSetWebviewPolicy(render.getRenderName(),
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
    public void releaseTargetFPS(boolean release, boolean scrolling) {
        if (mDisableScrollPolicy) {
            LogUtil.traceAndMLogd(TAG, "releaseTargetFPS scroll policy has been disable");
            return;
        }
        int renderThreadTid = getRenderThreadTid();
        if (renderThreadTid == ActivityInfo.NON_RENDER_THREAD_TID) {
            LogUtil.mLogw(TAG, "cannot found render thread");
            return;
        }

        if (release) {
            mWorkerHandler.sendMessage(
                mWorkerHandler.obtainMessage(
                        WorkerHandler.MSG_RELEASE_BEGIN, scrolling));
        } else {
            mWorkerHandler.sendMessage(
                mWorkerHandler.obtainMessage(
                        WorkerHandler.MSG_RELEASE_END, scrolling));
        }
    }

    private void releaseTargetFPSInternel(boolean isBegin, boolean scrolling) {
        String logStr = "release Target FPS";
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, logStr + (isBegin ? "start " : "stop ") + useFPSGo);
        }
        mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_DISABLE_FPSGO_COUNT_DOWN);
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
            releaseFPSGOControl(isBegin, sFLING_VERTICAL, scrolling);
        }
        LogUtil.traceEnd();
    }

    private void scrollCommonPolicyCheck() {
        //check last scroll over release duration or not
        if (mLastScrollTimeMS > 0
                && (Config.getVerticalScrollDuration()) >
                    (System.currentTimeMillis() - mLastScrollTimeMS)) {
            enableScrollingCommonCMD(true);
        } else if (mCommonPolicyEnabled) {
            enableScrollingCommonCMD(false);
        }
    }

    private void resetScrollPolicyStatus(Context c) {
        //reset fling state
        resetFlingState(c);

        //pause but still scrolling
        if (ScrollState.isScrolling()) {
            ScrollState.setScrolling(false, "page pause");
            notifyScrollingType(false);
        }

        //reset fpsgo scroll state
        if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_3
                && getRenderThreadTid() > 0 && mPowerHalWrap != null) {
            if (LogUtil.DEBUG) {
                LogUtil.traceBeginAndMLogd(TAG, " notify fpsgo reset status");
            }
            mPowerHalWrap.mtkSbeSetWebviewPolicy("RenderThread", "", 0,
                        android.os.Process.myPid(), SBE_CLEAR_SCROLLING_INFO, 1);
            LogUtil.traceEnd();
        }
    }

    private void resetScrollRenders(Context c) {
        if (mPowerHalWrap != null) {
            if (LogUtil.DEBUG) {
                LogUtil.traceBeginAndMLogd(TAG, " notify fpsgo reset renders");
            }
            mPowerHalWrap.mtkSbeSetWebviewPolicy("RenderThread", "", 0,
                        android.os.Process.myPid(), SBE_CLEAR_RENDERS, 1);
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
        if (null != mPerformance) {
            mPowerHandle = mPerformance.perfLockAcquire(
                    Config.getVerticalScrollDuration(), resList);
            mPerformance.perfLockRelease(mPowerHandle);
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

    private void disableTouchBoost() {
        if (Config.isInterruptTouchBoost() == Config.FEATURE_OPTION_OFF) {
            return;
        }
        // after android W interruptTouchBoost in scntbl common policy
        // int perf_lock_rsc[] = new int[] {PERF_RES_POWERHAL_TOUCH_BOOST_ENABLE, 0};
        // perfLockAcquire(perf_lock_rsc);

        if (Config.getBoostFwkVersion() >=  Config.BOOST_FWK_VERSION_3) {
            if (null != mPowerHalService) {
                mPowerHalService.perfLockRelease(mTouchBoostPWRHandle);
                mTouchBoostPWRHandle = -1;
            }
        }
    }

    private void enableScrollingCommonCMD(boolean enable) {
        if (!ENABLE_SCROLL_COMMON_POLICY
                || Config.getScollPolicyType() == Config.MTK_SCROLL_POLICY_MODE_EAS) {
            return;
        }

        mCommonPolicyEnabled = enable;
        if (enable) {
            long systemTime = System.currentTimeMillis();
            //filter 10ms app call
            if (mCheckCommonPolicyTime > 0 && (systemTime - mCheckCommonPolicyTime) < 10) {
                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, " filter app call in 10ms" + mCheckCommonPolicyTime);
                }
                return;
            }
            mCheckCommonPolicyTime = systemTime;
            mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_SCROLL_COMMON_POLICY_COUNT_DOWN);

            if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4) {
                mLastScrollCommonCMD = getScrollCommonCMD();
            }
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG,
                        "Enable MTKPOWER_HINT_UX_SCROLLING_COMMON cmd=" + mLastScrollCommonCMD);
            }
            if (mCommonPolicyPWRHandle != -1) {
                mPowerHalService.perfLockRelease(mCommonPolicyPWRHandle);
            }
            mCommonPolicyPWRHandle = mPowerHalService.perfCusLockHint(mLastScrollCommonCMD,
                    Config.getVerticalScrollDuration());
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
            mCheckCommonPolicyTime = 0;
            mWorkerHandler.removeMessages(WorkerHandler.MSG_SBE_SCROLL_COMMON_POLICY_COUNT_DOWN);

            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG,
                        "Disable MTKPOWER_HINT_UX_SCROLLING_COMMON cmd=" + mLastScrollCommonCMD);
            }
            mPowerHalService.perfLockRelease(mCommonPolicyPWRHandle);
            mCommonPolicyPWRHandle = -1;
        }
    }

    private int getScrollCommonCMD() {
        int cmd = MTKPOWER_HINT_UX_SCROLLING_NORMAL_MODE;
        if (mActivityInfo == null) {
            return cmd;
        }

        //update loading from pagetype and render
        int loading = ActivityInfo.SBE_PAGE_LOADING_M;
        if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_FLUTTER)) {
            loading = ActivityInfo.SBE_PAGE_LOADING_P;
        } else if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW)
                || mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW_APPBRAND)
                || mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_DESIGN_DECODE)) {
            loading = ActivityInfo.SBE_PAGE_LOADING_H;
        } else if (mActivityInfo.isAOSPPageDesign()) {
            loading = ActivityInfo.SBE_PAGE_LOADING_L;
        }

        int loadingRender = mActivityInfo.getRenderLoading();
        if (loadingRender == ActivityInfo.SBE_PAGE_LOADING_P) {
            loading = loadingRender;
        }

        switch (loading) {
            case ActivityInfo.SBE_PAGE_LOADING_P:
                cmd = MTKPOWER_HINT_UX_SCROLLING_PERF_MODE;
                break;
            case ActivityInfo.SBE_PAGE_LOADING_H:
            case ActivityInfo.SBE_PAGE_LOADING_M:
                cmd = MTKPOWER_HINT_UX_SCROLLING_BALANCE_MODE;
                break;
            case ActivityInfo.SBE_PAGE_LOADING_L:
                cmd = MTKPOWER_HINT_UX_SCROLLING_NORMAL_MODE;
                break;
            default:
                break;
        }

        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "final scroll cmd=" + cmd
                    + " loadingRender=" + loadingRender + " loading=" + loading);
        }
        return cmd;
    }

    private void enableScrollingMovingCMD(boolean enable) {
        if (Config.getDisableTouchMovingMode()) {
            return;
        }
        if (enable) {
            if (mMovingPolicyPWRHandle != -1) {
                mPowerHalService.perfLockRelease(mMovingPolicyPWRHandle);
            }
            mMovingPolicyPWRHandle = mPowerHalService.perfCusLockHint(
                    MTKPOWER_HINT_UX_SCROLLING_MOVING_MODE, MOVING_MODE_DURATION);
        } else {
            mPowerHalService.perfLockRelease(mMovingPolicyPWRHandle);
            mMovingPolicyPWRHandle = -1;
        }
    }

    private void checkTouchMoveBoostIfNeeded() {
        if (null != mPowerHalService) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "Touch Move Boost : "
                    + Config.TOUCH_MOVE_BOOST_DURATION);
            }
            mTouchBoostPWRHandle = mPowerHalService.perfCusLockHint(MTKPOWER_HINT_UX_TOUCH_MOVE,
                Config.TOUCH_MOVE_BOOST_DURATION);
        }
    }
}