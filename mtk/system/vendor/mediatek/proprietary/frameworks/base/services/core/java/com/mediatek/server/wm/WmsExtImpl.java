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

package com.mediatek.server.wm;

import static com.android.server.wm.CompatModePackages.DOWNSCALED;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_30;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_35;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_40;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_45;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_50;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_55;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_60;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_65;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_70;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_75;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_80;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_85;
import static com.android.server.wm.CompatModePackages.DOWNSCALE_90;

import android.content.Context;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.ArraySet;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.compat.Compatibility;

import com.android.internal.compat.CompatibilityChangeConfig;
import com.android.server.compat.PlatformCompat;

import com.android.server.wm.ActivityRecord;
//SDD: modified DRT 20240422 by yubei.zhang start
//import com.mediatek.appresolutiontuner.ResolutionTunerAppList;
//SDD: modified DRT 20240422 by yubei.zhang end
//@ M: For MSync3.0 hint start
//import com.mediatek.wmsmsync.MSyncCtrlTable;
//import com.mediatek.wmsmsync.MSyncCtrlBean;
//@ M: For MSync3.0 hint end

//@ M: Add for MWC hint start
import com.mediatek.server.wm.mwc.MwcAppList;
//@ M: Add for MWC hint end

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

public class WmsExtImpl extends WmsExt {
    //SDD: modified for DRT 20240422 by yubei.zhang start
    //private static final String TAG = "WmsExtImpl";
    //private static final String TAG_ART = "AppResolutionTuner";
    //private static final boolean APP_RESOLUTION_TUNING_AI_ENABLE = "1".equals(
    //        SystemProperties.get("ro.vendor.game_aisr_enable"));
    //SDD: modified for DRT 20240422 by yubei.zhang end

    //@ M: For MSync3.0 hint start
//    public static final String DEBUG_MSYNC_LOG = "debug.wms.msync.log";
//    public boolean isMSyncLogOn = false;
//    private static final String TAG_REFRESH = "MSyncCtrlTable";
//    //action
//    private final int ACTION_DEFAULT = 0;
//    private final int ACTION_ENTER = 1;
//    private final int ACTION_EXIT = 2;
//    //type
//    private final int TYPE_DEFAULT = 0;
//    private final int TYPE_IME = 1;
//    private final int TYPE_VOICE = 2;
//
//    private static MSyncCtrlTable mMSyncCtrlTable;
    //@ M: For MSync3.0 hint end

    @Override
    public void dump(PrintWriter pw) {
        pw.println("WMSExt dump:");
        //@ M: Add for MWC hint start
        MwcAppList.getInstance().dump(pw);
        //@ M: Add for MWC hint end
    }

