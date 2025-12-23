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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import static android.os.Process.FIRST_APPLICATION_UID;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioSystem;
import android.media.IPlaybackConfigDispatcher;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Log;
import com.android.internal.annotations.GuardedBy;
import com.android.server.audio.AudioService;
import com.android.server.audio.AudioSystemAdapter;
import com.android.server.audio.PlaybackActivityMonitor;
import com.android.server.audio.SystemServerAdapter;

import com.mediatek.bt.BluetoothLeCallControl;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

/**
 * The implementation of additional features for BLE Audio
 * <p>
 * This implementation focuses on BLE-UMS/CG device connect/disconnects, BLE status providers
 * asynchronous to external calls and the task runs in AOSP- AudioServices threads only
 * It uses AOSP locks
 *
 * @hide
 */
public class AudioServiceExtImpl extends AudioServiceExt {

    private static final String TAG = "AS.AudioServiceExt";

    /// M: Add for control debug log, only default enable it on eng/userdebug load.
    protected static final boolean LOGD = "eng".equals(Build.TYPE)
            || "userdebug".equals(Build.TYPE);

    /** Debug audio mode. */
    protected static final boolean DEBUG_MODE = false  || LOGD;

    /** Debug audio policy feature. */
    protected static final boolean DEBUG_AP = false  || LOGD;

    /** Debug volumes */
    protected static final boolean DEBUG_VOL = false  || LOGD;

    /** debug calls to devices APIs. */
    protected static final boolean DEBUG_DEVICES = false  || LOGD;

    private boolean mIsBleCgFeatureSupported = false;

   // If absolute volume/VC profile is supported in LE device
    private volatile boolean mLeVcSupportsAbsoluteVolume = false;
    private  AudioDeviceBrokerExt mDeviceBrokerExt = null;
    private  static AudioService sAudioService = null;
    private  static Method sSendMsgMethod = null;
    private  static Field sAudioHandlerField = null;
    private  Handler mAudioHandler = null;
    private  Field mStreamStatesField = null;
    private  Field mPlaybackMonitorField = null;
    private  Field mVoiceActivityMonitorField = null;
    private  PlaybackActivityMonitor mPlaybackMonitor;
    private  IPlaybackConfigDispatcher mVoiceActivityMonitor;

    private Object mPlaybackMonitorObj;
    private Object mVoiceActivityMonitorObj;
    private Object mStreamStatesObj = null;
    private Method registerPlaybackCallbackMethod = null;
    private Method unregisterPlaybackCallbackMethod = null;
    private Method getAudioModeOwnerHandlerMethod = null;

    private @NonNull Context mContext;
    private AudioServiceExtImpl mAudioServiceExtImpl = null;
    private AtomicBoolean mVoicePlaybackActive = null;
    private AtomicInteger mMode;

    private Field mleCallVcSupportsAbsoluteVolumeField = null;
    private Field mleVcSupportsAbsoluteVolumeField = null;

    /**
     * @hide
     * The states that can be used with AudioService.setBluetoothHearingAidDeviceConnectionState()
     * and AudioService.setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent()
     */
    @IntDef({
            BluetoothProfile.STATE_DISCONNECTED,
            BluetoothProfile.STATE_CONNECTED,
    })

    @Retention(RetentionPolicy.SOURCE)
    public @interface BtProfileConnectionState {}

    /*package*/ static final int CONNECTION_STATE_DISCONNECTED = 0;
    /*package*/ static final int CONNECTION_STATE_CONNECTED = 1;

