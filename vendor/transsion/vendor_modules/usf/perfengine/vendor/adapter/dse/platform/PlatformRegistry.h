#pragma once

// DSE — 依据 docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。
//
// 平台能力编译时配置：
// 本模块采用编译时平台能力确定策略，不进行运行时动态探测。
// 平台类型和能力通过编译宏在编译阶段确定：
//   - PERFENGINE_PLATFORM_QCOM:    Qualcomm 平台
//   - PERFENGINE_PLATFORM_MTK:     MediaTek 平台
//   - PERFENGINE_PLATFORM_UNISOC:  Unisoc 平台
//
// 未注册平台必须显式失败，不得静默返回空能力。

#include <cstdint>
#include <functional>

#include "PlatformBase.h"
#include "platform/state/IPlatformStateBackend.h"

namespace vendor::transsion::perfengine::dse {

using PlatformExecutorFactory = std::function<IPlatformExecutor *(const PlatformCapability &)>;
using PlatformActionMapFactory = std::function<IPlatformActionMap *(const PlatformCapability &)>;
using PlatformCapabilityFactory = std::function<void(PlatformCapability &)>;
using PlatformStateBackendFactory = std::function<IPlatformStateBackend *()>;

/**
 * @class PlatformRegistry
 * @brief 平台抽象注册表。
 *
 * 该类负责把“平台选择”收敛到统一入口：
 * - 平台能力描述
 * - 动作映射器
 * - 执行器
 * - 状态后端
 *
 * 调度主链只能依赖本注册表和抽象接口，不应直接 new 具体平台类。
 */
class PlatformRegistry {
public:
    /** @brief 获取单例注册表。 */
    static PlatformRegistry &Instance();

    /**
     * @brief 注册平台执行器与动作映射器工厂。
     * @param vendor 平台厂商。
     * @param executorFactory 执行器工厂。
     * @param actionMapFactory 动作映射工厂。
     */
    void Register(PlatformVendor vendor, PlatformExecutorFactory executorFactory,
                  PlatformActionMapFactory actionMapFactory);

    /** @brief 注册平台能力填充函数。 */
    void RegisterCapability(PlatformVendor vendor, PlatformCapabilityFactory capabilityFactory);

    /** @brief 注册平台状态后端工厂。 */
    void RegisterStateBackend(PlatformVendor vendor,
                              PlatformStateBackendFactory stateBackendFactory);

    /**
     * @brief 创建平台上下文。
     * @param vendor 平台厂商。
     * @return 平台上下文；若平台未注册或未启用则返回空上下文。
     */
    PlatformContext CreateContext(PlatformVendor vendor);

    /**
     * @brief 创建平台状态后端。
     * @param vendor 平台厂商。
     * @return 状态后端实例；若平台未启用或未注册则返回 nullptr。
     */
    IPlatformStateBackend *CreateStateBackend(PlatformVendor vendor);

    /** @brief 判断指定平台是否已注册任一工厂。 */
    bool IsRegistered(PlatformVendor vendor) const;

    /**
     * @brief 根据编译宏检测当前启用的平台。
     * @return 唯一启用的平台；若未启用或同时启用多个平台则返回 Unknown。
     */
    PlatformVendor DetectVendor() const;

    /** @brief 清空所有注册项，主要用于测试场景。 */
    void Clear();

    /**
     * @brief 填充平台能力信息
     * @param vendor 平台厂商（仅用于日志，实际能力由编译宏确定）
     * @param cap 输出的平台能力结构
     *
     * @note 平台能力由编译时宏定义确定，不进行运行时探测：
     *       - PERFENGINE_PLATFORM_QCOM:    QCOM 平台能力
     *       - PERFENGINE_PLATFORM_MTK:     MTK 平台能力
     *       - PERFENGINE_PLATFORM_UNISOC:  Unisoc 平台能力
     *       - 未定义:               空能力（所有资源 unsupported）
     */
    static void FillPlatformCapability(PlatformVendor vendor, PlatformCapability &cap);

private:
    struct FactoryEntry {
        PlatformExecutorFactory executorFactory;
        PlatformActionMapFactory actionMapFactory;
        PlatformCapabilityFactory capabilityFactory;
        PlatformStateBackendFactory stateBackendFactory;
    };

    PlatformRegistry() = default;

    /** @brief 判断一个注册项中是否至少存在一种可用工厂。 */
    static bool HasAnyFactory(const FactoryEntry &entry);
    /** @brief 解析指定平台在工厂数组中的注册项。 */
    static FactoryEntry *ResolveEntry(FactoryEntry (&entries)[4], PlatformVendor vendor);
    /** @brief 常量版本的注册项解析。 */
    static const FactoryEntry *ResolveEntry(const FactoryEntry (&entries)[4],
                                            PlatformVendor vendor);

    FactoryEntry entries_[4];
};

class PlatformRegistrar {
public:
    /** @brief 构造时立即完成平台执行链注册。 */
    PlatformRegistrar(PlatformVendor vendor, PlatformExecutorFactory executorFactory,
                      PlatformActionMapFactory actionMapFactory) {
        PlatformRegistry::Instance().Register(vendor, executorFactory, actionMapFactory);
    }
};

class PlatformCapabilityRegistrar {
public:
    /** @brief 构造时立即完成平台能力工厂注册。 */
    PlatformCapabilityRegistrar(PlatformVendor vendor,
                                PlatformCapabilityFactory capabilityFactory) {
        PlatformRegistry::Instance().RegisterCapability(vendor, capabilityFactory);
    }
};

class PlatformStateBackendRegistrar {
public:
    /** @brief 构造时立即完成平台状态后端工厂注册。 */
    PlatformStateBackendRegistrar(PlatformVendor vendor,
                                  PlatformStateBackendFactory stateBackendFactory) {
        PlatformRegistry::Instance().RegisterStateBackend(vendor, stateBackendFactory);
    }
};

#define DSE_REGISTER_PLATFORM(vendor_id, executorFactory, actionMapFactory)                   \
    static ::vendor::transsion::perfengine::dse::PlatformRegistrar                            \
        _platform_registrar_##vendor_id(                                                      \
            ::vendor::transsion::perfengine::dse::PlatformVendor::vendor_id, executorFactory, \
            actionMapFactory)

#define DSE_REGISTER_PLATFORM_CAPABILITY(vendor_id, capabilityFactory)       \
    static ::vendor::transsion::perfengine::dse::PlatformCapabilityRegistrar \
        _platform_capability_registrar_##vendor_id(                          \
            ::vendor::transsion::perfengine::dse::PlatformVendor::vendor_id, capabilityFactory)

#define DSE_REGISTER_PLATFORM_STATE_BACKEND(vendor_id, stateBackendFactory)    \
    static ::vendor::transsion::perfengine::dse::PlatformStateBackendRegistrar \
        _platform_state_backend_registrar_##vendor_id(                         \
            ::vendor::transsion::perfengine::dse::PlatformVendor::vendor_id, stateBackendFactory)

}    // namespace vendor::transsion::perfengine::dse
