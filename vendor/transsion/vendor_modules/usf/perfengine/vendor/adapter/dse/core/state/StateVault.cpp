#include "StateVault.h"

#include <chrono>
#include <cstdlib>

#include "platform/state/StateTypes.h"

namespace vendor::transsion::perfengine::dse {

namespace {

/**
 * @brief 稳定化状态等值比较（用于等值抑制 / 幂等写入）
 *
 * `StateVault` 需要遵循《整机资源确定性调度抽象框架》的“更新节流、重复去重”理念：
 * 当同一候选的稳定化结果与当前已发布 payload 完全一致时，不应触发
 * `generation++`、不应新建 history snapshot，从而避免在稳态高频事件下产生无效写回/锁竞争。
 *
 * 为保证确定性一致性，本比较需包含 stability 状态机相关的所有可观察字段，
 * 包括计数器与 pending/阶段标识（而不仅是 inSteady/inObservation）。
 */
inline bool StabilityStateEquals(const StabilityState &lhs, const StabilityState &rhs) noexcept {
    return lhs.observationWindowMs == rhs.observationWindowMs &&
           lhs.minResidencyMs == rhs.minResidencyMs && lhs.lastUpdateMs == rhs.lastUpdateMs &&
           lhs.observationStartTimeMs == rhs.observationStartTimeMs &&
           lhs.steadyEnterTimeMs == rhs.steadyEnterTimeMs &&
           lhs.steadyEnterMerges == rhs.steadyEnterMerges &&
           lhs.exitHysteresisMerges == rhs.exitHysteresisMerges &&
           lhs.totalMergeCount == rhs.totalMergeCount &&
           lhs.stableMergeCount == rhs.stableMergeCount &&
           lhs.exitMergeCount == rhs.exitMergeCount && lhs.inSteady == rhs.inSteady &&
           lhs.inObservation == rhs.inObservation && lhs.pendingExit == rhs.pendingExit &&
           SceneSemanticEquals(lhs.pendingSemantic, rhs.pendingSemantic) &&
           IntentContractEquals(lhs.pendingIntent, rhs.pendingIntent);
}

template <size_t N>
void InitFreeBitmap(std::atomic<uint64_t> (&bitmap)[N], size_t slotCount) {
    for (size_t i = 0; i < N; ++i) {
        bitmap[i].store(~0ULL, std::memory_order_relaxed);
    }
    const size_t validBitsLastWord = slotCount % 64u;
    if (validBitsLastWord != 0) {
        const uint64_t mask = (1ULL << validBitsLastWord) - 1ULL;
        bitmap[N - 1].store(mask, std::memory_order_relaxed);
    }
}

template <size_t N>
void MarkSlotUsed(std::atomic<uint64_t> (&bitmap)[N], uint32_t slotIndex) {
    const size_t word = slotIndex / 64u;
    const uint64_t bit = 1ULL << (slotIndex % 64u);
    bitmap[word].fetch_and(~bit, std::memory_order_acq_rel);
}

template <size_t N>
void MarkSlotFree(std::atomic<uint64_t> (&bitmap)[N], uint32_t slotIndex) {
    const size_t word = slotIndex / 64u;
    const uint64_t bit = 1ULL << (slotIndex % 64u);
    bitmap[word].fetch_or(bit, std::memory_order_release);
}

template <size_t N>
uint32_t AcquireFreeSlot(std::atomic<uint64_t> (&bitmap)[N], uint32_t slotCount,
                         uint32_t &nextHint) {
    if (slotCount == 0) {
        std::abort();
    }

    const uint32_t startWord = (nextHint / 64u) % static_cast<uint32_t>(N);
    for (uint32_t step = 0; step < static_cast<uint32_t>(N); ++step) {
        const uint32_t wordIndex = (startWord + step) % static_cast<uint32_t>(N);
        uint64_t available = bitmap[wordIndex].load(std::memory_order_acquire);
        while (available != 0) {
            const uint32_t bitIndex = static_cast<uint32_t>(__builtin_ctzll(available));
            const uint32_t slotIndex = wordIndex * 64u + bitIndex;
            if (slotIndex >= slotCount) {
                available &= ~(1ULL << bitIndex);
                continue;
            }
            const uint64_t mask = 1ULL << bitIndex;
            const uint64_t prev = bitmap[wordIndex].fetch_and(~mask, std::memory_order_acq_rel);
            if ((prev & mask) != 0) {
                nextHint = (slotIndex + 1u) % slotCount;
                return slotIndex;
            }
            available = prev & ~mask;
        }
    }

    std::abort();
}

template <typename Slot>
void RetainDomainSlot(Slot *slot) {
    if (slot != nullptr) {
        slot->refCount.fetch_add(1, std::memory_order_acq_rel);
    }
}

template <typename Slot, size_t PoolSize, size_t BitmapWords>
void ReleaseDomainSlot(Slot *slot, Slot (&pool)[PoolSize],
                       std::atomic<uint64_t> (&bitmap)[BitmapWords]) {
    if (slot == nullptr) {
        return;
    }
    if (slot->refCount.fetch_sub(1, std::memory_order_acq_rel) == 1u) {
        const uint32_t slotIndex = static_cast<uint32_t>(slot - &pool[0]);
        MarkSlotFree(bitmap, slotIndex);
    }
}

template <typename Slot, size_t PoolSize, size_t BitmapWords, typename State>
Slot *CreateDomainSnapshotSlot(const State &nextState, Slot (&pool)[PoolSize],
                               std::atomic<uint64_t> (&bitmap)[BitmapWords], uint32_t &nextHint) {
    const uint32_t slotIndex = AcquireFreeSlot(bitmap, static_cast<uint32_t>(PoolSize), nextHint);
    auto &slot = pool[slotIndex];
    slot.payload = nextState;
    slot.refCount.store(1, std::memory_order_release);
    return &slot;
}

ConstraintSnapshotSlot &EmptyConstraintSlot() {
    static ConstraintSnapshotSlot slot{};
    return slot;
}

SceneSnapshotSlot &EmptySceneSlot() {
    static SceneSnapshotSlot slot{};
    return slot;
}

IntentSnapshotSlot &EmptyIntentSlot() {
    static IntentSnapshotSlot slot{};
    return slot;
}

LeaseSnapshotSlot &EmptyLeaseSlot() {
    static LeaseSnapshotSlot slot{};
    return slot;
}

DependencySnapshotSlot &EmptyDependencySlot() {
    static DependencySnapshotSlot slot{};
    return slot;
}

CapabilitySnapshotSlot &EmptyCapabilitySlot() {
    static CapabilitySnapshotSlot slot{};
    return slot;
}

StabilitySnapshotSlot &EmptyStabilitySlot() {
    static StabilitySnapshotSlot slot{};
    return slot;
}

const StateSnapshotPayload &EmptyStateSnapshot() {
    static const StateSnapshotPayload empty{
        &EmptyConstraintSlot(), &EmptySceneSlot(),      &EmptyIntentSlot(),   &EmptyLeaseSlot(),
        &EmptyDependencySlot(), &EmptyCapabilitySlot(), &EmptyStabilitySlot()};
    return empty;
}

void ResetSnapshotSlotState(SnapshotSlot &slot) {
    slot.payload = EmptyStateSnapshot();
    slot.parentSnapshotSlot = kInvalidSnapshotSlot;
    slot.ownedBits.Clear();
    slot.chainDepth = 0;
}

}    // namespace

StateView::StateView() : snapshotData_(&EmptyStateSnapshot()) {}

StateView::StateView(const StateView &other) : StateView() {
    AttachSnapshotSlot(other.ownerVault_, other.snapshotSlotIndex_, other.snapshotData_,
                       other.generation_, other.timestamp_);
}

StateView::StateView(StateView &&other) noexcept
    : ownerVault_(other.ownerVault_),
      snapshotSlotIndex_(other.snapshotSlotIndex_),
      snapshotData_(other.snapshotData_),
      generation_(other.generation_),
      timestamp_(other.timestamp_) {
    other.ownerVault_ = nullptr;
    other.snapshotSlotIndex_ = kInvalidSnapshotSlot;
    other.snapshotData_ = &EmptyStateSnapshot();
    other.generation_ = 0;
    other.timestamp_ = 0;
}

StateView &StateView::operator=(const StateView &other) {
    if (this != &other) {
        AttachSnapshotSlot(other.ownerVault_, other.snapshotSlotIndex_, other.snapshotData_,
                           other.generation_, other.timestamp_);
    }
    return *this;
}

StateView &StateView::operator=(StateView &&other) noexcept {
    if (this != &other) {
        ReleaseSnapshotSlot();
        ownerVault_ = other.ownerVault_;
        snapshotSlotIndex_ = other.snapshotSlotIndex_;
        snapshotData_ = other.snapshotData_;
        generation_ = other.generation_;
        timestamp_ = other.timestamp_;
        other.ownerVault_ = nullptr;
        other.snapshotSlotIndex_ = kInvalidSnapshotSlot;
        other.snapshotData_ = &EmptyStateSnapshot();
        other.generation_ = 0;
        other.timestamp_ = 0;
    }
    return *this;
}

StateView::~StateView() {
    ReleaseSnapshotSlot();
}

void StateView::AttachSnapshotSlot(StateVault *owner, uint32_t slotIndex,
                                   const StateSnapshotPayload *payload, Generation gen,
                                   int64_t timestamp) {
    if (owner == ownerVault_ && slotIndex == snapshotSlotIndex_ && payload == snapshotData_) {
        generation_ = gen;
        timestamp_ = timestamp;
        return;
    }
    if (owner != nullptr && slotIndex != kInvalidSnapshotSlot) {
        owner->RetainSnapshotSlot(slotIndex);
    }
    ReleaseSnapshotSlot();
    ownerVault_ = owner;
    snapshotSlotIndex_ = slotIndex;
    snapshotData_ = payload != nullptr ? payload : &EmptyStateSnapshot();
    generation_ = gen;
    timestamp_ = timestamp;
}

void StateView::AttachRetainedSnapshotSlot(StateVault *owner, uint32_t slotIndex,
                                           const StateSnapshotPayload *payload, Generation gen,
                                           int64_t timestamp) {
    // 调用方已对 slotIndex 做过一次 Retain。即使是同槽位重入，也要先释放旧引用，
    // 再接管新引用，避免 refcount 泄漏导致快照池耗尽。
    ReleaseSnapshotSlot();
    ownerVault_ = owner;
    snapshotSlotIndex_ = slotIndex;
    snapshotData_ = payload != nullptr ? payload : &EmptyStateSnapshot();
    generation_ = gen;
    timestamp_ = timestamp;
}

void StateView::ReleaseSnapshotSlot() {
    if (ownerVault_ != nullptr && snapshotSlotIndex_ != kInvalidSnapshotSlot) {
        ownerVault_->ReleaseSnapshotSlot(snapshotSlotIndex_);
    }
    ownerVault_ = nullptr;
    snapshotSlotIndex_ = kInvalidSnapshotSlot;
    snapshotData_ = &EmptyStateSnapshot();
}

StateVault::StateVault() {
    generation_.store(0, std::memory_order_relaxed);
    syncEpoch_.store(0, std::memory_order_relaxed);
    committedTimestamp_.store(0, std::memory_order_relaxed);
    InitFreeBitmap(snapshotFreeBitmap_, kSnapshotPoolSize);
    InitFreeBitmap(constraintFreeBitmap_, kDomainSnapshotPoolSize);
    InitFreeBitmap(sceneFreeBitmap_, kDomainSnapshotPoolSize);
    InitFreeBitmap(intentFreeBitmap_, kDomainSnapshotPoolSize);
    InitFreeBitmap(leaseFreeBitmap_, kDomainSnapshotPoolSize);
    InitFreeBitmap(dependencyFreeBitmap_, kDomainSnapshotPoolSize);
    InitFreeBitmap(capabilityFreeBitmap_, kDomainSnapshotPoolSize);
    InitFreeBitmap(stabilityFreeBitmap_, kDomainSnapshotPoolSize);

    currentSnapshotSlot_.store(0, std::memory_order_relaxed);
    MarkSlotUsed(snapshotFreeBitmap_, 0);
    MarkSlotUsed(constraintFreeBitmap_, 0);
    MarkSlotUsed(sceneFreeBitmap_, 0);
    MarkSlotUsed(intentFreeBitmap_, 0);
    MarkSlotUsed(leaseFreeBitmap_, 0);
    MarkSlotUsed(dependencyFreeBitmap_, 0);
    MarkSlotUsed(capabilityFreeBitmap_, 0);
    MarkSlotUsed(stabilityFreeBitmap_, 0);

    constraintSnapshotPool_[0].refCount.store(1, std::memory_order_release);
    sceneSnapshotPool_[0].refCount.store(1, std::memory_order_release);
    intentSnapshotPool_[0].refCount.store(1, std::memory_order_release);
    leaseSnapshotPool_[0].refCount.store(1, std::memory_order_release);
    dependencySnapshotPool_[0].refCount.store(1, std::memory_order_release);
    capabilitySnapshotPool_[0].refCount.store(1, std::memory_order_release);
    stabilitySnapshotPool_[0].refCount.store(1, std::memory_order_release);

    snapshotPool_[0].payload.constraint = &constraintSnapshotPool_[0];
    snapshotPool_[0].payload.scene = &sceneSnapshotPool_[0];
    snapshotPool_[0].payload.intent = &intentSnapshotPool_[0];
    snapshotPool_[0].payload.lease = &leaseSnapshotPool_[0];
    snapshotPool_[0].payload.dependency = &dependencySnapshotPool_[0];
    snapshotPool_[0].payload.capability = &capabilitySnapshotPool_[0];
    snapshotPool_[0].payload.stability = &stabilitySnapshotPool_[0];
    snapshotPool_[0].parentSnapshotSlot = kInvalidSnapshotSlot;
    snapshotPool_[0].ownedBits.bits =
        DirtyBits::kConstraint | DirtyBits::kScene | DirtyBits::kIntent | DirtyBits::kLease |
        DirtyBits::kDependency | DirtyBits::kCapability | DirtyBits::kStability;
    snapshotPool_[0].chainDepth = 0;
    snapshotPool_[0].refCount.store(1, std::memory_order_release);
}

SnapshotToken StateVault::Pin() {
    return Pin(0);
}

SnapshotToken StateVault::Pin(int64_t deterministicTs) {
    const Generation generation = generation_.load(std::memory_order_acquire);
    const int64_t committedTs = committedTimestamp_.load(std::memory_order_acquire);
    return MakeSnapshotToken(generation, committedTs != 0 ? committedTs : deterministicTs);
}

StateView StateVault::Snapshot() {
    StateView view;
    (void)PinAndSnapshot(0, view);
    return view;
}

SnapshotToken StateVault::GetCommittedToken() const {
    for (;;) {
        const Generation genBefore = generation_.load(std::memory_order_acquire);
        const int64_t ts = committedTimestamp_.load(std::memory_order_acquire);
        const Generation genAfter = generation_.load(std::memory_order_acquire);
        if (genBefore == genAfter) {
            return MakeSnapshotToken(genAfter, ts);
        }
    }
}

SnapshotToken StateVault::PinAndSnapshot(int64_t deterministicTs, StateView &outView) {
    for (;;) {
        const int64_t committedTs = committedTimestamp_.load(std::memory_order_acquire);
        // 历史回放分支保留锁保护，主路径走乐观读以保障 P1 热路径时延。
        if (deterministicTs > 0 && committedTs != 0 && deterministicTs < committedTs) {
            ReadLockGuard guard(rwlock_);
            const HistoryEntry *entry = FindHistoryByTimestamp(deterministicTs);
            if (entry && entry->snapshotSlot != kInvalidSnapshotSlot) {
                outView.AttachSnapshotSlot(this, entry->snapshotSlot,
                                           &snapshotPool_[entry->snapshotSlot].payload,
                                           entry->generation, entry->timestamp);
                return MakeSnapshotToken(entry->generation, entry->timestamp);
            }
            const Generation lockedGeneration = generation_.load(std::memory_order_acquire);
            const int64_t lockedSnapshotTs = committedTs != 0 ? committedTs : deterministicTs;
            const uint32_t lockedSlot = currentSnapshotSlot_.load(std::memory_order_acquire);
            outView.AttachSnapshotSlot(this, lockedSlot, &snapshotPool_[lockedSlot].payload,
                                       lockedGeneration, lockedSnapshotTs);
            return MakeSnapshotToken(lockedGeneration, lockedSnapshotTs);
        }

        const Generation genBefore = generation_.load(std::memory_order_acquire);
        const uint32_t slot = currentSnapshotSlot_.load(std::memory_order_acquire);
        if (slot == kInvalidSnapshotSlot || !TryRetainSnapshotSlot(slot)) {
            continue;
        }

        const Generation genAfter = generation_.load(std::memory_order_acquire);
        const uint32_t slotAfter = currentSnapshotSlot_.load(std::memory_order_acquire);
        if (genBefore != genAfter || slot != slotAfter) {
            ReleaseSnapshotSlot(slot);
            continue;
        }

        const int64_t snapshotTs = committedTs != 0 ? committedTs : deterministicTs;
        outView.AttachRetainedSnapshotSlot(this, slot, &snapshotPool_[slot].payload, genAfter,
                                           snapshotTs);
        return MakeSnapshotToken(genAfter, snapshotTs);
    }
}

void StateVault::BeginTransaction() {
    WriteLockGuard guard(rwlock_);
    if (transaction_.active) {
        return;
    }

    ResetTransactionUnlocked();
    transaction_.generation = generation_.load(std::memory_order_acquire);
    transaction_.dirtyBits = dirtyBits_;
    transaction_.active = true;
}

bool StateVault::BeginTransaction(Generation expectedGen) {
    WriteLockGuard guard(rwlock_);
    const Generation currentGen = generation_.load(std::memory_order_acquire);
    if (currentGen != expectedGen) {
        return false;
    }
    if (transaction_.active) {
        return false;
    }

    ResetTransactionUnlocked();
    transaction_.generation = currentGen;
    transaction_.dirtyBits = dirtyBits_;
    transaction_.active = true;
    return true;
}

TransactionResult StateVault::CommitTransactionCas() {
    TransactionResult result;
    WriteLockGuard guard(rwlock_);

    if (!transaction_.active) {
        result.success = false;
        result.conflict = false;
        return result;
    }

    const Generation expectedGen = transaction_.generation;
    const Generation currentGen = generation_.load(std::memory_order_acquire);
    result.expectedGen = expectedGen;
    result.actualGen = currentGen;

    if (currentGen != expectedGen) {
        result.success = false;
        result.conflict = true;
        ResetTransactionUnlocked();
        return result;
    }

    if (transaction_.pendingBump) {
        result.actualGen = expectedGen + 1;
        CommitStagedUpdateUnlocked(BuildTransactionPatch(), transaction_.dirtyBits);
    }

    ResetTransactionUnlocked();
    result.success = true;
    result.conflict = false;
    return result;
}

bool StateVault::CommitTransactionUnlocked() noexcept {
    if (!transaction_.active) {
        return false;
    }
    if (transaction_.pendingBump) {
        CommitStagedUpdateUnlocked(BuildTransactionPatch(), transaction_.dirtyBits);
    }
    ResetTransactionUnlocked();
    return true;
}

bool StateVault::CommitTransaction() {
    WriteLockGuard guard(rwlock_);
    return CommitTransactionUnlocked();
}

void StateVault::RollbackTransaction() {
    WriteLockGuard guard(rwlock_);
    ResetTransactionUnlocked();
}

void StateVault::UpdateConstraint(const ConstraintSnapshot &snapshot) {
    WriteLockGuard guard(rwlock_);
    if (transaction_.active) {
        // 等值抑制：避免重复候选写入推进 generation / history。
        if (transaction_.stagedBits.HasConstraint()) {
            if (CanonicalConstraintSnapshotEquals(transaction_.constraint.snapshot, snapshot)) {
                return;    // no-op
            }
        } else {
            if (CanonicalConstraintSnapshotEquals(CurrentConstraintState().snapshot, snapshot)) {
                return;    // no-op
            }
        }

        auto &constraint = EnsureTransactionConstraintStaged();
        constraint.snapshot = snapshot;
        transaction_.dirtyBits.SetConstraint();
        transaction_.pendingBump = true;
        return;
    }

    // 等值抑制：相同 canonical snapshot 不推进 generation / history。
    if (CanonicalConstraintSnapshotEquals(CurrentConstraintState().snapshot, snapshot)) {
        return;    // no-op
    }

    ConstraintState constraint{};
    constraint.snapshot = snapshot;
    StateUpdatePatch patch;
    patch.constraint = &constraint;
    DirtyBits nextDirtyBits = dirtyBits_;
    nextDirtyBits.SetConstraint();
    CommitStagedUpdateUnlocked(patch, nextDirtyBits);
}

void StateVault::UpdateScene(const SceneSemantic &semantic, SceneId sceneId) {
    WriteLockGuard guard(rwlock_);
    // 热路径需要“无效更新也不写回”：语义等值则保持 generation/history 不变。
    if (transaction_.active) {
        if (transaction_.stagedBits.HasScene()) {
            if (SceneSemanticEquals(transaction_.scene.semantic, semantic) &&
                transaction_.scene.activeSceneId == sceneId) {
                return;    // no-op: keep generation and history unchanged
            }
        } else {
            const auto &current = CurrentSceneState();
            if (SceneSemanticEquals(current.semantic, semantic) &&
                current.activeSceneId == sceneId) {
                return;    // no-op: keep generation and history unchanged
            }
        }

        auto &scene = EnsureTransactionSceneStaged();
        scene.semantic = semantic;
        scene.activeSceneId = sceneId;
        transaction_.dirtyBits.SetScene();
        transaction_.pendingBump = true;
        return;
    }

    const auto &current = CurrentSceneState();
    if (SceneSemanticEquals(current.semantic, semantic) && current.activeSceneId == sceneId) {
        return;    // no-op
    }

    SceneState scene{};
    scene.semantic = semantic;
    scene.activeSceneId = sceneId;
    StateUpdatePatch patch;
    patch.scene = &scene;
    DirtyBits nextDirtyBits = dirtyBits_;
    nextDirtyBits.SetScene();
    CommitStagedUpdateUnlocked(patch, nextDirtyBits);
}

void StateVault::UpdateIntent(const IntentContract &contract, SessionId boundSession) {
    WriteLockGuard guard(rwlock_);
    // intent 既包含 contract，也包含绑定 sessionId（影响后续租约/释放与 replay 追踪键）。
    // 因此等值抑制必须在（contract, activeSessionId）维度上完成。
    if (transaction_.active) {
        const SessionId newActiveSessionId =
            (boundSession != 0) ? boundSession : PeekActiveSessionIdFromCurrentSlot();

        if (transaction_.stagedBits.HasIntent()) {
            if (IntentContractEquals(transaction_.intent.contract, contract) &&
                transaction_.intent.activeSessionId == newActiveSessionId) {
                return;    // no-op
            }
        } else {
            const auto &current = CurrentIntentState();
            if (IntentContractEquals(current.contract, contract) &&
                current.activeSessionId == newActiveSessionId) {
                return;    // no-op
            }
        }

        auto &intent = EnsureTransactionIntentStaged();
        intent.contract = contract;
        intent.activeSessionId = newActiveSessionId;
        transaction_.dirtyBits.SetIntent();
        transaction_.pendingBump = true;
        return;
    }

    const SessionId newActiveSessionId = (boundSession != 0) ? boundSession
                                                             : PeekActiveSessionIdFromCurrentSlot();

    const auto &current = CurrentIntentState();
    if (IntentContractEquals(current.contract, contract) &&
        current.activeSessionId == newActiveSessionId) {
        return;    // no-op
    }

    IntentState intent{};
    intent.contract = contract;
    intent.activeSessionId = newActiveSessionId;
    StateUpdatePatch patch;
    patch.intent = &intent;
    DirtyBits nextDirtyBits = dirtyBits_;
    nextDirtyBits.SetIntent();
    CommitStagedUpdateUnlocked(patch, nextDirtyBits);
}

void StateVault::UpdateLease(const TemporalContract &contract, LeaseId leaseId) {
    WriteLockGuard guard(rwlock_);
    // lease 与 intent 共享相同绑定主键（session/lease），用于保证 ReleaseSession 的线性化观测。
    // 当（TemporalContract, activeLeaseId）完全一致时，跳过写回以避免稳态 generation/history 膨胀。
    if (transaction_.active) {
        if (transaction_.stagedBits.HasLease()) {
            if (TemporalContractEquals(transaction_.lease.contract, contract) &&
                transaction_.lease.activeLeaseId == leaseId) {
                return;    // no-op
            }
        } else {
            const auto &current = CurrentLeaseState();
            if (TemporalContractEquals(current.contract, contract) &&
                current.activeLeaseId == leaseId) {
                return;    // no-op
            }
        }

        auto &lease = EnsureTransactionLeaseStaged();
        lease.contract = contract;
        lease.activeLeaseId = leaseId;
        transaction_.dirtyBits.SetLease();
        transaction_.pendingBump = true;
        return;
    }

    const auto &current = CurrentLeaseState();
    if (TemporalContractEquals(current.contract, contract) && current.activeLeaseId == leaseId) {
        return;    // no-op
    }

    LeaseState lease{};
    lease.contract = contract;
    lease.activeLeaseId = leaseId;
    StateUpdatePatch patch;
    patch.lease = &lease;
    DirtyBits nextDirtyBits = dirtyBits_;
    nextDirtyBits.SetLease();
    CommitStagedUpdateUnlocked(patch, nextDirtyBits);
}

void StateVault::UpdateDependency(const DependencyState &dep) {
    WriteLockGuard guard(rwlock_);
    if (transaction_.active) {
        EnsureTransactionDependencyStaged() = dep;
        transaction_.dirtyBits.SetDependency();
        transaction_.pendingBump = true;
        return;
    }

    StateUpdatePatch patch;
    patch.dependency = &dep;
    DirtyBits nextDirtyBits = dirtyBits_;
    nextDirtyBits.SetDependency();
    CommitStagedUpdateUnlocked(patch, nextDirtyBits);
}

void StateVault::UpdateCapability(const CapabilitySnapshot &snapshot) {
    WriteLockGuard guard(rwlock_);
    if (transaction_.active) {
        // 等值抑制：避免重复候选写入推进 generation / history。
        if (transaction_.stagedBits.HasCapability()) {
            if (CanonicalCapabilitySnapshotEquals(transaction_.capability.snapshot, snapshot)) {
                return;    // no-op
            }
        } else {
            if (CanonicalCapabilitySnapshotEquals(CurrentCapabilityState().snapshot, snapshot)) {
                return;    // no-op
            }
        }

        auto &capability = EnsureTransactionCapabilityStaged();
        capability.snapshot = snapshot;
        transaction_.dirtyBits.SetCapability();
        transaction_.pendingBump = true;
        return;
    }

    // 等值抑制：相同 canonical snapshot 不推进 generation / history。
    if (CanonicalCapabilitySnapshotEquals(CurrentCapabilityState().snapshot, snapshot)) {
        return;    // no-op
    }

    CapabilityState capability{};
    capability.snapshot = snapshot;
    StateUpdatePatch patch;
    patch.capability = &capability;
    DirtyBits nextDirtyBits = dirtyBits_;
    nextDirtyBits.SetCapability();
    CommitStagedUpdateUnlocked(patch, nextDirtyBits);
}

void StateVault::UpdateStability(const StabilityState &stability) {
    WriteLockGuard guard(rwlock_);
    // stability 写入频率高（稳态观察窗口内），必须保持幂等：
    // payload 无变化则不推进 generation / 不生成新的 history snapshot。
    if (transaction_.active) {
        if (transaction_.stagedBits.HasStability()) {
            if (StabilityStateEquals(transaction_.stability, stability)) {
                return;    // no-op
            }
        } else {
            const auto &current = CurrentStabilityState();
            if (StabilityStateEquals(current, stability)) {
                return;    // no-op
            }
        }

        EnsureTransactionStabilityStaged() = stability;
        transaction_.dirtyBits.SetStability();
        transaction_.pendingBump = true;
        return;
    }

    const auto &current = CurrentStabilityState();
    if (StabilityStateEquals(current, stability)) {
        return;    // no-op
    }

    StateUpdatePatch patch;
    patch.stability = &stability;
    DirtyBits nextDirtyBits = dirtyBits_;
    nextDirtyBits.SetStability();
    CommitStagedUpdateUnlocked(patch, nextDirtyBits);
}

void StateVault::UpdateConstraintAndCapability(const ConstraintSnapshot &constraintSnapshot,
                                               const CapabilitySnapshot &capabilitySnapshot) {
    WriteLockGuard guard(rwlock_);
    if (transaction_.active) {
        bool constraintSame = false;
        bool capabilitySame = false;

        if (transaction_.stagedBits.HasConstraint()) {
            constraintSame = CanonicalConstraintSnapshotEquals(transaction_.constraint.snapshot,
                                                               constraintSnapshot);
        } else {
            constraintSame = CanonicalConstraintSnapshotEquals(CurrentConstraintState().snapshot,
                                                               constraintSnapshot);
        }

        if (transaction_.stagedBits.HasCapability()) {
            capabilitySame = CanonicalCapabilitySnapshotEquals(transaction_.capability.snapshot,
                                                               capabilitySnapshot);
        } else {
            capabilitySame = CanonicalCapabilitySnapshotEquals(CurrentCapabilityState().snapshot,
                                                               capabilitySnapshot);
        }

        if (constraintSame && capabilitySame) {
            return;    // no-op
        }

        if (!constraintSame) {
            auto &constraint = EnsureTransactionConstraintStaged();
            constraint.snapshot = constraintSnapshot;
            transaction_.dirtyBits.SetConstraint();
        }

        if (!capabilitySame) {
            auto &capability = EnsureTransactionCapabilityStaged();
            capability.snapshot = capabilitySnapshot;
            transaction_.dirtyBits.SetCapability();
        }

        transaction_.pendingBump = true;
        return;
    }

    const bool constraintSame =
        CanonicalConstraintSnapshotEquals(CurrentConstraintState().snapshot, constraintSnapshot);
    const bool capabilitySame =
        CanonicalCapabilitySnapshotEquals(CurrentCapabilityState().snapshot, capabilitySnapshot);

    if (constraintSame && capabilitySame) {
        return;    // no-op
    }

    ConstraintState constraint{};
    constraint.snapshot = constraintSnapshot;
    CapabilityState capability{};
    capability.snapshot = capabilitySnapshot;
    StateUpdatePatch patch;
    patch.constraint = constraintSame ? nullptr : &constraint;
    patch.capability = capabilitySame ? nullptr : &capability;

    DirtyBits nextDirtyBits = dirtyBits_;
    if (!constraintSame) {
        nextDirtyBits.SetConstraint();
    }
    if (!capabilitySame) {
        nextDirtyBits.SetCapability();
    }
    CommitStagedUpdateUnlocked(patch, nextDirtyBits);
}

void StateVault::ReleaseSession(SessionId sessionId) {
    WriteLockGuard guard(rwlock_);
    if (transaction_.active) {
        bool changed = false;
        if (GetTransactionLeaseState().activeLeaseId == sessionId) {
            EnsureTransactionLeaseStaged() = LeaseState{};
            transaction_.dirtyBits.SetLease();
            changed = true;
        }
        if (GetTransactionIntentState().activeSessionId == sessionId && sessionId != 0) {
            EnsureTransactionIntentStaged() = IntentState{};
            transaction_.dirtyBits.SetIntent();
            changed = true;
        }
        if (changed) {
            transaction_.pendingBump = true;
        }
        return;
    }

    bool changed = false;
    LeaseState resetLease{};
    IntentState resetIntent{};
    StateUpdatePatch patch;
    bool hasStagedLease = false;
    bool hasStagedIntent = false;
    {
        const uint32_t slot = currentSnapshotSlot_.load(std::memory_order_acquire);
        const auto &leasePayload = snapshotPool_[slot].payload.lease->payload;
        const auto &intentPayload = snapshotPool_[slot].payload.intent->payload;
        if (leasePayload.activeLeaseId == sessionId) {
            patch.lease = &resetLease;
            changed = true;
            hasStagedLease = true;
        }
        if (intentPayload.activeSessionId == sessionId && sessionId != 0) {
            patch.intent = &resetIntent;
            changed = true;
            hasStagedIntent = true;
        }
    }
    if (changed) {
        DirtyBits nextDirtyBits = dirtyBits_;
        if (hasStagedLease) {
            nextDirtyBits.SetLease();
        }
        if (hasStagedIntent) {
            nextDirtyBits.SetIntent();
        }
        CommitStagedUpdateUnlocked(patch, nextDirtyBits);
    }
}

void StateVault::MarkClean() {
    WriteLockGuard guard(rwlock_);
    dirtyBits_.Clear();
    if (transaction_.active) {
        transaction_.dirtyBits = dirtyBits_;
    }
}

void StateVault::SyncCommitBarrier() noexcept {
    WriteLockGuard guard(rwlock_);
    dirtyBits_.Clear();
    if (transaction_.active) {
        transaction_.dirtyBits = dirtyBits_;
    }
    syncEpoch_.fetch_add(1, std::memory_order_release);
}

DirtyBits StateVault::GetDirtyBits() const {
    return dirtyBits_;
}

void StateVault::CommitStagedUpdateUnlocked(const StateUpdatePatch &patch,
                                            const DirtyBits &nextDirtyBits) {
    const Generation nextGeneration = generation_.load(std::memory_order_acquire) + 1;
    dirtyBits_ = nextDirtyBits;
    generation_.store(nextGeneration, std::memory_order_release);
    SaveToHistory(patch, nextGeneration);
}

void StateVault::SaveToHistory(const StateUpdatePatch &patch, Generation generation) {
    const uint32_t snapshotSlot = AcquireSnapshotSlotLocked();
    uint32_t slot = historyHead_.fetch_add(1, std::memory_order_acq_rel) % kHistoryBufferSize;
    const int64_t timestamp = static_cast<int64_t>(SystemTime::GetDeterministicNs());
    const uint32_t previousCurrentSlot = currentSnapshotSlot_.load(std::memory_order_relaxed);
    const StateSnapshotPayload &prevSnapshot = snapshotPool_[previousCurrentSlot].payload;

    auto &snapshot = snapshotPool_[snapshotSlot].payload;
    auto &snapshotMeta = snapshotPool_[snapshotSlot];
    snapshotMeta.ownedBits.Clear();
    const bool flattenSnapshot =
        previousCurrentSlot == kInvalidSnapshotSlot ||
        snapshotPool_[previousCurrentSlot].chainDepth >= (kMaxSnapshotChainDepth - 1);
    snapshot = prevSnapshot;
    if (!flattenSnapshot && previousCurrentSlot != kInvalidSnapshotSlot) {
        snapshotMeta.parentSnapshotSlot = previousCurrentSlot;
        snapshotMeta.chainDepth = snapshotPool_[previousCurrentSlot].chainDepth + 1;
        RetainSnapshotSlot(previousCurrentSlot);
    } else {
        snapshotMeta.parentSnapshotSlot = kInvalidSnapshotSlot;
        snapshotMeta.chainDepth = 0;
        snapshotMeta.ownedBits.bits =
            DirtyBits::kConstraint | DirtyBits::kScene | DirtyBits::kIntent | DirtyBits::kLease |
            DirtyBits::kDependency | DirtyBits::kCapability | DirtyBits::kStability;
        RetainDomainSlot(snapshot.constraint);
        RetainDomainSlot(snapshot.scene);
        RetainDomainSlot(snapshot.intent);
        RetainDomainSlot(snapshot.lease);
        RetainDomainSlot(snapshot.dependency);
        RetainDomainSlot(snapshot.capability);
        RetainDomainSlot(snapshot.stability);
    }

    if (patch.constraint != nullptr) {
        if (flattenSnapshot) {
            ReleaseDomainSlot(snapshot.constraint, constraintSnapshotPool_, constraintFreeBitmap_);
        }
        snapshot.constraint =
            CreateDomainSnapshotSlot(*patch.constraint, constraintSnapshotPool_,
                                     constraintFreeBitmap_, nextConstraintSlotHint_);
        snapshotMeta.ownedBits.SetConstraint();
    }
    if (patch.scene != nullptr) {
        if (flattenSnapshot) {
            ReleaseDomainSlot(snapshot.scene, sceneSnapshotPool_, sceneFreeBitmap_);
        }
        snapshot.scene = CreateDomainSnapshotSlot(*patch.scene, sceneSnapshotPool_,
                                                  sceneFreeBitmap_, nextSceneSlotHint_);
        snapshotMeta.ownedBits.SetScene();
    }
    if (patch.intent != nullptr) {
        if (flattenSnapshot) {
            ReleaseDomainSlot(snapshot.intent, intentSnapshotPool_, intentFreeBitmap_);
        }
        snapshot.intent = CreateDomainSnapshotSlot(*patch.intent, intentSnapshotPool_,
                                                   intentFreeBitmap_, nextIntentSlotHint_);
        snapshotMeta.ownedBits.SetIntent();
    }
    if (patch.lease != nullptr) {
        if (flattenSnapshot) {
            ReleaseDomainSlot(snapshot.lease, leaseSnapshotPool_, leaseFreeBitmap_);
        }
        snapshot.lease = CreateDomainSnapshotSlot(*patch.lease, leaseSnapshotPool_,
                                                  leaseFreeBitmap_, nextLeaseSlotHint_);
        snapshotMeta.ownedBits.SetLease();
    }
    if (patch.dependency != nullptr) {
        if (flattenSnapshot) {
            ReleaseDomainSlot(snapshot.dependency, dependencySnapshotPool_, dependencyFreeBitmap_);
        }
        snapshot.dependency =
            CreateDomainSnapshotSlot(*patch.dependency, dependencySnapshotPool_,
                                     dependencyFreeBitmap_, nextDependencySlotHint_);
        snapshotMeta.ownedBits.SetDependency();
    }
    if (patch.capability != nullptr) {
        if (flattenSnapshot) {
            ReleaseDomainSlot(snapshot.capability, capabilitySnapshotPool_, capabilityFreeBitmap_);
        }
        snapshot.capability =
            CreateDomainSnapshotSlot(*patch.capability, capabilitySnapshotPool_,
                                     capabilityFreeBitmap_, nextCapabilitySlotHint_);
        snapshotMeta.ownedBits.SetCapability();
    }
    if (patch.stability != nullptr) {
        if (flattenSnapshot) {
            ReleaseDomainSlot(snapshot.stability, stabilitySnapshotPool_, stabilityFreeBitmap_);
        }
        snapshot.stability = CreateDomainSnapshotSlot(*patch.stability, stabilitySnapshotPool_,
                                                      stabilityFreeBitmap_, nextStabilitySlotHint_);
        snapshotMeta.ownedBits.SetStability();
    }
    snapshotMeta.refCount.store(1, std::memory_order_release);
    currentSnapshotSlot_.store(snapshotSlot, std::memory_order_release);

    if (historyBuffer_[slot].snapshotSlot != kInvalidSnapshotSlot) {
        ReleaseSnapshotSlot(historyBuffer_[slot].snapshotSlot);
    }
    historyBuffer_[slot].snapshotSlot = snapshotSlot;
    historyBuffer_[slot].generation = generation;
    historyBuffer_[slot].timestamp = timestamp;
    RetainSnapshotSlot(snapshotSlot);
    committedTimestamp_.store(timestamp, std::memory_order_release);

    if (previousCurrentSlot != kInvalidSnapshotSlot) {
        ReleaseSnapshotSlot(previousCurrentSlot);
    }

    uint32_t count = historyCount_.load(std::memory_order_relaxed);
    if (count < kHistoryBufferSize) {
        historyCount_.compare_exchange_strong(count, count + 1, std::memory_order_acq_rel);
    }
}

void StateVault::ResetTransactionUnlocked() noexcept {
    transaction_.generation = 0;
    transaction_.dirtyBits.Clear();
    transaction_.stagedBits.Clear();
    transaction_.active = false;
    transaction_.pendingBump = false;
    transaction_.txnPayloadView_ = nullptr;
}

ConstraintState &StateVault::EnsureTransactionConstraintStaged() {
    if (!transaction_.stagedBits.HasConstraint()) {
        const auto &p = TxnPinnedSnapshotPayload();
        transaction_.constraint = p.constraint->payload;
        transaction_.stagedBits.SetConstraint();
    }
    return transaction_.constraint;
}

SceneState &StateVault::EnsureTransactionSceneStaged() {
    if (!transaction_.stagedBits.HasScene()) {
        const auto &p = TxnPinnedSnapshotPayload();
        transaction_.scene = p.scene->payload;
        transaction_.stagedBits.SetScene();
    }
    return transaction_.scene;
}

IntentState &StateVault::EnsureTransactionIntentStaged() {
    if (!transaction_.stagedBits.HasIntent()) {
        const auto &p = TxnPinnedSnapshotPayload();
        transaction_.intent = p.intent->payload;
        transaction_.stagedBits.SetIntent();
    }
    return transaction_.intent;
}

LeaseState &StateVault::EnsureTransactionLeaseStaged() {
    if (!transaction_.stagedBits.HasLease()) {
        const auto &p = TxnPinnedSnapshotPayload();
        transaction_.lease = p.lease->payload;
        transaction_.stagedBits.SetLease();
    }
    return transaction_.lease;
}

DependencyState &StateVault::EnsureTransactionDependencyStaged() {
    if (!transaction_.stagedBits.HasDependency()) {
        const auto &p = TxnPinnedSnapshotPayload();
        transaction_.dependency = p.dependency->payload;
        transaction_.stagedBits.SetDependency();
    }
    return transaction_.dependency;
}

CapabilityState &StateVault::EnsureTransactionCapabilityStaged() {
    if (!transaction_.stagedBits.HasCapability()) {
        const auto &p = TxnPinnedSnapshotPayload();
        transaction_.capability = p.capability->payload;
        transaction_.stagedBits.SetCapability();
    }
    return transaction_.capability;
}

StabilityState &StateVault::EnsureTransactionStabilityStaged() {
    if (!transaction_.stagedBits.HasStability()) {
        const auto &p = TxnPinnedSnapshotPayload();
        transaction_.stability = p.stability->payload;
        transaction_.stagedBits.SetStability();
    }
    return transaction_.stability;
}

const IntentState &StateVault::GetTransactionIntentState() const {
    if (transaction_.stagedBits.HasIntent()) {
        return transaction_.intent;
    }
    const auto &p = TxnPinnedSnapshotPayload();
    return p.intent->payload;
}

const LeaseState &StateVault::GetTransactionLeaseState() const {
    if (transaction_.stagedBits.HasLease()) {
        return transaction_.lease;
    }
    const auto &p = TxnPinnedSnapshotPayload();
    return p.lease->payload;
}

StateUpdatePatch StateVault::BuildTransactionPatch() const {
    StateUpdatePatch patch;
    if (transaction_.stagedBits.HasConstraint()) {
        patch.constraint = &transaction_.constraint;
    }
    if (transaction_.stagedBits.HasScene()) {
        patch.scene = &transaction_.scene;
    }
    if (transaction_.stagedBits.HasIntent()) {
        patch.intent = &transaction_.intent;
    }
    if (transaction_.stagedBits.HasLease()) {
        patch.lease = &transaction_.lease;
    }
    if (transaction_.stagedBits.HasDependency()) {
        patch.dependency = &transaction_.dependency;
    }
    if (transaction_.stagedBits.HasCapability()) {
        patch.capability = &transaction_.capability;
    }
    if (transaction_.stagedBits.HasStability()) {
        patch.stability = &transaction_.stability;
    }
    return patch;
}

const HistoryEntry *StateVault::FindHistoryByTimestamp(int64_t timestamp) const {
    const uint32_t count = historyCount_.load(std::memory_order_acquire);
    const uint32_t head = historyHead_.load(std::memory_order_acquire);

    for (uint32_t i = 0; i < count; ++i) {
        const uint32_t slot = (head + kHistoryBufferSize - i - 1) % kHistoryBufferSize;
        if (historyBuffer_[slot].timestamp <= timestamp) {
            return &historyBuffer_[slot];
        }
    }

    return nullptr;
}

uint32_t StateVault::AcquireSnapshotSlotLocked() {
    return AcquireFreeSlot(snapshotFreeBitmap_, static_cast<uint32_t>(kSnapshotPoolSize),
                           nextSnapshotSlotHint_);
}

bool StateVault::TryRetainSnapshotSlot(uint32_t slotIndex) {
    if (slotIndex == kInvalidSnapshotSlot) {
        return false;
    }
    auto &ref = snapshotPool_[slotIndex].refCount;
    uint32_t current = ref.load(std::memory_order_acquire);
    while (current != 0u) {
        if (ref.compare_exchange_weak(current, current + 1u, std::memory_order_acq_rel,
                                      std::memory_order_acquire)) {
            return true;
        }
    }
    return false;
}

void StateVault::RetainSnapshotSlot(uint32_t slotIndex) {
    if (slotIndex != kInvalidSnapshotSlot) {
        snapshotPool_[slotIndex].refCount.fetch_add(1, std::memory_order_acq_rel);
    }
}

void StateVault::ReleaseSnapshotSlot(uint32_t slotIndex) {
    while (slotIndex != kInvalidSnapshotSlot) {
        auto &slot = snapshotPool_[slotIndex];
        if (slot.refCount.fetch_sub(1, std::memory_order_acq_rel) != 1u) {
            return;
        }

        const uint32_t parentSlot = slot.parentSnapshotSlot;
        const DirtyBits ownedBits = slot.ownedBits;
        if (ownedBits.HasConstraint()) {
            ReleaseDomainSlot(slot.payload.constraint, constraintSnapshotPool_,
                              constraintFreeBitmap_);
        }
        if (ownedBits.HasScene()) {
            ReleaseDomainSlot(slot.payload.scene, sceneSnapshotPool_, sceneFreeBitmap_);
        }
        if (ownedBits.HasIntent()) {
            ReleaseDomainSlot(slot.payload.intent, intentSnapshotPool_, intentFreeBitmap_);
        }
        if (ownedBits.HasLease()) {
            ReleaseDomainSlot(slot.payload.lease, leaseSnapshotPool_, leaseFreeBitmap_);
        }
        if (ownedBits.HasDependency()) {
            ReleaseDomainSlot(slot.payload.dependency, dependencySnapshotPool_,
                              dependencyFreeBitmap_);
        }
        if (ownedBits.HasCapability()) {
            ReleaseDomainSlot(slot.payload.capability, capabilitySnapshotPool_,
                              capabilityFreeBitmap_);
        }
        if (ownedBits.HasStability()) {
            ReleaseDomainSlot(slot.payload.stability, stabilitySnapshotPool_, stabilityFreeBitmap_);
        }
        ResetSnapshotSlotState(slot);
        MarkSlotFree(snapshotFreeBitmap_, slotIndex);
        slotIndex = parentSlot;
    }
}

const StateSnapshotPayload *StateVault::GetSnapshotPayload(uint32_t slotIndex) const {
    return slotIndex == kInvalidSnapshotSlot ? &EmptyStateSnapshot()
                                             : &snapshotPool_[slotIndex].payload;
}

const StateSnapshotPayload &StateVault::CurrentSnapshotPayload() const {
    const uint32_t slot = currentSnapshotSlot_.load(std::memory_order_acquire);
    if (slot == kInvalidSnapshotSlot || slot >= kSnapshotPoolSize) {
        return EmptyStateSnapshot();
    }
    return snapshotPool_[slot].payload;
}

const StateSnapshotPayload &StateVault::TxnPinnedSnapshotPayload() const {
    if (transaction_.txnPayloadView_) {
        return *transaction_.txnPayloadView_;
    }
    const uint32_t slot = currentSnapshotSlot_.load(std::memory_order_acquire);
    if (slot == kInvalidSnapshotSlot || slot >= kSnapshotPoolSize) {
        return EmptyStateSnapshot();
    }
    transaction_.txnPayloadView_ = &snapshotPool_[slot].payload;
    return *transaction_.txnPayloadView_;
}

const ConstraintState &StateVault::CurrentConstraintState() const {
    return CurrentSnapshotPayload().constraint->payload;
}

const SceneState &StateVault::CurrentSceneState() const {
    return CurrentSnapshotPayload().scene->payload;
}

const IntentState &StateVault::CurrentIntentState() const {
    return CurrentSnapshotPayload().intent->payload;
}

SessionId StateVault::PeekActiveSessionIdFromCurrentSlot() const noexcept {
    return CurrentSnapshotPayload().intent->payload.activeSessionId;
}

SessionId StateVault::ReadActiveSessionId() const {
    ReadLockGuard guard(rwlock_);
    return PeekActiveSessionIdFromCurrentSlot();
}

const LeaseState &StateVault::CurrentLeaseState() const {
    return CurrentSnapshotPayload().lease->payload;
}

const DependencyState &StateVault::CurrentDependencyState() const {
    return CurrentSnapshotPayload().dependency->payload;
}

const CapabilityState &StateVault::CurrentCapabilityState() const {
    return CurrentSnapshotPayload().capability->payload;
}

const StabilityState &StateVault::CurrentStabilityState() const {
    return CurrentSnapshotPayload().stability->payload;
}

StateVault::CurrentStateRefs StateVault::GetCurrentStateRefs() const {
    ReadLockGuard guard(rwlock_);
    const StateSnapshotPayload &payload = CurrentSnapshotPayload();
    if (&payload == &EmptyStateSnapshot()) {
        return CurrentStateRefs{};
    }
    CurrentStateRefs refs{};
    refs.constraint = payload.constraint ? &payload.constraint->payload : nullptr;
    refs.scene = payload.scene ? &payload.scene->payload : nullptr;
    refs.intent = payload.intent ? &payload.intent->payload : nullptr;
    refs.lease = payload.lease ? &payload.lease->payload : nullptr;
    refs.dependency = payload.dependency ? &payload.dependency->payload : nullptr;
    refs.capability = payload.capability ? &payload.capability->payload : nullptr;
    refs.stability = payload.stability ? &payload.stability->payload : nullptr;
    return refs;
}
}    // namespace vendor::transsion::perfengine::dse
