#pragma once

// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。
// 状态金库：七维状态域的统一存储与版本管理（任务清单 §4.1、§5.1）。
//
// 设计要点（对齐框架 §5「可确定性」— 短链、可预期时延；§2.4/§4.5 节流与一致快照）：
// 1. Generation + StateView：决策侧只读 Pin()/Snapshot()，StateView 绑定共享不可变快照（CoW 域槽 +
// 引用计数），避免每事件整包复制
// 2. 热路径读：Snapshot()/GetCommittedToken() 与 PinAndSnapshot 主路径一致 — 无读锁乐观读（槽
// retain + 世代双检），写线程不再被只读 Snapshot 阻塞
// 3. 历史回放：deterministicTs 早于已提交时间时仍用读锁，保证 historyBuffer 与槽索引一致遍历
// 4. 写路径：写锁内改状态 + 世代推进；事务 CAS 与写锁合一；SyncCommitBarrier
// 作为批处理后的全局可见点
// 5. 与 docs/commit.md §三、§四 对照：池化快照 + refcount 为「堆
// RCU」伪代码的框架内等价实现，勿引入热路径堆分配。

#include <atomic>

#include "StateDomains.h"

namespace vendor::transsion::perfengine::dse {

static constexpr size_t kHistoryBufferSize = 16;
static constexpr size_t kSnapshotPoolSize = 1024;
static constexpr size_t kDomainSnapshotPoolSize = 2048;
static constexpr uint32_t kMaxSnapshotChainDepth = 16;
static constexpr uint32_t kInvalidSnapshotSlot = 0xFFFFFFFFu;
static constexpr size_t kSnapshotBitmapWordCount = (kSnapshotPoolSize + 63u) / 64u;
static constexpr size_t kDomainBitmapWordCount = (kDomainSnapshotPoolSize + 63u) / 64u;

template <typename T>
struct DomainSnapshotSlot {
    T payload;
    std::atomic<uint32_t> refCount{0};
};

using ConstraintSnapshotSlot = DomainSnapshotSlot<ConstraintState>;
using SceneSnapshotSlot = DomainSnapshotSlot<SceneState>;
using IntentSnapshotSlot = DomainSnapshotSlot<IntentState>;
using LeaseSnapshotSlot = DomainSnapshotSlot<LeaseState>;
using DependencySnapshotSlot = DomainSnapshotSlot<DependencyState>;
using CapabilitySnapshotSlot = DomainSnapshotSlot<CapabilityState>;
using StabilitySnapshotSlot = DomainSnapshotSlot<StabilityState>;

struct StateSnapshotPayload {
    ConstraintSnapshotSlot *constraint = nullptr;
    SceneSnapshotSlot *scene = nullptr;
    IntentSnapshotSlot *intent = nullptr;
    LeaseSnapshotSlot *lease = nullptr;
    DependencySnapshotSlot *dependency = nullptr;
    CapabilitySnapshotSlot *capability = nullptr;
    StabilitySnapshotSlot *stability = nullptr;
};

struct SnapshotSlot {
    StateSnapshotPayload payload;
    uint32_t parentSnapshotSlot = kInvalidSnapshotSlot;
    DirtyBits ownedBits;
    uint32_t chainDepth = 0;
    std::atomic<uint32_t> refCount{0};
};

struct HistoryEntry {
    uint32_t snapshotSlot = kInvalidSnapshotSlot;
    Generation generation = 0;
    int64_t timestamp = 0;
};

class LightweightRWLock {
public:
    LightweightRWLock() : state_(0) {}

    void LockRead() {
        int32_t expected = state_.load(std::memory_order_acquire);
        while (expected < 0 ||
               !state_.compare_exchange_weak(expected, expected + 1, std::memory_order_acq_rel,
                                             std::memory_order_acquire)) {
            expected = state_.load(std::memory_order_acquire);
        }
    }

    void UnlockRead() { state_.fetch_sub(1, std::memory_order_release); }

    void LockWrite() {
        int32_t expected = 0;
        while (!state_.compare_exchange_weak(expected, -1, std::memory_order_acq_rel,
                                             std::memory_order_acquire)) {
            expected = 0;
        }
    }

