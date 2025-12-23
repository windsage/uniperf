/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2023. All rights reserved.
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

package com.mediatek.networkpolicymanager;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.mediatek.networkpolicymanager.booster.BoosterInfo;
import com.mediatek.networkpolicymanager.booster.NPMBooster;
import com.mediatek.networkpolicymanager.fastswitch.FastSwitchInfo;
import com.mediatek.networkpolicymanager.fastswitch.IFastSwitchListener;
import com.mediatek.networkpolicymanager.fastswitch.NPMFastSwitch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** NetworkPolicyManager provoide APIs to call. */
public class NetworkPolicyManager {
    private static final String TAG = "NetworkPolicyManager";
    /**
     * Property for enable function name log.
     * Disable it now because U is first version and VF version before U will warn selinux.
     * 0: disable, != 0: enable function name.
     * @hide
     */
    public static final int NAME_LOG = 0; // SystemProperties.getInt("persist.vendor.jxnps.log", 0);

    /**
     * INetworkPolicyService service name, used to ServiceManager add/get NetworkPolicyService.
     * @hide
     */
    public static final String JXNPS_SERVICE_NAME = "JXNetworkPolicyService";

    /**
     * Below is sub-feature names supported by INetworkPolicyService,
     * used to check whether the sub-feature service is supported or not.
     * NOTE: Corresponding to the feature name in NOSManager, they should be modified together.
     */
    /**
     * APK Booster feature name, used to check whether APK Booster service is supported or not.
     * @hide
     */
    public static final String JXNPS_APK_BOOSTER = "JXNPS_APK_BOOSTER";
    /**
     * Fast Switch feature name, used to check whether Fast Switch service is supported or not.
     * @hide
     */
    public static final String JXNPS_FAST_SWITCH = "JXNPS_FAST_SWITCH";
    // New feature name added here please

    /* NetworkPolicyManager singleton instance */
    private static NetworkPolicyManager sInstance = new NetworkPolicyManager();

    /* INetworkPolicyService singleton instance */
    private static volatile INetworkPolicyService sNPService = null;
    private static final Object sNPServiceLock = new Object();
    /* NetworkPolicyService died callback */
    private static final DeathRecipient sNPServiceDeath = new DeathRecipient();

    private final Context mContext;

    private static final Object sINOSListenerLock = new Object();
    // Below variables are related with FastSwitch, but it is difficult to move into fastswitch
    // folder. Process it later.
    /* INOSListener registered RemoteCallbackList */
    private static RemoteCallbackList<INOSListener> sINOSListenerRemoteCallbackList
            = new RemoteCallbackList<>() {
                @Override
                public void onCallbackDied(INOSListener l, Object cookie) {
                    synchronized (sINOSListenerLock) {
                        boolean s = true;
                        Bundle b = new Bundle();
                        String ifname = sPidIfNames.get((Integer) cookie);
                        b.putString(FastSwitchInfo.BK_APK_CAP, sListenerVersions.get(l));
                        b.putString(FastSwitchInfo.BK_APK_PID,
                                String.valueOf(sListenerPids.get(l)));
                        b.putString(FastSwitchInfo.BK_APK_UID,
                                String.valueOf(sPidUids.get((Integer) cookie)));
                        b.putString(FastSwitchInfo.BK_IF_NAME,
                                sDefaultIfName.equals(ifname) ? null : ifname);
                        b.putString(FastSwitchInfo.BK_REQ_SOURCE, FastSwitchInfo.BV_REQ_SOURCE_PP);
                        if (sPidNetTypes.get((Integer) cookie) == null) {
                            s = false;
                        } else {
                            NetworkPolicyManager.getDefaultInstance()
                                    .stopMonitor(sPidNetTypes.get((Integer) cookie), b);
                        }

                        if (l != null) {
                            sListenerPids.remove(l);
                            sListenerVersions.remove(l);
                            sINOSListenerHashSet.remove(l);
                        }
                        if (cookie != null) {
                            sPidNetTypes.remove((Integer) cookie);
                            sPidVersions.remove((Integer) cookie);
                            sPidIfNames.remove((Integer) cookie);
                            sPidUids.remove((Integer) cookie);
                            sPidOperFlag.remove((Integer) cookie);
                        }
                        logi("onCallbackDied: FW l-" + l + ", pid-" + (Integer) cookie + ", s-" + s
                                + ", size(" + sListenerPids.size() + "/" + sListenerVersions.size()
                                + "/" + sPidNetTypes.size() + "/" + sPidUids.size()
                                + "/" + sPidVersions.size() + "/" + sPidIfNames.size()
                                + "/" + sINOSListenerHashSet.size() + "/" + sPidOperFlag.size()
                                + ")");
                    }
                }
            };
    /* INOSListener registered list, used to ensure that INOSListener is unique */
    private static HashSet<INOSListener> sINOSListenerHashSet = new HashSet<>();
    private static final int INOS_LISTENER_MAX = 100;
    /* IFastSwitchListener singleton instance */
    private static volatile IFastSwitchListener.Stub sIFastSwitchListener = null;
    private static boolean sIsIFastSwitchListenerRegistered = false;
    private static final Object sFastSwitchListenerLock = new Object();

    // Below variables are related with FastSwitch version control
    /* Current NPM version, stable AILD version is major, minor indicates NOS service */
    private static final String sCurNPMcap = "1.1";
    private static final String sDefaultIfName = "default";
    private static ArrayMap<Integer, Integer> sPidUids = new ArrayMap<Integer, Integer>();
    private static ArrayMap<Integer, Integer> sPidNetTypes = new ArrayMap<Integer, Integer>();
    private static ArrayMap<Integer, String> sPidVersions = new ArrayMap<Integer, String>();
    private static ArrayMap<Integer, String> sPidIfNames = new ArrayMap<Integer, String>();
    private static ArrayMap<INOSListener, Integer> sListenerPids
            = new ArrayMap<INOSListener, Integer>();
    private static ArrayMap<INOSListener, String> sListenerVersions
            = new ArrayMap<INOSListener, String>();

    // Foolproof mechanism for background user.
    // Only for "platform" source APK here. Source "sdk" APK is protected in NPS layer.
    private static ArrayMap<Integer, Integer> sPidOperFlag = new ArrayMap<Integer, Integer>();
    private static final int FLAG_INIT_MONITOR  = 0; // Initial state
    private static final int FLAG_FPP_PAU_MONITOR = 1 << 0; // Pause from foolproof protection
    private static final int FLAG_FPP_RES_MONITOR = 1 << 1; // Resume from foolproof protection
    private static final int FLAG_APK_STR_MONITOR = 1 << 2; // Start from APK
    private static final int FLAG_APK_RES_MONITOR = 1 << 3; // Resume from APK
    private static final int FLAG_APK_PAU_MONITOR = 1 << 4; // Pause from APK
    private static final int FLAG_APK_STP_MONITOR = 1 << 5; // Stop from APK

    /**
     * Callback by AmsExtImpl.java when APK Activity switches between foreground (resumed state
     * with focus) and background (paused/stopped/destoryed state).
     *
     * @param isTop Activity state, resumed or paused.
     * @param pid APK pid.
     * @param uid APK uid.
     * @param actName APK activity name.
     * @param pkgName APK package name.
     * @hide
     */
    public void onActivityStateChanged(boolean isTop, int pid, int uid, String actName,
            String pkgName) {
        /*
         * FastSwitch needs foolproof mechanism by calling pauseMonitor to avoid power costing
         * when caller goes to background.
         *
         * Here will try doing foolproof mechanism for low power to avoid costing power when the
         * user (APK) goes to background without calling pauseMonitor or stopMonitor.
         *
         * Only APKs with source type "platform" are applied now because only info of APKs with
         * "platform" source type is recorded in this file in NPM layer.
         *
         * APKs with source type "sdk" should be tried doing foolproof mechanism for low power in
         * NPS layer like this function when the user (APK) goes to background without calling
         * pauseMonitor or stopMonitor because "sdk" APK info is record in NPS layer.
         */
        handleActivityStateChanged(isTop, pid, uid, actName, pkgName);
        // APKBooster needn't foolproof mechanism because caller has background acceleration mode.
        // So, NPM cannot make decisions for callers.
    }

