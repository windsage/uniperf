#pragma once

#include "StageBase.h"
#include "config/ConfigLoader.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @struct IntentStageOutput
 * @brief IntentStage 的聚合输出。
 *
 * 该结构主要用于表达 IntentStage 会同时生成三类互相关联的契约：
 * - IntentContract：优先级语义
 * - TemporalContract：时间模式与租约
 * - ResourceRequest：多资源请求向量
 */
struct IntentStageOutput : StageOutput {
    IntentContract intentContract;
    TemporalContract temporalContract;
    ResourceRequest resourceRequest;
};

/**
 * @class IntentStage
 * @brief 把场景语义展开为意图、时间和资源三类契约。
 *
 * 这是四维抽象中的核心翻译阶段：
 * - SceneSemantic -> IntentLevel
 * - IntentLevel + SceneSemantic -> TimeMode / Lease
 * - IntentLevel -> 多资源请求
 *
 * 其输出会被稳定化、约束裁剪和仲裁阶段继续消费。
 */
class IntentStage : public IStage {
public:
    const char *Name() const override { return "IntentStage"; }

    /** @brief 执行场景语义到契约的推导。 */
    StageOutput Execute(StageContext &ctx) override;

    /**
     * @brief 根据场景语义和意图契约推导资源请求
     * @param semantic 场景语义
     * @param intent 意图契约
     * @return 资源请求结构
     *
     * 此方法为公共方法，供 InheritanceStage 等阶段复用，
     * 避免逻辑硬拷贝冗余。
     *
     * @note 遵循框架 SSOT 原则，资源请求推导逻辑统一在此处
     */
    static ResourceRequest DeriveResourceRequest(const SceneSemantic &semantic,
                                                 const IntentContract &intent,
                                                 uint32_t profileResourceMask, ConfigLoader &loader,
                                                 const ConfigParams &params);
    static void FillResourceRequest(ResourceRequest &request, const SceneSemantic &semantic,
                                    const IntentContract &intent, uint32_t profileResourceMask,
                                    ConfigLoader &loader, const ConfigParams &params);

    static ResourceRequest DeriveResourceRequest(const SceneSemantic &semantic,
                                                 const IntentContract &intent,
                                                 uint32_t profileResourceMask,
                                                 ConfigLoader &loader) {
        return DeriveResourceRequest(semantic, intent, profileResourceMask, loader,
                                     loader.GetParams());
    }

    static ResourceRequest DeriveResourceRequest(
        const SceneSemantic &semantic, const IntentContract &intent,
        uint32_t profileResourceMask = ((1u << kResourceDimCount) - 1u)) {
        return DeriveResourceRequest(semantic, intent, profileResourceMask,
                                     ConfigLoader::Instance());
    }

    /**
     * @brief 根据场景语义和意图推导时间契约。
     * @param semantic 场景语义。
     * @param intent 当前意图契约。
     * @param deterministicTs 确定性时间戳（ns）。
     * @return 时间契约结构。
     *
     * @note 该接口被 InheritanceStage 复用，用于保证 intent 被提升后，
     * temporal contract 与 lease 也同步重算。
     */
    static TemporalContract DeriveTemporalContract(const SceneSemantic &semantic,
                                                   const IntentContract &intent,
                                                   int64_t deterministicTs,
                                                   const SceneRule *rule = nullptr);
    static void FillTemporalContract(TemporalContract &contract, const SceneSemantic &semantic,
                                     const IntentContract &intent, int64_t deterministicTs,
                                     const SceneRule *rule = nullptr);

private:
    /** @brief 根据场景语义推导意图等级。 */
    IntentContract DeriveIntentContract(const SceneSemantic &semantic, const SceneRule *rule) const;
    void FillIntentContract(IntentContract &contract, const SceneSemantic &semantic,
                            const SceneRule *rule) const;
};

}    // namespace vendor::transsion::perfengine::dse
