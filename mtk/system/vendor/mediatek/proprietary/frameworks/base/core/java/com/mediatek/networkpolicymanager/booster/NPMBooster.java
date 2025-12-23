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

package com.mediatek.networkpolicymanager.booster;

import android.content.Context;

import android.os.RemoteException;
import android.util.Log;

import com.mediatek.networkpolicymanager.INetworkPolicyService;
import com.mediatek.networkpolicymanager.NetworkPolicyManager;

/** NPMBooster exports APK booster APIs. */
public class NPMBooster {
    private static final String TAG = "NPMBooster";

    /* Singleton NPMBooster instace */
    private volatile static NPMBooster sInstance = null;
    private static final Object sNPMBoosterLock = new Object();

    private Context mContext = null;

    /** NPMBooster constructor. */
    private NPMBooster(Context context) {
        mContext = context;
    }

    /**
     * get NPMBooster instance, it is a singleton istance.
     *
     * @param context a context.
     * @return NPMBooster service.
     * @hide
     */
    public static NPMBooster getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sNPMBoosterLock) {
                if (sInstance == null) {
                    sInstance = new NPMBooster(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Configure BoosterInfo to enable or disable booster for specified application.
     *
     * @param info to be config.
     * @param service that encapsulates booster feature implement.
     * @return true for success, false for fail.
     *
     * @hide
     */
    public boolean configBoosterInfo(BoosterInfo info, INetworkPolicyService service) {
        boolean status = false;

        if (!isValid(info, service)) {
            return false;
        }

        try {
            status = service.configBoosterInfo(info);
        } catch (RemoteException e) {
            loge("[Booster]: configBoosterInfo fail: " + e);
        }

        return status;
    }

    /**
     * Check whether BoosterInfo and INetworkPolicyService are not null.
     *
     * @param info to be checked.
     * @param service to be checked.
     * @return true for valid, false for invalid.
     */
    private boolean isValid(BoosterInfo info, INetworkPolicyService service) {
        if (info == null || service == null) {
            loge(true, "[Booster]: info is null:" + (info == null) + ", service is null:"
                    + (service == null));
            return false;
        }

        if ((info.getGroup() > BoosterInfo.BOOSTER_GROUP_MAX)
                || (info.getGroup() <= BoosterInfo.BOOSTER_GROUP_BASE)) {
            loge(true, "[Booster]: Unsupported group " + info.getGroup());
            return false;
        }
        return true;
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
     * Encapsulated d level log function.
     */
    private static void logd(String message) {
        Log.d(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated i level log function.
     */
    private static void logi(String message) {
        Log.i(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated e level log function.
     */
    private static void loge(String message) {
        Log.e(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated e level log function.
     * NOTE: Only used in encapsulation check function
     */
    private static void loge(boolean moreDepth, String message) {
        Log.e(TAG, "JXNPS: " + getMethodName(moreDepth) + ":" + message);
    }
}
