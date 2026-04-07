#pragma once

/**
 * @file StateDomains.h
 * @brief DSE 状态域定义
 *
 * 本文件定义了整机资源确定性调度抽象框架的状态域：
 * - 约束状态 (ConstraintState): 环境约束状态
 * - 场景状态 (SceneState): 场景识别状态
 * - 意图状态 (IntentState): 意图契约状态
 * - 租约状态 (LeaseState): 时间契约状态
 * - 依赖状态 (DependencyState): 意图继承依赖状态
 * - 能力状态 (CapabilityState): 平台能力状态
 * - 稳定化状态 (StabilityState): 迁移稳定化状态
 * - 脏标记 (DirtyBits): 状态变更追踪
 *
 * 状态域设计原则：
 * 1. 统一由 StateVault generation / snapshotToken 负责版本推进
 * 2. 状态变更通过 DirtyBits 追踪
 * 3. 支持原子化读取和更新
 *
 * @see docs/整机资源确定性调度抽象框架.md §5 状态管理
 */

#include <cstdint>

#include "core/types/ConstraintTypes.h"
#include "core/types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @struct ConstraintState
 * @brief 约束状态：环境约束状态
 *
 * ConstraintState 存储当前系统的约束边界：
 * - snapshot: 约束快照，包含热/电/内存/显示等 canonical 输入
 *
 * 约束类型：
 * - 热约束：温度等级、降频状态
 * - 电约束：电量等级、省电模式
 * - 内存约束：内存压力、PSI 等级
 * - 显示约束：熄屏状态
 *
 * @note 约束维度始终高于意图等级
 * @see 框架 §2.5 约束维度
 */
struct ConstraintState {
    ConstraintSnapshot snapshot;    ///< 约束快照
};

/**
 * @struct SceneState
 * @brief 场景状态：场景识别状态
 *
 * SceneState 存储当前活跃场景的信息：
 * - activeSceneId: 当前活跃场景标识
 * - semantic: 场景语义（类型、阶段、感知状态）
 *
 * 场景生命周期：
 * 1. Enter: 场景进入，可能触发 P1 保障
 * 2. Active: 场景活跃，进入稳态（P2/P3）
 * 3. Exit: 场景退出，资源契约衰减
 *
 * @see 框架 §2.2 场景识别
 */
struct SceneState {
    SceneId activeSceneId = 0;    ///< 当前活跃场景标识
    SceneSemantic semantic;       ///< 场景语义
};

/**
 * @struct IntentState
 * @brief 意图状态：意图契约状态
 *
 * IntentState 存储当前工作负载的意图契约：
 * - contract: 意图契约（P1~P4 等级）
 *
 * 意图等级迁移：
 * - P1 → P2: 启动完成后进入连续感知
 * - P2 → P3: 主链路退出后进入弹性可见
 * - P3 → P4: 界面不可见后进入后台
 *
 * @note 意图等级描述资源交付契约，而非进程优先级
 * @see 框架 §2.3 意图等级
 */
struct IntentState {
    SessionId activeSessionId =
        0;    ///< 绑定的调度会话（与 SchedEvent.sessionId 对齐，供契约释放）
    IntentContract contract;    ///< 意图契约
};

/**
 * @struct LeaseState
 * @brief 租约状态：时间契约状态
 *
 * LeaseState 存储资源租约的时间信息：
 * - activeLeaseId: 当前活跃租约标识
 * - contract: 时间契约（租约时间、衰减因子）
 *
 * 租约类型：
 * - Burst: 短租约（≤2s），快速衰减
 * - Steady: 长租约，支持续租
 * - Intermittent: 按需续租
 * - Deferred: 无租约
 *
 * @see 框架 §2.4 时间维度
 */
struct LeaseState {
    LeaseId activeLeaseId = 0;    ///< 当前活跃租约标识
    TemporalContract contract;    ///< 时间契约
};

