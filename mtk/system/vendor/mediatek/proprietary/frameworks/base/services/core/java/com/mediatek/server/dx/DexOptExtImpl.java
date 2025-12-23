package com.mediatek.server.dx;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.content.pm.ApplicationInfo;
import android.content.pm.dex.ArtManagerInternal;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PackageManagerInternal;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Slog;
import com.android.server.LocalServices;
import com.android.server.pm.dex.DexoptOptions;
import com.android.server.pm.PackageDexOptimizer;
import com.android.server.pm.PackageManagerService.IPackageManagerImpl;
import com.android.server.pm.pkg.AndroidPackage;

import com.mediatek.dx.DexOptExt;
import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import static com.android.server.pm.InstructionSets.getAppDexInstructionSets;
import static com.android.server.pm.InstructionSets.getDexCodeInstructionSets;

public class DexOptExtImpl extends DexOptExt {
    private static final String TAG = "DexOptExtImpl";
    private static final int MSG_BASE = 10000;
    private static final int MSG_ON_PROCESS_START = MSG_BASE + 1;
    private static final int MSG_DO_DEXOPT = MSG_BASE + 2;
    private static final int TRY_DEX2OAT_INTERVAL_MS = 180*1000;
    private static final int MAX_TRY_COUNTS = 4;
    private static final int DEX_OPT_INTERVAL_MS = 50;
    private static final int REASON_AGRESSIVE = 1000;
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 1000; // 1 second delay
    private static final String PROPERTY_TRY_INTERVAL = "pm.dexopt.aggressive_dex2oat.interval";
    //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 start
    //private static final String PROPERTY_FEATURE_ENABLE = "pm.dexopt.aggressive_dex2oat.enable";
    private static final String PROPERTY_FEATURE_ENABLE = "persist.sys.dexopt.aggressive_dex2oat.enable";
    //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 end
    private static final String PROPERTY_AGGRESSIVE_DEX2OAT_CPU_SET = SystemProperties
                                .get("ro.vendor.dex2oat-aggressive-cpu-set");
    private static final String PROPERTY_AGGRESSIVE_DEX2OAT_THREADS = SystemProperties
                                .get("ro.vendor.dex2oat-aggressive-threads");
    private static final String DEX2OAT_CPU_SET = SystemProperties.get("dalvik.vm.dex2oat-cpu-set");
    private static final String DEX2OAT_THREADS = SystemProperties.get("dalvik.vm.dex2oat-threads");
    private static final String COMPILERFILTER_SPEED_PROFILE = "speed-profile";
    private static final String BLOCK_CHECK_POINT = "performDexOpt";
    private static final String THREAD_NAME_SPEEDUP = "DexoptExtSpeedup";
    //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 start
    //private static boolean sIsEnable = SystemProperties.getBoolean(PROPERTY_FEATURE_ENABLE, true)
    //                                   && !Build.IS_ENG;
    private static boolean sTranDexoptEnable = SystemProperties.getBoolean("ro.transsion.feature.app_start_dexopt.support", true);
    //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 end
    private static final int CMDLINE_OUT[] = {Process.PROC_OUT_STRING};
    private static DexOptExtImpl sInstance = null;
    private HandlerThread mHandlerThread;
    private Thread mRcvNotifyThread = null;
    private Thread mCurDex2oatThread = null;
    private Handler mDexoptExtHandler;
    private static Object lock = new Object();
    private IPackageManagerImpl mPm =  null;
    private int mTryDex2oatInterval = TRY_DEX2OAT_INTERVAL_MS;
    private long mLastDex2oatTime = 0;
    private long mLastKillDex2oatTime = 0;
    private HashSet<String> mMointorPkgs = new HashSet<>();
    private Object mMointorPkgsLock = new Object();
    private static final String PIDS_OF_INTREST[] = new String[] {
                        "/system/bin/installd",
                        "/apex/com.android.art/bin/dex2oat32",
                        "/apex/com.android.art/bin/dex2oat64",
                        };


    private DexOptExtImpl() {
        setTryDex2oatInterval(SystemProperties.getInt(PROPERTY_TRY_INTERVAL,
                                                      TRY_DEX2OAT_INTERVAL_MS));
        if (isDexOptExtEnable())
            initHandlerAndStartHandlerThread();
    }

