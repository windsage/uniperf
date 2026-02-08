#define LOG_TAG "PerfHub-XmlParser"

#include "XmlConfigParser.h"

#include <libxml/parser.h>
#include <libxml/tree.h>
#include <sys/stat.h>

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <cstring>

#include "TranLog.h"

namespace vendor {
namespace transsion {
namespace perfhub {

// ====================== Public Methods ======================

XmlConfigParser &XmlConfigParser::getInstance() {
    static XmlConfigParser instance;
    return instance;
}

bool XmlConfigParser::loadConfig(const std::string &xmlPath) {
    std::lock_guard<std::mutex> lock(mMutex);

    // Find config file with priority
    std::string fullPath = findConfigFile(xmlPath);
    if (fullPath.empty()) {
        TLOGE("Config file not found: %s", xmlPath.c_str());
        return false;
    }

    TLOGI("Loading config from: %s", fullPath.c_str());

    // Parse XML document
    bool result = parseXmlDocument(fullPath);
    if (result) {
        mCurrentConfigPath = fullPath;
        TLOGI("Config loaded successfully, %zu scenarios cached", mConfigCache.size());
    } else {
        TLOGE("Failed to parse config file: %s", fullPath.c_str());
    }

    return result;
}

const ScenarioConfig *XmlConfigParser::getScenarioConfig(int32_t sceneId, int32_t currentFps) {
    std::lock_guard<std::mutex> lock(mMutex);

    // 1. Try FPS-specific configuration first
    if (currentFps > 0) {
        uint32_t key = makeCacheKey(sceneId, currentFps);
        auto it = mConfigCache.find(key);
        if (it != mConfigCache.end()) {
            TLOGD("Found FPS-specific config: sceneId=%d, fps=%d", sceneId, currentFps);
            return &(it->second);
        }
    }

    // 2. Fallback to generic configuration (fps=0)
    uint32_t key = makeCacheKey(sceneId, 0);
    auto it = mConfigCache.find(key);
    if (it != mConfigCache.end()) {
        TLOGD("Using generic config: sceneId=%d", sceneId);
        return &(it->second);
    }

    // 3. Configuration not found
    TLOGW("No config found for sceneId=%d, fps=%d", sceneId, currentFps);
    return nullptr;
}

bool XmlConfigParser::reloadConfig(const std::string &xmlPath) {
    clearCache();
    return loadConfig(xmlPath);
}

void XmlConfigParser::clearCache() {
    std::lock_guard<std::mutex> lock(mMutex);
    mConfigCache.clear();
    mCurrentConfigPath.clear();
    TLOGI("Config cache cleared");
}

size_t XmlConfigParser::getScenarioCount() const {
    std::lock_guard<std::mutex> lock(mMutex);
    return mConfigCache.size();
}

// ====================== Private Methods ======================

std::string XmlConfigParser::findConfigFile(const std::string &filename) {
    struct stat st;

    // 1. Check /data/vendor/perfhub/ first (OTA update path)
    std::string dataPath = std::string(ConfigPath::DATA_VENDOR) + "/" + filename;
    if (stat(dataPath.c_str(), &st) == 0) {
        TLOGD("Found config in data partition: %s", dataPath.c_str());
        return dataPath;
    }

    // 2. Fallback to /vendor/etc/perfhub/ (read-only)
    std::string vendorPath = std::string(ConfigPath::VENDOR_ETC) + "/" + filename;
    if (stat(vendorPath.c_str(), &st) == 0) {
        TLOGD("Found config in vendor partition: %s", vendorPath.c_str());
        return vendorPath;
    }

    // 3. Not found
    return "";
}

bool XmlConfigParser::parseXmlDocument(const std::string &xmlPath) {
    // Initialize libxml2 (thread-safe)
    xmlInitParser();

    // Parse XML file with whitespace handling
    xmlDocPtr doc = xmlReadFile(xmlPath.c_str(), nullptr, XML_PARSE_NOBLANKS | XML_PARSE_NONET);
    if (doc == nullptr) {
        TLOGE("Failed to parse XML file: %s", xmlPath.c_str());
        xmlCleanupParser();
        return false;
    }

    // Get root node
    xmlNodePtr root = xmlDocGetRootElement(doc);
    if (root == nullptr) {
        TLOGE("Empty XML document");
        xmlFreeDoc(doc);
        xmlCleanupParser();
        return false;
    }

    // Check root node name
    if (xmlStrcmp(root->name, (const xmlChar *)"TranPerfHub") != 0) {
        TLOGE("Invalid root node: %s (expected: TranPerfHub)", root->name);
        xmlFreeDoc(doc);
        xmlCleanupParser();
        return false;
    }

    // Parse platform info (for logging)
    std::string platform = getXmlAttribute(root, "platform");
    std::string chip = getXmlAttribute(root, "chip");
    if (!platform.empty() && !chip.empty()) {
        TLOGI("Config platform=%s, chip=%s", platform.c_str(), chip.c_str());
    }

    // Find <Scenarios> node
    xmlNodePtr scenarios = nullptr;
    for (xmlNodePtr child = root->children; child; child = child->next) {
        if (child->type == XML_ELEMENT_NODE &&
            xmlStrcmp(child->name, (const xmlChar *)"Scenarios") == 0) {
            scenarios = child;
            break;
        }
    }

    if (scenarios == nullptr) {
        TLOGE("No <Scenarios> node found");
        xmlFreeDoc(doc);
        xmlCleanupParser();
        return false;
    }

    // Parse all <Scenario> nodes
    int scenarioCount = 0;
    for (xmlNodePtr node = scenarios->children; node; node = node->next) {
        // Skip non-element nodes (text, comment, etc.)
        if (node->type != XML_ELEMENT_NODE) {
            continue;
        }

        if (xmlStrcmp(node->name, (const xmlChar *)"Scenario") == 0) {
            ScenarioConfig config = parseScenarioNode(node);

            // Validate scenario ID
            if (config.id <= 0) {
                TLOGW("Invalid scenario ID: %d, skipping", config.id);
                continue;
            }

            // Store in cache
            uint32_t key = makeCacheKey(config.id, config.fps);
            mConfigCache[key] = config;
            scenarioCount++;

            TLOGD("Parsed scenario: id=%d, name='%s', fps=%d, timeout=%d, params=%zu", config.id,
                  config.name.c_str(), config.fps, config.timeout, config.params.size());
        }
    }

    TLOGI("Successfully parsed %d scenarios", scenarioCount);

    // Cleanup
    xmlFreeDoc(doc);
    xmlCleanupParser();

    return scenarioCount > 0;
}

ScenarioConfig XmlConfigParser::parseScenarioNode(void *node) {
    xmlNodePtr scenarioNode = static_cast<xmlNodePtr>(node);
    ScenarioConfig config;

    // Parse 'id' attribute
    std::string idStr = getXmlAttribute(scenarioNode, "id");
    if (!idStr.empty()) {
        config.id = std::atoi(idStr.c_str());
    }

    // Parse 'name' attribute
    config.name = getXmlAttribute(scenarioNode, "name");

    // Parse 'fps' attribute (optional)
    std::string fpsStr = getXmlAttribute(scenarioNode, "fps");
    if (!fpsStr.empty()) {
        config.fps = std::atoi(fpsStr.c_str());
    }

    // Find <Config> node
    xmlNodePtr configNode = nullptr;
    for (xmlNodePtr child = scenarioNode->children; child; child = child->next) {
        if (child->type == XML_ELEMENT_NODE &&
            xmlStrcmp(child->name, (const xmlChar *)"Config") == 0) {
            configNode = child;
            break;
        }
    }

    if (configNode == nullptr) {
        TLOGW("No <Config> node for scenario id=%d", config.id);
        return config;
    }

    // Parse 'timeout' attribute
    std::string timeoutStr = getXmlAttribute(configNode, "timeout");
    if (!timeoutStr.empty()) {
        config.timeout = std::atoi(timeoutStr.c_str());
    }

    // Parse 'release' attribute
    config.release = getXmlAttribute(configNode, "release");
    if (config.release.empty()) {
        config.release = "auto";    // Default value
    }

    // Find <Resources> node
    xmlNodePtr resources = nullptr;
    for (xmlNodePtr child = configNode->children; child; child = child->next) {
        if (child->type == XML_ELEMENT_NODE &&
            xmlStrcmp(child->name, (const xmlChar *)"Resources") == 0) {
            resources = child;
            break;
        }
    }

    if (resources == nullptr) {
        TLOGW("No <Resources> node for scenario id=%d", config.id);
        return config;
    }

    // Parse all <Param> nodes
    for (xmlNodePtr param = resources->children; param; param = param->next) {
        // Skip non-element nodes
        if (param->type != XML_ELEMENT_NODE) {
            continue;
        }

        if (xmlStrcmp(param->name, (const xmlChar *)"Param") == 0) {
            PerfParam p = parseParamNode(param);
            if (!p.name.empty()) {
                config.params.push_back(p);
            }
        }
    }

    return config;
}

PerfParam XmlConfigParser::parseParamNode(void *node) {
    xmlNodePtr paramNode = static_cast<xmlNodePtr>(node);
    PerfParam param;

    // Parse 'name' attribute with whitespace trimming
    param.name = getXmlAttribute(paramNode, "name");

    // Parse 'value' attribute (support decimal and hexadecimal)
    std::string valueStr = getXmlAttribute(paramNode, "value");
    if (!valueStr.empty()) {
        param.value = parseInt(valueStr.c_str());
    }

    return param;
}

int32_t XmlConfigParser::parseInt(const char *valueStr) {
    if (valueStr == nullptr || *valueStr == '\0') {
        return 0;
    }

    // Trim whitespace
    std::string trimmed = trim(valueStr);

    // Check for hexadecimal (0x or 0X prefix)
    if (trimmed.size() >= 2 && (trimmed.substr(0, 2) == "0x" || trimmed.substr(0, 2) == "0X")) {
        // Parse as hexadecimal
        return static_cast<int32_t>(std::strtol(trimmed.c_str(), nullptr, 16));
    } else {
        // Parse as decimal
        return std::atoi(trimmed.c_str());
    }
}

std::string XmlConfigParser::getXmlAttribute(void *node, const char *attrName) {
    xmlNodePtr xmlNode = static_cast<xmlNodePtr>(node);

    xmlChar *attrValue = xmlGetProp(xmlNode, (const xmlChar *)attrName);
    if (attrValue == nullptr) {
        return "";
    }

    // Convert to std::string and trim whitespace
    std::string result = trim(reinterpret_cast<const char *>(attrValue));
    xmlFree(attrValue);

    return result;
}

std::string XmlConfigParser::trim(const std::string &str) {
    // Find first non-whitespace character
    auto start =
        std::find_if(str.begin(), str.end(), [](unsigned char ch) { return !std::isspace(ch); });

    // Find last non-whitespace character
    auto end = std::find_if(str.rbegin(), str.rend(), [](unsigned char ch) {
                   return !std::isspace(ch);
               }).base();

    // Return trimmed string
    return (start < end) ? std::string(start, end) : "";
}

}    // namespace perfhub
}    // namespace transsion
}    // namespace vendor
