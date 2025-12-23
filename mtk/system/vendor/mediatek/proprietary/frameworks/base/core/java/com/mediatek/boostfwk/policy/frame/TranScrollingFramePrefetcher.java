/**
 * Transsion top secret
 * Copyright (C) 2024 Transsion Inc.
 * Scrolling Frame Prefetcher Feature
 *
 * @authour: song.tang
 * @date: 2024/02/26
 */

package com.mediatek.boostfwk.policy.frame;

import android.content.Context;
import android.os.SystemProperties;
import android.graphics.BLASTBufferQueue;
import android.util.TimeUtils;
import android.view.Choreographer;
import android.view.Choreographer.FrameData;
import android.view.MotionEvent;
import android.view.ThreadedRenderer;
import android.os.RemoteException;
import android.os.Trace;
import android.os.SystemClock;
import com.mediatek.boostfwk.identify.scroll.ScrollIdentify;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.info.ScrollState;
import com.mediatek.boostfwk.scenario.frame.FrameScenario;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.transsion.hubcore.view.ITranSurfaceControl;
import com.transsion.hubsdk.trancare.cloudengine.ITranCloudEngineCallback;
import com.transsion.hubsdk.trancare.trancare.TranTrancareManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @hide
 */
public class TranScrollingFramePrefetcher {

    private static final String TAG = "TranScrollingFramePrefetcher";
    public static final boolean FEATURE_ENABLE = SystemProperties.get("ro.tr_perf.boostfwk.frame_prefetcher.feature.support", "0").equals("1");
    public static final boolean FEATURE_ENABLE_WHITE_LIST = SystemProperties.get("vendor.boostfwk.transsion.frameprefetcher_whitelist", "0").equals("1");
    public static boolean mFeatureLocked = false;
    // 能够连续插帧的最大帧数
    public static final int ESTIMATION_FRAME_LIMIT = 2;
    //SPD:add for special app boost of sf by Troye 202401023 start
    public static final int ESTIMATION_FRAME_LIMIT_WHITELIST = 4;
    //SPD:add for special app boost of sf by Troye 202401023 end
    public static final long ESTIMATION_FRAME_ID = -1L;
    private static final Object lock = new Object();
    private static TranScrollingFramePrefetcher sInstance = null;
    private static final float DISPLAY_RATE_90 = 90;

    private static final double FRAME_THRESHOLD = 1.4;
    private static final long ESTIMATION_BUFFER_COUNT_LIMIT = 2;

    private WeakReference<Choreographer> mChoreographer = null;
    private long mFirstFlingFrameTimeNano = -1;
    private long mLastFrameTimeNano = -1;
    private long mFrameEndTimeNano = -1;
    private boolean mIsSpecialApp = false;
    //SPD:add for special app boost of sf by Troye 202401023 start
    private boolean isWhiteList = false;
    //SPD:add for special app boost of sf by Troye 202401023 end
    private int mEstimationFrameMaxTimes  = 0;
    private long mEstimationFrameCountLimit = ESTIMATION_BUFFER_COUNT_LIMIT;
    private boolean mIsEstimationFrame = false;
    private boolean mIsFling = false;
    private boolean mAlreadyIncreaseBuffer = false;
    private boolean mIsClientComposition = false;
    private int mEstimationFrameCount = 0;
    private long mFrameIntervalNanos = -1;
    private boolean mDisableForMultiOnVsync = false;
    private long mLastTimeStampNanos = -1;
    private boolean mUserTouchDownWhenFling = false;

    private final int FIRST_FRAME = 0;
    private final int DROP_FRAME = 1;
    private final int RESET_FRAME = 2;
    private final int WHITELIST_APP = 3;

    private static final int MAX_BUFFER_SIZE = 5;
    private static final int ERROR_CODE = -22;
    private static final int BUFFER_COUNT = 1;
    private static final int NO_CHANGED = -1;
    private int mDefaultBufferSize = NO_CHANGED;
    private int mLastBufferSize = NO_CHANGED;
    private ActivityInfo mActivityInfo = null;
    private boolean mIsFirstFlingFrame = false;
    private boolean mAlreadyUpdateDebug = false;
    private boolean mDisableSFPForMultiBuffer = false;
    private String mCurrentAc = null;
    private boolean mForceDisableSFP = false;

