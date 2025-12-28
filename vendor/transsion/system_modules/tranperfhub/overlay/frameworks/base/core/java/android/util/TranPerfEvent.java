package android.util;

import android.util.Log;
import android.util.SparseIntArray;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TranPerfEvent - Performance Event Management
 * 
 * Features:
 * 1. Send performance events (via reflection to TranPerfHub for optimization)
 * 2. Event listening (in-process observer pattern)
 * 
 * Usage:
 * <pre>
 * // Send event
 * TranPerfEvent.notifyEventStart(TranPerfEvent.EVENT_APP_LAUNCH, 0);
 * TranPerfEvent.notifyEventEnd(TranPerfEvent.EVENT_APP_LAUNCH);
 * 
 * // Listen to events (framework/system only)
 * TranPerfEvent.registerListener(new TranPerfEvent.TrEventListener() {
 *     public void onEventStart(int eventType, int eventParam) {
 *         // Handle event start
 *     }
 *     public void onEventEnd(int eventType) {
 *         // Handle event end
 *     }
 * });
 * </pre>
 * 
 * @hide
 */
public final class TranPerfEvent {
    private static final String TAG = "TranPerfEvent";
    private static final boolean DEBUG = false;
    
    // TranPerfHub class name for reflection
    private static final String PERF_HUB_CLASS = "com.transsion.perfhub.TranPerfHub";
    
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
    
    // ==================== Event Listener Interface ====================
    
    /**
     * Listener interface for performance events.
     */
    public interface TrEventListener {
        /**
         * Called when a performance event starts.
         * 
         * @param eventType Event type (EVENT_*)
         * @param eventParam Event-specific parameter
         */
        void onEventStart(int eventType, int eventParam);
        
        /**
         * Called when a performance event ends.
         * 
         * @param eventType Event type (EVENT_*)
         */
        void onEventEnd(int eventType);
    }
    
    // ==================== Internal State ====================
    
    // Event listeners (thread-safe)
    private static final CopyOnWriteArrayList<TrEventListener> sListeners = 
        new CopyOnWriteArrayList<>();
    
    // Event handle mapping (eventType -> handle)
    private static final SparseIntArray sEventHandles = new SparseIntArray();
    
    // Reflection cache
    private static Class<?> sPerfHubClass;
    private static Method sAcquireMethod;
    private static Method sReleaseMethod;
    private static boolean sReflectionInitialized = false;
    
    // Prevent instantiation
    private TranPerfEvent() {}
    
    // ==================== Public API ====================
    
    /**
     * Notify that a performance event has started.
     * 
     * @param eventType Event type (use EVENT_* constants)
     * @param eventParam Event-specific parameter
     */
    public static void notifyEventStart(int eventType, int eventParam) {
        if (DEBUG) {
            Log.d(TAG, "notifyEventStart: eventType=" + eventType + 
                ", eventParam=" + eventParam);
        }
        
        // 1. Call underlying performance optimization
        int handle = acquirePerfLock(eventType, eventParam);
        if (handle > 0) {
            synchronized (sEventHandles) {
                // If this event already has a handle, release the old one first
                int oldHandle = sEventHandles.get(eventType, -1);
                if (oldHandle > 0) {
                    releasePerfLock(oldHandle);
                }
                sEventHandles.put(eventType, handle);
            }
        }
        
        // 2. Notify all listeners
        for (TrEventListener listener : sListeners) {
            try {
                listener.onEventStart(eventType, eventParam);
            } catch (Exception e) {
                Log.e(TAG, "Listener callback failed", e);
            }
        }
    }
    
    /**
     * Notify that a performance event has ended.
     * 
     * @param eventType Event type
     */
    public static void notifyEventEnd(int eventType) {
        if (DEBUG) {
            Log.d(TAG, "notifyEventEnd: eventType=" + eventType);
        }
        
        // 1. Release performance lock
        synchronized (sEventHandles) {
            int handle = sEventHandles.get(eventType, -1);
            if (handle > 0) {
                releasePerfLock(handle);
                sEventHandles.delete(eventType);
            }
        }
        
        // 2. Notify all listeners
        for (TrEventListener listener : sListeners) {
            try {
                listener.onEventEnd(eventType);
            } catch (Exception e) {
                Log.e(TAG, "Listener callback failed", e);
            }
        }
    }
    
    /**
     * Register an event listener.
     * 
     * Note: Listener callbacks are executed on the thread that calls 
     * notifyEventStart/End.
     * 
     * @param listener Event listener
     */
    public static void registerListener(TrEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        
        if (!sListeners.contains(listener)) {
            sListeners.add(listener);
            if (DEBUG) {
                Log.d(TAG, "registerListener: " + listener + 
                    ", total=" + sListeners.size());
            }
        }
    }
    
    /**
     * Unregister an event listener.
     * 
     * @param listener Event listener to remove
     */
    public static void unregisterListener(TrEventListener listener) {
        if (listener != null) {
            sListeners.remove(listener);
            if (DEBUG) {
                Log.d(TAG, "unregisterListener: " + listener + 
                    ", remaining=" + sListeners.size());
            }
        }
    }
    
    // ==================== Reflection Implementation ====================
    
    /**
     * Initialize reflection (lazy, thread-safe)
     */
    private static void ensureReflectionInitialized() {
        if (sReflectionInitialized) return;
        
        synchronized (TranPerfEvent.class) {
            if (sReflectionInitialized) return;
            
            try {
                // Load TranPerfHub class
                sPerfHubClass = Class.forName(PERF_HUB_CLASS);
                
                // Get acquirePerfLock method
                sAcquireMethod = sPerfHubClass.getMethod(
                    "acquirePerfLock", int.class, int.class);
                
                // Get releasePerfLock method
                sReleaseMethod = sPerfHubClass.getMethod(
                    "releasePerfLock", int.class);
                
                sReflectionInitialized = true;
                if (DEBUG) {
                    Log.d(TAG, "TranPerfHub reflection initialized successfully");
                }
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "TranPerfHub not found, performance optimization disabled");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize TranPerfHub reflection", e);
            }
        }
    }
    
    /**
     * Acquire performance lock
     */
    private static int acquirePerfLock(int eventType, int eventParam) {
        ensureReflectionInitialized();
        
        if (sAcquireMethod == null) {
            return -1;
        }
        
        try {
            Object result = sAcquireMethod.invoke(null, eventType, eventParam);
            return (result instanceof Integer) ? (Integer) result : -1;
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire perf lock: eventType=" + eventType, e);
            return -1;
        }
    }
    
    /**
     * Release performance lock
     */
    private static void releasePerfLock(int handle) {
        if (sReleaseMethod == null) {
            return;
        }
        
        try {
            sReleaseMethod.invoke(null, handle);
        } catch (Exception e) {
            Log.e(TAG, "Failed to release perf lock: handle=" + handle, e);
        }
    }
}