package com.transsion.sysmonitor.collectors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.transsion.sysmonitor.SysMonitorBridge;

/**
 * WifiSignalCollector — monitors Wi-Fi RSSI via WifiManager broadcast.
 *
 * Push model: registers RSSI_CHANGED_ACTION broadcast; each receive
 * pushes METRIC_WIFI_RSSI (dBm) to SysMonitorBridge.
 * No polling thread — zero idle overhead.
 */
public class WifiSignalCollector {
    private static final String TAG = "SMon-Wifi";

    private final Context mContext;
    private final WifiManager mWifiManager;
    private boolean mRegistered = false;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!WifiManager.RSSI_CHANGED_ACTION.equals(intent.getAction()))
                return;
            int rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, Integer.MIN_VALUE);
            if (rssi == Integer.MIN_VALUE)
                return;
            Log.d(TAG, "RSSI=" + rssi + " dBm");
            SysMonitorBridge.push(SysMonitorBridge.METRIC_WIFI_RSSI, rssi);
        }
    };

    public WifiSignalCollector(Context context) {
        mContext = context.getApplicationContext();
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    /** Start listening. Safe to call multiple times. */
    public void start() {
        if (mRegistered || mWifiManager == null)
            return;
        IntentFilter filter = new IntentFilter(WifiManager.RSSI_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, filter);
        mRegistered = true;
        // Push current RSSI immediately on start
        int rssi = mWifiManager.getConnectionInfo().getRssi();
        if (rssi != Integer.MIN_VALUE) {
            SysMonitorBridge.push(SysMonitorBridge.METRIC_WIFI_RSSI, rssi);
        }
        Log.i(TAG, "started, current RSSI=" + rssi);
    }

    /** Stop listening. */
    public void stop() {
        if (!mRegistered)
            return;
        mContext.unregisterReceiver(mReceiver);
        mRegistered = false;
        Log.i(TAG, "stopped");
    }
}