    private static final String CLOUD_KEY = "1001000043";
    private static final String CLOUD_ENABLE_KEY = "frame_prefetcher";
    private boolean mCloudEnable = true;

    private ScrollState.ScrollStateListener mScrollStateListener = null;
    private ActivityInfo.ActivityChangeListener mActivityChangeListener = null;
    private ScrollIdentify.TouchEventListener mTouchEventListener = null;

    //add for pre-animation by shijun.tang start
    private boolean mIsPreAnim = false;
    private boolean mIsInPreAnimationWhiteList = false;
    private static final boolean PRE_ANIM_ENABLE = Config.isEnablePreAnimation();
    //add for pre-animation by shijun.tang end

    //add for sbe cloud by dewang.tan start
    private ArrayList<String> PKG_PRE_ANIMATION_WHITE_LIST;
    private ArrayList<String> SPECIAL_APP_LIST;
    private ArrayList<String> SF_SPECIAL_ACTIVITY_LIST;
    //add for sbe cloud by dewang.tan end

    //Activity 黑名单
    private final ArrayList<String> mNameList = new ArrayList<>(){
        {
            add("NextGenHomeActivity");
            add("NextGenMenuActivity");
            add("com.google.android.apps.youtube.music.activities.MusicActivity");
        }
    };

    public static TranScrollingFramePrefetcher getInstance() {
        if (null == sInstance) {
            synchronized (lock) {
                if (null == sInstance) {
                    sInstance = new TranScrollingFramePrefetcher();
                }
            }
        }
        return sInstance;
    }

