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

package com.mediatek.boostfwkV5.identify.launch;

import android.content.Context;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.scenario.BasicScenario;
import com.mediatek.boostfwk.scenario.launch.LaunchScenario;

import com.mediatek.boostfwkV5.identify.BaseIdentify;
import com.mediatek.boostfwkV5.info.ActivityInfo;
import com.mediatek.boostfwkV5.policy.launch.LaunchPolicy;
import com.mediatek.boostfwkV5.utils.Config;
import com.mediatek.boostfwkV5.utils.LogUtil;
import com.mediatek.boostfwkV5.utils.Util;
import com.mediatek.boostfwkV5.utils.TasksUtil;
import com.mediatek.powerhalwrapper.PowerHalWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Map.Entry;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class LaunchIdentify extends BaseIdentify{
    private static final String TAG = "SBE-LaunchIdentify";
    private static volatile LaunchIdentify sInstance = null;
    private HandlerThread mWorkerThread = null;
    private WorkerHandler mWorkerHandler = null;
    private String mPkgName;
    private LaunchConfig mLaunchConfig;
    private LaunchPolicy mLaunchPolicy;
    // Check if need end boost
    private boolean mIsBegin = false;
    private int mCount = 0;
    private List<String> mSpecialPkgNames = new ArrayList<String>();
    public static final String THREAD_NAME = "launch";
    public static final String HOSTTYPE_ACTIVITY = "activity";

    private boolean mInit = false;
    private static PowerHalWrapper mPowerHalWrap = PowerHalWrapper.getInstance();

    public static LaunchIdentify getInstance() {
        if (null == sInstance) {
            synchronized (LaunchIdentify.class) {
                if (null == sInstance) {
                    sInstance = new LaunchIdentify();
                }
            }
        }
        return sInstance;
    }


    private void initThread() {
        if (mInit || (mWorkerThread != null && mWorkerThread.isAlive()
                && mWorkerHandler != null)) {
            LogUtil.mLogd(TAG, "re-init");
        } else {
            mWorkerThread = new HandlerThread(THREAD_NAME);
            mWorkerThread.start();
            Looper looper = mWorkerThread.getLooper();
            if (looper == null) {
                LogUtil.mLogi(TAG, "Thread looper is null");
            } else {
                mWorkerHandler = new WorkerHandler(looper);
            }
            mInit = true;
        }
    }

    public LaunchIdentify() {
        mLaunchConfig = new LaunchConfig();
        mLaunchPolicy = new LaunchPolicy();
        initSpecialMap();
    }

    @Override
    public boolean isMainThreadOnly() {
        return false;
    }

    /**
     * Dispatcher the ux launch refer actions to do.
     *
     * @param scenario LaunchScenario obj
     */
    @Override
    public boolean dispatchScenario(BasicScenario basicScenario) {
        if (basicScenario == null) {
            LogUtil.mLogw(TAG, "No Launch scenario to dispatcher.");
            return false;
        }
        LaunchScenario scenario = (LaunchScenario)basicScenario;
        int action = scenario.getScenarioAction();
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "Launch action dispatcher to = " + action);
        }

        switch (action) {
            case BoostFwkManager.Launch.LAUNCH_COLD:
                initThread();
                launchHintCheck(scenario.getBoostStatus(),
                        scenario.getHostingType(), scenario.getPackageName(),
                        scenario.getWindowingMode(), scenario.getActivityName(),
                        scenario.getIsComeFromIdle());
                break;
            case BoostFwkManager.Launch.ACTIVITY_SWITCH:
                WeakReference<Context> weakReference = scenario.getActivity();
                if (weakReference !=null && weakReference.get() != null) {
                    if (Config.isBoostFwkLogEnable()) {
                        LogUtil.mLogd(TAG, "ACTIVITY_SWITCH set new context "+weakReference.get());
                    }
                    ActivityInfo.getInstance().setContext(weakReference.get());
                    weakReference.clear();
                }
                break;
            default:
                LogUtil.mLogw(TAG, "Not found dispatcher launch action.");
                break;
        }
        return true;
    }

    /**
     *
     * Check the launch step and set the step flag if the process is started
     * with "pre-top-activity", confrim this is launch by user.
     *
     * @param boostStaus
     *            The status include launch begin or not.
     * @param hostingType
     *            The process started type
     * @param pkgName
     *            The package name
     */
    public void launchHintCheck(int boostStaus, String hostingType,
            String pkgName, int windowingmode,
            String activityName, boolean isComeFromIdle) {
        switch (boostStaus) {
        case BoostFwkManager.BOOST_BEGIN:
            boostHintBegin(hostingType, pkgName);
            break;
        case BoostFwkManager.BOOST_END:
            if (isComeFromIdle) {
                boostHintEnd(pkgName, windowingmode);
            } else {
                boostHintEndForSpecial(pkgName, activityName);
            }
            break;
        default:
            LogUtil.mLogw(TAG, "Not found dispatcher launch action.");
            break;
        }
    }

    public void boostHintBegin(String hostingType, String pkgName) {
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "boostHintBegin for hostingType= " + hostingType
                    + "; pkgName= " + pkgName);
        }
        // We only handle hint when the process is started by launcher click.
        // And the app is not system app.
        if (hostingType != null
                && hostingType.contains(HOSTTYPE_ACTIVITY) && !Util.isSystemApp(pkgName)) {
            LogUtil.mLogd(TAG, "SBE boost:" + pkgName + " begin");
            mPkgName = pkgName;
            mIsBegin = true;
            mPowerHalWrap.notifyColdLaunchState(mIsBegin);
            mCount = 0;
        }
    }

    public void boostHintEndForSpecial(String pkgName, String activityName) {
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "boostHintResume for pkgName= " + pkgName
                    + ", activityName= " + activityName
                    + ", mLaunchConfig.isInSpecialList(pkgName) = "
                    + isInSpecialList(pkgName));
        }
        // activity resume hint is only used for special app case.
        if (mPkgName != null && mPkgName.equals(pkgName) && isInSpecialList(pkgName)) {

            ++mCount;
            int configCount = getActivityCount(pkgName);
            // When the resume activity index is the customized index.
            if (mCount == configCount) {
                // Send the really handle message
                mWorkerHandler.sendMessageDelayed(
                        mWorkerHandler.obtainMessage(
                                WorkerHandler.MSG_ACTIVITY_RESUME, pkgName),
                        LaunchConfig.ACTIVITY_CONSIDERED_IDLE);
                mCount = 0;
            }
        }
    }

    public void boostHintEnd(String pkgName, int windowingmode) {
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd(TAG, "boostHintEnd for pkgName = " + pkgName
                    + ", mPkgName = " + mPkgName
                    + ", isGameApp = " + Util.isGameApp(pkgName)
                    + ", isSpecialApp = " + isInSpecialList(pkgName)
                    + ", isFullScreen = " + Util.IsFullScreen(windowingmode));
        }
        // Only need to boost end for the idle activity require 3 conditions:
        // 3rd party app && app already boost start && activity is not fullscreen.
        // If the activity is fullscreen we think maybe it is a welcome or
        // advertising activity.
        if (mPkgName != null && mPkgName.equals(pkgName) && !Util.isSystemApp(pkgName)) {
            //If is special app, we skip it.
            if (isInSpecialList(pkgName)) {
                return;
            }

            // If is Game app no need to handle activity idle. In future will handle
            // Game app in another rule. Now we also first skip it.
            if (Util.isGameApp(pkgName)) {
                //TODO
                mIsBegin = false;
                LogUtil.mLogd(TAG, "SBE boost isGameApp:" + pkgName + " end");
                mPowerHalWrap.notifyColdLaunchState(mIsBegin);
                return;
            }

            // If the activity is fullscreen, we wil skip it.
            // So if the app has no non-fullscreen activity idle, there will be no end hint.
            if (Util.IsFullScreen(windowingmode)) {
                // Remove the above activity idle message
                mWorkerHandler.removeMessages(WorkerHandler.MSG_ACTIVITY_IDLE,
                        pkgName);
                // Send the really handle message
                mWorkerHandler.sendMessageDelayed(
                        mWorkerHandler.obtainMessage(
                                WorkerHandler.MSG_ACTIVITY_IDLE, pkgName),
                        LaunchConfig.ACTIVITY_CONSIDERED_IDLE);
            } else {
                LogUtil.mLogd(TAG, "SBE boost IsFullScreen:" + pkgName + " end");
                mIsBegin = false;
                mPowerHalWrap.notifyColdLaunchState(mIsBegin);
            }
        }
    }

    public class WorkerHandler extends Handler {
        public static final int MSG_PROCESS_START = 1;
        public static final int MSG_ACTIVITY_RESUME = 2;
        public static final int MSG_ACTIVITY_IDLE = 3;

        WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_ACTIVITY_IDLE:
            case MSG_ACTIVITY_RESUME:
                boostEnd((String) msg.obj);
                break;
            default:
                break;
            }
        }
    }

    public void boostEnd(String pkgName) {
        if (mIsBegin) {
            LogUtil.mLogd(TAG, "SBE boost:" + pkgName + " end");
            mLaunchPolicy.boostEnd(pkgName);
            mIsBegin = false;
            mPowerHalWrap.notifyColdLaunchState(mIsBegin);
            mPkgName = null;
            mCount = 0;
        }
    }


    public void initSpecialMap() {
        Iterator it = LaunchConfig.SPECIAL_MAP.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = (Entry) it.next();
            // entry.getKey() key
            // entry.getValue() value
            mSpecialPkgNames.add((String) entry.getKey());
        }
    }


    // Check if is in Special list
    private boolean isInSpecialList(String pkgName) {
        if (mSpecialPkgNames != null && mSpecialPkgNames.contains(pkgName)) {
            return true;
        }
        return false;
    }

    // Get customized end activity count for special app.
    private int getActivityCount(String pkgName) {
        if (LaunchConfig.SPECIAL_MAP.get(pkgName) != null) {
            return Integer.parseInt(LaunchConfig.SPECIAL_MAP.get(pkgName));
        }
        return LaunchConfig.COUNT_DEFAULT;
    }
}
