#define LOG_TAG "SMon-Mem"

#include <fcntl.h>
#include <unistd.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "ICollector.h"
#include "MetricDef.h"
#include "SysMonLog.h"

/**
 * MemCollector - Memory and PSI pressure metrics
 *
 * Sources:
 *   /proc/meminfo  → MEM_FREE / MEM_AVAILABLE / MEM_CACHED / MEM_SWAP_USED
 *   /proc/pressure/memory → MEM_PRESSURE (PSI some_avg10, percent * 100)
 *   /proc/pressure/io     → IO_PRESSURE  (PSI some_avg10, percent * 100)
 *
 * PSI value is stored as integer percent*100 (e.g. 12.34% → 1234)
 * to avoid floating point on the hot path.
 *
 * All nodes are universal procfs — no platform difference.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

class MemCollector : public ICollector {
public:
    MemCollector() = default;
    ~MemCollector() override { closeFds(); }

    bool init() override {
        mMemFd = ::open("/proc/meminfo", O_RDONLY | O_CLOEXEC);
        if (mMemFd < 0) {
            SMLOGE("init: cannot open /proc/meminfo: errno=%d", errno);
            return false;
        }

        // PSI nodes available since kernel 4.20 / Android 10 — optional
        mMemPsiFd = ::open("/proc/pressure/memory", O_RDONLY | O_CLOEXEC);
        if (mMemPsiFd < 0) {
            SMLOGW("init: /proc/pressure/memory not available (kernel < 4.20?)");
        }
        mIoPsiFd = ::open("/proc/pressure/io", O_RDONLY | O_CLOEXEC);
        if (mIoPsiFd < 0) {
            SMLOGW("init: /proc/pressure/io not available");
        }

        SMLOGI("MemCollector: memFd=%d psiMemFd=%d psiIoFd=%d", mMemFd, mMemPsiFd, mIoPsiFd);
        mAvailable = true;
        return true;
    }

    void sample(const PublishFn &publishFn) override {
        struct timespec ts;
        ::clock_gettime(CLOCK_MONOTONIC, &ts);
        const int64_t nowNs = ts.tv_sec * 1'000'000'000LL + ts.tv_nsec;

        sampleMeminfo(publishFn, nowNs);
        if (mMemPsiFd >= 0)
            samplePsi(mMemPsiFd, MetricId::MEM_PRESSURE, "mem", publishFn, nowNs);
        if (mIoPsiFd >= 0)
            samplePsi(mIoPsiFd, MetricId::IO_PRESSURE, "io", publishFn, nowNs);
    }

    int32_t getIntervalMs() const override { return SampleInterval::SLOW; }
    const char *getName() const override { return "MemCollector"; }
    bool isAvailable() const override { return mAvailable; }

private:
    void sampleMeminfo(const PublishFn &publishFn, int64_t nowNs) {
        if (::lseek(mMemFd, 0, SEEK_SET) < 0)
            return;

        char buf[2048];
        ssize_t n = ::read(mMemFd, buf, sizeof(buf) - 1);
        if (n <= 0)
            return;
        buf[n] = '\0';

        int64_t memFree = 0, memAvail = 0, memCached = 0;
        int64_t swapTotal = 0, swapFree = 0;

        char *line = buf;
        while (line && *line) {
            char *next = strchr(line, '\n');
            if (next)
                *next = '\0';

            // Each line: "KeyName:    <value> kB"
            if (strncmp(line, "MemFree:", 8) == 0)
                memFree = parseKb(line);
            else if (strncmp(line, "MemAvailable:", 13) == 0)
                memAvail = parseKb(line);
            else if (strncmp(line, "Cached:", 7) == 0)
                memCached = parseKb(line);
            else if (strncmp(line, "SwapTotal:", 10) == 0)
                swapTotal = parseKb(line);
            else if (strncmp(line, "SwapFree:", 9) == 0)
                swapFree = parseKb(line);

            line = next ? next + 1 : nullptr;
        }

        int64_t swapUsed = swapTotal - swapFree;
        SMLOGD("mem: free=%lldkB avail=%lldkB cached=%lldkB swapUsed=%lldkB", (long long)memFree,
               (long long)memAvail, (long long)memCached, (long long)swapUsed);

        publishFn(MetricId::MEM_FREE, memFree, nowNs);
        publishFn(MetricId::MEM_AVAILABLE, memAvail, nowNs);
        publishFn(MetricId::MEM_CACHED, memCached, nowNs);
        publishFn(MetricId::MEM_SWAP_USED, swapUsed, nowNs);
    }

    void samplePsi(int fd, MetricId id, const char *name, const PublishFn &publishFn,
                   int64_t nowNs) {
        if (::lseek(fd, 0, SEEK_SET) < 0)
            return;

        char buf[256];
        ssize_t n = ::read(fd, buf, sizeof(buf) - 1);
        if (n <= 0)
            return;
        buf[n] = '\0';

        // PSI format: "some avg10=0.12 avg60=0.34 avg300=0.56 total=...\n"
        // Extract avg10 from "some" line (first line)
        // Stored as integer: 0.12% → 12 (percent * 100)
        const char *p = strstr(buf, "some avg10=");
        if (!p)
            return;
        p += strlen("some avg10=");

        // Parse float without using strtof to avoid locale issues
        int intPart = 0, fracPart = 0, fracDiv = 1;
        sscanf(p, "%d.%d", &intPart, &fracPart);
        // Determine decimal places in fracPart
        int tmp = fracPart;
        while (tmp >= 10) {
            tmp /= 10;
            fracDiv *= 10;
        }
        // Convert to percent*100: e.g. "12.34" → 1234
        int64_t val = (int64_t)intPart * 100LL + (int64_t)(fracPart * 100 / fracDiv);

        SMLOGD("psi %s: some_avg10=%lld (percent*100)", name, (long long)val);
        publishFn(id, val, nowNs);
    }

    static int64_t parseKb(const char *line) {
        // Skip key name and colon
        const char *p = strchr(line, ':');
        if (!p)
            return 0;
        return (int64_t)strtoll(p + 1, nullptr, 10);
    }

    void closeFds() {
        if (mMemFd >= 0) {
            ::close(mMemFd);
            mMemFd = -1;
        }
        if (mMemPsiFd >= 0) {
            ::close(mMemPsiFd);
            mMemPsiFd = -1;
        }
        if (mIoPsiFd >= 0) {
            ::close(mIoPsiFd);
            mIoPsiFd = -1;
        }
    }

    int mMemFd = -1;
    int mMemPsiFd = -1;
    int mIoPsiFd = -1;
    bool mAvailable = false;
};

ICollector *createMemCollector() {
    return new MemCollector();
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
