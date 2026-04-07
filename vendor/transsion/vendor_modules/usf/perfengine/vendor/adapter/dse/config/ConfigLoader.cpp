#include "ConfigLoader.h"

#include <cstdio>
#include <cstring>

namespace vendor::transsion::perfengine::dse {
namespace {

// Reload() 传入 params_.configPath 时与 strncpy 目标重叠；使用 memmove 保证自拷贝合法。
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

}    // namespace

// 单例配置入口。配置生命周期与进程一致，避免在调度热路径中重复构造。
ConfigLoader &ConfigLoader::Instance() {
    static ConfigLoader instance;
    return instance;
}

// 加载顺序为：外部回调优先，其次本地文件。这样既支持注入式测试，
// 也支持商用落地时由外部配置中心接管。
bool ConfigLoader::Load(const char *configPath) {
    bool loadSuccess = false;

    if (loadCallback_) {
        if (loadCallback_(configPath)) {
            loadSuccess = true;
        }
    }

    if (!loadSuccess) {
        loadSuccess = LoadFromFile(configPath);
    }

    if (!loadSuccess) {
        loaded_ = false;
        return false;
    }

    loaded_ = true;
    CopyStoredPath(params_.configPath, sizeof(params_.configPath), configPath);

    return true;
}

// 重新加载保持与上次成功加载时相同的路径语义；空路径意味着继续使用默认规则。
bool ConfigLoader::Reload() {
    if (!loaded_) {
        return false;
    }

    char stablePath[sizeof(params_.configPath)] = {0};
    const char *reloadPath = nullptr;
    if (params_.configPath[0] != '\0') {
        CopyStoredPath(stablePath, sizeof(stablePath), params_.configPath);
        reloadPath = stablePath;
    }

    return Load(reloadPath);
}

const SceneRule *ConfigLoader::FindSceneRuleByKind(SceneKind kind) const {
    const size_t index = static_cast<size_t>(kind);
    return index < sceneRuleIndex_.size() ? sceneRuleIndex_[index] : nullptr;
}

// IntentLevel 从 P1 开始连续编号，因此这里使用 O(1) 下标映射而非线性查找。
const IntentRule *ConfigLoader::FindIntentRule(IntentLevel level) const {
    size_t index = static_cast<size_t>(level) - 1;
    if (index < 4) {
        return &intentRules_[index];
    }
    return nullptr;
}

// 加载入口只关心“是否给定路径”，不在此处区分配置来源的具体 schema。
bool ConfigLoader::LoadFromFile(const char *path) {
    if (!path || path[0] == '\0') {
        SetDefaults();
        return true;
    }

    SetDefaults();
    return LoadXmlConfig(path);
}

// XML 配置的职责仅是生成 canonical 规则；缺失版本字段时回退到默认版本号，
// 保证规则版本总是可比较的。
bool ConfigLoader::LoadXmlConfig(const char *xmlPath) {
    if (!xmlPath)
        return false;

    if (ParseXmlFile(xmlPath)) {
        ConvertXmlToCanonicalRules();
        if (params_.configVersion == 0) {
            params_.configVersion = 1;
        }
        if (params_.ruleVersion == 0) {
            params_.ruleVersion = 1;
        }
        return true;
    }

    return false;
}

// 默认规则直接体现框架中的基线语义：P1 突发、P2 稳态、P4 受控后台。
// 这些默认值是“无配置也可运行”的兜底，而不是平台能力矩阵。
void ConfigLoader::SetDefaults() {
    params_ = ConfigParams{};
    sceneSeedCount_ = 0;
    sceneRuleIndex_.fill(nullptr);
    for (auto &rule : sceneRules_) {
        rule = SceneRule{};
    }
    for (auto &rule : intentRules_) {
        rule = IntentRule{};
    }
    for (auto &seed : sceneSeeds_) {
        seed = ParsedSceneSeed{};
    }

    params_.observationWindowMs = 100;
    params_.minResidencyMs = 50;
    params_.hysteresisEnterMs = 0;
    params_.hysteresisExitMs = 0;
    params_.updateThrottleMs = 10;
    params_.steadyEnterMerges = 2;
    params_.exitHysteresisMerges = 1;
    params_.traceLevel = 2;
    params_.resourceMask = 0xFF;
    params_.configVersion = 1;
    params_.ruleVersion = 1;
    params_.contentHash = 0;

    sceneRules_[0] = {
        0x01, SceneKind::Launch, IntentLevel::P1, TimeMode::Burst, 1500, 0.8f, true, false, false};
    sceneRules_[1] = {
        0x02, SceneKind::Transition, IntentLevel::P1, TimeMode::Burst, 1200, 0.85f, true, false,
        false};
    sceneRules_[2] = {
        0x04, SceneKind::Scroll, IntentLevel::P1, TimeMode::Burst, 800, 0.85f, true, false, false};
    sceneRules_[3] = {
        0x08, SceneKind::Animation, IntentLevel::P1, TimeMode::Burst, 800, 0.85f, true, false,
        false};
    sceneRules_[4] = {
        0x10, SceneKind::Gaming, IntentLevel::P2, TimeMode::Steady, 30000, 0.95f, true, false,
        true};
    sceneRules_[5] = {
        0x20, SceneKind::Camera, IntentLevel::P1, TimeMode::Burst, 1500, 0.95f, true, false, false};
    sceneRules_[6] = {
        0x40, SceneKind::Playback, IntentLevel::P2, TimeMode::Steady, 60000, 0.9f, true, true,
        true};
    sceneRules_[7] = {
        0x80, SceneKind::Download, IntentLevel::P4, TimeMode::Deferred, 60000, 0.5f, false, false,
        false};

    intentRules_[0] = {IntentLevel::P1,
                       768,
                       1024,
                       1024,
                       256,
                       384,
                       512,
                       700,
                       900,
                       1024,
                       512,
                       768,
                       1024,
                       400,
                       700,
                       900,
                       400,
                       700,
                       900,
                       128,
                       256,
                       384};
    intentRules_[1] = {IntentLevel::P2,
                       512,
                       768,
                       1024,
                       192,
                       320,
                       512,
                       450,
                       700,
                       900,
                       256,
                       576,
                       900,
                       200,
                       400,
                       700,
                       200,
                       400,
                       700,
                       256,
                       512,
                       768};
    intentRules_[2] = {IntentLevel::P3,
                       128,
                       320,
                       512,
                       128,
                       192,
                       256,
                       200,
                       350,
                       500,
                       64,
                       200,
                       400,
                       100,
                       200,
                       300,
                       100,
                       200,
                       300,
                       64,
                       128,
                       256};
    intentRules_[3] = {IntentLevel::P4,
                       32,
                       96,
                       192,
                       64,
                       96,
                       128,
                       64,
                       128,
                       192,
                       0,
                       64,
                       128,
                       0,
                       128,
                       512,
                       0,
                       128,
                       512,
                       0,
                       256,
                       768};
    RebuildSceneRuleIndex();
}

// 解析器刻意保持为简单的单遍扫描：
// 1. 初始化路径短且确定
// 2. 不引入第三方 XML 依赖
// 3. 只提取会进入 canonical 规则的公共字段
//
// 平台/SoC 私有频点与 hint 参数即使出现在 XML 中，也不会被提升到通用配置模型。
bool ConfigLoader::ParseXmlFile(const char *path) {
    FILE *fp = fopen(path, "r");
    if (!fp)
        return false;

    sceneSeedCount_ = 0;
    ParsedSceneSeed *currentScenario = nullptr;
    uint32_t hashAccumulator = 0;

    static constexpr size_t kBufferSize = 4096;
    char buffer[kBufferSize];
    size_t bufferLen = 0;

    enum class ParseState { Root, InPerfEngine, InScenario, InParam };
    ParseState state = ParseState::Root;

    while (true) {
        if (bufferLen < kBufferSize - 256) {
            char *readResult =
                fgets(buffer + bufferLen, static_cast<int>(kBufferSize - bufferLen), fp);
            if (readResult) {
                bufferLen = strlen(buffer);
            } else if (bufferLen == 0) {
                break;
            }
        }

        char *line = buffer;
        char *newline = strchr(line, '\n');
        if (newline) {
            *newline = '\0';
        }

        while (*line == ' ' || *line == '\t' || *line == '\r')
            line++;
        if (*line == '\0') {
            if (newline) {
                size_t consumed = static_cast<size_t>(newline - buffer) + 1;
                memmove(buffer, buffer + consumed, bufferLen - consumed + 1);
                bufferLen = strlen(buffer);
            }
            continue;
        }

        if (strstr(line, "<PerfEngine")) {
            state = ParseState::InPerfEngine;

            char *versionAttr = strstr(line, "version=\"");
            if (versionAttr) {
                char *end = strchr(versionAttr + 9, '"');
                if (end) {
                    *end = '\0';
                    params_.configVersion = static_cast<uint32_t>(atoi(versionAttr + 9));
                }
            }

        } else if (strstr(line, "<Scenario")) {
            if (sceneSeedCount_ >= 32) {
                // 固定容量是有意为之：配置规模受控，避免初始化阶段动态扩容。
                break;
            }
            state = ParseState::InScenario;
            currentScenario = &sceneSeeds_[sceneSeedCount_++];
            *currentScenario = ParsedSceneSeed{};

            char *idAttr = strstr(line, "id=\"");
            if (idAttr) {
                char *end = strchr(idAttr + 4, '"');
                if (end) {
                    *end = '\0';
                    currentScenario->id = static_cast<uint32_t>(atoi(idAttr + 4));
                    hashAccumulator ^= (currentScenario->id * 31);
                }
            }

            char *nameAttr = strstr(line, "name=\"");
            if (nameAttr) {
                char *start = nameAttr + 6;
                char *end = strchr(start, '"');
                if (end) {
                    size_t len = static_cast<size_t>(end - start);
                    if (len >= sizeof(currentScenario->name))
                        len = sizeof(currentScenario->name) - 1;
                    strncpy(currentScenario->name, start, len);
                    currentScenario->name[len] = '\0';
                    for (size_t i = 0; i < len; ++i) {
                        hashAccumulator ^= static_cast<uint32_t>(start[i]) << ((i % 4) * 8);
                    }
                }
            }

            char *fpsAttr = strstr(line, "fps=\"");
            if (fpsAttr) {
                char *end = strchr(fpsAttr + 5, '"');
                if (end) {
                    *end = '\0';
                    currentScenario->fps = static_cast<uint32_t>(atoi(fpsAttr + 5));
                    hashAccumulator ^= (currentScenario->fps * 37);
                }
            }

            char *pkgAttr = strstr(line, "package=\"");
            if (pkgAttr) {
                char *start = pkgAttr + 9;
                char *end = strchr(start, '"');
                if (end) {
                    size_t len = static_cast<size_t>(end - start);
                    if (len >= sizeof(currentScenario->package))
                        len = sizeof(currentScenario->package) - 1;
                    strncpy(currentScenario->package, start, len);
                    currentScenario->package[len] = '\0';
                }
            }
        } else if (strstr(line, "</Scenario")) {
            state = ParseState::InPerfEngine;
            currentScenario = nullptr;
        } else if (strstr(line, "</PerfEngine")) {
            state = ParseState::Root;
        } else if (currentScenario && state == ParseState::InScenario) {
            // 这里只抽取会参与 canonical 场景推导的公共属性。
            char *timeoutAttr = strstr(line, "timeout=\"");
            if (timeoutAttr) {
                char *end = strchr(timeoutAttr + 9, '"');
                if (end) {
                    *end = '\0';
                    currentScenario->timeoutMs = atoi(timeoutAttr + 9);
                    hashAccumulator ^= static_cast<uint32_t>(currentScenario->timeoutMs);
                }
            }

            char *releaseAttr = strstr(line, "release=\"");
            if (releaseAttr) {
                currentScenario->autoRelease = (strncmp(releaseAttr + 9, "auto", 4) == 0);
            }
        }

        if (newline) {
            size_t consumed = static_cast<size_t>(newline - buffer) + 1;
            memmove(buffer, buffer + consumed, bufferLen - consumed + 1);
            bufferLen = strlen(buffer);
        } else {
            bufferLen = 0;
        }
    }

    fclose(fp);

    params_.contentHash = hashAccumulator;
    params_.ruleVersion = 1 + (hashAccumulator % 0xFFFFFFFE);

    return sceneSeedCount_ > 0;
}

// 该步骤是配置层最关键的归一化过程：
// XML 中的场景名和超时只是输入线索，真正供调度链消费的是 SceneRule。
void ConfigLoader::ConvertXmlToCanonicalRules() {
    for (uint32_t i = 0; i < sceneSeedCount_ && i < 16; ++i) {
        const auto &xml = sceneSeeds_[i];
        auto &rule = sceneRules_[i];

        rule.actionMask = (1 << (xml.id % 32));
        rule.kind = MapNameToSceneKind(xml.name);
        rule.defaultIntent = MapSceneToIntent(rule.kind, xml.name, xml.timeoutMs);
        rule.defaultTimeMode = MapSceneToTimeMode(rule.kind, xml.name, xml.timeoutMs);
        rule.defaultLeaseMs = xml.timeoutMs;
        rule.decayFactor = ComputeDecayFactor(xml.timeoutMs, xml.name);
        rule.visible = DetermineVisibility(xml.name);
        rule.audible = DetermineAudibility(xml.name);
        rule.continuousPerception = DetermineContinuousPerception(xml.name);
    }
    RebuildSceneRuleIndex();
}

void ConfigLoader::RebuildSceneRuleIndex() {
    sceneRuleIndex_.fill(nullptr);
    for (const auto &rule : sceneRules_) {
        const size_t index = static_cast<size_t>(rule.kind);
        if (rule.actionMask == 0 || index >= sceneRuleIndex_.size()) {
            continue;
        }
        if (sceneRuleIndex_[index] == nullptr) {
            sceneRuleIndex_[index] = &rule;
        }
    }
}

// 名称映射优先使用显式关键字，避免把平台事件名直接泄漏到核心调度逻辑中。
SceneKind ConfigLoader::MapNameToSceneKind(const char *name) const {
    if (!name || name[0] == '\0')
        return SceneKind::Unknown;

    if (strstr(name, "LAUNCH"))
        return SceneKind::Launch;
    if (strstr(name, "GAME"))
        return SceneKind::Gaming;
    if (strstr(name, "CAMERA"))
        return SceneKind::Camera;
    if (strstr(name, "SCROLL"))
        return SceneKind::Scroll;
    if (strstr(name, "ANIMATION"))
        return SceneKind::Animation;
    if (strstr(name, "PLAYBACK") || strstr(name, "VIDEO"))
        return SceneKind::Playback;
    if (strstr(name, "DOWNLOAD"))
        return SceneKind::Download;
    if (strstr(name, "FOREGROUND"))
        return SceneKind::Transition;

    return SceneKind::Unknown;
}

// 意图推导遵循文档中的核心语义：
// - P1：交互瞬时保障
// - P2：连续感知稳态
// - P4：后台受控
// 当场景类型未知时，再使用名称与超时信息做保守推断。
IntentLevel ConfigLoader::MapSceneToIntent(SceneKind kind, const char *name,
                                           int64_t timeoutMs) const {
    switch (kind) {
        case SceneKind::Launch:
        case SceneKind::Transition:
        case SceneKind::Scroll:
        case SceneKind::Animation:
            return IntentLevel::P1;
        case SceneKind::Gaming:
        case SceneKind::Playback:
            return IntentLevel::P2;
        case SceneKind::Camera:
            return IntentLevel::P1;
        case SceneKind::Download:
        case SceneKind::Background:
            return IntentLevel::P4;
        case SceneKind::Unknown:
        default:
            break;
    }

    if (name && (strstr(name, "AUDIO") || strstr(name, "MUSIC") || strstr(name, "NAVI") ||
                 strstr(name, "CALL") || strstr(name, "VOICE") || strstr(name, "VIDEO"))) {
        return IntentLevel::P2;
    }
    if (timeoutMs <= 0)
        return IntentLevel::P4;
    if (timeoutMs <= 2000)
        return IntentLevel::P1;
    return IntentLevel::P3;
}

// 时间模式与意图等级并非一一绑定：两者共同构成调度契约。
// 这里优先按已知场景类型判定，再在未知场景上退化到持续时长启发式。
TimeMode ConfigLoader::MapSceneToTimeMode(SceneKind kind, const char *name,
                                          int64_t timeoutMs) const {
    switch (kind) {
        case SceneKind::Launch:
        case SceneKind::Transition:
        case SceneKind::Scroll:
        case SceneKind::Animation:
        case SceneKind::Camera:
            return TimeMode::Burst;
        case SceneKind::Gaming:
        case SceneKind::Playback:
            return TimeMode::Steady;
        case SceneKind::Download:
        case SceneKind::Background:
            return TimeMode::Deferred;
        case SceneKind::Unknown:
        default:
            break;
    }

    if (name && (strstr(name, "AUDIO") || strstr(name, "MUSIC") || strstr(name, "NAVI") ||
                 strstr(name, "CALL") || strstr(name, "VOICE") || strstr(name, "VIDEO"))) {
        return TimeMode::Steady;
    }
    if (timeoutMs <= 0)
        return TimeMode::Deferred;
    if (timeoutMs <= 2000)
        return TimeMode::Burst;
    if (timeoutMs <= 10000)
        return TimeMode::Intermittent;
    return TimeMode::Steady;
}

// 衰减系数越高，代表场景进入稳态后的资源回收越慢；
// 游戏/相机这类强主链路场景在文档语义下需要更保守的衰减。
float ConfigLoader::ComputeDecayFactor(int64_t timeoutMs, const char *name) const {
    if (name && strstr(name, "GAME"))
        return 0.95f;
    if (name && strstr(name, "CAMERA"))
        return 0.95f;
    if (timeoutMs <= 2000)
        return 0.8f;
    if (timeoutMs <= 10000)
        return 0.9f;
    return 0.95f;
}

// 可见/可听/连续感知这三类属性是区分 P2 和 P3 的关键输入。
bool ConfigLoader::DetermineVisibility(const char *name) const {
    if (!name)
        return false;
    return (strstr(name, "LAUNCH") != nullptr || strstr(name, "FOREGROUND") != nullptr ||
            strstr(name, "GAME") != nullptr || strstr(name, "SCROLL") != nullptr ||
            strstr(name, "ANIMATION") != nullptr || strstr(name, "CAMERA") != nullptr ||
            strstr(name, "PLAYBACK") != nullptr || strstr(name, "VIDEO") != nullptr ||
            strstr(name, "PIP") != nullptr || strstr(name, "PICTURE") != nullptr);
}

bool ConfigLoader::DetermineAudibility(const char *name) const {
    if (!name)
        return false;
    return (strstr(name, "PLAYBACK") != nullptr || strstr(name, "VIDEO") != nullptr ||
            strstr(name, "AUDIO") != nullptr || strstr(name, "MUSIC") != nullptr ||
            strstr(name, "CALL") != nullptr || strstr(name, "VOICE") != nullptr ||
            strstr(name, "NAVI") != nullptr);
}

// 连续感知强调“持续被用户感知”，因此滚动、动画、启动这种短时突发场景
// 即使可见，也不应该被误归到稳态连续感知链路。
bool ConfigLoader::DetermineContinuousPerception(const char *name) const {
    if (!name)
        return false;
    if (strstr(name, "SCROLL") || strstr(name, "ANIMATION") || strstr(name, "LAUNCH")) {
        return false;
    }
    return (strstr(name, "GAME") != nullptr || strstr(name, "PLAYBACK") != nullptr ||
            strstr(name, "VIDEO") != nullptr || strstr(name, "AUDIO") != nullptr ||
            strstr(name, "MUSIC") != nullptr || strstr(name, "CALL") != nullptr ||
            strstr(name, "VOICE") != nullptr || strstr(name, "NAVI") != nullptr ||
            strstr(name, "PREVIEW") != nullptr || strstr(name, "RECORD") != nullptr);
}

}    // namespace vendor::transsion::perfengine::dse
