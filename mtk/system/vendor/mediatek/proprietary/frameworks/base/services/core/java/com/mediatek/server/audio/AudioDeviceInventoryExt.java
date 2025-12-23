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
package com.mediatek.server.audio;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
//import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.media.AudioDeviceAttributes;
import android.media.AudioSystem;
import android.os.Binder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.android.server.audio.AudioDeviceInventory;
//import com.android.server.audio.AudioDeviceInventory.DeviceInfo;
import com.android.server.audio.AudioService;
import com.android.server.audio.AudioSystemAdapter;
import com.android.server.audio.SystemServerAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class AudioDeviceInventoryExt {
    private static final String TAG = "AS.AudioDeviceInventoryExt";
    /** for mocking only, allows to inject AudioSystem adapter */

    AudioSystemAdapter mAudioSystem = null;
    private static Object mDeviceBroker = null;
    private static Field mDevicesLockField = null;
    private static Object mDevicesLock = null;
    private static Method mCheckSendBecomingNoisyIntentIntMethod = null;
    //private static Method mSetCurrentAudioRouteNameIfPossibleMethod = null;
    private static Method mPostAccessoryPlugMediaUnmuteMethod = null;
    private static Method mSetBluetoothA2dpOnIntMethod = null;
    private static Method mPostApplyVolumeOnDeviceMethod = null;
    private static Method mPostSetVolumeIndexOnDeviceMethod = null;
    private static Method mGetA2dpCodecMethod = null;
    private static Method mPostBluetoothA2dpDeviceConfigChangeMethod = null;
    private static AudioDeviceBrokerExt mDeviceBrokerExt = null;

    private static AudioDeviceInventory mDeviceInventory;
    //private static LinkedHashMap<String, DeviceInfo> mConnectedDevices = null;
    private static ArrayMap<Integer, String> mApmConnectedDevices = null;

   /*package*/ AudioDeviceInventoryExt(@NonNull AudioService service,
                                       @NonNull AudioSystemAdapter audioSystem,
                                       Object deviceBroker,
                                       AudioDeviceBrokerExt deviceBrokerExt,
                                       AudioDeviceInventory deviceInventory) {
        mDeviceBroker = deviceBroker;
        mAudioSystem = audioSystem;
        mDeviceInventory =  deviceInventory;
        mDeviceBrokerExt = deviceBrokerExt;
        mCheckSendBecomingNoisyIntentIntMethod = ReflectionHelper.getMethod(mDeviceInventory,
                                           "checkSendBecomingNoisyIntentInt",
                                            int.class /*device*/,
                                            int.class /*state*/,
                                            int.class /*musicDevice*/);
        //mSetCurrentAudioRouteNameIfPossibleMethod = ReflectionHelper.getMethod(mDeviceInventory,
        //                                   "setCurrentAudioRouteNameIfPossible",
        //                                    String.class /*name*/,
        //                                    boolean.class /*fromA2dp*/);
        mPostAccessoryPlugMediaUnmuteMethod  = ReflectionHelper.getMethod(deviceBroker,
                                           "postAccessoryPlugMediaUnmute",
                                            int.class /*device*/);
        mPostSetVolumeIndexOnDeviceMethod = ReflectionHelper.getMethod(deviceBroker,
                                           "postSetVolumeIndexOnDevice",
                                            int.class /*streamType*/,
                                            int.class /*vssVolIndex*/,
                                            int.class /*device*/,
                                            String.class /*caller*/);
        mPostApplyVolumeOnDeviceMethod = ReflectionHelper.getMethod(deviceBroker,
                                           "postApplyVolumeOnDevice",
                                            int.class /*streamType*/,
                                            int.class /*device*/,
                                            String.class /*caller*/);
        mDevicesLock = ReflectionHelper.getFieldObject(deviceInventory,
                                           "mDevicesLock");
        /*mConnectedDevices = (LinkedHashMap<String, DeviceInfo>)ReflectionHelper.getFieldObject(deviceInventory,
                                           "mConnectedDevices"); */
    }

     /*private void setCurrentAudioRouteNameIfPossible(String name, boolean fromA2dp) {
         ReflectionHelper.callMethod(mSetCurrentAudioRouteNameIfPossibleMethod,
                                mDeviceInventory, name, fromA2dp);
     }*/

      private int checkSendBecomingNoisyIntentInt(int device,
            @AudioService.ConnectionState int state, int musicDevice) {
        int delay = 0;
        delay = (int) ReflectionHelper.callMethod(mCheckSendBecomingNoisyIntentIntMethod,
                                mDeviceInventory, device, state,
                                musicDevice);
        return delay;
      }

    /*package*/ int setPreferredDevicesForStrategySync(int strategy,
            @NonNull List<AudioDeviceAttributes> devices) {
        final long identity = Binder.clearCallingIdentity();

        Log.d(TAG,"setPreferredDevicesForStrategySync, strategy: " + strategy
                                + " devices: " + devices);
        final long diff = SystemClock.uptimeMillis();
        final int status = mAudioSystem.setDevicesRoleForStrategy(
                strategy, AudioSystem.DEVICE_ROLE_PREFERRED, devices);
        Binder.restoreCallingIdentity(identity);

        if (status == AudioSystem.SUCCESS) {
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.d(TAG, "setPreferredDevicesForStrategySync, strategy: " + strategy
                        + ", APM made devices: " + devices + "preferred device in"
                        +  (SystemClock.uptimeMillis() - diff) + "ms" );
            }
        } else {
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.e(TAG,"setPreferredDevicesForStrategySync, strategy: " + strategy
                                + ", APM fail to set devices: " + devices);
            }
        }
        return status;
    }

    /*package*/ int  setBluetoothLeAudioDeviceConnectionState(
            @NonNull BluetoothDevice device, @AudioService.BtProfileConnectionState int state,
            boolean suppressNoisyIntent, int musicDevice) {
        int delay = 0;
        synchronized (mDevicesLock) {
            if (!suppressNoisyIntent) {
              //TODO:
               int intState = (state == 2/*BluetoothLeAudio.STATE_CONNECTED*/) ? 1 : 0;
               delay = checkSendBecomingNoisyIntentInt(AudioSystem.DEVICE_OUT_BLE_HEADSET,
                        intState, musicDevice);
             return delay;
            }
        return delay;
        }
    }
}
