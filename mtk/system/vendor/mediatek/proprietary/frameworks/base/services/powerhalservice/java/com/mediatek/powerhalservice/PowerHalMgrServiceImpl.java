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
package com.mediatek.powerhalservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.Binder;
import android.util.Log;
import com.android.server.SystemService;
import com.mediatek.powerhalmgr.IPowerHalMgr;
import com.mediatek.powerhalwrapper.PowerHalWrapper;
// M: DPP @{
import android.os.IRemoteCallback;
// Booster feature
import com.mediatek.powerhalmgr.BoosterInfo;
import com.mediatek.powerhalmgr.DupLinkInfo;
// @}

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import vendor.mediatek.hardware.power.V2_0.*;
import com.mediatek.boostframework.Performance;

public class PowerHalMgrServiceImpl extends IPowerHalMgr.Stub {
    private final String TAG = "PowerHalMgrServiceImpl";
    private static PowerHalWrapper mPowerHalWrap = null;
    private static int mhandle = 0;
    private static Performance mPerformance = new Performance();

    // M: DPP @{
    private PowerHalWifiMonitor mPowerHalWifiMonitor = null;
    // @}

    private Context mContext = null;

/*
    static {
        System.loadLibrary("powerhalmgrserv_jni");
    }
*/
    public PowerHalMgrServiceImpl(Context context){
        mPowerHalWrap = PowerHalWrapper.getInstance();
        // M: DataStall @{
        mPowerHalWifiMonitor = new PowerHalWifiMonitor(context);
        // @}
        mContext = context;
    }

    public int scnReg() {
        loge("scnReg not supported");
        return 0;
    }

    public void scnConfig(int handle, int cmd, int param_1,
                                          int param_2, int param_3, int param_4) {
        loge("scnConfig not supported");
    }

    public void scnUnreg(int handle) {
        loge("scnUnreg not supported");
    }

    public void scnEnable(int handle, int timeout) {
        loge("scnEnable not supported");
    }

    public void scnDisable(int handle) {
        loge("scnDisable not supported");
    }

    public void scnUltraCfg(int handle, int ultracmd, int param_1,
                                                 int param_2, int param_3, int param_4) {
        loge("scnUltraCfg not supported");
    }

    public void mtkPowerHint(int hint, int data) {
        mPowerHalWrap.mtkPowerHint(hint, data);
    }

    public void mtkCusPowerHint(int hint, int data) {
        mPowerHalWrap.mtkCusPowerHint(hint, data);
    }

    public void getCpuCap() {
        loge("getCpuCap not supported");
    }

    public void getGpuCap() {
        loge("getGpuCap not supported");
    }

    public void getGpuRTInfo() {
        loge("getGpuRTInfo not supported");
    }

    public void getCpuRTInfo() {
        loge("getCpuRTInfo not supported");
    }

    public void UpdateManagementPkt(int type, String packet) {
        mPowerHalWrap.UpdateManagementPkt(type, packet);
    }

    public void setForegroundSports() {
        loge("setForegroundSports not supported");
    }

    public void setSysInfo(int type, String data) {
        loge("setSysInfo not supported");
    }

    // M: DPP @{
    private boolean checkDppPermission() {
        int uid = Binder.getCallingUid();
        if (mPowerHalWrap.getRildCap(uid) == false) {
            logd("checkDppPermission(), no permission");
            return false;
        } else {
            return true;
        }
    }

    public boolean startDuplicatePacketPrediction() {
        loge("startDuplicatePacketPrediction not supported");
        return false;
    }

    public boolean stopDuplicatePacketPrediction() {
        loge("stopDuplicatePacketPrediction not supported");
        return false;
    }

    public boolean isDupPacketPredictionStarted() {
        loge("isDupPacketPredictionStarted not supported");
        return false;
    }

    public boolean registerDuplicatePacketPredictionEvent(IRemoteCallback listener) {
        loge("registerDuplicatePacketPredictionEvent not supported");
        return false;
    }

    public boolean unregisterDuplicatePacketPredictionEvent(IRemoteCallback listener) {
        loge("unregisterDuplicatePacketPredictionEvent not supported");
        return false;
    }

