#ifndef VENDOR_TRANSSION_PERFHUB_PARAM_MAPPER_H
#define VENDOR_TRANSSION_PERFHUB_PARAM_MAPPER_H

#include "PerfHubTypes.h"
#include <unordered_map>
#include <string>
#include <mutex>

// 编译时开关：开发期用XML，量产期用硬编码
#ifndef PERFHUB_USE_HARDCODED_MAPPINGS
#define PERFHUB_USE_HARDCODED_MAPPINGS 0  // 开发期默认为0
#endif

namespace vendor {
namespace transsion {
namespace perfhub {

/**
 * Parameter Mapper
 * 
 * Responsibilities:
 * 1. Load platform-specific parameter mappings (QCOM/MTK)
 * 2. Convert semantic parameter names to platform opcodes/commands
 * 3. Cache mappings for fast lookup (O(1) hash map)
 * 4. Support both XML (development) and hardcoded (production) modes
 * 
 * Thread Safety:
 *  - Thread-safe after initialization
 *  - Uses std::mutex for concurrent access protection
 */
class ParamMapper {
public:
    /**
     * Get singleton instance
     */
    static ParamMapper& getInstance();

    /**
     * Initialize mapper with platform detection
     * 
     * @param platform Target platform (UNKNOWN = auto-detect)
     * @return true on success, false on failure
     */
    bool init(Platform platform = Platform::UNKNOWN);

    /**
     * Get opcode/command for a parameter name
     * 
     * @param paramName Semantic parameter name (e.g., "CPU_CLUSTER0_MIN_FREQ")
     * @return Opcode/Command value, -1 if not found
     * 
     * Examples:
     *   QCOM: "CPU_CLUSTER0_MIN_FREQ" -> 0x40800100
     *   MTK:  "CPU_CLUSTER0_MIN_FREQ" -> 0x00400000
     */
    int32_t getOpcode(const std::string& paramName);

    /**
     * Check if parameter is supported on current platform
     * 
     * @param paramName Parameter name
     * @return true if supported, false otherwise
     */
    bool isSupported(const std::string& paramName);

    /**
     * Get current platform type
     */
    Platform getPlatform() const { return mPlatform; }

    /**
     * Get number of loaded mappings
     */
    size_t getMappingCount() const;

    /**
     * Clear all cached mappings (for testing)
     */
    void clearMappings();

private:
    ParamMapper() = default;
    ~ParamMapper() = default;
    
    // Disable copy and assignment
    ParamMapper(const ParamMapper&) = delete;
    ParamMapper& operator=(const ParamMapper&) = delete;

#if PERFHUB_USE_HARDCODED_MAPPINGS
    /**
     * Load hardcoded mappings (production mode)
     */
    void loadHardcodedMappings();
#else
    /**
     * Load mappings from XML file (development mode)
     * 
     * @param xmlPath XML file path
     * @return true on success, false on failure
     */
    bool loadXmlMappings(const std::string& xmlPath);

    /**
     * Parse XML document and extract mappings
     * 
     * @param xmlPath Full XML file path
     * @return true on success, false on failure
     */
    bool parseXmlDocument(const std::string& xmlPath);

    /**
     * Find mapping XML file with priority
     * 
     * Priority: /data/vendor/perfhub/ > /vendor/etc/perfhub/
     * 
     * @param filename XML filename
     * @return Full path if found, empty string otherwise
     */
    std::string findMappingFile(const std::string& filename);
#endif

private:
    // Current platform type
    Platform mPlatform = Platform::UNKNOWN;

    // Parameter name -> Opcode/Command mapping cache
    std::unordered_map<std::string, int32_t> mMappings;

    // Thread-safe mutex
    mutable std::mutex mMutex;

    // Initialization flag
    bool mInitialized = false;
};

} // namespace perfhub
} // namespace transsion
} // namespace vendor

#endif // VENDOR_TRANSSION_PERFHUB_PARAM_MAPPER_H
