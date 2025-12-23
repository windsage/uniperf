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

import android.app.WindowConfiguration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Slog;
import android.view.View;
import android.view.WindowManager.LayoutParams;

import com.mediatek.powerhalwrapper.PowerHalWrapper;
import com.mediatek.server.MtkSystemServiceFactoryImpl;
import com.mediatek.server.am.AmsExtImpl;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * This class is the core class and has following functions:
 * 1. Recording window status.
 */
public class MwcAppList {
    static final String TAG = "MwcAppList";

    private static volatile MwcAppList sInstance;

    public static boolean ENABLE_LOG = SystemProperties.getBoolean("debug.wms.mwc.log",
            false);

    // WindowInfo sort by PID
    private final ArrayList<WindowsInfo> mWindowsInfos = new ArrayList<>();
    protected final AmsExtImpl amsExt;

    /**
     * We create new thread to work. It has following benefits.
     * 1. Avoid block WMS thread.
     * 2. Make update data in same thread, avoid some time issue.
     */
    private Handler mHandler;

    private final ArrayList<IBinder> mFloatWindows = new ArrayList<IBinder>();

    //Multi window power hal hint CMD.
    private static final int PERF_RES_PEAK_POWER_FAVOR_MULTISCENE = 0x03c04200;
    private boolean mLastMultiWindowState = false;
    private final PowerHalWrapper mPowerHalWrapper;
    private int prePowerHalHandle = -1;

    private MwcAppList() {
        HandlerThread handlerThread = new HandlerThread("MwcAppList");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        if (looper == null) {
            Slog.e(TAG, "MwcAppList thread looper is null! Use main looper!");
            mHandler = new Handler(Looper.getMainLooper());
        } else {
            mHandler = new Handler(looper);
        }
        mPowerHalWrapper = PowerHalWrapper.getInstance();
        amsExt = (AmsExtImpl)MtkSystemServiceFactoryImpl.getInstance().makeAmsExt();
    }

    public static MwcAppList getInstance() {
        if (sInstance == null) {
            synchronized (MwcAppList.class) {
                if (sInstance == null) {
                    sInstance = new MwcAppList();
                }
            }
        }
        return sInstance;
    }

    public void dump(PrintWriter pw) {
        ENABLE_LOG = SystemProperties.getBoolean("debug.wms.mwc.log", false);
        pw.println("");
        pw.println("-MWC dump:");
        pw.println("  enable log:" + ENABLE_LOG);
        pw.println("  Windows:");
        synchronized (mWindowsInfos) {
            for (int i = 0; i < mWindowsInfos.size(); i++) {
                WindowsInfo windowsInfo = mWindowsInfos.get(i);
                windowsInfo.dump(pw);
            }
        }
        pw.println("  FloatWindows:");
        synchronized (mFloatWindows) {
            for (int i = 0; i < mFloatWindows.size(); i++) {
                pw.println("    " + i + ":" + mFloatWindows.get(i));
            }
        }
    }

    /**
     * request binder of window dead.
     */
    void windowBinderDead(MwcDeathRecipient mwcDeathRecipient) {
        mHandler.post(() -> {
            handleWindowBinderDead(mwcDeathRecipient);
        });
    }

    /**
     * ViewRootImpl binder dead
     *
     * @param mwcDeathRecipient Binder dead callback
     */
    void handleWindowBinderDead(MwcDeathRecipient mwcDeathRecipient) {
        WindowInfo windowInfo = mwcDeathRecipient.getWindowInfo();
        int pid = windowInfo.getPid();
        WindowsInfo windowsForBinderDead = findWindowsInfoByPid(pid);
        if (windowsForBinderDead == null) {
            return;
        }
        if (ENABLE_LOG) {
            Slog.d(TAG, "handleWindowBinderDead: " + windowInfo);
        }
        handleUpdateWindowForMwcFeature(windowInfo.getPackageName(), windowInfo.getActivityName(),
                windowInfo.getActivityId(), pid, windowInfo.getUid(), windowInfo.getWindowMode(),
                windowInfo.getWindowType(), windowInfo.getClient(), View.GONE);
    }

    /**
     * report Focus window changed.
     *
     * @param packageName package name
     * @param pid         the PID of focus window
     */
    public void reportFocusChangedForMwcFeature(String packageName, int pid) {
        mHandler.post(() -> {
            handleReportFocusChangedForMwcFeature(packageName, pid);
        });
    }

    /**
     * Set Focus PID to FOS Core.
     */
    public void handleReportFocusChangedForMwcFeature(String packageName, int pid) {
        WindowsInfo windowsInfo = findWindowsInfoByPid(pid);
        if (windowsInfo == null || windowsInfo.getVisibilityMultiWindowSize() == 0) {
            return;
        }
        if (ENABLE_LOG) {
            Slog.d(TAG, "reportFocusChangedForMwcFeature: focusInfo=" + packageName);
        }
        notifyAppFocus(windowsInfo);
    }

    /**
     * Update window info.
     *
     * @param packageName    Package Name
     * @param pid            The PID of client
     * @param windowMode     Window Mode
     * @param client         Window client
     * @param viewVisibility View visibility flag
     */
    public void reportUpdateWindowForMwcFeature(String packageName, final String activityName,
                                                int activityId, int pid, int uid, int windowMode,
                                                int windowType, IBinder client,
                                                int viewVisibility) {
        mHandler.post(() -> {
            handleUpdateWindowForMwcFeature(packageName, activityName, activityId, pid, uid,
                    windowMode, windowType, client, viewVisibility);
        });
    }

