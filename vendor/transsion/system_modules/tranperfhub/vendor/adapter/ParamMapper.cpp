#define LOG_TAG "PerfHub-ParamMapper"

#include "ParamMapper.h"

#include "TranLog.h"

#if !PERFHUB_USE_HARDCODED_MAPPINGS
#include <libxml/parser.h>
#include <libxml/tree.h>
#endif

#include <cutils/properties.h>
#include <sys/stat.h>

#include <cstring>

namespace vendor {
namespace transsion {
namespace perfhub {

// ====================== Public Methods ======================

ParamMapper &ParamMapper::getInstance() {
    static ParamMapper instance;
    return instance;
}

bool ParamMapper::init(Platform platform) {
    std::lock_guard<std::mutex> lock(mMutex);

    if (mInitialized) {
        TLOGW("ParamMapper already initialized");
        return true;
    }

    // Detect platform if not specified
    if (platform == Platform::UNKNOWN) {
        mPlatform = PlatformDetector::detect();
    } else {
        mPlatform = platform;
    }

    if (mPlatform == Platform::UNKNOWN) {
        TLOGE("Failed to detect platform");
        return false;
    }

    TLOGI("Initializing ParamMapper for platform: %s",
          PlatformDetector::getPlatformName(mPlatform));

#if PERFHUB_USE_HARDCODED_MAPPINGS
    // Production mode: Load hardcoded mappings
    loadHardcodedMappings();
    TLOGI("Loaded hardcoded mappings: %zu parameters", mMappings.size());
#else
    // Development mode: Load from XML
    std::string xmlFilename;
    if (mPlatform == Platform::QCOM) {
        xmlFilename = "platform_mappings_qcom.xml";
    } else if (mPlatform == Platform::MTK) {
        xmlFilename = "platform_mappings_mtk.xml";
    } else {
        TLOGE("Unsupported platform: %d", static_cast<int>(mPlatform));
        return false;
    }

    if (!loadXmlMappings(xmlFilename)) {
        TLOGE("Failed to load XML mappings");
        return false;
    }

    TLOGI("Loaded XML mappings: %zu parameters", mMappings.size());
#endif

    mInitialized = true;
    return true;
}

int32_t ParamMapper::getOpcode(const std::string &paramName) {
    std::lock_guard<std::mutex> lock(mMutex);

    if (!mInitialized) {
        TLOGE("ParamMapper not initialized");
        return -1;
    }

    auto it = mMappings.find(paramName);
    if (it != mMappings.end()) {
        TLOGD("Mapped '%s' -> 0x%08X", paramName.c_str(), it->second);
        return it->second;
    }

    // Parameter not found - might be platform-specific param on wrong platform
    TLOGD("Parameter '%s' not found in %s mappings (platform-specific or unsupported)",
          paramName.c_str(), getPlatformName(mPlatform).c_str());
    return -1;
}

bool ParamMapper::isSupported(const std::string &paramName) {
    std::lock_guard<std::mutex> lock(mMutex);
    return mMappings.find(paramName) != mMappings.end();
}

size_t ParamMapper::getMappingCount() const {
    std::lock_guard<std::mutex> lock(mMutex);
    return mMappings.size();
}

void ParamMapper::clearMappings() {
    std::lock_guard<std::mutex> lock(mMutex);
    mMappings.clear();
    mInitialized = false;
    TLOGI("Mappings cleared");
}

// ====================== Private Methods ======================
#if PERFHUB_USE_HARDCODED_MAPPINGS

// ====================== Production Mode: Hardcoded Mappings ======================

void ParamMapper::loadHardcodedMappings() {
    TLOGI("Loading hardcoded mappings for platform: %s",
          PlatformDetector::getPlatformName(mPlatform));

    if (mPlatform == Platform::QCOM) {
#include "QcomMappings.inc"
        mMappings = QCOM_MAPPINGS;
        TLOGI("Loaded QCOM hardcoded mappings: %zu params", mMappings.size());
    } else if (mPlatform == Platform::MTK) {
#include "MtkMappings.inc"
        mMappings = MTK_MAPPINGS;
        TLOGI("Loaded MTK hardcoded mappings: %zu params", mMappings.size());
    } else {
        TLOGE("Unsupported platform for hardcoded mappings: %d", static_cast<int>(mPlatform));
    }
}

#else

// ====================== Development Mode: XML Parsing ======================

bool ParamMapper::loadXmlMappings(const std::string &xmlFilename) {
    // Find XML file with priority
    std::string fullPath = findMappingFile(xmlFilename);
    if (fullPath.empty()) {
        TLOGE("Mapping file not found: %s", xmlFilename.c_str());
        return false;
    }

    TLOGI("Loading mappings from: %s", fullPath.c_str());

    // Parse XML document
    return parseXmlDocument(fullPath);
}

std::string ParamMapper::findMappingFile(const std::string &filename) {
    struct stat st;

    // 1. Check /data/vendor/perfhub/ (highest priority - OTA updateable)
    std::string dataPath = std::string(ConfigPath::DATA_VENDOR) + "/" + filename;
    if (stat(dataPath.c_str(), &st) == 0) {
        TLOGD("Found mapping in data partition: %s", dataPath.c_str());
        return dataPath;
    }

    // 2. Check /vendor/etc/perfhub/ (default location)
    std::string vendorPath = std::string(ConfigPath::VENDOR_ETC) + "/" + filename;
    if (stat(vendorPath.c_str(), &st) == 0) {
        TLOGD("Found mapping in vendor partition: %s", vendorPath.c_str());
        return vendorPath;
    }

    // 3. Not found
    return "";
}

bool ParamMapper::parseXmlDocument(const std::string &xmlPath) {
    // Initialize libxml2
    xmlInitParser();

    // Parse XML file
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
    if (xmlStrcmp(root->name, (const xmlChar *)"PlatformMapping") != 0) {
        TLOGE("Invalid root node: %s (expected: PlatformMapping)", root->name);
        xmlFreeDoc(doc);
        xmlCleanupParser();
        return false;
    }

    // Verify platform matches
    xmlChar *xmlPlatform = xmlGetProp(root, (const xmlChar *)"platform");
    if (xmlPlatform) {
        std::string platformStr = (const char *)xmlPlatform;
        xmlFree(xmlPlatform);

        TLOGI("XML platform: %s, Current platform: %s", platformStr.c_str(),
              getPlatformName(mPlatform).c_str());
    }

    // Parse all <ParamMap> nodes
    int mappingCount = 0;
    for (xmlNodePtr node = root->children; node; node = node->next) {
        // Skip non-element nodes
        if (node->type != XML_ELEMENT_NODE) {
            continue;
        }

        if (xmlStrcmp(node->name, (const xmlChar *)"ParamMap") == 0) {
            // Extract 'name' attribute
            xmlChar *name = xmlGetProp(node, (const xmlChar *)"name");
            if (!name) {
                TLOGW("ParamMap missing 'name' attribute, skipping");
                continue;
            }

            std::string paramName = (const char *)name;
            xmlFree(name);

            // Extract 'opcode' or 'command' attribute
            xmlChar *opcodeAttr = xmlGetProp(node, (const xmlChar *)"opcode");
            xmlChar *commandAttr = xmlGetProp(node, (const xmlChar *)"command");

            int32_t value = -1;
            if (opcodeAttr) {
                // QCOM: opcode attribute (hex)
                const char *opcodeStr = (const char *)opcodeAttr;
                if (strncmp(opcodeStr, "0x", 2) == 0 || strncmp(opcodeStr, "0X", 2) == 0) {
                    value = static_cast<int32_t>(strtol(opcodeStr, nullptr, 16));
                } else {
                    value = atoi(opcodeStr);
                }
                xmlFree(opcodeAttr);
            } else if (commandAttr) {
                // MTK: command attribute (hex)
                const char *commandStr = (const char *)commandAttr;
                if (strncmp(commandStr, "0x", 2) == 0 || strncmp(commandStr, "0X", 2) == 0) {
                    value = static_cast<int32_t>(strtol(commandStr, nullptr, 16));
                } else {
                    value = atoi(commandStr);
                }
                xmlFree(commandAttr);
            } else {
                TLOGW("ParamMap '%s' missing 'opcode'/'command' attribute, skipping",
                      paramName.c_str());
                continue;
            }

            // Store in cache
            mMappings[paramName] = value;
            mappingCount++;

            TLOGD("Parsed mapping: '%s' -> 0x%08X", paramName.c_str(), value);
        }
    }

    TLOGI("Parsed %d parameter mappings", mappingCount);

    // Cleanup
    xmlFreeDoc(doc);
    xmlCleanupParser();

    return mappingCount > 0;
}

#endif    // !PERFHUB_USE_HARDCODED_MAPPINGS

}    // namespace perfhub
}    // namespace transsion
}    // namespace vendor
