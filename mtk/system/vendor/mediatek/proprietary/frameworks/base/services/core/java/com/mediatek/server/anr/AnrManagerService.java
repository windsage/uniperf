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
package com.mediatek.server.anr;

import android.app.IActivityController;
import android.app.IApplicationThread;
import android.app.ApplicationExitInfo;
import android.app.AnrController;
import android.content.pm.ApplicationInfo;
import android.content.pm.IncrementalStatesInfo;
import android.content.pm.PackageManagerInternal;
import android.content.Context;
import android.os.Build;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.StrictMode;
import android.os.SELinux;
import android.os.Process;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.incremental.IIncrementalService;
import android.os.incremental.IncrementalManager;
import android.os.incremental.IncrementalMetrics;
import android.server.ServerProtoEnums;
import android.util.EventLog;
import android.util.Slog;
import android.util.SparseBooleanArray;
import android.util.StatsLog;

import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.os.anr.AnrLatencyTracker;
import com.android.internal.os.ProcessCpuTracker;
import com.android.server.am.ActivityManagerService;
import com.android.server.am.AppNotRespondingDialog;
import com.android.server.am.AppNotRespondingDialog.Data;
import com.android.server.am.ProcessRecord;
import com.android.server.am.ProcessErrorStateRecord;
import com.android.server.am.ProcessList;
import com.android.server.am.PackageList;
import com.android.server.am.StackTracesDumpHelper;
import com.android.server.wm.ActivityRecord;
import com.android.server.wm.WindowProcessController;
import com.android.server.Watchdog;
import com.android.server.ResourcePressureUtil;

import com.mediatek.aee.ExceptionLog;
import com.mediatek.anr.AnrAppManagerImpl;
import com.mediatek.anr.AnrManagerNative;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.LinkedHashMap;
import java.util.UUID;
//SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 start
import static android.os.Process.getFreeMemory;
import static android.os.Process.getTotalMemory;
//SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 end


/**
 * Be used for ANR debug mechanism.
 *
 * @hide
 */
public final class AnrManagerService extends AnrManagerNative {
    private static final String TAG = "AnrManager";
    private static boolean sEnhanceEnable = true;

    private static final String PROCESS_RECORD = "com.android.server.am.ProcessRecord";
    private static final String PROCESS_ERROR_STATE_RECORD = "com.android.server.am." +
                                                             "ProcessErrorStateRecord";
    private static final String ACTIVITY_RECORD = "com.android.server.wm.ActivityRecord";
    private static final String ACTIVITY_MANAGER = "com.android.server.am.ActivityManagerService";
    private static final String APP_PROFILER = "com.android.server.am.AppProfiler";
    private static final String APP_ERRORS = "com.android.server.am.AppErrors";
    private static final String ACTIVE_SERVICES = "com.android.server.am.ActiveServices";
    private static final String BATTERY_STATS = "com.android.server.am.BatteryStatsService";
    private static final String ATM_SERVICE = "com.android.server.wm.ActivityTaskManagerService";
    private static final String PROCESS_LIST = "com.android.server.am.ProcessList";

    private static final int START_MONITOR_BROADCAST_TIMEOUT_MSG = 1001;
    private static final int START_MONITOR_SERVICE_TIMEOUT_MSG = 1002;
    private static final int START_ANR_DUMP_MSG = 1003;
    private static final int START_MONITOR_KEYDISPATCHING_TIMEOUT_MSG = 1004;
    private static final int REMOVE_KEYDISPATCHING_TIMEOUT_MSG = 1005;
    private static final int SERVICE_TIMEOUT = 20 * 1000;

    private static final boolean IS_USER_BUILD = "user".equals(Build.TYPE)
            || "userdebug".equals(Build.TYPE);

    private static final boolean IS_USER_LOAD = "user".equals(Build.TYPE);

    private static final String BINDERINFO_PATH = "/dev/binderfs/binder_logs";
    private static int[] mZygotePids = null;

    private static final int MAX_MTK_TRACE_COUNT = 10;
    private static final Object mDumpStackTraces = new Object();
    private static String[] NATIVE_STACKS_OF_INTEREST = new String[] {
            "/system/bin/netd", "/system/bin/audioserver",
            "/system/bin/cameraserver", "/system/bin/drmserver",
            "/system/bin/mediadrmserver", "/system/bin/mediaserver",
            "/system/bin/sdcard", "/system/bin/surfaceflinger",
            "vendor/bin/hw/camerahalserver","/system/bin/vold",
            "media.extractor", // system/bin/mediaextractor
            "media.metrics", // system/bin/mediametrics
            "media.codec", // vendor/bin/hw/android.hardware.media.omx@1.0-service
            "media.swcodec", // /apex/com.android.media.swcodec/bin/mediaswcodec
            "media.transcoding", // Media transcoding service
            "com.android.bluetooth", // Bluetooth service
            "/apex/com.android.os.statsd/bin/statsd",  // Stats daemon
            "/vendor/bin/hw/android.hardware.media.c2@1.2-mediatek-64b",
            "/vendor/bin/hw/android.hardware.media.c2@1.2-mediatek",
            "/vendor/bin/hw/android.hardware.media.c2-mediatek-64b",
            "/vendor/bin/hw/android.hardware.media.c2-mediatek",
    };

    private static final ProcessCpuTracker mAnrProcessStats = new ProcessCpuTracker(
            false);
    private final AtomicLong mLastCpuUpdateTime = new AtomicLong(0);
    private static final long MONITOR_CPU_MIN_TIME = 2500;

    private static ConcurrentHashMap<Integer, String> mMessageMap =
            new ConcurrentHashMap<Integer, String>();
    private static final int EVENT_BOOT_COMPLETED = 9001;

    private static final int MESSAGE_MAP_BUFFER_COUNT_MAX = 5;
    private static final int MESSAGE_MAP_BUFFER_SIZE_MAX = 50 * 1000;

    private static final long ANR_BOOT_DEFER_TIME = 30 * 1000;
    private static final long ANR_CPU_DEFER_TIME = 8 * 1000;
    private static final float ANR_CPU_THRESHOLD = 90.0F;

    private static final int INVALID_ANR_FLOW = -1;
    private static final int NORMAL_ANR_FLOW = 0;
    private static final int SKIP_ANR_FLOW = 1;
    private static final int SKIP_ANR_FLOW_AND_KILL = 2;

    private static final int ENABLE_ANR_DUMP_FOR_3RD_APP = 1;
    private static final int DISABLE_ANR_DUMP_FOR_3RD_APP = 0;

    private static final int INVALID_ANR_OPTION = -1;
    private static final int DISABLE_ALL_ANR_MECHANISM = 0;
    private static final int DISABLE_PARTIAL_ANR_MECHANISM = 1;
    private static final int ENABLE_ALL_ANR_MECHANISM = 2;

    private static Object lock = new Object();
    private static AnrManagerService sInstance = null;
    private AnrMonitorHandler mAnrHandler;
    private AnrDumpManager mAnrDumpManager;
    private ActivityManagerService mService;
    private ApplicationInfo aInfo;
    private int mAmsPid;
    private long mEventBootCompleted = 0;
    private long mCpuDeferred = 0;
    private int mAnrFlow = INVALID_ANR_FLOW;
    private int mAnrOption = INVALID_ANR_OPTION;
    private ExceptionLog exceptionLog = null;
    private File mTracesFile = null;
    private final AtomicLong firstPidEndOffset = new AtomicLong(-1);
    private float loadingProgress = 1;
    private long anrDialogDelayMs = 0;
    private IncrementalMetrics incrementalMetrics = null;

    private Class<?> mProcessRecord = getProcessRecord();
    private Class<?> mProcessErrorStateRecord = getProcessErrorStateRecord();
    private Class<?> mAMS = getActivityManagerService();
    private Class<?> mAppProfiler = getAppProfiler();

    private Method mKill = getProcessRecordMethod("killLocked", new Class[] {
            String.class, int.class, boolean.class });
    private Method mUpdateCpuStatsNow = getAMSMethod("updateCpuStatsNow");
    private Method mNoteProcessANR = getBatteryStatsServiceMethod(
            "noteProcessAnr", new Class[] { String.class, int.class });
    private Method mScheduleServiceTimeoutLocked = getActiveServicesMethod(
            "scheduleServiceTimeoutLocked", new Class[] { ProcessRecord.class });
    private Method mMakeAppNotRespondingLocked = getProcessErrorStateRecordMethod(
            "makeAppNotRespondingLSP", new Class[] { String.class,
                    String.class, String.class });