    //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 start
    // private void initHandlerAndStartHandlerThread() {
    //     mHandlerThread = new HandlerThread("DexOptExt");
    //     mHandlerThread.start();
    //     mDexoptExtHandler = new Handler(mHandlerThread.getLooper(), new DexOptExtHandler());
    // }
    private synchronized void initHandlerAndStartHandlerThread() {
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("DexOptExt");
            mHandlerThread.start();
            mDexoptExtHandler = new Handler(mHandlerThread.getLooper(), new DexOptExtHandler());
        }
    }
    //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 end

    class DexOptExtHandler implements Handler.Callback{

        @Override
        public boolean handleMessage(Message msg) {
            String pkg = (String)msg.obj;
            switch(msg.what) {
                case MSG_ON_PROCESS_START:
                    handleProcessStart(pkg);
                    break;
                case MSG_DO_DEXOPT:
                    handleDoDexopt(msg);
                    break;
            }
            return true;
        }
    }

    public void setTryDex2oatInterval(int durationMillionSeconds) {
        if (durationMillionSeconds < 0 )
            return;
        this.mTryDex2oatInterval = durationMillionSeconds;
    }

    public static DexOptExtImpl getInstance() {
        synchronized (lock) {
            if (sInstance == null)
               sInstance = new DexOptExtImpl();
        }
        return sInstance;
    }

    /**
     *This function was called when a process was start
     *hostingType:the reason of start the process
     *pkg:package of this process
     */
    @Override
    public void onStartProcess(String hostingType, String pkg) {

        if (!shouldSendProcessStartMessage(hostingType, pkg))
            return;

        synchronized(this) {
            if (isInMonitorList(pkg))
                return;
       }

       Message msg = Message.obtain();
       msg.what = MSG_ON_PROCESS_START;
       msg.obj = pkg;
       mDexoptExtHandler.sendMessage(msg);
     }

    private boolean shouldSendProcessStartMessage(String hostingType, String pkg) {
         if (!isDexOptExtEnable())
            return false;
        //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 start
        initHandlerAndStartHandlerThread();
        //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 end

        //only focus on activity start
        if (hostingType == null || !hostingType.contains("activity"))
            return false;

        //if packagemanager not ready,then return
        if (getPackageManager() == null)
           return false;

        //only happend after boot completed
        String bootcomplete = SystemProperties.get("dev.bootcomplete");
        if (!bootcomplete.equals("1"))
            return false;

       return true;
    }

    private IPackageManagerImpl getPackageManager() {
        if (mPm != null)
            return mPm;

        mPm = (IPackageManagerImpl)ServiceManager.getService("package");
        return mPm;
    }

    private void checkAndWait() {
        long now = SystemClock.uptimeMillis();
	long duration = DEX_OPT_INTERVAL_MS;

        // if dex2oat was killed,that's meaning that the system is
        // busy now.
	if (mLastKillDex2oatTime != 0) {
                // 600s is the duration of watchdog for PMS, we at least
                // after one period of watchdog for PMS.
                duration = 610000 - (now - mLastKillDex2oatTime);
                Slog.d(TAG,"last killed at:" + mLastKillDex2oatTime + " now:" + now + " duration:" + duration);
                mLastKillDex2oatTime = 0;
        }
        else
                duration = DEX_OPT_INTERVAL_MS - (now - mLastDex2oatTime);

	if (duration > 0)
                SystemClock.sleep(duration);
    }

    private void handleDoDexopt(Message msg) {
        String targetCompilerFilter = COMPILERFILTER_SPEED_PROFILE;
        String pkg = (String)msg.obj;
        int result = -1;
        int flags = DexoptOptions.DEXOPT_CHECK_FOR_PROFILES_UPDATES |
                    DexoptOptions.DEXOPT_BOOT_COMPLETE;

        // avoid hold PMS lock frequently
        checkAndWait();
        if (null != PROPERTY_AGGRESSIVE_DEX2OAT_CPU_SET
            && PROPERTY_AGGRESSIVE_DEX2OAT_CPU_SET.length() != 0){
            setSystemPropertyWithRetries(
              "dalvik.vm.dex2oat-cpu-set",PROPERTY_AGGRESSIVE_DEX2OAT_CPU_SET);
        }
        if (null != PROPERTY_AGGRESSIVE_DEX2OAT_THREADS
            && PROPERTY_AGGRESSIVE_DEX2OAT_THREADS.length() != 0){
            setSystemPropertyWithRetries(
              "dalvik.vm.dex2oat-threads",PROPERTY_AGGRESSIVE_DEX2OAT_THREADS);
        }
        result = mPm.performDexOptWithStatusByOption(new DexoptOptions(pkg,
                            12, targetCompilerFilter, null, flags));
        if (null != PROPERTY_AGGRESSIVE_DEX2OAT_CPU_SET
            && PROPERTY_AGGRESSIVE_DEX2OAT_CPU_SET.length() != 0){
            setSystemPropertyWithRetries("dalvik.vm.dex2oat-cpu-set", DEX2OAT_CPU_SET);
        }
        if (null != PROPERTY_AGGRESSIVE_DEX2OAT_THREADS
            && PROPERTY_AGGRESSIVE_DEX2OAT_THREADS.length() != 0){
            setSystemPropertyWithRetries("dalvik.vm.dex2oat-threads", DEX2OAT_THREADS);
        }
        if (mCurDex2oatThread == mHandlerThread)
                mCurDex2oatThread = null;

        mLastDex2oatTime = SystemClock.uptimeMillis();
        Slog.d(TAG,"try dex2oat for "  + pkg + " result=" + result + " cnt = " + msg.arg1);
        //do not care DEX_OPT_FAILED,because we can do nothing if failed
        if (result == PackageDexOptimizer.DEX_OPT_SKIPPED && msg.arg1 < MAX_TRY_COUNTS) {
            Message againMsg = Message.obtain(msg);
            againMsg.arg1 ++;
            //only first time wait full time,otherwise wait half of interval
            mDexoptExtHandler.sendMessageDelayed(againMsg, mTryDex2oatInterval/2);
        }
        else {
            removeMonitorPkg(pkg);
        }
    }

    public static void setSystemPropertyWithRetries(String key, String value) {
        int retries = 0;
        boolean isSetSuccessfully = false;
        while (retries < MAX_RETRIES && !isSetSuccessfully) {
            try {
                SystemProperties.set(key, value);
                isSetSuccessfully = true; // If no exception, set was successful
            } catch (RuntimeException e) {
                retries++;
                if (retries < MAX_RETRIES) {
                    // Log retry attempt
                    Slog.w(TAG, "Failed to set system property. Retrying... ["
                           + retries + "/" + MAX_RETRIES + "]");
                    try {
                        Thread.sleep(RETRY_DELAY_MS); // Wait before retrying
                    } catch (InterruptedException ie) {
                        Slog.e(TAG, "Retry delay interrupted", ie);
                    }
                } else {
                    // Log final failure
                    Slog.e(TAG, "Failed to set system property after "
                           + MAX_RETRIES + " attempts.", e);
                }
            }
        }
    }

   private void handleProcessStart(String pkg) {
        //"install" means that the app have not been dexopt after installation
        if (!isDexoptReasonInstall(pkg))
            return;

        addPkgToMonitor(pkg);
        Message msg = Message.obtain();
        msg.what = MSG_DO_DEXOPT;
        msg.obj = pkg;
        msg.arg1 = 0;
        mDexoptExtHandler.sendMessageDelayed(msg, mTryDex2oatInterval);
   }

   private boolean isDexoptReasonInstall(String pkg) {
        if (mPm == null)
            return false;
        ApplicationInfo appInfo = mPm.getApplicationInfo(pkg, 0, UserHandle.USER_SYSTEM);
        if (appInfo == null)
            return false;
        String abi = appInfo.primaryCpuAbi;
        if (abi == null) {
            abi = Build.SUPPORTED_ABIS[0];
        }
        ArtManagerInternal artManager = LocalServices.getService(ArtManagerInternal.class);
        int reason = artManager.getPackageOptimizationInfo(appInfo, abi, "fakeactivity").getCompilationReason();
        Slog.d(TAG,pkg + " reason is " + reason + " abi is " + abi);
        // in ArtManagerService.java: 4 is install,9 is install-dm
        switch (reason) {
            case 4:
            case 9:
                return true;
            default:
                break;
        }
        return false;
   }

   private String getFirstCodeIsa(ApplicationInfo info) {
        String isa = null;
        final String[] instructionSets = getAppDexInstructionSets(info.primaryCpuAbi,
                                                                info.secondaryCpuAbi);
        final String[] dexCodeInstructionSets = getDexCodeInstructionSets(instructionSets);
        if (dexCodeInstructionSets.length > 0) {
            isa = dexCodeInstructionSets[0];
        }

        return isa;
   }

    private void addPkgToMonitor(String pkg) {
        synchronized(mMointorPkgsLock) {
            mMointorPkgs.add(pkg);
        }
    }

    private boolean isInMonitorList(String pkg) {
        boolean result = false;
        synchronized(mMointorPkgsLock) {
            result = mMointorPkgs.contains(pkg);
        }
        return result;
    }

    private void removeMonitorPkg(String pkg) {
        synchronized(mMointorPkgsLock) {
            mMointorPkgs.remove(pkg);
        }
    }

    //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 start
    // public boolean isDexOptExtEnable() {
    //     return sIsEnable;
    // }
    public boolean isDexOptExtEnable() {
        return SystemProperties.getBoolean(PROPERTY_FEATURE_ENABLE, !sTranDexoptEnable);
    }
    //T-HUB Core[SPD]:added for dexopt by yufeng.liu 2024.12.01 end

    private boolean isBlockedInDexopt() {

       if (mCurDex2oatThread != null &&
                mCurDex2oatThread == mHandlerThread)
                return true;

        return false;
    }

    private static String readCmdlineFromProcfs(int pid) {
        String [] cmdline = new String[1];

        if (!Process.readProcFile("/proc/" + pid + "/cmdline", CMDLINE_OUT,
                                 cmdline, null, null)) {
                return "";
        }
        return cmdline[0];
    }

    private void killIfIsDex2oat(int pid, String cmdline, int installPid) {
        int ppid = -1;

        if (pid == installPid)
                return;

        ppid = Process.getParentPid(pid);

        if (ppid != installPid)
                return;

        Slog.d(TAG,"kill dex2oat,pid " + pid + " cmdline is " + cmdline);
        mLastKillDex2oatTime = SystemClock.uptimeMillis();
        Process.killProcess(pid);
    }

    private void tryToStopDex2oat() {

        if (mRcvNotifyThread != null && mRcvNotifyThread.getState() !=
                                Thread.State.TERMINATED)
                return;

        mRcvNotifyThread = new Thread(
                new Runnable() {

                        @Override
                        public void run() {

                                // check we are watting lock too, If yes, do nothing.
                                if (!isBlockedInDexopt())
                                        return;

                                int pids[] = Process.getPidsForCommands(PIDS_OF_INTREST);
                                String cmdlines [] = new String[pids.length];
                                int install_idx = -1;

                                for ( int i = 0; i < pids.length; i++) {
                                        cmdlines[i] = readCmdlineFromProcfs(pids[i]);
                                        if (cmdlines[i].equals(PIDS_OF_INTREST[0]))
                                                install_idx = i;
                                }
                                if (install_idx == -1)
                                        return;

                                for (int i = 0 ; i < pids.length; i++) {
                                        if ( i == install_idx)
                                                continue;
                                        killIfIsDex2oat(pids[i], cmdlines[i], pids[install_idx]);
                                }
                        }
                }
        );

        mRcvNotifyThread.setPriority(Thread.MAX_PRIORITY);
        mRcvNotifyThread.setName(THREAD_NAME_SPEEDUP);
        mRcvNotifyThread.start();
    }
    /*
     * press dex2oat to speed up
     */
    @Override
    public void notifySpeedUp(){

        if (!isBlockedInDexopt()) {
                Slog.d(TAG,"receive speed up notify,do nothing!");
                return;
        }
        Slog.d(TAG,"we are doing dex2oat, stop it now");
        tryToStopDex2oat();
    }

    @Override
    public void notifyBeginDexopt(String pkg) {

        mCurDex2oatThread = Thread.currentThread();

    }
}
