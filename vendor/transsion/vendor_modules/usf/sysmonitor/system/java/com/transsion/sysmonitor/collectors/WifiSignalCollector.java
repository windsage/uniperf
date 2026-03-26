package com.transsion.sysmonitor.collectors;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
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
    private static final int POLL_INTERVAL_MS = 2000;

    private final WifiManager mWifiMgr;
    private final ConnectivityManager mConnectivityMgr;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning = false;

    private final Runnable mPollTask = new Runnable() {
        @Override
        public void run() {
            if (!mRunning)
                return;
            sample();
            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public WifiSignalCollector(Context context) {
        Context appContext = context.getApplicationContext();
        mWifiMgr = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        mConnectivityMgr =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void start() {
        if (mRunning)
            return;
        mRunning = true;
        mHandler.post(mPollTask);
        Log.i(TAG, "started");
    }

    public void stop() {
        if (!mRunning)
            return;
        mRunning = false;
        mHandler.removeCallbacks(mPollTask);
        Log.i(TAG, "stopped");
    }

    private void sample() {
        if (mWifiMgr == null || !mWifiMgr.isWifiEnabled()) {
            return;
        }

        Network network = mConnectivityMgr.getActiveNetwork();
        if (network == null)
            return;

        NetworkCapabilities nc = mConnectivityMgr.getNetworkCapabilities(network);
        if (nc == null || !nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return;
        }

        WifiInfo info = (WifiInfo) nc.getTransportInfo();
        if (info != null) {
            int rssi = info.getRssi();
            Log.d(TAG, "RSSI=" + rssi);
            SysMonitorBridge.push(SysMonitorBridge.METRIC_WIFI_RSSI, rssi);
        }
    }
}