    /**
     * The default constructor of NetworkPolicyManager.
     */
    private NetworkPolicyManager() {
        mContext = null;
    }

    /**
     * The constructor of NetworkPolicyManager.
     *
     * Construt an instannce with a Context
     *
     * @param context a Context.
     * @hide
     */
    public NetworkPolicyManager(Context context) {
        mContext = context;
    }

    /**
     * Get a default NetworkPolicyManager singleton instance.
     *
     * @return singleton default instance of NetworkPolicyManager.
     */
    public static NetworkPolicyManager getDefaultInstance() {
        return sInstance;
    }

    /**
     * get INetworkPolicyService instance.
     *
     * @return INetworkPolicyService service or null.
     *
     * @hide
     */
    private INetworkPolicyService getINetworkPolicyService() {
        if (sNPService == null) {
            synchronized (sNPServiceLock) {
                if (sNPService == null) {
                    IBinder b = ServiceManager.checkService(JXNPS_SERVICE_NAME);
                    try {
                        if (b != null) {
                            sNPService = INetworkPolicyService.Stub.asInterface(b);
                            sNPService.asBinder().linkToDeath(sNPServiceDeath, 0);
                        } else {
                            loge("getINetworkPolicyService() fail");
                        }
                    } catch (RemoteException e) {
                        // something has gone horribly wrong
                        loge("getINetworkPolicyService() fail: " + e);
                        sNPService = null;
                    }
                }
            }
        }
        return sNPService;
    }

