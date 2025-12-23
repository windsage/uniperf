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

package com.mediatek.networkpolicyservice.fastswitch;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.mediatek.networkpolicymanager.NetworkPolicyManager;
import com.mediatek.networkpolicymanager.fastswitch.FastSwitchInfo;
import com.mediatek.networkpolicymanager.fastswitch.IFastSwitchListener;

import com.mediatek.networkpolicyservice.utils.INPSFastSwitchServiceMonitor;
import com.mediatek.networkpolicyservice.utils.NPSAddress;
import com.mediatek.networkpolicyservice.utils.NPSFastSwitchService;
import com.mediatek.networkpolicyservice.utils.NPSFastSwitchServiceListener;

import java.util.Map;

import vendor.mediatek.hardware.nps.nos.fastswitch.NativeFastSwitchInfo;
import vendor.mediatek.hardware.nps.nos.fastswitch.NativeFastSwitchInfoConst;

/**
 * Fast Switch service adapter.
 * NPM layer with "platform" source has same behavior with "sdk" source.
 * When developing APK with "platform" source, should adjust the places marked "NPM".
 */
public class NPSFastSwitch {
    private static final String TAG = "NPSFastSwitch";

    /* Singleton NPSFastSwitch instace */
    private volatile static NPSFastSwitch sInstance = null;
    private static final Object sNPSFastSwitchLock = new Object();

    private Context mContext = null;
    // Feature control property, only can be operated by platform.
    private static final String FEATURE_STATE_PROPERTY = "persist.vendor.jxnps.fastswitch.enabled";

    private static boolean sInited = false;

    private static final Object sIFastSwitchListenerLock = new Object();
    /* IFastSwitchListener registered RemoteCallbackList */
    private static RemoteCallbackList<IFastSwitchListener> sIFastSwitchListenerRemoteCallbackList
            = new RemoteCallbackList<>() {
                @Override
                public void onCallbackDied(IFastSwitchListener l, Object cookie) {
                    synchronized (sIFastSwitchListenerLock) {
                        // stopMonitor for this crash APK if it is from "sdk".
                        // it is unexpected that other sources come here.
                        // carsh APK from "platform" should be protected by NPM, see onCallbackDied
                        // in NetworkPolicyManager.java.
                        boolean s = true;
                        Bundle b = new Bundle();
                        b.putString(FastSwitchInfo.BK_APK_CAP, sListenerVersions.get(l));
                        b.putString(FastSwitchInfo.BK_APK_PID, sListenerPids.get(l));
                        b.putString(FastSwitchInfo.BK_APK_UID, sPidUids.get((String) cookie));
                        b.putString(FastSwitchInfo.BK_IF_NAME, sPidIfNames.get((String) cookie));
                        b.putString(FastSwitchInfo.BK_REQ_SOURCE, FastSwitchInfo.BV_REQ_SOURCE_F);
                        if (sPidNetTypes.get((String) cookie) == null) {
                            s = false;
                        } else {
                            FastSwitchInfo info = new FastSwitchInfo(
                                    sPidNetTypes.get((String) cookie),
                                    FastSwitchInfo.ACTION_REQ_STP_MNR, b);
                            // need stopMonitor is static, so use class to get instead because
                            // static have more cost.
                            // the null context has some risk, but now it is ok.
                            NPSFastSwitch.getInstance(null).stopMonitor(info);
                        }

                        if (l != null) {
                            sListenerPids.remove(l);
                            sListenerVersions.remove(l);
                            sListenerSource.remove(l);
                        }
                        if (cookie != null) {
                            sPidNetTypes.remove((String) cookie);
                            sPidVersions.remove((String) cookie);
                            sPidIfNames.remove((String) cookie);
                            sPidUids.remove((String) cookie);
                            sPidListeners.remove((String) cookie);
                            sPidOperFlag.remove((String) cookie);
                        }
                        logi("onCallbackDied: l-" + l + ", pid-" + (String) cookie + ", send-" + s
                                + ", size(" + sListenerPids.size() + "/" + sListenerVersions.size()
                                + "/" + sListenerSource.size() + "/" + sPidNetTypes.size()
                                + "/" + sPidVersions.size() + "/" + sPidIfNames.size()
                                + "/" + sPidUids.size() + "/" + sPidListeners.size()
                                + "/" + sPidOperFlag.size() + ")");
                    }
                }
            };
    private static final int IFASTSWITCH_LISTENER_MAX = 100;
    private static ArrayMap<String, Integer> sPidNetTypes = new ArrayMap<String, Integer>();
    private static ArrayMap<String, String> sPidVersions = new ArrayMap<String, String>();
    private static ArrayMap<String, String> sPidIfNames = new ArrayMap<String, String>();
    private static ArrayMap<String, String> sPidUids = new ArrayMap<String, String>();
    private static ArrayMap<String, IFastSwitchListener> sPidListeners
            = new ArrayMap<String, IFastSwitchListener>();
    private static ArrayMap<IFastSwitchListener, String> sListenerPids
            = new ArrayMap<IFastSwitchListener, String>();
    private static ArrayMap<IFastSwitchListener, String> sListenerVersions
            = new ArrayMap<IFastSwitchListener, String>();
    private static ArrayMap<IFastSwitchListener, String> sListenerSource
            = new ArrayMap<IFastSwitchListener, String>();

