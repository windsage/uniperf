/*
 * TranPerfHub Listener Demo (Native/C++)
 *
 * This demo shows how to register a listener to receive performance events
 * from TranPerfHub in a native (C++) process.
 *
 * Usage:
 *   adb shell /vendor/bin/perfhub_listener_demo
 */

#define LOG_TAG "PerfHubListenerDemo"

#include <aidl/vendor/transsion/hardware/perfhub/BnEventListener.h>
#include <aidl/vendor/transsion/hardware/perfhub/ITranPerfHub.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include <csignal>
#include <iostream>

using ::aidl::vendor::transsion::hardware::perfhub::BnEventListener;
using ::aidl::vendor::transsion::hardware::perfhub::ITranPerfHub;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

// ==================== Event Listener Implementation ====================

class EventListenerImpl : public BnEventListener {
private:
    int mEventCount = 0;

    const char *getEventName(int32_t eventId) {
        switch (eventId) {
            case 1:
                return "APP_LAUNCH";
            case 2:
                return "APP_SWITCH";
            case 3:
                return "SCROLL";
            case 4:
                return "CAMERA_OPEN";
            case 5:
                return "GAME_START";
            case 6:
                return "VIDEO_PLAY";
            case 7:
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
        LOG(INFO) << "  eventId: " << eventId << " (" << getEventName(eventId) << ")";
        LOG(INFO) << "  timestamp: " << timestamp << " ns";
        LOG(INFO) << "  numParams: " << numParams;

        if (!intParams.empty()) {
            LOG(INFO) << "  intParams:";
            for (size_t i = 0; i < std::min(intParams.size(), static_cast<size_t>(numParams));
                 i++) {
                if (i == 0) {
                    LOG(INFO) << "    [" << i << "] duration = " << intParams[i] << " ms";
                } else {
                    LOG(INFO) << "    [" << i << "] = " << intParams[i];
                }
            }
        }

        if (extraStrings.has_value() && !extraStrings->empty()) {
            LOG(INFO) << "  extraStrings: " << *extraStrings;
        }

        LOG(INFO) << "========================================";

        return ScopedAStatus::ok();
    }

    ScopedAStatus onEventEnd(int32_t eventId, int64_t timestamp,
                             const std::optional<std::string> &extraStrings) override {
        LOG(INFO) << "========================================";
        LOG(INFO) << "Event End Received #" << mEventCount;
        LOG(INFO) << "  eventId: " << eventId << " (" << getEventName(eventId) << ")";
        LOG(INFO) << "  timestamp: " << timestamp << " ns";

        if (extraStrings.has_value() && !extraStrings->empty()) {
            LOG(INFO) << "  extraStrings: " << *extraStrings;
        }

        LOG(INFO) << "========================================";

        return ScopedAStatus::ok();
    }
};

// ==================== Demo Application ====================

class PerfHubListenerDemo {
private:
    std::shared_ptr<ITranPerfHub> mPerfHub;
    std::shared_ptr<EventListenerImpl> mListener;
    bool mRunning = false;

public:
    bool start() {
        LOG(INFO) << "Starting TranPerfHub Listener Demo...";

        // Connect to TranPerfHub service
        if (!connectToService()) {
            LOG(ERROR) << "Failed to connect to TranPerfHub service";
            return false;
        }

        // Create listener
        mListener = ndk::SharedRefBase::make<EventListenerImpl>();
        if (!mListener) {
            LOG(ERROR) << "Failed to create listener";
            return false;
        }

        // Register listener
        auto status = mPerfHub->registerEventListener(mListener);
        if (!status.isOk()) {
            LOG(ERROR) << "Failed to register listener: " << status.getMessage();
            return false;
        }

        LOG(INFO) << "Listener registered successfully!";
        LOG(INFO) << "Waiting for events... (Press Ctrl+C to stop)";

        mRunning = true;
        return true;
    }

    void stop() {
        if (!mRunning) {
            return;
        }

        LOG(INFO) << "Stopping TranPerfHub Listener Demo...";

        if (mPerfHub && mListener) {
            auto status = mPerfHub->unregisterEventListener(mListener);
            if (!status.isOk()) {
                LOG(WARNING) << "Failed to unregister listener: " << status.getMessage();
            } else {
                LOG(INFO) << "Listener unregistered";
            }
        }

        mListener.reset();
        mPerfHub.reset();
        mRunning = false;

        LOG(INFO) << "Demo stopped";
    }

private:
    bool connectToService() {
        const std::string instance = std::string() + ITranPerfHub::descriptor + "/default";

        SpAIBinder binder(AServiceManager_waitForService(instance.c_str()));
        if (binder.get() == nullptr) {
            LOG(ERROR) << "Service not found: " << instance;
            return false;
        }

        mPerfHub = ITranPerfHub::fromBinder(binder);
        if (!mPerfHub) {
            LOG(ERROR) << "Failed to get ITranPerfHub interface";
            return false;
        }

        LOG(INFO) << "Connected to TranPerfHub service";
        return true;
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

    LOG(INFO) << "TranPerfHub Native Listener Demo";
    LOG(INFO) << "==================================";

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
