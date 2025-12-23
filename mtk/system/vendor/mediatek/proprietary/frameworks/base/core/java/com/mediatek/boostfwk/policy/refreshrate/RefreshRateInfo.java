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
import android.content.Context;
import android.view.WindowManager;
import android.view.Window;
import android.view.Display;
import android.view.WindowManager.LayoutParams;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.info.ActivityInfo;
import com.mediatek.boostfwk.scenario.refreshrate.RefreshRateScenario;

public class RefreshRateInfo {
    private static final String TAG = "M-ProMotion";

    private static final int DEFAULT_SUPPORT_REFRESHRATE_NUMBER = 4;
    private static final int DEFAULT_MIN_SUPPORT_REFRESHRATE_NUMBER = 3;
    private static final int DEFAULT_MIN_SUPPORT_WHEN_FLING = 30;
    private static final int DEFAULT_MIN_SUPPORT_WHEN_FLING_WITH_VIDEO = 60;
    private static final int DEFAULT_TOUCH_SCROLL_VELOCITY_THRESHOLED = 150; // 90 or 120
    private static final String USER_CONFIG_DEFAULT_VALUE = "-1";

    private ArrayList<Integer> mHardwareSupportedRefreshRate = new ArrayList();
    private ArrayList<Integer> mFlingSupportedRefreshRate = new ArrayList();
    private ArrayList<Float> mFlingRefreshRateChangeGap = new ArrayList();
    private ArrayList<Float> mFlingRefreshRateChangeTimeOffset = new ArrayList();
    private ArrayList<Float> mFlingRefreshRateVSyncTime = new ArrayList();
    private ArrayList<Float> mFlingVelocityThreshhold = new ArrayList();

    // Smooth fling turning value start
    private float mInflextion;

    private static final float INFLEXION_MSYNC = 0.26f; // Tension lines cross at (INFLEXION, 1)
    private static final float INFLEXION_DEFAULT = 0.35f; // Tension lines cross at (INFLEXION, 1)
    // Smooth fling turning value end

    // Maximum velocity to initiate a fling, as measured in pixels per second
    private float mMaximumVelocity;
    private String mPackageName;
    private String mCurrentActivityName;
    private boolean mIsDataInited = false;
    private boolean mIsSameActivity = false;
    private int mFlingSupportedRefreshRateSize = 0;
    private int mTouchScrollSpeedLow = -1;
    private int mVideoFloorRefreshRate = -1;
    private int mTouchScrollVelocityThreshold = -1;
    private int mCustomerConfig = -1;

    public RefreshRateInfo() {
        mInflextion = Config.isMSync3SmoothFlingEnabled() ? INFLEXION_MSYNC : INFLEXION_DEFAULT;
    }

    public void updateCurrentActivityName(Context activityContext) {
        Activity activity = (Activity)activityContext;
        String lastActivityName = mCurrentActivityName;
        mCurrentActivityName = activity.getLocalClassName();
        mIsSameActivity = mCurrentActivityName.equals(lastActivityName);
        LogUtil.mLogd(TAG, "mCurrentActivityName = " + mCurrentActivityName);
    }

    public boolean isSameActivity(Context activityContext) {
        return mIsSameActivity;
    }

    public void initPackageInfo(Context activityContext) {
            ViewConfiguration configuration = ViewConfiguration.get(activityContext);
            mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
            mPackageName = activityContext.getApplicationInfo().packageName;
    }

