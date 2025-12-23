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

package com.mediatek.boostfwkV5.info;


import android.app.ActivityThread;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.view.Display;
import android.view.DisplayInfo;
import android.util.TimeUtils;
import android.os.Handler;
import android.os.Trace;

import com.mediatek.boostfwkV5.info.ActivityInfo;
import com.mediatek.boostfwkV5.info.ScrollMbrainSync;
import com.mediatek.boostfwkV5.info.ScrollMbrainSync.T3Data;
import com.mediatek.boostfwkV5.utils.LogUtil;
import com.mediatek.boostfwkV5.utils.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ScrollState {

    private static final String TAG = "ScrollState";
    private static final int SCROL_TRACE_COOKIE = ScrollState.class.hashCode() + 33333;
    private static final int TOUCH_TRACE_COOKIE = ScrollState.class.hashCode() + 55555;

    // refresh rate define
    public static final float REFRESHRATE_1 = 1.0f;
    public static final float REFRESHRATE_10 = 10.0f;
    public static final float REFRESHRATE_24 = 24.0f;
    public static final float REFRESHRATE_30 = 30.0f;
    public static final float REFRESHRATE_48 = 48.0f;
    public static final float REFRESHRATE_60 = 60.0f;
    public static final float REFRESHRATE_90 = 90.0f;
    public static final float REFRESHRATE_120 = 120.0f;
    public static final float REFRESHRATE_144 = 144.0f;
    public static final float REFRESHRATE_240 = 240.0f;
    public static final float REFRESHRATE_480 = 480.0f;
    private static final float[] PREDEINE_RATES = {
            REFRESHRATE_1, REFRESHRATE_10, REFRESHRATE_24,
            REFRESHRATE_30, REFRESHRATE_48, REFRESHRATE_60,
            REFRESHRATE_90, REFRESHRATE_120, REFRESHRATE_144,
            REFRESHRATE_240, REFRESHRATE_480
    };

    private static CopyOnWriteArrayList<ScrollStateListener> mScrollStateListeners
            = new CopyOnWriteArrayList<>();
    private static CopyOnWriteArrayList<RefreshRateChangedListener> mRefreshRateListeners
            = new CopyOnWriteArrayList<>();
    private static DisplayManager.DisplayListener mDisplayListener = null;
    private static boolean mListenOnDispaly = false;
    private static float mRefreshRate = -1.0f;
    private static final Object LOCK = new Object();
    /**
     * for SBE 1.0
     */
    private static boolean mIsScrolling = false;
    private static boolean mIsAOSPFling = false;

    public static final int SCROLL_STATUS_NON_SCROLL = 0;
    public static final int SCROLL_STATUS_SCROLLING = 1 << 0;
    public static final int SCROLL_STATUS_INPUT = 1 << 1;
    // AOSP widget(Scroller or OversScroller) trigger scrolling
    public static final int SCROLL_STATUS_FLING_AOSP = 1 << 2;
    public static final int SCROLL_STATUS_FLING_GESTURE = 1 << 3;
    public static final int SCROLL_STATUS_VERTICAL = 1 << 4;
    public static final int SCROLL_STATUS_HORIZONTAL = 1 << 5;
    private static int mScrollStatus = SCROLL_STATUS_NON_SCROLL;

    // for SBE Mbrain , get the tmp data and scroll status
    private static T3Data mCurT3ScrollData = new T3Data();

    public interface ScrollStateListener {
        // public void onScrollerScroll(Object scroller, boolean scrolling);
        public void onScroll(boolean scrolling);

        default void onFling(boolean fling, boolean isAOSP) {
        }

        default void onScrollWhenTouch(boolean scrolling) {
        }
    }

    public static void registerScrollStateListener(ScrollStateListener listener) {
        if (listener == null) {
            return;
        }
        mScrollStateListeners.add(listener);
    }

    public static void unregisterScrollStateListener(ScrollStateListener listener) {
        if (listener == null) {
            return;
        }
        mScrollStateListeners.remove(listener);
    }

    public static boolean onScrolling() {
        return mIsScrolling;
    }

    public static void setVertical() {
        // will be remove after scrolling end
        updateScrollState(SCROLL_STATUS_VERTICAL, true);
    }

    public static void setHorizontal() {
        // will be remove after scrolling end
        updateScrollState(SCROLL_STATUS_HORIZONTAL, true);
    }

    /**
     * update sbe scrolling state
     * @param scroll
     * @param msg
     * @return true update scrolling state, otherwise state not update beacause of same
     */
    public static boolean setScrolling(boolean scroll, String msg) {
        boolean last = mIsScrolling;
        mIsScrolling = scroll;

        if (LogUtil.DEBUG) {
            LogUtil.trace("scroll state changed to " + scroll + " because:" + msg);
        }
        if (last != mIsScrolling) {
            for (ScrollStateListener scrollStateListener : mScrollStateListeners) {
                scrollStateListener.onScroll(mIsScrolling);
            }
            if (mIsScrolling) {
                updateScrollState(SCROLL_STATUS_SCROLLING, mIsScrolling);
                Trace.asyncTraceBegin(Trace.TRACE_TAG_VIEW, "Scrolling", SCROL_TRACE_COOKIE);
                if (Config.isEnableSBEMbrain()) {
                    mCurT3ScrollData.mScrollStartTime = System.currentTimeMillis();
                }
            } else {
                if (Config.isEnableSBEMbrain()) {
                    mCurT3ScrollData.mScrollType = getLocalScrollStatus();
                    mCurT3ScrollData.mScrollEndTime = System.currentTimeMillis();
                }
                synchronized (LOCK) {
                    mScrollStatus = SCROLL_STATUS_NON_SCROLL;
                }
                Trace.asyncTraceEnd(Trace.TRACE_TAG_VIEW, "Scrolling", SCROL_TRACE_COOKIE);
            }
            notfiyMbrainScrolling();
            return true;
        }
        return false;
    }

    private static void notfiyMbrainScrolling() {
        // MBrain:send scroll status to MBrain when cur scrolling end
        if (Config.isEnableSBEMbrain() && mCurT3ScrollData.mScrollEndTime > 0) {
            int renderthread = ActivityInfo.getInstance().getRenderThreadTid();
            if (renderthread > 0) {
                mCurT3ScrollData.mRenderTid = ActivityInfo.getInstance().getRenderThreadTid();
                final T3Data curT2Data = mCurT3ScrollData.getCopy();
                ScrollMbrainSync.getInstance().onSendToMbrain(curT2Data);
            }
            // reset status, for next scroll
            mCurT3ScrollData.clear();
        }
    }

    public static boolean getFling() {
        return mIsAOSPFling;
    }

    public static void setFling(boolean fling) {
        // this is AOSP fling
        mIsAOSPFling = fling;
        updateScrollState(SCROLL_STATUS_FLING_AOSP, fling);
        for (ScrollStateListener scrollStateListener : mScrollStateListeners) {
            scrollStateListener.onFling(mIsAOSPFling, true);
        }
    }

    public static void onGestureFling(boolean fling) {
        updateScrollState(SCROLL_STATUS_FLING_GESTURE, fling);
        for (ScrollStateListener scrollStateListener : mScrollStateListeners) {
            scrollStateListener.onFling(fling, false);
        }
    }

    /**
     * start means touch down,
     * end means touch up
     */
    public static void onScrollWhenMove(boolean start) {
        updateScrollState(SCROLL_STATUS_INPUT, start);
        if (start) {
            Trace.asyncTraceBegin(Trace.TRACE_TAG_VIEW, "ScrollingWhenMove", TOUCH_TRACE_COOKIE);
        } else {
            Trace.asyncTraceEnd(Trace.TRACE_TAG_VIEW, "ScrollingWhenMove", TOUCH_TRACE_COOKIE);
        }
    }

    public static void updateScrollState(int mask, boolean add) {
        synchronized (LOCK) {
            if (add) {
                mScrollStatus |= mask;
            } else {
                mScrollStatus &= (~mask);
            }
        }
        if (LogUtil.DEBUG) {
            LogUtil.trace("updatescrollstatus to " + scrollStatus2Str());
        }
    }

    public static boolean isScrollingWhenInput() {
        return (getLocalScrollStatus() & SCROLL_STATUS_INPUT) != 0;
    }

    public static boolean isScrolling() {
        return (getLocalScrollStatus() & SCROLL_STATUS_SCROLLING) != 0;
    }

    public static boolean isStatus(int status) {
        return (getLocalScrollStatus() & status) != 0;
    }

    public static String scrollStatus2Str() {
        if (!LogUtil.DEBUG) {
            return "";
        }
        final int scrollStatus = getLocalScrollStatus();
        String result = "";
        if (scrollStatus == SCROLL_STATUS_NON_SCROLL) {
            result = "SCROLL_STATUS_NON_SCROLL";
        } else if ((scrollStatus & SCROLL_STATUS_SCROLLING) != 0) {
            result = "SCROLL_STATUS_SCROLLING";
            if ((scrollStatus & SCROLL_STATUS_INPUT) != 0) {
                result += " & SCROLL_STATUS_INPUT";
            }
            if ((scrollStatus & SCROLL_STATUS_FLING_AOSP) != 0) {
                result += " & SCROLL_STATUS_FLING_AOSP";
            }
            if ((scrollStatus & SCROLL_STATUS_FLING_GESTURE) != 0) {
                result += " & SCROLL_STATUS_FLING_GESTURE";
            }
            if ((scrollStatus & SCROLL_STATUS_VERTICAL) != 0) {
                result += " & SCROLL_STATUS_VERTICAL";
            }
            if ((scrollStatus & SCROLL_STATUS_HORIZONTAL) != 0) {
                result += " & SCROLL_STATUS_HORIZONTAL";
            }
        }
        return result;
    }

    private static int getLocalScrollStatus() {
        synchronized (LOCK) {
            return mScrollStatus;
        }
    }

    public interface RefreshRateChangedListener {
        public void onDisplayRefreshRateChanged(int displayId,
                float refreshRate, float frameIntervalNanos);
    }

    public static void registerRefreshRateChangedListener(RefreshRateChangedListener listener) {
        if (listener == null) {
            return;
        }

        mRefreshRateListeners.add(listener);
    }

    public static void unregisterRefreshRateChangedListener(RefreshRateChangedListener listener) {
        if (listener == null) {
            return;
        }
        mRefreshRateListeners.remove(listener);
    }

    private static synchronized void notifyRefreshRateChangedIfNeeded() {
        int displayId = Display.DEFAULT_DISPLAY;
        if (updateDisplayRefreshRate()) {
            float frameIntervalNanos = (TimeUtils.NANOS_PER_MS * 1000.0f) / mRefreshRate;
            for (RefreshRateChangedListener listener : mRefreshRateListeners) {
                listener.onDisplayRefreshRateChanged(displayId, mRefreshRate, frameIntervalNanos);
            }
        }
    }

    private static boolean updateDisplayRefreshRate() {
        final float refreshRate = DisplayManagerGlobal.getInstance()
                .getDisplayInfo(Display.DEFAULT_DISPLAY).getRefreshRate();
        if (refreshRate != mRefreshRate) {
            mRefreshRate = refreshRate;
            // A error handle for 0hz
            if (mRefreshRate <= 0.0f) {
                mRefreshRate = REFRESHRATE_1;
            }
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "refresh rate changed to " + mRefreshRate);
            }
            return true;
        }
        return false;
    }

    public static void unregisterDispalyCallbacks(){
        synchronized (LOCK) {
            if (mDisplayListener != null) {
                DisplayManagerGlobal.getInstance().unregisterDisplayListener(mDisplayListener);
            }
            mRefreshRate = -1.0f;
            mListenOnDispaly = false;
        }
    }

    public static void registerDispalyCallbacks() {
        updateDisplayRefreshRate();
        registerDisplyListenerIfNeeded();
    }

    private static void registerDisplyListenerIfNeeded() {
        //in case mutil thread
        synchronized (LOCK) {
            if (mListenOnDispaly) {
                return;
            }
            if (mDisplayListener == null) {
                mDisplayListener = new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int displayId) {
                    }

                    @Override
                    public void onDisplayRemoved(int displayId) {
                    }

                    @Override
                    public void onDisplayChanged(int displayId) {
                        if (displayId == Display.DEFAULT_DISPLAY) {
                            notifyRefreshRateChangedIfNeeded();
                        }
                    }
                };
            }
            String packgeName = ActivityThread.currentPackageName();
            if (packgeName != null) {
                DisplayManagerGlobal.getInstance().registerDisplayListener(
                    mDisplayListener, new Handler(),
                    DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE,
                    packgeName);
                mListenOnDispaly = true;
            } else {
                LogUtil.mLoge(TAG, "sbe not control this app for current packagename null.");
            }
        }
    }

    public static float getRefreshRate() {
        if (mRefreshRate == -1.0f) {//for no-activity case
            registerDispalyCallbacks();
        }
        return mRefreshRate;
    }

    public static float adjClosestFPS(int fps) {
        if (fps <= 0) {
            return REFRESHRATE_60;
        }

        // Check if the fps is exactly one of the defined refresh rates
        for (float rate : PREDEINE_RATES) {
            if (fps == rate) {
                return rate;
            }
        }

        // If not, find the closest refresh rate
        float closestRate = PREDEINE_RATES[0];
        float minDifference = Math.abs(fps - PREDEINE_RATES[0]);
        for (float rate : PREDEINE_RATES) {
            float difference = Math.abs(fps - rate);
            if (difference < minDifference) {
                minDifference = difference;
                closestRate = rate;
            }
        }
        return closestRate;
    }

}