    void UnlockWrite() { state_.store(0, std::memory_order_release); }

private:
    std::atomic<int32_t> state_;
};

class ReadLockGuard {
public:
    explicit ReadLockGuard(LightweightRWLock &lock) : lock_(lock) { lock_.LockRead(); }
    ~ReadLockGuard() { lock_.UnlockRead(); }

private:
    ReadLockGuard(const ReadLockGuard &) = delete;
    ReadLockGuard &operator=(const ReadLockGuard &) = delete;
    LightweightRWLock &lock_;
};

class WriteLockGuard {
public:
    explicit WriteLockGuard(LightweightRWLock &lock) : lock_(lock) { lock_.LockWrite(); }
    ~WriteLockGuard() { lock_.UnlockWrite(); }

private:
    WriteLockGuard(const WriteLockGuard &) = delete;
    WriteLockGuard &operator=(const WriteLockGuard &) = delete;
    LightweightRWLock &lock_;
};

class StateVault;

class StateView {
public:
    StateView();
    StateView(const StateView &other);
    StateView(StateView &&other) noexcept;
    StateView &operator=(const StateView &other);
    StateView &operator=(StateView &&other) noexcept;
    ~StateView();

    const ConstraintState &Constraint() const { return snapshotData_->constraint->payload; }
    const SceneState &Scene() const { return snapshotData_->scene->payload; }
    const IntentState &Intent() const { return snapshotData_->intent->payload; }
    const LeaseState &Lease() const { return snapshotData_->lease->payload; }
    const DependencyState &Dependency() const { return snapshotData_->dependency->payload; }
    const CapabilityState &Capability() const { return snapshotData_->capability->payload; }
    const StabilityState &Stability() const { return snapshotData_->stability->payload; }

    Generation GetGeneration() const { return generation_; }
    int64_t GetTimestamp() const { return timestamp_; }

private:
    friend class StateVault;

    void AttachSnapshotSlot(StateVault *owner, uint32_t slotIndex,
                            const StateSnapshotPayload *payload, Generation gen, int64_t timestamp);
    void AttachRetainedSnapshotSlot(StateVault *owner, uint32_t slotIndex,
                                    const StateSnapshotPayload *payload, Generation gen,
                                    int64_t timestamp);
    void ReleaseSnapshotSlot();

    StateVault *ownerVault_ = nullptr;
    uint32_t snapshotSlotIndex_ = kInvalidSnapshotSlot;
    const StateSnapshotPayload *snapshotData_ = nullptr;
    Generation generation_ = 0;
    int64_t timestamp_ = 0;
};

struct TransactionSnapshot {
    ConstraintState constraint;
    SceneState scene;
    IntentState intent;
    LeaseState lease;
    DependencyState dependency;
    CapabilityState capability;
    StabilityState stability;
    Generation generation;
    DirtyBits dirtyBits;
    DirtyBits stagedBits;
    bool active = false;
    bool pendingBump = false;
    /** 事务活跃期间钉扎的已发布快照 payload，避免多域 Ensure* 重复 load currentSnapshotSlot_。 */
    mutable const StateSnapshotPayload *txnPayloadView_ = nullptr;
};

struct StateUpdatePatch {
    const ConstraintState *constraint = nullptr;
    const SceneState *scene = nullptr;
    const IntentState *intent = nullptr;
    const LeaseState *lease = nullptr;
    const DependencyState *dependency = nullptr;
    const CapabilityState *capability = nullptr;
    const StabilityState *stability = nullptr;
};

struct TransactionResult {
    bool success = false;
    bool conflict = false;
    Generation expectedGen = 0;
    Generation actualGen = 0;
};

class StateVault {
public:
    StateVault();

    SnapshotToken Pin();
    SnapshotToken Pin(int64_t deterministicTs);
    StateView Snapshot();
    SnapshotToken GetCommittedToken() const;

    SnapshotToken PinAndSnapshot(int64_t deterministicTs, StateView &outView);

    void BeginTransaction();
    bool BeginTransaction(Generation expectedGen);
    TransactionResult CommitTransactionCas();
    bool CommitTransaction();
    void RollbackTransaction();
    bool InTransaction() const { return transaction_.active; }

    void UpdateConstraint(const ConstraintSnapshot &snapshot);
    void UpdateScene(const SceneSemantic &semantic, SceneId sceneId);
    void UpdateIntent(const IntentContract &contract, SessionId boundSession = 0);
    void UpdateLease(const TemporalContract &contract, LeaseId leaseId);
    void UpdateDependency(const DependencyState &dep);
    void UpdateCapability(const CapabilitySnapshot &snapshot);
    void UpdateStability(const StabilityState &stability);

