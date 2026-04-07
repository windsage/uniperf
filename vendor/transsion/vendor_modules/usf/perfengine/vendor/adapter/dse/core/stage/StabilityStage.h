#pragma once

#include "StageBase.h"
#include "stability/StabilityMechanism.h"

namespace vendor::transsion::perfengine::dse {

class StabilityStage : public IStage {
public:
    const char *Name() const override { return "StabilityStage"; }

    StageOutput Execute(StageContext &ctx) override;

private:
    StabilityMechanism stability_;

    void RestoreFromVault(StageContext &ctx);
    /**
     * @brief 将 StabilityMechanism 的输出写回到阶段中间态（供 Orchestrator 原子提交）
     *
     * @param ctx 阶段上下文（用于访问 committed 稳定化状态以保持幂等）
     * @param output 稳定化状态机对本轮候选的评估结果
     * @param mergeCount 需要写入的 mergeCount（用于稳态/观察窗口的确定性衰减语义）
     * @param candidateChanged 候选语义是否发生变化（用于避免无效 lastUpdateMs 推进）
     */
    void UpdateVault(StageContext &ctx, const StabilityOutput &output, uint32_t mergeCount,
                     bool candidateChanged);
};

}    // namespace vendor::transsion::perfengine::dse
