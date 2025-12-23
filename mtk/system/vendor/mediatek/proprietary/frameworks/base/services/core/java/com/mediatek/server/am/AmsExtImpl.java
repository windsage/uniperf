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

package com.mediatek.server.am;

import static com.android.server.am.ActivityManagerDebugConfig.APPEND_CATEGORY_NAME;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_ALL;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_ANR;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BACKGROUND_CHECK;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BACKUP;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BROADCAST;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BROADCAST_LIGHT;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_LRU;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_MU;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_NETWORK;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_OOM_ADJ;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_OOM_ADJ_REASON;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_POWER;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_POWER_QUICK;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PROCESS_OBSERVERS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PROCESSES;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PROVIDER;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PSS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_SERVICE;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_FOREGROUND_SERVICE;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_SERVICE_EXECUTING;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_UID_OBSERVERS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_USAGE_STATS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PERMISSIONS_REVIEW;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_ALLOWLISTS;

import static com.android.server.wm.ActivityTaskManagerDebugConfig.APPEND_CATEGORY_NAME;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_ALL;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_ALL_ACTIVITIES;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_RECENTS;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_RECENTS_TRIM_TASKS;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_ROOT_TASK;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_SWITCH;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_TRANSITION;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_VISIBILITY;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_APP;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_IDLE;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_RELEASE;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_USER_LEAVING;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_PERMISSIONS_REVIEW;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_RESULTS;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_ACTIVITY_STARTS;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_CLEANUP;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_METRICS;

import android.app.ActivityManagerInternal;
import android.app.ActivityManager;
import android.app.AppGlobals;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;

import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseArray;
import com.android.internal.app.procstats.ProcessStats;
import com.android.internal.os.ProcessCpuTracker;
import com.android.server.LocalServices;
import com.android.server.am.ActivityManagerService;
import com.android.server.wm.ActivityRecord;
import com.android.server.am.ProcessList;
import com.android.server.am.ProcessRecord;
import com.android.server.am.ActivityManagerDebugConfig;
import com.android.server.wm.ActivityTaskManagerDebugConfig;
import com.android.server.wm.WindowState;
import com.mediatek.aee.ExceptionLog;

/// M: App-based AAL @{
import com.mediatek.amsAal.AalUtils;
/// @}

/// M: DuraSpeed @{
import com.mediatek.duraspeed.manager.IDuraSpeedNative;
import com.mediatek.duraspeed.suppress.ISuppressAction;
/// @}

 /// M: PowerHal @{
import com.mediatek.dx.DexOptExt;
import com.mediatek.dx.DexOptExtFactory;
/// @}

/// M: NPS @{
import com.mediatek.networkpolicyservice.NetworkPolicyService;
import com.mediatek.networkpolicyservice.NetworkPolicyServiceImpl;
/// @}

import com.mediatek.server.powerhal.PowerHalManager;
import com.mediatek.server.powerhal.PowerHalManagerImpl;
/// @}

/// M: DuraSpeed @{
import dalvik.system.PathClassLoader;
/// @}

/// M: Added for launch boost @{
import com.mediatek.boostfwk.BoostFwkFactory;
import com.mediatek.boostfwk.BoostFwkManager;
import com.mediatek.boostfwk.scenario.launch.LaunchScenario;
/// M: End of launch boost @}

/// M: MBrain @{
import com.mediatek.mbrainlocalservice.MBrainNotifyWrapper;
/// @}

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

//SPD: add GameModeList For MTK by jingwen.pan 20240920 start
import java.util.HashSet;
import java.util.HashMap;
//SPD: add GameModeList For MTK by jingwen.pan 20240920 end


public class AmsExtImpl extends AmsExt {
    private static final String TAG = "AmsExtImpl";
    private boolean isDebug = false;
    private boolean mAnimationOn = false;

    private boolean isDuraSpeedSupport = "1".equals
            (SystemProperties.get("persist.vendor.duraspeed.support"));

