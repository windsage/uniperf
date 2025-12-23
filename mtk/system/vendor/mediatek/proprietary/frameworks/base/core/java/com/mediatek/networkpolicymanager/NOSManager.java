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

import android.annotation.RequiresPermission;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

/** NOSManager exports APIs. */
public class NOSManager {
    private static final String TAG = "NOSManager";

    // For permission required APIs, you must own a valid context.
    private final Context mContext;
    private volatile static NOSManager sInstanceWithContext = null;
    private static final Object sNOSManagerLock = new Object();

    /* NOSManager singleton instance */
    private static NOSManager sInstance = new NOSManager();

    /**
     * Below is sub-feature names supported by INetworkPolicyService,
     * used to check whether the sub-feature service is supported or not.
     * Mapping feature name in NetworkPolicyManager, they should be consistent.
     */
    /* APK Booster feature name, used to check whether APK Booster service is supported or not */
    public static final String JXNPS_APK_BOOSTER = "JXNPS_APK_BOOSTER";
    /* Fast Switch feature name, used to check whether Fast Switch service is supported or not */
    public static final String JXNPS_FAST_SWITCH = "JXNPS_FAST_SWITCH";
    // New feature name added here please

    /**
     * Get a default NOSManager singleton instance.
     *
     * @return singleton default instance of NOSManager.
     */
    public static NOSManager getInstance() {
        return sInstance;
    }

    /**
     * Get a default NOSManager singleton instance with context.
     *
     * @param context caller context.
     * @return singleton default instance of NOSManager.
     */
    public static NOSManager getInstance(Context context) {
        if (sInstanceWithContext == null) {
            synchronized (sNOSManagerLock) {
                if (sInstanceWithContext == null) {
                    sInstanceWithContext = new NOSManager(context);
                }
            }
        }
        return sInstanceWithContext;
    }

    /**
     * The constructor of NOSManager.
     *
     * Construt an instannce without a Context
     *
     * @param context a Context.
     * @hide
     */
    private NOSManager() {
        mContext = null;
    }

    /**
     * The constructor of NOSManager.
     *
     * Construt an instannce with a Context
     *
     * @param context a Context.
     * @hide
     */
    private NOSManager(Context context) {
        mContext = context;
    }

    /**
     * Query whether the specified feature supported and return supported version.
     *
     * @param feature specified feature name to be queried.
     * @return 0 if not support, a number greater than 0 indicating the feature version.
     */
    public int versionSupported(String feature) {
        return NetworkPolicyManager.getDefaultInstance().versionSupported(feature);
    }

    /**
     * Enable or disable the specified feature. SDK has no permission and cannot call this API.
     *
     * Applications must have the com.mediatek.permission.SWITCH_NOS_FEATURE permission to toggle
     * the specified feature.
     *
     * @param feature specified feature name to be switched.
     * @param enabled true to enable, false to disable.
     * @return false if the request cannot be satisfied; true indicates that the specified feature
     *         either already in the requested state, or in progress toward the requested state.
     */
    @RequiresPermission("com.mediatek.permission.SWITCH_NOS_FEATURE")
    public boolean setFeatureEnabled(String feature, boolean enabled) {
        return NetworkPolicyManager.getDefaultInstance().setFeatureEnabled(mContext, feature,
                enabled);
    }

    /**
     * Query the status of the specified feature. SDK has no permission and cannot call this API.
     *
     * Applications must have the com.mediatek.permission.SWITCH_NOS_FEATURE permission to query
     * the status of the specified feature.
     *
     * @param feature specified feature name to be queried.
     * @return false for disabled, true for enabled.
     */
    @RequiresPermission("com.mediatek.permission.SWITCH_NOS_FEATURE")
    public boolean isFeatureEnabled(String feature) {
        return NetworkPolicyManager.getDefaultInstance().isFeatureEnabled(mContext, feature);
    }

