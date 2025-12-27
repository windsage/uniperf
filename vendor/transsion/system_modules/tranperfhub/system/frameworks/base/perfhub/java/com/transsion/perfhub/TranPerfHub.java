package com.transsion.perfhub;

import android.util.Log;

/**
 * TranPerfHub - 底层性能优化接口
 * 
 * 职责:
 * - 通过 JNI 调用 Native 层
 * - Native 层通过 AIDL 调用 Vendor 层
 * - Vendor 层进行平台适配和参数映射
 * 
 * 注意: 此类不包含监听器机制，监听器在 TranSceneHint中实现
 * 
 * @hide
 */
public class TranPerfHub {
    private static final String TAG = "TranPerfHub";
    private static final boolean DEBUG = false;
    
    // 场景常量定义
    public static final int SCENE_APP_LAUNCH = 1;
    public static final int SCENE_SCROLL = 2;
    public static final int SCENE_ANIMATION = 3;
    public static final int SCENE_WINDOW_SWITCH = 4;
    public static final int SCENE_TOUCH = 5;
    public static final int SCENE_APP_SWITCH = 6;
    
    // Native 库加载状态
    private static boolean sNativeLoaded = false;
    
    // 加载 Native 库
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
    
    // 禁止实例化
    private TranPerfHub() {}
    
    /**
     * 获取性能锁
     * 
     * @param sceneId 场景ID
     * @param param 场景参数
     * @return 性能锁句柄 (>0 成功, <=0 失败)
     */
    public static int acquirePerfLock(int sceneId, int param) {
        if (!sNativeLoaded) {
            if (DEBUG) {
                Log.w(TAG, "Native library not loaded");
            }
            return -1;
        }
        
        int handle = nativeAcquirePerfLock(sceneId, param);
        if (DEBUG) {
            Log.d(TAG, "acquirePerfLock: sceneId=" + sceneId + 
                ", param=" + param + ", handle=" + handle);
        }
        return handle;
    }
    
    /**
     * 释放性能锁
     * 
     * @param handle 性能锁句柄
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
    
    // ==================== Native 方法 ====================
    
    private static native void nativeInit();
    private static native int nativeAcquirePerfLock(int sceneId, int param);
    private static native void nativeReleasePerfLock(int handle);
}