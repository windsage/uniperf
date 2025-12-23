/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2017. All rights reserved.
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

package com.mediatek.server.wm.mwc;

import static com.mediatek.server.wm.mwc.MwcAppList.ENABLE_LOG;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import java.util.NoSuchElementException;

/**
 * Window info in MULTI WINDOW
 */
class WindowInfo {
    static final String TAG = MwcAppList.TAG;
    private final int mPid;
    private final int mUid;
    private final String mPackageName;
    private final String mActivityName;
    private final int mActivityId;
    private final String mWindowName;
    private int mWindowMode;
    private int mWindowType;
    private int mViewVisibility;
    private final IBinder mClient;
    private MwcDeathRecipient mMwcDeathRecipient;
    private final MwcAppList mMwcAppList;

    public WindowInfo(String packageName, String activityName, int activityId, int pid, int uid,
                      int windowMode, int windowType, IBinder client, int viewVisibility,
                      MwcAppList mwcAppList) {
        this.mPackageName = packageName;
        this.mActivityName = activityName;
        this.mActivityId = activityId;
        this.mPid = pid;
        this.mUid = uid;
        this.mWindowMode = windowMode;
        this.mWindowType = windowType;
        this.mViewVisibility = viewVisibility;
        this.mClient = client;
        this.mMwcAppList = mwcAppList;
        mWindowName = "[" + pid + "," + packageName + "," + Integer.toHexString(hashCode()) + "]";
    }

    public String getPackageName() {
        return mPackageName;
    }

    public int getPid() {
        return mPid;
    }

    public int getUid() {
        return mUid;
    }

    public int getWindowMode() {
        return mWindowMode;
    }

    public void setWindowMode(int windowMode) {
        this.mWindowMode = windowMode;
    }

    public int getWindowType() {
        return mWindowType;
    }

    public void setWindowType(int windowType) {
        this.mWindowType = windowType;
    }

    public int getViewVisibility() {
        return mViewVisibility;
    }

    public void setViewVisibility(int viewVisibility) {
        this.mViewVisibility = viewVisibility;
    }

    /**
     * Unlink binder dead due to exit multi window
     */
    public void binderUnlinkToDeath() {
        if (mMwcDeathRecipient != null) {
            if (ENABLE_LOG) {
                Slog.d(TAG, "binderUnlinkToDeath " + mWindowName);
            }
            try {
                mClient.unlinkToDeath(mMwcDeathRecipient, 0);
            } catch (NoSuchElementException e) {
                if (ENABLE_LOG) {
                    Slog.e(TAG, "binderUnlinkToDeath error! this=" + this, e);
                } else {
                    Slog.w(TAG, "binderUnlinkToDeath error! this=" + this + ", e=" + e);
                }
            }
            mMwcDeathRecipient = null;
        }
    }

    /**
     * Link binder dead, it can help to remove window info.
     */
    public void binderLinkToDeath() {
        if (mMwcDeathRecipient == null) {
            if (ENABLE_LOG) {
                Slog.d(TAG, "binderLinkToDeath " + mWindowName);
            }
            mMwcDeathRecipient = new MwcDeathRecipient(this, mMwcAppList);
            try {
                mClient.linkToDeath(mMwcDeathRecipient, 0);
            } catch (NoSuchElementException | RemoteException e) {
                if (ENABLE_LOG) {
                    Slog.e(TAG, "binderLinkToDeath error! this=" + this, e);
                } else {
                    Slog.w(TAG, "binderLinkToDeath error! this=" + this + ", e=" + e);
                }
                mMwcDeathRecipient = null;
            }
        }
    }

    public String getWindowName() {
        return mWindowName;
    }

    public String getActivityName() {
        return mActivityName;
    }

    public int getActivityId() {
        return mActivityId;
    }

    public IBinder getClient() {
        return mClient;
    }

    @Override
    public String toString() {
        return "WindowInfo{" +
                mWindowName +
                ", mode=" + mWindowMode +
                ", type=" + mWindowType +
                ", viewVisibility=" + mViewVisibility +
                ", client=" + mClient +
                '}';
    }

    public boolean isSameWith(int windowMode, int windowType, int viewVisibility) {
        return mWindowMode == windowMode
                && mWindowType == windowType
                && mViewVisibility == viewVisibility;
    }
}
