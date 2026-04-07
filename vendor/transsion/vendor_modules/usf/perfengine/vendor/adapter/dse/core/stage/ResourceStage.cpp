#include "ResourceStage.h"

#include "state/StateVault.h"

namespace vendor::transsion::perfengine::dse {

namespace {
// rawFlags 只表达“疑似存在依赖”，真正的 holder/requester 关系必须由 hint 明示。
constexpr uint32_t kReasonDependencyMetadataMissing = 0x00010000;
constexpr uint32_t kReasonDependencyMetadataInvalid = 0x00020000;

// 解析显式依赖元数据。若 hint 不合法，后续必须显式降级而不是伪造依赖边。
const DependencyHintPayload *ParseDependencyHint(const SchedEvent &event) {
    if (!event.hint || event.hintSize < sizeof(DependencyHintPayload)) {
        return nullptr;
    }
    const auto *hint = static_cast<const DependencyHintPayload *>(event.hint);
    if (hint->version != 1) {
        return nullptr;
    }
    return hint;
}
}    // namespace

// ResourceStage 只负责显式依赖建模：
// 1. 从输入事件恢复真实 holder/requester 元数据
// 2. 把依赖关系转换成 DependencyState，供继承阶段使用
// 能力边界统一由 EnvelopeStage 单点生成，避免同一轮对 CapabilitySnapshot 做重复整理。
StageOutput ResourceStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.state || !ctx.intermediates) {
        out.success = false;
        return out;
    }

    out.reasonBits = DetectDependency(ctx);

    out.success = true;
    if (ctx.intermediates->hasDependencyState) {
        out.dirtyBits.SetDependency();
    }
    return out;
}

// DetectDependency 的核心原则是“宁可显式失败，也不伪造依赖边”。
// 这符合框架对确定性和可解释性的要求：继承必须建立在真实 holder/requester 上。
uint32_t ResourceStage::DetectDependency(StageContext &ctx) {
    if (!ctx.event)
        return false;

    const auto &semantic = ctx.GetSceneSemantic();
    bool hasDependency = false;
    DependencyKind depKind = DependencyKind::None;

    if (semantic.kind == SceneKind::Gaming || semantic.kind == SceneKind::Camera ||
        semantic.kind == SceneKind::Playback || semantic.kind == SceneKind::Launch ||
        semantic.kind == SceneKind::Transition || semantic.kind == SceneKind::Scroll ||
        semantic.kind == SceneKind::Animation) {
        if (ctx.event->rawFlags & ResourceStage::kRawFlagHasLockDependency) {
            hasDependency = true;
            depKind = DependencyKind::Lock;
        } else if (ctx.event->rawFlags & ResourceStage::kRawFlagHasBinderDependency) {
            hasDependency = true;
            depKind = DependencyKind::Binder;
        }
    }

    if (!hasDependency) {
        return 0;
    }

    const auto *hint = ParseDependencyHint(*ctx.event);
    if (!hint) {
        // 有依赖标志但没有元数据时，只记录原因码，供 trace / review 使用。
        return kReasonDependencyMetadataMissing;
    }

    if (hint->holderSceneId == 0 || hint->requesterSceneId == 0 ||
        hint->holderSceneId == hint->requesterSceneId) {
        return kReasonDependencyMetadataInvalid;
    }

    DependencyState dep;
    dep.holderSceneId = hint->holderSceneId;
    dep.requesterSceneId = hint->requesterSceneId;
    dep.holderOriginalIntent = hint->holderOriginalIntent;
    dep.kind = (hint->kind != DependencyKind::None) ? hint->kind : depKind;
    dep.depth = (hint->depth == 0) ? 1 : hint->depth;
    dep.inheritedIntent = ctx.GetIntentContract().level;
    dep.inheritStartTimeMs = static_cast<int64_t>(ctx.event->timestamp / 1000000);
    dep.inheritExpireTimeMs = dep.inheritStartTimeMs + 2000;
    dep.active = true;

    if (ctx.state) {
        // 如果持有者恰好就是当前活跃场景，则优先使用状态中的原始 intent，避免 hint 过期。
        const auto &prevScene = ctx.state->Scene();
        const auto &prevIntent = ctx.state->Intent();
        if (prevScene.activeSceneId == dep.holderSceneId) {
            dep.holderOriginalIntent = prevIntent.contract.level;
        }
    }

    ctx.SetDependencyState(dep);
    return 0;
}

}    // namespace vendor::transsion::perfengine::dse
