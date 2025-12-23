/*
 * Copyright (c) 2015,2017-2018 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 * Copyright (c) 2011-2014, The Linux Foundation. All rights reserved.
 */

package com.qualcomm.qti;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Trace;
import android.util.Log;
import java.util.ArrayList;
import java.util.Set;
import android.os.Build;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Intent;
import java.io.File;
import java.io.FileNotFoundException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.UserHandle;
import java.util.Vector;

public class Performance
{
    private static final String TAG = "Perf";
    private static final String PERF_SERVICE_BINDER_NAME = "vendor.perfservice";
    private static final boolean sPerfServiceDisabled = false;
    private static final boolean DEBUG = false;

    private static IBinder clientBinder;
    private static IBinder sPerfServiceBinder;
    private static IPerfManager sPerfService;
    private static boolean sLoaded = false;
    private final Object mLock = new Object();
    private static PerfServiceDeathRecipient sPerfServiceDeathRecipient;
    private static boolean sIsPlatformOrPrivApp = true;
    private static boolean sIsUntrustedDomain = false;
    private int UXE_EVENT_BINDAPP = 2;
    private static Boolean sIsChecked = false;
    private static boolean sIsGreChecked = false;
    public static final int VENDOR_FEEDBACK_WORKLOAD_TYPE = 0x00001601;
    public static final int VENDOR_HINT_FD_COUNT = 0x00001007;
    public static final int VENDOR_FEEDBACK_LAUNCH_END_POINT = 0x00001602;
    public static final int GAME = 2;
    private static boolean RestrictUnTrustedAppAccess = false;
    private static double DEF_PERF_HAL =  2.2f;

    public static final int VENDOR_APP_LAUNCH_HINT = 0x00001081;
    public static final int FD_COUNT_HINT_CAP = 18;
    public static final int TYPE_START_PROC = 101;
    private static Context mContext;
    private static Intent mIntent;

    private static String PKGNAME_GRE = "com.qualcomm.qti.gameruntimeengine";
    private static String PKGNAME_GENSHIN = "com.miHoYo.Yuanshen";
    private static String PKGNAME_STARRAIL = "com.miHoYo.hkrpg";
    private static String PKGNAME_STARRAIL_BETA = "com.miHoYo.hkrpgcb";
    private static String PKGNAME_STARRAIL_BILIBILI = "com.miHoYo.hkrpg.bilibili";
    private static String PKGNAME_STARRAIL_OVERSEA = "com.HoYoverse.hkrpgoversea";

    private static String ACTION_GRE_LAUNCH = "com.qualcomm.qti.gameruntimeengine.APP_LAUNCH";
    public static final Set<String> GSS_PKG_SET = Set.of(PKGNAME_GENSHIN, PKGNAME_STARRAIL,
        PKGNAME_STARRAIL_BETA, PKGNAME_STARRAIL_BILIBILI, PKGNAME_STARRAIL_OVERSEA);
    private static final String PROP_GRE_ENABLE = "vendor.perf.gre.enable";
    private static final String KEY_PKG_INTENT = "PKG_NAME";
    private static boolean GRE_ENABLE = false;

    // getting fd data
    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null;
    private int mLastPid = 0;
    private int mSleepTime = 30;
    private int mPIDWaitSleepTime = 120;
    private final int PID_REQ_TH = 3;
    private int mGameSample = 2000;
    private int mAppSample = 500;
    private static final int BOOST_V1 = 1;
    private static final int BOOST_V1_ARGS = 2;
    private static final int LL_FB_TYP_FD_THRD_STATE = 5;
    private static final int THRD_STATE_VALID = 1;
    private static final int ACTIVITY_LAUNCH_BOOST = 10;
    private Vector<Integer> mFDCountData = new Vector<Integer>();
    private final Object mPidLock = new Object();
    private static final String mLALPerfProp = "ro.vendor.perf.lal";
    private static final String mLGLPerfProp = "ro.vendor.perf.lgl";
    private static int mLaunchPropAcq = -1; //static: to make visible across Activity records
    private final Object mLLFeatureStateLock = new Object();
    private static int mMaxLLCallTries = 5; //static: to make visible across Activity records
    public static final int VENDOR_HINT_LAUNCHER_NAME = 0x000010AE;

    /** @hide */
    public Performance() {
    }

