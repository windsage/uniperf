#include "PerfLockCaller.h"

#include <aidl/vendor/qti/hardware/display/config/Attributes.h>
#include <aidl/vendor/qti/hardware/display/config/DisplayType.h>
#include <aidl/vendor/qti/hardware/display/config/IDisplayConfig.h>
#include <android-base/properties.h>
#include <android/binder_manager.h>
#include <dlfcn.h>
#include <libxml/parser.h>
#include <unistd.h>

#include <chrono>
#include <cstring>

#include "ParamMapper.h"
#include "PerfEngineTypes.h"
#include "TranLog.h"
#include "XmlConfigParser.h"

using aidl::vendor::qti::hardware::display::config::Attributes;
using aidl::vendor::qti::hardware::display::config::DisplayType;
using aidl::vendor::qti::hardware::display::config::IDisplayConfig;
using ::ndk::SpAIBinder;

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "TPE-Caller"

namespace vendor {
namespace transsion {
namespace perfengine {

// ==================== Constructor / Destructor ====================

PerfLockCaller::PerfLockCaller() : mPlatform(Platform::UNKNOWN), mInitialized(false) {
    memset(&mQcomFuncs, 0, sizeof(mQcomFuncs));
    memset(&mMtkFuncs, 0, sizeof(mMtkFuncs));
}

PerfLockCaller::~PerfLockCaller() {
    TLOGI("Destroying PerfLockCaller");

    std::lock_guard<std::mutex> lock(mTimerMutex);
    for (auto &pair : mActiveTimers) {
        if (pair.second.cancelled) {
            pair.second.cancelled->store(true);
        }

        if (pair.second.thread && pair.second.thread->joinable()) {
            pair.second.thread->join();
        }

        if (pair.second.platformHandle > 0) {
            if (mPlatform == Platform::QCOM) {
                qcomReleasePerfLock(pair.second.platformHandle);
            } else if (mPlatform == Platform::MTK) {
                mtkReleasePerfLock(pair.second.platformHandle);
            }
        }
    }
    mActiveTimers.clear();
}

// ==================== 初始化 ====================

bool PerfLockCaller::init() {
    if (mInitialized) {
        TLOGW("PerfLockCaller already initialized");
        return true;
    }

    TLOGI("Initializing PerfLockCaller");

    // 1. Detect platform
    mPlatform = PlatformDetector::getInstance().getPlatform();
    if (mPlatform == Platform::UNKNOWN) {
        TLOGE("Failed to detect platform");
        return false;
    }
    TLOGI("Detected platform: %s", PlatformDetector::getInstance().getPlatformName());

    // 2. Initialize platform functions (dlsym)
    bool platformInitOk = false;
    switch (mPlatform) {
        case Platform::QCOM:
            platformInitOk = initQcom();
            break;
        case Platform::MTK:
            platformInitOk = initMtk();
            break;
        case Platform::UNISOC:
            platformInitOk = initUnisoc();
            break;
        default:
            TLOGE("Unknown platform");
            return false;
    }
    if (!platformInitOk) {
        TLOGE("Failed to initialize platform functions");
        return false;
    }

    // 3. Initialize ParamMapper FIRST — XmlConfigParser depends on it for pre-conversion
    if (!ParamMapper::getInstance().init(mPlatform)) {
        TLOGE("Failed to initialize ParamMapper");
        return false;
    }
    TLOGI("ParamMapper initialized: %zu mappings", ParamMapper::getInstance().getMappingCount());

    // 4. Load scenario XML — triggers pre-conversion using ParamMapper
    if (!XmlConfigParser::getInstance().loadConfig("perfengine_params.xml")) {
        TLOGE("Failed to load scenario config");
        return false;
    }
    TLOGI("Loaded scenario config: %zu entries", XmlConfigParser::getInstance().getScenarioCount());

    // 5. Clean libxml2
    xmlCleanupParser();
    TLOGI("libxml2 cleaned up after initialization");

    mInitialized = true;
    TLOGI("PerfLockCaller initialized successfully");
    return true;
}

// ==================== 配置文件路径 ====================

std::string PerfLockCaller::getPlatformMappingFile() {
    std::string filename;
    if (mPlatform == Platform::QCOM) {
        filename = "platform_mappings_qcom.xml";
    } else if (mPlatform == Platform::MTK) {
        filename = "platform_mappings_mtk.xml";
    } else {
        TLOGE("Unsupported platform for mapping file");
        return "";
    }

    TLOGD("Platform mapping file: %s", filename.c_str());
    return filename;
}

bool PerfLockCaller::initQcom() {
    TLOGI("Initializing QCOM platform");

    mQcomFuncs.submitRequest = dlsym(RTLD_DEFAULT, "perfmodule_submit_request");

    if (!mQcomFuncs.submitRequest) {
        TLOGE("Failed to find perfmodule_submit_request: %s", dlerror());
        return false;
    }

    TLOGI("QCOM platform initialized");
    return true;
}

/**
 * Query current display refresh rate (QCOM only).
 *
 * Primary path: read /data/vendor/perfd/current_fps written by
 * QCOM perf-hal-service on every VENDOR_HINT_FPS_UPDATE.
 * Mirrors OptsHandlerExtn::DisplayExtn::readFPSFile().
 *
 * Fallback: query IDisplayConfig AIDL directly.
 * Mirrors OptsHandlerExtn::DisplayExtn::getDisplayFPS().
 *
 * Returns BASE_FPS (60) on all failure paths.
 */
int32_t PerfLockCaller::queryDisplayFps() {
    static constexpr int32_t BASE_FPS = 60;
    static constexpr const char *CURRENT_FPS_FILE = "/data/vendor/perfd/current_fps";

    // --- Primary path: read file maintained by QCOM perf-hal-service ---
    FILE *fpsFile = fopen(CURRENT_FPS_FILE, "r");
    if (fpsFile != nullptr) {
        float fps = BASE_FPS;
        int ret = fscanf(fpsFile, "%f", &fps);
        fclose(fpsFile);

        if (ret == 1 && fps >= 1.0f) {
            int32_t ifps = static_cast<int32_t>(fps + 0.5f);
            TLOGD("queryDisplayFps: read from file -> %d Hz", ifps);
            return ifps;
        }
        TLOGW("queryDisplayFps: file read invalid value %.1f, fallback to AIDL", fps);
    } else {
        TLOGW("queryDisplayFps: cannot open %s, fallback to AIDL", CURRENT_FPS_FILE);
    }

    // --- Fallback path: query IDisplayConfig AIDL ---
    static constexpr int64_t NSEC_TO_SEC = 1000000000LL;

    SpAIBinder binder(
        AServiceManager_checkService("vendor.qti.hardware.display.config.IDisplayConfig/default"));
    if (binder.get() == nullptr) {
        TLOGW("queryDisplayFps: IDisplayConfig not available, using BASE_FPS=%d", BASE_FPS);
        return BASE_FPS;
    }

    auto displayConfig = IDisplayConfig::fromBinder(binder);
    if (displayConfig == nullptr) {
        TLOGW("queryDisplayFps: fromBinder failed, using BASE_FPS=%d", BASE_FPS);
        return BASE_FPS;
    }

    int32_t dpyIndex = -1;
    displayConfig->getActiveConfig(DisplayType::PRIMARY, &dpyIndex);
    if (dpyIndex < 0) {
        TLOGW("queryDisplayFps: getActiveConfig failed, using BASE_FPS=%d", BASE_FPS);
        return BASE_FPS;
    }

    Attributes dpyAttr;
    displayConfig->getDisplayAttributes(dpyIndex, DisplayType::PRIMARY, &dpyAttr);
    if (dpyAttr.vsyncPeriod <= 0) {
        TLOGW("queryDisplayFps: invalid vsyncPeriod=%d, using BASE_FPS=%d", dpyAttr.vsyncPeriod,
              BASE_FPS);
        return BASE_FPS;
    }

    int32_t fps = static_cast<int32_t>(
        static_cast<float>(NSEC_TO_SEC) / static_cast<float>(dpyAttr.vsyncPeriod) + 0.5f);
    TLOGI("queryDisplayFps: AIDL vsyncPeriod=%d ns -> %d Hz", dpyAttr.vsyncPeriod, fps);
    return fps;
}

bool PerfLockCaller::initMtk() {
    TLOGI("Initializing MTK platform");

    mMtkFuncs.lockAcq = dlsym(RTLD_DEFAULT, "libpowerhal_LockAcq");
    mMtkFuncs.lockRel = dlsym(RTLD_DEFAULT, "libpowerhal_LockRel");

    if (!mMtkFuncs.lockAcq || !mMtkFuncs.lockRel) {
        TLOGE("Failed to find MTK functions: %s", dlerror());
        return false;
    }

    TLOGI("MTK platform initialized");
    return true;
}

bool PerfLockCaller::initUnisoc() {
    TLOGE("UNISOC platform not implemented");
    return false;
}

// ==================== 核心功能链路 ====================

int32_t PerfLockCaller::acquirePerfLock(const EventContext &ctx) {
    if (!mInitialized) {
        TLOGE("PerfLockCaller not initialized");
        return -1;
    }

    TLOGI("=== acquirePerfLock START: eventId=%d fps=%d pkg='%s' act='%s' intParam1=%d ===",
          ctx.eventId, ctx.intParam0, ctx.package.c_str(), ctx.activity.c_str(), ctx.intParam1);

    // QCOM: use cached display fps for scenario matching.
    // Other platforms: fps=0, falls through to generic P6 config.
    int32_t fps = (mPlatform == Platform::QCOM) ? queryDisplayFps() : 0;
    // Find best-matching scenario config (6-level priority)
    const ScenarioConfig *config = XmlConfigParser::getInstance().getScenarioConfig(
        ctx.eventId, fps, ctx.package, ctx.activity);

    if (!config) {
        TLOGE("No config: eventId=%d param0=%d pkg='%s' act='%s'", ctx.eventId, ctx.intParam0,
              ctx.package.c_str(), ctx.activity.c_str());
        return -1;
    }

#if !PERFENGINE_PRODUCTION
    TLOGI("Matched: id=%d name='%s' fps=%d pkg='%s' act='%s' timeout=%d opcodes=%zu", config->id,
          config->name.c_str(), config->fps, config->package.c_str(), config->activity.c_str(),
          config->timeout, config->platformParams.size() / 2);
#else
    TLOGI("Matched: id=%d fps=%d timeout=%d opcodes=%zu", config->id, config->fps, config->timeout,
          config->platformParams.size() / 2);
#endif

    // platformParams pre-converted at load time — zero conversion cost here
    if (config->platformParams.empty()) {
        TLOGE("No platform params for eventId=%d (all params unmapped on this platform)",
              ctx.eventId);
        return -1;
    }

    int32_t effectiveTimeout = config->timeout;

    int32_t platformHandle = -1;
    switch (mPlatform) {
        case Platform::QCOM:
            platformHandle = qcomAcquirePerfLockWithParams(ctx.eventId, effectiveTimeout,
                                                           config->platformParams, ctx.package);
            break;
        case Platform::MTK:
            platformHandle =
                mtkAcquirePerfLockWithParams(ctx.eventId, effectiveTimeout, config->platformParams);
            break;
        default:
            TLOGE("Unsupported platform");
            return -1;
    }

    if (platformHandle < 0) {
        TLOGE("Platform acquirePerfLock failed: eventId=%d", ctx.eventId);
        return -1;
    }

    TLOGI("=== acquirePerfLock SUCCESS: handle=%d ===", platformHandle);
    return platformHandle;
}

// ==================== QCOM 平台调用 ====================
int32_t PerfLockCaller::qcomAcquirePerfLockWithParams(int32_t eventId, int32_t duration,
                                                      const std::vector<int32_t> &platformParams,
                                                      const std::string &packageName) {
    typedef int (*perfmodule_submit_request_t)(mpctl_msg_t *);
    auto submitRequest = reinterpret_cast<perfmodule_submit_request_t>(mQcomFuncs.submitRequest);

    if (!submitRequest) {
        TLOGE("QCOM submitRequest not initialized");
        return -1;
    }

    if (platformParams.size() > MAX_ARGS_PER_REQUEST) {
        TLOGE("QCOM: Param count %zu exceeds %d", platformParams.size(), MAX_ARGS_PER_REQUEST);
        return -1;
    }

    mpctl_msg_t msg;
    memset(&msg, 0, sizeof(mpctl_msg_t));

    msg.req_type = MPCTL_CMD_PERFLOCKACQ;
    msg.pl_handle = 0;
    msg.pl_time = duration;
    msg.data = static_cast<uint16_t>(platformParams.size());
    msg.client_pid = getpid();
    msg.client_tid = gettid();
    msg.version = 1;
    msg.size = sizeof(mpctl_msg_t);

    for (size_t i = 0; i < platformParams.size() && i < MAX_ARGS_PER_REQUEST; i++) {
        msg.pl_args[i] = platformParams[i];
    }

    if (!packageName.empty()) {
        strncpy(msg.usrdata_str, packageName.c_str(), MAX_MSG_APP_NAME_LEN - 1);
        msg.usrdata_str[MAX_MSG_APP_NAME_LEN - 1] = '\0';
    }
    TLOGI("QCOM perfmodule_submit_request: duration=%dms, numArgs=%u", duration, msg.data);

    int32_t handle = submitRequest(&msg);

    if (handle > 0) {
        TLOGI("QCOM SUCCESS: handle=%d", handle);
        if (duration > 0) {
            startTimeoutTimer(eventId, handle, duration);
        }
    } else {
        TLOGE("QCOM FAILED: handle=%d", handle);
    }

    return handle;
}

void PerfLockCaller::qcomReleasePerfLock(int32_t handle) {
    typedef int (*perfmodule_submit_request_t)(mpctl_msg_t *);
    auto submitRequest = reinterpret_cast<perfmodule_submit_request_t>(mQcomFuncs.submitRequest);

    if (!submitRequest) {
        TLOGE("QCOM submitRequest not initialized");
        return;
    }

    mpctl_msg_t msg;
    memset(&msg, 0, sizeof(mpctl_msg_t));

    msg.req_type = MPCTL_CMD_PERFLOCKREL;
    msg.pl_handle = handle;
    msg.client_pid = getpid();
    msg.client_tid = gettid();
    msg.version = 1;
    msg.size = sizeof(mpctl_msg_t);

    int32_t result = submitRequest(&msg);

    if (result >= 0) {
        TLOGI("QCOM release SUCCESS: handle=%d", handle);
    } else {
        TLOGE("QCOM release FAILED: handle=%d, result=%d", handle, result);
    }

    cancelTimeoutTimer(handle);
}

// ==================== MTK 平台调用 ====================

int32_t PerfLockCaller::mtkAcquirePerfLockWithParams(int32_t eventId, int32_t duration,
                                                     const std::vector<int32_t> &platformParams) {
    typedef int (*mtk_perf_lock_acq_t)(int *, int, int, int, int, int);
    auto lockAcq = reinterpret_cast<mtk_perf_lock_acq_t>(mMtkFuncs.lockAcq);

    if (!lockAcq) {
        TLOGE("MTK lockAcq not initialized");
        return 0;
    }

    if (platformParams.size() % 2 != 0) {
        TLOGE("MTK: Invalid param size %zu (must be even)", platformParams.size());
        return 0;
    }

    if (platformParams.size() > MTK_MAX_ARGS_PER_REQUEST) {
        TLOGE("MTK: Param count %zu exceeds %d", platformParams.size(), MTK_MAX_ARGS_PER_REQUEST);
        return 0;
    }

    std::vector<int32_t> commands = platformParams;

    TLOGI("MTK libpowerhal_LockAcq: timeout=%dms, commands=%zu pairs", duration,
          commands.size() / 2);

    int32_t handle = lockAcq(commands.data(), 0, static_cast<int>(commands.size()), getpid(),
                             gettid(), duration);

    if (handle > 0) {
        TLOGI("MTK SUCCESS: handle=%d", handle);
        if (duration > 0) {
            startTimeoutTimer(eventId, handle, duration);
        }
    } else {
        TLOGE("MTK FAILED: handle=%d", handle);
    }

    return handle;
}

void PerfLockCaller::mtkReleasePerfLock(int32_t handle) {
    typedef int (*mtk_perf_lock_rel_t)(int);
    auto lockRel = reinterpret_cast<mtk_perf_lock_rel_t>(mMtkFuncs.lockRel);

    if (!lockRel) {
        TLOGE("MTK lockRel not initialized");
        return;
    }

    int result = lockRel(handle);

    if (result == 0) {
        TLOGI("MTK release SUCCESS: handle=%d", handle);
    } else {
        TLOGE("MTK release FAILED: handle=%d, result=%d", handle, result);
    }

    cancelTimeoutTimer(handle);
}

// ==================== 公共接口 ====================

void PerfLockCaller::releasePerfLock(int32_t handle) {
    if (!mInitialized) {
        TLOGE("PerfLockCaller not initialized");
        return;
    }

    if (handle <= 0) {
        TLOGW("Invalid handle: %d", handle);
        return;
    }

    switch (mPlatform) {
        case Platform::QCOM:
            qcomReleasePerfLock(handle);
            break;
        case Platform::MTK:
            mtkReleasePerfLock(handle);
            break;
        default:
            TLOGE("Unknown platform");
            break;
    }
}

// ==================== 超时管理 ====================
void PerfLockCaller::startTimeoutTimer(int32_t eventId, int32_t platformHandle, int32_t duration) {
    std::lock_guard<std::mutex> lock(mTimerMutex);

    auto cancelled = std::make_shared<std::atomic<bool>>(false);
    auto cvMutex = std::make_shared<std::mutex>();
    auto cv = std::make_shared<std::condition_variable>();

    auto thread = std::make_shared<std::thread>(
        [this, eventId, platformHandle, duration, cancelled, cvMutex, cv]() {
            std::unique_lock<std::mutex> lk(*cvMutex);

            // Block until timeout OR cancellation — wakes up immediately on cancel
            bool timedOut = !cv->wait_for(lk, std::chrono::milliseconds(duration), [&cancelled]() {
                return cancelled->load(std::memory_order_relaxed);
            });

            if (timedOut && !cancelled->load(std::memory_order_relaxed)) {
                handleTimeout(eventId, platformHandle);
            }
            // If cancelled, just return — join() in cancelTimeoutTimer returns instantly
        });

    TimeoutInfo info;
    info.eventId = eventId;
    info.platformHandle = platformHandle;
    info.duration = duration;
    info.thread = thread;
    info.cancelled = cancelled;
    info.cvMutex = cvMutex;
    info.cv = cv;

    mActiveTimers[platformHandle] = std::move(info);
}

void PerfLockCaller::cancelTimeoutTimer(int32_t platformHandle) {
    std::lock_guard<std::mutex> lock(mTimerMutex);

    auto it = mActiveTimers.find(platformHandle);
    if (it == mActiveTimers.end())
        return;

    // Signal cancellation
    it->second.cancelled->store(true, std::memory_order_relaxed);

    // Wake up the timer thread immediately — it will exit right away
    { std::lock_guard<std::mutex> cvLk(*it->second.cvMutex); }
    it->second.cv->notify_one();

    // join() now returns in microseconds, not 100ms
    if (it->second.thread && it->second.thread->joinable()) {
        it->second.thread->join();
    }

    mActiveTimers.erase(it);
}

void PerfLockCaller::handleTimeout(int32_t eventId, int32_t platformHandle) {
    std::lock_guard<std::mutex> lock(mTimerMutex);

    auto it = mActiveTimers.find(platformHandle);
    if (it != mActiveTimers.end()) {
        TLOGW("Auto-releasing handle %d after timeout", platformHandle);
        mActiveTimers.erase(it);
    }
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