    /**
     * The states that can be used with AudioService.setWiredDeviceConnectionState()
     * Attention: those values differ from those in BluetoothProfile, follow annotations to
     * distinguish between @ConnectionState and @BtProfileConnectionState
     */
    @IntDef({
            CONNECTION_STATE_DISCONNECTED,
            CONNECTION_STATE_CONNECTED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConnectionState {}

    public AudioServiceExtImpl() {
        if (LOGD) {
            Log.d(TAG, "AudioServiceExtImpl");
        }
        String LEAUDIO_MODE_PROPERTY = "persist.vendor.bluetooth.leaudio_mode";
        String SUPPORT_LEAUDIO_CG_MODE = "ums-cg";
        if (SystemProperties.get(LEAUDIO_MODE_PROPERTY, "")
            .equalsIgnoreCase(SUPPORT_LEAUDIO_CG_MODE)) {
            mIsBleCgFeatureSupported = true;
        }
        if (LOGD) {
           Log.d(TAG, "isBleCgFeatureSupported() status="
            + mIsBleCgFeatureSupported);
        }
    }

    @Override
    public boolean isBleAudioFeatureSupported() {
        boolean status = true;
        if (LOGD && !status) {
           Log.d(TAG, "isBleAudioEnable() status=" + status);
        }
        return status;
    }

    public boolean isBleCgFeatureSupported() {
        return mIsBleCgFeatureSupported;
    }

    @Override
    public void init(Context context, AudioService audioService,
                AudioSystemAdapter audioSystem,
                SystemServerAdapter systemServer,
                Object deviceBroker) {
        if (LOGD) {
            Log.d(TAG, "init()--context=" + context
                    +",audioService=" + audioService
                    +", audioSystem=" + audioSystem
                    +",systemServer" + systemServer
                    +",deviceBroker" + deviceBroker);
        }
        sAudioService = audioService;
        mContext = context;
        mDeviceBrokerExt = new AudioDeviceBrokerExt(context, audioService, this, audioSystem,
                                                    deviceBroker, systemServer);

        sSendMsgMethod = ReflectionHelper.getMethod(audioService,
                                           "sendMsg",
                                            Handler.class /*handler*/,
                                            int.class /*msg*/,
                                            int.class /*existingMsgPolicy*/,
                                            int.class /*arg1*/,
                                            int.class /*arg2*/,
                                            Object.class /*obj*/,
                                            int.class /*delay*/);
        mAudioHandler = (Handler) ReflectionHelper.getFieldObject(audioService,
                                       "mAudioHandler");
        mMode       = (AtomicInteger) ReflectionHelper.getFieldObject(audioService,
                                       "mMode");
        mVoicePlaybackActive = (AtomicBoolean) ReflectionHelper.getFieldObject(audioService,
                                       "mVoicePlaybackActive");
        mleCallVcSupportsAbsoluteVolumeField  = ReflectionHelper.getField(audioService,
                                       "mleCallVcSupportsAbsoluteVolume");
        mleVcSupportsAbsoluteVolumeField = ReflectionHelper.getField(audioService,
                                       "mleVcSupportsAbsoluteVolume");
        getAudioModeOwnerHandlerMethod = ReflectionHelper.getMethod(audioService,
                                                    "getAudioModeOwnerHandler");
        mIsSystemReadyStatus = true;
        if (LOGD) {
            Log.d(TAG, "init()--Done");
        }
    }


    /*package*/ void setBleCallVcSupportsAbsoluteVolume(boolean support) {
        if (LOGD) {
            Log.d(TAG, "setBleCallVcSupportsAbsoluteVolume() support="
                + support);
        }
        ReflectionHelper.callSetBoolean(
                mleCallVcSupportsAbsoluteVolumeField,
                sAudioService,
                support);
    }

    /**  */
    @Override
    public void onReceiveExt(Context context, Intent intent) {
       if (intent == null || context == null) {
           Log.e(TAG, "onReceiveExt returned Intent or context is null");
           return;
       }
       final String action = intent.getAction();
       int outDevice;
       int inDevice;
       int state;
       if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
           if (LOGD) {
               Log.d(TAG, "onReceiveExt action=" + action);
           }
           state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
           if (state == BluetoothAdapter.STATE_OFF ||
                   state == BluetoothAdapter.STATE_TURNING_OFF) {
               mDeviceBrokerExt.disconnectAllBluetoothProfiles();
           }
       } else if (action.equals(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED)
                   || action.equals(BluetoothLeCallControl.ACTION_AUDIO_STATE_CHANGED)) {

           if (isBleCgFeatureSupported()) {
               if (LOGD) {
                   Log.d(TAG, "onReceiveExt action=" + action);
               }
               mDeviceBrokerExt.receiveBtEvent(intent);
               return;
           }
           if (LOGD) {
               Log.d(TAG, "onReceiveExt skipped CG intents action="
                       + action);
           }
       }
    }

    @Override
    public IBinder getModeCb() {
        IBinder mCb = null;
        Object currentModeHandlerObject = (Object) ReflectionHelper
                                                        .callMethod(
                                                        getAudioModeOwnerHandlerMethod,
                                                         sAudioService);
        if (currentModeHandlerObject != null) {
            Method getBinderMethod = ReflectionHelper
                                        .getMethod(currentModeHandlerObject,
                                                    "getBinder");
            if (getBinderMethod != null) {
                mCb = (IBinder) ReflectionHelper.callMethod(getBinderMethod,
                                                currentModeHandlerObject);
                return mCb;
            }
        }
        return mCb;
    }

    /*package*/ int getModePid() {
        int mModePid = 0;
        Object currentModeHandlerObject = (Object) ReflectionHelper
                                                        .callMethod(
                                                        getAudioModeOwnerHandlerMethod,
                                                         sAudioService);
        if (currentModeHandlerObject != null) {
            Method getPidMethod = ReflectionHelper
                                    .getMethod(currentModeHandlerObject,
                                                    "getPid");
            if (getPidMethod != null) {
                mModePid = (int) ReflectionHelper.callMethod(getPidMethod,
                                                currentModeHandlerObject);
                return mModePid;
            }
        }
        return mModePid;
    }

    /**  */
    @Override
    public void getBleIntentFilters(IntentFilter intentFilter) {
        if (!isBleCgFeatureSupported()) {
            return;
        }
        int filtersCountBefore = intentFilter.countActions();
        intentFilter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED);
        intentFilter.addAction(BluetoothLeCallControl.ACTION_AUDIO_STATE_CHANGED);
        if (LOGD) {
            Log.d(TAG, "getBleIntentFilters() Before=" + filtersCountBefore
                + ",after=" + intentFilter.countActions());
        }
    }

