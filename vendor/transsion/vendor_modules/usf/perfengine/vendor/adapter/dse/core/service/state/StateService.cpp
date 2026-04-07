#include "StateService.h"

#include <algorithm>
#include <chrono>
#include <thread>

#include "core/types/ConstraintTypes.h"
#include "core/types/CoreTypes.h"
#include "platform/PlatformRegistry.h"
#include "platform/state/PlatformStateCommon.h"

namespace vendor::transsion::perfengine::dse {
namespace {
// 该函数把平台注册表中的抽象能力转换为状态快照格式。
// 注意这里生成的是“平台可行能力边界”，而不是运行时已被热/电裁剪后的 allowed。
void FillCapabilityFromPlatform(PlatformVendor vendor, CapabilitySnapshot &capability) {
    PlatformCapability platformCapability;
    PlatformRegistry::FillPlatformCapability(vendor, platformCapability);
    FillCapabilitySnapshotFromPlatformCapability(platformCapability, capability);
}
}    // namespace

// 构造时先建立一份可工作的默认约束/能力缓存，避免后续快照比较访问空状态。
StateAggregator::StateAggregator(StateVault &vault, StateServiceMetrics &metrics)
    : vault_(vault), metrics_(metrics) {
    InitializeConstraintSnapshotDefaults(constraintCache_);
    FillCapabilityFromPlatform(PlatformVendor::Unknown, capabilityCache_);

    lastPublishedConstraint_ = constraintCache_;
    lastPublishedCapability_ = capabilityCache_;
}

void StateAggregator::SetRuleSet(const StateRuleSet &rules) {
    std::lock_guard<std::mutex> lock(mutex_);
    rules_ = rules;
}

uint64_t StateAggregator::GetLastCaptureNs(StateDomain domain) const {
    return lastCaptureNs_[StateDomainToIndex(domain)];
}

bool StateAggregator::ShouldAccept(const StateDelta &delta, bool *staleByOrder) const {
    if (staleByOrder) {
        *staleByOrder = false;
    }
    if (delta.domainMask == 0) {
        return false;
    }

    uint32_t domainIdx = 0;
    for (uint32_t mask = 1; mask <= static_cast<uint32_t>(StateDomain::Capability);
         mask <<= 1, ++domainIdx) {
        if (delta.domainMask & mask) {
            if (delta.captureNs < lastCaptureNs_[domainIdx]) {
                if (staleByOrder) {
                    *staleByOrder = true;
                }
                return false;
            }
        }
    }

    return true;
}

void StateAggregator::ApplyConstraintPatch(const ConstraintDeltaPatch &patch,
                                           ConstraintSnapshot &snapshot) {
    if (patch.fieldMask & ConstraintDeltaPatch::kThermalLevel) {
        snapshot.thermalLevel = patch.thermalLevel;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kThermalScale) {
        snapshot.thermalScaleQ10 = patch.thermalScaleQ10;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kThermalSevere) {
        snapshot.thermalSevere = patch.thermalSevere;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kBatteryLevel) {
        snapshot.batteryLevel = patch.batteryLevel;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kBatteryScale) {
        snapshot.batteryScaleQ10 = patch.batteryScaleQ10;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kBatterySaver) {
        snapshot.batterySaver = patch.batterySaver;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kMemoryPressure) {
        snapshot.memoryPressure = patch.memoryPressure;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kPsiLevel) {
        snapshot.psiLevel = patch.psiLevel;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kPsiScale) {
        snapshot.psiScaleQ10 = patch.psiScaleQ10;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kScreenOn) {
        snapshot.screenOn = patch.screenOn;
    }
    if (patch.fieldMask & ConstraintDeltaPatch::kPowerState) {
        snapshot.powerState = patch.powerState;
    }
}

// 能力补丁只更新明确声明变化的维度，避免未上报字段误清零。
void StateAggregator::ApplyCapabilityPatch(const CapabilityDeltaPatch &patch,
                                           CapabilitySnapshot &snapshot) {
    if (patch.supportedResources != 0) {
        snapshot.supportedResources = patch.supportedResources;
    }
    if (patch.capabilityFlags != 0) {
        snapshot.capabilityFlags = patch.capabilityFlags;
    }

    for (size_t i = 0; i < kResourceDimCount; ++i) {
        const uint32_t dimMask = (1u << i);
        if (patch.feasibleChangedMask & dimMask) {
            snapshot.domains[i].maxCapacity = patch.maxCapacityPatch.v[i];
        }
        if (patch.actionChangedMask & dimMask) {
            snapshot.actionPathFlags[i] = patch.actionPathFlags[i];
        }
        if ((patch.feasibleChangedMask | patch.actionChangedMask) & dimMask) {
            snapshot.domains[i].flags = patch.domainFlags[i];
        }
    }
}

// NormalizeConstraint 负责把原始采样值转换为调度链可消费的规则化状态：
// 例如 severeThreshold、battery saver 与 scaling 上限裁剪。
void StateAggregator::NormalizeConstraint(ConstraintSnapshot &snapshot) {
    if (snapshot.thermalLevel >= rules_.thermal.severeThreshold) {
        snapshot.thermalSevere = true;
    }

    if (snapshot.batteryLevel <= rules_.battery.saverThreshold) {
        snapshot.batterySaver = true;
    }

    if (snapshot.thermalScaleQ10 > 1024) {
        snapshot.thermalScaleQ10 = 1024;
    }
    if (snapshot.batteryScaleQ10 > 1024) {
        snapshot.batteryScaleQ10 = 1024;
    }
    if (snapshot.psiScaleQ10 > 1024) {
        snapshot.psiScaleQ10 = 1024;
    }
}

// ApplyDeltaNoLock 只做合并和提交，不在这里持锁，便于同步与异步入口复用。
bool StateAggregator::ApplyDeltaNoLock(const StateDelta &delta) noexcept {
    uint32_t domainIdx = 0;
    for (uint32_t mask = 1; mask <= static_cast<uint32_t>(StateDomain::Capability);
         mask <<= 1, ++domainIdx) {
        if (delta.domainMask & mask) {
            lastCaptureNs_[domainIdx] = delta.captureNs;
        }
    }

    if (delta.domainMask & (ToMask(StateDomain::Thermal) | ToMask(StateDomain::Battery) |
                            ToMask(StateDomain::MemoryPsi) | ToMask(StateDomain::Screen) |
                            ToMask(StateDomain::Power))) {
        ApplyConstraintPatch(delta.constraint, constraintCache_);
        NormalizeConstraint(constraintCache_);
    }

    if (delta.domainMask & ToMask(StateDomain::Capability)) {
        ApplyCapabilityPatch(delta.capability, capabilityCache_);
    }

    return CommitToVault(constraintCache_, capabilityCache_, false);
}

void StateAggregator::PrimeCachesAndCommit(const ConstraintSnapshot &constraint,
                                           const CapabilitySnapshot &capability) {
    constraintCache_ = constraint;
    capabilityCache_ = capability;
    (void)CommitToVault(constraint, capability, true);
}

// 提交前会先做“与上次发布快照是否等价”的比较，以减少 generation 无意义增长，
// 这也是降低调度链振荡的重要性能优化点。
bool StateAggregator::CommitToVault(const ConstraintSnapshot &constraint,
                                    const CapabilitySnapshot &capability, bool forceCommit) {
    if (!forceCommit && CanonicalConstraintSnapshotEquals(constraint, lastPublishedConstraint_) &&
        CanonicalCapabilitySnapshotEquals(capability, lastPublishedCapability_)) {
        metrics_.coalesceCount.fetch_add(1, std::memory_order_relaxed);
        return false;
    }

    vault_.BeginTransaction();
    // 约束/能力是同一份状态快照的两个面，聚合器内合并比较一次即可。
    vault_.UpdateConstraintAndCapability(constraint, capability);

    auto result = vault_.CommitTransactionCas();
    if (!result.success) {
        if (result.conflict) {
            metrics_.mixedGenerationCount.fetch_add(1, std::memory_order_relaxed);
        }
        return false;
    }

    lastPublishedConstraint_ = constraint;
    lastPublishedCapability_ = capability;
    return true;
}

bool StateAggregator::OnDelta(const StateDelta &delta) noexcept {
    std::lock_guard<std::mutex> lock(mutex_);

    const auto t0 = std::chrono::steady_clock::now();
    auto updateLatencySample = [&](size_t elapsedNs) {
        // 使用“最大值采样”近似 P99：在单实例聚合器持锁条件下，该采样足以用于趋势观测。
        size_t cur = metrics_.updateLatencyP99.load(std::memory_order_relaxed);
        while (elapsedNs > cur && !metrics_.updateLatencyP99.compare_exchange_weak(
                                      cur, elapsedNs, std::memory_order_relaxed)) {
        }
    };

    bool staleByOrder = false;
    if (!ShouldAccept(delta, &staleByOrder)) {
        metrics_.staleDropCount.fetch_add(1, std::memory_order_relaxed);

        // sourceResyncCount：如果拒绝原因是“时间戳单调性破坏”，视为源端需要 resync。
        if (staleByOrder) {
            metrics_.sourceResyncCount.fetch_add(1, std::memory_order_relaxed);
        }

        updateLatencySample(
            static_cast<size_t>(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                    std::chrono::steady_clock::now() - t0)
                                    .count()));
        return false;
    }

