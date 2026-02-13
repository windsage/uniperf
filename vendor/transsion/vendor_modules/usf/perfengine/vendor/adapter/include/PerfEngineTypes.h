#ifndef VENDOR_TRANSSION_PERFENGINE_TYPES_H
#define VENDOR_TRANSSION_PERFENGINE_TYPES_H

#include <cstdint>
#include <string>
#include <vector>

namespace vendor {
namespace transsion {
namespace perfengine {

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
 *
 * platformParams is always present: pre-converted [opcode, value, opcode, value, ...]
 * params and name are stripped in production builds (PERFENGINE_PRODUCTION=1)
 * to reduce memory footprint.
 */
struct ScenarioConfig {
    int32_t id;              // Scenario ID (event ID)
    int32_t fps;             // Target FPS (0 = generic config)
    std::string package;     // Package name filter (empty = match all)
    std::string activity;    // Activity name filter (empty = match all)
    int32_t timeout;         // Duration in ms (0 = infinite)
    std::string release;     // Release strategy: "auto" | "manual"

    // Pre-converted platform opcodes: [opcode0, value0, opcode1, value1, ...]
    // Built at parse time, zero conversion cost at acquirePerfLock hot path.
    std::vector<int32_t> platformParams;

#if !PERFENGINE_PRODUCTION
    // Debug/dev only: stripped in production to save memory
    std::string name;
    std::vector<PerfParam> params;
#endif

    ScenarioConfig() : id(0), fps(0), timeout(0), release("auto") {}
};

/**
 * Config file paths
 */
namespace ConfigPath {
constexpr const char *DATA_VENDOR = "/data/vendor/perfengine";
constexpr const char *VENDOR_ETC = "/vendor/etc/perfengine";
}    // namespace ConfigPath

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor

#endif    // VENDOR_TRANSSION_PERFENGINE_TYPES_H
