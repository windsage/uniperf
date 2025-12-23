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

package com.mediatek.networkpolicyservice.utils;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.mediatek.networkpolicymanager.NetworkPolicyManager;

import vendor.mediatek.hardware.netdagent.INetdagents;
import vendor.mediatek.hardware.netdagent.V1_0.INetdagent;

/** Netdagent service adapter. */
public class NPSNetdagentService {
    private static final String TAG = "NPSNetdagentService";

    /**
     * Return an INetdagents instance, or null if not available.
     *
     * It is the caller's responsibility to check for a null return value
     * and to handle RemoteException errors from invocations on the returned
     * interface if, for example, netdagent dies and is restarted.
     *
     * Returned instances of INetdagents should not be cached.
     *
     * @return an INetdagents instance or null.
     */
    private static INetdagents getNetdagentAidlInstance() {
        // NOTE: ServiceManager does no caching for the netdagent service,
        // because netdagent is not one of the defined common services.
        final INetdagents netdagentInstance = INetdagents.Stub.asInterface(
                ServiceManager.checkService(INetdagents.DESCRIPTOR + "/default"));
        if (netdagentInstance == null) {
            loge("aidl: getNetdagentInstance, get" + INetdagents.DESCRIPTOR
                    + "aidl service failed");
        }
        return netdagentInstance;
    }

    /**
     * Send a command to netdagent service.
     *
     * @param cmd sent to netdagent.
     * @return true for success, false for fail.
     */
    public static boolean sendNetdagentCommand(String cmd) {
        boolean res = false;
        try {

            INetdagents netdagentAidl = getNetdagentAidlInstance();
            if (netdagentAidl == null) {
                loge("aidl: netdagentAidl is null");
            } else {
                res = netdagentAidl.dispatchNetdagentCmd(cmd);
                logd("aidl: Netdagent Command: " + cmd + ", res: " + res);
                return res;
            }

            INetdagent netagent = INetdagent.getService();
            if (netagent == null) {
                loge("hidl: netagent is null");
                return false;
            }
            res = netagent.dispatchNetdagentCmd(cmd);
            logd("hidl: Netdagent Command: " + cmd + ", res: " + res);
        } catch (RemoteException e) {
            loge("get netdagent service(sendcmd) exception: " + e);
        }
        return res;
    }

    /**
     * Provide current HAL interface version.
     *
     * @return  HAL interface version.
     *         0 is unsupported, > 0 is client version (high 16bits) + server version (low 16bits).
     * @hide
     */
    public static int getInterfaceVersion() {
        int ver = 0;
        try {
            INetdagents netdagentAidl = getNetdagentAidlInstance();
            if (netdagentAidl == null) {
                loge("aidl: getInterfaceVersion netdagentAidl is null");
            } else {
                ver = netdagentAidl.getInterfaceVersion();
                if (ver > 0) {
                    logd("aidl: getInterfaceVersion netdagentAidl, ver: " + ver);
                    return ((INetdagents.VERSION << 16) + ver);
                } else {
                    loge("aidl: getInterfaceVersion netdagentAidl, ver: " + ver);
                    return 0;
                }
            }
        } catch (RemoteException e) {
            loge("get netdagent service(ver) exception: " + e);
        }

        // hidl has no getInterfaceVersion()
        ver = 1;
        logi("hidl: getInterfaceVersion netdagentAidl, default = 1");
        return (ver > 0) ? ((ver << 16) + ver) : 0;
    }

    /**
     * Provide current HAL interface hash.
     *
     * @return HAL interface hash
     * @hide
     */
    public static String getInterfaceHash() {
        String h = null;
        try {
            INetdagents netdagentAidl = getNetdagentAidlInstance();
            if (netdagentAidl == null) {
                loge("aidl: getInterfaceHash netdagentAidl is null");
            } else {
                h = netdagentAidl.getInterfaceHash();
                logd("aidl: getInterfaceHash netdagentAidl, h: " + h);
                return h;
            }
        } catch (RemoteException e) {
            loge("get netdagent service(hash) exception: " + e);
        }

        // hidl has no getInterfaceHash()
        h = "0123456789012345678901234567890123456789";
        logi("hidl: getInterfaceHash netdagentAidl, default = 0123456789");
        return h;
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
