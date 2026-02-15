package com.transsion.sysmonitor.collectors;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.transsion.sysmonitor.SysMonitorBridge;

/**
 * CellSignalCollector — monitors cellular signal strength via TelephonyManager.
 *
 * Push model: PhoneStateListener callback; no polling thread.
 * Publishes METRIC_CELL_SIGNAL as dBm for LTE/NR, ASU for legacy.
 *
 * Uses SignalStrength.getLevel() (0-4) as the published value —
 * universally available across all RATs without per-RAT branching.
 */
public class CellSignalCollector {
    private static final String TAG = "SMon-Cell";

    private final TelephonyManager mTelMgr;
    private boolean mListening = false;

    @SuppressWarnings("deprecation")
    private final PhoneStateListener mListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength ss) {
            if (ss == null)
                return;
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

    @SuppressWarnings("deprecation")
    public void start() {
        if (mListening || mTelMgr == null)
            return;
        mTelMgr.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        mListening = true;
        Log.i(TAG, "started");
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        if (!mListening)
            return;
        mTelMgr.listen(mListener, PhoneStateListener.LISTEN_NONE);
        mListening = false;
        Log.i(TAG, "stopped");
    }
}
