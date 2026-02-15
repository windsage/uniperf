package com.transsion.sysmonitor.collectors;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.FrameMetrics;
import android.view.Window;

import com.transsion.sysmonitor.SysMonitorBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FrameMetricsCollector — frame jank and missed frame monitoring.
 *
 * Uses Window.addOnFrameMetricsAvailableListener() (API 24+) to receive
 * per-frame timing data from the renderer without polling.
 *
 * Metrics published:
 *   METRIC_FRAME_JANKY_RATE  — janky frames / total frames × 100 (integer percent×100)
 *                              e.g. 5.25% janky → 525
 *   METRIC_FRAME_MISSED      — absolute janky frame count in the rolling window
 *
 * A frame is "janky" when total frame duration > vsync interval (16.67ms at 60fps).
 * Threshold: JANK_THRESHOLD_NS = 16_666_667 ns (one 60Hz vsync).
 *
 * Window tracking: monitors all Activity windows via Application.ActivityLifecycleCallbacks.
 * Automatically attaches/detaches listener as Activities resume/pause.
 */
public class FrameMetricsCollector {
    private static final String TAG = "SMon-Frame";
    private static final long JANK_THRESHOLD_NS = 16_666_667L; // 1/60s in ns
    private static final int WINDOW_SIZE = 120; // rolling window frames

    private final Application mApp;
    private Handler mHandler;
    private HandlerThread mThread;
    private boolean mRunning = false;

    // Rolling counters (atomic for cross-thread safety)
    private final AtomicLong mTotalFrames = new AtomicLong(0);
    private final AtomicLong mJankyFrames = new AtomicLong(0);

    // Tracked windows (guarded by mWindows lock)
    private final List<Window> mWindows = new ArrayList<>();

    // FrameMetrics listener — shared across all tracked windows
    private final Window.OnFrameMetricsAvailableListener mFrameListener =
            new Window.OnFrameMetricsAvailableListener() {
                @Override
                public void onFrameMetricsAvailable(Window window, FrameMetrics frameMetrics,
                        int dropCountSinceLastInvocation) {
                    long totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION);
                    long total = mTotalFrames.incrementAndGet();
                    long janky = mJankyFrames.get();
                    if (totalNs > JANK_THRESHOLD_NS) {
                        janky = mJankyFrames.incrementAndGet();
                    }

                    // Publish and reset every WINDOW_SIZE frames
                    if (total % WINDOW_SIZE == 0) {
                        long rate = (janky * 10000L) / total; // percent × 100
                        Log.d(TAG, "jankyRate=" + rate + " (per 10000), missed=" + janky);
                        SysMonitorBridge.push(SysMonitorBridge.METRIC_FRAME_JANKY_RATE, rate);
                        SysMonitorBridge.push(SysMonitorBridge.METRIC_FRAME_MISSED, janky);
                        // Reset rolling window
                        mTotalFrames.set(0);
                        mJankyFrames.set(0);
                    }
                }
            };

    // Activity lifecycle tracking
    private final Application.ActivityLifecycleCallbacks mLifecycle =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityResumed(Activity activity) {
                    attachWindow(activity.getWindow());
                }
                @Override
                public void onActivityPaused(Activity activity) {
                    detachWindow(activity.getWindow());
                }
                @Override
                public void onActivityCreated(Activity a, Bundle b) {}
                @Override
                public void onActivityStarted(Activity a) {}
                @Override
                public void onActivityStopped(Activity a) {}
                @Override
                public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                @Override
                public void onActivityDestroyed(Activity a) {
                    detachWindow(a.getWindow());
                }
            };

    public FrameMetricsCollector(Application application) {
        mApp = application;
    }

    public void start() {
        if (mRunning)
            return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "FrameMetrics requires API 24+, skipping");
            return;
        }
        mThread = new HandlerThread("smon-frame");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mApp.registerActivityLifecycleCallbacks(mLifecycle);
        mRunning = true;
        Log.i(TAG, "started");
    }

    public void stop() {
        if (!mRunning)
            return;
        mRunning = false;
        mApp.unregisterActivityLifecycleCallbacks(mLifecycle);
        synchronized (mWindows) {
            for (Window w : mWindows) {
                w.removeOnFrameMetricsAvailableListener(mFrameListener);
            }
            mWindows.clear();
        }
        mThread.quitSafely();
        Log.i(TAG, "stopped");
    }

    private void attachWindow(Window window) {
        if (window == null || !mRunning)
            return;
        synchronized (mWindows) {
            if (mWindows.contains(window))
                return;
            window.addOnFrameMetricsAvailableListener(mFrameListener, mHandler);
            mWindows.add(window);
            Log.d(TAG, "attached window: " + window);
        }
    }

    private void detachWindow(Window window) {
        if (window == null)
            return;
        synchronized (mWindows) {
            if (mWindows.remove(window)) {
                window.removeOnFrameMetricsAvailableListener(mFrameListener);
                Log.d(TAG, "detached window: " + window);
            }
        }
    }
}
