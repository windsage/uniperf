#pragma once

/**
 * MtkNodeDef.h - MTK platform sysfs/thermal node candidate lists
 *
 * Chip coverage:
 *   Dimensity 9300 (MT6989) / 9200 (MT6985) / 9000 (MT6983)
 *   Dimensity 8300 (MT6897) / 8200 (MT6895) / 8100 (MT6893)
 *   Dimensity 7xxx / 6xxx series (best-effort)
 *
 * MTK thermal zone naming differs from QCOM:
 *   - Uses numeric zones: "thermal_zone0", "thermal_zone1", ...
 *   - Or named zones: "mtktscpu", "mtktsGPU", "mtktsAP"
 *   NodeProbe reads /sys/class/thermal/thermal_zone<N>/type to match.
 */

// ---------------------------------------------------------------------------
// GPU Utilization
// MTK exposes GPU loading via multiple paths depending on DDK version
// ---------------------------------------------------------------------------
static constexpr const char* MTK_GPU_UTIL_CANDIDATES[] = {
    // Mali DDK (most Dimensity chips)
    "/sys/kernel/ged/hal/gpu_utilization",
    // Older path
    "/sys/kernel/debug/ged/hal/gpu_utilization",
    // Some MT6xxx fallback
    "/sys/devices/platform/mali.0/devfreq/mali.0/cur_load",
    nullptr
};

// ---------------------------------------------------------------------------
// GPU Current Frequency (Hz)
// ---------------------------------------------------------------------------
static constexpr const char* MTK_GPU_FREQ_CANDIDATES[] = {
    "/sys/kernel/ged/hal/current_freqency",   // Note: MTK typo intentional
    "/sys/kernel/ged/hal/current_frequency",
    "/sys/devices/platform/mali.0/devfreq/mali.0/cur_freq",
    nullptr
};

// ---------------------------------------------------------------------------
// CPU Temperature — thermal zone type name candidates
// NodeProbe matches these against /sys/class/thermal/thermal_zone<N>/type
// ---------------------------------------------------------------------------

// Little cluster (CLUSTER0)
static constexpr const char* MTK_CPU_TEMP_CLUSTER0_ZONE_CANDIDATES[] = {
    "mtktscpu",             // Dimensity 9xxx / 8xxx (little)
    "cpu_thermal",          // Generic Linux name
    "mtk-cpu-cooler",
    nullptr
};

// Big cluster (CLUSTER1)
static constexpr const char* MTK_CPU_TEMP_CLUSTER1_ZONE_CANDIDATES[] = {
    "mtktsbig",             // Dimensity 9xxx big cluster
    "cpu-big-thermal",
    "cpu_thermal1",
    nullptr
};

// Prime / ultra-big cluster (CLUSTER2)
static constexpr const char* MTK_CPU_TEMP_PRIME_ZONE_CANDIDATES[] = {
    "mtktsultracore",       // Dimensity 9300 / 9200 ultra-big
    "mtktsprime",
    "cpu-prime-thermal",
    nullptr
};

// ---------------------------------------------------------------------------
// GPU Temperature
// ---------------------------------------------------------------------------
static constexpr const char* MTK_GPU_TEMP_ZONE_CANDIDATES[] = {
    "mtktsGPU",             // Dimensity series standard name
    "mtk_gpufreq_tz",
    "gpu-thermal",
    nullptr
};

// ---------------------------------------------------------------------------
// Skin temperature
// ---------------------------------------------------------------------------
static constexpr const char* MTK_SKIN_TEMP_ZONE_CANDIDATES[] = {
    "mtktsAP",              // AP (application processor) board temp
    "skin_thermal",
    "board-thermal",
    nullptr
};

// ---------------------------------------------------------------------------
// SoC temperature
// ---------------------------------------------------------------------------
static constexpr const char* MTK_SOC_TEMP_ZONE_CANDIDATES[] = {
    "mtktssoc",
    "soc-thermal",
    "mtktspa",              // PA zone as SoC proxy on some chips
    nullptr
};

// ---------------------------------------------------------------------------
// Battery (relatively uniform across MTK boards)
// ---------------------------------------------------------------------------
static constexpr const char* MTK_BATTERY_CURRENT_CANDIDATES[] = {
    "/sys/class/power_supply/battery/current_now",
    "/sys/class/power_supply/mtk-gauge/current_now",
    nullptr
};

static constexpr const char* MTK_BATTERY_TEMP_CANDIDATES[] = {
    "/sys/class/power_supply/battery/temp",
    "/sys/class/power_supply/mtk-gauge/temp",
    nullptr
};

static constexpr const char* MTK_BATTERY_LEVEL_CANDIDATES[] = {
    "/sys/class/power_supply/battery/capacity",
    "/sys/class/power_supply/mtk-gauge/capacity",
    nullptr
};

static constexpr const char* MTK_CHARGER_ONLINE_CANDIDATES[] = {
    "/sys/class/power_supply/usb/online",
    "/sys/class/power_supply/mtk_charger/online",
    "/sys/class/power_supply/ac/online",
    nullptr
};