    const bool committed = ApplyDeltaNoLock(delta);
    updateLatencySample(static_cast<size_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - t0)
            .count()));
    return committed;
}

bool StateAggregator::UpdateSync(const StateDelta &delta, uint32_t timeoutMs) noexcept {
    (void)timeoutMs;
    std::lock_guard<std::mutex> lock(mutex_);

    const auto t0 = std::chrono::steady_clock::now();
    auto updateLatencySample = [&](size_t elapsedNs) {
        // 使用最大值采样近似 P99；对单实例聚合器该值足以反映尾延迟趋势。
        size_t cur = metrics_.updateLatencyP99.load(std::memory_order_relaxed);
        while (elapsedNs > cur && !metrics_.updateLatencyP99.compare_exchange_weak(
                                      cur, elapsedNs, std::memory_order_relaxed)) {
        }
    };

    bool staleByOrder = false;
    if (!ShouldAccept(delta, &staleByOrder)) {
        metrics_.staleDropCount.fetch_add(1, std::memory_order_relaxed);

        if (staleByOrder) {
            metrics_.sourceResyncCount.fetch_add(1, std::memory_order_relaxed);
        }

        updateLatencySample(
            static_cast<size_t>(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                    std::chrono::steady_clock::now() - t0)
                                    .count()));
        return false;
    }

    const Generation genBefore = vault_.GetGeneration();
    const bool committed = ApplyDeltaNoLock(delta);
    updateLatencySample(static_cast<size_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - t0)
            .count()));
    return committed && (vault_.GetGeneration() != genBefore);
}