    // Foolproof mechanism for background user.
    // Only for "sdk" source APK here. Source "platform" APK is protected in NPM layer.
    private static ArrayMap<String, Integer> sPidOperFlag = new ArrayMap<String, Integer>();
    private static final int FLAG_INIT_MONITOR  = 0; // Initial state
    private static final int FLAG_FPP_PAU_MONITOR = 1 << 0; // Pause from foolproof protection
    private static final int FLAG_FPP_RES_MONITOR = 1 << 1; // Resume from foolproof protection
    private static final int FLAG_APK_STR_MONITOR = 1 << 2; // Start from APK
    private static final int FLAG_APK_RES_MONITOR = 1 << 3; // Resume from APK
    private static final int FLAG_APK_PAU_MONITOR = 1 << 4; // Pause from APK
    private static final int FLAG_APK_STP_MONITOR = 1 << 5; // Stop from APK

    // Monitor NativeFastSwitch service.
    private static INPSFastSwitchServiceMonitor sNPSSM = new INPSFastSwitchServiceMonitor() {
        /**
         * notify NativeFastSwitch service die.
         */
        @Override
        public void onNativeFastSwitchServiceDied() {
            synchronized (sIFastSwitchListenerLock) {
                loge("[FastSwitch]: onNativeFastSwitchServiceDied, notify and clear listeners");
                sInited = false;
                int n = sIFastSwitchListenerRemoteCallbackList.beginBroadcast();
                if (n < 1) {
                    loge("[FastSwitch]: onNativeFastSwitchServiceDied, No IFastSwitchListener");
                    return;
                }

                try {
                    for (int i = 0; i < n; i++) {
                        logd("[FastSwitch]: onNativeFastSwitchServiceDied: " + n + ":" + i + ": "
                                + sIFastSwitchListenerRemoteCallbackList.getBroadcastItem(i));
                        sIFastSwitchListenerRemoteCallbackList.getBroadcastItem(i)
                                .onNativeFastSwitchServiceDied();
                    }
                } catch (RemoteException e) {
                    loge("[FastSwitch]: onNativeFastSwitchServiceDied fail: " + e);
                }
                sIFastSwitchListenerRemoteCallbackList.finishBroadcast();
                removeAllIFastSwitchListenerFromRemoteCallbackList();
            }
        }
    };

    // This should be a global variable. Or it will be destroied to cause NativeFastSwitch service
    // receives died notifiction to erase this listener.
    private static NPSFastSwitchServiceListener sNFSSL = new NPSFastSwitchServiceListener() {
        /**
         * Notifies that FastSwitch infomation.
         * @param info NativeFastSwitch change event.
         */
        @Override
        public void onFastSwitchInfoNotify(NativeFastSwitchInfo info) {
            String str = "";
            FastSwitchInfo fi = null;
            int act = info.mAction;
            int net = info.mNetworkType;
            int sta = info.mMonitorState;
            int sen = info.mMonitorSensitivity;
            int llq = info.mLocalLinkQuality;
            int flq = info.mFullLinkQuality;
            int ss  = info.mSignalStrength;
            Bundle b = new Bundle();

            if (info.mStringValues != null) {
                for (int i = 0; i < info.mStringValues.length; i++) {
                    if (!TextUtils.isEmpty(info.mStringValues[i])) {
                        str += info.mStringValues[i];
                        String[] kv = info.mStringValues[i].split(":");
                        if (kv.length != 2) {
                            loge("onFastSwitchInfoNotify: s[" + i + "]: " + info.mStringValues[i]);
                        } else {
                            if (!TextUtils.isEmpty(kv[0])) {
                                b.putString(kv[0], kv[1]);
                            } else {
                                loge("onFastSwitchInfoNotify: kv: " + info.mStringValues[i]);
                            }
                        }
                    }
                }
            }

            logi("onFastSwitchInfoNotify: act: " + act + ", net: " + net + ", sta: " + sta
                    + ", sen: " + sen + ", llq: " + llq + ", flq: " + flq + ", ss: " + ss
                    + ", ver: " + b.getString(FastSwitchInfo.BK_PF_CAP)
                    + ", pid: " + b.getString(FastSwitchInfo.BK_APK_PID)
                    + ", ifname: " + b.getString(FastSwitchInfo.BK_IF_NAME)
                    + ", info.mStringValues: " + str);
            synchronized (sIFastSwitchListenerLock) {
                int n = sIFastSwitchListenerRemoteCallbackList.beginBroadcast();
                if (n < 1) {
                    loge("[FastSwitch]: onFastSwitchInfoNotify, No IFastSwitchListener");
                    return;
                }

                switch (act) {
                case NativeFastSwitchInfoConst.ACTION_REPORT_STA:
                    fi = new FastSwitchInfo(net, act, sta, b);
                    break;
                case NativeFastSwitchInfoConst.ACTION_REPORT_MS:
                    fi = new FastSwitchInfo(net, act, sen, b);
                    break;
                case NativeFastSwitchInfoConst.ACTION_REPORT_LLQ:
                    fi = new FastSwitchInfo(net, act, llq, b);
                    break;
                case NativeFastSwitchInfoConst.ACTION_REPORT_FLQ:
                    fi = new FastSwitchInfo(net, act, flq, b);
                    break;
                case NativeFastSwitchInfoConst.ACTION_REPORT_SS:
                    fi = new FastSwitchInfo(net, act, ss, b);
                    break;
                case FastSwitchInfo.ACTION_REPORT_GEN:
                    // User FastSwitchInfo.ACTION_REPORT_GEN because the change doesn't update aidl
                    fi = new FastSwitchInfo(net, act, b);
                    break;
                default:
                    loge("[FastSwitch]: onFastSwitchInfoNotify, unsupported " + "action:" + act);
                    sIFastSwitchListenerRemoteCallbackList.finishBroadcast();
                    return;
                }

                try {
                    for (int i = 0; i < n; i++) {
                        logd("[FastSwitch]: onFastSwitchInfoNotify: " + n + ":" + i + ": "
                                + sIFastSwitchListenerRemoteCallbackList.getBroadcastItem(i));
                        sIFastSwitchListenerRemoteCallbackList.getBroadcastItem(i)
                                .onFastSwitchInfoNotify(fi);
                    }
                 } catch (RemoteException e) {
                     loge("onFastSwitchInfoNotify fail: " + e);
                 }
                 sIFastSwitchListenerRemoteCallbackList.finishBroadcast();
            }
        }
    };

