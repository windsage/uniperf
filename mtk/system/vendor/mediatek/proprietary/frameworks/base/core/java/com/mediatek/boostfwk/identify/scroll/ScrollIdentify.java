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

package com.mediatek.boostfwk.identify.scroll;

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.os.Trace;

import com.android.internal.os.BackgroundThread;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.identify.BaseIdentify;
import com.mediatek.boostfwk.info.ScrollState;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.utils.TasksUtil;
import com.mediatek.boostfwk.utils.Util;
import com.mediatek.boostfwk.policy.scroll.ScrollPolicy;
import com.mediatek.boostfwk.policy.touch.TouchPolicy;
import com.mediatek.boostfwk.policy.frame.FramePolicy;
import com.mediatek.boostfwk.policy.frame.TranScrollingFramePrefetcher;
import com.mediatek.boostfwk.policy.frame.ScrollingFramePrefetcher;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.scroll.ScrollScenario;
//T-HUB Core[SPD]:add for increase draw threads priority by wei.kang 20230627 start
import android.app.ActivityTaskManager;
//T-HUB Core[SPD]:add for increase draw threads priority by wei.kang 20230627 end

import java.util.ArrayList;
import java.util.List;

/** @hide */
public class ScrollIdentify extends BaseIdentify{

    private static final String TAG = "ScrollIdentify";

    private static volatile ScrollIdentify sInstance = null;
    private static Object lock = new Object();
    private static final Object SCROLL_LOCK = new Object();
    private Object scrollerLock = null;

    private boolean mIsInput = false;
    private String mDispatcherPkgName = null;
    private boolean mIsSystemApp = false;
    private String mInputPkgName = "";
    private float mRefreshRate = 0;
    private long mFrameIntervalMs = 0;
    private long mLimitVsyncTime = 0;

    protected float mMoveDistanceThreshold = Config.getMoveThreshold();
    protected final float FLING_DISTANCE_VERTICAL_DP = 70;
    //ViewPager is 16dp, but for boost don`t need such sensitive
    protected final int FLING_DISTANCE_HORIZONTAL_DP = 48;
    protected final int FLING_SPEED_VERTICAL_DP = 350;
    //Refer to ViewPager
    protected final int FLING_SPEED_HORIZONTAL_DP = 400;
    //should be FLING_SPEED_HORIZONTAL_DP * density
    private float minVelocityHorizontal = -1;
    private float minVelocityVertical = -1;
    private float minTouchDistanceHorizontal = -1;
    private float minTouchDistanceVertical = -1;
    private GestureDetector mGestureDetector;
    // Finger input check flag
    private boolean mHaveMoveEvent = false;
    // Check is input step or not
    private boolean mIsInputLockAcquired = false;
    // Check is scrolling step or not
    private boolean mIsScrollLockAcquired = false;
    private boolean mIsScrolling = false;
    private boolean mIsUserTouched = false;
    private boolean mLastScrollerEnd = false;

    // APP scrolling not init done, never check is special or not
    public static final int NO_CHECKED_STATUS = -1;
    // Special app design, not use android AOSP draw flow, such as Flutter
    private static int mIsSpecialPageDesign = NO_CHECKED_STATUS;
    private static final int SCROLL_TYPE_SCROLLER = -2;

    private int mApplicationType = NO_CHECKED_STATUS;
    private final int APP_TYPE_GAME = 1;
    private final int APP_TYPE_READER = 2;
    private final int APP_TYPE_MAP = 3;
    private final int APP_TYPE_SYSTEM = 4;
    private final int APP_TYPE_STRICT_MODE = 5;
    private final int APP_TYPE_NORMAL = 6;

    private ActivityInfo mActivityInfo = null;
    private TouchPolicy mTouchPolicy = null;

    private MotionEvent mCurrentMotionEvent = null;

