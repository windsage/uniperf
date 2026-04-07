#include "EnvelopeStage.h"

#include <algorithm>

#include "state/StateVault.h"

namespace vendor::transsion::perfengine::dse {

// EnvelopeStage 是“约束守门人”：
// 1. 计算当前约束下 allowed 上限
// 2. 生成当前能力边界 feasible
// 3. 把两者作为后续仲裁的输入
StageOutput EnvelopeStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.state || !ctx.intermediates) {
        out.success = false;
        return out;
    }

    const auto &constraintState = ctx.state->Constraint();
    // 屏幕约束需要结合当前场景语义单独处理，因此这里先计算通用约束，再叠加场景分支。
    auto constraintAllowed = ComputeConstraintAllowedImpl(constraintState.snapshot, false);
    ApplyScreenConstraintForScene(constraintState.snapshot, ctx.GetSceneSemantic(),
                                  ctx.GetIntentContract(), constraintAllowed);

    constraintAllowed.resourceMask = ctx.GetResourceRequest().resourceMask;

    ctx.SetConstraintAllowed(constraintAllowed);

    auto capabilityFeasible = ComputeCapabilityFeasible(ctx);
    ctx.SetCapabilityFeasible(capabilityFeasible);

    out.success = true;
    return out;
}

// 能力边界只来源于状态快照和上游中间态，stage 不再重新探测平台，
// 以保持“平台能力是状态输入，不是策略阶段侧探测结果”的抽象边界。
CapabilityFeasible EnvelopeStage::ComputeCapabilityFeasible(StageContext &ctx) {
    const auto *capabilityState = ctx.state ? &ctx.state->Capability() : nullptr;
    CapabilityFeasible feasible =
        capabilityState ? BuildCapabilityFeasible(capabilityState->snapshot,
                                                  capabilityState->snapshot.supportedResources)
                        : CapabilityFeasible{};

    for (size_t i = 0; i < kResourceDimCount; ++i) {
        const uint32_t dimBit = (1u << i);
        if ((feasible.resourceMask & dimBit) == 0) {
            // 不支持的维度在能力层必须显式归零，避免后续仲裁误以为可交付。
            feasible.feasible.v[i] = 0;
            feasible.capabilityFlags |= dimBit;
        }
    }
    return feasible;
}

uint32_t EnvelopeStage::BuildConstraintSourceBits(const ConstraintSnapshot &snapshot) {
    uint32_t bits = 0;
    if (snapshot.thermalSevere)
        bits |= 0x01;
    if (snapshot.thermalLevel >= 2)
        bits |= 0x02;
    if (snapshot.batterySaver)
        bits |= 0x04;
    if (snapshot.batteryLevel <= 20)
        bits |= 0x08;
    if (snapshot.memoryPressure >= 2)
        bits |= 0x10;
    if (!snapshot.screenOn)
        bits |= 0x20;
    if (snapshot.powerState != PowerState::Active)
        bits |= 0x40;
    if (snapshot.psiLevel >= 2)
        bits |= 0x80;
    return bits;
}

ConstraintEnvelope EnvelopeStage::BuildConstraintEnvelope(const ConstraintSnapshot &snapshot,
                                                          uint32_t resourceMask) {
    ConstraintEnvelope envelope;
    envelope.resourceMask = resourceMask;
    envelope.sourceBits = BuildConstraintSourceBits(snapshot);

    const auto allowed = ComputeConstraintAllowed(snapshot);
    envelope.allowed = allowed.allowed;
    envelope.thermalLevel = snapshot.thermalLevel;
    envelope.batteryLevel = snapshot.batteryLevel;
    envelope.memoryLevel = snapshot.memoryPressure;
    envelope.thermalScalingQ10 = allowed.thermalScaling;
    envelope.batteryScalingQ10 = allowed.batteryScaling;
    envelope.memoryScalingQ10 = allowed.memoryScaling;
    return envelope;
}

ConstraintAllowed EnvelopeStage::ComputeConstraintAllowed(const ConstraintSnapshot &snapshot) {
    return ComputeConstraintAllowedImpl(snapshot, true);
}