    /**  */
    @Override
    public void onSystemReadyExt() {
        if (LOGD) {
            Log.d(TAG, "onSystemReadyExt()");
        }
        mDeviceBrokerExt.onSystemReady();
    }

    /** @see AudioManager#isBluetoothLeOn() */
    @Override
    public boolean isBluetoothLeOn() {
        if (mDeviceBrokerExt == null) {
            return false;
        }
        return mDeviceBrokerExt.isBluetoothLeOn();
    }

    /** @see AudioManager#isBluetoothLeCgOn() */
    @Override
    public boolean isBluetoothLeCgOn() {
        if (!isBleCgFeatureSupported() || mDeviceBrokerExt == null) {
            return false;
        }
        boolean status = mDeviceBrokerExt.isBluetoothLeCgOn();
        if (LOGD) {
            Log.d(TAG, "isBluetoothLeCgOn()="+ status);
        }
        return status;
    }

    @Override
    public boolean isBluetoothLeCgStateOn() {
        return mDeviceBrokerExt.isBluetoothLeCgStateOn();
    }

    /** @see AudioManager#isBluetoothLeTbsDeviceActive() */
    @Override
    public boolean isBluetoothLeTbsDeviceActive() {
        if (!isBleCgFeatureSupported() || mDeviceBrokerExt == null) {
            return false;
        }
        boolean status = mDeviceBrokerExt.isBluetoothLeTbsDeviceActive();
        if (LOGD) {
            Log.d(TAG, "isBluetoothLeTbsDeviceActive()" + status);
        }
        return status;
    }

    @Override
    public void startBluetoothLeCg(IBinder cb, int targetSdkVersion) {
        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        final int leCgAudioMode =
                    (targetSdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR2) ?
                            BtHelperExt.LE_CG_MODE_VIRTUAL_CALL : BtHelperExt.LE_CG_MODE_UNDEFINED;
        final String eventSource = new StringBuilder("startBluetoothLeCg()")
                        .append(") from u/pid:").append(uid).append("/")
                        .append(pid).toString();
        Log.d(TAG, eventSource);
        startBluetoothLeCgInt(cb, pid, leCgAudioMode, eventSource);
    }

