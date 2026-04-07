#pragma once

#include <atomic>
#include <cstdint>
#include <thread>

#include "ActionCompiler.h"
#include "ExecutorSet.h"
#include "PlatformActionMapper.h"
#include "platform/PlatformBase.h"
#include "platform/state/StateTypes.h"
#include "trace/TraceRecorder.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @struct ExecutionFlowResult
 * @brief 执行流输出结果
 *
 * execResult 保存对外可追踪的执行元数据；
 * success 反映平台执行是否整体成功，调用方可据此决定是否记录降级或继续补偿。
 */
struct ExecutionFlowResult {
    ExecutionResult execResult;
    bool success = false;
};

/**
 * @class ExecutionFlow
 * @brief 将 PolicyDecision 下沉为平台动作并触发实际执行
 *
 * 执行流负责衔接三层语义：
 * 1. ActionCompiler：把框架契约转换成平台无关的抽象动作
 * 2. IPlatformActionMap：把抽象动作映射为平台命令批次
 * 3. IPlatformExecutor：实际把命令提交到平台后端
 *
 * 当某一层缺失或映射结果为空时，执行流会显式落到 NoopFallback，而不是静默吞掉失败。
 * 这符合框架的可解释降级原则。
 */
template <typename ProfileSpec>
class ExecutionFlow {
public:
    ExecutionFlow() = default;

    /**
     * @brief 执行平台动作下发流程
     * @param decision 收敛路径输出的完整策略决策
     * @param actionMap 平台动作映射器，可为空；为空时显式走 NoopFallback
     * @param executor 平台执行器，可为空；为空时显式走 NoopFallback
     * @return ExecutionFlowResult 执行结果与平台回执摘要
     *
     * @note 该函数不修改决策本身，只负责把统一语义可靠地下沉到平台层。
     */
    ExecutionFlowResult Execute(const PolicyDecision &decision, IPlatformActionMap *actionMap,
                                IPlatformExecutor *executor, int64_t dedupWindowMs = 0) {
        ExecutionFlowResult result;
        result.execResult.sessionId = decision.sessionId;
        result.execResult.meta = decision.meta;

        const bool dedupEligible = dedupWindowMs > 0 && IsDedupEligible(decision);
        const uint64_t dedupCheckNs = dedupEligible ? SystemTime::GetDeterministicNs() : 0;
        const uint64_t dedupSummary =
            dedupEligible ? (decision.executionSummary != 0u ? decision.executionSummary
                                                             : ComputeExecutionSummary(decision))
                          : 0;

        if (dedupEligible && TryBuildDedupExecutionResult(decision, dedupSummary, dedupCheckNs,
                                                          dedupWindowMs, result.execResult)) {
            result.success = true;
            TraceRecorder::Instance().RecordExecution(result.execResult);
            return result;
        }

        if (!actionMap) {
            // 平台映射器缺失时必须显式失败，避免把“未实现”误当成“无需执行”。
            SetNoopFallbackResult(result.execResult, 0x01);
            result.success = false;
            RecordFailure(result.execResult);
            return result;
        }

        ActionCompiler<ProfileSpec> compiler;
        // Compile 负责保留执行层仍会消费的 contractMode/timeMode/intent
        // 语义，不能被平台侧提前吞掉。
        auto abstractBatch = compiler.Compile(decision);
        auto cmdBatch = actionMap->Map(abstractBatch);
        ExecuteCommandBatch(cmdBatch, executor, result);

        UpdateDedupCache(decision, dedupEligible, dedupSummary, SystemTime::GetDeterministicNs(),
                         result);

        return result;
    }

    ExecutionFlowResult ExecuteFastGrant(const FastGrant &grant, SessionId sessionId,
                                         const DecisionMeta &meta, IntentLevel effectiveIntent,
                                         TimeMode timeMode, IPlatformActionMap *actionMap,
                                         IPlatformExecutor *executor) {
        ExecutionFlowResult result;
        result.execResult.sessionId = sessionId;
        result.execResult.meta = meta;
        if (!actionMap) {
            SetNoopFallbackResult(result.execResult, 0x01);
            result.success = false;
            RecordFailure(result.execResult);
            return result;
        }
        auto cmdBatch = actionMap->MapFastGrant(grant, effectiveIntent, timeMode);
        ExecuteCommandBatch(cmdBatch, executor, result);
        return result;
    }

private:
    enum : uint32_t {
        kExecutionFlagRejected = 0x01u,
        kExecutionFlagFallback = 0x02u,
        kExecutionFlagSilentFallbackBlocked = 0x04u,
        kExecutionFlagDedupSkipped = 0x08u
    };

    struct DedupCacheEntry {
        uint64_t summary = 0;
        uint64_t signature = 0;
        uint64_t dispatchNs = 0;
        ExecutionResult result;
        bool valid = false;
    };