// 极致优化版：零分支单次遍历。通过预计算因子表消除循环内分支，对齐 SIMD 优化。
ConstraintAllowed EnvelopeStage::ComputeConstraintAllowedImpl(const ConstraintSnapshot &snapshot,
                                                              bool applyScreenConstraint) {
    ConstraintAllowed allowed;
    allowed.resourceMask = 0xFF;

    // 1. 预填充标志位 (与计算逻辑解耦)
    if (snapshot.thermalSevere)
        allowed.constraintFlags |= 0x01;
    else if (snapshot.thermalLevel >= 4)
        allowed.constraintFlags |= 0x02;
    else if (snapshot.thermalLevel >= 2)
        allowed.constraintFlags |= 0x04;

    if (snapshot.batterySaver)
        allowed.constraintFlags |= 0x08;
    if (snapshot.batteryLevel <= 10)
        allowed.constraintFlags |= 0x10;
    else if (snapshot.batteryLevel <= 20)
        allowed.constraintFlags |= 0x20;

    if (snapshot.memoryPressure >= 3)
        allowed.constraintFlags |= 0x40;
    else if (snapshot.memoryPressure >= 2)
        allowed.constraintFlags |= 0x80;

    if (!snapshot.screenOn)
        allowed.constraintFlags |= 0x100;

    if (snapshot.powerState == PowerState::Suspend)
        allowed.constraintFlags |= 0x200;
    else if (snapshot.powerState == PowerState::Sleep)
        allowed.constraintFlags |= 0x400;
    else if (snapshot.powerState == PowerState::Doze)
        allowed.constraintFlags |= 0x800;

    if (snapshot.psiLevel >= 4)
        allowed.constraintFlags |= 0x1000;
    else if (snapshot.psiLevel >= 2)
        allowed.constraintFlags |= 0x2000;

    // 2. 极端状态快速路径 (Early Exit)
    if (snapshot.powerState == PowerState::Suspend) {
        allowed.allowed.Clear();
        return allowed;
    }

    // 3. 预计算所有缩放因子
    allowed.thermalScaling = ComputeThermalScaling(snapshot);
    allowed.batteryScaling = ComputeBatteryScaling(snapshot);
    allowed.memoryScaling = ComputeMemoryScaling(snapshot);

    // 4. 构建维度相关的因子查找表 (LUT) - 消除循环内 if
    uint32_t psiFactors[kResourceDimCount] = {100, 100, 100, 100, 100, 100, 100, 100};
    if (snapshot.psiLevel >= 4) {
        psiFactors[static_cast<size_t>(ResourceDim::CpuCapacity)] = 40;
        psiFactors[static_cast<size_t>(ResourceDim::MemoryCapacity)] = 30;
        psiFactors[static_cast<size_t>(ResourceDim::StorageBandwidth)] = 30;
    } else if (snapshot.psiLevel >= 2) {
        psiFactors[static_cast<size_t>(ResourceDim::CpuCapacity)] = 70;
        psiFactors[static_cast<size_t>(ResourceDim::MemoryCapacity)] = 60;
        psiFactors[static_cast<size_t>(ResourceDim::StorageBandwidth)] = 60;
    }

    uint32_t memPressFactors[kResourceDimCount] = {1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000};
    if (allowed.memoryScaling < 1000) {
        memPressFactors[static_cast<size_t>(ResourceDim::MemoryCapacity)] = allowed.memoryScaling;
        memPressFactors[static_cast<size_t>(ResourceDim::MemoryBandwidth)] = allowed.memoryScaling;
    }

    // 硬裁剪因子
    const uint32_t screenFactor = (!snapshot.screenOn && applyScreenConstraint) ? 30 : 100;
    const uint32_t powerFactor = (snapshot.powerState == PowerState::Sleep)  ? 10
                                 : (snapshot.powerState == PowerState::Doze) ? 50
                                                                             : 100;

    const uint32_t tS = allowed.thermalScaling;
    const uint32_t bS = allowed.batteryScaling;

    // 5. 核心热路径：零分支循环
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        uint32_t v = 1024;

        // 顺序执行所有收缩，无条件分支判断
        v = (v * tS) / 1000;
        v = (v * bS) / 1000;
        v = (v * memPressFactors[i]) / 1000;
        v = (v * psiFactors[i]) / 100;
        v = (v * screenFactor) / 100;
        v = (v * powerFactor) / 100;

        allowed.allowed.v[i] = v;
    }

    return allowed;
}

