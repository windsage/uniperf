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

#include "PlatformRegistry.h"

namespace vendor::transsion::perfengine::dse {
namespace {
// 平台启用状态只由编译宏决定，避免在运行时因探测不一致破坏确定性。
bool IsVendorEnabledByBuild(PlatformVendor vendor) {
    switch (vendor) {
        case PlatformVendor::Qcom:
#if defined(PERFENGINE_PLATFORM_QCOM)
            return true;
#else
            return false;
#endif
        case PlatformVendor::Mtk:
#if defined(PERFENGINE_PLATFORM_MTK)
            return true;
#else
            return false;
#endif
        case PlatformVendor::Unisoc:
#if defined(PERFENGINE_PLATFORM_UNISOC)
            return true;
#else
            return false;
#endif
        default:
            return false;
    }
}

// 未启用平台必须显式退化到 Unknown，防止主链误以为平台能力有效。
void FillCapabilityFallback(PlatformVendor vendor, PlatformCapability &cap) {
    cap = PlatformCapability{};
    cap.vendor = vendor;
    if (!IsVendorEnabledByBuild(vendor)) {
        cap.vendor = PlatformVendor::Unknown;
    }
}
}    // namespace

// 注册表是进程级共享对象；平台注册通常在静态初始化阶段完成。
PlatformRegistry &PlatformRegistry::Instance() {
    static PlatformRegistry instance;
    return instance;
}

bool PlatformRegistry::HasAnyFactory(const FactoryEntry &entry) {
    return static_cast<bool>(entry.executorFactory) || static_cast<bool>(entry.actionMapFactory) ||
           static_cast<bool>(entry.capabilityFactory) ||
           static_cast<bool>(entry.stateBackendFactory);
}

// 平台枚举数量固定，因此这里使用定长数组而非 map，查找成本稳定且无额外分配。
PlatformRegistry::FactoryEntry *PlatformRegistry::ResolveEntry(FactoryEntry (&entries)[4],
                                                               PlatformVendor vendor) {
    const size_t index = static_cast<size_t>(vendor);
    if (index >= 4) {
        return nullptr;
    }
    return &entries[index];
}

const PlatformRegistry::FactoryEntry *PlatformRegistry::ResolveEntry(
    const FactoryEntry (&entries)[4], PlatformVendor vendor) {
    const size_t index = static_cast<size_t>(vendor);
    if (index >= 4) {
        return nullptr;
    }
    return &entries[index];
}

void PlatformRegistry::Register(PlatformVendor vendor, PlatformExecutorFactory executorFactory,
                                PlatformActionMapFactory actionMapFactory) {
    if (auto *entry = ResolveEntry(entries_, vendor)) {
        entry->executorFactory = executorFactory;
        entry->actionMapFactory = actionMapFactory;
    }
}

void PlatformRegistry::RegisterCapability(PlatformVendor vendor,
                                          PlatformCapabilityFactory capabilityFactory) {
    if (auto *entry = ResolveEntry(entries_, vendor)) {
        entry->capabilityFactory = capabilityFactory;
    }
}

void PlatformRegistry::RegisterStateBackend(PlatformVendor vendor,
                                            PlatformStateBackendFactory stateBackendFactory) {
    if (auto *entry = ResolveEntry(entries_, vendor)) {
        entry->stateBackendFactory = stateBackendFactory;
    }
}

// CreateContext 只负责拼装抽象平台上下文，不做任何 BSP 细节判断。
// 如果平台能力为空，会直接返回空上下文，让上层走显式 fallback。
PlatformContext PlatformRegistry::CreateContext(PlatformVendor vendor) {
    PlatformContext ctx;
    FillPlatformCapability(vendor, ctx.capability);
    if (ctx.capability.vendor == PlatformVendor::Unknown ||
        ctx.capability.supportedResources == 0) {
        return ctx;
    }

    const auto *entry = ResolveEntry(entries_, vendor);
    if (!entry) {
        return ctx;
    }
    if (entry->executorFactory) {
        ctx.executor = entry->executorFactory(ctx.capability);
    }
    if (entry->actionMapFactory) {
        ctx.actionMap = entry->actionMapFactory(ctx.capability);
    }

    return ctx;
}

// 状态后端是平台层的边界入口，因此这里单独提供工厂方法，
// 供 StateService 初始化时使用。
IPlatformStateBackend *PlatformRegistry::CreateStateBackend(PlatformVendor vendor) {
    const auto *entry = ResolveEntry(entries_, vendor);
    if (!entry || !entry->stateBackendFactory || !IsVendorEnabledByBuild(vendor)) {
        return nullptr;
    }
    return entry->stateBackendFactory();
}

// 平台能力由各平台文件自行注册填充；注册表只负责调度，不持有平台硬编码。
void PlatformRegistry::FillPlatformCapability(PlatformVendor vendor, PlatformCapability &cap) {
    const auto *entry = ResolveEntry(Instance().entries_, vendor);
    FillCapabilityFallback(vendor, cap);
    if (!entry || !entry->capabilityFactory || cap.vendor == PlatformVendor::Unknown) {
        cap.vendor = PlatformVendor::Unknown;
        return;
    }
    entry->capabilityFactory(cap);
}

bool PlatformRegistry::IsRegistered(PlatformVendor vendor) const {
    const auto *entry = ResolveEntry(entries_, vendor);
    if (!entry) {
        return false;
    }
    return HasAnyFactory(*entry);
}

// 框架要求一个构建产物只面向单一目标平台；若同时启用多个平台宏，
// 返回 Unknown 以强制暴露构建配置错误。
PlatformVendor PlatformRegistry::DetectVendor() const {
    int enabledPlatforms = 0;
#if defined(PERFENGINE_PLATFORM_QCOM)
    ++enabledPlatforms;
#endif
#if defined(PERFENGINE_PLATFORM_MTK)
    ++enabledPlatforms;
#endif
#if defined(PERFENGINE_PLATFORM_UNISOC)
    ++enabledPlatforms;
#endif

    if (enabledPlatforms != 1) {
        return PlatformVendor::Unknown;
    }

#if defined(PERFENGINE_PLATFORM_QCOM)
    return PlatformVendor::Qcom;
#elif defined(PERFENGINE_PLATFORM_MTK)
    return PlatformVendor::Mtk;
#elif defined(PERFENGINE_PLATFORM_UNISOC)
    return PlatformVendor::Unisoc;
#else
    return PlatformVendor::Unknown;
#endif
}

// Clear 主要供单元测试使用，生产路径不会频繁调用。
void PlatformRegistry::Clear() {
    for (size_t i = 0; i < 4; ++i) {
        entries_[i].executorFactory = nullptr;
        entries_[i].actionMapFactory = nullptr;
        entries_[i].capabilityFactory = nullptr;
        entries_[i].stateBackendFactory = nullptr;
    }
}

}    // namespace vendor::transsion::perfengine::dse
