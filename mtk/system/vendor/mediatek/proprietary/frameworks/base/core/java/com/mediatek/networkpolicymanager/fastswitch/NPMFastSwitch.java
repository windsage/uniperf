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

package com.mediatek.networkpolicymanager.fastswitch;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.mediatek.networkpolicymanager.INetworkPolicyService;
import com.mediatek.networkpolicymanager.NetworkPolicyManager;

import java.util.Map;

/** NPMFastSwitch exports fast switch APIs. */
public class NPMFastSwitch {
    private static final String TAG = "NPMFastSwitch";

    /* Singleton NPMFastSwitch instace */
    private volatile static NPMFastSwitch sInstance = null;
    private static final Object sNPMFastSwitchLock = new Object();

    private Context mContext = null;
    private ListenerThreadHandler mListenerHandler = null;
    private final Object mListenerThreadLock = new Object();
    private ListenerThread mListenerThread = null;
    private Message mGeneralReportCallback = null;
    private final Object mGeneralReportCallbackLock = new Object();
    private final long mReportWaitInMs = 10; // ms
    private final long mRequestWaitInMs = 500; // ms

    /** NPMFastSwitch constructor. */
    private NPMFastSwitch(Context context) {
        mContext = context;
        mListenerThread = new ListenerThread();
    }

