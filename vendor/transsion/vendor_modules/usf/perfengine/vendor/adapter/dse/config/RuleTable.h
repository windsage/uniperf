#pragma once

// 资源/约束/仲裁规则数据；不得承载 Stage 顺序或流程控制（任务清单 §10.3）。

#include <cstdint>

#include "core/types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

struct ConstraintRule {
    uint32_t thermalThreshold = 4;
    uint32_t batteryThreshold = 20;
    uint32_t memoryPressureThreshold = 3;
    float thermalScale = 0.5f;
    float batteryScale = 0.7f;
    float memoryScale = 0.6f;
};

struct ArbitrationRule {
    bool enablePreemption = true;
    bool enableInheritance = true;
    uint32_t maxHierarchyDepth = 2;
    int64_t inheritanceDurationMs = 2000;
    uint32_t arbitrationFlags = 0;
};

class RuleTable {
public:
    static RuleTable &Instance();

    bool Load(const char *rulePath);
    bool Reload();

    const ResourceRule *GetResourceRule(ResourceDim dim) const;
    const ConstraintRule &GetConstraintRule() const { return constraintRule_; }
    const ArbitrationRule &GetArbitrationRule() const { return arbitrationRule_; }

    void SetResourceRule(ResourceDim dim, const ResourceRule &rule);
    void SetConstraintRule(const ConstraintRule &rule);
    void SetArbitrationRule(const ArbitrationRule &rule);

    bool IsLoaded() const { return loaded_; }
    uint32_t GetRuleVersion() const { return ruleVersion_; }
    void SetRuleVersion(uint32_t version) { ruleVersion_ = version; }

    const char *GetRulePath() const { return rulePath_; }

private:
    RuleTable();

    void SetDefaults();

    ResourceRule resourceRules_[kResourceDimCount];
    ConstraintRule constraintRule_;
    ArbitrationRule arbitrationRule_;
    bool loaded_ = false;
    uint32_t ruleVersion_ = 1;
    char rulePath_[256] = {0};
};

}    // namespace vendor::transsion::perfengine::dse