    //display rate == 60hz
    private static final float DISPLAY_RATE_60 = 60f;
    // Is enable swtich FPSGO
    private static final boolean sAUTO_SWITCH_FPSGO = true;

    private ActivityInfo.ActivityChangeListener activityChangeListener = null;
    private List<TouchEventListener> mTouchEventListeners = new ArrayList<>();
    private List<ScrollEventListener> mScrollEventListeners = new ArrayList<>();

    private static class ActivityListener implements ActivityInfo.ActivityChangeListener {
        @Override
        public void onChange(Context c) {
        }

        @Override
        public void onActivityPaused(Context c) {
            if (sInstance != null) {
                sInstance.scrollerLock = null;
            }
        }
    }

    public static ScrollIdentify getInstance() {
        if (null == sInstance) {
            synchronized (ScrollIdentify.class) {
                if (null == sInstance) {
                    sInstance = new ScrollIdentify();
                }
            }
        }
        return sInstance;
    }

    private ScrollIdentify() {

    }

    /**
     * Dispatcher the ux scroll refer actions to do.
     *
     * @param scenario ScrollScenario obj
     */
    @Override
    public boolean dispatchScenario(BasicScenario basicScenario) {
        if (Config.isBoostFwkScrollIdentify() && basicScenario != null) {
            ScrollScenario scenario = (ScrollScenario)basicScenario;
            int action = scenario.getScenarioAction();
            int status = scenario.getBoostStatus();
            MotionEvent event = scenario.getScenarioInputEvent();
            Object object = scenario.getScenarioObj();
            String packageName = null;
            Context viewContext = null;

            if (scenario.getScenarioContext() != null) {
                viewContext = scenario.getScenarioContext();
            }
            if (viewContext == null) {
                return false;
            }

            if (mActivityInfo == null) {
                mActivityInfo =  ActivityInfo.getInstance();
            }
            //Using activity packagename first
            packageName = mActivityInfo.getPackageName();
            if (packageName == null) {
                packageName = viewContext.getPackageName();
            }

            if (packageName == null) {
                return false;
            }

            //packageName init or changed
            if (mDispatcherPkgName == null || !mDispatcherPkgName.equals(packageName)) {
                mApplicationType = NO_CHECKED_STATUS;
            }

            if (mApplicationType == NO_CHECKED_STATUS) {
                checkAppType(packageName);
            }

            if (mApplicationType == APP_TYPE_GAME
                    || mApplicationType == APP_TYPE_SYSTEM
                    || mApplicationType == APP_TYPE_STRICT_MODE) {
                mDispatcherPkgName = packageName;
                return false;
            }

            // Create scroll GestureDetector for input event
            if (mGestureDetector == null) {
                try {
                    mGestureDetector = new GestureDetector(
                            viewContext, new ScrollGestureListener());
                } catch (Exception e) {
                    if (Config.isBoostFwkLogEnable()) {
                        LogUtil.mLoge(TAG, "layout not inflate, cannot create GestureDetector,"
                                + "try to next time.");
                    }
                    mGestureDetector = null;
                    return false;
                }
            }

            if (activityChangeListener == null) {
                activityChangeListener = new ActivityListener();
                mActivityInfo.registerActivityListener(activityChangeListener);
            }

            // Update refresh rate when scroll
            updateRefreshRate();
            mDispatcherPkgName = packageName;

            if (Config.isBoostFwkLogEnable()) {
                LogUtil.mLogd(TAG, packageName +  ", Scroll action dispatcher to = "
                        + action + " status = " + status + ", viewContext = " + viewContext);
            }
            //sync sfp state to scenario
            scenario.setSFPEnable(ScrollingFramePrefetcher.PRE_ANIM_ENABLE);

            switch(action) {
                case BoostFwkManager.Scroll.INPUT_EVENT:
                    if (event != null) {
                        inputEventCheck(status, event);
                    }
                    break;
                case BoostFwkManager.Scroll.PREFILING:
                    inputDrawCheck(status, packageName);
                    // Notify scroll info to M-ProMotion
                    notifyScrollEvent();
                    break;
                case BoostFwkManager.Scroll.VERTICAL:
                    inertialScrollCheck(status, packageName, object);
                    break;
                case BoostFwkManager.Scroll.SCROLLER:
                    if (mHaveMoveEvent) {
                        if (Config.isBoostFwkLogEnable()) {
                            LogUtil.mLogd(TAG, "using scroller when HORIZONTAL scroll"
                                + " Duration = " + scenario.getDuration());
                        }
                        // Some app used Scroller when horizontal, such as Tiktok.
                        // We will stop SBE policy and use FPSGO to control.
                        if (Config.getBoostFwkVersion() < Config.BOOST_FWK_VERSION_4) {
                            inertialScrollCheck(SCROLL_TYPE_SCROLLER, packageName, object);
                        }
                        ScrollPolicy.getInstance().setScrollerDuration(scenario.getDuration());
                    }
                    break;
                default:
                LogUtil.mLogw(TAG, "Not found dispatcher scroll action.");
                    break;
            }
            return true;
        }
        return false;
    }

