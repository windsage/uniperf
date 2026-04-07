#pragma once

#include <atomic>
#include <mutex>
#include <vector>

#include "core/state/StateVault.h"
#include "platform/state/IPlatformStateBackend.h"
#include "platform/state/StateTypes.h"

namespace vendor::transsion::perfengine::dse {

class StateService;

/**
 * @struct StateServiceMetrics
 * @brief 状态服务运行指标。
 *
 * 用于观测状态更新链路的时延、回退率和冲突情况，
 * 帮助区分问题发生在状态采集、合并还是发布阶段。
 */
struct StateServiceMetrics {
    std::atomic<size_t> updateLatencyP99{0};
    std::atomic<size_t> coalesceCount{0};
    std::atomic<size_t> staleDropCount{0};
    std::atomic<size_t> sourceResyncCount{0};
    std::atomic<size_t> mixedGenerationCount{0};
    std::atomic<size_t> bootstrapReadyMs{0};
    std::atomic<size_t> fallbackCount[kStateDomainCount] = {};
};

/**
 * @class StateAggregator
 * @brief 状态增量聚合器。
 *
 * 负责把平台状态后端上报的 delta 聚合为：
 * - 规范化后的 ConstraintSnapshot
 * - 当前平台 CapabilitySnapshot
 * - 可原子提交到 StateVault 的约束/能力组合
 *
 * 它是状态侧的 SSOT 入口，避免多个采集源各自直接写 Vault。
 */
class StateAggregator {
public:
    /** @brief 构造聚合器。 */
    explicit StateAggregator(StateVault &vault, StateServiceMetrics &metrics);

    /**
     * @brief 异步接收状态增量。
     * @param delta 平台状态增量。
     * @return 是否成功接受并提交。
     */
    bool OnDelta(const StateDelta &delta) noexcept;

    /** 持锁应用 delta，成功提交则 generation 前进；返回是否已推进（timeoutMs 预留） */
    bool UpdateSync(const StateDelta &delta, uint32_t timeoutMs) noexcept;

    /** @brief 设置状态规则集。 */
    void SetRuleSet(const StateRuleSet &rules);

    /** @brief 获取某个状态域最近一次成功采样时间。 */
    uint64_t GetLastCaptureNs(StateDomain domain) const;

    size_t GetCoalesceCount() const { return metrics_.coalesceCount.load(); }
    size_t GetStaleDropCount() const { return metrics_.staleDropCount.load(); }
    size_t GetSourceResyncCount() const { return metrics_.sourceResyncCount.load(); }

    const ConstraintSnapshot &GetCachedConstraint() const { return constraintCache_; }
    const CapabilitySnapshot &GetCachedCapability() const { return capabilityCache_; }

private:
    friend class StateService;

    /** @brief 判断 delta 是否满足单调时间要求，并可回传是否因 capture 顺序倒退而拒绝。 */
    bool ShouldAccept(const StateDelta &delta, bool *staleByOrder = nullptr) const;
    /** @brief 在持锁条件下应用 delta 并尝试提交。 */
    bool ApplyDeltaNoLock(const StateDelta &delta) noexcept;
    /** @brief 把约束补丁合并到快照。 */
    void ApplyConstraintPatch(const ConstraintDeltaPatch &patch, ConstraintSnapshot &snapshot);
    /** @brief 把能力补丁合并到快照。 */
    void ApplyCapabilityPatch(const CapabilityDeltaPatch &patch, CapabilitySnapshot &snapshot);
    /** @brief 对约束快照做阈值归一化。 */
    void NormalizeConstraint(ConstraintSnapshot &snapshot);
    /** @brief 将聚合后的约束/能力原子提交到 StateVault。 */
    bool CommitToVault(const ConstraintSnapshot &constraint, const CapabilitySnapshot &capability,
                       bool forceCommit);
    /** @brief 用初始快照灌满缓存并完成首次提交。 */
    void PrimeCachesAndCommit(const ConstraintSnapshot &constraint,
                              const CapabilitySnapshot &capability);

    StateVault &vault_;
    StateServiceMetrics &metrics_;
    StateRuleSet rules_;

    ConstraintSnapshot constraintCache_;
    CapabilitySnapshot capabilityCache_;

    uint64_t lastCaptureNs_[kStateDomainCount] = {0};

    ConstraintSnapshot lastPublishedConstraint_{};
    CapabilitySnapshot lastPublishedCapability_{};

    std::mutex mutex_;
};

class StateService final : public IStateSink {
public:
    static constexpr uint32_t kDefaultWaitReadyTimeoutMs = 50;
    static constexpr uint32_t kBootstrapHardTimeoutMs = 150;
    static constexpr uint32_t kDefaultUpdateSyncTimeoutMs = 20;
    static constexpr size_t kMaxListenerCount = 64;

    /** @brief 构造状态服务。 */
    explicit StateService(StateVault &vault) noexcept;
    ~StateService();

    /**
     * @brief 初始化状态服务。
     * @param vendor 目标平台厂商。
     * @return 初始化是否成功。
     */
    bool Init(PlatformVendor vendor) noexcept;

    /** @brief 启动状态采集线程和初始引导。 */
    bool Start() noexcept;
    /** @brief 停止状态采集。 */
    void Stop() noexcept;

    bool IsReady() const noexcept { return ready_.load(std::memory_order_acquire); }
    /** @brief 等待状态服务进入 ready。 */
    bool WaitUntilReady(uint32_t timeoutMs = kDefaultWaitReadyTimeoutMs) noexcept;

    /** @brief 更新监控上下文，例如前后台/场景信息。 */
    void SetMonitoringContext(const MonitoringContext &ctx) noexcept;
    /** @brief 订阅状态更新。 */
    void Subscribe(IStateListener *listener) noexcept;
    /** @brief 取消订阅状态更新。 */
    void Unsubscribe(IStateListener *listener) noexcept;

    /** @brief IStateSink 异步回调入口。 */
    void OnDelta(const StateDelta &delta) noexcept override;
    /** @brief IStateSink 同步更新入口。 */
    bool UpdateSync(const StateDelta &delta,
                    uint32_t timeoutMs = kDefaultUpdateSyncTimeoutMs) noexcept override;

    StateAggregator &GetAggregator() { return aggregator_; }
    const StateAggregator &GetAggregator() const { return aggregator_; }

    const StateRuleSet &GetRuleSet() const { return ruleSet_; }
    const StateServiceMetrics &GetMetrics() const { return metrics_; }

    size_t GetUpdateLatencyP99() const { return metrics_.updateLatencyP99.load(); }
    size_t GetMixedGenerationCount() const { return metrics_.mixedGenerationCount.load(); }
    size_t GetBootstrapReadyMs() const { return metrics_.bootstrapReadyMs.load(); }
    size_t GetFallbackRateByDomain(StateDomain domain) const;

private:
    /** @brief 向订阅者广播状态变化。 */
    void NotifyListenersIfAny(const StateDelta &delta);
    /** @brief 从平台状态后端读取首帧状态。 */
    bool BootstrapInitial();
    /** @brief 通过平台注册表创建状态后端。 */
    bool CreateBackend(PlatformVendor vendor);

    StateVault &vault_;
    StateServiceMetrics metrics_;
    StateAggregator aggregator_;
    IPlatformStateBackend *backend_ = nullptr;
    std::atomic<bool> ready_{false};
    std::atomic<bool> started_{false};

    StateRuleSet ruleSet_;
    MonitoringContext monitoringCtx_;

    std::vector<IStateListener *> listeners_;
    std::mutex listenerMutex_;
};

}    // namespace vendor::transsion::perfengine::dse
