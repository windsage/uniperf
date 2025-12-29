package com.transsion.perfhub;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import vendor.transsion.hardware.perfhub.ITranPerfHub;

/**
 * TranPerfHub - Performance Hub Implementation
 *
 * This class provides the bridge between Framework API and Vendor AIDL service.
 */
public class TranPerfHub {
    private static final String TAG = "TranPerfHub";
    private static final boolean DEBUG = false;

    private static final String SERVICE_NAME =
            "vendor.transsion.hardware.perfhub.ITranPerfHub/default";

    private static ITranPerfHub sService;
    private static final Object sLock = new Object();

    /**
     * Acquire performance lock
     *
     * Called by TranPerfEvent via reflection
     */
    public static int acquirePerfLock(
            int eventId, long timestamp, int numParams, int[] intParams, String extraStrings) {
        if (DEBUG) {
            Log.d(TAG, String.format("acquirePerfLock: eventId=%d, numParams=%d, extraStrings=%s",
                               eventId, numParams, extraStrings));
        }

        ITranPerfHub service = getService();
        if (service == null) {
            Log.e(TAG, "TranPerfHub service not available");
            return -1;
        }

        try {
            service.notifyEventStart(eventId, timestamp, numParams, intParams, extraStrings);
            return eventId; // Return eventId as pseudo-handle

        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify event start", e);
            return -1;
        }
    }

    /**
     * Release performance lock
     *
     * Called by TranPerfEvent via reflection
     */
    public static void releasePerfLock(int eventId, long timestamp, String extraStrings) {
        if (DEBUG) {
            Log.d(TAG, String.format("releasePerfLock: eventId=%d", eventId));
        }

        ITranPerfHub service = getService();
        if (service == null) {
            Log.e(TAG, "TranPerfHub service not available");
            return;
        }

        try {
            service.notifyEventEnd(eventId, timestamp, extraStrings);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify event end", e);
        }
    }

    /**
     * Get AIDL service instance
     */
    private static ITranPerfHub getService() {
        synchronized (sLock) {
            if (sService != null) {
                return sService;
            }

            try {
                IBinder binder = ServiceManager.getService(SERVICE_NAME);
                if (binder == null) {
                    Log.e(TAG, "Service not found: " + SERVICE_NAME);
                    return null;
                }

                sService = ITranPerfHub.Stub.asInterface(binder);

                if (DEBUG) {
                    Log.d(TAG, "Service connected: " + SERVICE_NAME);
                }

                return sService;

            } catch (Exception e) {
                Log.e(TAG, "Failed to get service", e);
                return null;
            }
        }
    }
}
