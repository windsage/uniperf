#ifndef PLATFORM_ADAPTER_H
#define PLATFORM_ADAPTER_H

#include <stdint.h>

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

/**
 * 平台适配器 - 封装 QCOM/MTK 平台调用
 * 
 * 单例模式
 */
class PlatformAdapter {
public:
    static PlatformAdapter& getInstance();
    
    /**
     * Acquire performance lock
     * 
     * @param eventType Event type
     * @param eventParam Event parameter
     * @return handle (>0 success, <=0 failure)
     */
    int32_t acquirePerfLock(int32_t eventType, int32_t eventParam);
    
    /**
     * 释放性能锁
     * 
     * @param handle 性能锁句柄
     */
    void releasePerfLock(int32_t handle);

private:
    PlatformAdapter();
    ~PlatformAdapter();
    
    // 禁止拷贝
    PlatformAdapter(const PlatformAdapter&) = delete;
    PlatformAdapter& operator=(const PlatformAdapter&) = delete;
    
    // 平台检测
    enum Platform {
        PLATFORM_UNKNOWN = 0,
        PLATFORM_QCOM = 1,
        PLATFORM_MTK = 2,
    };
    
    Platform detectPlatform();
    
    // 平台初始化
    bool initQcom();
    bool initMtk();
    
    // QCOM 平台调用
    int32_t qcomAcquirePerfLock(int32_t eventType, int32_t eventParam);
    void qcomReleasePerfLock(int32_t handle);
    
    // MTK 平台调用
    int32_t mtkAcquirePerfLock(int32_t eventType, int32_t eventParam);
    void mtkReleasePerfLock(int32_t handle);
    
    Platform mPlatform;
    void* mQcomLibHandle;
    void* mMtkLibHandle;
};

} // namespace perfhub
} // namespace hardware
} // namespace transsion
} // namespace vendor

#endif // PLATFORM_ADAPTER_H
