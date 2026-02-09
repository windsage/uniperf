package com.transsion.perfengine.demo;

import android.os.RemoteException;
import android.util.Log;
import android.util.TranPerfEvent;

import java.util.Arrays;

import vendor.transsion.hardware.perfengine.IEventListener;

/**
 * PerfEngine Listener Demo (Java)
 *
 * This demo demonstrates how to use TranPerfEvent Framework API:
 *   1. Send performance events (notifyEventStart/End)
 *   2. Register listener to receive event callbacks
 *
 * Usage:
 *   adb shell am start -n com.transsion.perfengine.demo/.MainActivity
 *
 * Or run as standalone application:
 *   adb shell /system_ext/bin/app_process /system_ext/bin \
 *       com.transsion.perfengine.demo.PerfEngineListenerDemo
 */
public class PerfEngineListenerDemo {
    private static final String TAG = "PerfEngineListenerDemo";
    private static final boolean DEBUG = true;

    private IEventListener mListener;
    private boolean mRunning = false;

    // ==================== Event Listener Implementation ====================

    /**
     * Full AIDL Event Listener Implementation
     *
     * Receives complete event information including all parameters.
     */
    private class FullEventListener extends IEventListener.Stub {
        private int mEventCount = 0;

        @Override
        public void onEventStart(int eventId, long timestamp, int numParams, int[] intParams,
                String extraStrings) throws RemoteException {
            mEventCount++;

            Log.i(TAG, "========================================");
            Log.i(TAG, "Event Start Received #" + mEventCount);
            Log.i(TAG, "  Event ID: " + eventId + " (" + getEventName(eventId) + ")");
            Log.i(TAG, "  Timestamp: " + timestamp + " ns (" + (timestamp / 1_000_000) + " ms)");
            Log.i(TAG, "  Num Params: " + numParams);

            if (intParams != null && numParams > 0) {
                Log.i(TAG, "  Int Parameters:");
                for (int i = 0; i < Math.min(intParams.length, numParams); i++) {
                    if (i == 0) {
                        Log.i(TAG, "      [" + i + "] duration = " + intParams[i] + " ms");
                    } else {
                        Log.i(TAG, "      [" + i + "] = " + intParams[i]);
                    }
                }
            }

            if (extraStrings != null && !extraStrings.isEmpty()) {
                // Split by '\x1F' to show individual strings
                String[] strings = extraStrings.split("\u001F");
                Log.i(TAG, "  String Parameters: " + Arrays.toString(strings));
            }

            Log.i(TAG, "========================================");
        }

        @Override
        public void onEventEnd(int eventId, long timestamp, String extraStrings)
                throws RemoteException {
            Log.i(TAG, "========================================");
            Log.i(TAG, "Event End Received");
            Log.i(TAG, "  Event ID: " + eventId + " (" + getEventName(eventId) + ")");
            Log.i(TAG, "  Timestamp: " + timestamp + " ns (" + (timestamp / 1_000_000) + " ms)");

            if (extraStrings != null && !extraStrings.isEmpty()) {
                String[] strings = extraStrings.split("\u001F");
                Log.i(TAG, "  String Parameters: " + Arrays.toString(strings));
            }

            Log.i(TAG, "========================================");
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

    // ==================== Demo Control Methods ====================

    /**
     * Start the demo
     *
     * Registers listener and optionally sends test events.
     */
    public void start() {
        if (mRunning) {
            Log.w(TAG, "Demo already running");
            return;
        }

        Log.i(TAG, "");
        Log.i(TAG, "╔════════════════════════════════════════╗");
        Log.i(TAG, "║  PerfEngine Listener Demo (Java)     ║");
        Log.i(TAG, "╚════════════════════════════════════════╝");
        Log.i(TAG, "");

        // Step 1: Register listener
        registerListener();

        // Step 2: Send test events (optional)
        sendTestEvents();

        mRunning = true;

        Log.i(TAG, "");
        Log.i(TAG, "Demo started successfully!");
        Log.i(TAG, "Listening for performance events...");
        Log.i(TAG, "(Press Ctrl+C to stop)");
        Log.i(TAG, "");
    }

    /**
     * Stop the demo
     *
     * Unregisters listener and cleans up.
     */
    public void stop() {
        if (!mRunning) {
            return;
        }

        Log.i(TAG, "");
        Log.i(TAG, "Stopping PerfEngine Listener Demo...");

        // Unregister listener
        unregisterListener();

        mRunning = false;

        Log.i(TAG, "Demo stopped");
        Log.i(TAG, "");
    }

    // ==================== Listener Registration ====================

    /**
     * Register event listener using TranPerfEvent API
     */
    private void registerListener() {
        Log.i(TAG, "Registering event listener...");

        try {
            // Create listener
            mListener = new FullEventListener();

            // Register using TranPerfEvent Framework API
            TranPerfEvent.registerEventListener(mListener);

            Log.i(TAG, "Listener registered successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to register listener", e);
        }
    }

    /**
     * Unregister event listener using TranPerfEvent API
     */
    private void unregisterListener() {
        if (mListener == null) {
            return;
        }

        Log.i(TAG, "Unregistering event listener...");

        try {
            // Unregister using TranPerfEvent Framework API
            TranPerfEvent.unregisterEventListener(mListener);

            Log.i(TAG, "Listener unregistered successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister listener", e);
        }

        mListener = null;
    }

    // ==================== Test Event Sending ====================

    /**
     * Send test events to demonstrate all TranPerfEvent APIs
     */
    private void sendTestEvents() {
        Log.i(TAG, "");
        Log.i(TAG, "Sending test events...");
        Log.i(TAG, "");

        try {
            // Test 1: Simple event (eventId + timestamp)
            testSimpleEvent();
            Thread.sleep(500);

            // Test 2: Event with string
            testEventWithString();
            Thread.sleep(500);

            // Test 3: Event with string array
            testEventWithStringArray();
            Thread.sleep(500);

            // Test 4: Event with int parameters
            testEventWithIntParams();
            Thread.sleep(500);

            // Test 5: Event with full parameters
            testEventWithFullParams();

            Log.i(TAG, "");
            Log.i(TAG, "All test events sent");

        } catch (Exception e) {
            Log.e(TAG, "Failed to send test events", e);
        }
    }

    /**
     * Test 1: notifyEventStart(eventId, timestamp)
     */
    private void testSimpleEvent() {
        Log.i(TAG, "Test 1: Simple event (eventId + timestamp)");

        long ts = TranPerfEvent.now();

        // Send event start
        TranPerfEvent.notifyEventStart(TranPerfEvent.EVENT_APP_LAUNCH, ts);

        Log.i(TAG, "   Sent: EVENT_APP_LAUNCH");

        // Wait and send event end
        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }

        // Send event end
        TranPerfEvent.notifyEventEnd(TranPerfEvent.EVENT_APP_LAUNCH, TranPerfEvent.now());

        Log.i(TAG, "   Sent: EVENT_APP_LAUNCH (end)");
    }

