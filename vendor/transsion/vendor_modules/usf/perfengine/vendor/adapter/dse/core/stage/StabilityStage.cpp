#include "StabilityStage.h"

#include <utility>

#include "IntentStage.h"
#include "state/StateVault.h"

namespace vendor::transsion::perfengine::dse {

// StabilityStage 负责把 StabilityMechanism 嵌入调度流水线：
// - 从 Vault 恢复历史稳定化状态
// - 构造候选/已提交契约
// - 在需要 hold 时回退到上一拍已提交契约
StageOutput StabilityStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.intermediates || !ctx.convergeIntermediates) {
        out.success = false;
        return out;
    }

    RestoreFromVault(ctx);

    auto &loader = ctx.GetConfigLoader();
    const auto &params = loader.GetParams();

    StabilityParams stabilityParams;
    stabilityParams.observationWindowMs = params.observationWindowMs;
    stabilityParams.minResidencyMs = params.minResidencyMs;
    stabilityParams.steadyEnterMerges = params.steadyEnterMerges;
    stabilityParams.exitHysteresisMerges = params.exitHysteresisMerges;
    stabilityParams.updateThrottleMs = params.updateThrottleMs;

    stability_.UpdateParams(stabilityParams);

    StabilityInput input;
    input.currentTimeMs = ctx.event ? (ctx.event->timestamp / 1000000) : 0;

    const auto &semantic = ctx.GetSceneSemantic();
    const auto &intentContract = ctx.GetIntentContract();
    input.candidateSemantic = semantic;
    input.candidateIntent = intentContract;

    if (ctx.state) {
        const auto &prevScene = ctx.state->Scene();
        const auto &prevIntent = ctx.state->Intent();
        input.committedSemantic = prevScene.semantic;
        input.committedIntent = prevIntent.contract;

        input.semanticChanged = !SceneSemanticEquals(semantic, prevScene.semantic);
        input.intentChanged = (intentContract.level != prevIntent.contract.level);
    } else {
        input.semanticChanged = (semantic.kind != SceneKind::Unknown);
        input.intentChanged = true;
    }
    // 仅当前候选是 P1 时旁路稳定化；从上一拍 P1 收敛出去时仍需进入观察窗口。
    input.bypassForP1 = (intentContract.level == IntentLevel::P1);

    const bool candidateChanged = input.semanticChanged || input.intentChanged;

    // mergeCount 只在“候选确实发生变化且未被更新节流（throttled）”时推进。
    //
    // 设计目标（对应框架“更新节流/重复去重”）：
    // - 稳定观察窗口内，如果候选语义并未真正变化或被节流抑制，
    //   则稳定化状态机不应产生可观察的“伪变化”；
    // - 否则 `StateVault` 的等值抑制无法生效，会导致 generation/history 在稳态高频事件下持续增长。
    uint32_t mergeCount = 1;
    if (ctx.state) {
        const auto &prevStability = ctx.state->Stability();
        bool throttled = false;
        if (candidateChanged && params.updateThrottleMs > 0) {
            int64_t deltaMs = input.currentTimeMs - prevStability.lastUpdateMs;
            if (deltaMs < 0) {
                deltaMs = 0;
            }
            throttled = deltaMs < params.updateThrottleMs;
        }
        mergeCount = prevStability.totalMergeCount + ((candidateChanged && !throttled) ? 1u : 0u);
    }
    input.mergeCount = mergeCount;

    auto stabilityOut = stability_.Evaluate(input);

    if (stabilityOut.shouldHold && ctx.state) {
        // hold 的语义是“继续沿用上一拍已提交契约”，而不是放弃本轮计算。
        const auto &prevScene = ctx.state->Scene();
        const auto &prevIntent = ctx.state->Intent();
        const auto &prevLease = ctx.state->Lease();

        // ResourceRequest 的 FillResourceRequest 只依赖：
        // - `intent.level`
        // - `semantic.kind`
        // - `semantic.visible`（仅 `SceneKind::Playback` 分支会读取）
        //
        // 因此当 shouldHold 发生且上述“决定 request 的输入要素”与上一拍一致时，
        // 继续调用 DeriveResourceRequest
        // 等价于重复计算与整对象搬运（不符合框架的“稳态维持、防抖动”）。
        const auto &candScene = ctx.GetSceneSemantic();
        const auto &candIntent = ctx.GetIntentContract();
        const bool requestSemanticallyUnchanged = (candIntent.level == prevIntent.contract.level) &&
                                                  (candScene.kind == prevScene.semantic.kind) &&
                                                  (candScene.kind != SceneKind::Playback ||
                                                   candScene.visible == prevScene.semantic.visible);

        ctx.SetSceneSemantic(prevScene.semantic);
        ctx.SetIntentContract(prevIntent.contract);
        ctx.SetTemporalContract(prevLease.contract);
        if (!requestSemanticallyUnchanged) {
            ctx.SetResourceRequest(IntentStage::DeriveResourceRequest(
                prevScene.semantic, prevIntent.contract, ctx.profileResourceMask, loader, params));
        }
    }

    UpdateVault(ctx, stabilityOut, mergeCount, candidateChanged);

    if (stabilityOut.shouldThrottle) {
        out.success = true;
        out.dirtyBits.SetStability();
        return out;
    }

    out.success = true;
    out.dirtyBits.SetStability();
    if (stabilityOut.shouldHold) {
        out.dirtyBits.SetIntent();
        out.dirtyBits.SetLease();
    }
    return out;
}

