#define LOG_TAG "SMon-Therm"

#include <fcntl.h>
#include <unistd.h>

#include <cstdlib>

#include "ICollector.h"
#include "MetricDef.h"
#include "NodeProbe.h"
#include "SysMonLog.h"

/**
 * ThermalCollector - CPU cluster, GPU, skin, SoC temperature
 *
 * Metrics:
 *   CPU_TEMP_CLUSTER0/1/PRIME  — milli-celsius
 *   GPU_TEMP                   — milli-celsius
 *   SKIN_TEMP                  — milli-celsius
 *   SOC_TEMP                   — milli-celsius
 *
 * All paths resolved by NodeProbe at init; no platform #if here.
 *
 * /sys/class/thermal/thermal_zoneN/temp values:
 *   Most kernels: milli-celsius directly (e.g. "45000" = 45°C)
 *   Some legacy: celsius integer (e.g. "45" = 45°C)
 *   Normalization: if raw value < 1000, multiply by 1000.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

// Map from MetricId → open fd (index in kSlots array)
struct ThermalSlot {
    MetricId id;
    const char *name;    // for logging
    int fd;
};

class ThermalCollector : public ICollector {
public:
    ThermalCollector() {
        for (auto &s : mSlots)
            s.fd = -1;
    }
    ~ThermalCollector() override {
        for (auto &s : mSlots) {
            if (s.fd >= 0) {
                ::close(s.fd);
                s.fd = -1;
            }
        }
    }

    bool init() override {
        int opened = 0;
        for (auto &s : mSlots) {
            const char *path = NodeProbe::getPath(s.id);
            if (!path) {
                SMLOGW("init: %s zone path not found", s.name);
                continue;
            }
            s.fd = ::open(path, O_RDONLY | O_CLOEXEC);
            if (s.fd < 0) {
                SMLOGW("init: open '%s' failed: errno=%d", path, errno);
            } else {
                SMLOGI("%-18s: %s fd=%d", s.name, path, s.fd);
                ++opened;
            }
        }
        mAvailable = (opened > 0);
        if (!mAvailable)
            SMLOGE("init: no thermal nodes available");
        return mAvailable;
    }

    void sample(const PublishFn &publishFn) override {
        struct timespec ts;
        ::clock_gettime(CLOCK_MONOTONIC, &ts);
        const int64_t nowNs = ts.tv_sec * 1'000'000'000LL + ts.tv_nsec;

        for (const auto &s : mSlots) {
            if (s.fd < 0)
                continue;
            int64_t val = readMilliCelsius(s.fd);
            if (val == kReadError)
                continue;
            SMLOGD("%s = %lld mC", s.name, (long long)val);
            publishFn(s.id, val, nowNs);
        }
    }

    int32_t getIntervalMs() const override { return SampleInterval::MEDIUM; }
    const char *getName() const override { return "ThermalCollector"; }
    bool isAvailable() const override { return mAvailable; }

private:
    static constexpr int64_t kReadError = INT64_MIN;

    /**
     * Read thermal node and normalize to milli-celsius.
     * Thermal zone /temp values < 1000 are assumed to be plain Celsius.
     */
    static int64_t readMilliCelsius(int fd) {
        if (::lseek(fd, 0, SEEK_SET) < 0)
            return kReadError;
        char buf[24];
        ssize_t n = ::read(fd, buf, sizeof(buf) - 1);
        if (n <= 0)
            return kReadError;
        buf[n] = '\0';
        int64_t raw = strtoll(buf, nullptr, 10);
        // Normalize: values like "45" → treat as Celsius → 45000 mC
        return (raw >= -300 && raw <= 300) ? raw * 1000 : raw;
    }

    // Slot table — all thermal metrics in one array for uniform handling
    ThermalSlot mSlots[6] = {
        {MetricId::CPU_TEMP_CLUSTER0, "CPU_TEMP_C0", -1},
        {MetricId::CPU_TEMP_CLUSTER1, "CPU_TEMP_C1", -1},
        {MetricId::CPU_TEMP_PRIME, "CPU_TEMP_PRIME", -1},
        {MetricId::GPU_TEMP, "GPU_TEMP", -1},
        {MetricId::SKIN_TEMP, "SKIN_TEMP", -1},
        {MetricId::SOC_TEMP, "SOC_TEMP", -1},
    };
    bool mAvailable = false;
};

ICollector *createThermalCollector() {
    return new ThermalCollector();
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
