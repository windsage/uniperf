// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。

#include "MtkActionMap.h"

#include "platform/PlatformActionMapCommon.h"

namespace vendor::transsion::perfengine::dse {

MtkActionMap::MtkActionMap(const PlatformCapability &capability) : capability_(capability) {}

PlatformCommandBatch MtkActionMap::Map(const AbstractActionBatch &abstractBatch) {
    return platform_action_map_detail::MapAbstractActionBatch(
        abstractBatch, capability_, kMtkFallbackFlag,
        [this](ResourceDim dim, uint32_t actionType) { return GetActionKey(dim, actionType); });
}

PlatformCommandBatch MtkActionMap::MapFastGrant(const FastGrant &grant, IntentLevel effectiveIntent,
                                                TimeMode timeMode) {
    return platform_action_map_detail::MapFastGrantBatch(
        grant, capability_, kMtkFallbackFlag, effectiveIntent, timeMode,
        [this](ResourceDim dim, uint32_t actionType) { return GetActionKey(dim, actionType); });
}

uint16_t MtkActionMap::GetActionKey(ResourceDim dim, uint32_t actionType) const {
    switch (dim) {
        case ResourceDim::CpuCapacity:
            return (actionType == 1) ? kMtkCpuBoostCmd
                                     : static_cast<uint16_t>(kMtkCpuFreqCmd + actionType);
        case ResourceDim::MemoryCapacity:
            return kMtkMemCapCtl + static_cast<uint16_t>(actionType);
        case ResourceDim::MemoryBandwidth:
            return kMtkMemBwCmd + static_cast<uint16_t>(actionType);
        case ResourceDim::GpuCapacity:
            return (actionType == 1) ? kMtkGpuBoostCmd
                                     : static_cast<uint16_t>(kMtkGpuFreqCmd + actionType);
        case ResourceDim::StorageBandwidth:
            return kMtkStorageBwCtl + static_cast<uint16_t>(actionType);
        case ResourceDim::StorageIops:
            return kMtkStorageIopsCtl + static_cast<uint16_t>(actionType);
        case ResourceDim::NetworkBandwidth:
            return kMtkNetworkBwCtl + static_cast<uint16_t>(actionType);
        default:
            return static_cast<uint16_t>(dim);
    }
}
}    // namespace vendor::transsion::perfengine::dse
