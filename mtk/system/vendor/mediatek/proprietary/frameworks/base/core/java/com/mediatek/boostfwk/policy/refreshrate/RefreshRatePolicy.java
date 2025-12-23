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

package com.mediatek.boostfwk.policy.refreshrate;

import android.app.Activity;
import android.annotation.NonNull;
import android.content.Context;
import android.view.WindowManager;
import android.view.Window;
import android.view.MotionEvent;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerGlobal;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.view.ViewRootImpl;
import android.os.Looper;
import android.os.SystemClock;
import android.os.RemoteException;
import android.os.Trace;
import android.os.Handler;
import android.os.Message;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.info.ActivityInfo.ActivityChangeListener;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.scenario.refreshrate.EventScenario;
import com.mediatek.boostfwk.scenario.refreshrate.RefreshRateScenario;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.utils.Util;

import dalvik.system.PathClassLoader;

import java.lang.ref.WeakReference;
import java.util.Arrays;

import java.util.ArrayList;

public class RefreshRatePolicy {
    private static final String TAG = "M-ProMotion";

    private RefreshRateScenario mActiveRefreshScenario = null;
    private EventScenario mActiveReEventScenario = null;
    private RefreshRateInfo mRefreshRateInfo = null;
    private WorkerHandler mWorkerHandler = null;
    private boolean mIsDataInited = false;
    private boolean mMSyncSupportedByProcess = true;
    private IRefreshRateEx mRefreshRatePolicyExt = null;
    private PathClassLoader mClassLoader;

    // Fling Strat
    private ArrayList<Integer> mFlingSupportedRefreshRate;
    private ArrayList<Float> mFlingRefreshRateChangeGap;
    private ArrayList<Float> mFlingRefreshRateTimeOffset;
    private ArrayList<Float> mFlingRefreshRateVsyncTime;
    private ArrayList<Float> mFlingVelocityThreshhold;
    private int mFlingSupportRefreshRateCount;
    private int mSplinePositionCount;
    private static final int DEFAULT_MIN_REFRESHRATE_CHANGE_VALUE = 1000; // Enable change fling
    private static final float FLING_STOP_PROGRESS_THRESHOLED = 0.99f;

    private static final int LIST_STATE_FLING_START = 0;
    private static final int LIST_STATE_FLING_UPDATE = 1;
    private static final int LIST_STATE_FLING_FINISH = 2;
    private static final int LIST_STATE_SCROLLER_INIT = 3;
    // Fling End

    // Touch Scroll Start
    private int mTouchScrollSpeed = TOUCH_SCROLL_STATE_HIGH_SPEED;
    private long mLastRefreshChangeTime  = 0;
    private int mCurrentTouchState = MOTION_EVENT_ACTION_DEFAULT;
    private boolean mIsRealTouchMove = false;
    private boolean mIsTouchDownToBeHandled = false;
    private boolean mIsTouchUpToBeHandled = false;
    private VelocityTracker mVelocityTracker = null;
    private long mTouchDownTime = 0;
    private long mTouchDuration = 0;
    private float mTouchDownPointerY;
    private float mLastTouchMovePointerY;
    private boolean mMarkRefreshRateChange = false;
    private int mLowSpeedRefreshRateIndex = 0;
    private static final float MIN_VELOCITY = 1;

    private static final int TOUCH_SCROLL_STATE_INVALID_SPEED = -1; // default :120Hz
    private static final int TOUCH_SCROLL_STATE_HIGH_SPEED = 0; // default :120Hz
    private static final int TOUCH_SCROLL_STATE_LOW_SPEED = 1; // default : 90Hz
    private static final int LIST_FINISH_THRESHOLED = 150;
    private static final int MOTION_EVENT_ACTION_DEFAULT = -1;
    private static final int MOTION_EVENT_ACTION_DOWN = 0;
    private static final int MOTION_EVENT_ACTION_MOVE = 1;
    private static final int MOTION_EVENT_ACTION_UP = 2;

    private static final int REFRESH_RATE_IDLE = 1000; // Default
    private static final int REFRESH_RATE_TOUCH_SCROLL_DOWN = 1001; // Touch Scroll Start
    private static final int REFRESH_RATE_TOUCH_SCROLL_UP = 1000; // Touch Scroll END
    private static final int MIN_REFRESH_RATE_SUPPORT_IN_FLING = 60; // Touch Scroll END
    // Touch Scroll End

    // Special Scenario
    private static final int SCENARIO_TYPE_DEFAULT = 0;
    private static final int SCENARIO_TYPE_IME = 1;
    private static final int SCENARIO_TYPE_VOICE = 2;
    private static final int SCENARIO_TYPE_FRAMEID = 3;

    private static final int SCENARIO_ACTION_DEFAULT = 0;
    private static final int SCENARIO_ACTION_SHOW = 1;
    private static final int SCENARIO_ACTION_HIDE = 2;

    private static final int INVALID_FRAME_ID = -1;
    private static final int DEFAULT_FRAME_ID = 0;
    // IME
    private WeakReference<Window> mImeWindow;

    private static ArrayList<VariableRefreshRateListener> mRefreshRateListeners = new ArrayList<>(8);
    public interface VariableRefreshRateListener {
        public void onVariableRefreshRateChanged(int refreshRate);
    }

    public static void registerVariableRefreshRateListener(VariableRefreshRateListener listener) {
        if (listener == null) {
            return;
        }
        mRefreshRateListeners.add(listener);
    }

    public static void unregisterVariableRefreshRateListener(VariableRefreshRateListener listener) {
        if (listener == null) {
            return;
        }
        mRefreshRateListeners.remove(listener);
    }

    private static synchronized void notifyRefreshRateChangedIfNeeded(int refreshRate) {
        for (VariableRefreshRateListener listener : mRefreshRateListeners) {
            listener.onVariableRefreshRateChanged(refreshRate);
        }
    }

/** ------------------------------------Data Init Start--------------------------------------**/