StateService::StateService(StateVault &vault) noexcept
    : vault_(vault), aggregator_(vault, metrics_) {}

StateService::~StateService() {
    Stop();
}

// Init 只做规则和后端准备，不启动任何线程；真正的 I/O 行为在 Start 中触发。
bool StateService::Init(PlatformVendor vendor) noexcept {
    ruleSet_.ruleVersion = 1;
    ruleSet_.thermal.severeThreshold = 5;
    ruleSet_.battery.saverThreshold = 20;

    aggregator_.SetRuleSet(ruleSet_);
    // 先把目标平台的静态能力边界注入缓存，保证 Bootstrap 之前也有一致的能力视图。
    FillCapabilityFromPlatform(vendor, aggregator_.capabilityCache_);

    if (!CreateBackend(vendor)) {
        return false;
    }

    return true;
}

bool StateService::CreateBackend(PlatformVendor vendor) {
    backend_ = PlatformRegistry::Instance().CreateStateBackend(vendor);
    if (!backend_) {
        return false;
    }

    if (!backend_->Init()) {
        delete backend_;
        backend_ = nullptr;
        return false;
    }

    return true;
}

// BootstrapInitial 读取首帧状态并一次性提交，保证后续读取者拿到的是同 generation 的完整快照。
bool StateService::BootstrapInitial() {
    if (!backend_) {
        for (uint32_t i = 0; i < kStateDomainCount; ++i) {
            metrics_.fallbackCount[i].fetch_add(1, std::memory_order_relaxed);
        }
        return false;
    }

    ConstraintSnapshot constraint;
    CapabilitySnapshot capability;
    if (!backend_->ReadInitial(&constraint, &capability)) {
        for (uint32_t i = 0; i < kStateDomainCount; ++i) {
            metrics_.fallbackCount[i].fetch_add(1, std::memory_order_relaxed);
        }
        return false;
    }

    aggregator_.PrimeCachesAndCommit(constraint, capability);
    return true;
}