    /*************************************APK Booster*********************************************/
    // APK Booster start @{
    // Booster level, now only support BOOSTER_FASTEST & BOOSTER_FASTER.
    // Mapping Booster GROUP, they should be consistent.
    public static final int BOOSTER_LEVEL_FASTEST              = 1;
    public static final int BOOSTER_LEVEL_FASTER               = 2;
    public static final int BOOSTER_LEVEL_FAST                 = 3;
    public static final int BOOSTER_LEVEL_NORMAL               = 4;
    /**
     * Add an uid into booster to seepup the corresponding APK.
     *
     * @param level at which level the application is accelerated by booster.
     * @param uid application uid accelerated by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean addUidToBooster(int level, int uid) {
        return NetworkPolicyManager.getDefaultInstance().addUidToBooster(level, uid);
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
        return NetworkPolicyManager.getDefaultInstance().deleteUidFromBooster(level, uid);
    }

    /**
     * Remove all uids from booster to stop accelerating the corresponding APK.
     *
     * @param level at which level the applications are accelerated by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteAllUidsFromBooster(int level) {
        return NetworkPolicyManager.getDefaultInstance().deleteAllUidsFromBooster(level);
    }

    /**
     * Add a link info into booster to seepup the corresponding streams.
     *
     * @param level at which level the link info stream is accelerated by booster.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocol with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocol with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @return true for success, false for fail.
     *
     */
    public boolean addLinkInfoToBooster(int level, String srcIp, String dstIp, int srcPort,
            int dstPort, int proto) {
        return NetworkPolicyManager.getDefaultInstance().addLinkInfoToBooster(level,
                srcIp, dstIp, srcPort, dstPort, proto);
    }

    /**
     * Remove a link info from booster to stop accelerating the corresponding streams.
     *
     * @param level at which level the link info stream is accelerated by booster.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocol with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocol with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteLinkInfoFromBooster(int level,  String srcIp, String dstIp, int srcPort,
            int dstPort, int proto) {
        return NetworkPolicyManager.getDefaultInstance().deleteLinkInfoFromBooster(level,
                srcIp, dstIp, srcPort, dstPort, proto);
    }

    /**
     * Remove all link info from booster to stop accelerating the corresponding streams.
     *
     * @param level at which level the link info stream are accelerated by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteAllLinkInfoFromBooster(int level) {
        return NetworkPolicyManager.getDefaultInstance().deleteAllLinkInfoFromBooster(level);
    }

    /**
     * Remove all link info / uids from booster to stop accelerating the corresponding streams.
     *
     * @param level at which level the link info / uids stream are accelerated by booster.
     * @return true for success, false for fail.
     *
     */
    public boolean deleteAllFromBooster(int level) {
        return NetworkPolicyManager.getDefaultInstance().deleteAllFromBooster(level);
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
        return NetworkPolicyManager.getDefaultInstance().doBoosterExtOperation(level, cmd, val);
    }
    // APK Booster end @}

    /*************************************Fast Swtich*********************************************/
    // Fast Swtich start @{
    /**
     * NOS Bundle key constant.
     */
    // the following Bundle keys own string values
    public static final String BK_IF_NAME             = "interfacename";
    public static final String BK_IF_INDEX            = "interfaceindex";
    public static final String BK_NW_TYPE             = "networktype";
    public static final String BK_APK_PID             = "apkpid";
    public static final String BK_APK_CAP             = "apkcapability";
    public static final String BK_PF_CAP              = "platformcapability";
    public static final String BK_ERR_REASON          = "errorreason";