    /// M: add for App Resolution Tuner feature @{
    //SDD: modified DRT 20240422 by yubei.zhang start
//    @Override
//    public boolean isAppResolutionTunerSupport() {
//        return "1".equals(SystemProperties.get("ro.vendor.app_resolution_tuner"))
//                && 0 == SystemProperties.getInt("persist.vendor.dbg.disable.art", 0);
//    }

//    @Override
//    public boolean isAppResolutionTunerAISupport() {
//        return APP_RESOLUTION_TUNING_AI_ENABLE;
//    }

//    @Override
//    public void loadResolutionTunerAppList() {
//        ArrayList<ResolutionTunerAppList.Applic> loadTunerAppList =
//                getTunerList().loadTunerAppList();
//        if (loadTunerAppList == null || !APP_RESOLUTION_TUNING_AI_ENABLE) {
//            return;
//        }
//        for (ResolutionTunerAppList.Applic applic : loadTunerAppList) {
//            final String packageName = applic.getPackageName();
//            final long changeId = DOWNSCALE_50;
//            Set<Long> enabled = new ArraySet<>();
//            Set<Long> disabled;
//            if (!applic.isGameFlow() || (applic.getScale() == 0 && applic.getScaleHeight() == 0
//                    && applic.getScaleWidth() == 0)) {
//                Slog.d(TAG, "loadResolutionTunerAppList disable scale for " + packageName);
//                disabled = DOWNSCALE_CHANGE_IDS;
//            } else {
//                enabled.add(DOWNSCALED);
//                enabled.add(changeId);
//                disabled = DOWNSCALE_CHANGE_IDS.stream()
//                        .filter(it -> it != DOWNSCALED && it != changeId)
//                        .collect(Collectors.toSet());
//            }
//            Slog.d(TAG, "loadResolutionTunerAppList packageName=" + packageName
//                    + ", applic=" + applic);
//            final PlatformCompat platformCompat = (PlatformCompat)
//                    ServiceManager.getService(Context.PLATFORM_COMPAT_SERVICE);
//            final CompatibilityChangeConfig overrides =
//                    new CompatibilityChangeConfig(
//                            new Compatibility.ChangeConfig(enabled, disabled));
//            platformCompat.setOverrides(overrides, packageName);
//        }
//    }
//    private static final ArraySet<Long> DOWNSCALE_CHANGE_IDS = new ArraySet<>(new Long[]{
//            DOWNSCALED,
//            DOWNSCALE_90,
//            DOWNSCALE_85,
//            DOWNSCALE_80,
//            DOWNSCALE_75,
//            DOWNSCALE_70,
//            DOWNSCALE_65,
//            DOWNSCALE_60,
//            DOWNSCALE_55,
//            DOWNSCALE_50,
//            DOWNSCALE_45,
//            DOWNSCALE_40,
//            DOWNSCALE_35,
//            DOWNSCALE_30,
//    });
    //SDD: modified DRT 20240422 by yubei.zhang end
    //SDD: modified DRT 20240422 by yubei.zhang start
//    @Override
//    public void setWindowScaleByWL(com.android.server.wm.WindowState win, DisplayInfo displayInfo,
//                                   LayoutParams attrs, int requestedWidth, int requestedHeight) {
//        float scale = 1.f;
//        int width = displayInfo.logicalWidth;
//        int height = displayInfo.logicalHeight;
//        String packageName = attrs != null ? attrs.packageName : null;
//        String windowName = attrs != null && attrs.getTitle() != null ?
//                attrs.getTitle().toString() : null;
//        if (packageName != null && windowName != null && !windowName.contains("FastStarting")
//                /** splash screen is transient **/
//                && (!windowName.contains("Splash Screen"))
//                /** PopupWindow is transient **/
//                && (!windowName.contains("PopupWindow"))
//                /** full screen window **/
//                && ((height == requestedHeight && width == requestedWidth)
//                || (attrs.width == -1 && attrs.height == -1 && attrs.x == 0 && attrs.y == 0))
//                /** app contains in white list **/
//                && getTunerList().isScaledByWMS(packageName, windowName)) {
//            scale = getTunerList().getScaleValue(packageName);
//        }

//        if (scale != 1.f) {
//            win.mHWScale = scale;
//            win.mNeedHWResizer = true;
//            Slog.v(TAG_ART, "setWindowScaleByWL - new scale = " + scale
//                    + " ,set mEnforceSizeCompat/mNeedHWResizer = true" + " , win : " + win
//                    + " ,attrs=" + attrs.getTitle().toString());
//        }
//    }

//    @Override
//    public float getWindowScaleForAI(String packageName) {
//        float scale = getTunerList().getScaleValue(packageName);
//        if (scale == 0) {
//            float scaleHeight = getTunerList().getScaleHeight(packageName);
//            float scaleWidth = getTunerList().getScaleWidth(packageName);
//            scale = Math.min(scaleHeight, scaleWidth);
//        }
//        return scale;
//    }

//    private ResolutionTunerAppList getTunerList() {
//        return ResolutionTunerAppList.getInstance();
//    }
    /// @}
    //SDD: add DRT 20240422 by yubei.zhang end
    //@ M: For MSync3.0 hint start
//    @Override
//    public void loadMSyncCtrlTable() {
//        isMSyncLogOn = SystemProperties.getBoolean(DEBUG_MSYNC_LOG, false);
//        mMSyncCtrlTable = MSyncCtrlTable.getInstance();
//        mMSyncCtrlTable.loadMSyncCtrlTable();
//    }
//
//
//    /**
//     * Get the maximum value that the current refresh rate can be set to
//     */
//    @Override
//    public float getMaxRefreshRate(int type, int action, String pkgName, String activityName) {
//        if (isMSyncLogOn) {
//            Slog.d(TAG_REFRESH, "pkgName = " + pkgName + "activityName = " + activityName);
//        }
//        if (null == pkgName || null == activityName) {
//            Slog.e(TAG_REFRESH, "pkgName or activityName is null");
//            return 0;
//        }
//        if (!mMSyncCtrlTable.isRead()) {
//            Slog.e(TAG_REFRESH, "LoadMSyncCtrlTable Failed");
//            return 0;
//        }
//        ArrayList<MSyncCtrlBean> mSyncCtrlBeans = mMSyncCtrlTable.getMSyncAppCache();
//        int mSyncCtrlBeanSize = mSyncCtrlBeans.size();
//        float listedLimit =
//                queryAppListedLimit(mSyncCtrlBeans, mSyncCtrlBeanSize, pkgName, activityName);
//        if (type == TYPE_DEFAULT) {
//            return listedLimit;
//        } else {
//            for (int i = 0; i < mSyncCtrlBeanSize; i++) {
//                if (pkgName.equals(mSyncCtrlBeans.get(i).getPackageName())) {
//                    MSyncCtrlBean app = mSyncCtrlBeans.get(i);
//                    return queryAppListedLimitWithType(type, action, app,
//                            activityName, listedLimit);
//                }
//            }
//            if (ACTION_ENTER != action) {
//                return listedLimit;
//            } else {
//                switch (type) {
//                    case TYPE_IME:
//                        if (mMSyncCtrlTable.isEnableImeGlobalFpsControl()) {
//                            return mMSyncCtrlTable.getDefaultImeFps();
//                        } else {
//                            return listedLimit;
//                        }
//                    case TYPE_VOICE:
//                        if (mMSyncCtrlTable.isEnableVoiceGlobalFpsControl()) {
//                            return mMSyncCtrlTable.getDefaultVoiceFps();
//                        } else {
//                            return listedLimit;
//                        }
//                }
//            }
//        }
//        return 0;
//    }
//
//    /**
//     * Get the maximum value that can be set for the current refresh rate of the configured app
//     */
//    private float queryAppListedLimit(ArrayList<MSyncCtrlBean> mSyncCtrlBeans,
//                                      int mSyncCtrlBeanSize, String pkgName, String activityName) {
//        for (int i = 0; i < mSyncCtrlBeanSize; i++) {
//            if (pkgName.equals(mSyncCtrlBeans.get(i).getPackageName())) {
//                MSyncCtrlBean app = mSyncCtrlBeans.get(i);
//                int activityBeanSize = app.getActivityBeans().size();
//                for (int j = 0; j < activityBeanSize; j++) {
//                    MSyncCtrlBean.ActivityBean activityBean = app.getActivityBeans().get(j);
//                    if (activityName.contains(activityBean.getName())) {
//                        if (!app.isSlideResponse()) {
//                            //Limit maximum frame rate
//                            return activityBean.getFps();
//                        } else {
//                            //ebook
//                            return 0;
//                        }
//                    }
//                }
//                if (!app.isSlideResponse()) {
//                    //Limit maximum frame rate
//                    return app.getFps();
//                } else {
//                    //ebook
//                    return 0;
//                }
//            }
//        }
//        return 0;
//    }
//
//    /**
//     * Get the maximum value that can be set for the current refresh rate of the configured app
//     */
//    private float queryAppListedLimitWithType(int type, int action, MSyncCtrlBean app,
//                                              String activityName, float listedLimit) {
//        int activityBeanSize = app.getActivityBeans().size();
//        float appImeFps = app.getImeFps();
//        float appVoiceFps = app.getVoiceFps();
//        float appDefaultFps = app.getFps();
//        if (action == ACTION_ENTER) {
//            if (TYPE_IME == type) {
//                for (int j = 0; j < activityBeanSize; j++) {
//                    MSyncCtrlBean.ActivityBean activityBean = app.getActivityBeans().get(j);
//                    float activityImeFps = activityBean.getImeFps();
//                    if (activityName.contains(activityBean.getName())
//                            && activityImeFps > 0) {
//                        return activityImeFps;
//                    }
//                }
//                if (appImeFps > 0) {
//                    return appImeFps;
//                } else if (appDefaultFps > 0) {
//                    return appDefaultFps;
//                } else if (mMSyncCtrlTable.isEnableImeGlobalFpsControl()) {
//                    return mMSyncCtrlTable.getDefaultImeFps();
//                }
//            } else if (TYPE_VOICE == type) {
//                for (int j = 0; j < activityBeanSize; j++) {
//                    MSyncCtrlBean.ActivityBean activityBean = app.getActivityBeans().get(j);
//                    float activityVoiceFps = activityBean.getVoiceFps();
//                    if (activityName.contains(activityBean.getName())
//                            && activityVoiceFps > 0) {
//                        return activityVoiceFps;
//                    }
//                }
//                if (appVoiceFps > 0) {
//                    return appVoiceFps;
//                } else if (appDefaultFps > 0) {
//                    return appDefaultFps;
//                } else if (mMSyncCtrlTable.isEnableVoiceGlobalFpsControl()) {
//                    return mMSyncCtrlTable.getDefaultVoiceFps();
//                }
//            }
//        } else {
//            return listedLimit;
//        }
//        return 0f;
//    }
//
//    /**
//     * Get the refresh rate of the configured app
//     */
//    @Override
//    public float getPreferredRefreshRate(String pkgName, String activityName) {
//        if (isMSyncLogOn) {
//            Slog.d(TAG_REFRESH, "pkgName = " + pkgName + "activityName = " + activityName);
//        }
//        if (null == pkgName || null == activityName) {
//            Slog.e(TAG_REFRESH, "pkgName or activityName is null");
//            return 0;
//        }
//        if (!mMSyncCtrlTable.isRead()) {
//            Slog.e(TAG_REFRESH, "LoadMSyncCtrlTable Failed");
//            return 0;
//        }
//        ArrayList<MSyncCtrlBean> mSyncCtrlBeans = mMSyncCtrlTable.getMSyncAppCache();
//        int mSyncCtrlBeanSize = mSyncCtrlBeans.size();
//        for (int i = 0; i < mSyncCtrlBeanSize; i++) {
//            if (pkgName.equals(mSyncCtrlBeans.get(i).getPackageName())) {
//                MSyncCtrlBean app = mSyncCtrlBeans.get(i);
//                int activityBeanSize = app.getActivityBeans().size();
//                for (int j = 0; j < activityBeanSize; j++) {
//                    MSyncCtrlBean.ActivityBean activityBean = app.getActivityBeans().get(j);
//                    if (activityName.contains(activityBean.getName())) {
//                        return activityBean.getFps();
//                    }
//                }
//                return app.getFps();
//            }
//        }
//        return 0;
//    }
//
//    /**
//     * Get the refresh rate of the special scenes
//     */
//    @Override
//    public float getSpecialRefreshRate(int type, int action, String pkgName, String activityName) {
//        if (isMSyncLogOn) {
//            Slog.d(TAG_REFRESH, "pkgName = " + pkgName + "activityName = " + activityName);
//        }
//        if (null == pkgName || null == activityName) {
//            Slog.e(TAG_REFRESH, "pkgName or activityName is null");
//            return 0;
//        }
//        if (!mMSyncCtrlTable.isRead()) {
//            Slog.e(TAG_REFRESH, "LoadMSyncCtrlTable Failed");
//            return 0;
//        }
//        ArrayList<MSyncCtrlBean> mSyncCtrlBeans = mMSyncCtrlTable.getMSyncAppCache();
//        int mSyncCtrlTableSize = mSyncCtrlBeans.size();
//        for (int i = 0; i < mSyncCtrlTableSize; i++) {
//            if (pkgName.equals(mSyncCtrlBeans.get(i).getPackageName())) {
//                MSyncCtrlBean app = mSyncCtrlBeans.get(i);
//                return getSpecialFpsWithLimit(type, action, app, activityName);
//            }
//        }
//        switch (type) {
//            case TYPE_IME:
//                if (mMSyncCtrlTable.isEnableImeGlobalFpsControl()
//                        && ACTION_ENTER == action) {
//                    return mMSyncCtrlTable.getDefaultImeFps();
//                } else {
//                    return 0;
//                }
//            case TYPE_VOICE:
//                if (mMSyncCtrlTable.isEnableVoiceGlobalFpsControl()
//                        && ACTION_ENTER == action) {
//                    return mMSyncCtrlTable.getDefaultVoiceFps();
//                } else {
//                    return 0;
//                }
//        }
//        return 0;
//    }
//
//    /**
//     * Get the refresh rate of the special scenes (IME,VOICE)
//     */
//    private float getSpecialFpsWithLimit(int type, int action, MSyncCtrlBean app,
//                                         String activityName) {
//        int activityBeanSize = app.getActivityBeans().size();
//        float appTempFps = 0;
//        if (type == TYPE_IME) {
//            appTempFps = app.getImeFps();
//        } else if (type == TYPE_VOICE) {
//            appTempFps = app.getVoiceFps();
//        }
//        float appDefaultFps = app.getFps();
//        if (action == ACTION_ENTER) {
//            for (int j = 0; j < activityBeanSize; j++) {
//                MSyncCtrlBean.ActivityBean activityBean = app.getActivityBeans().get(j);
//                float activityTempFps = 0;
//                if (type == TYPE_IME) {
//                    activityTempFps = activityBean.getImeFps();
//                } else if (type == TYPE_VOICE) {
//                    activityTempFps = activityBean.getVoiceFps();
//                }
//                if (activityName.contains(activityBean.getName())
//                        && activityTempFps > 0) {
//                    return activityTempFps;
//                }
//            }
//            if (appTempFps > 0) {
//                return appTempFps;
//            } else if (appDefaultFps > 0) {
//                return appDefaultFps;
//            } else if (type == TYPE_IME && mMSyncCtrlTable.isEnableImeGlobalFpsControl()) {
//                return mMSyncCtrlTable.getDefaultImeFps();
//            } else if (type == TYPE_VOICE && mMSyncCtrlTable.isEnableVoiceGlobalFpsControl()) {
//                return mMSyncCtrlTable.getDefaultVoiceFps();
//            }
//        } else if (action == ACTION_EXIT) {
//            for (int j = 0; j < activityBeanSize; j++) {
//                MSyncCtrlBean.ActivityBean activityBean = app.getActivityBeans().get(j);
//                float activityFps = activityBean.getFps();
//                if (activityName.contains(activityBean.getName()) && activityFps > 0) {
//                    return activityFps;
//                }
//            }
//            return appDefaultFps;
//        }
//        return 0;
//    }
//
//    @Override
//    public float[] getRemoteRefreshRate(int type, int action, String pkgName, String activityName) {
//        if (isMSyncLogOn) {
//            Slog.d(TAG_REFRESH, "pkgName = " + pkgName + "activityName = " + activityName);
//        }
//        if (null == pkgName || null == activityName) {
//            Slog.e(TAG_REFRESH, "pkgName or activityName is null");
//            return new float[]{DEFAULT_REFRESH_RATE, DEFAULT_REFRESH_RATE};
//        }
//        if (!mMSyncCtrlTable.isRead()) {
//            Slog.e(TAG_REFRESH, "LoadMSyncCtrlTable Failed");
//            return new float[]{DEFAULT_REFRESH_RATE, DEFAULT_REFRESH_RATE};
//        }
//        ArrayList<MSyncCtrlBean> MSyncCtrlBeans = mMSyncCtrlTable.getMSyncAppCache();
//        for (int i = 0; i < MSyncCtrlBeans.size(); i++) {
//            if (pkgName.equals(MSyncCtrlBeans.get(i).getPackageName())) {
//                MSyncCtrlBean app = MSyncCtrlBeans.get(i);
//                switch (type) {
//                    case TYPE_VOICE:
//                        return getRemoteVoiceRefreshRate(action, app, activityName);
//                }
//            }
//        }
//        switch (type) {
//            case TYPE_VOICE:
//                if (mMSyncCtrlTable.isEnableVoiceGlobalFpsControl()) {
//                    float defaultVoiceFps = mMSyncCtrlTable.getDefaultVoiceFps();
//                    if (ACTION_ENTER == action) {
//                        return new float[]{defaultVoiceFps, defaultVoiceFps};
//                    } else {
//                        return new float[]{0, 0};
//                    }
//                } else {
//                    return new float[]{mMSyncCtrlTable.getDefaultImeFps(), 0};
//                }
//        }
//        return new float[]{DEFAULT_REFRESH_RATE, DEFAULT_REFRESH_RATE};
//    }
//
//    private float[] getRemoteVoiceRefreshRate(int action, MSyncCtrlBean app, String activityName) {
//        int activityBeanSize = app.getActivityBeans().size();
//        float appVoiceFps = app.getVoiceFps();
//        float appDefaultFps = app.getFps();
//        float defaultVoiceFps = mMSyncCtrlTable.getDefaultVoiceFps();
//        if (action == ACTION_ENTER) {
//            for (int j = 0; j < activityBeanSize; j++) {
//                MSyncCtrlBean.ActivityBean activityBean = app.getActivityBeans().get(j);
//                float activityVoiceFps = activityBean.getVoiceFps();
//                if (activityName.contains(activityBean.getName()) && activityVoiceFps > 0) {
//                    return new float[]{activityVoiceFps, activityVoiceFps};
//                }
//            }
//            if (appVoiceFps > 0) {
//                return new float[]{appVoiceFps, appVoiceFps};
//            } else if (appDefaultFps > 0) {
//                return new float[]{appDefaultFps, appDefaultFps};
//            } else if (mMSyncCtrlTable.isEnableVoiceGlobalFpsControl()) {
//                return new float[]{defaultVoiceFps, defaultVoiceFps};
//            }
//        } else if (action == ACTION_EXIT) {
//            for (int j = 0; j < activityBeanSize; j++) {
//                MSyncCtrlBean.ActivityBean activityBean = app.getActivityBeans().get(j);
//                float activityFps = activityBean.getFps();
//                if (activityName.contains(activityBean.getName()) && activityFps > 0) {
//                    return new float[]{activityFps, 0f};
//                }
//            }
//            return new float[]{appDefaultFps, 0f};
//        }
//        return new float[]{DEFAULT_REFRESH_RATE, DEFAULT_REFRESH_RATE};
//    }
//
//    @Override
//    public float getGlobalFPS() {
//        return mMSyncCtrlTable.getGlobalFPS();
//    }
    //@ M: For MSync3.0 hint end