    private TranScrollingFramePrefetcher() {
        if (FEATURE_ENABLE) {
            mActivityInfo = ActivityInfo.getInstance();

            mActivityChangeListener = new ActivityInfo.ActivityChangeListener() {
                @Override
                public void onChange(Context c) {
                    mForceDisableSFP = false;
                    resetStatusWhenScroll();
                    mCurrentAc = (c == null ? "" : c.toString());
                    for (String s : mNameList) {
                        if (mCurrentAc.contains(s)) {
                            mForceDisableSFP = true;
                            break;
                        }
                    }
                    //SPD:add for special app boost of sf by Troye 202401023 start
                    if (FEATURE_ENABLE_WHITE_LIST) {
                        if (SF_SPECIAL_ACTIVITY_LIST != null && !SF_SPECIAL_ACTIVITY_LIST.isEmpty()) {
                            isWhiteList = isInList(mCurrentAc, SF_SPECIAL_ACTIVITY_LIST);
                        } else {
                            isWhiteList = isInList(mCurrentAc, Config.SF_SPECIAL_ACTIVITY_LIST);
                        }
                    }
                    //SPD:add for special app boost of sf by Troye 202401023 end
                    String packageName = (c == null ? "" : c.getPackageName());
                    if (SPECIAL_APP_LIST != null && !SPECIAL_APP_LIST.isEmpty()) {
                        mIsSpecialApp = SPECIAL_APP_LIST.contains(packageName);
                    } else {
                        mIsSpecialApp = Config.SPECIAL_APP_LIST.contains(packageName);
                    }

                    // add for pre-animation by shijun.tang start
                    if (PKG_PRE_ANIMATION_WHITE_LIST != null && !PKG_PRE_ANIMATION_WHITE_LIST.isEmpty()) {
                        mIsInPreAnimationWhiteList = isInList(c.getPackageName(),PKG_PRE_ANIMATION_WHITE_LIST);
                    } else {
                        mIsInPreAnimationWhiteList = Config.isInPreAnimationWhiteList(c.getPackageName());
                    }
                    setPreAnimation(mIsInPreAnimationWhiteList);
                    // add for pre-animation by shijun.tang end
                }
            };
            mActivityInfo.registerActivityListener(mActivityChangeListener);

            mScrollStateListener = new ScrollState.ScrollStateListener() {
                @Override
                public void onScroll(boolean scrolling) {
                    if (!scrolling && mAlreadyIncreaseBuffer) {
                        getInstance().setBufferSize(
                                mActivityInfo.getThreadedRenderer(true), false);
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

            CloudEngineCallback callback = new CloudEngineCallback();
            try {
                TranTrancareManager.regCloudEngineCallback(CLOUD_KEY, "v1.x", callback);
            } catch (Exception e) {
                LogUtil.mLoge(TAG, "Failed to register cloud engine callback: " + e.getMessage());
                // 当系统服务死亡时捕获异常
            }
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
        if (queue != null) {
            queue.presentLastFrame();
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "notify touch down presentLatestFrame -> " + queue);
        }
        Choreographer choreographer;
        if (mChoreographer != null
                && (choreographer = mChoreographer.get()) != null) {
            choreographer.resetLastFrameTimeForSFP();
        }
    }

    public boolean doScrollingFramePrefetcher(FrameScenario scenario) {
        if (!FEATURE_ENABLE) {
            return false;
        }
        checkMultiOnVsync(scenario);
        estimationFrameTime(scenario.getOrigFrameTime(), scenario.getFrameData(), scenario);
        return true;
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

        long frameTimeStampNanos = scenario.getVsyncWakeupTimeNanos();
        if (frameTimeStampNanos < 0) {
            return;
        }

        if (mLastTimeStampNanos != -1 && mLastTimeStampNanos == frameTimeStampNanos
                && scenario.getFrameId() > ESTIMATION_FRAME_ID) {
            mDisableForMultiOnVsync = true;
        }
        LogUtil.trace(TAG + "#doScrollingFramePrefetcher, " +
                "frameTimeStampNanos = " + frameTimeStampNanos +
                ", mLastTimeStampNanos = " + mLastTimeStampNanos +
                ", mDisableForMultiOnVsync = " + mDisableForMultiOnVsync);
        if (scenario.getFrameId() > ESTIMATION_FRAME_ID) {
            mLastTimeStampNanos = frameTimeStampNanos;
        }
    }

    private boolean disableSFP() {
        return !FEATURE_ENABLE || !mCloudEnable || !mIsFling || mFeatureLocked || mDisableSFPForMultiBuffer
                || mDisableForMultiOnVsync || (mIsClientComposition && !mIsFirstFlingFrame) || mForceDisableSFP;
    }

    /**
     * To compute the new frame time when fling begin.
     */
    private void estimationFrameTime(long origFrameTimeNano, FrameData frameData,
            FrameScenario scenario) {
        long result = origFrameTimeNano;
        Choreographer choreographer = scenario.getChoreographer();

        if (disableSFP() || choreographer == null) {
            scenario.setFrameTimeResult(result);
            LogUtil.trace(TAG + "#disableSFP-because, feature:" +FEATURE_ENABLE +
                    "isFling= " + mIsFling +
                    "featureLocked= " + mFeatureLocked +
                    "refreshRate: " + ScrollState.getRefreshRate());
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
            setBufferSize(mActivityInfo.getThreadedRenderer(true), true);
        } else {
            mLastFrameTimeNano = correctionFrameTime(frameTimeNano, mLastFrameTimeNano);
            result = mLastFrameTimeNano;
        }
        LogUtil.trace(TAG + "#estimationFrameTimeInternel, " +
                "orig frame time = " + (origFrameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", frame time = " + (frameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", last frame time = " + (mLastFrameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", result = " + (result / TimeUtils.NANOS_PER_MS));
        return result;
    }

    public boolean onFrameEnd(boolean isFrameBegin, FrameScenario scenario) {
        if (!FEATURE_ENABLE) {
            return false;
        }
        if (disableSFP() || isFrameBegin || mChoreographer == null) {
            LogUtil.traceMessage(TAG + "#onFrameEnd, " + "isFrameBegin = " + isFrameBegin +
                    ", mIsFling = " + mIsFling + ", mChoreographer = " + mChoreographer);
            return true;
        }

        //If frame drop, reset all status to begin the first frame workflow
        long nowTimeNano = System.nanoTime();
        boolean isDropFrame = isDropFrame(mLastFrameTimeNano, nowTimeNano, FRAME_THRESHOLD);
        String dropReason = "drop frame because this is first frame";
        if (isDropFrame) {
            dropReason = "drop frame because this frame too long, " +
                    "will insert new frame when this frame end.";
        }

        if (!isDropFrame && mFrameEndTimeNano != -1) {
            isDropFrame = isDropFrame(mFrameEndTimeNano, nowTimeNano, FRAME_THRESHOLD);
            if (isDropFrame) {
                dropReason = "drop frame because time too long between last frame end with this," +
                        "will insert new frame when this frame end.";
            }
        }
        long frameId = scenario.getFrameId();
        if (frameId == ESTIMATION_FRAME_ID) {
            //this is first estimation frame end
            mEstimationFrameCount++;
        } else {
            mEstimationFrameCount = 0;
        }

        long buffercount = mActivityInfo.getBufferCountFromProducer();
        if (isWhiteList && buffercount <= ESTIMATION_FRAME_LIMIT_WHITELIST){
            //SPD:add for special app boost of sf by Troye 202401023 start
            dropReason = "drop frame because it is in Wechat and buffercount is less than 4";
            updateDynaFrameStatus(WHITELIST_APP, mLastFrameTimeNano);
            //SPD:add for special app boost of sf by Troye 202401023 end
        } else if (isDropFrame || (mEstimationFrameCount == 0 && !mIsFirstFlingFrame && mEstimationFrameMaxTimes > 1)) {
            if (!isDropFrame) {
                mEstimationFrameMaxTimes--;
                dropReason = "drop frame because this is special estimation frame";
            }
            long tmpLastTime = mLastFrameTimeNano;
            updateDynaFrameStatus(DROP_FRAME, tmpLastTime);
        }
        mFrameEndTimeNano = nowTimeNano;

        //modify for pre-animation by shijun.tang start
        if (mIsFling && mIsInPreAnimationWhiteList) {
            doPreAnimation(mLastFrameTimeNano);
        }
        //modify for pre-animation by shijun.tang end

        // mIsEstimationFrame means first frame or drop frame,
        // The feature is animation time equals than frame time when estimation frame
        if (mIsEstimationFrame) {
            mIsEstimationFrame = false;
            if (mIsFirstFlingFrame) {
                mIsFirstFlingFrame = false;
            }
            LogUtil.traceMessage(TAG + "#onFrameEnd, " + ",is drop frame = " + isDropFrame
                    + ",drop reason = " + dropReason);
            if (isWhiteList && mEstimationFrameCount < ESTIMATION_FRAME_LIMIT_WHITELIST){
                //SPD:add for special app boost of sf by Troye 202401023 start
                doEstimationFrameHook(mLastFrameTimeNano);
                //SPD:add for special app boost of sf by Troye 202401023 end
                return true;
            } else if (mEstimationFrameCount < ESTIMATION_FRAME_LIMIT) {
                // Estimation frame, need to do animation in doframe
                doEstimationFrameHook(mLastFrameTimeNano);
                return true;
            }
        }
        return true;
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

    private void updateFlingStatus(boolean isFling, long frameTimeNano) {
        //check should disable SFP or not
        mDisableSFPForMultiBuffer = isFling && mActivityInfo.isMutilDrawPage();

        //update fling status to SFP
        if (mIsFling != isFling) {
            LogUtil.traceBeginAndMLogd(TAG,"#updateFlingStatus, " + "mIsFling = " + mIsFling +
                    ", isFling = " + isFling + ", mUserTouchDownWhenFling = " + mUserTouchDownWhenFling);
            mIsFling = isFling;
            updateDynaFrameStatus(RESET_FRAME, 0);
            if (!mIsFling) {
                if (mUserTouchDownWhenFling) {
                    notifySFPresentBuffer();
                } else {
                    Choreographer choreographer;
                    if (mChoreographer != null
                            && (choreographer = mChoreographer.get()) != null) {
                        choreographer.resetLastFrameTimeForSFP();
                    }
                }
                mUserTouchDownWhenFling = false;
                mChoreographer = null;
            }
        }
    }

    public void updateComposition(boolean isClientComposition) {
        // 如果是GPU合成+高刷，此时容易引起SF阻塞，暂时不进行插帧
        mIsClientComposition = isClientComposition && (ScrollState.getRefreshRate() >= DISPLAY_RATE_90);
    }

    public boolean getFling() {
        return mIsFling;
    }

    public void setFling(boolean isFling) {
        // modify for pre-animation by shijun.tang start
        if (!FEATURE_ENABLE && !PRE_ANIM_ENABLE) {
            return;
        }
        // modify for pre-animation by shijun.tang end
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
            case WHITELIST_APP:
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
        mFirstFlingFrameTimeNano = frameTimeNanos;
        mIsEstimationFrame = true;
        mLastFrameTimeNano = frameTimeNanos;
        mIsFirstFlingFrame = true;
        LogUtil.trace(TAG + "#firstFrameStatus, mLastFrameTimeNano = " + mLastFrameTimeNano);
    }

    private void dropFrameStatus(long frameTimeNanos) {
        mLastFrameTimeNano = frameTimeNanos;
        mIsEstimationFrame = true;
        LogUtil.trace(TAG + "#dropFrameStatus, mLastFrameTimeNano = " + mLastFrameTimeNano);
    }

    private void resetStatus() {
        if (mIsSpecialApp && (ScrollState.getRefreshRate() >= DISPLAY_RATE_90)) {
            mEstimationFrameMaxTimes = ESTIMATION_FRAME_LIMIT;
            mEstimationFrameCountLimit = ESTIMATION_FRAME_LIMIT + 1;
        } else {
            mEstimationFrameMaxTimes = 0;
            mEstimationFrameCountLimit = ESTIMATION_FRAME_LIMIT;
        }
        mFirstFlingFrameTimeNano = -1;
        mLastFrameTimeNano = -1;
        mFrameEndTimeNano = -1;
        mIsEstimationFrame = false;
        mLastTimeStampNanos = -1;
        mDisableForMultiOnVsync = false;
        LogUtil.trace(TAG + "#resetStatus");
    }

    private void updateFrameIntervalNanos() {
        long temp = (long)(1000000000 / ScrollState.getRefreshRate());

        if (mFrameIntervalNanos != temp) {
            LogUtil.traceMessage(TAG + "#updateFrameIntervalNanos, newTimeNano = " + temp);
            mFrameIntervalNanos = temp;
        }
    }

    private long correctionFrameTime(long frameTimeNano, long lastFrameTimeNano) {
        long targetFrameTime = lastFrameTimeNano + mFrameIntervalNanos;
        final long now = System.nanoTime();
        long diff = frameTimeNano - targetFrameTime;
        LogUtil.traceMessage(TAG + "#correctionFrameTime, "
                + "frameTimeNano = " + (frameTimeNano / TimeUtils.NANOS_PER_MS)
                + ", lastFrameTimeNano = " + (lastFrameTimeNano / TimeUtils.NANOS_PER_MS)
                + ", targetFrameTime = " + ((lastFrameTimeNano + mFrameIntervalNanos)/ TimeUtils.NANOS_PER_MS)
                + ", now = " + (now / TimeUtils.NANOS_PER_MS) + ", diff = " + diff);
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
        long buffercount = mActivityInfo.getBufferCountFromProducer();
        boolean isWhite = isWhiteList && buffercount <= ESTIMATION_FRAME_LIMIT_WHITELIST;
        LogUtil.traceMessage(TAG + "#doEstimationFrameHook " + "buffer count = " + buffercount
                + " mEstimationFrameCountLimit = " + mEstimationFrameCountLimit + " frameTimeNano = " + frameTimeNano);
        if (buffercount <= mEstimationFrameCountLimit || isWhite) {
            mIsFirstFlingFrame = false;
            choreographer.doEstimationFrame(frameTimeNano);
        }
    }

    public void disableAndLockSFP(boolean locked) {
        if (!FEATURE_ENABLE) {
            return;
        }
        LogUtil.traceBegin(TAG + " resetAndLockSFP mFeatureLocked");
        mFeatureLocked = locked;
        if (locked) {
            resetStatusWhenScroll();
        }
        LogUtil.traceEnd();
    }

    private void resetStatusWhenScroll() {
        setFling(false);
    }

    // Set buffer size start
    private void setBufferSize(ThreadedRenderer render, boolean isScrolling) {
        if (render != null) {
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
                    LogUtil.traceAndLog(TAG, "set buffer size = " + (mDefaultBufferSize + BUFFER_COUNT)
                            + ", isScrolling = " + isScrolling + ", render = " + render);
                    render.getSurface().setBufferSize(mDefaultBufferSize + BUFFER_COUNT);
                    mAlreadyIncreaseBuffer = true;
                }
            } else {
                if (mDefaultBufferSize != NO_CHANGED) {
                    render.getSurface().setBufferSize(mDefaultBufferSize);
                    mLastBufferSize = mDefaultBufferSize;
                    LogUtil.mLogd(TAG, "reset buffer size = " + mDefaultBufferSize
                            + ", isScrolling = " + isScrolling + ", render = " + render);
                    mDefaultBufferSize = NO_CHANGED;
                    mAlreadyIncreaseBuffer = false;
                }
            }
        } else {
            LogUtil.mLogd(TAG, "render=null, set buffer size error");
        }
    }
    // Set buffer size end.

    //add for pre-animation by shijun.tang start
    private void doPreAnimation(long frameTimeNano) {
        if (!PRE_ANIM_ENABLE || mChoreographer == null) {
            return;
        }
        long newAnimFrameTime = predictPreAnimTime(frameTimeNano, mFrameIntervalNanos);
        doPreAnimationHook(newAnimFrameTime);
    }

    private void doPreAnimationHook(long frameTimeNano) {
        Choreographer choreographer;
        if (mChoreographer == null || (choreographer = mChoreographer.get()) == null) {
            return;
        }
        LogUtil.trace(TAG + "#doPreAnimationHook, " + frameTimeNano);
        choreographer.doPreAnimation(frameTimeNano, mFrameIntervalNanos);
    }

    private long predictPreAnimTime(long frameTimeNano, long frameIntervalNanos) {
        long result = frameTimeNano + frameIntervalNanos;
        if (LogUtil.DEBUG) {
            LogUtil.trace(TAG + "#predictPreAnimTime, " +
                "orig frame Time = " + (frameTimeNano / TimeUtils.NANOS_PER_MS) +
                ", frameIntervalNanos = " + (frameIntervalNanos / TimeUtils.NANOS_PER_MS) +
                ", next frame time = " + (result / TimeUtils.NANOS_PER_MS) + ", current time = " + (System.nanoTime()/TimeUtils.NANOS_PER_MS));
        }
        return result;
    }

    public void setPreAnimation(boolean isPreAnim) {
        if (!PRE_ANIM_ENABLE) {
            mIsPreAnim = false;
        }
        mIsPreAnim = isPreAnim;
    }

    public boolean isPreAnimation() {
        if (!PRE_ANIM_ENABLE) {
            return false;
        }
        return mIsPreAnim;
    }
    //add for pre-animation by shijun.tang end

    //add for sbe cloud by dewang.tan start
    class CloudEngineCallback extends ITranCloudEngineCallback.Stub {
        @Override
        public void onUpdate(String key, boolean fileState, String config) throws RemoteException {
            if (CLOUD_KEY.equals(key)) {

                LogUtil.mLogd(TAG, "[onUpdate] key = " + key + " filestate = " + fileState + " config = " + config);
                updateConfig(key, config);
                TranTrancareManager.feedBack(key, true);

            }

        }
    }

    private void updateConfig(String cloudKey, String config) {
        mCloudEnable = TranTrancareManager.getAsBoolean(cloudKey, CLOUD_ENABLE_KEY, false);
        String[] preAnimationWhiteList = TranTrancareManager.getAsStringArray(cloudKey, "PKG_PRE_ANIMATION_WHITE_LIST", false);
        if (preAnimationWhiteList != null) {
            PKG_PRE_ANIMATION_WHITE_LIST = new ArrayList<>(Arrays.asList(preAnimationWhiteList));
        }
        String[] specialAppList = TranTrancareManager.getAsStringArray(cloudKey, "SPECIAL_APP_LIST", false);
        if (specialAppList != null) {
            SPECIAL_APP_LIST = new ArrayList<>(Arrays.asList(specialAppList));
        }
        String[] sfSpecialActivityList = TranTrancareManager.getAsStringArray(cloudKey, "SF_SPECIAL_ACTIVITY_LIST", false);
        if (sfSpecialActivityList != null) {
            SF_SPECIAL_ACTIVITY_LIST = new ArrayList<>(Arrays.asList(sfSpecialActivityList));
        }
    }

    private static boolean isInList(String name, ArrayList<String> list) {
        if (name == null) {
            return false;
        }
        for (String item : list) {
            if (name.contains(item)) {
                return true;
            }
        }
        return false;
    }
    //add for sbe cloud by dewang.tan end
}