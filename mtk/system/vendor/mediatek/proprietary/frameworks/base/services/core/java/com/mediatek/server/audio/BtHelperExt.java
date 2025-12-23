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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.media.AudioDeviceAttributes;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.Binder;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.os.SystemProperties;

import com.android.internal.annotations.GuardedBy;
import com.mediatek.bt.BluetoothAppAcceptList;
import com.mediatek.bt.BluetoothLeCallControl;
import com.mediatek.bt.BluetoothProfileEx;

import java.util.List;
import java.util.HashMap;
import java.util.Objects;

/**
 * @hide
 * Class to encapsulate all communication with Bluetooth services
 */
public class BtHelperExt {

    private static final String TAG = "AS.BtHelperExt";

    // Reference to BluetoothLeAudio to query for AbsoluteVolume.
    private @Nullable BluetoothLeAudio mLeAudio;

    private @Nullable BluetoothLeAudio mLeAudioProfile;

    // If absolute volume is supported in VC/LE device
    private boolean mLeVcAbsVolSupported = false;

    // If call volume control is supported in VC/LE device
    private boolean mLeCallVcAbsVolSupported = false;

    //private final @NonNull AudioDeviceBroker mDeviceBroker;
    Object mDeviceBroker;

    // BluetoothLeCallControl API to control CG  connection
    private @Nullable BluetoothLeCallControl mBluetoothLeCallControl;

    // Bluetooth LE TBS device for BLE CG ( call Gateway)
    private @Nullable BluetoothDevice mBluetoothLeTbsDevice;

    private @NonNull AudioDeviceBrokerExt mDeviceBrokerExt = null;

    private int mLeCgAudioMode;

    private int mLeCgAudioState;

    // Current connection state indicated by bluetooth BluetoothTbs
    private int mLeCgConnectionState;
    //indicate vendor version
    private boolean mIsVendorBeforeAndroidU = false;
    /// M: false means offload
    private boolean mIsSupportNonOffload = false;
    // The status of the previous cg
    private boolean mIsCgOn;

    // LE CG audio state is not active
    private static final int LE_CG_STATE_INACTIVE = 0;
    // LE CG audio activation request waiting for BluetoothTbs service to connect
    private static final int LE_CG_STATE_ACTIVATE_REQ = 1;
    // LE CG audio state is active due to an action in BT BluetoothTbs (either voice recognition or
    // in call audio)
    private static final int LE_CG_STATE_ACTIVE_EXTERNAL = 2;
    // LE CG audio state is active or starting due to a request from AudioManager API
    private static final int LE_CG_STATE_ACTIVE_INTERNAL = 3;
    // LE CG audio deactivation request waiting for headset service to connect
    private static final int LE_CG_STATE_DEACTIVATE_REQ = 4;
    // LE CG audio deactivation in progress, waiting for Bluetooth audio intent
    private static final int LE_CG_STATE_DEACTIVATING = 5;

    // LE CG audio mode is undefined
    /*package*/  static final int LE_CG_MODE_UNDEFINED = -1;
    // LE CG audio mode is virtual voice call (BluetoothTbs.startScoUsingVirtualVoiceCall())
    /*package*/  static final int LE_CG_MODE_VIRTUAL_CALL = 0;
    // LE CG audio mode is raw audio (BluetoothLeCallControl.connectAudio())
    /*package*/  static final int LE_CG_MODE_RAW = 1;
    // LE CG audio mode is for MD call (BluetoothLeCallControl.connectAudio())
    /*package*/  static final int LE_CG_MODE_MD_CALL = 3;
    // LE CG audio mode is virtual voice call (BluetoothTbs.startScoUsingVirtualVoiceCall())
    // Sample rate will 48 khz
    /*package*/  static final int LE_CG_MODE_HD_VIRTUAL_CALL = 4;
    // Sample rate will normal khz
    /*package*/  static final int LE_CG_MODE_NORMAL_VIRTUAL_CALL = 5;
    // max valid LE CG audio mode values
    /*package*/ static final int LE_CG_MODE_MAX = 5;

    BtHelperExt(@NonNull Object broker, AudioDeviceBrokerExt deviceBrokerExt) {
        mDeviceBroker = broker;
        mDeviceBrokerExt = deviceBrokerExt;
    }

    /*package*/ @NonNull static String getName(@NonNull BluetoothDevice device) {
        final String deviceName = device.getName();
        if (deviceName == null) {
            return "";
        }
        return deviceName;
    }

