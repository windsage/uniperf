#pragma once

/**
 * @file CoreTypes.h
 * @brief DSE 核心类型定义
 *
 * 本文件定义了整机资源确定性调度抽象框架的核心数据结构：
 * - 调度事件 (SchedEvent): 输入事件抽象
 * - 场景语义 (SceneSemantic): 场景识别结果
 * - 意图契约 (IntentContract): P1~P4 意图等级
 * - 时间契约 (TemporalContract): 租约与衰减语义
 * - 资源请求 (ResourceRequest): 三级资源需求向量
 * - 约束边界 (ConstraintAllowed): 环境约束限制
 * - 能力上限 (CapabilityFeasible): 平台能力边界
 * - 资源授权 (ResourceGrant/FastGrant): 交付结果
 * - 策略决策 (PolicyDecision): 完整决策输出
 *
 * @see docs/整机资源确定性调度抽象框架.md
 */

#include <cstdint>

#include "dse/Types.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @struct SnapshotToken
 * @brief 快照令牌：GenerationPinning 的原子状态标识
 *
 * SnapshotToken 用于标识状态快照的原子性，包含：
 * - generation: 状态版本号
 * - snapshotTs: 快照时间戳
 * - token: 唯一令牌值
 *
 * @see 框架 §5.4 GenerationPinning 原子状态快照机制
 */
struct SnapshotToken {
    Generation generation = 0;    ///< 状态版本号
    int64_t snapshotTs = 0;       ///< 快照时间戳
    uint64_t token = 0;           ///< 唯一令牌值
};

/** 与 StateVault::Pin(snapshotTs) 相同的 token 组合，避免在监听链上重复读锁 */
inline SnapshotToken MakeSnapshotToken(Generation generation, int64_t snapshotTs = 0) noexcept {
    SnapshotToken t;
    t.generation = generation;
    t.snapshotTs = snapshotTs;
    t.token = static_cast<uint64_t>(generation) ^ static_cast<uint64_t>(snapshotTs);
    return t;
}

/**
 * @struct ResourceRule
 * @brief 资源规则：单一资源维度的配置规则
 *
 * ResourceRule 定义资源维度的配置参数：
 * - dim: 资源维度
 * - min: 最小资源值
 * - target: 目标资源值
 * - max: 最大资源值
 * - step: 资源步进
 * - weight: 权重因子
 */
struct ResourceRule {
    ResourceDim dim = ResourceDim::CpuCapacity;    ///< 资源维度
    uint32_t min = 0;                              ///< 最小资源值
    uint32_t target = 0;                           ///< 目标资源值
    uint32_t max = 1024;                           ///< 最大资源值
    uint32_t step = 100;                           ///< 资源步进
    float weight = 1.0f;                           ///< 权重因子
};

/**
 * @brief Hash 组合函数
 * @param seed 种子值
 * @param value 待组合值
 * @return 组合后的哈希值
 *
 * 使用 boost::hash_combine 算法，用于构建复合哈希。
 */
inline uint64_t HashCombine(uint64_t seed, uint64_t value) {
    seed ^= value + 0x9e3779b9 + (seed << 6) + (seed >> 2);
    return seed;
}

/**
 * @brief 计算资源向量哈希值
 * @param rv 资源向量
 * @return 哈希值
 */
inline uint64_t HashResourceVector(const ResourceVector &rv) {
    uint64_t hash = 0;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        hash = HashCombine(hash, static_cast<uint64_t>(rv.v[i]));
    }
    return hash;
}

/**
 * @enum PlatformActionPath
 * @brief 平台执行路径类型
 *
 * 定义资源授权下发到硬件的不同路径：
 * - PerfHintHal: 通过 PerfHint HAL 下发
 * - DirectKernelCtl: 直接内核控制接口
 * - Mixed: 混合路径
 * - NoopFallback: 降级空操作
 */
enum class PlatformActionPath : uint8_t {
    None = 0,               ///< 无路径
    PerfHintHal = 1,        ///< PerfHint HAL 路径
    DirectKernelCtl = 2,    ///< 直接内核控制路径
    Mixed = 3,              ///< 混合路径
    NoopFallback = 4        ///< 降级空操作
};

/**
 * @enum EventSemanticAction
 * @brief Bridge -> Stage 共享的语义 action 定义（避免分散魔法值）
 */
enum class EventSemanticAction : uint32_t {
    Unknown = 0,
    Launch = 1,
    Transition = 2,
    Scroll = 3,
    Animation = 4,
    Gaming = 5,
    Camera = 6,
    Playback = 7,
    Download = 8,
    Background = 9
};

/**
 * @enum DependencyKind
 * @brief 依赖类型：意图继承的依赖来源
 */
enum class DependencyKind : uint8_t { None = 0, Lock = 1, Binder = 2, Futex = 3 };

