#pragma once

#include <cstdint>
#include <functional>
#include "MetricDef.h"

/**
 * ICollector.h - Abstract base class for all metric collectors
 *
 * Each collector is responsible for:
 *   1. init()  — probe sysfs/procfs nodes once at startup (not on hot path)
 *   2. sample() — read current values and store into MetricStore
 *   3. getIntervalMs() — report preferred sample interval
 *
 * CollectorManager calls sample() on a unified background thread;
 * collectors must NOT create their own threads.
 *
 * Thread safety: sample() is called only from CollectorManager's
 * sampling thread. init() is called once before sampling starts.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

// Callback signature: collector calls this to publish a sampled value.
// CollectorManager injects this during registration so collectors
// remain decoupled from MetricStore internals.
using PublishFn = std::function<void(MetricId id, int64_t value, int64_t timestampNs)>;

// ---------------------------------------------------------------------------
// ICollector
// ---------------------------------------------------------------------------
class ICollector {
public:
    virtual ~ICollector() = default;

    /**
     * Initialize the collector.
     * Called once before the sampling loop starts.
     * Performs node probing, file descriptor caching, etc.
     *
     * @return true if at least one node is usable; false if collector
     *         should be skipped entirely (e.g., no GPU node found).
     */
    virtual bool init() = 0;

    /**
     * Perform one sampling cycle.
     * Must be non-blocking and complete quickly (target < 1 ms).
     * Use the injected publishFn to report each value.
     *
     * @param publishFn  Callback to store each MetricId/value pair.
     */
    virtual void sample(const PublishFn& publishFn) = 0;

    /**
     * Preferred sample interval in milliseconds.
     * CollectorManager uses this to group collectors into timer buckets.
     */
    virtual int32_t getIntervalMs() const = 0;

    /**
     * Human-readable name for logging / debug dumps.
     */
    virtual const char* getName() const = 0;

    /**
     * Whether this collector is currently usable.
     * Returns false if init() failed or all probed nodes are missing.
     */
    virtual bool isAvailable() const = 0;
};

}  // namespace sysmonitor
}  // namespace transsion
}  // namespace vendor
