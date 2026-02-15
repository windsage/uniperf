#pragma once

#include <cstdint>
#include <string>
#include "MetricDef.h"

/**
 * NodeProbe.h - Runtime sysfs/thermal node probing
 *
 * Called ONCE during init(), never on the sampling hot path.
 *
 * Two probe strategies:
 *
 * 1. Direct path probe (GPU, battery, PSI):
 *    Iterate a nullptr-terminated candidate array.
 *    Call access(path, R_OK) on each; cache the first hit.
 *
 * 2. Thermal zone scan (CPU temp, GPU temp, skin, SoC):
 *    Enumerate /sys/class/thermal/thermal_zone<N>/type
 *    Match the zone "type" string against a candidate name list.
 *    Cache the resolved /sys/class/thermal/thermal_zone<N>/temp path.
 *
 * Results are stored in sResolvedPaths[], indexed by MetricId.
 * Collectors call getPath(id) to obtain the cached path — O(1), no IO.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

class NodeProbe {
public:
    /**
     * Run all probes for the current platform.
     * Must be called once from CollectorManager::init(), before sampling starts.
     * Not thread-safe — single-threaded init assumed.
     */
    static void probeAll();

    /**
     * Get the resolved (probed) sysfs path for a metric.
     *
     * @param id  MetricId to look up
     * @return    Null-terminated path string, or nullptr if not found
     */
    static const char* getPath(MetricId id);

    /**
     * Dump all probe results to logcat.
     * Useful for debugging new device bring-up.
     */
    static void dumpResults();

private:
    // ---------------------------------------------------------------------------
    // Probe helpers
    // ---------------------------------------------------------------------------

    /**
     * Probe a nullptr-terminated candidate path array.
     * Returns the first path that is readable, or empty string if none.
     */
    static std::string probeDirectPath(const char* const* candidates);

    /**
     * Scan thermal zones to find one whose "type" matches any name in the
     * nullptr-terminated candidates array.
     * Returns the resolved ".../temp" path, or empty string if not found.
     *
     * @param zoneCandidates  nullptr-terminated list of zone type name strings
     */
    static std::string probeThermalZone(const char* const* zoneCandidates);

    /**
     * Store a resolved path for the given MetricId.
     * Ignores empty string (probe failed → path stays nullptr).
     */
    static void store(MetricId id, const std::string& path);

    // ---------------------------------------------------------------------------
    // Per-group probe functions (called from probeAll)
    // ---------------------------------------------------------------------------
    static void probeGpu();
    static void probeThermal();
    static void probePower();

    // Resolved path storage — indexed by metricIndex(id)
    // Allocated as a flat C-string array to keep hot-path reads simple.
    // Max path length: 128 chars (all known sysfs paths fit comfortably)
    static constexpr size_t kMaxPathLen = 128;
    static char sResolvedPaths[kMetricSlotCount][kMaxPathLen];
    static bool sProbeCompleted;
};

}  // namespace sysmonitor
}  // namespace transsion
}  // namespace vendor
