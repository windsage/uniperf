#pragma once

/**
 * @file Types.h
 * @brief DSE 公开基础类型定义
 *
 * 本文件定义了整机资源确定性调度抽象框架的核心基础类型：
 * - 意图等级 (IntentLevel): P1~P4 资源交付契约
 * - 时间模式 (TimeMode): 租约/衰减语义分类
 * - 资源维度 (ResourceDim): Canonical 资源坐标轴
 * - 资源向量 (ResourceVector): 空间维统一抽象
 *
 * @see docs/整机资源确定性调度抽象框架.md
 * @see docs/重构任务清单.md (v3.1)
 */

#include <cstddef>
#include <cstdint>

namespace vendor::transsion::perfengine::dse {

/**
 * @enum IntentLevel
 * @brief 资源交付契约等级 P1~P4
 *
 * 意图等级描述工作负载在当前场景下应获得的资源交付契约，
 * 而非简单的进程优先级标签。同一任务会随场景变化动态迁移。
 *
 * | 等级 | 定义 | 响应要求 | 资源契约 | 典型场景 |
 * |------|------|----------|----------|----------|
 * | P1 | 主交互型 | 亚毫秒级 | 下限契约 | 启动、触控、首帧 |
 * | P2 | 连续感知型 | 毫秒级稳定 | 稳态契约 | 视频、游戏、导航 |
 * | P3 | 弹性可见型 | 可伸缩 | 弹性契约 | 静止界面、阅读 |
 * | P4 | 后台吞吐型 | 可压制 | 上限契约 | 下载、同步、索引 |
 *
 * @note P1 是短时突发契约，不应长期保持
 * @note P2 的判定标准是"是否构成连续感知主链路"
 * @note 约束维度（热/电/熄屏）始终高于意图等级
 */
enum class IntentLevel : uint8_t {
    P1 = 1,    ///< 主交互型：用户正在直接操作，核心诉求是瞬时响应
    P2 = 2,    ///< 连续感知型：用户连续感知主链路，核心诉求是稳态防抖
    P3 = 3,    ///< 弹性可见型：用户可见但不构成主链路，核心诉求是基本可用
    P4 = 4     ///< 后台吞吐型：用户无直接感知，核心诉求是服从整机约束
};

/**
 * @enum TimeMode
 * @brief 时间维度的租约/衰减语义模式分类
 *
 * 时间维度将 PELT 的指数衰减思想显式化，区分瞬时峰值与长期占用，
 * 指导资源预留和租约管理。
 *
 * | 模式 | 特征 | 典型场景 | 租约策略 |
 * |------|------|----------|----------|
 * | Burst | 短时突发，≤2s | 启动、触控、动画 | 短租约，快速衰减 |
 * | Steady | 长期稳态 | 视频、游戏、导航 | 长租约，续租机制 |
 * | Intermittent | 间歇性 | 阅读、浏览 | 按需续租 |
 * | Deferred | 可延迟 | 后台同步、索引 | 无租约，按空闲调度 |
 */
enum class TimeMode : uint8_t {
    Burst = 1,           ///< 突发型：短时高资源需求，快速衰减
    Steady = 2,          ///< 稳态型：长期稳定资源需求，支持续租
    Intermittent = 3,    ///< 间歇型：周期性或按需资源需求
    Deferred = 4         ///< 延迟型：可延迟执行的后台任务
};

/**
 * @enum ResourceDim
 * @brief Core 层统一资源语义坐标轴（Canonical 资源维度）
 *
 * 定义 8 个标准资源维度，槽位语义编译期固定，不得被平台重定义。
 * 这确保了决策层的资源语义与平台实现解耦。
 *
 * | 维度 | 物理含义 | 对应框架章节 |
 * |------|----------|-------------|
 * | CpuCapacity | CPU 算力 (0-1024) | §3.2 CPU 抽象 |
 * | MemoryCapacity | 内存容量 (MB) | §3.1 内存抽象 |
 * | MemoryBandwidth | DDR 总线带宽 (MB/s) | §3.1 内存抽象 |
 * | GpuCapacity | GPU 计算权重 (0-1024) | §3.3 GPU 抽象 |
 * | StorageBandwidth | 存储带宽 (MB/s) | §3.4.1 存储 I/O |
 * | StorageIops | 存储 IOPS | §3.4.1 存储 I/O |
 * | NetworkBandwidth | 网络带宽 (Mbps) | §3.4.2 网络 I/O |
 * | Reserved | 预留扩展 | - |
 *
 * @note 平台单位在 PlatformActionMapper 执行链下沉时转换
 */
enum class ResourceDim : uint8_t {
    CpuCapacity = 0,         ///< CPU 算力：归一化到 0-1024，对齐 EAS capacity
    MemoryCapacity = 1,      ///< 内存容量：物理内存预留量 (MB)
    MemoryBandwidth = 2,     ///< 内存带宽：DDR 总线带宽需求 (MB/s)
    GpuCapacity = 3,         ///< GPU 算力：计算权重 0-1024
    StorageBandwidth = 4,    ///< 存储带宽：UFS/NVMe 读写带宽 (MB/s)
    StorageIops = 5,         ///< 存储 IOPS：每秒 I/O 操作次数
    NetworkBandwidth = 6,    ///< 网络带宽：上下行带宽 (Mbps)
    Reserved = 7             ///< 预留维度：供未来扩展
};

/// 资源维度总数
constexpr size_t kResourceDimCount = 8;

/**
 * @struct ResourceVector
 * @brief 空间维统一抽象：多维度资源向量
 *
 * ResourceVector 是框架空间维度的核心数据结构，统一表达：
 * - 资源需求 (ResourceRequest)
 * - 约束边界 (ConstraintAllowed)
 * - 能力上限 (CapabilityFeasible)
 * - 交付结果 (ResourceGrant)
 *
 * 设计原则：
 * 1. 归一化：所有资源在同一坐标系中表达
 * 2. 平台无关：Core 层使用抽象单位，平台层转换为硬件指令
 * 3. Canonical：维度语义编译期固定，避免运行时解析
 *
 * @note 平台单位在 PlatformActionMapper / 执行链下沉时转换
 */
struct ResourceVector {
    uint32_t v[kResourceDimCount] = {0};    ///< 各维度资源值数组

