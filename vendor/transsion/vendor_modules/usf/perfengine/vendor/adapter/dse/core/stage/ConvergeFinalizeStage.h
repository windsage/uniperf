#pragma once

#include "StageBase.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @class ConvergeFinalizeStage
 * @brief 将收敛路径仲裁结果封装成完整 PolicyDecision
 *
 * 该阶段负责把 ArbitrationStage 产出的 delivered 结果转成框架统一决策对象，
 * 补齐 session、temporal、meta 等执行与追踪所需信息，并保持与快路径不同的
 * “完整收敛”语义。
 */
class ConvergeFinalizeStage : public IStage {
public:
    const char *Name() const override { return "ConvergeFinalizeStage"; }

    /**
     * @brief 执行收敛路径收尾
     * @param ctx 阶段上下文
     * @return StageOutput 阶段执行结果；成功时写回 PolicyDecision
     */
    StageOutput Execute(StageContext &ctx) override;

private:
    /**
     * @brief 把仲裁结果原地写入最终资源授权
     * @param arbitration 仲裁阶段输出
     * @param grant 决策中的最终资源授权
     */
    void BuildGrantFromArbitration(const ArbitrationResult &arbitration,
                                   ResourceGrant &grant) const;

    /**
     * @brief 把授权结果与契约元数据封装为完整决策
     * @param grant 最终资源授权
     * @param intent 当前有效意图契约
     * @param ctx 阶段上下文
     */
    void BuildDecision(const IntentContract &intent, StageContext &ctx,
                       PolicyDecision &decision) const;

    /**
     * @brief 记录收敛路径决策 Trace 与 ReplayHash
     * @param ctx 阶段上下文
     * @param decision 最终策略决策
     */
    void RecordDecisionTrace(const StageContext &ctx, const PolicyDecision &decision) const;
};

}    // namespace vendor::transsion::perfengine::dse
