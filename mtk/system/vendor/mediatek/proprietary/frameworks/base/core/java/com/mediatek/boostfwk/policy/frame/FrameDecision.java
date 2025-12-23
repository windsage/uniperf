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
import android.os.Process;
import android.view.Choreographer;
import android.view.ThreadedRenderer;

import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.info.ScrollState;
import com.mediatek.boostfwk.info.ScrollState.ScrollStateListener;
import com.mediatek.boostfwk.scenario.frame.FrameScenario;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.powerhalmgr.PowerHalMgr;
import com.mediatek.powerhalmgr.PowerHalMgrFactory;
import com.mediatek.powerhalwrapper.PowerHalWrapper;

import java.util.ArrayList;
import java.util.List;

/** @hide */
public class FrameDecision
        implements ScrollState.ScrollStateListener, ActivityInfo.ActivityChangeListener{

    private static final String TAG = "FrameDecision";

    private static final Object LOCK = new Object();
    private static volatile FrameDecision mInstance = null;

    private PowerHalWrapper mPowerHalWrap = null;

    private final long INVALID_FRAME_ID = Integer.MIN_VALUE;
    //do not have traversal step
    private final int FRAME_MUTIL_BUFFER = -2;
    private final int FRAME_UNUSUAL = -1;
    private final int FRAME_BEGIN = 0;
    private final int FRAME_END = 1;
    private final int FRAME_DO_FRAME_END = 2;
    private final int FRAME_ADD_DEP = 3;

    private final int PAGE_TYPE_HWUI = 0;
    private final int PAGE_TYPE_FLUTTER = 1;
    private final int PAGE_TYPE_WEBVIEW = 2;

    private final int DEP_MODE_DEFAULT = 0;
    private final int DEP_MODE_DEL_SPECIFIC_TASK = 1;
    private final int DEP_MODE_ADD_SPECIFIC_TASK = 2;

    private int mFrameDrawnCount = 0;
    private int mFrameRequestTraversalCount = 0;
    private int mLastHWUIDoFrameStep = -1;
    private long mLastFrameBeginFrameId = INVALID_FRAME_ID;

    private boolean mEnbaleFrameTracking = false;
    private boolean mStartListenFrameHint = false;
    private boolean mForceCtrl = false;
    private boolean mClearFrameHint = false;

    //Render thread tid
    private int mRenderThreadTid = Integer.MIN_VALUE;

    //high 16 means frame types, low 16 means rescue type
    private int mFrameStatus = 0;
    private final int FRAME_STATUS_OBTAINVIEW = 1 << 0;
    private final int FRAME_STATUS_INFLATE = 1 << 1;
    private final int FRAME_STATUS_INPUT = 1 << 2;
    private final int FRAME_STATUS_FLING = 1 << 3;
    private final int FRAME_STATUS_OTHERS = 1 << 4;

    private ActivityInfo mActivityInfo;

    public static FrameDecision getInstance() {
        if (null == mInstance) {
            synchronized (FrameDecision.class) {
                if (null == mInstance) {
                    mInstance = new FrameDecision();
                }
            }
        }
        return mInstance;
    }

    private FrameDecision() {
        if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
            ScrollState.registerScrollStateListener(this);
            mActivityInfo = ActivityInfo.getInstance();
            mActivityInfo.registerActivityListener(this);
            ActivityInfo.updateSBEMask(ActivityInfo.SBE_MASK_DEBUG_HINT, LogUtil.DEBUG);
        }
    }

    //HWUI begin --------------------------------
    public void onDoFrame(boolean begin, long frameId) {
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "begin=" + begin + " mEnableDecision="
                    + mEnbaleFrameTracking + " mStartListenFrameHint=" + mStartListenFrameHint);
        }
        if (begin) {
            onDoFrameBegin(frameId);
        } else {
            onDoFrameEnd(frameId);
        }
    }

    private void onDoFrameBegin(long frameId) {
        if (!mEnbaleFrameTracking || !mStartListenFrameHint) {
            return;
        }
        mFrameStatus = 0;
        mLastFrameBeginFrameId = frameId;
        int capacity = notifySbeFrame(frameId, FRAME_BEGIN, PAGE_TYPE_HWUI);
        if (capacity <= 100 && capacity > 0) {
            mActivityInfo.recordLastFrameCapacity(capacity);
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "last capacity is " + capacity);
            }
        }
    }

    private void onDoFrameEnd(long frameId) {
        if (mClearFrameHint ||
                //enable frame tracking and there has a frame already hint begin
                (mEnbaleFrameTracking && mStartListenFrameHint
                && mLastFrameBeginFrameId != INVALID_FRAME_ID)) {

            mClearFrameHint = false;
            if (mLastHWUIDoFrameStep < Choreographer.CALLBACK_TRAVERSAL || mFrameDrawnCount == 0) {
                mFrameStatus = 0;
                //this is unusual frame, notify fpsgo
                notifySbeFrame(frameId, FRAME_UNUSUAL, PAGE_TYPE_HWUI);
            } else if (mFrameDrawnCount < mFrameRequestTraversalCount
                    && mFrameRequestTraversalCount > 1) {
                mFrameStatus = 0;
                //notify frame end, when mutil buffer, but last frame no-draw
                notifySbeFrame(frameId, FRAME_END, PAGE_TYPE_HWUI);
            } else if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_3) {
                notifySbeFrame(frameId, FRAME_DO_FRAME_END, PAGE_TYPE_HWUI);
            }
            mFrameDrawnCount = 0;
            mLastHWUIDoFrameStep = -1;
            mLastFrameBeginFrameId = INVALID_FRAME_ID;
        }
    }

    public boolean isObtianViewOrFlutter(long frameId) {
        if (mLastFrameBeginFrameId != frameId) {
            return false;
        }
        int mFrameFlags = mFrameStatus >> 16;
        return (mFrameFlags & FRAME_STATUS_OBTAINVIEW) != 0
                || (mFrameFlags & FRAME_STATUS_INFLATE) != 0;
    }

    public void onFrameStatus(FrameScenario scenario) {
        if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_3
                && mLastFrameBeginFrameId != INVALID_FRAME_ID
                && scenario.getScenarioAction() == BoostFwkManager.Draw.FRAME_OBTAIN_VIEW){
            int flag = FRAME_STATUS_OBTAINVIEW;
            //moving to high 16
            flag = flag << 16;
            updateFrameStatus(flag, true);
        }
    }

    public synchronized int updateFrameStatus(int mask, boolean add) {
        if (Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_3) {
            return 0;
        }
        if (add) {
            mFrameStatus |= mask;
        } else {
            mFrameStatus &= (~mask);
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "updateFrameStatus to " + mFrameStatus);
        }
        return mFrameStatus;
    }

    public void perfromDraw() {
        if (!mEnbaleFrameTracking || !mStartListenFrameHint) {
            return;
        }
        mFrameDrawnCount++;
    }

    public void onDoFrameStep(boolean begin, int step, FrameScenario scenario) {
        if (!mEnbaleFrameTracking || !mStartListenFrameHint) {
            return;
        }
        if (begin) {
            if (step == Choreographer.CALLBACK_TRAVERSAL) {
                mFrameRequestTraversalCount = scenario.getTraversalCallbackCount();
                if (LogUtil.DEBUG) {
                    LogUtil.traceBegin("check skip frame:" + mFrameRequestTraversalCount);
                }
                if (mFrameRequestTraversalCount > 1) {
                    //have another traversal callbackInput
                    //call HWUI skip this frame end hint
                    int skipFrameCount = (mFrameRequestTraversalCount - 1) << 16;
                    long frameId = scenario.getFrameId();
                    //mask max is 999 = 11 1110 0111
                    int frameIdMask = frameId > 0 ? ((int) (frameId % 1000) << 20) : 0;
                    if (LogUtil.DEBUG) {
                        LogUtil.traceAndMLogd(TAG, "skip frame:" + (skipFrameCount | frameIdMask
                            | ActivityInfo.SBE_MASK_SKIP_FRAME_END_HINT));
                    }
                    ThreadedRenderer.needFrameCompleteHint(ActivityInfo.SBE_MASK_SKIP_FRAME_END_HINT
                            | skipFrameCount | frameIdMask);
                }
                LogUtil.traceEnd();
            }
        } else {
            mLastHWUIDoFrameStep = step;
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "mLastHWUIDoFrameStep=" + mLastHWUIDoFrameStep);
            }
        }
    }

    private int notifySbeFrame(long frameId, int frameStatus, int pageType) {
        if (mPowerHalWrap == null) {
            mPowerHalWrap = PowerHalWrapper.getInstance();
        }
        int renderThreadTid = getRenderThreadTid();
        if (renderThreadTid < 0 ) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "invalid renderThreadTid");
            }
            return -1;
        }
        if (frameId < 0 && LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "mRenderThread "+ renderThreadTid + "hint for prefetch frame");
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceBeginAndMLogd(TAG, "hint for frame [" + pageType + "] frameId="
                + frameId + " status=" + frameStatus
                +" renderThreadTid=" + renderThreadTid + frameStatus2String());
        }

        int specificTaskSize = 0;
        int mode = DEP_MODE_DEL_SPECIFIC_TASK;
        String specificTask = "";
        if (frameStatus == FRAME_BEGIN && !mActivityInfo.isAOSPPageDesign()) {
            specificTask = mActivityInfo.getSpecificRenderStr();
            specificTaskSize = mActivityInfo.getSpecificRenderSize();
            if (mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_MAP)) {
                specificTask = "JavaScriptThrea";
                specificTaskSize = 1;
                mode = DEP_MODE_ADD_SPECIFIC_TASK;
            }
        }

        //IOCTRL call directly, more faster.
        int result = mPowerHalWrap.mtkSbeSetHwuiPolicy(mode, specificTask, specificTaskSize,
                renderThreadTid, frameStatus, frameId, mFrameStatus);
        LogUtil.traceEnd();
        return result;
    }

    private String frameStatus2String(){
        if (LogUtil.DEBUG) {
            return "";
        }
        int status = mFrameStatus >> 16;
        String result = "FrameStatus: ";
        if ((status & FRAME_STATUS_INFLATE) != 0) {
            result += " FRAME_STATUS_OBTAINVIEW |";
        }
        if ((status & FRAME_STATUS_INPUT) != 0) {
            result += " FRAME_STATUS_INFLATE |";
        }
        if ((status & FRAME_STATUS_OBTAINVIEW) != 0) {
            result += " FRAME_STATUS_INPUT |";
        }
        if ((status & FRAME_STATUS_FLING) != 0) {
            result += " FRAME_STATUS_FLING |";
        }
        if ((status & FRAME_STATUS_OTHERS) != 0) {
            result += " FRAME_STATUS_OTHERS |";
        }
        return result;
    }

    private int getRenderThreadTid() {
        if (mRenderThreadTid == Integer.MIN_VALUE) {
            mRenderThreadTid = ActivityInfo.getInstance().getRenderThreadTid();
        }
        return mRenderThreadTid;
    }
    //HWUI End --------------------------------

    @Override
    public void onScroll(boolean scrolling) {
        if (!mForceCtrl && Config.isFrameDecisionForScrolling()) {
            mEnbaleFrameTracking = scrolling;
            //if rescue not enable, ctrl frame hint
            if (!Config.isEnableFrameRescue()) {
                updateSBEMask(mEnbaleFrameTracking, ActivityInfo.SBE_MASK_FRAME_HINT);
            }
            if (!mEnbaleFrameTracking && mLastFrameBeginFrameId > 0) {
                //if last frame already begin hint, allow check frame end for no draw case
                mClearFrameHint = true;
            }
            if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4
                    && mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY)) {
                //no activity could not call onchange, do it in after scrolling change
                mStartListenFrameHint = true;
                updateSBEMask(mEnbaleFrameTracking, ActivityInfo.SBE_MASK_FRAME_HINT);
            }
        }
    }

    @Override
    public void onFling(boolean fling, boolean isAOSP) {
        updateFrameStatus(FRAME_STATUS_FLING << 16, fling);
    }

    @Override
    public void onScrollWhenTouch(boolean scrolling) {
        updateFrameStatus(FRAME_STATUS_INPUT << 16, scrolling);
    }

    @Override
    public void onChange(Context c) {
        //after first activity resume, allow listen hint.
        mStartListenFrameHint = true;
        if (Config.isFrameDecisionForAll()) {
            mEnbaleFrameTracking = true;
        } else if(Config.isDisableFrameDecision()) {
            mEnbaleFrameTracking = false;
        }
        updateSBEMask(mEnbaleFrameTracking, ActivityInfo.SBE_MASK_FRAME_HINT);
        //after activity resume get renderthreadTid again
        mRenderThreadTid = Integer.MIN_VALUE;
    }

    @Override
    public void onActivityPaused(Context c) {
        if (mEnbaleFrameTracking) {
            if (mLastFrameBeginFrameId > 0) {
                notifySbeFrame(mLastFrameBeginFrameId, FRAME_UNUSUAL, PAGE_TYPE_HWUI);
            }
            updateSBEMask(false, ActivityInfo.SBE_MASK_FRAME_HINT);
            mEnbaleFrameTracking = false;
        }
    }

    public void setStartFrameTracking(boolean start) {
        mForceCtrl = start;
        mEnbaleFrameTracking = start;
        updateSBEMask(start, ActivityInfo.SBE_MASK_FRAME_HINT);
    }

    public void setStartListenFrameHint(boolean start) {
        mStartListenFrameHint = start;
    }

    public boolean isEnableFrameTracking() {
        return mEnbaleFrameTracking;
    }

    private void updateSBEMask(boolean add, int mask) {
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "updateSBEMask: " + mask + " " + add);
        }
        ThreadedRenderer.needFrameCompleteHint(ActivityInfo.updateSBEMask(mask, add));
    }
}