    //----------------------------------------------------------------------
    // Interface for AudioDeviceBroker
    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void onSystemReady() {
        Log.i(TAG, "onSystemReady");
        /// M: check offload status
        mIsSupportNonOffload =
            SystemProperties.getBoolean("persist.bluetooth.mtk.leaudio.tb.support", false);
        int firstApiLevel = SystemProperties.getInt("ro.vndk.version", 34);
        // true: before android U or non-offload
        mIsVendorBeforeAndroidU =
                    ((firstApiLevel <= Build.VERSION_CODES.TIRAMISU ? true : false)
                    || mIsSupportNonOffload);
        //if (!mIsVendorBeforeAndroidU) {
        //    return;
        //}
        mLeCgConnectionState = android.media.AudioManager.SCO_AUDIO_STATE_ERROR;
        resetBluetoothLeCg();
        //FIXME: this is to maintain compatibility with deprecated intent
        // AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED. Remove when appropriate.
        Intent newIntent = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
        newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
        sendStickyBroadcastToAll(newIntent);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(mDeviceBrokerExt.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfileEx.LE_CALL_CONTROL);
            adapter.getProfileProxy(mDeviceBrokerExt.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfile.LE_AUDIO);
        }
    }

    /*package*/ synchronized void onLeProfileConnected(BluetoothProfile LeAudio) {
        Log.i(TAG, "onLeProfileConnected");
        mLeAudio = (BluetoothLeAudio)LeAudio;
        final List<BluetoothDevice> deviceList = LeAudio.getConnectedDevices();
        if (deviceList.isEmpty()) {
            return;
        }
        final BluetoothDevice btDevice = deviceList.get(0);
        final /*@BluetoothProfile.BtProfileState*/ int state =
                LeAudio.getConnectionState(btDevice);
        mDeviceBrokerExt.postBluetoothLeAudioDeviceConnectionState(
                btDevice, state,
                /*suppressNoisyIntent*/ false,
                /*musicDevice*/ android.media.AudioSystem.DEVICE_NONE,
                /*eventSource*/ "mBluetoothProfileServiceListener");
    }

      // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void receiveBtEvent(Intent intent) {
        final String action = intent.getAction();

        Log.i(TAG, "receiveBtEvent action: " + action + " mLeCgAudioMode: "
                    + cgAudioModeToString(mLeCgAudioMode));
        if (action.equals(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED)) {
            BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.d(TAG, "receiveBtEvent() BTactiveDeviceChanged" +  ", btDevice=" + btDevice);
            }
            if (mLeAudio != null && mLeAudio.getAudioLocationOfSrc(btDevice) ==
                    BluetoothLeAudio.AUDIO_LOCATION_INVALID) {
                Log.d(TAG, "receiveBtEvent: remote device not support cg feature");
                setBLeCgActiveDevice(null);
                return;
            }
            setBLeCgActiveDevice(btDevice);
        } else if (action.equals(BluetoothLeCallControl.ACTION_AUDIO_STATE_CHANGED)) {
            int btState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            // processing in Broker thread, instead of Main thread
            mDeviceBrokerExt.postCgAudioStateChanged(btState);
            String address = null;
            if (btDevice != null) {
                address = btDevice.getAnonymizedAddress();
            }
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                String btStateInfo = (btState == BluetoothHeadset.STATE_AUDIO_CONNECTED) ?
                      "AudioConnected" : "AudioDisconnected";
                Log.d(TAG, "receiveBtEvent() ACTION_AUDIO_STATE_CHANGED" +  ", btState=" + btState
                     + "{" + btStateInfo + "}" +"address=" + address);
            }
        }
    }

    /**
     * Exclusively called from AudioDeviceBroker when handling MSG_I_SCO_AUDIO_STATE_CHANGED
     * as part of the serialization of the communication route selection
     */
    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    void onCgAudioStateChanged(int btState) {
        boolean broadcast = false;
        int LeCgAudioState = AudioManager.SCO_AUDIO_STATE_ERROR;
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            String btStateInfo = (btState == BluetoothHeadset.STATE_AUDIO_CONNECTED) ?
                  "AudioConnected" : "AudioDisconnected";
            Log.d(TAG, "onCgAudioStateChanged ACTION_AUDIO_STATE_CHANGED" +  ", btState=" + btState
                 + "{" + btStateInfo + "}");
        }
        if (mIsVendorBeforeAndroidU) {
          switch (btState) {
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                LeCgAudioState = AudioManager.SCO_AUDIO_STATE_CONNECTED;
                if (mLeCgAudioState != LE_CG_STATE_ACTIVE_INTERNAL
                        && mLeCgAudioState != LE_CG_STATE_DEACTIVATE_REQ) {
                    mLeCgAudioState = LE_CG_STATE_ACTIVE_EXTERNAL;
                } else if (mDeviceBrokerExt.isBluetoothLeCgRequested()) {
                    // broadcast intent if the connection was initated by AudioService
                    broadcast = true;
                }
                mLeCallVcAbsVolSupported = true;
                mDeviceBrokerExt
                    .setBleCallVcSupportsAbsoluteVolume(true);
                mDeviceBrokerExt.setBluetoothLeCgOn(true, "BtHelper.receiveBtEvent");
                break;
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                mLeCallVcAbsVolSupported = false;
                mDeviceBrokerExt
                    .setBleCallVcSupportsAbsoluteVolume(false);
                mDeviceBrokerExt.setBluetoothLeCgOn(false, "BtHelper.receiveBtEvent");
                LeCgAudioState = AudioManager.SCO_AUDIO_STATE_DISCONNECTED;
                // There are two cases where we want to immediately reconnect audio:
                // 1) If a new start request was received while disconnecting: this was
                // notified by requestScoState() setting state to LE_CG_STATE_ACTIVATE_REQ.
                // 2) If audio was connected then disconnected via Bluetooth APIs and
                // we still have pending activation requests by apps: this is indicated by
                // state LE_CG_STATE_ACTIVE_EXTERNAL and BT SCO is requested.
                if (mLeCgAudioState == LE_CG_STATE_ACTIVATE_REQ
                        || (mLeCgAudioState == LE_CG_STATE_ACTIVE_EXTERNAL
                                && mDeviceBrokerExt.isBluetoothLeCgRequested())) {
                    if (mBluetoothLeCallControl != null && mBluetoothLeTbsDevice != null
                            && connectBluetoothLeCgAudioHelper(mBluetoothLeCallControl,
                            mBluetoothLeTbsDevice, mLeCgAudioMode)) {
                        mLeCgAudioState = LE_CG_STATE_ACTIVE_INTERNAL;
                        LeCgAudioState = AudioManager.SCO_AUDIO_STATE_CONNECTING;
                            broadcast = true;
                        break;
                    }
                }
                if (mLeCgAudioState != LE_CG_STATE_ACTIVE_EXTERNAL) {
                    broadcast = true;
                }
                mLeCgAudioState = LE_CG_STATE_INACTIVE;
                break;
            case BluetoothHeadset.STATE_AUDIO_CONNECTING:
                if (mLeCgAudioState != LE_CG_STATE_ACTIVE_INTERNAL
                        && mLeCgAudioState != LE_CG_STATE_DEACTIVATE_REQ) {
                    mLeCgAudioState = LE_CG_STATE_ACTIVE_EXTERNAL;
                }
                break;
            default:
                break;
          }
        } else {
          switch (btState) {
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                LeCgAudioState = AudioManager.SCO_AUDIO_STATE_CONNECTED;
                mDeviceBrokerExt.setBluetoothLeCgOn(true, "BtHelperExt.receiveBtEvent");
                broadcast = true;
                break;
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                LeCgAudioState = AudioManager.SCO_AUDIO_STATE_DISCONNECTED;
                mDeviceBrokerExt.setBluetoothLeCgOn(false, "BtHelperExt.receiveBtEvent");
                broadcast = true;
                break;
            default:
                break;
          }
        }

        if (broadcast) {
            broadcastScoConnectionState(LeCgAudioState);
            //FIXME: this is to maintain compatibility with deprecated intent
            // AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED. Remove when appropriate.
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.d(TAG, "receiveBtEvent(): BR cgAudioStateChanged"
                    +  ", LeCgAudioState=" + LeCgAudioState);
            }
            Intent newIntent = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
            newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, LeCgAudioState);
            sendStickyBroadcastToAll(newIntent);
        }
    }

    private void sendStickyBroadcastToAll(Intent intent) {
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        final long ident = Binder.clearCallingIdentity();
        try {
            BluetoothAppAcceptList appAcceptList = BluetoothAppAcceptList.getDefaultAppAcceptList();
            appAcceptList.sendStickyBroadcast(mDeviceBrokerExt.getContext(), intent);
            //mDeviceBrokerExt.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

     private boolean disconnectBluetoothLeCgAudioHelper(BluetoothLeCallControl bluetoothLeCallControl,
            BluetoothDevice device, int leCgAudioMode) {
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.d(TAG, "disconnectBluetoothLeCgAudioHelper, leCgAudioMode="
                    + cgAudioModeToString(leCgAudioMode)
                    + ",device=" + device);
        }
        switch (leCgAudioMode) {
            case LE_CG_MODE_VIRTUAL_CALL:
            case LE_CG_MODE_HD_VIRTUAL_CALL:
            case LE_CG_MODE_NORMAL_VIRTUAL_CALL:
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.d(TAG, "stopIsoUsingVirtualVoiceCall()");
                }
                return bluetoothLeCallControl.stopIsoUsingVirtualVoiceCall();
            case LE_CG_MODE_MD_CALL:
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.d(TAG, "disconnectAudio()");
                }
                return bluetoothLeCallControl.disconnectAudio();
            default:
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.e(TAG, "CG disconnection failed due to unknown CG Mode leCgAudioMode="
                        + cgAudioModeToString(leCgAudioMode));
                }
                return false;
        }
    }

    private boolean connectBluetoothLeCgAudioHelper(BluetoothLeCallControl bluetoothLeCallControl,
            BluetoothDevice device, int leCgAudioMode) {
        if (mLeAudio != null && mLeAudio.getAudioLocationOfSrc(device) ==
                BluetoothLeAudio.AUDIO_LOCATION_INVALID) {
            Log.d(TAG, "connectBluetoothLeCgAudioHelper: remote device not support cg feature");
            return false;
        }
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.d(TAG, "connectBluetoothLeCgAudioHelper, leCgAudioMode="
                + cgAudioModeToString(leCgAudioMode)
                + ",device=" + device);
        }
        switch (leCgAudioMode) {
            case LE_CG_MODE_VIRTUAL_CALL:
            case LE_CG_MODE_NORMAL_VIRTUAL_CALL:
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.d(TAG, "startIsoUsingVirtualVoiceCall()");
                }
                return bluetoothLeCallControl
                .startIsoUsingVirtualVoiceCall(
                    BluetoothLeAudioCodecConfig.SAMPLE_RATE_NONE);
            case LE_CG_MODE_HD_VIRTUAL_CALL:
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.d(TAG, "startIsoUsingVirtualVoiceCall()");
                }
                return bluetoothLeCallControl
                    .startIsoUsingVirtualVoiceCall(
                        BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000);
            case LE_CG_MODE_MD_CALL:
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.d(TAG, "connectAudio()");
                }
                return bluetoothLeCallControl.connectAudio();
            default:
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.e(TAG, "CG connection failed due to unknown CG Mode leCgAudioMode="
                        + cgAudioModeToString(leCgAudioMode));
                }
                return false;
        }
    }

    private void checkCgAudioState() {
        if (mBluetoothLeCallControl != null
                && mBluetoothLeTbsDevice != null
                && mLeCgAudioState == LE_CG_STATE_INACTIVE
                && mBluetoothLeCallControl.getAudioState(mBluetoothLeTbsDevice)
                != BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
            mLeCgAudioState = LE_CG_STATE_ACTIVE_EXTERNAL;
        }
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.d(TAG, "checkCgAudioState() mLeCgAudioMode="
                + cgAudioModeToString(mLeCgAudioMode)
                + ", mLeCgAudioState ="  + cgAudioStateToString(mLeCgAudioState)
                + ", mBluetoothLeTbsDevice ="  + mBluetoothLeTbsDevice);
        }
    }

    synchronized void onLeTbsProfileConnected(BluetoothLeCallControl bluetoothLeTbs) {
        // Discard timeout message
        mDeviceBrokerExt.handleCancelFailureToConnectToBluetoothTbsService();
        mBluetoothLeCallControl = bluetoothLeTbs;
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
             Log.d(TAG, "onLeTbsProfileConnected bluetoothLeTbs = " + bluetoothLeTbs);
        }
        if (!mIsVendorBeforeAndroidU) {
            return;
        }
       /* List<BluetoothDevice> mLeDevices = mLeAudioProfile.getActiveDevices();
        if (mLeDevices.size() <= 0 || mLeDevices == null) {
            Log.e(TAG, "LeService is not connected, getAcgtiveDevice() failed");
            mBluetoothLeCallControl = null;
            return;
        }
        if (mLeDevices != null) {
            Log.d(TAG, "LeService.getAcgtiveDevice()" + mLeDevices.size());
            setBLeCgActiveDevice(mLeDevices.get(0));
        }*/

        // Refresh SCO audio state
        checkCgAudioState();
        if (mLeCgAudioState != LE_CG_STATE_ACTIVATE_REQ
                && mLeCgAudioState != LE_CG_STATE_DEACTIVATE_REQ) {
            return;
        }
        boolean status = false;
        if (mBluetoothLeTbsDevice != null) {
            switch (mLeCgAudioState) {
                case LE_CG_STATE_ACTIVATE_REQ:
                    status = connectBluetoothLeCgAudioHelper(
                            mBluetoothLeCallControl,
                            mBluetoothLeTbsDevice, mLeCgAudioMode);
                    if (status) {
                        mLeCgAudioState = LE_CG_STATE_ACTIVE_INTERNAL;
                    }
                    break;
                case LE_CG_STATE_DEACTIVATE_REQ:
                    status = disconnectBluetoothLeCgAudioHelper(
                            mBluetoothLeCallControl,
                            mBluetoothLeTbsDevice, mLeCgAudioMode);
                    if (status) {
                        mLeCgAudioState = LE_CG_STATE_DEACTIVATING;
                    }
                    break;
            }
        }
        if (!status) {
            mLeCgAudioState = LE_CG_STATE_INACTIVE;
            broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
        }
    }

    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void disconnectBleTbs() {
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
             Log.d(TAG, "disconnectBleTbs()");
        }
        setBLeCgActiveDevice(null);
        mBluetoothLeCallControl = null;
    }

    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    //@GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    @GuardedBy("BtHelper.this")
    private void setBLeCgActiveDevice(BluetoothDevice btDevice) {
        Log.i(TAG, "setBLeCgActiveDevice: " + getAnonymizedAddress(mBluetoothLeTbsDevice)
                + " -> " + getAnonymizedAddress(btDevice));
        final BluetoothDevice previousActiveDevice = mBluetoothLeTbsDevice;
        if (Objects.equals(btDevice, previousActiveDevice)) {
            return;
        }
        // if (!handleFakeScoActiveDeviceChange(previousActiveDevice, false)) {
        //     Log.w(TAG, "setBLeCgActiveDevice() failed to remove previous device "
        //             + getAnonymizedAddress(previousActiveDevice));
        // }
        // if (!handleFakeScoActiveDeviceChange(btDevice, true)) {
        //     Log.e(TAG, "setBLeCgActiveDevice() failed to add new device "
        //             + getAnonymizedAddress(btDevice));
        //     // set mBluetoothHeadsetDevice to null when failing to add new device
        //     btDevice = null;
        // }
        mBluetoothLeTbsDevice = btDevice;
        if (mBluetoothLeTbsDevice == null) {
            resetBluetoothLeCg();
            mLeCallVcAbsVolSupported = false;
            mDeviceBrokerExt
                .setBleCallVcSupportsAbsoluteVolume(false);
            return;
        } else {
            mDeviceBrokerExt.restartCgInCall();
        }
    }

    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void resetBluetoothLeCg() {
        mLeCgAudioState = LE_CG_STATE_INACTIVE;
        broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
        mDeviceBrokerExt.setBluetoothLeCgOn(false, "resetBluetoothLeCg");
        notifyCgState(false);
    }

    private AudioDeviceAttributes btLeTbsDeviceToAudioDevice(BluetoothDevice btDevice) {
        String address = btDevice.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }
        BluetoothClass btClass = btDevice.getBluetoothClass();
        int nativeType = AudioSystem.DEVICE_OUT_BLE_HEADSET;
        if (btClass != null) {
            switch (btClass.getDeviceClass()) {
                case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                    nativeType = AudioSystem.DEVICE_OUT_BLE_HEADSET;
                    break;
                case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                    nativeType = AudioSystem.DEVICE_OUT_BLE_HEADSET;
                    break;
            }
        }
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.i(TAG, "btHeadsetDeviceToAudioDevice btDevice: " + btDevice
                    + " btClass: " + (btClass == null ? "Unknown" : btClass)
                    + " nativeType: " + nativeType + " address: " + address);
        }
        return new AudioDeviceAttributes(nativeType, address);
    }

    // private boolean handleFakeScoActiveDeviceChange(BluetoothDevice btDevice, boolean isActive) {
    //     if (btDevice == null) {
    //         return true;
    //     }
    //     if (!isSupportFakeHfp()) {
    //         return true;
    //     }
    //     int inDevice = AudioSystem.DEVICE_IN_BLUETOOTH_SCO_HEADSET;
    //     AudioDeviceAttributes audioDevice =  btLeTbsDeviceToAudioFakeScoDevice(btDevice);
    //     String btDeviceName =  getName(btDevice);
    //     boolean result = false;
    //     if (isActive) {
    //         result |= mDeviceBrokerExt.handleDeviceConnection(audioDevice, isActive, btDevice);
    //     } else {
    //         int[] outDeviceTypes = {
    //             AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_HEADSET,
    //         };
    //         for (int outDeviceType : outDeviceTypes) {
    //             result |= mDeviceBrokerExt.handleDeviceConnection(new AudioDeviceAttributes(
    //                     outDeviceType, getAnonymizedAddress(btDevice), btDeviceName),
    //                     isActive, btDevice);
    //         }
    //     }
    //     // handleDeviceConnection() && result to make sure the method get executed
    //     result = mDeviceBrokerExt.handleDeviceConnection(new AudioDeviceAttributes(
    //                     inDevice, getAnonymizedAddress(btDevice), btDeviceName),
    //             isActive, btDevice) && result;
    //     return result;
    // }

    private boolean isSupportFakeHfp() {
        BluetoothAppAcceptList appAcceptList = BluetoothAppAcceptList.getDefaultAppAcceptList();
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.d(TAG, "handleFakeScoActiveDeviceChange, support = " + appAcceptList.isEnabled());
        }
        return appAcceptList.isEnabled();
    }