/**
 * @struct DependencyState
 * @brief 依赖状态：意图继承依赖状态
 *
 * DependencyState 存储意图继承的依赖关系：
 * - holderSceneId: 持有依赖的场景标识（持有锁的进程）
 * - requesterSceneId: 请求者的场景标识（等待锁的进程）
 * - holderOriginalIntent: 持有者的原始意图等级
 * - inheritedIntent: 继承后的意图等级（请求者的意图等级）
 * - active: 依赖是否活跃
 * - kind: 依赖类型
 * - depth: 依赖层级深度
 *
 * 意图继承场景：
 * - 子进程继承父进程的意图等级
 * - 服务进程继承调用方的意图等级
 * - 组件间通过依赖关系传递意图
 *
 * 继承方向：
 * - holderSceneId: 持有锁的低等级任务（如 P4）
 * - requesterSceneId: 等待锁的高等级任务（如 P1）
 * - holderOriginalIntent: 持有者的原始意图等级（P4）
 * - inheritedIntent: 继承后的意图等级（P1）
 *
 * @see 框架 §2.4 意图继承
 */
struct DependencyState {
    SceneId holderSceneId = 0;       ///< 持有依赖的场景标识（持有锁的进程）
    SceneId requesterSceneId = 0;    ///< 请求者的场景标识（等待锁的进程）
    IntentLevel holderOriginalIntent = IntentLevel::P4;    ///< 持有者的原始意图等级
    IntentLevel inheritedIntent = IntentLevel::P4;         ///< 继承后的意图等级
    int64_t inheritStartTimeMs = 0;                        ///< 继承开始时间（ms）
    int64_t inheritExpireTimeMs = 0;                       ///< 继承到期时间（ms）
    DependencyKind kind = DependencyKind::None;            ///< 依赖类型
    uint32_t depth = 0;                                    ///< 依赖层级深度
    bool active = false;                                   ///< 依赖是否活跃
};

/**
 * @struct CapabilityState
 * @brief 能力状态：平台能力状态
 *
 * CapabilityState 存储平台硬件的能力边界：
 * - snapshot: 平台能力的 canonical 快照
 *
 * 能力类型：
 * - CPU 频率范围和算力上限
 * - 内存带宽和容量
 * - GPU 算力和频率
 * - 存储 I/O 能力
 *
 * @see 框架 §3.5 能力边界
 */
struct CapabilityState {
    CapabilitySnapshot snapshot;    ///< canonical 能力快照
};

/**
 * @struct StabilityState
 * @brief 稳定化状态：迁移稳定化状态
 *
 * StabilityState 存储意图等级迁移的稳定化信息：
 * - observationWindowMs: 观察窗口时长
 * - minResidencyMs: 最小驻留时长
 * - lastUpdateMs: 最近更新时间
 * - steadyEnterMerges: 稳态进入合并次数
 * - exitHysteresisMerges: 退出滞后合并次数
 * - inSteady: 是否处于稳态
 *
 * 稳定化机制：
 * 1. 观察窗口：收集一段时间内的意图变化
 * 2. 最小驻留：防止意图等级频繁跳变
 * 3. 滞后机制：退出稳态需要额外的确认
 *
 * @note 稳定化机制防止 PELT 风格的抖动
 * @see 框架 §2.4 迁移稳定化机制
 */
struct StabilityState {
    int64_t observationWindowMs = 0;       ///< 观察窗口时长 (ms)
    int64_t minResidencyMs = 0;            ///< 最小驻留时长 (ms)
    int64_t lastUpdateMs = 0;              ///< 最近更新时间 (ms)
    int64_t observationStartTimeMs = 0;    ///< 当前候选迁移的观察起点 (ms)
    int64_t steadyEnterTimeMs = 0;         ///< 当前稳态进入时间 (ms)
    uint32_t steadyEnterMerges = 0;        ///< 稳态进入合并次数
    uint32_t exitHysteresisMerges = 0;     ///< 退出滞后合并次数
    uint32_t totalMergeCount = 0;     ///< 总合并次数（独立计数，不依赖 generation）
    uint32_t stableMergeCount = 0;    ///< 当前候选迁移已稳定合并次数
    uint32_t exitMergeCount = 0;      ///< 当前稳态退出确认次数
    bool inSteady = false;            ///< 是否处于稳态
    bool inObservation = false;       ///< 是否正在观察候选迁移
    bool pendingExit = false;         ///< 是否处于退出确认阶段
    SceneSemantic pendingSemantic;    ///< 观察窗口中的候选场景语义
    IntentContract pendingIntent;     ///< 观察窗口中的候选意图契约
};