bool StateService::Start() noexcept {
    if (started_.exchange(true, std::memory_order_acq_rel)) {
        return true;
    }

    auto bootstrapStart = std::chrono::steady_clock::now();

    if (!BootstrapInitial()) {
        started_.store(false, std::memory_order_release);
        return false;
    }

    if (backend_ && !backend_->Start(this)) {
        ready_.store(false, std::memory_order_release);
        started_.store(false, std::memory_order_release);
        return false;
    }

    ready_.store(true, std::memory_order_release);

    auto bootstrapEnd = std::chrono::steady_clock::now();
    auto bootstrapMs =
        std::chrono::duration_cast<std::chrono::milliseconds>(bootstrapEnd - bootstrapStart)
            .count();
    metrics_.bootstrapReadyMs.store(static_cast<size_t>(bootstrapMs), std::memory_order_relaxed);

    return true;
}

// Stop 必须先清 ready，再停止后端线程，避免调用方继续把服务当成可用状态。
void StateService::Stop() noexcept {
    if (!started_.exchange(false, std::memory_order_acq_rel)) {
        return;
    }

    ready_.store(false, std::memory_order_release);

    if (backend_) {
        backend_->Stop();
    }
}

bool StateService::WaitUntilReady(uint32_t timeoutMs) noexcept {
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    while (!ready_.load(std::memory_order_acquire)) {
        if (std::chrono::steady_clock::now() >= deadline) {
            return false;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    return true;
}

void StateService::SetMonitoringContext(const MonitoringContext &ctx) noexcept {
    monitoringCtx_ = ctx;
    if (backend_) {
        backend_->UpdateMonitoringContext(ctx);
    }
}

void StateService::Subscribe(IStateListener *listener) noexcept {
    if (!listener) {
        return;
    }
    std::lock_guard<std::mutex> lock(listenerMutex_);
    if (listeners_.size() >= kMaxListenerCount) {
        return;
    }
    listeners_.push_back(listener);
}

void StateService::Unsubscribe(IStateListener *listener) noexcept {
    std::lock_guard<std::mutex> lock(listenerMutex_);
    listeners_.erase(std::remove(listeners_.begin(), listeners_.end(), listener), listeners_.end());
}

void StateService::OnDelta(const StateDelta &delta) noexcept {
    if (!aggregator_.OnDelta(delta)) {
        return;
    }
    vault_.SyncCommitBarrier();
    NotifyListenersIfAny(delta);
}

bool StateService::UpdateSync(const StateDelta &delta, uint32_t timeoutMs) noexcept {
    const bool committed = aggregator_.UpdateSync(delta, timeoutMs);
    if (committed) {
        vault_.SyncCommitBarrier();
        NotifyListenersIfAny(delta);
    }
    return committed;
}

void StateService::NotifyListenersIfAny(const StateDelta &delta) {
    std::lock_guard<std::mutex> lock(listenerMutex_);
    if (listeners_.empty()) {
        return;
    }
    const SnapshotToken token = vault_.GetCommittedToken();
    for (auto *listener : listeners_) {
        listener->OnStateChanged(token.generation, token, delta.domainMask);
    }
}

size_t StateService::GetFallbackRateByDomain(StateDomain domain) const {
    const uint32_t idx = StateDomainToIndex(domain);
    return metrics_.fallbackCount[idx].load(std::memory_order_relaxed);
}

}    // namespace vendor::transsion::perfengine::dse
