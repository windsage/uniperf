#include "IntentInheritance.h"

namespace vendor::transsion::perfengine::dse {

IntentInheritance::IntentInheritance(const InheritancePolicy &policy) : policy_(policy) {}

bool IntentInheritance::ShouldTrigger(const InheritanceContext &ctx) const {
    if (ctx.requesterIntent != IntentLevel::P1 && ctx.requesterIntent != IntentLevel::P2) {
        return false;
    }

    if (ctx.requesterIntent == IntentLevel::P1 && !policy_.allowP1Inherit) {
        return false;
    }

    if (ctx.requesterIntent == IntentLevel::P2 && !policy_.allowP2Inherit) {
        return false;
    }

    if (static_cast<int>(ctx.holderOriginalIntent) <= static_cast<int>(ctx.requesterIntent)) {
        return false;
    }

    return true;
}

InheritanceContext IntentInheritance::Trigger(SceneId holderSceneId, SceneId requesterSceneId,
                                              IntentLevel holderOriginal,
                                              IntentLevel requesterIntent, int64_t currentTimeMs) {
    InheritanceContext ctx;
    ctx.holderSceneId = holderSceneId;
    ctx.requesterSceneId = requesterSceneId;
    ctx.holderOriginalIntent = holderOriginal;
    ctx.holderEffectiveIntent = requesterIntent;
    ctx.requesterIntent = requesterIntent;
    ctx.inheritStartTimeMs = currentTimeMs;
    ctx.inheritDurationMs = policy_.maxDurationMs;
    ctx.active = true;

    context_ = ctx;
    return ctx;
}

bool IntentInheritance::ShouldRevoke(const InheritanceContext &ctx, int64_t currentTimeMs) const {
    if (!ctx.active) {
        return false;
    }

    if (policy_.requireExplicitRelease) {
        return false;
    }

    int64_t elapsed = currentTimeMs - ctx.inheritStartTimeMs;
    return elapsed >= ctx.inheritDurationMs;
}

void IntentInheritance::Revoke(InheritanceContext &ctx) {
    ctx.active = false;
    ctx.holderEffectiveIntent = ctx.holderOriginalIntent;
    context_.active = false;
}

void IntentInheritance::RestoreFromDependencyState(const DependencyState &state) {
    context_.active = state.active;
    context_.holderSceneId = state.holderSceneId;
    context_.requesterSceneId = state.requesterSceneId;
    context_.holderOriginalIntent = state.holderOriginalIntent;
    context_.holderEffectiveIntent = state.inheritedIntent;
    context_.requesterIntent = state.inheritedIntent;
    context_.inheritStartTimeMs = state.inheritStartTimeMs;
    context_.inheritDurationMs = (state.inheritExpireTimeMs > state.inheritStartTimeMs)
                                     ? (state.inheritExpireTimeMs - state.inheritStartTimeMs)
                                     : policy_.maxDurationMs;
}

IntentLevel IntentInheritance::ComputeEffectiveIntent(IntentLevel original,
                                                      IntentLevel inherited) const {
    if (static_cast<int>(inherited) < static_cast<int>(original)) {
        return inherited;
    }
    return original;
}

bool IntentInheritance::CheckHierarchyDepth(uint32_t currentDepth) const {
    return currentDepth < policy_.maxHierarchyDepth;
}

}    // namespace vendor::transsion::perfengine::dse
