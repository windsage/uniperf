package com.transsion.perfhub;

/**
 * TranPerfEvent - SDK API Definition
 * 
 * This is a stub API for third-party applications.
 * The actual implementation is provided by the system.
 * 
 * Usage:
 *   Add dependency in build.gradle:
 *     compileOnly 'com.transsion.perfhub:perfhub-sdk:1.0.0'
 *   
 *   In your code:
 *     long ts = TranPerfEvent.now();
 *     TranPerfEvent.notifyEventStart(TranPerfEvent.EVENT_APP_LAUNCH, ts, packageName);
 */
public final class TranPerfEvent {
    
    // ==================== Event Type Constants ====================
    
    /** App launch event */
    public static final int EVENT_APP_LAUNCH = 1;
    
    /** Scroll event */
    public static final int EVENT_SCROLL = 2;
    
    /** Animation event */
    public static final int EVENT_ANIMATION = 3;
    
    /** Window switch event */
    public static final int EVENT_WINDOW_SWITCH = 4;
    
    /** Touch event */
    public static final int EVENT_TOUCH = 5;
    
    /** App switch event */
    public static final int EVENT_APP_SWITCH = 6;
    
    // ==================== Event Parameter Constants ====================
    
    /** Cold start for app launch */
    public static final int PARAM_COLD_START = 0;
    
    /** Warm start for app launch */
    public static final int PARAM_WARM_START = 1;
    
    // Prevent instantiation
    private TranPerfEvent() {}
    
    // ==================== API Methods (Stub) ====================
    
    /**
     * Notify event start with timestamp only
     */
    public static void notifyEventStart(int eventType, long timestamp) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    /**
     * Notify event start with timestamp + int parameter
     */
    public static void notifyEventStart(int eventType, long timestamp, int param) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    /**
     * Notify event start with timestamp + int array
     */
    public static void notifyEventStart(int eventType, long timestamp, int[] intParams) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    /**
     * Notify event start with timestamp + string parameter
     */
    public static void notifyEventStart(int eventType, long timestamp, String stringParam) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    /**
     * Notify event start with timestamp + string array
     */
    public static void notifyEventStart(int eventType, long timestamp, String[] stringParams) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    /**
     * Notify event start with timestamp + int + string
     */
    public static void notifyEventStart(
            int eventType, 
            long timestamp, 
            int intParam, 
            String stringParam) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    /**
     * Notify event start with timestamp + int array + string
     */
    public static void notifyEventStart(
            int eventType, 
            long timestamp, 
            int[] intParams, 
            String stringParam) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    /**
     * Notify event start with timestamp + int array + string array
     */
    public static void notifyEventStart(
            int eventType, 
            long timestamp, 
            int[] intParams, 
            String[] stringParams) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    // ==================== Event End Methods ====================
    
    /**
     * Notify event end with timestamp
     */
    public static void notifyEventEnd(int eventType, long timestamp) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    /**
     * Notify event end with timestamp and string parameter
     */
    public static void notifyEventEnd(int eventType, long timestamp, String extraStrings) {
        throw new RuntimeException("Stub! System implementation required.");
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Get current timestamp in nanoseconds
     */
    public static long now() {
        throw new RuntimeException("Stub! System implementation required.");
    }
}
