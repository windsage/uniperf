#pragma once

#include "StageBase.h"
#include "inheritance/IntentInheritance.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @class InheritanceStage
 * @brief 处理依赖引发的意图继承。
 *
 * 该阶段对应框架中“优先级反转时的意图继承”机制：
 * - 根据 ResourceStage 建立的真实依赖边判断是否触发继承
 * - 在当前执行实体是 holder 时，临时提升其 intent / lease / request
 * - 继承结束后按时撤销
 */
class InheritanceStage : public IStage {
public:
    const char *Name() const override { return "InheritanceStage"; }

    /** @brief 执行继承触发、维持与撤销逻辑。 */
    StageOutput Execute(StageContext &ctx) override;

private:
    /** @brief 判断当前上下文是否满足触发继承的条件。 */
    bool CheckInheritanceNeeded(const StageContext &ctx) const;
    /** @brief 把继承后的有效 intent 落到当前 holder 上下文。 */
    bool ApplyInheritance(StageContext &ctx, const InheritanceContext &inheritCtx);

    IntentInheritance inheritance_;
};

}    // namespace vendor::transsion::perfengine::dse
