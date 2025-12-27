package android.util;

import android.util.Log;
import android.util.SparseIntArray;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TranSceneHint - 统一场景性能管理
 * 
 * 功能:
 * 1. 发送场景事件 (通过反射调用 TranPerfHub 进行性能优化)
 * 2. 场景监听 (进程内观察者模式)
 * 
 * 使用示例:
 * <pre>
 * // 发送场景
 * TranSceneHint.notifySceneStart(TranSceneHint.SCENE_APP_LAUNCH, 0);
 * TranSceneHint.notifySceneEnd(TranSceneHint.SCENE_APP_LAUNCH);
 * 
 * // 监听场景
 * TranSceneHint.registerListener(new TranSceneHint.SceneListener() {
 *     public void onSceneStart(int sceneId, int param) {
 *         // 处理场景开始
 *     }
 *     public void onSceneEnd(int sceneId) {
 *         // 处理场景结束
 *     }
 * });
 * </pre>
 * 
 * @hide
 */
public final class TranSceneHint {
    private static final String TAG = "TranSceneHint";
    private static final boolean DEBUG = false;
    
    // TranPerfHub 类名
    private static final String PERF_HUB_CLASS = "com.transsion.perfhub.TranPerfHub";
    
    // ==================== 场景常量 ====================
    
    /** 应用启动场景 */
    public static final int SCENE_APP_LAUNCH = 1;
    
    /** 滑动场景 */
    public static final int SCENE_SCROLL = 2;
    
    /** 动画场景 */
    public static final int SCENE_ANIMATION = 3;
    
    /** 窗口切换场景 */
    public static final int SCENE_WINDOW_SWITCH = 4;
    
    /** 触摸场景 */
    public static final int SCENE_TOUCH = 5;
    
    /** 应用切换场景 */
    public static final int SCENE_APP_SWITCH = 6;
    
    // ==================== 监听器接口 ====================
    
    /**
     * 场景事件监听器接口
     */
    public interface SceneListener {
        /**
         * 场景开始回调
         * 
         * @param sceneId 场景ID
         * @param param 场景参数
         */
        void onSceneStart(int sceneId, int param);
        
        /**
         * 场景结束回调
         * 
         * @param sceneId 场景ID
         */
        void onSceneEnd(int sceneId);
    }
    
    // ==================== 内部状态 ====================
    
    // 监听器列表 (线程安全)
    private static final CopyOnWriteArrayList<SceneListener> sListeners = 
        new CopyOnWriteArrayList<>();
    
    // 场景句柄映射 (sceneId -> handle)
    private static final SparseIntArray sSceneHandles = new SparseIntArray();
    
    // 反射缓存
    private static Class<?> sPerfHubClass;
    private static Method sAcquireMethod;
    private static Method sReleaseMethod;
    private static boolean sReflectionInitialized = false;
    
    // 禁止实例化
    private TranSceneHint() {}
    
    // ==================== 公开 API ====================
    
    /**
     * 通知场景开始
     * 
     * @param sceneId 场景ID (使用 SCENE_* 常量)
     * @param param 场景参数
     */
    public static void notifySceneStart(int sceneId, int param) {
        if (DEBUG) {
            Log.d(TAG, "notifySceneStart: sceneId=" + sceneId + ", param=" + param);
        }
        
        // 1. 调用底层性能优化
        int handle = acquirePerfLock(sceneId, param);
        if (handle > 0) {
            synchronized (sSceneHandles) {
                // 如果该场景已有句柄，先释放旧的
                int oldHandle = sSceneHandles.get(sceneId, -1);
                if (oldHandle > 0) {
                    releasePerfLock(oldHandle);
                }
                sSceneHandles.put(sceneId, handle);
            }
        }
        
        // 2. 通知所有监听器
        for (SceneListener listener : sListeners) {
            try {
                listener.onSceneStart(sceneId, param);
            } catch (Exception e) {
                Log.e(TAG, "Listener callback failed", e);
            }
        }
    }
    
    /**
     * 通知场景结束
     * 
     * @param sceneId 场景ID
     */
    public static void notifySceneEnd(int sceneId) {
        if (DEBUG) {
            Log.d(TAG, "notifySceneEnd: sceneId=" + sceneId);
        }
        
        // 1. 释放性能锁
        synchronized (sSceneHandles) {
            int handle = sSceneHandles.get(sceneId, -1);
            if (handle > 0) {
                releasePerfLock(handle);
                sSceneHandles.delete(sceneId);
            }
        }
        
        // 2. 通知所有监听器
        for (SceneListener listener : sListeners) {
            try {
                listener.onSceneEnd(sceneId);
            } catch (Exception e) {
                Log.e(TAG, "Listener callback failed", e);
            }
        }
    }
    
    /**
     * 注册场景监听器
     * 
     * 注意: 监听器回调在调用 notifySceneStart/End 的线程中执行
     * 
     * @param listener 监听器
     */
    public static void registerListener(SceneListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        
        if (!sListeners.contains(listener)) {
            sListeners.add(listener);
            if (DEBUG) {
                Log.d(TAG, "registerListener: " + listener + ", total=" + sListeners.size());
            }
        }
    }
    
    /**
     * 注销场景监听器
     * 
     * @param listener 监听器
     */
    public static void unregisterListener(SceneListener listener) {
        if (listener != null) {
            sListeners.remove(listener);
            if (DEBUG) {
                Log.d(TAG, "unregisterListener: " + listener + 
                    ", remaining=" + sListeners.size());
            }
        }
    }
    
    // ==================== 反射实现 ====================
    
    /**
     * 初始化反射 (懒加载，线程安全)
     */
    private static void ensureReflectionInitialized() {
        if (sReflectionInitialized) return;
        
        synchronized (TranSceneHint.class) {
            if (sReflectionInitialized) return;
            
            try {
                // 加载 TranPerfHub 类
                sPerfHubClass = Class.forName(PERF_HUB_CLASS);
                
                // 获取 acquirePerfLock 方法
                sAcquireMethod = sPerfHubClass.getMethod(
                    "acquirePerfLock", int.class, int.class);
                
                // 获取 releasePerfLock 方法
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
     * 获取性能锁
     */
    private static int acquirePerfLock(int sceneId, int param) {
        ensureReflectionInitialized();
        
        if (sAcquireMethod == null) {
            return -1;
        }
        
        try {
            Object result = sAcquireMethod.invoke(null, sceneId, param);
            return (result instanceof Integer) ? (Integer) result : -1;
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire perf lock: sceneId=" + sceneId, e);
            return -1;
        }
    }
    
    /**
     * 释放性能锁
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