    /**
     * Check the the window is multi window or not.<br/>
     * 1. WindowsInfo from find @{windowsInfos} is null:<br/>
     * 1.1. Window mode is WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW etc.<br/>
     * 1.1.1. Create new WindowsInfo. It means start enter MULTI WINDOW.<br/>
     * 1.1.2. Set displayId and displayRefreshRate to WindowsInfo.<br/>
     * 1.1.3. Add to windowsInfos list.<br/>
     * 2. WindowsInfo is not null.<br/>
     * 2.1. Call updateWindow.<br/>
     * 2.2. Remove WindowsInfo from windowsInfos if Size of IBinder in WindowsInfo is 0.<br/>
     */
    public void handleUpdateWindowForMwcFeature(String packageName, String activityName,
                                                int activityId, int pid, int uid, int windowMode,
                                                int windowType, IBinder client,
                                                int viewVisibility) {
        if (ENABLE_LOG) {
            Slog.d(TAG, "updateWindowForMwcFeature packageName=" + packageName
                    + ", pid=" + pid + ", WindowMode=" + windowMode +
                    ", WindowType=" + windowType);
        }
        WindowsInfo windowsInfo = findWindowsInfoByPid(pid);
        if (windowsInfo == null) {
            if (viewVisibility != View.VISIBLE) {
                if (ENABLE_LOG) {
                    Slog.d(TAG, "updateWindow: add windows fail due to window not visibility");
                }
                return;
            }
            if (ENABLE_LOG) {
                Slog.d(TAG, "updateWindowForMwcFeature: new WINDOW - [" + pid + ", "
                        + packageName + "]" + ", mode=" + windowMode + ", type=" + windowType);
            }
            windowsInfo = new WindowsInfo(packageName, pid, this);
            synchronized (mWindowsInfos) {
                mWindowsInfos.add(windowsInfo);
            }
        }
        boolean hasFloatWindow = hasFloatWindow();
        windowsInfo.updateWindow(packageName, activityName, activityId, uid, windowMode,
                windowType, client, viewVisibility);
        if (windowsInfo.getVisibilityWindowSize() == 0) {
            synchronized (mWindowsInfos) {
                mWindowsInfos.remove(windowsInfo);
            }
        }
        if (hasFloatWindow != hasFloatWindow()) {
            if (ENABLE_LOG) {
                Slog.d(TAG, "Float window changed: hasFloatWindow=" + hasFloatWindow);
            }
            for (WindowsInfo info : mWindowsInfos) {
                if (pid != info.getPid()) {
                    info.updateWindowForFloatWindow();
                }
            }
        }
    }

    /**
     * Find windowsInfo by PID.
     *
     * @param pid pid to need find WindowsInfo
     * @return return null if don't find.
     */
    public WindowsInfo findWindowsInfoByPid(int pid) {
        for (WindowsInfo windowsInfo : mWindowsInfos) {
            if (windowsInfo.getPid() == pid) {
                return windowsInfo;
            }
        }
        return null;
    }

    public void addFloatWindow(IBinder client) {
        if (!mFloatWindows.contains(client)) {
            synchronized (mFloatWindows) {
                mFloatWindows.add(client);
            }
            if (ENABLE_LOG) {
                Slog.d(TAG, "FloatWindow: add-total=" + mFloatWindows.size() + ", client=" +
                        Integer.toHexString(client.hashCode()));
            }
        }
    }

    public void reduceFloatWindow(IBinder client) {
        if (mFloatWindows.contains(client)) {
            synchronized (mFloatWindows) {
                mFloatWindows.remove(client);
            }
            if (ENABLE_LOG) {
                Slog.d(TAG, "FloatWindow: reduce-total=" + mFloatWindows.size() + ", client=" +
                        Integer.toHexString(client.hashCode()));
            }
        }
    }

    public boolean hasFloatWindow() {
        return mFloatWindows.size() > 0;
    }

    public static boolean isMultiWindow(int windowMode, int windowType) {
        return windowMode == WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW ||
                windowMode == WindowConfiguration.WINDOWING_MODE_PINNED ||
                windowType == LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    public static boolean isFloatWindow(int windowMode, int windowType) {
        return windowMode == WindowConfiguration.WINDOWING_MODE_PINNED ||
                windowType == LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    public void notifyWindowsMultiStatusChange(WindowsInfo windowsInfo, boolean visibility) {
        // Check multi window statue and sync to power hal.
        boolean multiWindowState = false;
        for (WindowsInfo mWindowsInfo : mWindowsInfos) {
            if (mWindowsInfo.getVisibilityMultiWindowSize() > 0) {
                multiWindowState = mWindowsInfo.existVisibilityWindowByMode(
                        WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW);
            }
            if (multiWindowState) {
                break;
            }
        }
        if (multiWindowState != mLastMultiWindowState) {
            if (ENABLE_LOG) {
                Slog.d(TAG, "Changing multi window status to " + multiWindowState);
            }
            mLastMultiWindowState = multiWindowState;
            if (prePowerHalHandle != -1) {
                mPowerHalWrapper.perfLockRelease(prePowerHalHandle);
            }
            if (multiWindowState) {
                prePowerHalHandle = mPowerHalWrapper.perfLockAcquire(0, 0,
                        PERF_RES_PEAK_POWER_FAVOR_MULTISCENE, 1);
                amsExt.notifyMultiWindow(true);
            } else {
                prePowerHalHandle = mPowerHalWrapper.perfLockAcquire(0, 0,
                        PERF_RES_PEAK_POWER_FAVOR_MULTISCENE, 0);
                amsExt.notifyMultiWindow(false);
            }
        }
    }

    private void notifyAppFocus(WindowsInfo windowsInfo) {
    }
}
