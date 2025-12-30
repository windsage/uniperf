#ifndef TRANPERF_EVENT_VENDOR_H
#define TRANPERF_EVENT_VENDOR_H

#include <string>
#include <vector>

namespace vendor {
namespace transsion {
namespace perfhub {

/**
 * Scene ID 定义
 */
enum SceneId {
    SCENE_APP_LAUNCH = 1,     // 应用启动
    SCENE_APP_SWITCH = 2,     // 应用切换
    SCENE_SCROLL = 3,         // 滚动
    SCENE_CAMERA_OPEN = 4,    // 相机打开
    SCENE_GAME_START = 5,     // 游戏启动
    SCENE_VIDEO_PLAY = 6,     // 视频播放
    SCENE_ANIMATION = 7,      // 动画
};

/**
 * TranPerfHub Vendor Native API
 */
class TranPerfEvent {
public:
    // ========================================
    // AIDL 原始接口 (与 AIDL 完全一致)
    // ========================================

    /**
     * 通知性能事件开始
     *
     * @param eventId 事件ID/场景ID
     * @param timestamp 事件时间戳 (纳秒)
     * @param numParams intParams 数组的有效长度
     * @param intParams 整型参数数组
     * @param extraStrings 字符串参数 (通常是 packageName)
     */
    static void notifyEventStart(int eventId, long timestamp, int numParams,
                                 const std::vector<int> &intParams,
                                 const std::string &extraStrings = "");

    /**
     * 通知性能事件结束
     *
     * @param eventId 事件ID/场景ID
     * @param timestamp 事件时间戳 (纳秒)
     * @param extraStrings 字符串参数 (通常是 packageName)
     */
    static void notifyEventEnd(int eventId, long timestamp, const std::string &extraStrings = "");

    // ========================================
    // 便捷接口 (自动填充 timestamp 和 numParams)
    // ========================================

    /**
     * 便捷方法: 通知事件开始 (自动获取时间戳)
     *
     * @param eventId 事件ID
     * @param intParams 整型参数数组
     * @param extraStrings 字符串参数
     */
    static void notifyEventStartAuto(int eventId, const std::vector<int> &intParams,
                                     const std::string &extraStrings = "");

    /**
     * 便捷方法: 通知事件结束 (自动获取时间戳)
     *
     * @param eventId 事件ID
     * @param extraStrings 字符串参数
     */
    static void notifyEventEndAuto(int eventId, const std::string &extraStrings = "");

    /**
     * 工具方法: 获取当前时间戳 (纳秒)
     */
    static long getCurrentTimestampNs();
};

}    // namespace perfhub
}    // namespace transsion
}    // namespace vendor

#endif    // TRANPERF_EVENT_VENDOR_H