/**
 * @struct SchedEvent
 * @brief 调度事件：系统输入事件的统一抽象
 *
 * SchedEvent 是 DSE 的输入起点，封装了来自各子系统的事件：
 * - 用户交互事件（触控、滑动）
 * - 应用生命周期事件（启动、切换）
 * - 系统状态变化事件
 *
 * 框架映射：
 * - eventId: 事件唯一标识，用于追踪
 * - timestamp: 事件时间戳，用于时序分析
 * - source/action: 事件来源与类型，用于场景识别
 * - package/activity: 应用包名和组件名，用于场景语义提取
 *
 * @note package 和 activity 用于场景识别和意图推导
 * @note 遵循框架 §5.1 输入边界定义，事件只携带事实数据
 */
struct SchedEvent {
    int64_t eventId = 0;        ///< 事件唯一标识
    SessionId sessionId = 0;    ///< 会话标识（用于追踪调度决策生命周期）
    SceneId sceneId = 0;    ///< 场景标识（显式 scene 实例；缺失时由 package/activity 退化生成）
    int32_t pid = 0;           ///< 进程 ID
    int32_t tid = 0;           ///< 线程 ID
    int32_t uid = 0;           ///< 用户 ID
    uint64_t timestamp = 0;    ///< 事件时间戳 (ns)
    uint32_t source = 0;       ///< 事件来源
    uint32_t action = 0;       ///< 事件动作类型
    uint32_t rawFlags = 0;     ///< 原始标志位
    uint32_t rawHint = 0;    ///< 原始提示（非权威，仅用于 profile 选择辅助，不可直接驱动调度决策）
    const void *hint = nullptr;    ///< 扩展提示数据
    size_t hintSize = 0;           ///< 提示数据大小

    char package[128] = {};     ///< 应用包名 (如 com.example.app)
    char activity[128] = {};    ///< 组件名 (如 .MainActivity)

    uint64_t Hash() const {
        uint64_t hash = 0;
        hash = HashCombine(hash, static_cast<uint64_t>(eventId));
        hash = HashCombine(hash, static_cast<uint64_t>(sessionId));
        hash = HashCombine(hash, static_cast<uint64_t>(sceneId));
        hash = HashCombine(hash, static_cast<uint64_t>(pid));
        hash = HashCombine(hash, static_cast<uint64_t>(tid));
        hash = HashCombine(hash, static_cast<uint64_t>(uid));
        hash = HashCombine(hash, timestamp);
        hash = HashCombine(hash, static_cast<uint64_t>(source));
        hash = HashCombine(hash, static_cast<uint64_t>(action));
        hash = HashCombine(hash, static_cast<uint64_t>(rawFlags));
        hash = HashCombine(hash, static_cast<uint64_t>(rawHint));
        for (size_t i = 0; package[i] != '\0' && i < 128; ++i) {
            hash = HashCombine(hash, static_cast<uint64_t>(package[i]));
        }
        for (size_t i = 0; activity[i] != '\0' && i < 128; ++i) {
            hash = HashCombine(hash, static_cast<uint64_t>(activity[i]));
        }
        return hash;
    }
};

/**
 * @brief 统一的会话解析规则：优先使用显式 sessionId，否则回退到 eventId
 */
inline SessionId ResolveSchedSessionId(const SchedEvent &event) noexcept {
    return (event.sessionId != 0) ? event.sessionId : static_cast<SessionId>(event.eventId);
}

inline SessionId ResolveSchedSessionId(const SchedEvent *event) noexcept {
    return event ? ResolveSchedSessionId(*event) : 0;
}

inline SceneId ComputeSyntheticSceneId(int32_t uid, const char *package,
                                       const char *activity) noexcept {
    uint64_t hash = static_cast<uint64_t>(static_cast<uint32_t>(uid));
    if (package) {
        for (size_t i = 0; package[i] != '\0' && i < 128; ++i) {
            hash = HashCombine(hash, static_cast<uint64_t>(static_cast<uint8_t>(package[i])));
        }
    }
    if (activity) {
        for (size_t i = 0; activity[i] != '\0' && i < 128; ++i) {
            hash = HashCombine(hash, static_cast<uint64_t>(static_cast<uint8_t>(activity[i])));
        }
    }
    return (hash == 0) ? 0 : static_cast<SceneId>(hash & 0x7FFFFFFFFFFFFFFFULL);
}

inline SceneId ResolveSchedSceneId(const SchedEvent &event) noexcept {
    if (event.sceneId != 0) {
        return event.sceneId;
    }
    const SceneId synthetic = ComputeSyntheticSceneId(event.uid, event.package, event.activity);
    if (synthetic != 0) {
        return synthetic;
    }
    if (event.pid != 0) {
        return static_cast<SceneId>(event.pid);
    }
    return static_cast<SceneId>(event.eventId);
}

inline SceneId ResolveSchedSceneId(const SchedEvent *event) noexcept {
    return event ? ResolveSchedSceneId(*event) : 0;
}