//     private AudioDeviceAttributes btLeTbsDeviceToAudioFakeScoDevice(BluetoothDevice btDevice) {
//         String address = getAnonymizedAddress(btDevice);
//         String name = getName(btDevice);
//         // if (!BluetoothAdapter.checkBluetoothAddress(address)) {
//         //     address = "";
//         // }
//         BluetoothClass btClass = btDevice.getBluetoothClass();
//         int nativeType = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_HEADSET;
// //        if (btClass != null) {
// //            switch (btClass.getDeviceClass()) {
// //                case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
// //                case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
// //                    nativeType = AudioSystem.DEVICE_OUT_BLE_HEADSET;
// //                    break;
// //                case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
// //                    nativeType = AudioSystem.DEVICE_OUT_BLE_HEADSET;
// //                    break;
// //            }
// //        }
//         if (AudioServiceExtImpl.DEBUG_DEVICES) {
//             Log.i(TAG, "btHeadsetDeviceToAudioDevice btDevice: " + btDevice
//                     + " btClass: " + (btClass == null ? "Unknown" : btClass)
//                     + " nativeType: " + nativeType + " address: " + address);
//         }
//         return new AudioDeviceAttributes(nativeType, address, name);
//     }

    // Return `(null)` if given BluetoothDevice is null. Otherwise, return the anonymized address.
    private String getAnonymizedAddress(BluetoothDevice btDevice) {
        return btDevice == null ? "(null)" : btDevice.getAnonymizedAddress();
    }

    // NOTE this listener is NOT called from AudioDeviceBroker event thread, only call async
    //      methods inside listener.
    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
            new BluetoothProfile.ServiceListener() {
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    //Bluetooth Telephone Bearer Service
                    if (profile == BluetoothProfileEx.LE_CALL_CONTROL) {
                        Log.i(TAG, "BLE CALL CONTROL Profile Connected, proxy=" + proxy);
                        mDeviceBrokerExt.postBtTbsProfileConnected((BluetoothLeCallControl) proxy);
                    } else if (profile == BluetoothProfile.LE_AUDIO) {
                        mDeviceBrokerExt.postBtLEProfileConnected((BluetoothLeAudio)proxy);
                    }

                }
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfileEx.LE_CALL_CONTROL) {
                        Log.i(TAG, "BLE CALL CONTROL Profile Disconnected");
                        mDeviceBrokerExt.postBtTbsProfileDisconnected();
                    }
                }
            };

    /*package*/ synchronized void disconnectAllBluetoothProfiles() {
        Log.i(TAG, "disconnectAllBluetoothProfiles");
        mDeviceBrokerExt.postBtTbsProfileDisconnected();
    }

    /*package*/ synchronized boolean isLeVcAbsoluteVolumeSupported() {
        return ((mLeAudio != null) && mLeVcAbsVolSupported);
    }

     /*package*/ synchronized void setLeVcAbsoluteVolumeSupported(boolean supported) {
        mLeVcAbsVolSupported = supported;
        Log.i(TAG, "setLeVcAbsoluteVolumeSupported supported=" + mLeVcAbsVolSupported);
    }

    /* synchronized void setLeVcAbsoluteVolumeIndex(int index) {
        if (mLeAudio == null) {
            if (AudioServiceExtImpl.DEBUG_VOL) {
                Log.d(TAG, "setLEAvrcpAbsoluteVolumeIndex: bailing due to null mLeAudio");
                return;
            }
        }
        if (!mLeVcAbsVolSupported) {
            Log.d(TAG, "setLeVcAbsoluteVolumeIndex: abs vol not supported");
            return;
        }
        if (AudioServiceExtImpl.DEBUG_VOL) {
            Log.i(TAG, " setLeVcAbsoluteVolumeIndex()"
                + "mLeAudio.setVcAbsoluteVolume for index=" + index);
        }
        /// M: LeAudio Feature
        // TO DO
        //BluetoothLeAudioFactory.getInstance().setVcAbsoluteVolume(index, mLeAudio);
    }*/

    /* synchronized void setLeCgVcIndex(int index) {
        if (mLeAudio == null) {
            if (AudioServiceExtImpl.DEBUG_VOL) {
                Log.d(TAG, "setLeCgVcIndex: bailing due to null mLeAudio");
                return;
            }
        }
        if (!mLeCallVcAbsVolSupported) {
            Log.d(TAG, "setLeCgVcIndex: abs vol not supported");
            return;
        }
        if (AudioServiceExtImpl.DEBUG_VOL) {
            Log.i(TAG, "setLeCgVcIndex()"
                + "mLeAudio.setVcAbsoluteVolume for index=" + index);
        }
        /// M: LeAudio Feature
        //BluetoothLeAudioFactory.getInstance().setVcAbsoluteVolume(index, mLeAudio);
    }*/

    /* package */synchronized boolean isBluetoothLeTbsDeviceActive() {
        return (mBluetoothLeTbsDevice != null);
    }

    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized boolean startBluetoothLeCg(int LeCgAudioMode,
                @NonNull String eventSource) {
        if (AudioServiceExtImpl.DEBUG_VOL) {
            Log.i(TAG, "startBluetoothLeCg() LeCgAudioMode=" + LeCgAudioMode
                  + ",eventSource = " + eventSource);
        }
        return requestLeCgState(BluetoothHeadset.STATE_AUDIO_CONNECTED, LeCgAudioMode);
    }

    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized boolean stopBluetoothLeCg(@NonNull String eventSource) {
        if (AudioServiceExtImpl.DEBUG_VOL) {
            Log.i(TAG, "stopBluetoothLeCg() eventSource = " + eventSource);
        }
        return requestLeCgState(BluetoothHeadset.STATE_AUDIO_DISCONNECTED, mLeCgAudioMode);
    }

    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized boolean stopBluetoothLeCg(@NonNull String eventSource, int leMode) {
        if (AudioServiceExtImpl.DEBUG_VOL) {
            Log.i(TAG, "stopBluetoothLeCg() eventSource = " + eventSource  );
        }
        return requestLeCgState(BluetoothHeadset.STATE_AUDIO_DISCONNECTED, leMode);
    }

     private boolean getBluetoothTbs() {
        boolean result = false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            result = adapter.getProfileProxy(mDeviceBrokerExt.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfileEx.LE_CALL_CONTROL);
        }
        if (AudioServiceExtImpl.DEBUG_VOL) {
            Log.i(TAG, "getBluetoothTbs() result = " + result  );
        }
        // If we could not get a bluetooth headset proxy, send a failure message
        // without delay to reset the SCO audio state and clear SCO clients.
        // If we could get a proxy, send a delayed failure message that will reset our state
        // in case we don't receive onServiceConnected().

        mDeviceBrokerExt.handleFailureToConnectToBluetoothTbsService(
                result ? AudioDeviceBrokerExt.BT_LE_TBS_CNCT_TIMEOUT_MS : 0);

        return result;
    }

    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    //@GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    @GuardedBy("BtHelper.this")
    private boolean requestLeCgState(int state, int cGAudioMode) {
        checkCgAudioState();
        Log.d(TAG, "requestScoState: state=" + state
                    + ", cGAudioMode=" + cGAudioMode);
        if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
            // Make sure that the state transitions to CONNECTING even if we cannot initiate
            // the connection.
            broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_CONNECTING);
            switch (mLeCgAudioState) {
                case LE_CG_STATE_INACTIVE:
                    mLeCgAudioMode = cGAudioMode;
                    if (cGAudioMode == LE_CG_MODE_UNDEFINED) {
                        mLeCgAudioMode = LE_CG_MODE_VIRTUAL_CALL;
                        if (mBluetoothLeTbsDevice != null) {
                             mLeCgAudioMode = Settings.Global.getInt(
                                    mDeviceBrokerExt.getContentResolver(),
                                    "bluetooth_sco_channel_"
                                            + mBluetoothLeTbsDevice.getAddress(),
                                    LE_CG_MODE_VIRTUAL_CALL);
                            if (mLeCgAudioMode > LE_CG_MODE_MAX || mLeCgAudioMode < 0) {
                                mLeCgAudioMode = LE_CG_MODE_VIRTUAL_CALL;
                            }
                        }
                    }
                    if (mBluetoothLeCallControl == null) {
                        if (getBluetoothTbs()) {
                            mLeCgAudioState = LE_CG_STATE_ACTIVATE_REQ;
                        } else {
                            Log.w(TAG, "requestScoState: getBluetoothTbs() failed during"
                                    + " connection, mLeCgAudioMode=" + mLeCgAudioMode);
                            broadcastScoConnectionState(
                                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                            return false;
                        }
                        break;
                    }
                    if (mBluetoothLeTbsDevice == null) {
                        Log.w(TAG, "requestScoState: no active device while connecting,"
                                + " mLeCgAudioMode=" + mLeCgAudioMode);
                        broadcastScoConnectionState(
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        return false;
                    }
                    if (connectBluetoothLeCgAudioHelper(mBluetoothLeCallControl,
                            mBluetoothLeTbsDevice, mLeCgAudioMode)) {
                        mLeCgAudioState = LE_CG_STATE_ACTIVE_INTERNAL;
                    } else {
                        Log.w(TAG, "requestScoState: connect to "
                                + getAnonymizedAddress(mBluetoothLeTbsDevice)
                                + " failed, mLeCgAudioMode=" + mLeCgAudioMode);
                        broadcastScoConnectionState(
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        return false;
                    }
                    break;
                case LE_CG_STATE_DEACTIVATING:
                    mLeCgAudioState = LE_CG_STATE_ACTIVATE_REQ;
                    //  When telecom is call disconnected and Audio Manager
                    //  disconnects ISO channel.
                    //  During this period, if VOIP call is resumed and
                    //  virtual call is connected.
                    //  CG state is changed to LE_CG_STATE_ACTIVATE_REQ,
                    //  but Audio Mode is not updated
                    //  So, ISO channel reconnected again,
                    //  instead of virtual call.
                    if (cGAudioMode != mLeCgAudioMode
                            && cGAudioMode == LE_CG_MODE_UNDEFINED) {
                        if (AudioServiceExtImpl.DEBUG_DEVICES) {
                            Log.i(TAG, "requestLeCgState: deactivating"
                                + " -> ACTIVATE_REQ, Audio Mode changed"
                                + cgAudioModeToString(mLeCgAudioMode)
                                + " -> "
                                + cgAudioModeToString(cGAudioMode));
                        }
                        mLeCgAudioMode = cGAudioMode;
                    }
                    break;
                case LE_CG_STATE_DEACTIVATE_REQ:
                    mLeCgAudioState = LE_CG_STATE_ACTIVE_INTERNAL;
                    broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_CONNECTED);
                    break;
                case LE_CG_STATE_ACTIVE_INTERNAL:
                    Log.w(TAG, "requestLeCgState: already in ACTIVE mode, simply return");
                    break;
                default:
                    Log.w(TAG, "requestLeCgState: failed to connect in state "
                            + mLeCgAudioMode + ", cGAudioMode=" + cGAudioMode);
                    broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                    return false;
            }
        } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
            switch (mLeCgAudioState) {
                case LE_CG_STATE_ACTIVE_INTERNAL:
                case LE_CG_STATE_ACTIVE_EXTERNAL:
                    if (mBluetoothLeCallControl == null) {
                        if (getBluetoothTbs()) {
                            mLeCgAudioState = LE_CG_STATE_DEACTIVATE_REQ;
                        } else {
                            Log.w(TAG, "requestScoState: getBluetoothHeadset failed during"
                                    + " disconnection, mLeCgAudioMode=" + mLeCgAudioMode);
                            mLeCgAudioState = LE_CG_STATE_INACTIVE;
                            broadcastScoConnectionState(
                                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                            return false;
                        }
                        break;
                    }
                    if (mBluetoothLeTbsDevice == null) {
                        mLeCgAudioState = LE_CG_STATE_INACTIVE;
                        broadcastScoConnectionState(
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        break;
                    }
                    if (disconnectBluetoothLeCgAudioHelper(mBluetoothLeCallControl,
                            mBluetoothLeTbsDevice, cGAudioMode)) {
                        mLeCgAudioState = LE_CG_STATE_DEACTIVATING;
                    } else {
                        mLeCgAudioState = LE_CG_STATE_INACTIVE;
                        broadcastScoConnectionState(
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                    }
                    break;
                case LE_CG_STATE_ACTIVATE_REQ:
                    mLeCgAudioState = LE_CG_STATE_INACTIVE;
                    broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                    break;
                default:
                    Log.w(TAG, "requestLeCgState: failed to disconnect in state "
                            + mLeCgAudioState + ", cGAudioMode=" + cGAudioMode);
                    broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                    return false;
            }
        }
        return true;
    }

    private void broadcastScoConnectionState(int state) {
        mDeviceBrokerExt.postBroadcastLeCgConnectionState(state);
    }

    /*package*/ synchronized void onBroadcastLeCgConnectionState(int state) {
        if (state == mLeCgConnectionState) {
            return;
        }
        Intent newIntent = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, state);
        newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE,
            mLeCgConnectionState);
        sendStickyBroadcastToAll(newIntent);
        mLeCgConnectionState = state;
    }

    @Nullable AudioDeviceAttributes getLeTbsAudioDevice() {
        if (mBluetoothLeTbsDevice == null) {
            return null;
        }
        return btLeTbsDeviceToAudioDevice(mBluetoothLeTbsDevice);
    }

    /**
     *
     * @return false if LE CG isn't connected
     */
    /*package*/ synchronized boolean isBluetoothLeCgOn() {
        if (mBluetoothLeCallControl == null) {
            return false;
        }
        return mBluetoothLeCallControl.getAudioState(mBluetoothLeTbsDevice)
                == BluetoothHeadset.STATE_AUDIO_CONNECTED;
    }

    /**
     *
     * @return CG Audio Mode
     */
    /*package*/ synchronized int getCgAudioMode() {
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.d(TAG, "getAudioMode() mCgAudioMode="
                + cgAudioModeToString(mLeCgAudioMode));
        }
        return mLeCgAudioMode;
    }

    /*package*/ boolean isInbandRingingEnabled() {
        if (mBluetoothLeCallControl == null) {
            return false;
        }
        return mBluetoothLeCallControl.isInbandRingingEnabled();
    }

    public static String cgAudioModeToString(int cgAudioMode) {
        switch (cgAudioMode) {
            case LE_CG_MODE_UNDEFINED:
                return "LE_CG_MODE_UNDEFINED";
            case LE_CG_MODE_VIRTUAL_CALL:
                return "LE_CG_MODE_VIRTUAL_CALL";
            case LE_CG_MODE_RAW:
                return "LE_CG_MODE_RAW";
            case LE_CG_MODE_MD_CALL:
                return "LE_CG_MODE_MD_CALL";
            case LE_CG_MODE_HD_VIRTUAL_CALL:
                return "LE_CG_MODE_HD_VIRTUAL_CALL";
            case LE_CG_MODE_NORMAL_VIRTUAL_CALL:
                return "LE_CG_MODE_NORMAL_VIRTUAL_CALL";
            default:
                return "LE_CG_MODE_(" + cgAudioMode + ")";
        }
    }

    public static String cgAudioStateToString(int cgAudioState) {
        switch (cgAudioState) {
            case LE_CG_STATE_INACTIVE:
                return "LE_CG_STATE_INACTIVE";
            case LE_CG_STATE_ACTIVATE_REQ:
                return "LE_CG_STATE_ACTIVATE_REQ";
            case LE_CG_STATE_ACTIVE_EXTERNAL:
                return "LE_CG_STATE_ACTIVE_EXTERNAL";
            case LE_CG_STATE_ACTIVE_INTERNAL:
                return "LE_CG_STATE_ACTIVE_INTERNAL";
            case LE_CG_STATE_DEACTIVATE_REQ:
                return "LE_CG_STATE_DEACTIVATE_REQ";
            case LE_CG_STATE_DEACTIVATING:
                return "LE_CG_STATE_DEACTIVATING";
            default:
                return "LE_CG_STATE_(" + cgAudioState + ")";
        }
    }

    /**
     * Notify cg audio isCgOn.
     * @param isCgOn is cg on
     */
    public void notifyCgState(boolean isCgOn) {
        if (mBluetoothLeCallControl == null) {
            Log.e(TAG, "notifyCgState, mBluetoothLeCallControl is null");
            mIsCgOn = false;
            return;
        }
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
             Log.d(TAG, "notifyCgState, isCgOn=" + isCgOn + " ,mIsCgOn=" + mIsCgOn);
        }
        if (isCgOn == mIsCgOn) {
            return;
        }
        if (!isSupportFakeHfp()) {
            return;
        }
        mIsCgOn = isCgOn;
        if (isCgOn) {
            mBluetoothLeCallControl.connectCgAudio();
        } else {
            mBluetoothLeCallControl.disconnectCgAudio();
        }
    }

    /**
     *  Check is vendor before android U.
     *
     * @return mIsVendorBeforeAndroidU
     */
    public boolean isVendorBeforeAndroidU() {
        return mIsVendorBeforeAndroidU;
    }
}
