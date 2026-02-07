#ifndef VENDOR_TRANSSION_PERFHUB_XML_CONFIG_PARSER_H
#define VENDOR_TRANSSION_PERFHUB_XML_CONFIG_PARSER_H

#include "PerfHubTypes.h"
#include <unordered_map>
#include <memory>
#include <mutex>

namespace vendor {
namespace transsion {
namespace perfhub {

/**
 * XML Configuration Parser
 * 
 * Features:
 * 1. Parse scenario configuration XML files
 * 2. Support FPS-specific configurations
 * 3. Cache parsed results for performance
 * 4. Thread-safe
 * 5. Handle XML whitespace correctly
 * 
 * Thread Safety:
 *  - All public methods are thread-safe
 *  - Uses std::mutex for synchronization
 */
class XmlConfigParser {
public:
    /**
     * Get singleton instance
     */
    static XmlConfigParser& getInstance();

    /**
     * Load configuration file
     * 
     * Priority: /data/vendor/perfhub/ > /vendor/etc/perfhub/
     * 
     * @param xmlPath XML filename (e.g., "perfhub_config_pineapple.xml")
     * @return true on success, false on failure
     */
    bool loadConfig(const std::string& xmlPath);

    /**
     * Get scenario configuration
     * 
     * @param sceneId Scenario ID (EventId)
     * @param currentFps Current screen refresh rate (for FPS-specific matching)
     * @return Scenario config pointer, nullptr if not found
     */
    const ScenarioConfig* getScenarioConfig(int32_t sceneId, int32_t currentFps = 0);

    /**
     * Reload configuration (support hot updates)
     * 
     * @param xmlPath XML filename
     * @return true on success, false on failure
     */
    bool reloadConfig(const std::string& xmlPath);

    /**
     * Clear all cached configurations
     */
    void clearCache();

    /**
     * Get number of loaded scenarios
     */
    size_t getScenarioCount() const;

private:
    XmlConfigParser() = default;
    ~XmlConfigParser() = default;
    
    // Disable copy and assignment
    XmlConfigParser(const XmlConfigParser&) = delete;
    XmlConfigParser& operator=(const XmlConfigParser&) = delete;

    /**
     * Find configuration file with priority
     * 
     * @param filename Filename
     * @return Full path, empty string if not found
     */
    std::string findConfigFile(const std::string& filename);

    /**
     * Parse XML document
     * 
     * @param xmlPath Full file path
     * @return true on success, false on failure
     */
    bool parseXmlDocument(const std::string& xmlPath);

    /**
     * Parse single <Scenario> node
     * 
     * @param node XML node pointer (xmlNodePtr)
     * @return Scenario configuration
     */
    ScenarioConfig parseScenarioNode(void* node);

    /**
     * Parse <Param> node
     * 
     * @param node XML node pointer (xmlNodePtr)
     * @return Performance parameter
     */
    PerfParam parseParamNode(void* node);

    /**
     * Parse integer value (support decimal and hexadecimal)
     * 
     * @param valueStr Value string
     * @return Parsed integer
     */
    int32_t parseInt(const char* valueStr);

    /**
     * Get XML attribute value with whitespace trimming
     * 
     * @param node XML node pointer
     * @param attrName Attribute name
     * @return Trimmed string, empty if attribute not found
     */
    std::string getXmlAttribute(void* node, const char* attrName);

    /**
     * Trim leading and trailing whitespace
     * 
     * @param str Input string
     * @return Trimmed string
     */
    std::string trim(const std::string& str);

private:
    // Configuration cache: key = (sceneId << 16) | fps
    std::unordered_map<uint32_t, ScenarioConfig> mConfigCache;
    
    // Thread-safe mutex
    mutable std::mutex mMutex;
    
    // Current loaded config path
    std::string mCurrentConfigPath;

    // Helper: Generate cache key
    static uint32_t makeCacheKey(int32_t sceneId, int32_t fps) {
        return (static_cast<uint32_t>(sceneId) << 16) | (fps & 0xFFFF);
    }
};

} // namespace perfhub
} // namespace transsion
} // namespace vendor

#endif // VENDOR_TRANSSION_PERFHUB_XML_CONFIG_PARSER_H
