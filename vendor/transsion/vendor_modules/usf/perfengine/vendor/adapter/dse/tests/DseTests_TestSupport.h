#pragma once

#include "dse/Dse.h"

// Some tests (e.g. StateService/metrics integration) need the service header explicitly.
#include "core/service/state/StateService.h"

// These helpers are used by multiple test translation units.
// Keep them in a header to avoid duplicating large blocks and to make moved tests compile.

#if defined(PERFENGINE_PLATFORM_QCOM)
#include "platform/qcom/QcomActionMap.h"
#include "platform/qcom/QcomExecutor.h"
#include "platform/qcom/QcomPlatformStateBackend.h"
#elif defined(PERFENGINE_PLATFORM_MTK)
#include "platform/mtk/MtkActionMap.h"
#include "platform/mtk/MtkExecutor.h"
#include "platform/mtk/MtkPlatformStateBackend.h"
#endif

namespace vendor::transsion::perfengine::dse {

inline void ForcePlatformRegistrationLinkage() {
#if defined(PERFENGINE_PLATFORM_QCOM)
    PlatformCapability capability;
    capability.vendor = PlatformVendor::Qcom;
    QcomExecutor executor(capability);
    size_t nodeCount = 0;
    QcomPlatformStateBackend backend;
    (void)executor.GetName();
    (void)backend.GetNodeSpecs(&nodeCount);
#elif defined(PERFENGINE_PLATFORM_MTK)
    PlatformCapability capability;
    capability.vendor = PlatformVendor::Mtk;
    MtkExecutor executor(capability);
    size_t nodeCount = 0;
    MtkPlatformStateBackend backend;
    (void)executor.GetName();
    (void)backend.GetNodeSpecs(&nodeCount);
#endif
}

struct CountingActionMap final : IPlatformActionMap {
    PlatformCommandBatch Map(const AbstractActionBatch &abstractBatch) override {
        PlatformCommandBatch batch;
        batch.commandCount = abstractBatch.actionCount;
        batch.reasonBits = abstractBatch.reasonBits;
        batch.path = PlatformActionPath::PerfHintHal;
        for (uint32_t i = 0; i < abstractBatch.actionCount; ++i) {
            const auto &action = abstractBatch.actions[i];
            auto &cmd = batch.commands[i];
            cmd.commandKey = action.actionKey;
            cmd.backend = 1;
            cmd.resourceDim = action.actionKey;
            cmd.uintValue = action.value;
            cmd.flags = action.flags;
        }
        return batch;
    }

    PlatformCommandBatch MapFastGrant(const FastGrant &grant, IntentLevel effectiveIntent,
                                      TimeMode timeMode) override {
        PlatformCommandBatch batch;
        batch.reasonBits = grant.reasonBits;
        batch.path = PlatformActionPath::PerfHintHal;
        const ActionContractMode contractMode = DeriveActionContractMode(effectiveIntent);
        const uint16_t flags = EncodeActionFlags(true, contractMode, effectiveIntent, timeMode);
        const uint32_t actionType = (contractMode == ActionContractMode::Floor)     ? 1u
                                    : (contractMode == ActionContractMode::Cap)     ? 2u
                                    : (contractMode == ActionContractMode::Elastic) ? 3u
                                                                                    : 0u;
        for (uint32_t dim = 0; dim < kResourceDimCount && batch.commandCount < batch.kMaxCommands;
             ++dim) {
            const uint32_t dimMask = 1u << dim;
            if ((grant.grantedMask & dimMask) == 0) {
                continue;
            }
            auto &cmd = batch.commands[batch.commandCount++];
            cmd.commandKey = GetActionKey(static_cast<ResourceDim>(dim), actionType);
            cmd.backend = 1;
            cmd.resourceDim = static_cast<uint16_t>(dim);
            cmd.uintValue = grant.delivered.v[dim];
            cmd.flags = flags;
        }
        return batch;
    }

    uint16_t GetActionKey(ResourceDim dim, uint32_t actionType) const override {
        return static_cast<uint16_t>((static_cast<uint32_t>(dim) << 4) | (actionType & 0xF));
    }
};

struct CountingExecutor final : IPlatformExecutor {
    PlatformExecutionResult Execute(const PlatformCommandBatch &batch) override {
        ++executeCount;
        PlatformExecutionResult result;
        result.success = forceSuccess;
        for (uint32_t i = 0; i < batch.commandCount; ++i) {
            result.appliedMask |= (1u << batch.commands[i].resourceDim);
        }
        return result;
    }

    PlatformCapability GetCapability() const override {
        PlatformCapability capability;
        capability.vendor = PlatformVendor::Unknown;
        capability.supportedResources = 0xFFu;
        return capability;
    }

    const char *GetName() const override { return "CountingExecutor"; }

    uint32_t executeCount = 0;
    bool forceSuccess = true;
};

}    // namespace vendor::transsion::perfengine::dse