    /**
     * Get NOS service maximum capability.
     * It is the minimum capability among NPM, NPS, NNPS and NOS.
     * For built-in APK, will use platform NPM that should always keep consistent with NOS.
     *
     * Usage(3rd party APK and built-in APK):
     *     import android.text.TextUtils;
     *
     *     // Strongly suggest APK follows the below demo and define gCapability with NPM version
     *     // in SDK or Platform.
     *     private final String gCapability = "1.5";
     *     private bool sIsNOSEnabled = false;
     *
     *     String cap = getNOSServerCapability(null);
     *     float max = -1.0;
     *
     *     cap = (TextUtils.isEmpty(cap)) ? null : cap.split(",")[0];
     *     try {
     *         max = (cap == null) ? -1.0 : Float.valueOf(cap);
     *     } catch (NumberFormatException e) {
     *     }
     *
     *     if (max <= 0) {
     *         // Disable NOS service in current APK directly.
     *         // sIsNOSEnabled = false; disable NOS service in current APK
     *     } else if (max >= Float.valueOf(gCapability)) {
     *         float min = -1.0;
     *         if (cap.split(",").lenght > 1) {
     *             try {
     *                 min = Float.valueOf(cap.split(",")[1]);
     *                 if (min > Float.valueOf(gCapability)) {
     *                     // Disable NOS service in current.
     *                     // sIsNOSEnabled = false; disable NOS service in current APK
     *                 } else {
     *                     // Using gCapability
     *                     sIsNOSEnabled = true;
     *                     addINOSListener(listener, gCapability, null);
     *                 }
     *             } catch (NumberFormatException e) {
     *                 // do nothing
     *             }
     *         } else {
     *             // The platform guarantees backward compatibility with APK
     *             sIsNOSEnabled = true;
     *             addINOSListener(listener, gCapability, null);
     *         }
     *     } else {
     *         // APK owner decide how to do.
     *
     *         // Disable NOS service in current APK.
     *         // sIsNOSEnabled = false; disable NOS service in current APK
     *
     *         // Enable NOS service in current APK, but APK owner should guarantee backward
     *         // compatibility with platform.
     *         // sIsNOSEnabled = true;
     *         // addINOSListener(listener, String.valueOf(max), null);
     *     }
     *
     * @param b bundle object. for extension, input null now please.
     * @return platform capability. Format sample "1.2", get float by Float.valueOf().
     *         Why return a string? This is for extension later.
     *         The future format will be like "7.8,5.2", 7.8 is max platform capability and
     *         5.2 will be min platform capability because platform cannot have unrestricted
     *         backward compatibility for ever.
     */
    public String getNOSServerCapability(Bundle b) {
        return NetworkPolicyManager.getDefaultInstance().getNOSServerCapability(b);
    }

    /**
     * Add INOSListener to listener list to monitor notification.
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
     *    NOSManager.getInstance().addINOSListener(listener, capability, null);
     *
     * @param listener to listen INOS related information.
     * @param capability can be understood as on which version the current APK will work.
     * @param b bundle object. for extension, input null now please.
     */
    public void addINOSListener(INOSListener listener, String capability, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().addINOSListener(listener, capability, b);
    }

    /**
     * Add INOSListener to listener list to monitor notification.
     * The API has been superseded by:
     *        addINOSListener(INOSListener listener, String capability, Bundle b)
     *
     * @param listener to listen INOS related information.
     * @Deprecated
     */
    public void addINOSListener(INOSListener listener) {
        NetworkPolicyManager.getDefaultInstance().addINOSListener(listener, null, null);
    }

    /**
     * Remove INOSListener from listener list.
     *
     * @param listener to be removed listener of INOS.
     */
    public void removeINOSListener(INOSListener listener) {
        NetworkPolicyManager.getDefaultInstance().removeINOSListener(listener);
    }

    /**
     * Start monitor for the specified network.
     *
     * Usage:
     *     Bundle b = new Bundle();
     *     b.putString("key1", "value1");
     *     b.putInt("key2", "value2");
     *     b.putX("key3", "value3");   // Suggest basic data type now.
     *
     *     b.containsKey("key1");      // it is better before use it.
     *     b.getString("key1", null);  // default null.
     *     b.getInt("key2", -1);       // default -1.
     *     b.getX("key3", x);          // default x.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     *          Such as networkType NETWORK_TYPE_WIFI, b.putString("interfacename", "wlan1");
     *          Default is determined by NOS server if "interfacename" is not set.
     */
    public void startMonitor(int networkType, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().startMonitor(networkType, b);
    }

