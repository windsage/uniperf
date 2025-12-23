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

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.mediatek.networkpolicymanager.NetworkPolicyManager;

import vendor.mediatek.hardware.nps.nos.fastswitch.INativeFastSwitch;
import vendor.mediatek.hardware.nps.nos.fastswitch.NativeFastSwitchInfo;

/** FastSwitch service adapter. */
public class NPSFastSwitchService {
    private static final String TAG = "NPSFastSwitchService";

    private static final Object sNPSFastSwitchServiceListenerLock = new Object();

    private static volatile INativeFastSwitch sNFS = null;
    private static final Object sNFSServiceLock = new Object();
    /* NativeFastSwitchService died callback */
    private static final DeathRecipient sNFSServiceDeath = new DeathRecipient();

    private static INPSFastSwitchServiceMonitor sMonitor = null;

    /**
     * Reset INativeFastSwitch when its server die.
     *
     * @hide
     */
    private static void resetFastSwitchService() {
        synchronized (sNFSServiceLock) {
            // sNFS die.
            // Clear local sub-feature information to avoid resume fail.
            if (sMonitor != null) {
                sMonitor.onNativeFastSwitchServiceDied();
                sMonitor = null;
            }
            if (sNFS != null) {
                sNFS.asBinder().unlinkToDeath(sNFSServiceDeath, 0);
                sNFS = null;
            }
            // can callback to client here
            loge("resetFastSwitchService(): NativeFastSwitchService died");
        }
    }

