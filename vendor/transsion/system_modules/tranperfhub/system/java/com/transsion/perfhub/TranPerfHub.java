package com.transsion.perfhub;

import android.util.Log;

/**
 * TranPerfHub - Performance Optimization Implementation
 * 
 * Responsibilities:
 * - Call native layer via JNI
 * - Native layer calls vendor layer via AIDL
 * - Vendor layer handles platform adaptation and parameter mapping
 * 
 * Note: This class does not include listener mechanism.
 * Listeners are implemented in TranPerfEvent.
 * 
 * @hide
 */
public class TranPerfHub {
    private static final String TAG = "TranPerfHub";
    private static final boolean DEBUG = false;
    
    // Event type constants (must match TranPerfEvent)
    public static final int EVENT_APP_LAUNCH = 1;
    public static final int EVENT_SCROLL = 2;
    public static final int EVENT_ANIMATION = 3;
    public static final int EVENT_WINDOW_SWITCH = 4;
    public static final int EVENT_TOUCH = 5;
    public static final int EVENT_APP_SWITCH = 6;
    
    // Native library loading status
    private static boolean sNativeLoaded = false;
    
    // Load native library
    static {
        try {
            System.loadLibrary("tranperfhub-system");
            nativeInit();
            sNativeLoaded = true;
            if (DEBUG) {
                Log.d(TAG, "Native library loaded successfully");
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library", e);
        }
    }
    
    // Prevent instantiation
    private TranPerfHub() {}
    
    /**
     * Acquire performance lock
     * 
     * @param eventType Event type
     * @param eventParam Event parameter
     * @return Performance lock handle (>0 success, <=0 failure)
     */
    public static int acquirePerfLock(int eventType, int eventParam) {
        if (!sNativeLoaded) {
            if (DEBUG) {
                Log.w(TAG, "Native library not loaded");
            }
            return -1;
        }
        
        int handle = nativeAcquirePerfLock(eventType, eventParam);
        if (DEBUG) {
            Log.d(TAG, "acquirePerfLock: eventType=" + eventType + 
                ", eventParam=" + eventParam + ", handle=" + handle);
        }
        return handle;
    }
    
    /**
     * Release performance lock
     * 
     * @param handle Performance lock handle
     */
    public static void releasePerfLock(int handle) {
        if (!sNativeLoaded) {
            return;
        }
        
        nativeReleasePerfLock(handle);
        if (DEBUG) {
            Log.d(TAG, "releasePerfLock: handle=" + handle);
        }
    }
    
    // ==================== Native Methods ====================
    
    private static native void nativeInit();
    private static native int nativeAcquirePerfLock(int eventType, int eventParam);
    private static native void nativeReleasePerfLock(int handle);
}