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

package com.mediatek.boostfwk.info;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Slog;

import com.mediatek.boostfwk.utils.LogUtil;

import java.util.ArrayList;

import vendor.mediatek.hardware.mbrain.IMBrain;
import vendor.mediatek.hardware.mbrain.IMBrainCallbacks;
import vendor.mediatek.hardware.mbrain.MBrain_CallbackDataType;
import vendor.mediatek.hardware.mbrain.MBrain_CallbackRegisterData;
import vendor.mediatek.hardware.mbrain.MBrain_CallbackConfigData;
import vendor.mediatek.hardware.mbrain.MBrain_Event;
import vendor.mediatek.hardware.mbrain.MBrain_Parcelable;
import org.json.JSONException;
import org.json.JSONObject;

public class ScrollMbrainSync {
    private static final String TAG = "ScrollMbrainSync";
    private static final String SERVICE_NAME = IMBrain.DESCRIPTOR + "/default";
    private static final int MSG_SEND_INFO = 1;
    private static final int DATA_SEND_THRESHOLD = 10;

    private MBrain_Parcelable mData;
    private IMBrain mBrain;
    private IMBrainCallbacks mCallback;
    private MBrain_CallbackConfigData mConfig;
    private boolean mServiceEnable = false;
    private boolean mStartHandler = false;

    private HandlerThread mHandlerThread = new HandlerThread("DebugT3SBE");
    private Handler mHandler;

    private ArrayList<T3Data> mT3Datas = new ArrayList<>();

    private static volatile ScrollMbrainSync sInstance = null;

    public static ScrollMbrainSync getInstance() {
        if (null == sInstance) {
            synchronized (ScrollMbrainSync.class) {
                if (null == sInstance) {
                    sInstance = new ScrollMbrainSync();
                }
            }
        }
        return sInstance;
    }

    public ScrollMbrainSync() {
        initMBrainHook();
    }

    private void initMBrainHook() {
        IMBrain mbrain = getService();
        if (mbrain != null) {
            mServiceEnable = getServiceAvaliable(mbrain);
        }
        if (mServiceEnable && mBrain != null) {
            Slog.d(TAG, "Init MbrainDebugManagerImpl");
            mData = new MBrain_Parcelable();
            registerCallbackByHashID();
        }
    }

    private void registerCallbackByHashID() {
        int registerToken = -1;
        try {
            MBrain_CallbackRegisterData data = new MBrain_CallbackRegisterData();
            data.registerEvent = MBrain_Event.MB_UX_SET_INFO;
            data.registerSubType = android.os.Process.myPid();
            data.registerData = "";
            registerToken = mBrain.GetRegisterToken(data);
        } catch (RemoteException e) {
            Slog.d(TAG, "GetRegisterToken failed");
        }

        if (registerToken == -1)
            return;

        try {
            IMBrainCallbacks callback = new MBrainCallback();
            MBrain_CallbackConfigData config = new MBrain_CallbackConfigData();
            config.configToken = registerToken;
            config.configSubToken = android.os.Process.myPid();
            config.configData = "";
            mBrain.RegisterCallbackByHashID(config, callback);
            Slog.d(TAG, "RegisterCallbackByHashID  success");
            mCallback = callback;
            mConfig = config;
        } catch (RemoteException e) {
            Slog.d(TAG, "RegisterCallbackByHashID failed");
        }
    }

    public static void tryunRegisterCallback() {
        if (sInstance != null) {
            sInstance.unRegisterCallback();
        }
    }

    public void unRegisterCallback() {
        if (mConfig == null || mCallback == null || mBrain == null) {
            return;
        }
        Slog.d(TAG, "MBrainCallback unRegisterCallback");
        try {
            mBrain.UnRegisterCallbackByHashID(mConfig, mCallback);
            mConfig = null;
            mCallback = null;
        } catch (RemoteException e) {
            Slog.d(TAG, "UnRegisterCallbackByHashID failed");
        }
    }

    private IMBrain getService() {
        IMBrain newService;
        newService = IMBrain.Stub.asInterface(ServiceManager.checkService(SERVICE_NAME));
        if (newService == null) {
            LogUtil.mLoge(TAG, "getService failed");
        } else {
            LogUtil.mLogd(TAG, "getService success");
        }
        mBrain = newService;
        return newService;
    }

    private boolean getServiceAvaliable(IMBrain service) {
        if (service == null) {
            return false;
        }

        try {
            if (LogUtil.DEBUG) {
                LogUtil.mLogd(TAG, "IsMBrainSupport=" + (service.IsMBrainSupport() == 1));
            }
            return (service.IsMBrainSupport() == 1);
        } catch (RemoteException | ServiceSpecificException ex) {
            Slog.i(TAG, "Cannot call update on mbrain AIDL", ex);
            return false;
        } catch (Exception ex) {
            Slog.i(TAG, "unknown mbrain AIDL exception", ex);
            return false;
        }
    }

