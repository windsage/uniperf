#include "ArbitrationStage.h"

#include <utility>

namespace vendor::transsion::perfengine::dse {

StageOutput ArbitrationStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.intermediates || !ctx.convergeIntermediates) {
        out.success = false;
        return out;
    }

    const auto &request = ctx.GetResourceRequest();
    const auto &constraintAllowed = ctx.GetConstraintAllowed();
    const auto &capabilityFeasible = ctx.GetCapabilityFeasible();
    const auto &intentContract = ctx.GetIntentContract();

    // 仲裁层按“请求 -> 约束 -> 能力 -> 意图”的固定顺序裁剪，确保相同输入稳定得到相同输出。
    // 该阶段只产生 ConvergeFinalizeStage
    // 真正消费的最小中间结果（delivered/grantedMask/reasonBits）。
    auto &ruleTable = ctx.GetRuleTable();
    auto layers = ComputeArbitrationLayers(request, constraintAllowed, capabilityFeasible,
                                           intentContract.level,
                                           [&ruleTable](ResourceDim dim) -> const ResourceRule * {
                                               return ruleTable.GetResourceRule(dim);
                                           });

    ArbitrationResult result;
    result.grantedMask = layers.grantedMask;
    result.reasonBits = layers.ruleReasonBits;
    result.delivered = layers.delivered;

    // ArbitrationResult 是收敛路径 finalize 的唯一仲裁输入，不在此阶段引入平台特化。
    ctx.SetArbitrationResult(result);

    out.success = true;
    return out;
}

}    // namespace vendor::transsion::perfengine::dse