/**
 * @struct DirtyBits
 * @brief 脏标记：状态变更追踪
 *
 * DirtyBits 用于追踪哪些状态域被修改：
 * - 支持批量状态更新
 * - 避免不必要的全量同步
 * - 支持增量提交
 *
 * 脏标记位定义：
 * - kConstraint: 约束状态变更
 * - kScene: 场景状态变更
 * - kIntent: 意图状态变更
 * - kLease: 租约状态变更
 * - kDependency: 依赖状态变更
 * - kCapability: 能力状态变更
 * - kStability: 稳定化状态变更
 *
 * @note 使用 DirtyBits 而非直接修改状态，支持批量提交
 */
struct DirtyBits {
    uint32_t bits = 0;    ///< 脏标记位掩码

    static constexpr uint32_t kConstraint = 1 << 0;    ///< 约束状态位
    static constexpr uint32_t kScene = 1 << 1;         ///< 场景状态位
    static constexpr uint32_t kIntent = 1 << 2;        ///< 意图状态位
    static constexpr uint32_t kLease = 1 << 3;         ///< 租约状态位
    static constexpr uint32_t kDependency = 1 << 4;    ///< 依赖状态位
    static constexpr uint32_t kCapability = 1 << 5;    ///< 能力状态位
    static constexpr uint32_t kStability = 1 << 6;     ///< 稳定化状态位

    /**
     * @brief 检查指定标记是否设置
     * @param mask 标记掩码
     * @return 是否设置
     */
    bool Has(uint32_t mask) const { return (bits & mask) != 0; }

    /**
     * @brief 设置指定标记
     * @param mask 标记掩码
     */
    void Set(uint32_t mask) { bits |= mask; }

    /**
     * @brief 清除指定标记
     * @param mask 标记掩码
     */
    void Clear(uint32_t mask) { bits &= ~mask; }

    bool HasConstraint() const { return Has(kConstraint); }
    bool HasScene() const { return Has(kScene); }
    bool HasIntent() const { return Has(kIntent); }
    bool HasLease() const { return Has(kLease); }
    bool HasDependency() const { return Has(kDependency); }
    bool HasCapability() const { return Has(kCapability); }
    bool HasStability() const { return Has(kStability); }

    void SetConstraint() { Set(kConstraint); }
    void SetScene() { Set(kScene); }
    void SetIntent() { Set(kIntent); }
    void SetLease() { Set(kLease); }
    void SetDependency() { Set(kDependency); }
    void SetCapability() { Set(kCapability); }
    void SetStability() { Set(kStability); }

    void ClearConstraint() { Clear(kConstraint); }
    void ClearScene() { Clear(kScene); }
    void ClearIntent() { Clear(kIntent); }
    void ClearLease() { Clear(kLease); }
    void ClearDependency() { Clear(kDependency); }
    void ClearCapability() { Clear(kCapability); }
    void ClearStability() { Clear(kStability); }

    /**
     * @brief 合并其他脏标记
     * @param other 其他脏标记
     */
    void Merge(const DirtyBits &other) { bits |= other.bits; }

    /**
     * @brief 清除所有标记
     */
    void Clear() { bits = 0; }

    /**
     * @brief 检查是否为空
     * @return 是否没有任何标记
     */
    bool IsEmpty() const { return bits == 0; }
};

}    // namespace vendor::transsion::perfengine::dse
