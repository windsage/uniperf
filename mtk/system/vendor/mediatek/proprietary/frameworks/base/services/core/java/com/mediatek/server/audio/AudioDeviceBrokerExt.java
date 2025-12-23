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
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.AttributionSource;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRoutesInfo;
import android.media.AudioSystem;
import android.media.AudioPort;
import android.media.AudioDevicePort;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.annotations.GuardedBy;

import com.android.server.audio.AudioDeviceInventory;
import com.android.server.audio.AudioService;
import com.android.server.audio.BtHelper;
import com.android.server.audio.AudioSystemAdapter;
import com.android.server.audio.SystemServerAdapter;

import com.mediatek.bt.BluetoothLeCallControl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Class to manage the inventory of BLE connected devices.
 * This class is thread-safe.
 * (non final for mocking/spying)
 */
public class AudioDeviceBrokerExt {

   private static final String TAG = "AS.AudioDeviceBrokerExt";

    /** If the msg is already queued, replace it with this one. */
    private static final int SENDMSG_REPLACE = 0;
    /** If the msg is already queued, ignore this one and leave the old. */
    private static final int SENDMSG_NOOP = 1;
    /** If the msg is already queued, queue this one and leave the old. */
    private static final int SENDMSG_QUEUE = 2;

    private static final int MSG_IIL_SET_FORCE_BT_A2DP_USE = 5;

    private static final int MSG_L_UPDATE_COMMUNICATION_ROUTE = 39;

    // process external command to (dis)connect a le audio device
    private static final int MSG_IL_SET_LE_AUDIO_CONNECTION_STATE = 100;
    private static final int MSG_L_LE_AUDIO_DEVICE_CONNECTION_CHANGE_EXT = 101;

    // process external command to (dis)connect a le audio device
    private static final int MSG_L_BT_SERVICE_CONNECTED_PROFILE_LE = 102;
    private static final int MSG_DISCONNECT_BT_LE = 103;
    private static final int MSG_I_SET_LE_VC_ABSOLUTE_VOLUME = 104;

    private static final int MSG_L_BT_SERVICE_CONNECTED_PROFILE_LE_TBS = 105;
    private static final int MSG_DISCONNECT_BLE_TBS = 106;
    private static final int MSG_I_SET_LE_CG_VC_ABSOLUTE_VOLUME = 107;
    private static final int MSG_BT_LE_TBS_CNCT_FAILED = 108;
    private static final int MSG_I_BROADCAST_BT_LE_CG_CONNECTION_STATE = 109;

    private static final int MSG_I_SET_STOP_LE_CG_AUDIO = 110;
    private static final int MSG_I_SET_RESTART_LE_CG_AUDIO = 111;
    private static final int MSG_I_CG_AUDIO_STATE_CHANGED = 112;
    private static final int MSG_IL_SET_PREF_DEVICES_FOR_STRATEGY = 113;
    private static final int MSG_I_SET_RESTART_SCO_AUDIO = 114;

    private static final int MSG_L_RESTART_LE_CG_AUDIO_LATER = 115;

    // Timeout for CG audio restart
    private static final int BT_RESTART_LE_CG_AUDIO_TIMEOUT_MS = 1000;
    private static final int BT_RESTART_SCO_AUDIO_TIMEOUT_MS = 100;

    // Timeout for connection to bluetooth headset service
    /*package*/ static final int BT_LE_TBS_CNCT_TIMEOUT_MS = 3000;

   // Volume update is delayed when CG audio is connected or disconnected
   // This creates problem while connected or disconnected
    private static final int DEFAULT_ABS_VOL_IDX_DELAY_MS = 200;

    // we use a different lock than mDeviceStateLock so as not to create
    // lock contention between enqueueing a message and handling them
    private static final Object sLastBLeCgDeviceConnectTimeLock = new Object();

    @GuardedBy("sLastBLeCgDeviceConnectTime")
    private static long sLastBLeCgDeviceConnectTime = 0;

    // LE device information is maintained.
    private boolean mBluetoothLeDeviceStatus;

    private final @NonNull AudioService mAudioService;
    private final @NonNull Context mContext;
    private final Object mDeviceBroker;
   // Manages notifications to BT service
    private final BtHelperExt mBtHelperExt;
    // Manages all connected devices, only ever accessed on the message loop
    private final AudioDeviceInventoryExt mDeviceInventoryExt;

   private final AudioDeviceInventory mDeviceInventory;

    // Adapter for system_server-reserved operations
    private final SystemServerAdapter mSystemServer;
    private AudioServiceExtImpl mAudioServiceExtImpl = null;
    private Method sendIILMsgMethod = null;
    private Method removeMessagesMethod = null;
    private Method onSetForceUseMethod = null;
    //private Method handleDeviceConnectionMethod = null;
    private Method isDeviceRequestedForCommunicationMethod = null;
    private Method isDeviceActiveForCommunicationMethod = null;
    private Method requestedCommunicationDeviceMethod = null;
    private Method getCommunicationRouteClientForUidMethod = null;
    private Method getCommunicationDeviceMethod = null;
    private Method addCommunicationRouteClientMethod = null;
    private Method removeCommunicationRouteClientMethod = null;
    private Method getContentResolverMethod = null;
    private Method isBluetoothScoOnMethod = null;
    private Method stopBluetoothScoMethod = null;
    private Method getHeadsetAudioDeviceMethod = null;
    private Method startBluetoothScoForClientMethod = null;
    private Method startBluetoothBleForClientMethod = null;
    private Method isBleRecordingIdleMethod = null;
    private Method isBleHDRecordActiveMethod = null;
    private Field mDeviceStateLockField = null;
    private Field mSetModeLockField = null;
    private Field mDeviceInventoryField = null;

    private Object mDeviceStateLock = null;
    private Object mSetModeLock = null;
    private Handler mBrokerHandler = null;

    private BtHelper mBtHelper = null;

    /** Last preferred device set for communication strategy */
    private AudioDeviceAttributes mPreferredCommunicationDevice;

    /** ID for Communication strategy retrieved form audio policy manager */
    private Field mCommunicationStrategyIdField = null;

    private boolean isInCallHfpTOCgSwitch = false;

    private AudioDeviceInfo mActiveCommunicationDevice = null;

