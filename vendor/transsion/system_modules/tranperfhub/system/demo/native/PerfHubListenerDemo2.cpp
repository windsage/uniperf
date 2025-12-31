/*
 * TranPerfHub Listener Demo (Native/C++)
 *
 * This demo shows how to use TranPerfEvent Native API:
 *   1. Register listener to receive performance events
 *   2. Send performance events
 *
 * Usage:
 *   adb shell /system_ext/bin/perfhub_listener_demo
 */

#define LOG_TAG "PerfHubListenerDemo"

#include <aidl/vendor/transsion/hardware/perfhub/BnEventListener.h>
#include <android-base/logging.h>
#include <android/binder_process.h>
#include <perfhub/TranPerfEvent.h>

#include <csignal>
#include <thread>
#include <vector>

using ::aidl::vendor::transsion::hardware::perfhub::BnEventListener;
using ::android::transsion::TranPerfEvent;
using ::ndk::ScopedAStatus;

// ==================== Event Listener Implementation ====================

class EventListenerImpl : public BnEventListener {
private:
    int mEventCount = 0;

    const char *getEventName(int32_t eventId) {
        switch (eventId) {
            case TranPerfEvent::EVENT_APP_LAUNCH:
                return "APP_LAUNCH";
            case TranPerfEvent::EVENT_APP_SWITCH:
                return "APP_SWITCH";
            case TranPerfEvent::EVENT_SCROLL:
                return "SCROLL";
            case TranPerfEvent::EVENT_CAMERA_OPEN:
                return "CAMERA_OPEN";
            case TranPerfEvent::EVENT_GAME_START:
                return "GAME_START";
            case TranPerfEvent::EVENT_VIDEO_PLAY:
                return "VIDEO_PLAY";
            case TranPerfEvent::EVENT_ANIMATION:
                return "ANIMATION";
            default:
                return "UNKNOWN";
        }
    }

public:
    ScopedAStatus onEventStart(int32_t eventId, int64_t timestamp, int32_t numParams,
                               const std::vector<int32_t> &intParams,
                               const std::optional<std::string> &extraStrings) override {
        mEventCount++;

        LOG(INFO) << "========================================";
        LOG(INFO) << "Event Start Received #" << mEventCount;
        LOG(INFO) << "  Event ID: " << eventId << " (" << getEventName(eventId) << ")";
        LOG(INFO) << "  Timestamp: " << timestamp << " ns (" << (timestamp / 1000000) << " ms)";
        LOG(INFO) << "  Num Params: " << numParams;

        if (!intParams.empty() && numParams > 0) {
            LOG(INFO) << "  Int Parameters:";
            for (size_t i = 0; i < std::min(intParams.size(), static_cast<size_t>(numParams));
                 i++) {
                if (i == 0) {
                    LOG(INFO) << "      [" << i << "] duration = " << intParams[i] << " ms";
                } else {
                    LOG(INFO) << "      [" << i << "] = " << intParams[i];
                }
            }
        }

        if (extraStrings.has_value() && !extraStrings->empty()) {
            LOG(INFO) << "  String Parameters: " << *extraStrings;
        }

        LOG(INFO) << "========================================";

        return ScopedAStatus::ok();
    }

    ScopedAStatus onEventEnd(int32_t eventId, int64_t timestamp,
                             const std::optional<std::string> &extraStrings) override {
        LOG(INFO) << "========================================";
        LOG(INFO) << "Event End Received";
        LOG(INFO) << "  Event ID: " << eventId << " (" << getEventName(eventId) << ")";
        LOG(INFO) << "  Timestamp: " << timestamp << " ns (" << (timestamp / 1000000) << " ms)";

        if (extraStrings.has_value() && !extraStrings->empty()) {
            LOG(INFO) << "  String Parameters: " << *extraStrings;
        }

        LOG(INFO) << "========================================";

        return ScopedAStatus::ok();
    }
};

// ==================== Demo Application ====================

class PerfHubListenerDemo {
private:
    std::shared_ptr<EventListenerImpl> mListener;
    bool mRunning = false;

public:
    bool start() {
        if (mRunning) {
            LOG(WARNING) << "Demo already running";
            return false;
        }

        LOG(INFO) << "";
        LOG(INFO) << "╔════════════════════════════════════════╗";
        LOG(INFO) << "║  TranPerfHub Listener Demo (Native)   ║";
        LOG(INFO) << "╚════════════════════════════════════════╝";
        LOG(INFO) << "";

        // Step 1: Register listener
        if (!registerListener()) {
            return false;
        }

        // Step 2: Send test events (optional)
        sendTestEvents();

        mRunning = true;

        LOG(INFO) << "";
        LOG(INFO) << "Demo started successfully!";
        LOG(INFO) << "Listening for performance events...";
        LOG(INFO) << " (Press Ctrl+C to stop)";
        LOG(INFO) << "";

        return true;
    }

    void stop() {
        if (!mRunning) {
            return;
        }

        LOG(INFO) << "";
        LOG(INFO) << "Stopping TranPerfHub Listener Demo...";

        // Unregister listener
        unregisterListener();

        mRunning = false;

        LOG(INFO) << "Demo stopped";
        LOG(INFO) << "";
    }

private:
    // ==================== Listener Registration ====================

    bool registerListener() {
        LOG(INFO) << "Registering event listener...";

        // Create listener
        mListener = ndk::SharedRefBase::make<EventListenerImpl>();
        if (!mListener) {
            LOG(ERROR) << "   Failed to create listener";
            return false;
        }

        // 使用 TranPerfEvent API 注册监听器
        int result = TranPerfEvent::registerEventListener(mListener);

        if (result != 0) {
            LOG(ERROR) << "   Failed to register listener (error: " << result << ")";
            return false;
        }

        LOG(INFO) << "   Listener registered successfully";
        return true;
    }

