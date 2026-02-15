#define LOG_TAG "SMon-ColMgr"

#include "CollectorManager.h"
#include "SysMonLog.h"
#include "NodeProbe.h"

#include <sched.h>
#include <sys/resource.h>
#include <time.h>
#include <cstring>

namespace vendor {
namespace transsion {
namespace sysmonitor {

// ---------------------------------------------------------------------------
// Constructor / Destructor
// ---------------------------------------------------------------------------

CollectorManager::CollectorManager(MetricStore* store)
    : mStore(store) {
    SMLOGD("CollectorManager created");
}

CollectorManager::~CollectorManager() {
    stop();
    SMLOGD("CollectorManager destroyed");
}

// ---------------------------------------------------------------------------
// Registration
// ---------------------------------------------------------------------------

void CollectorManager::registerCollector(ICollector* collector) {
    if (collector == nullptr) {
        SMLOGW("registerCollector: null collector, skip");
        return;
    }
    mCollectors.push_back(collector);
    SMLOGD("Registered collector: %s (interval=%dms)",
           collector->getName(), collector->getIntervalMs());
}

// ---------------------------------------------------------------------------
// Init
// ---------------------------------------------------------------------------

int CollectorManager::initCollectors() {
    // Step 1: run NodeProbe once for all platform-specific nodes
    SMLOGI("initCollectors: running NodeProbe...");
    NodeProbe::probeAll();

    // Step 2: init each collector
    int successCount = 0;
    for (ICollector* c : mCollectors) {
        SMLOGD("init collector: %s", c->getName());
        bool ok = c->init();
        if (ok) {
            ++successCount;
            SMLOGI("  [OK] %s (interval=%dms)", c->getName(), c->getIntervalMs());
        } else {
            SMLOGW("  [SKIP] %s: init failed, collector unavailable", c->getName());
        }
    }

    SMLOGI("initCollectors: %d/%zu collectors ready",
           successCount, mCollectors.size());
    return successCount;
}

// ---------------------------------------------------------------------------
// Start / Stop
// ---------------------------------------------------------------------------

bool CollectorManager::start() {
    if (mRunning.load(std::memory_order_acquire)) {
        SMLOGW("start: sampling thread already running");
        return true;
    }
    if (mStore == nullptr || !mStore->isReady()) {
        SMLOGE("start: MetricStore not ready");
        return false;
    }

    mStopRequested.store(false, std::memory_order_relaxed);
    mRunning.store(true, std::memory_order_release);

    mThread = std::thread(&CollectorManager::samplingLoop, this);

    SMLOGI("Sampling thread started (%zu collectors, tick=%dms)",
           mCollectors.size(), kBaseTickMs);
    return true;
}

void CollectorManager::stop() {
    if (!mRunning.load(std::memory_order_acquire)) {
        return;
    }
    SMLOGI("Stopping sampling thread...");
    mStopRequested.store(true, std::memory_order_release);
    if (mThread.joinable()) {
        mThread.join();
    }
    mRunning.store(false, std::memory_order_relaxed);
    SMLOGI("Sampling thread stopped");
}

// ---------------------------------------------------------------------------
// Sampling loop
// ---------------------------------------------------------------------------

void CollectorManager::samplingLoop() {
    SMLOGI("samplingLoop: enter, tid=%d", ::gettid());

    // Lower the thread priority slightly to avoid competing with UI threads.
    // SCHED_BATCH: CPU-bound work, yields to interactive threads.
    struct sched_param param = { .sched_priority = 0 };
    if (::sched_setscheduler(0, SCHED_BATCH, &param) != 0) {
        SMLOGW("sched_setscheduler SCHED_BATCH failed: errno=%d", errno);
    }
    // Nice value +5: cooperative de-prioritization
    ::setpriority(PRIO_PROCESS, 0, 5);

    // Build per-collector tick counters.
    // countdown[i] = how many base ticks until collector[i] fires next.
    const size_t n = mCollectors.size();
    std::vector<int32_t> countdown(n, 0);  // Start at 0 → fire on first tick

    // Pre-build the PublishFn closure once (heap alloc only here, not hot path)
    MetricStore* store = mStore;
    PublishFn publishFn = [store](MetricId id, int64_t value, int64_t ts) {
        store->publish(id, value, ts);
    };

    const int64_t tickIntervalNs =
        static_cast<int64_t>(kBaseTickMs) * 1'000'000LL;

    // Compute first tick boundary
    struct timespec ts;
    ::clock_gettime(CLOCK_MONOTONIC, &ts);
    int64_t nextNs = nextTickNs(
        ts.tv_sec * 1'000'000'000LL + ts.tv_nsec, tickIntervalNs);

    while (!mStopRequested.load(std::memory_order_acquire)) {
        // Sleep until the next tick boundary (drift-corrected)
        struct timespec sleepTs;
        sleepTs.tv_sec  = nextNs / 1'000'000'000LL;
        sleepTs.tv_nsec = nextNs % 1'000'000'000LL;

        // clock_nanosleep: restarts from remaining time if interrupted by signal
        int rc;
        do {
            rc = ::clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &sleepTs, nullptr);
        } while (rc == EINTR && !mStopRequested.load(std::memory_order_acquire));

        if (mStopRequested.load(std::memory_order_acquire)) break;

        // Get current timestamp once — shared by all collectors this tick
        struct timespec nowTs;
        ::clock_gettime(CLOCK_MONOTONIC, &nowTs);
        const int64_t nowNs =
            nowTs.tv_sec * 1'000'000'000LL + nowTs.tv_nsec;

        // Fire collectors whose countdown has expired
        for (size_t i = 0; i < n; ++i) {
            ICollector* c = mCollectors[i];
            if (!c->isAvailable()) continue;

            if (countdown[i] <= 0) {
                // Reset countdown for this collector
                // Round interval to nearest base-tick multiple
                int32_t ticks = c->getIntervalMs() / kBaseTickMs;
                if (ticks < 1) ticks = 1;
                countdown[i] = ticks;

                SMLOGV("tick: fire collector '%s'", c->getName());
                c->sample(publishFn);
            }
            --countdown[i];
        }

        // Advance to the next tick boundary
        nextNs = nextTickNs(nowNs, tickIntervalNs);
    }

    SMLOGI("samplingLoop: exit");
}

// ---------------------------------------------------------------------------
// Tick alignment helper
// ---------------------------------------------------------------------------

int64_t CollectorManager::nextTickNs(int64_t nowNs, int64_t tickIntervalNs) {
    // Align to the next multiple of tickIntervalNs on the monotonic clock.
    // This prevents drift accumulation over many ticks.
    int64_t elapsed = nowNs % tickIntervalNs;
    return nowNs + (tickIntervalNs - elapsed);
}

}  // namespace sysmonitor
}  // namespace transsion
}  // namespace vendor
