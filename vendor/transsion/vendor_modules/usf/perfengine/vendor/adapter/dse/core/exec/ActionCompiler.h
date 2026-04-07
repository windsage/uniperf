#pragma once

#include <cstddef>
#include <cstdint>

#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

inline uint16_t EncodeActionFlags(bool admitted, ActionContractMode contractMode,
                                  IntentLevel intentLevel, TimeMode timeMode) noexcept {
    uint16_t flags = admitted ? 0x01u : 0x00u;
    flags |= (static_cast<uint16_t>(contractMode) & 0x07u) << 1;
    flags |= (static_cast<uint16_t>(intentLevel) & 0x07u) << 4;
    flags |= (static_cast<uint16_t>(timeMode) & 0x0Fu) << 8;
    return flags;
}

inline ActionContractMode DecodeActionContractMode(uint16_t flags) noexcept {
    return static_cast<ActionContractMode>((flags >> 1) & 0x07u);
}

inline IntentLevel DecodeActionIntentLevel(uint16_t flags) noexcept {
    return static_cast<IntentLevel>((flags >> 4) & 0x07u);
}

inline TimeMode DecodeActionTimeMode(uint16_t flags) noexcept {
    return static_cast<TimeMode>((flags >> 8) & 0x0Fu);
}

/**
 * @struct AbstractAction
 * @brief 调度决策向平台映射前的中间动作表达。
 *
 * 它只保留下发层真正消费的最小语义：
 * - actionKey：资源维度
 * - value：本轮授权值
 * - flags：admitted / contractMode / intentLevel / timeMode 的紧凑编码
 */
struct AbstractAction {
    uint16_t actionKey = 0;
    uint16_t flags = 0;
    uint32_t value = 0;
};

/**
 * @struct AbstractActionBatch
 * @brief 一次决策对应的抽象动作批次。
 *
 * 平台动作映射器会基于这个批次选择平台路径并生成具体命令。
 */
struct AbstractActionBatch {
    uint32_t actionCount = 0;
    uint32_t reasonBits = 0;
    static constexpr size_t kMaxActions = kResourceDimCount;
    AbstractAction actions[kMaxActions];
};

/**
 * @class ActionCompiler
 * @brief 将 PolicyDecision 编译为抽象动作批次。
 *
 * 该阶段不直接生成平台命令，只把执行层仍会消费的最小契约语义压缩到 batch 中，
 * 避免在热路径上搬运无人读取的 floor/target/cap 边界与调试字段。
 */
template <typename ProfileSpec>
class ActionCompiler {
public:
    /**
     * @brief 编译策略决策。
     * @param decision 精修路径输出的策略决策。
     * @return 抽象动作批次。
     */
    AbstractActionBatch Compile(const PolicyDecision &decision) {
        AbstractActionBatch batch;
        batch.actionCount = 0;
        batch.reasonBits = decision.reasonBits;

        if (decision.grant.resourceMask == 0) {
            // 无授权维度时直接返回空批次，让上层进入显式 fallback。
            return batch;
        }

        const ActionContractMode contractMode = DeriveActionContractMode(decision.effectiveIntent);
        const uint16_t encodedFlags = EncodeActionFlags(
            decision.admitted, contractMode, decision.effectiveIntent, decision.temporal.timeMode);

        for (size_t dim = 0; dim < kResourceDimCount; ++dim) {
            uint32_t dimMask = 1 << dim;
            if ((decision.grant.resourceMask & dimMask) == 0) {
                continue;
            }

            if (batch.actionCount >= batch.kMaxActions) {
                break;
            }

            CompileDimensionAction(static_cast<ResourceDim>(dim), decision, encodedFlags, batch);
        }

        return batch;
    }

private:
    // 每个资源维度生成一个紧凑动作，只保留下发真正需要的维度、授权值和编码 flags。
    void CompileDimensionAction(ResourceDim dim, const PolicyDecision &decision,
                                uint16_t encodedFlags, AbstractActionBatch &batch) {
        auto &action = batch.actions[batch.actionCount++];
        const auto idx = static_cast<size_t>(dim);
        action = AbstractAction{};
        action.actionKey = static_cast<uint16_t>(dim);
        action.flags = encodedFlags;
        action.value = decision.grant.delivered.v[idx];
    }
};

}    // namespace vendor::transsion::perfengine::dse