    void unregisterListener() {
        if (!mListener) {
            return;
        }

        LOG(INFO) << "Unregistering event listener...";

        // 使用 TranPerfEvent API 取消注册
        int result = TranPerfEvent::unregisterEventListener(mListener);

        if (result != 0) {
            LOG(WARNING) << "  Failed to unregister listener (error: " << result << ")";
        } else {
            LOG(INFO) << "  Listener unregistered successfully";
        }

        mListener.reset();
    }

    // ==================== Test Event Sending ====================

    void sendTestEvents() {
        LOG(INFO) << "";
        LOG(INFO) << "Sending test events...";
        LOG(INFO) << "";

        // Test 1: Simple event
        testSimpleEvent();
        std::this_thread::sleep_for(std::chrono::milliseconds(500));

        // Test 2: Event with string
        testEventWithString();
        std::this_thread::sleep_for(std::chrono::milliseconds(500));

        // Test 3: Event with string array
        testEventWithStringArray();
        std::this_thread::sleep_for(std::chrono::milliseconds(500));

        // Test 4: Event with int parameters
        testEventWithIntParams();
        std::this_thread::sleep_for(std::chrono::milliseconds(500));

        // Test 5: Event with full parameters
        testEventWithFullParams();

        LOG(INFO) << "";
        LOG(INFO) << "All test events sent";
    }

    void testSimpleEvent() {
        LOG(INFO) << "Test 1: Simple event (eventId + timestamp)";

        int64_t ts = TranPerfEvent::now();

        // 使用 TranPerfEvent API 发送事件
        TranPerfEvent::notifyEventStart(TranPerfEvent::EVENT_APP_LAUNCH, ts);

        LOG(INFO) << "   Sent: EVENT_APP_LAUNCH";

        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        TranPerfEvent::notifyEventEnd(TranPerfEvent::EVENT_APP_LAUNCH, TranPerfEvent::now());

        LOG(INFO) << "   Sent: EVENT_APP_LAUNCH (end)";
    }

    void testEventWithString() {
        LOG(INFO) << "Test 2: Event with string parameter";

        int64_t ts = TranPerfEvent::now();

        // 使用 TranPerfEvent API
        TranPerfEvent::notifyEventStart(TranPerfEvent::EVENT_APP_SWITCH, ts,
                                        "com.android.settings");

        LOG(INFO) << "   Sent: EVENT_APP_SWITCH with packageName";

        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        TranPerfEvent::notifyEventEnd(TranPerfEvent::EVENT_APP_SWITCH, TranPerfEvent::now(),
                                      "com.android.settings");
    }

    void testEventWithStringArray() {
        LOG(INFO) << "Test 3: Event with string array";

        int64_t ts = TranPerfEvent::now();

        std::vector<std::string> strings = {"com.android.settings", ".MainActivity", "cold_start"};

        // 使用 TranPerfEvent API
        TranPerfEvent::notifyEventStart(TranPerfEvent::EVENT_SCROLL, ts, strings);

        LOG(INFO) << "   Sent: EVENT_SCROLL with string array";

        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        TranPerfEvent::notifyEventEnd(TranPerfEvent::EVENT_SCROLL, TranPerfEvent::now());
    }

    void testEventWithIntParams() {
        LOG(INFO) << "Test 4: Event with int parameters";

        int64_t ts = TranPerfEvent::now();

        std::vector<int32_t> intParams = {
            2000,    // duration = 2s
            500,     // velocity = 500
            1        // direction = down
        };

        // 使用 TranPerfEvent API
        TranPerfEvent::notifyEventStart(TranPerfEvent::EVENT_CAMERA_OPEN, ts, intParams);

        LOG(INFO) << "   Sent: EVENT_CAMERA_OPEN with intParams";

        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        TranPerfEvent::notifyEventEnd(TranPerfEvent::EVENT_CAMERA_OPEN, TranPerfEvent::now());
    }

    void testEventWithFullParams() {
        LOG(INFO) << "Test 5: Event with full parameters";

        int64_t ts = TranPerfEvent::now();

        std::vector<int32_t> intParams = {3000, 1, 0};
        std::vector<std::string> stringParams = {"com.example.game", ".GameActivity", "level_1"};

        // 使用 TranPerfEvent API
        TranPerfEvent::notifyEventStart(TranPerfEvent::EVENT_GAME_START, ts, intParams,
                                        stringParams);

        LOG(INFO) << "   Sent: EVENT_GAME_START with full parameters";

        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        TranPerfEvent::notifyEventEnd(TranPerfEvent::EVENT_GAME_START, TranPerfEvent::now());
    }
};

// ==================== Signal Handling ====================

static PerfHubListenerDemo *gDemo = nullptr;

void signalHandler(int signal) {
    LOG(INFO) << "Received signal " << signal << ", shutting down...";
    if (gDemo) {
        gDemo->stop();
    }
    exit(0);
}

// ==================== Main Entry Point ====================

int main(int argc, char **argv) {
    // Setup logging
    android::base::InitLogging(argv, android::base::StderrLogger);
    android::base::SetMinimumLogSeverity(android::base::INFO);

    // Initialize binder thread pool
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();

    // Create demo instance
    PerfHubListenerDemo demo;
    gDemo = &demo;

    // Setup signal handlers
    signal(SIGINT, signalHandler);     // Ctrl+C
    signal(SIGTERM, signalHandler);    // kill command

    // Start demo
    if (!demo.start()) {
        LOG(ERROR) << "Failed to start demo";
        return 1;
    }

    // Join thread pool (blocks forever)
    ABinderProcess_joinThreadPool();

    // Cleanup (will never reach here unless process is killed)
    demo.stop();

    return 0;
}
