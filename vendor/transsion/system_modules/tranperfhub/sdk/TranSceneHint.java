package android.util;

/**
 * TranSceneHint - 轻量级 SDK
 * 
 * 编译期依赖:
 * compileOnly 'com.transsion.perfhub:transcenehint:1.0.0'
 * 
 * 运行期:
 * 调用系统内置的 android.util.TranSceneHint (在 framework.jar 中)
 * 
 * 注意: 此类在编译后不会打包进 APK
 */
public final class TranSceneHint {
    
    // ==================== 场景常量 ====================
    
    public static final int SCENE_APP_LAUNCH = 1;
    public static final int SCENE_SCROLL = 2;
    public static final int SCENE_ANIMATION = 3;
    public static final int SCENE_WINDOW_SWITCH = 4;
    public static final int SCENE_TOUCH = 5;
    public static final int SCENE_APP_SWITCH = 6;
    
    // ==================== 监听器接口 ====================
    
    public interface SceneListener {
        void onSceneStart(int sceneId, int param);
        void onSceneEnd(int sceneId);
    }
    
    // ==================== 公开 API ====================
    
    /**
     * 通知场景开始
     * 
     * 运行时会调用系统内置的实现
     */
    public static void notifySceneStart(int sceneId, int param) {
        // Stub 实现，实际由系统提供
        throw new RuntimeException("Stub! This should be provided by system.");
    }
    
    /**
     * 通知场景结束
     */
    public static void notifySceneEnd(int sceneId) {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * 注册监听器 (仅系统 App 可用)
     */
    public static void registerListener(SceneListener listener) {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * 注销监听器
     */
    public static void unregisterListener(SceneListener listener) {
        throw new RuntimeException("Stub!");
    }
    
    // 禁止实例化
    private TranSceneHint() {}
}