    @Override
    public void startBluetoothLeCg(int pid, int uid, int mode, IBinder cb) {
        if (LOGD) {
            Log.d(TAG, "startBluetoothLeCg setMode="
                + mode + ", pid=" + pid + ", uid =" + uid + ",cb =" + cb);
        }
        if (!isBluetoothLeTbsDeviceActive() || (cb == null)) {
            return;
        }
        if (mDeviceBrokerExt == null) {
            return;
        }
        switch (mode) {
            case AudioSystem.MODE_RINGTONE:
                if (mDeviceBrokerExt.isInbandRingingEnabled()) {
                    String callingApp =
                        mContext.getPackageManager()
                            .getNameForUid(uid);
                    if (callingApp.equals("com.android.server.telecom")
                        || callingApp.contains("android.uid.system")) {
                        if (LOGD) {
                            Log.d(TAG, "startBluetoothLeCg setMode="
                                + mode + ",pid=" + pid + ",cb =" + cb);
                        }
                        startBluetoothLeCgInt(cb, pid,
                            BtHelperExt.LE_CG_MODE_MD_CALL,
                            "onUpdateAudioModeExt");
                    }
                }
            break;
            case AudioSystem.MODE_IN_CALL:
                startBluetoothLeCgInt(cb, pid,
                    BtHelperExt.LE_CG_MODE_MD_CALL,
                    "onUpdateAudioModeExt");
                break;
            case AudioSystem.MODE_IN_COMMUNICATION:
                startBluetoothLeCgInt(cb, pid,
                    BtHelperExt.LE_CG_MODE_VIRTUAL_CALL,
                    "onUpdateAudioModeExt");
                break;
            case AudioSystem.MODE_NORMAL:
                stopBluetoothLeCg(cb);
                break;
            default:
                Log.w(TAG, "mode=" + mode);
                return;
        }
    }

    @Override
    public void stopBluetoothLeCgLater(IBinder cb){
        if (!isBleCgFeatureSupported() || mDeviceBrokerExt == null) {
            return;
        }
        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        final String eventSource = new StringBuilder("stopBluetoothLeCgLater()")
                        .append(") from u/pid:").append(uid).append("/")
                        .append(pid).toString();
        Log.d(TAG, eventSource);
        boolean status = false;
        final long ident = Binder.clearCallingIdentity();
        //status = mDeviceBrokerExt.stopBluetoothLeCgForClientLater(cb, pid, eventSource);
        status = mDeviceBrokerExt.stopBluetoothLeCgForClient(cb, pid, eventSource);
        Binder.restoreCallingIdentity(ident);
        return ;
    }

    @Override
    public boolean stopBluetoothLeCg(IBinder cb){
        if (!isBleCgFeatureSupported() || mDeviceBrokerExt == null) {
            return false;
        }
        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        final String eventSource = new StringBuilder("stopBluetoothLeCg()")
                        .append(") from u/pid:").append(uid).append("/")
                        .append(pid).toString();
        Log.d(TAG, eventSource);
        boolean status = false;
        final long ident = Binder.clearCallingIdentity();
        status = mDeviceBrokerExt.stopBluetoothLeCgForClient(cb, pid, eventSource);
        Binder.restoreCallingIdentity(ident);
        return status;
    }

   /* @Override
    public void startBluetoothLeCgVirtualCall(IBinder cb) {
        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        final String eventSource = new StringBuilder("startBluetoothLeCgVirtualCall()")
                .append(") from u/pid:").append(uid).append("/")
                .append(pid).toString();
        Log.d(TAG, eventSource);
        startBluetoothLeCgInt(cb, pid, BtHelperExt.LE_CG_MODE_VIRTUAL_CALL, eventSource);
    }*/

    void startBluetoothLeCgInt(IBinder cb, int pid, int LeAudioMode, @NonNull String eventSource) {
        final long ident = Binder.clearCallingIdentity();
        mDeviceBrokerExt.startBluetoothLeCgForClient(cb, pid, LeAudioMode, eventSource);
        Binder.restoreCallingIdentity(ident);
    }

    /* This is a temporary copy from hearing aid - adjust for le audio */
    /* int getLeAudioStreamType() {
        return getLeAudioStreamType(mMode.intValue());
    }*/

