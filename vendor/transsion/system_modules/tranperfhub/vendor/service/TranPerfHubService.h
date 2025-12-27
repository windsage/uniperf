#ifndef TRANPERFHUB_SERVICE_H
#define TRANPERFHUB_SERVICE_H

#include <aidl/vendor/transsion/hardware/perfhub/BnTranPerfHub.h>
#include <utils/Mutex.h>
#include <map>

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

using aidl::vendor::transsion::hardware::perfhub::BnTranPerfHub;
using aidl::vendor::transsion::hardware::perfhub::ITranPerfHub;

/**
 * TranPerfHub AIDL 服务实现
 * 
 * 职责:
 * 1. 实现 ITranPerfHub AIDL 接口
 * 2. 管理场景到句柄的映射 (sceneId -> handle)
 * 3. 调用平台适配器进行性能优化
 */
class TranPerfHubService : public BnTranPerfHub {
public:
    TranPerfHubService();
    ~TranPerfHubService();
    
    // AIDL 接口实现
    ndk::ScopedAStatus notifySceneStart(
        int32_t sceneId, 
        int32_t param, 
        int32_t* _aidl_return) override;
    
    ndk::ScopedAStatus notifySceneEnd(int32_t sceneId) override;

private:
    // 场景句柄映射 (sceneId -> handle)
    std::map<int32_t, int32_t> mSceneHandles;
    android::Mutex mSceneLock;
};

} // namespace perfhub
} // namespace hardware
} // namespace transsion
} // namespace vendor

#endif // TRANPERFHUB_SERVICE_H
