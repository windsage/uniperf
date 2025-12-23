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

import android.os.Bundle;

/* Aidl exported in SDK */
oneway interface INOSListener {
    /**
     * const int * will be compiled into public static final int * in a java file.
     * Example: int a = INOSListener.MONITOR_STATE_IDLE;
     */

    /**
     * Network type.
     * Mapping FastSwitch network type, they should be consistent.
     */
    const int NETWORK_TYPE_BASE = 1;
    const int NETWORK_TYPE_UNKNOWN              = NETWORK_TYPE_BASE << 0;
    const int NETWORK_TYPE_MOBILE               = NETWORK_TYPE_BASE << 1;
    const int NETWORK_TYPE_WIFI                 = NETWORK_TYPE_BASE << 2;
    const int NETWORK_TYPE_BLUETOOTH            = NETWORK_TYPE_BASE << 3;
    const int NETWORK_TYPE_ETHERNET             = NETWORK_TYPE_BASE << 4;
    const int NETWORK_TYPE_MAX                  = NETWORK_TYPE_BASE << 5;

    /**
     * Monitor state, pause will enter IDLE, resume will enter RUNNING.
     * Mapping FastSwitch STATE, they should be consistent.
     */
    const int MONITOR_STATE_BASE = 0;
    const int MONITOR_STATE_UNKNOWN             = MONITOR_STATE_BASE + 1;
    const int MONITOR_STATE_NOTSTART            = MONITOR_STATE_BASE + 2;
    const int MONITOR_STATE_RUNNING             = MONITOR_STATE_BASE + 3;
    const int MONITOR_STATE_IDLE                = MONITOR_STATE_BASE + 4;
    const int MONITOR_STATE_NUM = MONITOR_STATE_IDLE;

    /**
     * Monitor Sensitivity.
     * Mapping FastSwitch SENSITIVITY, they should be consistent.
     */
    const int SENSITIVITY_LEVEL_BASE = 0;
    const int SENSITIVITY_LEVEL_UNKNOWN         = SENSITIVITY_LEVEL_BASE + 1;
    const int SENSITIVITY_LEVEL_LOW             = SENSITIVITY_LEVEL_BASE + 2;
    const int SENSITIVITY_LEVEL_MEDIUM          = SENSITIVITY_LEVEL_BASE + 3;
    const int SENSITIVITY_LEVEL_HIGH            = SENSITIVITY_LEVEL_BASE + 4;
    const int SENSITIVITY_LEVEL_NUM = SENSITIVITY_LEVEL_HIGH;

    /**
     * Local/Full link quality.
     * Mapping FastSwitch Local/Full link quality, they should be consistent.
     */
    const int LINKQUALITY_LEVEL_BASE = 0;
    const int LINKQUALITY_LEVEL_UNKNOWN         = LINKQUALITY_LEVEL_BASE + 1;
    const int LINKQUALITY_LEVEL_WORSE           = LINKQUALITY_LEVEL_BASE + 2;
    const int LINKQUALITY_LEVEL_POOR            = LINKQUALITY_LEVEL_BASE + 3;
    const int LINKQUALITY_LEVEL_MODERATE        = LINKQUALITY_LEVEL_BASE + 4;
    const int LINKQUALITY_LEVEL_GOOD            = LINKQUALITY_LEVEL_BASE + 5;
    const int LINKQUALITY_LEVEL_GREAT           = LINKQUALITY_LEVEL_BASE + 6;
    const int LINKQUALITY_LEVEL_NUM = LINKQUALITY_LEVEL_GREAT;

    /**
     * Signal strength level.
     */
    const int SIGNAL_STRENGTH_BASE = 0;
    const int SIGNAL_STRENGTH_NONE_OR_UNKNOWN   = SIGNAL_STRENGTH_BASE + 1;
    const int SIGNAL_STRENGTH_WORSE             = SIGNAL_STRENGTH_BASE + 2;
    const int SIGNAL_STRENGTH_POOR              = SIGNAL_STRENGTH_BASE + 3;
    const int SIGNAL_STRENGTH_MODERATE          = SIGNAL_STRENGTH_BASE + 4;
    const int SIGNAL_STRENGTH_GOOD              = SIGNAL_STRENGTH_BASE + 5;
    const int SIGNAL_STRENGTH_GREAT             = SIGNAL_STRENGTH_BASE + 6;
    const int SIGNAL_STRENGTH_NUM = SIGNAL_STRENGTH_GREAT;

    /**
     * Callback functions. Bundle is for extension. Baisc parameters aren't put into Bundle.
     */
    void onMonitorStateChanged(in int networkType, in int state, in Bundle b) = 0;
    void onMonitorSensitivityChanged(in int networkType, in int sensitivity, in Bundle b) = 1;
    void onMonitorLocalLinkQualityAlarm(in int networkType, in int level, in Bundle b) = 2;
    void onMonitorFullLinkQualityAlarm(in int networkType, in int level, in Bundle b) = 3;
    void onMonitorSignalStrengthAlarm(in int networkType, in int level, in Bundle b) = 4;
    void onLinstenerStateChanged(in boolean active, in Bundle b) = 5;
    void onServiceDie() = 6;
    void onCfgParmChanged(in int networkType, in Bundle b) = 7;
}