    public RefreshRatePolicy() {
        mRefreshRateInfo = new RefreshRateInfo();
        registerActivityListener();
        mWorkerHandler = new WorkerHandler(Looper.getMainLooper());
    }

    public void registerActivityListener() {
        ActivityInfo.getInstance().registerActivityListener(new ActivityChangeListener() {

            @Override
            public void onChange(Context c) {
                LogUtil.mLogd(TAG, "Activity Changed");
                if (!mMSyncSupportedByProcess ||c == null || !(c instanceof Activity)) return;

                mRefreshRateInfo.updateCurrentActivityName(c);
                if (!mRefreshRateInfo.isSameActivity(c)) {
                    LogUtil.mLogd(TAG, "Not same activity, Clear context info");
                    mActiveRefreshScenario = null;
                    mActiveReEventScenario = null;
                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }
                }

                if (mIsDataInited) return;
                mRefreshRateInfo.initHardwareSupportRefreshRate(c);
                mRefreshRateInfo.initMSync3SupportRefreshRate();
                mRefreshRateInfo.initPackageInfo(c);

                mFlingSupportedRefreshRate = mRefreshRateInfo.getFlingSupportedRefreshRate();
                mFlingRefreshRateChangeGap = mRefreshRateInfo.getFlingRefreshRateChangeGap();
                mFlingRefreshRateTimeOffset = mRefreshRateInfo.getFlingRefreshRateTimeOffset();
                mFlingSupportRefreshRateCount
                        = mRefreshRateInfo.getFlingSupportedRefreshRateCount();
                mFlingRefreshRateVsyncTime = mRefreshRateInfo.getFlingRefreshRateVSyncTime();
                mFlingVelocityThreshhold = mRefreshRateInfo.getFlingVelocityThreshhold();
                mLowSpeedRefreshRateIndex = mFlingSupportedRefreshRate.indexOf(
                    mRefreshRateInfo.getSlowScrollRefreshRate());

                String className = "com.mediatek.msync.RefreshRatePolicyExt";
                String classPath = "/system/framework/msync-lib.jar";
                Class<?> clazz = null;
                try {
                    mClassLoader = new PathClassLoader(classPath, c.getClassLoader());
                    clazz = Class.forName(className, false, mClassLoader);
                    mRefreshRatePolicyExt = (IRefreshRateEx) clazz.getConstructor().newInstance();
                    if (mRefreshRatePolicyExt != null) {
                        mRefreshRatePolicyExt.setVeloctiyThreshhold(mFlingVelocityThreshhold);
                    }
                } catch (Exception e) {
                    LogUtil.mLoge(TAG, "msync-lib.jar not exits");
                }

                mIsDataInited = true;
            }

            @Override
            public void onActivityPaused(Context c) {
                if (mActiveRefreshScenario != null) {
                    LogUtil.mLogd(TAG, "onActivityPaused");
                    mActiveRefreshScenario.setActivityStopped(true);
                    if (checkIfActivityStopped(mActiveRefreshScenario)) {
                        LogUtil.mLogd(TAG,"Activity paused , Disable msync while flinging");
                    }
                }
            }
        });
    }

    public void setVeriableRefreshRateSupported(boolean isSupport) {
        mMSyncSupportedByProcess = isSupport;
    }

    private static final int MSG_ENTER_IDLE = 0;
    private static final int IDLE_DELAY_TIME = 2000;
    class WorkerHandler extends Handler {

        WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_ENTER_IDLE:
                    if (mActiveRefreshScenario != null) {
                        updateRefreshRateWhenFling(REFRESH_RATE_IDLE, mActiveRefreshScenario);
                    }
                break;
            }
        }
    }

/** -------------------------------------Data Init End---------------------------------**/