uint32_t EnvelopeStage::ComputeThermalScaling(const ConstraintSnapshot &snapshot) {
    if (snapshot.thermalScaleQ10 > 0 && snapshot.thermalScaleQ10 < 1024) {
        return (snapshot.thermalScaleQ10 * 1000) / 1024;
    }
    if (snapshot.thermalSevere) {
        return 300;
    }

    if (snapshot.thermalLevel >= 5) {
        return 300;
    } else if (snapshot.thermalLevel >= 4) {
        return 500;
    } else if (snapshot.thermalLevel >= 2) {
        return 750;
    }

    return 1000;
}

uint32_t EnvelopeStage::ComputeBatteryScaling(const ConstraintSnapshot &snapshot) {
    if (snapshot.batteryScaleQ10 > 0 && snapshot.batteryScaleQ10 < 1024) {
        return (snapshot.batteryScaleQ10 * 1000) / 1024;
    }
    uint32_t scaling = 1000;

    if (snapshot.batterySaver) {
        scaling = scaling * 60 / 100;
    }

    if (snapshot.batteryLevel <= 10) {
        scaling = scaling * 70 / 100;
    } else if (snapshot.batteryLevel <= 20) {
        scaling = scaling * 85 / 100;
    }

    return scaling;
}

uint32_t EnvelopeStage::ComputeMemoryScaling(const ConstraintSnapshot &snapshot) {
    if (snapshot.psiScaleQ10 > 0 && snapshot.psiScaleQ10 < 1024) {
        return (snapshot.psiScaleQ10 * 1000) / 1024;
    }
    if (snapshot.memoryPressure >= 3) {
        return 400;
    } else if (snapshot.memoryPressure >= 2) {
        return 700;
    }

    return 1000;
}

void EnvelopeStage::ApplyScreenConstraint(const ConstraintSnapshot &snapshot,
                                          ConstraintAllowed &allowed) {
    if (!snapshot.screenOn) {
        for (size_t i = 0; i < kResourceDimCount; ++i) {
            allowed.allowed.v[i] = allowed.allowed.v[i] * 30 / 100;
        }
        allowed.constraintFlags |= 0x100;
    }
}

void EnvelopeStage::ApplyScreenConstraintForScene(const ConstraintSnapshot &snapshot,
                                                  const SceneSemantic &semantic,
                                                  const IntentContract &intent,
                                                  ConstraintAllowed &allowed) {
    if (snapshot.screenOn) {
        return;
    }

    const bool preserveContinuousChain =
        intent.level == IntentLevel::P2 && (semantic.audible || semantic.continuousPerception);
    if (!preserveContinuousChain) {
        ApplyScreenConstraint(snapshot, allowed);
        return;
    }

    allowed.allowed[ResourceDim::CpuCapacity] =
        std::max<uint32_t>(allowed.allowed[ResourceDim::CpuCapacity] * 50 / 100, 256);
    allowed.allowed[ResourceDim::MemoryCapacity] =
        std::max<uint32_t>(allowed.allowed[ResourceDim::MemoryCapacity] * 45 / 100, 128);
    allowed.allowed[ResourceDim::MemoryBandwidth] =
        std::max<uint32_t>(allowed.allowed[ResourceDim::MemoryBandwidth] * 55 / 100, 256);
    allowed.allowed[ResourceDim::GpuCapacity] =
        std::min<uint32_t>(allowed.allowed[ResourceDim::GpuCapacity] * 15 / 100, 128);
    allowed.allowed[ResourceDim::StorageBandwidth] =
        std::max<uint32_t>(allowed.allowed[ResourceDim::StorageBandwidth] * 45 / 100, 128);
    allowed.allowed[ResourceDim::StorageIops] =
        std::max<uint32_t>(allowed.allowed[ResourceDim::StorageIops] * 45 / 100, 128);
    allowed.allowed[ResourceDim::NetworkBandwidth] =
        std::max<uint32_t>(allowed.allowed[ResourceDim::NetworkBandwidth] * 55 / 100, 192);
    allowed.constraintFlags |= 0x100;
    allowed.constraintFlags |= 0x4000;
}

}    // namespace vendor::transsion::perfengine::dse
