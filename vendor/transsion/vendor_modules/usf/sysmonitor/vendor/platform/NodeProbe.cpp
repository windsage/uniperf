#define LOG_TAG "SMon-Probe"

#include "NodeProbe.h"

#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>

#include <cstdio>
#include <cstring>
#include <string>

#include "PlatformNodeDef.h"
#include "SysMonLog.h"

namespace vendor {
namespace transsion {
namespace sysmonitor {

// ---------------------------------------------------------------------------
// Static storage
// ---------------------------------------------------------------------------
char NodeProbe::sResolvedPaths[kMetricSlotCount][kMaxPathLen];
bool NodeProbe::sProbeCompleted = false;

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

void NodeProbe::probeAll() {
    if (sProbeCompleted) {
        SMLOGW("probeAll: already completed, skip");
        return;
    }

    // Zero-initialize all paths (empty string = not found)
    memset(sResolvedPaths, 0, sizeof(sResolvedPaths));

    SMLOGI("NodeProbe: starting platform node detection...");

    probeGpu();
    probeThermal();
    probePower();

    sProbeCompleted = true;
    SMLOGI("NodeProbe: complete");
    dumpResults();
}

const char *NodeProbe::getPath(MetricId id) {
    const uint32_t idx = metricIndex(id);
    if (idx == 0 || idx >= kMetricSlotCount) {
        return nullptr;
    }
    const char *p = sResolvedPaths[idx];
    return (p[0] != '\0') ? p : nullptr;
}

void NodeProbe::dumpResults() {
#ifndef SMON_LOG_DISABLED
    SMLOGI("NodeProbe resolved paths:");

    // GPU
    SMLOGI("  GPU_UTIL    : %s", getPath(MetricId::GPU_UTIL) ?: "(not found)");
    SMLOGI("  GPU_FREQ_CUR: %s", getPath(MetricId::GPU_FREQ_CUR) ?: "(not found)");
    SMLOGI("  GPU_TEMP    : %s", getPath(MetricId::GPU_TEMP) ?: "(not found)");

    // Thermal
    SMLOGI("  CPU_TEMP_C0 : %s", getPath(MetricId::CPU_TEMP_CLUSTER0) ?: "(not found)");
    SMLOGI("  CPU_TEMP_C1 : %s", getPath(MetricId::CPU_TEMP_CLUSTER1) ?: "(not found)");
    SMLOGI("  CPU_TEMP_PRX: %s", getPath(MetricId::CPU_TEMP_PRIME) ?: "(not found)");
    SMLOGI("  SKIN_TEMP   : %s", getPath(MetricId::SKIN_TEMP) ?: "(not found)");
    SMLOGI("  SOC_TEMP    : %s", getPath(MetricId::SOC_TEMP) ?: "(not found)");

    // Power
    SMLOGI("  BAT_CURRENT : %s", getPath(MetricId::BATTERY_CURRENT) ?: "(not found)");
    SMLOGI("  BAT_TEMP    : %s", getPath(MetricId::BATTERY_TEMP) ?: "(not found)");
    SMLOGI("  BAT_LEVEL   : %s", getPath(MetricId::BATTERY_LEVEL) ?: "(not found)");
    SMLOGI("  CHARGER     : %s", getPath(MetricId::CHARGER_ONLINE) ?: "(not found)");
#endif
}

// ---------------------------------------------------------------------------
// Probe groups
// ---------------------------------------------------------------------------

void NodeProbe::probeGpu() {
    SMLOGD("probeGpu: scanning GPU utilization nodes...");

    std::string p;

    p = probeDirectPath(GPU_UTIL_CANDIDATES);
    if (!p.empty()) {
        store(MetricId::GPU_UTIL, p);
        SMLOGI("probeGpu: GPU_UTIL -> %s", p.c_str());
    } else {
        SMLOGW("probeGpu: GPU_UTIL node not found on this device");
    }

    p = probeDirectPath(GPU_FREQ_CANDIDATES);
    if (!p.empty()) {
        store(MetricId::GPU_FREQ_CUR, p);
        SMLOGI("probeGpu: GPU_FREQ_CUR -> %s", p.c_str());
    } else {
        SMLOGW("probeGpu: GPU_FREQ_CUR node not found on this device");
    }
}

void NodeProbe::probeThermal() {
    SMLOGD("probeThermal: scanning thermal zones...");

    struct {
        MetricId id;
        const char *const *candidates;
        const char *name;
    } entries[] = {
        {MetricId::CPU_TEMP_CLUSTER0, CPU_TEMP_CLUSTER0_ZONE_CANDIDATES, "CPU_TEMP_C0"},
        {MetricId::CPU_TEMP_CLUSTER1, CPU_TEMP_CLUSTER1_ZONE_CANDIDATES, "CPU_TEMP_C1"},
        {MetricId::CPU_TEMP_PRIME, CPU_TEMP_PRIME_ZONE_CANDIDATES, "CPU_TEMP_PRIM"},
        {MetricId::GPU_TEMP, GPU_TEMP_ZONE_CANDIDATES, "GPU_TEMP"},
        {MetricId::SKIN_TEMP, SKIN_TEMP_ZONE_CANDIDATES, "SKIN_TEMP"},
        {MetricId::SOC_TEMP, SOC_TEMP_ZONE_CANDIDATES, "SOC_TEMP"},
    };

    for (auto &e : entries) {
        std::string p = probeThermalZone(e.candidates);
        if (!p.empty()) {
            store(e.id, p);
            SMLOGI("probeThermal: %s -> %s", e.name, p.c_str());
        } else {
            SMLOGW("probeThermal: %s zone not found", e.name);
        }
    }
}

void NodeProbe::probePower() {
    SMLOGD("probePower: scanning power supply nodes...");

    struct {
        MetricId id;
        const char *const *candidates;
        const char *name;
    } entries[] = {
        {MetricId::BATTERY_CURRENT, BATTERY_CURRENT_CANDIDATES, "BAT_CURRENT"},
        {MetricId::BATTERY_TEMP, BATTERY_TEMP_CANDIDATES, "BAT_TEMP"},
        {MetricId::BATTERY_LEVEL, BATTERY_LEVEL_CANDIDATES, "BAT_LEVEL"},
        {MetricId::CHARGER_ONLINE, CHARGER_ONLINE_CANDIDATES, "CHARGER"},
    };

    for (auto &e : entries) {
        std::string p = probeDirectPath(e.candidates);
        if (!p.empty()) {
            store(e.id, p);
            SMLOGI("probePower: %s -> %s", e.name, p.c_str());
        } else {
            SMLOGW("probePower: %s node not found", e.name);
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

std::string NodeProbe::probeDirectPath(const char *const *candidates) {
    if (candidates == nullptr)
        return {};
    for (int i = 0; candidates[i] != nullptr; ++i) {
        if (::access(candidates[i], R_OK) == 0) {
            SMLOGD("probeDirectPath: hit [%d] %s", i, candidates[i]);
            return candidates[i];
        }
        SMLOGD("probeDirectPath: miss [%d] %s (errno=%d)", i, candidates[i], errno);
    }
    return {};
}

std::string NodeProbe::probeThermalZone(const char *const *zoneCandidates) {
    if (zoneCandidates == nullptr)
        return {};

    // Enumerate /sys/class/thermal/thermal_zone0 .. thermal_zoneN
    // Typically < 30 zones; scan is fast at init time.
    static constexpr int kMaxZones = 64;
    char typePath[128];
    char typeContent[64];

    for (int zoneIdx = 0; zoneIdx < kMaxZones; ++zoneIdx) {
        // Build type path
        snprintf(typePath, sizeof(typePath), "/sys/class/thermal/thermal_zone%d/type", zoneIdx);

        int fd = ::open(typePath, O_RDONLY | O_CLOEXEC);
        if (fd < 0) {
            // No more zones
            SMLOGD("probeThermalZone: no zone%d, stop scan", zoneIdx);
            break;
        }

        ssize_t n = ::read(fd, typeContent, sizeof(typeContent) - 1);
        ::close(fd);

        if (n <= 0)
            continue;

        // Strip trailing newline
        typeContent[n] = '\0';
        if (n > 0 && typeContent[n - 1] == '\n') {
            typeContent[n - 1] = '\0';
        }

        SMLOGV("probeThermalZone: zone%d type='%s'", zoneIdx, typeContent);

        // Check against all candidates
        for (int c = 0; zoneCandidates[c] != nullptr; ++c) {
            if (strcmp(typeContent, zoneCandidates[c]) == 0) {
                // Found — build the temp path
                char tempPath[128];
                snprintf(tempPath, sizeof(tempPath), "/sys/class/thermal/thermal_zone%d/temp",
                         zoneIdx);

                // Verify the temp node is readable too
                if (::access(tempPath, R_OK) == 0) {
                    SMLOGD("probeThermalZone: matched zone%d type='%s' candidate[%d]='%s'", zoneIdx,
                           typeContent, c, zoneCandidates[c]);
                    return tempPath;
                }
            }
        }
    }
    return {};
}

void NodeProbe::store(MetricId id, const std::string &path) {
    if (path.empty())
        return;
    const uint32_t idx = metricIndex(id);
    if (idx == 0 || idx >= kMetricSlotCount)
        return;

    // Truncate silently if path exceeds buffer (should never happen in practice)
    strncpy(sResolvedPaths[idx], path.c_str(), kMaxPathLen - 1);
    sResolvedPaths[idx][kMaxPathLen - 1] = '\0';
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