/**
 * @brief 灰度采样键：混合 eventId/session/pid/uid 等，避免仅按 eventId 导致同类事件固定全开或全关
 *       （商用灰度语义；与 SafetyController::ShouldUseGrayscale 配合）。
 */
inline uint32_t GrayscaleSampleKey(const SchedEvent &e) {
    uint64_t h = static_cast<uint64_t>(static_cast<uint32_t>(e.eventId));
    h ^= static_cast<uint64_t>(static_cast<uint32_t>(e.pid)) * 0x9e3779b97f4a7c15ULL;
    h ^= static_cast<uint64_t>(static_cast<uint32_t>(e.uid)) * 0xbf58476d1ce4e5b9ULL;
    h ^= static_cast<uint64_t>(e.sessionId) * 0x94d049bb133111ebULL;
    h ^= static_cast<uint64_t>(e.rawHint) << 1;
    return static_cast<uint32_t>(h ^ (h >> 32));
}

/**
 * @enum SceneKind
 * @brief 场景类型分类
 *
 * 定义系统识别的主要场景类型，用于映射到意图等级和资源需求。
 *
 * | 场景类型 | 典型意图等级 | 资源特征 |
 * |----------|-------------|----------|
 * | Launch | P1 | 短时高资源需求 |
 * | Transition | P1/P2 | 短时中等需求 |
 * | Scroll | P1 | 持续 CPU/GPU 需求 |
 * | Animation | P1/P2 | GPU 密集 |
 * | Gaming | P2 | 持续高资源需求 |
 * | Camera | P1/P2 | CPU/GPU/内存密集 |
 * | Playback | P2 | 稳态资源需求 |
 * | Download | P4 | 后台低优先级 |
 * | Background | P4 | 受控后台任务 |
 */
enum class SceneKind : uint8_t {
    Unknown = 0,       ///< 未知场景
    Launch = 1,        ///< 应用启动
    Transition = 2,    ///< 界面转场
    Scroll = 3,        ///< 滑动交互
    Animation = 4,     ///< 动画播放
    Gaming = 5,        ///< 游戏运行
    Camera = 6,        ///< 相机拍照/录像
    Playback = 7,      ///< 音视频播放
    Download = 8,      ///< 下载任务
    Background = 9     ///< 后台任务
};

/**
 * @enum ScenePhase
 * @brief 场景阶段
 *
 * 定义场景的生命周期阶段，用于状态迁移和稳定化机制。
 *
 * - Enter: 场景进入，可能触发 P1 保障
 * - Active: 场景活跃，进入稳态（P2/P3）
 * - Exit: 场景退出，资源契约衰减
 */
enum class ScenePhase : uint8_t {
    None = 0,      ///< 无阶段
    Enter = 1,     ///< 进入阶段
    Active = 2,    ///< 活跃阶段
    Exit = 3       ///< 退出阶段
};

/**
 * @struct SceneSemantic
 * @brief 场景语义：场景识别的结构化结果
 *
 * SceneSemantic 封装了场景识别的完整语义信息：
 * - kind: 场景类型
 * - phase: 场景阶段
 * - visible/audible: 感知状态
 * - continuousPerception: 是否构成连续感知主链路
 *
 * @note continuousPerception 是区分 P2 与 P3 的关键判定依据
 */
struct SceneSemantic {
    SceneKind kind = SceneKind::Unknown;    ///< 场景类型
    ScenePhase phase = ScenePhase::None;    ///< 场景阶段
    bool visible = false;                   ///< 是否可见
    bool audible = false;                   ///< 是否可听
    bool continuousPerception = false;      ///< 是否构成连续感知主链路
};

inline bool SceneSemanticEquals(const SceneSemantic &lhs, const SceneSemantic &rhs) noexcept {
    return lhs.kind == rhs.kind && lhs.phase == rhs.phase && lhs.visible == rhs.visible &&
           lhs.audible == rhs.audible && lhs.continuousPerception == rhs.continuousPerception;
}

/**
 * @struct IntentContract
 * @brief 意图契约：P1~P4 资源交付契约
 *
 * IntentContract 描述工作负载在当前场景下应获得的资源交付契约。
 *
 * 契约类型：
 * - P1: 下限契约 (min 必须满足)
 * - P2: 稳态契约 (优先满足 target)
 * - P3: 弹性契约 (无刚性下限，动态伸缩)
 * - P4: 上限契约 (max 严格限制)
 */
struct IntentContract {
    IntentLevel level = IntentLevel::P3;    ///< 意图等级
};

inline bool IntentContractEquals(const IntentContract &lhs, const IntentContract &rhs) noexcept {
    return lhs.level == rhs.level;
}

/**
 * @struct DependencyHintPayload
 * @brief 事件侧显式提供的真实依赖边
 *
 * 只有当输入层拿到了真实 holder/requester 元数据时，才允许触发意图继承。
 */
struct DependencyHintPayload {
    uint32_t version = 1;
    SceneId holderSceneId = 0;
    SceneId requesterSceneId = 0;
    IntentLevel holderOriginalIntent = IntentLevel::P4;
    DependencyKind kind = DependencyKind::None;
    uint32_t depth = 1;
};