/** ------------------------------Scenario Dispatch Start---------------------------------**/
    public void dispatchScenario(RefreshRateScenario refreshRateScenario) {
        int action = refreshRateScenario.getScenarioAction();
        LogUtil.traceBeginAndMLogd(TAG, "Dispatch Scenario Action =" + action);

        switch (action) {
            case BoostFwkManager.RefreshRate.SCROLLER_INIT: // List scroller init
                onListScrollerInit(refreshRateScenario);
                break;
            case BoostFwkManager.RefreshRate.FLING_START: // List fling start
                if (!mIsDataInited) return;
                onListFlingStart(refreshRateScenario);
                break;
            case BoostFwkManager.RefreshRate.FLING_FINISH: // List fling finished
                if (!mIsDataInited) return;
                onListFlingFinished(refreshRateScenario);
                break;
            case BoostFwkManager.RefreshRate.FLING_UPDATE: // List in fling state
                if (!mIsDataInited) return;
                if (mRefreshRateInfo.getIsCustomerFlingPolicy()) {
                    // Use customer fling policy
                    onListFlingUpdateWithCustomerPolicy(refreshRateScenario);
                } else {
                    // Use M-ProMotion fling policy
                    onListFlingUpdate(refreshRateScenario);
                }
                break;
            default:
                break;
        }

        LogUtil.traceEnd();
    }

    public void dispatchEvent(EventScenario eventScenario) {
        int action = eventScenario.getScenarioAction();
        LogUtil.traceBeginAndMLogd(TAG, "Dispatch View Event Action = " + action);

        switch (action) {
            case BoostFwkManager.ViewEvent.TEXTUREVIEW_VISIBILITY: //TextureView visibility update
            case BoostFwkManager.ViewEvent.SURFACEVIEW_VISIBILITY: //SurfaceView visibility update
                onViewEventUpdate(eventScenario);
                eventScenario.setVariableRefreshRateEnabled(true);
                break;
            default:
                break;
        }

        LogUtil.traceEnd();
    }

    private void onListScrollerInit(RefreshRateScenario refreshRateScenario) {
        refreshRateScenario.setCurrentFlingState(LIST_STATE_SCROLLER_INIT)
                .setVariableRefreshRateEnabled(Config.isVariableRefreshRateSupported())
                .setSmoothFlingEnabled(Config.isMSync3SmoothFlingEnabled())
                .setListScrollStateListening(true)
                .setFlingRefreshRateChangeTimeOffset(mFlingRefreshRateTimeOffset)
                .setFlingRefreshRateVSyncTime(mFlingRefreshRateVsyncTime);

        LogUtil.mLogd(TAG, "onListScrollerInit action = SCROLL_INIT");
    }

    private void onListFlingStart(RefreshRateScenario refreshRateScenario) {
        LogUtil.mLogi(TAG, "List fling start");

        // Stop other scenario fling
        if (mActiveRefreshScenario != null) {
            mActiveRefreshScenario.setRefreshRateChangeEnabledWhenFling(false);
            mWorkerHandler.removeMessages(MSG_ENTER_IDLE);
        }

        // If Config.isMSync3SmoothFlingEnabled disabled
        // fling Distance\Duration\SplinePosition will not changed by MSync3
        if (Config.isMSync3SmoothFlingEnabled() && mRefreshRatePolicyExt != null) {
            refreshRateScenario.setRealSplinePosition(
                mRefreshRatePolicyExt.getSmoothFlingSplinePosition());
            mSplinePositionCount = mRefreshRatePolicyExt.getSmoothFlingSplinePositionCount();
        } else {
            refreshRateScenario.setRealSplinePosition(
                    refreshRateScenario.getOriginalSplinePosition());
            mSplinePositionCount = refreshRateScenario.getOriginalSplinePosition().length - 1;
        }

        mActiveRefreshScenario = refreshRateScenario;

        float velocity = refreshRateScenario.getCurrentVelocity();

        refreshRateScenario.setCurrentFlingState(LIST_STATE_FLING_START)
            .resetTotalOffsetTime()
            .setRefreshRateChangeNotFinish(false)
            .setIsChangingRefreshRate(false);

        if (mActiveReEventScenario != null) {
            mActiveReEventScenario.setIsMarked(false);
        }

        int touchScrollState = getTouchScrollState();
        int velocityIndex = calculateRefreshRateIndex(Math.abs(velocity), mLowSpeedRefreshRateIndex);
        int refreshrateIndex = velocityIndex < touchScrollState ? velocityIndex : touchScrollState;

        int refreshRateByTouch = 0;
        if (touchScrollState != TOUCH_SCROLL_STATE_INVALID_SPEED) {
            refreshRateByTouch = mFlingSupportedRefreshRate.get(refreshrateIndex).intValue();
        }

        if (touchScrollState == TOUCH_SCROLL_STATE_INVALID_SPEED
                || Math.abs(velocity) < DEFAULT_MIN_REFRESHRATE_CHANGE_VALUE
                || refreshRateByTouch <= MIN_REFRESH_RATE_SUPPORT_IN_FLING) {
            // If scroll state is invalid, or velocity is close to zero, disable change refresh rate
            refreshRateScenario.setRefreshRateChangeEnabledWhenFling(false);
            if (LogUtil.DEBUG) {
                LogUtil.mLogd(TAG,"Do igonre refresh rate change when fling " +
                        " touchScrollState = " + touchScrollState + ", velocity = " + velocity
                        + ", refreshRateByTouch = " + refreshRateByTouch);
            }
        } else {
            refreshRateScenario.setFlingRefreshRateChangeIndex(refreshrateIndex)
                    .setCurrentRefreshrate(refreshRateByTouch)
                    .setRefreshRateChangeEnabledWhenFling(true);
            boostToMaxRefreshRate(refreshRateScenario, refreshRateByTouch);
            notifyRefreshRateChangedIfNeeded(refreshRateByTouch);

            // init delta time for customer fling policy , depends on touch scroll speed
            if (mRefreshRateInfo.getIsCustomerFlingPolicy()) {
                refreshRateScenario.setCurrentDeltaTime(mFlingRefreshRateTimeOffset.get(
                        refreshRateScenario.getFlingRefreshRateChangeIndex()).doubleValue())
                        .setDistanceCoef(0);
            }
        }

        // Check if activity is in PIP mode or MultiWindow Mode
        if (isPipModeOrMultiWindowMode(refreshRateScenario.getScenarioContext())) {
            refreshRateScenario.setRefreshRateChangeEnabledWhenFling(false);
            if (LogUtil.DEBUG) {
                LogUtil.mLogd(TAG,"PIP Mode or MultiWindow Mode");
            }
        }

        // Use M-ProMotion fling policy
        if (velocity != 0 && !mRefreshRateInfo.getIsCustomerFlingPolicy()) {
            refreshRateScenario.setCurrentVelocity(adjustVelocity(velocity));

            if (mRefreshRatePolicyExt != null) {
                int splineDuration = (int)mRefreshRatePolicyExt.getSplineFlingDuration(
                    mRefreshRateInfo.getInflextion(),refreshRateScenario.getCurrentVelocity(),
                    refreshRateScenario.getFlingFriction(),refreshRateScenario.getPhysicalCoeff(),
                    mRefreshRateInfo.getMaximumVelocity(),mSplinePositionCount,
                    refreshRateScenario.getRealSplinePosition(),Config.isMSync3SmoothFlingEnabled()
                );

                Double splineFlingDistance = mRefreshRatePolicyExt.getSplineFlingDistance(
                    mRefreshRateInfo.getInflextion(),refreshRateScenario.getCurrentVelocity(),
                    refreshRateScenario.getFlingFriction(),refreshRateScenario.getPhysicalCoeff(),
                    mRefreshRateInfo.getMaximumVelocity(),mSplinePositionCount,
                    refreshRateScenario.getRealSplinePosition(),Config.isMSync3SmoothFlingEnabled()
                );
                int splineDistance = (int) (splineFlingDistance * Math.signum(velocity));

                refreshRateScenario.setSplineDuration(splineDuration)
                        .setSplineFlingDistance(splineFlingDistance)
                        .setSplineDistance(splineDistance)
                        .setDistanceCoef(0);

                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG,"FLING START splineDuration = " + splineDuration
                    + " splineDistance =" + splineDistance
                    + " velocity = " + velocity);
                    refreshRateScenario.setLastCurrentTime(0);
                }
            }
        }

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "onListFlingStart action = FLING_START"
                + " velocity = " + velocity
                + " mSplineDuration = " + refreshRateScenario.getSplineDuration()
                + " mSplineFlingDistance = " + refreshRateScenario.getSplineFlingDistance()
                + " mSplineDistance = "+ refreshRateScenario.getSplineDistance()
                + " isEnabled = " + refreshRateScenario.getRefreshRateChangeEnabledWhenFling());
        }
    }

    // Get refresh rate depends on velocity
    private int calculateRefreshRateIndex(float velocity , int lowSpeedRefreshRateIndex) {
        float tempVelocity = velocity;
        int count = mFlingVelocityThreshhold.size();

        for (int i = 0; i < count; i++) {
            if (tempVelocity > mFlingVelocityThreshhold.get(i)) {
                return i > lowSpeedRefreshRateIndex ? lowSpeedRefreshRateIndex : i;
            }
        }
        return lowSpeedRefreshRateIndex;
    }

    private boolean isPipModeOrMultiWindowMode(Context Context) {
        Activity activity = (Activity)Context;
        if (activity != null) {
            return activity.isInMultiWindowMode() || activity.isInPictureInPictureMode();
        }
        return false;
    }

    private float adjustVelocity(float velocity) {
        float newVelocity = velocity;
        long touchScrollTime = SystemClock.uptimeMillis() - mTouchDownTime;
        float deltaY = mTouchDownPointerY - mLastTouchMovePointerY;
        float deltaV = velocity / ((deltaY / touchScrollTime) * 1000);

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "adjustVelocity " + " velocity = " + velocity
                + " touchScrollTime = " + touchScrollTime
                + " deltaY = " + deltaY
                + " result = " + deltaV);
        }

        if (touchScrollTime < 200 && Math.abs(deltaY) < 400 && Math.abs(deltaV) > 2.5) {
            newVelocity = (float) ((deltaY / touchScrollTime) * 1000 * 2.5) * Math.signum(velocity);
        }
        return newVelocity;
    }

    // List Update : Use customer's fling implementation
    private void onListFlingUpdateWithCustomerPolicy(RefreshRateScenario refreshRateScenario) {
        refreshRateScenario.setCurrentFlingState(LIST_STATE_FLING_UPDATE);
        double stepGap = 0;
        double currentDistance = refreshRateScenario.getCurrentDistance();
        double currentDeltaDistance = refreshRateScenario.getCurrentDeltaDistance();
        int flingRefreshRateChangeIndex = refreshRateScenario.getFlingRefreshRateChangeIndex();

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG,"onListFlingUpdateWithCustomerPolicy "
                + " currentDistance = " + currentDistance
                + " currentDeltaDistance = " + currentDeltaDistance);
        }

        if (!refreshRateScenario.getRefreshRateChangeEnabledWhenFling()
                || (flingRefreshRateChangeIndex >= mFlingSupportRefreshRateCount -1)
                || hasVideoWhenFlingUpdate(refreshRateScenario,flingRefreshRateChangeIndex)) return;

        if (mRefreshRatePolicyExt != null) {
            if (mRefreshRatePolicyExt.checkIfRefreshRateChangeNeeded(currentDistance,
                    currentDeltaDistance, flingRefreshRateChangeIndex, mFlingSupportRefreshRateCount,
                    mFlingRefreshRateChangeGap.get(flingRefreshRateChangeIndex))) {
                if (updateRefreshRateWhenFling(
                    mFlingSupportedRefreshRate.get(flingRefreshRateChangeIndex + 1).intValue(),
                    refreshRateScenario)) {
                    // update delta time
                    refreshRateScenario.setCurrentDeltaTime(
                        mFlingRefreshRateTimeOffset.get(flingRefreshRateChangeIndex).doubleValue())
                        .setIsChangingRefreshRate(true);
                    notifyRefreshRateChangedIfNeeded(
                        mFlingSupportedRefreshRate.get(flingRefreshRateChangeIndex + 1).intValue());
                }
            }
        }

        // Calculate distanceCoef for touch speed
        float distanceCoef = (float)currentDistance / refreshRateScenario.getSplineDistance();
        refreshRateScenario.setDistanceCoef(distanceCoef);
    }

    // List Update : Use M-ProMotion fling implementation
    private void onListFlingUpdate(RefreshRateScenario refreshRateScenario) {
        refreshRateScenario.setCurrentFlingState(LIST_STATE_FLING_UPDATE);

        long currentTime = refreshRateScenario.getCurrentTime();
        int splineDuration = refreshRateScenario.getSplineDuration();
        int splineDistance = refreshRateScenario.getSplineDistance();
        float[] realSplinePosition = refreshRateScenario.getRealSplinePosition();
        int flingRefreshRateChangeIndex = refreshRateScenario.getFlingRefreshRateChangeIndex();
        int currentRefreshRate = refreshRateScenario.getCurrentRefreshrate();

        // Adujst current time when change refresh rate
        if (refreshRateScenario.getRefreshRateChangeNotFinish()
                && !Config.isEnableFramePrefetcher()) {
            currentTime = adjustCurrentTime(refreshRateScenario);
        }

        // Calculate current distance and velocity
        final float t = (float) currentTime / (splineDuration * 1000000L);
        final int index = (int) (mSplinePositionCount * t);
        float distanceCoef = 1.f;
        float velocityCoef = 0.f;
        if (index < mSplinePositionCount) {
            final float t_inf = (float) index / mSplinePositionCount;
            final float t_sup = (float) (index + 1) / mSplinePositionCount;
            final float d_inf = realSplinePosition[index];
            final float d_sup = realSplinePosition[index + 1];
            velocityCoef = (d_sup - d_inf) / (t_sup - t_inf);
            distanceCoef = d_inf + (t - t_inf) * velocityCoef;
        }
        double currentDistance = distanceCoef * splineDistance;
        float currentVelocity = (float)(velocityCoef * splineDistance / splineDuration * 1000.0f);

        double lastDistance = refreshRateScenario.getCurrentDistance();
        refreshRateScenario.setCurrentDistance(currentDistance)
                .setCurrentVelocity(currentVelocity)
                .setDistanceCoef(distanceCoef);

        if (!refreshRateScenario.getRefreshRateChangeEnabledWhenFling()
                || (flingRefreshRateChangeIndex >= mFlingSupportRefreshRateCount -1)
                || hasVideoWhenFlingUpdate(refreshRateScenario,flingRefreshRateChangeIndex)) return;

        if (mRefreshRatePolicyExt != null) {
            double stepGap = 0;
            stepGap = mRefreshRatePolicyExt.calculateGap(currentTime,
                refreshRateScenario.getCurrentRefreshrate(), (int)Util.getRefreshRate(),
                mFlingSupportedRefreshRate.get(flingRefreshRateChangeIndex).intValue(),
                mFlingRefreshRateVsyncTime.get(flingRefreshRateChangeIndex).doubleValue(),
                mFlingRefreshRateTimeOffset.get(flingRefreshRateChangeIndex).doubleValue(),
                splineDuration, splineDistance, mSplinePositionCount, realSplinePosition);

            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG,"dis = " + currentDistance + " currentTime = " + currentTime
                    + " deltaDistance =" + (currentDistance - lastDistance)
                    + " deltaTime = " + (currentTime - refreshRateScenario.getLastCurrentTime())/1000000f
                    + " deltaGap = " + stepGap
                    + " velocity = " + currentVelocity
                    + " realVelocity = " + ((currentDistance - lastDistance))
                        /((currentTime - refreshRateScenario.getLastCurrentTime())/1000000f));

                LogUtil.mLogd(TAG, "onListFlingUpdate stepGap = " + stepGap
                    + " flingRefreshRateChangeIndex = " + flingRefreshRateChangeIndex);
                refreshRateScenario.setLastCurrentTime(currentTime);
            }

            if (flingRefreshRateChangeIndex < mFlingSupportRefreshRateCount &&
                    stepGap <= mFlingRefreshRateChangeGap.get(flingRefreshRateChangeIndex) &&
                    stepGap > 0) {
                if (updateRefreshRateWhenFling(mFlingSupportedRefreshRate.get(
                        flingRefreshRateChangeIndex + 1).intValue(),refreshRateScenario)) {
                    refreshRateScenario.setRefreshRateChangeNotFinish(true)
                        .setIsChangingRefreshRate(true);
                    notifyRefreshRateChangedIfNeeded(refreshRateScenario.getCurrentRefreshrate());
                }
            }
        }
    }

    private boolean checkIfActivityStopped(RefreshRateScenario refreshRateScenario) {
        if (refreshRateScenario.getRefreshRateChangeEnabledWhenFling()) {
            LogUtil.mLogd(TAG, "checkActivityStopped true");
            updateRefreshInternal(refreshRateScenario, REFRESH_RATE_IDLE, DEFAULT_FRAME_ID,
                SCENARIO_TYPE_FRAMEID);
            refreshRateScenario.setRefreshRateChangeEnabledWhenFling(false);
            return true;
        }
        return false;
    }

    // if list with video visibility, do not set refresh rate lower than 60
    private boolean hasVideoWhenFlingUpdate(RefreshRateScenario refreshRateScenario,
            int flingRefreshRateChangeIndex) {
        if (refreshRateScenario.hasVideo()) {
            if (mFlingSupportedRefreshRate.get(flingRefreshRateChangeIndex + 1).intValue()
                < mRefreshRateInfo.getVideoFloorRefreshRate()) {
                    LogUtil.mLogd(TAG, "List with video, min support refresh rate is "
                        + mRefreshRateInfo.getVideoFloorRefreshRate());
                    return true;
            }
        }
        return false;
    }

    private long adjustCurrentTime(RefreshRateScenario refreshRateScenario) {
        int currentRefreshRate = refreshRateScenario.getCurrentRefreshrate();
        long currentTime = refreshRateScenario.getCurrentTime();
        long lastOriginTime = refreshRateScenario.getLastOriginTime();
        int flingRefreshRateChangeIndex = refreshRateScenario.getFlingRefreshRateChangeIndex();

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "adjustCurrentTime currentRefreshRate = " + currentRefreshRate
            + " currentTime = " + currentTime
            + " flingRefreshRateChangeIndex = " + flingRefreshRateChangeIndex
            + " mFlingSupportRefreshRateCount = " + mFlingSupportRefreshRateCount
            + " realRefreshRate = " + (int)Util.getRefreshRate());
        }

        // No need to adjust current time if current index is
        if (flingRefreshRateChangeIndex >= mFlingSupportRefreshRateCount
                || flingRefreshRateChangeIndex -1 < 0) {
                return currentTime;
        }

        float timeOffset = mFlingRefreshRateTimeOffset.get(flingRefreshRateChangeIndex - 1);
        if (currentRefreshRate != (int)Util.getRefreshRate() &&
            (currentTime - lastOriginTime) <
            mFlingRefreshRateVsyncTime.get(flingRefreshRateChangeIndex) - timeOffset/2) {
                refreshRateScenario.setCurrentOffsetTime(timeOffset);
                refreshRateScenario.increaseTotalOffsetTime(timeOffset);
                if (LogUtil.DEBUG) {
                    LogUtil.mLogd(TAG, "realAdjustCurrentTime timeOffset  = " + timeOffset
                        + " TotalOffsetTime = " + refreshRateScenario.getTotalOffsetTime());
                }
        } else {
            refreshRateScenario.setIsChangingRefreshRate(false);
        }

        refreshRateScenario.setLastOriginTime(currentTime);
        currentTime += refreshRateScenario.getTotalOffsetTime();
        refreshRateScenario.setCurrentTime(currentTime);

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "adjustCurrentTime newCurrentTime  = " + currentTime);
        }

        return currentTime;
    }

    private void onListFlingFinished(RefreshRateScenario refreshRateScenario) {
        if (refreshRateScenario.getCurrentFlingState() != LIST_STATE_FLING_UPDATE) {
            refreshRateScenario.setCurrentFlingState(LIST_STATE_FLING_FINISH);
            return;
        }

        refreshRateScenario.setLastFlingFinishTime(SystemClock.uptimeMillis());
        if (refreshRateScenario.getDistanceCoef() > FLING_STOP_PROGRESS_THRESHOLED) {
            if (mActiveRefreshScenario != null
                && mActiveRefreshScenario != refreshRateScenario
                && mActiveRefreshScenario.getCurrentFlingState() == LIST_STATE_FLING_UPDATE) {
                LogUtil.mLogd(TAG, "Another list is fling update");
            } else {
                updateRefreshRateWhenFling(REFRESH_RATE_IDLE, refreshRateScenario);
            }
        } else {
            // List stop by other reason, such as list reach edge or enter recents window
            mWorkerHandler.removeMessages(MSG_ENTER_IDLE);
            mWorkerHandler.sendEmptyMessageDelayed(MSG_ENTER_IDLE, LIST_FINISH_THRESHOLED);
            LogUtil.mLogd(TAG, "onListFlingFinished List stop by other reason");
        }

        refreshRateScenario.setCurrentFlingState(LIST_STATE_FLING_FINISH)
            .setFlingRefreshRateChangeIndex(0)
            .setRefreshRateChangeEnabledWhenFling(false)
            .setDistanceCoef(0)
            .setRefreshRateChangeNotFinish(false)
            .setIsChangingRefreshRate(false);
        notifyRefreshRateChangedIfNeeded(-1);
    }

    // Handle video event
    private void onViewEventUpdate(EventScenario eventScenario) {
        mActiveReEventScenario =  eventScenario;
        if (mActiveRefreshScenario != null) {
            if (mActiveRefreshScenario.getCurrentFlingState() == LIST_STATE_FLING_UPDATE) {
                mActiveRefreshScenario.setHasVideo(true);
            }
        }
        mActiveReEventScenario.setIsMarked(true);
    }

