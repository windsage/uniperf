#include "XmlConfigParser.h"

#include <libxml/parser.h>
#include <libxml/tree.h>
#include <sys/stat.h>

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <cstring>

#include "TranLog.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "PerfEngine-XmlParser"

namespace vendor {
namespace transsion {
namespace perfengine {

// ============================================================
// Public Methods
// ============================================================

XmlConfigParser &XmlConfigParser::getInstance() {
    static XmlConfigParser instance;
    return instance;
}

bool XmlConfigParser::loadConfig(const std::string &filename) {
    std::lock_guard<std::mutex> lock(mMutex);

    std::string fullPath = findConfigFile(filename);
    if (fullPath.empty()) {
        TLOGE("Config file not found: %s", filename.c_str());
        return false;
    }

    TLOGI("Loading config from: %s", fullPath.c_str());

    bool result = parseXmlDocument(fullPath);
    if (result) {
        mCurrentConfigPath = fullPath;
        // Count total entries across all scenario IDs
        size_t total = 0;
        for (const auto &kv : mConfigCache)
            total += kv.second.size();
        TLOGI("Config loaded: %zu scenario-id groups, %zu total entries", mConfigCache.size(),
              total);
    } else {
        TLOGE("Failed to parse config: %s", fullPath.c_str());
    }

    return result;
}

const ScenarioConfig *XmlConfigParser::getScenarioConfig(int32_t sceneId, int32_t currentFps,
                                                         const std::string &package,
                                                         const std::string &activity) {
    std::lock_guard<std::mutex> lock(mMutex);
    return findBestMatch(sceneId, currentFps, package, activity);
}

bool XmlConfigParser::reloadConfig(const std::string &filename) {
    clearCache();
    return loadConfig(filename);
}

void XmlConfigParser::clearCache() {
    std::lock_guard<std::mutex> lock(mMutex);
    mConfigCache.clear();
    mCurrentConfigPath.clear();
    TLOGI("Config cache cleared");
}

size_t XmlConfigParser::getScenarioCount() const {
    std::lock_guard<std::mutex> lock(mMutex);
    size_t total = 0;
    for (const auto &kv : mConfigCache)
        total += kv.second.size();
    return total;
}

// ============================================================
// Core matching logic
// ============================================================

/**
 * Priority cascade (called with mMutex held):
 *   P1: fps==currentFps && package==pkg && activity==act
 *   P2: fps==currentFps && package==pkg && activity==""
 *   P3: fps==currentFps && package==""  && activity==""
 *   P4: fps==0          && package==pkg && activity==act
 *   P5: fps==0          && package==pkg && activity==""
 *   P6: fps==0          && package==""  && activity==""
 */
const ScenarioConfig *XmlConfigParser::findBestMatch(int32_t sceneId, int32_t fps,
                                                     const std::string &package,
                                                     const std::string &activity) const {
    auto it = mConfigCache.find(sceneId);
    if (it == mConfigCache.end()) {
        TLOGW("No scenarios found for sceneId=%d", sceneId);
        return nullptr;
    }

    const std::vector<ScenarioConfig> &entries = it->second;

    TLOGD("findBestMatch: sceneId=%d, fps=%d, pkg='%s', act='%s', candidates=%zu", sceneId, fps,
          package.c_str(), activity.c_str(), entries.size());

    // Helper lambda: find first entry matching the given criteria
    auto find = [&](int32_t wantFps, const std::string &wantPkg,
                    const std::string &wantAct) -> const ScenarioConfig * {
        for (const auto &cfg : entries) {
            if (cfg.fps == wantFps && cfg.package == wantPkg && cfg.activity == wantAct) {
                return &cfg;
            }
        }
        return nullptr;
    };

    const ScenarioConfig *result = nullptr;

    // ---- P1: exact fps + package + activity ----
    if (fps > 0 && !package.empty() && !activity.empty()) {
        result = find(fps, package, activity);
        if (result) {
            TLOGD("Matched P1: fps=%d pkg='%s' act='%s'", fps, package.c_str(), activity.c_str());
            return result;
        }
    }

    // ---- P2: exact fps + package (no activity) ----
    if (fps > 0 && !package.empty()) {
        result = find(fps, package, "");
        if (result) {
            TLOGD("Matched P2: fps=%d pkg='%s'", fps, package.c_str());
            return result;
        }
    }

    // ---- P3: exact fps, generic (no package) ----
    if (fps > 0) {
        result = find(fps, "", "");
        if (result) {
            TLOGD("Matched P3: fps=%d (generic)", fps);
            return result;
        }
    }

    // ---- P4: fps=0 fallback + package + activity ----
    if (!package.empty() && !activity.empty()) {
        result = find(0, package, activity);
        if (result) {
            TLOGD("Matched P4: fps=0 pkg='%s' act='%s'", package.c_str(), activity.c_str());
            return result;
        }
    }

    // ---- P5: fps=0 fallback + package (no activity) ----
    if (!package.empty()) {
        result = find(0, package, "");
        if (result) {
            TLOGD("Matched P5: fps=0 pkg='%s'", package.c_str());
            return result;
        }
    }

    // ---- P6: fps=0 generic fallback ----
    result = find(0, "", "");
    if (result) {
        TLOGD("Matched P6: generic fallback for sceneId=%d", sceneId);
        return result;
    }

    TLOGW("No matching config: sceneId=%d fps=%d pkg='%s' act='%s'", sceneId, fps, package.c_str(),
          activity.c_str());
    return nullptr;
}

// ============================================================
// Private: File discovery
// ============================================================

std::string XmlConfigParser::findConfigFile(const std::string &filename) {
    struct stat st;

    // Priority 1: /data/vendor/perfengine/ (OTA / debug override)
    std::string dataPath = std::string(ConfigPath::DATA_VENDOR) + "/" + filename;
    if (stat(dataPath.c_str(), &st) == 0) {
        TLOGD("Found config in data partition: %s", dataPath.c_str());
        return dataPath;
    }

    // Priority 2: /vendor/etc/perfengine/ (built-in)
    std::string vendorPath = std::string(ConfigPath::VENDOR_ETC) + "/" + filename;
    if (stat(vendorPath.c_str(), &st) == 0) {
        TLOGD("Found config in vendor partition: %s", vendorPath.c_str());
        return vendorPath;
    }

    return "";
}

// ============================================================
// Private: XML parsing
// ============================================================

bool XmlConfigParser::parseXmlDocument(const std::string &xmlPath) {
    xmlInitParser();

    xmlDocPtr doc = xmlReadFile(xmlPath.c_str(), nullptr, XML_PARSE_NOBLANKS | XML_PARSE_NONET);
    if (doc == nullptr) {
        TLOGE("xmlReadFile failed: %s", xmlPath.c_str());
        xmlCleanupParser();
        return false;
    }

    xmlNodePtr root = xmlDocGetRootElement(doc);
    if (root == nullptr) {
        TLOGE("Empty XML document: %s", xmlPath.c_str());
        xmlFreeDoc(doc);
        xmlCleanupParser();
        return false;
    }

    if (xmlStrcmp(root->name, (const xmlChar *)"PerfEngine") != 0) {
        TLOGE("Invalid root node '%s', expected 'PerfEngine'", root->name);
        xmlFreeDoc(doc);
        xmlCleanupParser();
        return false;
    }

    // Log header attributes
    std::string version = getXmlAttribute(root, "version");
    std::string platform = getXmlAttribute(root, "platform");
    std::string chip = getXmlAttribute(root, "chip");
    TLOGI("XML header: version=%s platform=%s chip=%s", version.c_str(), platform.c_str(),
          chip.c_str());

    // Find <Scenarios> node
    xmlNodePtr scenariosNode = nullptr;
    for (xmlNodePtr child = root->children; child; child = child->next) {
        if (child->type == XML_ELEMENT_NODE &&
            xmlStrcmp(child->name, (const xmlChar *)"Scenarios") == 0) {
            scenariosNode = child;
            break;
        }
    }

    if (scenariosNode == nullptr) {
        TLOGE("No <Scenarios> node found in: %s", xmlPath.c_str());
        xmlFreeDoc(doc);
        xmlCleanupParser();
        return false;
    }

    // Parse each <Scenario>
    int parsedCount = 0;
    for (xmlNodePtr node = scenariosNode->children; node; node = node->next) {
        if (node->type != XML_ELEMENT_NODE)
            continue;

        if (xmlStrcmp(node->name, (const xmlChar *)"Scenario") != 0) {
            TLOGD("Skipping unknown node: <%s>", node->name);
            continue;
        }

        ScenarioConfig cfg = parseScenarioNode(node);

        if (cfg.id <= 0) {
            TLOGW("Scenario with invalid id=%d, skipping", cfg.id);
            continue;
        }

        if (cfg.params.empty()) {
            TLOGW("Scenario id=%d name='%s' has no params, skipping", cfg.id, cfg.name.c_str());
            continue;
        }

        TLOGI(
            "Parsed Scenario: id=%d name='%s' fps=%d pkg='%s' act='%s' "
            "timeout=%d release=%s params=%zu",
            cfg.id, cfg.name.c_str(), cfg.fps, cfg.package.c_str(), cfg.activity.c_str(),
            cfg.timeout, cfg.release.c_str(), cfg.params.size());

        mConfigCache[cfg.id].push_back(std::move(cfg));
        parsedCount++;
    }

    xmlFreeDoc(doc);
    xmlCleanupParser();

    TLOGI("Parsed %d scenario entries from %s", parsedCount, xmlPath.c_str());
    return parsedCount > 0;
}

ScenarioConfig XmlConfigParser::parseScenarioNode(void *node) {
    xmlNodePtr scenarioNode = static_cast<xmlNodePtr>(node);
    ScenarioConfig config;

    // --- Scenario attributes ---
    std::string idStr = getXmlAttribute(scenarioNode, "id");
    if (!idStr.empty()) {
        config.id = std::atoi(idStr.c_str());
    }

    config.name = getXmlAttribute(scenarioNode, "name");

    std::string fpsStr = getXmlAttribute(scenarioNode, "fps");
    config.fps = fpsStr.empty() ? 0 : std::atoi(fpsStr.c_str());

    // package / activity (optional)
    config.package = getXmlAttribute(scenarioNode, "package");
    config.activity = getXmlAttribute(scenarioNode, "activity");

    // description is doc-only, no need to store
    TLOGD("parseScenarioNode: id=%d name='%s' fps=%d pkg='%s' act='%s'", config.id,
          config.name.c_str(), config.fps, config.package.c_str(), config.activity.c_str());

    // --- Find <Config> child ---
    xmlNodePtr configNode = nullptr;
    for (xmlNodePtr child = scenarioNode->children; child; child = child->next) {
        if (child->type == XML_ELEMENT_NODE &&
            xmlStrcmp(child->name, (const xmlChar *)"Config") == 0) {
            configNode = child;
            break;
        }
    }

    if (configNode == nullptr) {
        TLOGW("No <Config> for Scenario id=%d, entry will be skipped", config.id);
        return config;
    }

    // Config attributes
    std::string timeoutStr = getXmlAttribute(configNode, "timeout");
    config.timeout = timeoutStr.empty() ? 0 : std::atoi(timeoutStr.c_str());

    config.release = getXmlAttribute(configNode, "release");
    if (config.release.empty())
        config.release = "auto";

    TLOGD("  Config: timeout=%d release=%s", config.timeout, config.release.c_str());

    // --- Find <Resources> child ---
    xmlNodePtr resourcesNode = nullptr;
    for (xmlNodePtr child = configNode->children; child; child = child->next) {
        if (child->type == XML_ELEMENT_NODE &&
            xmlStrcmp(child->name, (const xmlChar *)"Resources") == 0) {
            resourcesNode = child;
            break;
        }
    }

    if (resourcesNode == nullptr) {
        TLOGW("No <Resources> for Scenario id=%d", config.id);
        return config;
    }

    // --- Parse <Param> nodes ---
    for (xmlNodePtr paramNode = resourcesNode->children; paramNode; paramNode = paramNode->next) {
        if (paramNode->type != XML_ELEMENT_NODE)
            continue;

        if (xmlStrcmp(paramNode->name, (const xmlChar *)"Param") != 0) {
            TLOGD("  Skipping unknown node <%s> in <Resources>", paramNode->name);
            continue;
        }

        PerfParam p = parseParamNode(paramNode);
        if (p.name.empty()) {
            TLOGW("  Param with empty name, skipping");
            continue;
        }

        TLOGD("  Param: name='%s' value=%d (0x%X)", p.name.c_str(), p.value, p.value);
        config.params.push_back(std::move(p));
    }

    return config;
}

PerfParam XmlConfigParser::parseParamNode(void *node) {
    xmlNodePtr paramNode = static_cast<xmlNodePtr>(node);
    PerfParam param;

    param.name = getXmlAttribute(paramNode, "name");

    std::string valueStr = getXmlAttribute(paramNode, "value");
    if (!valueStr.empty()) {
        param.value = parseInt(valueStr.c_str());
    }

    return param;
}

// ============================================================
// Private: Utilities
// ============================================================

int32_t XmlConfigParser::parseInt(const char *valueStr) {
    if (valueStr == nullptr || *valueStr == '\0')
        return 0;

    std::string s = trim(valueStr);

    // Hex: 0x / 0X prefix
    if (s.size() >= 2 && (s[0] == '0') && (s[1] == 'x' || s[1] == 'X')) {
        return static_cast<int32_t>(std::strtol(s.c_str(), nullptr, 16));
    }
    return std::atoi(s.c_str());
}

std::string XmlConfigParser::getXmlAttribute(void *node, const char *attrName) {
    xmlNodePtr xmlNode = static_cast<xmlNodePtr>(node);
    xmlChar *val = xmlGetProp(xmlNode, (const xmlChar *)attrName);
    if (val == nullptr)
        return "";

    std::string result = trim(reinterpret_cast<const char *>(val));
    xmlFree(val);
    return result;
}

std::string XmlConfigParser::trim(const std::string &str) {
    auto start =
        std::find_if(str.begin(), str.end(), [](unsigned char c) { return !std::isspace(c); });
    auto end = std::find_if(str.rbegin(), str.rend(), [](unsigned char c) {
                   return !std::isspace(c);
               }).base();
    return (start < end) ? std::string(start, end) : "";
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