/**
 * @struct TemporalContract
 * @brief 时间契约：租约与衰减语义
 *
 * TemporalContract 将 PELT 的指数衰减思想显式化，
 * 定义资源需求的时间模式和租约边界。
 *
 * @see 框架 §2.4 迁移稳定化机制
 */
struct TemporalContract {
    int64_t leaseStartTs = 0;                      ///< 租约开始时间戳
    int64_t leaseEndTs = 0;                        ///< 租约结束时间戳
    float decayFactor = 1.0f;                      ///< 衰减因子
    uint32_t renewalCount = 0;                     ///< 续租次数
    TimeMode timeMode = TimeMode::Intermittent;    ///< 时间模式
};

inline bool TemporalContractEquals(const TemporalContract &lhs,
                                   const TemporalContract &rhs) noexcept {
    return lhs.leaseStartTs == rhs.leaseStartTs && lhs.leaseEndTs == rhs.leaseEndTs &&
           lhs.decayFactor == rhs.decayFactor && lhs.renewalCount == rhs.renewalCount &&
           lhs.timeMode == rhs.timeMode;
}

/**
 * @struct ResourceRequest
 * @brief 资源请求：三级资源需求向量
 *
 * ResourceRequest 定义了工作负载的资源需求轮廓：
 * - min: 必须满足的下限（P1 契约核心）
 * - target: 建议达到的目标（P2 契约核心）
 * - max: 允许的上限（P4 契约核心）
 *
 * @see 框架 §3 微观资源度量
 */
struct ResourceRequest {
    uint32_t resourceMask = 0;    ///< 有效资源维度掩码
    ResourceVector min;           ///< 下限需求向量
    ResourceVector target;        ///< 目标需求向量
    ResourceVector max;           ///< 上限需求向量
};

inline bool ResourceRequestEquals(const ResourceRequest &lhs, const ResourceRequest &rhs) noexcept {
    return lhs.resourceMask == rhs.resourceMask && lhs.min == rhs.min && lhs.target == rhs.target &&
           lhs.max == rhs.max;
};

/**
 * @struct ConstraintAllowed
 * @brief 约束边界：环境约束限制
 *
 * ConstraintAllowed 描述当前系统约束下的资源允许范围：
 * - 热约束：温度过高导致降频
 * - 电约束：低电量限制功耗
 * - 内存约束：PSI 压力触发回收
 * - 熄屏约束：后台资源压制
 *
 * @see 框架 §2.5 "约束维度始终高于意图等级"
 */
struct ConstraintAllowed {
    uint32_t resourceMask = 0;         ///< 有效资源维度掩码
    ResourceVector allowed;            ///< 允许的资源向量
    uint32_t constraintFlags = 0;      ///< 约束标志位
    uint32_t thermalLevel = 0;         ///< 热等级
    uint32_t batteryLevel = 100;       ///< 电池等级
    uint32_t memoryPressure = 0;       ///< 内存压力等级
    uint32_t thermalScaling = 1000;    ///< 热缩放因子 (Q10)
    uint32_t batteryScaling = 1000;    ///< 电量缩放因子 (Q10)
    uint32_t memoryScaling = 1000;     ///< 内存缩放因子 (Q10)
};

/**
 * @struct CapabilityFeasible
 * @brief 能力上限：平台能力边界
 *
 * CapabilityFeasible 描述平台硬件的实际能力上限：
 * - CPU 频率范围
 * - 内存带宽上限
 * - GPU 算力上限
 * - 存储 I/O 能力
 *
 * @see 框架 §3.5 平台能力建模
 */
struct CapabilityFeasible {
    uint32_t resourceMask = 0;       ///< 有效资源维度掩码
    ResourceVector feasible;         ///< 可行的资源向量
    uint32_t capabilityFlags = 0;    ///< 能力标志位
};

enum class DecisionPath : uint8_t { Fast = 1, Converge = 2 };

/**
 * @struct ResourceGrant
 * @brief 资源授权：Converge 路径的完整授权结果
 *
 * ResourceGrant 是精修路径（Converge Path）的最终交付结果，
 * 包含经过约束裁剪和能力限制后的实际资源授权。
 */
struct ResourceGrant {
    uint32_t resourceMask = 0;          ///< 授权的资源维度掩码
    ResourceVector delivered;           ///< 实际交付的资源向量
    uint32_t finalizeReasonBits = 0;    ///< 最终化原因位
};

/**
 * @enum ActionContractMode
 * @brief 执行动作的契约语义类型
 */
enum class ActionContractMode : uint8_t { None = 0, Floor = 1, Target = 2, Elastic = 3, Cap = 4 };

