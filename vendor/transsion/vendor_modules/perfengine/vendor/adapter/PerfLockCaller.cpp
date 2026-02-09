#include "PerfLockCaller.h"

#include <android-base/properties.h>
#include <dlfcn.h>
#include <unistd.h>

#include <chrono>
#include <cstring>

#include "PerfEngineType.h"
#include "TranLog.h"

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

    // 取消所有活跃的超时定时器并释放锁
    std::lock_guard<std::mutex> lock(mTimerMutex);
    for (auto &pair : mActiveTimers) {
        // 取消定时器
        if (pair.second.cancelled) {
            pair.second.cancelled->store(true);
        }

        if (pair.second.thread && pair.second.thread->joinable()) {
            pair.second.thread->join();
        }

        // 释放锁
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

    // 1. 检测平台
    mPlatform = PlatformDetector::getInstance().getPlatform();
    if (mPlatform == Platform::UNKNOWN) {
        TLOGE("Failed to detect platform");
        return false;
    }

    // 2. 初始化平台函数
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

    mInitialized = true;
    TLOGI("PerfLockCaller initialized successfully");
    return true;
}

bool PerfLockCaller::initQcom() {
    TLOGI("Initializing QCOM platform");

    // 使用 dlsym(RTLD_DEFAULT) 查找当前进程中的函数
    // perf-hal-service 已经链接了 libqti-perfd.so,函数已在进程空间
    mQcomFuncs.submitRequest = dlsym(RTLD_DEFAULT, "perfmodule_submit_request");

    if (!mQcomFuncs.submitRequest) {
        TLOGE("Failed to find perfmodule_submit_request: %s", dlerror());
        return false;
    }

    TLOGI("QCOM platform initialized (found perfmodule_submit_request)");
    return true;
}

bool PerfLockCaller::initMtk() {
    TLOGI("Initializing MTK platform");

    // 使用 dlsym(RTLD_DEFAULT) 查找当前进程中的函数
    // mtkpower-service 已经链接了 libpowerhal.so,函数已在进程空间
    mMtkFuncs.lockAcq = dlsym(RTLD_DEFAULT, "libpowerhal_LockAcq");
    mMtkFuncs.lockRel = dlsym(RTLD_DEFAULT, "libpowerhal_LockRel");

    if (!mMtkFuncs.lockAcq || !mMtkFuncs.lockRel) {
        TLOGE("Failed to find MTK functions: %s", dlerror());
        return false;
    }

    TLOGI("MTK platform initialized (found libpowerhal_LockAcq/Rel)");
    return true;
}

bool PerfLockCaller::initUnisoc() {
    TLOGI("Initializing UNISOC platform...");
    TLOGW("UNISOC platform not yet implemented");
    return false;
}

// ==================== 公共接口 ====================