    /**
     * Pause monitor for the specified network.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void pauseMonitor(int networkType, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().pauseMonitor(networkType, b);
    }

    /**
     * Resume monitor for the specified network.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void resumeMonitor(int networkType, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().resumeMonitor(networkType, b);
    }

    /**
     * Stop monitor for the specified network.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void stopMonitor(int networkType, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().stopMonitor(networkType, b);
    }

    /**
     * Get monitor state of the specified network type.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     *          Such as networkType NETWORK_TYPE_WIFI, b.putString("interfacename", "wlan1");
     * @return monitor state.
     */
    public int getMonitorState(int networkType, Bundle b) {
        return NetworkPolicyManager.getDefaultInstance().getMonitorState(networkType, b);
    }

    /**
     * Set monitor sensitivity for the specified network, update it if it has been set.
     *
     * @param networkType to be monitored.
     * @param sensitivity Monitor sensitivity.
     * @param b bundle object. for extension, input null now please.
     */
    public void setMonitorSensitivity(int networkType, int sensitivity, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().setMonitorSensitivity(networkType, sensitivity,
                b);
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
        return NetworkPolicyManager.getDefaultInstance().getMonitorSensitivity(networkType, b);
    }

    /**
     * Set uid for the specified network type to monitor.
     * It maybe update operation for same networkType because NOS service only support three items.
     *
     * @param networkType to be monitored.
     * @param uid to be monitored. default will monitor all packets.
     * @param b bundle object. for extension, input null now please.
     */
    public void setUidForMonitor(int networkType, int uid, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().setUidForMonitor(networkType, uid, b);
    }

    /**
     * Remove uid from the specified network type monitor.
     *
     * @param networkType to be monitored.
     * @param uid to be monitored. default will monitor all packets.
     * @param b bundle object. for extension, input null now please.
     */
    public void removeUidFromMonitor(int networkType, int uid, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().removeUidFromMonitor(networkType, uid, b);
    }

    /**
     * Remove all uids from the specified network type monitor.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void removeAllUidsFromMonitor(int networkType, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().removeAllUidsFromMonitor(networkType, b);
    }

    /**
     * Set link info for the specified network type to monitor.
     * It maybe update operation for same networkType because NOS service only support three items.
     *
     * @param networkType to be monitored.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocol with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocol with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @param b bundle object. for extension, input null now please.
     */
    public void setLinkInfoForMonitor(int networkType, String srcIp, String dstIp, int srcPort,
            int dstPort, int proto, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().setLinkInfoForMonitor(networkType, srcIp, dstIp,
                srcPort,  dstPort, proto, b);
    }

    /**
     * Remove link info from the specified network type monitor.
     *
     * @param networkType to be monitored.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocol with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocol with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @param b bundle object. for extension, input null now please.
     */
    public void removeLinkInfoFromMonitor(int networkType, String srcIp, String dstIp, int srcPort,
            int dstPort, int proto, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().removeLinkInfoFromMonitor(networkType, srcIp,
                dstIp, srcPort,  dstPort, proto, b);
    }

    /**
     * Remove all link info from the specified network type monitor.
     *
     * @param networkType to be monitored.
     * @param b bundle object. for extension, input null now please.
     */
    public void removeAllLinkInfoFromMonitor(int networkType, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().removeAllLinkInfoFromMonitor(networkType, b);
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
        return NetworkPolicyManager.getDefaultInstance().getLocalLinkQuality(networkType, b);
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
        return NetworkPolicyManager.getDefaultInstance().getFullLinkQuality(networkType, b);
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
        return NetworkPolicyManager.getDefaultInstance().getSignalStrength(networkType, b);
    }

