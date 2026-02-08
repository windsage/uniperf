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
    // Get singleton instance (thread-safe, lazy initialization)
    static PlatformDetector &getInstance();

    // Get cached platform type (zero-cost after first call)
    inline Platform getPlatform() const { return mPlatform; }

    // Fast boolean queries (inlined for zero overhead)
    inline bool isQCOM() const { return mPlatform == Platform::QCOM; }
    inline bool isMTK() const { return mPlatform == Platform::MTK; }
    inline bool isUNISOC() const { return mPlatform == Platform::UNISOC; }
    inline bool isUnknown() const { return mPlatform == Platform::UNKNOWN; }

    // Get platform name string (for logging)
    const char *getPlatformName() const;

    // Prevent copy and assignment
    PlatformDetector(const PlatformDetector &) = delete;
    PlatformDetector &operator=(const PlatformDetector &) = delete;

private:
    // Private constructor for singleton
    PlatformDetector();

    // Core detection logic (called once during initialization)
    Platform performDetection();

    // Cached platform type
    Platform mPlatform;
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