    /*package*/ boolean isInCall() {
        switch (mMode.intValue()) {
            case AudioSystem.MODE_IN_COMMUNICATION:
            case AudioSystem.MODE_IN_CALL:
                return true;
            case AudioSystem.MODE_NORMAL:
            default:
                return false;
        }
    }

    /*package*/ int getMode() {
        switch (mMode.intValue()) {
            case AudioSystem.MODE_IN_COMMUNICATION:
                return AudioSystem.MODE_IN_COMMUNICATION;
            case AudioSystem.MODE_IN_CALL:
                return AudioSystem.MODE_IN_CALL;
            case AudioSystem.MODE_NORMAL:
            default:
                return AudioSystem.MODE_NORMAL;
        }
    }

    /* private int getLeAudioStreamType(int mode) {
        switch (mode) {
            case AudioSystem.MODE_IN_COMMUNICATION:
            case AudioSystem.MODE_IN_CALL:
                return AudioSystem.STREAM_VOICE_CALL;
            case AudioSystem.MODE_NORMAL:
            default:
                // other conditions will influence the stream type choice, read on...
                break;
        }
        if (mVoicePlaybackActive.get()) {
            return AudioSystem.STREAM_VOICE_CALL;
        }
        return AudioSystem.STREAM_MUSIC;
    }*/

    private static void sendMsg(Handler handler, int msg,
            int existingMsgPolicy, int arg1, int arg2, Object obj, int delay) {

        ReflectionHelper.callMethod(sSendMsgMethod,
                                     sAudioService,
                                     sAudioHandlerField,
                                     msg,
                                     existingMsgPolicy,
                                     arg1,
                                     arg2,
                                     obj,
                                    delay);
    }

    int mBleCgVolume = 0;
    /**
    * returns BLE Cg Volume Index
    */
    /* @Override
    public int getBleCgVolume() {
        if (LOGD) {
            Log.d(TAG, "getBleCgVolume index="
                + mBleCgVolume);
        }
        return mBleCgVolume;
    } */

    /**
    * Sends BLE's index to  BLE device for call
    */
    /* @Override
    public void postSetLeCgVcIndex(int index) {
        if (mDeviceBrokerExt == null) {
            return ;
        }
        if (LOGD) {
            Log.d(TAG, "postSetLeCgVcIndex index="
                + index);
        }
        mBleCgVolume = index;
        mDeviceBrokerExt.postSetLeCgVcIndex(index);
    }*/

    @Override
    public void handleMessageExt(Message msg) {
        if (mDeviceBrokerExt == null) {
            return ;
        }
        if (LOGD) {
            Log.d(TAG, "handleMessageExt messageID="+ msg.what);
        }
        mDeviceBrokerExt.postHandleMessageExt(msg);
    }

    @Override
    public AudioDeviceAttributes preferredCommunicationDevice() {
        if (mDeviceBrokerExt == null || !isBleCgFeatureSupported()) {
            return null;
        }
        AudioDeviceAttributes device = mDeviceBrokerExt.preferredCommunicationDevice();
        if (LOGD) {
            Log.d(TAG, "preferredCommunicationDevice device=" + device);
        }
        return device;
    }
    public AudioDeviceAttributes getLeAudioDevice() {
        if (mDeviceBrokerExt == null || !isBleCgFeatureSupported()) {
            return null;
        }
        AudioDeviceAttributes device = mDeviceBrokerExt.getLeAudioDevice();
        if (LOGD) {
            Log.d(TAG, "getLeAudioDevice device=" + device);
        }
        return device;
    }

    @Override
    public boolean isBluetoothLeCgActive() {
        if (mDeviceBrokerExt == null || !isBleCgFeatureSupported()) {
            return false;
        }

        boolean deviceStatus = mDeviceBrokerExt.isBluetoothLeTbsDeviceActive();
        if (deviceStatus == false) {
            return false;
        }

        boolean status = mDeviceBrokerExt.isBluetoothLeCgActive();
        if (LOGD) {
            Log.d(TAG, "isBluetoothLeCgActive status=" + status);
        }
        return status;
    }

