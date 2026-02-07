// vendor/transsion/system_modules/tranperfhub/vendor/adapter/PlatformAdapter.cpp

#define LOG_TAG "PerfHub-PlatformAdapter"

#include "PlatformAdapter.h"
#include "TranLog.h"
#include "XmlConfigParser.h"
#include "ParamMapper.h"

#include <android-base/properties.h>
#include <dlfcn.h>
#include <unistd.h>
#include <cstring>
#include <chrono>

// QCOM mpctl_msg_t structure (from mp-ctl/VendorIPerf.h)
#define MAX_ARGS_PER_REQUEST 64
#define MAX_MSG_APP_NAME_LEN 128

struct mpctl_msg_t {
    uint16_t data;                             // Number of parameters
    int32_t pl_handle;                         // Performance lock handle
    uint8_t req_type;                          // Command type
    int32_t pl_time;                           // Duration in milliseconds
    int32_t pl_args[MAX_ARGS_PER_REQUEST];     // Parameter array
    int32_t reservedArgs[32];                  // Reserved arguments
    int32_t numRArgs;                          // Number of reserved args
    pid_t client_pid;                          // Client PID
    pid_t client_tid;                          // Client TID
    uint32_t hint_id;                          // Hint ID
    int32_t hint_type;                         // Hint type
    void *userdata;                            // User data pointer
    char usrdata_str[MAX_MSG_APP_NAME_LEN];    // Package name
    char propDefVal[92];                       // Property default value
    bool renewFlag;                            // Renew flag
    bool offloadFlag;                          // Offload flag
    int32_t app_workload_type;                 // App workload type
    int32_t app_pid;                           // App PID
    int16_t version;                           // Message version
    int16_t size;                              // Message size
};

#define MPCTL_CMD_PERFLOCKACQ 0
#define MPCTL_CMD_PERFLOCKREL 1

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

using vendor::transsion::perfhub::XmlConfigParser;
using vendor::transsion::perfhub::ParamMapper;
using vendor::transsion::perfhub::ScenarioConfig;
using vendor::transsion::perfhub::Platform;

// ====================== Constructor / Destructor ======================

PlatformAdapter::PlatformAdapter() 
    : mPlatform(Platform::UNKNOWN),
      mInitialized(false) {
    memset(&mQcomFuncs, 0, sizeof(mQcomFuncs));
    memset(&mMtkFuncs, 0, sizeof(mMtkFuncs));
}

PlatformAdapter::~PlatformAdapter() {
    TLOGI("Destroying PlatformAdapter");
    
    // Cancel all active timers and release locks
    std::lock_guard<std::mutex> lock(mLocksMutex);
    for (auto& pair : mActiveLocks) {
        // Cancel timer
        {
            std::lock_guard<std::mutex> timerLock(pair.second.timerMutex);
            pair.second.timerCanceled = true;
        }
        pair.second.timerCv.notify_all();
        
        if (pair.second.timerThread.joinable()) {
            pair.second.timerThread.join();
        }
        
        // Release lock
        if (pair.second.platformHandle > 0) {
            if (mPlatform == Platform::QCOM) {
                qcomReleasePerfLock(pair.second.platformHandle);
            } else if (mPlatform == Platform::MTK) {
                mtkReleasePerfLock(pair.second.platformHandle);
            }
        }
    }
    mActiveLocks.clear();
}

// ====================== Initialization ======================