inline ActionContractMode DeriveActionContractMode(IntentLevel intent) noexcept {
    switch (intent) {
        case IntentLevel::P1:
            return ActionContractMode::Floor;
        case IntentLevel::P2:
            return ActionContractMode::Target;
        case IntentLevel::P3:
            return ActionContractMode::Elastic;
        case IntentLevel::P4:
            return ActionContractMode::Cap;
    }
    return ActionContractMode::Elastic;
}

/**
 * @struct FastGrant
 * @brief 快速授权：Fast 路径的快速授权结果
 *
 * FastGrant 是快路径（Fast Path）的授权结果，
 * 用于 P1 首响应的快速保障，不经过完整决策流程。
 *
 * @see 框架 §2.4 "P1 的首响应优先由快路径直接保障"
 */
struct FastGrant {
    SessionId sessionId = 0;     ///< 会话标识（用于追踪调度决策生命周期）
    uint32_t grantedMask = 0;    ///< 授权的资源维度掩码
    ResourceVector delivered;    ///< 实际交付的资源向量
    int32_t leaseMs = 0;         ///< 租约时长 (ms)
    uint32_t reasonBits = 0;     ///< 原因位
};

/**
 * @struct DecisionMeta
 * @brief 决策元数据：决策的可追溯性标识
 *
 * DecisionMeta 封装了决策的元数据信息，用于：
 * - 版本追踪：generation, ruleVersion
 * - 时序分析：decisionTs, latencyNs
 * - 路径标识：platformActionPath, stageMask
 *
 * @see 框架 §5.5 元数据原子化
 */
struct DecisionMeta {
    Generation generation = 0;                                           ///< 状态快照版本号
    uint32_t profileId = 0;                                              ///< 配置档位 ID
    uint32_t ruleVersion = 0;                                            ///< 规则版本号
    PlatformActionPath platformActionPath = PlatformActionPath::None;    ///< 执行路径
    uint32_t fallbackFlags = 0;                                          ///< 降级标志位
    int64_t decisionTs = 0;                                              ///< 决策时间戳
    uint32_t stageMask = 0;                                              ///< 执行阶段掩码
    uint32_t latencyNs = 0;                                              ///< 决策延迟 (ns)

    /**
     * @brief 获取世代号
     * @return 状态快照版本号
     */
    Generation GetGeneration() const { return generation; }

    /**
     * @brief 获取快照时间戳
     * @return 决策时间戳
     */
    int64_t GetSnapshotTs() const { return decisionTs; }

    /**
     * @brief 从上下文创建决策元数据
     * @param gen 世代号
     * @param profId 配置档位 ID
     * @param ruleVer 规则版本号
     * @param ts 时间戳
     * @param stage 阶段掩码
     * @return 初始化的 DecisionMeta 实例
     */
    static DecisionMeta CreateFromContext(Generation gen, uint32_t profId, uint32_t ruleVer,
                                          int64_t ts, uint32_t stage = 0) {
        DecisionMeta meta;
        meta.generation = gen;
        meta.profileId = profId;
        meta.ruleVersion = ruleVer;
        meta.decisionTs = ts;
        meta.stageMask = stage;
        return meta;
    }

    /**
     * @brief 从快照令牌创建决策元数据
     * @param token 快照令牌（包含 generation 和 snapshotTs）
     * @param profId 配置档位 ID
     * @param ruleVer 规则版本号
     * @param stage 阶段掩码
     * @return 初始化的 DecisionMeta 实例
     *
     * @note 遵循 GenerationPinning 原则，使用快照时间戳而非事件时间戳
     */
    static DecisionMeta CreateFromSnapshotToken(const SnapshotToken &token, uint32_t profId,
                                                uint32_t ruleVer, uint32_t stage = 0) {
        DecisionMeta meta;
        meta.generation = token.generation;
        meta.profileId = profId;
        meta.ruleVersion = ruleVer;
        meta.decisionTs = token.snapshotTs;
        meta.stageMask = stage;
        return meta;
    }

    /**
     * @brief 计算元数据哈希值
     * @return 用于去重和追踪的哈希值
     */
    uint64_t Hash() const {
        uint64_t hash = 0;
        hash = HashCombine(hash, static_cast<uint64_t>(generation));
        hash = HashCombine(hash, static_cast<uint64_t>(profileId));
        hash = HashCombine(hash, static_cast<uint64_t>(ruleVersion));
        hash = HashCombine(hash, static_cast<uint64_t>(platformActionPath));
        hash = HashCombine(hash, static_cast<uint64_t>(fallbackFlags));
        hash = HashCombine(hash, static_cast<uint64_t>(decisionTs));
        hash = HashCombine(hash, static_cast<uint64_t>(stageMask));
        hash = HashCombine(hash, static_cast<uint64_t>(latencyNs));
        return hash;
    }
};

/**
 * @struct PolicyDecision
 * @brief 策略决策：完整的调度决策输出
 *
 * PolicyDecision 是 DSE 决策流程的最终输出，包含：
 * - 资源授权 (grant)
 * - 决策元数据 (meta)
 * - 意图等级 (effectiveIntent)
 * - 决策状态 (admitted, degraded)
 *
 * 设计原则：
 * - 不可变性：决策一旦生成，不应被修改
 * - 可追溯性：通过 meta 追踪决策过程
 * - 确定性：相同输入产生相同输出
 */