    /**
     * get NPMFastSwitch instance, it is a singleton istance.
     *
     * @param context a context.
     * @return NPMFastSwitch service.
     * @hide
     */
    public static NPMFastSwitch getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sNPMFastSwitchLock) {
                if (sInstance == null) {
                    sInstance = new NPMFastSwitch(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Add IFastSwitchListener to monitor FastSwitchInfo notification.
     *
     * @param listener to listen FastSwitch related information.
     * @param service INetworkPolicyService service.
     * @param b Bundle object.
     */
    public void addFastSwitchListener(IFastSwitchListener listener,
            INetworkPolicyService service, Bundle b) {
        if (listener == null || service == null) {
            loge("[FastSwitch]: info is null:" + (listener == null) + ", service is null:"
                    + (service == null));
            return;
        }

        try {
            service.addFastSwitchListener(listener, b);
            synchronized (mListenerThreadLock) {
                if (mListenerThread.getState() == Thread.State.NEW) {
                    mListenerThread.start();
                } else if (mListenerThread.getState() == Thread.State.TERMINATED) {
                    loge("addFastSwitchListener: thread TERMINATED, re-new and start");
                    mListenerThread = new ListenerThread();
                    mListenerThread.start();
                } else {
                    logi("addFastSwitchListener: thread state: " + mListenerThread.getState());
                }
            }
        } catch (RemoteException e) {
            loge("[FastSwitch]: addFastSwitchListener fail: " + e);
        }
    }

    /**
     * Configure fast switch for the specified network without return valaue.
     * All functions with void return type can be implemented by info.mAction.
     *
     * @param info networkType and action in info are necessory.
     * @param service INetworkPolicyService service.
     */
    public void configFastSwitchInfo(FastSwitchInfo info, INetworkPolicyService service) {
        if (!isValid(info, service)) {
            return;
        }

        processGeneralAction(info, false);

        try {
            service.configFastSwitchInfo(info);
        } catch (RemoteException e) {
            loge("[FastSwitch]: configFastSwitchInfo fail: " + e);
        }
    }

    /**
     * Configure fast switch for the specified network with return int value.
     * For boolean value returned, use 0 indicateds false, != 0 indicates true.
     * All functions with int and boolean return type can be implemented by info.mAction.
     *
     * @param info networkType in info is necessory.
     * @param service INetworkPolicyService service.
     * @return response code corresponding to the specified action.
     */
    public int configFastSwitchInfoWithIntResult(FastSwitchInfo info,
            INetworkPolicyService service) {
        int ret = -1;
        boolean needWait = false;
        boolean waitResult = false;
        if (!isValid(info, service)) {
            return ret;
        }

        needWait = processGeneralAction(info, true);

        try {
            ret = service.configFastSwitchInfoWithIntResult(info);
            // ret == 0 is success for FastSwitchInfo.ACTION_REQ_GEN_ACT request, !0 is fail.
            if (needWait && ret == 0) {
                Bundle b = info.getBundle();
                String timeout = (b.containsKey(FastSwitchInfo.BK_TIMEOUT_INMS)
                        && (b.getString(FastSwitchInfo.BK_TIMEOUT_INMS) != null)) ?
                        b.getString(FastSwitchInfo.BK_TIMEOUT_INMS) : "-1";
                int timeoutInMs = 0;
                try {
                    timeoutInMs = Integer.valueOf(timeout);
                } catch (NumberFormatException e) {
                    loge("[FastSwitch]: configFastSwitchInfoWithIntResult e:" + e);
                }

                waitResult = sendRequest(info, (timeoutInMs > 0 ? timeoutInMs : mRequestWaitInMs));
                if (!waitResult) {
                    loge("[FastSwitch]: configFastSwitchInfoWithIntResult fail to wait callback, "
                            + "wait " + (timeoutInMs > 0 ? timeoutInMs : mRequestWaitInMs) + "ms");
                    ret = -1;
                }
            }
        } catch (RemoteException e) {
            loge("[FastSwitch]: configFastSwitchInfoWithIntResult fail: " + e);
        }
        return ret;
    }

    /**
     * Check whether FastSwitchInfo and INetworkPolicyService are not null.
     *
     * @param info to be checked.
     * @param service to be checked.
     * @return true for valid, false for invalid.
     */
    private boolean isValid(FastSwitchInfo info, INetworkPolicyService service) {
        if (info == null || service == null) {
            loge(true, "[FastSwitch]: info is null:" + (info == null) + ", service is null:"
                    + (service == null));
            return false;
        }

        if ((info.getNetworkType() >= FastSwitchInfo.NETWORK_TYPE_MAX)
                || (info.getNetworkType() < FastSwitchInfo.NETWORK_TYPE_BASE)) {
            loge(true, "[FastSwitch]: Unsupported group " + info.getNetworkType());
            return false;
        }
        return true;
    }

    /**
     * Stop the ListenerThread handling sub action in FastSwitchInfo.ACTION_REPORT_GEN because
     * Native service or framework binder service die.
     */
    public void stopGeneralReportListenerThread() {
        synchronized (mListenerThreadLock) {
            loge("stopGeneralReportListenerThread: " + mListenerThread.getState());
            if (!(mListenerThread.getState() == Thread.State.NEW
                    || mListenerThread.getState() == Thread.State.TERMINATED)) {
                mListenerThread.shutDown();
            }
            try {
                mListenerThread.join();
            } catch (InterruptedException e) {
                // Do nothing, go back and check if request is completed or timeout
            }
            mListenerThread.resetLooper();
            mListenerHandler = null;
            loge("stopGeneralReportListenerThread done: " + mListenerThread.getState());
        }
    }

    /**
     * Notify result of sub action in FastSwitchInfo.ACTION_REPORT_GEN to here for get Action.
     * This will unlock the "wait" of get flow to return the information
     * Note: nos service MUSTn't report FastSwitchInfo.ACTION_REPORT_GEN voluntarily.
     *
     * @param info reported FastSwitchInfo.
     */
    public void notifyGeneralReport(FastSwitchInfo info) {
        // avoid callback listener coming earlier than new request in ListenerThread
        // Why here don't use mGeneralReportCallbackLock to protect?
        // Because mGeneralReportCallback for new request is needed to make in ListenerThread.
        // This is not good design, but it is a necessary compromise.
        if (mGeneralReportCallback == null) {
            try {
                Thread.sleep(mReportWaitInMs);
            } catch (InterruptedException e) {
                // Do nothing, go back and check if request is completed or timeout
            }
            loge("[FastSwitch]: ACTION_REPORT_GEN comes earlier, wait " + mReportWaitInMs + "ms");
        }
        synchronized (mGeneralReportCallbackLock) {
            if (mGeneralReportCallback != null) {
                ListenerThreadRequest request = (ListenerThreadRequest) mGeneralReportCallback.obj;
                ListenerThreadRequest innerreq = (ListenerThreadRequest) request.argument;
                FastSwitchInfo orig = (FastSwitchInfo) innerreq.argument;
                if (mGeneralReportCallback.arg1 != FastSwitchInfo.ACTION_REQ_GEN_ACT
                        || orig.getAction() != FastSwitchInfo.ACTION_REQ_GEN_ACT
                        || info.getAction() != FastSwitchInfo.ACTION_REPORT_GEN) {
                    request.result = -1; // fail
                    notifyRequester(request);
                    loge("[FastSwitch]: actions mismatch. origact:" + orig.getAction()
                        + "  reportact:" + info.getAction()
                        + "  requestact:" + mGeneralReportCallback.arg1);
                    return;
                }
                request.result = 1; // success
                request.ret = info; // success
                notifyRequester(request);
            } else {
                loge("[FastSwitch]: mGeneralReportCallback is still null after "
                        + mReportWaitInMs + "ms");
            }
        }
    }

    /**
     * Reorganize bundle parameter for FastSwitchInfo.ACTION_REQ_GEN_ACT if it is a
     * configure(debug) operation.
     *
     * @param info networkType in info is necessory.
     * @param isGet whether it is get function (with return value).
     * @return true for valid and need wait result, false for invalid.
     */
    private boolean processGeneralAction(FastSwitchInfo info, boolean isGet) {
        Bundle b = info.getBundle();
        for (Map.Entry<String, Integer> entry : FastSwitchInfo.MAP_BKS_SUBACT.entrySet()) {
            String mKey = entry.getKey();
            if (!(mKey.equals(FastSwitchInfo.BK_PARM_ACT_EXT)) && b.containsKey(mKey)) {
                String bVal = b.getString(mKey);
                if (bVal != null) {
                    int mVal = entry.getValue();
                    String timeout;
                    if (bVal.startsWith(FastSwitchInfo.BV_PARM_QRY)) {
                        mVal += 1;
                    } else if (bVal.startsWith(FastSwitchInfo.BV_PARM_SVR)) {
                        mVal += 2;
                    }
                    b.putString(FastSwitchInfo.BK_PARM_ACT_EXT, String.valueOf(mVal));
                    info.resetBundle(b);
                    timeout = (b.containsKey(FastSwitchInfo.BK_TIMEOUT_INMS)
                            && (b.getString(FastSwitchInfo.BK_TIMEOUT_INMS) != null)) ?
                            b.getString(FastSwitchInfo.BK_TIMEOUT_INMS) : "";
                    logi("[FastSwitch]: config parameter: " + mKey + ", value: " + bVal
                            + ", subact: " + mVal + ", isGet: " + isGet + ", timeout: " + timeout);
                    if (isGet) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * A thread to run {@link ListenerThreadRequest}.
     */
    private final class ListenerThread extends Thread {
        /** Thread looper. */
        private Looper mLooper = null;
        /** The result of the request that run on the this thread. */
        @Override
        public void run() {
            Looper.prepare();
            mLooper = Looper.myLooper();
            synchronized (mListenerThreadLock) {
                mListenerHandler = new ListenerThreadHandler(mLooper);
            }
            Looper.loop();
        }
        /** Stop the thread. */
        public void shutDown() {
            if (mLooper != null) {
                mLooper.quitSafely();
            }
        }
        /** Reset looper. */
        public void resetLooper() {
            mLooper = null;
        }
    }

    /**
     * A request object for use with {@link ListenerThreadHandler}. Requesters should wait() on the
     * request after sending. The listener thread will notify the request when it is complete.
     * Listener thread is to wait sub report of ACTION_REPORT_GEN to get result of sub request of
     * ACTION_REQ_GEN_ACT
     */
    private static final class ListenerThreadRequest {
        /** The argument to use for the request. */
        public Object argument;
        /** The return value of the request that run on the listener thread. */
        public Object ret;
        /**
         * The result of the request that run on the listener thread.
         * -1 fail, 0 not finished, 1 success
         */
        public int result = 0;

        public ListenerThreadRequest(Object argument) {
            this.argument = argument;
        }
    }

    /**
     * A handler that processes messages on the wait thread in caller process. Since callers
     * need get result from listener that is a callback from framework service. So it is need to
     * shuttle the request from callers thread to the listener thread of callback.
     * The caller thread may provide a {@link ListenerThreadRequest} object in the msg.obj field
     * that they are waiting on, which will be notified when the operation completes and will
     * contain the result of request.
     *
     * <p>If a ListenerThreadRequest object is provided in the msg.obj field,
     * note that request.result must be set to -1 (fail) or 1 (success) for the calling thread to
     * unblock.
     */
    private final class ListenerThreadHandler extends Handler {
        // Constructor
        public ListenerThreadHandler(Looper looper) {
            super(looper);
        }
        // override handle message function
        @Override
        public void handleMessage(Message msg) {
            ListenerThreadRequest request = (ListenerThreadRequest) msg.obj;
            ListenerThreadRequest newreq = new ListenerThreadRequest(request);
            switch (msg.what) {
            case FastSwitchInfo.ACTION_SUB_REQ_GET_L2RTT_THR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_L2RTT_THR_SVR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_L2RTT_IE_THR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_L2RTT_IE_THR_SVR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_L4RTT_THR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_L4RTT_THR_SVR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_RSSI_THR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_RSSI_THR_SVR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_RSSI_LWEI_THR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_RSSI_LWEI_THR_SVR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_REC_THR:
            case FastSwitchInfo.ACTION_SUB_REQ_GET_REC_THR_SVR:
            case FastSwitchInfo.ACTION_SUB_REQ_DBG_GET_FS:
            case FastSwitchInfo.ACTION_SUB_REQ_DBG_GET_FS_SVR:
                // 3 is an experience value, it is the time that user thread is to here
                long timeoutInMs = (long) (msg.arg2 - 3);
                synchronized (mGeneralReportCallbackLock) {
                    mGeneralReportCallback = obtainMessage(msg.what, msg.arg1, msg.arg2, newreq);
                }

                synchronized (newreq) {
                    if (msg.arg2 >= 0) {
                        // Wait for at least timeoutInMs before returning null request result
                        long now = SystemClock.elapsedRealtime();
                        // Wait 2ms at least, 2 is also an experience value. need tune
                        long deadline = now + ((timeoutInMs <= 2) ? 2 : timeoutInMs);
                        while (newreq.result == 0 && now < deadline) {
                            try {
                                newreq.wait(deadline - now);
                            } catch (InterruptedException e) {
                                // Do nothing, go back and check if request is completed or timeout
                            } finally {
                                now = SystemClock.elapsedRealtime();
                            }
                        }
                    } else {
                        // Wait for the request to complete
                        while (newreq.result == 0) {
                            try {
                                newreq.wait();
                            } catch (InterruptedException e) {
                                // Do nothing, go back and wait until the request is complete
                            }
                        }
                    }
                }
                synchronized (mGeneralReportCallbackLock) {
                    mGeneralReportCallback = null;
                }
                if (newreq.result <= 0) {
                    request.result = newreq.result; // fail
                    notifyRequester(request);
                    loge("[FastSwitch]: subact:" + msg.what
                            + ", newreq.result: " + newreq.result);
                    return;
                }

                FastSwitchInfo reportInfo = (FastSwitchInfo) newreq.ret;
                FastSwitchInfo origInfo = (FastSwitchInfo) request.argument;

                for (Map.Entry<String, String> e : FastSwitchInfo.MAP_SUBACT_SUBREP.entrySet()) {
                    String mKey = e.getKey();
                    String mVal = e.getValue();
                    if (mKey.equals(origInfo.getBundle().getString(FastSwitchInfo.BK_PARM_ACT_EXT))
                            && mVal.equals(reportInfo.getBundle().getString(
                            FastSwitchInfo.BK_PARM_ACT_EXT))) {
                        Bundle rb = reportInfo.getBundle();
                        Bundle ob = origInfo.getBundle();
                        for (Map.Entry<String, Integer> entry
                                : FastSwitchInfo.MAP_BKS_SUBACT.entrySet()) {
                            String kstr = entry.getKey();
                            if (!(kstr.equals(FastSwitchInfo.BK_PARM_ACT_EXT))
                                    && rb.containsKey(kstr)
                                    && rb.getString(kstr) != null) {
                                ob.putString(kstr, rb.getString(kstr));
                                logi("[FastSwitch]: subact:" + msg.what + " receive key: " + kstr
                                        + " and val: " + rb.getString(kstr));
                                request.result = 1; // success
                                break;
                            }
                        }
                        break;
                    }
                }
                if (request.result == 0) {
                    request.result = -1;
                    loge("[FastSwitch]: subact:" + msg.what + ", receive nothing, fail");
                }
                break;
            default:
                loge("[FastSwitch]: subact:" + msg.what + " receive nothing from report");
                break;
            }
            notifyRequester(request);
        }
    }

    /**
     * Notify caller to unlock.
     *
     * @param request original request.
     */
    private void notifyRequester(ListenerThreadRequest request) {
        synchronized (request) {
            request.notifyAll();
        }
    }

    /**
     * Send a {@link ListenerThreadRequest} object to a {@link ListenerThread} to obtain query
     * result that is from report of sub action in FastSwitchInfo.ACTION_REPORT_GEN.
     *
     * @param info requsted information instance.
     * @param timeoutInMs will retrun directly if no result return after waiting timeoutInMs.
     * @return true for get success, false for fail.
     */
    private boolean sendRequest(FastSwitchInfo info, long timeoutInMs) {
        ListenerThreadRequest request = null;
        Message msg = null;

        request = new ListenerThreadRequest(info);
        synchronized (mListenerThreadLock) {
            if (mListenerHandler == null) {
                loge("[FastSwitch]: sendRequest, no listener");
                return false;
            }
            msg = mListenerHandler.obtainMessage(
                    Integer.parseInt(info.getBundle().getString(FastSwitchInfo.BK_PARM_ACT_EXT)),
                    info.getAction(), (int) timeoutInMs, request);
            msg.sendToTarget();
        }

        synchronized (request) {
            if (timeoutInMs >= 0) {
                // Wait for at least timeoutInMs before returning null request result
                long now = SystemClock.elapsedRealtime();
                long deadline = now + timeoutInMs;
                while (request.result == 0 && now < deadline) {
                    try {
                        request.wait(deadline - now);
                    } catch (InterruptedException e) {
                        // Do nothing, go back and check if request is completed or timeout
                    } finally {
                        now = SystemClock.elapsedRealtime();
                    }
                }
            } else {
                // Wait for the request to complete
                while (request.result == 0) {
                    try {
                        request.wait();
                    } catch (InterruptedException e) {
                        // Do nothing, go back and wait until the request is complete
                    }
                }
            }
        }
        if (request.result == 0) {
            loge("[FastSwitch]: sendRequest: Blocking command timed out."
                    + " Something has gone terribly wrong.");
        }
        return (request.result > 0);
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
     * Encapsulated d levevl log function.
     */
    private static void logd(String message) {
        Log.d(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated i levevl log function.
     */
    private static void logi(String message) {
        Log.i(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated e levevl log function.
     */
    private static void loge(String message) {
        Log.e(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated e levevl log function.
     * NOTE: Only used in encapsulation check function
     */
    private static void loge(boolean moreDepth, String message) {
        Log.e(TAG, "JXNPS: " + getMethodName(moreDepth) + ":" + message);
    }
}