    private Field mPidField = getProcessRecordField("mPid");
    private Field mProcessNameField = getProcessRecordField("processName");
    private Field mThreadField = getProcessRecordField("mThread");
    private Field mNotRespondingField = getProcessErrorStateRecordField("mNotResponding");
    private Field mCrashingField = getProcessErrorStateRecordField("mCrashing");
    private Field mUserIdField = getProcessRecordField("userId");
    private Field mUidField = getProcessRecordField("uid");
    private Field mInfoField = getProcessRecordField("info");
    private Field mPkgListField = getProcessRecordField("mPkgList");
    private Field mPersistentField = getProcessRecordField("mPersistent");
    private Field mParentPidField = getProcessRecordField("mPid");
    private Field mParentAppField = getActivityRecordField("app");

    //private Field mControllerField = getAMSField("mController");
    //private Field mLruProcessesField = getAMSField("mLruProcesses");
    private Field mLruProcessesField = getPLField("mLruProcesses");
    private Field mProcessListField = getAMSField("mProcessList");
    private Field mProcessCpuTrackerField = getAppProfilerField("mProcessCpuTracker");
    private Field mMonitorCpuUsageField = getAppProfilerField("MONITOR_CPU_USAGE");
    private Field mShowNotRespondingUiMsgField = getAMSField("SHOW_NOT_RESPONDING_UI_MSG");
    private Field mBatteryStatsServiceField = getAMSField("mBatteryStatsService");
    private Field mActiveServicesField = getAMSField("mServices");
    private Field mUiHandlerField = getAMSField("mUiHandler");
    private Field mControllerField = getATMField("mController");
    //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 start
    private static final boolean ANR_DUMP_OPT = "1".equals(SystemProperties.get("ro.anr_dump_opt"));
    private final static boolean IS_ADB_ENABLE = SystemProperties.get("persist.sys.adb.support","0").equals("1");
    private boolean mIgnoreAnr = false;
    private long mLastAnrTime = 0;
    private final long ANR_INTERVAL =  60*2*1000;
    private long mIgnoreAnrTime = 0;
    //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 end
    private Class<?> getProcessRecord() {
        try {
            return Class.forName(PROCESS_RECORD);
        } catch (Exception e) {
            return null;
        }
    }

    private Class<?> getProcessErrorStateRecord() {
        try {
            return Class.forName(PROCESS_ERROR_STATE_RECORD);
        } catch (Exception e) {
            return null;
        }
    }

    private Class<?> getActivityManagerService() {
        try {
            return Class.forName(ACTIVITY_MANAGER);
        } catch (Exception e) {
            return null;
        }
    }

    private Class<?> getAppProfiler() {
        try {
            return Class.forName(APP_PROFILER);
        } catch (Exception e) {
            return null;
        }
    }

    private Method getProcessRecordMethod(String func, Class[] cls) {
        try {
            Method method = mProcessRecord.getDeclaredMethod(func, cls);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            Slog.w(TAG, "getProcessRecordMethod Exception: " + e);
            return null;
        }
    }

    private Method getProcessErrorStateRecordMethod(String func, Class[] cls) {
        try {
            Method method = mProcessErrorStateRecord.getDeclaredMethod(func, cls);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            Slog.w(TAG, "getProcessErrorStateRecordMethod Exception: " + e);
            return null;
        }
    }

    private Method getAMSMethod(String func) {
        try {
            Method method = mAMS.getDeclaredMethod(func);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            return null;
        }
    }

    private Method getBatteryStatsServiceMethod(String func, Class[] cls) {
        try {
            Class<?> batteryStatsService = Class.forName(BATTERY_STATS);
            Method method = batteryStatsService.getDeclaredMethod(func, cls);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            return null;
        }
    }

    private Method getActiveServicesMethod(String func, Class[] cls) {
        try {
            Class<?> activeServices = Class.forName(ACTIVE_SERVICES);
            return activeServices.getDeclaredMethod(func, cls);
        } catch (Exception e) {
            return null;
        }
    }

    private Method getAppErrorsMethod(String func, Class[] cls) {
        try {
            Class<?> appErrors = Class.forName(APP_ERRORS);
            Method method = appErrors.getDeclaredMethod(func, cls);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            return null;
        }
    }

