#include "FastFinalizeStage.h"

#include <algorithm>

#include "config/RuleTable.h"
#include "core/state/StateVault.h"

namespace vendor::transsion::perfengine::dse {

StageOutput FastFinalizeStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.intermediates || !ctx.fastIntermediates) {
        out.success = false;
        return out;
    }

    const auto &request = ctx.GetResourceRequest();
    const auto &constraintAllowed = ctx.GetConstraintAllowed();
    const auto &capabilityFeasible = ctx.GetCapabilityFeasible();
    const auto &intentContract = ctx.GetIntentContract();

    // 快路径仍沿用与收敛路径相同的裁剪顺序，避免两条路径在同一输入上出现不可解释的分叉。
    auto &grant = ctx.MutableFastGrant();
    ComputeFastGrant(request, constraintAllowed, capabilityFeasible, intentContract.level,
                     ctx.GetRuleTable(), grant);

    // FastGrant 必须绑定稳定的 sessionId，后续 lease 回收和 trace 对账都依赖该标识。
    grant.sessionId = ctx.GetResolvedSessionId();
    ctx.fastIntermediates->hasFastGrant = true;

    // 快路径同样保留决策追踪，便于比较首拍授权与收敛稳态决策之间的差异。
    RecordFastGrantTrace(ctx, grant);

    out.success = true;
    return out;
}

void FastFinalizeStage::ComputeFastGrant(const ResourceRequest &request,
                                         const ConstraintAllowed &constraintAllowed,
                                         const CapabilityFeasible &capabilityFeasible,
                                         IntentLevel intentLevel, RuleTable &ruleTable,
                                         FastGrant &grant) const {
    grant.grantedMask = 0;
    grant.delivered.Clear();
    grant.reasonBits = 0;
    grant.leaseMs = 150;

    // 复用统一仲裁算法，避免快路径和收敛路径出现两套 delivered 语义。
    auto layers =
        ComputeArbitrationLayers(request, constraintAllowed, capabilityFeasible, intentLevel,
                                 [&ruleTable](ResourceDim dim) -> const ResourceRule * {
                                     return ruleTable.GetResourceRule(dim);
                                 });

    grant.delivered = layers.delivered;
    grant.grantedMask = layers.grantedMask;
    grant.reasonBits = layers.ruleReasonBits;
    if (request.resourceMask != 0) {
        // 受控短租约既保证 P1 首响应，也限制错误授权的放大窗口。
        const uint32_t preferredLeaseMs = (intentLevel == IntentLevel::P1)   ? 800u
                                          : (intentLevel == IntentLevel::P2) ? 500u
                                                                             : 200u;
        grant.leaseMs = std::min<uint32_t>(2000u, std::max<uint32_t>(100u, preferredLeaseMs));
    }
}

void FastFinalizeStage::RecordFastGrantTrace(const StageContext &ctx,
                                             const FastGrant &grant) const {
    auto &tracer = ctx.GetTraceRecorder();
    if (!tracer.IsReplayHashTraceEnabled() || !ctx.event) {
        return;
    }
    // ReplayHash 把输入、状态和决策压缩成哈希，便于验证同输入/同快照下结果稳定。
    static const ConstraintSnapshot kEmptyConstraint{};
    const ConstraintSnapshot &constraint = (ctx.state != nullptr) ? ctx.state->Constraint().snapshot
                                                                  : kEmptyConstraint;
    const CapabilityFeasible &capability = ctx.GetCapabilityFeasible();
    const uint64_t decisionHash = ComputeFastGrantHash(grant);
    const uint64_t stateHash = tracer.ComputeStateHash(constraint, capability);
    ReplayHashInputs replayInputs;
    const ReplayHash replayHash =
        ctx.hasPrecomputedInputHash
            ? tracer.BuildReplayHashFromInputs(decisionHash, ctx.precomputedInputHash, stateHash,
                                               static_cast<int64_t>(ctx.event->timestamp),
                                               ctx.generation, ctx.snapshotToken, grant.sessionId,
                                               ctx.profileId, ctx.ruleVersion,
                                               ctx.GetIntentContract().level, &replayInputs)
            : tracer.BuildReplayHash(decisionHash, *ctx.event, constraint, capability,
                                     ctx.generation, ctx.snapshotToken, grant.sessionId,
                                     ctx.profileId, ctx.ruleVersion, ctx.GetIntentContract().level,
                                     &replayInputs);
    (void)tracer.RecordReplayHashChecked(replayHash, decisionHash, replayInputs, *ctx.event);
}

}    // namespace vendor::transsion::perfengine::dse