    private class MBrainCallback extends IMBrainCallbacks.Stub {
        @Override
        public boolean notifyToClient(String callbackData) throws RemoteException {
            try {
                JSONObject callbackObj = new JSONObject(callbackData);
                int touchDebugInfo = callbackObj.optInt("touchdebug", -1);
                // mIsTouchDebugEnabled = (touchDebugInfo == 1) ? true : false;
                // MbrainDebugManager.DEBUG_T2 = mIsTouchDebugEnabled;
                Slog.d(TAG, "MBrainCallback notifyToClient , touchDebugInfo = " + touchDebugInfo);
            } catch (JSONException e) {
                Slog.d(TAG, "invlaid callback data: " + e.toString());
            }
            return true;
        }

        @Override
        public boolean getMessgeFromClient(MBrain_Parcelable inputData) throws RemoteException {
            return true;
        }

        @Override
        public boolean getDataFromClient(MBrain_CallbackDataType cbData) throws RemoteException {
            return true;
        }

        @Override
        public String getInterfaceHash() {
            return IMBrainCallbacks.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IMBrainCallbacks.VERSION;
        }
    }

    public static class T3Data {
        public long mScrollStartTime = 0;// scrollstart time
        public long mScrollEndTime = 0; // scroll end time
        public long mRenderTid = 0;// renderthread tid
        public int mScrollType = 0;// 0:fling scroll, 1 :press scroll

        // data fomrat:[mScrollStartTime,mScrollEndTime,mRenderTid,mScrollType]
        public StringBuilder getMBrainData() {
            StringBuilder data = new StringBuilder("[");
            data.append(mScrollStartTime);
            data.append(",");
            data.append(mScrollEndTime);
            data.append(",");
            data.append(mRenderTid);
            data.append(",");
            data.append(mScrollType);
            data.append("]");
            return data;
        }

        public T3Data getCopy() {
            T3Data t = new T3Data();
            t.mScrollStartTime = mScrollStartTime;
            t.mScrollEndTime = mScrollEndTime;
            t.mRenderTid = mRenderTid;
            t.mScrollType = mScrollType;
            return t;
        }

        public void clear() {
            mScrollStartTime = 0;
            mScrollEndTime = 0;
            mRenderTid = 0;
            mScrollType = 0;
        }
    }

    public void onSendToMbrain(T3Data data) {
        if (data != null) {
            mT3Datas.add(data);
        }
        checkSendDataOrNot();
    }

    private void notifyMBrain(IMBrain brain) {
        if (brain == null) {
            LogUtil.mLogi(TAG, "mbrain is null, cancel this send");
            //in case data size too bigger
            mT3Datas.clear();
            return;
        }
        // pack the data as json format to mbrain
        int pid = android.os.Process.myPid();
        StringBuilder stb = new StringBuilder("{\"timestamp\": " + System.currentTimeMillis() +
                ", \"SBE\": {\"" + pid + "\":[");
        if (!mT3Datas.isEmpty()) {
            for (T3Data data : mT3Datas) {
                stb.append(data.getMBrainData() + ",");
            }
            stb.deleteCharAt(stb.length() - 1);
            stb.append("]}}");
        }

        try {
            if (mData != null) {
                mData.eventHint = 0;
                mData.privData = stb.toString();

                if (LogUtil.DEBUG) {
                    Slog.d(TAG, "sent data at last: " + mData.privData);
                }
                brain.Notify(MBrain_Event.MB_UX_SET_INFO, mData);
                mT3Datas.clear();
            }
        } catch (RemoteException | ServiceSpecificException ex) {
            initMBrainHook();
            if (LogUtil.DEBUG) {
                Slog.e(TAG, "Cannot call update on AIDL", ex);
            }
        } catch (Exception ex) {
            if (LogUtil.DEBUG) {
                Slog.e(TAG, "unknown AIDL exception", ex);
            }
        } finally {
            // Slog.d(TAG, "MBrainServiceWrapper notifyEventToNativeService end");
        }
    }

    private void checkSendDataOrNot() {
        int size = mT3Datas.size();
        if (LogUtil.DEBUG) {
            Slog.d(TAG, "current data SIZE = " + size);
        }
        if (size >= DATA_SEND_THRESHOLD) {
            startHandler();
            mHandler.sendEmptyMessage(MSG_SEND_INFO);
        }
    }

    /**
     * check if send fail, retry after 5 seconds
     */
    private void startHandler() {
        if (mStartHandler) {
            return;
        }
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SEND_INFO:
                        if (LogUtil.DEBUG) {
                            Slog.d(TAG, "still MSG_SEND_INFO ,less than THRESHOLD");
                        }
                        notifyMBrain(mBrain);
                        break;
                    default:
                        break;
                }
            }
        };
        mStartHandler = true;
    }
}
