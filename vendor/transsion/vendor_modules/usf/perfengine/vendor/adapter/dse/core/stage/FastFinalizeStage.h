#pragma once

#include "StageBase.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

class RuleTable;

/**
 * @class FastFinalizeStage
 * @brief 将快路径中间态收束为可立即下发的短租约 FastGrant
 *
 * 该阶段服务于框架中的 P1 首响应路径，只做最小必要裁剪和短租约封装：
 * - 不等待完整稳定化或复杂收敛逻辑
 * - 输出可快速执行的 delivered/grantedMask/lease 组合
 * - 保留 trace 信息，便于后续与收敛路径结果对账
 */
class FastFinalizeStage : public IStage {
public:
    const char *Name() const override { return "FastFinalizeStage"; }

    /**
     * @brief 执行快路径收尾
     * @param ctx 阶段上下文
     * @return StageOutput 阶段执行结果；成功时在上下文中写入 FastGrant
     */
    StageOutput Execute(StageContext &ctx) override;

private:
    /**
     * @brief 基于请求、约束和能力计算快路径授权结果
     * @param request 规范化资源请求
     * @param constraintAllowed 约束裁剪后的可用上界
     * @param capabilityFeasible 平台能力边界
     * @param intentLevel 当前有效意图等级
     * @return FastGrant 快路径授权结果，包含 delivered、reasonBits 与短租约信息
     *
     * @note 快路径强调首响应时延，因此只保留有限 lease 策略，不承担完整收敛职责。
     */
    void ComputeFastGrant(const ResourceRequest &request,
                          const ConstraintAllowed &constraintAllowed,
                          const CapabilityFeasible &capabilityFeasible, IntentLevel intentLevel,
                          RuleTable &ruleTable, FastGrant &grant) const;

    /**
     * @brief 记录 FastGrant Trace 信息
     * @param ctx 阶段上下文
     * @param grant 快速授权结果
     *
     * 记录快路径授权的 ReplayHash，用于与收敛路径对账。
     *
     * @note 遵循框架 §13 可追溯性原则
     */
    void RecordFastGrantTrace(const StageContext &ctx, const FastGrant &grant) const;
};

}    // namespace vendor::transsion::perfengine::dse
