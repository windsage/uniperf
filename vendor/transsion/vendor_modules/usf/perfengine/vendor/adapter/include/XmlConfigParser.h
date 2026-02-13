#ifndef VENDOR_TRANSSION_PERFENGINE_XML_CONFIG_PARSER_H
#define VENDOR_TRANSSION_PERFENGINE_XML_CONFIG_PARSER_H

#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "PerfEngineTypes.h"

namespace vendor {
namespace transsion {
namespace perfengine {

/**
 * XML Configuration Parser
 *
 * Matching priority (high to low):
 *   1. id + fps + package + activity
 *   2. id + fps + package
 *   3. id + fps  (no package filter)
 *   4. id + package + activity  (fps=0 fallback)
 *   5. id + package             (fps=0 fallback)
 *   6. id only                  (fps=0, generic fallback)
 *
 * Thread Safety: All public methods are thread-safe via std::mutex.
 */
class XmlConfigParser {
public:
    static XmlConfigParser &getInstance();

    /**
     * Load configuration file.
     * Search priority: /data/vendor/perfengine/ > /vendor/etc/perfengine/
     *
     * @param filename  XML filename, e.g. "perfengine_params.xml"
     * @return true on success
     */
    bool loadConfig(const std::string &filename);

    /**
     * Get best-matching scenario configuration.
     *
     * @param sceneId     Event ID
     * @param currentFps  Current screen refresh rate (0 = ignore FPS)
     * @param package     Foreground package name (empty = generic)
     * @param activity    Foreground activity name (empty = package-level)
     * @return Pointer to matched config, nullptr if not found
     */
    const ScenarioConfig *getScenarioConfig(int32_t sceneId, int32_t currentFps = 0,
                                            const std::string &package = "",
                                            const std::string &activity = "");

    /**
     * Reload configuration (supports hot update).
     */
    bool reloadConfig(const std::string &filename);

    /**
     * Clear all cached configurations.
     */
    void clearCache();

    /**
     * Return total number of cached scenario entries.
     */
    size_t getScenarioCount() const;

private:
    XmlConfigParser() = default;
    ~XmlConfigParser() = default;

    XmlConfigParser(const XmlConfigParser &) = delete;
    XmlConfigParser &operator=(const XmlConfigParser &) = delete;

    std::string findConfigFile(const std::string &filename);
    bool parseXmlDocument(const std::string &xmlPath);
    ScenarioConfig parseScenarioNode(void *node);
    PerfParam parseParamNode(void *node);
    int32_t parseInt(const char *valueStr);
    std::string getXmlAttribute(void *node, const char *attrName);
    std::string trim(const std::string &str);

    /**
     * Core matching logic — called with mMutex already held.
     * Implements the 6-level priority cascade.
     */
    const ScenarioConfig *findBestMatch(int32_t sceneId, int32_t fps, const std::string &package,
                                        const std::string &activity) const;

    // key = sceneId; value = all Scenario entries for that id (unsorted)
    // Using vector per id so multiple entries (different fps/package/activity) coexist.
    std::unordered_map<int32_t, std::vector<ScenarioConfig>> mConfigCache;

    mutable std::mutex mMutex;
    std::string mCurrentConfigPath;
    /**
     * Pre-convert semantic params to platform opcodes for all cached entries.
     * Must be called after ParamMapper is initialized.
     * Iterates all ScenarioConfig entries and fills platformParams in-place.
     */
    void buildPlatformParams();

    /**
     * Convert one ScenarioConfig's params to platformParams.
     * Returns number of successfully mapped params.
     */
    int32_t convertScenarioParams(ScenarioConfig &config);
};

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor

#endif    // VENDOR_TRANSSION_PERFENGINE_XML_CONFIG_PARSER_H