    /*package*/ AudioDeviceBrokerExt(@NonNull Context context, @NonNull AudioService service,
                                     AudioServiceExtImpl serviceExtImpl,
                                    AudioSystemAdapter audioSystem,
                                    Object deviceBroker,
                                    SystemServerAdapter systemServer) {
        mContext = context;
        mAudioService = service;
        mAudioServiceExtImpl = serviceExtImpl;
        mBtHelperExt = new BtHelperExt(deviceBroker,this);

        mDeviceBroker = deviceBroker;
        mSystemServer = systemServer;

        sendIILMsgMethod = ReflectionHelper.getMethod(deviceBroker,
                                           "sendIILMsg",
                                            int.class /*msg*/,
                                            int.class /*existingMsgPolicy*/,
                                            int.class /*arg1*/,
                                            int.class /*arg2*/,
                                            Object.class /*obj*/,
                                            int.class /*delay*/);
        mBrokerHandler = (Handler) ReflectionHelper.getFieldObject(deviceBroker,
                                           "mBrokerHandler");
        getContentResolverMethod = ReflectionHelper.getMethod(deviceBroker,
                                       "getContentResolver");
        onSetForceUseMethod = ReflectionHelper.getMethod(deviceBroker,
                                       "onSetForceUse",
                                        int.class /*useCase*/,
                                        int.class /*config*/,
                                        boolean.class /*fromA2dp*/,
                                        String.class /*eventSource*/);
        //handleDeviceConnectionMethod = ReflectionHelper.getMethod(deviceBroker,
        //        "handleDeviceConnection",
        //        AudioDeviceAttributes.class /*attributes*/,
        //        boolean.class /*connect*/, BluetoothDevice.class);
        isDeviceRequestedForCommunicationMethod = ReflectionHelper.getMethod(deviceBroker,
                                                   "isDeviceRequestedForCommunication",
                                                    int.class /*deviceType*/);
        isDeviceActiveForCommunicationMethod = ReflectionHelper.getMethod(deviceBroker,
                                                    "isDeviceActiveForCommunication",
                                                    int.class /*deviceType*/);
        requestedCommunicationDeviceMethod = ReflectionHelper.getMethod(deviceBroker,
                "requestedCommunicationDevice");
        getCommunicationDeviceMethod  = ReflectionHelper.getMethod(deviceBroker,
                                                    "getCommunicationDeviceInt");
        getCommunicationRouteClientForUidMethod = ReflectionHelper.getMethod(deviceBroker,
                                                    "getCommunicationRouteClientForUid",
                                                    int.class /*uid*/);
        addCommunicationRouteClientMethod = ReflectionHelper.getMethod(deviceBroker,
                                                    "addCommunicationRouteClient",
                                                    IBinder.class /* cb */,
                                                    AttributionSource.class /*uid*/,
                                                    AudioDeviceAttributes.class /*device*/,
                                                    boolean.class /* isPrivileged */);
        removeCommunicationRouteClientMethod = ReflectionHelper.getMethod(deviceBroker,
                                                    "removeCommunicationRouteClient",
                                                    IBinder.class /* cb */,
                                                    boolean.class /* true */);
        startBluetoothScoForClientMethod = ReflectionHelper.getMethod(deviceBroker,
                                                    "startBluetoothScoForClient",
                                                    IBinder.class /* cb */,
                                                    AttributionSource.class /*uid*/,
                                                    int.class /*scomode*/,
                                                    boolean.class /* isPrivileged */,
                                                    String.class /*eventSource*/);
        startBluetoothBleForClientMethod = ReflectionHelper.getMethod(deviceBroker,
                "startBluetoothBleForClient",
                IBinder.class /* cb */,
                AttributionSource.class /*uid*/,
                int.class /*scomode*/,
                boolean.class /* isPrivileged */,
                String.class /*eventSource*/);
        isBleRecordingIdleMethod = ReflectionHelper.getMethod(deviceBroker,
                                                    "isBleRecordingIdle");
        isBleHDRecordActiveMethod = ReflectionHelper.getMethod(deviceBroker,
                                                    "isBleHDRecordActive");
        mDeviceStateLock = ReflectionHelper.getFieldObject(deviceBroker,
                                       "mDeviceStateLock");
        mSetModeLock = ReflectionHelper.getFieldObject(deviceBroker,
                                       "mSetModeLock");
        mBtHelper = (BtHelper) ReflectionHelper.getFieldObject(deviceBroker,
                                       "mBtHelper");
        isBluetoothScoOnMethod = ReflectionHelper.getMethod(mBtHelper,
                                       "isBluetoothScoOn");
        stopBluetoothScoMethod = ReflectionHelper.getMethod(mBtHelper,
                                       "stopBluetoothSco",
                                       String.class /*eventSource*/);
        getHeadsetAudioDeviceMethod = ReflectionHelper.getMethod(mBtHelper,
                                       "getHeadsetAudioDevice");
        mDeviceInventory = (AudioDeviceInventory) ReflectionHelper.getFieldObject(deviceBroker,
                                       "mDeviceInventory");
      //mModeOwnerPid = (int) ReflectionHelper.getFieldObject(deviceBroker,
      //                                 "mModeOwnerPid");
        mCommunicationStrategyIdField = ReflectionHelper.getField(deviceBroker,
                                       "mCommunicationStrategyId");
        mDeviceInventoryExt = new AudioDeviceInventoryExt(service, audioSystem, deviceBroker,
                                                        this,
                                                        mDeviceInventory);
        mActiveCommunicationDevice = (AudioDeviceInfo) ReflectionHelper
                                                .getFieldObject(mDeviceBroker,
                                                "mActiveCommunicationDevice");
    }

    /*package*/ Context getContext() {
        return mContext;
    }

    /*package*/ void onSystemReady() {
        synchronized (mSetModeLock) {
            synchronized (mDeviceStateLock) {
                mBtHelperExt.onSystemReady();
            }
        }
    }

    /*package*/ boolean isInbandRingingEnabled() {
        synchronized (mSetModeLock) {
            synchronized (mDeviceStateLock) {
                return mBtHelperExt.isInbandRingingEnabled();
            }
        }
    }

    private Object addCommunicationRouteClient(
                    IBinder cb, @NonNull AttributionSource attributionSource,
                    AudioDeviceAttributes device, boolean isPrivileged) {
        return ReflectionHelper.callMethod(addCommunicationRouteClientMethod,
                mDeviceBroker, cb, attributionSource, device, isPrivileged);
    }

    private Object removeCommunicationRouteClient(
                    IBinder cb, boolean unregister) {
        return ReflectionHelper.callMethod(removeCommunicationRouteClientMethod,
                mDeviceBroker, cb, unregister);
    }

    private Object getCommunicationRouteClientForUid(int uid) {
        return ReflectionHelper.callMethod(getCommunicationRouteClientForUidMethod,
                mDeviceBroker, uid);
    }

    private boolean isDeviceActiveForCommunication(int deviceType) {
        return (boolean) ReflectionHelper.callMethod(isDeviceActiveForCommunicationMethod,
                mDeviceBroker, deviceType);
    }

    private AudioDeviceAttributes requestedCommunicationDevice() {
        return (AudioDeviceAttributes) ReflectionHelper.callMethod(requestedCommunicationDeviceMethod,
                mDeviceBroker);
    }

    private boolean isDeviceRequestedForCommunication(int deviceType) {
        return (boolean) ReflectionHelper.callMethod(isDeviceRequestedForCommunicationMethod,
                mDeviceBroker, deviceType);
    }

    private boolean isBleRecordingIdle() {
        return (boolean) ReflectionHelper.callMethod(isBleRecordingIdleMethod,
                mDeviceBroker);
    }

    private boolean isBleHDRecordActive() {
        return (boolean) ReflectionHelper.callMethod(isBleHDRecordActiveMethod,
                mDeviceBroker);
    }

