#include "ConvergeFinalizeStage.h"

#include "core/state/StateVault.h"

namespace vendor::transsion::perfengine::dse {

StageOutput ConvergeFinalizeStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.intermediates || !ctx.convergeIntermediates) {
        out.success = false;
        return out;
    }

    const auto &arbitration = ctx.GetArbitrationResult();
    const auto &intentContract = ctx.GetIntentContract();

    // 收敛路径先把仲裁 delivered 收束成 ResourceGrant，再补齐完整决策元数据。
    auto &decision = ctx.MutableDecision();
    BuildGrantFromArbitration(arbitration, decision.grant);
    BuildDecision(intentContract, ctx, decision);
    ctx.convergeIntermediates->hasDecision = true;

    // 记录 trace 供执行异常、回放比对和规则变更审计使用。
    RecordDecisionTrace(ctx, decision);

    out.success = true;
    return out;
}

void ConvergeFinalizeStage::BuildGrantFromArbitration(const ArbitrationResult &arbitration,
                                                      ResourceGrant &grant) const {
    grant.resourceMask = 0;
    grant.finalizeReasonBits = 0;

    // finalize 阶段直接复用 ArbitrationStage 已经固定好的授权位和规则原因位，
    // 避免对同一 delivered 做第二次映射。
    grant.resourceMask = arbitration.grantedMask;
    grant.finalizeReasonBits = arbitration.reasonBits;
    grant.delivered = arbitration.delivered;
}

void ConvergeFinalizeStage::BuildDecision(const IntentContract &intent, StageContext &ctx,
                                          PolicyDecision &decision) const {
    // 收敛路径决策必须绑定稳定 session，用于执行、回收与跨阶段 trace 关联。
    // Prefer orchestration-level resolved session binding to avoid repeated parsing.
    decision.sessionId = ctx.GetResolvedSessionId();

    decision.reasonBits = decision.grant.finalizeReasonBits;
    decision.rejectReasons = 0;
    decision.effectiveIntent = intent.level;
    decision.temporal = ctx.GetTemporalContract();
    decision.request = ctx.GetResourceRequest();
    // admitted 表示至少有一个资源维度被接受；degraded 则表示发生了裁剪或降配。
    decision.admitted = (decision.grant.resourceMask != 0);
    decision.degraded = (decision.grant.finalizeReasonBits != 0);
    decision.executionSignature = 0;
    decision.executionSummary = 0;

    // meta 固定绑定快照、规则版本与阶段指纹，保证决策具备可重放和可归因属性。
    decision.meta = DecisionMeta::CreateFromSnapshotToken(ctx.GetSnapshotToken(), ctx.profileId,
                                                          ctx.ruleVersion, ctx.stageMask);

    // 轻量摘要供执行流去重首判；完整签名仅对具备去重资格的决策计算（commit.md 1.3）。
    decision.executionSummary = ComputeExecutionSummary(decision);
    if (decision.IsDedupCandidate()) {
        decision.executionSignature = ComputeExecutionSignature(decision);
    }
}

void ConvergeFinalizeStage::RecordDecisionTrace(const StageContext &ctx,
                                                const PolicyDecision &decision) const {
    auto &tracer = ctx.GetTraceRecorder();
    if (!tracer.IsReplayHashTraceEnabled() || !ctx.event) {
        return;
    }
    // 回放哈希覆盖输入事件、状态快照和最终决策，便于规则升级后的结果一致性校验。
    static const ConstraintSnapshot kEmptyConstraint{};
    const ConstraintSnapshot &constraint = (ctx.state != nullptr) ? ctx.state->Constraint().snapshot
                                                                  : kEmptyConstraint;
    const CapabilityFeasible &capability = ctx.GetCapabilityFeasible();
    // P3：避免在 replay-hash 场景下对候选决策重复计算 decision.Hash()。
    // executionSignature 预计算时已经包含决策哈希中“昂贵向量部分”与必要的前缀字段，
    // 对于去重候选决策可直接补齐 degraded/meta 得到与 decision.Hash() 一致的结果。
    uint64_t decisionHash = 0;
    if (decision.IsDedupCandidate()) {
        decisionHash = HashCombine(
            HashCombine(decision.executionSignature, static_cast<uint64_t>(decision.degraded)),
            decision.meta.Hash());
    } else {
        decisionHash = ComputeDecisionHash(decision);
    }
    const uint64_t stateHash = tracer.ComputeStateHash(constraint, capability);
    ReplayHashInputs replayInputs;
    const ReplayHash replayHash =
        ctx.hasPrecomputedInputHash
            ? tracer.BuildReplayHashFromInputs(decisionHash, ctx.precomputedInputHash, stateHash,
                                               static_cast<int64_t>(ctx.event->timestamp),
                                               ctx.generation, ctx.snapshotToken,
                                               decision.sessionId, ctx.profileId, ctx.ruleVersion,
                                               decision.effectiveIntent, &replayInputs)
            : tracer.BuildReplayHash(decisionHash, *ctx.event, constraint, capability,
                                     ctx.generation, ctx.snapshotToken, decision.sessionId,
                                     ctx.profileId, ctx.ruleVersion, decision.effectiveIntent,
                                     &replayInputs);
    (void)tracer.RecordReplayHashChecked(replayHash, decisionHash, replayInputs, *ctx.event);
}

}    // namespace vendor::transsion::perfengine::dse
