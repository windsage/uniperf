#pragma once

// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。
// QCOM：抽象动作→平台命令映射（任务清单 §7.4–§7.5）。

#include <cstdint>

#include "platform/PlatformBase.h"

namespace vendor::transsion::perfengine::dse {

class QcomActionMap : public IPlatformActionMap {
public:
    explicit QcomActionMap(const PlatformCapability &capability);
    ~QcomActionMap() override = default;

    PlatformCommandBatch Map(const AbstractActionBatch &abstractBatch) override;
    PlatformCommandBatch MapFastGrant(const FastGrant &grant, IntentLevel effectiveIntent,
                                      TimeMode timeMode) override;
    uint16_t GetActionKey(ResourceDim dim, uint32_t actionType) const override;

private:
    PlatformCapability capability_;

    static constexpr uint16_t kQcomCpuFreqHint = 0x0100;
    static constexpr uint16_t kQcomCpuBoostHint = 0x0101;
    static constexpr uint16_t kQcomMemCapCtl = 0x0110;
    static constexpr uint16_t kQcomMemBwHint = 0x0200;
    static constexpr uint16_t kQcomGpuFreqHint = 0x0300;
    static constexpr uint16_t kQcomGpuBusHint = 0x0301;
    static constexpr uint16_t kQcomStorageBwCtl = 0x0400;
    static constexpr uint16_t kQcomStorageIopsCtl = 0x0401;
    static constexpr uint16_t kQcomNetworkBwCtl = 0x0500;
    static constexpr uint16_t kQcomFallbackFlag = 0xF000;
};

}    // namespace vendor::transsion::perfengine::dse
