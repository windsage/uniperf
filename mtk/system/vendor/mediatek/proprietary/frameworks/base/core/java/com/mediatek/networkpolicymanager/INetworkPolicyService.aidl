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

import com.mediatek.networkpolicymanager.booster.BoosterInfo;
import com.mediatek.networkpolicymanager.fastswitch.FastSwitchInfo;
import com.mediatek.networkpolicymanager.fastswitch.IFastSwitchListener;

/**
 * Note: This feature has SDK for 3rd party customer.
 *
 *       So:
 *       You mustn't modify the current APIs and their numbers.
 *       You can add new API.
 *       You must set new number for your newly added API.
 *       The newly added API number must be increasing.
 *       The newly added API number mustn't duplicate the existing one.
 *       You can delete the existed API, but you must keep the API number for it.
 *       The newly added API number mustn't duplicate the keeped number for deleted API.
 *
 *       For delete case, strongly suggest you update SDK and publish to customer in time
 *       because delete case is not backward compatibility.
 *
 *       Special note:
 *           please add mark to describe for your deleted API, especially last one.
 *
 *       Example
 *           2023.01.01, version 1
 *           API: int setInfor() = 0;
 *           API: int getInfor() = 1; it is just the last API
 *
 *           2023.03.01, version 2
 *           API: int setInfor() = 0;
 *           (API: // int getInfor() = 1; it is deleted, keep it here)
 *
 *           2023.04.01, version 3
 *           API: int setInfor() = 0;
 *           (API: // int getInfor() = 1; it is deleted, keep it here)
 *           API: int getDetailInfor() = 2; it is the new last API, but number must be 2
 *
 *@hide
 */
interface INetworkPolicyService {
    /**
     * Query whether the specified feature supported and return supported version.
     *
     * Every feature service will be optimized and upgrade in future. This function will return
     * which version supported for specified feature. Client can do function control.
     *
     * @param feature specified feature to be queried.
     * @return 0 if not support, a number greater than 0 indicating the feature version.
     */
    int versionSupported(in String feature) = 0;

    /*************************************APK Booster*********************************************/
    // APK Booster start @{
    /**
     * Configure BoosterInfo to enable or disable booster for specified application.
     *
     * @param info to be config.
     * @return true for success, false for fail.
     */
    boolean configBoosterInfo(in BoosterInfo info) = 1;
    // APK Booster end @}

    /*************************************Fast Swtich*********************************************/
    // Fast Swtich start @{ // Fast switch between WiFi and Cellular
    /**
     * Add IFastSwitchListener to monitor FastSwitchInfo notification.
     *
     * Clients will receive all network type callback and it is ok that interersted clients process
     * those who care about.
     *
     * @param listener to listen FastSwitch related information.
     * @param b Bundle object.
     */
    oneway void addFastSwitchListener(in IFastSwitchListener listener, in Bundle b) = 2;

    /**
     * Configure fast switch for the specified network without return valaue.
     * All functions with void return type can be implemented by info.mAction.
     *
     * @param info networkType and action in info are necessory.
     */
    oneway void configFastSwitchInfo(in FastSwitchInfo info) = 3;

    /**
     * Configure fast switch for the specified network with return int value.
     * For boolean value returned, use 0 indicateds false, != 0 indicates true.
     * All functions with int and boolean return type can be implemented by info.mAction.
     *
     * @param info networkType in info is necessory.
     */
    int configFastSwitchInfoWithIntResult(in FastSwitchInfo info) = 4;
    // Fast Swtich end @}
}