/** -----------------------------------Scenario Dispatch End---------------------------------**/


/** --------------------------------------Fling Start----------------------------------**/
    private boolean updateRefreshRateWhenFling(final int refreshRate,
            RefreshRateScenario refreshRateScenario) {
        // List fling finished , reset frame id to 0
        if (refreshRate == REFRESH_RATE_IDLE) {
            refreshRateScenario.setFrameId(DEFAULT_FRAME_ID);
        }

        if (refreshRateScenario.getCurrentRefreshrate() == refreshRate
            || refreshRateScenario.getFrameId() == INVALID_FRAME_ID) return false;

        if (updateRefreshInternal(refreshRateScenario,refreshRate,
                (int)refreshRateScenario.getFrameId(), SCENARIO_TYPE_FRAMEID)) {
            refreshRateScenario.setCurrentRefreshrate(refreshRate);
            refreshRateScenario.increaseFlingRefreshRateChangeIndex();
            return true;
        }
        return false;
    }

    private void boostToMaxRefreshRate(RefreshRateScenario refreshRateScenario,
            final int refreshRate) {
        updateRefreshInternal(refreshRateScenario,refreshRate,DEFAULT_FRAME_ID,
            SCENARIO_TYPE_FRAMEID);
    }
/** ---------------------------------------Fling End------------------------------------------**/


