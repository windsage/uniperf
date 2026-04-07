#pragma once

#include "StageBase.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @class ArbitrationStage
 * @brief 在约束、平台能力和意图优先级之间完成确定性仲裁
 *
 * ArbitrationStage 对应框架中的“可交付资源裁剪”阶段：
 * - 输入为上游推导出的规范化资源请求、约束上界和平台可行边界
 * - 输出为每个资源维度的最终 delivered 值及对应原因位
 * - 不直接执行平台命令，只负责形成后续 finalize 阶段可消费的仲裁结果
 *
 * 该阶段必须保持纯计算特性，避免引入额外状态副作用，从而满足框架对可重放性、
 * 可解释性和时延上界的要求。
 */
class ArbitrationStage : public IStage {
public:
    const char *Name() const override { return "ArbitrationStage"; }

    /**
     * @brief 执行资源仲裁
     * @param ctx 阶段上下文，需携带 request / constraint / capability / intent 等中间态
     * @return StageOutput 阶段执行结果；成功时会在上下文中写回 ArbitrationResult
     */
    StageOutput Execute(StageContext &ctx) override;
};

}    // namespace vendor::transsion::perfengine::dse