    /** @brief 统一设置显式 NoopFallback 执行结果，避免分支各自维护相同位图语义。 */
    static void SetNoopFallbackResult(ExecutionResult &execResult, uint32_t rejectReason) {
        execResult.rejectReasons = rejectReason;
        execResult.actualPath = PlatformActionPath::NoopFallback;
        execResult.fallbackFlags = rejectReason;
        execResult.executionFlags = kExecutionFlagRejected;
        execResult.meta.platformActionPath = execResult.actualPath;
        execResult.meta.fallbackFlags = execResult.fallbackFlags;
    }

    static bool IsDedupEligible(const PolicyDecision &decision) {
        return decision.IsDedupCandidate();
    }

    void ExecuteCommandBatch(const PlatformCommandBatch &cmdBatch, IPlatformExecutor *executor,
                             ExecutionFlowResult &result) {
        if (!executor) {
            // executor 缺失与 actionMap 缺失是两类不同问题，分别编码便于平台接入排障。
            SetNoopFallbackResult(result.execResult, 0x02);
            result.success = false;
            RecordFailure(result.execResult);
            return;
        }

        if (cmdBatch.commandCount == 0 || cmdBatch.path == PlatformActionPath::NoopFallback) {
            // unsupported-only 或空批次都必须显式进入 NoopFallback，保证失败路径可诊断。
            SetNoopFallbackResult(result.execResult, 0x04);
            result.success = false;
            RecordFailure(result.execResult);
            return;
        }

        auto platformResult = executor->Execute(cmdBatch);

        // 执行结果按“已执行 / 被拒绝 / fallback”三类位图回传，供 trace 和统计聚合使用。
        result.execResult.executedMask = platformResult.appliedMask;
        result.execResult.rejectReasons = platformResult.rejectedMask;
        result.execResult.fallbackFlags = platformResult.fallbackMask;
        result.execResult.actualPath = cmdBatch.path;
        result.execResult.meta.platformActionPath = result.execResult.actualPath;
        result.execResult.meta.fallbackFlags = result.execResult.fallbackFlags;

        if (platformResult.hadRejection) {
            result.execResult.executionFlags |= kExecutionFlagRejected;
        }
        if (platformResult.hadFallback) {
            result.execResult.executionFlags |= kExecutionFlagFallback;
        }
        if (platformResult.silentFallbackBlocked != 0) {
            result.execResult.executionFlags |= kExecutionFlagSilentFallbackBlocked;
        }

        result.success = platformResult.success;

        if (!result.success) {
            RecordFailure(result.execResult);
        }

        TraceRecorder::Instance().RecordExecution(result.execResult);
    }

    bool TryBuildDedupExecutionResult(const PolicyDecision &decision, uint64_t dedupSummary,
                                      uint64_t nowNs, int64_t dedupWindowMs,
                                      ExecutionResult &execResult) {
        if (dedupWindowMs <= 0) {
            return false;
        }
        const uint64_t windowNs = static_cast<uint64_t>(dedupWindowMs) * 1000000ULL;
        if (!dedupPublishedValid_.load(std::memory_order_acquire)) {
            return false;
        }
        if (dedupPublishedSummary_.load(std::memory_order_acquire) != dedupSummary) {
            return false;
        }
        const uint64_t dispatchNs = dedupPublishedDispatchNs_.load(std::memory_order_acquire);
        if (nowNs < dispatchNs || (nowNs - dispatchNs) >= windowNs) {
            return false;
        }

        const uint32_t activeSlot = dedupActiveSlot_.load(std::memory_order_acquire);
        const DedupCacheEntry &cache = dedupCacheSlots_[activeSlot];
        if (!cache.valid) {
            return false;
        }
        if (cache.summary != dedupSummary) {
            return false;
        }
        if (nowNs < cache.dispatchNs || (nowNs - cache.dispatchNs) >= windowNs) {
            return false;
        }
        // [核心优化] 直接复用 decision 中预计算好的指纹，彻底消除热路径上的 4 向量哈希遍历。
        if (cache.signature != decision.executionSignature) {
            return false;
        }
        execResult = cache.result;
        execResult.sessionId = decision.sessionId;
        execResult.meta = decision.meta;
        execResult.meta.platformActionPath = execResult.actualPath;
        execResult.meta.fallbackFlags = execResult.fallbackFlags;
        execResult.executionFlags |= kExecutionFlagDedupSkipped;
        return true;
    }

    static void SpinPause(uint32_t spinCount) noexcept {
#if defined(__x86_64__) || defined(__i386__)
        (void)spinCount;
        __asm__ __volatile__("pause" ::: "memory");
#elif defined(__aarch64__)
        (void)spinCount;
        __asm__ __volatile__("yield" ::: "memory");
#else
        if (spinCount >= 16u) {
            std::this_thread::yield();
        }
#endif
    }

    void AcquireDedupWriteLock() noexcept {
        uint32_t spins = 0;
        while (dedupWriteLock_.test_and_set(std::memory_order_acquire)) {
            ++spins;
            SpinPause(spins);
            if (spins > 64u) {
                std::this_thread::yield();
                spins = 32u;
            }
        }
    }

