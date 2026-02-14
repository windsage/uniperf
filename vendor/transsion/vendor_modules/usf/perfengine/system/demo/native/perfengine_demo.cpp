/*
 * PerfEngine Demo Tool
 *
 * Usage:
 *   perfengine_demo listen [--filter <id,id,...>] [--verbose]
 *   perfengine_demo send --event <id|name> [--pkg <pkg>] [--duration <ms>]
 *                        [--count <n>] [--interval <ms>]
 *   perfengine_demo send --all [--interval <ms>]
 *   perfengine_demo both [--filter <id,...>]
 *
 * Examples:
 *   perfengine_demo listen
 *   perfengine_demo listen --filter 1,3,5
 *   perfengine_demo send --event app_launch --pkg com.android.settings --duration 3000
 *   perfengine_demo send --event 1 --count 5 --interval 1000
 *   perfengine_demo send --all
 *   perfengine_demo both
 */

#define LOG_TAG "TPE-Nademo"

#include <aidl/vendor/transsion/hardware/perfengine/BnEventListener.h>
#include <android-base/logging.h>
#include <android/binder_process.h>
#include <perfengine/TranPerfEvent.h>

#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

using ::aidl::vendor::transsion::hardware::perfengine::BnEventListener;
using ::android::transsion::TranPerfEvent;
using ::ndk::ScopedAStatus;

// ==================== Globals ====================

static std::atomic<bool> gRunning{true};

// ==================== Helpers ====================

static const std::unordered_map<std::string, int32_t> kEventNameMap = {
    {"app_launch", TranPerfEvent::EVENT_APP_LAUNCH},
    {"app_switch", TranPerfEvent::EVENT_APP_SWITCH},
    {"scroll", TranPerfEvent::EVENT_SCROLL},
    {"camera_open", TranPerfEvent::EVENT_CAMERA_OPEN},
    {"game_start", TranPerfEvent::EVENT_GAME_START},
    {"video_play", TranPerfEvent::EVENT_VIDEO_PLAY},
    {"animation", TranPerfEvent::EVENT_ANIMATION},
};

static const std::vector<int32_t> kAllEvents = {
    TranPerfEvent::EVENT_APP_LAUNCH, TranPerfEvent::EVENT_APP_SWITCH,
    TranPerfEvent::EVENT_SCROLL,     TranPerfEvent::EVENT_CAMERA_OPEN,
    TranPerfEvent::EVENT_GAME_START, TranPerfEvent::EVENT_VIDEO_PLAY,
    TranPerfEvent::EVENT_ANIMATION,
};

