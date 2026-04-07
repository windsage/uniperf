#pragma once

#include "StageBase.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

class ResourceStage : public IStage {
public:
    static constexpr uint32_t kRawFlagHasLockDependency = (1u << 28);
    static constexpr uint32_t kRawFlagHasBinderDependency = (1u << 29);
    static constexpr uint32_t kRawFlagHasAnyDependency =
        kRawFlagHasLockDependency | kRawFlagHasBinderDependency;

    const char *Name() const override { return "ResourceStage"; }

    StageOutput Execute(StageContext &ctx) override;

private:
    /**
     * @brief 检测依赖关系
     * @param ctx 阶段上下文
     * @return 是否检测到依赖关系
     *
     * 只有检测到依赖关系时才返回 true，
     * 用于精确控制 DirtyBits 的设置。
     */
    uint32_t DetectDependency(StageContext &ctx);
};

}    // namespace vendor::transsion::perfengine::dse