    private boolean mBtLeCgOnByApp;
    /* pakcage*/ void resetBluetoothLeCgOfApp() {
        mBtLeCgOnByApp = false;
    }

    @Override
    public void setBluetoothLeCgOn(boolean on) {
        if (mDeviceBrokerExt == null && !isBleCgFeatureSupported()) {
            mBtLeCgOnByApp = false;
            return;
        }
        // ALPS06163979- Few applications using deprecated setBluetoothScoOn API.
        // Like, apks sets setBluetoothScoOn(true) and checks SCO-Audio status using
        // isBluetoothScoOn() API
        // isBluetoothScoOn() returns as connected since application's SCO-Audio statue is set
        // and application skips calling startBluetoothSco API
        // Sol: To avoid this case,  don't update application's sco state,
        // and always return internal SCO –Audio state.

        // Only enable calls from system components
        if (UserHandle.getCallingAppId() >= FIRST_APPLICATION_UID) {
            //mBtLeCgOnByApp = on;
            return;
        }
        // for logging only
        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        final String eventSource = new StringBuilder("setBluetoothLeCgOn(").append(on)
                .append(") from u/pid:").append(uid).append("/").append(pid).toString();
        if (LOGD) {
            String callingApp =
                          mContext.getPackageManager().getNameForUid(Binder.getCallingUid());
            Log.d(TAG, eventSource + ", callingApp=" + callingApp);
        }
        mDeviceBrokerExt.setBluetoothLeCgOn(on, eventSource);
    }


    private boolean mIsSystemReadyStatus = false;
    @Override
    public boolean isSystemReady() {
        if (LOGD && !mIsSystemReadyStatus) {
            Log.d(TAG, "isSystemReady()" + mIsSystemReadyStatus);
        }
        return mIsSystemReadyStatus;
    }

     /**
     * Select device for use for communication use cases.
     * @param cb Client binder for death detection
     * @param pid Client pid
     * @param device Device selected or null to unselect.
     * @param eventSource for logging purposes
     */
    @Override
    public boolean setCommunicationDeviceExt(
            IBinder cb, int pid, AudioDeviceInfo device, String eventSource) {
        boolean result = true;
        if (!isBleCgFeatureSupported() || mDeviceBrokerExt == null) {
            return false;
        }
        String callingApp =
              mContext.getPackageManager().getNameForUid(Binder.getCallingUid());
        int LeCgAudioMode = BtHelperExt.LE_CG_MODE_VIRTUAL_CALL;
        boolean isBLEDevice = (device != null
                    && device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET);
        if (LOGD) {
            Log.d(TAG, "setCommunicationDevice callingApp=" + callingApp
            + ",isBLEDevice=" + isBLEDevice);
        }
        if (isBLEDevice) {
            if (callingApp.equals("com.android.server.telecom")
                 || callingApp.contains("android.uid.system")) {
                LeCgAudioMode = BtHelperExt.LE_CG_MODE_MD_CALL;
            } else {
                LeCgAudioMode = BtHelperExt.LE_CG_MODE_VIRTUAL_CALL;
            }
            startBluetoothLeCgInt(cb, pid, LeCgAudioMode, eventSource);
        } else if (device == null) {
            result = stopBluetoothLeCg(cb);
        }
        return result;
    }

    /**
     * SCO audio is restarted during VOIP call.
     */
    @Override
    public void restartScoInVoipCall() {
        if (mDeviceBrokerExt == null) {
            return;
        }
        mDeviceBrokerExt.restartScoInVoipCall();
    }

