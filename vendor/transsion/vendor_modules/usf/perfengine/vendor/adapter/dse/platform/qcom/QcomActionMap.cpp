// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。

#include "QcomActionMap.h"

#include "platform/PlatformActionMapCommon.h"

namespace vendor::transsion::perfengine::dse {

QcomActionMap::QcomActionMap(const PlatformCapability &capability) : capability_(capability) {}

PlatformCommandBatch QcomActionMap::Map(const AbstractActionBatch &abstractBatch) {
    return platform_action_map_detail::MapAbstractActionBatch(
        abstractBatch, capability_, kQcomFallbackFlag,
        [this](ResourceDim dim, uint32_t actionType) { return GetActionKey(dim, actionType); });
}

PlatformCommandBatch QcomActionMap::MapFastGrant(const FastGrant &grant,
                                                 IntentLevel effectiveIntent, TimeMode timeMode) {
    return platform_action_map_detail::MapFastGrantBatch(
        grant, capability_, kQcomFallbackFlag, effectiveIntent, timeMode,
        [this](ResourceDim dim, uint32_t actionType) { return GetActionKey(dim, actionType); });
}

uint16_t QcomActionMap::GetActionKey(ResourceDim dim, uint32_t actionType) const {
    switch (dim) {
        case ResourceDim::CpuCapacity:
            return (actionType == 1) ? kQcomCpuBoostHint
                                     : static_cast<uint16_t>(kQcomCpuFreqHint + actionType);
        case ResourceDim::MemoryCapacity:
            return kQcomMemCapCtl + static_cast<uint16_t>(actionType);
        case ResourceDim::MemoryBandwidth:
            return kQcomMemBwHint + static_cast<uint16_t>(actionType);
        case ResourceDim::GpuCapacity:
            return (actionType == 1) ? kQcomGpuBusHint
                                     : static_cast<uint16_t>(kQcomGpuFreqHint + actionType);
        case ResourceDim::StorageBandwidth:
            return kQcomStorageBwCtl + static_cast<uint16_t>(actionType);
        case ResourceDim::StorageIops:
            return kQcomStorageIopsCtl + static_cast<uint16_t>(actionType);
        case ResourceDim::NetworkBandwidth:
            return kQcomNetworkBwCtl + static_cast<uint16_t>(actionType);
        default:
            return static_cast<uint16_t>(dim);
    }
}
}    // namespace vendor::transsion::perfengine::dse
