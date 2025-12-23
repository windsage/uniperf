/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.powerhalwrapper;

import android.util.Log;
import android.util.Printer;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.Binder;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Trace;
import android.os.HwBinder;
import android.os.IHwBinder;
import android.os.IHwInterface;
import android.app.AppGlobals;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.IOException;
import java.io.InputStream;

/// MTK power
import vendor.mediatek.hardware.power.V2_0.*;
import com.mediatek.scnmodule.ScnModule;

public class PowerHalWrapper {

    private static final String TAG = "PowerHalWrapper";

    private static final int AMS_BOOST_TIME = 10000;
    private static final int USER_DURATION_MAX = 30000;

    public static final int SETSYS_MANAGEMENT_PREDICT  = 1;
    public static final int SETSYS_SPORTS_APK          = 2;
    public static final int SETSYS_FOREGROUND_SPORTS   = 3;
    public static final int SETSYS_MANAGEMENT_PERIODIC = 4;
    public static final int SETSYS_INTERNET_STATUS     = 5;
    public static final int SETSYS_NETD_STATUS         = 6;
    public static final int SETSYS_PREDICT_INFO        = 7;
    public static final int SETSYS_NETD_DUPLICATE_PACKET_LINK = 8;
    public static final int SETSYS_PACKAGE_VERSION_NAME = 9;
    public static final int SETSYS_NETD_SET_FASTPATH_BY_UID = 15;
    public static final int SETSYS_NETD_SET_FASTPATH_BY_LINKINFO = 16;
    public static final int SETSYS_NETD_CLEAR_FASTPATH_RULES = 17;
    public static final int SETSYS_NETD_BOOSTER_CONFIG = 18;

    public static final int PERF_RES_NET_WIFI_SMART_PREDICT    = 0x02804100;
    public static final int PERF_RES_NET_MD_CRASH_PID          = 0x0280c300;
    public static final int PERF_RES_POWERHAL_SCREEN_OFF_STATE = 0x03400000;

    public static final int MTKPOWER_CMD_GET_RILD_CAP       = 40;
    public static final int MTKPOWER_CMD_GET_POWER_SCN_TYPE = 105;

    public static final int POWER_HIDL_SET_SYS_INFO    = 0;

    public static final int SCREEN_OFF_DISABLE      = 0;
    public static final int SCREEN_OFF_ENABLE       = 1;
    public static final int SCREEN_OFF_WAIT_RESTORE = 2;

    public static final int SCN_USER_HINT       = 2;
    public static final int SCN_PERF_LOCK_HINT  = 3;

    public static final int MAX_NETD_IP_FILTER_COUNT   = 3;

    private static boolean AMS_BOOST_PROCESS_CREATE = false;
    private static boolean AMS_BOOST_PROCESS_CREATE_BOOST = false;
    private boolean AMS_BOOST_PROCESS_CREATE_FOR_GAME = false;
    private boolean EXT_LAUNCH_FOR_GAME = false;
    private static boolean AMS_BOOST_PACK_SWITCH = false;
    private static boolean AMS_BOOST_ACT_SWITCH = false;
    private static boolean AMS_BOOST_HOT_LAUNCH = false;
    private static boolean EXT_PEAK_PERF_MODE = false;
    private static boolean sAnimationOn = true;
    private static boolean mIsColdLaunch = false;
    private static boolean sSmartLaunchOn = false;
    private static PowerHalWrapper sInstance = null;
    private static Object lock = new Object();
    public List<ScnList> scnlist = new ArrayList<ScnList>();
    private static String mProcessCreatePack = null;

    private static final boolean ENG = true;//"eng".equals(Build.TYPE);
    private final int LAUNCH_VERSION = SystemProperties.getInt("vendor.boostfwk.launch.version", 0);
    private final int LAUNCH_VERSION_1 = 1;
    private static boolean SMART_BOOST_ENABLE = SystemProperties.getBoolean(
                                              "vendor.boostfwk.smartboost.enable", false);
    private static final String[] GAME_LIBS = {"libunity.so", "libhegame.so"/* LUA engine */};
    private static final String GMS_GAMES_APP_ID = "com.google.android.gms.games.APP_ID";