    /**
     * Test 2: notifyEventStart(eventId, timestamp, string)
     */
    private void testEventWithString() {
        Log.i(TAG, "Test 2: Event with string parameter");

        long ts = TranPerfEvent.now();

        // Send event with package name
        TranPerfEvent.notifyEventStart(TranPerfEvent.EVENT_APP_SWITCH, ts, "com.android.settings");

        Log.i(TAG, "   Sent: EVENT_APP_SWITCH with packageName");

        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }

        TranPerfEvent.notifyEventEnd(
                TranPerfEvent.EVENT_APP_SWITCH, TranPerfEvent.now(), "com.android.settings");
    }

    /**
     * Test 3: notifyEventStart(eventId, timestamp, string[])
     */
    private void testEventWithStringArray() {
        Log.i(TAG, "Test 3: Event with string array");

        long ts = TranPerfEvent.now();

        String[] strings = {"com.android.settings", ".MainActivity", "cold_start"};

        // Send event with string array
        TranPerfEvent.notifyEventStart(TranPerfEvent.EVENT_SCROLL, ts, strings);

        Log.i(TAG, "   Sent: EVENT_SCROLL with string array: " + Arrays.toString(strings));

        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }

        TranPerfEvent.notifyEventEnd(TranPerfEvent.EVENT_SCROLL, TranPerfEvent.now());
    }

    /**
     * Test 4: notifyEventStart(eventId, timestamp, numParams, int[])
     */
    private void testEventWithIntParams() {
        Log.i(TAG, "Test 4: Event with int parameters");

        long ts = TranPerfEvent.now();

        int[] intParams = {
                2000, // duration = 2s
                500, // velocity = 500
                1 // direction = down
        };

        // Send event with int parameters
        TranPerfEvent.notifyEventStart(
                TranPerfEvent.EVENT_CAMERA_OPEN, ts, intParams.length, intParams);

        Log.i(TAG, "   Sent: EVENT_CAMERA_OPEN with intParams: " + Arrays.toString(intParams));

        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }

        TranPerfEvent.notifyEventEnd(TranPerfEvent.EVENT_CAMERA_OPEN, TranPerfEvent.now());
    }

    /**
     * Test 5: notifyEventStart(eventId, timestamp, numParams, int[], string[])
     */
    private void testEventWithFullParams() {
        Log.i(TAG, "Test 5: Event with full parameters");

        long ts = TranPerfEvent.now();

        int[] intParams = {3000, 1, 0};
        String[] stringParams = {"com.example.game", ".GameActivity", "level_1"};

        // Send event with full parameters
        TranPerfEvent.notifyEventStart(
                TranPerfEvent.EVENT_GAME_START, ts, intParams.length, intParams, stringParams);

        Log.i(TAG, "   Sent: EVENT_GAME_START with full parameters");
        Log.i(TAG, "      intParams: " + Arrays.toString(intParams));
        Log.i(TAG, "      stringParams: " + Arrays.toString(stringParams));

        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }

        TranPerfEvent.notifyEventEnd(TranPerfEvent.EVENT_GAME_START, TranPerfEvent.now());
    }

    // ==================== Helper Methods ====================

    /**
     * Get event name by ID
     */
    private String getEventName(int eventId) {
        switch (eventId) {
            case TranPerfEvent.EVENT_APP_LAUNCH:
                return "APP_LAUNCH";
            case TranPerfEvent.EVENT_APP_SWITCH:
                return "APP_SWITCH";
            case TranPerfEvent.EVENT_SCROLL:
                return "SCROLL";
            case TranPerfEvent.EVENT_CAMERA_OPEN:
                return "CAMERA_OPEN";
            case TranPerfEvent.EVENT_GAME_START:
                return "GAME_START";
            case TranPerfEvent.EVENT_VIDEO_PLAY:
                return "VIDEO_PLAY";
            case TranPerfEvent.EVENT_ANIMATION:
                return "ANIMATION";
            default:
                return "UNKNOWN(" + eventId + ")";
        }
    }

    // ==================== Main Entry Point ====================

    /**
     * Main entry point for standalone execution
     */
    public static void main(String[] args) {
        PerfEngineListenerDemo demo = new PerfEngineListenerDemo();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { demo.stop(); }));

        // Start demo
        demo.start();

        // Keep running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Log.i(TAG, "Demo interrupted");
            demo.stop();
        }
    }
}