    /**
     * Callback class when the process hosting INetworkPolicyService has gone away.
     *
     * @hide
     */
    private static class DeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            resetNPService();
        }
    }

    /**
     * Reset INetworkPolicyService when its server die.
     *
     * @hide
     */
    private static void resetNPService() {
        synchronized (sNPServiceLock) {
            if (sNPService != null) {
                sNPService.asBinder().unlinkToDeath(sNPServiceDeath, 0);
                sNPService = null;
            }
            // NPService die.
            // Clear local sub-feature information to avoid resume fail.
            resetFastSwitch();
            // can callback to client here
            loge("resetNPService(): INetworkPolicyService died");
        }
    }

    /**
     * Query whether the specified feature supported and return supported version.
     *
     * Every feature service will be optimized and upgrade in future. This function will return
     * which version supported for specified feature. Client can do function control.
     *
     * @param feature specified feature name to be queried.
     * @return 0 if not support, a number greater than 0 indicating the feature version.
     */
    public int versionSupported(String feature) {
        int versionSupported = 0; // 0 indicates unsupported
        INetworkPolicyService service = getINetworkPolicyService();

        if (service != null) {
            try {
                versionSupported = service.versionSupported(feature);
            } catch (RemoteException e) {
                loge("versionSupported fail: " + e);
            }

            if (versionSupported > 0) {
                logi(feature + " is supported and version is " + versionSupported);
            } else {
                loge(feature + " is unsupported");
            }
        } else {
            loge("INetworkPolicyService is not supported, so " + feature + " is unsupported");
        }
        return versionSupported;
    }

    /**
     * Enable or disable the specified feature.
     *
     * Applications must have the com.mediatek.permission#SWITCH_NOS_FEATURE permission to toggle
     * the specified feature.
     *
     * @param c caller context.
     * @param feature specified feature name to be switched.
     * @param enabled true to enable, false to disable.
     * @return false if the request cannot be satisfied; true indicates that the specified feature
     *         either already in the requested state, or in progress toward the requested state.
     */
    public boolean setFeatureEnabled(Context c, String feature, boolean enabled) {
        if (c == null) {
            loge("[NOSManager]: setFeatureEnabled, context is null, cannot check permission");
            return false;
        } else {
            c.enforceCallingOrSelfPermission("com.mediatek.permission.SWITCH_NOS_FEATURE",
                    "Permission denied for setFeatureEnabled");
        }

        if (JXNPS_FAST_SWITCH.equals(feature)) {
            int ret = -1;
            Bundle b = new Bundle();
            b.putString(FastSwitchInfo.BK_FEATURE_CTRL, (enabled ? "1" : "-1"));
            FastSwitchInfo info = new FastSwitchInfo(FastSwitchInfo.NETWORK_TYPE_WIFI,
                    FastSwitchInfo.ACTION_REQ_FEA_OPS, b);
            ret = configFastSwitchInfoWithIntResult(info);
            return (ret == 0 ? true : false);
        } else if (JXNPS_APK_BOOSTER.equals(feature)) {
            // This switch operation doesn't care about level. But level must be a valid value.
            /**
            * Action BOOSTER_ACTION_FEA_OPS is only used by platform to enable/disable Booster.
            * Note: put your operation with parameters in a int[].
            * Format {operationNumber}. -1 for disable, 1 for enable.
            */
            BoosterInfo info = new BoosterInfo(BoosterInfo.BOOSTER_GROUP_A,
                    BoosterInfo.BOOSTER_ACTION_FEA_OPS, null,
                    (enabled ? (new int[]{1}) : (new int[]{-1})));
            return configBoosterInfo(info);
        } else {
            loge("NOS doesn't include feature: " + feature
                    + (enabled ? ", enable" : ", disable") + " it fail.");
            return false;
        }
    }

    /**
     * Query the status of the specified feature.
     *
     * Applications must have the com.mediatek.permission.SWITCH_NOS_FEATURE permission to query
     * the status of the specified feature.
     *
     * @param c caller context.
     * @param feature specified feature name to be queried.
     * @return false for disabled, true for enabled.
     */
    public boolean isFeatureEnabled(Context c, String feature) {
        if (c == null) {
            loge("[NOSManager]: isFeatureEnabled, context is null, cannot check permission");
            return false;
        } else {
            c.enforceCallingOrSelfPermission("com.mediatek.permission.SWITCH_NOS_FEATURE",
                    "Permission denied for isFeatureEnabled");
        }

        if (JXNPS_FAST_SWITCH.equals(feature)) {
            int ret = -1;
            FastSwitchInfo info = new FastSwitchInfo(FastSwitchInfo.NETWORK_TYPE_WIFI,
                    FastSwitchInfo.ACTION_REQ_FEA_OPS, null);
            ret = configFastSwitchInfoWithIntResult(info);
            return (ret == 0 ? true : false);
        } else if (JXNPS_APK_BOOSTER.equals(feature)) {
            // This query doesn't care about level. But level must be a valid value.
            /**
            * Action BOOSTER_ACTION_FEA_OPS is only used by platform to query status of Booster.
            * Note: put your operation with parameters in a int[].
            * null for operation 0. Here null is set directly.
            */
            BoosterInfo info = new BoosterInfo(BoosterInfo.BOOSTER_GROUP_A,
                    BoosterInfo.BOOSTER_ACTION_FEA_OPS, null, null);
            return configBoosterInfo(info);
        } else {
            loge("NOS doesn't include feature: " + feature + ", false directly for query.");
            return false;
        }
    }

    /*************************************APK Booster*********************************************/
    // APK Booster start @{
    /**
     * Add an uid into booster to accelerate the corresponding APK.
     *
     * @param level at which level the application is accelerated by booster.
     * @param uid application uid accelerated by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean addUidToBooster(int level, int uid) {
        BoosterInfo info = new BoosterInfo(level, BoosterInfo.BOOSTER_ACTION_ADD_BY_UID, uid);
        return configBoosterInfo(info);
    }

    /**
     * Remove an uid from booster to stop accelerating the corresponding APK.
     *
     * @param level at which level the application is accelerated by booster.
     * @param uid application uid stopped acceleration by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteUidFromBooster(int level, int uid) {
        BoosterInfo info = new BoosterInfo(level, BoosterInfo.BOOSTER_ACTION_DEL_BY_UID, uid);
        return configBoosterInfo(info);
    }

    /**
     * Remove all uids from booster to stop accelerating the corresponding APK.
     *
     * @param level at which level the applications are accelerated by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteAllUidsFromBooster(int level) {
        BoosterInfo info = new BoosterInfo(level, BoosterInfo.BOOSTER_ACTION_DEL_BY_UID_ALL);
        return configBoosterInfo(info);
    }

    /**
     * Add an link info into booster to setup the corresponding stream.
     *
     * @param level at which level the link info stream is accelerated by booster.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @return true for success, false for fail.
     *
     */
    public boolean addLinkInfoToBooster(int level, String srcIp, String dstIp, int srcPort,
            int dstPort, int proto) {
        BoosterInfo info = new BoosterInfo(level, BoosterInfo.BOOSTER_ACTION_ADD_BY_LINKINFO,
                srcIp, dstIp, srcPort, dstPort, proto);
        return configBoosterInfo(info);
    }

    /**
     * Remove an link info from booster to stop accelerating the corresponding stream.
     *
     * @param level at which level the link info stream is accelerated by booster.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteLinkInfoFromBooster(int level,  String srcIp, String dstIp, int srcPort,
            int dstPort, int proto) {
        BoosterInfo info = new BoosterInfo(level, BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO,
                srcIp, dstIp, srcPort, dstPort, proto);
        return configBoosterInfo(info);
    }

    /**
     * Remove all link info from booster to stop accelerating the corresponding streams.
     *
     * @param level at which level the link info stream are accelerated by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteAllLinkInfoFromBooster(int level) {
        BoosterInfo info = new BoosterInfo(level, BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO_ALL);
        return configBoosterInfo(info);
    }

    /**
     * Remove all link info / uids from booster to stop accelerating the corresponding streams.
     *
     * @param level at which level the link info / uids stream are accelerated by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteAllFromBooster(int level) {
        BoosterInfo info = new BoosterInfo(level, BoosterInfo.BOOSTER_ACTION_DEL_ALL);
        return configBoosterInfo(info);
    }

    /**
     * Universal operation function.
     * Format {cmdStr, parmStr}. cmdStr is necessary. User defines detail content.
     * sample: String[] command = {"priority_set_toup", "192.168.1.100 80 192.168.1.101 80 TCP"}.
     * sample: String[] command = {"add_reset_iptable", "iptables -A OUTPUT -j drop"}.
     * sample: int[] categories = {0}.
     *
     * @param level at which level the cmd will be done by booster.
     * @param cmd command with parameters will be executed by booster.
     * @param val for operation category. null for category 0. Valid value of val[0] is [0, 3].
     *            cmdStr must include category name like "firewall priority_set_toup" when val[0]
     *            is 3. Setting val[0] as 3 is for more extensions.
     * @return true for success, false for fail.
     *
     */
    public boolean doBoosterExtOperation(int level, String[] cmd, int[] val) {
        BoosterInfo info = new BoosterInfo(level, BoosterInfo.BOOSTER_ACTION_DBG_EXT, cmd, val);
        return configBoosterInfo(info);
    }

    /**
     * Configure BoosterInfo to enable or disable booster for specified application.
     *
     * @param info to be config.
     * @return true for success, false for fail.
     */
    private boolean configBoosterInfo(BoosterInfo info) {
        boolean status = false;
        // Try to get service to avoid having died
        INetworkPolicyService service = getINetworkPolicyService();
        if (service != null) {
            status = NPMBooster.getInstance(mContext).configBoosterInfo(info, service);
        } else {
            loge("[Booster]: INetworkPolicyService is not existed.");
        }
        return status;
    }
    // APK Booster end @}

    /*************************************Fast Swtich*********************************************/
    // Fast Swtich start @{
    /**
     * Fast Switch to handle Activity State Change from AMS.
     *
     * @param isTop Activity state, resumed or paused.
     * @param pid APK pid.
     * @param uid APK uid.
     * @param actName APK activity name.
     * @param pkgName APK package name.
     * @hide
     */
    private void handleActivityStateChanged(boolean isTop, int pid, int uid, String actName,
            String pkgName) {
        int act = FastSwitchInfo.ACTION_REQ_BASE;
        int opFlag = FLAG_INIT_MONITOR;
        Bundle b = null;
        FastSwitchInfo info = null;
        synchronized (sINOSListenerLock) {
            // Here is to process nos platform user to reduce power and time consumption.
            if (sPidNetTypes.containsKey(pid)) {
                if (sPidNetTypes.get(pid) != null) {
                    opFlag = (sPidOperFlag.get(pid) != null) ? sPidOperFlag.get(pid) : opFlag;
                    if (isTop && ((opFlag & FLAG_FPP_PAU_MONITOR) > 0)) {
                        act = FastSwitchInfo.ACTION_REQ_RES_MNR;
                    } else if (!isTop && ((opFlag & FLAG_FPP_PAU_MONITOR) == 0)) {
                        act = FastSwitchInfo.ACTION_REQ_PAU_MNR;
                    } else {
                        // ignore duplicated action
                        logi("handleActivityStateChanged: isTop: " + isTop + ", pid: " + pid
                                + ", uid: " + uid + ", actName: " + actName + ", pkgName: "
                                + pkgName + ", opFlag: " + opFlag);
                        return;
                    }
                    b = new Bundle();
                    b.putString(FastSwitchInfo.BK_APK_CAP, sPidVersions.get(pid));
                    b.putString(FastSwitchInfo.BK_APK_PID,  String.valueOf(pid));
                    b.putString(FastSwitchInfo.BK_APK_UID, String.valueOf(uid));
                    b.putString(FastSwitchInfo.BK_IF_NAME, sPidIfNames.get(pid));
                    b.putString(FastSwitchInfo.BK_REQ_SOURCE, FastSwitchInfo.BV_REQ_SOURCE_PP);
                    info = new FastSwitchInfo(sPidNetTypes.get(pid), act, b);
                    logd("handleActivityStateChanged: isTop: " + isTop + ", pid: " + pid
                            + ", uid: " + uid + ", actName: " + actName + ", pkgName: " + pkgName
                            + ", opFlag: " + opFlag + ", act: " + act);

                } else {
                    loge("[FastSwitch]: handleActivityStateChanged why networktype is null?");
                    return;
                }
            } else {
                  // not nos sdk user, return directly
                  return;
            }
        }

        // The purpose of pause/resume here is to decouple (avoid deadlock).
        configFastSwitchInfo(info);
    }

    /**
     * Update new flag for current action.
     *
     * Note: Callers must hold sINOSListenerLock.
     * Note: Only for start/stop/pause/resume monitor operations.
     *
     * @param info operation information.
     * @return old flag.
     * @hide
     */
    private int resetOperFlag(FastSwitchInfo info) {
        Bundle b = info.getBundle();
        if (b == null) {
            // Need Monitor actions' bundle info to check
            loge("[FastSwitch]: no bundle info for reset flag");
            return -1;
        }

        String source = b.getString(FastSwitchInfo.BK_REQ_SOURCE);
        if (TextUtils.isEmpty(source)) {
            // Don't know how to protect without source information
            loge("[FastSwitch]: no source info for reset flag");
            return -1;
        }

        if (!source.equals(FastSwitchInfo.BV_REQ_SOURCE_P)
                && !source.equals(FastSwitchInfo.BV_REQ_SOURCE_PP)) {
            // Only protect "sdk" user, "platform" user is protected in NPM layer.
            logd("[FastSwitch]: non sdk/fwservice source");
            return -1;
        }

        int unset = FLAG_INIT_MONITOR;
        int set = FLAG_INIT_MONITOR;
        switch (info.getAction()) {
        case FastSwitchInfo.ACTION_REQ_STR_MNR:
            if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_P)) {
                // Only FastSwitchInfo.BV_REQ_SOURCE_S can do start action
                unset = 0x7fffffff;
                set = FLAG_APK_STR_MONITOR;
            } else {
                loge("[FastSwitch]: wrong usage, fwservice shouldn't start monitor");
            }
            break;
        case FastSwitchInfo.ACTION_REQ_RES_MNR:
            if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_P)) {
                unset = FLAG_APK_STR_MONITOR | FLAG_APK_PAU_MONITOR | FLAG_APK_STP_MONITOR;
                set = FLAG_APK_RES_MONITOR;
            } else {
                unset = FLAG_FPP_PAU_MONITOR;
                set = FLAG_FPP_RES_MONITOR;
            }
            break;
        case FastSwitchInfo.ACTION_REQ_PAU_MNR:
            if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_P)) {
                unset = FLAG_APK_STR_MONITOR | FLAG_APK_RES_MONITOR | FLAG_APK_STP_MONITOR;
                set = FLAG_APK_PAU_MONITOR;
            } else {
                unset = FLAG_FPP_RES_MONITOR;
                set = FLAG_FPP_PAU_MONITOR;
            }
            break;
        case FastSwitchInfo.ACTION_REQ_STP_MNR:
            if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_P)) {
                unset = FLAG_APK_STR_MONITOR | FLAG_APK_RES_MONITOR | FLAG_APK_PAU_MONITOR;
                set = FLAG_APK_STP_MONITOR;
            } else {
                // NPM stop monitor is crash protection. it will clear all info later.
                logd("[FastSwitch]: ignore NPM protection stop for crashed \"platform\" APK");
                return -1;
            }
            break;
        default:
            logd("[FastSwitch]: wrong action " + info.getAction() + " to reset flag");
            return -1;
        }

        String pid = b.getString(FastSwitchInfo.BK_APK_PID);
        if (TextUtils.isEmpty(pid) || (sPidOperFlag.get(Integer.valueOf(pid)) == null)) {
            loge("[FastSwitch]: no pid info or non-sdk user to reset flag");
            return -1;
        }

        try {
            int oflag = sPidOperFlag.get(Integer.valueOf(pid));
            int nflag = oflag & ~unset | set;
            sPidOperFlag.put(Integer.valueOf(pid), nflag);
            return oflag;
        } catch (NullPointerException e) {
            loge("[FastSwitch]: wrong usage, pid " + pid + ", e " + e);
            return -1;
        }
    }

    /**
     * Remove all callbacks from RemoteCallbackList.
     * RemoteCallbackList.kill() will disable RemoteCallbackList, so cannot cover the function of
     * remove all callbacks.
     *
     * Note: Callers must held sINOSListenerLock.
     */
    private static void removeAllINOSListenerFromRemoteCallbackList() {
        int n = sINOSListenerRemoteCallbackList.beginBroadcast();
        if (n < 1) {
            loge("[FastSwitch]: removeAllINOSListenerFromRemoteCallbackList, No INOSListener");
            return;
        }

        for (int i = n - 1; i >= 0; i--) {
            logd("[FastSwitch]: removeAllINOSListenerFromRemoteCallbackList: " + n + ":" + i
                    + ": " + sINOSListenerRemoteCallbackList.getBroadcastItem(i));
            sINOSListenerRemoteCallbackList.unregister(
                    sINOSListenerRemoteCallbackList.getBroadcastItem(i));
        }

        sINOSListenerRemoteCallbackList.finishBroadcast();
        sINOSListenerHashSet.clear();
        sPidUids.clear();
        sPidNetTypes.clear();
        sPidVersions.clear();
        sPidIfNames.clear();
        sPidOperFlag.clear();
        sListenerVersions.clear();
        sListenerPids.clear();
     }

    /**
     * Reset FastSwitch infomation.
     */
    private static void resetFastSwitch() {
        synchronized (sFastSwitchListenerLock) {
            if (sIFastSwitchListener != null) {
                sIFastSwitchListener = null;
                sIsIFastSwitchListenerRegistered = false;
            }
        }

        synchronized (sINOSListenerLock) {
            int n = sINOSListenerRemoteCallbackList.beginBroadcast();
            if (n < 1) {
                loge("[FastSwitch]: resetFastSwitch, No INOSListener");
                return;
            }

            try {
                for (int i = 0; i < n; i++) {
                    logd("[FastSwitch]: resetFastSwitch: " + n + ":" + i + ": "
                            + sINOSListenerRemoteCallbackList.getBroadcastItem(i));
                    sINOSListenerRemoteCallbackList.getBroadcastItem(i)
                            .onLinstenerStateChanged(false, null);
                    sINOSListenerRemoteCallbackList.getBroadcastItem(i)
                            .onServiceDie();
                }
            } catch (RemoteException e) {
                loge("[FastSwitch]: resetFastSwitch listener callback fail: " + e);
            } catch (Exception e) {
                // Avoid hacker using this vulnerability for attacks.
                // The vulnerability is that client makes RuntimeException to
                // make beginBroadcast exception to block callback to other APKs.
                loge("[FastSwitch]: resetFastSwitch listener callback fail, Exception: " + e);
            }
            sINOSListenerRemoteCallbackList.finishBroadcast();
            removeAllINOSListenerFromRemoteCallbackList();
        }

        NPMFastSwitch.getInstance(null).stopGeneralReportListenerThread();
    }

    /**
     * Check whether the information is reported to current listener or not.
     *
     * @param b including information from NOS server.
     * @param l current listener.
     * @return true to report, otherwise fase.
     */
    private boolean needReport(Bundle b, INOSListener l) {
        synchronized (sINOSListenerLock) {  // same lock can be nested
            if ((b == null) || (l == null)) {
                loge("[FastSwitch]: needReport, return listener is null return false");
                return false;
            }

            String c = (b.getString(FastSwitchInfo.BK_PF_CAP) != null)
                    ? b.getString(FastSwitchInfo.BK_PF_CAP) : "";
            // NOS Server should report pid list format is a string with delimiter
            String apid = (b.getString(FastSwitchInfo.BK_APK_PID) != null)
                    ? b.getString(FastSwitchInfo.BK_APK_PID) : "";
            String ifname = (b.getString(FastSwitchInfo.BK_IF_NAME) != null)
                    ? b.getString(FastSwitchInfo.BK_IF_NAME) : "";

            List<String> apids = (TextUtils.isEmpty(apid)) ? null : Arrays.asList(apid.split(","));

            String cc = sListenerVersions.get(l);
            String capid = String.valueOf(sListenerPids.get(l));
            String cifname = sPidIfNames.get(sListenerPids.get(l));

            logi("[FastSwitch]: needReport, cap:" + c + ", ifname:" + ifname + ", apid:" + apid
                    + ", ccap:" + cc + ", cifname:" + cifname + ", capid:" + capid);
            cifname = sDefaultIfName.equals(cifname) ? "wlan0" : cifname;

            if (c.equals(cc) && ifname.equals(cifname) && (capid != null)
                    && (apids != null) && apids.contains(capid)) {
                logi("[FastSwitch]: needReport, return true");
                return true;
            }
            return false;
        }
    }

    /**
     * get IFastSwitchListener.Stub instance.
     *
     * @return IFastSwitchListener.Stub or null.
     */
    private IFastSwitchListener.Stub getIFastSwitchListener() {
        if (sIFastSwitchListener == null) {
            synchronized (sFastSwitchListenerLock) {
                if (sIFastSwitchListener == null) {
                    sIFastSwitchListener = new IFastSwitchListener.Stub() {
                        @Override
                        public void onNativeFastSwitchServiceDied() {
                            loge("[FastSwitch]: onNativeFastSwitchServiceDied");
                            resetFastSwitch();
                        }

                        @Override
                        public void onFastSwitchInfoNotify(FastSwitchInfo info) {
                            int act = info.getAction();
                            int net = info.getNetworkType();
                            int sta = info.getMonitorState();
                            int sen = info.getMonitorSensitivity();
                            int llq = info.getLocalLinkQuality();
                            int flq = info.getFullLinkQuality();
                            int ss  = info.getSignalStrength();
                            Bundle b = info.getBundle();
                            if (b == null) {
                                loge("[FastSwitch]: onFastSwitchInfoNotify, b is null");
                                return;
                            }
                            String c = (b.getString(FastSwitchInfo.BK_PF_CAP) != null)
                                    ? b.getString(FastSwitchInfo.BK_PF_CAP) : "";
                            String apid = (b.getString(FastSwitchInfo.BK_APK_PID) != null)
                                    ? b.getString(FastSwitchInfo.BK_APK_PID) : "";
                            String ifname = (b.getString(FastSwitchInfo.BK_IF_NAME) != null)
                                    ? b.getString(FastSwitchInfo.BK_IF_NAME) : "";

                            if (TextUtils.isEmpty(c) || TextUtils.isEmpty(apid)
                                    || TextUtils.isEmpty(ifname)) {
                                loge("[FastSwitch]: onFastSwitchInfoNotify, empty item, "
                                        + "cap:" + c + ", ifname:" + ifname + ", apid:" + apid);
                                return;
                            }

                            synchronized (sINOSListenerLock) {
                                int n = sINOSListenerRemoteCallbackList.beginBroadcast();
                                if (n < 1) {
                                    loge("[FastSwitch]: onFastSwitchInfoNotify, No INOSListener, "
                                            + "Need check who registerIFastSwitchListener");
                                    return;
                                }

                                String pfx = "[FastSwitch]: onFastSwitchInfoNotify, action:" + act
                                        + ", reportCapability:" + c + ", ifname:" + ifname
                                        + ", apid:" + apid + ", net:" + net;
                                // Callback will check capability, ifname and pid.
                                // Listener callback if and only if the three indices match
                                try {
                                    switch (act) {
                                    case FastSwitchInfo.ACTION_REPORT_STA:
                                        logi(pfx + ", state:" + sta);
                                        for (int i = 0; i < n; i++) {
                                            INOSListener l = sINOSListenerRemoteCallbackList
                                                    .getBroadcastItem(i);
                                            if (needReport(b, l)) {
                                                l.onMonitorStateChanged(net, sta, b);
                                            }
                                        }
                                        break;
                                    case FastSwitchInfo.ACTION_REPORT_MS:
                                        logi(pfx + ", MonitorSensitivity:" + sen);
                                        for (int i = 0; i < n; i++) {
                                            INOSListener l = sINOSListenerRemoteCallbackList
                                                    .getBroadcastItem(i);
                                            if (needReport(b, l)) {
                                                l.onMonitorSensitivityChanged(net, sen, b);
                                            }
                                        }
                                        break;
                                    case FastSwitchInfo.ACTION_REPORT_LLQ:
                                        logi(pfx + ", LocalLinkQuality:" + llq);
                                        for (int i = 0; i < n; i++) {
                                            INOSListener l = sINOSListenerRemoteCallbackList
                                                    .getBroadcastItem(i);
                                            if (needReport(b, l)) {
                                                l.onMonitorLocalLinkQualityAlarm(net, llq, b);
                                            }
                                        }
                                        break;
                                    case FastSwitchInfo.ACTION_REPORT_FLQ:
                                        logi(pfx + ", FullLinkQuality:" + flq);
                                        for (int i = 0; i < n; i++) {
                                            INOSListener l = sINOSListenerRemoteCallbackList
                                                    .getBroadcastItem(i);
                                            if (needReport(b, l)) {
                                                l.onMonitorFullLinkQualityAlarm(net, flq, b);
                                            }
                                        }
                                        break;
                                    case FastSwitchInfo.ACTION_REPORT_SS:
                                        logi(pfx + ", SignalStrength:" + ss);
                                        for (int i = 0; i < n; i++) {
                                            INOSListener l = sINOSListenerRemoteCallbackList
                                                    .getBroadcastItem(i);
                                            if (needReport(b, l)) {
                                                l.onMonitorSignalStrengthAlarm(net, ss, b);
                                            }
                                        }
                                        break;
                                    case FastSwitchInfo.ACTION_REPORT_GEN:
                                        logi(pfx + ", General report:"
                                                + b.getString(FastSwitchInfo.BK_PARM_ACT_EXT));
                                        for (int i = 0; i < n; i++) {
                                            INOSListener l = sINOSListenerRemoteCallbackList
                                                    .getBroadcastItem(i);
                                            String r = (b.containsKey(FastSwitchInfo.BK_ERR_REASON)
                                                    && b.getString(FastSwitchInfo.BK_ERR_REASON)
                                                    != null) ?
                                                    b.getString(FastSwitchInfo.BK_ERR_REASON)
                                                    : "non-get";
                                            if (needReport(b, l)) {
                                                if (r.equals(FastSwitchInfo.BV_PARM_QRY)
                                                        || r.equals(FastSwitchInfo.BV_PARM_SVR)) {
                                                    NPMFastSwitch.getInstance(mContext)
                                                            .notifyGeneralReport(info);
                                                } else {
                                                    l.onCfgParmChanged(net, b);
                                                }
                                            }
                                        }
                                        break;
                                    default:
                                        loge("[FastSwitch]: onFastSwitchInfoNotify, unsupported "
                                                + "action:" + act);
                                        break;
                                    }
                                } catch (RemoteException e) {
                                    loge("onFastSwitchInfoNotify fail: " + e);
                                } catch (Exception e) {
                                    // Avoid hacker using this vulnerability for attacks.
                                    // The vulnerability is that client makes RuntimeException to
                                    // make beginBroadcast exception to block callback to other
                                    // APKs.
                                    loge("onFastSwitchInfoNotify fail, Exception: " + e);
                                }
                                sINOSListenerRemoteCallbackList.finishBroadcast();
                            }
                        }
                    };
                    logi("[FastSwitch]: getIFastSwitchListener() successfully!");
                }
            }
        }
        return sIFastSwitchListener;
    }

    /**
     * Register IFastSwitchListener.
     * sIFastSwitchListener can be only register once for all INOSListener.
     */
    private void registerIFastSwitchListener() {
        if (sIFastSwitchListener == null) {
            getIFastSwitchListener();
        }

        synchronized (sFastSwitchListenerLock) {
            if (sIFastSwitchListener != null && !sIsIFastSwitchListenerRegistered) {
                addFastSwitchListener(sIFastSwitchListener);
                sIsIFastSwitchListenerRegistered = true;
            } else {
                loge("[FastSwitch]: registerIFastSwitchListener, sIsIFastSwitchListenerRegistered:"
                        + sIsIFastSwitchListenerRegistered);
            }
        }
    }

    /**
     * Unregister IFastSwitchListener.
     * This function process INetworkPolicyService disappear case.
     */
    private void unregisterIFastSwitchListener() {
        synchronized (sFastSwitchListenerLock) {
            if (sIFastSwitchListener != null) {
                logd("[FastSwitch]: unregisterIFastSwitchListener: " + sIFastSwitchListener);
                sIFastSwitchListener = null;
                sIsIFastSwitchListenerRegistered = false;
            } else {
                loge("[FastSwitch]: unregisterIFastSwitchListener, sIFastSwitchListener is null");
            }
        }
    }

    /**
     * Calculate platform capability. the platform capability is the minimum one between NPM & NOS.
     *
     * @param c NOS service capability.
     * @return platform supported capability.
     */
    private String calculatePlatformCapability(String c) {
        if (TextUtils.isEmpty(c)) {
            loge("[FastSwitch]: calculatePlatformCapability c is null");
            return null;
        } else {
            try {
                // max capability from NOS server.
                String max = c.split(",")[0];
                // min capability from NOS server.
                String min = (c.split(",").length > 1) ? c.split(",")[1] : "";
                String cap = (Float.valueOf(max) > Float.valueOf(sCurNPMcap)) ? sCurNPMcap : max;
                if (!TextUtils.isEmpty(min)) {
                    if (Float.valueOf(cap) >= Float.valueOf(min)) {
                        cap += "," + min;
                    } else {
                        // NPM version < NPS/NOS min version, so cannot work, so return null.
                        cap = null;
                        loge("[FastSwitch]: calculatePlatformCapability max:" + max + ", min:"
                                + min + ", sCurNPMcap:" + sCurNPMcap);
                    }
                }
                return cap;
            } catch (NumberFormatException e) {
                loge("[FastSwitch]: calculatePlatformCapability e:" + e);
                return null;
            } catch (NullPointerException e) {
                loge("[FastSwitch]: calculatePlatformCapability e:" + e);
                return null;
            }
        }
    }

    /**
     * Get NOS service maximum capability.
     * It is the minimum capability among NPM, NPS, NNPS and NOS.
     * The capability of platform NPM capability should always keep consistent with NOS.
     *
     * @param b bundle object. for extension, input null now please.
     * @return platform capability. Format sample "1.2", get float by Float.valueOf().
     *         Why return a string? This is for extension later.
     *         The future format will be like "7.8,5.2", 7.8 is max platform capability and
     *         5.2 will be min platform capability because platform cannot have unrestricted
     *         backward compatibility for ever.
     *         NOS service capability format is:
     *           4th byte       3rd byte       2nd byte       1st byte
     *               5              2              7              8
     */
    public String getNOSServerCapability(Bundle b) {
        // NOS Server capability (version) is not related with network type
        FastSwitchInfo info = new FastSwitchInfo(FastSwitchInfo.NETWORK_TYPE_UNKNOWN,
                FastSwitchInfo.ACTION_REQ_GET_NSC, b);
        int capability;

        if (versionSupported(JXNPS_FAST_SWITCH) <= 0) {
            loge("[FastSwitch]: getNOSServerCapability fail, " + JXNPS_FAST_SWITCH
                + " is not supported");
            return null;
        }

        capability = configFastSwitchInfoWithIntResult(info);
        if (capability <= 0) {
            // get wrong capability, so we think not support directly
            loge("[FastSwitch]: getNOSServerCapability fail");
            return null;
        } else {
            String cap = String.valueOf((capability & 0xff00) >> 8) + "."
                    + String.valueOf(capability & 0xff);
            if ((capability & 0x7fff0000) > 0) {
                cap += "," + String.valueOf((capability & 0x7f000000) >> 24) + "."
                        + String.valueOf(capability & 0xff0000 >> 16);
            }
            logi("[FastSwitch]: getNOSServerCapability, ret = " + capability + ", cap = " + cap);
            cap = calculatePlatformCapability(cap);
            return cap;
        }
    }

    /**
     * Get the capability on which the current client APK is working.
     *
     * @return the capability, maybe null but this is not expected.
     */
    private String getCurrentUserCapability() {
        synchronized (sINOSListenerLock) {
            return sPidVersions.get(Process.myPid());
        }
    }

    /**
     * Get the interface that the current client APK is monitoring.
     *
     * @return the interface name, maybe null but this is not expected.
     */
    private String getCurrentUserMonitoredIfName() {
        synchronized (sINOSListenerLock) {
            return sPidIfNames.get(Process.myPid());
        }
    }

    /**
     * Add INOSListener to listener list to monitor notification.
     * Export to SDK.
     * Also strongly suggest to use addINOSListener and removeINOSListener for platform developer.
     *
     * NOTE:
     *    Throw RemoteException for the functions because they are binder call.
     *
     * Usage(SDK):
     *     INOSListener.Stub listener = new INOSListener.Stub() {
     *         @Override
     *         public void onMonitorStateChanged(int networkType, int state, Bundle b) {
     *             loge("[FastSwitch]: onMonitorStateChanged, state:" + state);
     *         }
     *         @Override
     *         public void onMonitorSensitivityChanged(int networkType, int sensitivity,
     *                 Bundle b) {
     *             loge("[FastSwitch]: onMonitorSensitivityChanged, sensitivity:" + sensitivity);
     *         }
     *         @Override
     *         public void onMonitorLocalLinkQualityAlarm(int networkType, int level, Bundle b) {
     *             loge("[FastSwitch]: onMonitorLocalLinkQualityAlarm, level:" + level);
     *         }
     *         @Override
     *         public void onMonitorFullLinkQualityAlarm(int networkType, int level, Bundle b) {
     *             loge("[FastSwitch]: onMonitorFullLinkQualityAlarm, level:" + level);
     *         }
     *         // SDK has no this function
     *         @Override
     *         public void onMonitorSignalStrengthAlarm(int networkType, int level, Bundle b) {
     *             loge("[FastSwitch]: onMonitorSignalStrengthAlarm, level:" + level);
     *         }
     *         @Override
     *         public void onLinstenerStateChanged(boolean active, Bundle b) {
     *             loge("[FastSwitch]: onLinstenerStateChanged, active:" + active);
     *         }
     *         @Override
     *         public void onServiceDie() {
     *             loge("[FastSwitch]: onServiceDie");
     *         }
     *     };
     *
     *     NetworkPolicyManager.getDefaultInstance().addINOSListener(listener, capability, b);
     *
     * @param listener to listen INOS related information.
     * @param capability can be understood as on which version the current APK wants to work.
     * @param b bundle object. for extension, input null now please.
     */
    public void addINOSListener(INOSListener listener, String capability, Bundle b) {
        // Try to get service to avoid having died
        INetworkPolicyService service = getINetworkPolicyService();
        if (service != null) {
            synchronized (sINOSListenerLock) {
                if (listener != null) {
                    if (sINOSListenerHashSet.contains(listener)) {
                        loge("[FastSwitch]: addINOSListener, " + listener + " is duplicated");
                        try {
                            listener.onLinstenerStateChanged(true, null);
                        } catch (RemoteException e) {
                            loge("[FastSwitch]: addINOSListener onLinstenerStateChanged: " + e);
                        }
                        return;
                    }

                    if (versionSupported(JXNPS_FAST_SWITCH) <= 0) {
                        loge("[FastSwitch]: addINOSListener fail, " + JXNPS_FAST_SWITCH
                                + " is not supported");
                        return;
                    }
                    // Jibin, it is best to add elements limitation check.
                    if (sINOSListenerHashSet.size() >= INOS_LISTENER_MAX) {
                        loge("[FastSwitch]: addINOSListener, >= 100, remove all old elements: "
                                + sINOSListenerRemoteCallbackList.getRegisteredCallbackCount());
                        removeAllINOSListenerFromRemoteCallbackList();
                    }

                    if (TextUtils.isEmpty(capability)) {
                        loge("[FastSwitch]: addINOSListener, " + listener + " works on null cap");
                        return;
                    } else {
                        try {
                            // check if it is a float version
                            float v = Float.valueOf(capability);
                        } catch (NumberFormatException e) {
                             loge("[FastSwitch]: addINOSListener fail capability:" + "capability");
                             return;
                        }
                    }
                    sPidVersions.put(Process.myPid(), capability);
                    sPidOperFlag.put(Process.myPid(), FLAG_INIT_MONITOR);
                    sListenerVersions.put(listener, capability);
                    sListenerPids.put(listener, Process.myPid());
                    sINOSListenerRemoteCallbackList.register(listener, Process.myPid());
                    sINOSListenerHashSet.add(listener);
                    logi("[FastSwitch]: addINOSListener listener: " + listener + ", capability: "
                            + capability + ", pid: " + Process.myPid());
                    try {
                        listener.onLinstenerStateChanged(true, null);
                    } catch (RemoteException e) {
                        loge("[FastSwitch]: addINOSListener onLinstenerStateChanged: " + e);
                    }
                } else {
                    loge("[FastSwitch]: addINOSListener, listener is null");
                    return;
                }
            }
            if (!sINOSListenerHashSet.isEmpty()) {
                registerIFastSwitchListener();
            }
        } else {
            synchronized (sINOSListenerLock) {
                // clear all client listeners and unregister sIFastSwitchListener
                removeAllINOSListenerFromRemoteCallbackList();
            }
            unregisterIFastSwitchListener();
            loge("[FastSwitch]: addINOSListener, INetworkPolicyService is not existed.");
        }
    }

    /**
     * Remove INOSListener from listener list
     * Export to SDK.
     * Also strongly suggest to use addINOSListener and removeINOSListener for platform developer.
     *
     * Usage(SDK and Platform):
     *    NetworkPolicyManager.getDefaultInstance().removeINOSListener(listener);
     *
     * @param listener to be removed listener of INOS.
     */
    public void removeINOSListener(INOSListener listener) {
        // Try to get service to avoid having died
        INetworkPolicyService service = getINetworkPolicyService();
        if (service != null) {
            synchronized (sINOSListenerLock) {
                if (listener != null) {
                    if (!sINOSListenerHashSet.contains(listener)) {
                        loge("[FastSwitch]: removeINOSListener, " + listener + " is not existed");
                        return;
                    }
                    sINOSListenerRemoteCallbackList.unregister(listener);
                    sINOSListenerHashSet.remove(listener);
                    sPidUids.remove(sListenerPids.get(listener));
                    sPidNetTypes.remove(sListenerPids.get(listener));
                    sPidVersions.remove(sListenerPids.get(listener));
                    sPidIfNames.remove(sListenerPids.get(listener));
                    sListenerVersions.remove(listener);
                    sListenerPids.remove(listener);
                    try {
                        listener.onLinstenerStateChanged(false, null);
                    } catch (RemoteException e) {
                        loge("[FastSwitch]: removeINOSListener onLinstenerStateChanged: " + e);
                    }
                } else {
                    loge("[FastSwitch]: removeINOSListener, listener is null");
                    return;
                }
            }

        } else {
            synchronized (sINOSListenerLock) {
                // clear all client listeners and unregister sIFastSwitchListener
                removeAllINOSListenerFromRemoteCallbackList();
            }
            unregisterIFastSwitchListener();
            loge("[FastSwitch]: removeINOSListener, INetworkPolicyService is not existed.");
        }
    }

    /**
     * Add IFastSwitchListener to monitor FastSwitchInfo notification.
     *
     * Usage(Not suggest, so set private level to avoid misuse):
     *    IFastSwitchListener.Stub listener = new IFastSwitchListener.Stub() {
     *        // "throws RemoteException" can be removed. It is better to add it.
     *        @Override
     *        public void onFastSwitchInfoNotify(FastSwitchInfo info) {
     *            int action = info.getAction();
     *            int network = info.getNetworkType();
     *            switch (action) {
     *            case FastSwitchInfo.ACTION_REPORT_STA:
     *                loge("[FastSwitch]: onFastSwitchInfoNotify, act:" + action);
     *                loge("[FastSwitch]: onFastSwitchInfoNotify, state:" + info.getState());
     *                break;
     *            default:
     *                loge("[FastSwitch]: onFastSwitchInfoNotify, unsupported act:" + action);
     *                break;
     *            }
     *        }
     *    };
     *
     *    NetworkPolicyManager.getDefaultInstance().addFastSwitchListener(listener);
     *
     * @param listener to listen FastSwitch related information.
     */
    private void addFastSwitchListener(IFastSwitchListener listener) {
        // Try to get service to avoid having died
        INetworkPolicyService service = getINetworkPolicyService();
        if (service != null) {
            Bundle b = new Bundle();
            b.putString(FastSwitchInfo.BK_APK_PID, String.valueOf(Process.myPid()));
            b.putString(FastSwitchInfo.BK_REQ_SOURCE, FastSwitchInfo.BV_REQ_SOURCE_P);
            NPMFastSwitch.getInstance(mContext).addFastSwitchListener(listener, service, b);
        } else {
            loge("[FastSwitch]: addFastSwitchListener, INetworkPolicyService is not existed.");
        }
    }

    /**
     * Configure fast switch for the specified network without return valaue.
     * All functions with void return type can be implemented by info.mAction.
     *
     * @param info networkType and action in info are necessory.
     */
    private void configFastSwitchInfo(FastSwitchInfo info) {
        // Try to get service to avoid having died
        INetworkPolicyService service = getINetworkPolicyService();
        if (service != null) {
            String capability = getCurrentUserCapability();
            String ifname = getCurrentUserMonitoredIfName();
            Bundle b = (info.getBundle() != null) ? info.getBundle() : new Bundle();
            String source = b.getString(FastSwitchInfo.BK_REQ_SOURCE);

            if (source == null || !source.equals(FastSwitchInfo.BV_REQ_SOURCE_PP)) {
                b.putString(FastSwitchInfo.BK_APK_CAP, capability);
                b.putString(FastSwitchInfo.BK_APK_PID, String.valueOf(Process.myPid()));
                b.putString(FastSwitchInfo.BK_APK_UID, String.valueOf(Process.myUid()));
                b.putString(FastSwitchInfo.BK_REQ_SOURCE, FastSwitchInfo.BV_REQ_SOURCE_P);
                b.putString(FastSwitchInfo.BK_IF_NAME,
                        (sDefaultIfName.equals(ifname) ? null : ifname));
                info.resetBundle(b);
            }
            synchronized (sINOSListenerLock) {
                resetOperFlag(info);
            }
            NPMFastSwitch.getInstance(mContext).configFastSwitchInfo(info, service);
        } else {
            loge("[FastSwitch]: configFastSwitchInfo, INetworkPolicyService is not existed.");
        }
    }

    /**
     * Configure fast switch for the specified network with return int value.
     * For boolean value returned, use 0 indicateds true, != 0 indicates false.
     * All functions with int and boolean return type can be implemented by info.mAction.
     *
     * @param info networkType in info is necessory.
     * @return result of info.mAction.
     */
    private int configFastSwitchInfoWithIntResult(FastSwitchInfo info) {
        // int ret = 0; // 0 is not used for all variables in info, that is to say 0 indicates fail.
        /* change -1 as default fail number because more and more workaround usage appears.
         * Such as getCfgParm that takes 0 as success with the real result returned in bundle.
         * Such as isFeatureEnabled that takes 0 as success and others is for fail.
         */
        int ret = -1;
        // Try to get service to avoid having died
        INetworkPolicyService service = getINetworkPolicyService();
        if (service != null) {
            String capability = getCurrentUserCapability();
            String ifname = getCurrentUserMonitoredIfName();
            Bundle b = (info.getBundle() != null) ? info.getBundle() : new Bundle();
            b.putString(FastSwitchInfo.BK_APK_CAP, capability);
            b.putString(FastSwitchInfo.BK_APK_PID, String.valueOf(Process.myPid()));
            b.putString(FastSwitchInfo.BK_APK_UID, String.valueOf(Process.myUid()));
            b.putString(FastSwitchInfo.BK_REQ_SOURCE, FastSwitchInfo.BV_REQ_SOURCE_P);
            b.putString(FastSwitchInfo.BK_IF_NAME,
                    (sDefaultIfName.equals(ifname) ? null : ifname));
            info.resetBundle(b);
            ret = NPMFastSwitch.getInstance(mContext).configFastSwitchInfoWithIntResult(info,
                    service);
        } else {
            loge("[FastSwitch]: configFastSwitchInfoWithIntResult, INetworkPolicyService "
                    + "is not existed.");
        }
        return ret;
    }

    /**
     * Start monitor for the specified network.
     *
     * Monitor related repeated operations should be covered by native single service - NOS.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     *          Such as networkType NETWORK_TYPE_WIFI, b.putString("interfacename", "wlan1");
     *          Default is determined by NOS server if "interfacename" is not set.
     */
    public void startMonitor(int networkType, Bundle b) {
        synchronized (sINOSListenerLock) {
            sPidUids.put(Process.myPid(), Process.myUid());
            sPidNetTypes.put(Process.myPid(), networkType);
            sPidIfNames.put(Process.myPid(),
                    ((b == null)
                    ? sDefaultIfName
                    : b.getString(FastSwitchInfo.BK_IF_NAME, sDefaultIfName)));
        }
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_STR_MNR,
                b);
        configFastSwitchInfo(info);
    }

    /**
     * Pause monitor for the specified network.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void pauseMonitor(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_PAU_MNR,
                b);
        configFastSwitchInfo(info);
    }

    /**
     * Resume monitor for the specified network.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void resumeMonitor(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_RES_MNR,
                b);
        configFastSwitchInfo(info);
    }

    /**
     * Stop monitor for the specified network.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void stopMonitor(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_STP_MNR,
                b);
        configFastSwitchInfo(info);
    }

    /**
     * Get monitor state of the specified network type.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     *          Such as networkType NETWORK_TYPE_WIFI, b.putString("interfacename", "wlan1");
     * @return true for monitoring, false for stoped.
     */
    public int getMonitorState(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_GET_STA,
                b);
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Set monitor sensitivity for the specified network, update it if it has been set.
     *
     * @param networkType to be monitored.
     * @param sensitivity Monitor sensitivity.
     * @param b bundle object. for extension, input null now please.
     */
    public void setMonitorSensitivity(int networkType, int sensitivity, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_SET_MS,
                sensitivity, b);
        configFastSwitchInfo(info);
    }

    /**
     * Get current monitor sensitivity for the specified network.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     *          Such as networkType NETWORK_TYPE_WIFI, b.putString("interfacename", "wlan1");
     * @return monitor sensitivity for the specified network type.
     */
    public int getMonitorSensitivity(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_GET_MS, b);
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
    /**
     * Set uid for the specified network type monitor to monitor.
     * It maybe update operation for same networkType because NOS service only support three items.
     *
     * @param networkType to be monitored.
     * @param uid to be monitored. default will monitor all packets.
     * @param b bundle object. for extension, input null now please.
     */
    public void setUidForMonitor(int networkType, int uid, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_SET_UID,
                uid, b);
        configFastSwitchInfo(info);
    }

    /**
     * Remove uid from the specified network type monitor.
     *
     * @param networkType to be monitored.
     * @param uid to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void removeUidFromMonitor(int networkType, int uid, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_REM_UID,
                uid, b);
        configFastSwitchInfo(info);
    }

    /**
     * Remove uid from the specified network type monitor.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void removeAllUidsFromMonitor(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType,
                FastSwitchInfo.ACTION_REQ_REM_ALL_UID, b);
        configFastSwitchInfo(info);
    }

    /**
     * Set link info for the specified network type monitor to monitor.
     * It maybe update operation for same networkType because NOS service only support three items.
     *
     * @param networkType to be monitored.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @param b bundle object. for extension, input null now please.
     */
    public void setLinkInfoForMonitor(int networkType, String srcIp, String dstIp, int srcPort,
            int dstPort, int proto, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType,
                FastSwitchInfo.ACTION_REQ_SET_LIN, srcIp, dstIp, srcPort, dstPort, proto, b);
        configFastSwitchInfo(info);
    }

    /**
     * Remove link info from the specified network type monitor.
     *
     * @param networkType to be monitored.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @param b bundle object. for extension, input null now please.
     */
    public void removeLinkInfoFromMonitor(int networkType, String srcIp, String dstIp, int srcPort,
            int dstPort, int proto, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType,
                FastSwitchInfo.ACTION_REQ_REM_LIN, srcIp, dstIp, srcPort, dstPort, proto, b);
        configFastSwitchInfo(info);
    }

    /**
     * Remove all link info from the specified network type monitor.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void removeAllLinkInfoFromMonitor(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType,
                FastSwitchInfo.ACTION_REQ_REM_ALL_LIN, b);
        configFastSwitchInfo(info);
    }

    /**
     * Get local link quality of the specified network type.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     *          Such as networkType NETWORK_TYPE_WIFI, b.putString("interfacename", "wlan1");
     * @return local link quality for the specified network type.
     */
    public int getLocalLinkQuality(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_GET_LLQ,
                b);
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Get full link quality of the specified network type.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     *          Such as networkType NETWORK_TYPE_WIFI, b.putString("interfacename", "wlan1");
     * @return full link quality for the specified network type.
     */
    public int getFullLinkQuality(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_GET_FLQ,
                b);
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Get signal strength level of the specified network type.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     *          Such as networkType NETWORK_TYPE_WIFI, b.putString("interfacename", "wlan1");
     * @return signal strength for the specified network type.
     */
    public int getSignalStrength(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_GET_SS, b);
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Set specified parameters in b into nos service. No callback for "set" in INOSListener.
     * networkType is necessary
     *
     * @param networkType to be configured.
     * @param b bundle object with configure parameter key and its values. key & value are string.
     */
    public void setCfgParm(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_GEN_ACT,
                b);
        configFastSwitchInfo(info);
    }

    /**
     * Get specified parameters in b from nos service. BV_PARM_DEF or specified item name should
     * be into bundle as value. networkType is necessary
     *
     * @param networkType to be queried.
     * @param b bundle object with queried parameter key & value(detail key). key&value are string
     * @return 0 success and !0 fail.
     */
    public int getCfgParm(int networkType, Bundle b) {
        FastSwitchInfo info = new FastSwitchInfo(networkType, FastSwitchInfo.ACTION_REQ_GEN_ACT,
                b);
        return configFastSwitchInfoWithIntResult(info);
    }

    // Fast Swtich end @}

    /**
     * Get current function name, like C++ __FUNC__.
     * NOTE: StackTraceElement[0] is getStackTrace
     *       StackTraceElement[1] is getMethodName
     *       StackTraceElement[2] is logx
     *       StackTraceElement[3] is function name
     *       StackTraceElement[4] is function name with encapsulated layer
     * @return caller function name.
     */
    private static String getMethodName(boolean moreDepth) {
        int layer = 3;
        if (NetworkPolicyManager.NAME_LOG > 0) {
            if (moreDepth) {
                layer = 4;
            }
            return Thread.currentThread().getStackTrace()[layer].getMethodName();
        }
        return null;
    }

    /**
     * Encapsulated d levevl log function.
     */
    private static void logd(String message) {
        Log.d(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated i levevl log function.
     */
    private static void logi(String message) {
        Log.i(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated e levevl log function.
     */
    private static void loge(String message) {
        Log.e(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated e levevl log function.
     * NOTE: Only used in encapsulation check function
     */
    private static void loge(boolean moreDepth, String message) {
        Log.e(TAG, "JXNPS: " + getMethodName(moreDepth) + ":" + message);
    }
}
