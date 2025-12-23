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

package com.mediatek.boostfwkV5.policy.frame;

import android.content.Context;
import android.graphics.BLASTBufferQueue;
import android.os.Trace;
import android.util.TimeUtils;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.ThreadedRenderer;
import android.view.MotionEvent;
import android.view.Choreographer.FrameData;

import com.mediatek.boostfwk.scenario.frame.FrameScenario;

import com.mediatek.boostfwkV5.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwkV5.info.ActivityInfo;
import com.mediatek.boostfwkV5.info.ScrollState;
import com.mediatek.boostfwkV5.utils.Config;
import com.mediatek.boostfwkV5.utils.LogUtil;
import com.mediatek.boostfwkV5.utils.Util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * @hide
 */
public class ScrollingFramePrefetcher {

    private static final String TAG = "ScrollingFramePrefetcher";
    public static final boolean FEATURE_ENABLE = Config.isEnableFramePrefetcher();
    public static final boolean PRE_ANIM_ENABLE = Config.isEnablePreAnimation();
    public static boolean mFeatureLocked = false;

    // MAX continuous estimation frame
    public static final int ESTIMATION_FRAME_LIMIT = 1;
    public static final long ESTIMATION_FRAME_ID = -1L;
    private static volatile ScrollingFramePrefetcher sInstance = null;

    private static final double FRAME_THRESHOLD = 1.4;
    private static final double ANIM_THRESHOLD = 1.0;
    private static final float DISPLAY_RATE_90 = 90;
    private static final int ESTIMATION_BUFFER_COUNT_LIMIT = 4;

    private int mMaxEstimationFrameCount = ESTIMATION_BUFFER_COUNT_LIMIT;
    private WeakReference<Choreographer> mChoreographer = null;
    private long mFirstFlingFrameTimeNano = -1;
    private long mLastFrameTimeNano = -1;
    private long mLastOrigFrameTimeNano = -1;
    private long mLastAnimFrameTimeNano = -1;
    private long mFrameEndTimeNano = -1;
    private long mPreAnimEndTimeNano = -1;
    private boolean mIsEstimationFrame = false;
    private boolean mIsFling = false;
    private boolean mIsPreAnim = false;
    private boolean mIsAnimDrop = false;
    private boolean mAppRequestVsync = false;
    private boolean mIsPreAnimationRunning = false;
    private boolean mPreAnimationWorkingWhenFling = false;
    private boolean mNeedScheduleNextFrame = false;
    private boolean mWaitForMarkFlingEnd = false;
    private boolean mAlreadyIncreaseBuffer = false;
    private int mEstimationFrameCount = 0;
    private long mFrameIntervalNanos = -1;
    private int mVariableRefreshRate = -1;
    private boolean mDisableForMultiOnVsync = false;
    private long mLastTimeStampNanos = -1;
    private boolean mUserTouchDownWhenFling = false;

    private final int FIRST_FRAME = 0;
    private final int DROP_FRAME = 1;
    private final int RESET_FRAME = 2;

    private static final int MAX_BUFFER_SIZE = 5;
    private static final int ERROR_CODE = -22;
    private static final int BUFFER_COUNT = 1;
    private static final int NO_CHANGED = -1;
    private int mDefaultBufferSize = NO_CHANGED;
    private int mLastBufferSize = NO_CHANGED;
    private ActivityInfo mActivityInfo = null;
    private String mCurrentAc = null;
    private boolean mForceDisbaleSFP = false;
    private boolean mAlreadyUpdateDebug = false;
    private boolean mDisbleSFPForMutiBuffer = false;

    private ScrollState.ScrollStateListener mScrollStateListener = null;
    private ActivityInfo.ActivityChangeListener mActivityChangeListener = null;
    private ScrollIdentify.TouchEventListener mTouchEventListener = null;

    private final ArrayList<String> mNameList = new ArrayList<>(){
        {
            add("NextGenHomeActivity");
            add("NextGenMenuActivity");
        }
    };

    public static ScrollingFramePrefetcher getInstance() {
        if (null == sInstance) {
            synchronized (ScrollingFramePrefetcher.class) {
                if (null == sInstance) {
                    sInstance = new ScrollingFramePrefetcher();
                }
            }
        }
        return sInstance;
    }

