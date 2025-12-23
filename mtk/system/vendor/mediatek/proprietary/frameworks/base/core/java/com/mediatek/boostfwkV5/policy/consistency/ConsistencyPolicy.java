/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2024. All rights reserved.
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

package com.mediatek.boostfwkV5.policy.consistency;

import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Trace;
import android.os.Process;

import java.util.HashMap;
import java.util.HashSet;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.scenario.consistency.ConsistencyScenario;
import com.mediatek.boostfwkV5.info.ActivityInfo;
import com.mediatek.boostfwkV5.utils.Config;
import com.mediatek.boostfwkV5.utils.LogUtil;
import com.mediatek.powerhalmgr.PowerHalMgr;
import com.mediatek.powerhalmgr.PowerHalMgrFactory;
import com.mediatek.powerhalwrapper.PowerHalWrapper;

public class ConsistencyPolicy {
    private static final String TAG = "ConsistencyPolicy";
    private static volatile ConsistencyPolicy sInstance = null;

    private HandlerThread mWorkerThread = null;
    private WorkerHandler mWorkerHandler = null;
    private final static String sTHREAD_NAME = TAG;

    private PowerHalWrapper mPowerHalWrap = null;

    private static HashMap<Integer, ConsistencyScenarioInternal> sConsisData = null;

    private static class ConsistencyScenarioInternal {
        ConsistencyScenario scenario;
        int renderTid;
    }

    public static ConsistencyPolicy getInstance() {
        if (null == sInstance) {
            synchronized (ConsistencyPolicy.class) {
                if (null == sInstance) {
                    sInstance = new ConsistencyPolicy();
                }
            }
        }
        return sInstance;
    }

    private PowerHalWrapper getPowerHalWrapper() {
        if (mPowerHalWrap == null) {
            mPowerHalWrap = PowerHalWrapper.getInstance();
        }
        return mPowerHalWrap;
    }

    public ConsistencyPolicy() {
        initThread();
        sConsisData = new HashMap<Integer, ConsistencyScenarioInternal>();
    }

