#define LOG_TAG "SMon-Cpu"

#include "ICollector.h"
#include "MetricDef.h"
#include "SysMonLog.h"

#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>
#include <cstdlib>

/**
 * CpuCollector - CPU utilization per cluster + total
 *
 * Data source: /proc/stat (universal procfs, no platform difference)
 *
 * /proc/stat line format:
 *   cpu<N>  user  nice  system  idle  iowait  irq  softirq  steal  guest  guest_nice
 *
 * Utilization = (total_delta - idle_delta) / total_delta * 100
 *
 * Cluster → core mapping (device-specific, configured at build time via cflags):
 *   SMON_CPU_CLUSTER0_CORES="0-3"   little
 *   SMON_CPU_CLUSTER1_CORES="4-6"   big
 *   SMON_CPU_CLUSTER2_CORES="7"     prime
 *
 * If cflags not set, falls back to a 4+3+1 heuristic for 8-core SoCs.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

// ---------------------------------------------------------------------------
// Cluster core range defaults (override via Android.bp cflags if needed)
// ---------------------------------------------------------------------------
#ifndef SMON_CPU_CLUSTER0_FIRST
#define SMON_CPU_CLUSTER0_FIRST 0
#define SMON_CPU_CLUSTER0_LAST  3
#endif
#ifndef SMON_CPU_CLUSTER1_FIRST
#define SMON_CPU_CLUSTER1_FIRST 4
#define SMON_CPU_CLUSTER1_LAST  6
#endif
#ifndef SMON_CPU_CLUSTER2_FIRST
#define SMON_CPU_CLUSTER2_FIRST 7
#define SMON_CPU_CLUSTER2_LAST  7
#endif

// Max CPU cores we track (covers up to cpu15)
static constexpr int kMaxCores = 16;

// ---------------------------------------------------------------------------
// Per-core stat snapshot
// ---------------------------------------------------------------------------
struct CoreStat {
    uint64_t user{0}, nice{0}, system{0}, idle{0},
             iowait{0}, irq{0}, softirq{0}, steal{0};

    uint64_t totalTime() const {
        return user + nice + system + idle + iowait + irq + softirq + steal;
    }
    uint64_t idleTime() const { return idle + iowait; }
};

// ---------------------------------------------------------------------------
// CpuCollector implementation
// ---------------------------------------------------------------------------
class CpuCollector : public ICollector {
public:
    CpuCollector() = default;
    ~CpuCollector() override {
        if (mStatFd >= 0) { ::close(mStatFd); mStatFd = -1; }
    }

    bool init() override {
        // Open /proc/stat once and keep fd open to avoid repeated open/close
        mStatFd = ::open("/proc/stat", O_RDONLY | O_CLOEXEC);
        if (mStatFd < 0) {
            SMLOGE("init: cannot open /proc/stat: errno=%d", errno);
            return false;
        }
        SMLOGI("CpuCollector: /proc/stat fd=%d cluster0=[%d-%d] cluster1=[%d-%d] cluster2=[%d-%d]",
               mStatFd,
               SMON_CPU_CLUSTER0_FIRST, SMON_CPU_CLUSTER0_LAST,
               SMON_CPU_CLUSTER1_FIRST, SMON_CPU_CLUSTER1_LAST,
               SMON_CPU_CLUSTER2_FIRST, SMON_CPU_CLUSTER2_LAST);

        // Perform first read to populate mPrev — first sample will output 0% which is fine
        CoreStat dummy[kMaxCores + 1];
        readStats(dummy);
        memcpy(mPrev, dummy, sizeof(mPrev));
        mAvailable = true;
        return true;
    }

    void sample(const PublishFn& publishFn) override {
        CoreStat cur[kMaxCores + 1];  // index 0 = "cpu" total, 1..N = cpu0..cpuN-1
        if (!readStats(cur)) return;

        struct timespec ts;
        ::clock_gettime(CLOCK_MONOTONIC, &ts);
        const int64_t nowNs = ts.tv_sec * 1'000'000'000LL + ts.tv_nsec;

        // Total CPU utilization (index 0)
        publishFn(MetricId::CPU_UTIL_TOTAL,
                  calcUtil(mPrev[0], cur[0]), nowNs);

        // Per-cluster: average over cores in the cluster
        publishCluster(publishFn, MetricId::CPU_UTIL_CLUSTER0,
                       SMON_CPU_CLUSTER0_FIRST, SMON_CPU_CLUSTER0_LAST,
                       cur, nowNs);
        publishCluster(publishFn, MetricId::CPU_UTIL_CLUSTER1,
                       SMON_CPU_CLUSTER1_FIRST, SMON_CPU_CLUSTER1_LAST,
                       cur, nowNs);
        publishCluster(publishFn, MetricId::CPU_UTIL_CLUSTER2,
                       SMON_CPU_CLUSTER2_FIRST, SMON_CPU_CLUSTER2_LAST,
                       cur, nowNs);

        memcpy(mPrev, cur, sizeof(mPrev));
    }

    int32_t getIntervalMs() const override { return SampleInterval::FAST; }
    const char* getName()   const override { return "CpuCollector"; }
    bool isAvailable()      const override { return mAvailable; }

private:
    /**
     * Read /proc/stat into stats[].
     * stats[0] = aggregate "cpu" line
     * stats[1..N] = individual "cpu0".."cpuN-1" lines
     * Returns false on IO error.
     */
    bool readStats(CoreStat* stats) {
        // Seek to beginning (fd stays open between calls)
        if (::lseek(mStatFd, 0, SEEK_SET) < 0) {
            SMLOGE("lseek /proc/stat failed: errno=%d", errno);
            return false;
        }

        // Read entire file into stack buffer — /proc/stat is typically < 4KB
        char buf[4096];
        ssize_t n = ::read(mStatFd, buf, sizeof(buf) - 1);
        if (n <= 0) {
            SMLOGE("read /proc/stat failed: n=%zd errno=%d", n, errno);
            return false;
        }
        buf[n] = '\0';

        // Parse line by line
        // Zero-init output
        memset(stats, 0, sizeof(CoreStat) * (kMaxCores + 1));

        char* line = buf;
        int   coreIdx = 0;  // 1-based core index (1..kMaxCores)

        while (line && *line) {
            char* next = strchr(line, '\n');
            if (next) *next = '\0';

            if (strncmp(line, "cpu", 3) == 0) {
                const char* numStart = line + 3;
                if (*numStart == ' ' || *numStart == '\t') {
                    // Aggregate "cpu " line → index 0
                    parseLine(line + 3, &stats[0]);
                } else {
                    // "cpuN" line
                    int cpuNum = atoi(numStart);
                    if (cpuNum >= 0 && cpuNum < kMaxCores) {
                        // Skip past the number to the stats
                        const char* p = numStart;
                        while (*p && *p != ' ') ++p;
                        parseLine(p, &stats[cpuNum + 1]);
                        SMLOGV("cpu%d: user=%llu idle=%llu",
                               cpuNum,
                               (unsigned long long)stats[cpuNum+1].user,
                               (unsigned long long)stats[cpuNum+1].idle);
                    }
                }
            } else if (strncmp(line, "intr", 4) == 0) {
                // Past CPU lines, stop early
                break;
            }

            line = next ? (next + 1) : nullptr;
            (void)coreIdx;
        }
        return true;
    }

    static void parseLine(const char* p, CoreStat* s) {
        // Format: " user nice system idle iowait irq softirq steal ..."
        sscanf(p, " %llu %llu %llu %llu %llu %llu %llu %llu",
               (unsigned long long*)&s->user,
               (unsigned long long*)&s->nice,
               (unsigned long long*)&s->system,
               (unsigned long long*)&s->idle,
               (unsigned long long*)&s->iowait,
               (unsigned long long*)&s->irq,
               (unsigned long long*)&s->softirq,
               (unsigned long long*)&s->steal);
    }

    /** Calculate utilization percentage (0-100) between two snapshots. */
    static int64_t calcUtil(const CoreStat& prev, const CoreStat& cur) {
        uint64_t totalDelta = cur.totalTime() - prev.totalTime();
        uint64_t idleDelta  = cur.idleTime()  - prev.idleTime();
        if (totalDelta == 0) return 0;
        uint64_t busyDelta = (totalDelta > idleDelta) ? (totalDelta - idleDelta) : 0;
        return static_cast<int64_t>((busyDelta * 100ULL) / totalDelta);
    }

    void publishCluster(const PublishFn& publishFn, MetricId id,
                        int first, int last,
                        const CoreStat* cur, int64_t nowNs) {
        // Aggregate cores in range [first, last] for cluster average
        CoreStat clusterPrev{}, clusterCur{};
        for (int c = first; c <= last && c < kMaxCores; ++c) {
            const CoreStat& p = mPrev[c + 1];
            const CoreStat& n = cur[c + 1];
            clusterPrev.user    += p.user;    clusterCur.user    += n.user;
            clusterPrev.nice    += p.nice;    clusterCur.nice    += n.nice;
            clusterPrev.system  += p.system;  clusterCur.system  += n.system;
            clusterPrev.idle    += p.idle;    clusterCur.idle    += n.idle;
            clusterPrev.iowait  += p.iowait;  clusterCur.iowait  += n.iowait;
            clusterPrev.irq     += p.irq;     clusterCur.irq     += n.irq;
            clusterPrev.softirq += p.softirq; clusterCur.softirq += n.softirq;
            clusterPrev.steal   += p.steal;   clusterCur.steal   += n.steal;
        }
        int64_t util = calcUtil(clusterPrev, clusterCur);
        SMLOGD("cluster[%d-%d] util=%lld%%", first, last, (long long)util);
        publishFn(id, util, nowNs);
    }

    int      mStatFd    = -1;
    bool     mAvailable = false;
    CoreStat mPrev[kMaxCores + 1];  // Previous snapshot for delta calculation
};

// ---------------------------------------------------------------------------
// Factory function — used by SysMonitor to instantiate
// ---------------------------------------------------------------------------
ICollector* createCpuCollector() {
    return new CpuCollector();
}

}  // namespace sysmonitor
}  // namespace transsion
}  // namespace vendor
