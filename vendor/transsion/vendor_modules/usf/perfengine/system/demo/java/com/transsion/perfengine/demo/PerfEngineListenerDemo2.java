package com.transsion.perfengine.demo;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import vendor.transsion.hardware.perfengine.IEventListener;
import vendor.transsion.hardware.perfengine.IPerfEngine;

/**
 * PerfEngine Listener Demo (Java)
 *
 * This demo shows how to register a listener to receive performance events
 * from PerfEngine in a Java process.
 *
 * Usage:
 *   adb shell am start -n com.transsion.perfengine.demo/.PerfEngineListenerDemo
 *
 * Or programmatically:
 *   PerfEngineListenerDemo demo = new PerfEngineListenerDemo();
 *   demo.start();
 */
public class PerfEngineListenerDemo {
    private static final String TAG = "PerfEngineListenerDemo";
    private static final boolean DEBUG = true;

    private IPerfEngine mPerfEngine;
    private EventListenerImpl mListener;

    /**
     * Event Listener Implementation
     */
    private class EventListenerImpl extends IEventListener.Stub {
        private int mEventCount = 0;

        @Override
        public void onEventStart(int eventId, long timestamp, int numParams, int[] intParams,
                String extraStrings) throws RemoteException {
            mEventCount++;

            if (DEBUG) {
                Log.d(TAG, "========================================");
                Log.d(TAG, "Event Start Received #" + mEventCount);
                Log.d(TAG, "  eventId: " + eventId + " (" + getEventName(eventId) + ")");
                Log.d(TAG, "  timestamp: " + timestamp + " ns");
                Log.d(TAG, "  numParams: " + numParams);

                if (intParams != null && intParams.length > 0) {
                    Log.d(TAG, "  intParams:");
                    for (int i = 0; i < Math.min(intParams.length, numParams); i++) {
                        if (i == 0) {
                            Log.d(TAG, "    [" + i + "] duration = " + intParams[i] + " ms");
                        } else {
                            Log.d(TAG, "    [" + i + "] = " + intParams[i]);
                        }
                    }
                }

                if (extraStrings != null && !extraStrings.isEmpty()) {
                    Log.d(TAG, "  extraStrings: " + extraStrings);
                }

                Log.d(TAG, "========================================");
            }
        }

        @Override
        public void onEventEnd(int eventId, long timestamp, String extraStrings)
                throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "========================================");
                Log.d(TAG, "Event End Received #" + mEventCount);
                Log.d(TAG, "  eventId: " + eventId + " (" + getEventName(eventId) + ")");
                Log.d(TAG, "  timestamp: " + timestamp + " ns");

                if (extraStrings != null && !extraStrings.isEmpty()) {
                    Log.d(TAG, "  extraStrings: " + extraStrings);
                }

                Log.d(TAG, "========================================");
            }
        }

        @Override
        public int getInterfaceVersion() {
            return IEventListener.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IEventListener.HASH;
        }
    }

    /**
     * Start the demo
     */
    public void start() {
        Log.i(TAG, "Starting PerfEngine Listener Demo...");

        // Get PerfEngine service
        if (!connectToService()) {
            Log.e(TAG, "Failed to connect to PerfEngine service");
            return;
        }

        // Create and register listener
        mListener = new EventListenerImpl();

        try {
            mPerfEngine.registerEventListener(mListener);
            Log.i(TAG, "Listener registered successfully!");
            Log.i(TAG, "Waiting for events... (Press Ctrl+C to stop)");

        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register listener", e);
        }
    }

    /**
     * Stop the demo
     */
    public void stop() {
        Log.i(TAG, "Stopping PerfEngine Listener Demo...");

        if (mPerfEngine != null && mListener != null) {
            try {
                mPerfEngine.unregisterEventListener(mListener);
                Log.i(TAG, "Listener unregistered");
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to unregister listener", e);
            }
        }

        mListener = null;
        mPerfEngine = null;
    }

    /**
     * Connect to PerfEngine service
     */
    private boolean connectToService() {
        String serviceName = IPerfEngine.DESCRIPTOR + "/default";

        IBinder binder = ServiceManager.getService(serviceName);
        if (binder == null) {
            Log.e(TAG, "Service not found: " + serviceName);
            return false;
        }

        mPerfEngine = IPerfEngine.Stub.asInterface(binder);
        if (mPerfEngine == null) {
            Log.e(TAG, "Failed to get IPerfEngine interface");
            return false;
        }

        Log.i(TAG, "Connected to PerfEngine service");
        return true;
    }

    /**
     * Get event name by ID
     */
    private String getEventName(int eventId) {
        switch (eventId) {
            case 1:
                return "APP_LAUNCH";
            case 2:
                return "APP_SWITCH";
            case 3:
                return "SCROLL";
            case 4:
                return "CAMERA_OPEN";
            case 5:
                return "GAME_START";
            case 6:
                return "VIDEO_PLAY";
            case 7:
                return "ANIMATION";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Main entry point for command line testing
     */
    public static void main(String[] args) {
        Log.i(TAG, "PerfEngine Java Listener Demo");
        Log.i(TAG, "================================");

        PerfEngineListenerDemo demo = new PerfEngineListenerDemo();
        demo.start();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.i(TAG, "Shutting down...");
            demo.stop();
        }));

        // Keep running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Log.i(TAG, "Interrupted");
            demo.stop();
        }
    }
}
