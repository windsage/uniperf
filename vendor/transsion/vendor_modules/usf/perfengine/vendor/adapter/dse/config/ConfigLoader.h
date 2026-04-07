#pragma once

#include <array>
#include <cstdint>
#include <functional>

#include "core/types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @struct ConfigParams
 * @brief DSE 通用配置参数。
 *
 * 该结构只承载框架级、平台无关的配置：
 * - 稳定化窗口与驻留时间
 * - 更新节流参数
 * - 资源维度掩码
 * - 配置版本与内容哈希
 *
 * 按《整机资源确定性调度抽象框架》要求，平台/SoC 私有频点、
 * BSP hint 等信息不得进入该通用配置模型。
 */
struct ConfigParams {
    int64_t observationWindowMs = 100;
    int64_t minResidencyMs = 50;
    int64_t hysteresisEnterMs = 0;
    int64_t hysteresisExitMs = 0;
    int64_t updateThrottleMs = 10;
    uint32_t steadyEnterMerges = 2;
    uint32_t exitHysteresisMerges = 1;
    uint32_t traceLevel = 2;
    uint32_t resourceMask = 0xFF;
    uint32_t configVersion = 1;
    uint32_t ruleVersion = 1;
    uint32_t contentHash = 0;
    char configPath[256] = {0};
};

/**
 * @struct SceneRule
 * @brief 场景语义到意图/时间契约的归一化规则。
 *
 * SceneRule 是框架中的 canonical 表达，用于把上游事件或配置中的
 * 场景名称映射为统一的 SceneKind、IntentLevel 和 TimeMode。
 */
struct SceneRule {
    uint32_t actionMask = 0;
    SceneKind kind = SceneKind::Unknown;
    IntentLevel defaultIntent = IntentLevel::P3;
    TimeMode defaultTimeMode = TimeMode::Intermittent;
    int64_t defaultLeaseMs = 3000;
    float decayFactor = 0.85f;
    bool visible = false;
    bool audible = false;
    bool continuousPerception = false;
};

/**
 * @struct IntentRule
 * @brief 意图等级到多资源请求向量的映射规则。
 *
 * 每个字段都采用 0-1024 归一化值域，保持 CPU / 内存 / GPU / I/O /
 * 网络在统一坐标系内参与仲裁，符合框架的四维统一抽象。
 */
struct IntentRule {
    IntentLevel level = IntentLevel::P3;
    uint32_t cpuMin = 100;
    uint32_t cpuTarget = 300;
    uint32_t cpuMax = 600;
    uint32_t memMin = 64;
    uint32_t memTarget = 128;
    uint32_t memMax = 256;
    uint32_t memBwMin = 100;
    uint32_t memBwTarget = 300;
    uint32_t memBwMax = 600;
    uint32_t gpuMin = 0;
    uint32_t gpuTarget = 0;
    uint32_t gpuMax = 0;
    uint32_t storageBwMin = 0;
    uint32_t storageBwTarget = 100;
    uint32_t storageBwMax = 200;
    uint32_t storageIopsMin = 0;
    uint32_t storageIopsTarget = 100;
    uint32_t storageIopsMax = 200;
    uint32_t networkBwMin = 0;
    uint32_t networkBwTarget = 64;
    uint32_t networkBwMax = 128;
};

/**
 * @class ConfigLoader
 * @brief 加载并维护 DSE 的平台无关配置。
 *
 * 设计要点：
 * - 只向调度主链输出 canonical 规则，不暴露平台私有 schema
 * - 默认配置可独立工作，保证无外部配置时仍有确定性行为
 * - 解析过程使用固定数组，避免初始化阶段引入额外动态分配
 */
class ConfigLoader {
public:
    using LoadCallback = std::function<bool(const char *path)>;

    /** @brief 获取全局单例。 */
    static ConfigLoader &Instance();

    /**
     * @brief 加载配置。
     * @param configPath 配置路径；为空时回退到内建默认规则。
     * @return 加载是否成功。
     */
    bool Load(const char *configPath);