    /** @hide */
    public Performance(Context context) {
        mContext = context;
        mIntent = new Intent();
        if (mIntent != null) {
            mIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            mIntent.setAction("com.qualcomm.qti.workloadclassifier.APP_LAUNCH");
            mIntent.setPackage("com.qualcomm.qti.workloadclassifier");
        }

        if (DEBUG)Trace.traceBegin(Trace.TRACE_TAG_ALWAYS, "Create Performance instance");
        synchronized (Performance.class) {
            if (!sLoaded) {
                connectPerfServiceLocked();
                if (sPerfService == null && !sPerfServiceDisabled)
                    Log.e(TAG, "Perf service is unavailable.");
                else
                    sLoaded = true;
            }
        }
        checkAppPlatformSigned(context);
        if (DEBUG)Trace.traceEnd(Trace.TRACE_TAG_ALWAYS);
    }

    /** @hide */
    public Performance(boolean isUntrusterdDomain) {
        sIsUntrustedDomain = isUntrusterdDomain;
    }

    /* The following are the PerfLock API return values*/
    /** @hide */ public static final int REQUEST_FAILED = -1;
    /** @hide */ public static final int REQUEST_SUCCEEDED = 0;

    /** @hide */ private int mHandle = 0;


    private void connectPerfServiceLocked() {
        if (sPerfService != null || sPerfServiceDisabled) return;

        if (DEBUG)Trace.traceBegin(Trace.TRACE_TAG_ALWAYS, "connectPerfServiceLocked");
        Log.i(TAG, "Connecting to perf service.");

        sPerfServiceBinder = ServiceManager.getService(PERF_SERVICE_BINDER_NAME);
        if (sPerfServiceBinder == null) {
            Log.e(TAG, "Perf service is now down, set sPerfService as null.");
            if (DEBUG)Trace.traceEnd(Trace.TRACE_TAG_ALWAYS);
            return;
        }
        try {
            sPerfServiceDeathRecipient = new PerfServiceDeathRecipient();
            //link perfDeathRecipient to binder to receive DeathRecipient call back.
            sPerfServiceBinder.linkToDeath(sPerfServiceDeathRecipient, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Perf service is now down, leave sPerfService as null.");
            if (DEBUG)Trace.traceEnd(Trace.TRACE_TAG_ALWAYS);
            return;
        }
        if (sPerfServiceBinder != null)
            sPerfService = IPerfManager.Stub.asInterface(sPerfServiceBinder);

        if (sPerfService != null) {
            try {
                clientBinder = new Binder();
                sPerfService.setClientBinder(clientBinder);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (DEBUG)Trace.traceEnd(Trace.TRACE_TAG_ALWAYS);
    }

    /* The following functions are the PerfLock APIs*/
    /** @hide */
    public int perfLockAcquire(int duration, int... list) {
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            mHandle = native_perf_lock_acq(mHandle, duration, list);
        } else {
            synchronized (mLock) {
                try {
                    if (sPerfService != null && RestrictUnTrustedAppAccess == false)
                    {
                        mHandle = sPerfService.perfLockAcquire(duration, list.length, list);
                    }
                    else
                        return REQUEST_FAILED;

                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfLockAcquire", e);
                    return REQUEST_FAILED;
                }
            }
        }
        if (mHandle <= 0)
            return REQUEST_FAILED;
        else
            return mHandle;
    }

    /** @hide */
    public int perfLockRelease() {
        int retValue = REQUEST_SUCCEEDED;
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            retValue = native_perf_lock_rel(mHandle);
            mHandle = 0;
            return retValue;
        } else {
            synchronized (mLock) {
                try {
                    if (sPerfService != null && RestrictUnTrustedAppAccess == false)
                    {
                        retValue = sPerfService.perfLockReleaseHandler(mHandle);
                    }
                    else
                        retValue = REQUEST_FAILED;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfLockRelease", e);
                    return REQUEST_FAILED;
                }
            }
            return retValue;
        }
    }

    /** @hide */
    public int perfHintRelease() {
        int retValue = REQUEST_SUCCEEDED;
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            retValue = native_perf_hint_rel(mHandle);
            mHandle = 0;
            return retValue;
        } else {
            synchronized (mLock) {
                try {
                    if (sPerfService != null && RestrictUnTrustedAppAccess == false)
                    {
                        retValue = sPerfService.perfLockReleaseHandler(mHandle);
                    }
                    else
                        retValue = REQUEST_FAILED;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfHintRelease", e);
                    return REQUEST_FAILED;
                }
            }
            return retValue;
        }
    }

