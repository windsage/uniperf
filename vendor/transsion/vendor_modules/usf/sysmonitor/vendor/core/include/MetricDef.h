#pragma once

#include <cstdint>
#include <cstring>

/**
 * MetricDef.h - System Monitor Metric Definitions
 *
 * Defines all metric IDs and associated data structures used across
 * the entire SysMonitor module (native + Java bridge).
 *
 * ID allocation:
 *   0x0001 - 0x000F  : CPU utilization
 *   0x0010 - 0x001F  : CPU temperature
 *   0x0020 - 0x002F  : GPU
 *   0x0030 - 0x003F  : Board / skin temperature
 *   0x0040 - 0x004F  : Memory
 *   0x0050 - 0x005F  : Power / battery
 *   0x0060 - 0x006F  : Network (Java-side collected)
 *   0x0070 - 0x007F  : Frame (Java-side collected)
 *   0x0080 - 0x008F  : Process pressure
 *   0x00FF           : Sentinel / MAX
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

// ---------------------------------------------------------------------------
// MetricId
// ---------------------------------------------------------------------------
enum class MetricId : uint32_t {
    INVALID = 0,

    // --- CPU utilization (source: /proc/stat) ---
    CPU_UTIL_CLUSTER0   = 0x0001,   // percent, little cluster avg
    CPU_UTIL_CLUSTER1   = 0x0002,   // percent, big cluster avg
    CPU_UTIL_CLUSTER2   = 0x0003,   // percent, prime cluster avg
    CPU_UTIL_TOTAL      = 0x0004,   // percent, all-core average

    // --- CPU temperature (source: thermal zone, platform-specific) ---
    CPU_TEMP_CLUSTER0   = 0x0011,   // milli-celsius, little cluster
    CPU_TEMP_CLUSTER1   = 0x0012,   // milli-celsius, big cluster
    CPU_TEMP_PRIME      = 0x0013,   // milli-celsius, prime cluster

    // --- GPU (source: sysfs, platform-specific) ---
    GPU_UTIL            = 0x0021,   // percent
    GPU_TEMP            = 0x0022,   // milli-celsius
    GPU_FREQ_CUR        = 0x0023,   // Hz

    // --- Board / skin temperature ---
    SKIN_TEMP           = 0x0031,   // milli-celsius
    SOC_TEMP            = 0x0032,   // milli-celsius

    // --- Memory (source: /proc/meminfo + /proc/pressure/*) ---
    MEM_FREE            = 0x0041,   // kB
    MEM_AVAILABLE       = 0x0042,   // kB
    MEM_CACHED          = 0x0043,   // kB
    MEM_SWAP_USED       = 0x0044,   // kB
    MEM_PRESSURE        = 0x0045,   // percent (PSI some_avg10)
    IO_PRESSURE         = 0x0046,   // percent (PSI some_avg10)

    // --- Power / battery (source: /sys/class/power_supply/) ---
    BATTERY_CURRENT     = 0x0051,   // microampere (may be negative = discharge)
    BATTERY_TEMP        = 0x0052,   // milli-celsius
    BATTERY_LEVEL       = 0x0053,   // percent 0-100
    CHARGER_ONLINE      = 0x0054,   // bool: 0=offline, 1=online

    // --- Network — collected by Java layer, pushed via JNI ---
    WIFI_RSSI           = 0x0061,   // dBm
    CELL_SIGNAL         = 0x0062,   // ASU

    // --- Frame metrics — collected by Java layer (FrameMetrics), pushed via JNI ---
    FRAME_JANKY_RATE    = 0x0071,   // percent  (janky frames / total frames * 100)
    FRAME_MISSED        = 0x0072,   // count    (absolute missed frames in last window)

    // --- Process pressure ---
    BG_PROCESS_COUNT    = 0x0081,   // count (ActivityManager.getRunningAppProcesses)

    // Sentinel — keep last, used for array sizing
    METRIC_ID_MAX       = 0x00FF,
};

// Convenience: total number of valid metric slots
static constexpr uint32_t kMetricSlotCount =
    static_cast<uint32_t>(MetricId::METRIC_ID_MAX) + 1u;

// ---------------------------------------------------------------------------
// MetricValue - a single sampled data point
// ---------------------------------------------------------------------------
struct MetricValue {
    int64_t  value;         // Raw metric value (unit depends on MetricId)
    int64_t  timestampNs;   // CLOCK_MONOTONIC nanoseconds when sampled
    bool     valid;         // false = not yet sampled / node not found

    MetricValue() : value(0), timestampNs(0), valid(false) {}
    MetricValue(int64_t v, int64_t ts) : value(v), timestampNs(ts), valid(true) {}
};

// ---------------------------------------------------------------------------
// SampleInterval - canonical sample periods in milliseconds
// Collectors pick the closest interval tier; CollectorManager batches them.
// ---------------------------------------------------------------------------
namespace SampleInterval {
    static constexpr int32_t FAST     = 200;    // CPU util, GPU util/freq
    static constexpr int32_t MEDIUM   = 500;    // Temperature, PSI
    static constexpr int32_t SLOW     = 1000;   // Memory, battery current/temp
    static constexpr int32_t VSLOW    = 5000;   // Battery level, charger state
    static constexpr int32_t JAVA     = 2000;   // Network, frame (Java-driven)
}  // namespace SampleInterval

// ---------------------------------------------------------------------------
// Helper: convert MetricId to array index
// ---------------------------------------------------------------------------
inline constexpr uint32_t metricIndex(MetricId id) {
    return static_cast<uint32_t>(id);
}

// ---------------------------------------------------------------------------
// Helper: human-readable name (debug builds only)
// ---------------------------------------------------------------------------
#ifndef SMON_LOG_DISABLED
inline const char* metricName(MetricId id) {
    switch (id) {
        case MetricId::CPU_UTIL_CLUSTER0:  return "CPU_UTIL_CLUSTER0";
        case MetricId::CPU_UTIL_CLUSTER1:  return "CPU_UTIL_CLUSTER1";
        case MetricId::CPU_UTIL_CLUSTER2:  return "CPU_UTIL_CLUSTER2";
        case MetricId::CPU_UTIL_TOTAL:     return "CPU_UTIL_TOTAL";
        case MetricId::CPU_TEMP_CLUSTER0:  return "CPU_TEMP_CLUSTER0";
        case MetricId::CPU_TEMP_CLUSTER1:  return "CPU_TEMP_CLUSTER1";
        case MetricId::CPU_TEMP_PRIME:     return "CPU_TEMP_PRIME";
        case MetricId::GPU_UTIL:           return "GPU_UTIL";
        case MetricId::GPU_TEMP:           return "GPU_TEMP";
        case MetricId::GPU_FREQ_CUR:       return "GPU_FREQ_CUR";
        case MetricId::SKIN_TEMP:          return "SKIN_TEMP";
        case MetricId::SOC_TEMP:           return "SOC_TEMP";
        case MetricId::MEM_FREE:           return "MEM_FREE";
        case MetricId::MEM_AVAILABLE:      return "MEM_AVAILABLE";
        case MetricId::MEM_CACHED:         return "MEM_CACHED";
        case MetricId::MEM_SWAP_USED:      return "MEM_SWAP_USED";
        case MetricId::MEM_PRESSURE:       return "MEM_PRESSURE";
        case MetricId::IO_PRESSURE:        return "IO_PRESSURE";
        case MetricId::BATTERY_CURRENT:    return "BATTERY_CURRENT";
        case MetricId::BATTERY_TEMP:       return "BATTERY_TEMP";
        case MetricId::BATTERY_LEVEL:      return "BATTERY_LEVEL";
        case MetricId::CHARGER_ONLINE:     return "CHARGER_ONLINE";
        case MetricId::WIFI_RSSI:          return "WIFI_RSSI";
        case MetricId::CELL_SIGNAL:        return "CELL_SIGNAL";
        case MetricId::FRAME_JANKY_RATE:   return "FRAME_JANKY_RATE";
        case MetricId::FRAME_MISSED:       return "FRAME_MISSED";
        case MetricId::BG_PROCESS_COUNT:   return "BG_PROCESS_COUNT";
        default:                           return "UNKNOWN";
    }
}
#endif  // !SMON_LOG_DISABLED

}  // namespace sysmonitor
}  // namespace transsion
}  // namespace vendor