    private final int LAUNCH_VERSION = SystemProperties.getInt("vendor.boostfwk.launch.version", 0);
    private final int LAUNCH_VERSION_1 = 1;
    private static boolean SMART_BOOST_ENABLE = SystemProperties.getBoolean(
            "vendor.boostfwk.smartboost.enable", false);
    private SettingsOberver mSettingsOberver = new SettingsOberver();

    /// M: PowerHal @{
    public PowerHalManagerImpl mPowerHalManagerImpl = null;
    /// @}

    private DexOptExt mDexOptExt;

    /// M: App-based AAL @{
    private AalUtils mAalUtils = null;
    /// @}

    /// M: DuraSpeed @{
    public static PathClassLoader sClassLoader;
    private IDuraSpeedNative mDuraSpeedService;
    private ISuppressAction mSuppressAction;
    private Context mContext;
    /// @}

    private ActivityManagerInternal mActivityManagerInternal;
    private PackageManagerInternal mPackageManagerInternal;
    private Method mStartProcessMethod;
    private Field mProcessNamesField;
    private ActivityRecord mResumedActivity;
    private ActivityRecord mLastdActivity;
    private boolean misMultiWindow = false;
    private boolean misFloatWindow = false;
    private PackageManager mPm;
    private final String LAUNCHER_MAINACTIVITYNAME =
            "com.android.launcher3.uioverrides.QuickstepLauncher";
    //T-HUB Core[SPD]: add GameModeList For MTK by mingyang.liao 20251010 start
    private TranGameMode mTranGameMode;
    //T-HUB Core[SPD]: add GameModeList For MTK by mingyang.liao 20251010 end
    private final String amsLogProp = "persist.vendor.sys.activitylog";
    /// M: AEE exception log @{
    ExceptionLog exceptionLog = null;
    /// M: AEE exception log @}
    

    /// M: MBrain @{
    private MBrainNotifyWrapper mMBrainNotifyWrapper = null;
    /// @}

    public static final int MTKPOWER_STATE_PAUSED    = 0;
    public static final int MTKPOWER_STATE_RESUMED   = 1;
    public static final int MTKPOWER_STATE_DEAD      = 3;

   public AmsExtImpl() {
       /// M: AEE exception log @{
        if (SystemProperties.get("ro.vendor.have_aee_feature").equals("1")) {
            exceptionLog = ExceptionLog.getInstance();
        }
        /// M: AEE exception log @}
        
         mPowerHalManagerImpl = new PowerHalManagerImpl();
         mDexOptExt = DexOptExtFactory.getInstance().makeDexOpExt();
        //T-HUB Core[SPD]: add GameModeList For MTK by mingyang.liao 20251010 start
        mTranGameMode = new TranGameMode();
        //T-HUB Core[SPD]: add GameModeList For MTK by mingyang.liao 20251010 end
        /// M: DuraSpeed @{

       

        if (isDuraSpeedSupport) {
            String className1 = "com.mediatek.duraspeed.manager.DuraSpeedService";
            String className2 = "com.mediatek.duraspeed.suppress.SuppressAction";
            String classPackage = "/system_ext/framework/duraspeed.jar";
            Class<?> clazz = null;
            try {
                sClassLoader = new PathClassLoader(classPackage, AmsExtImpl.class.getClassLoader());
                clazz = Class.forName(className1, false, sClassLoader);
                mDuraSpeedService = (IDuraSpeedNative) clazz.getConstructor().newInstance();

                clazz = Class.forName(className2, false, sClassLoader);
                mSuppressAction = (ISuppressAction) clazz.getConstructor().newInstance();
            } catch (Exception e) {
                Slog.e("AmsExtImpl", e.toString());
            }
        }
        /// @}
        // M: App-based AAL @{
        if (mAalUtils == null && AalUtils.isSupported()) {
            mAalUtils = AalUtils.getInstance();
        }
        //@}

        if (mMBrainNotifyWrapper == null) {
            mMBrainNotifyWrapper = MBrainNotifyWrapper.getInstance();
        }
   }