/** -----------------------------------Touch Scroll Start--------------------------------**/
    public void onTouchEvent(final MotionEvent event) {
        if (mVelocityTracker == null) {
            // Retrieve a new VelocityTracker object to watch the velocity of a motion.
            mVelocityTracker = VelocityTracker.obtain();
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Reset the velocity tracker back to its initial state.
                mVelocityTracker.clear();
                // Add a user's movement to the tracker.
                mVelocityTracker.addMovement(event);
                mTouchDownTime = SystemClock.uptimeMillis();
                mTouchDownPointerY = event.getY();
                mLastTouchMovePointerY = 0f;
                onScrollStateChange(MotionEvent.ACTION_DOWN , 0f);
                mCurrentTouchState = MOTION_EVENT_ACTION_DOWN;
                mTouchScrollSpeed = TOUCH_SCROLL_STATE_HIGH_SPEED;
                if (mRefreshRatePolicyExt != null) {
                    mRefreshRatePolicyExt.resetRefreshRateMarker();
                }
                mIsRealTouchMove = false;
                break;
            case MotionEvent.ACTION_MOVE:
                mVelocityTracker.addMovement(event);
                mVelocityTracker.computeCurrentVelocity(100);
                mTouchDuration = SystemClock.uptimeMillis() - mTouchDownTime;
                onVelocityChange(mVelocityTracker.getYVelocity(), mTouchDuration);
                mCurrentTouchState = MOTION_EVENT_ACTION_MOVE;
                mLastTouchMovePointerY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mVelocityTracker.addMovement(event);
                mVelocityTracker.computeCurrentVelocity(100);
                onScrollStateChange(MotionEvent.ACTION_UP, mVelocityTracker.getYVelocity());
                mCurrentTouchState = MOTION_EVENT_ACTION_UP;
                mTouchScrollSpeed = TOUCH_SCROLL_STATE_INVALID_SPEED;
                mLastTouchMovePointerY = event.getY();
                if (mRefreshRatePolicyExt != null) {
                    mRefreshRatePolicyExt.resetRefreshRateMarker();
                }
                mIsRealTouchMove = false;
                break;
            default:
                break;
        }
    }

    public void onVelocityChange(final float velocity, final long duration) {
        if (!mIsRealTouchMove || Math.abs(velocity) < MIN_VELOCITY  || ActivityInfo.getInstance().getContext() == null
                || !Config.isMSync3TouchScrollEnabled()) {
            LogUtil.mLogd(TAG, "Activity is null or touch scroll is disabled");
            return;
        }

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "onVelocityChange velocity = " + velocity + " duration = " + duration);
        }

        int tempTouchScrollSpeed = mRefreshRatePolicyExt.getRefreshRateIndex(velocity,
            duration, mLastRefreshChangeTime, mLowSpeedRefreshRateIndex, mTouchScrollSpeed);

        if (tempTouchScrollSpeed != TOUCH_SCROLL_STATE_INVALID_SPEED) {
            mLastRefreshChangeTime = duration;
            mTouchScrollSpeed = tempTouchScrollSpeed;
            if (mActiveRefreshScenario != null) {
                mActiveRefreshScenario.setCurrentRefreshrate(
                    mFlingSupportedRefreshRate.get(mTouchScrollSpeed));
            }
        }
    }

    // Handle touch move event
    private void updateRefreshRateWhenScroll(RefreshRateScenario refreshRateScenario,
            final int refreshRate) {
        LogUtil.mLogd(TAG, "updateRefreshRateWhenScroll oldRefreshRate = "
            + refreshRateScenario.getCurrentRefreshrate()+ " New RefreshRate = " + refreshRate);

        if (updateRefreshInternal(refreshRateScenario,refreshRate,DEFAULT_FRAME_ID,
                SCENARIO_TYPE_FRAMEID)) {
            refreshRateScenario.setCurrentRefreshrate(refreshRate);
        }
    }

    // Handle touch move event
    private boolean updateRefreshRateWhenTouchScroll(final int refreshRate) {
        LogUtil.mLogd(TAG, "updateRefreshRateWhenScroll refreshRate = " + refreshRate);

        if (updateRefreshInternal(null ,refreshRate,
                DEFAULT_FRAME_ID, SCENARIO_TYPE_FRAMEID)) {
            if(mActiveRefreshScenario != null) {
                mActiveRefreshScenario.setCurrentRefreshrate(refreshRate);
            }
            return true;
        }
        return false;
    }

    public void onScrollStateChange(final int scrollState, final float velocity) {
        switch (scrollState) {
            case MotionEvent.ACTION_DOWN: // Boost to 120Hz
                if (mActiveRefreshScenario != null) {
                    mActiveRefreshScenario.setRefreshRateChangeEnabledWhenFling(false);
                    mWorkerHandler.removeMessages(MSG_ENTER_IDLE);
                    updateScrollAction(mActiveRefreshScenario, REFRESH_RATE_IDLE);
                }

                mLastRefreshChangeTime = 0;
                mTouchScrollSpeed = TOUCH_SCROLL_STATE_HIGH_SPEED;
                mIsTouchDownToBeHandled = true;
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsTouchUpToBeHandled) {
                    // fling not start
                    if (mActiveRefreshScenario != null) {
                        if (Math.abs(velocity) <= DEFAULT_MIN_REFRESHRATE_CHANGE_VALUE / 10) {
                            updateScrollAction(mActiveRefreshScenario, REFRESH_RATE_TOUCH_SCROLL_UP);
                        }
                    } else {
                        updateRefreshRateWhenTouchScroll(REFRESH_RATE_TOUCH_SCROLL_UP);
                    }
                    mIsTouchUpToBeHandled = false;
                }

                break;
            default:
                break;
        }
    }

    // Handle touch down and touch up event
    private void updateScrollAction(RefreshRateScenario refreshRateScenario,final int refreshRate) {
        updateRefreshInternal(refreshRateScenario, refreshRate,DEFAULT_FRAME_ID,
            SCENARIO_TYPE_FRAMEID);
    }

    private boolean updateRefreshInternal(RefreshRateScenario refreshRateScenario,
            final int refreshRate, final int scenarioAction, final int scenarioType) {
        LogUtil.mLogd(TAG, "updateRefreshInternal RefreshRate = " + refreshRate);

        Context activityContext;
        if (refreshRateScenario != null) {
            activityContext = refreshRateScenario.getScenarioContext();
        } else {
            activityContext = ActivityInfo.getInstance().getContext();
        }

        if (activityContext == null || isPipModeOrMultiWindowMode(activityContext)) return false;

//        if (activityContext instanceof Activity) {
//            try {
//                Window window = ((Activity) activityContext).getWindow();
//                ViewRootImpl viewRootImpl = window.getDecorView().getViewRootImpl();
//                if (viewRootImpl != null) { // PhoneWindow
//                    SurfaceControl surfaceControl = viewRootImpl.getSurfaceControl();
//                    if (surfaceControl.isValid()) {
//                        LogUtil.traceBeginAndMLogd(TAG, "Update RefreshRate = " + refreshRate
//                            + " scenarioAction = " + scenarioAction
//                            + " scenarioType = " + scenarioType);

//                        WindowManagerGlobal.getWindowSession().setRefreshRate(
//                            surfaceControl,
//                            (float)refreshRate,
//                            scenarioAction,
//                            scenarioTyp
//                            mRefreshRateInfo.getCurrentActivityName(),
//                            mRefreshRateInfo.getPackageName());

//                        LogUtil.traceEnd();
//                        return true;
//                    }
//                }
//            } catch (RemoteException e) {
//                LogUtil.mLogd(TAG, "Refreshrate change failed, e = " + e.toString());
//            }
//        }
        return false;
    }

    private int getTouchScrollState() {
        int currentRefreshRate = (int)Util.getRefreshRate();
        if (currentRefreshRate < mRefreshRateInfo.getSlowScrollRefreshRate()) {
            return TOUCH_SCROLL_STATE_INVALID_SPEED;
        }
        return mFlingSupportedRefreshRate.indexOf(currentRefreshRate);
    }

    // Handle view scroll event for touch scroll case
    public void onScrollEvent() {
        if (mCurrentTouchState == MOTION_EVENT_ACTION_MOVE) {
            mIsRealTouchMove = true;
        }

        // Check if we need to trigger the scroll event and send touch down to SurfaceFlinger
        if (mIsTouchDownToBeHandled && mIsRealTouchMove) {
            dealWithDownEvent();
            mIsTouchDownToBeHandled = false;
            mIsTouchUpToBeHandled = true;
            return; // handle touch move event next frame
        }

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "onScrollEvent  mCurrentTouchState =" + mCurrentTouchState
                    + " mTouchScrollSpeed = " + mTouchScrollSpeed
                    + " mIsRealTouchMove = " + mIsRealTouchMove);
        }

        // Touch scroll and view is moving
        if (mCurrentTouchState == MOTION_EVENT_ACTION_MOVE
                && mTouchScrollSpeed != TOUCH_SCROLL_STATE_INVALID_SPEED
                && mRefreshRatePolicyExt != null
                && mRefreshRatePolicyExt.isRefreshRateMarkerSet()) {
            int refreshRate = mFlingSupportedRefreshRate.get(mTouchScrollSpeed);
            if (refreshRate < mRefreshRateInfo.getSlowScrollRefreshRate()) {
                refreshRate = mRefreshRateInfo.getSlowScrollRefreshRate();
            }
            if (updateRefreshRateWhenTouchScroll(refreshRate)) {
                mRefreshRatePolicyExt.resetRefreshRateMarker();
                LogUtil.mLogd(TAG, "onScrollEvent,Refresh rate change to " + refreshRate);
            }
        }
    }

    // Tell SurfaceFlinger now is real touch scroll
    private void dealWithDownEvent() {
        LogUtil.mLogd(TAG, "dealWithDownEvent");
        if (mActiveRefreshScenario != null) {
            updateScrollAction(mActiveRefreshScenario, REFRESH_RATE_TOUCH_SCROLL_DOWN);
        } else {
            updateRefreshRateWhenTouchScroll(REFRESH_RATE_TOUCH_SCROLL_DOWN);
        }
    }