struct PolicyDecision {
    SessionId sessionId = 0;                          ///< 会话标识
    ResourceGrant grant;                              ///< 资源授权
    DecisionMeta meta;                                ///< 决策元数据
    uint32_t reasonBits = 0;                          ///< 原因位
    uint32_t rejectReasons = 0;                       ///< 拒绝原因
    IntentLevel effectiveIntent = IntentLevel::P3;    ///< 实际生效的意图等级
    TemporalContract temporal;                        ///< 生效时间契约
    ResourceRequest request;            ///< 原始契约请求（保留 min/target/max 语义）
    uint64_t executionSignature = 0;    ///< [优化] 预计算的执行签名（用于 dedup）
    uint64_t executionSummary =
        0;    ///< [优化] 预计算轻量摘要（ExecutionFlow 去重首判，避免热路径重复哈希）
    bool admitted = true;     ///< 是否被接纳
    bool degraded = false;    ///< 是否降级

    /**
     * @brief 检查决策是否具备去重潜力
     * 符合去重条件的决策才需要计算执行签名。
     */
    bool IsDedupCandidate() const {
        return admitted && (grant.resourceMask != 0) && (effectiveIntent != IntentLevel::P1) &&
               (temporal.timeMode != TimeMode::Burst);
    }

    /**
     * @brief 获取锁定世代号
     * @return 决策基于的状态版本号
     */
    Generation GetPinnedGeneration() const { return meta.generation; }

    /**
     * @brief 获取快照时间戳
     * @return 决策时间戳
     */
    int64_t GetSnapshotTs() const { return meta.decisionTs; }

    /**
     * @brief 计算决策哈希值
     * @return 用于去重和追踪的哈希值
     */
    uint64_t Hash() const {
        uint64_t hash = 0;
        hash = HashCombine(hash, static_cast<uint64_t>(sessionId));
        hash = HashCombine(hash, static_cast<uint64_t>(grant.resourceMask));
        hash = HashCombine(hash, HashResourceVector(grant.delivered));
        hash = HashCombine(hash, static_cast<uint64_t>(reasonBits));
        hash = HashCombine(hash, static_cast<uint64_t>(effectiveIntent));
        hash = HashCombine(hash, static_cast<uint64_t>(temporal.timeMode));
        hash = HashCombine(hash, static_cast<uint64_t>(temporal.leaseStartTs));
        hash = HashCombine(hash, static_cast<uint64_t>(temporal.leaseEndTs));
        hash = HashCombine(hash, static_cast<uint64_t>(temporal.renewalCount));
        hash = HashCombine(hash, static_cast<uint64_t>(request.resourceMask));
        hash = HashCombine(hash, HashResourceVector(request.min));
        hash = HashCombine(hash, HashResourceVector(request.target));
        hash = HashCombine(hash, HashResourceVector(request.max));
        hash = HashCombine(hash, static_cast<uint64_t>(admitted));
        hash = HashCombine(hash, static_cast<uint64_t>(degraded));
        hash = HashCombine(hash, meta.Hash());
        return hash;
    }
};

/**
 * @brief 计算决策的执行指纹（用于执行流去重判定）
 *
 * 包含所有影响物理下发的语义因子，由 Finalize 阶段预计算并缓存。
 */
inline uint64_t ComputeExecutionSignature(const PolicyDecision &decision) {
    uint64_t hash = 0;
    hash = HashCombine(hash, static_cast<uint64_t>(decision.sessionId));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.grant.resourceMask));
    hash = HashCombine(hash, HashResourceVector(decision.grant.delivered));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.reasonBits));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.effectiveIntent));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.temporal.timeMode));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.temporal.leaseStartTs));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.temporal.leaseEndTs));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.temporal.renewalCount));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.request.resourceMask));
    hash = HashCombine(hash, HashResourceVector(decision.request.min));
    hash = HashCombine(hash, HashResourceVector(decision.request.target));
    hash = HashCombine(hash, HashResourceVector(decision.request.max));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.admitted));
    return hash;
}

/**
 * @brief 计算执行摘要（轻量哈希，用于初步碰撞判定）
 */
inline uint64_t ComputeExecutionSummary(const PolicyDecision &decision) {
    uint64_t hash = 0;
    hash = HashCombine(hash, static_cast<uint64_t>(decision.sessionId));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.grant.resourceMask));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.reasonBits));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.request.resourceMask));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.effectiveIntent));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.temporal.timeMode));
    hash = HashCombine(hash, static_cast<uint64_t>(decision.admitted));
    return hash;
}

