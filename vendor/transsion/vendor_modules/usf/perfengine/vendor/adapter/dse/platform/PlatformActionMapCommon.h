#pragma once

#include "PlatformBase.h"
#include "core/exec/PlatformActionMapper.h"

namespace vendor::transsion::perfengine::dse::platform_action_map_detail {

inline uint32_t ResolveActionType(ActionContractMode contractMode) noexcept {
    switch (contractMode) {
        case ActionContractMode::Floor:
            return 1;
        case ActionContractMode::Cap:
            return 2;
        case ActionContractMode::Elastic:
            return 3;
        case ActionContractMode::Target:
        case ActionContractMode::None:
        default:
            return 0;
    }
}

inline uint32_t ResolveActionType(const AbstractAction &action) noexcept {
    return ResolveActionType(DecodeActionContractMode(action.flags));
}

inline bool IsResourceSupported(const PlatformCapability &capability, ResourceDim dim) noexcept {
    const uint32_t dimMask = 1u << static_cast<uint32_t>(dim);
    return (capability.supportedResources & dimMask) != 0;
}

inline void AppendCommand(PlatformCommandBatch &batch, uint16_t commandKey, uint16_t backend,
                          ResourceDim dim, const AbstractAction &action,
                          bool useSignedValue) noexcept {
    if (batch.commandCount >= PlatformCommandBatch::kMaxCommands) {
        return;
    }

    auto &cmd = batch.commands[batch.commandCount++];
    cmd = PlatformCommand{};
    cmd.commandKey = commandKey;
    cmd.backend = backend;
    cmd.resourceDim = static_cast<uint16_t>(dim);
    if (useSignedValue) {
        cmd.intValue = static_cast<int32_t>(action.value);
    } else {
        cmd.uintValue = action.value;
    }
    cmd.flags = action.flags;
}

inline void AppendFastCommand(PlatformCommandBatch &batch, uint16_t commandKey, uint16_t backend,
                              ResourceDim dim, uint32_t value, uint16_t flags,
                              bool useSignedValue) noexcept {
    if (batch.commandCount >= PlatformCommandBatch::kMaxCommands) {
        return;
    }

    auto &cmd = batch.commands[batch.commandCount++];
    cmd = PlatformCommand{};
    cmd.commandKey = commandKey;
    cmd.backend = backend;
    cmd.resourceDim = static_cast<uint16_t>(dim);
    if (useSignedValue) {
        cmd.intValue = static_cast<int32_t>(value);
    } else {
        cmd.uintValue = value;
    }
    cmd.flags = flags;
}

inline void AppendFallbackCommand(PlatformCommandBatch &batch, uint16_t fallbackFlag,
                                  ResourceDim dim, const AbstractAction &action) noexcept {
    if (batch.commandCount >= PlatformCommandBatch::kMaxCommands) {
        return;
    }

    auto &cmd = batch.commands[batch.commandCount++];
    cmd = PlatformCommand{};
    cmd.commandKey = fallbackFlag | static_cast<uint16_t>(dim);
    cmd.backend = 0;
    cmd.resourceDim = static_cast<uint16_t>(dim);
    cmd.intValue = static_cast<int32_t>(action.value);
    cmd.flags = action.flags | 0x8000;

    const uint32_t dimMask = 1u << static_cast<uint32_t>(dim);
    batch.fallbackFlags |= dimMask;
}

inline void AppendFastFallbackCommand(PlatformCommandBatch &batch, uint16_t fallbackFlag,
                                      ResourceDim dim, uint32_t value, uint16_t flags) noexcept {
    if (batch.commandCount >= PlatformCommandBatch::kMaxCommands) {
        return;
    }

    auto &cmd = batch.commands[batch.commandCount++];
    cmd = PlatformCommand{};
    cmd.commandKey = fallbackFlag | static_cast<uint16_t>(dim);
    cmd.backend = 0;
    cmd.resourceDim = static_cast<uint16_t>(dim);
    cmd.intValue = static_cast<int32_t>(value);
    cmd.flags = flags | 0x8000u;

    const uint32_t dimMask = 1u << static_cast<uint32_t>(dim);
    batch.fallbackFlags |= dimMask;
}

inline void FinalizePath(PlatformCommandBatch &batch) noexcept {
    if (batch.commandCount == 0) {
        batch.path = PlatformActionPath::NoopFallback;
        return;
    }

    bool hasPerfHint = false;
    bool hasKernelCtl = false;
    bool hasExecutable = false;
    for (uint32_t i = 0; i < batch.commandCount; ++i) {
        const auto backend = batch.commands[i].backend;
        if (backend == 1) {
            hasExecutable = true;
            hasPerfHint = true;
        } else if (backend == 2 || backend == 3) {
            hasExecutable = true;
            hasKernelCtl = true;
        }
    }

    if (!hasExecutable) {
        batch.path = PlatformActionPath::NoopFallback;
        return;
    }

    if (hasPerfHint && hasKernelCtl) {
        batch.path = PlatformActionPath::Mixed;
    } else if (hasPerfHint) {
        batch.path = PlatformActionPath::PerfHintHal;
    } else {
        batch.path = PlatformActionPath::DirectKernelCtl;
    }
}

template <typename ActionKeyResolver>
inline PlatformCommandBatch MapAbstractActionBatch(const AbstractActionBatch &abstractBatch,
                                                   const PlatformCapability &capability,
                                                   uint16_t fallbackFlag,
                                                   ActionKeyResolver &&resolveActionKey) {
    PlatformCommandBatch batch;
    batch.commandCount = 0;
    batch.reasonBits = abstractBatch.reasonBits;
    batch.path = PlatformActionPath::NoopFallback;
    batch.fallbackFlags = 0;

    for (uint32_t i = 0;
         i < abstractBatch.actionCount && batch.commandCount < PlatformCommandBatch::kMaxCommands;
         ++i) {
        const auto &action = abstractBatch.actions[i];
        const ResourceDim dim = static_cast<ResourceDim>(action.actionKey);
        if (!IsResourceSupported(capability, dim)) {
            AppendFallbackCommand(batch, fallbackFlag, dim, action);
            continue;
        }

        const uint32_t actionType = ResolveActionType(action);
        switch (dim) {
            case ResourceDim::CpuCapacity:
                AppendCommand(batch, resolveActionKey(ResourceDim::CpuCapacity, actionType), 1,
                              ResourceDim::CpuCapacity, action, true);
                break;
            case ResourceDim::MemoryCapacity:
                AppendCommand(batch, resolveActionKey(ResourceDim::MemoryCapacity, actionType), 2,
                              ResourceDim::MemoryCapacity, action, false);
                break;
            case ResourceDim::MemoryBandwidth:
                AppendCommand(batch, resolveActionKey(ResourceDim::MemoryBandwidth, actionType), 1,
                              ResourceDim::MemoryBandwidth, action, true);
                break;
            case ResourceDim::GpuCapacity:
                AppendCommand(batch, resolveActionKey(ResourceDim::GpuCapacity, actionType), 1,
                              ResourceDim::GpuCapacity, action, true);
                break;
            case ResourceDim::StorageBandwidth:
            case ResourceDim::StorageIops:
                AppendCommand(batch, resolveActionKey(dim, actionType), 2, dim, action, false);
                break;
            case ResourceDim::NetworkBandwidth:
                AppendCommand(batch, resolveActionKey(ResourceDim::NetworkBandwidth, actionType), 3,
                              ResourceDim::NetworkBandwidth, action, false);
                break;
            case ResourceDim::Reserved:
            default:
                AppendFallbackCommand(batch, fallbackFlag, dim, action);
                break;
        }
    }

    FinalizePath(batch);
    return batch;
}

template <typename ActionKeyResolver>
inline PlatformCommandBatch MapFastGrantBatch(const FastGrant &grant,
                                              const PlatformCapability &capability,
                                              uint16_t fallbackFlag, IntentLevel effectiveIntent,
                                              TimeMode timeMode,
                                              ActionKeyResolver &&resolveActionKey) {
    PlatformCommandBatch batch;
    batch.commandCount = 0;
    batch.reasonBits = grant.reasonBits;
    batch.path = PlatformActionPath::NoopFallback;
    batch.fallbackFlags = 0;

    const ActionContractMode contractMode = DeriveActionContractMode(effectiveIntent);
    const uint32_t actionType = ResolveActionType(contractMode);
    const uint16_t flags = EncodeActionFlags(true, contractMode, effectiveIntent, timeMode);

    for (uint32_t dimIndex = 0;
         dimIndex < kResourceDimCount && batch.commandCount < PlatformCommandBatch::kMaxCommands;
         ++dimIndex) {
        const uint32_t dimMask = 1u << dimIndex;
        if ((grant.grantedMask & dimMask) == 0) {
            continue;
        }

        const ResourceDim dim = static_cast<ResourceDim>(dimIndex);
        const uint32_t value = grant.delivered.v[dimIndex];
        if (!IsResourceSupported(capability, dim)) {
            AppendFastFallbackCommand(batch, fallbackFlag, dim, value, flags);
            continue;
        }

        switch (dim) {
            case ResourceDim::CpuCapacity:
                AppendFastCommand(batch, resolveActionKey(ResourceDim::CpuCapacity, actionType), 1,
                                  dim, value, flags, true);
                break;
            case ResourceDim::MemoryCapacity:
                AppendFastCommand(batch, resolveActionKey(ResourceDim::MemoryCapacity, actionType),
                                  2, dim, value, flags, false);
                break;
            case ResourceDim::MemoryBandwidth:
                AppendFastCommand(batch, resolveActionKey(ResourceDim::MemoryBandwidth, actionType),
                                  1, dim, value, flags, true);
                break;
            case ResourceDim::GpuCapacity:
                AppendFastCommand(batch, resolveActionKey(ResourceDim::GpuCapacity, actionType), 1,
                                  dim, value, flags, true);
                break;
            case ResourceDim::StorageBandwidth:
            case ResourceDim::StorageIops:
                AppendFastCommand(batch, resolveActionKey(dim, actionType), 2, dim, value, flags,
                                  false);
                break;
            case ResourceDim::NetworkBandwidth:
                AppendFastCommand(batch,
                                  resolveActionKey(ResourceDim::NetworkBandwidth, actionType), 3,
                                  dim, value, flags, false);
                break;
            case ResourceDim::Reserved:
            default:
                AppendFastFallbackCommand(batch, fallbackFlag, dim, value, flags);
                break;
        }
    }

    FinalizePath(batch);
    return batch;
}

}    // namespace vendor::transsion::perfengine::dse::platform_action_map_detail
