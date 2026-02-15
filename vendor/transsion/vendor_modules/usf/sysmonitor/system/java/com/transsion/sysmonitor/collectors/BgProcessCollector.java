package com.transsion.sysmonitor.collectors;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.transsion.sysmonitor.SysMonitorBridge;

import java.util.List;

/**
 * BgProcessCollector — periodic background process count sampling.
 *
 * Polls ActivityManager.getRunningAppProcesses() every INTERVAL_MS.
 * Uses a dedicated HandlerThread to avoid blocking the main thread.
 * Counts only processes with importance >= IMPORTANCE_BACKGROUND.
 */
public class BgProcessCollector {
    private static final String TAG = "SMon-BgProc";
    private static final int INTERVAL_MS = 5000; // 5s — matches VSLOW tier

    private final ActivityManager mAm;
    private HandlerThread mThread;
    private Handler mHandler;
    private boolean mRunning = false;

    private final Runnable mSampleTask = new Runnable() {
        @Override
        public void run() {
            if (!mRunning)
                return;
            sample();
            mHandler.postDelayed(this, INTERVAL_MS);
        }
    };

    public BgProcessCollector(Context context) {
        mAm = (ActivityManager) context.getApplicationContext().getSystemService(
                Context.ACTIVITY_SERVICE);
    }

    public void start() {
        if (mRunning || mAm == null)
            return;
        mThread = new HandlerThread("smon-bgproc");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mRunning = true;
        mHandler.post(mSampleTask);
        Log.i(TAG, "started, interval=" + INTERVAL_MS + "ms");
    }

    public void stop() {
        if (!mRunning)
            return;
        mRunning = false;
        mHandler.removeCallbacks(mSampleTask);
        mThread.quitSafely();
        Log.i(TAG, "stopped");
    }

    private void sample() {
        List<ActivityManager.RunningAppProcessInfo> procs = mAm.getRunningAppProcesses();
        if (procs == null)
            return;
        int bgCount = 0;
        for (ActivityManager.RunningAppProcessInfo info : procs) {
            if (info.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                bgCount++;
            }
        }
        Log.d(TAG, "bgProcessCount=" + bgCount);
        SysMonitorBridge.push(SysMonitorBridge.METRIC_BG_PROCESS_COUNT, bgCount);
    }
}