   @Override
   public void onAddErrorToDropBox(String dropboxTag, String info, int pid) {
       if (isDebug) {
           Slog.d(TAG, "onAddErrorToDropBox, dropboxTag=" + dropboxTag
                   + ", info=" + info
                   + ", pid=" + pid);
       }
       /// M: AEE exception log @{
       if (exceptionLog != null) {
             exceptionLog.handle(dropboxTag, info, Integer.toString(pid));
       }
       /// M: AEE exception log @}
   }

   @Override
   public void onSystemReady(Context context) {
       Slog.d(TAG, "onSystemReady");

        /// M: DuraSpeed @{
        if (isDuraSpeedSupport && mDuraSpeedService != null) {
            mDuraSpeedService.onSystemReady();
        }
        mContext = context;
        /// @}
        mPm = mContext.getPackageManager();
        mAnimationOn = getIsAnimationOn();
        registerAnimationContentObserver();
   }

   private void registerAnimationContentObserver() {
       onNotifyAnimationStatus();
       ContentResolver resolver = mContext.getContentResolver();
       resolver.registerContentObserver(Settings.Global.getUriFor(
               Settings.Global.TRANSITION_ANIMATION_SCALE), false,
               mSettingsOberver);
       resolver.registerContentObserver(Settings.Global.getUriFor(
               Settings.Global.WINDOW_ANIMATION_SCALE), false,
               mSettingsOberver);
       resolver.registerContentObserver(Settings.Global.getUriFor(
               Settings.Global.ANIMATOR_DURATION_SCALE), false,
               mSettingsOberver);
   }

    /**
     * is split screen
     */
    public void notifyMultiWindow(boolean isMultiWindow) {
       misMultiWindow = isMultiWindow;
    }

    public void notifyFloatWindow(boolean isFloatWindow) {
        misFloatWindow = isFloatWindow;
    }

    private boolean isMultiWindow() {
        if (misMultiWindow || misFloatWindow) {
            if (isDebug) {
                Slog.d(TAG, "is multi window state");
            }
            return true;
        }
        return false;
    }

    private class SettingsOberver extends ContentObserver {

       public SettingsOberver() {
           super(null);
       }

       @Override
       public void onChange(boolean selfChange) {
           onNotifyAnimationStatus();
       }
    }

   /*Return false: Animation off, True: Animation on*/
   private boolean getIsAnimationOn() {
        float tranAnimation = getAnimationScaleSetting(Settings.Global.TRANSITION_ANIMATION_SCALE);
        float animation = getAnimationScaleSetting(Settings.Global.WINDOW_ANIMATION_SCALE);
        float winAnimation = getAnimationScaleSetting(Settings.Global.ANIMATOR_DURATION_SCALE);

        if (tranAnimation == 0 && animation == 0 && winAnimation == 0) {
            return false;
        }

        return true;
   }

   private void onNotifyAnimationStatus() {
       if (mPowerHalManagerImpl == null) {
           Slog.d(TAG, "mPowerHalManagerImpl is null");
           return;
       }

       mAnimationOn = getIsAnimationOn();
       mPowerHalManagerImpl.onNotifyAnimationStatus(mAnimationOn);
   }

    private float getAnimationScaleSetting(String animationName) {
        float animationScale = Settings.Global.getFloat(mContext.getContentResolver(),
                animationName, mContext.getResources().getFloat(
                com.android.internal.R.dimen.config_appTransitionAnimationDurationScaleDefault));
        return Math.max(Math.min(animationScale, 20), 0);
    }