    /**
    * check hardware support refresh rate
    * e.g. 120\90\60\30\24\10
    **/
    public void initHardwareSupportRefreshRate(Context activityContext) {
        Display display = ((Activity) activityContext).getWindowManager().getDefaultDisplay();
        Display.Mode[] displayMode = display.getSupportedModes();
        for (int i = 0; i < displayMode.length; i ++) {
            int refreshRate = (int)displayMode[i].getRefreshRate();
            if (!mHardwareSupportedRefreshRate.contains(refreshRate)) {
                mHardwareSupportedRefreshRate.add(refreshRate);
            }
        }
        mHardwareSupportedRefreshRate.sort(Comparator.reverseOrder());
        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "mHardwareSupportedRefreshRate = "
                    + mHardwareSupportedRefreshRate.toString());
        }
    }

    public boolean initMSync3SupportRefreshRate() {
        boolean isInitSuccess = false;

        // 1. Check user config
        String userConfigRefreshRate = Config.MSYNC3_FLING_SUPPORT_REFRESHRATE;
        String userConfigRefreshChangeGap = Config.MSYNC3_FLING_REFRESHRATE_CHANGE_GAP;
        String touchScrollVelocityThreshold = Config.MSYNC3_TOUCH_SCROLL_VELOCITY;

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "userConfigRefreshRate = " + userConfigRefreshRate
                    + " userConfigRefreshChangeGap = " + userConfigRefreshChangeGap
                    + " touchScrollVelocityThreshold = " + touchScrollVelocityThreshold);
        }

        if (!USER_CONFIG_DEFAULT_VALUE.equals(userConfigRefreshRate)
                && !USER_CONFIG_DEFAULT_VALUE.equals(userConfigRefreshChangeGap)) {
            String[] userConfigRefreshRates = userConfigRefreshRate.split(",");
            String[] userConfigRefreshChangeGaps = userConfigRefreshChangeGap.split(",");
            String[] touchScrollVelocityThresholds = touchScrollVelocityThreshold.split(",");
            if (userConfigRefreshRates.length < DEFAULT_MIN_SUPPORT_REFRESHRATE_NUMBER) {
                isInitSuccess = false;
            } else {
                for (int i = 0; i < userConfigRefreshRates.length; i ++) {
                    mFlingSupportedRefreshRate.add(Integer.valueOf(userConfigRefreshRates[i]));
                }

                for (int i = 0; i < userConfigRefreshChangeGaps.length; i ++) {
                    mFlingRefreshRateChangeGap.add(Float.valueOf(userConfigRefreshChangeGaps[i]));
                }

                // Caculate velocity threshhold
                for (int i = 0; i < touchScrollVelocityThresholds.length; i ++) {
                    mFlingVelocityThreshhold.add(Float.valueOf(touchScrollVelocityThresholds[i]));
                }
                isInitSuccess = true;
            }
            if (LogUtil.DEBUG) {
                LogUtil.mLogd(TAG, "Userconfig mMSyncSupportedRefreshRate = "
                        + mFlingSupportedRefreshRate.toString()
                        + " mFlingRefreshRateChangeGap = " + mFlingRefreshRateChangeGap.toString()
                        + " isInitSuccess = " + isInitSuccess);
            }
        }

        // 2. update the user config
        mTouchScrollSpeedLow = Integer.valueOf(Config.MSYNC3_TOUCHSCROLL_REFRESHRATE_SPEED_LOW);
        mVideoFloorRefreshRate = Integer.valueOf(Config.MSYNC3_VIDEO_FLOOR_FPS);
        mCustomerConfig = Integer.valueOf(Config.MSYNC3_MSYNC3_CUSTOMERCONFIG);

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "Userconfig  = mTouchScrollSpeedLow = " + mTouchScrollSpeedLow
                + " mVideoFloorRefreshRate = " + mVideoFloorRefreshRate);
        }

        // 3. if user not define, use msync3 default config
        if (!isInitSuccess) {
            int totalSupportRefreshRateSize = mHardwareSupportedRefreshRate.size();
            if (totalSupportRefreshRateSize < DEFAULT_MIN_SUPPORT_REFRESHRATE_NUMBER) {
                isInitSuccess = false; // e.g. 120,90
            } else if ((totalSupportRefreshRateSize == DEFAULT_MIN_SUPPORT_REFRESHRATE_NUMBER
                    || totalSupportRefreshRateSize == DEFAULT_SUPPORT_REFRESHRATE_NUMBER)
                    && mHardwareSupportedRefreshRate.get(
                        totalSupportRefreshRateSize -1) >= DEFAULT_MIN_SUPPORT_WHEN_FLING) {
                mFlingSupportedRefreshRate.addAll(mHardwareSupportedRefreshRate);
                for (int i = 0; i < totalSupportRefreshRateSize; i ++) {
                    mFlingRefreshRateChangeGap.add(Config.FLING_DEFAULT_GAP_CHANGE_REFRESHRATE);
                }
                isInitSuccess = true;
            // e.g. 120,90,60,45,30, here we choose 120,90,60,45
            } else if (totalSupportRefreshRateSize > DEFAULT_SUPPORT_REFRESHRATE_NUMBER) {
                for (int i = 0; i < DEFAULT_SUPPORT_REFRESHRATE_NUMBER; i ++) {
                    mFlingSupportedRefreshRate.add(mHardwareSupportedRefreshRate.get(i));
                    mFlingRefreshRateChangeGap.add(Config.FLING_DEFAULT_GAP_CHANGE_REFRESHRATE);
                }
                isInitSuccess = true;
            }
        }

        // 4. Caculate time gap for refresh rate change,e.g.8.33,11.11,16.66
        if (isInitSuccess) {
            mFlingSupportedRefreshRateSize = mFlingSupportedRefreshRate.size();
            for (int i = 0; i < mFlingSupportedRefreshRateSize - 1; i ++) {
                float currentRefreshRateVsyncIntervalMS
                        = (1/Float.valueOf(mFlingSupportedRefreshRate.get(i)))*1000;
                float nextLevelRefreshRateVsyncIntervalMS
                        = (1/Float.valueOf(mFlingSupportedRefreshRate.get(i + 1)))*1000;
                mFlingRefreshRateChangeTimeOffset.add(
                        nextLevelRefreshRateVsyncIntervalMS - currentRefreshRateVsyncIntervalMS);
                mFlingRefreshRateVSyncTime.add(currentRefreshRateVsyncIntervalMS);
            }
            mFlingRefreshRateVSyncTime.add((1/Float.valueOf(
                mFlingSupportedRefreshRate.get(mFlingSupportedRefreshRateSize - 1)))*1000);
        }

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "SupportedRefreshRate = " + mFlingSupportedRefreshRate.toString()
                    + " mFlingChangeGap = " + mFlingRefreshRateChangeGap.toString()
                    + " mFlingChangeTimeOffset = " + mFlingRefreshRateChangeTimeOffset.toString()
                    + " mFlingRefreshRateVSyncTime = " + mFlingRefreshRateVSyncTime.toString()
                    + " mFlingRefreshRateSpeed = " + mFlingVelocityThreshhold.toString()
                    + " isInitSuccess = " + isInitSuccess);
        }
        return isInitSuccess;
    }

    public float getMaximumVelocity() {
        return mMaximumVelocity;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getCurrentActivityName() {
        return mCurrentActivityName;
    }

    public ArrayList getFlingSupportedRefreshRate() {
        return mFlingSupportedRefreshRate;
    }

    public ArrayList getFlingRefreshRateChangeGap() {
        return mFlingRefreshRateChangeGap;
    }

    public ArrayList getFlingRefreshRateTimeOffset() {
        return mFlingRefreshRateChangeTimeOffset;
    }

    public ArrayList getFlingRefreshRateVSyncTime() {
        return mFlingRefreshRateVSyncTime;
    }

    public ArrayList getFlingVelocityThreshhold() {
        return mFlingVelocityThreshhold;
    }

    public int getMaxFlingSupportedRefreshRate() {
        if (mFlingSupportedRefreshRate.size() < DEFAULT_MIN_SUPPORT_REFRESHRATE_NUMBER) return -1;
        return mFlingSupportedRefreshRate.get(0);
    }

    public int getSlowScrollRefreshRate() {
        if (mFlingSupportedRefreshRate.size() < DEFAULT_MIN_SUPPORT_REFRESHRATE_NUMBER) return -1;
        return mTouchScrollSpeedLow != -1
            ? mTouchScrollSpeedLow : mFlingSupportedRefreshRate.get(1);
    }

    public int getVideoFloorRefreshRate() {
        return mVideoFloorRefreshRate != -1
            ? mVideoFloorRefreshRate : DEFAULT_MIN_SUPPORT_WHEN_FLING_WITH_VIDEO;
    }

    public int getFlingSupportedRefreshRateCount() {
        return mFlingSupportedRefreshRateSize;
    }

    public int getTouchScrollVelocityThreshold() {
        return mTouchScrollVelocityThreshold != -1
            ? mTouchScrollVelocityThreshold : DEFAULT_TOUCH_SCROLL_VELOCITY_THRESHOLED;
    }

    public int getCustomerFlingPolicyIndex() {
        return mCustomerConfig;
    }

    public boolean getIsCustomerFlingPolicy() {
        return mCustomerConfig != -1;
    }

    public boolean getIsDataInited() {
        return mIsDataInited;
    }

    public float getInflextion() {
        return mInflextion;
    }
}
