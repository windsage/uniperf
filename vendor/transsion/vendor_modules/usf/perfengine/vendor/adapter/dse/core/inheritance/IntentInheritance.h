#pragma once

// 意图继承：触发/撤销/时长/层级边界；与 DependencyState 协同（任务清单 §11.3）。

#include <cstdint>

#include "dse/Types.h"
#include "state/StateDomains.h"

namespace vendor::transsion::perfengine::dse {

struct InheritanceContext {
    SceneId holderSceneId = 0;
    SceneId requesterSceneId = 0;
    IntentLevel holderOriginalIntent = IntentLevel::P3;
    IntentLevel holderEffectiveIntent = IntentLevel::P3;
    IntentLevel requesterIntent = IntentLevel::P1;
    int64_t inheritStartTimeMs = 0;
    int64_t inheritDurationMs = 0;
    bool active = false;
};

struct InheritancePolicy {
    int64_t maxDurationMs = 2000;
    uint32_t maxHierarchyDepth = 2;
    bool allowP1Inherit = true;
    bool allowP2Inherit = true;
    bool requireExplicitRelease = false;
};

class IntentInheritance {
public:
    explicit IntentInheritance(const InheritancePolicy &policy = InheritancePolicy{});

    bool ShouldTrigger(const InheritanceContext &ctx) const;

    InheritanceContext Trigger(SceneId holderSceneId, SceneId requesterSceneId,
                               IntentLevel holderOriginal, IntentLevel requesterIntent,
                               int64_t currentTimeMs);

    bool ShouldRevoke(const InheritanceContext &ctx, int64_t currentTimeMs) const;

    void Revoke(InheritanceContext &ctx);

    IntentLevel ComputeEffectiveIntent(IntentLevel original, IntentLevel inherited) const;

    bool CheckHierarchyDepth(uint32_t currentDepth) const;

    bool IsActive() const { return context_.active; }
    const InheritanceContext &GetContext() const { return context_; }

    void RestoreFromDependencyState(const DependencyState &state);

private:
    InheritancePolicy policy_;
    InheritanceContext context_;
};

}    // namespace vendor::transsion::perfengine::dse
