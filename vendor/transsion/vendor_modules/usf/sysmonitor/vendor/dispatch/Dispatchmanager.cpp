#define LOG_TAG "SMon-Dispatch"

#include "DispatchManager.h"

#include <climits>
#include <cstring>

namespace vendor {
namespace transsion {
namespace sysmonitor {

// ---------------------------------------------------------------------------
// Constructor: initialize last-notified table
// ---------------------------------------------------------------------------
// Note: DispatchManager has no explicit ctor in header (defaulted),
// so we initialize mLastNotified here via a static helper trick.
// Actually we add a real ctor body via an init helper called from
// registerListener on first use — simpler: just memset in notify() guard.
// Cleanest: add real ctor. Since header says default, we add a real one here.
// (Redefine as non-default in .cpp — header forward-declared only default,
//  which is compatible as long as we provide the body here.)
DispatchManager::DispatchManager() {
    for (size_t i = 0; i < kMetricSlotCount; ++i) {
        mLastNotified[i] = LLONG_MIN;
    }
}

// ---------------------------------------------------------------------------
// Register
// ---------------------------------------------------------------------------

bool DispatchManager::registerListener(const std::shared_ptr<IMetricListener> &listener,
                                       const std::vector<int32_t> &metricIds,
                                       const std::vector<int64_t> &thresholds) {
    if (!listener) {
        SMLOGE("registerListener: null listener");
        return false;
    }

    AIBinder *binder = listener->asBinder().get();

    std::lock_guard<std::mutex> lock(mMutex);

    // Prevent duplicate registration
    if (mSubs.count(binder) > 0) {
        SMLOGW("registerListener: already registered binder=%p", binder);
        return true;
    }

    // Build subscription
    Subscription sub;
    sub.listener = listener;

    for (size_t i = 0; i < metricIds.size(); ++i) {
        uint32_t mid = static_cast<uint32_t>(metricIds[i]);
        sub.metricIds.insert(mid);
        int64_t thr = (i < thresholds.size()) ? thresholds[i] : 0LL;
        sub.thresholds[mid] = thr;
    }

    // Link death recipient
    auto deathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(
        AIBinder_DeathRecipient_new(DispatchManager::onBinderDied));

    auto *cookie = new DeathCookie{this, binder};
    binder_status_t status = AIBinder_linkToDeath(binder, deathRecipient.get(), cookie);
    if (status != STATUS_OK) {
        SMLOGE("registerListener: linkToDeath failed: %d", status);
        delete cookie;
        return false;
    }

    sub.deathRecipient = std::move(deathRecipient);
    sub.cookie = cookie;

    mSubs.emplace(binder, std::move(sub));
    mListenerCount.fetch_add(1, std::memory_order_release);

    SMLOGI("registerListener: binder=%p watchIds=%zu total=%d", binder, metricIds.size(),
           mListenerCount.load());

    return true;
}

// ---------------------------------------------------------------------------
// Unregister
// ---------------------------------------------------------------------------

void DispatchManager::unregisterListener(const std::shared_ptr<IMetricListener> &listener) {
    if (!listener)
        return;

    AIBinder *binder = listener->asBinder().get();
    std::lock_guard<std::mutex> lock(mMutex);
    removeByBinder(binder);
}

size_t DispatchManager::listenerCount() const {
    std::lock_guard<std::mutex> lock(mMutex);
    return mSubs.size();
}

// ---------------------------------------------------------------------------
// Notify — called from sampling thread (hot path)
// ---------------------------------------------------------------------------

void DispatchManager::notify(MetricId id, int64_t value, int64_t timestampNs) {
    // Fast-path gate: zero-cost when no listeners
    if (mListenerCount.load(std::memory_order_acquire) == 0) {
        return;
    }

    const uint32_t idx = metricIndex(id);
    const uint32_t mid = static_cast<uint32_t>(id);

    // Snapshot subscribers under lock — hold time is short (vector copy of ptrs)
    struct FireEntry {
        std::shared_ptr<IMetricListener> listener;
        bool crossedThreshold;    // true → onThresholdExceeded; false → onMetricUpdated
    };
    // Use a small stack-allocated vector to avoid heap alloc on hot path.
    // Max typical subscribers: <10.  Stack array capped at 16.
    constexpr int kMaxFire = 16;
    FireEntry fire[kMaxFire];
    int fireCount = 0;

    {
        std::lock_guard<std::mutex> lock(mMutex);

        for (auto &[binder, sub] : mSubs) {
            // Check if this subscriber watches this metric
            bool watches = sub.metricIds.empty() || (sub.metricIds.count(mid) > 0);
            if (!watches)
                continue;

            // Determine fire mode
            auto thrIt = sub.thresholds.find(mid);
            int64_t thr = (thrIt != sub.thresholds.end()) ? thrIt->second : 0LL;

            bool shouldFire = false;
            bool crossedThreshold = false;

            if (thr == 0) {
                // threshold==0 → notify on every sample
                shouldFire = true;
                crossedThreshold = false;
            } else {
                // Rising-edge threshold: fire when value goes from below to above
                int64_t last = mLastNotified[idx];
                if (value >= thr && last < thr) {
                    shouldFire = true;
                    crossedThreshold = true;
                    SMLOGD("notify: %s threshold crossed: value=%lld thr=%lld", metricName(id),
                           (long long)value, (long long)thr);
                }
            }

            if (shouldFire && fireCount < kMaxFire) {
                fire[fireCount++] = {sub.listener, crossedThreshold};
            }
        }

        // Update last-notified value (under lock, used for threshold edge detect).
        // Design limitation: mLastNotified is a per-metric global value, NOT
        // per-subscriber. If multiple subscribers watch the same metricId with
        // different thresholds, the rising-edge detection shares one "last" value.
        // Consequence: after subscriber A's threshold is crossed and mLastNotified
        // is updated to the new value, subscriber B with a higher threshold may
        // lose its rising-edge opportunity if the value drops and rises again but
        // stays above A's threshold (mLastNotified never resets to below B's threshold).
        // This is an acceptable trade-off for the current single-threshold-per-metric
        // use case. If per-subscriber edge detection is needed in future, move
        // mLastNotified into the Subscription struct.
        mLastNotified[idx] = value;
    }

    // Fire callbacks outside the lock to avoid deadlock / long lock hold
    for (int i = 0; i < fireCount; ++i) {
        auto &e = fire[i];
        ::ndk::ScopedAStatus status;
        if (e.crossedThreshold) {
            status = e.listener->onThresholdExceeded(static_cast<int32_t>(mid), value, timestampNs);
        } else {
            status = e.listener->onMetricUpdated(static_cast<int32_t>(mid), value, timestampNs);
        }
        if (!status.isOk()) {
            SMLOGW("notify: callback failed for metricId=%u: %s", mid, status.getMessage());
        }
    }
}

// ---------------------------------------------------------------------------
// Death handling
// ---------------------------------------------------------------------------

void DispatchManager::removeByBinder(AIBinder *binder) {
    // Must be called under mMutex
    auto it = mSubs.find(binder);
    if (it == mSubs.end())
        return;

    // cookie was new'd at registration; safe to delete after unlink
    delete static_cast<DeathCookie *>(it->second.cookie);
    mSubs.erase(it);
    mListenerCount.fetch_sub(1, std::memory_order_release);
    SMLOGI("removeByBinder: binder=%p removed, remaining=%d", binder, mListenerCount.load());
}

void DispatchManager::onBinderDied(void *cookie) {
    auto *dc = static_cast<DeathCookie *>(cookie);
    if (!dc || !dc->mgr) {
        delete dc;
        return;
    }

    SMLOGW("onBinderDied: binder=%p died, removing subscription", dc->binder);
    {
        std::lock_guard<std::mutex> lock(dc->mgr->mMutex);
        // removeByBinder would delete dc again — handle manually
        auto it = dc->mgr->mSubs.find(dc->binder);
        if (it != dc->mgr->mSubs.end()) {
            it->second.cookie = nullptr;    // prevent double-delete
            dc->mgr->mSubs.erase(it);
            dc->mgr->mListenerCount.fetch_sub(1, std::memory_order_release);
        }
    }
    delete dc;
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
