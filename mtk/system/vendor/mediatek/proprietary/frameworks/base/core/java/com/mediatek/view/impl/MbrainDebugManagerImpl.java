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
package com.mediatek.view.impl;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Slog;
import android.util.Log;
import android.view.MotionEvent;

import com.mediatek.view.MbrainDebugManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import vendor.mediatek.hardware.mbrain.IMBrain;
import vendor.mediatek.hardware.mbrain.IMBrainCallbacks;
import vendor.mediatek.hardware.mbrain.MBrain_CallbackDataType;
import vendor.mediatek.hardware.mbrain.MBrain_CallbackRegisterData;
import vendor.mediatek.hardware.mbrain.MBrain_CallbackConfigData;
import vendor.mediatek.hardware.mbrain.MBrain_Event;
import vendor.mediatek.hardware.mbrain.MBrain_Parcelable;

public class MbrainDebugManagerImpl extends MbrainDebugManager{

    private static final int MSG_GET_INFO = 0;
    private static final int MSG_SEND_INFO = 1;
    private static final String TAG = "MbrainDebugManagerImpl";
    private static final String SERVICE_NAME = IMBrain.DESCRIPTOR + "/default";
    private static Object lock = new Object();

    private ArrayList<StringBuilder> mInputRecored = new ArrayList<>();
    private HandlerThread mHandlerThread = new HandlerThread("DebugT2");
    private Handler mHandler;
    private IMBrain mBrain;
    private IMBrainCallbacks mCallback;
    private MBrain_CallbackConfigData mConfig;
    private MBrain_Parcelable mData;
    private Set<Integer> mViewRootVisible = new HashSet<>();
    private boolean mServiceEnable = false;
    private boolean mStartHandler = false;
    private boolean mTimeOut = true;
    private boolean mIsTouchDebugEnabled = false;
    private ArrayList<T2Data> mT2Datas = new ArrayList<>();
    private T2Data mTmpT2Data = new T2Data();
    private boolean mInTouching = false;
    private boolean mInDoFrame = false;
    private int mRefreshRate = 0;

    public MbrainDebugManagerImpl() {
        startHandler();
        mHandler.post(() -> {
            if (!initMBrainHook()) {
                stopHandler();
            }
        });

    }

    private boolean initMBrainHook() {
       IMBrain mbrain = getService();
       if (mbrain != null) {
           mServiceEnable = getServiceAvaliable(mbrain);
        }
       if (mServiceEnable && mBrain != null) {
            Log.d(TAG,"Init MbrainDebugManagerImpl");
            mData = new MBrain_Parcelable();
            registerCallbackByHashID();
            return true;
        }
        return false;
    }

    private void registerCallbackByHashID() {
        int registerToken = -1;
        try {
            MBrain_CallbackRegisterData data = new MBrain_CallbackRegisterData();
            data.registerEvent = MBrain_Event.MB_UX_SET_INFO;
            data.registerSubType = android.os.Process.myPid();
            data.registerData = "";
            registerToken = mBrain.GetRegisterToken(data);
        } catch (Exception e) {
            Slog.d(TAG, "GetRegisterToken failed", e);
        }

        if (registerToken == -1) {
            Log.d(TAG,"registerToken==-1 return..");
            return;
        }

        try {
            IMBrainCallbacks callback = new MBrainCallback();
            MBrain_CallbackConfigData config = new MBrain_CallbackConfigData();
            config.configToken = registerToken;
            config.configSubToken = android.os.Process.myPid();
            config.configData = "";
            mBrain.RegisterCallbackByHashID(config, callback);
            mCallback = callback;
            mConfig = config;
        } catch (RemoteException e) {
            Slog.d(TAG, "RegisterCallbackByHashID failed");
        }
    }

    private void getData(StringBuilder sb) {
        //long now = SystemClock.uptimeMillis();
        mInputRecored.add(sb);
        if (mInputRecored.size() >= 20) {
            sendData();
        }
        if (mTimeOut) {
            startHandler();
            mHandler.sendEmptyMessageDelayed(MSG_SEND_INFO, 5000);
            mTimeOut = false;
        }
    }