// 从 Vault 恢复稳定化状态，保证跨轮调度时观察窗口和驻留计数连续。
void StabilityStage::RestoreFromVault(StageContext &ctx) {
    if (!ctx.state) {
        return;
    }

    const auto &vaultStability = ctx.state->Stability();

    if (vaultStability.totalMergeCount == 0 && !vaultStability.inSteady &&
        !vaultStability.inObservation && !vaultStability.pendingExit) {
        return;
    }

    StabilityState restored;
    restored.inSteady = vaultStability.inSteady;
    restored.inObservation = vaultStability.inObservation;
    restored.lastUpdateMs = vaultStability.lastUpdateMs;
    restored.observationStartTimeMs = vaultStability.observationStartTimeMs;
    restored.steadyEnterTimeMs = vaultStability.steadyEnterTimeMs;
    restored.steadyEnterMerges = vaultStability.steadyEnterMerges;
    restored.exitHysteresisMerges = vaultStability.exitHysteresisMerges;
    restored.stableMergeCount = vaultStability.stableMergeCount;
    restored.exitMergeCount = vaultStability.exitMergeCount;
    restored.pendingExit = vaultStability.pendingExit;
    restored.pendingSemantic = vaultStability.pendingSemantic;
    restored.pendingIntent = vaultStability.pendingIntent;

    stability_.RestoreState(std::move(restored));
}

// UpdateVault 把本轮稳定化状态显式写回中间态，供 Orchestrator 原子提交。
void StabilityStage::UpdateVault(StageContext &ctx, const StabilityOutput &output,
                                 uint32_t mergeCount, bool candidateChanged) {
    StabilityState state;
    state.observationWindowMs = stability_.GetParams().observationWindowMs;
    state.minResidencyMs = stability_.GetParams().minResidencyMs;
    state.lastUpdateMs = output.lastUpdateMs;
    if (ctx.state && !candidateChanged) {
        // 候选未发生变化时，lastUpdateMs 不需要推进到 vault，
        // 否则会造成无效稳定化写回（generation/history 被动增长）。
        state.lastUpdateMs = ctx.state->Stability().lastUpdateMs;
    }
    state.observationStartTimeMs = output.observationStartTimeMs;
    state.steadyEnterTimeMs = output.steadyEnterTimeMs;
    state.steadyEnterMerges = output.steadyEnterMerges;
    state.exitHysteresisMerges = output.exitHysteresisMerges;
    state.stableMergeCount = output.stableMergeCount;
    state.exitMergeCount = output.exitMergeCount;
    state.inSteady = output.inSteady;
    state.inObservation = output.inObservation;
    state.pendingExit = output.pendingExit;
    state.pendingSemantic = output.pendingSemantic;
    state.pendingIntent = output.pendingIntent;

    state.totalMergeCount = mergeCount;

    ctx.SetStabilityState(state);
}

}    // namespace vendor::transsion::perfengine::dse
