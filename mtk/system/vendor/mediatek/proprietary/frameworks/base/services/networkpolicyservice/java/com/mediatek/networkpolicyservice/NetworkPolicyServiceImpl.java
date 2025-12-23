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

package com.mediatek.networkpolicyservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import com.mediatek.networkpolicymanager.INetworkPolicyService;
import com.mediatek.networkpolicymanager.NetworkPolicyManager;
import com.mediatek.networkpolicymanager.booster.BoosterInfo;
import com.mediatek.networkpolicymanager.fastswitch.FastSwitchInfo;
import com.mediatek.networkpolicymanager.fastswitch.IFastSwitchListener;
import com.mediatek.networkpolicyservice.booster.NPSBooster;
import com.mediatek.networkpolicyservice.fastswitch.NPSFastSwitch;

/** INetworkPolicyService.aidl implements. Name: JXNetworkPolicyService. */
public class NetworkPolicyServiceImpl extends INetworkPolicyService.Stub {
    private static final String TAG = "NetworkPolicyServiceImpl";

    private Context mContext = null;

    /**
     * NetworkPolicyServiceImpl constructor.
     * @param context a context.
     */
    public NetworkPolicyServiceImpl(Context context) {
        mContext = context;
        NPSFastSwitch.init();
        // onScreenStateChanged();  // enable it if need later.
    }

    public static final String JXNPS_APK_BOOSTER = "JXNPS_APK_BOOSTER";
    /**
     * Fast Switch feature name, used to check whether Fast Switch service is supported or not.
     * @hide
     */
    public static final String JXNPS_FAST_SWITCH = "JXNPS_FAST_SWITCH";
    // New feature name added here please


    /**
     * Query whether the specified feature supported and return supported version.
     *
     * Every feature service will be optimized and upgrade in future. This function will return
     * which version supported for specified feature. Client can do function control.
     *
     * @param feature specified feature to be queried.
     * @return 0 if not support, a number greater than 0 indicating the feature version.
     */
    public int versionSupported(String feature) {
        int ver = 0;
        if (feature != null) {
            switch (feature) {
                case NetworkPolicyManager.JXNPS_APK_BOOSTER:
                    ver = NPSBooster.getInstance(mContext).getInterfaceVersion();
                    break;
                case NetworkPolicyManager.JXNPS_FAST_SWITCH:
                    ver = NPSFastSwitch.getInstance(mContext).getInterfaceVersion();
                    break;
                default:
                    loge("versionSupported, not support feature: " + feature);
            }
        }
        return ver;
    }