    /* IMtkPower::hint. It shell be sync with mtkpower_hint.h */
    private static final int MTKPOWER_HINT_PROCESS_CREATE        = 21;
    private static final int MTKPOWER_HINT_PACK_SWITCH           = 22;
    private static final int MTKPOWER_HINT_ACT_SWITCH            = 23;
    private static final int MTKPOWER_HINT_APP_ROTATE            = 24;
    private static final int MTKPOWER_HINT_GALLERY_BOOST         = 26;
    private static final int MTKPOWER_HINT_WFD                   = 28;
    private static final int MTKPOWER_HINT_PMS_INSTALL           = 29;
    private static final int MTKPOWER_HINT_EXT_LAUNCH            = 30;
    private static final int MTKPOWER_HINT_WIPHY_SPEED_DL        = 32;
    private static final int MTKPOWER_HINT_PROCESS_CREATE_PERF_MODE_GAME = 54;
    private static final int MTKPOWER_HINT_EXT_LAUNCH_FOR_GAME   = 55;
    private static final int MTKPOWER_HINT_PROCESS_CREATE_PERF_MODE = 57;
    private static final int MTKPOWER_HINT_EXT_LAUNCH_ANIMATION_OFF   = 60;
    private static final int MTKPOWER_HINT_PROCESS_CREATE_BALANCE_MODE_GAME = 63;
    private static final int MTKPOWER_HINT_HOT_LAUNCH = 64;

    private static final int MTKPOWER_HINT_ALWAYS_ENABLE         = 0x0FFFFFFF;

    private static final int SETSYS_NOTIFY_SMART_LAUNCH_ENGINE   = 27;
    private static final int SETSYS_CODE_NOTIFY_SMART_LAUNCH_BOOT_START_END = 0;
    private static final int SMART_LAUNCH_DATA_BOOT_START         = 1;
    private static final int SMART_LAUNCH_DATA_BOOT_END           = 0;
    private static final int SETSYS_CODE_NOTIFY_SMART_LAUNCH_BOOT_INFO  = 1;
    private static final int SMART_LAUNCH_TYPE_COLD_LAUNCH        = 1;
    private static final int SMART_LAUNCH_TYPE_WARM_LAUNCH        = 2;
    private static final int SMART_LAUNCH_TYPE_HOT_LAUNCH         = 3;
    private static final int SETSYS_CODE_NOTIFY_SMART_LAUNCH_UNINSTALL_APP = 2;


    /* It shell be sync with mtkpower_types.h */
    private static final int MTKPOWER_STATE_PAUSED    = 0;
    private static final int MTKPOWER_STATE_RESUMED   = 1;
    private static final int MTKPOWER_STATE_DESTORYED = 2;
    private static final int MTKPOWER_STATE_DEAD      = 3;
    private static final int MTKPOWER_STATE_STOPPED   = 4;

    private static final int BOOST_PROCESS_CREATE_START = 1;
    private static final int BOOST_PROCESS_CREATE_STOP  = 0;

    public static native int nativeMtkPowerHint(int hint, int data);
    public static native int nativeMtkCusPowerHint(int hint, int data);
    public static native int nativeQuerySysInfo(int cmd, int param);
    public static native int nativeNotifyAppState(String packname, String actname,
                                                  int pid, int activityId, int status, int uid);
    public static native int nativeScnReg();
    public static native int nativeScnConfig(int hdl, int cmd,
                                             int param_1, int param_2, int param_3, int param_4);
    public static native int nativeScnUnreg(int hdl);
    public static native int nativeScnEnable(int hdl, int timeout);
    public static native int nativeScnDisable(int hdl);
    public static native int nativeScnUltraCfg(int hdl, int ultracmd,
                                             int param_1, int param_2, int param_3, int param_4);
    public static native int nativeSetSysInfo(String data, int type);
    public static native int nativeSetSysInfoAsync(String data, int type);
    public static native int nativeGameModeEnable(int enable, int pid);
    public static native int nativePerfLockAcq(int hdl, int duration, int... list);
    public static native int nativePerfLockRel(int hdl);
    public static native int nativePerfCusLockHint(int hint, int duration);

    public static native int nativeNotifySbeRescue(int tid, int start, int enhance,
            int rescue_type, long rescue_target, long frameId);