    void UpdateConstraintAndCapability(const ConstraintSnapshot &constraintSnapshot,
                                       const CapabilitySnapshot &capabilitySnapshot);

    void MarkClean();

    /** @brief 全局发布点：清脏 + 递增 syncEpoch（批处理完成后调用一次即可） */
    void SyncCommitBarrier() noexcept;

    uint64_t GetSyncEpoch() const noexcept { return syncEpoch_.load(std::memory_order_acquire); }

    DirtyBits GetDirtyBits() const;

    /**
     * @brief 释放会话资源 - 清理会话相关状态
     * @param sessionId 会话标识符
     *
     * 租约终止时清理相关状态：
     * 1. 清除租约状态
     * 2. 重置意图契约
     *
     * @note 遵循框架"契约衰减"原则
     */
    void ReleaseSession(SessionId sessionId);

    Generation GetGeneration() const { return generation_.load(std::memory_order_acquire); }

    /**
     * @brief 无锁读取当前已发布槽上的 `activeSessionId`（内部复用 `CurrentSnapshotPayload()`）。
     * @note 与 `ReadActiveSessionId` 分工：本接口供 **已持 `WriteLockGuard`** 的路径（如
     * `UpdateIntent`） 使用，避免写锁内再套读锁死锁；语义为「当前槽指针视图」，与乐观读路径一致。
     * @note 并发线程若需与写提交 **线性化** 的观测，应使用 `ReadActiveSessionId()`（读锁），
     *       对齐框架 §5 / §7「一致快照、可解释状态」的观测口径。
     */
    SessionId PeekActiveSessionIdFromCurrentSlot() const noexcept;

    /**
     * @brief 在 `ReadLockGuard` 下读取
     * `activeSessionId`，与并发写路径互斥，保证观测点与槽发布一致。
     * @note **禁止**在已持有本 vault **写锁** 的同线程调用（会与 `LightweightRWLock` 死锁）。
     * @note 与 `PeekActiveSessionIdFromCurrentSlot` 非重复：`Peek*`
     * 无读锁、供写锁内路径；本接口承担与写提交的线性化观测。
     */
    SessionId ReadActiveSessionId() const;

    /** 批量状态引用：同一次 `CurrentSnapshotPayload`
     * 下的多域指针，供决策侧一次取齐（框架：一致快照）。 */
    struct CurrentStateRefs {
        const ConstraintState *constraint = nullptr;
        const SceneState *scene = nullptr;
        const IntentState *intent = nullptr;
        const LeaseState *lease = nullptr;
        const DependencyState *dependency = nullptr;
        const CapabilityState *capability = nullptr;
        const StabilityState *stability = nullptr;
    };

    /**
     * @brief 读锁下基于 `CurrentSnapshotPayload()` 填充 `CurrentStateRefs`，与逐域
     * `Current*State()` 共享同一槽解析路径。
     * @note 与 `CurrentSnapshotPayload` 为分层
     * API：本接口在一次读锁内批量给出各域指针，服务需要多域一致视图的调用方。
     */
    CurrentStateRefs GetCurrentStateRefs() const;

    friend class StateView;

private:
    std::atomic<Generation> generation_{0};
    DirtyBits dirtyBits_;
    TransactionSnapshot transaction_;

    HistoryEntry historyBuffer_[kHistoryBufferSize];
    std::atomic<uint32_t> historyHead_{0};
    std::atomic<uint32_t> historyCount_{0};

    std::atomic<uint64_t> syncEpoch_{0};
    std::atomic<int64_t> committedTimestamp_{0};

