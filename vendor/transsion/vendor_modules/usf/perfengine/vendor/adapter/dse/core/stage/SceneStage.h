#pragma once

#include <cstdint>

#include "StageBase.h"

namespace vendor::transsion::perfengine::dse {

class SceneStage : public IStage {
public:
    const char *Name() const override { return "SceneStage"; }

    StageOutput Execute(StageContext &ctx) override;

private:
    SceneKind ClassifySceneKind(const SchedEvent &event) const;
    ScenePhase ClassifyScenePhase(const SchedEvent &event, SceneKind kind) const;
    bool InferVisibility(const SchedEvent &event, SceneKind kind) const;
    bool InferAudibility(const SchedEvent &event, SceneKind kind) const;
    bool InferContinuousPerception(const SchedEvent &event, const SceneSemantic &semantic,
                                   const SceneRule *rule) const;

    /**
     * @brief 从事件推导场景语义
     * @param event 调度事件
     * @return 场景语义结构
     */
    SceneSemantic DeriveSceneSemantic(const SchedEvent &event, SceneKind kind, ScenePhase phase,
                                      const SceneRule *rule) const;

    /**
     * @brief 从之前的状态恢复场景语义
     * @param prevSceneState 之前的场景状态
     * @return 恢复的场景语义结构
     *
     * 用于场景退出时恢复之前的场景状态，
     * 支持"100% 重放一致性"的准出条件。
     *
     * @note 遵循框架 §5 状态管理原则
     */
    SceneSemantic RestorePreviousSceneSemantic(const SceneState &prevSceneState) const;

    static constexpr uint32_t kActionLaunch = 1;
    static constexpr uint32_t kActionTransition = 2;
    static constexpr uint32_t kActionScroll = 3;
    static constexpr uint32_t kActionAnimation = 4;
    static constexpr uint32_t kActionGaming = 5;
    static constexpr uint32_t kActionCamera = 6;
    static constexpr uint32_t kActionPlayback = 7;
    static constexpr uint32_t kActionDownload = 8;
    static constexpr uint32_t kActionBackground = 9;
};

}    // namespace vendor::transsion::perfengine::dse