    /**sIFastSwitchListenerRemoteCallbackList
     * Remove all callbacks from RemoteCallbackList.
     * RemoteCallbackList.kill() will disable RemoteCallbackList, so cannot cover the function of
     * remove all callbacks.
     *
     * Note: Callers must hold sIFastSwitchListenerLock.
     */
    private static void removeAllIFastSwitchListenerFromRemoteCallbackList() {
        int n = sIFastSwitchListenerRemoteCallbackList.beginBroadcast();
        if (n < 1) {
            loge("[FastSwitch]: removeAllIFastSwitchListenerFromRemoteCallbackList, "
                    + "No IFastSwitchListener");
            return;
        }

        for (int i = n - 1; i >= 0; i--) {
            logd("[FastSwitch]: removeAllIFastSwitchListenerFromRemoteCallbackList: " + n + ":"
                    + i + ": " + sIFastSwitchListenerRemoteCallbackList.getBroadcastItem(i));
            sIFastSwitchListenerRemoteCallbackList.unregister(
                    sIFastSwitchListenerRemoteCallbackList.getBroadcastItem(i));
        }
        sIFastSwitchListenerRemoteCallbackList.finishBroadcast();
        sPidNetTypes.clear();
        sPidVersions.clear();
        sPidIfNames.clear();
        sPidUids.clear();
        sPidListeners.clear();
        sPidOperFlag.clear();
        sListenerPids.clear();
        sListenerVersions.clear();
        sListenerSource.clear();
     }

    /* NPSFastSwitch constructor */
    private NPSFastSwitch(Context context) {
        mContext = context;
    }

