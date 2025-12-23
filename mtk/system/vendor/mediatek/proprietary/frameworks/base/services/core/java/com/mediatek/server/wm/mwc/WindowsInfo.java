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
import static com.mediatek.server.wm.mwc.MwcAppList.isFloatWindow;

import android.os.IBinder;
import android.util.Slog;
import android.view.View;

import com.mediatek.server.am.AmsExtImpl;

import java.io.PrintWriter;
import java.util.HashMap;

/**
 * Windows info For Multi window
 */
class WindowsInfo {
    static final String TAG = MwcAppList.TAG;
    private final String mPackageName;
    private final int mPid;
    //All visibility window
    private HashMap<IBinder, WindowInfo> visibilityClients = new HashMap<>();
    //All visibility window in MULTI WINDOW, Only change by changeMultiWindowClient().
    private HashMap<IBinder, WindowInfo> visibilityMultiWindowClients = new HashMap<>();
    private final MwcAppList mMwcAppList;
    private boolean mMultiWindowVisibility;
    private final String mWindowsName;

    public WindowsInfo(String packageName, int pid, MwcAppList mwcAppList) {
        this.mPackageName = packageName;
        this.mPid = pid;
        this.mMwcAppList = mwcAppList;
        mWindowsName = "[" + pid + "," + packageName + "]";
    }

    public String getPackageName() {
        return mPackageName;
    }

    public int getPid() {
        return mPid;
    }

    /**
     * Get visibility window size in
     *
     * @return Visibility window size
     */
    public int getVisibilityWindowSize() {
        return visibilityClients.size();
    }

    /**
     * Get window size in MULTI WINDOW
     *
     * @return Window size in MULTI WINDOW
     */
    public int getVisibilityMultiWindowSize() {
        return visibilityMultiWindowClients.size();
    }

    /**
     * 1. Can't find WindowInfo.<br/>
     * 1.1. Create new WindowInfo
     */
    public void updateWindow(String packageName, String activityName, int activityId,
                             int uid, int windowMode, int windowType, IBinder client,
                             int viewVisibility) {
        WindowInfo windowInfo = visibilityClients.get(client);
        boolean hasFloatWindow = mMwcAppList.hasFloatWindow();
        boolean isFloatWindow = MwcAppList.isFloatWindow(windowMode, windowType);
        boolean visibility = viewVisibility == View.VISIBLE;
        boolean isNewWindow = false;

        if (windowInfo == null) {
            if (!visibility) {
                if (ENABLE_LOG) {
                    Slog.d(TAG, "updateWindow: add window fail due to window not visibility");
                }
                return;
            }
            windowInfo = new WindowInfo(packageName, activityName, activityId, mPid, uid,
                    windowMode, windowType, client, viewVisibility, mMwcAppList);
            isNewWindow = true;
        } else {
            if (windowInfo.isSameWith(windowMode, windowType, viewVisibility)) {
                if (ENABLE_LOG) {
                    Slog.d(TAG, "updateWindow: do not update due to window not change");
                }
                return;
            }
        }

        // update multi-window status by ams: start.
        if (isNewWindow) {
            if (isFloatWindow(windowMode, windowType)) {
                if (ENABLE_LOG) {
                    Slog.d(TAG, "updateWindow: new window, ams state to resume: " +
                            windowInfo.getWindowName());
                }
                mMwcAppList.amsExt.amsBoostNotify(mPid, activityName, packageName, activityId, uid,
                        AmsExtImpl.MTKPOWER_STATE_RESUMED);
                mMwcAppList.amsExt.notifyFloatWindow(true);
            }
        } else {
            if (isFloatWindow(windowInfo.getWindowMode(), windowInfo.getWindowType())) {
                if (!isFloatWindow(windowMode, windowType)) {
                    if (ENABLE_LOG) {
                        Slog.d(TAG, "updateWindow: window changed to not float, ams " +
                                "state to pause: " + windowInfo.getWindowName());
                    }
                    mMwcAppList.amsExt.amsBoostNotify(mPid, activityName, packageName, activityId,
                            uid, AmsExtImpl.MTKPOWER_STATE_PAUSED);
                    mMwcAppList.amsExt.notifyFloatWindow(false);
                } else {
                    if (visibility != (windowInfo.getViewVisibility() == View.VISIBLE)) {
                        if (ENABLE_LOG) {
                            Slog.d(TAG, "updateWindow: visibility change to " + visibility +
                                    "ams state to " + (visibility ? "resume" : "pause") + ": " +
                                    windowInfo.getWindowName());
                        }
                        mMwcAppList.amsExt.amsBoostNotify(mPid, activityName, packageName,
                                activityId, uid,
                                (visibility ? AmsExtImpl.MTKPOWER_STATE_RESUMED :
                                        AmsExtImpl.MTKPOWER_STATE_PAUSED));
                        mMwcAppList.amsExt.notifyFloatWindow(visibility);
                    }
                }
            } else {
                if (isFloatWindow(windowMode, windowType)) {
                    if (ENABLE_LOG) {
                        Slog.d(TAG, "updateWindow: window changed to float, " +
                                "ams state to resume: " + windowInfo.getWindowName());
                    }
                    mMwcAppList.amsExt.amsBoostNotify(mPid, activityName, packageName, activityId,
                            uid, AmsExtImpl.MTKPOWER_STATE_RESUMED);
                    mMwcAppList.amsExt.notifyFloatWindow(true);
                }
            }
        }
        // update multi-window status by ams: end.

        if (visibility) {
            if (ENABLE_LOG) {
                Slog.d(TAG, "updateWindow: add window " + windowInfo.getWindowName());
            }
            visibilityClients.put(client, windowInfo);
            windowInfo.binderLinkToDeath();
            if (isFloatWindow) {
                mMwcAppList.addFloatWindow(client);
            } else {
                mMwcAppList.reduceFloatWindow(client);
            }
            windowInfo.setViewVisibility(viewVisibility);
            windowInfo.setWindowMode(windowMode);
            windowInfo.setWindowType(windowType);
            if (hasFloatWindow || MwcAppList.isMultiWindow(windowMode, windowType)) {
                changeMultiWindowClient(windowInfo, true);
            } else {
                changeMultiWindowClient(windowInfo, false);
            }
        } else {
            if (ENABLE_LOG) {
                Slog.d(TAG, "updateWindow: remove window-" + windowInfo.getWindowName());
            }
            visibilityClients.remove(client);
            windowInfo.binderUnlinkToDeath();
            if (isFloatWindow) {
                mMwcAppList.reduceFloatWindow(client);
            }
            changeMultiWindowClient(windowInfo, false);
        }
        notifyWindowsChange();
    }

