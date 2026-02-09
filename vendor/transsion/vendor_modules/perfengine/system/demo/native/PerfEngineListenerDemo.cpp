/*
 * PerfEngine Listener Demo (Native/C++)
 *
 * This demo shows how to register a listener to receive performance events
 * from PerfEngine in a native (C++) process.
 *
 * Usage:
 *   adb shell /vendor/bin/perfengine_listener_demo
 */

#define LOG_TAG "PerfEngineListenerDemo"

#include <aidl/vendor/transsion/hardware/perfengine/BnEventListener.h>
#include <aidl/vendor/transsion/hardware/perfengine/IPerfEngine.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include <csignal>
#include <iostream>

using ::aidl::vendor::transsion::hardware::perfengine::BnEventListener;
using ::aidl::vendor::transsion::hardware::perfengine::IPerfEngine;
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

class PerfEngineListenerDemo {
private:
    std::shared_ptr<IPerfEngine> mPerfEngine;
    std::shared_ptr<EventListenerImpl> mListener;
    bool mRunning = false;

public:
    bool start() {
        LOG(INFO) << "Starting PerfEngine Listener Demo...";

        // Connect to PerfEngine service
        if (!connectToService()) {
            LOG(ERROR) << "Failed to connect to PerfEngine service";
            return false;
        }

        // Create listener
        mListener = ndk::SharedRefBase::make<EventListenerImpl>();
        if (!mListener) {
            LOG(ERROR) << "Failed to create listener";
            return false;
        }

        // Register listener
        auto status = mPerfEngine->registerEventListener(mListener);
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

        LOG(INFO) << "Stopping PerfEngine Listener Demo...";

        if (mPerfEngine && mListener) {
            auto status = mPerfEngine->unregisterEventListener(mListener);
            if (!status.isOk()) {
                LOG(WARNING) << "Failed to unregister listener: " << status.getMessage();
            } else {
                LOG(INFO) << "Listener unregistered";
            }
        }

        mListener.reset();
        mPerfEngine.reset();
        mRunning = false;

        LOG(INFO) << "Demo stopped";
    }

private:
    bool connectToService() {
        const std::string instance = std::string() + IPerfEngine::descriptor + "/default";

        SpAIBinder binder(AServiceManager_waitForService(instance.c_str()));
        if (binder.get() == nullptr) {
            LOG(ERROR) << "Service not found: " << instance;
            return false;
        }

        mPerfEngine = IPerfEngine::fromBinder(binder);
        if (!mPerfEngine) {
            LOG(ERROR) << "Failed to get IPerfEngine interface";
            return false;
        }

        LOG(INFO) << "Connected to PerfEngine service";
        return true;
    }
};

// ==================== Signal Handling ====================

static PerfEngineListenerDemo *gDemo = nullptr;

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

    LOG(INFO) << "PerfEngine Native Listener Demo";
    LOG(INFO) << "==================================";

    // Initialize binder thread pool
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();

    // Create demo instance
    PerfEngineListenerDemo demo;
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