int32_t PerfLockCaller::acquirePerfLock(int32_t eventId, int32_t duration,
                                        const std::vector<int32_t> &intParams,
                                        const std::string &packageName) {
    if (!mInitialized) {
        TLOGE("PerfLockCaller not initialized");
        return -1;
    }

    TLOGI("acquirePerfLock: eventId=%d, duration=%d, pkg=%s", eventId, duration,
          packageName.c_str());

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

void PerfLockCaller::releasePerfLock(int32_t handle) {
    if (!mInitialized) {
        TLOGE("PerfLockCaller not initialized");
        return;
    }

    if (handle <= 0) {
        TLOGW("Invalid handle: %d", handle);
        return;
    }

    TLOGI("releasePerfLock: handle=%d", handle);

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

// ==================== QCOM 平台实现 ====================

int32_t PerfLockCaller::qcomAcquirePerfLock(int32_t eventId, int32_t duration,
                                            const std::vector<int32_t> &intParams,
                                            const std::string &packageName) {
    // QCOM 函数签名: int perfmodule_submit_request(mpctl_msg_t*)
    typedef int (*perfmodule_submit_request_t)(mpctl_msg_t *);
    auto submitRequest = reinterpret_cast<perfmodule_submit_request_t>(mQcomFuncs.submitRequest);

    if (!submitRequest) {
        TLOGE("QCOM submitRequest function not initialized");
        return -1;
    }

    // TODO: 这里需要将 intParams 转换为 QCOM opcodes
    // 目前简化实现,直接使用 intParams (假设已经是 opcodes)
    if (intParams.size() > MAX_ARGS_PER_REQUEST) {
        TLOGE("QCOM: Parameter count %zu exceeds limit %d", intParams.size(), MAX_ARGS_PER_REQUEST);
        return -1;
    }

    // 构造 mpctl_msg_t
    mpctl_msg_t msg;
    memset(&msg, 0, sizeof(mpctl_msg_t));

    msg.req_type = MPCTL_CMD_PERFLOCKACQ;
    msg.pl_handle = 0;    // 0 表示新锁
    msg.pl_time = duration;
    msg.data = static_cast<uint16_t>(intParams.size());
    msg.client_pid = getpid();
    msg.client_tid = gettid();
    msg.renewFlag = false;
    msg.offloadFlag = false;
    msg.app_workload_type = 0;
    msg.app_pid = 0;
    msg.version = 1;
    msg.size = sizeof(mpctl_msg_t);

    // 拷贝参数 (opcodes)
    for (size_t i = 0; i < intParams.size() && i < MAX_ARGS_PER_REQUEST; i++) {
        msg.pl_args[i] = intParams[i];
    }

    // 拷贝包名
    if (!packageName.empty()) {
        strncpy(msg.usrdata_str, packageName.c_str(), MAX_MSG_APP_NAME_LEN - 1);
        msg.usrdata_str[MAX_MSG_APP_NAME_LEN - 1] = '\0';
    }

    TLOGI("QCOM perfmodule_submit_request: duration=%dms, numArgs=%u", duration, msg.data);
    TLOGD("QCOM opcodes[0-3]: 0x%08x 0x%08x 0x%08x 0x%08x", intParams.size() > 0 ? intParams[0] : 0,
          intParams.size() > 1 ? intParams[1] : 0, intParams.size() > 2 ? intParams[2] : 0,
          intParams.size() > 3 ? intParams[3] : 0);

    // 调用 QCOM API
    int32_t handle = submitRequest(&msg);

    if (handle > 0) {
        TLOGI("QCOM perfmodule_submit_request SUCCESS: handle=%d", handle);

        // 启动超时定时器
        if (duration > 0) {
            startTimeoutTimer(eventId, handle, duration);
        }
    } else if (handle == -3) {
        TLOGE("QCOM perfmodule_submit_request FAILED: target init not complete");
    } else {
        TLOGE("QCOM perfmodule_submit_request FAILED: handle=%d", handle);
    }

    return handle;
}

void PerfLockCaller::qcomReleasePerfLock(int32_t handle) {
    typedef int (*perfmodule_submit_request_t)(mpctl_msg_t *);
    auto submitRequest = reinterpret_cast<perfmodule_submit_request_t>(mQcomFuncs.submitRequest);

    if (!submitRequest) {
        TLOGE("QCOM submitRequest function not initialized");
        return;
    }

    TLOGI("QCOM Lock Release: handle=%d", handle);

    // 准备 mpctl_msg_t 用于释放
    mpctl_msg_t msg;
    memset(&msg, 0, sizeof(mpctl_msg_t));

    msg.req_type = MPCTL_CMD_PERFLOCKREL;
    msg.pl_handle = handle;
    msg.client_pid = getpid();
    msg.client_tid = gettid();
    msg.version = 1;
    msg.size = sizeof(mpctl_msg_t);

    // 调用 perfmodule_submit_request
    int32_t result = submitRequest(&msg);

    if (result >= 0) {
        TLOGI("QCOM perfmodule_submit_request (release) SUCCESS: handle=%d", handle);
    } else {
        TLOGE("QCOM perfmodule_submit_request (release) FAILED: handle=%d, result=%d", handle,
              result);
    }

    // 取消超时定时器
    cancelTimeoutTimer(handle);
}

// ==================== 超时管理 ====================

void PerfLockCaller::startTimeoutTimer(int32_t eventId, int32_t platformHandle, int32_t duration) {
    std::lock_guard<std::mutex> lock(mTimerMutex);

    // 创建取消标志
    auto cancelled = std::make_shared<std::atomic<bool>>(false);

    // 创建超时线程
    auto thread =
        std::make_shared<std::thread>([this, eventId, platformHandle, duration, cancelled]() {
            auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(duration);

            // 每 100ms 检查一次取消标志
            while (std::chrono::steady_clock::now() < deadline) {
                if (cancelled->load()) {
                    TLOGD("Timeout thread cancelled for handle=%d", platformHandle);
                    return;
                }
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }

            // 超时到达
            if (!cancelled->load()) {
                TLOGW("Timeout reached for handle=%d", platformHandle);
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

    // 设置取消标志
    if (it->second.cancelled) {
        it->second.cancelled->store(true);
    }

    // 等待线程结束
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

        // 底层 perflock 已经超时释放,这里只清理映射
        mActiveTimers.erase(it);
    }
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