    private boolean mBluetoothLeCgOn;
    /*package*/ void setBluetoothLeCgOn(boolean on, String eventSource) {
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "setBluetoothLeCgOn: " + on + " " + eventSource);
        }
        if (!on) {
           mAudioServiceExtImpl.resetBluetoothLeCgOfApp();
        } else {
            AudioDeviceAttributes attributes = requestedCommunicationDevice();
            if (mBtHelperExt.isVendorBeforeAndroidU() && attributes != null
                    && attributes.getType() != AudioDeviceInfo.TYPE_BLE_HEADSET) {
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.v(TAG, "setBluetoothLeCgOn: " + on
                            + " ,current communication device is not ble, stop cg");
                }
                mBtHelperExt.stopBluetoothLeCg("setBluetoothLeCgOn");
                return;
            }
        }
        synchronized (mDeviceStateLock) {
            mBluetoothLeCgOn = on;
            sendLMsgNoDelay(MSG_L_UPDATE_COMMUNICATION_ROUTE, SENDMSG_QUEUE, eventSource);
        }
    }

    /* package */ boolean isBluetoothLeCgOn() {
        synchronized (mDeviceStateLock) {
            return mBluetoothLeCgOn;
        }
    }

    /* package */ boolean isBluetoothLeCgStateOn() {
        return mBtHelperExt.isBluetoothLeCgOn();
    }

    /* package */  boolean isBluetoothLeTbsDeviceActive() {
        synchronized (mDeviceStateLock) {
            return mBtHelperExt.isBluetoothLeTbsDeviceActive();
        }
    }

    /* package */ void startBluetoothLeCgForClient(IBinder cb, int pid, int LeAudioMode, @NonNull String eventSource) {
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "startBluetoothLeCsForClient_Sync, pid: " + pid);
        }

        synchronized (mSetModeLock) {
            synchronized (mDeviceStateLock) {
                AudioDeviceAttributes device =
                        new AudioDeviceAttributes(AudioSystem.DEVICE_OUT_BLE_HEADSET, "");
                setCommunicationRouteForStartCsClient(cb, pid, device, LeAudioMode, eventSource);
            }
        }
    }

    /*package*/ void PoststopBluetoothLeCgForClientLater(IBinder cb,
                int pid, @NonNull String eventSource){
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "PoststopBluetoothLeCgForClientLater, pid: " + pid
                + "eventSource=" + eventSource);
        }
        //TO DO:
       /* sendIMsg(MSG_I_SET_STOP_LE_CG_AUDIO, SENDMSG_REPLACE,
                    0,
                    pid,
                    cb,
                    250); */
    }

    /*package*/ void postCgAudioStateChanged(int state) {
        sendIMsgNoDelay(MSG_I_CG_AUDIO_STATE_CHANGED, SENDMSG_QUEUE, state);
    }
    /*package*/ void postUpdateCommunicationRoute(@NonNull String eventSource) {
        sendLMsgNoDelay(MSG_L_UPDATE_COMMUNICATION_ROUTE, SENDMSG_QUEUE, eventSource);
    }

    /* package */ void stopBluetoothLeCgForClientLater(IBinder cb, int pid, @NonNull String eventSource) {
            PoststopBluetoothLeCgForClientLater(cb, pid, eventSource);
    }

    /* package */ boolean stopBluetoothLeCgForClient(IBinder cb, int pid, @NonNull String eventSource){
        boolean status = true;
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "stopBluetoothLeCgForClient, pid: " + pid);
        }

        synchronized (mSetModeLock) {
            synchronized (mDeviceStateLock) {
                int uid = Process.getUidForPid(pid);
                Object client = getCommunicationRouteClientForUid(uid);
                boolean leCGStatus = false;
                boolean mIsSpeakerOn = false;
                AudioDeviceAttributes mDevice = null;
                if (client != null) {
                    Method getDeviceMethod = ReflectionHelper.getMethod(client,
                                       "getDevice");
                    mDevice = (AudioDeviceAttributes)
                                    ReflectionHelper.callMethod(getDeviceMethod,
                                        client);

                    // When communication client, turns ON the speaker before turning off
                    // SCO-Audio or CG-Audio,communication's client device will be replaced
                    // with speaker device.
                    // Later, if the same client wants to turn off the SCO-Audio or BLE- CG audio
                    // and SCO-Audio or CG-Audio will not be turned off at BT end.
                    // Since communication client device will be speaker but not BLE-CS/SCO device.
                    // and SCO-Audio or BLE-CG audio remains active.
                    // Sol- Stop BT-SCO or CG-Audio & update the preferred device.
                    if(mDevice != null
                        && mDevice.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                        leCGStatus = true;
                    }
                    if(mDevice != null
                        && mDevice.getType() == AudioSystem.DEVICE_OUT_SPEAKER
                        && mBtHelperExt.isBluetoothLeCgOn()) {
                        int currModePid = mAudioServiceExtImpl.getModePid();
                        if (currModePid != 0 && pid != currModePid) {
                            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                                Log.v(TAG, "stopBluetoothLeCgForClient: mCurrModePid: "
                                        + currModePid
                                        + ", pid=" + pid + ", skip stopping CG audio");
                            }
                            return false;
                        }
                        Log.w(TAG, "stopBluetoothLeCgForClient, preferred device is speaker,"
                              + "turn off CG, pid: " + pid);
                        mBtHelperExt.stopBluetoothLeCg(eventSource);
                        sendLMsgNoDelay(MSG_L_UPDATE_COMMUNICATION_ROUTE,
                            SENDMSG_QUEUE, eventSource);
                        return true;
                    }
                }
                if (client == null || !leCGStatus) {
                    Log.w(TAG, "stopBluetoothLeCgForClient CG is OFF,failed, pid: " + pid);
                    return false;
                }
                /*if (leCGStatus && isInCallHfpTOCgSwitch) {
                    Log.w(TAG, "skip CG stop when HFP to CG switching: " + pid);
                    return true;
                }*/
                setCommunicationRouteForStopCsClient(
                        cb, pid, null, BtHelperExt.LE_CG_MODE_UNDEFINED, eventSource);
            }
        }
        return status;
    }

    /*package*/ boolean isBluetoothLeCgRequested() {
        /* CG device is remapped as TYPE BUS */
        return isDeviceRequestedForCommunication(AudioDeviceInfo.TYPE_BLE_HEADSET);
    }

    /**
     * Helper method on top of isDeviceRequestedForCommunication() indicating if
     * Bluetooth SCO ON is currently requested or not.
     * @return true if Bluetooth SCO ON is requested, false otherwise.
     */
    /*package*/ boolean isBluetoothScoRequested() {
        return isDeviceRequestedForCommunication(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
    }

    // @GuardedBy("AudioDeviceBroker.mSetModeLock")
    @GuardedBy("AudioDeviceBroker.mDeviceStateLock")
    private boolean stopBluetoothSco(@NonNull String eventSource) {
        return (boolean) ReflectionHelper.callMethod(stopBluetoothScoMethod,
                mBtHelper, eventSource);
    }

    /**
     *
     * @return false if SCO isn't connected
     */
    private boolean isBluetoothScoOn() {
        return (boolean) ReflectionHelper.callMethod(isBluetoothScoOnMethod,
                mBtHelper);
    }

    /*package*/ void postSetScoPreferredDeviceForStrategy() {
        AudioDeviceAttributes device = getHeadsetAudioDevice();
        int commID = ReflectionHelper.getFieldIntValue(mDeviceBroker,
                        "mCommunicationStrategyId");
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.d(TAG, "postSetScoPreferredDeviceForStrategy commID ="
                + commID + ", device=" + device);
        }
        if (device == null) {
            return;
        }
        sendIILMsg(MSG_IL_SET_PREF_DEVICES_FOR_STRATEGY, SENDMSG_REPLACE,
                commID, 0, Arrays.asList(device), 50);
    }

    /**
     *
     * @return false if SCO isn't connected
     */
    /*package*/ AudioDeviceAttributes getHeadsetAudioDevice() {
        return (AudioDeviceAttributes) ReflectionHelper.callMethod(getHeadsetAudioDeviceMethod,
                mBtHelper);
    }

    /**
     * Restarts CG-Audio if call is active.
     * when set mode is 2 or 3
     */
    /*package*/ void restartCgInCall() {
        if (!mAudioServiceExtImpl.isInCall()) {
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.v(TAG, "restartCgIfInCall - call is not active," +
                    "ignore restarting");
            }
            return;
        }
        AudioDeviceInfo mActiveCommunicationDevice = getCommunicationDevice();
        if (mActiveCommunicationDevice != null) {
            int deviceType = mActiveCommunicationDevice.getType();
            if (deviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.v(TAG, "restartCgIfNeeded - Speaker is ON," +
                    "Skip CG restart");
                }
                return;
            }
            /*if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                if(isBluetoothScoOn()) {
                    isInCallHfpTOCgSwitch = true;
                    stopBluetoothSco("hfp to CG switch");
                }

            }
            if (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                isCgToBeOn = true;
            }*/
        }
        if (mBtHelperExt.isBluetoothLeCgOn()) {
            return;
        }
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "restartCgIfInCall - restarting CG,");
        }
        postRestartCgInCall(BT_RESTART_LE_CG_AUDIO_TIMEOUT_MS);
    }

    /*package*/ void onRestartCgInCall() {
        int leAudioMode = 0;
        int mode = mAudioServiceExtImpl.getMode();
        int pid = 0;
        int uid = 0;
        IBinder mCb = null;
        isInCallHfpTOCgSwitch = false;
        switch (mode) {
            case AudioSystem.MODE_IN_CALL:
                leAudioMode = BtHelperExt.LE_CG_MODE_MD_CALL;
                break;
            case AudioSystem.MODE_IN_COMMUNICATION:
                leAudioMode = BtHelperExt.LE_CG_MODE_VIRTUAL_CALL;
                break;
            default:
                return;
        }
        pid = mAudioServiceExtImpl.getModePid();
        uid = Process.getUidForPid(pid);
        Object client; /* CommunicationRouteClient*/
        client = getCommunicationRouteClientForUid(uid);

        // when Communication client is not present.
        // During Telecom call, Telecom starts HFP-SCO
        // In this case, Communication client will not be
        // present
        if (client == null) {
            mCb = mAudioServiceExtImpl.getModeCb();
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.v(TAG, " when no client mCb," +
                mCb);
            }
        }
        if (client != null) {
            Method getBinderMethod = ReflectionHelper.getMethod(client,
                               "getBinder");
            if (getBinderMethod == null) {
                Log.w(TAG, "restartCgInCall" + ",getBinderMethod=" + getBinderMethod);
                return;
            }
            mCb = (IBinder) ReflectionHelper.callMethod(getBinderMethod,
                        client);
        }
        if (pid == 0 || mCb == null) {
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.w(TAG, "onRestartCgInCall pid or mCb is invalid" +
                    ", Pid=" + pid + ", mCb=" + mCb);
            }
            return;
        }
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.d(TAG, "onRestartCgInCall" +
                        ", Pid=" + pid + ", mCb=" + mCb);
        }
        if (mBtHelperExt.isVendorBeforeAndroidU()) {
            startBluetoothLeCgForClient(mCb, pid, leAudioMode, "hfp to CG switch");
        } else if (mode == AudioSystem.MODE_IN_COMMUNICATION) {
            startBluetoothBleForClient(mCb, pid, leAudioMode, "hfp to CG switch");
        } else {
            postUpdateCommunicationRoute("onRestartCgInCall");
        }
    }

    private void startBluetoothBleForClient(IBinder cb, int pid, int bleAudioMode,
                                            @NonNull String eventSource) {

        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "startBluetoothBleForClient_Sync, pid: " + pid);
        }
        int uid = Process.getUidForPid(pid);
        boolean isPrivileged = mContext.checkPermission(
          android.Manifest.permission.MODIFY_PHONE_STATE,
                    pid, uid) == PackageManager.PERMISSION_GRANTED;
        ReflectionHelper.callMethod(startBluetoothBleForClientMethod,
                mDeviceBroker, cb, getAttributionSource(uid, pid),
                                    bleAudioMode, isPrivileged,
                eventSource);
    }

    /*package*/ void postRestartCgInCall(int delay) {
        sendIMsg(MSG_I_SET_RESTART_LE_CG_AUDIO, SENDMSG_REPLACE, 0, delay);
    }

    /*package*/ void postRestartCgLater(int delay, LeCgClientInfo info) {
        sendLMsg(MSG_L_RESTART_LE_CG_AUDIO_LATER, SENDMSG_REPLACE, info, delay);
    }

    /*package*/ boolean isBluetoothLeCgActive() {
       /* CG device is remapped as TYPE BUS */
       /* 1. check if activeDevice is SCO or BUS,
       *  (APM replaces BUS with SCO)
       *  2. And check if preferred device is BUS,
       *  BUS remapped as BLE-CG device.
       */
        boolean activeDeviceStatus = false;
        boolean preferredDeviceStatus = false;
        if (mActiveCommunicationDevice == null) {
            mActiveCommunicationDevice = getCommunicationDevice();
        }
        if (mActiveCommunicationDevice != null) {
            int deviceType = mActiveCommunicationDevice.getType();
            activeDeviceStatus =
                ((deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)||
                (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET));
            Log.d(TAG, "ActiveDevice=" + deviceType);
            if (activeDeviceStatus == false) {
                return false;
            }
        }
        AudioDeviceAttributes mPrefComDev = (AudioDeviceAttributes) ReflectionHelper
                                            .getFieldObject(mDeviceBroker,
                                            "mPreferredCommunicationDevice");
        if (mPrefComDev == null) {
            return false;
        }
        if (mPrefComDev != null) {
            int prefdeviceType = mPrefComDev.getType();
            preferredDeviceStatus =
            (prefdeviceType == AudioDeviceInfo.TYPE_BLE_HEADSET);
            Log.d(TAG, "mPreferredCommunicationDevice=" + prefdeviceType);
            if (preferredDeviceStatus == false) {
                return false;
            }
        }
        Log.d(TAG, "activeDeviceStatus=" + activeDeviceStatus
                + ",preferredDeviceStatus=" + preferredDeviceStatus);
        if (!mBtHelperExt.isBluetoothLeCgOn()) {
            return false;
        }
        return (activeDeviceStatus && preferredDeviceStatus);
    }

    /* package */ AudioDeviceInfo getCommunicationDevice() {
        return (AudioDeviceInfo) ReflectionHelper.callMethod(getCommunicationDeviceMethod,
        mDeviceBroker);
    }

    /*package*/ void setBleCallVcSupportsAbsoluteVolume(boolean support) {
        mAudioServiceExtImpl.setBleCallVcSupportsAbsoluteVolume(support);
    }

    /*package*/ static final class LeCgClientInfo {
        IBinder mCb;
        int mPid;
        AudioDeviceAttributes mDevice;
        int mLeCgAudioMode;
        String mEventSource;
        LeCgClientInfo(IBinder cb, int pid, AudioDeviceAttributes device,
                            int LeCgAudioMode, String eventSource) {
            mDevice = device;
            mCb = cb;
            mPid = pid;
            mLeCgAudioMode = LeCgAudioMode;
            mEventSource = eventSource;
        }
    }

    private AttributionSource getAttributionSource(int uid, int pid){
        return new AttributionSource
                    .Builder(uid)
                    .setPid(pid)
                    .setDeviceId(mContext.DEVICE_ID_DEFAULT)
                    .setPackageName(mContext.getPackageManager()
                                    .getPackagesForUid(uid)[0])
                    .build();
    }

    @GuardedBy("mDeviceStateLock")
    /*package*/ void setCommunicationRouteForStartCsClient(
                            IBinder cb, int pid, AudioDeviceAttributes device,
                            int LeCgAudioMode, String eventSource) {

        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "setCommunicationRouteForStartCsClient: device: " + device
                       + " pid:" + pid);
        }
        Object client; /* CommunicationRouteClient*/
        // Save previous client route in case of failure to start BT SCO audio
        AudioDeviceAttributes prevClientDevice = null;
        int uid = 0;
        uid = Process.getUidForPid(pid);
        client = getCommunicationRouteClientForUid(uid);
        AttributionSource attributionSource = getAttributionSource(uid, pid);
        if (client != null) {
            Method getDeviceMethod = ReflectionHelper.getMethod(client,
                               "getDevice");
            prevClientDevice = (AudioDeviceAttributes) ReflectionHelper
                                .callMethod(getDeviceMethod,
                client);
            //prevClientDevice = client.getDevice();
        }
        boolean isPrivileged = mContext
          .checkPermission(android.Manifest.permission.MODIFY_PHONE_STATE,
                    pid, uid) == PackageManager.PERMISSION_GRANTED;

        if (device != null) {
            client = addCommunicationRouteClient(cb, attributionSource, device, isPrivileged);
            if (client == null) {
                Log.w(TAG, "setCommunicationRouteForStartCsClient: could not add client for uid: "
                        + uid + " and device: " + device);
            }
        }
        if (client == null) {
            return;
        }
        int currentCgAudioMode = mBtHelperExt.getCgAudioMode();
        boolean isCgOn = mBtHelperExt.isBluetoothLeCgOn();
        // when CG Audio Mode is switching from HD_VIRTUAL_CALL to
        // MODE_MD_CALL or VIRTUAL_CALL
        // Previous HD_VIRTUAL_CALL CG audio is disconnected
        // and again MODE_MD_CALL/VIRTUAL_CALL CG audio is restarted.

        if(isCgOn && mBtHelperExt.getCgAudioMode() != LeCgAudioMode) {
            // stop cg first, then restart cg
            // 48K record => call,
            if ((currentCgAudioMode == BtHelperExt.LE_CG_MODE_HD_VIRTUAL_CALL
                    &&  ((LeCgAudioMode == BtHelperExt.LE_CG_MODE_VIRTUAL_CALL)
                    || LeCgAudioMode == BtHelperExt.LE_CG_MODE_MD_CALL))
                    // // normal record => 48k record
                    || (currentCgAudioMode == BtHelperExt.LE_CG_MODE_NORMAL_VIRTUAL_CALL
                    && LeCgAudioMode == BtHelperExt.LE_CG_MODE_HD_VIRTUAL_CALL)) {
                mBtHelperExt.stopBluetoothLeCg("switchingCGModeChanging");
                LeCgClientInfo leCgClientInfo = new LeCgClientInfo(cb,
                        pid,
                        device,
                        LeCgAudioMode,
                        eventSource);
                postRestartCgLater(250, leCgClientInfo);
                return;
            }
        }
        if (!isCgOn) {
            if (!mBtHelperExt.startBluetoothLeCg(LeCgAudioMode, eventSource)) {
                Log.w(TAG, "setCommunicationRouteForStartCsClient: "
                        +"failure to start BLE-CG for pid: "
                        + pid);
                // clean up or restore previous client selection
                if (prevClientDevice != null) {
                    addCommunicationRouteClient(cb,
                        attributionSource, prevClientDevice, isPrivileged);
                } else {
                    removeCommunicationRouteClient(cb, true);
                }
                postBroadcastLeCgConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
            }
        }
        sendLMsgNoDelay(MSG_L_UPDATE_COMMUNICATION_ROUTE, SENDMSG_QUEUE, eventSource);
    }

    @GuardedBy("mDeviceStateLock")
    /*package*/ void setCommunicationRouteForStopCsClient(
                            IBinder cb, int pid, AudioDeviceAttributes device,
                            int LeCgAudioMode, String eventSource) {

        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "setCommunicationRouteForStopCsClient: device: " + device);
        }
        Object client; /* CommunicationRouteClient*/
        if (device == null) {
            client = removeCommunicationRouteClient(cb, true);
            if (client == null) {
                Log.e(TAG, "setCommunicationRouteForStopCsClient: client is null");
                return;
            }
        }

        if (mBtHelperExt.isBluetoothLeCgOn()) {
            int mode = mAudioServiceExtImpl.getMode();
            int leAudioMode = 0;
            int mCurrModePid = 0;
            switch (mode) {
                case AudioSystem.MODE_IN_CALL:
                    leAudioMode = BtHelperExt.LE_CG_MODE_MD_CALL;
                    break;
                case AudioSystem.MODE_IN_COMMUNICATION:
                    leAudioMode = BtHelperExt.LE_CG_MODE_VIRTUAL_CALL;
                    break;
            }
            mCurrModePid = mAudioServiceExtImpl.getModePid();
            if (mCurrModePid != 0 && pid != mCurrModePid) {
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.v(TAG, "setCommunicationRouteForStopCsClient: mCurrModePid: "
                        + mCurrModePid
                        + ", pid=" + pid + ", skip stopping CG audio");
                }
                return;
            }
            if (leAudioMode != 0) {
                mBtHelperExt.stopBluetoothLeCg(eventSource, leAudioMode);
            } else {
                mBtHelperExt.stopBluetoothLeCg(eventSource);
            }
        } else {
            Log.w(TAG, "setCommunicationRouteForStopCsClient: Cg is already off");
        }
        sendLMsgNoDelay(MSG_L_UPDATE_COMMUNICATION_ROUTE, SENDMSG_QUEUE, eventSource);
    }

     /*@GuardedBy("mDeviceStateLock")
     void setCommunicationRouteForClient(
                            IBinder cb, int pid, AudioDeviceAttributes device,
                            int LeCgAudioMode, String eventSource) {

        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "setCommunicationRouteForClient: device: " + device);
        }

        final boolean wasBLeCgRequested = isBluetoothLeCgRequested();
        Object client; // CommunicationRouteClient


        // Save previous client route in case of failure to start BT SCO audio
        AudioDeviceAttributes prevClientDevice = null;
        client = getCommunicationRouteClientForPid(pid);
        if (client != null) {
            Method getDeviceMethod = ReflectionHelper.getMethod(client,
                               "getDevice");
            prevClientDevice = (AudioDeviceAttributes) ReflectionHelper.callMethod(getDeviceMethod,
                client);
            //prevClientDevice = client.getDevice();
        }

        if (device != null) {
            client = addCommunicationRouteClient(cb, pid, device);
            if (client == null) {
                Log.w(TAG, "setCommunicationRouteForClient: could not add client for pid: "
                        + pid + " and device: " + device);
            }
        } else {
            client = removeCommunicationRouteClient(cb, true);
        }
        if (client == null) {
            return;
        }

        boolean isBtLeCgRequested = isBluetoothLeCgRequested();
        if (isBtLeCgRequested && !wasBLeCgRequested) {
            if (!mBtHelperExt.startBluetoothLeCg(LeCgAudioMode, eventSource)) {
                Log.w(TAG, "setCommunicationRouteForClient: failure to start BT SCO for pid: "
                        + pid);
                // clean up or restore previous client selection
                if (prevClientDevice != null) {
                    addCommunicationRouteClient(cb, pid, prevClientDevice);
                } else {
                    removeCommunicationRouteClient(cb, true);
                }
                postBroadcastLeCgConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
            }
        } else if (!isBtLeCgRequested && wasBLeCgRequested) {
            mBtHelperExt.stopBluetoothLeCg(eventSource);
        }

        sendLMsgNoDelay(MSG_L_UPDATE_COMMUNICATION_ROUTE, SENDMSG_QUEUE, eventSource);
    }*/

    ///*package*/ boolean handleDeviceConnection(AudioDeviceAttributes attributes, boolean connect,
    //                                           BluetoothDevice btDevice) {
    //    return (boolean) ReflectionHelper.callMethod(
    //            handleDeviceConnectionMethod, mDeviceBroker, attributes, connect, btDevice);
    //}

    private void sendLMsgNoDelay(int msg, int existingMsgPolicy, Object obj) {
        sendIILMsg(msg, existingMsgPolicy, 0, 0, obj, 0);
    }

    private void sendLMsg(int msg, int existingMsgPolicy, Object obj, int delay) {
        sendIILMsg(msg, existingMsgPolicy, 0, 0, obj, delay);
    }

    private void sendMsg(int msg, int existingMsgPolicy, int delay) {
        sendIILMsg(msg, existingMsgPolicy, 0, 0, null, delay);
    }

    private void sendIMsg(int msg, int existingMsgPolicy, int arg, int delay) {
        sendIILMsg(msg, existingMsgPolicy, arg, 0, null, delay);
    }

    private void sendMsgNoDelay(int msg, int existingMsgPolicy) {
        sendIILMsg(msg, existingMsgPolicy, 0, 0, null, 0);
    }

    private void sendIMsgNoDelay(int msg, int existingMsgPolicy, int arg) {
        sendIILMsg(msg, existingMsgPolicy, arg, 0, null, 0);
    }

    private void sendIILMsgNoDelay(int msg, int existingMsgPolicy, int arg1, int arg2, Object obj) {
       sendIILMsg(msg, existingMsgPolicy, arg1, arg2, obj, 0);
    }

    private void sendILMsg(int msg, int existingMsgPolicy, int arg, Object obj, int delay) {
        sendIILMsg(msg, existingMsgPolicy, arg, 0, obj, delay);
    }

    private void sendILMsgNoDelay(int msg, int existingMsgPolicy, int arg, Object obj) {
        sendIILMsg(msg, existingMsgPolicy, arg, 0, obj, 0);
    }

    private void sendIILMsg(int msg, int existingMsgPolicy, int arg1, int arg2, Object obj,
                            int delay) {
       ReflectionHelper.callMethod(sendIILMsgMethod, mDeviceBroker, msg, existingMsgPolicy, arg1,
                                 arg2, obj,delay);
    }

    /*package*/ void disconnectAllBluetoothProfiles() {
        synchronized (mDeviceStateLock) {
            mBtHelperExt.disconnectAllBluetoothProfiles();
        }
    }

  private static final class BtDeviceConnectionInfo {
        final @NonNull BluetoothDevice mDevice;
        final @AudioService.BtProfileConnectionState int mState;
        final int mProfile;
        final boolean mSupprNoisy;
        final int mVolume;

        BtDeviceConnectionInfo(@NonNull BluetoothDevice device,
                @AudioService.BtProfileConnectionState int state,
                int profile, boolean suppressNoisyIntent, int vol) {
            mDevice = device;
            mState = state;
            mProfile = profile;
            mSupprNoisy = suppressNoisyIntent;
            mVolume = vol;
        }

        // redefine equality op so we can match messages intended for this device
        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (this == o) {
                return true;
            }
            if (o instanceof BtDeviceConnectionInfo) {
                return mDevice.equals(((BtDeviceConnectionInfo) o).mDevice);
            }
            return false;
        }

        @Override
        public String toString() {
            return "BtDeviceConnectionInfo dev=" + mDevice.toString();
        }
    }

   private static final class LeAudioDeviceConnectionInfo {
        final @NonNull BluetoothDevice mDevice;
        final @AudioService.BtProfileConnectionState int mState;
        final boolean mSupprNoisy;
        final int mMusicDevice;
        final @NonNull String mEventSource;

        LeAudioDeviceConnectionInfo(@NonNull BluetoothDevice device,
                @AudioService.BtProfileConnectionState int state,
                boolean suppressNoisyIntent, int musicDevice, @NonNull String eventSource) {
            mDevice = device;
            mState = state;
            mSupprNoisy = suppressNoisyIntent;
            mMusicDevice = musicDevice;
            mEventSource = eventSource;
        }
    }

    /*package*/ void postBluetoothLeAudioDeviceConnectionState(
            @NonNull BluetoothDevice device, @AudioService.BtProfileConnectionState int state,
            boolean suppressNoisyIntent, int musicDevice, @NonNull String eventSource) {
        final LeAudioDeviceConnectionInfo info = new LeAudioDeviceConnectionInfo(
                device, state, suppressNoisyIntent, musicDevice, eventSource);
        sendLMsgNoDelay(MSG_L_LE_AUDIO_DEVICE_CONNECTION_CHANGE_EXT, SENDMSG_QUEUE, info);
    }

    /* void postSetLeVcAbsoluteVolumeIndex(int index) {
        sendIMsgNoDelay(MSG_I_SET_LE_VC_ABSOLUTE_VOLUME, SENDMSG_REPLACE, index);
    }*/

    /* void postSetLeCgVcIndex(int index) {
        synchronized (sLastBLeCgDeviceConnectTimeLock) {
            int delay = DEFAULT_ABS_VOL_IDX_DELAY_MS;
            long diffTime = SystemClock.uptimeMillis() - sLastBLeCgDeviceConnectTime;
            // Add delay if the gap is less between CG-Audio connection/disconnection and
            // volume change event
            if (diffTime >= delay) {
                sendIMsgNoDelay(MSG_I_SET_LE_CG_VC_ABSOLUTE_VOLUME, SENDMSG_REPLACE, index);
            } else {
                sendIMsg(MSG_I_SET_LE_CG_VC_ABSOLUTE_VOLUME, SENDMSG_REPLACE,
                    index,
                    delay);
            }
        }

    }*/

    /*package*/ void postSetLeAudioConnectionState(
            @AudioService.BtProfileConnectionState int state,
            @NonNull BluetoothDevice device, int delay) {

        sendILMsg(MSG_IL_SET_LE_AUDIO_CONNECTION_STATE, SENDMSG_QUEUE,
                state,
                device,
                delay);
    }

    /*package*/ void postDisconnectLE() {
        sendMsgNoDelay(MSG_DISCONNECT_BT_LE, SENDMSG_QUEUE);
    }

    /*package*/ void postBtLEProfileConnected(BluetoothProfile LEProfile) {
        sendLMsgNoDelay(MSG_L_BT_SERVICE_CONNECTED_PROFILE_LE, SENDMSG_QUEUE,
                LEProfile);
    }

    /*package*/ void postBtTbsProfileConnected(BluetoothProfile leTbsProfile) {
        sendLMsgNoDelay(MSG_L_BT_SERVICE_CONNECTED_PROFILE_LE_TBS, SENDMSG_QUEUE, leTbsProfile);
    }

    /*package*/ void postBtTbsProfileDisconnected() {
        sendMsgNoDelay(MSG_DISCONNECT_BLE_TBS, SENDMSG_QUEUE);
    }

     /*package*/ void postBroadcastLeCgConnectionState(int state) {
        sendIMsgNoDelay(MSG_I_BROADCAST_BT_LE_CG_CONNECTION_STATE, SENDMSG_QUEUE, state);
    }


    /*package*/ void handleFailureToConnectToBluetoothTbsService(int delay) {
        sendMsg(MSG_BT_LE_TBS_CNCT_FAILED, SENDMSG_REPLACE, delay);
    }

    /*package*/ void handleCancelFailureToConnectToBluetoothTbsService() {
        mBrokerHandler.removeMessages(MSG_BT_LE_TBS_CNCT_FAILED);
    }

    /*package*/ boolean isBluetoothLeOn() {
            return mBluetoothLeDeviceStatus;
    }

    //---------------------------------------------------------------------
    // Method forwarding between the helper classes (BtHelper, AudioDeviceInventory)
    // only call from a "handle"* method or "on"* method
      /*package*/ void setBluetoothLeOnInt(boolean on, boolean fromLe, String source) {
        // for logging only
        final String eventSource = new StringBuilder("setBluetoothLeOnInt(").append(on)
                .append(") from u/pid:").append(Binder.getCallingUid()).append("/")
                .append(Binder.getCallingPid()).append(" src:").append(source).toString();
        synchronized (mDeviceStateLock) {
            mBluetoothLeDeviceStatus = on;
           // ALPS05519378 : Route Media Stream to default device when LE is connected.
            /* ReflectionHelper.callMethod(removeMessagesMethod, mBrokerHandler,
                MSG_IIL_SET_FORCE_BT_A2DP_USE); */
            mBrokerHandler.removeMessages(MSG_IIL_SET_FORCE_BT_A2DP_USE);
            if(on) {
                /*onSetForceUse(
                    AudioSystem.FOR_MEDIA,
                    AudioSystem.FORCE_NONE,
                    fromLe,
                    eventSource);*/
                 ReflectionHelper.callMethod(onSetForceUseMethod, mDeviceBroker,
                     AudioSystem.FOR_MEDIA, AudioSystem.FORCE_NONE, fromLe,
                                 eventSource);
            }
        }
    }

    /*package*/ void setLeVcAbsoluteVolumeSupported(boolean supported) {
        mBtHelperExt.setLeVcAbsoluteVolumeSupported(supported);
    }

    /*package*/ void postHandleMessageExt(Message msg){
        if (AudioServiceExtImpl.LOGD) {
            Log.d(TAG, "postHandleMessageExt msgId=" + msg.what);
        }
        switch (msg.what) {
            case MSG_L_LE_AUDIO_DEVICE_CONNECTION_CHANGE_EXT: {
                final LeAudioDeviceConnectionInfo info =
                        (LeAudioDeviceConnectionInfo) msg.obj;
                if (AudioServiceExtImpl.LOGD) {
                    Log.d(TAG, "setLeAudioDeviceConnectionState state=" + info.mState
                                + " addr=" + info.mDevice.getAddress()
                                + " supprNoisy=" + info.mSupprNoisy
                                + " src=" + info.mEventSource);
                }
                synchronized (mDeviceStateLock) {
                    mDeviceInventoryExt.setBluetoothLeAudioDeviceConnectionState(
                            info.mDevice, info.mState, info.mSupprNoisy, info.mMusicDevice);
                }
            } break;
            case MSG_L_BT_SERVICE_CONNECTED_PROFILE_LE:
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                       mBtHelperExt.onLeProfileConnected((BluetoothProfile) msg.obj);
                    }
                }
                break;
             case MSG_I_SET_STOP_LE_CG_AUDIO:
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                       stopBluetoothLeCgForClient((IBinder) msg.obj,
                            (int) msg.arg2, "");
                    }
                }
                break;
             case MSG_I_SET_RESTART_LE_CG_AUDIO:
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                        onRestartCgInCall();
                    }
                }
                break;
             case MSG_L_RESTART_LE_CG_AUDIO_LATER:
                final LeCgClientInfo info = (LeCgClientInfo) msg.obj;
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                        setCommunicationRouteForStartCsClient(
                                info.mCb,
                                info.mPid,
                                info.mDevice,
                                info.mLeCgAudioMode,
                                info.mEventSource);
                    }
                }
                break;
             case MSG_I_SET_RESTART_SCO_AUDIO:
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                        onRestartScoInVoipCall();
                    }
                }
                break;
            case MSG_I_SET_LE_VC_ABSOLUTE_VOLUME:
                /*synchronized (mDeviceStateLock) {
                    mBtHelperExt.setLeVcAbsoluteVolumeIndex(msg.arg1);
                }*/
                break;
            case MSG_I_SET_LE_CG_VC_ABSOLUTE_VOLUME :
                /*synchronized (mDeviceStateLock) {
                    mBtHelperExt.setLeCgVcIndex(msg.arg1);
                }*/
                break;
            case MSG_DISCONNECT_BT_LE:
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                       //mDeviceInventoryExt.disconnectLE();
                    }
                }
                break;
            case MSG_IL_SET_LE_AUDIO_CONNECTION_STATE:
                synchronized (mDeviceStateLock) {
                    /*mDeviceInventoryExt.onSetLeAudioConnectionState(
                            (BluetoothDevice) msg.obj, msg.arg1,
                            mAudioServiceExtImpl.getLeAudioStreamType());*/
                }
                break;
            case MSG_L_BT_SERVICE_CONNECTED_PROFILE_LE_TBS:
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                        mBtHelperExt.onLeTbsProfileConnected((BluetoothLeCallControl) msg.obj);
                    }
                }
                break;
            case MSG_DISCONNECT_BLE_TBS:
                    synchronized (mSetModeLock) {
                        synchronized (mDeviceStateLock) {
                            mBtHelperExt.disconnectBleTbs();
                        }
                    }
                break;
            case MSG_BT_LE_TBS_CNCT_FAILED:
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                        mBtHelperExt.resetBluetoothLeCg();
                    }
                }
                break;
            case MSG_I_BROADCAST_BT_LE_CG_CONNECTION_STATE:
                synchronized (mDeviceStateLock) {
                    mBtHelperExt.onBroadcastLeCgConnectionState(msg.arg1);
                }
                break;
            case MSG_I_CG_AUDIO_STATE_CHANGED:
                synchronized (mSetModeLock) {
                    synchronized (mDeviceStateLock) {
                        mBtHelperExt.onCgAudioStateChanged(msg.arg1);
                    }
                }
                break;
            case MSG_IL_SET_PREF_DEVICES_FOR_STRATEGY: {
                final int strategy = msg.arg1;
                final List<AudioDeviceAttributes> devices =
                        (List<AudioDeviceAttributes>) msg.obj;
                setPreferredDevicesForStrategySync(strategy, devices);
            } break;
            default:
                Log.w(TAG, "Invalid message " + msg.what);
        }
    }

    /*package*/ void receiveBtEvent(@NonNull Intent intent) {
        synchronized (sLastBLeCgDeviceConnectTimeLock) {
            sLastBLeCgDeviceConnectTime = SystemClock.uptimeMillis();
        }
        synchronized (mDeviceStateLock) {
            mBtHelperExt.receiveBtEvent(intent);
        }
    }

    /*package*/ int setPreferredDevicesForStrategySync(int strategy,
            @NonNull List<AudioDeviceAttributes> devices) {
        return mDeviceInventoryExt.setPreferredDevicesForStrategySync(strategy, devices);
    }


    @GuardedBy("mDeviceStateLock")
    /*package*/ AudioDeviceAttributes preferredCommunicationDevice() {

        boolean btCgOn = mBluetoothLeCgOn && mBtHelperExt.isBluetoothLeCgOn();
        if (btCgOn) {
             AudioDeviceAttributes device = mBtHelperExt.getLeTbsAudioDevice();
            if (device != null) {
                return device;
            }
        }
        return null;
    }
    /*package*/ AudioDeviceAttributes getLeAudioDevice() {
        return mBtHelperExt.getLeTbsAudioDevice();
    }

    /*package*/ ContentResolver getContentResolver() {
        return (ContentResolver) ReflectionHelper.
            callMethod(getContentResolverMethod, mDeviceBroker);
    }

    /**
     * Restarts SCO-Audio if VOIP call is active.
     * when set mode is  3
     */
    /*package*/ void restartScoInVoipCall() {
        int mode = mAudioService.getMode();
        if (mode != AudioSystem.MODE_IN_COMMUNICATION) {
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.v(TAG, "restartScoInVoipCall -  voip call is not active," +
                    "ignore restarting");
            }
            return;
        }
        AudioDeviceInfo mActiveCommunicationDevice = getCommunicationDevice();
        if (mActiveCommunicationDevice != null) {
            //check whether this project have earpiece or not first.
            ArrayList<AudioPort> ports = new ArrayList<AudioPort>();
            int[] portGeneration = new int[1];
            boolean isEarpieceSupport = false;
            int status = AudioSystem.listAudioPorts(ports, portGeneration);
            if (status != AudioManager.SUCCESS) {
                Log.e(TAG, "listAudioPorts error " + status + " in configureHdmiPlugIntent");
                return;
            }
            for (AudioPort port : ports) {
                if (!(port instanceof AudioDevicePort)) {
                    continue;
                }
                AudioDevicePort devicePort = (AudioDevicePort) port;
                if (devicePort.type() == AudioManager.DEVICE_OUT_EARPIECE) {
                    isEarpieceSupport = true;
                    Log.v(TAG, "earpiece project found");
                    break;
                }
            }
            int deviceType = mActiveCommunicationDevice.getType();
            if (deviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && isEarpieceSupport) {
                if (AudioServiceExtImpl.DEBUG_DEVICES) {
                    Log.v(TAG, "restartScoInVoipCall - Speaker is ON," +
                    "Skip SCO restart");
                }
                return;
            }
        }
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "restartScoInVoipCall - restarting SCO,");
        }
        postRestartScoInCall(BT_RESTART_SCO_AUDIO_TIMEOUT_MS);
    }

    private void postRestartScoInCall(int delay) {
        sendIMsg(MSG_I_SET_RESTART_SCO_AUDIO, SENDMSG_REPLACE, 0, delay);
    }

   private void onRestartScoInVoipCall() {
        int mScoAudioMode = 0;
        int mode = mAudioService.getMode();
        int pid = 0;
        IBinder mCb = null;
        isInCallHfpTOCgSwitch = false;
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "onRestartScoInVoipCall - restarting SCO," + mode);
        }
        switch (mode) {
            case AudioSystem.MODE_IN_COMMUNICATION:
                mScoAudioMode = BtHelperExt.LE_CG_MODE_VIRTUAL_CALL;
                break;
            default:
                return;
        }
        pid = mAudioServiceExtImpl.getModePid();
        Object client; /* CommunicationRouteClient*/
        client = getCommunicationRouteClientForUid(Process.getUidForPid(pid));

        // when Communication client is not present.
        // During Telecom call, Telecom starts HFP-SCO
        // In this case, Communication client will not be
        // present
        if (client == null) {
            mCb = mAudioServiceExtImpl.getModeCb();
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.v(TAG, " when no client mCb," + mCb + " pid:" + pid);
            }
        }
        if (client != null) {
            Method getBinderMethod = ReflectionHelper.getMethod(client,
                               "getBinder");
            if (getBinderMethod == null) {
                Log.w(TAG, "restartCgInCall" +
                    ",getBinderMethod=" + getBinderMethod);
                return;
            }
            mCb = (IBinder) ReflectionHelper.callMethod(getBinderMethod,
                        client);
        }
        if (pid == 0 || mCb == null) {
            if (AudioServiceExtImpl.DEBUG_DEVICES) {
                Log.w(TAG, "onRestartScoInCall pid or mCb is invalid" +
                    ", Pid=" + pid + ", mCb=" + mCb);
            }
            return;
        }
        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.d(TAG, "onRestartScoInCall" +
                        ", pid=" + pid + ", mCb=" + mCb);
        }

        startBluetoothScoForClient(mCb, pid, mScoAudioMode, "cf to hfp switch");
    }

    private void startBluetoothScoForClient(IBinder cb, int pid, int scoAudioMode,
                @NonNull String eventSource) {

        if (AudioServiceExtImpl.DEBUG_DEVICES) {
            Log.v(TAG, "startBluetoothScoForClient_Sync, pid: " + pid);
        }
        int uid = Process.getUidForPid(pid);
        boolean isPrivileged = mContext.checkPermission(android.Manifest.permission.MODIFY_PHONE_STATE,
                    pid, uid) == PackageManager.PERMISSION_GRANTED;
        ReflectionHelper.callMethod(startBluetoothScoForClientMethod,
            mDeviceBroker, cb, getAttributionSource(uid, pid), scoAudioMode, isPrivileged,
            eventSource);
    }

    /**
     * check ble record is need restart or not
     */
    public void restartBleRecord() {
        if (isBleHDRecordActive()) {
            mBtHelperExt.startBluetoothLeCg(
                BtHelperExt.LE_CG_MODE_HD_VIRTUAL_CALL, "restartBleRecord");
        } else if (!isBleRecordingIdle()) {
            mBtHelperExt.startBluetoothLeCg(
                BtHelperExt.LE_CG_MODE_NORMAL_VIRTUAL_CALL, "restartBleRecord");
        }
    }

    /**
     * Notify cg audio state.
     * @param state is cg on
     */
    public void notifyCgState(boolean state) {
        mBtHelperExt.notifyCgState(state);
    }
}