    /**
     * @brief 重新加载上次成功加载的配置。
     * @return 重新加载是否成功；若此前未成功加载过配置则返回 false。
     */
    bool Reload();

    const ConfigParams &GetParams() const { return params_; }

    /**
     * @brief 按场景类型查询规则。
     * @param kind 归一化场景类型。
     * @return 找到时返回规则指针，否则返回 nullptr。
     */
    const SceneRule *FindSceneRuleByKind(SceneKind kind) const;

    /**
     * @brief 按意图等级查询资源规则。
     * @param level P1~P4 意图等级。
     * @return 找到时返回规则指针，否则返回 nullptr。
     */
    const IntentRule *FindIntentRule(IntentLevel level) const;

    void SetLoadCallback(LoadCallback callback) { loadCallback_ = callback; }

    bool IsLoaded() const { return loaded_; }

private:
    /**
     * @struct ParsedSceneSeed
     * @brief XML 中间态种子。
     *
     * 该结构只在解析阶段存在，用于承接 XML 中可迁移到 canonical
     * 规则的公共字段；平台私有参数在解析阶段即被丢弃，不进入主链。
     */
    struct ParsedSceneSeed {
        uint32_t id = 0;
        char name[64] = {0};
        uint32_t fps = 0;
        char package[128] = {0};
        int64_t timeoutMs = 0;
        bool autoRelease = true;
    };

    ConfigLoader() = default;

    /** @brief 根据路径选择默认配置或 XML 配置加载流程。 */
    bool LoadFromFile(const char *path);

    /** @brief 加载 XML 配置并转换为 canonical 规则。 */
    bool LoadXmlConfig(const char *xmlPath);

    /** @brief 初始化内建默认规则，保证无配置时仍可运行。 */
    void SetDefaults();

    /**
     * @brief 解析 XML 文件到中间态种子数组。
     * @param path XML 文件路径。
     * @return 是否至少解析到一个合法场景。
     *
     * @note 这里故意采用轻量的顺序扫描而非通用 XML 库，减少依赖并保
     * 持初始化路径确定性；代价是仅支持当前受控 schema。
     */
    bool ParseXmlFile(const char *path);

    /** @brief 将 XML 中间态转换为场景/意图 canonical 规则。 */
    void ConvertXmlToCanonicalRules();

    /** @brief 根据场景名推导归一化 SceneKind。 */
    SceneKind MapNameToSceneKind(const char *name) const;

    /** @brief 根据场景类型、名称和租约长度推导 P1~P4 意图等级。 */
    IntentLevel MapSceneToIntent(SceneKind kind, const char *name, int64_t timeoutMs) const;

    /** @brief 根据场景类型、名称和租约长度推导时间模式。 */
    TimeMode MapSceneToTimeMode(SceneKind kind, const char *name, int64_t timeoutMs) const;

    /** @brief 计算场景租约的指数衰减系数。 */
    float ComputeDecayFactor(int64_t timeoutMs, const char *name) const;

    /** @brief 根据场景名估计是否可见。 */
    bool DetermineVisibility(const char *name) const;

    /** @brief 根据场景名估计是否可听。 */
    bool DetermineAudibility(const char *name) const;

    /** @brief 根据场景名估计是否属于连续感知主链路。 */
    bool DetermineContinuousPerception(const char *name) const;

    /** @brief 重建 SceneKind -> SceneRule 的 O(1) 索引。 */
    void RebuildSceneRuleIndex();

    ConfigParams params_;
    SceneRule sceneRules_[16];
    IntentRule intentRules_[4];
    ParsedSceneSeed sceneSeeds_[32];
    uint32_t sceneSeedCount_ = 0;
    std::array<const SceneRule *, static_cast<size_t>(SceneKind::Background) + 1> sceneRuleIndex_{};
    LoadCallback loadCallback_;
    bool loaded_ = false;
};

}    // namespace vendor::transsion::perfengine::dse
