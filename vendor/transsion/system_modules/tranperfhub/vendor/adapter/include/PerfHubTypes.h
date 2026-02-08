#ifndef VENDOR_TRANSSION_PERFHUB_TYPES_H
#define VENDOR_TRANSSION_PERFHUB_TYPES_H

#include <cstdint>
#include <string>
#include <vector>

namespace vendor {
namespace transsion {
namespace perfhub {

/**
 * Platform Type Enumeration
 *
 * Used across all modules for consistent platform identification
 */
enum class Platform {
    UNKNOWN = 0,
    QCOM = 1,
    MTK = 2,
    UNISOC = 3,
};

/**
 * Platform Detector Utility
 *
 * Provides unified platform detection logic across all modules
 * Single source of truth for platform identification
 */
class PlatformDetector {
public:
    /**
     * Detect current platform from system properties
     *
     * Reads ro.board.platform and matches against known platform patterns
     *
     * @return Detected platform type
     */
    static Platform detect();

    /**
     * Get human-readable platform name
     *
     * @param platform Platform enum value
     * @return Platform name string (e.g., "QCOM", "MTK")
     */
    static const char *getPlatformName(Platform platform);
};

/**
 * Performance parameter structure
 */
struct PerfParam {
    std::string name;    // Parameter name (e.g., "CPU_CLUSTER0_MIN_FREQ")
    int32_t value;       // Parameter value

    PerfParam() : value(0) {}
    PerfParam(const std::string &n, int32_t v) : name(n), value(v) {}
};

/**
 * Scenario configuration structure
 */
struct ScenarioConfig {
    int32_t id;                       // Scenario ID (event ID)
    std::string name;                 // Scenario name (for logging)
    int32_t fps;                      // Target FPS (0 = generic config)
    int32_t timeout;                  // Duration in ms (0 = infinite)
    std::string release;              // Release strategy: "auto" | "manual"
    std::vector<PerfParam> params;    // Parameter list

    ScenarioConfig() : id(0), fps(0), timeout(0), release("auto") {}
};

/**
 * Config file paths
 */
namespace ConfigPath {
constexpr const char *DATA_VENDOR = "/data/vendor/perfhub";
constexpr const char *VENDOR_ETC = "/vendor/etc/perfhub";
}    // namespace ConfigPath

}    // namespace perfhub
}    // namespace transsion
}    // namespace vendor

#endif    // VENDOR_TRANSSION_PERFHUB_TYPES_H