    public boolean updateMultiDuplicatePacketLink(DupLinkInfo[] linkList) {
        loge("updateMultiDuplicatePacketLink not supported");
        return false;
    }
    // @}
    public void setPredictInfo(String pack_name, int uid) {
        String data = pack_name + " " + uid;	
        logd("setPredictInfo:" + data);	
        mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_PREDICT_INFO, data);
      }

    //action: 1, add; 2, delete
    public boolean setPriorityByUid(int action, int uid) {
        String data;
	boolean ret = true;

        log("setPriorityByUid:" + action + uid);
	if (action == 1) {
		data = "SET" + " " + uid;
        	mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_SET_FASTPATH_BY_UID, data);
	}
	else if (action == 2) {
		data = "CLEAR" + " " + uid;
		mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_SET_FASTPATH_BY_UID, data);
	} else {
		loge("[setPriorityByLinkinfo] invalide action" + action);
		ret = false;
	}
	return ret;
    }
    //action: 1, add; 2, delete
    public boolean setPriorityByLinkinfo(int action, DupLinkInfo linkinfo) {

	if (linkinfo == null) {
            return false;
        }

        if (PowerHalAddressUtils.isIpPairValid(linkinfo.getSrcIp(), linkinfo.getDstIp(),
             linkinfo.getSrcPort(), linkinfo.getDstPort()) == false) {
	     loge("[setPriorityByLinkinfo] invalide input paremeters");
             return false;
            }

	    /* format cmd: SET/CLEAR  sip sport dip dport proto */
            StringBuilder sb = new StringBuilder();
	    if (action == 1) {
		sb.append("SET ");
	    } else if (action == 2) {
		sb.append("CLEAR ");
	    } else {
		loge("[setPriorityByLinkinfo] invalide action");
		return false;
	    }
            /* -1 => null */
            String strSrcPort = (linkinfo.getSrcPort() == -1) ? "none" :
                Integer.toString(linkinfo.getSrcPort());
            String dstSrcPort = (linkinfo.getDstPort() == -1) ? "none" :
                Integer.toString(linkinfo.getDstPort());
            String data = linkinfo.getSrcIp() + " " + strSrcPort + " "
                + linkinfo.getDstIp() + " " + dstSrcPort + " ";
            if(linkinfo.getProto() == 1) {
                data += "TCP";
            } else if(linkinfo.getProto() == 2) {
                data += "UDP";
            } else if(linkinfo.getProto() == -1) {
                data += "none";
            } else {
                loge("[setPriorityByLinkinfo] unknown protocol:" + linkinfo.getProto());
                return false;
            }
        sb.append(data);
	String Linkcmd = sb.toString();
        log("setPriorityByLinkinfo:" + Linkcmd);
        mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_SET_FASTPATH_BY_LINKINFO, Linkcmd);
	return true;
    }
   //#type : 1 RULE_TYPE_UID 
   //#type : 2 RULE_TYPE_LINKINFO 
   //#type : 3 RULE_TYPE_ALL
    public boolean flushPriorityRules(int type) {
        String data;
	boolean ret = true;

        log("flushPriorityRules:" + type);

	if (type == 1) {
		data = "UID";
        	mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_CLEAR_FASTPATH_RULES, data);
	}
	else if (type == 2) {
		data = "LINKINFO";
		mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_CLEAR_FASTPATH_RULES, data);
	}else if (type == 3) {
		data = "ALL";
		mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_CLEAR_FASTPATH_RULES, data);
	}else {
		loge("[flushPriorityRules] unknown type:" + type);
		ret = false;
	}
	return ret;
    }

    // APK Booster @{
    /**
     * configure accelerator by UID.
     * @param group which groud command will be executed.
     * @param action which action will be executed.
     * @param uid which uid will be set.
     * @return true for success, false for fail.
     */
    private boolean configBoosterByUid(int group, int action, int uid) {
        String data;
        String packageName = null;

        if ((uid < 0) && (action != BoosterInfo.BOOSTER_ACTION_DEL_BY_UID_ALL)) {
            loge("[Booster]: wrong uid: " + uid + ", group: " + group +  ", action: " + action);
            return false;
        }
        packageName = mContext.getPackageManager().getNameForUid(uid);

        logd("[Booster]: uid: " + uid + ", packageName: " + packageName + ", group: " + group
                + ", action: " + action);

        switch (action) {
            case BoosterInfo.BOOSTER_ACTION_ADD_BY_UID:
                data = "UIDSET_" + group + " " + uid;
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_UID:
                data = "UIDCLEAR_" + group + " " + uid;
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_UID_ALL:
                data = "UIDCLEARALL_" + group;
                break;
            default:
                loge("[Booster]: not support action(" + action + ") for UID");
                return false;
        }

        mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_BOOSTER_CONFIG, data);
        return true;
    }

    /**
     * configure accelerator by Linkinfo.
     * @param info to be config.
     * @return true for success, false for fail.
     */
    private boolean configBoosterByFiveTuple(BoosterInfo info) {
        String data = null;
        String srcPort = null;
        String dstPort = null;
        int group = info.getGroup();
        int action = info.getAction();
        boolean isClearAll = false;

        if ((info.getSrcIp() == null || info.getDstIp() == null
                || !PowerHalAddressUtils.isIpPairValid(info.getSrcIp(), info.getDstIp(),
                info.getSrcPort(), info.getDstPort()))
                && (action != BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO_ALL)) {
            loge("[Booster]: invalid parameter, sip/dip/sp/dp");
            return false;
        }

        switch (action) {
            case BoosterInfo.BOOSTER_ACTION_ADD_BY_LINKINFO:
                data = "LINFOSET_" + group + " ";
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO:
                data = "LINFOCLEAR_" + group + " ";
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO_ALL:
                data = "LINFOCLEARALL_" + group;
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
            data += info.getSrcIp() + " " + srcPort + " " + info.getDstIp() + " " + dstPort + " ";

            /* format cmd: LINFO SET_G/CLEAR_G  sip sport dip dport proto */
            switch (info.getProto()) {
                case 1:
                    data += "TCP";
                    break;
                case 2:
                    data += "UDP";
                    break;
                case -1:
                    data += "none";
                    break;
                default:
                    loge("[Booster] unknown protocol: " + info.getProto());
                    return false;
            }
        }

        logd("[Booster]: 5 tuple cmd: " + data);
        mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_BOOSTER_CONFIG, data);
        return true;
    }

    /**
     * configure accelerator, del all accelerators including UID/LINKINFO.
     * @param group which groud command will be executed.
     * @param action which action will be executed.
     * @return true for success, false for fail.
     */
    private boolean configBoosterRemoveAll(int group, int action) {
        String data = null;

        logd("[Booster]: remove all, group: " + group + ", action: " + action);

        if (action == BoosterInfo.BOOSTER_ACTION_DEL_ALL) {
            data = "REMOVEALL_" + group;
            mPowerHalWrap.setSysInfoAsync(PowerHalWrapper.SETSYS_NETD_BOOSTER_CONFIG, data);
        } else {
            loge("[Booster]: not support action(" + action + ") for remove all");
            return false;
        }
        return true;
    }

    /**
     * Configure BoosterInfo to enable or disable booster for specified application based on
     * specified info.
     * @param info to be config.
     * @return true for success, false for fail.
     */
    public boolean configBoosterInfo(BoosterInfo info) {
        int callingUid = Binder.getCallingUid();
        String packageName = mContext.getPackageManager().getNameForUid(callingUid);
        int group = -1;
        int action = -1;
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
                ret = configBoosterByUid(group, action, info.getUid());
                break;
            case BoosterInfo.BOOSTER_ACTION_ADD_BY_LINKINFO:
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO:
            case BoosterInfo.BOOSTER_ACTION_DEL_BY_LINKINFO_ALL:
                ret = configBoosterByFiveTuple(info);
                break;
            case BoosterInfo.BOOSTER_ACTION_DEL_ALL:
                ret = configBoosterRemoveAll(group, action);
                break;
            default:
                loge("[Booster]: not support action: " + action);
                ret = false;
        }
        return ret;
    }
    // @}

    public int perfLockAcquire(int handle, int duration, int[] list) {
        int i;

        if (list.length % 2 != 0)
            return -1;

        /* protection for IPowerHalMgr.aidl */
        //if (duration == 0 || duration > 600000) {
        //    loge("perfLockAcquire could not more than 600 sec. (dur:" + duration + ")");
        //    return -1;
        //}

        logd("perfLockAcquire hdl:" + handle + " dur:" + duration + " len:" + list.length);
        for (i=0; i<list.length; i+=2) {
            logd("perfLockAcquire " + i + " id:" +
                Integer.toHexString(list[i]) + " value:" + list[i+1]);
        }
        return mPowerHalWrap.perfLockAcquire(handle, duration, list);
    }

    public void perfLockRelease(int handle) {
        mPowerHalWrap.perfLockRelease(handle);
    }

    public int querySysInfo(int cmd, int param) {
         return mPowerHalWrap.querySysInfo(cmd, param);
     }

    public int perfCusLockHint(int hint, int duration) {
        return mPowerHalWrap.perfCusLockHint(hint, duration);
    }

    public int setSysInfoSync(int type, String data) {
        loge("setSysInfoSync not supported");
        return 0;
    }

    private void log(String info) {
        Log.i(TAG, info + " ");
    }

    private void logd(String info) {
        Log.d(TAG, info + " ");
    }

    private void loge(String info) {
        Log.e(TAG, "ERR: " + info + " ");
    }
}