    public static native int nativeSbeSetHwuiPolicy(String depName, int depMode, int num,
            int tid, int begin, long frameId, int frameFlags);
    public static native int nativeSbeSetWebviewPolicy(String name, String specificName, int num,
            int pid, int mask, int start);
    public static native int nativeConsistencyPolicy(int enabled, int pid,
            int uclamp_min, int uclamp_max);
    public static native int nativeSbeSetSBB(int pid, int enabled, int activeRatio);
    public static native int nativeInitSBEAPI();

    private Lock mLock = new ReentrantLock();

    //private static IMtkPower mMtkPower = null;

    static {
        System.loadLibrary("powerhalwrap_jni");
    }

    public static PowerHalWrapper getInstance() {
        log("PowerHalWrapper.getInstance");
        synchronized (lock) {
            if (null == sInstance) {
                sInstance = new PowerHalWrapper();
            }
            return sInstance;
        }
    }

    private PowerHalWrapper() {

    }

    public void mtkPowerHint(int hint, int data) {
        nativeMtkPowerHint(hint, data);
    }

    public void mtkCusPowerHint(int hint, int data) {
        nativeMtkCusPowerHint(hint, data);
    }

    public void mtkNotifySbeRescue(int tid, int start, int enhance, long frameId) {
        nativeNotifySbeRescue(tid, start, enhance, 0, 0, frameId);
    }

    public void mtkNotifySbeRescue(int tid, int start, int enhance,
            int rescueType, long rescueTarget, long frameId) {
        nativeNotifySbeRescue(tid, start, enhance, rescueType, rescueTarget, frameId);
    }

    public int mtkSbeSetHwuiPolicy(int depMode, String depName, int num,
            int tid, int begin, long frameId, int frameFlags) {
        return nativeSbeSetHwuiPolicy(depName, depMode, num, tid, begin, frameId, frameFlags);
    }

    public int mtkSbeSetWebviewPolicy(String name, String specificName, int num,
            int pid, int mask, int start) {
        return nativeSbeSetWebviewPolicy(name, specificName, num, pid, mask, start);
    }

    public int mtkConsistencyPolicy(int enabled, int pid, int uclamp_min, int uclamp_max) {
        return nativeConsistencyPolicy(enabled, pid, uclamp_min, uclamp_max);
    }

    public int mtkSbeSetSBB(int pid, int enabled, int activeRatio) {
        return nativeSbeSetSBB(pid, enabled, activeRatio);
    }

    public int mtkInitSBEAPI() {
        return nativeInitSBEAPI();
    }

    public int perfLockAcquire(int handle, int duration, int... list) {
        int new_hdl;
        int pid = Binder.getCallingPid();
        int uid = Binder.getCallingUid();
        new_hdl = nativePerfLockAcq(handle, duration, list);
        if (new_hdl > 0 && new_hdl != handle && (duration > USER_DURATION_MAX || duration == 0)) {
            mLock.lock();
            ScnList user = new ScnList(new_hdl, pid, uid);
            scnlist.add(user);
            mLock.unlock();
        }
        return new_hdl;
    }

    public void perfLockRelease(int handle) {
        mLock.lock();
        if (null != scnlist && scnlist.size() > 0) {
            Iterator<ScnList> iter = scnlist.iterator();
            while (iter.hasNext()) {
                ScnList item = iter.next();
                if (item.gethandle() == handle)
                iter.remove();
            }
        }
        mLock.unlock();
        nativePerfLockRel(handle);
    }

    public int perfCusLockHint(int hint, int duration) {
        return nativePerfCusLockHint(hint, duration);
    }

    public int scnReg() {
        loge("scnReg not support!!!");
        return -1;
    }

    public int scnConfig(int hdl, int cmd, int param_1,
                                      int param_2, int param_3, int param_4) {
        loge("scnConfig not support!!!");
        return 0;
    }

    public int scnUnreg(int hdl) {
        loge("scnUnreg not support!!!");
        return 0;
    }

    public int scnEnable(int hdl, int timeout) {
        loge("scnEnable not support!!!");
        return 0;
    }

    public int scnDisable(int hdl) {
        loge("scnDisable not support!!!");
        return 0;
    }

    public int scnUltraCfg(int hdl, int ultracmd, int param_1,
                                             int param_2, int param_3, int param_4) {
        loge("scnUltraCfg not support!!!");
        return 0;
    }

    public void getCpuCap() {
        log("getCpuCap");
    }

    public void getGpuCap() {
        log("mGpuCap");
    }

