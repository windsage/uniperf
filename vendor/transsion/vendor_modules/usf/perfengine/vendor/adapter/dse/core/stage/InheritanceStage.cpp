#include "InheritanceStage.h"

#include "IntentStage.h"
#include "state/StateVault.h"

namespace vendor::transsion::perfengine::dse {
namespace {

DependencyState &PrepareWritableDependencyState(StageContext &ctx) {
    const bool hasLocalDependency = ctx.intermediates && ctx.intermediates->hasDependencyState;
    auto &dep = ctx.MutableDependencyState();
    if (!hasLocalDependency) {
        dep = ctx.state ? ctx.state->Dependency() : DependencyState{};
    }
    return dep;
}

}    // namespace

// InheritanceStage 同时处理三种情况：
// 1. 已有继承是否到期并撤销
// 2. 新依赖是否需要触发继承
// 3. 当前执行实体如果正是 holder，则把继承后的契约应用到自身上下文
StageOutput InheritanceStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.intermediates) {
        out.success = false;
        return out;
    }

    if (ctx.state) {
        const auto &depState = ctx.state->Dependency();
        inheritance_.RestoreFromDependencyState(depState);
    }

    int64_t deterministicTsNs = ctx.event ? ctx.event->timestamp : 0;
    constexpr int64_t kNsPerMs = 1000000LL;
    int64_t currentTsMs = deterministicTsNs / kNsPerMs;

    bool inheritanceApplied = false;
    bool dependencyUpdated = false;

    if (inheritance_.IsActive()) {
        if (inheritance_.ShouldRevoke(inheritance_.GetContext(), currentTsMs)) {
            // 撤销时只关闭 dependency active 状态，具体 intent 会在后续阶段按正常语义重新收敛。
            InheritanceContext inheritCtx = inheritance_.GetContext();
            inheritance_.Revoke(inheritCtx);

            auto &dep = PrepareWritableDependencyState(ctx);
            dep.active = false;
            dep.inheritExpireTimeMs = currentTsMs;
            dependencyUpdated = true;
        }
    }

    if (CheckInheritanceNeeded(ctx)) {
        auto &dep = PrepareWritableDependencyState(ctx);

        InheritanceContext inheritCtx =
            inheritance_.Trigger(dep.holderSceneId, dep.requesterSceneId, dep.holderOriginalIntent,
                                 ctx.GetIntentContract().level, currentTsMs);

        dep.active = true;
        dep.inheritedIntent = inheritCtx.holderEffectiveIntent;
        dep.inheritStartTimeMs = currentTsMs;
        dep.inheritExpireTimeMs = currentTsMs + inheritCtx.inheritDurationMs;
        dependencyUpdated = true;

        const SceneId currentSceneId = ctx.GetResolvedSceneId();
        if (currentSceneId != 0 && currentSceneId == dep.holderSceneId) {
            // 只有真正的 holder 才会在本轮被提权；requester 仅触发依赖状态更新。
            inheritanceApplied = ApplyInheritance(ctx, inheritCtx);
        }
    } else if (inheritance_.IsActive() && ctx.state) {
        const auto &dep = ctx.state->Dependency();
        const SceneId currentSceneId = ctx.GetResolvedSceneId();
        if (dep.active && currentSceneId != 0 && currentSceneId == dep.holderSceneId) {
            inheritanceApplied = ApplyInheritance(ctx, inheritance_.GetContext());
        }
    }

    out.success = true;
    if (inheritanceApplied || dependencyUpdated) {
        out.dirtyBits.SetDependency();
    }
    if (inheritanceApplied) {
        out.dirtyBits.SetIntent();
        out.dirtyBits.SetLease();
    }
    return out;
}

// 继承判定必须建立在显式依赖状态之上，不能从当前 event 自行推测 holder/requester。
bool InheritanceStage::CheckInheritanceNeeded(const StageContext &ctx) const {
    if (!ctx.intermediates || !ctx.intermediates->hasDependencyState) {
        return false;
    }

    const auto &dep = ctx.intermediates->dependencyState;
    if (!dep.active)
        return false;
    if (!inheritance_.CheckHierarchyDepth(dep.depth))
        return false;

    InheritanceContext testCtx;
    testCtx.holderOriginalIntent = dep.holderOriginalIntent;
    testCtx.requesterIntent = ctx.GetIntentContract().level;
    testCtx.active = true;

    return inheritance_.ShouldTrigger(testCtx);
}

// ApplyInheritance 会同步更新三类信息：
// - intentContract：提升优先级语义
// - resourceRequest：重算多资源请求
// - temporalContract：重算 lease/time mode，避免 intent 与时间契约失配
bool InheritanceStage::ApplyInheritance(StageContext &ctx, const InheritanceContext &inheritCtx) {
    if (!inheritCtx.active || !ctx.intermediates) {
        return false;
    }

    const IntentContract &currentIntent = ctx.GetIntentContract();
    IntentContract nextIntent = currentIntent;
    nextIntent.level =
        inheritance_.ComputeEffectiveIntent(currentIntent.level, inheritCtx.holderEffectiveIntent);

    const auto &semantic = ctx.GetSceneSemantic();
    if (semantic.continuousPerception &&
        static_cast<int>(nextIntent.level) > static_cast<int>(IntentLevel::P2)) {
        // 连续感知场景下，即使继承来源更低，也不能把有效 intent 压到 P2 以下。
        nextIntent.level = IntentLevel::P2;
    }

    if (IntentContractEquals(nextIntent, currentIntent)) {
        // 继承生效但没有改变有效契约时，不重复续写 intent/lease/request。
        return false;
    }

    auto &loader = ctx.GetConfigLoader();
    const auto &params = loader.GetParams();
    auto &intentContract = ctx.MutableIntentContract();
    intentContract = nextIntent;
    auto &request = ctx.MutableResourceRequest();
    IntentStage::FillResourceRequest(request, semantic, intentContract, ctx.profileResourceMask,
                                     loader, params);
    const int64_t deterministicTs = ctx.event ? static_cast<int64_t>(ctx.event->timestamp) : 0;
    auto &temporalContract = ctx.MutableTemporalContract();
    IntentStage::FillTemporalContract(temporalContract, semantic, intentContract, deterministicTs);
    return true;
}

}    // namespace vendor::transsion::perfengine::dse