    private void updateRefreshRate() {
        float refreshRate = ScrollState.getRefreshRate();
        if (refreshRate == mRefreshRate) {
            return;
        }
        mRefreshRate = refreshRate;
        mFrameIntervalMs = (long) (1000 / mRefreshRate);
    }

    /**
     * For special display rate, we only check frame time
     */
    public boolean disableForSpecialRate() {
        boolean result = Config.isBoostFwkScrollIdentifyOn60hz()
                && mRefreshRate == DISPLAY_RATE_60;
        if (Config.isBoostFwkLogEnable() && result) {
            LogUtil.mLogd(TAG, "filter specila rate when scrolling: " + mRefreshRate);
        }
        return result;
    }

    class ScrollGestureListener extends SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
            if ((e1 == null) || (e2 == null)) {
                return false;
            }

            if (mActivityInfo == null) {
                mActivityInfo =  ActivityInfo.getInstance();
            }
            if (mMoveDistanceThreshold <= 0) {
                // Use the threshold value from ViewConfiguration
                mMoveDistanceThreshold = mActivityInfo.getScaledTouchSlop();
                if (Config.isBoostFwkLogEnable()) {
                    LogUtil.mLogd(TAG, "mMoveDistanceThreshold = " + mMoveDistanceThreshold);
                }
            }