    private Field getProcessRecordField(String var) {
        try {
            Field field = mProcessRecord.getDeclaredField(var);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    private Field getProcessErrorStateRecordField(String var) {
        try {
            Field field = mProcessErrorStateRecord.getDeclaredField(var);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    private Field getActivityRecordField(String var) {
        try {
            Class<?> mActivityRecord = Class.forName(ACTIVITY_RECORD);
            Field field = mActivityRecord.getDeclaredField(var);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    private Field getAMSField(String var) {
        try {
            Field field = mAMS.getDeclaredField(var);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            Slog.w(TAG,"get Field failed:"+var);
            return null;
        }
    }

    private Field getAppProfilerField(String var) {
        try {
            Field field = mAppProfiler.getDeclaredField(var);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            Slog.w(TAG,"get Field failed:"+var);
            return null;
        }
    }

    private Field getATMField(String var) {
        try {
            Class<?> mATM = Class.forName(ATM_SERVICE);
            Field field = mATM.getDeclaredField(var);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    private Field getPLField(String var) {
        try {
            Class<?> mATM = Class.forName(PROCESS_LIST);
            Field field = mATM.getDeclaredField(var);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    public static AnrManagerService getInstance() {
        if (null == sInstance) {
            synchronized (lock) {
                if (null == sInstance) {
                    sInstance = new AnrManagerService();
                }
            }
        }
        return sInstance;
    }

    // If you need to initial something, please put it here.
    public void startAnrManagerService(int pid) {
        Slog.i(TAG, "startAnrManagerService");
        mAmsPid = pid;
        HandlerThread handlerThread = new HandlerThread("AnrMonitorThread");
        handlerThread.start();
        mAnrHandler = new AnrMonitorHandler(handlerThread.getLooper());
        mAnrDumpManager = new AnrDumpManager();
        mAnrProcessStats.init();
        //prepareStackTraceFile(SystemProperties.get(
        //        "dalvik.vm.mtk-stack-trace-file", null));
        prepareStackTraceFile(SystemProperties.get(
                "dalvik.vm.stack-trace-file", null));
        File traceFile = new File(SystemProperties.get(
                "dalvik.vm.stack-trace-file", null));
        File traceDir = traceFile.getParentFile();
        if (traceDir != null && !SELinux.restoreconRecursive(traceDir)) {
            Slog.i(TAG,
                    "startAnrManagerService SELinux.restoreconRecursive fail dir = "
                            + traceDir.toString());
        }
        if (SystemProperties.get("ro.vendor.have_aee_feature").equals("1")) {
            exceptionLog = ExceptionLog.getInstance();
        }
//        if (!IS_USER_BUILD) {
//            Looper.myLooper().setMessageLogging(
//                    AnrAppManagerImpl.getInstance().newMessageLogger(false,
//                            Thread.currentThread().getName()));
//        }
        mKill.setAccessible(true);
        mUpdateCpuStatsNow.setAccessible(true);
        mNoteProcessANR.setAccessible(true);
        mScheduleServiceTimeoutLocked.setAccessible(true);
        mMakeAppNotRespondingLocked.setAccessible(true);
    }

    public void sendBroadcastMonitorMessage(long timeoutTime,
            long mTimeoutPeriod) {
        if (ENABLE_ALL_ANR_MECHANISM == checkAnrDebugMechanism()) {
            Message broadcastMonitor = mAnrHandler
                    .obtainMessage(START_MONITOR_BROADCAST_TIMEOUT_MSG);
            mAnrHandler.sendMessageAtTime(broadcastMonitor, timeoutTime
                    - mTimeoutPeriod / 2);
        }
    }

    public void removeBroadcastMonitorMessage() {
        if (ENABLE_ALL_ANR_MECHANISM == checkAnrDebugMechanism()) {
            mAnrHandler.removeMessages(START_MONITOR_BROADCAST_TIMEOUT_MSG);
        }
    }

    public void sendServiceMonitorMessage() {
        long now = SystemClock.uptimeMillis();
        if (ENABLE_ALL_ANR_MECHANISM == checkAnrDebugMechanism()) {
            Message serviceMonitor = mAnrHandler
                    .obtainMessage(START_MONITOR_SERVICE_TIMEOUT_MSG);
            mAnrHandler.sendMessageAtTime(serviceMonitor, now + SERVICE_TIMEOUT
                    * 2 / 3);
        }
    }

    public void removeServiceMonitorMessage() {
        if (ENABLE_ALL_ANR_MECHANISM == checkAnrDebugMechanism()) {
            mAnrHandler.removeMessages(START_MONITOR_SERVICE_TIMEOUT_MSG);
        }
    }

    public boolean startAnrDump(ActivityManagerService service, ProcessErrorStateRecord processESR,
            String activityShortComponentName, ApplicationInfo apInfo,
            String parentShortComponentName, ProcessRecord parentProcess, boolean aboveSystem,
            String annotation, final boolean isSilentANR, long anrTime, boolean onlyDumpSelf,
            UUID errorId, String criticalEventLog, ExecutorService auxiliaryTaskExecutor,
            AnrLatencyTracker latencyTracker, LinkedHashMap<String, String> memoryHeaders,
            Future<?> updateCpuStatsNowFirstCall, boolean isContinuousAnr,
            Future<File> firstPidFilePromise)
            throws Exception {
        Slog.i(TAG, "startAnrDump");
        if (DISABLE_ALL_ANR_MECHANISM == checkAnrDebugMechanism()) {
            return false;
        }
        ProcessRecord app = (ProcessRecord) processESR.mApp;
        aInfo = apInfo;
        mService = service;
        int pid = app.getPid();
        int uid = app.uid;
        int userid = app.userId;
        String processName = app.processName;
        ApplicationInfo appInfo = app.info;
        IActivityController controller = (IActivityController) mControllerField
                .get(mService.mActivityTaskManager);
        final PackageManagerInternal packageManagerInternal =
                mService.getPackageManagerInternal();

        int parentPid = -1;
        if (null != parentProcess) {
            parentPid = (int) mParentPidField.get(parentProcess);
        }

        if (isAnrFlowSkipped(pid, processName, annotation, app)) {
            return true;
        }

        if (ENABLE_ALL_ANR_MECHANISM == checkAnrDebugMechanism()) {
            try {
                ((IApplicationThread) mThreadField.get(app))
                        .dumpMessage(pid == mAmsPid);
            } catch (Exception e) {
                Slog.e(TAG, "Error happens when dumping message history", e);
            }
        }
        AnrDumpRecord anrDumpRecord = null;

        if (needAnrDump(appInfo)) {
            enableTraceLog(false);
            new BinderDumpThread(pid).start();
            if (!mAnrDumpManager.mDumpList.containsKey(app)) {
                anrDumpRecord = new AnrDumpRecord(pid,
                        uid, userid, appInfo, false,
                        processName,
                        app.toString(),
                        activityShortComponentName,
                        parentProcess != null ? parentPid : -1,
                        parentShortComponentName,
                        annotation, anrTime);
                if (ENABLE_ALL_ANR_MECHANISM == checkAnrDebugMechanism()) {
                    updateProcessStats();
                    String cpuInfo = getAndroidTime() + getProcessState() + "\n";
                    anrDumpRecord.mCpuInfo = cpuInfo;
                    Slog.i(TAG, cpuInfo.toString());
                }
                //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 start
                if (IS_ADB_ENABLE && mService.mLastDumpHeapCount > 0 && (mService.mLastDumpHeapTime + mService.ANR_DUMP_HEAP_TIME < anrTime)) {
                    mService.mLastDumpHeapCount = 0;
                    Slog.w(TAG, "timeout reset dumpheap mLastDumpHeapCount = 0");
                }
                if (IS_ADB_ENABLE && mService.mLastDumpHeapCount > 0){
                    Slog.w(TAG, "skip anr now because of dumpheap !");
                    anrDumpRecord.mIsCompleted = true;
                    mIgnoreAnr = true;
                }
                else if (ANR_DUMP_OPT) {
                    int totalCpu = 0;
                    if ((boolean) mMonitorCpuUsageField.get(mAMS)) {
                        mUpdateCpuStatsNow.invoke(mService);
                        ProcessCpuTracker pProcessCpuTracker =
                                (ProcessCpuTracker) mProcessCpuTrackerField.get(mService.mAppProfiler);
                        totalCpu = (int)pProcessCpuTracker.getTotalCpuPercent();
                        Slog.d(TAG, "appNotResponding totalCPU = " + totalCpu);
                    }
                    float count = (float)getFreeMemory()/getTotalMemory();
                    if(mLastAnrTime + ANR_INTERVAL > anrTime) {
                        if (totalCpu >90 || count < 0.3f ) {
                            mIgnoreAnrTime += 1;
                            Slog.w(TAG, "Anr too offen just ignore repeated anr " + mIgnoreAnrTime + "times! in heigh CPU or Low memory");
                            anrDumpRecord.mIsCompleted = true;
                            mIgnoreAnr = true;
                        } else {
                            mLastAnrTime = anrTime;
                            mIgnoreAnrTime = 0;
                            Slog.w(TAG, "appNotResponding not low memory or heigh CPU ");
                mAnrDumpManager.putDumpRecord(pid, anrDumpRecord);
                        }
                    } else {
                        mLastAnrTime = anrTime;
                        mIgnoreAnrTime = 0;
                        Slog.w(TAG, "appNotResponding dumping!");
                       mAnrDumpManager.mDumpList.put(pid, anrDumpRecord);
                    }
                }
                else {
                    mAnrDumpManager.mDumpList.put(pid, anrDumpRecord);
                }
                //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 end

                /**disable ANR asyncdump
                mAnrDumpManager.startAsyncDump(anrDumpRecord, isSilentANR);
                **/
            }

            if (anrDumpRecord != null) {
                synchronized (anrDumpRecord) {
                    mAnrDumpManager.dumpAnrDebugInfo(anrDumpRecord, false, isSilentANR, criticalEventLog, auxiliaryTaskExecutor, latencyTracker, app, updateCpuStatsNowFirstCall, memoryHeaders, firstPidFilePromise);
                }
            }
            mAnrDumpManager.removeDumpRecord(anrDumpRecord);

            if (anrDumpRecord != null) {
                anrDumpRecord.mCpuInfo = anrDumpRecord.mCpuInfo + mMessageMap.get(pid);
            }
        }


        if (mTracesFile == null) {
           // There is no trace file, so dump (only) the alleged culprit's threads to the log
           Process.sendSignal(pid, Process.SIGNAL_QUIT);
        } else if (firstPidEndOffset.get() > 0) {
              // We've dumped into the trace file successfully
              // We pass the start and end offsets of the first section of
              // the ANR file (the headers and first process dump)
              final long startOffset = 0L;
              final long endOffset = firstPidEndOffset.get();
              mService.mProcessList.mAppExitInfoTracker.scheduleLogAnrTrace(
                      pid, uid, app.getPackageList() , mTracesFile, startOffset, endOffset);
        }

        FrameworkStatsLog.write(FrameworkStatsLog.ANR_OCCURRED,
                (int) mUidField.get(app),processName, activityShortComponentName, annotation,
                appInfo.isInstantApp()
                        ? FrameworkStatsLog.ANROCCURRED__IS_INSTANT_APP__TRUE
                        : FrameworkStatsLog.ANROCCURRED__IS_INSTANT_APP__FALSE,
                app.isInterestingToUserLocked()
                        ? FrameworkStatsLog.ANROCCURRED__FOREGROUND_STATE__FOREGROUND
                        : FrameworkStatsLog.ANROCCURRED__FOREGROUND_STATE__BACKGROUND,
                app.getProcessClassEnum(),
                appInfo.packageName, incrementalMetrics != null
                /* isIncremental */, loadingProgress, incrementalMetrics != null ?
                incrementalMetrics.getMillisSinceOldestPendingRead() : -1,
                                0 /* storage_health_code */,
                0 /* data_loader_status_code */,
                false /* read_logs_enabled */,
                0 /* millis_since_last_data_loader_bind */,
                0 /* data_loader_bind_delay_millis */,
                0 /* total_delayed_reads */,
                0 /* total_failed_reads */,
                0 /* last_read_error_uid */,
                0 /* last_read_error_millis_since */,
                0 /* last_read_error_code */,
                -1);
        Slog.i(TAG, "addErrorToDropBox app = " + app + " processName = " + processName
                + " activityShortComponentName = "+ activityShortComponentName
                + " parentShortComponentName = " + parentShortComponentName + " parentProcess = "
                + parentProcess + " annotation = " + annotation + " mTracesFile = " + mTracesFile);

        //T-HUB Core[SDD]: add by yu.zhao2 2024/12/24 start
        com.transsion.hubcore.server.am.ITranActivityManagerService.Instance()
                .handleAppNotResponding("anr", app, app.processName, activityShortComponentName,
                        parentShortComponentName, parentProcess, annotation,
                        anrDumpRecord != null ? anrDumpRecord.mCpuInfo : "", mTracesFile);
        //T-HUB Core[SDD]: add by yu.zhao2 2024/12/24 end

        //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 start
        if (mIgnoreAnr) {
            //ignore db dump
            mIgnoreAnr = false;
        } else {
        mService.addErrorToDropBox("anr", app, processName, activityShortComponentName,
                parentShortComponentName, parentProcess, annotation,
                anrDumpRecord != null ? anrDumpRecord.mCpuInfo : "", mTracesFile, null,
                new Float(loadingProgress), incrementalMetrics, errorId, null);
        }
        //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 end


        //SDD:add app recovery by jianzhou.liu 2024-08-01 start
        if(mService.canRecoveryWhenError(app, null, "anr")){
            return true;
        }
        //SDD:add app recovery by jianzhou.liu 2024-08-01 end
        Slog.i(TAG, " controller = " + controller);
        if (controller != null) {
            try {
                // 0 == show dialog, 1 = keep waiting, -1 = kill process
                // immediately
                int res = controller.appNotResponding(processName, pid,
                        anrDumpRecord != null ? anrDumpRecord.mInfo.toString()
                                : "");
                Slog.i(TAG, " res = " + res);
                if (res != 0) {
                    if (res < 0 && pid != mAmsPid) {
                        synchronized (mService) {
                            mKill.invoke(app, new Object[] { "anr", ApplicationExitInfo.REASON_ANR, true });
                        }
                    } else {
                        synchronized (mService) {
                            mScheduleServiceTimeoutLocked.invoke(
                                    mActiveServicesField.get(mService),
                                    new Object[] { app });
                        }
                    }
                    return true;
                }
            } catch (RemoteException e) {
                mControllerField.set(mService.mActivityTaskManager, null);
                Watchdog.getInstance().setActivityController(null);
            }
        }

        synchronized (mService) {
            // mBatteryStatsService can be null if the AMS is constructed with injector only. This
            // will only happen in tests.
            if (mService.mBatteryStatsService != null) {
                mService.mBatteryStatsService.noteProcessAnr(processName, uid);
            }

            mNoteProcessANR.invoke(mBatteryStatsServiceField.get(mService),
                    new Object[] { processName, (int) mUidField.get(app) });

            if (isSilentANR && !app.isDebugging()) {
                mKill.invoke(app, new Object[] { "bg anr", ApplicationExitInfo.REASON_ANR, true });
                return true;
            }

            // Set the app's notResponding state, and look up the
            // errorReportReceiver
            mMakeAppNotRespondingLocked.invoke(
                    processESR,
                    new Object[] {
                            activityShortComponentName,
                            annotation != null ? "ANR " + annotation : "ANR",
                            anrDumpRecord != null ? anrDumpRecord.mInfo
                                    .toString() : "" });

            // Bring up the infamous App Not Responding dialog
            if (mService.mUiHandler != null) {
                Message msg = Message.obtain();
                msg.what = 2;
                //T-HUB Core[OS]:add by yu.xie for TESSCR-25775 2025/05/16 start
                AppNotRespondingDialog.Data data = new AppNotRespondingDialog.Data(app, aInfo, aboveSystem, isContinuousAnr);
                data.dataFile = mTracesFile;
                data.subject = annotation;
                msg.obj = data;
                //T-HUB Core[OS]:add by yu.xie for TESSCR-25775 2025/05/16 end

                Handler mUiHandler = (Handler) mUiHandlerField.get(mService);
                mUiHandler.sendMessageDelayed(msg, anrDialogDelayMs);
            }
        }
        return true;
    }

    public class AnrMonitorHandler extends Handler {
        public AnrMonitorHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case START_MONITOR_BROADCAST_TIMEOUT_MSG:
            case START_MONITOR_SERVICE_TIMEOUT_MSG:
            case START_MONITOR_KEYDISPATCHING_TIMEOUT_MSG:
                updateProcessStats();
                break;
            case START_ANR_DUMP_MSG:
//                AnrDumpRecord adp = (AnrDumpRecord) msg.obj;
//                boolean isSilentANR = msg.arg1 == 1;
//                Slog.i(TAG, "START_ANR_DUMP_MSG: " + adp + ", isSilentANR = " + isSilentANR);
//                mAnrDumpManager.dumpAnrDebugInfo(adp, true, isSilentANR, null);
                break;
            default:
                break;
            }
        }
    };

    protected static final class BinderWatchdog {
        private static final int MAX_TIMEOUT_PIDS = 5;
        private static final int MAX_LINES = 64;

        protected static class BinderInfo {
            /*
             * Node format Example: 2:execution 658:729 to 670:684 spends 4000
             * ms <> dex_code 4 start_at 71.726 2012-05-15 05:31:07.989
             */
            protected static final int INDEX_FROM = 1;
            protected static final int INDEX_TO = 3;

            protected int mSrcPid;
            protected int mSrcTid;
            protected int mDstPid;
            protected int mDstTid;
            protected String mText;

            protected BinderInfo(String text) {
                if (text == null || text.length() <= 0) {
                    return;
                }

                mText = new String(text);
                String[] tokens = text.split(" ");
                String[] from = tokens[INDEX_FROM].split(":");
                if (from != null && from.length == 2) {
                    mSrcPid = Integer.parseInt(from[0]);
                    mSrcTid = Integer.parseInt(from[1]);
                }

                String[] to = tokens[INDEX_TO].split(":");
                if (to != null && to.length == 2) {
                    mDstPid = Integer.parseInt(to[0]);
                    mDstTid = Integer.parseInt(to[1]);
                }
            }
        }

        public static final ArrayList<Integer> getTimeoutBinderPidList(int pid,
                int tid) {
            if (pid <= 0) {
                return null;
            }

            ArrayList<BinderInfo> binderList = readTimeoutBinderListFromFile();
            BinderInfo next = getBinderInfo(pid, tid, binderList);

            int count = 0;
            ArrayList<Integer> pidList = new ArrayList<Integer>();
            while (next != null) {
                if (next.mDstPid > 0) {
                    count++;
                    if (!pidList.contains(next.mDstPid)) {
                        Slog.i(TAG, "getTimeoutBinderPidList pid added: "
                                + next.mDstPid + " " + next.mText);
                        pidList.add(next.mDstPid);
                    } else {
                        Slog.i(TAG, "getTimeoutBinderPidList pid existed: "
                                + next.mDstPid + " " + next.mText);
                    }

                    if (count >= MAX_TIMEOUT_PIDS) {
                        break;
                    }
                }
                next = getBinderInfo(next.mDstPid, next.mDstTid, binderList);
            }

            if (pidList == null || pidList.size() == 0) {
                return getTimeoutBinderFromPid(pid, binderList);
            }
            return pidList;
        }

        public static final ArrayList<Integer> getTimeoutBinderFromPid(int pid,
                ArrayList<BinderInfo> binderList) {
            if (pid <= 0 || binderList == null) {
                return null;
            }

            Slog.i(TAG, "getTimeoutBinderFromPid " + pid + " list size: "
                    + binderList.size());
            int count = 0;
            ArrayList<Integer> pidList = new ArrayList<Integer>();
            for (BinderInfo bi : binderList) {
                if (bi != null && bi.mSrcPid == pid) {
                    count++;
                    if (!pidList.contains(bi.mDstPid)) {
                        Slog.i(TAG, "getTimeoutBinderFromPid pid added: "
                                + bi.mDstPid + " " + bi.mText);
                        pidList.add(bi.mDstPid);
                    } else {
                        Slog.i(TAG, "getTimeoutBinderFromPid pid existed: "
                                + bi.mDstPid + " " + bi.mText);
                    }

                    if (count >= MAX_TIMEOUT_PIDS) {
                        break;
                    }
                }
            }
            return pidList;
        }

        private static BinderInfo getBinderInfo(int pid, int tid,
                ArrayList<BinderInfo> binderList) {
            if (binderList == null || binderList.size() == 0 || pid == 0) {
                return null;
            }
            int size = binderList.size();
            for (BinderInfo bi : binderList) {
                if (bi.mSrcPid == pid && bi.mSrcTid == tid) {
                    Slog.i(TAG, "Timeout binder pid found: " + bi.mDstPid + " "
                            + bi.mText);
                    return bi;
                }
            }
            return null;
        }

        private static final ArrayList<BinderInfo> readTimeoutBinderListFromFile() {
            BufferedReader br = null;
            ArrayList<BinderInfo> binderList = null;
            try {
                File file = new File(BINDERINFO_PATH + "/timeout_log");
                if (file == null || !file.exists()) {
                    return null;
                }
                br = new BufferedReader(new FileReader(file));
                String line;
                binderList = new ArrayList<BinderInfo>();
                while ((line = br.readLine()) != null) {
                    BinderInfo bi = new BinderInfo(line);
                    if (bi != null && bi.mSrcPid > 0) {
                        binderList.add(bi);
                    }
                    if (binderList.size() > MAX_LINES) {
                        break;
                    }
                }
            } catch (FileNotFoundException e) {
                Slog.e(TAG, "FileNotFoundException", e);
            } catch (IOException e) {
                Slog.e(TAG, "IOException when gettting Binder. ", e);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ioe) {
                        Slog.e(TAG, "IOException when close buffer reader:",
                                ioe);
                    }
                }
                return binderList;
            }
        }

        protected static class TransactionInfo {
            protected String direction;
            protected String snd_pid;
            protected String snd_tid;
            protected String rcv_pid;
            protected String rcv_tid;
            protected String ktime;
            protected String atime;
            protected long spent_time;

            protected TransactionInfo() {
            };
        }

        private static final void readTransactionInfoFromFile(int pid,
                ArrayList<Integer> binderList) {
            String patternStr = "(\\S+.+transaction).+from\\s+(\\d+):(\\d+)\\s+to\\s+(\\d+):"
                    + "(\\d+).+start\\s+(\\d+\\.+\\d+).+android\\s+(\\d+-\\d+-\\d+\\s+\\d+:\\d+:\\d"
                    + "+\\.\\d+)";
            Pattern pattern = Pattern.compile(patternStr);

            BufferedReader br = null;
            ArrayList<TransactionInfo> transactionList = new ArrayList<TransactionInfo>();
            ArrayList<Integer> pidList = new ArrayList<Integer>();
            try {
                String filepath = BINDERINFO_PATH + "/proc/"
                        + Integer.toString(pid);
                File file = new File(filepath);
                if (file == null || !file.exists()) {
                    Slog.d(TAG, "Filepath isn't exist");
                    return;
                }

                br = new BufferedReader(new FileReader(file));
                String line;
                Matcher matcher;
                while ((line = br.readLine()) != null) {
                    if (line.contains("transaction")) {
                        matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            TransactionInfo tmpInfo = new TransactionInfo();
                            tmpInfo.direction = matcher.group(1);
                            tmpInfo.snd_pid = matcher.group(2);
                            tmpInfo.snd_tid = matcher.group(3);
                            tmpInfo.rcv_pid = matcher.group(4);
                            tmpInfo.rcv_tid = matcher.group(5);
                            tmpInfo.ktime = matcher.group(6);
                            tmpInfo.atime = matcher.group(7);
                            tmpInfo.spent_time = SystemClock.uptimeMillis()
                                    - (long) (Float.valueOf(tmpInfo.ktime) * 1000);
                            transactionList.add(tmpInfo);
                            if (tmpInfo.spent_time >= 1000) {
                                if (!binderList.contains(Integer
                                        .valueOf(tmpInfo.rcv_pid))) {
                                    binderList.add(Integer
                                            .valueOf(tmpInfo.rcv_pid));
                                    if (!pidList.contains(Integer
                                            .valueOf(tmpInfo.rcv_pid))) {
                                        pidList.add(Integer
                                                .valueOf(tmpInfo.rcv_pid));
                                        Slog.i(TAG,
                                                "Transcation binderList pid="
                                                        + tmpInfo.rcv_pid);
                                    }
                                }
                            }
                            Slog.i(TAG, tmpInfo.direction + " from "
                                    + tmpInfo.snd_pid + ":" + tmpInfo.snd_tid
                                    + " to " + tmpInfo.rcv_pid + ":"
                                    + tmpInfo.rcv_tid + " start "
                                    + tmpInfo.ktime + " android time "
                                    + tmpInfo.atime + " spent time "
                                    + tmpInfo.spent_time + " ms");
                        }
                    } else {
                        if (line.indexOf("node") != -1
                                && line.indexOf("node") < 20) {
                            break;
                        }
                    }
                }

                for (int pidnumber : pidList) {
                    readTransactionInfoFromFile(pidnumber, binderList);
                }

            } catch (FileNotFoundException e) {
                Slog.e(TAG, "FileNotFoundException", e);
            } catch (IOException e) {
                Slog.e(TAG, "IOException when gettting Binder. ", e);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ioe) {
                        Slog.e(TAG, "IOException when close buffer reader:",
                                ioe);
                    }
                }
            }
        }

        private static final void setTransactionTimeoutPids(int pid,
                ArrayList<Integer> desList, SparseBooleanArray lastPids) {
            ArrayList<Integer> tmpPidList = new ArrayList<Integer>();
            BinderWatchdog.readTransactionInfoFromFile(pid, tmpPidList);
            if (tmpPidList != null && tmpPidList.size() > 0) {
                for (Integer bpid : tmpPidList) {
                    if (bpid != null) {
                        int pidValue = bpid.intValue();
                        if (pidValue != pid) {
                            if (!desList.contains(pidValue)) {
                                desList.add(pidValue);
                                if (lastPids != null) {
                                    lastPids.delete(pidValue);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void prepareStackTraceFile(String filePath) {
        Slog.i(TAG, "prepareStackTraceFile: " + filePath);
        if (filePath == null || filePath.length() == 0) {
            return;
        }

        File traceFile = new File(filePath);
        try {
            // Create folder if not already existed.
            File traceDir = traceFile.getParentFile();
            if (traceDir != null) {
                if (!traceDir.exists()) {
                    traceDir.mkdirs();
                }
                // Create folder and set needed permission - drwxrwxr-x.
                FileUtils.setPermissions(traceDir.getPath(), 0775, -1, -1);
            }
            // Create file and set needed permission - -rw-rw-rw-.
            if (!traceFile.exists()) {
                traceFile.createNewFile();
            }
            FileUtils.setPermissions(traceFile.getPath(), 0666, -1, -1);
        } catch (IOException e) {
            Slog.e(TAG, "Unable to prepare stack trace file: " + filePath, e);
        }
    }

    public class AnrDumpRecord {
        protected int mAppPid;
        protected int mAppUid;
        protected int mAppUserid;
        protected boolean mAppCrashing;
        protected String mProcessName;
        protected String mAppString;
        protected String mShortComponentName;
        protected int mParentAppPid;
        protected String mParentShortComponentName;
        protected String mAnnotation;
        protected long mAnrTime;
        public String mCpuInfo = null;
        public StringBuilder mInfo = new StringBuilder(256);
        protected boolean mIsCompleted;
        protected boolean mIsCancelled;
        protected ApplicationInfo mAppInfo;

        public AnrDumpRecord(int appPid, int appUid, int appUserid, ApplicationInfo appInfo,
                boolean appCrashing, String processName, String appString,
                String shortComponentName, int parentAppPid,
                String parentShortComponentName, String annotation, long anrTime) {
            mAppPid = appPid;
            mAppUid = appUid;
            mAppUserid = appUserid;
            mAppInfo = appInfo;
            mAppCrashing = appCrashing;
            mProcessName = processName;
            mAppString = appString;
            mShortComponentName = shortComponentName;
            mParentAppPid = parentAppPid;
            mParentShortComponentName = parentShortComponentName;
            mAnnotation = annotation;
            mAnrTime = anrTime;
        }

        private boolean isValid() {
            if (mAppPid <= 0 || mIsCancelled || mIsCompleted) {
                Slog.e(TAG, "isValid! mAppPid: " + mAppPid + "mIsCancelled: "
                        + mIsCancelled + "mIsCompleted: " + mIsCompleted);
                return false;
            } else {
                return true;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("AnrDumpRecord{ ");
            sb.append(mAnnotation);
            sb.append(" ");
            sb.append(mAppString);
            sb.append(" IsCompleted:" + mIsCompleted);
            sb.append(" IsCancelled:" + mIsCancelled);
            sb.append(" }");
            return sb.toString();
        }
    }

    public class AnrDumpManager {
        public HashMap<Integer, AnrDumpRecord> mDumpList = new HashMap<Integer, AnrDumpRecord>();

        public void cancelDump(AnrDumpRecord dumpRecord) {
            if (dumpRecord == null || dumpRecord.mAppPid == -1) {
                return;
            }

            synchronized (mDumpList) {
                AnrDumpRecord value = mDumpList.remove(dumpRecord.mAppPid);
                if (value != null) {
                    value.mIsCancelled = true;
                }
            }
        }
        public void putDumpRecord(int pid, AnrDumpRecord dumpRecord) {
            if (dumpRecord == null || dumpRecord.mAppPid == -1) {
                return;
            }

            synchronized (mDumpList) {
                AnrDumpRecord value = mDumpList.put(pid, dumpRecord);
            }
        }

        public void removeDumpRecord(AnrDumpRecord dumpRecord) {
            if (dumpRecord == null || dumpRecord.mAppPid == -1) {
                return;
            }

            synchronized (mDumpList) {
                AnrDumpRecord value = mDumpList.remove(dumpRecord.mAppPid);
            }
        }

        public void startAsyncDump(AnrDumpRecord dumpRecord, boolean isSilentANR) {
            Slog.i(TAG, "startAsyncDump: " + dumpRecord + ", isSilentANR = " + isSilentANR);
            if (dumpRecord == null || dumpRecord.mAppPid == -1) {
                return;
            }

            int appPid = dumpRecord.mAppPid;
            synchronized (mDumpList) {
                if (mDumpList.containsKey(appPid)) {
                    return;
                }

                putDumpRecord(appPid, dumpRecord);

                Message msg = mAnrHandler.obtainMessage(START_ANR_DUMP_MSG,
                        dumpRecord);
                msg.arg1 = isSilentANR ? 1 : 0;
                mAnrHandler.sendMessageAtTime(msg,
                        SystemClock.uptimeMillis() + 500);
            }
        }

        private boolean isDumpable(AnrDumpRecord dumpRecord) {
            synchronized (mDumpList) {
                if (dumpRecord != null
                        && mDumpList.containsKey(dumpRecord.mAppPid)
                        && dumpRecord.isValid()) {
                    return true;
                } else {
                    return false;
                }
            }
        }

        public void dumpAnrDebugInfo(AnrDumpRecord dumpRecord,
                boolean onlyDumpSelf, final boolean isSilentANR, String criticalEventLog,
                ExecutorService auxiliaryTaskExecutor, AnrLatencyTracker latencyTracker,
                ProcessRecord mApp,Future<?> updateCpuStatsNowFirstCall,
                LinkedHashMap<String, String> memoryHeaders, Future<File> firstPidFilePromise) {
            Slog.i(TAG, "dumpAnrDebugInfo begin: " + dumpRecord
                    + ", onlyDumpSelf = " + onlyDumpSelf + ", isSilentANR = " + isSilentANR);
            if (dumpRecord == null) {
                return;
            }

            try {
                if (!isDumpable(dumpRecord)) {
                    Slog.i(TAG, "dumpAnrDebugInfo dump stopped: " + dumpRecord);
                    return;
                }
                dumpAnrDebugInfoLocked(dumpRecord, onlyDumpSelf, isSilentANR, criticalEventLog, auxiliaryTaskExecutor, latencyTracker, mApp, updateCpuStatsNowFirstCall, memoryHeaders, firstPidFilePromise);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Slog.i(TAG, "dumpAnrDebugInfo end: " + dumpRecord
                    + ", onlyDumpSelf = " + onlyDumpSelf + " , isSilentANR = " + isSilentANR);
        }

        protected void dumpAnrDebugInfoLocked(AnrDumpRecord dumpRecord,
                boolean onlyDumpSelf, final boolean isSilentANR, String criticalEventLog,
                ExecutorService auxiliaryTaskExecutor, AnrLatencyTracker latencyTracker,
                ProcessRecord mApp, Future<?> updateCpuStatsNowFirstCall,
                LinkedHashMap<String, String> memoryHeaders,
                Future<File> firstPidFilePromise) throws Exception {
            synchronized (dumpRecord) {
                Slog.i(TAG, "dumpAnrDebugInfoLocked: " + dumpRecord
                        + ", onlyDumpSelf = " + onlyDumpSelf + ", isSilentANR = " + isSilentANR);
                if (!isDumpable(dumpRecord)) {
                    return;
                }

                int appPid = dumpRecord.mAppPid;
                int uid = dumpRecord.mAppUid;
                int userId = dumpRecord.mAppUserid;
                int parentAppPid = dumpRecord.mParentAppPid;
                ArrayList<Integer> firstPids = new ArrayList<Integer>();
                SparseBooleanArray lastPids = new SparseBooleanArray(20);
                final PackageManagerInternal packageManagerInternal =
                        mService.getPackageManagerInternal();

                /** ALPS03762884 Avoiding send sig 3 to native process
                ArrayList<Integer> binderPids = null;
                if (appPid != -1) {
                    binderPids = BinderWatchdog.getTimeoutBinderPidList(appPid,
                            appPid);
                }
                **/

                // Dump thread traces as quickly as we can, starting with "interesting" processes.
                firstPids.add(appPid);

                // Don't dump other PIDs if it's a background ANR or is requested to only dump self.
                if (!onlyDumpSelf && !isSilentANR) {
                    int parentPid = appPid;
                    if (parentAppPid > 0) parentPid = parentAppPid;
                    if (parentPid != appPid) firstPids.add(parentPid);
                    if (mAmsPid != appPid && mAmsPid != parentPid) {
                        firstPids.add(mAmsPid);
                    }
                    //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 start
                    ProcessCpuTracker pProcessCpuTracker =
                        (ProcessCpuTracker) mProcessCpuTrackerField.get(mService.mAppProfiler);
                    //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 end
                    synchronized (mService) {
                        ProcessList mProcessList = (ProcessList) mProcessListField.get(mService);
                        ArrayList<ProcessRecord> mLruProcesses
                           = (ArrayList<ProcessRecord>) mLruProcessesField.get(mProcessList);

                        for (int i = mLruProcesses.size() - 1; i >= 0; i--) {
                            ProcessRecord r = mLruProcesses.get(i);
                            if (r != null
                                    && (IApplicationThread) mThreadField.get(r) != null) {
                                int pid = (int) mPidField.get(r);
                                if (pid > 0 && pid != appPid
                                        && pid != parentPid && pid != mAmsPid) {
                                    if ((boolean) mPersistentField.get(r)) {
                                        //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 start
                                        if (ANR_DUMP_OPT) {
                                            boolean isNeedAdd = false;
                                            int size1 = pProcessCpuTracker.countWorkingStats();
                                            if (size1 >= 10) {
                                                size1 = 10;
                                            }
                                            for (int j = 0; j < size1; j ++) {
                                                if (pid == pProcessCpuTracker.getWorkingStats(j).pid) {
                                                    isNeedAdd = true;
                                                    break;
                                                }
                                            }
                                            if (isNeedAdd) {
                                                firstPids.add(pid);
                                            } else {
                                                Slog.d(TAG, "dumpAnrDebugInfoLocked drop pid = " + pid);
                                            }
                                        } else {
                                            firstPids.add(pid);
                                        }
                                        //SDD:modify TESSCR-20036 by huan.liu5 2023/05/31 end
                                    } else if (r.mServices.isTreatedLikeActivity()) {
                                        firstPids.add(pid);
                                    } else {
                                        lastPids.put(pid, Boolean.TRUE);
                                    }
                                }
                            }
                        }
                    }
                }
                /** ALPS03762884 Avoiding send sig 3 to native process
                if (binderPids != null && binderPids.size() > 0) {
                    for (Integer bpid : binderPids) {
                        if (bpid != null) {
                            int pidValue = bpid.intValue();
                            if (pidValue != appPid && pidValue != parentPid
                                    && pidValue != mAmsPid) {
                                if (!firstPids.contains(pidValue)) {
                                    firstPids.add(pidValue);
                                    lastPids.remove(pidValue);
                                }
                            }
                        }
                    }
                }
                **/

                ArrayList<Integer> remotePids = new ArrayList<Integer>();
                if (appPid != -1) {
                    BinderWatchdog.setTransactionTimeoutPids(appPid, remotePids,
                            lastPids);
                }

                String annotation = dumpRecord.mAnnotation;

                // Check if package is still being loaded
                if (dumpRecord.mAppInfo != null && dumpRecord.mAppInfo.packageName != null) {
                    IncrementalStatesInfo incrementalStatesInfo =
                            packageManagerInternal.getIncrementalStatesInfo(
                                    dumpRecord.mAppInfo.packageName, uid, userId);
                    if (incrementalStatesInfo != null) {
                        loadingProgress = incrementalStatesInfo.getProgress();
                    }
                    final String codePath = dumpRecord.mAppInfo.getCodePath();
                    if (IncrementalManager.isIncrementalPath(codePath)) {
                        // Report in the main log that the incremental package is still loading
                        Slog.e(TAG, "App crashed on incremental package " + dumpRecord.mAppInfo.
                                packageName + " which is " + ((int) (loadingProgress * 100)) +
                                "% loaded.");
                        final IBinder incrementalService = ServiceManager.getService(
                                Context.INCREMENTAL_SERVICE);
                        if (incrementalService != null) {
                           final IncrementalManager incrementalManager = new IncrementalManager(
                                   IIncrementalService.Stub.asInterface(incrementalService));
                           incrementalMetrics = incrementalManager.getMetrics(codePath);
                        }
                    }
                }

                // Log the ANR to the main log.
                StringBuilder info = dumpRecord.mInfo;
                info.setLength(0);
                info.append("ANR in ").append(dumpRecord.mProcessName);
                if (dumpRecord.mShortComponentName != null) {
                    info.append(" (").append(dumpRecord.mShortComponentName)
                            .append(")");
                }
                info.append(", time=").append(dumpRecord.mAnrTime);
                info.append("\n");
                if (annotation != null) {
                    info.append("Reason: ").append(annotation).append("\n");
                }
                if (dumpRecord.mParentAppPid != -1
                          && dumpRecord.mParentAppPid != dumpRecord.mAppPid) {
                      info.append("Parent: ")
                              .append(dumpRecord.mParentShortComponentName)
                              .append("\n");
                }

                if (incrementalMetrics != null) {
                    // Report in the main log about the incremental package
                    info.append("Package is ").append((int) (loadingProgress * 100)).append(
                            "% loaded.\n");
                }

               // Retrieve controller with max ANR delay from AnrControllers
               // Note that we retrieve the controller before dumping stacks because dumping stacks
               // can take a few seconds, after which the cause of the ANR delay might have
               // completed and there might no longer be a valid ANR controller to cancel the dialog
               // in that case
               AnrController anrController = mService.mActivityTaskManager.getAnrController(aInfo);

               if (anrController != null) {
                   String packageName = aInfo.packageName;
                   int aInfo_uid = aInfo.uid;
                   anrDialogDelayMs = anrController.getAnrDelayMillis(packageName, aInfo_uid);
                   // Might execute an async binder call to a system app to show an interim
                   // ANR progress UI
                   anrController.onAnrDelayStarted(packageName, aInfo_uid);
                   Slog.i(TAG, "ANR delay of " + anrDialogDelayMs + "ms started for " +
                          packageName);
                }
                StringBuilder report = new StringBuilder();
                latencyTracker.currentPsiStateCalled();
                String currentPsiState = ResourcePressureUtil.currentPsiState();
                latencyTracker.currentPsiStateReturned();
                report.append(ResourcePressureUtil.currentPsiState());
                final ProcessCpuTracker processStats = new ProcessCpuTracker(true);

                if (!isDumpable(dumpRecord)) {
                    return;
                }
                // We push the native pids collection task to the helper thread through
                // the Anr auxiliary task executor, and wait on it later after dumping the first pids
                Future<ArrayList<Integer>> nativePidsFuture =
                auxiliaryTaskExecutor.submit(
                    () -> {
                        latencyTracker.nativePidCollectionStarted();
                        // don't dump native PIDs for background ANRs unless
                        // it is the process of interest
                        String[] nativeProcs = null;
                        if (isSilentANR || onlyDumpSelf) {
                            for (int i = 0; i < NATIVE_STACKS_OF_INTEREST.length; i++) {
                                if (NATIVE_STACKS_OF_INTEREST[i].equals(mApp.processName)) {
                                    nativeProcs = new String[] { mApp.processName };
                                    break;
                                }
                            }
                        } else {
                            nativeProcs = NATIVE_STACKS_OF_INTEREST;
                        }

                        int[] pids = nativeProcs == null
                                ? null : Process.getPidsForCommands(nativeProcs);
                        ArrayList<Integer> nativePids = null;

                        if (pids != null) {
                            nativePids = new ArrayList<>(pids.length);
                            for (int i : pids) {
                                nativePids.add(i);
                            }
                        }
                        latencyTracker.nativePidCollectionEnded();
                        return nativePids;
                    });
                // For background ANRs, don't pass the ProcessCpuTracker to
                // avoid spending 1/2 second collecting stats to rank lastPids.
                StringWriter tracesFileException = new StringWriter();
                // To hold the start and end offset to the ANR trace file respectively.

                Slog.i(TAG, "dumpStackTraces begin!");
                mTracesFile = StackTracesDumpHelper.dumpStackTraces(firstPids,
                                  isSilentANR ? null : processStats,
                                  isSilentANR ? null : lastPids,
                                  nativePidsFuture, tracesFileException, firstPidEndOffset, annotation,
                                  criticalEventLog, memoryHeaders, auxiliaryTaskExecutor, firstPidFilePromise, latencyTracker);
                Slog.i(TAG, "dumpStackTraces end!");

                if (!isDumpable(dumpRecord)) {
                    return;
                }

                String cpuInfo = null;
                ProcessCpuTracker mProcessCpuTracker =
                    (ProcessCpuTracker) mProcessCpuTrackerField.get(mService.mAppProfiler);
                synchronized (mProcessCpuTracker) {
                    cpuInfo = getAndroidTime() + mProcessCpuTracker
                        .printCurrentState(dumpRecord.mAnrTime);
                    dumpRecord.mCpuInfo = dumpRecord.mCpuInfo + cpuInfo;
                }
                try {
                    updateCpuStatsNowFirstCall.get();
                } catch (ExecutionException e) {
                    Slog.w(TAG, "Failed to update the CPU stats", e.getCause());
                } catch (InterruptedException e) {
                    Slog.w(TAG, "Interrupted while updating the CPU stats", e);
                }
                mUpdateCpuStatsNow.invoke(mService);
                report.append(cpuInfo);
                info.append(processStats.printCurrentLoad());
                info.append(report);
                report.append(tracesFileException.getBuffer());
                info.append(processStats.printCurrentState(dumpRecord.mAnrTime));

                Slog.i(TAG, info.toString());

                if (!isDumpable(dumpRecord)) {
                    return;
                }

                dumpRecord.mIsCompleted = true;
            }
        }
    }

    public boolean isJavaProcess(int pid) {
        if (pid <= 0) {
            return false;
        }

        if (mZygotePids == null) {
            String[] commands = new String[] {
               "zygote64",
               "zygote"
            };
            mZygotePids = Process.getPidsForCommands(commands);
        }

        if (mZygotePids != null) {
            int parentPid = Process.getParentPid(pid);
            for (int zygotePid : mZygotePids) {
                if (parentPid == zygotePid) {
                    return true;
                }
            }
        }
        Slog.i(TAG, "pid: " + pid + " is not a Java process");
        return false;
    }

    private Boolean isException() {
        try {
            String status = "free";
            // Two value: "free" and "dumping"
            if (status.equals(SystemProperties.get("vendor.debug.mtk.aee.status",
                    status))
                    && status.equals(SystemProperties.get(
                            "vendor.debug.mtk.aee.status64", status))
                    && status.equals(SystemProperties.get(
                            "vendor.debug.mtk.aee.vstatus", status))
                    && status.equals(SystemProperties.get(
                            "vendor.debug.mtk.aee.vstatus64", status))) {
                return false;
            }
        } catch (Exception e) {
            Slog.e(TAG, "isException: " + e.toString());
        }
        return true;
    }

    public void informMessageDump(String MessageInfo, int pid) {
        if (mMessageMap.containsKey(pid)) {
            String tmpString = mMessageMap.get(pid);
            if (tmpString.length() > MESSAGE_MAP_BUFFER_SIZE_MAX) {
                tmpString = "";
            }
            tmpString = tmpString + MessageInfo;
            mMessageMap.put(pid, tmpString);
        } else {
            if (mMessageMap.size() > MESSAGE_MAP_BUFFER_COUNT_MAX) {
                mMessageMap.clear();
            }
            mMessageMap.put(pid, MessageInfo);
        }
        Slog.i(TAG, "informMessageDump pid= " + pid);
    }

    public int checkAnrDebugMechanism() {
        if (!sEnhanceEnable) {
            return DISABLE_ALL_ANR_MECHANISM;
        }
        if (INVALID_ANR_OPTION == mAnrOption) {
            int option = ENABLE_ALL_ANR_MECHANISM;
            if (IS_USER_LOAD) {
                option = DISABLE_PARTIAL_ANR_MECHANISM;
            }
            mAnrOption = SystemProperties.getInt("persist.vendor.anr.enhancement",
                    option);
        }
        switch (mAnrOption) {
        case ENABLE_ALL_ANR_MECHANISM:
            return ENABLE_ALL_ANR_MECHANISM;
        case DISABLE_PARTIAL_ANR_MECHANISM:
            return DISABLE_PARTIAL_ANR_MECHANISM;
        case DISABLE_ALL_ANR_MECHANISM:
            return DISABLE_ALL_ANR_MECHANISM;
        default:
            return ENABLE_ALL_ANR_MECHANISM;
        }
    }

    /**
     * Inform ANR Manager about interested events.
     *
     * @param event The interested event defined at AnrManagerService
     */
    public void writeEvent(int event) {
        switch (event) {
        case EVENT_BOOT_COMPLETED:
            mEventBootCompleted = SystemClock.uptimeMillis();
            break;
        default:
            break;
        }
    }

    /**
     * Check if ANR is deferrable.
     *
     * @return true if ANR can be deferred
     */
    public boolean isAnrDeferrable() {
        if (DISABLE_ALL_ANR_MECHANISM == checkAnrDebugMechanism()) {
            return false;
        }

        if ("dexopt".equals(SystemProperties.get("vendor.anr.autotest"))) {
            Slog.i(TAG,
                    "We are doing TestDexOptSkipANR; return true in this case");
            return true;
        }
        if ("enable".equals(SystemProperties.get("vendor.anr.autotest"))) {
            Slog.i(TAG, "Do Auto Test, don't skip ANR");
            return false;
        }

        long now = SystemClock.uptimeMillis();
        if (!IS_USER_BUILD) {
            if (mEventBootCompleted == 0
                    || (now - mEventBootCompleted < ANR_BOOT_DEFER_TIME)) {
                /* ANR happened before boot completed + N seconds */
                Slog.i(TAG, "isAnrDeferrable(): true since"
                        + " mEventBootCompleted = " + mEventBootCompleted
                        + " now = " + now);
                return true;
            } else if (isException()) {
                Slog.i(TAG, "isAnrDeferrable(): true since exception");
                return true;
            } else {
                float lastCpuUsage = mAnrProcessStats.getTotalCpuPercent();
                updateProcessStats();
                float currentCpuUsage = mAnrProcessStats.getTotalCpuPercent();
                if (lastCpuUsage > ANR_CPU_THRESHOLD
                        && currentCpuUsage > ANR_CPU_THRESHOLD) {
                    if (mCpuDeferred == 0) {
                        mCpuDeferred = now;
                        Slog.i(TAG, "isAnrDeferrable(): true since CpuUsage = "
                                + currentCpuUsage + ", mCpuDeferred = "
                                + mCpuDeferred);
                        return true;
                    } else if (now - mCpuDeferred < ANR_CPU_DEFER_TIME) {
                        Slog.i(TAG, "isAnrDeferrable(): true since CpuUsage = "
                                + currentCpuUsage + ", mCpuDeferred = "
                                + mCpuDeferred + ", now = " + now);
                        return true;
                    }
                }
                mCpuDeferred = 0;
            }
        }
        return false;
    }

    /**
     * Check if ANR flow is skipped or not.
     *
     * @return true if ANR is skipped
     */
    public boolean isAnrFlowSkipped(int appPid, String appProcessName,
            String annotation, ProcessRecord mApp) {
        if (INVALID_ANR_FLOW == mAnrFlow) {
            mAnrFlow = SystemProperties.getInt("persist.vendor.dbg.anrflow",
                    NORMAL_ANR_FLOW);
        }
        Slog.i(TAG, "isANRFlowSkipped() AnrFlow = " + mAnrFlow);

        switch (mAnrFlow) {
            case NORMAL_ANR_FLOW:
                return false;
            case SKIP_ANR_FLOW:
                Slog.i(TAG, "Skipping ANR flow: " + appPid + " " + appProcessName
                        + " " + annotation);
                return true;
            case SKIP_ANR_FLOW_AND_KILL:
                if (appPid != Process.myPid()) {
                    Slog.i(TAG, "Skipping ANR flow: " + appPid + " "
                        + appProcessName + " " + annotation);
                    Slog.w(TAG, "Skipping ANR flow and Kill process dicectly(" + appPid + ") due to ANR");
                    mService.killAppAtUsersRequest(mApp);
                }
                return true;
            default:
                return false;
        }
    }

    public void updateProcessStats() {
        synchronized (mAnrProcessStats) {
            final long now = SystemClock.uptimeMillis();
            if ((now - mLastCpuUpdateTime.get()) > MONITOR_CPU_MIN_TIME) {
                mLastCpuUpdateTime.set(now);
                mAnrProcessStats.update();
            }
        }
    }

    public String getProcessState() {
        synchronized (mAnrProcessStats) {
            return mAnrProcessStats.printCurrentState(SystemClock
                    .uptimeMillis());
        }
    }

    public String getAndroidTime() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SS");
        Date date = new Date(System.currentTimeMillis());
        Formatter formatter = new Formatter();
        return "Android time :["
                + simpleDateFormat.format(date)
                + "] ["
                + formatter.format("%.3f",
                        (float) SystemClock.uptimeMillis() / 1000) + "]\n";
    }

    public File createFile(String filepath) {
        File file = new File(filepath);
        if (file == null || !file.exists()) {
            Slog.i(TAG, filepath + " isn't exist");
            return null;
        }
        return file;
    }

    public boolean copyFile(File srcFile, File destFile) {
        boolean result = false;
        try {
            if (!srcFile.exists()) {
                return result;
            }
            if (!destFile.exists()) {
                destFile.createNewFile();
                FileUtils.setPermissions(destFile.getPath(), 0666, -1, -1); // -rw-rw-rw-
            }

            InputStream in = new FileInputStream(srcFile);
            try {
                result = copyToFile(in, destFile);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            Slog.e(TAG, "createFile fail");
            result = false;
        }
        return result;
    }

    public boolean copyToFile(InputStream inputStream, File destFile) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(destFile, true);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            Slog.w(TAG, "copyToFile fail" , e);
            return false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException e) {
                Slog.w(TAG, "close failed..");
            }
        }
        return true;
    }

    public void stringToFile(String filename, String string) throws IOException {
        FileWriter out = new FileWriter(filename, true);
        try {
            out.write(string);
        } finally {
            out.close();
        }
    }

    public class BinderDumpThread extends Thread {
        private int mPid;

        public BinderDumpThread(int pid) {
            mPid = pid;
        }

        public void run() {
            dumpBinderInfo(mPid);
        }
    }

    public void dumpBinderInfo(int pid) {
        try {
            File binderinfo = new File("/data/anr/binderinfo");
            if (binderinfo.exists()) {
                if (binderinfo.delete() == false) {
                    Slog.e(TAG,
                            "dumpBinderInfo fail due to file likely to be locked by others");
                    return;
                }
                if (binderinfo.createNewFile() == false) {
                    Slog.e(TAG,
                            "dumpBinderInfo fail due to file cannot be created");
                    return;
                }
                FileUtils.setPermissions(binderinfo.getPath(), 0666, -1, -1); // -rw-rw-rw-
            }

            File file = createFile(BINDERINFO_PATH + "/failed_transaction_log");
            if (null != file) {
                stringToFile("/data/anr/binderinfo",
                        "------ BINDER FAILED TRANSACTION LOG ------\n");
                copyFile(file, binderinfo);
            }

            file = createFile(BINDERINFO_PATH + "/timeout_log");
            if (null != file) {
                stringToFile("/data/anr/binderinfo",
                        "------ BINDER TIMEOUT LOG ------\n");
                copyFile(file, binderinfo);
            }

            file = createFile(BINDERINFO_PATH + "/transaction_log");
            if (null != file) {
                stringToFile("/data/anr/binderinfo",
                        "------ BINDER TRANSACTION LOG ------\n");
                copyFile(file, binderinfo);
            }

            file = createFile(BINDERINFO_PATH + "/transactions");
            if (null != file) {
                stringToFile("/data/anr/binderinfo",
                        "------ BINDER TRANSACTIONS ------\n");
                copyFile(file, binderinfo);
            }

            file = createFile(BINDERINFO_PATH + "/stats");
            if (null != file) {
                stringToFile("/data/anr/binderinfo",
                        "------ BINDER STATS ------\n");
                copyFile(file, binderinfo);
            }

            String filepath = BINDERINFO_PATH + "/proc/"
                    + Integer.toString(pid);
            file = new File(filepath);

            if (null != file) {
                stringToFile("/data/anr/binderinfo",
                        "------ BINDER PROCESS STATE: $i ------\n");
                copyFile(file, binderinfo);
            }

        } catch (IOException e) {
            Slog.e(TAG, "dumpBinderInfo fail");
        }
    }

    public void enableTraceLog(boolean enable) {
        Slog.i(TAG, "enableTraceLog: " + enable);
        if (null != exceptionLog) {
            exceptionLog.switchFtrace(enable ? 1 : 0);
        }
    }

    private void writeStringToFile(String filepath, String string) {
        if (filepath == null) {
            return;
        }

        File file = new File(filepath);
        FileOutputStream out = null;

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();

        try {
            out = new FileOutputStream(file);
            out.write(string.getBytes());
            out.flush();
        } catch (IOException e) {
            Slog.e(TAG,
                    "writeStringToFile error: " + filepath + " " + e.toString());
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ioe) {
                    Slog.e(TAG, "writeStringToFile close error: " + filepath
                            + " " + ioe.toString());
                }
            }
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private boolean isBuiltinApp(ApplicationInfo appInfo) {
        return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
            || (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    /**
     * the 3rd party's anr dump info, it can be disabled in user load for performance
     */
    private boolean needAnrDump(ApplicationInfo appInfo) {
        return isBuiltinApp(appInfo) || (SystemProperties.getInt(
                "persist.vendor.anr.dumpthr",ENABLE_ANR_DUMP_FOR_3RD_APP) != DISABLE_ANR_DUMP_FOR_3RD_APP);
    }

}