    /**
     * Update window status due to has some other float window.
     */
    public void updateWindowForFloatWindow() {
        boolean hasFloatWindow = mMwcAppList.hasFloatWindow();
        for (WindowInfo windowInfo : visibilityClients.values()) {
            if (!MwcAppList.isMultiWindow(windowInfo.getWindowMode(),
                    windowInfo.getWindowType())) {
                if (hasFloatWindow) {
                    changeMultiWindowClient(windowInfo, true);
                    if (ENABLE_LOG) {
                        Slog.d(TAG, "updateWindowForFloat multi window: enter, " + windowInfo);
                    }
                } else if (visibilityMultiWindowClients.containsKey(windowInfo.getClient())) {
                    changeMultiWindowClient(windowInfo, false);
                    if (ENABLE_LOG) {
                        Slog.d(TAG, "updateWindowForFloat multi window: exit, " + windowInfo);
                    }
                }
            }
        }
        if (ENABLE_LOG) {
            Slog.d(TAG, "updateWindowForFloat multi window[" + mPackageName + "] size="
                    + visibilityMultiWindowClients.size());
        }
        notifyWindowsChange();
    }

    /**
     * Notify app multi window status
     * check size of multiWindowClients.
     */
    private void notifyWindowsChange() {
        boolean hasMultiWindowVisibility = visibilityMultiWindowClients.size() > 0;
        if (mMultiWindowVisibility != hasMultiWindowVisibility) {
            if (ENABLE_LOG) {
                Slog.d(TAG, "notifyWindowsChange: notify window[" + mPackageName + "] to "
                        + hasMultiWindowVisibility);
            }
            mMultiWindowVisibility = hasMultiWindowVisibility;
            mMwcAppList.notifyWindowsMultiStatusChange(this, mMultiWindowVisibility);
        } else {
            if (ENABLE_LOG) {
                Slog.d(TAG, "notifyWindowsChange: multi window visibility same, ignore!");
            }
        }
    }

    /**
     * Get window name
     *
     * @return window name, like: [pid,packageName]
     */
    public String getWindowsName() {
        return mWindowsName;
    }

    /**
     * Check Window by mode.
     *
     * @param windowMode window mode
     * @return
     */
    public boolean existVisibilityWindowByMode(int windowMode) {
        for (WindowInfo windowInfo : visibilityMultiWindowClients.values()) {
            if (windowInfo.getWindowMode() == windowMode) {
                return true;
            }
        }
        return false;
    }

    private void changeMultiWindowClient(WindowInfo windowInfo, boolean visibility) {
        if (visibility) {
            visibilityMultiWindowClients.put(windowInfo.getClient(), windowInfo);
        } else {
            visibilityMultiWindowClients.remove(windowInfo.getClient());
        }
    }

    public void dump(PrintWriter pw) {
        pw.println("    " + getWindowsName());
        pw.println("      VisibilityWindowSize: " + getVisibilityWindowSize());
        for (WindowInfo window : visibilityClients.values()) {
            pw.println("        " + window);
        }
        pw.println("      VisibilityMultiWindowSize: " + getVisibilityMultiWindowSize());
        for (WindowInfo window : visibilityMultiWindowClients.values()) {
            pw.println("        " + window);
        }
    }
}