/** -----------------------------Touch Scroll End-----------------------------**/


/** ----------------------------Voice Dialog Start--------------------------**/

    public void onVoiceDialogEvent(boolean isVoiceShow) {
        LogUtil.mLogi(TAG, "Voice Dialog Show = " + isVoiceShow);

        if (isVoiceShow) {
            updateVoiceDialogRefreshRate(SCENARIO_ACTION_SHOW);
        } else {
            updateVoiceDialogRefreshRate(SCENARIO_ACTION_HIDE);
        }
    }

    private void updateVoiceDialogRefreshRate(final int scenarioAction) {
        LogUtil.traceBeginAndMLogd(TAG, "Voice Action=" + scenarioAction);

        Context activityContext = ActivityInfo.getInstance().getContext();
        if (activityContext != null && activityContext instanceof Activity) {
            Window window = ((Activity) activityContext).getWindow();
            if (window != null) {
                WindowManager.LayoutParams windowLayoutParams = window.getAttributes();
//                windowLayoutParams.mMSyncScenarioType  = SCENARIO_TYPE_VOICE;
//                windowLayoutParams.mMSyncScenarioAction  = scenarioAction;
//                window.setAttributes(windowLayoutParams);
            }
        }
        LogUtil.traceEnd();
    }
/** ---------------------------Voice Dialog End--------------------------------**/