bool PlatformAdapter::init() {
    if (mInitialized) {
        TLOGW("PlatformAdapter already initialized");
        return true;
    }
    
    TLOGI("Initializing PlatformAdapter");
    
    // 1. Detect platform
    mPlatform = PlatformDetector::detect();
    if (mPlatform == Platform::UNKNOWN) {
        TLOGE("Failed to detect platform");
        return false;
    }
    
    // 2. Initialize platform-specific functions
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
    
    // 3. Initialize XmlConfigParser
    mConfigParser = std::make_unique<XmlConfigParser>();
    
    std::string configFile;
    if (mPlatform == Platform::QCOM) {
        // TODO: Get chip name from property, use default for now
        configFile = "perfhub_config_pineapple.xml";
    } else if (mPlatform == Platform::MTK) {
        configFile = "perfhub_config_mt6878.xml";
    }
    
    if (!mConfigParser->loadConfig(configFile)) {
        TLOGE("Failed to load scenario config: %s", configFile.c_str());
        return false;
    }
    TLOGI("Loaded scenario config: %zu scenarios", mConfigParser->getScenarioCount());
    
    // 4. Initialize ParamMapper
    mParamMapper = std::make_unique<ParamMapper>();
    
    Platform mappingPlatform;
    if (mPlatform == Platform::QCOM) {
        mappingPlatform = Platform::QCOM;
    } else if (mPlatform == Platform::MTK) {
        mappingPlatform = Platform::MTK;
    } else {
        mappingPlatform = Platform::UNKNOWN;
    }
    
    if (!mParamMapper->init(mappingPlatform)) {
        TLOGE("Failed to initialize ParamMapper");
        return false;
    }
    TLOGI("Loaded parameter mappings: %zu params", mParamMapper->getMappingCount());
    
    mInitialized = true;
    TLOGI("PlatformAdapter initialized successfully");
    return true;
}

bool PlatformAdapter::initQcom() {
    TLOGI("Initializing QCOM platform...");

    // Use dlsym(RTLD_DEFAULT) to find function in current process
    // perf-hal-service has already linked libqti-perfd.so
    mQcomFuncs.submitRequest = dlsym(RTLD_DEFAULT, "perfmodule_submit_request");

    if (!mQcomFuncs.submitRequest) {
        TLOGE("Failed to find perfmodule_submit_request: %s", dlerror());
        return false;
    }

    TLOGI("QCOM platform initialized successfully (direct call)");
    return true;
}

bool PlatformAdapter::initMtk() {
    TLOGI("Initializing MTK platform...");

    // Use dlsym(RTLD_DEFAULT) to find functions in current process
    // mtkpower-service has already linked libpowerhal.so
    mMtkFuncs.lockAcq = dlsym(RTLD_DEFAULT, "libpowerhal_LockAcq");
    mMtkFuncs.lockRel = dlsym(RTLD_DEFAULT, "libpowerhal_LockRel");

    if (!mMtkFuncs.lockAcq || !mMtkFuncs.lockRel) {
        TLOGE("Failed to find MTK functions: %s", dlerror());
        return false;
    }

    TLOGI("MTK platform initialized successfully (direct call)");
    return true;
}

bool PlatformAdapter::initUnisoc() {
    TLOGI("Initializing UNISOC platform...");
    TLOGW("UNISOC platform not yet implemented");
    return false;
}

// ====================== Public Interface ======================

int32_t PlatformAdapter::acquirePerfLock(int32_t eventId, int32_t duration,
                                         const std::vector<int32_t> &intParams,
                                         const std::string &packageName) {
    if (!mInitialized) {
        TLOGE("PlatformAdapter not initialized");
        return -1;
    }
    
    TLOGI("acquirePerfLock: eventId=%d, duration=%d, pkg=%s",
          eventId, duration, packageName.c_str());
    
    int32_t platformHandle = -1;
    
    switch (mPlatform) {
        case Platform::QCOM:
            platformHandle = qcomAcquirePerfLock(eventId, duration, intParams, packageName);
            break;
        case Platform::MTK:
            platformHandle = mtkAcquirePerfLock(eventId, duration, intParams, packageName);
            break;
        case Platform::UNISOC:
            TLOGE("UNISOC not implemented");
            return -1;
        default:
            TLOGE("Unknown platform");
            return -1;
    }
    
    if (platformHandle < 0) {
        TLOGE("Failed to acquire perf lock");
        return -1;
    }
    
    TLOGI("Perf lock acquired: handle=%d", platformHandle);
    return platformHandle;
}