    /**
     * get NPSFastSwitch instance, it is a singleton istance.
     *
     * @param context a Context.
     * @return NPSFastSwitch service.
     * @hide
     */
    public static NPSFastSwitch getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sNPSFastSwitchLock) {
                if (sInstance == null) {
                    sInstance = new NPSFastSwitch(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Init Singleton listener.
     * Because NativeFastSwitch is a singleton service, so it is ok that we only register one
     * native listener for all uplayer listeners.
     *
     * @hide
     */
    public static void init() {
        if (!sInited) {
            // To avoid deadlock between sIFastSwitchListenerLock and sNFSServiceLock
            // Changing the order of the below two sentences will cause deadlock.
            NPSFastSwitchService.addNativeFastSwitchServiceMonitor(sNPSSM);
            boolean ret = NPSFastSwitchService.addFastSwitchListener(sNFSSL);
            if (ret) {
                sInited = true;
                logi("[FastSwitch]: init done");
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
     * In fact, NPM mustn't add more than one listener by design. But each "sdk" user adds its own.
     *
     * @param listener to listen FastSwitch related information.
     * @param b Bundle object.
     * @hide
     */
    public void addFastSwitchListener(IFastSwitchListener listener, Bundle b) {
        synchronized (sIFastSwitchListenerLock) {
            init();
            int size = sIFastSwitchListenerRemoteCallbackList.getRegisteredCallbackCount();
            String pid = "";
            String source = "";
            if (b != null) {
                pid = b.getString(FastSwitchInfo.BK_APK_PID);
                source = b.getString(FastSwitchInfo.BK_REQ_SOURCE);
            } else {
                loge("[FastSwitch]: addFastSwitchListener, bad caller");
            }
            logd("[FastSwitch]: addFastSwitchListener, total: " + size + ", " + listener + ", "
                    + pid + ", " + source);
            if (size >= IFASTSWITCH_LISTENER_MAX) {
                loge("[FastSwitch]: listener users >= 100, remove all old elements");
                removeAllIFastSwitchListenerFromRemoteCallbackList();
            }
            if (listener != null) {
                if (!(TextUtils.isEmpty(pid)) && !(TextUtils.isEmpty(source))) {
                    if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_S)) {
                        sPidListeners.put(pid, listener);
                        sPidOperFlag.put(pid, FLAG_INIT_MONITOR);
                        sListenerPids.put(listener, pid);
                        sListenerSource.put(listener, source);
                    } else {
                        logd("[FastSwitch]: not catch " + pid + " from " + source);
                    }
                } else {
                    loge("[FastSwitch]: imperfect listener added, " + pid + ", " + source);
                }
                sIFastSwitchListenerRemoteCallbackList.register(listener, pid);
            }
        }
    }

    /**
     * Notify NOS server screen state when state changed.
     *
     * @param isOn true for on, false for off.
     * @hide
     */
    public void notifyScreenState(boolean isOn) {
        Bundle b = new Bundle();
        b.putString(FastSwitchInfo.BK_SCR_STA, (isOn ? FastSwitchInfo.BV_SCR_STA_ON
                : FastSwitchInfo.BV_SCR_STA_OFF));
        FastSwitchInfo info = new FastSwitchInfo(FastSwitchInfo.NETWORK_TYPE_UNKNOWN,
                FastSwitchInfo.ACTION_NF_SCR_STA, b);
        configFastSwitchInfo(info);
    }

    /**
     * Callback by AmsExtImpl.java when APK Activity switches between foreground (resumed state
     * with focus) and background (paused/stopped/destoryed state).
     *
     * Here will try doing foolproof mechanism for low power to avoid costing power when the user
     * (APK) goes to background without calling pauseMonitor or stopMonitor.
     *
     * Only APKs with source type "sdk" are applied now because only info of APKs with "sdk" source
     * type is recorded by cacheSDKListenerInfo() in NPS layer.
     *
     * APKs with source type "platform" should be tried doing foolproof mechanism for low power in
     * NPM layer like this function when the user (APK) goes to background without calling
     * pauseMonitor or stopMonitor.
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
        int act = FastSwitchInfo.ACTION_REQ_BASE;
        int opFlag = FLAG_INIT_MONITOR;
        Bundle b = null;
        FastSwitchInfo info = null;
        synchronized (sIFastSwitchListenerLock) {
            String spid = String.valueOf(pid);
            // Make sure that we only process nos sdk user to reduce power and time consumption.
            if (sPidNetTypes.containsKey(spid)) {
                if (sPidNetTypes.get(spid) != null) {
                    opFlag = (sPidOperFlag.get(spid) != null) ? sPidOperFlag.get(spid) : opFlag;
                    if (isTop && ((opFlag & FLAG_FPP_PAU_MONITOR) > 0)) {
                        act = FastSwitchInfo.ACTION_REQ_RES_MNR;
                    } else if (!isTop && ((opFlag & FLAG_FPP_PAU_MONITOR) == 0)) {
                        act = FastSwitchInfo.ACTION_REQ_PAU_MNR;
                    } else {
                        // ignore duplicated action
                        logi("onActivityStateChanged: isTop: " + isTop + ", pid: " + pid
                                + ", uid: " + uid + ", actName: " + actName + ", pkgName: "
                                + pkgName + ", opFlag: " + opFlag);
                        return;
                    }
                    b = new Bundle();
                    b.putString(FastSwitchInfo.BK_APK_CAP, sPidVersions.get(spid));
                    b.putString(FastSwitchInfo.BK_APK_PID, spid);
                    b.putString(FastSwitchInfo.BK_APK_UID, String.valueOf(uid));
                    b.putString(FastSwitchInfo.BK_IF_NAME, sPidIfNames.get(spid));
                    b.putString(FastSwitchInfo.BK_REQ_SOURCE, FastSwitchInfo.BV_REQ_SOURCE_F);
                    info = new FastSwitchInfo(sPidNetTypes.get(spid), act, b);
                    logd("onActivityStateChanged: isTop: " + isTop + ", pid: " + pid + ", uid: "
                            + uid + ", actName: " + actName + ", pkgName: " + pkgName
                            + ", opFlag: " + opFlag + ", act: " + act);

                } else {
                    loge("[FastSwitch]: onActivityStateChanged why networktype is null?");
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
     * Start monitor for the specified network.
     *
     * @param info networkType in info is necessory.
     * @hide
     */
    public void startMonitor(FastSwitchInfo info) {
        if (!isFastSwitchEnabled()) {
            loge("[FastSwitch]: startMonitor, feature disabled");
            return;
        }
        synchronized (sIFastSwitchListenerLock) {
            cacheSDKListenerInfo(info);
        }
        configFastSwitchInfo(info);
    }

    /**
     * Pause monitor for the specified network.
     *
     * @param info networkType in info is necessory.
     * @hide
     */
    public void pauseMonitor(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Resume monitor for the specified network.
     *
     * @param info networkType in info is necessory.
     * @hide
     */
    public void resumeMonitor(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Stop monitor for the specified network.
     *
     * @param info networkType in info is necessory.
     * @hide
     */
    public void stopMonitor(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Get monitor state of the specified network type.
     *
     * @param info networkType in info is necessory.
     * @return true for monitoring, false for stoped.
     * @hide
     */
    public int getMonitorState(FastSwitchInfo info) {
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Set monitor sensitivity for the specified network.
     * It is update operation for same networkType
     *
     * @param info networkType and Sensitivity in info are necessory.
     * @hide
     */
    public void setMonitorSensitivity(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Get current monitor sensitivity for the specified network.
     *
     * @param info networkType in info is necessory.
     * @return monitor sensitivity for the specified network type.
     * @hide
     */
    public int getMonitorSensitivity(FastSwitchInfo info) {
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Set uid for the monitor to monitor.
     * It is update operation for same networkType
     *
     * @param info networkType and uid in info are necessory.
     * @hide
     */
    public void setUidForMonitor(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Remove uid from the monitor.
     * It is update operation for same networkType
     *
     * @param info networkType and uid in info are necessory.
     * @hide
     */
    public void removeUidFromMonitor(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Remove all uids from the monitor.
     * It is update operation for same networkType
     *
     * @param info networkType in info is necessory.
     * @hide
     */
    public void removeAllUidsFromMonitor(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Set link info for the monitor to monitor.
     * It is update operation for same networkType
     *
     * @param info networkType and link info in info are necessory.
     * @hide
     */
    public void setLinkInfoForMonitor(FastSwitchInfo info) {
        if (validateFiveTuple(info)) {
            configFastSwitchInfo(info);
        }
    }

    /**
     * Remove link info from the monitor.
     * It is update operation for same networkType
     *
     * @param info networkType and link info in info are necessory.
     * @hide
     */
    public void removeLinkInfoFromMonitor(FastSwitchInfo info) {
        if (validateFiveTuple(info)) {
            configFastSwitchInfo(info);
        }
    }

    /**
     * Remove all link info from the monitor.
     * It is update operation for same networkType
     *
     * @param info networkType in info is necessory.
     * @hide
     */
    public void removeAllLinkInfoFromMonitor(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Get local link quality of the specified network type.
     *
     * @param info networkType in info is necessory.
     * @return local link quality for the specified network type.
     * @hide
     */
    public int getLocalLinkQuality(FastSwitchInfo info) {
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Get full link quality of the specified network type.
     *
     * @param info networkType in info is necessory.
     * @return full link quality for the specified network type.
     * @hide
     */
    public int getFullLinkQuality(FastSwitchInfo info) {
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Get signal strength level of the specified network type.
     *
     * @param info networkType in info is necessory.
     * @return signal strength for the specified network type.
     * @hide
     */
    public int getSignalStrength(FastSwitchInfo info) {
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Get signal strength level of the specified network type.
     *
     * @param info networkType in info is necessory.
     * @return signal strength for the specified network type.
     * @hide
     */
    public int getNOSServerCapability(FastSwitchInfo info) {
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Set configure information.
     * This is used to transfer user feedback or configure info to native service.
     *
     * @param info networkType in info is necessory.
     * @hide
     */
    public void setCfgParm(FastSwitchInfo info) {
        configFastSwitchInfo(info);
    }

    /**
     * Get configure information.
     * This is used to query info from native service.
     *
     * @param info networkType in info is necessory.
     * @return value of the parameter.
     * @hide
     */
    public int getCfgParm(FastSwitchInfo info) {
        return configFastSwitchInfoWithIntResult(info);
    }

    /**
     * Enable or disable the FastSwitch.
     * Format {operationNumber}. -1 for disable, 1 for enable.
     * sample: int[] ops = {1}.
     *
     * @param info to be operated.
     * @return 0 for success, -1 for fail.
     * @hide
     */
    public int setFeatureEnabled(FastSwitchInfo info) {
        Bundle b = info.getBundle();
        int action = info.getAction();

        if (b == null || action != FastSwitchInfo.ACTION_REQ_FEA_OPS) {
            loge("[FastSwitch]: bundle is null or wrong act " + action + " for feature switch");
            return -1;
        }

        String source = b.getString(FastSwitchInfo.BK_REQ_SOURCE);
        if (TextUtils.isEmpty(source) || !source.equals(FastSwitchInfo.BV_REQ_SOURCE_P)) {
            // non-platform caller cannot do this operation.
            loge("[FastSwitch]: not allow feature switch because error source info: " + source);
            return -1;
        }

        String ops = b.getString(FastSwitchInfo.BK_FEATURE_CTRL);
        if (TextUtils.isEmpty(ops)) {
            // no operation to do
            loge("[FastSwitch]: no operation to do: " + ops);
            return -1;
        }

        if (!ops.equals("1") && !ops.equals("-1")) {
            loge("[FastSwitch]: wrong operation to do: " + ops);
            return -1;
        }

        try {
            SystemProperties.set(FEATURE_STATE_PROPERTY, ops);
            logd("[FastSwitch]: " + (ops.equals("1")  ? "enable" : "disable") + " FastSwitch");
            return 0;
        } catch (Exception e) {
            // avoid RuntimeException affects whole target because this lives in system_server.
            loge("[FastSwitch]: " + (ops.equals("1")  ? "enable" : "disable") + " FastSwitch" + e);
        }
        return -1;
    }

    /**
     * Query status of the FastSwitch.
     *
     * @param info to be operated.
     * @return 0 for enable, -1 for disable.
     * @hide
     */
    public int isFeatureEnabled(FastSwitchInfo info) {
        int action = info.getAction();

        if (action != FastSwitchInfo.ACTION_REQ_FEA_OPS) {
            loge("[FastSwitch]: Wrong act " + action + " for query feature status");
            return -1;
        }

        return isFastSwitchEnabled() ? 0 : -1;
    }

    /**
     * Check status of the FastSwitch.
     *
     * Initializion and start operations are prohibited to new user after FastSwitch disabled.
     * All other operations are allowed to support running user after FastSwitch disabled.
     *
     * @return true for enable, false for disable.
     * @hide
     */
    private boolean isFastSwitchEnabled() {
        String ret = null;
        try {
            ret = SystemProperties.get(FEATURE_STATE_PROPERTY, "1");
            if (Integer.valueOf(ret) == 1) {
                logd("[FastSwitch]: Feature is available");
                return true;
            } else {
                logd("[FastSwitch]: Feature is unavailable");
            }
        } catch (NumberFormatException e) {
            loge("[FastSwitch]: Feature is unavailable, wrong flags " + ret + " e: " + e);
        } catch (Exception e) {
            // avoid RuntimeException affects whole target because this lives in system_server.
            loge("[FastSwitch]: Feature is unavailable, e: " + e);
        }
        return false;
    }

    /**
     * Provide current AIDL interface version.
     *
     * @return AIDL interface version
     * @hide
     */
    public int getInterfaceVersion() {
        if (!isFastSwitchEnabled()) {
            loge("[FastSwitch]: getInterfaceVersion, feature disabled");
            return 0;
        }
        return NPSFastSwitchService.getInterfaceVersion();
    }

    /**
     * Provide current AIDL interface hash.
     *
     * @return AIDL interface hash
     * @hide
     */
    public String getInterfaceHash() {
        return NPSFastSwitchService.getInterfaceHash();
    }

    /**
     * validateFiveTuple to check validation for five tuple.
     *
     * @param info action in info is necessory.
     * @return true for valid, false for invalid.
     * @hide
     */
    private boolean validateFiveTuple(FastSwitchInfo info) {
        if (info.getAction() != FastSwitchInfo.ACTION_REQ_SET_LIN
                && info.getAction() != FastSwitchInfo.ACTION_REQ_REM_LIN) {
            loge("[FastSwitch]: wrong usage for action: " + info.getAction());
            return false;
        }
        if (info.getSrcIp() == null || info.getDstIp() == null
                || (info.getProto() != 1 && info.getProto() != 2 && info.getProto() != -1)
                || !NPSAddress.isIpPairValid(info.getSrcIp(), info.getDstIp(),
                info.getSrcPort(), info.getDstPort())) {
            loge("[FastSwitch]: invalid parameter, sip/dip/sp/dp/port");
            return false;
        }
        return true;
    }

    /**
     * Update new flag for current action.
     *
     * Note: Callers must hold sIFastSwitchListenerLock.
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

        if (!source.equals(FastSwitchInfo.BV_REQ_SOURCE_S)
                && !source.equals(FastSwitchInfo.BV_REQ_SOURCE_F)) {
            // Only protect "sdk" user, "platform" user is protected in NPM layer.
            logd("[FastSwitch]: non sdk/fwservice source");
            return -1;
        }

        int unset = FLAG_INIT_MONITOR;
        int set = FLAG_INIT_MONITOR;
        switch (info.getAction()) {
        case FastSwitchInfo.ACTION_REQ_STR_MNR:
            if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_S)) {
                // Only FastSwitchInfo.BV_REQ_SOURCE_S can do start action
                unset = 0x7fffffff;
                set = FLAG_APK_STR_MONITOR;
            } else {
                loge("[FastSwitch]: wrong usage, fwservice shouldn't start monitor");
            }
            break;
        case FastSwitchInfo.ACTION_REQ_RES_MNR:
            if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_S)) {
                unset = FLAG_APK_STR_MONITOR | FLAG_APK_PAU_MONITOR | FLAG_APK_STP_MONITOR;
                set = FLAG_APK_RES_MONITOR;
            } else {
                unset = FLAG_FPP_PAU_MONITOR;
                set = FLAG_FPP_RES_MONITOR;
            }
            break;
        case FastSwitchInfo.ACTION_REQ_PAU_MNR:
            if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_S)) {
                unset = FLAG_APK_STR_MONITOR | FLAG_APK_RES_MONITOR | FLAG_APK_STP_MONITOR;
                set = FLAG_APK_PAU_MONITOR;
            } else {
                unset = FLAG_FPP_RES_MONITOR;
                set = FLAG_FPP_PAU_MONITOR;
            }
            break;
        case FastSwitchInfo.ACTION_REQ_STP_MNR:
            if (source.equals(FastSwitchInfo.BV_REQ_SOURCE_S)) {
                unset = FLAG_APK_STR_MONITOR | FLAG_APK_RES_MONITOR | FLAG_APK_PAU_MONITOR;
                set = FLAG_APK_STP_MONITOR;
            } else {
                // fwservice stop monitor is crash protection. it will clear all info later.
                logd("[FastSwitch]: ignore fwservice stop for crashed \"sdk\" APK");
                return -1;
            }
            break;
        default:
            logd("[FastSwitch]: action " + info.getAction() + " isn't to reset flag");
            return -1;
        }

        String pid = b.getString(FastSwitchInfo.BK_APK_PID);
        if (TextUtils.isEmpty(pid) || (sPidOperFlag.get(pid) == null)) {
            loge("[FastSwitch]: no pid info or non-sdk user to reset flag");
            return -1;
        }

        try {
            int oflag = sPidOperFlag.get(pid);
            int nflag = oflag & ~unset | set;
            sPidOperFlag.put(pid, nflag);
            return oflag;
        } catch (NullPointerException e) {
            loge("[FastSwitch]: wrong usage, pid " + pid + ", e " + e);
            return -1;
        }
    }

    /**
     * cacheSDKListenerInfo to handle APK (sdk) crash, only for startMonitor.
     *
     * carsh APK from "platform" should be protected by NPM, see onCallbackDied
     * in NetworkPolicyManager.java.
     *
     * Needn't remove them in stopMonitor because caller is alive and listener is on.
     * Also needn't remove them for startMonitor fail because the same caller only owns one
     * set of records based on ArrayMap property. Recycling is enough when destroying APK.
     *
     * Note: Callers must hold sIFastSwitchListenerLock.
     *
     * @param info action in info is necessory.
     * @hide
     */
    private void cacheSDKListenerInfo(FastSwitchInfo info) {
        if (info.getAction() != FastSwitchInfo.ACTION_REQ_STR_MNR) {
            loge("[FastSwitch]: cacheSDKListenerInfo wrong usage for action: " + info.getAction());
            return;
        }

        Bundle b = info.getBundle();
        if (b != null) {
            String source = "";
            String pid = "";
            if (b.containsKey(FastSwitchInfo.BK_REQ_SOURCE)) {
                if (b.getString(FastSwitchInfo.BK_REQ_SOURCE) == null) {
                    loge("[FastSwitch]: reqestsource in bundle, but no value");
                    // For crash, no rule to process, so return
                    return;
                } else {
                    source = b.getString(FastSwitchInfo.BK_REQ_SOURCE);
                    logi("[FastSwitch]: reqestsource in bundle, source: " + source);
                }
            }
            if (b.containsKey(FastSwitchInfo.BK_APK_PID)) {
                if (b.getString(FastSwitchInfo.BK_APK_PID) == null) {
                    loge("[FastSwitch]: apkpid in bundle, but no value");
                    // For crash, no rule to process, so return
                    return;
                } else {
                    pid = b.getString(FastSwitchInfo.BK_APK_PID);
                    logi("[FastSwitch]: apkpid in bundle, pid: " + pid);
                }
            }
            if (!(TextUtils.isEmpty(pid)) && !(TextUtils.isEmpty(source))) {
                IFastSwitchListener l = sPidListeners.get(pid);
                String v;
                if (l == null || (l != null && !source.equals(sListenerSource.get(l)))
                        || !source.equals(FastSwitchInfo.BV_REQ_SOURCE_S)) {
                    loge("[FastSwitch]: skip cache, l: " + l + ", pid: " + pid + ", source: "
                            + source + ", ls: " + sListenerSource.get(l));
                    // For crash, no rule to process, so return
                    // l == null: no listener, should be from "platfrom" source and not the first.
                    // l != null && souce != ls: skip different source
                    // souce != "sdk": only process sdk apk
                    return;
                }

                if (b.containsKey(FastSwitchInfo.BK_APK_CAP)) {
                    if (b.getString(FastSwitchInfo.BK_APK_CAP) == null) {
                        loge("[FastSwitch]: apkcapability in bundle, but no value");
                    } else {
                        v = b.getString(FastSwitchInfo.BK_APK_CAP);
                        sListenerVersions.put(l, v);
                        sPidVersions.put(pid, v);
                        logi("[FastSwitch]: apkcapability in bundle, cap: " + v);
                    }
                }
                if (b.containsKey(FastSwitchInfo.BK_IF_NAME)) {
                    if (b.getString(FastSwitchInfo.BK_IF_NAME) == null) {
                        loge("[FastSwitch]: interfacename in bundle, but no value");
                    } else {
                        v = b.getString(FastSwitchInfo.BK_IF_NAME);
                        sPidIfNames.put(pid, v);
                        logi("[FastSwitch]: interfacename in bundle, name: " + v);
                    }
                }
                if (b.containsKey(FastSwitchInfo.BK_APK_UID)) {
                    if (b.getString(FastSwitchInfo.BK_APK_UID) == null) {
                        loge("[FastSwitch]: apkuid in bundle, but no value");
                    } else {
                        v = b.getString(FastSwitchInfo.BK_APK_UID);
                        sPidUids.put(pid, v);
                        logi("[FastSwitch]: apkuid in bundle, uid: " + v);
                    }
                }
                sPidNetTypes.put(pid, info.getNetworkType());
                logi("[FastSwitch]: networktype, type: " + info.getNetworkType());
            }
        }
    }

    /**
     * Convert FastSwitchInfo to NativeFastSwitchInfo and send request.
     *
     * Used to indicate more information.
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
     * @param info networkType in info is necessory.
     * @return NativeFastSwitchInfo instance.
     * @hide
     */
    private NativeFastSwitchInfo convertJavaInfoToNative(FastSwitchInfo info) {
        NativeFastSwitchInfo i = new NativeFastSwitchInfo();
        i.mAction = info.getAction();
        i.mNetworkType = info.getNetworkType();
        i.mMonitorState = info.getMonitorState();
        i.mMonitorSensitivity = info.getMonitorSensitivity();
        i.mLocalLinkQuality = info.getLocalLinkQuality();
        i.mFullLinkQuality = info.getFullLinkQuality();
        i.mSignalStrength = info.getSignalStrength();
        i.mUid = info.getUid();
        i.mSrcIp = (info.getSrcIp() != null) ? info.getSrcIp() : "";
        i.mDstIp = (info.getSrcIp() != null) ? info.getSrcIp() : "";
        i.mSrcPort = info.getSrcPort();
        i.mDstPort = info.getDstPort();
        i.mProto = info.getProto();
        Bundle b = info.getBundle();
        String bstr = "";
        if (b != null) {
            if (b.containsKey(FastSwitchInfo.BK_APK_CAP)) {
                if (b.getString(FastSwitchInfo.BK_APK_CAP) == null) {
                    loge("[FastSwitch]: apkcapability in bundle, but value is null");
                } else {
                    bstr += FastSwitchInfo.BK_APK_CAP + ":"
                            + b.getString(FastSwitchInfo.BK_APK_CAP);
                    logi("[FastSwitch]: apkcapability in bundle, bstr: " + bstr);
                }
            }
            if (b.containsKey(FastSwitchInfo.BK_APK_PID)) {
                if (b.getString(FastSwitchInfo.BK_APK_PID) == null) {
                    loge("[FastSwitch]: apkpid in bundle, but value is null");
                } else {
                    bstr += ((TextUtils.isEmpty(bstr)) ? "" : ",") + FastSwitchInfo.BK_APK_PID
                             + ":" + b.getString(FastSwitchInfo.BK_APK_PID);
                    logi("[FastSwitch]: apkpid in bundle, bstr: " + bstr);
                }
            }
            if (b.containsKey(FastSwitchInfo.BK_IF_NAME)) {
                if (b.getString(FastSwitchInfo.BK_IF_NAME) == null) {
                    loge("[FastSwitch]: interfacename in bundle, but value is null");
                } else {
                    bstr += ((TextUtils.isEmpty(bstr)) ? "" : ",") + FastSwitchInfo.BK_IF_NAME
                            + ":" + b.getString(FastSwitchInfo.BK_IF_NAME);
                    logi("[FastSwitch]: interfacename in bundle, bstr: " + bstr);
                }
            }
            if (b.containsKey(FastSwitchInfo.BK_REQ_SOURCE)) {
                if (b.getString(FastSwitchInfo.BK_REQ_SOURCE) == null) {
                    loge("[FastSwitch]: reqestsource in bundle, but value is null");
                } else {
                    bstr += ((TextUtils.isEmpty(bstr)) ? "" : ",") + FastSwitchInfo.BK_REQ_SOURCE
                            + ":" + b.getString(FastSwitchInfo.BK_REQ_SOURCE);
                    logi("[FastSwitch]: reqestsource in bundle, bstr: " + bstr);
                }
            }
            if (b.containsKey(FastSwitchInfo.BK_IF_INDEX)) {
                if (b.getString(FastSwitchInfo.BK_IF_INDEX) == null) {
                    loge("[FastSwitch]: interfaceindex in bundle, but value is null");
                } else {
                    bstr += ((TextUtils.isEmpty(bstr)) ? "" : ",") + FastSwitchInfo.BK_IF_INDEX
                            + ":" + b.getString(FastSwitchInfo.BK_IF_INDEX);
                    logi("[FastSwitch]: interfaceindex in bundle, bstr: " + bstr);
                }
            }
            if (b.containsKey(FastSwitchInfo.BK_APK_UID)) {
                if (b.getString(FastSwitchInfo.BK_APK_UID) == null) {
                    loge("[FastSwitch]: apkuid in bundle, but value is null");
                } else {
                    bstr += ((TextUtils.isEmpty(bstr)) ? "" : ",") + FastSwitchInfo.BK_APK_UID
                            + ":" + b.getString(FastSwitchInfo.BK_APK_UID);
                    logi("[FastSwitch]: apkuid in bundle, bstr: " + bstr);
                }
            }
            if (b.containsKey(FastSwitchInfo.BK_NW_TYPE)) {
                if (b.getString(FastSwitchInfo.BK_NW_TYPE) == null) {
                    loge("[FastSwitch]: networktype in bundle, but value is null");
                } else {
                    bstr += ((TextUtils.isEmpty(bstr)) ? "" : ",") + FastSwitchInfo.BK_NW_TYPE
                            + ":" + b.getString(FastSwitchInfo.BK_NW_TYPE);
                    logi("[FastSwitch]: networktype in bundle, bstr: " + bstr);
                }
            }
            if (b.containsKey(FastSwitchInfo.BK_SCR_STA)) {
                if (b.getString(FastSwitchInfo.BK_SCR_STA) == null) {
                    loge("[FastSwitch]: screenstate in bundle, but value is null");
                } else {
                    bstr += ((TextUtils.isEmpty(bstr)) ? "" : ",") + FastSwitchInfo.BK_SCR_STA
                            + ":" + b.getString(FastSwitchInfo.BK_SCR_STA);
                    logi("[FastSwitch]: screenstate in bundle, bstr: " + bstr);
                }
            }
            for (Map.Entry<String, Integer> entry : FastSwitchInfo.MAP_BKS_SUBACT.entrySet()) {
                String kstr = entry.getKey();
                if (b.containsKey(kstr)) {
                    if (b.getString(kstr) == null) {
                        loge("[FastSwitch]: " + kstr + " in bundle, but value is null");
                    } else {
                        bstr += ((TextUtils.isEmpty(bstr)) ? "" : ",") + kstr + ":"
                                + b.getString(kstr);
                        logi("[FastSwitch]: " + kstr + " in bundle, bstr: " + bstr);
                    }
                }
            }
        }
        i.mStringValues = transferToStringArray(bstr);
        return i;
    }

    /**
     * configFastSwitchInfo to native.
     *
     * @param info networkType in info is necessory.
     * @hide
     */
    private void configFastSwitchInfo(FastSwitchInfo info) {
        synchronized (sIFastSwitchListenerLock) {
            resetOperFlag(info);
        }
        NativeFastSwitchInfo i = convertJavaInfoToNative(info);
        NPSFastSwitchService.configFastSwitchInfo(i);
    }

    /**
     * configFastSwitchInfo to native.
     *
     * @param info networkType in info is necessory.
     * @return configFastSwitchInfo result.
     * @hide
     */
    private int configFastSwitchInfoWithIntResult(FastSwitchInfo info) {
        NativeFastSwitchInfo i = convertJavaInfoToNative(info);
        return NPSFastSwitchService.configFastSwitchInfoWithIntResult(i);
    }

    /**
     * covert string to string array.
     *
     * @param str parameters string.
     * @return parameters array or empty string array.
     * @hide
     */
    private String[] transferToStringArray(String str) {
        return (TextUtils.isEmpty(str)) ? new String[0] : str.split(",");
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
