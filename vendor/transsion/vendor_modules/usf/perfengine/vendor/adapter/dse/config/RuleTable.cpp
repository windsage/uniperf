#include "RuleTable.h"

#include <cstring>

#include "ConfigLoader.h"

namespace vendor::transsion::perfengine::dse {
namespace {

// Reload() 会传入 rulePath_ 自身；strncpy 禁止 dest==src 重叠，改用 memmove 截断拷贝。
void CopyStoredPath(char *dest, size_t destCap, const char *src) {
    if (destCap == 0) {
        return;
    }
    if (!src) {
        dest[0] = '\0';
        return;
    }
    const size_t maxLen = destCap - 1;
    const size_t n = strnlen(src, maxLen);
    memmove(dest, src, n);
    dest[n] = '\0';
}

void ApplyConfigOverrides(ResourceRule (&rules)[kResourceDimCount], const ConfigParams &params) {
    const uint32_t enabledMask = params.resourceMask;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        const uint32_t dimMask = (1u << i);
        if ((enabledMask & dimMask) != 0) {
            continue;
        }
        rules[i].min = 0;
        rules[i].target = 0;
        rules[i].max = 0;
        rules[i].step = 0;
        rules[i].weight = 0.0f;
    }
}
}    // namespace

RuleTable &RuleTable::Instance() {
    static RuleTable instance;
    return instance;
}

RuleTable::RuleTable() {
    SetDefaults();
}

bool RuleTable::Load(const char *rulePath) {
    CopyStoredPath(rulePath_, sizeof(rulePath_), rulePath);

    auto &loader = ConfigLoader::Instance();
    if (rulePath && rulePath[0] != '\0') {
        const auto &params = loader.GetParams();
        const bool sameCanonicalPath =
            loader.IsLoaded() &&
            (strncmp(params.configPath, rulePath, sizeof(params.configPath)) == 0);
        if (!sameCanonicalPath && !loader.Load(rulePath)) {
            loaded_ = false;
            return false;
        }
    }

    SetDefaults();
    const auto &configParams = loader.GetParams();
    ApplyConfigOverrides(resourceRules_, configParams);
    ruleVersion_ = (configParams.ruleVersion != 0) ? configParams.ruleVersion : 1;
    loaded_ = true;
    return true;
}

bool RuleTable::Reload() {
    return Load(rulePath_[0] != '\0' ? rulePath_ : nullptr);
}

const ResourceRule *RuleTable::GetResourceRule(ResourceDim dim) const {
    size_t index = static_cast<size_t>(dim);
    if (index < kResourceDimCount) {
        return &resourceRules_[index];
    }
    return nullptr;
}

void RuleTable::SetResourceRule(ResourceDim dim, const ResourceRule &rule) {
    size_t index = static_cast<size_t>(dim);
    if (index < kResourceDimCount) {
        resourceRules_[index] = rule;
    }
}

void RuleTable::SetConstraintRule(const ConstraintRule &rule) {
    constraintRule_ = rule;
}

void RuleTable::SetArbitrationRule(const ArbitrationRule &rule) {
    arbitrationRule_ = rule;
}

void RuleTable::SetDefaults() {
    resourceRules_[0] = {ResourceDim::CpuCapacity, 50, 500, 1024, 100, 1.0f};
    resourceRules_[1] = {ResourceDim::MemoryCapacity, 50, 300, 800, 50, 0.8f};
    resourceRules_[2] = {ResourceDim::MemoryBandwidth, 50, 400, 1024, 100, 0.9f};
    resourceRules_[3] = {ResourceDim::GpuCapacity, 0, 300, 1024, 100, 0.7f};
    resourceRules_[4] = {ResourceDim::StorageBandwidth, 0, 200, 600, 50, 0.5f};
    resourceRules_[5] = {ResourceDim::StorageIops, 0, 200, 600, 50, 0.5f};
    resourceRules_[6] = {ResourceDim::NetworkBandwidth, 0, 200, 600, 50, 0.4f};
    resourceRules_[7] = {ResourceDim::Reserved, 0, 0, 0, 0, 0.0f};

    constraintRule_.thermalThreshold = 4;
    constraintRule_.batteryThreshold = 20;
    constraintRule_.memoryPressureThreshold = 3;
    constraintRule_.thermalScale = 0.5f;
    constraintRule_.batteryScale = 0.7f;
    constraintRule_.memoryScale = 0.6f;

    arbitrationRule_.enablePreemption = true;
    arbitrationRule_.enableInheritance = true;
    arbitrationRule_.maxHierarchyDepth = 2;
    arbitrationRule_.inheritanceDurationMs = 2000;
    arbitrationRule_.arbitrationFlags = 0;
}

}    // namespace vendor::transsion::perfengine::dse
