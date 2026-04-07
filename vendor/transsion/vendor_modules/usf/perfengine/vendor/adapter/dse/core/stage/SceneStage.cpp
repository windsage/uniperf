#include "SceneStage.h"

#include "core/state/StateVault.h"

namespace vendor::transsion::perfengine::dse {

bool SceneStage::InferContinuousPerception(const SchedEvent &event, const SceneSemantic &semantic,
                                           const SceneRule *rule) const {
    (void)event;
    SceneKind kind = semantic.kind;
    if (rule) {
        if (rule->continuousPerception) {
            return true;
        }
        if (kind == SceneKind::Scroll || kind == SceneKind::Animation) {
            return false;
        }
    }

    switch (kind) {
        case SceneKind::Gaming:
            return semantic.phase == ScenePhase::Active || semantic.phase == ScenePhase::Enter;
        case SceneKind::Playback:
            return semantic.audible || semantic.visible;
        case SceneKind::Camera:
            return semantic.visible && semantic.phase == ScenePhase::Active;
        case SceneKind::Background:
            return semantic.audible;
        case SceneKind::Scroll:
        case SceneKind::Animation:
        case SceneKind::Launch:
        case SceneKind::Transition:
        case SceneKind::Download:
        case SceneKind::Unknown:
        default:
            return false;
    }
}

StageOutput SceneStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.event) {
        out.success = false;
        return out;
    }

    const SceneKind kind = ClassifySceneKind(*ctx.event);
    ScenePhase phase = ClassifyScenePhase(*ctx.event, kind);
    SceneSemantic semantic;
    const SceneRule *rule = nullptr;
    auto &loader = ctx.GetConfigLoader();

    if (phase == ScenePhase::Exit && ctx.state) {
        const auto &prevSceneState = ctx.state->Scene();
        if (prevSceneState.activeSceneId != 0) {
            semantic = RestorePreviousSceneSemantic(prevSceneState);
        } else {
            rule = loader.FindSceneRuleByKind(kind);
            semantic = DeriveSceneSemantic(*ctx.event, kind, phase, rule);
        }
    } else {
        rule = loader.FindSceneRuleByKind(kind);
        semantic = DeriveSceneSemantic(*ctx.event, kind, phase, rule);
    }

    if (semantic.kind != kind) {
        rule = loader.FindSceneRuleByKind(semantic.kind);
    }
    ctx.SetSceneSemantic(semantic);
    ctx.SetSceneRule(rule);

    out.success = true;
    out.dirtyBits.SetScene();
    return out;
}

SceneSemantic SceneStage::DeriveSceneSemantic(const SchedEvent &event, SceneKind kind,
                                              ScenePhase phase, const SceneRule *rule) const {
    SceneSemantic semantic;
    semantic.kind = kind;
    semantic.phase = phase;
    semantic.visible = InferVisibility(event, kind);
    semantic.audible = InferAudibility(event, kind);
    semantic.continuousPerception = InferContinuousPerception(event, semantic, rule);
    return semantic;
}

SceneSemantic SceneStage::RestorePreviousSceneSemantic(const SceneState &prevSceneState) const {
    SceneSemantic semantic = prevSceneState.semantic;
    semantic.phase = ScenePhase::Active;
    return semantic;
}

SceneKind SceneStage::ClassifySceneKind(const SchedEvent &event) const {
    switch (event.action) {
        case kActionLaunch:
            return SceneKind::Launch;
        case kActionTransition:
            return SceneKind::Transition;
        case kActionScroll:
            return SceneKind::Scroll;
        case kActionAnimation:
            return SceneKind::Animation;
        case kActionGaming:
            return SceneKind::Gaming;
        case kActionCamera:
            return SceneKind::Camera;
        case kActionPlayback:
            return SceneKind::Playback;
        case kActionDownload:
            return SceneKind::Download;
        case kActionBackground:
            return SceneKind::Background;
        default:
            return SceneKind::Unknown;
    }
}

ScenePhase SceneStage::ClassifyScenePhase(const SchedEvent &event, SceneKind kind) const {
    uint32_t phaseFlags = event.rawFlags & 0x0F;
    switch (phaseFlags) {
        case 1:
            return ScenePhase::Enter;
        case 2:
            return ScenePhase::Active;
        case 3:
            return ScenePhase::Exit;
        default:
            break;
    }

    switch (kind) {
        case SceneKind::Launch:
        case SceneKind::Transition:
        case SceneKind::Scroll:
        case SceneKind::Animation:
        case SceneKind::Camera:
            return ScenePhase::Enter;
        case SceneKind::Gaming:
        case SceneKind::Playback:
        case SceneKind::Download:
        case SceneKind::Background:
            return ScenePhase::Active;
        case SceneKind::Unknown:
        default:
            return ScenePhase::None;
    }
}

bool SceneStage::InferVisibility(const SchedEvent &event, SceneKind kind) const {
    if ((event.rawFlags & 0x10) != 0) {
        return true;
    }

    switch (kind) {
        case SceneKind::Launch:
        case SceneKind::Transition:
        case SceneKind::Scroll:
        case SceneKind::Animation:
        case SceneKind::Gaming:
        case SceneKind::Camera:
            return true;
        case SceneKind::Playback:
            return (event.rawFlags & 0x20) == 0;
        case SceneKind::Download:
        case SceneKind::Background:
        case SceneKind::Unknown:
        default:
            return false;
    }
}

bool SceneStage::InferAudibility(const SchedEvent &event, SceneKind kind) const {
    if ((event.rawFlags & 0x20) != 0) {
        return true;
    }

    switch (kind) {
        case SceneKind::Playback:
            return true;
        case SceneKind::Gaming:
        case SceneKind::Camera:
        case SceneKind::Launch:
        case SceneKind::Transition:
        case SceneKind::Scroll:
        case SceneKind::Animation:
        case SceneKind::Download:
        case SceneKind::Background:
        case SceneKind::Unknown:
        default:
            return false;
    }
}

}    // namespace vendor::transsion::perfengine::dse