    /** @hide */
    public int perfLockReleaseHandler(int _handle) {
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            return native_perf_lock_rel(_handle);
        } else {
            int retValue = REQUEST_SUCCEEDED;
            synchronized (mLock) {
                try {
                    if (sPerfService != null && RestrictUnTrustedAppAccess == false)
                    {
                        retValue = sPerfService.perfLockReleaseHandler(_handle);
                    }
                    else
                        retValue = REQUEST_FAILED;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfLockRelease(handle)", e);
                    return REQUEST_FAILED;
                }
            }
            return retValue;
        }
    }

    /* Thread to send launch broadcast for games */
    private class SendGameLaunchBroadcast implements Runnable {
        public String pkgName;

        public SendGameLaunchBroadcast(String pkgName) {
            this.pkgName = pkgName;
        }

        public void run() {
            if (mContext != null && mIntent != null) {
                int appType = perfGetFeedback(VENDOR_FEEDBACK_WORKLOAD_TYPE, pkgName);
                mIntent.putExtra("PKG_NAME", pkgName);
                mIntent.putExtra("APP_TYP", appType);
                mContext.sendBroadcast(mIntent);
            }
            return;
        }
    }

    private void sendGreGameBroadcast(String pkgName) {
        Intent greLaunchIntent = new Intent();
        greLaunchIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        greLaunchIntent.setAction(ACTION_GRE_LAUNCH);
        greLaunchIntent.setPackage(PKGNAME_GRE);
        greLaunchIntent.putExtra(KEY_PKG_INTENT, pkgName);
        mContext.sendBroadcastAsUser(greLaunchIntent, UserHandle.ALL);
        Log.i(TAG, "send launch broadcast to gameruntime apk, game package name: " + pkgName);
    }

    private boolean getGreEnable() {
        synchronized(Performance.class){
            if(sIsGreChecked) {
                return GRE_ENABLE;
            }
            GRE_ENABLE = Boolean.parseBoolean(perfGetProp(PROP_GRE_ENABLE, "false"));
            sIsGreChecked = true;
            return GRE_ENABLE;
        }
    }

    /** @hide */
    public int perfHint(int hint, String userDataStr, int userData1, int userData2) {

        //send broadcast on game launch hint
        if (mContext != null && mIntent != null && hint == VENDOR_APP_LAUNCH_HINT
                                                   && userData2 == TYPE_START_PROC)
        {
            new Thread(new SendGameLaunchBroadcast(userDataStr)).start();
        }

        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            mHandle = native_perf_hint(hint, userDataStr, userData1, userData2);
        } else {
            synchronized (mLock) {
                try {
                    if (sPerfService != null && RestrictUnTrustedAppAccess == false)
                    {
                        mHandle = sPerfService.perfHint(hint, userDataStr, userData1, userData2,
                                Process.myTid());
                    }
                    else
                        return REQUEST_FAILED;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfHint", e);
                    return REQUEST_FAILED;
                }
            }
        }
        if (mHandle <= 0)
            return REQUEST_FAILED;
        else
            return mHandle;
    }

    /** @hide */
    public int perfGetFeedback(int req, String pkg_name) {
        return perfGetFeedbackExtn(req, pkg_name, 0);
    }

    /** @hide */
    public int perfGetFeedbackExtn(int req, String pkg_name, int numArgs,int... list) {
        int mInfo = 0;
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            mInfo = native_perf_get_feedback_extn(req, pkg_name, numArgs, list);
        } else {
            synchronized (mLock) {
                try {
                    if (sPerfService != null)
                        mInfo = sPerfService.perfGetFeedbackExtn(req, pkg_name, Process.myTid(), numArgs, list);
                    else
                        return REQUEST_FAILED;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfGetFeedbackExtn", e);
                    return REQUEST_FAILED;
                }
            }
        }
        if (mInfo <= 0)
            return REQUEST_FAILED;
        else
            return mInfo;
    }

    public int perfIOPrefetchStart(int PId, String Pkg_name, String Code_path)
    {
        return native_perf_io_prefetch_start(PId,Pkg_name, Code_path);
    }

    public int perfIOPrefetchStop(){
        return native_perf_io_prefetch_stop();
    }

    public int perfUXEngine_events(int opcode, int pid, String pkg_name, int lat, String CodePath)
    {

        if (opcode == UXE_EVENT_BINDAPP) {
            synchronized (mLock) {
                try {
                    if (sPerfService != null)
                        mHandle = sPerfService.perfUXEngine_events(opcode, pid, pkg_name, lat);
                    else
                        return REQUEST_FAILED;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfHint", e);
                    return REQUEST_FAILED;
                }
            }

        } else {
            mHandle = native_perf_uxEngine_events(opcode, pid, pkg_name, lat);
        }
        if (mHandle <= 0)
            return REQUEST_FAILED;
        else
            return mHandle;
    }

    public int perfLockAcqAndRelease(int handle, int duration, int numArgs,int reserveNumArgs, int... list) {
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            mHandle = native_perf_lock_acq_rel(handle, duration, numArgs, reserveNumArgs, list);
        } else {
            synchronized (mLock) {

                try {
                    if (sPerfService != null && RestrictUnTrustedAppAccess == false) {
                        mHandle = sPerfService.perfLockAcqAndRelease(handle, duration, numArgs, reserveNumArgs, list);
                    }
                    else
                        return REQUEST_FAILED;

                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfLockAcqAndRelease", e);
                    return REQUEST_FAILED;
                }
            }
        }
        if (mHandle <= 0)
            return REQUEST_FAILED;
        else
            return mHandle;
    }

    /** @hide */
    public void perfEvent(int eventId, String pkg_name, int numArgs, int... list) {
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            native_perf_event(eventId, pkg_name, numArgs, list);
        } else {
            synchronized (mLock) {
                try {
                    if (sPerfService != null)
                        sPerfService.perfEvent(eventId, pkg_name, Process.myTid(), numArgs, list);
                    else
                        return;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfEvent", e);
                    return;
                }
            }
        }
    }

    /** @hide */
    public int perfHintAcqRel(int handle,int hint, String pkg_name, int duration, int type, int numArgs, int... list) {
        if (hint == VENDOR_APP_LAUNCH_HINT && type == BOOST_V1 && numArgs == BOOST_V1_ARGS &&
               GSS_PKG_SET.contains(pkg_name) && getGreEnable() && mContext != null) {
            new Thread(()->sendGreGameBroadcast(pkg_name)).start();
        }

        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            mHandle = native_perf_hint_acq_rel(handle, hint, pkg_name, duration, type, numArgs, list);
        } else {
            synchronized (mLock) {
                try {
                    if (sPerfService != null)
                        mHandle = sPerfService.perfHintAcqRel(handle, hint, pkg_name, duration, type, Process.myTid(), numArgs, list);
                    else
                        return REQUEST_FAILED;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfHintAcqRel", e);
                    return REQUEST_FAILED;
                }
            }
        }
        processLaunchHint(hint, pkg_name, type, numArgs, list);
        if (mHandle <= 0)
            return REQUEST_FAILED;
        else
            return mHandle;
    }

    /** @hide */
    public int perfHintRenew(int handle,int hint, String pkg_name, int duration, int type, int numArgs, int... list) {
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            mHandle = native_perf_hint_renew(handle, hint, pkg_name, duration, type, numArgs, list);
        } else {
            synchronized (mLock) {
                try {
                    if (sPerfService != null)
                        mHandle = sPerfService.perfHintRenew(handle, hint, pkg_name, duration, type, Process.myTid(), numArgs, list);
                    else
                        return REQUEST_FAILED;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error calling perfHintRenew", e);
                    return REQUEST_FAILED;
                }
            }
        }
        if (mHandle <= 0)
            return REQUEST_FAILED;
        else
            return mHandle;
    }


    public String perfUXEngine_trigger(int opcode)
    {
        return native_perf_uxEngine_trigger(opcode);
    }

    public String perfSyncRequest(int opcode) {
        String def_val = "";
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            return native_perf_sync_request(opcode);
        } else {
            return def_val;
        }
    }

    private void checkAppPlatformSigned(Context context) {
        synchronized(sIsChecked){
            if (context == null || sIsChecked)return;
            if (DEBUG)Trace.traceBegin(Trace.TRACE_TAG_ALWAYS, "checkAppPlatformSigned");
            try {
                PackageInfo pkg = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNATURES);
                PackageInfo sys = context.getPackageManager().getPackageInfo(
                        "android", PackageManager.GET_SIGNATURES);
                sIsPlatformOrPrivApp =
                        (pkg != null && pkg.signatures != null
                        && pkg.signatures.length > 0
                        && sys.signatures[0].equals(pkg.signatures[0]))
                        || pkg.applicationInfo.isPrivilegedApp();

                /* some 3rd apps are using reflection to invoke the perf locks directly, below logic will restrict the access */
                if ((pkg != null
                    && pkg.applicationInfo != null
                    && pkg.applicationInfo.getHiddenApiEnforcementPolicy() == ApplicationInfo.HIDDEN_API_ENFORCEMENT_ENABLED))
                {
                    RestrictUnTrustedAppAccess = true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "packageName is not found.");
                sIsPlatformOrPrivApp = true;
            }
            sIsChecked = true;
            if (DEBUG)Log.d(TAG, " perftest packageName : " + context.getPackageName() + " sIsPlatformOrPrivApp is :" + sIsPlatformOrPrivApp);
            if (DEBUG)Trace.traceEnd(Trace.TRACE_TAG_ALWAYS);
        }
    }

    private final class PerfServiceDeathRecipient implements IBinder.DeathRecipient {
        public void binderDied() {
            synchronized(mLock) {
                Log.e(TAG, "Perf Service died.");
                if (sPerfServiceBinder != null)
                    sPerfServiceBinder.unlinkToDeath(this, 0);
                sPerfServiceBinder = null;
                sPerfService = null;
            }
        }
    }

    public String perfGetProp(String prop_name, String def_val) {
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            return native_perf_get_prop(prop_name, def_val);
        } else {
            return def_val;
        }
    }

    public double perfGetHalVer() {
        double def_val = DEF_PERF_HAL;
        if (sIsPlatformOrPrivApp && !sIsUntrustedDomain) {
            def_val =  native_perf_get_hal_ver();
        }
        return def_val;
    }

    private int processFDInfo(int pid, int count, String pkg_name) {
        try {
            if (mFDCountData == null) {
                return 0;
            }
            mFDCountData.add(count);
            if (mFDCountData.size() >= FD_COUNT_HINT_CAP) {
                int list[] = new int[FD_COUNT_HINT_CAP];
                if (list == null) {
                    return 0;
                }
                int i = 0;
                for (; i < FD_COUNT_HINT_CAP; i++) {
                    list[i] = mFDCountData.elementAt(i);
                }
                perfEvent(VENDOR_HINT_FD_COUNT, pkg_name, i, list);
                mFDCountData.clear();
            }
        }   catch (Exception e) {
                Log.e(TAG, "Error in processFDInfo" + e);
        }
        return 0;
    }
    private int getDirectoryFileCount(File folder) {
        int size = 0;
        File f = null;
        if(folder == null)
            return 0;
        try {
            File[] files = folder.listFiles();
            if(files == null)
                return 0;
            size = files.length;
        } catch(SecurityException e) {
            Log.e(TAG, "getFolderSize () : " + e);
        }
        return size;
    }

    private void initLLHandlerCallback(HandlerThread handlerThread) {

        try {
            mHandler =  new Handler(handlerThread.getLooper()) {
                @Override
                    public void handleMessage(Message msg) {
                        int totalSize = 0;
                        if (!getLaunchFeatureInitState(1)) {
                            return;
                        }
                        if (msg == null || mFDCountData == null) {
                            return;
                        }
                        String pkgName = (String) msg.obj;
                        if (pkgName == null) {
                            return;
                        }
                        int extPid = getLaunchFeatureThreadStatePid(pkgName);
                        // if thread state is not valid extPid is 0 i.e. data not required
                        if (extPid <= 0) {
                            return;
                        }

                        int pid = msg.what;
                        if (pid == -1 && extPid == THRD_STATE_VALID) {
                            // we just made a call, PID was not available,
                            // wait for pid to be assigned
                            try {
                                Thread.sleep(mPIDWaitSleepTime);
                            } catch (Exception e) {
                                Log.e(TAG, "Error in Handler Thread Sleep" + e);
                            }
                            extPid = waitForPID(pkgName, pid);
                            if (extPid > THRD_STATE_VALID) {
                                pid = extPid;
                            } else if (extPid <= 0) {
                                return;
                            }
                        } else if (pid == -1 && extPid > THRD_STATE_VALID) {
                            pid = extPid;
                            synchronized(mPidLock) {
                                mLastPid = pid;
                            }
                        }
                        int hint_type = msg.arg1;
                        int hint_id = msg.arg2;
                        int appType = msg.sendingUid;
                        int samples = 0;
                        if (appType == GAME) {
                            samples = mGameSample;
                        } else {
                            samples = mAppSample;
                        }
                        int iterator = 0;
                        mFDCountData.clear();
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "Get Fd for "+ pkgName + " : " + Integer.toString(pid));
                        String DirPath = "/proc/" + Integer.toString(pid) +"/fdinfo";
                        try {
                            File folder = new File(DirPath);
                            if (folder == null || mPidLock == null) {
                                return;
                            }
                            for (iterator = 0; iterator < samples; iterator++) {
                                    synchronized(mPidLock) {
                                        if (mLastPid != pid) {
                                            break;
                                        }
                                    }
                                    if(!folder.exists()) {
                                        // this could be a pid switch- try to resurrect
                                        extPid = waitForPID(pkgName, pid);
                                        if (extPid > THRD_STATE_VALID && extPid != pid) {
                                            pid = extPid;
                                            DirPath = "/proc/" + Integer.toString(pid) +"/fdinfo";
                                            folder = new File(DirPath);
                                        }
                                        if (folder == null || !folder.exists()) {
                                            break;
                                        }
                                    }
                                    int Size = getDirectoryFileCount(folder);
                                    processFDInfo(pid, Size, pkgName);
                                try {
                                    Thread.sleep(mSleepTime);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in Handler Thread Sleep" + e);
                                }
                            }
                        } catch(SecurityException e) {
                            Log.e(TAG, "Security Exception: " + e);
                        }
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
            };
        } catch (Exception e) {
            Log.e(TAG, "Error in Starting Handler Thread" + e.getCause());
            Log.e(TAG, "Error in Starting Handler Thread" + e.getMessage());
        }
    }

    private int processLaunchHint(int hint, String pkg_name,
                                    int type, int numArgs, int... list) {
        //Removed pre processed value check as we need to try to get property once.
        if (getLaunchFeatureInitState(-1) == false) {
            return 0;
        }
        if (hint == VENDOR_APP_LAUNCH_HINT && type == BOOST_V1 && numArgs == BOOST_V1_ARGS) {
            synchronized (mLock) {
                if (mHandlerThread == null) {
                    try {
                        mHandlerThread = new HandlerThread("Perf-FDM");
                        if (mHandlerThread == null) {
                            return 0;
                        }
                        mHandlerThread.start();
                        initLLHandlerCallback(mHandlerThread);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in Starting Handler Thread" + e);
                    }
                }
            }
            if (mHandler == null) {
                Log.e(TAG, "Handler is null");
            } else {
                if (mPidLock != null && mLastPid != list[1]) {
                    synchronized (mPidLock) {
                        Message msg = new Message();
                        if (msg != null) {
                            mLastPid = list[1];
                            msg.obj = pkg_name;
                            msg.arg1 = hint;
                            msg.arg2 = type;
                            msg.what = list[1];
                            msg.sendingUid = list[0];
                            mHandler.sendMessage(msg);
                        }
                    }
                }
            }
        }
        return 0;
    }

    // if immediate is -1, check if need to disable thread.
    // if immediate is 0 , return pre-processed status.
    // if immediate is 1 for further processing and is binder bound.
    private boolean getLaunchFeatureInitState(int immediate) {
        if (perfGetHalVer() <= DEF_PERF_HAL) {
            return false;
        }
        if (mLLFeatureStateLock == null) {
            return false;
        }
        boolean retVal = false;
        synchronized (mLLFeatureStateLock) {
            if (immediate == 1 && mLaunchPropAcq < 0  &&  mMaxLLCallTries > 0) {
                    String lalProp = perfGetProp(mLALPerfProp, "none");
                    String lglProp = perfGetProp(mLGLPerfProp, "none");
                    if (lalProp.equals("true") || lglProp.equals("true")) {
                        mLaunchPropAcq = 1; //prop acquired and enabled
                        mMaxLLCallTries = 0;
                        retVal = true;
                    } else if (lalProp.equals("false") || lglProp.equals("false")) {
                        mLaunchPropAcq = 0; //prop acquired but disabled
                        mMaxLLCallTries = 0;
                        retVal = false;
                    } else {
                        retVal = false; //if prop is not there in config or perf is not up.
                    }
                    if (mMaxLLCallTries > 0) {
                        mMaxLLCallTries--;
                    }
            } else if(mLaunchPropAcq == 1) {
                retVal = true;
            }
            if (immediate < 0) { // This case is to check whether we need to close thread or not.
                if (mMaxLLCallTries <= 0 && mLaunchPropAcq <= 0) {
                    deInitLLHandlerCallback();
                    retVal = false;
                } else {
                    retVal = true;
                }
            }
        }
        return retVal;
    }

    private int waitForPID(String pkgName, int pid) {
        int extPid = THRD_STATE_VALID;
        for (int pidReqCount = 0; pidReqCount < PID_REQ_TH; pidReqCount++) {
            extPid = getLaunchFeatureThreadStatePid(pkgName);
            if (extPid > THRD_STATE_VALID) {
                break;
            }
            try {
                Thread.sleep(mPIDWaitSleepTime);
            }
            catch (Exception e) {
                extPid = THRD_STATE_VALID;
            }
        }
        if (extPid > THRD_STATE_VALID && extPid != pid) {
            synchronized(mPidLock) {
                mLastPid = extPid;
            }
        }
        return extPid;
    }

    // if thread state is valid returns a valid PID for the pkgName
    // if thread state is valid & PID not found returns 1
    // if thread state is not valid returns 0
    private int getLaunchFeatureThreadStatePid(String pkgName) {
        int retval = -1;
        if (perfGetHalVer() <= DEF_PERF_HAL) {
            return retval;
        }
        if (mLLFeatureStateLock == null) {
            return retval;
        }
        synchronized (mLLFeatureStateLock) {
            if (mLaunchPropAcq > 0) {
                retval = perfGetFeedbackExtn(VENDOR_FEEDBACK_LAUNCH_END_POINT,
                                pkgName, 1, LL_FB_TYP_FD_THRD_STATE);
            }
        }
        return retval;
    }

    private void deInitLLHandlerCallback() {
        if(mHandlerThread != null && mHandlerThread.isAlive()) {
            Log.e(TAG,"Exiting LL handler Thread");
            mHandlerThread.quitSafely();
            mHandler = null;
            mHandlerThread = null;
        }
    }
    protected void finalize()  {
        deInitLLHandlerCallback();
    }
    private native int  native_perf_lock_acq(int handle, int duration, int list[]);
    private native int  native_perf_lock_rel(int handle);
    private native int  native_perf_hint_rel(int handle);
    private native int  native_perf_hint(int hint, String userDataStr, int userData1, int userData2);
    private native int  native_perf_get_feedback(int req, String pkg_name);
    private native int  native_perf_get_feedback_extn(int req, String pkg_name, int numArgs, int list[]);
    private native int  native_perf_io_prefetch_start(int pid, String pkg_name, String Code_path);
    private native int  native_perf_io_prefetch_stop();
    private native int  native_perf_uxEngine_events(int opcode, int pid, String pkg_name, int lat);
    private native String  native_perf_uxEngine_trigger(int opcode);
    private native String  native_perf_sync_request(int opcode);
    private native String native_perf_get_prop(String prop_name, String def_val);
    private native int  native_perf_lock_acq_rel(int handle, int duration, int numArgs,int reserveNumArgs, int list[]);
    private native void  native_perf_event(int eventId, String pkg_name, int numArgs, int list[]);
    private native int  native_perf_hint_acq_rel(int handle, int hint, String pkg_name, int duration, int hint_type, int numArgs, int list[]);
    private native int  native_perf_hint_renew(int handle, int hint, String pkg_name, int duration, int hint_type, int numArgs, int list[]);
    private native double  native_perf_get_hal_ver();

}