    private void startHandler() {
        if (!mStartHandler) {
            synchronized (lock) {
                if (!mStartHandler) {
                    mHandlerThread.start();
                    mHandler = new Handler(mHandlerThread.getLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            switch (msg.what) {
                            case MSG_GET_INFO:
                                Log.d(TAG, "MSG_GET_INFO");
                                break;
                            case MSG_SEND_INFO:
                                Log.d(TAG, "MSG_SEND_INFO");
                                sendData();
                                break;
                            default:
                                break;
                            }
                        }
                    };
                    mStartHandler = true;
                }
            }
        }
    }

    private void stopHandler() {
        if (mStartHandler) {
            mHandlerThread.quitSafely();
            mStartHandler = false;
        }
    }

    private void sendData() {
        notifyMBrain(mBrain);
        mTimeOut = true;
    }

    private void notifyMBrain(IMBrain brain) {
        if (mTimeOut) {
          Log.d(TAG,"notifyMBrain return");
          return;
        }

        int pid = android.os.Process.myPid();
        StringBuilder stb = new StringBuilder("{\"timestamp\": " + System.currentTimeMillis() +
                                             ", \"T2\": {\""+pid+"\":[");
        if (!mInputRecored.isEmpty()) {
            for (StringBuilder item: mInputRecored) {
                stb.append(item + ",");
            }
            stb.deleteCharAt(stb.length() - 1);
            stb.append("]}}");
        }

        try {
                mData.eventHint = 0;
                mData.privData = stb.toString();
                Log.d(TAG, mData.privData);
                brain.Notify(MBrain_Event.MB_UX_SET_INFO, mData);
                mInputRecored.clear();
            } catch (RemoteException | ServiceSpecificException ex) {
                initMBrainHook();
                Slog.e(TAG, "Cannot call update on AIDL", ex);
            } catch (Exception ex) {
                Slog.e(TAG, "unknown AIDL exception", ex);
            } finally {
                Slog.d(TAG, "MBrainServiceWrapper notifyEventToNativeService end");
            }
    }

    private IMBrain getService() {
        IMBrain newService;
        newService = IMBrain.Stub.asInterface(ServiceManager.checkService(SERVICE_NAME));
        if (newService == null) {
            Slog.d(TAG, "getService failed");
        } else {
            Slog.d(TAG, "getService");
        }
        mBrain = newService;
        return newService;
    }

    private boolean getServiceAvaliable(IMBrain service) {
        try {
                boolean mbrainSupport = service.IsMBrainSupport() == 1;
                Log.d(TAG,"IsMBrainSupport=" + mbrainSupport);
                return mbrainSupport;
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
                mIsTouchDebugEnabled = (touchDebugInfo == 1) ? true : false;
                MbrainDebugManager.DEBUG_T2 = mIsTouchDebugEnabled;
                Log.d(TAG, "MBrainCallback notifyToClient");
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

    private static class T2Data {
        private long mCurrentTimeMillis = -1;
        private int mEventId = -1;
        private int mPreEventId = -1;
        private long mVsyncId = -1;
        private long mTotal = -1;
        private long mEventTime = -1;
        private long mReadTime = -1;
        private long mDispatchTime = -1;
        private long mSendTime = -1;
        private long mOnVsyncTime = -1;
        private long mOnVsyncFrameTime = -1;
        private long mDoFrameStart = -1;
        private long mDoFrameEnd = -1;
        private long mPostInputCallTime = -1;
        private long mInputFrameTime = -1;
        private long mDeliverStart = -1;
        private long mDeliverEnd = -1;
        private int mDeviceId = -2;
        private int mAction = -2;
        private int mRefreshRate = -1;
        private int mAbnormal = -1;

        public StringBuilder getMBrainData(){
            StringBuilder data = new StringBuilder("[");
            data.append(mCurrentTimeMillis  + "," +
                    mEventId + "," +
                    mPreEventId + "," +
                    mVsyncId + "," +
                    mTotal + "," +
                    mEventTime + "," +
                    mReadTime + "," +
                    mDispatchTime + "," +
                    mSendTime + "," +
                    mPostInputCallTime + "," +
                    mOnVsyncTime + "," +
                    mOnVsyncFrameTime + "," +
                    mDoFrameStart + "," +
                    mInputFrameTime + "," +
                    mDeliverStart + "," +
                    mDeliverEnd + "," +
                    mDoFrameEnd + "," +
                    mDeviceId + "," +
                    mAction + "," +
                    mRefreshRate + "," +
                    mAbnormal +
                    "]"
            );
            return data;
        }

        public T2Data getCopy(){
            T2Data t = new T2Data();
            t.mCurrentTimeMillis = mCurrentTimeMillis;
            t.mEventId = mEventId;
            t.mPreEventId = mPreEventId;
            t.mVsyncId = mVsyncId;
            t.mTotal = mTotal;
            t.mEventTime = mEventTime;
            t.mReadTime = mReadTime;
            t.mDispatchTime = mDispatchTime;
            t.mSendTime = mSendTime;
            t.mOnVsyncTime = mOnVsyncTime;
            t.mOnVsyncFrameTime = mOnVsyncFrameTime;
            t.mDoFrameStart = mDoFrameStart;
            t.mDoFrameEnd = mDoFrameEnd;
            t.mPostInputCallTime = mPostInputCallTime;
            t.mInputFrameTime = mInputFrameTime;
            t.mDeliverStart = mDeliverStart;
            t.mDeliverEnd = mDeliverEnd;
            t.mDeviceId = mDeviceId;
            t.mAction = mAction;
            t.mRefreshRate = mRefreshRate;
            t.mAbnormal = mAbnormal;
            return t;
        }
    }

    private void onSendToMbrain(T2Data data) {
        int curEventId = data.mEventId;
        long timeout = Long.MAX_VALUE;
        if (data.mAction == MotionEvent.ACTION_DOWN ||
                data.mAction == MotionEvent.ACTION_UP) {
            timeout = 5000;
        } else if (data.mAction == MotionEvent.ACTION_MOVE && mRefreshRate > 0) {
            timeout = (long) (1000000 / mRefreshRate) + 7000;
        }
        if (data.mTotal / 1000 >= timeout && data.mTotal != -1) {
            data.mAbnormal = 1;
        }
        if (data.mAction == MotionEvent.ACTION_DOWN) {
            data.mPreEventId = -1;
        }

        StringBuilder curData = data.getMBrainData();
        String msg = "id=0x" + Integer.toHexString(data.mEventId) + curData;
        //Log.d(TAG, "onSendToMbrain: " + mT2Datas.size() + ": " + msg);
        if (Trace.isTagEnabled(Trace.TRACE_TAG_VIEW)) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg);
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }

        if (data.mAbnormal == 1) {
            ArrayList<T2Data> sendToMBrainDatas = new ArrayList<>();
            if (data.mAction == MotionEvent.ACTION_DOWN) {
                if (mT2Datas.size() > 0) {
                    sendToMBrainDatas.add(mT2Datas.get(mT2Datas.size() - 1));
                }
            } else {
                for (int i = mT2Datas.size() - 1; i >= 0; i--) {
                    T2Data t2Data = mT2Datas.get(i);
                    sendToMBrainDatas.add(t2Data);
                    if (t2Data.mDeliverStart > 0 || sendToMBrainDatas.size() > 4) {
                        break;
                    }
                }
            }
            for (int i = sendToMBrainDatas.size() - 1; i >= 0; i--) {
                getData(sendToMBrainDatas.get(i).getMBrainData());
            }
            getData(data.getMBrainData());
            mT2Datas.clear();
        } else {
            if (mT2Datas.size() >= 30) {
                ArrayList<T2Data> cacheDatas = new ArrayList<>();
                for (int i = mT2Datas.size() - 1; i >= 25; i--) {
                    cacheDatas.add(mT2Datas.get(i));
                }
                mT2Datas.clear();
                for (int i = cacheDatas.size() - 1; i >= 0; i--) {
                    mT2Datas.add(cacheDatas.get(i));
                }
            }
            mT2Datas.add(data);
        }
    }

    private void registerCallback() {
        if(mConfig == null && mCallback == null && mBrain != null) {
            registerCallbackByHashID();
        }
    }

    private void unRegisterCallback() {
        if(mConfig == null || mCallback == null || mBrain == null) {
            return;
        }
        Log.d(TAG, "MBrainCallback unRegisterCallback");
        try {
            mBrain.UnRegisterCallbackByHashID(mConfig, mCallback);
            mConfig = null;
            mCallback = null;
        } catch (RemoteException e) {
            Slog.d(TAG, "UnRegisterCallbackByHashID failed");
        }
    }

    /**
     * getOnVsyncTime
     *
     */
    @Override
    public void onDeliverInputEventStart(MotionEvent motionEvent) {
        int eventAction = motionEvent.getAction();
        if (eventAction == MotionEvent.ACTION_DOWN) {
            mInTouching = true;
        } else if (eventAction == MotionEvent.ACTION_UP ||
                eventAction == MotionEvent.ACTION_CANCEL) {
            mInTouching = false;
        }
        mTmpT2Data.mDeliverStart = SystemClock.uptimeNanos();
        mTmpT2Data.mCurrentTimeMillis = System.currentTimeMillis();
        mTmpT2Data.mEventTime = motionEvent.getEventTimeNanos();
        mTmpT2Data.mReadTime = motionEvent.getReadTimeNanos();
        mTmpT2Data.mDispatchTime = motionEvent.getDispatchTimeNanos();
        mTmpT2Data.mSendTime = motionEvent.getSendTimeNanos();
        mTmpT2Data.mDeviceId = motionEvent.getDeviceId();
        mTmpT2Data.mEventId = motionEvent.getId() & 0x0FFFFFFFF;
        mTmpT2Data.mAction = eventAction;
        if (motionEvent.getHistorySize() > 0) {
            mTmpT2Data.mEventTime = motionEvent.getHistoricalEventTimeNanos(0);
        }
        boolean isInjectEvent = motionEvent.getDeviceId() == -1 || mTmpT2Data.mReadTime == 0;
        mTmpT2Data.mTotal = isInjectEvent ? (mTmpT2Data.mDeliverStart - mTmpT2Data.mEventTime) :
                (mTmpT2Data.mDeliverStart - mTmpT2Data.mReadTime);
        mTmpT2Data.mRefreshRate = mRefreshRate;
    }

    @Override
    public void onDeliverInputEventEnd(MotionEvent motionEvent) {
        mTmpT2Data.mDeliverEnd = SystemClock.uptimeNanos();
        if (!mInDoFrame) {
            startHandler();
            final T2Data curT2Data = mTmpT2Data.getCopy();
            mHandler.post(() -> {
                onSendToMbrain(curT2Data);
            });
            int curEventId = mTmpT2Data.mEventId;
            mTmpT2Data = new T2Data();
            mTmpT2Data.mPreEventId = curEventId;
        }
    }

    @Override
    public void onInputFrameTime(long frameTime) {
        mTmpT2Data.mInputFrameTime = frameTime;
    }

    @Override
    public void onPostInputCallTime() {
        if (mTmpT2Data.mPostInputCallTime == -1) {
            mTmpT2Data.mPostInputCallTime = SystemClock.uptimeNanos();
        }
    }

    @Override
    public void onFrameStart(long frameTime) {
        mTmpT2Data.mCurrentTimeMillis = System.currentTimeMillis();
        mInDoFrame = true;
        mTmpT2Data.mDoFrameStart = SystemClock.uptimeNanos();
    }

    @Override
    public void onFrameEnd() {
        mTmpT2Data.mDoFrameEnd = SystemClock.uptimeNanos();
        startHandler();
        final T2Data curT2Data = mTmpT2Data.getCopy();
        mHandler.post(() -> {
            onSendToMbrain(curT2Data);
        });
        int curEventId = mTmpT2Data.mEventId;
        mTmpT2Data = new T2Data();
        mTmpT2Data.mPreEventId = curEventId;
        mInDoFrame = false;
    }

    @Override
    public void onInputVsync(long vsyncFrameTime, long vsyncId, int reshRate) {
        mRefreshRate = reshRate;
        mTmpT2Data.mVsyncId = vsyncId;
        mTmpT2Data.mOnVsyncFrameTime = vsyncFrameTime;
        mTmpT2Data.mOnVsyncTime = SystemClock.uptimeNanos();
    }

    @Override
    public void setViewRootVisible(int ViewRootImplTag, boolean visible) {
        if (visible) {
            mViewRootVisible.add(ViewRootImplTag);
        } else {
            mViewRootVisible.remove(ViewRootImplTag);
        }
        if (mBrain == null || !mServiceEnable) {
            return;
        }
        startHandler();
        if (visible) {
            mHandler.post(() -> {
                registerCallback();
            });
        } else {
            if (mViewRootVisible.size() == 0) {
                mHandler.post(() -> {
                    unRegisterCallback();
                });
            }
        }
    }

}