/**
 * @struct ArbitrationResult
 * @brief 仲裁结果：仲裁阶段输出 + ConvergeFinalize 消费的交付向量（单一来源）
 *
 * ArbitrationStage 计算 ComputeArbitrationLayers 一次，并把 delivered / reasonBits
 * 写入本结构；ConvergeFinalizeStage 只消费该份结果做 BuildGrantFromDelivered，
 * 禁止再次调用 ComputeArbitrationLayers，避免快路径与收敛路径交付漂移（commit.md / 框架 §4.3）。
 *
 * reasonBits：RuleTable 裁剪产生的 ruleReasonBits（与 ArbitrationStage 一致）。
 *
 * @note 为了控制收敛链工作集，本结构只保留下游真正消费的字段（grantedMask/reasonBits/delivered），
 *       未消费的调试/诊断字段应避免在稳态高频事件中占用带宽与 cache。
 */
struct ArbitrationResult {
    uint32_t grantedMask = 0;      ///< 授权的资源维度掩码
    uint32_t reasonBits = 0;       ///< RuleTable 规则原因位
    ResourceVector delivered{};    ///< min(Intent, Constraint, Capability)，与仲裁阶段一致

    /**
     * @brief 检查是否有授权
     * @return 是否有任何资源被授权
     */
    bool HasGrant() const { return grantedMask != 0; }
};

/**
 * @struct ExecutionResult
 * @brief 执行结果：平台执行的反馈
 *
 * ExecutionResult 记录资源授权下发到平台后的执行结果，
 * 用于闭环反馈和降级处理。
 */
struct ExecutionResult {
    SessionId sessionId = 0;        ///< 会话标识
    DecisionMeta meta;              ///< 决策元数据
    uint32_t executedMask = 0;      ///< 实际执行的资源维度掩码
    uint32_t executionFlags = 0;    ///< 执行标志位
    uint32_t rejectReasons = 0;     ///< 拒绝原因
    uint32_t fallbackFlags = 0;     ///< 降级标志位
    PlatformActionPath actualPath = PlatformActionPath::None;    ///< 实际执行路径
};

/**
 * @struct ReplayHash
 * @brief 回放哈希：用于确定性验证
 *
 * ReplayHash 包含决策、输入和状态的哈希值，
 * 用于验证决策的确定性和可重现性。
 *
 * 完整字段用于支持 GenerationPinning 和会话追踪：
 * - decisionHash/inputHash/stateHash: 核心哈希值
 * - generation/timestamp/snapshotToken: 快照绑定
 * - sessionId/profileId/ruleVersion: 会话与版本追踪
 * - effectiveIntent: 意图等级记录
 */
struct ReplayHash {
    uint64_t decisionHash = 0;                        ///< 决策哈希
    uint64_t inputHash = 0;                           ///< 输入哈希
    uint64_t stateHash = 0;                           ///< 状态哈希
    Generation generation = 0;                        ///< 世代号
    int64_t timestamp = 0;                            ///< 时间戳
    uint64_t snapshotToken = 0;                       ///< 快照令牌
    SessionId sessionId = 0;                          ///< 会话标识
    uint32_t profileId = 0;                           ///< 配置档位 ID
    uint32_t ruleVersion = 0;                         ///< 规则版本号
    IntentLevel effectiveIntent = IntentLevel::P3;    ///< 有效意图等级
};

/**
 * @brief 计算策略决策哈希值
 * @param decision 策略决策
 * @return 哈希值
 */
inline uint64_t ComputeDecisionHash(const PolicyDecision &decision) {
    return decision.Hash();
}

/**
 * @brief 计算调度事件哈希值
 * @param event 调度事件
 * @return 哈希值
 */
inline uint64_t ComputeInputHash(const SchedEvent &event) {
    return event.Hash();
}

/**
 * @brief 计算快速授权哈希值
 * @param grant 快速授权
 * @return 哈希值
 */
inline uint64_t ComputeFastGrantHash(const FastGrant &grant) {
    uint64_t hash = 0;
    hash = HashCombine(hash, static_cast<uint64_t>(grant.sessionId));
    hash = HashCombine(hash, static_cast<uint64_t>(grant.grantedMask));
    hash = HashCombine(hash, HashResourceVector(grant.delivered));
    hash = HashCombine(hash, static_cast<uint64_t>(grant.leaseMs));
    hash = HashCombine(hash, static_cast<uint64_t>(grant.reasonBits));
    return hash;
}

/**
 * @struct ArbitrationLayers
 * @brief 仲裁层结果：保留后续阶段真正需要的中间结果
 *
 * ArbitrationLayers 保留仲裁阶段真正会被后续消费的中间结果：
 * - delivered: 最终交付向量
 *
 * 约束与能力边界本身已经由 StageContext 持有，避免在这里再复制一份。
 *
 * @see 框架 §3.5 三层交付公式
 */
struct ArbitrationLayers {
    ResourceVector delivered;       ///< 最终交付
    uint32_t reasonBits = 0;        ///< 裁剪原因位
    uint32_t grantedMask = 0;       ///< 交付向量对应的授权掩码
    uint32_t ruleReasonBits = 0;    ///< 规则原因位（已合并 reasonBits）
};

