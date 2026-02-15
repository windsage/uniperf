#define LOG_TAG "SMon-Pwr"

#include <fcntl.h>
#include <unistd.h>

#include <cstdlib>

#include "ICollector.h"
#include "MetricDef.h"
#include "NodeProbe.h"
#include "SysMonLog.h"

/**
 * PowerCollector - Battery and charger metrics
 *
 * Metrics:
 *   BATTERY_CURRENT  — microampere (negative = discharging on most platforms)
 *   BATTERY_TEMP     — milli-celsius
 *   BATTERY_LEVEL    — percent 0-100
 *   CHARGER_ONLINE   — 0 or 1
 *
 * Paths resolved by NodeProbe at init.
 *
 * Sample intervals: CURRENT/TEMP at SLOW(1s), LEVEL/CHARGER at VSLOW(5s).
 * Since CollectorManager fires this collector at SLOW(1s), LEVEL and CHARGER
 * are sub-sampled internally via mVSlowCounter.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

class PowerCollector : public ICollector {
public:
    PowerCollector() = default;
    ~PowerCollector() override { closeFds(); }

    bool init() override {
        // Open each node if probed; missing nodes are silently skipped
        openNode(MetricId::BATTERY_CURRENT, "BAT_CURRENT", mCurrentFd);
        openNode(MetricId::BATTERY_TEMP, "BAT_TEMP", mTempFd);
        openNode(MetricId::BATTERY_LEVEL, "BAT_LEVEL", mLevelFd);
        openNode(MetricId::CHARGER_ONLINE, "CHARGER", mChargerFd);

        mAvailable = (mCurrentFd >= 0 || mTempFd >= 0 || mLevelFd >= 0 || mChargerFd >= 0);
        if (!mAvailable)
            SMLOGE("init: no power supply nodes available");
        return mAvailable;
    }

    void sample(const PublishFn &publishFn) override {
        struct timespec ts;
        ::clock_gettime(CLOCK_MONOTONIC, &ts);
        const int64_t nowNs = ts.tv_sec * 1'000'000'000LL + ts.tv_nsec;

        // SLOW metrics: sample every tick (1s)
        if (mCurrentFd >= 0) {
            int64_t v = readInt(mCurrentFd);
            if (v != kErr) {
                SMLOGD("BAT_CURRENT=%lld uA", (long long)v);
                publishFn(MetricId::BATTERY_CURRENT, v, nowNs);
            }
        }
        if (mTempFd >= 0) {
            int64_t v = readMilliCelsius(mTempFd);
            if (v != kErr) {
                SMLOGD("BAT_TEMP=%lld mC", (long long)v);
                publishFn(MetricId::BATTERY_TEMP, v, nowNs);
            }
        }

        // VSLOW metrics: sub-sample at 1/5 of SLOW ticks (every ~5s)
        ++mVSlowCounter;
        if (mVSlowCounter >= kVSlowEvery) {
            mVSlowCounter = 0;
            if (mLevelFd >= 0) {
                int64_t v = readInt(mLevelFd);
                if (v != kErr) {
                    SMLOGD("BAT_LEVEL=%lld%%", (long long)v);
                    publishFn(MetricId::BATTERY_LEVEL, v, nowNs);
                }
            }
            if (mChargerFd >= 0) {
                int64_t v = readInt(mChargerFd);
                if (v != kErr) {
                    SMLOGD("CHARGER_ONLINE=%lld", (long long)v);
                    publishFn(MetricId::CHARGER_ONLINE, v, nowNs);
                }
            }
        }
    }

    // Collector fires at SLOW; VSLOW handled internally via counter
    int32_t getIntervalMs() const override { return SampleInterval::SLOW; }
    const char *getName() const override { return "PowerCollector"; }
    bool isAvailable() const override { return mAvailable; }

private:
    static constexpr int64_t kErr = INT64_MIN;
    // VSLOW = 5s = 5 ticks of SLOW(1s)
    static constexpr int kVSlowEvery = SampleInterval::VSLOW / SampleInterval::SLOW;

    void openNode(MetricId id, const char *name, int &fd) {
        const char *path = NodeProbe::getPath(id);
        if (!path) {
            SMLOGW("init: %s path not found", name);
            return;
        }
        fd = ::open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0)
            SMLOGW("init: open '%s' failed: errno=%d", path, errno);
        else
            SMLOGI("%-14s: %s fd=%d", name, path, fd);
    }

    static int64_t readInt(int fd) {
        if (::lseek(fd, 0, SEEK_SET) < 0)
            return kErr;
        char buf[24];
        ssize_t n = ::read(fd, buf, sizeof(buf) - 1);
        if (n <= 0)
            return kErr;
        buf[n] = '\0';
        return strtoll(buf, nullptr, 10);
    }

    static int64_t readMilliCelsius(int fd) {
        int64_t raw = readInt(fd);
        if (raw == kErr)
            return kErr;
        // Battery temp nodes: some report in 0.1°C (e.g. "298" = 29.8°C),
        // some in milli-celsius (e.g. "29800"). Heuristic: < 1000 → ×100 (0.1°C→mC)
        if (raw > -3000 && raw < 3000)
            return raw * 100;    // 0.1°C units
        return raw;              // already milli-celsius
    }

    void closeFds() {
        for (int *fd : {&mCurrentFd, &mTempFd, &mLevelFd, &mChargerFd}) {
            if (*fd >= 0) {
                ::close(*fd);
                *fd = -1;
            }
        }
    }

    int mCurrentFd = -1;
    int mTempFd = -1;
    int mLevelFd = -1;
    int mChargerFd = -1;
    bool mAvailable = false;
    int mVSlowCounter = 0;
};

ICollector *createPowerCollector() {
    return new PowerCollector();
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