    private void initThread() {
        if (mWorkerThread != null && mWorkerThread.isAlive()
                && mWorkerHandler != null) {
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.mLogi(TAG, "re-init");
            }
        } else {
            int priority = android.os.Process.THREAD_PRIORITY_FOREGROUND;
            mWorkerThread = new HandlerThread(sTHREAD_NAME, priority);
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
        public static final int MSG_CONSIS_END_WITH_TIMEOUT = 0;

        WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONSIS_END_WITH_TIMEOUT:
                    ConsistencyScenarioInternal tmpScenario
                            = (ConsistencyScenarioInternal)(msg.obj);
                    int pid = tmpScenario.scenario.getHostPID();
                    consistencyHint(false, tmpScenario, msg.arg2);

                    //After timeout, remove data from consistency list
                    boolean hasKey = queryPidFromConsistencyList(pid);
                    if (hasKey) {
                        sConsisData.remove(Integer.valueOf(pid));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void enableNormalModePolicy(ConsistencyScenario scenario, int type) {
        boolean isEnable = scenario.getBoostStatus() == BoostFwkManager.BOOST_BEGIN ? true : false;
        startConsistency(isEnable, scenario, type);
    }

    public void enableAPPLaunchResPolicy(ConsistencyScenario scenario, int type) {
        boolean isEnable = scenario.getBoostStatus() == BoostFwkManager.BOOST_BEGIN ? true : false;
        startConsistency(isEnable, scenario, type);
    }

    private void startConsistency(boolean isEnable, ConsistencyScenario scenario, int type) {
        int what = -1;
        int pid = scenario.getHostPID();
        // New start consistency come from user, we need check it already exist at list.
        // If Exist, need remove it from handler thread and timeout handler message.
        boolean hasKey = queryPidFromConsistencyList(pid);
        ConsistencyScenarioInternal tmpConsisObj = null;
        if (hasKey) {
            tmpConsisObj = sConsisData.get(pid);
            mWorkerHandler.removeMessages(WorkerHandler.MSG_CONSIS_END_WITH_TIMEOUT, tmpConsisObj);
            sConsisData.remove(pid);
        }

        if (isEnable) {
            tmpConsisObj = new ConsistencyScenarioInternal();
            tmpConsisObj.scenario = scenario;
            if (Process.myPid() == pid) {
                tmpConsisObj.renderTid = ActivityInfo.getRenderThreadTidNoCheck();
            } else {
                tmpConsisObj.renderTid = -1;
            }
            //Store the scenario in the map
            sConsisData.put(Integer.valueOf(scenario.getHostPID()), tmpConsisObj);
        }
        // User active Enable/Disable need quick response to execute, handler thread too slow.
        if (tmpConsisObj != null) {
            consistencyHint(isEnable, tmpConsisObj, type);
        }

        if (isEnable && scenario.getScenarioTimeout() > 0) {
            startConsistencyWithTimeout(tmpConsisObj, type);
        }
    }

    private void startConsistencyWithTimeout(ConsistencyScenarioInternal internal, int type) {
        mWorkerHandler.sendMessageDelayed(
            mWorkerHandler.obtainMessage(
                    WorkerHandler.MSG_CONSIS_END_WITH_TIMEOUT, 0, type, internal),
                    internal.scenario.getScenarioTimeout());
    }

    private boolean queryPidFromConsistencyList(int pid) {
        if (sConsisData == null) {
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.traceAndMLogd(TAG, "sConsisData is null when query data.");
            }
            return false;
        }

        //Check already storage data, if pid has store, need to remove at first
        boolean hasKey = sConsisData.containsKey(Integer.valueOf(pid));
        return hasKey;
    }

    private void consistencyHint(boolean isEnable, ConsistencyScenarioInternal internal, int type) {
        ConsistencyScenario scenario = internal.scenario;
        int pid = scenario.getHostPID();
        int targetTime = scenario.getTargetTime();
        int timeout = scenario.getScenarioTimeout();
        int consistencyCap = -1;
        int minCap = 0;
        int maxCap = 1024;

        if (isEnable) {
            if (type == BoostFwkManager.Consistency.NORMAL_MODE) {
                minCap = scenario.getMinCapability();
                maxCap = scenario.getMaxCapability();
            } else if (type == BoostFwkManager.Consistency.APP_LAUNCH_RESPONSE) {
                consistencyCap = getConsistencyCap(targetTime);
                minCap = consistencyCap;
                maxCap = consistencyCap;
                if (Config.isBoostFwkLogEnable()) {
                    String tmpLog = "Consistency type=APP_LAUNCH_RESPONSE, consistencyCap="
                            +consistencyCap;
                    LogUtil.traceAndMLogd(TAG, tmpLog);
                }
            }
            getPowerHalWrapper().mtkConsistencyPolicy(1, pid, minCap, maxCap);
            if (internal.renderTid > 0) {
                getPowerHalWrapper().mtkConsistencyPolicy(1, internal.renderTid, minCap, maxCap);
            }
        } else {
            //reset uclamp to default
            getPowerHalWrapper().mtkConsistencyPolicy(0, pid, minCap, maxCap);
            if (internal.renderTid > 0) {
                getPowerHalWrapper().mtkConsistencyPolicy(0, internal.renderTid, minCap, maxCap);
            }
        }
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.traceAndMLogd(TAG, "Consistency begin=" + isEnable
                    + ", pid=" + pid + ", minCap=" + minCap + ", maxCap=" + maxCap
                    + ", timeout=" + timeout + " rTid=" + internal.renderTid);
        }
    }

    private int getConsistencyCap(int targetTime) {
        int baseTargetTime = Config.getConsistencyResponse();
        int baseCapability = Config.getConsistencyCapability();
        int targetTimeChange = targetTime - baseTargetTime;
        double newCapability = 0;

        if (Config.isBoostFwkLogEnable()) {
            String tmpLog = "getConsistencyCap baseTargetTime=" + baseTargetTime +
                    ", baseCapability=" + baseCapability + ", targetTime=" + targetTime +
                    ", target diff = " + targetTimeChange;
            LogUtil.traceAndMLogd(TAG, tmpLog);
        }

        if  (Config.modeWithConsistency().equals(Config.CONSISTENCY_ENABLE)) {
            if (targetTimeChange < 0) {
                newCapability = 1024;
            } else if (targetTimeChange > 0 && targetTimeChange <= 5) {
                newCapability = baseCapability * 0.75;
            } else if (targetTimeChange > 5 && targetTimeChange <= 10) {
                newCapability = baseCapability * 0.52;
            } else if (targetTimeChange > 10 && targetTimeChange <= 15) {
                newCapability = baseCapability * 0.25;
            } else if (targetTimeChange > 15) {
                newCapability = baseCapability * 0.15;
            }

            return (int)newCapability;
        } else if (Config.modeWithConsistency()
                .equals(Config.CONSISTENCY_ENABLE_WITHOUT_PREDICT)) {
            return baseCapability;
        }

        return baseCapability;
    }
}