    mutable LightweightRWLock rwlock_;
    SnapshotSlot snapshotPool_[kSnapshotPoolSize];
    std::atomic<uint64_t> snapshotFreeBitmap_[kSnapshotBitmapWordCount];
    ConstraintSnapshotSlot constraintSnapshotPool_[kDomainSnapshotPoolSize];
    SceneSnapshotSlot sceneSnapshotPool_[kDomainSnapshotPoolSize];
    IntentSnapshotSlot intentSnapshotPool_[kDomainSnapshotPoolSize];
    LeaseSnapshotSlot leaseSnapshotPool_[kDomainSnapshotPoolSize];
    DependencySnapshotSlot dependencySnapshotPool_[kDomainSnapshotPoolSize];
    CapabilitySnapshotSlot capabilitySnapshotPool_[kDomainSnapshotPoolSize];
    StabilitySnapshotSlot stabilitySnapshotPool_[kDomainSnapshotPoolSize];
    std::atomic<uint64_t> constraintFreeBitmap_[kDomainBitmapWordCount];
    std::atomic<uint64_t> sceneFreeBitmap_[kDomainBitmapWordCount];
    std::atomic<uint64_t> intentFreeBitmap_[kDomainBitmapWordCount];
    std::atomic<uint64_t> leaseFreeBitmap_[kDomainBitmapWordCount];
    std::atomic<uint64_t> dependencyFreeBitmap_[kDomainBitmapWordCount];
    std::atomic<uint64_t> capabilityFreeBitmap_[kDomainBitmapWordCount];
    std::atomic<uint64_t> stabilityFreeBitmap_[kDomainBitmapWordCount];
    std::atomic<uint32_t> currentSnapshotSlot_{kInvalidSnapshotSlot};
    uint32_t nextSnapshotSlotHint_ = 1;
    uint32_t nextConstraintSlotHint_ = 1;
    uint32_t nextSceneSlotHint_ = 1;
    uint32_t nextIntentSlotHint_ = 1;
    uint32_t nextLeaseSlotHint_ = 1;
    uint32_t nextDependencySlotHint_ = 1;
    uint32_t nextCapabilitySlotHint_ = 1;
    uint32_t nextStabilitySlotHint_ = 1;

    void CommitStagedUpdateUnlocked(const StateUpdatePatch &patch, const DirtyBits &nextDirtyBits);
    void SaveToHistory(const StateUpdatePatch &patch, Generation generation);
    const HistoryEntry *FindHistoryByTimestamp(int64_t timestamp) const;
    uint32_t AcquireSnapshotSlotLocked();
    bool TryRetainSnapshotSlot(uint32_t slotIndex);
    void RetainSnapshotSlot(uint32_t slotIndex);
    void ReleaseSnapshotSlot(uint32_t slotIndex);
    const StateSnapshotPayload *GetSnapshotPayload(uint32_t slotIndex) const;

    bool CommitTransactionUnlocked() noexcept;
    void ResetTransactionUnlocked() noexcept;
    ConstraintState &EnsureTransactionConstraintStaged();
    SceneState &EnsureTransactionSceneStaged();
    IntentState &EnsureTransactionIntentStaged();
    LeaseState &EnsureTransactionLeaseStaged();
    DependencyState &EnsureTransactionDependencyStaged();
    CapabilityState &EnsureTransactionCapabilityStaged();
    StabilityState &EnsureTransactionStabilityStaged();
    const IntentState &GetTransactionIntentState() const;
    const LeaseState &GetTransactionLeaseState() const;
    StateUpdatePatch BuildTransactionPatch() const;
    /**
     * @brief 单次 `currentSnapshotSlot_` 加载后返回对应 `payload`；槽非法时返回
     * `EmptyStateSnapshot()`。
     * @note 供 `Current*State`、`GetCurrentStateRefs`、`PeekActiveSessionIdFromCurrentSlot`
     * 复用，避免 DRY 分叉。
     * @note 阶段链热路径经 `PinAndSnapshot`+`StateView` 消费固定 payload
     * 指针，不反复走本函数；本函数主要用于 直接读当前槽、事务 staging、测试断言。
     */
    const StateSnapshotPayload &CurrentSnapshotPayload() const;
    /**
     * @brief 仅在 `transaction_.active` 且已持写锁时使用：首次解析并缓存
     * `CurrentSnapshotPayload`，后续域 staging 复用。
     */
    const StateSnapshotPayload &TxnPinnedSnapshotPayload() const;
    const ConstraintState &CurrentConstraintState() const;
    const SceneState &CurrentSceneState() const;
    const IntentState &CurrentIntentState() const;
    const LeaseState &CurrentLeaseState() const;
    const DependencyState &CurrentDependencyState() const;
    const CapabilityState &CurrentCapabilityState() const;
    const StabilityState &CurrentStabilityState() const;
};

}    // namespace vendor::transsion::perfengine::dse