    /**
     * Monitor Screen ON/OFF state for lower notification.
     *
     */
    private void onScreenStateChanged() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (mContext == null) {
            logd("onScreenStateChanged register fail, context is null");
            return;
        }
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (action.equals(Intent.ACTION_SCREEN_ON)) {
                            logd("onScreenStateChanged screen on");
                            NPSFastSwitch.getInstance(mContext).notifyScreenState(true);
                        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                            logd("onScreenStateChanged screen off");
                            NPSFastSwitch.getInstance(mContext).notifyScreenState(false);
                        }
                    }
                }, filter, null, null);
    }

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
        // FastSwitch needs foolproof mechanism by calling pauseMonitor to avoid power costing
        // when caller goes to background and doesn't call pauseMonitor and stopMonitor.
        NPSFastSwitch.getInstance(mContext).onActivityStateChanged(isTop, pid, uid, actName,
            pkgName);
        // APKBooster needn't foolproof mechanism because caller has background acceleration mode.
        // So, NPS cannot make decisions for callers.
    }

    /**
     * Callback by AmsExtImpl.java when APK crash.
     * Normal onDestroy also can come here.
     *
     * @param pid APK pid.
     * @param uid APK uid.
     * @param pkgName APK package name.
     * @hide
     */
    public void onAppProcessCrash(int pid, int uid, String pkgName) {
        /* FastSwitch needn't any processing here.
           carsh APK from "platform" is protected by NPM, see onCallbackDied in
           NetworkPolicyManager.java.
           carsh APK from "sdk" is protected by NPS, see onCallbackDied in NPSFastSwitch.java. */
        /* APKBooster needs delete acceleration rule here.
           All carsh APK can be processd by caching info in add action.
           But now, 1-no caching info in NPSBooster.java and 2-Linkinfo rules are needed
           bringing uid/pid to here by modify NPM. */
        // NPSBooster.getInstance(mContext).onAppProcessCrash(pid, uid, pkgName);
        // logd("onAppProcessCrash: pid: " + pid + ", uid: " + uid + ", pkgName: " + pkgName);
    }

    // APK Booster @{
    /**
     * Configure BoosterInfo to enable or disable booster for specified application based on
     * specified info.
     * @param info to be config.
     * @return true for success, false for fail.
     */
    public boolean configBoosterInfo(BoosterInfo info) {
        int callingUid = Binder.getCallingUid();
        String packageName = mContext.getPackageManager().getNameForUid(callingUid);
        int group;
        int action;
        boolean ret = false;

        logd("[Booster]: callingUid: " + callingUid + ", callingPktName: " + packageName);

        if (info == null) {
            loge("[Booster]: info: null");
            return ret;
        }

        group = info.getGroup();
        action = info.getAction();

        if (group <= BoosterInfo.BOOSTER_GROUP_BASE || action <= BoosterInfo.BOOSTER_ACTION_BASE) {
            loge("[Booster]: group: " + group + ", action: " + action);
            return ret;
        }

        switch (action) {
            case BoosterInfo.BOOSTER_ACTION_ADD_BY_UID:
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_UID:
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_UID_ALL:
                ret = NPSBooster.getInstance(mContext).configBoosterByUid(group, action,
                        info.getUid());
                break;
            case BoosterInfo.BOOSTER_ACTION_ADD_BY_LINKINFO:
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO:
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO_ALL:
                ret = NPSBooster.getInstance(mContext).configBoosterByFiveTuple(info);
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_ALL:
                ret = NPSBooster.getInstance(mContext).configBoosterRemoveAll(group, action);
                break;
            case BoosterInfo.BOOSTER_ACTION_DBG_EXT:
                ret = NPSBooster.getInstance(mContext).configBoosterDoExtOperation(info);
                break;
            case BoosterInfo.BOOSTER_ACTION_FEA_OPS:
                if (info.getMoreValue() == null) {
                    ret = NPSBooster.getInstance(mContext).isFeatureEnabled(info);
                } else {
                    ret = NPSBooster.getInstance(mContext).setFeatureEnabled(info);
                }
                break;
            default:
                loge("[Booster]: not support action: " + action);
                ret = false;
        }
        return ret;
    }
    // @}

    // Fast Swtich start @{ // Fast switch between WiFi and Cellular
    /**
     * Add IFastSwitchListener to monitor FastSwitchInfo notification.
     *
     * Why add Listener without network type parameter? Because it report for all network type.
     * Clients will receive all network type callback and it is ok that interersted clients process
     * those who care about.
     *
     * @param listener to listen FastSwitch related information.
     * @param b for more info.
     */
    public void addFastSwitchListener(IFastSwitchListener listener, Bundle b) {
        NPSFastSwitch.getInstance(mContext).addFastSwitchListener(listener, b);
    }

    /**
     * Configure fast switch for the specified network without return valaue.
     * All functions with void return type can be implemented by info.mAction.
     *
     * @param info networkType and action in info are necessory.
     */
    public void configFastSwitchInfo(FastSwitchInfo info) {
        int act = info.getAction();
        int networkType = info.getNetworkType();

        if (!isValid(info)) {
            return;
        }

        switch (act) {
        case FastSwitchInfo.ACTION_REQ_STR_MNR:
            NPSFastSwitch.getInstance(mContext).startMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_PAU_MNR:
            NPSFastSwitch.getInstance(mContext).pauseMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_RES_MNR:
            NPSFastSwitch.getInstance(mContext).resumeMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_STP_MNR:
            NPSFastSwitch.getInstance(mContext).stopMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_SET_MS:
            NPSFastSwitch.getInstance(mContext).setMonitorSensitivity(info);
            break;
        case FastSwitchInfo.ACTION_REQ_SET_UID:
            NPSFastSwitch.getInstance(mContext).setUidForMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_REM_UID:
            NPSFastSwitch.getInstance(mContext).removeUidFromMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_REM_ALL_UID:
            NPSFastSwitch.getInstance(mContext).removeAllUidsFromMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_SET_LIN:
            NPSFastSwitch.getInstance(mContext).setLinkInfoForMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_REM_LIN:
            NPSFastSwitch.getInstance(mContext).removeLinkInfoFromMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_REM_ALL_LIN:
            NPSFastSwitch.getInstance(mContext).removeAllLinkInfoFromMonitor(info);
            break;
        case FastSwitchInfo.ACTION_REQ_GEN_ACT:
            // This case includes get non-int info actions to implement get functions because
            // original design doesn't cover return non-int case. non-int get functions are only
            // to trigger report (Don't update aidl due to special reasons this time)
            // Trigger report + callback together to implement get non-int function.
            NPSFastSwitch.getInstance(mContext).setCfgParm(info);
            break;
        default:
            loge("[FastSwitch]: configFastSwitchInfo, unsupported act:" + act);
            break;
        }
    }

    /**
     * Configure fast switch for the specified network with return int value.
     * For boolean value returned, use 0 indicateds false, != 0 indicates true.
     * All functions with int and boolean return type can be implemented by info.mAction.
     *
     * @param info networkType in info is necessory.
     * @return result of info.mAction.
     */
    public int configFastSwitchInfoWithIntResult(FastSwitchInfo info) {
        int act = info.getAction();
        int ret = 0;

        if (!isValid(info)) {
            return ret;
        }

        switch (act) {
        case FastSwitchInfo.ACTION_REQ_GET_STA:
            ret = NPSFastSwitch.getInstance(mContext).getMonitorState(info);
            break;
        case FastSwitchInfo.ACTION_REQ_GET_MS:
            ret = NPSFastSwitch.getInstance(mContext).getMonitorSensitivity(info);
            break;
        case FastSwitchInfo.ACTION_REQ_GET_LLQ:
            ret = NPSFastSwitch.getInstance(mContext).getLocalLinkQuality(info);
            break;
        case FastSwitchInfo.ACTION_REQ_GET_FLQ:
            ret = NPSFastSwitch.getInstance(mContext).getFullLinkQuality(info);
            break;
        case FastSwitchInfo.ACTION_REQ_GET_SS:
            ret = NPSFastSwitch.getInstance(mContext).getSignalStrength(info);
            break;
        case FastSwitchInfo.ACTION_REQ_GET_NSC:
            ret = NPSFastSwitch.getInstance(mContext).getNOSServerCapability(info);
            break;
        case FastSwitchInfo.ACTION_REQ_GEN_ACT:
            ret = NPSFastSwitch.getInstance(mContext).getCfgParm(info);
            break;
        case FastSwitchInfo.ACTION_REQ_FEA_OPS:
            Bundle b = info.getBundle();
            if (b != null && b.containsKey(FastSwitchInfo.BK_FEATURE_CTRL)) {
                ret = NPSFastSwitch.getInstance(mContext).setFeatureEnabled(info);
            } else {
                ret = NPSFastSwitch.getInstance(mContext).isFeatureEnabled(info);
            }
            break;
        default:
            loge("[FastSwitch]: configFastSwitchInfoWithIntResult, unsupported act:" + act);
            break;
        }
        return ret;
    }

    /**
     * Check whether FastSwitchInfo are not null.
     *
     * @param info to be checked.
     * @param service to be checked.
     * @return true for valid, false for invalid.
     */
    private boolean isValid(FastSwitchInfo info) {
        if (info == null) {
            loge(true, "[FastSwitch]: info is null: " + (info == null));
            return false;
        }

        if ((info.getNetworkType() >= FastSwitchInfo.NETWORK_TYPE_MAX)
                || (info.getNetworkType() < FastSwitchInfo.NETWORK_TYPE_BASE)) {
            loge(true, "[FastSwitch]: Unsupported network type " + info.getNetworkType());
            return false;
        }
        return true;
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