    private ScrollingFramePrefetcher() {
        mActivityInfo = ActivityInfo.getInstance();

        mActivityChangeListener = new ActivityInfo.ActivityChangeListener() {
            @Override
            public void onChange(Context c) {
                mForceDisbaleSFP = false;
                //A activity resume, a ctivity may pause, reset once
                resetStatusWhenScroll();
                mCurrentAc = (c == null ? "" : c.toString());
                if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                    for (String s : mNameList) {
                        if (mCurrentAc.contains(s)) {
                            mForceDisbaleSFP = true;
                            break;
                        }
                    }
                }
            }
        };
        mActivityInfo.registerActivityListener(mActivityChangeListener);

        if (FEATURE_ENABLE) {
            mScrollStateListener = new ScrollState.ScrollStateListener() {
                @Override
                public void onScroll(boolean scrolling) {
                    if (Config.mIncreaseBuffer && !scrolling && mAlreadyIncreaseBuffer) {
                        setBufferSize(
                            mActivityInfo.getThreadedRenderer(true), false);
                    }
                    if (scrolling) {
                        mMaxEstimationFrameCount = ESTIMATION_BUFFER_COUNT_LIMIT;
                    }
                }
            };

            mTouchEventListener = new ScrollIdentify.TouchEventListener() {
                @Override
                public void onTouchEvent(MotionEvent event) {
                    if (event != null) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                onUserTouchDown();
                                break;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                mUserTouchDownWhenFling = false;
                            break;
                            default:
                                break;
                        }
                    }
                }

