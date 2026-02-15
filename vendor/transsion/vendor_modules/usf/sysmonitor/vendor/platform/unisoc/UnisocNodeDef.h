#pragma once

/**
 * UnisocNodeDef.h - UNISOC (Spreadtrum) platform sysfs/thermal node candidate lists
 *
 * Chip coverage:
 *   T820 / T760 / T618 / T616 / T606
 *   UMS9230 / UMS9620 series
 *
 * Note: UNISOC GPU is typically IMG PowerVR or ARM Mali depending on tier.
 *   High-end (T820, UMS9620): IMG GPU
 *   Mid-range (T760, T618):   ARM Mali
 */

// ---------------------------------------------------------------------------
// GPU Utilization
// ---------------------------------------------------------------------------
static constexpr const char* UNISOC_GPU_UTIL_CANDIDATES[] = {
    // IMG PowerVR (T820, UMS9620)
    "/sys/kernel/debug/pvr/status",             // pvr utilization (non-standard read)
    "/sys/devices/platform/sprd-gpu/utilization",
    // Mali (T760, T618)
    "/sys/devices/platform/mali.0/devfreq/mali.0/cur_load",
    "/sys/kernel/gpu/gpu_loading",
    nullptr
};

// ---------------------------------------------------------------------------
// GPU Current Frequency (Hz)
// ---------------------------------------------------------------------------
static constexpr const char* UNISOC_GPU_FREQ_CANDIDATES[] = {
    "/sys/devices/platform/sprd-gpu/cur_freq",
    "/sys/devices/platform/mali.0/devfreq/mali.0/cur_freq",
    "/sys/kernel/gpu/gpu_clock",
    nullptr
};

// ---------------------------------------------------------------------------
// CPU Temperature — thermal zone type names
// UNISOC uses descriptive zone names under /sys/class/thermal/
// ---------------------------------------------------------------------------

// Little cluster (CLUSTER0)
static constexpr const char* UNISOC_CPU_TEMP_CLUSTER0_ZONE_CANDIDATES[] = {
    "cpu-thmzone",          // T820 / T760 little cluster
    "sprd-cpu-thmzone",
    "cpu_thermal",
    nullptr
};

// Big cluster (CLUSTER1)
static constexpr const char* UNISOC_CPU_TEMP_CLUSTER1_ZONE_CANDIDATES[] = {
    "cpu-big-thmzone",
    "sprd-cpu-big-thmzone",
    "cpu_thermal1",
    nullptr
};

// Prime cluster (CLUSTER2) — T820 only
static constexpr const char* UNISOC_CPU_TEMP_PRIME_ZONE_CANDIDATES[] = {
    "cpu-prime-thmzone",
    nullptr
};

// ---------------------------------------------------------------------------
// GPU Temperature
// ---------------------------------------------------------------------------
static constexpr const char* UNISOC_GPU_TEMP_ZONE_CANDIDATES[] = {
    "gpu-thmzone",
    "sprd-gpu-thmzone",
    "gpu_thermal",
    nullptr
};

// ---------------------------------------------------------------------------
// Skin temperature
// ---------------------------------------------------------------------------
static constexpr const char* UNISOC_SKIN_TEMP_ZONE_CANDIDATES[] = {
    "board-thmzone",
    "skin-thmzone",
    "sprd-board-thmzone",
    nullptr
};

// ---------------------------------------------------------------------------
// SoC temperature
// ---------------------------------------------------------------------------
static constexpr const char* UNISOC_SOC_TEMP_ZONE_CANDIDATES[] = {
    "soc-thmzone",
    "sprd-soc-thmzone",
    nullptr
};

// ---------------------------------------------------------------------------
// Battery
// ---------------------------------------------------------------------------
static constexpr const char* UNISOC_BATTERY_CURRENT_CANDIDATES[] = {
    "/sys/class/power_supply/battery/current_now",
    "/sys/class/power_supply/sprd-fgu/current_now",
    nullptr
};

static constexpr const char* UNISOC_BATTERY_TEMP_CANDIDATES[] = {
    "/sys/class/power_supply/battery/temp",
    "/sys/class/power_supply/sprd-fgu/temp",
    nullptr
};

static constexpr const char* UNISOC_BATTERY_LEVEL_CANDIDATES[] = {
    "/sys/class/power_supply/battery/capacity",
    "/sys/class/power_supply/sprd-fgu/capacity",
    nullptr
};

static constexpr const char* UNISOC_CHARGER_ONLINE_CANDIDATES[] = {
    "/sys/class/power_supply/usb/online",
    "/sys/class/power_supply/sprd-charger/online",
    "/sys/class/power_supply/ac/online",
    nullptr
};