void PlatformAdapter::releasePerfLock(int32_t handle) {
    if (!mInitialized) {
        TLOGE("PlatformAdapter not initialized");
        return;
    }
    
    TLOGI("releasePerfLock: handle=%d", handle);
    
    // Cancel timeout timer if exists
    cancelTimeoutTimer(handle);
    
    // Release platform lock
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
    
    // Remove from tracking
    {
        std::lock_guard<std::mutex> lock(mLocksMutex);
        auto it = mActiveLocks.find(handle);
        if (it != mActiveLocks.end()) {
            int64_t duration = (getCurrentTimeNs() - it->second.startTime) / 1000000;
            TLOGI("Lock released: handle=%d, duration=%lld ms (timeout was %d ms)",
                  handle, static_cast<long long>(duration), it->second.timeout);
            mActiveLocks.erase(it);
        }
    }
}

// ====================== QCOM Implementation ======================

int32_t PlatformAdapter::qcomAcquirePerfLock(int32_t eventId, int32_t duration,
                                             const std::vector<int32_t> &intParams,
                                             const std::string &packageName) {
    typedef int (*perfmodule_submit_request_t)(mpctl_msg_t *);
    auto submitRequest = reinterpret_cast<perfmodule_submit_request_t>(mQcomFuncs.submitRequest);

    if (!submitRequest) {
        TLOGE("QCOM submitRequest function not initialized");
        return -1;
    }

    // Extract current FPS from intParams
    int32_t currentFps = extractCurrentFps(intParams);
    
    // Convert scenario to opcodes using XML config + ParamMapper
    std::vector<int32_t> opcodes;
    int32_t effectiveTimeout = duration;
    
    if (!convertScenarioToOpcodes(eventId, currentFps, opcodes, effectiveTimeout)) {
        TLOGE("Failed to convert scenario to opcodes: eventId=%d", eventId);
        return -1;
    }
    
    TLOGI("QCOM: eventId=%d, fps=%d, timeout=%d, opcodes=%zu",
          eventId, currentFps, effectiveTimeout, opcodes.size());

    // Prepare mpctl message
    mpctl_msg_t msg;
    memset(&msg, 0, sizeof(mpctl_msg_t));

    msg.req_type = MPCTL_CMD_PERFLOCKACQ;
    msg.pl_handle = 0;    // 0 means create new lock
    msg.pl_time = effectiveTimeout;
    msg.client_pid = getpid();
    msg.client_tid = gettid();
    msg.version = 1;
    msg.size = sizeof(mpctl_msg_t);

    // Copy package name
    if (!packageName.empty()) {
        strncpy(msg.usrdata_str, packageName.c_str(), MAX_MSG_APP_NAME_LEN - 1);
        msg.usrdata_str[MAX_MSG_APP_NAME_LEN - 1] = '\0';
    }

    // Copy opcodes to message
    size_t numOpcodes = std::min(opcodes.size(), static_cast<size_t>(MAX_ARGS_PER_REQUEST));
    for (size_t i = 0; i < numOpcodes; i++) {
        msg.pl_args[i] = opcodes[i];
    }
    msg.data = static_cast<uint16_t>(numOpcodes);

    // Call QCOM perfmodule_submit_request (direct in-process call)
    int32_t handle = submitRequest(&msg);

    TLOGI("QCOM perfmodule_submit_request returned: handle=%d", handle);

    if (handle > 0 && effectiveTimeout > 0) {
        // Start timeout timer
        startTimeoutTimer(eventId, handle, effectiveTimeout);
    }

    return handle;
}

