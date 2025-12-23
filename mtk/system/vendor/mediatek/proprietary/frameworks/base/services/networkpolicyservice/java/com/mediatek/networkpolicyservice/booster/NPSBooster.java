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

package com.mediatek.networkpolicyservice.booster;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

import com.mediatek.networkpolicymanager.NetworkPolicyManager;
import com.mediatek.networkpolicymanager.booster.BoosterInfo;
import com.mediatek.networkpolicyservice.utils.NPSAddress;
import com.mediatek.networkpolicyservice.utils.NPSNetdagentService;

/**
 * APK Booster service adapter.
 */
public class NPSBooster {
    private static final String TAG = "NPSBooster";

    /* Singleton NPSBooster instace */
    private volatile static NPSBooster sInstance = null;
    private static final Object sNPSBoosterLock = new Object();

    private Context mContext = null;
    // Feature control property, only can be operated by platform.
    private static final String FEATURE_STATE_PROPERTY = "persist.vendor.jxnps.booster.enabled";

    /* NPSBooster constructor */
    private NPSBooster(Context context) {
        mContext = context;
    }

    /**
     * get NPSBooster instance, it is a singleton istance.
     *
     * @param context a Context.
     * @return NPSBooster service.
     * @hide
     */
    public static NPSBooster getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sNPSBoosterLock) {
                if (sInstance == null) {
                    sInstance = new NPSBooster(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * configure accelerator by UID.
     * @param group which groud command will be executed.
     * @param action which action will be executed.
     * @param uid which uid will be set.
     * @return true for success, false for fail.
     * @hide
     */
    public boolean configBoosterByUid(int group, int action, int uid) {
        String packageName = null;
        String cmd = "netdagent firewall ";

        if ((uid < 0) && (action != BoosterInfo.BOOSTER_ACTION_DEL_BY_UID_ALL)) {
            loge("[Booster]: wrong uid: " + uid + ", group: " + group +  ", action: "
                    + action);
            return false;
        }
        packageName = mContext.getPackageManager().getNameForUid(uid);

        logd("[Booster]: uid: " + uid + ", packageName: " + packageName + ", group: "
                + group + ", action: " + action);

        switch (action) {
            case BoosterInfo.BOOSTER_ACTION_ADD_BY_UID:
                if (!isBoosterEnabled()) {
                    loge("[Booster]: Add uid, feature disabled");
                    return false;
                }
                cmd += "priority_set_uid." + group + " " + uid;
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_UID:
                cmd += "priority_clear_uid." + group + " " + uid;
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_UID_ALL:
                cmd += "priority_clear_uid_all." + group;
                break;
            default:
                loge("[Booster]: not support action(" + action + ") for UID");
                return false;
        }

        return NPSNetdagentService.sendNetdagentCommand(cmd);
    }

    /**
     * configure accelerator by Linkinfo.
     * @param info to be config.
     * @return true for success, false for fail.
     * @hide
     */
    public boolean configBoosterByFiveTuple(BoosterInfo info) {
        String cmd = "netdagent firewall ";
        String srcPort = null;
        String dstPort = null;
        int group = info.getGroup();
        int action = info.getAction();
        boolean isClearAll = false;

        if ((info.getSrcIp() == null || info.getDstIp() == null
                || !NPSAddress.isIpPairValid(info.getSrcIp(), info.getDstIp(),
                info.getSrcPort(), info.getDstPort()))
                && (action != BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO_ALL)) {
            loge("[Booster]: invalid parameter, sip/dip/sp/dp");
            return false;
        }

        switch (action) {
            case BoosterInfo.BOOSTER_ACTION_ADD_BY_LINKINFO:
                if (!isBoosterEnabled()) {
                    loge("[Booster]: Add linkinfo, feature disabled");
                    return false;
                }
                cmd += "priority_set_toup." + group + " ";
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO:
                cmd += "priority_clear_toup." + group + " ";
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO_ALL:
                cmd += "priority_clear_toup_all." + group;
                isClearAll = true;
                break;
            default:
                loge("[Booster]: not support action(" + action + ") for linkinfo");
                return false;
        }

        if (!isClearAll) {
            /* -1 => null */
            srcPort = (info.getSrcPort() == -1) ? "none" : Integer.toString(info.getSrcPort());
            dstPort = (info.getDstPort() == -1) ? "none" : Integer.toString(info.getDstPort());
            cmd += info.getSrcIp() + " " + srcPort + " " + info.getDstIp() + " " + dstPort + " ";

            /* format cmd: LINFO SET_G/CLEAR_G  sip sport dip dport proto */
            switch (info.getProto()) {
                case 1:
                    cmd += "TCP";
                    break;
                case 2:
                    cmd += "UDP";
                    break;
                case -1:
                    cmd += "none";
                    break;
                default:
                    loge("[Booster] unknown protocol: " + info.getProto());
                    return false;
            }
        }

        logd("[Booster]: 5 tuple cmd: " + cmd);
        return NPSNetdagentService.sendNetdagentCommand(cmd);
    }

    /**
     * configure accelerator, del all accelerators including UID/LINKINFO.
     * @param group which groud command will be executed.
     * @param action which action will be executed.
     * @return true for success, false for fail.
     * @hide
     */
    public boolean configBoosterRemoveAll(int group, int action) {
        String baseCmd = "netdagent firewall ";
        String cmd = null;
        boolean retUid = false;
        boolean retToup = false;

        logd("[Booster]: remove all, group: " + group + ", action: " + action);

        if (action == BoosterInfo.BOOSTER_ACTION_DEL_ALL) {
            cmd = baseCmd + "priority_clear_uid_all." + group;
            retUid = NPSNetdagentService.sendNetdagentCommand(cmd);
            cmd = baseCmd + "priority_clear_toup_all." + group;
            retToup = NPSNetdagentService.sendNetdagentCommand(cmd);
            logd("[Booster]: remove all retUid: " + retUid + ", retToup: " + retToup);
        } else {
            loge("[Booster]: not support action(" + action + ") for remove all");
            return false;
        }
        return (retToup && retUid);
    }

    /**
     * Universal operation function.
     * Format {cmdStr, parmStr}. cmdStr is necessary. User defines detail content.
     * sample: String[] command = {"priority_set_toup", "192.168.1.100 80 192.168.1.101 80 TCP"}.
     * sample: String[] command = {"add_reset_iptable", "iptables -A OUTPUT -j drop"}.
     * sample: String[] command = {"firewall priority_set_toup",
     *                             "192.168.1.100 80 192.168.1.101 80 TCP"}. when class is 3.
     *
     * @param info to be operated.
     * @return true for success, false for fail.
     * @hide
     */
    public boolean configBoosterDoExtOperation(BoosterInfo info) {
        // Ending with non space can make the string of avoiding risk in official code.
        String[] cmds = {"netdagent firewall", "netdagent throttle", "netdagent network",
                "netdagent"};
        String baseCmd = null;
        String cmd = null;
        String[] strs = info.getMoreInfo();
        int[] cmdClasses = info.getMoreValue();
        // Default the request is processed by "netdagent firewall"
        int cmdClass = (cmdClasses != null && cmdClasses.length > 0) ? cmdClasses[0] : 0;
        String optr = (strs != null && strs.length > 0) ? strs[0] : null;
        String parm = (strs != null && strs.length > 1) ? strs[1] : null;
        int group = info.getGroup();
        int action = info.getAction();
        boolean ret = false;

        if (!isBoosterEnabled()) {
            loge("[Booster]: Extension operation, feature disabled");
            return false;
        }

        if (cmdClass >= cmds.length) {
            loge("[Booster]: Extension operation, error class: " + cmdClass + ", act: " + action);
            return false;
        }

        baseCmd = cmds[cmdClass];
        // Enable the below line to avoid risk in official load. Disable it for pre-research.
        baseCmd += "Official String to avoid risk";
        logd("[Booster]: Extension operation, group: " + group + ", act: " + action
                + ", baseCmd: " + baseCmd);

        if ((action == BoosterInfo.BOOSTER_ACTION_DBG_EXT) && (optr != null)) {
            cmd = baseCmd + " " + optr + "." + group + ((parm == null) ? "" : (" " + parm));
            ret = NPSNetdagentService.sendNetdagentCommand(cmd);
            logd("[Booster]: Extension operation cmd: " + cmd + ", ret: " + ret);
        } else {
            loge("[Booster]: not support action(" + action + ") for " + " optr " + optr);
            return false;
        }
        return ret;
    }

    /**
     * Enable or disable the Booster.
     * Format {operationNumber}. -1 for disable, 1 for enable.
     * sample: int[] ops = {1}.
     *
     * @param info to be operated.
     * @return true for success, false for fail.
     * @hide
     */
    public boolean setFeatureEnabled(BoosterInfo info) {
        int[] ops = info.getMoreValue();
        int action = info.getAction();

        if (ops == null || ops.length == 0 || action != BoosterInfo.BOOSTER_ACTION_FEA_OPS) {
            loge("[Booster]: Wrong ops " + ops + " or act " + action + " for feature switch");
            return false;
        }

        if (ops[0] != 1 && ops[0] != -1) {
            loge("[Booster]: Wrong ops " + ops[0] + " for feature switch");
            return false;
        } else {
            try {
                SystemProperties.set(FEATURE_STATE_PROPERTY, String.valueOf(ops[0]));
                logd("[Booster]: " + (ops[0] == 1  ? "enable" : "disable") + " Booster");
                return true;
            } catch (Exception e) {
                // avoid RuntimeException affects whole target because this lives in system_server.
                loge("[Booster]: " + (ops[0] == 1  ? "enable" : "disable") + " Booster, e: " + e);
            }
            return false;
        }
    }

    /**
     * Query status of the Booster.
     *
     * @param info to be operated.
     * @return true for enable, false for disable.
     * @hide
     */
    public boolean isFeatureEnabled(BoosterInfo info) {
        int action = info.getAction();

        if (action != BoosterInfo.BOOSTER_ACTION_FEA_OPS) {
            loge("[Booster]: Wrong act " + action + " for query feature status");
            return false;
        }

        return isBoosterEnabled();
    }

    /**
     * Check status of the Booster.
     *
     * All set operations are prohibited to avoid rule item increasing after booster disabled.
     * All remove operations are allowed to reduce rule items after booster disabled.
     *
     * @return true for enable, false for disable.
     * @hide
     */
    private boolean isBoosterEnabled() {
        try {
            String ret = SystemProperties.get(FEATURE_STATE_PROPERTY, "1");
            if (Integer.valueOf(ret) == 1) {
                logd("[Booster]: Booster is available");
                return true;
            } else {
                logd("[Booster]: Booster is unavailable");
            }
        } catch (NumberFormatException e) {
            loge("[Booster]: Booster is unavailable, wrong flags e: " + e);
        } catch (Exception e) {
            // avoid RuntimeException affects whole target because this lives in system_server.
            loge("[Booster]: Booster is unavailable, e: " + e);
        }
        return false;
    }

    /**
     * Provide current HAL interface version.
     *
     * @return HAL interface version.
     * @hide
     */
    public int getInterfaceVersion() {
        if (!isBoosterEnabled()) {
            loge("[Booster]: feature disabled");
            return 0;
        }
        return NPSNetdagentService.getInterfaceVersion();
    }

    /**
     * Provide current HAL interface hash.
     *
     * @return HAL interface hash.
     * @hide
     */
    public String getInterfaceHash() {
        return NPSNetdagentService.getInterfaceHash();
    }


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