    /**
     * Callback class when the process hosting INativeFastSwitch has gone away.
     *
     * @hide
     */
    private static class DeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            resetFastSwitchService();
        }
    }

    /**
     * Return an INativeFastSwitch instance, or null if not available.
     *
     * It is the caller's responsibility to check for a null return value
     * and to handle RemoteException errors from invocations on the returned
     * interface, for example, INativeFastSwitch dies and is restarted.
     *
     * Returned instances of INativeFastSwitch should not be cached.
     *
     * @return an INativeFastSwitch instance or null.
     */

    private static INativeFastSwitch getFastSwitchAidlInstance() {
        if (sNFS == null) {
            synchronized (sNFSServiceLock) {
                if (sNFS == null) {
                    // NOTE: ServiceManager does no caching for the FastSwitch service,
                    // because FastSwitch is not one of the defined common services.
                    // But here, client need know service state in time, so cache it here.
                    sNFS = INativeFastSwitch.Stub.asInterface(
                            ServiceManager.checkService(INativeFastSwitch.DESCRIPTOR
                            + "/default"));
                    if (sNFS == null) {
                        loge("aidl: getFastSwitchAidlInstance, get " + INativeFastSwitch.DESCRIPTOR
                                + "aidl service failed");
                    }
                }
            }
        }
        return sNFS;
    }

    /**
     * Add INPSFastSwitchServiceMonitor to notify FastSwitchService die.
     *
     * @param m to notify NativeFastSwitch service die.
     * @hide
     */
    public static void addNativeFastSwitchServiceMonitor(INPSFastSwitchServiceMonitor m) {
        synchronized (sNFSServiceLock) {
            if (sMonitor == null) {
                sMonitor = m;
            } else {
                loge("too many addNativeFastSwitchServiceMonitor");
            }
        }
    }

    /**
     * Add IFastSwitchListener to monitor FastSwitchInfo notification.
     *
     * Why add Listener without network type parameter? Because it report for all network type.
     * Clients will receive all network type callback and it is ok that interersted clients process
     * those who care about.
     *
     * In fact, NPM only add one listener by design.
     *
     * @param listener to listen FastSwitch related information.
     * @return true for success, false for fail
     * @hide
     */
    public static boolean addFastSwitchListener(NPSFastSwitchServiceListener listener) {
        boolean ret = false;
        synchronized (sNPSFastSwitchServiceListenerLock) {
            try {
                INativeFastSwitch fastswitchAidl = getFastSwitchAidlInstance();
                if (fastswitchAidl != null) {
                    fastswitchAidl.addFastSwitchListener(listener);
                    fastswitchAidl.asBinder().linkToDeath(sNFSServiceDeath, 0);
                    ret = true;
                    logi("aidl: fastswich listener: " + listener);
                } else {
                    loge("aidl: addFastSwitchListener fastswitchAidl is null");
                }
            } catch (RemoteException e) {
                ret = false;
                loge("addFastSwitchListener exception: " + e);
            } catch (android.os.ServiceSpecificException e) {
                ret = false;
                loge("addFastSwitchListener exception: " + e + ", errno: " + e.errorCode);
            }
        }
        return ret;
    }

    /**
     * Configure fast switch for the specified network without return valaue.
     * All functions with void return type can be implemented by info.mAction.
     *
     * @param info networkType and action in info are necessory.
     * @hide
     */
    public static void configFastSwitchInfo(NativeFastSwitchInfo info) {
        try {
            INativeFastSwitch fastswitchAidl = getFastSwitchAidlInstance();
            if (fastswitchAidl != null) {
                fastswitchAidl.configFastSwitchInfo(info);
                logi("aidl: configFastSwitchInfo, act: " + info.mAction + ", network type: "
                        + info.mNetworkType);
            } else {
                loge("aidl: configFastSwitchInfo fastswitchAidl is null");
            }
        } catch (RemoteException e) {
            loge("configFastSwitchInfo exception: " + e);
        } catch (android.os.ServiceSpecificException e) {
            loge("configFastSwitchInfo exception: " + e + ", errno: " + e.errorCode);
        }
    }

    /**
     * Configure fast switch for the specified network with return int value.
     * For boolean value returned, use 0 indicateds false, != 0 indicates true.
     * All functions with int and boolean return type can be implemented by info.mAction.
     *
     * @param info networkType in info is necessory.
     * @return result of info.mAction.
     * @hide
     */
    public static int configFastSwitchInfoWithIntResult(NativeFastSwitchInfo info) {
        int ret = 0;
        try {
            INativeFastSwitch fastswitchAidl = getFastSwitchAidlInstance();
            if (fastswitchAidl != null) {
                ret = fastswitchAidl.configFastSwitchInfoWithIntResult(info);
                logi("aidl: configFastSwitchInfoWithIntResult, act: " + info.mAction
                        + ", network type: " + info.mNetworkType + ", ret: " + ret);
            } else {
                loge("aidl: configFastSwitchInfoWithIntResult fastswitchAidl is null");
            }
        } catch (RemoteException e) {
            loge("configFastSwitchInfoWithIntResult exception: " + e);
        } catch (android.os.ServiceSpecificException e) {
            ret = e.errorCode > 0 ? -e.errorCode : e.errorCode;
            loge("configFastSwitchInfoWithIntResult exception: " + e + ", errno: " + (-ret));
        }
        return ret;
    }

    /**
     * Provide current AIDL interface version.
     *
     * @return AIDL interface version.
     *         0 is unsupported, > 0 is client version (high 16bits) + server version (low 16bits).
     * @hide
     */
    public static int getInterfaceVersion() {
        int ver = 0;
        try {
            INativeFastSwitch fastswitchAidl = getFastSwitchAidlInstance();
            if (fastswitchAidl != null) {
                ver = fastswitchAidl.getInterfaceVersion();
                if (ver > 0) {
                    logi("aidl: getInterfaceVersion, ver: " + ver);
                    return ((INativeFastSwitch.VERSION << 16) + ver);
                } else {
                    loge("aidl: getInterfaceVersion, ver: " + ver);
                }
            } else {
                loge("aidl: getInterfaceVersion fastswitchAidl is null");
            }
        } catch (RemoteException e) {
            loge("get nativefastswich service(ver) exception: " + e);
        }
        return 0;
    }

    /**
     * Provide current AIDL interface hash.
     *
     * @return AIDL interface hash
     * @hide
     */
    public static String getInterfaceHash() {
        String h = null;
        try {
            INativeFastSwitch fastswitchAidl = getFastSwitchAidlInstance();
            if (fastswitchAidl != null) {
                h = fastswitchAidl.getInterfaceHash();
                logi("aidl: getInterfaceHash, h: " + h);
            } else {
                loge("aidl: getInterfaceHash fastswitchAidl is null");
            }
        } catch (RemoteException e) {
            loge("get nativefastswich service(hash) exception: " + e);
        }
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
