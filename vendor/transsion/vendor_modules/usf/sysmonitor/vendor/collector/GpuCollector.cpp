#define LOG_TAG "SMon-Gpu"

#include <fcntl.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>

#include "ICollector.h"
#include "MetricDef.h"
#include "NodeProbe.h"
#include "SysMonLog.h"

/**
 * GpuCollector - GPU utilization and current frequency
 *
 * Metrics: GPU_UTIL (%), GPU_FREQ_CUR (Hz)
 *
 * Paths are resolved by NodeProbe at init time; GpuCollector
 * just reads the cached fd — no platform #if needed here.
 *
 * Node format differences (handled transparently):
 *   QCOM kgsl gpu_busy_percentage : "42\n"        (integer percent)
 *   QCOM kgsl devfreq gpu_load    : "42\n"        (integer percent)
 *   MTK  ged  gpu_utilization     : "42 ...\n"    (first token percent)
 *   QCOM gpuclk                   : "587000000\n" (Hz)
 *   MTK  current_freqency         : "587000000\n" (Hz)
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

class GpuCollector : public ICollector {
public:
    GpuCollector() = default;
    ~GpuCollector() override { closeFds(); }

    bool init() override {
        // NodeProbe has already run — just look up cached paths
        const char *utilPath = NodeProbe::getPath(MetricId::GPU_UTIL);
        const char *freqPath = NodeProbe::getPath(MetricId::GPU_FREQ_CUR);

        if (utilPath) {
            mUtilFd = ::open(utilPath, O_RDONLY | O_CLOEXEC);
            if (mUtilFd < 0) {
                SMLOGW("init: open GPU_UTIL '%s' failed: errno=%d", utilPath, errno);
            } else {
                SMLOGI("GPU_UTIL: %s fd=%d", utilPath, mUtilFd);
            }
        } else {
            SMLOGW("init: GPU_UTIL node not probed (unsupported platform?)");
        }

        if (freqPath) {
            mFreqFd = ::open(freqPath, O_RDONLY | O_CLOEXEC);
            if (mFreqFd < 0) {
                SMLOGW("init: open GPU_FREQ '%s' failed: errno=%d", freqPath, errno);
            } else {
                SMLOGI("GPU_FREQ_CUR: %s fd=%d", freqPath, mFreqFd);
            }
        } else {
            SMLOGW("init: GPU_FREQ_CUR node not probed");
        }

        mAvailable = (mUtilFd >= 0 || mFreqFd >= 0);
        if (!mAvailable) {
            SMLOGE("init: no GPU nodes available, collector disabled");
        }
        return mAvailable;
    }

    void sample(const PublishFn &publishFn) override {
        struct timespec ts;
        ::clock_gettime(CLOCK_MONOTONIC, &ts);
        const int64_t nowNs = ts.tv_sec * 1'000'000'000LL + ts.tv_nsec;

        if (mUtilFd >= 0) {
            int64_t util = readFirstInt(mUtilFd);
            if (util >= 0) {
                SMLOGD("GPU_UTIL=%lld%%", (long long)util);
                publishFn(MetricId::GPU_UTIL, util, nowNs);
            }
        }
        if (mFreqFd >= 0) {
            int64_t freq = readFirstInt(mFreqFd);
            if (freq >= 0) {
                SMLOGD("GPU_FREQ_CUR=%lldHz", (long long)freq);
                publishFn(MetricId::GPU_FREQ_CUR, freq, nowNs);
            }
        }
    }

    int32_t getIntervalMs() const override { return SampleInterval::FAST; }
    const char *getName() const override { return "GpuCollector"; }
    bool isAvailable() const override { return mAvailable; }

private:
    /**
     * Read first integer token from a sysfs fd.
     * Re-seeks to 0 before each read so fd stays reusable.
     * Returns -1 on error.
     */
    static int64_t readFirstInt(int fd) {
        if (::lseek(fd, 0, SEEK_SET) < 0)
            return -1;
        char buf[32];
        ssize_t n = ::read(fd, buf, sizeof(buf) - 1);
        if (n <= 0)
            return -1;
        buf[n] = '\0';
        return (int64_t)strtoll(buf, nullptr, 10);
    }

    void closeFds() {
        if (mUtilFd >= 0) {
            ::close(mUtilFd);
            mUtilFd = -1;
        }
        if (mFreqFd >= 0) {
            ::close(mFreqFd);
            mFreqFd = -1;
        }
    }

    int mUtilFd = -1;
    int mFreqFd = -1;
    bool mAvailable = false;
};

ICollector *createGpuCollector() {
    return new GpuCollector();
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
