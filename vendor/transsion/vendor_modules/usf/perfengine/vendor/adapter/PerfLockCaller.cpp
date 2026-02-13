#include "PerfLockCaller.h"

#include <android-base/properties.h>
#include <dlfcn.h>
#include <unistd.h>

#include <chrono>
#include <cstring>

#include "ParamMapper.h"
#include "PerfEngineTypes.h"
#include "TranLog.h"
#include "XmlConfigParser.h"

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

    TLOGI("=== acquirePerfLock START: eventId=%d fps=%d pkg='%s' act='%s' duration=%d ===",
          ctx.eventId, ctx.fps, ctx.package.c_str(), ctx.activity.c_str(), ctx.duration);

    // Find best-matching scenario config (6-level priority)
    const ScenarioConfig *config = XmlConfigParser::getInstance().getScenarioConfig(
        ctx.eventId, ctx.fps, ctx.package, ctx.activity);

    if (!config) {
        TLOGE("No config: eventId=%d fps=%d pkg='%s' act='%s'", ctx.eventId, ctx.fps,
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

    // Config timeout takes priority; fall back to caller-supplied duration
    int32_t effectiveTimeout = (config->timeout > 0) ? config->timeout : ctx.duration;

    int32_t platformHandle = -1;
    switch (mPlatform) {
        case Platform::QCOM:
            platformHandle = qcomAcquirePerfLockWithParams(ctx.eventId, effectiveTimeout,
                                                           config->platformParams);
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
                                                      const std::vector<int32_t> &platformParams) {
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

    auto thread =
        std::make_shared<std::thread>([this, eventId, platformHandle, duration, cancelled]() {
            auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(duration);

            while (std::chrono::steady_clock::now() < deadline) {
                if (cancelled->load()) {
                    return;
                }
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }

            if (!cancelled->load()) {
                handleTimeout(eventId, platformHandle);
            }
        });

    TimeoutInfo info;
    info.eventId = eventId;
    info.platformHandle = platformHandle;
    info.duration = duration;
    info.thread = thread;
    info.cancelled = cancelled;

    mActiveTimers[platformHandle] = info;
}

void PerfLockCaller::cancelTimeoutTimer(int32_t platformHandle) {
    std::lock_guard<std::mutex> lock(mTimerMutex);

    auto it = mActiveTimers.find(platformHandle);
    if (it == mActiveTimers.end()) {
        return;
    }

    if (it->second.cancelled) {
        it->second.cancelled->store(true);
    }

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