    public void getGpuRTInfo() {
        log("getGpuCap");
    }

    public void getCpuRTInfo() {
        log("mCpuRTInfo");
    }

    public void UpdateManagementPkt(int type, String packet) {
        logd("<UpdateManagementPkt> type:" + type + ", packet:" + packet);

        switch(type) {

        case SETSYS_MANAGEMENT_PREDICT:
            nativeSetSysInfo(packet, SETSYS_MANAGEMENT_PREDICT);
            break;

        case SETSYS_MANAGEMENT_PERIODIC:
            nativeSetSysInfo(packet, SETSYS_MANAGEMENT_PERIODIC);
            break;

        default:
            break;
        }
    }

    public int setSysInfo(int type, String data) {
        //logd("<setSysInfo> type:" + type + " data:" + data);
        return nativeSetSysInfo(data, type);
    }

    public void setSysInfoAsync(int type, String data) {
        //logd("<setSysInfoAsync> type:" + type + " data:" + data);
        nativeSetSysInfoAsync(data, type);
    }

    public int querySysInfo(int cmd, int param) {
        logd("<querySysInfo> cmd:" + cmd + " param:" + param);
        return nativeQuerySysInfo(cmd, param);
    }

    public void mtkGameModeEnable(int enable, int pid) {
        log("<mtkGameModeEnable> enable: " + enable + " pid: " + pid);
        nativeGameModeEnable(enable, pid);
    }

    public void galleryBoostEnable(int timeoutMs) {
        log("<galleryBoostEnable> do boost with " + timeoutMs + "ms");
        nativeMtkPowerHint(MTKPOWER_HINT_GALLERY_BOOST, timeoutMs);
    }

    public void setRotationBoost(int boostTime) {
        log("<setRotation> do boost with " + boostTime + "ms");
        nativeMtkPowerHint(MTKPOWER_HINT_APP_ROTATE, boostTime);
    }

    public void setSpeedDownload(int timeoutMs) {
        log("<setSpeedDownload> do boost with " + timeoutMs + "ms");
        nativeMtkPowerHint(MTKPOWER_HINT_WIPHY_SPEED_DL, timeoutMs);
    }

    public void setWFD(boolean enable) {
        log("<setWFD> enable:" + enable);
        if (enable)
            nativeMtkPowerHint(MTKPOWER_HINT_WFD,
                                            MTKPOWER_HINT_ALWAYS_ENABLE);
        else
            nativeMtkPowerHint(MTKPOWER_HINT_WFD, 0);
    }

    public void setSportsApk(String pack) {
        log("<setSportsApk> pack:" + pack);
        nativeSetSysInfo(pack, SETSYS_SPORTS_APK);
    }

    public void NotifyAppCrash(int pid, int uid, String packageName) {
        int found = 0, type = -1, myPid;

        myPid = Process.myPid();

        /* It is not reasonable that crash pid is equal to caller pid */
        if (myPid == pid) {
            log("<NotifyAppCrash> pack:" + packageName + " ,pid:" + packageName + " == myPid:" + myPid);
            return;
        }

        /* Always calls nativeNotifyAppState for RILD first */
        ScnModule.notifyAppisGame(pid, packageName, null, 0);
        nativeNotifyAppState(packageName, packageName, pid, pid, MtkActState.STATE_DEAD, uid);

        mLock.lock();

        if (null != scnlist && scnlist.size() > 0) {
            Iterator<ScnList> iter = scnlist.iterator();
            while (iter.hasNext()) {
                ScnList item = iter.next();
                if (item.getpid() == pid) {
                    nativePerfLockRel(item.gethandle());
                    log("<NotifyAppCrash> pid:" + item.getpid() + " uid:"
                            + item.getuid() + " handle:" + item.gethandle());
                    iter.remove();
                    found++;
                }
            }
        }
        mLock.unlock();
    }

    public boolean getRildCap(int uid) {
        if (nativeQuerySysInfo(MTKPOWER_CMD_GET_RILD_CAP, uid) == 1)
            return true;
        else
            return false;
    }

    public void setInstallationBoost(boolean enable) {
        log("<setInstallationBoost> enable:" + enable);
        if (enable)
            nativeMtkPowerHint(MTKPOWER_HINT_PMS_INSTALL, 15000);
        else
            nativeMtkPowerHint(MTKPOWER_HINT_PMS_INSTALL, 0);
    }