    // bundle.putString(BK_PARM_L2RTT_THR, "5") to set L2 RTT threshold. Unit is ms
    // bundle.putString(BK_PARM_L2RTT_THR, BV_PARM_QRY) to get L2 RTT threshold
    public static final String BK_PARM_L2RTT_THR      = "parml2rttthr";
    // timeout count in low interference environment/timeout count in high interference environment
    // bundle.putString(BK_PARM_L2RTT_IE_THR, "10/20/40/90") to set L2 RTT IE threshold
    // bundle.putString(BK_PARM_L2RTT_IE_THR, BV_PARM_QRY) to get L2 RTT IE threshold
    public static final String BK_PARM_L2RTT_IE_THR   = "parml2rttiethr";
    // bundle.putString(BK_PARM_L4RTT_THR, "level0|10/30/50/80") to set L4 RTT threshold. Unit: ms
    // bundle.putString(BK_PARM_L4RTT_THR, "level1|20/50/60/80") to set L4 RTT threshold. Unit: ms
    // bundle.putString(BK_PARM_L4RTT_THR, BV_PARM_QRY + "|level0") to get L4 RTT level0 threshold
    // bundle.putString(BK_PARM_L4RTT_THR, BV_PARM_QRY) to get L4 RTT default level1 threshold
    public static final String BK_PARM_L4RTT_THR      = "parml4rttthr";
    // bundle.putString(BK_PARM_RSSI_THR, "-88/-77/-66/-50") to set RSSI threshold. Unit is dBm
    // bundle.putString(BK_PARM_RSSI_THR, BV_PARM_QRY) to get RSSI threshold
    public static final String BK_PARM_RSSI_THR       = "parmrssithr";
    // bundle.putString(BK_PARM_RSSI_LWEI_THR, "-66/-75") to set large weight RSSI threshold
    // bundle.putString(BK_PARM_RSSI_LWEI_THR, BV_PARM_QRY) to get large weight RSSI threshold
    public static final String BK_PARM_RSSI_LWEI_THR  = "parmrssilweithr"; // Unit is dBm
    // bundle.putString(BK_PARM_REC_THR, "1000") to set reconfirm threshold. Unit is ms
    // bundle.putString(BK_PARM_REC_THR, BV_PARM_QRY) to get reconfirm threshold
    public static final String BK_PARM_REC_THR        = "parmreconfirmthr";
    // bundle.putString(BK_PARM_DEBUG, string) to set pre agreed debug string(key|value)
    // bundle.putString(BK_PARM_DEBUG, BV_PARM_QRY + "|" + subkey) to get value of specified subkey
    public static final String BK_PARM_DEBUG          = "parmdebug";
    // bundle.putString(BK_TIMEOUT_INMS, "500") to set waiting timeout time. Unit is ms
    // Get actions maybe cost more time. it will return fail directly after timeout. Default 500ms
    public static final String BK_TIMEOUT_INMS        = "TIMEOUTINMS";

    // Default value indicator of all bundle parameter key
    // nos service set default for this and ignore it in get functions.
    public static final String BV_PARM_DEF            = "JXNDEF";
    // Query operation. Return value format x/y/z/...
    // Sample: bundle.putString(BK_PARM_DEBUG, BV_PARM_QRY) to query value of non-subkey item.
    // Sample: bundle.putString(BK_PARM_DEBUG, BV_PARM_QRY + "|" + subkey) to query value of subkey
    public static final String BV_PARM_QRY            = "JXNQRY";
    // Query range operation. Return value format f~c/f~c/f~c/... (f:floor, c:ceil)
    // Sample: bundle.putString(BK_PARM_DEBUG, BV_PARM_SVR) to query scope of non-subkey item.
    // Sample: bundle.putString(BK_PARM_DEBUG, BV_PARM_SVR + "|" + subkey) to query scope of subkey
    public static final String BV_PARM_SVR            = "JXNSVR";

    /**
     * Set specified parameters in b into nos service. No callback for "set" in INOSListener.
     * networkType is necessary
     *
     * @param networkType to be configured.
     * @param b bundle object with configure parameter key and its values. key & value are string.
     */
    public void setCfgParm(int networkType, Bundle b) {
        NetworkPolicyManager.getDefaultInstance().setCfgParm(networkType, b);
    }

    /**
     * Get specified parameters in b from nos service. BV_PARM_QRY or BV_PARM_SVR should be into
     * bundle as value. networkType is necessary
     *
     * @param networkType to be queried.
     * @param b bundle object with queried parameter key & value(detail key). key&value are string
     * @return 0 success and !0 fail.
     */
    public int getCfgParm(int networkType, Bundle b) {
        return NetworkPolicyManager.getDefaultInstance().getCfgParm(networkType, b);
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