                @Override
                public void preTouchEvent(MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            if (LogUtil.DEBUG) {
                                LogUtil.traceAndMLogd(TAG, "preUserTouchDown isFling:"
                                        + mIsFling + " SFP enable:"+ !disableSFP());
                            }
                            mUserTouchDownWhenFling = mIsFling && !disableSFP();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            break;
                        default:
                            break;
                    }
                }
            };
            ScrollIdentify.getInstance().registerTouchEventListener(mTouchEventListener);
            ScrollState.registerScrollStateListener(mScrollStateListener);
        }
    }

    private void onUserTouchDown() {
        LogUtil.traceBeginAndMLogd(TAG, "onUserTouchDown ");
        mActivityInfo.clearMutilDrawPage();
        disableAndLockSFP(true);
        LogUtil.traceEnd();
    }

    private void notifySFPresentBuffer() {
        BLASTBufferQueue queue = mActivityInfo.getBLASTBufferQueue();
        if (queue == null || !Config.SFPCouldDropBuffer()) {
            return;
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "notify touch down presentLatestFrame -> " + queue);
        }
        queue.presentLastFrame();
        Choreographer choreographer;
        if (mChoreographer != null
                && (choreographer = mChoreographer.get()) != null) {
            choreographer.resetLastFrameTimeForSFP();
        }
    }

    public void doScrollingFramePrefetcher(FrameScenario scenario) {
        checkMultiOnVsync(scenario);
        estimationFrameTime(scenario.getOrigFrameTime(), scenario.getFrameData(), scenario);
    }

    /**
     * check multi onVsync callback at one vsync, if yes, disable SFP.
     */
    private void checkMultiOnVsync(FrameScenario scenario) {
        // If multi on vsync happened, no need to check again,
        // after fling end, resetStatus will reset flag to false.
        if (mDisableForMultiOnVsync) {
            return;
        }

        if (mActivityInfo != null
                && mActivityInfo.isPage(ActivityInfo.PAGE_TYPE_AOSP_DESIGN_MULTI_FRAME)) {
            mDisableForMultiOnVsync = true;
        }

        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#doScrollingFramePrefetcher, " +
                "mDisableForMultiOnVsync = " + mDisableForMultiOnVsync);
        }
    }

    private boolean disableSFP() {
        return !FEATURE_ENABLE || !mIsFling || mForceDisbaleSFP
                || ((ScrollState.getRefreshRate() < DISPLAY_RATE_90))
                || mFeatureLocked || mDisbleSFPForMutiBuffer || mDisableForMultiOnVsync;
    }

    /**
     * To compute the new frame time when fling begin.
     */
    private void estimationFrameTime(long origFrameTimeNano, FrameData frameData,
            FrameScenario scenario) {
        long result = origFrameTimeNano;
        Choreographer choreographer = scenario.getChoreographer();

        if (disableSFP() || choreographer == null) {
            mIsPreAnim = false;
            scenario.setFrameTimeResult(result);
            if (LogUtil.DEBUG) {
                LogUtil.trace(TAG + "#disableSFP-because, feature:" +FEATURE_ENABLE +
                    "isFling= " + mIsFling +
                    "featureLocked= " + mFeatureLocked +
                    "refreshRate: " + ScrollState.getRefreshRate());
            }
            return;
        }
        if (!mAlreadyUpdateDebug) {
            mAlreadyUpdateDebug = true;
            scenario.setIsDebug(LogUtil.DEBUG);
        }
        mChoreographer = new WeakReference<Choreographer>(choreographer);
        updateFrameIntervalNanos();
        result = estimationFrameTimeInternel(origFrameTimeNano, frameData);
        scenario.setFrameTimeResult(result);
    }

    private long estimationFrameTimeInternel(long origFrameTimeNano, FrameData frameData) {
        long result = 0;
        long frameTimeNano = origFrameTimeNano;

        // The first frame recode
        if (mFirstFlingFrameTimeNano == -1) {
            updateDynaFrameStatus(FIRST_FRAME, frameTimeNano);
            result = frameTimeNano;
            if (Config.mIncreaseBuffer) {
                setBufferSize(mActivityInfo.getThreadedRenderer(true), true);
            }
        } else {
            if (LogUtil.DEBUG) {
                LogUtil.trace(TAG + "#estimationFrameTimeInternel-before, " +
                    "orig frame time = " + origFrameTimeNano +
                    ", last orig frame time = " + mLastOrigFrameTimeNano +
                    ", last frame time = " + mLastFrameTimeNano);
            }
            // if begin pre anim,
            // frame time update will use last anim time to compute the new one.
            // if no begin pre anim,
            // frame time update will use last frame time to comput the new one.
            if (isPreAnimation()) {
                mLastFrameTimeNano = mLastAnimFrameTimeNano;
            } else {
                mLastFrameTimeNano = correctionFrameTime(frameTimeNano, mLastFrameTimeNano);
            }
            result = mLastFrameTimeNano;
        }
        mLastOrigFrameTimeNano = origFrameTimeNano;
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#estimationFrameTimeInternel, " +
                "orig frame time = " + (origFrameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", frame time = " + (frameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", last frame time = " + (mLastFrameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", last anim frame time = " + (mLastAnimFrameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", result = " + (result / TimeUtils.NANOS_PER_MS));
        }
        return result;
    }

    private long predictPreAnimTime(long frameTimeNano, long frameIntervalNanos) {
        long result = frameTimeNano + frameIntervalNanos;
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#predictPreAnimTime, " +
                "orig frame Time = " + (frameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", frameIntervalNanos = " + (frameIntervalNanos / TimeUtils.NANOS_PER_MS) +
                ", next frame time = " + (result / TimeUtils.NANOS_PER_MS));
        }
        return result;
    }

    public void onFrameEnd(boolean isFrameBegin, FrameScenario scenario) {
        if (mWaitForMarkFlingEnd) {
            updateFlingStatus(false, 0);
        }

        if (disableSFP() || isFrameBegin || mChoreographer == null) {
            if (LogUtil.DEBUG) {
                LogUtil.trace(TAG + "#onFrameEnd, " + "isFrameBegin = " + isFrameBegin +
                    ", mIsFling = " + mIsFling + ", mChoreographer = " + mChoreographer);
            }
            return;
        }

        //If frame drop, reset all status to begin the first frame workflow
        long nowTimeNano = System.nanoTime();
        boolean isDropFrame = isDropFrame(mLastFrameTimeNano, nowTimeNano, FRAME_THRESHOLD);
        String dropReason = "";
        if (isDropFrame && LogUtil.DEBUG) {
            dropReason = "drop frame because this frame too long, " +
                "will insert new frame when this frame end.";
        }

        if (!isDropFrame && mFrameEndTimeNano != -1) {
            isDropFrame = isDropFrame(mFrameEndTimeNano, nowTimeNano, FRAME_THRESHOLD);
            if (isDropFrame && LogUtil.DEBUG) {
                dropReason = "drop frame because time too long between last frame end with this," +
                    "will insert new frame when this frame end.";
            }
        }
        if (isDropFrame && !mIsAnimDrop) {
            long tmpLastTime = mLastFrameTimeNano;
            updateDynaFrameStatus(DROP_FRAME, tmpLastTime);
        }
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#onFrameEnd" + ", is drop frame = " + isDropFrame +
                    ", mIsEstimationFrame = " + mIsEstimationFrame +
                    ", drop reason = " + dropReason);
        }
        mFrameEndTimeNano = nowTimeNano;

        long frameId = scenario.getFrameId();
        if (frameId == ESTIMATION_FRAME_ID) {
            //this is first estimation frame end
            mEstimationFrameCount++;
        } else {
            mEstimationFrameCount = 0;
        }
        // mIsEstimationFrame means first frame or drop frame,
        // The feature is animation time equals than frame time when estimation frame
        if (mIsEstimationFrame) {
            mIsEstimationFrame = false;
            if (LogUtil.DEBUG) {
                LogUtil.trace(TAG + "#onFrameEnd, " + "estimation frame time = " +
                    (mLastFrameTimeNano / TimeUtils.NANOS_PER_MS));
            }
            if (mEstimationFrameCount < ESTIMATION_FRAME_LIMIT && !mIsAnimDrop) {
                // Estimation frame, need to do animation in doframe
                mIsPreAnim = false;
                doEstimationFrameHook(mLastFrameTimeNano);
                if (mIsFling) {
                    doPreAnimation(mLastFrameTimeNano);
                    mIsPreAnim = true;
                }
                return;
            }
        }
        // animation time more than frame time about a interval when pre animation
        if (mIsPreAnim && !mIsAnimDrop) {
            doPreAnimation(mLastAnimFrameTimeNano);
        }
    }

    private void doPreAnimation(long frameTimeNano) {
        if (!isPreAnimationEnable() || mChoreographer == null || mForceDisbaleSFP) {
            mIsPreAnim = false;
            return;
        }
        long newAnimFrameTime = predictPreAnimTime(frameTimeNano, mFrameIntervalNanos);
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#doPreAnimation" +
                ", pre anim frame time = " + (newAnimFrameTime / TimeUtils.NANOS_PER_MS) +
                ", mLastAnimFrameTimeNano = " + (mLastAnimFrameTimeNano / TimeUtils.NANOS_PER_MS));
        }
        mIsPreAnimationRunning = true;
        long startTimeNano = System.nanoTime();
        doPreAnimationHook(newAnimFrameTime);
        long endTimeNano = System.nanoTime();
        mIsPreAnimationRunning = false;
        mPreAnimEndTimeNano = endTimeNano;
        mLastAnimFrameTimeNano = newAnimFrameTime;
        mIsAnimDrop = isDropFrame(startTimeNano, endTimeNano, ANIM_THRESHOLD);
        if (mIsAnimDrop) {
            doEstimationFrameHook(mLastAnimFrameTimeNano);
            updateDynaFrameStatus(DROP_FRAME, frameTimeNano);
        }
        mPreAnimationWorkingWhenFling = true;
    }

    private boolean isDropFrame(long start, long end, double threshold) {
        boolean isDropFrame = false;
        if (start == -1) {
            return isDropFrame;
        }

        long dropTimeNanos = (long) (mFrameIntervalNanos * threshold);
        if (end - start > dropTimeNanos) {
            isDropFrame = true;
        } else {
            isDropFrame = false;
        }
        return isDropFrame;
    }

    public boolean isPreAnimation() {
        if (!isPreAnimationEnable()) {
            return false;
        }
        return mIsPreAnim;
    }

    public void setPreAnimation(boolean isPreAnim) {
        if (!isPreAnimationEnable()) {
            mIsPreAnim = false;
        } else {
            mIsPreAnim = isPreAnim;
        }
    }

    /**
     * only enable pre-animation for AOSP page
     */
    private boolean isPreAnimationEnable() {
        return PRE_ANIM_ENABLE
                && mActivityInfo != null && mActivityInfo.isAOSPPageDesign();
    }


    private void updateFlingStatus(boolean isFling, long frameTimeNano) {
        //check should disable SFP or not
        if (isFling && mActivityInfo.isMutilDrawPage()) {
            mDisbleSFPForMutiBuffer = true;
        } else {
            mDisbleSFPForMutiBuffer = false;
        }

        //updage fling status to SFP
        if (mIsFling != isFling) {
            if (LogUtil.DEBUG) {
                LogUtil.trace(TAG + "#updateFlingStatus, " + "mIsFling = " + mIsFling +
                    ", isFling = " + isFling + " mUserTouchDownWhenFling="+mUserTouchDownWhenFling);
            }
            if (mIsPreAnimationRunning && !isFling) {
                mWaitForMarkFlingEnd = true;
            } else {
                mIsFling = isFling;
                updateDynaFrameStatus(RESET_FRAME, 0);
                if (!mIsFling) {
                    if (mUserTouchDownWhenFling) {
                        notifySFPresentBuffer();
                    }
                    mUserTouchDownWhenFling = false;
                    if (isPreAnimationEnable() && mPreAnimationWorkingWhenFling) {
                        Choreographer choreographer;
                        if (mChoreographer != null
                                && (choreographer = mChoreographer.get()) != null) {
                            choreographer.forceScheduleNexFrame();
                        }
                    }
                    mChoreographer = null;
                }
                mWaitForMarkFlingEnd = false;
                mPreAnimationWorkingWhenFling = false;
            }
        }
    }

    public boolean getFling() {
        return mIsFling;
    }

    public void setFling(boolean isFling) {
        updateFlingStatus(isFling, 0);
    }

    private void updateDynaFrameStatus(int status, long frameTimeNanos) {
        switch(status) {
            //frame begin
            case FIRST_FRAME:
                firstFrameStatus(frameTimeNanos);
                break;
            //frame end
            case DROP_FRAME:
                dropFrameStatus(frameTimeNanos);
                break;
            //doframe-animation step / pre-animation step
            case RESET_FRAME:
                resetStatus();
                break;
            default:
                break;
        }
    }

    private void firstFrameStatus(long frameTimeNanos) {
        //LogUtil.trace(TAG + "#firstFrameStatus");
        mFirstFlingFrameTimeNano = frameTimeNanos;
        mIsEstimationFrame = true;
        mLastFrameTimeNano = frameTimeNanos;
        mLastAnimFrameTimeNano = frameTimeNanos;
        mIsPreAnim = false;
        mIsAnimDrop = false;
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#firstFrameStatus, mIsEstimationFrame = " + mIsEstimationFrame);
        }
    }

    private void dropFrameStatus(long frameTimeNanos) {
        mLastFrameTimeNano = frameTimeNanos;
        mLastAnimFrameTimeNano = frameTimeNanos;
        mIsEstimationFrame = true;
        mIsPreAnim = false;
        mIsAnimDrop = false;
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#dropFrameStatus, mFirstFlingFrameTimeNano = " +
                mFirstFlingFrameTimeNano + ", mIsEstimationFrame = " + mIsEstimationFrame);
        }
    }

    private void resetStatus() {
        mFirstFlingFrameTimeNano = -1;
        mLastFrameTimeNano = -1;
        mLastOrigFrameTimeNano = -1;
        mLastAnimFrameTimeNano = -1;
        mFrameEndTimeNano = -1;
        mPreAnimEndTimeNano = -1;
        mIsEstimationFrame = false;
        mIsPreAnim = false;
        mIsAnimDrop = false;
        mLastTimeStampNanos = -1;
        mDisableForMultiOnVsync = false;
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#resetStatus, mFirstFlingFrameTimeNano = " +
                mFirstFlingFrameTimeNano);
        }
    }

    private void updateFrameIntervalNanos() {
        long temp = (long)(1000000000 / ScrollState.getRefreshRate());

        if (mFrameIntervalNanos != temp) {
            mFrameIntervalNanos = temp;
        }
    }

    private long correctionFrameTime(long frameTimeNano, long lastFrameTimeNano) {
        long targetFrameTime = lastFrameTimeNano + mFrameIntervalNanos;
        long diff = frameTimeNano - targetFrameTime;
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#correctionFrameTime, " +
                "frameTimeNano = " + frameTimeNano + ", lastFrameTimeNano = " +
                lastFrameTimeNano + ", targetFrameTime = " + targetFrameTime +
                ", diff = " + diff);
        }
        return diff > 0 ? frameTimeNano : targetFrameTime;
    }

    private void doEstimationFrameHook(long frameTimeNano) {
        Choreographer choreographer;
        if (mChoreographer == null || (choreographer = mChoreographer.get()) == null) {
            return;
        }
        if (choreographer.isEmptyCallback()) {
            LogUtil.trace(TAG + "#no draw at this time, skip.");
            return;
        }
        int buffercount = 0;
        if (Config.getBoostFwkVersion() >= Config.BOOST_FWK_VERSION_4) {
            buffercount = mActivityInfo.getBufferCountFromProducer();
        }
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#doEstimationFrameHook, " + "buffer count = " + buffercount);
        }
        if (mIsAnimDrop || buffercount < mMaxEstimationFrameCount) {
            if (LogUtil.DEBUG) {
                LogUtil.trace(TAG + "#doEstimationFrameHook, " + "do new frame");
            }
            choreographer.doEstimationFrame(frameTimeNano);
        }
        //update max if needed
        if ((buffercount - 1) > mMaxEstimationFrameCount) {
            mMaxEstimationFrameCount = buffercount - 1;
        }
    }

    private void doPreAnimationHook(long frameTimeNano) {
        Choreographer choreographer;
        if (mChoreographer == null || (choreographer = mChoreographer.get()) == null) {
            return;
        }
        choreographer.doPreAnimation(frameTimeNano, mFrameIntervalNanos);
    }

    public void disableAndLockSFP(boolean locked) {
        LogUtil.traceBegin("resetAndLockSFP");
        mFeatureLocked = locked;
        resetStatusWhenScroll();
        LogUtil.traceEnd();
    }

    private void resetStatusWhenScroll() {
        setFling(false);
        setPreAnimation(false);
        mPreAnimationWorkingWhenFling = false;
    }

    public void onRequestNextVsync() {
        mAppRequestVsync = true;
    }

    public boolean isPreAnimationRunning() {
        return mIsPreAnimationRunning;
    }

    // Set buffer size start
    private void setBufferSize(ThreadedRenderer render, boolean isScrolling) {
        if (Config.mIncreaseBuffer && render != null) {
            if (isScrolling) {
                int size;
                if (mDefaultBufferSize == NO_CHANGED) {
                    size = render.getSurface().getBufferSize();
                } else {
                    return;
                }
                if (size == ERROR_CODE) {
                    LogUtil.mLoge(TAG, "get buffer size error.");
                    return;
                }
                if (size < MAX_BUFFER_SIZE && mDefaultBufferSize == NO_CHANGED) {
                    mDefaultBufferSize = size;
                    // if now buffer size > last buffer size,
                    // we think some error with revert last buffer size,
                    // need use the last buffer size to set.
                    if (mLastBufferSize != NO_CHANGED) {
                        if (size != mLastBufferSize) {
                            mDefaultBufferSize = mLastBufferSize;
                            LogUtil.mLogd(TAG, "reset buffer size to last " + mDefaultBufferSize);
                        }
                    }
                    if (LogUtil.DEBUG) {
                        LogUtil.mLogd(TAG, "set buffer size= "+(mDefaultBufferSize + BUFFER_COUNT)
                                + ", isScrolling = " + isScrolling + ", render = " + render);
                    }
                    render.getSurface().setBufferSize(mDefaultBufferSize + BUFFER_COUNT);
                    mAlreadyIncreaseBuffer = true;
                }
            } else {
                if (mDefaultBufferSize != NO_CHANGED) {
                    render.getSurface().setBufferSize(mDefaultBufferSize);
                    mLastBufferSize = mDefaultBufferSize;
                    if (LogUtil.DEBUG) {
                        LogUtil.mLogd(TAG, "reset buffer size = " + mDefaultBufferSize
                                + ", isScrolling = " + isScrolling + ", render = " + render);
                    }
                    mDefaultBufferSize = NO_CHANGED;
                    mAlreadyIncreaseBuffer = false;
                }
            }
        } else {
            LogUtil.mLogd(TAG, "render=null, set buffer size error");
        }
    }
    // Set buffer size end.
}