    void UpdateDedupCache(const PolicyDecision &decision, bool dedupEligible, uint64_t dedupSummary,
                          uint64_t dispatchNs, const ExecutionFlowResult &result) {
        AcquireDedupWriteLock();
        if (!dedupEligible || !result.success || result.execResult.rejectReasons != 0 ||
            result.execResult.fallbackFlags != 0 ||
            result.execResult.actualPath == PlatformActionPath::NoopFallback) {
            DedupCacheEntry &inactive =
                dedupCacheSlots_[1u - dedupActiveSlot_.load(std::memory_order_relaxed)];
            inactive.valid = false;
            dedupActiveSlot_.store(1u - dedupActiveSlot_.load(std::memory_order_relaxed),
                                   std::memory_order_release);
            dedupPublishedValid_.store(false, std::memory_order_release);
            dedupWriteLock_.clear(std::memory_order_release);
            return;
        }

        const uint32_t inactiveSlot = 1u - dedupActiveSlot_.load(std::memory_order_relaxed);
        DedupCacheEntry &cache = dedupCacheSlots_[inactiveSlot];
        cache.summary = dedupSummary;
        // [核心优化] 直接复用决策阶段预计算好的执行签名，消除此处重复哈希开销。
        cache.signature = decision.executionSignature;
        cache.dispatchNs = dispatchNs;
        cache.result = result.execResult;
        cache.result.meta.platformActionPath = cache.result.actualPath;
        cache.result.meta.fallbackFlags = cache.result.fallbackFlags;
        cache.valid = true;
        dedupActiveSlot_.store(inactiveSlot, std::memory_order_release);
        dedupPublishedSummary_.store(dedupSummary, std::memory_order_release);
        dedupPublishedDispatchNs_.store(dispatchNs, std::memory_order_release);
        dedupPublishedValid_.store(true, std::memory_order_release);
        dedupWriteLock_.clear(std::memory_order_release);
    }

    /**
     * @brief 记录失败或显式 fallback 的上下文
     * @param execResult 执行层结果
     *
     * 该记录用于把执行失败映射回框架统一的 fallback 原因树，便于上层做审计、
     * 统计和平台适配问题定位。
     */
    void RecordFailure(const ExecutionResult &execResult) {
        FallbackContext ctx;
        ctx.sessionId = execResult.sessionId;
        ctx.profileId = execResult.meta.profileId;
        ctx.generation = execResult.meta.generation;
        ctx.ruleVersion = execResult.meta.ruleVersion;
        ctx.resourceMask = execResult.executedMask | execResult.rejectReasons;
        ctx.timestamp = execResult.meta.decisionTs;

        if (execResult.actualPath == PlatformActionPath::NoopFallback) {
            // NoopFallback 是显式降级，而不是“没有事情要做”；不同 reject code 映射不同根因。
            if (execResult.rejectReasons == 0x01) {
                ctx.reason = DecisionFallbackReason::PlatformActionMapFailed;
                ctx.SetDetail(FallbackDomain::Platform, FallbackCategory::Unavailable, 0, 0, 1);
            } else if (execResult.rejectReasons == 0x02) {
                ctx.reason = DecisionFallbackReason::PlatformExecutorFailed;
                ctx.SetDetail(FallbackDomain::Platform, FallbackCategory::Unavailable, 0, 0, 2);
            } else if (execResult.rejectReasons == 0x04) {
                ctx.reason = DecisionFallbackReason::NoopFallback;
                ctx.SetDetail(FallbackDomain::Execution, FallbackCategory::Noop, 0, 0, 0);
            } else {
                ctx.reason = DecisionFallbackReason::ExecutionFailed;
                ctx.SetDetail(FallbackDomain::Execution, FallbackCategory::Failed, 0, 0, 0);
            }
        } else if (execResult.rejectReasons != 0) {
            ctx.reason = DecisionFallbackReason::ExecutionRejected;
            ctx.SetDetail(FallbackDomain::Execution, FallbackCategory::Rejected,
                          static_cast<uint8_t>(execResult.rejectReasons), 0, 0);
        } else {
            ctx.reason = DecisionFallbackReason::ExecutionFailed;
            ctx.SetDetail(FallbackDomain::Execution, FallbackCategory::Failed, 0, 0, 0);
        }

        TraceRecorder::Instance().RecordFallback(ctx);
    }

    DedupCacheEntry dedupCacheSlots_[2];
    std::atomic<uint32_t> dedupActiveSlot_{0};
    std::atomic_flag dedupWriteLock_ = ATOMIC_FLAG_INIT;
    std::atomic<uint64_t> dedupPublishedSummary_{0};
    std::atomic<uint64_t> dedupPublishedDispatchNs_{0};
    std::atomic<bool> dedupPublishedValid_{false};
};

}    // namespace vendor::transsion::perfengine::dse
