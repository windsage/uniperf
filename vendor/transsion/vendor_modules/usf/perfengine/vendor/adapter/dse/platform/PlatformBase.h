#pragma once

// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。
// 平台执行与映射接口；首期 QCOM/MTK 验收、UNISOC 占位（任务清单 §0.2）。

#if (defined(PERFENGINE_PLATFORM_QCOM) && defined(PERFENGINE_PLATFORM_MTK)) ||    \
    (defined(PERFENGINE_PLATFORM_QCOM) && defined(PERFENGINE_PLATFORM_UNISOC)) || \
    (defined(PERFENGINE_PLATFORM_MTK) && defined(PERFENGINE_PLATFORM_UNISOC))
#error "DSE build must enable exactly one platform macro"
#endif

#include <cstdint>

#include "core/exec/ExecutorSet.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @enum PlatformVendor
 * @brief 目标平台厂商枚举。
 *
 * 该枚举只用于选择平台抽象实现，不承载 BSP 私有逻辑。
 */
enum class PlatformVendor : uint8_t { Unknown = 0, Qcom = 1, Mtk = 2, Unisoc = 3 };

/**
 * @struct PlatformCapability
 * @brief 平台能力边界的抽象描述。
 *
 * 框架只关心“当前平台能否执行哪类资源控制”和“具备哪些抽象执行能力”，
 * 例如 PerfHintHal、PM QoS、cgroup v2 等；具体 sysfs 节点或 HAL ID
 * 属于平台层实现细节，不应泄漏到调度主链。
 */
struct PlatformCapability {
    PlatformVendor vendor = PlatformVendor::Unknown;
    uint32_t socId = 0;
    uint32_t supportedResources = 0;
    uint32_t supportedActions = 0;
    bool hasPerfHintHal = false;
    bool hasPmQos = false;
    bool hasCgroupV2 = false;
    bool hasEbpf = false;
    bool hasGpuPreemption = false;
};

/**
 * @brief 平台特征标志位。
 *
 * 使用高位编码，避免与 CapabilityFeasible 中低位的通用 capability flag
 * 或资源维度 bitmask 冲突。
 */
enum : uint32_t {
    kPlatformTraitPerfHintHal = 0x00000100u,
    kPlatformTraitPmQos = 0x00000200u,
    kPlatformTraitCgroupV2 = 0x00000400u,
    kPlatformTraitEbpf = 0x00000800u,
    kPlatformTraitGpuPreemption = 0x00001000u
};

/**
 * @brief 将平台能力描述编码为统一 trait 标志。
 * @param capability 平台能力描述。
 * @return trait bitmask。
 *
 * @note 该函数用于状态边界与执行边界之间传递平台抽象特征，
 * 使 stage 层只消费 trait，而不需要重新探测平台。
 */
inline uint32_t ComputePlatformTraitFlags(const PlatformCapability &capability) noexcept {
    uint32_t traits = 0;
    if (capability.hasPerfHintHal)
        traits |= kPlatformTraitPerfHintHal;
    if (capability.hasPmQos)
        traits |= kPlatformTraitPmQos;
    if (capability.hasCgroupV2)
        traits |= kPlatformTraitCgroupV2;
    if (capability.hasEbpf)
        traits |= kPlatformTraitEbpf;
    if (capability.hasGpuPreemption)
        traits |= kPlatformTraitGpuPreemption;
    return traits;
}

/**
 * @class IPlatformExecutor
 * @brief 平台执行器抽象接口。
 *
 * 负责把已经确定的抽象平台命令真正下发到 HAL、kernel control、
 * cgroup 或其他平台控制面。
 */
class IPlatformExecutor {
public:
    virtual ~IPlatformExecutor() = default;

    /** @brief 执行一批平台命令。 */
    virtual PlatformExecutionResult Execute(const PlatformCommandBatch &batch) = 0;
    /** @brief 返回当前执行器所对应的平台能力描述。 */
    virtual PlatformCapability GetCapability() const = 0;
    /** @brief 返回执行器名称，主要用于日志与调试。 */
    virtual const char *GetName() const = 0;
};

/**
 * @class IPlatformActionMap
 * @brief 平台动作映射接口。
 *
 * 把调度层产出的 AbstractActionBatch 映射为厂商平台可识别的命令批次。
 */
class IPlatformActionMap {
public:
    virtual ~IPlatformActionMap() = default;

    /** @brief 执行抽象动作到平台命令的映射。 */
    virtual PlatformCommandBatch Map(const AbstractActionBatch &abstractBatch) = 0;
    /** @brief 快路径直接把 FastGrant 映射为平台命令，避免额外构造抽象动作批次。 */
    virtual PlatformCommandBatch MapFastGrant(const FastGrant &grant, IntentLevel effectiveIntent,
                                              TimeMode timeMode) = 0;
    /** @brief 生成某资源维度在平台侧的动作键。 */
    virtual uint16_t GetActionKey(ResourceDim dim, uint32_t actionType) const = 0;
};

/**
 * @struct PlatformContext
 * @brief 资源调度服务使用的平台上下文。
 *
 * 包含平台能力快照以及动作映射/执行器两个运行时入口。
 */
struct PlatformContext {
    PlatformCapability capability;
    IPlatformExecutor *executor = nullptr;
    IPlatformActionMap *actionMap = nullptr;
};

}    // namespace vendor::transsion::perfengine::dse