    /**
     * @brief 按资源维度索引访问（可修改）
     * @param dim 资源维度枚举
     * @return 该维度的资源值引用
     */
    uint32_t &operator[](ResourceDim dim) { return v[static_cast<size_t>(dim)]; }

    /**
     * @brief 按资源维度索引访问（只读）
     * @param dim 资源维度枚举
     * @return 该维度的资源值常量引用
     */
    const uint32_t &operator[](ResourceDim dim) const { return v[static_cast<size_t>(dim)]; }

    /**
     * @brief 向量相等比较
     * @param other 比较对象
     * @return 所有维度值相等时返回 true
     */
    bool operator==(const ResourceVector &other) const {
        for (size_t i = 0; i < kResourceDimCount; ++i) {
            if (v[i] != other.v[i])
                return false;
        }
        return true;
    }

    /**
     * @brief 向量不等比较
     * @param other 比较对象
     * @return 存在维度值不等时返回 true
     */
    bool operator!=(const ResourceVector &other) const { return !(*this == other); }

    /**
     * @brief 清零所有维度
     */
    void Clear() {
        for (size_t i = 0; i < kResourceDimCount; ++i) {
            v[i] = 0;
        }
    }

    /**
     * @brief 就地取最小值
     * @param other 比较向量
     *
     * 将当前向量各维度值更新为与 other 对应维度的较小值。
     * 用于计算约束后的实际交付量。
     */
    void MinWith(const ResourceVector &other) {
        for (size_t i = 0; i < kResourceDimCount; ++i) {
            if (other.v[i] < v[i]) {
                v[i] = other.v[i];
            }
        }
    }

    /**
     * @brief 就地取最大值
     * @param other 比较向量
     *
     * 将当前向量各维度值更新为与 other 对应维度的较大值。
     * 用于合并多个资源需求。
     */
    void MaxWith(const ResourceVector &other) {
        for (size_t i = 0; i < kResourceDimCount; ++i) {
            if (other.v[i] > v[i]) {
                v[i] = other.v[i];
            }
        }
    }

    /**
     * @brief 静态方法：返回两个向量的逐维度最小值
     * @param a 向量 a
     * @param b 向量 b
     * @return 新向量，各维度为 min(a[i], b[i])
     */
    static ResourceVector Min(const ResourceVector &a, const ResourceVector &b) {
        ResourceVector result;
        for (size_t i = 0; i < kResourceDimCount; ++i) {
            result.v[i] = (a.v[i] < b.v[i]) ? a.v[i] : b.v[i];
        }
        return result;
    }

    /**
     * @brief 静态方法：返回两个向量的逐维度最大值
     * @param a 向量 a
     * @param b 向量 b
     * @return 新向量，各维度为 max(a[i], b[i])
     */
    static ResourceVector Max(const ResourceVector &a, const ResourceVector &b) {
        ResourceVector result;
        for (size_t i = 0; i < kResourceDimCount; ++i) {
            result.v[i] = (a.v[i] > b.v[i]) ? a.v[i] : b.v[i];
        }
        return result;
    }

    /**
     * @brief 资源向量缩放 (Q10 确定性整数算术)
     * @param rv 待缩放向量
     * @param factorQ10 缩放因子 (1024 = 1.0)
     * @return 缩放后的新向量
     *
     * 使用 64 位中间值防止溢出，并保持 bit-level 确定性。
     */
    static ResourceVector Scale(const ResourceVector &rv, uint32_t factorQ10) {
        ResourceVector result;
        for (size_t i = 0; i < kResourceDimCount; ++i) {
            result.v[i] =
                static_cast<uint32_t>((static_cast<uint64_t>(rv.v[i]) * factorQ10) / 1024);
        }
        return result;
    }
};
/// 场景标识符：标识唯一的场景实例
using SceneId = int64_t;

/// 租约标识符：标识资源租约的唯一性
using LeaseId = int64_t;

/// 会话标识符：标识一次调度决策的完整生命周期
using SessionId = int64_t;

/// 世代号：用于状态快照的一致性版本控制 (GenerationPinning)
using Generation = uint64_t;

/**
 * @brief 意图等级转字符串
 * @param level 意图等级枚举值
 * @return 对应的字符串表示 ("P1"/"P2"/"P3"/"P4"/"Unknown")
 */
constexpr const char *IntentLevelToString(IntentLevel level) {
    switch (level) {
        case IntentLevel::P1:
            return "P1";
        case IntentLevel::P2:
            return "P2";
        case IntentLevel::P3:
            return "P3";
        case IntentLevel::P4:
            return "P4";
        default:
            return "Unknown";
    }
}

/**
 * @brief 时间模式转字符串
 * @param mode 时间模式枚举值
 * @return 对应的字符串表示
 */
constexpr const char *TimeModeToString(TimeMode mode) {
    switch (mode) {
        case TimeMode::Burst:
            return "Burst";
        case TimeMode::Steady:
            return "Steady";
        case TimeMode::Intermittent:
            return "Intermittent";
        case TimeMode::Deferred:
            return "Deferred";
        default:
            return "Unknown";
    }
}

}    // namespace vendor::transsion::perfengine::dse