    //@ M: Add for MWC hint start
    public void updateWindowForMwcFeature(com.android.server.wm.WindowState win,
                                          int viewVisibility) {
        if (win == null) {
            return;
        }
        int windowMode = win.getWindowingMode();
        int windowType = win.getWindowType();
        if (windowType == LayoutParams.TYPE_APPLICATION_STARTING
                || windowType == LayoutParams.TYPE_STATUS_BAR
                || windowType == LayoutParams.TYPE_NAVIGATION_BAR
                || windowType == LayoutParams.TYPE_TOAST
                || windowType == LayoutParams.TYPE_WALLPAPER) {
            return;
        }
        int pid = win.getPid();
        int uid = win.getUid();
        IBinder iWindow = win.getWindowToken();
        String owningPackage = win.getOwningPackage();
        String activity = owningPackage + "._" + iWindow.hashCode();
        int activityId = -1;
        ActivityRecord activityRecord = win.getActivityRecord();
        if (activityRecord != null) {
            activity = activityRecord.info.name;
            activityId = System.identityHashCode(activityRecord);
        }

        MwcAppList.getInstance().reportUpdateWindowForMwcFeature(owningPackage, activity,
                activityId, pid, uid, windowMode, windowType, iWindow, viewVisibility);
    }

    public void removeMwcWindow(com.android.server.wm.WindowState win) {
        updateWindowForMwcFeature(win, View.GONE);
    }

    public void reportFocusChangedMwcFeature(com.android.server.wm.WindowState win) {
        if (win == null) {
            return;
        }
        MwcAppList.getInstance().reportFocusChangedForMwcFeature(win.getOwningPackage(),
                win.getPid());
    }
    //@ M: Add for MWC hint end
}
