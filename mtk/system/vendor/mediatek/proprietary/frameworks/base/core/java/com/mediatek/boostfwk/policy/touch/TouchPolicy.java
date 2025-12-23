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
package com.mediatek.boostfwk.policy.touch;

import android.content.Context;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.mediatek.boostfwk.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.info.ScrollState;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.utils.Util;
import com.mediatek.powerhalmgr.PowerHalMgr;
import com.mediatek.powerhalmgr.PowerHalMgrFactory;

/** @hide */
public class TouchPolicy
        implements ScrollIdentify.TouchEventListener, ScrollState.ScrollStateListener,
                ActivityInfo.ActivityChangeListener {

    private static final String TAG = "TouchPolicy";
    private static final String THREAD_NAME = TAG;
    private static final boolean ENABLE_TOUCH_POLICY_FOR_ALL = false;

    private HandlerThread mWorkerThread = null;
    private WorkerHandler mWorkerHandler = null;

    private ActivityInfo mActivityInfo = null;
    private boolean mIsSBBTrigger = false;
    private PowerHalMgr mPowerHalService = PowerHalMgrFactory.getInstance().makePowerHalMgr();
    private int mPowerHandle = 0;
    private String mActivityStr = "";
    private long mLastTriggerTime = -1L;
    private long mLastTouchDownTime = -1L;
    private int mReleaseDuration = -1;
    private final long mResetBufferTimeMS = 100;

    private int mPid = Integer.MIN_VALUE;

    private static final int PERF_RES_SCHED_SBB_GROUP_SET = 0x01444200;
    private static final int PERF_RES_SCHED_SBB_GROUP_UNSET = 0x01444300;
    private static final int PERF_RES_SCHED_SBB_ACTIVE_RATIO = 0x01444600;
    private static final int PERF_RES_SCHED_UCLAMP_MIN_TA = 0x01408300;
    private static final int PERF_RES_POWERHAL_CALLER_TYPE = 0x0340c300;
    private static final int sDEFAULT_ACTIVE_RATIO = 70;
    private static final int sDEFAULT_UCLAMP_TA = 25;
    private boolean mIsLongTimePages = false;
    public static final Map<String, Long> LONG_TIME_PAGES = new HashMap<String, Long>() {
        {
            put("NebulaActivity", 999999L);
        }
    };

    private static volatile TouchPolicy mInstance = null;
    private static final Object LOCK = new Object();

    public static TouchPolicy getInstance() {
        if (null == mInstance) {
            synchronized (TouchPolicy.class) {
                if (null == mInstance) {
                    mInstance = new TouchPolicy();
                }
            }
        }
        return mInstance;
    }

    private TouchPolicy() {
        initThread();
        ScrollIdentify.getInstance().registerTouchEventListener(this);
        ScrollState.registerScrollStateListener(this);
    }

    private void initThread() {
        if (mWorkerThread != null && mWorkerThread.isAlive()
                && mWorkerHandler != null) {
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.mLogi(TAG, "re-init");
            }
        } else {
            mWorkerThread = new HandlerThread(THREAD_NAME);
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
        public static final int MSG_ENABLE_SBB = 1;
        public static final int MSG_RESET_SBB = 2;

        WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENABLE_SBB:
                    enableSBBInternal(-1);
                    break;
                case MSG_RESET_SBB:
                    resetSBBInternal(msg.obj != null);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
        if (!Config.isEnableTouchPolicy() ||
                Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_1) {
            return;
        }
        //touch down and fps > 60 enable policy
        if (ScrollState.getRefreshRate() <= 61f
                || event.getAction() != MotionEvent.ACTION_DOWN || !Util.isMainThread()) {
            return;
        }

        if (mActivityInfo == null) {
            mActivityInfo = ActivityInfo.getInstance();
            mActivityInfo.registerActivityListener(this);
        }

        //try init if needed
        mActivityInfo.getPageType();

        if (((!mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY)
                && ENABLE_TOUCH_POLICY_FOR_ALL) || mActivityInfo.isSpecialPageDesign())
                && !mIsLongTimePages) {
            long diff = System.currentTimeMillis() - mLastTriggerTime;
            mLastTouchDownTime = System.currentTimeMillis();
            if (diff < mReleaseDuration) {
                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, "onTouchEvent for return" + diff);
                }
                return;
            }
            enableSBB();
        }
    }

    private void enableSBB() {
        if (mPid == Integer.MIN_VALUE) {
            mPid = Process.myPid();
        }
        mLastTriggerTime = System.currentTimeMillis();
        mWorkerHandler.removeMessages(WorkerHandler.MSG_ENABLE_SBB, null);
        mWorkerHandler.sendEmptyMessage(WorkerHandler.MSG_ENABLE_SBB);
    }

    private void enableSBBInternal(int duration) {
        if (mReleaseDuration < 0) {
            mReleaseDuration = (int)generateNewDuration();
        }
        int enableDuration = duration <= 0 ? mReleaseDuration : duration;
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "enableSBB for "+mActivityStr
                    + " with duration="+mReleaseDuration +" pid="+mPid);
        }

        int perf_lock_rsc[];
        if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_3) {
            if (mIsLongTimePages) {
                mIsSBBTrigger = true;
                perf_lock_rsc = new int[] {PERF_RES_SCHED_UCLAMP_MIN_TA, sDEFAULT_UCLAMP_TA,
                                          PERF_RES_POWERHAL_CALLER_TYPE, 1};
                acquirePowerhal(perf_lock_rsc, (int)enableDuration);
            }
        } else {
            mIsSBBTrigger = true;
            if (mIsLongTimePages) {
                perf_lock_rsc = new int[] {PERF_RES_SCHED_SBB_GROUP_SET, mPid,
                    PERF_RES_SCHED_SBB_ACTIVE_RATIO, sDEFAULT_ACTIVE_RATIO,
                    PERF_RES_POWERHAL_CALLER_TYPE, 1,
                    PERF_RES_SCHED_UCLAMP_MIN_TA, sDEFAULT_UCLAMP_TA};
            } else {
                perf_lock_rsc = new int[] {PERF_RES_SCHED_SBB_GROUP_SET, mPid,
                    PERF_RES_POWERHAL_CALLER_TYPE, 1,
                    PERF_RES_SCHED_SBB_ACTIVE_RATIO, sDEFAULT_ACTIVE_RATIO};
            }
            acquirePowerhal(perf_lock_rsc, (int)enableDuration);
        }

        mWorkerHandler.removeMessages(WorkerHandler.MSG_RESET_SBB, null);
        mWorkerHandler.sendMessageDelayed(
            mWorkerHandler.obtainMessage(WorkerHandler.MSG_RESET_SBB, true), enableDuration);
    }

    private long generateNewDuration() {
        long duration = Config.getTouchDuration();
        Set<String> list = LONG_TIME_PAGES.keySet();
        for (String activity : list) {
            if (mActivityStr.contains(activity)) {
                duration = LONG_TIME_PAGES.get(activity);
                mIsLongTimePages = true;
                break;
            }
        }
        return duration;
    }

    private void resetSBBInternal(boolean continueSbb) {
        long diff = 0L;
        //if long time pages, auto continue.
        if (continueSbb && (mIsLongTimePages ||
                //should continue touch policy
                mLastTouchDownTime > 0 && (diff = System.currentTimeMillis() - mLastTouchDownTime)
                            < (mReleaseDuration - mResetBufferTimeMS))) {
            LogUtil.traceAndMLogd(TAG, "continueSBB");
            mLastTriggerTime = System.currentTimeMillis();
            enableSBBInternal((int)(mReleaseDuration - diff));
        } else {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "resetSBB for "+mActivityStr);
            }
            releasePowerHandle();
            mLastTriggerTime =  0L;
            mIsSBBTrigger = false;
            mIsLongTimePages = false;
        }
    }

    @Override
    public void onScroll(boolean scrolling) {
        if (scrolling && !mIsLongTimePages) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "onScroll for "
                    +mActivityStr + " with scrolling="+scrolling);
            }
            resetSBB();
        }
    }

    private void resetSBB() {
        if (mIsSBBTrigger) {
            mWorkerHandler.removeMessages(WorkerHandler.MSG_RESET_SBB, null);
            mWorkerHandler.sendMessageAtFrontOfQueue(
                mWorkerHandler.obtainMessage(WorkerHandler.MSG_RESET_SBB));
        }
    }

    @Override
    public void onChange(Context c) {
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "onChange for " + c + " "+mActivityStr);
        }
        if (c != null && !c.toString().equals(mActivityStr)) {
            mActivityStr = c.toString();
            resetSBB();
            mReleaseDuration = -1;
            generateNewDuration();
            if (mIsLongTimePages) {
                enableSBB();
            }
        }
    }

    @Override
    public void onActivityPaused(Context c) {
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "onAllActivityPause for " + c + " "+mActivityStr);
        }
        resetSBB();
        mActivityStr = "";
    }

    private void acquirePowerhal(int perf_lock_rsc[], int duration) {
        if (null != mPowerHalService) {
            mPowerHandle = mPowerHalService.perfLockAcquire(mPowerHandle,
                    duration, perf_lock_rsc);
        }
    }

    private void releasePowerHandle() {
        if (null != mPowerHalService && mPid != Integer.MIN_VALUE) {
            int perf_lock_rsc[] = new int[] {PERF_RES_SCHED_SBB_GROUP_UNSET, mPid,
                    PERF_RES_POWERHAL_CALLER_TYPE, 1};
            acquirePowerhal(perf_lock_rsc, 1);
            mPowerHalService.perfLockRelease(mPowerHandle);
        }
    }
}