   @Override
   public void onBeforeActivitySwitch(ActivityRecord lastResumedActivity,
                                      ActivityRecord nextResumedActivity,
                                      boolean pausing,
                                      int nextResumedActivityType,
                                      boolean isKeyguardShowing,
                                      boolean hasLaunched,
                                      boolean hasAttachedToProcess) {
        if (nextResumedActivity == null || nextResumedActivity.info == null
                || (lastResumedActivity == null && !isKeyguardShowing
                && mResumedActivity == nextResumedActivity)) {
            return;
        } else if (lastResumedActivity!= null
                && nextResumedActivity.packageName == lastResumedActivity.packageName
                && nextResumedActivity.info.name == lastResumedActivity.info.name) {
            // same activity
            return;
        }

        mResumedActivity = nextResumedActivity;
        mLastdActivity = lastResumedActivity;
        String lastResumedPackageName = null;
        if (lastResumedActivity != null) {
            lastResumedPackageName = lastResumedActivity.packageName;
        }
        String nextResumedPackageName = nextResumedActivity.packageName;
        if (isDebug) {
            Slog.d(TAG, "onBeforeActivitySwitch, lastResumedPackageName=" + lastResumedPackageName
                    + ", nextResumedPackageName=" + nextResumedPackageName);
        }

        // Is this activity's application already running? hasLaunched means isnot cold launch
        if (hasLaunched && mPowerHalManagerImpl != null) {
           mPowerHalManagerImpl.amsBoostResume(lastResumedPackageName,
                                nextResumedPackageName, hasAttachedToProcess);
        }

        /// M: DuraSpeed @{
        if (isDuraSpeedSupport && mDuraSpeedService != null && lastResumedActivity != null) {
            mDuraSpeedService.onBeforeActivitySwitch(lastResumedActivity,
                    nextResumedActivity, pausing, nextResumedActivityType);
        }
        /// @}
   }

   @Override
   public void onAfterActivityResumed(ActivityRecord resumedActivity) {
        if (resumedActivity.app == null) {
            return;
        }

        int pid = resumedActivity.app.mPid;
        int uid = resumedActivity.app.mUid;
        String activityName = resumedActivity.info.name;
        String packageName = resumedActivity.info.packageName;
        if (isDebug) {
            Slog.d(TAG, "onAfterActivityResumed, pid=" + pid
                    + ", activityName=" + activityName
                    + ", packageName=" + packageName);
        }
//         //PowerHal need only call once when app switch, so no need call here,
//         //we use onTopResumedActivityChanged call directly.
//         //amsBoostNotify(pid, activityName, packageName, uid);

        /// M: App-based AAL @{
        if (mAalUtils != null) {
            mAalUtils.onAfterActivityResumed(packageName, activityName);
        }
        /// @}

        /// M: release hint when finish launch @{
        BoostFwkFactory.getInstance().makeBoostFwkManager().perfHint(new LaunchScenario(
                BoostFwkManager.UX_LAUNCH,
                BoostFwkManager.Launch.LAUNCH_COLD,
                BoostFwkManager.BOOST_END,
                packageName, activityName, false/*not came for idle*/));
        /// M: End of release hint @}
   }