static const char *eventName(int32_t id) {
    switch (id) {
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

// Parse "1,3,5" -> {1,3,5}
static std::vector<int32_t> parseFilter(const std::string &s) {
    std::vector<int32_t> result;
    std::stringstream ss(s);
    std::string token;
    while (std::getline(ss, token, ',')) {
        try {
            result.push_back(std::stoi(token));
        } catch (...) {
        }
    }
    return result;
}

// Parse event id: accept number string or name string
static int32_t parseEventId(const std::string &s) {
    // Try numeric first
    try {
        return std::stoi(s);
    } catch (...) {
    }
    // Try name
    std::string lower = s;
    for (auto &c : lower)
        c = tolower(c);
    auto it = kEventNameMap.find(lower);
    if (it != kEventNameMap.end())
        return it->second;
    return -1;
}

static void printUsage(const char *prog) {
    std::cout << "Usage:\n"
              << "  " << prog << " listen [--filter <id,id,...>] [--verbose]\n"
              << "  " << prog << " send --event <id|name> [--pkg <pkg>] [--duration <ms>]\n"
              << "                       [--count <n>] [--interval <ms>]\n"
              << "  " << prog << " send --all [--interval <ms>]\n"
              << "  " << prog << " both [--filter <id,...>]\n"
              << "\n"
              << "Event names: app_launch, app_switch, scroll, camera_open,\n"
              << "             game_start, video_play, animation\n"
              << "\n"
              << "Examples:\n"
              << "  " << prog << " listen\n"
              << "  " << prog << " listen --filter 1,3\n"
              << "  " << prog
              << " send --event app_launch --pkg com.android.settings --duration 3000\n"
              << "  " << prog << " send --event 1 --count 5 --interval 1000\n"
              << "  " << prog << " send --all\n"
              << "  " << prog << " both\n";
}

static void signalHandler(int) {
    gRunning = false;
    // 直接退出进程
    exit(0);
}

// ==================== Listener Implementation ====================

class DemoEventListener : public BnEventListener {
public:
    explicit DemoEventListener(bool verbose) : mVerbose(verbose) {}

    ScopedAStatus onEventStart(int32_t eventId, int64_t timestamp, int32_t numParams,
                               const std::vector<int32_t> &intParams,
                               const std::optional<std::string> &extraStrings) override {
        mStartCount++;
        std::cout << "[RECV][START] #" << mStartCount << " eventId=" << eventId << "("
                  << eventName(eventId) << ")"
                  << " ts=" << timestamp;
        if (mVerbose) {
            if (numParams > 0 && !intParams.empty()) {
                std::cout << " params=[";
                for (int i = 0; i < numParams && i < (int)intParams.size(); i++) {
                    if (i > 0)
                        std::cout << ",";
                    std::cout << intParams[i];
                }
                std::cout << "]";
            }
            if (extraStrings.has_value() && !extraStrings->empty()) {
                std::cout << " extra=\"" << *extraStrings << "\"";
            }
        }
        std::cout << std::endl;
        return ScopedAStatus::ok();
    }

    ScopedAStatus onEventEnd(int32_t eventId, int64_t timestamp,
                             const std::optional<std::string> &extraStrings) override {
        mEndCount++;
        std::cout << "[RECV][ END ] #" << mEndCount << " eventId=" << eventId << "("
                  << eventName(eventId) << ")"
                  << " ts=" << timestamp;
        if (mVerbose && extraStrings.has_value() && !extraStrings->empty()) {
            std::cout << " extra=\"" << *extraStrings << "\"";
        }
        std::cout << std::endl;
        return ScopedAStatus::ok();
    }

private:
    bool mVerbose;
    int mStartCount{0};
    int mEndCount{0};
};

// ==================== Send Logic ====================

static void sendOneEvent(int32_t id, const std::string &pkg, int32_t duration, bool verbose) {
    int64_t ts = TranPerfEvent::now();
    int32_t ret;

    if (!pkg.empty() && duration > 0) {
        std::vector<int32_t> intParams = {duration};
        std::vector<std::string> strParams = {pkg};
        ret = TranPerfEvent::notifyEventStart(id, ts, intParams, strParams);
    } else if (!pkg.empty()) {
        ret = TranPerfEvent::notifyEventStart(id, ts, pkg);
    } else if (duration > 0) {
        std::vector<int32_t> intParams = {duration};
        ret = TranPerfEvent::notifyEventStart(id, ts, intParams);
    } else {
        ret = TranPerfEvent::notifyEventStart(id, ts);
    }

    if (verbose) {
        std::cout << "[SEND][START] eventId=" << id << "(" << eventName(id) << ")"
                  << " pkg=\"" << pkg << "\""
                  << " duration=" << duration << "ms"
                  << " ret=" << ret << std::endl;
    } else {
        std::cout << "[SEND][START] eventId=" << id << "(" << eventName(id) << ")" << std::endl;
    }

    // Hold briefly then send end
    std::this_thread::sleep_for(std::chrono::milliseconds(duration > 0 ? 200 : 100));

    TranPerfEvent::notifyEventEnd(id, TranPerfEvent::now(), pkg);
    std::cout << "[SEND][ END ] eventId=" << id << "(" << eventName(id) << ")" << std::endl;
}

// ==================== Mode: listen ====================

static int modeListenRun(const std::vector<int32_t> &filter, bool verbose) {
    auto listener = ndk::SharedRefBase::make<DemoEventListener>(verbose);

    int32_t ret;
    if (filter.empty()) {
        ret = TranPerfEvent::registerEventListener(listener);
        std::cout << "[LISTEN] Registered, subscribing ALL events" << std::endl;
    } else {
        ret = TranPerfEvent::registerEventListener(listener, filter);
        std::cout << "[LISTEN] Registered, filter=[";
        for (size_t i = 0; i < filter.size(); i++) {
            if (i > 0)
                std::cout << ",";
            std::cout << filter[i] << "(" << eventName(filter[i]) << ")";
        }
        std::cout << "]" << std::endl;
    }

    if (ret != 0) {
        std::cerr << "[ERROR] registerEventListener failed, ret=" << ret << std::endl;
        return 1;
    }

    std::cout << "[LISTEN] Waiting for events... (Ctrl+C to stop)" << std::endl;

    ABinderProcess_joinThreadPool();    // blocks until signal

    TranPerfEvent::unregisterEventListener(listener);
    std::cout << "[LISTEN] Unregistered, exit" << std::endl;
    return 0;
}

// ==================== Mode: send ====================

static int modeSendRun(bool sendAll, int32_t eventId, const std::string &pkg, int32_t duration,
                       int32_t count, int32_t intervalMs, bool verbose) {
    std::vector<int32_t> events;
    if (sendAll) {
        events = kAllEvents;
    } else {
        if (eventId < 0) {
            std::cerr << "[ERROR] Invalid event id" << std::endl;
            return 1;
        }
        events.push_back(eventId);
    }

    for (int i = 0; i < count && gRunning; i++) {
        if (count > 1) {
            std::cout << "--- Round " << (i + 1) << "/" << count << " ---" << std::endl;
        }
        for (int32_t id : events) {
            if (!gRunning)
                break;
            sendOneEvent(id, pkg, duration, verbose);
            if (events.size() > 1) {
                std::this_thread::sleep_for(std::chrono::milliseconds(intervalMs));
            }
        }
        if (i < count - 1) {
            std::this_thread::sleep_for(std::chrono::milliseconds(intervalMs));
        }
    }
    return 0;
}

// ==================== Mode: both ====================

static int modeBothRun(const std::vector<int32_t> &filter) {
    auto listener = ndk::SharedRefBase::make<DemoEventListener>(true);
    int32_t ret;
    if (filter.empty()) {
        ret = TranPerfEvent::registerEventListener(listener);
    } else {
        ret = TranPerfEvent::registerEventListener(listener, filter);
    }
    if (ret != 0) {
        std::cerr << "[ERROR] registerEventListener failed, ret=" << ret << std::endl;
        return 1;
    }
    std::cout << "[BOTH] Listener registered" << std::endl;

    std::atomic<bool> senderDone{false};

    std::thread sender([&senderDone]() {
        std::this_thread::sleep_for(std::chrono::milliseconds(300));
        std::cout << "[BOTH] Sending test events..." << std::endl;
        for (int32_t id : kAllEvents) {
            if (!gRunning) break;
            sendOneEvent(id, "com.transsion.demo", 1000, true);
            std::this_thread::sleep_for(std::chrono::milliseconds(600));
        }
        std::cout << "[BOTH] All test events sent, waiting 2s then exit..." << std::endl;
        std::this_thread::sleep_for(std::chrono::seconds(2));
        senderDone = true;
    });

    while (!senderDone && gRunning) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    if (sender.joinable()) sender.join();

    TranPerfEvent::unregisterEventListener(listener);
    std::cout << "[BOTH] Done" << std::endl;
    return 0;
}

// ==================== main ====================

int main(int argc, char **argv) {
    android::base::InitLogging(argv, android::base::StderrLogger);
    android::base::SetMinimumLogSeverity(android::base::WARNING);    // suppress verbose AIDL logs

    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);

    if (argc < 2) {
        printUsage(argv[0]);
        return 1;
    }

    // Init binder thread pool (needed for listener callbacks)
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();

    std::string mode = argv[1];

    // ---- parse common options ----
    std::vector<int32_t> filter;
    int32_t eventId = -1;
    std::string pkg;
    int32_t duration = 0;
    int32_t count = 1;
    int32_t intervalMs = 500;
    bool verbose = false;
    bool sendAll = false;

    for (int i = 2; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--filter" && i + 1 < argc) {
            filter = parseFilter(argv[++i]);
        } else if (arg == "--event" && i + 1 < argc) {
            eventId = parseEventId(argv[++i]);
        } else if (arg == "--pkg" && i + 1 < argc) {
            pkg = argv[++i];
        } else if (arg == "--duration" && i + 1 < argc) {
            duration = std::atoi(argv[++i]);
        } else if (arg == "--count" && i + 1 < argc) {
            count = std::atoi(argv[++i]);
        } else if (arg == "--interval" && i + 1 < argc) {
            intervalMs = std::atoi(argv[++i]);
        } else if (arg == "--all") {
            sendAll = true;
        } else if (arg == "--verbose") {
            verbose = true;
        } else {
            std::cerr << "[WARN] Unknown option: " << arg << std::endl;
        }
    }

    if (mode == "listen") {
        return modeListenRun(filter, verbose);
    } else if (mode == "send") {
        return modeSendRun(sendAll, eventId, pkg, duration, count, intervalMs, verbose);
    } else if (mode == "both") {
        return modeBothRun(filter);
    } else {
        std::cerr << "[ERROR] Unknown mode: " << mode << std::endl;
        printUsage(argv[0]);
        return 1;
    }
}
