#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <thread>
#include <vector>

#include "ICollector.h"
#include "MetricStore.h"

/**
 * CollectorManager.h - Unified sampling thread scheduler
 *
 * All collectors share ONE background thread. Each collector declares
 * its preferred interval (FAST=200ms / MEDIUM=500ms / SLOW=1000ms / VSLOW=5000ms).
 * The manager runs a tick loop at the GCD of all intervals (200ms) and
 * fires each collector when its own countdown reaches zero.
 *
 * Thread model:
 *   - One std::thread created in start(), joined in stop().
 *   - Collectors must NOT spawn their own threads.
 *   - JavaBridgeCollector is push-based (JNI calls publish directly);
 *     its sample() is a no-op; it is registered only for lifecycle mgmt.
 *
 * Performance:
 *   - No dynamic allocation on the hot path.
 *   - publish lambda captures MetricStore* by pointer (stack, no heap).
 *   - Tick sleep uses clock_nanosleep(CLOCK_MONOTONIC) for drift correction.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

class CollectorManager {
public:
    explicit CollectorManager(MetricStore *store);
    ~CollectorManager();

    // Prevent copy
    CollectorManager(const CollectorManager &) = delete;
    CollectorManager &operator=(const CollectorManager &) = delete;

    /**
     * Register a collector. Must be called before start().
     * Ownership is NOT transferred; caller manages lifetime.
     * (In practice all collectors live as long as the process.)
     */
    void registerCollector(ICollector *collector);

    /**
     * Initialize all registered collectors (calls init() on each).
     * Collectors that return false from init() are marked unavailable
     * and skipped during sampling — no removal, logged as WARNING.
     * Must be called before start().
     *
     * @return Number of collectors successfully initialized (>= 0)
     */
    int initCollectors();

    /**
     * Start the sampling background thread.
     * Requires initCollectors() to have been called.
     * @return true if thread started successfully
     */
    bool start();

    /**
     * Stop the sampling thread and join it.
     * Safe to call multiple times.
     */
    void stop();

    /** Returns true if the sampling thread is running. */
    bool isRunning() const { return mRunning.load(std::memory_order_acquire); }

private:
    /** Main loop executed on the sampling thread. */
    void samplingLoop();

    /**
     * Compute CLOCK_MONOTONIC nanoseconds for the next tick boundary.
     * Uses the base tick interval (kBaseTickMs) aligned to wall clock
     * to minimize drift accumulation.
     */
    static int64_t nextTickNs(int64_t nowNs, int64_t tickIntervalNs);

    // Base tick: GCD of all sample intervals = 200ms
    static constexpr int32_t kBaseTickMs = 200;

    MetricStore *mStore;
    std::vector<ICollector *> mCollectors;
    std::thread mThread;
    std::atomic<bool> mRunning{false};
    std::atomic<bool> mStopRequested{false};
};

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