            float eventDistancX = Math.abs(e1.getX() - e2.getX());
            float eventDistancY = Math.abs(e1.getY() - e2.getY());
            if ((eventDistancX > mMoveDistanceThreshold)
                    || eventDistancY > mMoveDistanceThreshold) {
                checkSpecialPageType();
                if (Config.isBoostFwkLogEnable()) {
                    LogUtil.mLogd(TAG, "mIsSpecialPageDesign = " + mIsSpecialPageDesign);
                }
                if (mActivityInfo.isPage(
                        ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_FULLSCREEN_GLTHREAD)) {
                    return false;
                }
                if (Config.isBoostFwkLogEnable()) {
                    LogUtil.mLogd(TAG, "onScroll - "
                        + (eventDistancX > eventDistancY ? "horizontal" : "vertical"));
                }
                mHaveMoveEvent = true;
                sbeHint(ScrollPolicy.sFINGER_MOVE, "Boost when move");
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                float velocityX, float velocityY) {
            if ((e1 == null) || (e2 == null)) {
                return false;
            }
            if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_FULLSCREEN_GLTHREAD)) {
                return false;
            }

            float distanceX = Math.abs(e2.getX() - e1.getX());
            float distanceY = Math.abs(e2.getY() - e1.getY());
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.mLogd(TAG, "onFling --> distanceX: " + distanceX
                    + " Math.abs(velocityX):" + Math.abs(velocityX)
                    + " distanceY: " + distanceY
                    + " Math.abs(velocityY): " + Math.abs(velocityY)
                    + " minimumFlingVelocity:"+ mActivityInfo.getMinimumFlingVelocity());
            }
            LogUtil.mLogi(TAG, "on fling");

            initMinValuesIfNeeded();

            //horizontal scrolling is refer to ViewPager.java
            if (Math.abs(velocityX) > mActivityInfo.getMinimumFlingVelocity()
                    && (distanceX * 0.5f) > distanceY) {
                ScrollState.setHorizontal();
                sbeHint(ScrollPolicy.sFLING_HORIZONTAL, Math.abs(velocityX),
                    "onFling Boost: HORIZONTAL");
            } else if (Math.abs(velocityY)> mActivityInfo.getMinimumFlingVelocity()) {
                ScrollState.setVertical();
                sbeHint(ScrollPolicy.sFLING_VERTICAL, Math.abs(velocityY),
                    "onFling Boost: VERTICAL");
            }
            return true;
        }
    }

    private void initMinValuesIfNeeded() {
        if (minVelocityHorizontal == -1 || minTouchDistanceHorizontal == -1
                || minTouchDistanceVertical == -1 || minVelocityVertical == -1) {
            float density = mActivityInfo.getDensity();
            minVelocityHorizontal = density > 0 ? (FLING_SPEED_HORIZONTAL_DP * density) : 1200;
            minTouchDistanceHorizontal
                    = density > 0 ? (FLING_DISTANCE_HORIZONTAL_DP * density) : 150;
            minVelocityVertical = density > 0 ? (FLING_SPEED_VERTICAL_DP * density) : 1000;
            minTouchDistanceVertical = density > 0 ? (FLING_DISTANCE_VERTICAL_DP * density) : 200;
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.mLogd(TAG, "onFling density=" + density + " minTouchDistanceVertical="
                        + minTouchDistanceVertical + " minVelocityVertical=" + minVelocityVertical
                        + " minVelocityHorizontal="+minVelocityHorizontal
                        + "minTouchDistanceHorizontal=" + minTouchDistanceHorizontal);
            }
        }
    }

    /**
     * Check input event action status, if the flinger has move, user will begin scroll,
     * if flinger has up, confrim this is scroll by user.
     *
     * @param action The input event actions
     */

    private void inputEventCheck(int status, MotionEvent motionEvent) {
        boolean begin = boostBeginEndCheck(status);
        if (begin) {
            //only copy event when hint begin
            //incase application modify event type
            mCurrentMotionEvent = motionEvent.copy();
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mIsUserTouched = true;
                // only hint touch down now
                for (TouchEventListener touchEventListener : mTouchEventListeners) {
                    touchEventListener.preTouchEvent(mCurrentMotionEvent);
                }
            }
        } else {
            //if event type changed to ACTION_CANCEL by application,
            //use mCurrentMotionEvent we saved at begin.
            MotionEvent event = motionEvent;
            int action = event.getActionMasked();
            if (mCurrentMotionEvent != null) {
                int actionCur = mCurrentMotionEvent.getActionMasked();
                if (action != actionCur && action == MotionEvent.ACTION_CANCEL) {
                    event = mCurrentMotionEvent;
                    action = actionCur;
                }
            }
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mLastScrollerEnd = false;
                    if (mIsScrollLockAcquired && scrollerLock != null) {
                        //force reset scroller flags, when action down,
                        //incase scroller timing issue lead to no ctrl
                        scrollerLock = null;
                        mIsScrollLockAcquired = false;
                    }
                    mIsUserTouched = true;
                    if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                        //record last down time
                        ScrollPolicy.getInstance().scrollHint(ScrollPolicy.FINGER_DOWN, -1);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (Config.isBoostFwkLogEnable()) {
                        LogUtil.mLogd(TAG, "touch up/cancel ");
                    }
                    if (mHaveMoveEvent) {
                        mHaveMoveEvent = false;
                        sbeHint(ScrollPolicy.sFINGER_UP, "Boost when up/cancel " + action);
                    }
                    mIsInputLockAcquired = false;
                    if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                        //some App could not enable move boost,
                        //so fling policy do not end, check if should end.
                        ScrollPolicy.getInstance()
                                .disableMTKScrollingFlingPolicyIfNeeded(true);
                    }
                    ScrollingFramePrefetcher.getInstance().disableAndLockSFP(false);
                    //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 start
                    TranScrollingFramePrefetcher.getInstance().disableAndLockSFP(false);
                    //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 end
                    break;
                default:
                    break;
            }

            if (mGestureDetector != null) {
                mGestureDetector.onTouchEvent(event);
            }

            if (mTouchPolicy == null) {
                mTouchPolicy = TouchPolicy.getInstance();
            }

            // Dispatch touch events to listeners
            for (TouchEventListener touchEventListener : mTouchEventListeners) {
                touchEventListener.onTouchEvent(event);
            }

            if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                //release event after up/cancel
                mCurrentMotionEvent = null;
            }
        }
    }

    /**
     * Set scrolling begin when correct input begin draw, if flinger has up,
     * confrim this is scroll by user.
     *
     * Only android AOSP draw flow will called this function.
     *
     * @param action The input event actions
     * @param pkgName The package name
     */
    private void inputDrawCheck(int action, String pkgName) {
        if (mLastScrollerEnd || (mHaveMoveEvent && !mIsInputLockAcquired)) {
            mLimitVsyncTime = mFrameIntervalMs >> 1;
            mIsInput = boostBeginEndCheck(action);
            mInputPkgName = pkgName;
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.traceAndMLogd(TAG, "Vendor::inputDrawCheck begin, pkgName = " + pkgName
                        + ", refresh rate = " + mRefreshRate
                        + ", mFrameIntervalMs = " + mFrameIntervalMs);
            }
            mIsInputLockAcquired = true;
            mIsUserTouched = true;
        }
    }

    /**
     * Check the scrolling step and set the step flag when inertial scroll.
     *
     * @param action The actions include ux scrolling begin or not.
     * @param pkgName The package name.
     * @param obj The Scroller object use to check which one to fling.
     */
    public void inertialScrollCheck(int action, String pkgName, Object obj) {
        //non-activity page, ctrl by gesture
        if (!mIsUserTouched || mActivityInfo.isPage(
                ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY)) {
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.traceAndMLogd(TAG, "inertialScrollCheck mIsUserTouched="
                        + mIsUserTouched + " mIsSpecialPageDesign="+mIsSpecialPageDesign);
            }
            return;
        }

        // Some APP using scoller to vertical scroll, we don't think it need to boost
        if (action == SCROLL_TYPE_SCROLLER) {
            if (sAUTO_SWITCH_FPSGO) {
                ScrollPolicy.getInstance().switchToFPSGo(true);
            }
            mIsScrollLockAcquired = false;
            return;
        }

        if (Config.isBoostFwkLogEnable()) {
            LogUtil.traceAndMLogd(TAG, "inertialScrollCheck action=" + action + " pkgName="+pkgName
                    + " obj=" + obj + " " + scrollerLock);
        }
        boolean shouldBoost = boostBeginEndCheck(action);
        //check the Scroller object to cover the multi Scroller to control.
        if (!checkScroller(shouldBoost, obj)) {
            return;
        }

        if (!mIsScrollLockAcquired && shouldBoost) {
            inertialScrollHint(true, pkgName);
            mIsScrollLockAcquired = true;
        } else if (mIsScrollLockAcquired && !shouldBoost) {
            inertialScrollHint(false, pkgName);
            mIsScrollLockAcquired = false;
        }
    }

    /**
     * Do hint when UX scrolling
     *
     * @param action The actions include ux scrolling begin or not.
     * @param pkgName The package name.
     */
    private void inertialScrollHint(boolean enable, String pkgName) {
        if (Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_3
                && !mIsInput && mInputPkgName.equals("") && !mInputPkgName.equals(pkgName)
                && mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_DESIGN)) {
            resetInputFlag(true);
            return;
        }

        if (sAUTO_SWITCH_FPSGO) {
            ScrollPolicy.getInstance().switchToFPSGo(enable);
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "update state to " + (enable ? "fling" : "finish"));
        }
        ScrollState.setFling(enable);
        ScrollState.setVertical();
        //add for notify fling state by wei.kang 20241021 start
        ActivityTaskManager.getInstance().setFlingState(enable);
        //add for notify fling state by wei.kang 20241021 end
        //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 start
        TranScrollingFramePrefetcher.getInstance().setFling(enable);
        ScrollPolicy.getInstance().setFling(enable);
        //T-HUB Core[SPD]:added for frame prefetcher by song.tang 20240118 end
        ScrollingFramePrefetcher.getInstance().setFling(enable);
        ScrollPolicy.getInstance().releaseTargetFPS(enable);
        resetInputFlag(!enable);
        ActivityInfo.getInstance().setFling(enable);
    }

    private void resetInputFlag(boolean reset) {
        //reset status when fling end
        if (reset) {
            mIsInput = false;
            mInputPkgName = "";
            mIsUserTouched = false;
        }
        mLastScrollerEnd = reset;
    }

    private boolean boostBeginEndCheck(int action) {
        boolean isBegin = false;
        switch(action) {
            case BoostFwkManager.BOOST_BEGIN:
                isBegin = true;
                break;
            case BoostFwkManager.BOOST_END:
                isBegin = false;
                break;
            default:
                LogUtil.mLoge(TAG, "Unknown define action inputed, exit now.");
                break;
        }
        return isBegin;
    }

    /**
     * Check special app design, some app design cannot use Android draw flow, such as Flutter.
     *
     */
    private void checkSpecialPageType() {
        mIsSpecialPageDesign = mActivityInfo.getPageType();
        if (((mActivityInfo.isAOSPPageDesign()
                || mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY))
                && Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_3)
                || mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN)
                || mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_DESIGN_COMPOSE)
                || mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_WEBVIEW_60FPS)
                || mActivityInfo.isPage(
                    ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_FULLSCREEN_GLTHREAD)) {
            mIsInputLockAcquired = true;
        }
    }

    private void checkAppType(String packageName) {
        String log = "APP_TYPE_NORMAL";
        if (TasksUtil.isAPPInStrictMode(packageName)) {
            mApplicationType = APP_TYPE_STRICT_MODE;
            log = "APP_TYPE_STRICT_MODE";
        }
        //SPD: add for support system app fling boost by song.tang 20240619 start
        else if (checkSystemAPP(packageName)) {
            mApplicationType = APP_TYPE_NORMAL;
            log = "APP_TYPE_NORMAL";
        }
        //SPD: add for support system app fling boost by song.tang 20240619 end
        else if (TasksUtil.isGameAPP(packageName)
                && !Config.GAME_FILTER_LIST.contains(packageName)) {
            mApplicationType = APP_TYPE_GAME;
            log = "APP_TYPE_GAME";
        } else if (isSystemApp(packageName)) {
            mApplicationType = APP_TYPE_SYSTEM;
            log = "APP_TYPE_SYSTEM";
        }
        /* else if (Util.isReaderApp(mDispatcherPkgName)) {
            applicationType = APP_TYPE_READER;
            log = "APP_TYPE_READER";
        }  */
        else {
            mApplicationType = APP_TYPE_NORMAL;
        }
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "onScroll -- "+log);
        }
    }

    private boolean isSystemApp(String packageName) {
        if (Config.getBoostFwkVersion() > 1) {
            //only filter list system app since 2.0
            return Config.SYSTEM_PACKAGE_ARRAY.contains(packageName)
                    || "system_server".equals(android.os.Process.myProcessName());
        } else {
            return packageName.contains("android")
                    && checkSystemAPP(packageName);
        }
    }

    private boolean checkScroller(boolean shouldBoost, Object obj) {
        if (obj == null) {
            return false;
        }
        if (shouldBoost) {
            if (scrollerLock == null) {
                scrollerLock = obj;
                return true;
            }
        } else {
            if (scrollerLock != null && scrollerLock == obj) {
                scrollerLock = null;
                return true;
            }
        }
        return false;
    }

    private boolean checkSystemAPP(String pkgName) {
        boolean isSystemApp = false;
        if (mDispatcherPkgName == null && mDispatcherPkgName != pkgName) {
            mIsSystemApp = Util.isSystemApp(pkgName);
        }

        isSystemApp = mIsSystemApp;
        return isSystemApp;
    }

    private void sbeHint(int whichStep, String logStr) {
        sbeHint(whichStep, -1.0f, logStr); // Pass a default value for velocity
    }

    private void sbeHint(int whichStep, float velocity, String logStr) {
        if (Config.isBoostFwkLogEnable() && logStr != null) {
            LogUtil.traceAndMLogd(TAG, "step=" + whichStep + " " + logStr
                + " mIsInputLockAcquired = " + mIsInputLockAcquired);
        }
        //SPD:cancel fling(view) for aosp type by sifeng.tian 20230309 start
        if (whichStep == ScrollPolicy.sFLING_VERTICAL
                && mActivityInfo.isAOSPPageDesign()) {
            if (!mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_DESIGN_COMPOSE)) {
                return;
            }
        }
        if (whichStep == ScrollPolicy.sFLING_VERTICAL
                && mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY)) {
            return;
        }

        //SPD:cancel vertical fling(view) for aosp type by sifeng.tian 20230309 end
        if ((whichStep == ScrollPolicy.sFLING_VERTICAL
                || whichStep == ScrollPolicy.sFLING_HORIZONTAL)
                && mIsSpecialPageDesign != ActivityInfo.NO_CHECKED_STATUS) {
            if (velocity >= 0) { // Only cast velocity if it's been provided
                ScrollPolicy.getInstance().scrollHint(
                    whichStep, mIsSpecialPageDesign, (int)velocity);
            } else {
                ScrollPolicy.getInstance().scrollHint(whichStep, mIsSpecialPageDesign);
            }
            return;
        }

        if (mIsInputLockAcquired && sAUTO_SWITCH_FPSGO) {
            ScrollPolicy.getInstance().scrollHint(whichStep,
                    mIsSpecialPageDesign);
        }
    }

    public boolean isScroll() {
        synchronized (SCROLL_LOCK) {
            return mIsScrolling;
        }
    }

    public void setScrolling(boolean scrolling, String msg) {
        synchronized (SCROLL_LOCK) {
            mIsScrolling = scrolling;
            if (Config.isBoostFwkLogEnable()) {
                Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg
                        + " curScrollingState=" + String.valueOf(mIsScrolling));
                Trace.traceEnd(Trace.TRACE_TAG_VIEW);
            }
        }
    }

    public interface TouchEventListener {
        public void onTouchEvent(MotionEvent event);
        default void preTouchEvent(MotionEvent event){}
    }

    public void registerTouchEventListener(TouchEventListener listener) {
        if (listener == null) {
            return;
        }
        mTouchEventListeners.add(listener);
    }

    public interface ScrollEventListener {
        public void onScrollEvent();
    }

    public void registerScrollEventListener(ScrollEventListener listener) {
        if (listener == null) {
            return;
        }
        mScrollEventListeners.add(listener);
    }

    public void notifyScrollEvent(){
        // Dispatch Scroll events to listeners
        for (ScrollEventListener scrollEventListener : mScrollEventListeners) {
            scrollEventListener.onScrollEvent();
        }
    }

}
