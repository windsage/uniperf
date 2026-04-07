#pragma once

// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。
// QCOM 首期验收范围（任务清单 §0.2）。

#include <cstdint>

#include "platform/PlatformBase.h"
#include "platform/PlatformExecutorCommon.h"

namespace vendor::transsion::perfengine::dse {

class QcomExecutor : public IPlatformExecutor {
public:
    explicit QcomExecutor(const PlatformCapability &capability);
    ~QcomExecutor() override = default;

    PlatformExecutionResult Execute(const PlatformCommandBatch &batch) override;
    PlatformCapability GetCapability() const override { return capability_; }
    const char *GetName() const override { return "QcomExecutor"; }

private:
    using WriteResult = platform_executor_detail::WriteResult;

    WriteResult WritePerfHint(uint16_t hintId, int32_t value, uint32_t flags, uint32_t &reasonCode);
    WriteResult WriteCgroupValue(const char *path, uint32_t value, uint32_t &reasonCode);
    WriteResult WriteResourceControl(ResourceDim dim, uint32_t value, uint32_t &reasonCode);
    WriteResult WriteNetworkControl(ResourceDim dim, uint32_t value, uint32_t &reasonCode);
    const char *ResolveControlPath(ResourceDim dim) const;

    PlatformCapability capability_;
};

}    // namespace vendor::transsion::perfengine::dse