inline uint32_t ResolveIntentValue(const ResourceRequest &request, size_t index,
                                   IntentLevel intentLevel) noexcept {
    switch (intentLevel) {
        case IntentLevel::P1:
            return request.min.v[index];
        case IntentLevel::P2:
        case IntentLevel::P3:
            return request.target.v[index];
        case IntentLevel::P4:
            return request.max.v[index];
    }
    return request.target.v[index];
}

/**
 * @brief 计算完整的仲裁层结果
 * @param request 资源请求（包含 min/target/max 契约）
 * @param constraint 约束边界
 * @param capability 能力上限
 * @param intentLevel 意图等级
 * @return 完整的仲裁层结果
 *
 * 算法逻辑：
 * 1. 根据意图等级选择意图允许向量（P1用min，P2/P3用target，P4用max）
 * 2. 计算约束边界裁剪
 * 3. 计算能力上限裁剪
 * 4. 最终交付 = min(Intent, Constraint, Capability)
 *
 * @note 约束与能力边界由 StageContext 单独持有，这里不再重复复制。
 */
template <typename RuleProvider>
inline ArbitrationLayers ComputeArbitrationLayers(const ResourceRequest &request,
                                                  const ConstraintAllowed &constraint,
                                                  const CapabilityFeasible &capability,
                                                  IntentLevel intentLevel,
                                                  RuleProvider &&ruleProvider) {
    ArbitrationLayers layers;
    layers.reasonBits = 0;
    layers.grantedMask = 0;
    layers.ruleReasonBits = 0;

    for (size_t i = 0; i < kResourceDimCount; ++i) {
        uint32_t dimMask = 1 << i;
        if ((request.resourceMask & dimMask) == 0) {
            layers.delivered.v[i] = 0;
            continue;
        }

        layers.delivered.v[i] = ResolveIntentValue(request, i, intentLevel);

        if (layers.delivered.v[i] > constraint.allowed.v[i]) {
            layers.delivered.v[i] = constraint.allowed.v[i];
            layers.reasonBits |= (dimMask << 8);
        }
        if (layers.delivered.v[i] > capability.feasible.v[i]) {
            layers.delivered.v[i] = capability.feasible.v[i];
            layers.reasonBits |= (dimMask << 16);
        }

        if (intentLevel == IntentLevel::P1 && layers.delivered.v[i] < request.min.v[i]) {
            layers.reasonBits |= (dimMask << 24);
        }
        if (intentLevel == IntentLevel::P4 && layers.delivered.v[i] > request.max.v[i]) {
            layers.delivered.v[i] = request.max.v[i];
            layers.reasonBits |= (dimMask << 24);
        }

        if (layers.delivered.v[i] > 0) {
            layers.grantedMask |= dimMask;
        }

        const ResourceRule *rule = ruleProvider(static_cast<ResourceDim>(i));
        if (rule && layers.delivered.v[i] < rule->min) {
            layers.ruleReasonBits |= dimMask;
        }
    }

    layers.ruleReasonBits |= layers.reasonBits;
    return layers;
}

inline ArbitrationLayers ComputeArbitrationLayers(const ResourceRequest &request,
                                                  const ConstraintAllowed &constraint,
                                                  const CapabilityFeasible &capability,
                                                  IntentLevel intentLevel) {
    return ComputeArbitrationLayers(request, constraint, capability, intentLevel,
                                    [](ResourceDim) -> const ResourceRule * { return nullptr; });
}

/**
 * @struct GrantBuildResult
 * @brief 授权构建结果
 */
struct GrantBuildResult {
    uint32_t grantedMask = 0;       ///< 授权的资源维度掩码
    uint32_t ruleReasonBits = 0;    ///< 规则原因位
};

/**
 * @brief 从交付向量构建授权结果
 * @param delivered 交付向量
 * @param computeReasonBits 计算原因位
 * @param ruleProvider 规则提供者回调
 * @return 授权构建结果
 *
 * @tparam RuleProvider 规则提供者类型，需实现 const ResourceRule*(ResourceDim) 接口
 */
template <typename RuleProvider>
inline GrantBuildResult BuildGrantFromDelivered(const ResourceVector &delivered,
                                                uint32_t computeReasonBits,
                                                RuleProvider &&ruleProvider) {
    GrantBuildResult result;

    for (size_t i = 0; i < kResourceDimCount; ++i) {
        uint32_t dimMask = 1 << i;

        if (delivered.v[i] > 0) {
            result.grantedMask |= dimMask;
        }

        const ResourceRule *rule = ruleProvider(static_cast<ResourceDim>(i));
        if (rule && delivered.v[i] < rule->min) {
            result.ruleReasonBits |= dimMask;
        }
    }

    result.ruleReasonBits |= computeReasonBits;

    return result;
}

}    // namespace vendor::transsion::perfengine::dse
