#pragma once

/**
 * QcomNodeDef.h - QCOM platform sysfs/thermal node candidate lists
 *
 * Design principle:
 *   - Each candidate array is NULL-terminated.
 *   - NodeProbe iterates the list at init() and caches the first
 *     existing path. Zero probing cost on the sampling hot path.
 *   - Ordered from newest chip to oldest (most-likely first → faster probe).
 *
 * Chip coverage:
 *   SM8750 (volcano)  / SM8650 (pineapple) / SM8550 (kalama)
 *   SM8450 (taro)     / SM8350 (lahaina)   / SM8250 (kona)
 *   SM7xxx / SM6xxx   series (best-effort)
 */

// ---------------------------------------------------------------------------
// GPU Utilization
// Node type: percentage (0-100), read as integer string
// ---------------------------------------------------------------------------
static constexpr const char* QCOM_GPU_UTIL_CANDIDATES[] = {
    // SM8650+ (pineapple) — devfreq-based node
    "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
    // SM8550 and earlier — legacy busy_percentage
    "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
    // Some SM7xx boards expose this path
    "/sys/kernel/gpu/gpu_busy",
    nullptr
};

// ---------------------------------------------------------------------------
// GPU Current Frequency
// Node type: Hz as integer string
// ---------------------------------------------------------------------------
static constexpr const char* QCOM_GPU_FREQ_CANDIDATES[] = {
    "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",   // SM8650+
    "/sys/class/kgsl/kgsl-3d0/gpuclk",              // SM8550 and earlier
    "/sys/kernel/gpu/gpu_clock",                     // SM7xx fallback
    nullptr
};

// ---------------------------------------------------------------------------
// CPU Temperature — per cluster thermal zone names
// Node format: /sys/class/thermal/thermal_zone<N>/temp  (milli-celsius)
// NodeProbe scans all thermal zones to match these name candidates.
// ---------------------------------------------------------------------------

// Little cluster (CLUSTER0)
static constexpr const char* QCOM_CPU_TEMP_CLUSTER0_ZONE_CANDIDATES[] = {
    "cpu-0-0-usr",          // SM8650 / SM8550 (silver)
    "cpu0-silver-usr",      // SM8450 / SM8350
    "cpu0",                 // SM8250 / legacy
    "cpu-cluster0",         // Generic fallback
    nullptr
};

// Big cluster (CLUSTER1)
static constexpr const char* QCOM_CPU_TEMP_CLUSTER1_ZONE_CANDIDATES[] = {
    "cpu-1-0-usr",          // SM8650 / SM8550 (gold)
    "cpu4-gold-usr",        // SM8450 / SM8350
    "cpu4",                 // SM8250 / legacy
    "cpu-cluster1",
    nullptr
};

// Prime cluster (CLUSTER2 / single prime core)
static constexpr const char* QCOM_CPU_TEMP_PRIME_ZONE_CANDIDATES[] = {
    "cpu-7-0-usr",          // SM8650 / SM8550 prime
    "cpu7-gold-plus-usr",   // SM8450 prime
    "cpu7",                 // SM8250 gold+
    "cpu-cluster2",
    nullptr
};

// ---------------------------------------------------------------------------
// GPU Temperature
// ---------------------------------------------------------------------------
static constexpr const char* QCOM_GPU_TEMP_ZONE_CANDIDATES[] = {
    "gpuss-0-usr",          // SM8650+
    "gpuss-0",              // SM8550
    "gpu0-usr",             // SM8450 / SM8350
    "gpu",                  // Legacy
    nullptr
};

// ---------------------------------------------------------------------------
// Skin temperature (PCB surface)
// ---------------------------------------------------------------------------
static constexpr const char* QCOM_SKIN_TEMP_ZONE_CANDIDATES[] = {
    "skin-msm-therm",       // SM8650 / SM8550
    "skin-therm-usr",       // SM8450
    "quiet-therm-usr",      // SM8350 / SM8250
    "xo-therm-usr",         // Very legacy fallback
    nullptr
};

// ---------------------------------------------------------------------------
// SoC temperature (PMIC BCL)
// ---------------------------------------------------------------------------
static constexpr const char* QCOM_SOC_TEMP_ZONE_CANDIDATES[] = {
    "pm8550-bcl-lvl0",      // SM8650 / SM8550
    "pm8450-bcl-lvl0",      // SM8450
    "pm8350b-bcl-lvl0",     // SM8350
    "pm8150b-bcl-lvl0",     // SM8250
    nullptr
};

// ---------------------------------------------------------------------------
// Battery (power_supply nodes — relatively stable across QCOM chips)
// ---------------------------------------------------------------------------
static constexpr const char* QCOM_BATTERY_CURRENT_CANDIDATES[] = {
    "/sys/class/power_supply/battery/current_now",
    "/sys/class/power_supply/Battery/current_now",
    nullptr
};

static constexpr const char* QCOM_BATTERY_TEMP_CANDIDATES[] = {
    "/sys/class/power_supply/battery/temp",
    "/sys/class/power_supply/Battery/temp",
    nullptr
};

static constexpr const char* QCOM_BATTERY_LEVEL_CANDIDATES[] = {
    "/sys/class/power_supply/battery/capacity",
    "/sys/class/power_supply/Battery/capacity",
    nullptr
};

static constexpr const char* QCOM_CHARGER_ONLINE_CANDIDATES[] = {
    "/sys/class/power_supply/usb/online",
    "/sys/class/power_supply/USB/online",
    "/sys/class/power_supply/ac/online",    // Some boards expose AC instead
    nullptr
};