   @Override
   public void onActivityStateChanged(ActivityRecord activity, boolean onTop) {
        //If wm_on_top_resumed_lost_called no need callback,
        //only callback when wm_on_top_resumed_gained_called
        if (activity.app == null) {
            return;
        }

        int pid = activity.app.mPid;
        int uid = activity.app.mUid;
        String activityName = activity.info.name;
        String packageName = activity.info.packageName;
        int activityID = System.identityHashCode(activity);
        if (isDebug) {
            Slog.d(TAG, "onActivityStateChanged(onTop=" + onTop + "), pid=" + pid + ", uid=" + uid
                    + ", activityName=" + activityName
                    + ", packageName=" + packageName);
        }

        /// M: NPS @{
        NetworkPolicyServiceImpl nps = NetworkPolicyService.getNPSServiceImpl();
        if (nps != null) {
            nps.onActivityStateChanged(onTop, pid, uid, activityName, packageName);
        } else {
            Slog.d(TAG, "JXNPS: onActivityStateChanged nps == null");
        }
        /// @}
        //T-HUB Core[SPD]: add GameModeList For MTK by mingyang.liao 20251010 start
        if (onTop) {
            amsBoostNotify(pid, activityName, packageName, activityID, uid, MTKPOWER_STATE_RESUMED);
        } else {
            amsBoostNotify(pid, activityName, packageName, activityID, uid, MTKPOWER_STATE_PAUSED);            
        }
        mTranGameMode.setGameModeEnabled(packageName, pid, activityName, onTop, isDebug);
        //T-HUB Core[SPD]: add GameModeList For MTK by mingyang.liao 20251010 end
        if (mMBrainNotifyWrapper != null) {
            mMBrainNotifyWrapper.activityChange(onTop, uid, packageName, pid);
        }
   }

    public void amsBoostNotify(int pid, String activityName, String packageName, int activityID,
                               int uid, int state) {
        if (mPowerHalManagerImpl != null) {
            mPowerHalManagerImpl.amsBoostNotify(pid, activityName, packageName, activityID,
                    uid, state);
        } else {
            Slog.w(TAG, "amsBoostNotify mPowerHalManagerImpl is null.");
        }
    }

    @Override
    public void onNotifyAppTTId(String packageName,int TTidtime,int launchState) {
        if (mPowerHalManagerImpl != null) {
            mPowerHalManagerImpl.amsNotifyPackageTTID(packageName,TTidtime,launchState);
        }

        if (mMBrainNotifyWrapper != null) {
            mMBrainNotifyWrapper.recordAppLaunch(packageName, TTidtime, launchState);
        }
    }

   @Override
   public void onUpdateSleep(boolean wasSleeping, boolean isSleepingAfterUpdate) {
        if (isDebug) {
            Slog.d(TAG, "onUpdateSleep, wasSleeping=" + wasSleeping
                    + ", isSleepingAfterUpdate=" + isSleepingAfterUpdate);
        }
        /// M: App-based AAL @{
        if (mAalUtils != null) {
            mAalUtils.onUpdateSleep(wasSleeping, isSleepingAfterUpdate);
        }
        /// @}
   }

    /// M: App-based AAL @{
   @Override
   public void setAalMode(int mode) {
        if (mAalUtils != null) {
            mAalUtils.setAalMode(mode);
        }
   }

   @Override
   public void setAalEnabled(boolean enabled) {
        if (mAalUtils != null) {
            mAalUtils.setEnabled(enabled);
        }
   }

    @Override
    public int amsAalDump(PrintWriter pw, String[] args, int opti) {
        if (mAalUtils != null) {
            return mAalUtils.dump(pw, args, opti);
        } else {
            return opti;
        }
    }
    /// @}

