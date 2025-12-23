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

package com.mediatek.boostfwkV5.utils;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Looper;
import android.os.UserHandle;
import android.os.RemoteException;
import android.os.Process;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import com.mediatek.boostfwkV5.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

public final class Util {
    private static final String TAG = "SBE-Util";
    private static final String[] sGameLibs = {"libGame.so", "libhegame.so"/* LUA engine */};

    public static final int WINDOWING_MODE_FULLSCREEN = 1;

    // Check if is game app.
    public static boolean isGameApp(String pkgName) {
        IPackageManager pm = AppGlobals.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, 0,
                UserHandle.getCallingUserId());
            if (appInfo == null) {
                //if appinfo is null, thinking it as game, and SBE will do nothing
                return true;
            }
            //Is game app
            if (appInfo != null
                    && ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME)
                            == ApplicationInfo.FLAG_IS_GAME
                    || appInfo.category == ApplicationInfo.CATEGORY_GAME)) {
                return true;
            } else {
                for(String gameLibName : sGameLibs) {
                    File file = new File(appInfo.nativeLibraryDir, gameLibName);
                    if (file.exists()) {
                        return true;
                    }
                }
            }
        } catch (RemoteException e) {
            LogUtil.mLoge(TAG, "isGameApp exception :" + e);
        }
        return false;
    }

    // Check if is system app.
    public static boolean isSystemApp(String pkgName) {
        IPackageManager pm = AppGlobals.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, 0,
                    UserHandle.getCallingUserId());
            if (appInfo == null) {
                return false;
            }
            // Build in app
            if (appInfo != null
                    && ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)) {
                return true;
            }
        } catch (RemoteException e) {
            LogUtil.mLoge(TAG, "isSystemApp exception :" + e);
        }
        return false;
    }

    // Check if is full screen activity
    public static boolean IsFullScreen(WindowManager.LayoutParams attrs) {
        if (attrs != null
                && ((attrs.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN)
                == WindowManager.LayoutParams.FLAG_FULLSCREEN)) {
            return true;
        }
        return false;
    }

    public static boolean IsFullScreen(int windowingmode) {
        if (windowingmode == WINDOWING_MODE_FULLSCREEN) {
            return true;
        }
        return false;
    }

    /**
     * Get current display refresh rate.
     *
     * @return Refresh rate
     */
    public static float getRefreshRate() {
        DisplayInfo di = DisplayManagerGlobal.getInstance().getDisplayInfo(
                Display.DEFAULT_DISPLAY);
        return di.getMode().getRefreshRate();
    }

    public static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    /**
     * Copy form AOSP TextUtils.isEmpty()
     * @param str
     * @return true if str empty
     */
    public static boolean isEmptyStr(CharSequence str) {
        return str == null || str.length() == 0;
    }
}