    /**
     * Sets HFP device as preferred device in advance .
     * inbandringing is supported.
     */
    @Override
    public void setPreferredDeviceForHfpInbandRinging(int pid, int uid, int mode,IBinder cb,
                                                      boolean enable) {
        // During incoming call, when BT-HFP device is connected which supports
        // inband feature and then events sequence will be
        // 1.Setmode=1 is set by telecom
        // 2.Ring tone's Audio Track is started and it is plays via A2DP + Speaker, firstly
        // 3.SCO device is set as preferred device.
        // Pop noisy is heard when Audio Track playing is switched
        // from A2DP + Speaker to SCO + Speaker devices, in case SCO connection
        // is delayed by HFP because of remote BT device. Pop noisy will not there
        // if SCO connection is faster, starts before Audio Track start playing via
        //  A2DP + Speaker.
        // Solution
        // Audio Manager checks if HFP device supports inband feature
        // and sets sco device as set preferred at
        // APM end in advance, before ACTION_AUDIO_STATE_CHANGED intent-12 connected is
        // received from HFP. (i.e actually SCO-Audio is connected)
        // In this way, pop noisy can be avoid due to switching
        // ringtone's audio track playing from A2DP + Speaker devices
        // to SCO + Speaker.
        if (isBluetoothLeTbsDeviceActive() || (cb == null) || (mDeviceBrokerExt == null)) {
            return;
        }

        if (mode == AudioSystem.MODE_RINGTONE && enable) {
            String callingApp =
                mContext.getPackageManager()
                    .getNameForUid(uid);
            if (callingApp.equals("com.android.server.telecom")
                || callingApp.contains("android.uid.system")) {
                if (LOGD) {
                    Log.d(TAG, "setPreferredDeviceForHfpInbandRinging, HFP in supported");
                }
                mDeviceBrokerExt
                    .postSetScoPreferredDeviceForStrategy();
            }
            return;
        }
    }

    @Override
    public void startBluetoothLeCgForRecord(IBinder cb, int uid, int sampleRate) {
        if (LOGD) {
            Log.d(TAG, "startBluetoothLeCgForRecord uid = " + uid
                    + ", sampleRate = " + sampleRate + ", cb = " + cb);
        }
        if (!isBluetoothLeTbsDeviceActive() || (cb == null)) {
            return;
        }
        if (mDeviceBrokerExt == null) {
            return;
        }
        if (sampleRate >= AudioService.HD_SAMPLE_RATE) {
            startBluetoothLeCgInt(cb, getPidForUid(uid),
                    BtHelperExt.LE_CG_MODE_HD_VIRTUAL_CALL,
                    "handleRecordingConfigurationChanged");
        } else {
            startBluetoothLeCgInt(cb, getPidForUid(uid),
                    BtHelperExt.LE_CG_MODE_NORMAL_VIRTUAL_CALL,
                    "handleRecordingConfigurationChanged");
        }
    }
    ///M: use packageName getPid for uid @{
    public int getPidForUid(int uid){
        String pkName = mContext.getPackageManager().getPackagesForUid(uid)[0];
        ActivityManager am = (ActivityManager) mContext
                              .getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> list = am.getRunningAppProcesses();
        for (RunningAppProcessInfo info: list) {
            if (info.processName.equalsIgnoreCase(pkName)) {
                return info.pid;
            }
        }
        return uid;
    }
    ///@}

    @Override
    public boolean stopBluetoothLeCgForRecord(IBinder cb, int uid) {
        if (LOGD) {
            Log.d(TAG, "stopBluetoothLeCgForRecord mode = "
                    + getMode() + ", uid =" + uid + ", cb = " + cb);
        }
        if (!isBleCgFeatureSupported() || mDeviceBrokerExt == null) {
            return false;
        }
        if (getMode() != AudioSystem.MODE_NORMAL) {
            return false;
        }
        final String eventSource = new StringBuilder("stopBluetoothLeCgForRecord()")
                .append(") from u/uid:").append(uid).append("/")
                .append(uid).toString();
        Log.d(TAG, eventSource);
        final long ident = Binder.clearCallingIdentity();
        boolean status = mDeviceBrokerExt
            .stopBluetoothLeCgForClient(cb, getPidForUid(uid), eventSource);
        Binder.restoreCallingIdentity(ident);
        return status;
    }

    /**
     * check ble record is need restart or not
     */
    @Override
    public void restartBleRecord() {
        if (LOGD) {
            Log.d(TAG, "restartBleRecord");
        }
        if (!isBluetoothLeTbsDeviceActive() || mDeviceBrokerExt == null) {
            return;
        }
        mDeviceBrokerExt.restartBleRecord();
    }

    @Override
    public void notifyCgState(boolean state) {
        mDeviceBrokerExt.notifyCgState(state);
    }
}