void PlatformAdapter::qcomReleasePerfLock(int32_t handle) {
    typedef int (*perfmodule_submit_request_t)(mpctl_msg_t *);
    auto submitRequest = reinterpret_cast<perfmodule_submit_request_t>(mQcomFuncs.submitRequest);

    if (!submitRequest) {
        TLOGE("QCOM submitRequest function not initialized");
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

    TLOGI("QCOM: Releasing lock handle=%d", handle);
    submitRequest(&msg);
}

// ====================== MTK Implementation ======================

int32_t PlatformAdapter::mtkAcquirePerfLock(int32_t eventId, int32_t duration,
                                            const std::vector<int32_t> &intParams,
                                            const std::string &packageName) {
    typedef int (*mtk_perf_lock_acq_t)(int *, int, int, int, int, int);
    auto lockAcq = reinterpret_cast<mtk_perf_lock_acq_t>(mMtkFuncs.lockAcq);

    if (!lockAcq) {
        TLOGE("MTK lockAcq function not initialized");
        return -1;
    }

    // Extract current FPS from intParams
    int32_t currentFps = extractCurrentFps(intParams);
    
    // Convert scenario to commands using XML config + ParamMapper
    std::vector<int32_t> commands;
    int32_t effectiveTimeout = duration;
    
    if (!convertScenarioToOpcodes(eventId, currentFps, commands, effectiveTimeout)) {
        TLOGE("Failed to convert scenario to commands: eventId=%d", eventId);
        return -1;
    }
    
    TLOGI("MTK: eventId=%d, fps=%d, timeout=%d, commands=%zu",
          eventId, currentFps, effectiveTimeout, commands.size());

    // Call MTK libpowerhal_LockAcq (direct in-process call)
    int32_t handle = lockAcq(
        commands.data(),
        0,                                   // handle (0 = new)
        static_cast<int>(commands.size()),   // numArgs
        0,                                   // pid
        0,                                   // tid
        effectiveTimeout
    );

    TLOGI("MTK libpowerhal_LockAcq returned: handle=%d", handle);

    if (handle > 0 && effectiveTimeout > 0) {
        // Start timeout timer
        startTimeoutTimer(eventId, handle, effectiveTimeout);
    }

    return handle;
}

void PlatformAdapter::mtkReleasePerfLock(int32_t handle) {
    typedef int (*mtk_perf_lock_rel_t)(int);
    auto lockRel = reinterpret_cast<mtk_perf_lock_rel_t>(mMtkFuncs.lockRel);

    if (!lockRel) {
        TLOGE("MTK lockRel function not initialized");
        return;
    }

    TLOGI("MTK: Releasing lock handle=%d", handle);
    lockRel(handle);
}

// ====================== Scenario Conversion ======================

bool PlatformAdapter::convertScenarioToOpcodes(int32_t eventId, int32_t currentFps,
                                                std::vector<int32_t> &opcodes,
                                                int32_t &effectiveTimeout) {
    // 1. Get scenario configuration from XML
    const ScenarioConfig* config = mConfigParser->getScenarioConfig(eventId, currentFps);
    if (!config) {
        TLOGE("Scenario config not found: eventId=%d, fps=%d", eventId, currentFps);
        return false;
    }
    
    TLOGI("Found scenario: id=%d, name='%s', fps=%d, timeout=%d, params=%zu",
          config->id, config->name.c_str(), config->fps, 
          config->timeout, config->params.size());
    
    // 2. Use timeout from XML config
    effectiveTimeout = config->timeout;
    
    // 3. Convert params to platform opcodes
    opcodes.reserve(config->params.size() * 2);  // opcode + value pairs
    
    int validParamCount = 0;
    for (const auto& param : config->params) {
        int32_t opcode = mParamMapper->getOpcode(param.name);
        if (opcode < 0) {
            TLOGD("Skip unsupported param: %s (platform-specific or unavailable)", 
                  param.name.c_str());
            continue;
        }
        
        opcodes.push_back(opcode);
        opcodes.push_back(param.value);
        validParamCount++;
        
        TLOGD("  Map: %s=%d -> opcode=0x%08X", 
              param.name.c_str(), param.value, opcode);
    }
    
    if (validParamCount == 0) {
        TLOGW("No valid parameters to apply for eventId=%d", eventId);
        return false;
    }
    
    TLOGI("Converted %d/%zu params to opcodes", validParamCount, config->params.size());
    return true;
}

int32_t PlatformAdapter::extractCurrentFps(const std::vector<int32_t> &intParams) {
    // AIDL parameter format: [duration, fps, ...]
    // intParams[0] = duration (already passed separately)
    // intParams[1] = fps (if available)
    
    if (intParams.size() >= 2) {
        int32_t fps = intParams[1];
        TLOGD("Extracted FPS from intParams: %d", fps);
        return fps;
    }
    
    TLOGD("No FPS in intParams, using default 0");
    return 0;
}

// ====================== Timeout Timer Implementation ======================

void PlatformAdapter::startTimeoutTimer(int32_t eventId, int32_t platformHandle, 
                                        int32_t timeoutMs) {
    TLOGD("Starting timeout timer: eventId=%d, handle=%d, timeout=%d ms",
          eventId, platformHandle, timeoutMs);
    
    std::lock_guard<std::mutex> lock(mLocksMutex);
    
    EventLock& eventLock = mActiveLocks[platformHandle];
    eventLock.platformHandle = platformHandle;
    eventLock.eventId = eventId;
    eventLock.timeout = timeoutMs;
    eventLock.startTime = getCurrentTimeNs();
    eventLock.timerCanceled = false;
    
    // Create timer thread
    eventLock.timerThread = std::thread([this, eventId, platformHandle, timeoutMs]() {
        EventLock* lockPtr = nullptr;
        
        // Get lock pointer
        {
            std::lock_guard<std::mutex> lock(mLocksMutex);
            auto it = mActiveLocks.find(platformHandle);
            if (it != mActiveLocks.end()) {
                lockPtr = &it->second;
            }
        }
        
        if (!lockPtr) {
            TLOGE("Timer: Event lock not found: handle=%d", platformHandle);
            return;
        }
        
        // Wait for timeout or cancellation
        std::unique_lock<std::mutex> timerLock(lockPtr->timerMutex);
        bool canceled = lockPtr->timerCv.wait_for(
            timerLock,
            std::chrono::milliseconds(timeoutMs),
            [lockPtr]() { return lockPtr->timerCanceled.load(); }
        );
        
        if (canceled) {
            TLOGD("Timer canceled: handle=%d (End event received)", platformHandle);
            return;
        }
        
        // Timeout occurred - trigger auto-release
        TLOGW("Timer EXPIRED: eventId=%d, handle=%d, timeout=%d ms (End event NOT received)", 
              eventId, platformHandle, timeoutMs);
        onTimeout(eventId, platformHandle);
    });
}

void PlatformAdapter::cancelTimeoutTimer(int32_t platformHandle) {
    TLOGD("Canceling timeout timer: handle=%d", platformHandle);
    
    std::lock_guard<std::mutex> lock(mLocksMutex);
    
    auto it = mActiveLocks.find(platformHandle);
    if (it == mActiveLocks.end()) {
        TLOGD("No active timer to cancel: handle=%d", platformHandle);
        return;
    }
    
    EventLock& eventLock = it->second;
    
    // Signal timer thread to cancel
    {
        std::lock_guard<std::mutex> timerLock(eventLock.timerMutex);
        eventLock.timerCanceled = true;
    }
    eventLock.timerCv.notify_all();
    
    // Wait for timer thread to finish
    if (eventLock.timerThread.joinable()) {
        eventLock.timerThread.join();
        TLOGD("Timer thread joined: handle=%d", platformHandle);
    }
}

void PlatformAdapter::onTimeout(int32_t eventId, int32_t platformHandle) {
    TLOGW("AUTO-RELEASE triggered: eventId=%d, handle=%d (timeout expired)", 
          eventId, platformHandle);
    
    // Release platform lock
    if (mPlatform == Platform::QCOM) {
        qcomReleasePerfLock(platformHandle);
    } else if (mPlatform == Platform::MTK) {
        mtkReleasePerfLock(platformHandle);
    }
    
    // Remove from tracking
    {
        std::lock_guard<std::mutex> lock(mLocksMutex);
        auto it = mActiveLocks.find(platformHandle);
        if (it != mActiveLocks.end()) {
            int64_t duration = (getCurrentTimeNs() - it->second.startTime) / 1000000;
            TLOGW("Event auto-released after %lld ms (timeout=%d ms, End event missing)",
                  static_cast<long long>(duration), it->second.timeout);
            mActiveLocks.erase(it);
        }
    }
}

int64_t PlatformAdapter::getCurrentTimeNs() {
    auto now = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        now.time_since_epoch()).count();
}

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor
