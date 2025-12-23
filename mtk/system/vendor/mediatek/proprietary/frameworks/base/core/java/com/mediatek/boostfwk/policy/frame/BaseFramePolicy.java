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

import android.content.Context;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.ThreadedRenderer;

import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.info.ScrollState;
import com.mediatek.boostfwk.info.ActivityInfo.ActivityChangeListener;
import com.mediatek.boostfwk.info.ScrollState.ScrollStateListener;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.powerhalmgr.PowerHalMgr;
import com.mediatek.powerhalmgr.PowerHalMgrFactory;
import com.mediatek.powerhalwrapper.PowerHalWrapper;

/** @hide */
public class BaseFramePolicy implements ActivityChangeListener{

    static final String TAG = "FramePolicy";
    static final String THREAD_NAME = "FramePolicy";

    //powerhal define
    PowerHalMgr mPowerHalService = null;
    PowerHalWrapper mPowerHalWrap = null;
    int mPowerHandle = 0;
    static final int PERF_RES_POWERHAL_CALLER_TYPE = 0x0340c300;

    //handler define
    HandlerThread mWorkerThread = null;
    WorkerHandler mWorkerHandler = null;

    static boolean mListenFrameHint = false;
    static boolean mDisableFrameRescue = true;
    boolean mCoreServiceReady = false;
    static final float DEFAULT_CHECK_POINT = 0.5f;
    float mCheckPoint = DEFAULT_CHECK_POINT;

    ScrollStateListener mScrollStateListener = null;
    ActivityInfo mActivityInfo = null;

    private class ScrollListener implements ScrollStateListener{
        @Override
        public void onScroll(boolean scrolling) {
            onScrollStateChange(scrolling);
        }
    }

    BaseFramePolicy() {
        initThread();
        mScrollStateListener = new ScrollListener();
        mActivityInfo = ActivityInfo.getInstance();
        mActivityInfo.registerActivityListener(this);
        ScrollState.registerScrollStateListener(mScrollStateListener);
    }

    private void initThread() {
        if (mWorkerThread != null && mWorkerThread.isAlive()
                && mWorkerHandler != null) {
            LogUtil.mLogd(TAG, "re-init");
        } else {
            int priority = Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2
                    ? android.os.Process.THREAD_PRIORITY_FOREGROUND
                    //handler default priority
                    : android.os.Process.THREAD_PRIORITY_DEFAULT;
            mWorkerThread = new HandlerThread(THREAD_NAME, priority);
            mWorkerThread.start();
            Looper looper = mWorkerThread.getLooper();
            if (looper == null) {
                LogUtil.mLogd(TAG, "Thread looper is null");
            } else {
                mWorkerHandler = new WorkerHandler(looper);
            }
        }
    }

    public boolean initLimitTime(float frameIntervalTime) {
        return false;
    }

    public void onRequestNextVsync() {
    }

    public void doFrameStepHint(boolean isBegin, int step) {
    }

    public void doFrameHint(boolean isBegin, long frameId, long vsyncWakeUpTime) {
    }

    public void doFrameHint(boolean isBegin, long frameId,
            long vsyncWakeUpTime, long vsyncDeadlineNanos) {
        doFrameHint(isBegin, frameId, vsyncWakeUpTime);
    }

    public ThreadedRenderer getThreadedRenderer() {
        return mActivityInfo == null ? null : mActivityInfo.getThreadedRenderer();
    }

    protected void onScrollStateChange(boolean scrolling) {
        if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4
                && !mCoreServiceReady && mActivityInfo!= null
                && mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY)) {
            initialPolicy(null);
        }
        mDisableFrameRescue = !scrolling;
        if (!mDisableFrameRescue) {
            mListenFrameHint = true;
        }
    }

    protected void initCoreServiceInternal() {
        mPowerHalService = PowerHalMgrFactory.getInstance().makePowerHalMgr();
        mPowerHalWrap = PowerHalWrapper.getInstance();
        mPowerHalWrap.mtkInitSBEAPI();
        mCoreServiceReady = true;
    }

    protected void handleMessageInternal(Message msg) {
    }

    protected class WorkerHandler extends Handler {

        public static final int MSG_INIT_CORE_SERVICE = -1000;

        WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_INIT_CORE_SERVICE) {
                initCoreServiceInternal();
            } else {
                handleMessageInternal(msg);
            }
        }
    }

    @Override
    public void onChange(Context c) {
        initialPolicy(c);
    }

    //Context might be null
    private void initialPolicy(Context c) {
        //only once
        if (!mCoreServiceReady && mWorkerHandler != null) {
            if (c == null) {
                initCoreServiceInternal();
            } else if(!mWorkerHandler.hasMessages(WorkerHandler.MSG_INIT_CORE_SERVICE)) {
                mWorkerHandler.sendEmptyMessage(WorkerHandler.MSG_INIT_CORE_SERVICE);
            }
        }
        mCheckPoint = Config.getRescueCheckPoint();
    }

    @Override
    public void onActivityPaused(Context c) {
    }
}