#pragma once

#include <aidl/vendor/transsion/hardware/sysmonitor/IMetricListener.h>
#include <android/binder_auto_utils.h>

#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "MetricDef.h"
#include "SysMonLog.h"

/**
 * DispatchManager.h - Subscription and threshold-based callback dispatch
 *
 * Called by CollectorManager (via publish hook) after each MetricStore::publish().
 * Checks registered subscriptions and fires IMetricListener callbacks when:
 *   1. threshold > 0: value crossed the threshold (rising edge only)
 *   2. threshold == 0: every new sample (periodic update)
 *
 * Threading:
 *   - notify() called from the sampling thread (hot path — fast path must be cheap)
 *   - registerListener/unregisterListener called from Binder thread pool
 *   - Uses a std::mutex protecting the subscription table; held briefly
 *     (snapshot copy on notify to minimize lock hold time)
 *
 * Binder death:
 *   Links each listener to AIBinder_DeathRecipient; removes dead listeners
 *   automatically without waiting for explicit unregister.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

using IMetricListener = ::aidl::vendor::transsion::hardware::sysmonitor::IMetricListener;

// ---------------------------------------------------------------------------
// Subscription entry
// ---------------------------------------------------------------------------
struct Subscription {
    std::shared_ptr<IMetricListener> listener;
    std::unordered_set<uint32_t> metricIds;              // empty = all metrics
    std::unordered_map<uint32_t, int64_t> thresholds;    // metricId → threshold (0=always)
    ::ndk::ScopedAIBinder_DeathRecipient deathRecipient;
    void *cookie = nullptr;
};

// ---------------------------------------------------------------------------
// DispatchManager
// ---------------------------------------------------------------------------
class DispatchManager {
public:
    DispatchManager();
    ~DispatchManager() = default;

    DispatchManager(const DispatchManager &) = delete;
    DispatchManager &operator=(const DispatchManager &) = delete;

    /**
     * Register a listener.
     * Thread-safe (Binder thread).
     *
     * @param listener    AIDL callback object.
     * @param metricIds   uint32 MetricId values to watch. Empty = all.
     * @param thresholds  Parallel threshold array. Empty = notify on every sample.
     * @return true on success.
     */
    bool registerListener(const std::shared_ptr<IMetricListener> &listener,
                          const std::vector<int32_t> &metricIds,
                          const std::vector<int64_t> &thresholds);

    /**
     * Unregister a listener.
     * Thread-safe (Binder thread).
     */
    void unregisterListener(const std::shared_ptr<IMetricListener> &listener);

    /**
     * Notify of a new metric sample. Called from the sampling thread.
     * Fast path: if no subscriptions exist, returns immediately (single
     * atomic load, no lock).
     *
     * @param id          Metric that was just published.
     * @param value       New sampled value.
     * @param timestampNs CLOCK_MONOTONIC ns.
     */
    void notify(MetricId id, int64_t value, int64_t timestampNs);

    /** Current number of active subscriptions (for logging). */
    size_t listenerCount() const;

private:
    /** Remove subscription by binder pointer. Called under mMutex. */
    void removeByBinder(AIBinder *binder);

    /** Static death callback forwarded to removeByBinder. */
    static void onBinderDied(void *cookie);

    struct DeathCookie {
        DispatchManager *mgr;
        AIBinder *binder;
    };

    // Subscription table: keyed by raw AIBinder* for O(1) lookup on death
    std::unordered_map<AIBinder *, Subscription> mSubs;
    mutable std::mutex mMutex;

    // Fast-path gate: skip lock entirely when no listeners registered.
    // Written under mMutex, read atomically on sampling thread.
    std::atomic<int32_t> mListenerCount{0};

    // Per-metric last-notified value, used for threshold rising-edge detection.
    // Indexed by metricIndex(id); initialized to INT64_MIN (never triggered).
    int64_t mLastNotified[kMetricSlotCount];
};

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