    private boolean isPackageMainActivity(String createProcessName) {
       if (mResumedActivity == null) {
           return false;
       }
        String packageName = mResumedActivity.packageName;
        if (!packageName.equals(createProcessName) || mPm == null){
            return false;
        }
        Intent launchIntent = mPm.getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            ActivityInfo activityInfo = null;
            try {
                activityInfo = mPm.getActivityInfo(launchIntent.getComponent(),
                        PackageManager.GET_META_DATA);
                if (activityInfo != null) {
                    String entryActivityName = activityInfo.name;
                    if (isDebug) {
                        Slog.d(TAG, "entryActivityName:" + entryActivityName);
                    }
                    if (mResumedActivity.info.name.equals(entryActivityName)) {
                        if (isDebug) {
                            Slog.d(TAG, "isPackageMainActivity true");
                        }
                        return true;
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        if (isDebug) {
            Slog.d(TAG, "isPackageMainActivity false");
        }
        return false;
    }

    private boolean isLauncher() {
       if (mLastdActivity != null && LAUNCHER_MAINACTIVITYNAME.equals(mLastdActivity.info.name)) {
           return true;
       }
       return false;
    }

    private boolean isSystemApp(String pkgName) {
        try {
            ApplicationInfo appInfo = mPm.getApplicationInfo(pkgName, 0);
            if (appInfo != null && ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)) {
                if (isDebug) {
                    Slog.d(TAG, "isSystemApp true");
                }
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

   @Override
   public void onStartProcess(String hostingType, String packageName) {
        if (isDebug) {
            Slog.d(TAG, "onStartProcess, hostingType=" + hostingType
                    + ", packageName=" + packageName);
        }

        if (mPowerHalManagerImpl != null) {
            if (SMART_BOOST_ENABLE) {
                if (hostingType != null && hostingType.contains("activity") == true) {
                    if (isMultiWindow() || !isPackageMainActivity(packageName) ||
                            isSystemApp(packageName) || !isLauncher()) {
                        mPowerHalManagerImpl.onNotifySmartLaunchBoostStatus(false);
                    } else {
                        mPowerHalManagerImpl.onNotifySmartLaunchBoostStatus(true);
                    }
                }
            }
            mPowerHalManagerImpl.amsBoostProcessCreate(hostingType, packageName);
        }

        if(mDexOptExt != null){
            mDexOptExt.onStartProcess(hostingType, packageName);
        }

        /// M: Hint when process start @{
        BoostFwkFactory.getInstance().makeBoostFwkManager().perfHint(new LaunchScenario(
                BoostFwkManager.UX_LAUNCH,
                BoostFwkManager.Launch.LAUNCH_COLD,
                hostingType,
                BoostFwkManager.BOOST_BEGIN,
                packageName));
        /// M: End of hint @}

   }

   @Override
   public void onNotifyAppCrash(int pid, int uid, String packageName) {
        if (isDebug) {
            Slog.d(TAG, "onNotifyAppCrash, packageName=" + packageName
                    + ", pid=" + pid);
        }

        if (mPowerHalManagerImpl != null) {
            mPowerHalManagerImpl.NotifyAppCrash(pid, uid, packageName);
        }

        /// M: NPS @{
        NetworkPolicyServiceImpl nps = NetworkPolicyService.getNPSServiceImpl();
        if (nps != null) {
            nps.onAppProcessCrash(pid, uid, packageName);
        } else {
            Slog.d(TAG, "JXNPS: onNotifyAppCrash nps == null");
        }
        /// @}
  }

   @Override
   public void onEndOfActivityIdle(Context context, ActivityRecord activityRecord) {
        if (isDebug) {
            Slog.d(TAG, "onEndOfActivityIdle, activityRecord=" + activityRecord);
        }

        if (mPowerHalManagerImpl != null) {
            mPowerHalManagerImpl.amsBoostStop();
        }
        /// M: DuraSpeed @{
        if (isDuraSpeedSupport && mDuraSpeedService != null) {
            mDuraSpeedService.onActivityIdle(context, activityRecord.intent);
        }
        /// @}

        /// M: release hint when finish launch @{
        WindowState win = activityRecord.findMainWindow(false);
        if (win != null) {
            BoostFwkFactory.getInstance().makeBoostFwkManager().perfHint(new LaunchScenario(
                    BoostFwkManager.UX_LAUNCH,
                    BoostFwkManager.Launch.LAUNCH_COLD,
                    BoostFwkManager.BOOST_END,
                    activityRecord.packageName,
                    win.getWindowingMode(),
                    true/*come from idle*/));
        }
        /// M: End of release hint @}
   }

    @Override
    public void enableAmsLog(ArrayList<ProcessRecord> lruProcesses) {
        String activitylog = SystemProperties.get(amsLogProp, null);
        if (activitylog != null && !activitylog.equals("")) {
            if (activitylog.indexOf(" ") != -1
                    && activitylog.indexOf(" ") + 1 <= activitylog.length()) {
                String[] args = new String[2];
                args[0] = activitylog.substring(0, activitylog.indexOf(" "));
                args[1] = activitylog.substring(activitylog.indexOf(" ") + 1, activitylog.length());
                enableAmsLog(null, args, 0, lruProcesses);
            } else {
                SystemProperties.set(amsLogProp, "");
            }
        }
    }

    @Override
    public void enableAmsLog(PrintWriter pw, String[] args,
            int opti, ArrayList<ProcessRecord> lruProcesses) {
        String option = null;
        boolean isEnable = false;
        int indexLast = opti + 1;

        if (indexLast >= args.length) {
            if (pw != null) {
                pw.println("  Invalid argument!");
            }
            SystemProperties.set(amsLogProp, "");
        } else {
            option = args[opti];
            isEnable = "on".equals(args[indexLast]) ? true : false;
            SystemProperties.set(amsLogProp, args[opti] + " " + args[indexLast]);

            if (option.equals("x")) {
                enableAmsLog(isEnable, lruProcesses);
            } else {
                if (pw != null) {
                    pw.println("  Invalid argument!");
                }
                SystemProperties.set(amsLogProp, "");
            }
        }
    }

    private void enableAmsLog(boolean isEnable, ArrayList<ProcessRecord> lruProcesses) {
        isDebug = isEnable;
        // From AMS debug config
        ActivityManagerDebugConfig.APPEND_CATEGORY_NAME = isEnable;
        ActivityManagerDebugConfig.DEBUG_ALL = isEnable;
        DEBUG_ANR = isEnable;
        DEBUG_BACKGROUND_CHECK = isEnable;
        DEBUG_BACKUP = isEnable;
        DEBUG_BROADCAST = isEnable;
        DEBUG_BROADCAST_LIGHT = isEnable;
        DEBUG_LRU = isEnable;
        DEBUG_MU = isEnable;
        DEBUG_NETWORK = isEnable;
        DEBUG_POWER = isEnable;
        DEBUG_POWER_QUICK = isEnable;
        DEBUG_PROCESS_OBSERVERS = isEnable;
        DEBUG_PROCESSES = isEnable;
        DEBUG_PROVIDER = isEnable;
        DEBUG_PSS = isEnable;
        DEBUG_SERVICE = isEnable;
        DEBUG_FOREGROUND_SERVICE = isEnable;
        DEBUG_SERVICE_EXECUTING = isEnable;
        DEBUG_UID_OBSERVERS = isEnable;
        DEBUG_USAGE_STATS = isEnable;
        ActivityManagerDebugConfig.DEBUG_PERMISSIONS_REVIEW = isEnable;
        DEBUG_ALLOWLISTS = isEnable;

        // From Task Manager debug config
        ActivityTaskManagerDebugConfig.APPEND_CATEGORY_NAME = isEnable;
        ActivityTaskManagerDebugConfig.DEBUG_ALL = isEnable;
        DEBUG_RECENTS = isEnable;
        DEBUG_RECENTS_TRIM_TASKS = isEnable;
        DEBUG_ROOT_TASK = isEnable;
        DEBUG_SWITCH = isEnable;
        DEBUG_TRANSITION = isEnable;
        DEBUG_VISIBILITY = isEnable;
        DEBUG_APP = isEnable;
        DEBUG_IDLE = isEnable;
        DEBUG_RELEASE = isEnable;
        DEBUG_USER_LEAVING = isEnable;
        ActivityTaskManagerDebugConfig.DEBUG_PERMISSIONS_REVIEW = isEnable;
        DEBUG_RESULTS = isEnable;
        DEBUG_ACTIVITY_STARTS = isEnable;
        DEBUG_CLEANUP = isEnable;
        DEBUG_METRICS = isEnable;

        for (int i = 0; i < lruProcesses.size(); i++) {
            ProcessRecord app = lruProcesses.get(i);
            if (app != null && app.getThread() != null) {
                try {
                    app.getThread().enableActivityThreadLog(isEnable);
                } catch (Exception e) {
                     Slog.e(TAG, "Error happens when enableActivityThreadLog", e);
                }
            }
        }
    }

    // adb shell dumpsys activity message + processName
    public void enableProcessMainThreadLooperLog(PrintWriter pw, String[] args,
            int opti, ArrayList<ProcessRecord> lruProcesses) {
        String processName = null;
        boolean isSucess = false;

        if (args.length < 1) {
            if (pw != null) {
                pw.println("Invalid argument!");
            }
        } else {
            processName = args[opti];
            for (int i = 0; i < lruProcesses.size(); i++) {
                ProcessRecord app = lruProcesses.get(i);
                if (app != null && app.getThread() != null
                        && app.processName != null && app.processName.equals(processName)) {
                    try {
                        app.getThread().enableProcessMainThreadLooperLog();
                        isSucess = true;
                        if (pw != null) {
                            pw.println("Sucess enalbe " + processName + " main thread looper log.");
                        }
                    } catch (Exception e) {
                         Slog.e(TAG, "Error happens when enableProcessMainThreadLooperLog", e);
                    }
                }
            }

        }

        if (!isSucess) {
            if (pw != null) {
                pw.println("Canntot find prcess: " + processName);
            }
        }
    }

    /// M: DuraSpeed @{
    @Override
    public void onWakefulnessChanged(int wakefulness) {
        if (isDuraSpeedSupport && mDuraSpeedService != null) {
            mDuraSpeedService.onWakefulnessChanged(wakefulness);
        }
    }

    @Override
    public void addDuraSpeedService() {
        if (isDuraSpeedSupport && mDuraSpeedService != null) {
            ServiceManager.addService("duraspeed", (IBinder) mDuraSpeedService, true);
        }
    }

    @Override
    public void startDuraSpeedService(Context context) {
        if (isDuraSpeedSupport && mDuraSpeedService != null) {
            mDuraSpeedService.startDuraSpeedService(context);
        }
    }

    @Override
    public String onReadyToStartComponent(String packageName, int uid,
            String suppressReason, String className) {
        if (mDuraSpeedService != null && mDuraSpeedService.isDuraSpeedEnabled()) {
            return mSuppressAction.onReadyToStartComponent(mContext, packageName,
                    uid, suppressReason, className);
        }
        return null;
    }

    @Override
    public boolean onBeforeStartProcessForStaticReceiver(String packageName) {
        if (mDuraSpeedService != null && mDuraSpeedService.isDuraSpeedEnabled()) {
            return mSuppressAction.onBeforeStartProcessForStaticReceiver(packageName);
        }
        return false;
    }

    @Override
    public void addToSuppressRestartList(String packageName) {
        if (mDuraSpeedService != null && mDuraSpeedService.isDuraSpeedEnabled() &&
                mContext != null) {
            mSuppressAction.addToSuppressRestartList(mContext, packageName);
        }
    }

    @Override
    public boolean notRemoveAlarm(String packageName) {
        if (mDuraSpeedService != null && mDuraSpeedService.isDuraSpeedEnabled()) {
            return mSuppressAction.notRemoveAlarm(packageName);
        }
        return false;
    }

     @Override
     public void onAppProcessDied(Context context, ProcessRecord app, ApplicationInfo appInfo,
             int userId, ArrayList<ProcessRecord> lruProcesses, String reason) {
         if (isDuraSpeedSupport && mDuraSpeedService != null) {
             mDuraSpeedService.onAppProcessDied(app, appInfo.packageName, reason);
         }

         if (mMBrainNotifyWrapper != null) {
            mMBrainNotifyWrapper.recordAppDied(appInfo.packageName, reason);
         }
     }
}