#pragma once

// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。
// MTK：抽象动作→平台命令映射（任务清单 §7.4–§7.5）。

#include <cstdint>

#include "platform/PlatformBase.h"

namespace vendor::transsion::perfengine::dse {

class MtkActionMap : public IPlatformActionMap {
public:
    explicit MtkActionMap(const PlatformCapability &capability);
    ~MtkActionMap() override = default;

    PlatformCommandBatch Map(const AbstractActionBatch &abstractBatch) override;
    PlatformCommandBatch MapFastGrant(const FastGrant &grant, IntentLevel effectiveIntent,
                                      TimeMode timeMode) override;
    uint16_t GetActionKey(ResourceDim dim, uint32_t actionType) const override;

private:
    PlatformCapability capability_;

    static constexpr uint16_t kMtkCpuBoostCmd = 0x0100;
    static constexpr uint16_t kMtkCpuFreqCmd = 0x0101;
    static constexpr uint16_t kMtkMemCapCtl = 0x0110;
    static constexpr uint16_t kMtkMemBwCmd = 0x0200;
    static constexpr uint16_t kMtkGpuFreqCmd = 0x0300;
    static constexpr uint16_t kMtkGpuBoostCmd = 0x0301;
    static constexpr uint16_t kMtkStorageBwCtl = 0x0400;
    static constexpr uint16_t kMtkStorageIopsCtl = 0x0401;
    static constexpr uint16_t kMtkNetworkBwCtl = 0x0500;
    static constexpr uint16_t kMtkFallbackFlag = 0xF000;
};

}    // namespace vendor::transsion::perfengine::dse
