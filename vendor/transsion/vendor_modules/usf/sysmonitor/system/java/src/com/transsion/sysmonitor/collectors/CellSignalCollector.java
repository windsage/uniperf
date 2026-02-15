package com.transsion.sysmonitor.collectors;

import android.content.Context;
import android.os.Build;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.transsion.sysmonitor.SysMonitorBridge;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * CellSignalCollector — monitors cellular signal strength via TelephonyManager.
 *
 * API 31+: uses TelephonyCallback (PhoneStateListener deprecated).
 * Push model: callback driven, no polling thread.
 * Publishes METRIC_CELL_SIGNAL as signal level (0-4), universally
 * available across all RATs without per-RAT branching.
 */
public class CellSignalCollector {
    private static final String TAG = "SMon-Cell";

    private final TelephonyManager mTelMgr;
    // Dedicated single-thread executor for telephony callbacks
    // Avoids blocking the main thread; one thread is enough (callbacks are rare)
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private boolean mListening = false;

    // API 31+ callback (replaces deprecated PhoneStateListener)
    private final TelephonyCallback mCallback = new TelephonyCallback.SignalStrengthsListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength ss) {
            if (ss == null) {
                return;
            }
            // getLevel() returns 0-4 across all RATs (API 23+)
            int level = ss.getLevel();
            Log.d(TAG, "signal level=" + level);
            SysMonitorBridge.push(SysMonitorBridge.METRIC_CELL_SIGNAL, level);
        }
    };

    public CellSignalCollector(Context context) {
        mTelMgr = (TelephonyManager) context.getApplicationContext().getSystemService(
                Context.TELEPHONY_SERVICE);
    }

    public void start() {
        if (mListening || mTelMgr == null) {
            return;
        }
        // registerTelephonyCallback available since API 31
        mTelMgr.registerTelephonyCallback(mExecutor, mCallback);
        mListening = true;
        Log.i(TAG, "started");
    }

    public void stop() {
        if (!mListening) {
            return;
        }
        mTelMgr.unregisterTelephonyCallback(mCallback);
        mListening = false;
        Log.i(TAG, "stopped");
    }
}
