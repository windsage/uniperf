#pragma once

#include <cstdint>

#include "CoreTypes.h"
#include "dse/Types.h"

namespace vendor::transsion::perfengine::dse {

enum class PowerState : uint8_t {
    Unknown = 0,
    Active = 1,
    Doze = 2,
    Sleep = 3,
    Suspend = 4,
    Charging = 5,
    Discharging = 6,
};

struct ConstraintSnapshot {
    uint32_t thermalLevel = 0;
    uint32_t thermalScaleQ10 = 1024;
    uint32_t batteryLevel = 100;
    uint32_t batteryScaleQ10 = 1024;
    uint32_t memoryPressure = 0;
    uint32_t psiLevel = 0;
    uint32_t psiScaleQ10 = 1024;
    PowerState powerState = PowerState::Active;
    bool batterySaver = false;
    bool screenOn = true;
    bool thermalSevere = false;
};

struct ConstraintEnvelope {
    uint32_t resourceMask = 0;
    ResourceVector allowed;
    uint32_t sourceBits = 0;

    uint32_t thermalLevel = 0;
    uint32_t batteryLevel = 100;
    uint32_t memoryLevel = 0;
    uint32_t thermalScalingQ10 = 1000;
    uint32_t batteryScalingQ10 = 1000;
    uint32_t memoryScalingQ10 = 1000;
};

struct CapabilityDomain {
    uint32_t maxCapacity = 0;
    uint32_t flags = 0;
};

struct CapabilitySnapshot {
    uint32_t supportedResources = 0;
    uint32_t capabilityFlags = 0;
    CapabilityDomain domains[kResourceDimCount];
    uint32_t actionPathFlags[kResourceDimCount] = {0};
};

/** 监听层聚合后、与 Vault 提交对齐的 canonical 约束比较（不含 generation/timestamp） */
inline bool CanonicalConstraintSnapshotEquals(const ConstraintSnapshot &a,
                                              const ConstraintSnapshot &b) noexcept {
    return a.thermalLevel == b.thermalLevel && a.thermalScaleQ10 == b.thermalScaleQ10 &&
           a.batteryLevel == b.batteryLevel && a.batteryScaleQ10 == b.batteryScaleQ10 &&
           a.memoryPressure == b.memoryPressure && a.psiLevel == b.psiLevel &&
           a.psiScaleQ10 == b.psiScaleQ10 && a.powerState == b.powerState &&
           a.batterySaver == b.batterySaver && a.screenOn == b.screenOn &&
           a.thermalSevere == b.thermalSevere;
}

inline bool CanonicalCapabilitySnapshotEquals(const CapabilitySnapshot &a,
                                              const CapabilitySnapshot &b) noexcept {
    if (a.supportedResources != b.supportedResources || a.capabilityFlags != b.capabilityFlags) {
        return false;
    }
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        if (a.domains[i].maxCapacity != b.domains[i].maxCapacity ||
            a.domains[i].flags != b.domains[i].flags ||
            a.actionPathFlags[i] != b.actionPathFlags[i]) {
            return false;
        }
    }
    return true;
}

/**
 * @brief 将 canonical capability snapshot 转换为调度链消费的可行能力向量。
 *
 * 该 helper 保证状态层与服务层对 `resourceMask` 的解释保持一致：
 * 仅对显式支持的维度透传 `maxCapacity`，未声明支持的维度统一清零，
 * 避免不同调用点各自复制循环逻辑后发生裁剪语义漂移。
 */
inline CapabilityFeasible BuildCapabilityFeasible(const CapabilitySnapshot &snapshot,
                                                  uint32_t resourceMask) noexcept {
    CapabilityFeasible feasible;
    feasible.resourceMask = resourceMask;
    feasible.capabilityFlags = snapshot.capabilityFlags;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        const uint32_t dimMask = (1u << i);
        feasible.feasible.v[i] = ((resourceMask & dimMask) != 0) ? snapshot.domains[i].maxCapacity
                                                                 : 0u;
    }
    return feasible;
}

}    // namespace vendor::transsion::perfengine::dse
