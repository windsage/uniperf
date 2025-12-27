#define LOG_TAG "TranPerfHub-Service"

#include "TranPerfHubService.h"
#include "PlatformAdapter.h"

#include <utils/Log.h>
#include <android-base/logging.h>

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

using android::AutoMutex;

TranPerfHubService::TranPerfHubService() {
    ALOGI("TranPerfHubService created");
}

TranPerfHubService::~TranPerfHubService() {
    ALOGI("TranPerfHubService destroyed");
}

/**
 * 场景开始
 * 
 * 流程:
 * 1. 调用平台适配器获取性能锁
 * 2. 记录 sceneId -> handle 映射
 * 3. 返回 handle
 */
ndk::ScopedAStatus TranPerfHubService::notifySceneStart(
    int32_t sceneId, 
    int32_t param, 
    int32_t* _aidl_return) {
    
    ALOGD("notifySceneStart: sceneId=%d, param=%d", sceneId, param);
    
    // 1. 调用平台适配器
    PlatformAdapter& adapter = PlatformAdapter::getInstance();
    int32_t handle = adapter.acquirePerfLock(sceneId, param);
    
    if (handle <= 0) {
        ALOGE("Failed to acquire perf lock: sceneId=%d", sceneId);
        *_aidl_return = -1;
        return ndk::ScopedAStatus::ok();
    }
    
    // 2. 记录映射
    {
        AutoMutex _l(mSceneLock);
        
        // 如果该场景已有句柄，先释放旧的
        auto it = mSceneHandles.find(sceneId);
        if (it != mSceneHandles.end()) {
            int32_t oldHandle = it->second;
            ALOGD("Releasing old handle: %d for sceneId: %d", oldHandle, sceneId);
            adapter.releasePerfLock(oldHandle);
        }
        
        // 更新映射
        mSceneHandles[sceneId] = handle;
    }
    
    ALOGD("Scene started: sceneId=%d, handle=%d", sceneId, handle);
    
    *_aidl_return = handle;
    return ndk::ScopedAStatus::ok();
}

/**
 * 场景结束
 * 
 * 流程:
 * 1. 根据 sceneId 查找对应的 handle
 * 2. 调用平台适配器释放性能锁
 * 3. 删除映射记录
 */
ndk::ScopedAStatus TranPerfHubService::notifySceneEnd(int32_t sceneId) {
    ALOGD("notifySceneEnd: sceneId=%d", sceneId);
    
    AutoMutex _l(mSceneLock);
    
    // 查找场景对应的句柄
    auto it = mSceneHandles.find(sceneId);
    if (it == mSceneHandles.end()) {
        ALOGW("No handle found for sceneId: %d", sceneId);
        return ndk::ScopedAStatus::ok();
    }
    
    int32_t handle = it->second;
    
    // 释放性能锁
    PlatformAdapter& adapter = PlatformAdapter::getInstance();
    adapter.releasePerfLock(handle);
    
    // 删除映射
    mSceneHandles.erase(it);
    
    ALOGD("Scene ended: sceneId=%d, handle=%d", sceneId, handle);
    
    return ndk::ScopedAStatus::ok();
}

} // namespace perfhub
} // namespace hardware
} // namespace transsion
} // namespace vendor