/** ---------------------------IME Start-------------------------------**/

    public void onIMEInit(Window window) {
        if (window == null) return;

        if (mImeWindow != null) {
            mImeWindow.clear();
        }
        mImeWindow = new WeakReference<Window>(window);

        LogUtil.mLogd(TAG, "IME init");
    }

    private Window getIMEWindow() {
        if (mImeWindow == null) {
            return null;
        }
        return mImeWindow.get();
    }

    public void onIMEVisibilityChange(boolean isIMEShow) {
        Window imeWindow = getIMEWindow();
        if (imeWindow!= null) {
            LogUtil.mLogi(TAG, "IME show = " + isIMEShow);
            if (isIMEShow) {
                updateIMERefreshRate(SCENARIO_ACTION_SHOW, imeWindow);
            } else {
                updateIMERefreshRate(SCENARIO_ACTION_HIDE, imeWindow);
            }
        }
    }

    private void updateIMERefreshRate(final int action, Window window) {
        LogUtil.traceBeginAndMLogd(TAG, "Update IME Action = " + action);

        WindowManager.LayoutParams windowLayoutParams = window.getAttributes();
//        windowLayoutParams.mMSyncScenarioType  = SCENARIO_TYPE_IME;
//        windowLayoutParams.mMSyncScenarioAction  = action;
//        window.setAttributes(windowLayoutParams);

        LogUtil.traceEnd();
    }
/** --------------------------------IME End--------------------------------**/

/** ---------------------------Frame State Start------------------------------**/
    public void onFrameStateChange(long frameId) {
        if (mActiveRefreshScenario != null
            && mActiveRefreshScenario.getCurrentFlingState() == LIST_STATE_FLING_UPDATE) {
                mActiveRefreshScenario.setFrameId(frameId);
        }
    }
/** ---------------------------Frame State End--------------------------------**/
}