    /* AMS event handler */
    public void amsBoostResume(String lastResumedPackageName,
                        String nextResumedPackageName, Boolean hasAttachedToProcess) {
        log("<amsBoostResume> last:" + lastResumedPackageName + ", next:"
            + nextResumedPackageName + "hasATProcess:" + hasAttachedToProcess);

        int hint;
        Trace.asyncTraceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "amPerfBoost", 0);
        /*--make sure not re-entry ext_launch--*/
        nativeMtkPowerHint(MTKPOWER_HINT_EXT_LAUNCH, 0);
        if (EXT_LAUNCH_FOR_GAME) {
            EXT_LAUNCH_FOR_GAME = false;
            nativeMtkPowerHint(MTKPOWER_HINT_EXT_LAUNCH_FOR_GAME, 0);
        }

        if (lastResumedPackageName == null ||
            !lastResumedPackageName.equalsIgnoreCase(nextResumedPackageName)) {
            if (LAUNCH_VERSION >= LAUNCH_VERSION_1) {
                if (hasAttachedToProcess) {
                    AMS_BOOST_HOT_LAUNCH = true;
                    hint = MTKPOWER_HINT_HOT_LAUNCH;
                } else { // warm launch
                    AMS_BOOST_PACK_SWITCH = true;
                    hint = MTKPOWER_HINT_PACK_SWITCH;
                }
            } else {
                AMS_BOOST_PACK_SWITCH = true;
                /*--main package switch--*/
                hint = MTKPOWER_HINT_PACK_SWITCH;
            }
            nativeMtkPowerHint(hint, AMS_BOOST_TIME);
        } else {
            log(" mIsColdLaunch :" + mIsColdLaunch);
            if (!mIsColdLaunch) {
                AMS_BOOST_ACT_SWITCH = true;

                /*--main activity switch--*/
                hint = MTKPOWER_HINT_ACT_SWITCH;
                nativeMtkPowerHint(hint, AMS_BOOST_TIME);
            }
        }
    }

    public void amsBoostProcessCreate(String hostingType, String packageName) {
        if(hostingType != null && hostingType.contains("activity") == true) {
            log("amsBoostProcessCreate package:" + packageName);

            Trace.asyncTraceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "amPerfBoost", 0);
            AMS_BOOST_PROCESS_CREATE_BOOST = true;
            mProcessCreatePack = packageName;

            /*--make sure not re-entry ext_launch--*/
            nativeMtkPowerHint(MTKPOWER_HINT_EXT_LAUNCH, 0);
            /*--main process create--*/
            if (LAUNCH_VERSION >= LAUNCH_VERSION_1) {
               if (EXT_LAUNCH_FOR_GAME) {
                   EXT_LAUNCH_FOR_GAME = false;
                   nativeMtkPowerHint(MTKPOWER_HINT_EXT_LAUNCH_FOR_GAME, 0);
               }

               if (isGameApp(packageName)) {
                    AMS_BOOST_PROCESS_CREATE_FOR_GAME = true;
                    if (sAnimationOn) {
                        nativeMtkPowerHint(MTKPOWER_HINT_PROCESS_CREATE_BALANCE_MODE_GAME,
                                           AMS_BOOST_TIME);
                    } else {
                        nativeMtkPowerHint(MTKPOWER_HINT_PROCESS_CREATE_PERF_MODE_GAME,
                                           AMS_BOOST_TIME);
                    }
                } else {
                    AMS_BOOST_PROCESS_CREATE = true;
                    if (sAnimationOn) {
                        if (SMART_BOOST_ENABLE && sSmartLaunchOn) {
                            amsNotifyAppLaunchInfoWithStartOrEnd(packageName, 0,
                                            SMART_LAUNCH_TYPE_COLD_LAUNCH,
                                            SMART_LAUNCH_DATA_BOOT_START, 0);
                        }
                        nativeMtkPowerHint(MTKPOWER_HINT_PROCESS_CREATE, AMS_BOOST_TIME);
                    } else {
                        nativeMtkPowerHint(MTKPOWER_HINT_PROCESS_CREATE_PERF_MODE,
                                        AMS_BOOST_TIME);
                    }
              }
            } else {
                AMS_BOOST_PROCESS_CREATE = true;
                nativeMtkPowerHint(MTKPOWER_HINT_PROCESS_CREATE, AMS_BOOST_TIME);
            }
        }
    }

    public void amsBoostStop() {
        log("amsBoostStop AMS_BOOST_PACK_SWITCH:" + AMS_BOOST_PACK_SWITCH +
            ", AMS_BOOST_ACT_SWITCH:" + AMS_BOOST_ACT_SWITCH + ", AMS_BOOST_PROCESS_CREATE:"
            + AMS_BOOST_PROCESS_CREATE + ", AMS_BOOST_PROCESS_CREATE_FOR_GAME:"
            + AMS_BOOST_PROCESS_CREATE_FOR_GAME + ", AMS_BOOST_HOT_LAUNCH:" + AMS_BOOST_HOT_LAUNCH);

        int hint;
        if (AMS_BOOST_PACK_SWITCH) {
            AMS_BOOST_PACK_SWITCH = false;
            hint = MTKPOWER_HINT_PACK_SWITCH;
            nativeMtkPowerHint(hint, 0);
        }

        if (AMS_BOOST_ACT_SWITCH) {
            AMS_BOOST_ACT_SWITCH = false;
            hint = MTKPOWER_HINT_ACT_SWITCH;
            nativeMtkPowerHint(hint, 0);
        }

        if (AMS_BOOST_HOT_LAUNCH) {
            AMS_BOOST_HOT_LAUNCH = false;
            hint = MTKPOWER_HINT_HOT_LAUNCH;
            nativeMtkPowerHint(hint, 0);
        }

        if (AMS_BOOST_PROCESS_CREATE) {
            AMS_BOOST_PROCESS_CREATE = false;
            hint = MTKPOWER_HINT_PROCESS_CREATE;
            if (LAUNCH_VERSION >= LAUNCH_VERSION_1) {
                if (sAnimationOn) {
                    hint = MTKPOWER_HINT_PROCESS_CREATE;
                } else {
                    hint = MTKPOWER_HINT_PROCESS_CREATE_PERF_MODE;
                }
            }
            nativeMtkPowerHint(hint, 0);
        }

       if (AMS_BOOST_PROCESS_CREATE_FOR_GAME) {
            AMS_BOOST_PROCESS_CREATE_FOR_GAME = false;
            if (sAnimationOn) {
                nativeMtkPowerHint(MTKPOWER_HINT_PROCESS_CREATE_BALANCE_MODE_GAME, 0);
            } else {
                nativeMtkPowerHint(MTKPOWER_HINT_PROCESS_CREATE_PERF_MODE_GAME, 0);
            }
            EXT_LAUNCH_FOR_GAME = true;
        }

        Trace.asyncTraceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER, "amPerfBoost", 0);
    }

    /*Notify uninstall app package name*/
    public void notifyAppUninstall(String packageName) {
        String packageinfo;

        if (LAUNCH_VERSION < LAUNCH_VERSION_1)
            return;

        if (packageName == null)
            return;

        packageinfo = "Code:" + SETSYS_CODE_NOTIFY_SMART_LAUNCH_UNINSTALL_APP + ","
                    + "Package:" + packageName + ","
                    + "Origin:" + 0 + ","
                    + "LaunchType:" + 0 + ","
                    + "Data:" + 0 + ","
                    + "AnimTime:" + 0 + ",";

        log("amsNotifyPackageName packageNameinfo: " + packageinfo);
        nativeSetSysInfoAsync(packageinfo, SETSYS_NOTIFY_SMART_LAUNCH_ENGINE);
    }

    /*Notify  app cold launch */
    public void notifyColdLaunchState(Boolean isBeginColdLaunch) {
        mIsColdLaunch = isBeginColdLaunch;
    }


    /* Notify app launch package name for start or end */
    public void amsNotifyAppLaunchInfoWithStartOrEnd(String packageName, int Origin,
                int launchType, int data, int animationTime) {
        String packageinfo;

        if (LAUNCH_VERSION < LAUNCH_VERSION_1 || !SMART_BOOST_ENABLE)
            return;
        if (packageName == null)
            return;
        if (!sAnimationOn || !sSmartLaunchOn)
            return;

        packageinfo = "Code:" + SETSYS_CODE_NOTIFY_SMART_LAUNCH_BOOT_START_END + ","
                    + "Package:" + packageName + ","
                    + "Origin:" + Origin + ","
                    + "LaunchType:" + launchType + ","
                    + "Data:" + data + ","
                    + "AnimTime:" + animationTime + ",";

        log("amsNotifyAppLaunchInfoWithStartOrEnd info: " + packageinfo);

        nativeSetSysInfoAsync(packageinfo, SETSYS_NOTIFY_SMART_LAUNCH_ENGINE);
    }

    /*Notify app launch TTID Time*/
    public void amsNotifyPackageTTID(String packageName, int time, int type) {
        String packageinfo;
        int hint;

        if (LAUNCH_VERSION < LAUNCH_VERSION_1 || !SMART_BOOST_ENABLE)
            return;
        if (packageName == null)
            return;
        if (!sAnimationOn || !sSmartLaunchOn)
            return;

        if (AMS_BOOST_PROCESS_CREATE) {
            amsNotifyAppLaunchInfoWithStartOrEnd("processCreatBoostStop", 0,
            SMART_LAUNCH_TYPE_COLD_LAUNCH, SMART_LAUNCH_DATA_BOOT_END, 0);

            hint = MTKPOWER_HINT_PROCESS_CREATE;
            nativeMtkPowerHint(hint, 0);
        }

        packageinfo = "Code:" + SETSYS_CODE_NOTIFY_SMART_LAUNCH_BOOT_INFO + ","
                    + "Package:" + packageName + ","
                    + "Origin:" + 0 + ","
                    + "LaunchType:" + type + ","
                    + "Data:" + time + ","
                    + "AnimTime:" + 0 + ",";

        log("amsNotifyPackageTTID packageNameinfo: " + packageinfo);

        nativeSetSysInfoAsync(packageinfo, SETSYS_NOTIFY_SMART_LAUNCH_ENGINE);
    }

    /*Notify app state*/
    public void amsBoostNotify(int pid, String activityName, String packageName, int activityId,
                               int uid, int state) {
        log("amsBoostNotify pid:" + pid + ",activity:" + activityName + ", package:"
            + packageName + ", mProcessCreatePack" + mProcessCreatePack);
        log("state: " + state);

        ScnModule.notifyAppisGame(pid, packageName, activityName, state);
        nativeNotifyAppState(packageName, activityName,
                                    pid, activityId, state, uid);

        log("amsBoostNotify AMS_BOOST_PROCESS_CREATE_BOOST:" + AMS_BOOST_PROCESS_CREATE_BOOST);
        if(mProcessCreatePack != null && packageName != null) {
            if(AMS_BOOST_PROCESS_CREATE_BOOST && !mProcessCreatePack.equals(packageName) && state == MTKPOWER_STATE_RESUMED) {
                // use 1ms timeout to avoid hold time
                //nativeMtkPowerHint(MTKPOWER_HINT_PROCESS_CREATE, 1);
                AMS_BOOST_PROCESS_CREATE_BOOST = false;
            }
        }
    }

    // notify animation change
    public void onNotifyAnimationStatus(boolean isOn) {
        sAnimationOn = isOn;
    }

    // notify smart launch boost change, oN or Off
    public void onNotifySmartLaunchBoostStatus(boolean isOn) {
        sSmartLaunchOn = isOn;
    }


    // Check if is game app.
    private boolean isGameApp(String pkgName) {
        IPackageManager pm = AppGlobals.getPackageManager();
        boolean isGame = false;
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, PackageManager.GET_META_DATA,
                    UserHandle.getCallingUserId());
            if (appInfo != null) {
                //Is game app
                if ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) ==
                        ApplicationInfo.FLAG_IS_GAME) {
                    isGame = true;
                } else if (appInfo.metaData != null
                        && appInfo.metaData.containsKey(GMS_GAMES_APP_ID)) {
                    isGame = true;
                } else {
                    for (String gameLibName : GAME_LIBS) {
                        File file = new File(appInfo.nativeLibraryDir, gameLibName);
                        if (file.exists()) {
                            isGame = true;
                            break;
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "judge exception :" + e);
        }
        return isGame;
    }

    private static void log(String info) {
        Log.i(TAG, info + " ");
    }

    private static void logd(String info) {
        if (ENG)
            Log.d(TAG, info + " ");
    }

    private static void loge(String info) {
        Log.e(TAG, "ERR: " + info + " ");
    }
}

