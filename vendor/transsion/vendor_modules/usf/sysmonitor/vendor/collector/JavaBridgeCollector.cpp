#define LOG_TAG "SMon-JBridge"

#include <time.h>

#include <atomic>
#include <cstdint>

#include "ICollector.h"
#include "MetricDef.h"
#include "MetricStore.h"
#include "SysMonLog.h"

/**
 * JavaBridgeCollector - Receives metrics pushed from the Java layer via JNI
 *
 * Unlike other collectors (pull-based), this is PUSH-based:
 *   Java → JNI (SysMonitorJNI.cpp) → JavaBridgeCollector::push() → MetricStore
 *
 * Metrics handled:
 *   WIFI_RSSI        (dBm,     from WifiManager)
 *   CELL_SIGNAL      (ASU,     from TelephonyManager)
 *   FRAME_JANKY_RATE (pct*100, from FrameMetrics)
 *   FRAME_MISSED     (count,   from FrameMetrics)
 *   BG_PROCESS_COUNT (count,   from ActivityManager)
 *
 * sample() is a no-op — CollectorManager registers this collector only
 * for lifecycle management (init/isAvailable). The JNI thread calls push()
 * directly when Java data arrives, bypassing the tick loop entirely.
 *
 * Thread safety: push() may be called from any JNI thread concurrently
 * with the sampling thread. MetricStore::publish() is already atomic,
 * so no additional locking is needed here.
 */

namespace vendor {
namespace transsion {
namespace sysmonitor {

class JavaBridgeCollector : public ICollector {
public:
    explicit JavaBridgeCollector(MetricStore *store) : mStore(store) {}

    ~JavaBridgeCollector() override = default;

    bool init() override {
        if (mStore == nullptr) {
            SMLOGE("init: MetricStore is null");
            return false;
        }
        mAvailable = true;
        SMLOGI("JavaBridgeCollector ready (push-based, sample() is no-op)");
        return true;
    }

    // No-op: data arrives via push(), not via the tick loop
    void sample(const PublishFn & /*publishFn*/) override {}

    int32_t getIntervalMs() const override { return SampleInterval::JAVA; }
    const char *getName() const override { return "JavaBridgeCollector"; }
    bool isAvailable() const override { return mAvailable; }

    /**
     * Called directly from JNI thread when Java layer has new data.
     * Must be callable from any thread — MetricStore publish() is atomic.
     *
     * @param id    MetricId (must be one of the Java-side metrics)
     * @param value Raw metric value
     */
    void push(MetricId id, int64_t value) {
        // Validate that the caller is pushing a Java-side metric
        switch (id) {
            case MetricId::WIFI_RSSI:
            case MetricId::CELL_SIGNAL:
            case MetricId::FRAME_JANKY_RATE:
            case MetricId::FRAME_MISSED:
            case MetricId::BG_PROCESS_COUNT:
                break;
            default:
                SMLOGW("push: unexpected MetricId %u from Java bridge", metricIndex(id));
                return;
        }

        struct timespec ts;
        ::clock_gettime(CLOCK_MONOTONIC, &ts);
        const int64_t nowNs = ts.tv_sec * 1'000'000'000LL + ts.tv_nsec;

        SMLOGD("push: %s = %lld", metricName(id), (long long)value);
        mStore->publish(id, value, nowNs);
    }

    /**
     * Return singleton instance pointer for JNI access.
     * Set by SysMonitor during initialization.
     */
    static void setInstance(JavaBridgeCollector *inst) {
        sInstance.store(inst, std::memory_order_release);
    }

    static JavaBridgeCollector *getInstance() { return sInstance.load(std::memory_order_acquire); }

private:
    MetricStore *mStore = nullptr;
    bool mAvailable = false;

    // Global pointer accessed by JNI layer — set once at init, never changes
    static std::atomic<JavaBridgeCollector *> sInstance;
};

std::atomic<JavaBridgeCollector *> JavaBridgeCollector::sInstance{nullptr};

ICollector *createJavaBridgeCollector(MetricStore *store) {
    return new JavaBridgeCollector(store);
}

/**
 * JNI entry point — called from SysMonitorJNI.cpp
 * Looks up the singleton and forwards the push.
 */
void javaBridgePush(int metricIdInt, int64_t value) {
    JavaBridgeCollector *inst = JavaBridgeCollector::getInstance();
    if (__builtin_expect(inst == nullptr, 0)) {
        SMLOGW("javaBridgePush: collector not yet initialized (id=%d)", metricIdInt);
        return;
    }
    MetricId id = static_cast<MetricId>(static_cast<uint32_t>(metricIdInt));
    inst->push(id, value);
}

}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor
