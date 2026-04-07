#include "IntentStage.h"

namespace vendor::transsion::perfengine::dse {

namespace {
// 预定义的默认资源请求查找表，替代switch-case
// 性能优化：减少分支预测失败，减少代码膨胀
struct DefaultResourceRequest {
    uint32_t cpuMin, cpuTarget, cpuMax;
    uint32_t memMin, memTarget, memMax;
    uint32_t memBwMin, memBwTarget, memBwMax;
    uint32_t gpuMin, gpuTarget, gpuMax;
    uint32_t storageBwMin, storageBwTarget, storageBwMax;
    uint32_t storageIopsMin, storageIopsTarget, storageIopsMax;
    uint32_t networkBwMin, networkBwTarget, networkBwMax;
};

// P1-P4的默认资源请求
constexpr DefaultResourceRequest kDefaultRequests[] = {
    // P1: 交互突发，最高资源保障
    {768,  1024, 1024, 256, 384, 512, 700, 900, 1024, 512, 768,
     1024, 400,  700,  900, 400, 700, 900, 128, 256,  384},

    // P2: 连续感知，高资源保障
    {512, 768, 1024, 192, 320, 512, 450, 700, 900, 256, 576,
     900, 200, 400,  700, 200, 400, 700, 256, 512, 768},

    // P3: 弹性可见，中等资源保障
    {128, 320, 512, 128, 192, 256, 200, 350, 500, 64, 200,
     400, 100, 200, 300, 100, 200, 300, 64,  128, 256},

    // P4: 后台吞吐，最低资源保障
    {32, 96, 192, 64, 96, 128, 64, 128, 192, 0, 64, 128, 0, 128, 512, 0, 128, 512, 0, 256, 768}};

// P1..P4 → 表行 0..3（IntentLevel 枚举值为 1..4，禁止误用 raw 值当下标）
inline constexpr size_t IntentLevelTableIndex(IntentLevel level) noexcept {
    const unsigned u = static_cast<unsigned>(level);
    return (u >= 1u && u <= 4u) ? static_cast<size_t>(u - 1u) : 0u;
}
}    // namespace

// IntentStage 一次性生成意图、时间和资源三类契约，确保这些互相关联的输出
// 共享同一份 SceneSemantic 和 deterministic timestamp。
StageOutput IntentStage::Execute(StageContext &ctx) {
    StageOutput out;
    if (!ctx.intermediates) {
        out.success = false;
        return out;
    }

    const auto &semantic = ctx.GetSceneSemantic();
    int64_t deterministicTs = ctx.event ? ctx.event->timestamp : 0;

    auto &loader = ctx.GetConfigLoader();
    const auto &configParams = loader.GetParams();
    const SceneRule *rule = ctx.HasSceneRule() ? ctx.GetSceneRule()
                                               : loader.FindSceneRuleByKind(semantic.kind);
    auto &intentContract = ctx.MutableIntentContract();
    FillIntentContract(intentContract, semantic, rule);
    auto &temporalContract = ctx.MutableTemporalContract();
    FillTemporalContract(temporalContract, semantic, intentContract, deterministicTs, rule);
    auto &resourceRequest = ctx.MutableResourceRequest();
    FillResourceRequest(resourceRequest, semantic, intentContract, ctx.profileResourceMask, loader,
                        configParams);

    out.success = true;
    out.dirtyBits.SetIntent();
    out.dirtyBits.SetLease();
    return out;
}

// 意图推导优先尊重配置中的 canonical SceneRule，
// 然后再按框架语义对关键场景做显式收敛，避免配置偏差破坏 P1/P2/P3/P4 边界。
IntentContract IntentStage::DeriveIntentContract(const SceneSemantic &semantic,
                                                 const SceneRule *rule) const {
    IntentContract contract;
    FillIntentContract(contract, semantic, rule);
    return contract;
}

void IntentStage::FillIntentContract(IntentContract &contract, const SceneSemantic &semantic,
                                     const SceneRule *rule) const {
    contract = IntentContract{};
    if (rule) {
        contract.level = rule->defaultIntent;
    }

    switch (semantic.kind) {
        case SceneKind::Launch:
        case SceneKind::Transition:
        case SceneKind::Scroll:
        case SceneKind::Animation:
            contract.level = IntentLevel::P1;
            break;
        case SceneKind::Gaming:
            contract.level = (semantic.phase == ScenePhase::Enter) ? IntentLevel::P1
                                                                   : IntentLevel::P2;
            break;
        case SceneKind::Camera:
            contract.level = (semantic.phase == ScenePhase::Active && semantic.continuousPerception)
                                 ? IntentLevel::P2
                                 : IntentLevel::P1;
            break;
        case SceneKind::Playback:
            if (semantic.continuousPerception || semantic.audible) {
                contract.level = IntentLevel::P2;
            } else if (semantic.visible) {
                contract.level = IntentLevel::P3;
            } else {
                contract.level = IntentLevel::P4;
            }
            break;
        case SceneKind::Download:
            contract.level = IntentLevel::P4;
            break;
        case SceneKind::Background:
            contract.level = (semantic.audible || semantic.continuousPerception) ? IntentLevel::P2
                                                                                 : IntentLevel::P4;
            break;
        case SceneKind::Unknown:
        default:
            contract.level = semantic.visible ? IntentLevel::P3 : IntentLevel::P4;
            break;
    }

    if (semantic.continuousPerception && semantic.kind != SceneKind::Scroll &&
        semantic.kind != SceneKind::Animation && semantic.kind != SceneKind::Launch &&
        semantic.kind != SceneKind::Transition) {
        // 连续感知主链路最低应保持在 P2，避免被误降为弹性可见链路。
        if (static_cast<int>(contract.level) > static_cast<int>(IntentLevel::P2)) {
            contract.level = IntentLevel::P2;
        }
    }
}

// 时间契约体现的是“资源交付持续多久、以什么模式衰减”，
// 与意图等级共同决定后续稳定化和回收行为。
TemporalContract IntentStage::DeriveTemporalContract(const SceneSemantic &semantic,
                                                     const IntentContract &intent,
                                                     int64_t deterministicTs,
                                                     const SceneRule *rule) {
    TemporalContract contract;
    FillTemporalContract(contract, semantic, intent, deterministicTs, rule);
    return contract;
}

void IntentStage::FillTemporalContract(TemporalContract &contract, const SceneSemantic &semantic,
                                       const IntentContract &intent, int64_t deterministicTs,
                                       const SceneRule *rule) {
    contract = TemporalContract{};

    contract.leaseStartTs = deterministicTs;
    contract.renewalCount = 0;
    contract.decayFactor = 1.0f;

    constexpr int64_t kNsPerMs = 1000000LL;

    if (intent.level == IntentLevel::P1) {
        // P1 是短时突发契约，租约必须足够短，避免主交互 boost 长驻。
        contract.timeMode = TimeMode::Burst;
        int64_t leaseMs = (rule && rule->defaultTimeMode == TimeMode::Burst) ? rule->defaultLeaseMs
                                                                             : 1200;
        if (semantic.kind == SceneKind::Scroll || semantic.kind == SceneKind::Animation) {
            leaseMs = 800;
        } else if (semantic.kind == SceneKind::Transition) {
            leaseMs = 1200;
        } else if (semantic.kind == SceneKind::Camera) {
            leaseMs = 1500;
        } else if (semantic.kind == SceneKind::Playback) {
            leaseMs = 1200;
        }
        contract.leaseEndTs = deterministicTs + leaseMs * kNsPerMs;
        contract.decayFactor = (rule ? rule->decayFactor : 0.8f);
        return;
    }

    if (intent.level == IntentLevel::P2) {
        // P2 是连续感知稳态契约，租约更长，强调防抖动和防中断。
        contract.timeMode = TimeMode::Steady;
        int64_t leaseMs = (rule && rule->defaultTimeMode == TimeMode::Steady) ? rule->defaultLeaseMs
                                                                              : 30000;
        if (semantic.kind == SceneKind::Playback) {
            leaseMs = 60000;
        } else if (semantic.kind == SceneKind::Gaming) {
            leaseMs = 30000;
        } else if (semantic.kind == SceneKind::Background && semantic.audible) {
            leaseMs = 60000;
        }
        contract.leaseEndTs = deterministicTs + leaseMs * kNsPerMs;
        contract.decayFactor = (rule ? rule->decayFactor : 0.95f);
        return;
    }

    if (intent.level == IntentLevel::P4) {
        // P4 是后台受控契约，允许延后和压制。
        contract.timeMode = TimeMode::Deferred;
        int64_t leaseMs =
            (rule && rule->defaultTimeMode == TimeMode::Deferred) ? rule->defaultLeaseMs : 60000;
        contract.leaseEndTs = deterministicTs + leaseMs * kNsPerMs;
        contract.decayFactor = (rule ? rule->decayFactor : 0.5f);
        return;
    }

    if (rule) {
        contract.timeMode = rule->defaultTimeMode;
        contract.leaseEndTs = deterministicTs + rule->defaultLeaseMs * kNsPerMs;
        contract.decayFactor = rule->decayFactor;
    } else {
        contract.timeMode = TimeMode::Intermittent;
        contract.leaseEndTs = deterministicTs + 3000 * kNsPerMs;
        contract.decayFactor = 0.85f;
    }
}

// 资源请求推导遵循统一的 0-1024 归一化原则，使多资源维度可以进入同一仲裁公式。
// 若配置存在对应 IntentRule，则直接使用规则；否则退化到内建安全默认值。
ResourceRequest IntentStage::DeriveResourceRequest(const SceneSemantic &semantic,
                                                   const IntentContract &intent,
                                                   uint32_t profileResourceMask,
                                                   ConfigLoader &loader,
                                                   const ConfigParams &params) {
    ResourceRequest request;
    FillResourceRequest(request, semantic, intent, profileResourceMask, loader, params);
    return request;
}

void IntentStage::FillResourceRequest(ResourceRequest &request, const SceneSemantic &semantic,
                                      const IntentContract &intent, uint32_t profileResourceMask,
                                      ConfigLoader &loader, const ConfigParams &params) {
    request = ResourceRequest{};
    request.resourceMask = params.resourceMask & profileResourceMask;

    const IntentRule *rule = loader.FindIntentRule(intent.level);

    if (rule) {
        // 显式配置优先，保证商用策略可通过规则覆盖默认基线。
        request.min[ResourceDim::CpuCapacity] = rule->cpuMin;
        request.target[ResourceDim::CpuCapacity] = rule->cpuTarget;
        request.max[ResourceDim::CpuCapacity] = rule->cpuMax;
        request.min[ResourceDim::MemoryCapacity] = rule->memMin;
        request.target[ResourceDim::MemoryCapacity] = rule->memTarget;
        request.max[ResourceDim::MemoryCapacity] = rule->memMax;
        request.min[ResourceDim::MemoryBandwidth] = rule->memBwMin;
        request.target[ResourceDim::MemoryBandwidth] = rule->memBwTarget;
        request.max[ResourceDim::MemoryBandwidth] = rule->memBwMax;
        request.min[ResourceDim::GpuCapacity] = rule->gpuMin;
        request.target[ResourceDim::GpuCapacity] = rule->gpuTarget;
        request.max[ResourceDim::GpuCapacity] = rule->gpuMax;
        request.min[ResourceDim::StorageBandwidth] = rule->storageBwMin;
        request.target[ResourceDim::StorageBandwidth] = rule->storageBwTarget;
        request.max[ResourceDim::StorageBandwidth] = rule->storageBwMax;
        request.min[ResourceDim::StorageIops] = rule->storageIopsMin;
        request.target[ResourceDim::StorageIops] = rule->storageIopsTarget;
        request.max[ResourceDim::StorageIops] = rule->storageIopsMax;
        request.min[ResourceDim::NetworkBandwidth] = rule->networkBwMin;
        request.target[ResourceDim::NetworkBandwidth] = rule->networkBwTarget;
        request.max[ResourceDim::NetworkBandwidth] = rule->networkBwMax;
    } else {
        // 默认值承担“无配置也能维持框架语义”的兜底职责。
        // 使用查找表替代switch-case，减少分支预测失败
        const size_t idx = IntentLevelTableIndex(intent.level);
        const auto &def = kDefaultRequests[idx];

        request.min[ResourceDim::CpuCapacity] = def.cpuMin;
        request.target[ResourceDim::CpuCapacity] = def.cpuTarget;
        request.max[ResourceDim::CpuCapacity] = def.cpuMax;
        request.min[ResourceDim::MemoryCapacity] = def.memMin;
        request.target[ResourceDim::MemoryCapacity] = def.memTarget;
        request.max[ResourceDim::MemoryCapacity] = def.memMax;
        request.min[ResourceDim::MemoryBandwidth] = def.memBwMin;
        request.target[ResourceDim::MemoryBandwidth] = def.memBwTarget;
        request.max[ResourceDim::MemoryBandwidth] = def.memBwMax;
        request.min[ResourceDim::GpuCapacity] = def.gpuMin;
        request.target[ResourceDim::GpuCapacity] = def.gpuTarget;
        request.max[ResourceDim::GpuCapacity] = def.gpuMax;
        request.min[ResourceDim::StorageBandwidth] = def.storageBwMin;
        request.target[ResourceDim::StorageBandwidth] = def.storageBwTarget;
        request.max[ResourceDim::StorageBandwidth] = def.storageBwMax;
        request.min[ResourceDim::StorageIops] = def.storageIopsMin;
        request.target[ResourceDim::StorageIops] = def.storageIopsTarget;
        request.max[ResourceDim::StorageIops] = def.storageIopsMax;
        request.min[ResourceDim::NetworkBandwidth] = def.networkBwMin;
        request.target[ResourceDim::NetworkBandwidth] = def.networkBwTarget;
        request.max[ResourceDim::NetworkBandwidth] = def.networkBwMax;
    }

    if (semantic.kind == SceneKind::Launch || semantic.kind == SceneKind::Transition) {
        request.min[ResourceDim::GpuCapacity] = 256;
        request.target[ResourceDim::GpuCapacity] = 512;
        request.max[ResourceDim::GpuCapacity] = 768;
        request.min[ResourceDim::StorageBandwidth] = 500;
        request.target[ResourceDim::StorageBandwidth] = 800;
        request.max[ResourceDim::StorageBandwidth] = 950;
        request.min[ResourceDim::StorageIops] = 500;
        request.target[ResourceDim::StorageIops] = 800;
        request.max[ResourceDim::StorageIops] = 950;
    }

    if (semantic.kind == SceneKind::Scroll || semantic.kind == SceneKind::Animation) {
        request.min[ResourceDim::GpuCapacity] = 600;
        request.target[ResourceDim::GpuCapacity] = 850;
        request.max[ResourceDim::GpuCapacity] = 1024;
        request.min[ResourceDim::StorageBandwidth] = 100;
        request.target[ResourceDim::StorageBandwidth] = 160;
        request.max[ResourceDim::StorageBandwidth] = 240;
        request.min[ResourceDim::StorageIops] = 100;
        request.target[ResourceDim::StorageIops] = 160;
        request.max[ResourceDim::StorageIops] = 240;
        request.min[ResourceDim::NetworkBandwidth] = 0;
        request.target[ResourceDim::NetworkBandwidth] = 64;
        request.max[ResourceDim::NetworkBandwidth] = 128;
    }

    if (semantic.kind == SceneKind::Gaming) {
        request.min[ResourceDim::GpuCapacity] = 640;
        request.target[ResourceDim::GpuCapacity] = 900;
        request.max[ResourceDim::GpuCapacity] = 1024;
        request.min[ResourceDim::MemoryCapacity] = 256;
        request.target[ResourceDim::MemoryCapacity] = 384;
        request.max[ResourceDim::MemoryCapacity] = 512;
        request.min[ResourceDim::MemoryBandwidth] = 600;
        request.target[ResourceDim::MemoryBandwidth] = 850;
        request.max[ResourceDim::MemoryBandwidth] = 1024;
        request.min[ResourceDim::NetworkBandwidth] = 192;
        request.target[ResourceDim::NetworkBandwidth] = 384;
        request.max[ResourceDim::NetworkBandwidth] = 512;
    }

    if (semantic.kind == SceneKind::Camera) {
        request.min[ResourceDim::MemoryCapacity] = 400;
        request.target[ResourceDim::MemoryCapacity] = 600;
        request.max[ResourceDim::MemoryCapacity] = 800;
        request.min[ResourceDim::GpuCapacity] = 512;
        request.target[ResourceDim::GpuCapacity] = 768;
        request.max[ResourceDim::GpuCapacity] = 1024;
        request.min[ResourceDim::StorageBandwidth] = 500;
        request.target[ResourceDim::StorageBandwidth] = 850;
        request.max[ResourceDim::StorageBandwidth] = 1024;
        request.min[ResourceDim::StorageIops] = 500;
        request.target[ResourceDim::StorageIops] = 850;
        request.max[ResourceDim::StorageIops] = 1024;
    }

    if (semantic.kind == SceneKind::Playback) {
        if (semantic.visible) {
            request.min[ResourceDim::GpuCapacity] = 300;
            request.target[ResourceDim::GpuCapacity] = 500;
            request.max[ResourceDim::GpuCapacity] = 700;
        } else {
            request.min[ResourceDim::GpuCapacity] = 0;
            request.target[ResourceDim::GpuCapacity] = 0;
            request.max[ResourceDim::GpuCapacity] = 0;
        }
        request.min[ResourceDim::StorageBandwidth] = 150;
        request.target[ResourceDim::StorageBandwidth] = 300;
        request.max[ResourceDim::StorageBandwidth] = 500;
        request.min[ResourceDim::StorageIops] = 150;
        request.target[ResourceDim::StorageIops] = 300;
        request.max[ResourceDim::StorageIops] = 500;
        request.min[ResourceDim::NetworkBandwidth] = 256;
        request.target[ResourceDim::NetworkBandwidth] = 640;
        request.max[ResourceDim::NetworkBandwidth] = 900;
    }

    if (semantic.kind == SceneKind::Download) {
        request.min[ResourceDim::CpuCapacity] = 0;
        request.target[ResourceDim::CpuCapacity] = 64;
        request.max[ResourceDim::CpuCapacity] = 128;
        request.min[ResourceDim::GpuCapacity] = 0;
        request.target[ResourceDim::GpuCapacity] = 0;
        request.max[ResourceDim::GpuCapacity] = 0;
        request.min[ResourceDim::StorageBandwidth] = 0;
        request.target[ResourceDim::StorageBandwidth] = 128;
        request.max[ResourceDim::StorageBandwidth] = 700;
        request.min[ResourceDim::StorageIops] = 0;
        request.target[ResourceDim::StorageIops] = 128;
        request.max[ResourceDim::StorageIops] = 700;
        request.min[ResourceDim::NetworkBandwidth] = 0;
        request.target[ResourceDim::NetworkBandwidth] = 256;
        request.max[ResourceDim::NetworkBandwidth] = 900;
    }
}

}    // namespace vendor::transsion::perfengine::dse
