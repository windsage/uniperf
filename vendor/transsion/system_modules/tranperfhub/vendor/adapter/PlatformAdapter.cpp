#include "PlatformAdapter.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <dlfcn.h>
#include <unistd.h>

#include <cstring>

// QCOM mpctl_msg_t structure (simplified)
// Full definition should match mp-ctl/VendorIPerf.h
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

PlatformAdapter::PlatformAdapter() : mPlatform(PlatformType::UNKNOWN) {
    memset(&mQcomFuncs, 0, sizeof(mQcomFuncs));
    memset(&mMtkFuncs, 0, sizeof(mMtkFuncs));
}

PlatformAdapter::~PlatformAdapter() {}

bool PlatformAdapter::init() {
    // Detect platform
    mPlatform = detectPlatform();

    switch (mPlatform) {
        case PlatformType::QCOM:
            return initQcom();
        case PlatformType::MTK:
            return initMtk();
        case PlatformType::UNISOC:
            return initUnisoc();
        default:
            LOG(ERROR) << "Unknown platform";
            return false;
    }
}

PlatformType PlatformAdapter::detectPlatform() {
    std::string platform = android::base::GetProperty("ro.board.platform", "");

    LOG(INFO) << "Detected ro.board.platform: " << platform;

    // QCOM platform detection
    if (platform.find("qcom") != std::string::npos || platform.find("msm") != std::string::npos ||
        platform.find("sm") != std::string::npos || platform.find("sdm") != std::string::npos ||
        platform.find("kona") != std::string::npos ||
        platform.find("lahaina") != std::string::npos ||
        platform.find("taro") != std::string::npos ||
        platform.find("kalama") != std::string::npos ||
        platform.find("volcano") != std::string::npos ||
        platform.find("pineapple") != std::string::npos) {
        LOG(INFO) << "Platform: QCOM";
        return PlatformType::QCOM;
    }

    // MTK platform detection
    if (platform.find("mt") != std::string::npos) {
        LOG(INFO) << "Platform: MTK";
        return PlatformType::MTK;
    }

    // UNISOC platform detection
    if (platform.find("unisoc") != std::string::npos || platform.find("ums") != std::string::npos ||
        platform.find("sp") != std::string::npos) {
        LOG(INFO) << "Platform: UNISOC";
        return PlatformType::UNISOC;
    }

    LOG(WARNING) << "Platform: UNKNOWN";
    return PlatformType::UNKNOWN;
}

bool PlatformAdapter::initQcom() {
    LOG(INFO) << "Initializing QCOM platform...";

    // Use dlsym(RTLD_DEFAULT) to find function in current process
    // perf-hal-service has already linked libqti-perfd.so
    mQcomFuncs.submitRequest = dlsym(RTLD_DEFAULT, "perfmodule_submit_request");

    if (!mQcomFuncs.submitRequest) {
        LOG(ERROR) << "Failed to find perfmodule_submit_request: " << dlerror();
        return false;
    }

    LOG(INFO) << "QCOM platform initialized successfully (direct call)";
    return true;
}

bool PlatformAdapter::initMtk() {
    LOG(INFO) << "Initializing MTK platform...";

    // Use dlsym(RTLD_DEFAULT) to find functions in current process
    // mtkpower-service has already linked libpowerhal.so
    mMtkFuncs.lockAcq = dlsym(RTLD_DEFAULT, "libpowerhal_LockAcq");
    mMtkFuncs.lockRel = dlsym(RTLD_DEFAULT, "libpowerhal_LockRel");

    if (!mMtkFuncs.lockAcq || !mMtkFuncs.lockRel) {
        LOG(ERROR) << "Failed to find MTK functions: " << dlerror();
        return false;
    }

    LOG(INFO) << "MTK platform initialized successfully (direct call)";
    return true;
}

bool PlatformAdapter::initUnisoc() {
    LOG(INFO) << "Initializing UNISOC platform...";

    // TODO: Implement UNISOC initialization
    LOG(WARNING) << "UNISOC platform not yet implemented";
    return false;
}

int32_t PlatformAdapter::acquirePerfLock(int32_t eventId, int32_t duration,
                                         const std::vector<int32_t> &intParams,
                                         const std::string &packageName) {
    switch (mPlatform) {
        case PlatformType::QCOM:
            return qcomAcquirePerfLock(eventId, duration, intParams, packageName);
        case PlatformType::MTK:
            return mtkAcquirePerfLock(eventId, duration, intParams, packageName);
        case PlatformType::UNISOC:
            LOG(ERROR) << "UNISOC not implemented";
            return -1;
        default:
            LOG(ERROR) << "Unknown platform";
            return -1;
    }
}

void PlatformAdapter::releasePerfLock(int32_t handle) {
    switch (mPlatform) {
        case PlatformType::QCOM:
            qcomReleasePerfLock(handle);
            break;
        case PlatformType::MTK:
            mtkReleasePerfLock(handle);
            break;
        default:
            LOG(ERROR) << "Unknown platform";
            break;
    }
}

int32_t PlatformAdapter::qcomAcquirePerfLock(int32_t eventId, int32_t duration,
                                             const std::vector<int32_t> &intParams,
                                             const std::string &packageName) {
    typedef int (*perfmodule_submit_request_t)(mpctl_msg_t *);
    auto submitRequest = reinterpret_cast<perfmodule_submit_request_t>(mQcomFuncs.submitRequest);

    if (!submitRequest) {
        LOG(ERROR) << "QCOM submitRequest function not initialized";
        return -1;
    }

    // Prepare mpctl message
    mpctl_msg_t msg;
    memset(&msg, 0, sizeof(mpctl_msg_t));

    msg.req_type = MPCTL_CMD_PERFLOCKACQ;
    msg.pl_handle = 0;    // 0 means create new lock
    msg.pl_time = duration;
    msg.client_pid = getpid();
    msg.client_tid = gettid();
    msg.version = 1;
    msg.size = sizeof(mpctl_msg_t);

    // Copy package name
    if (!packageName.empty()) {
        strncpy(msg.usrdata_str, packageName.c_str(), MAX_MSG_APP_NAME_LEN - 1);
        msg.usrdata_str[MAX_MSG_APP_NAME_LEN - 1] = '\0';
    }

    // TODO: Map eventId to QCOM opcodes and fill msg.pl_args
    // For now, use hardcoded test values
    std::vector<int32_t> opcodes;
    mapEventToOpcodes(eventId, opcodes);

    // Copy opcodes to message
    size_t numOpcodes = std::min(opcodes.size(), static_cast<size_t>(MAX_ARGS_PER_REQUEST));
    for (size_t i = 0; i < numOpcodes; i++) {
        msg.pl_args[i] = opcodes[i];
    }
    msg.data = static_cast<uint16_t>(numOpcodes);

    LOG(INFO) << "QCOM acquirePerfLock: eventId=" << eventId << ", duration=" << duration
              << ", numParams=" << msg.data << ", pkg=" << packageName;

    // Direct function call (in-process)
    int32_t handle = submitRequest(&msg);

    LOG(INFO) << "QCOM perfmodule_submit_request returned: " << handle;

    return handle;
}

void PlatformAdapter::qcomReleasePerfLock(int32_t handle) {
    typedef int (*perfmodule_submit_request_t)(mpctl_msg_t *);
    auto submitRequest = reinterpret_cast<perfmodule_submit_request_t>(mQcomFuncs.submitRequest);

    if (!submitRequest) {
        LOG(ERROR) << "QCOM submitRequest function not initialized";
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

    LOG(INFO) << "QCOM releasePerfLock: handle=" << handle;

    submitRequest(&msg);
}

int32_t PlatformAdapter::mtkAcquirePerfLock(int32_t eventId, int32_t duration,
                                            const std::vector<int32_t> &intParams,
                                            const std::string &packageName) {
    typedef int (*mtk_perf_lock_acq_t)(int *, int, int, int, int, int);
    auto lockAcq = reinterpret_cast<mtk_perf_lock_acq_t>(mMtkFuncs.lockAcq);

    if (!lockAcq) {
        LOG(ERROR) << "MTK lockAcq function not initialized";
        return -1;
    }

    // TODO: Map eventId to MTK opcodes
    std::vector<int32_t> opcodes;
    mapEventToOpcodes(eventId, opcodes);

    LOG(INFO) << "MTK acquirePerfLock: eventId=" << eventId << ", duration=" << duration
              << ", numParams=" << opcodes.size() << ", pkg=" << packageName;

    // Direct function call (in-process)
    int32_t handle = lockAcq(opcodes.data(),
                             0,                                   // handle (0 = new)
                             static_cast<int>(opcodes.size()),    // numArgs
                             0,                                   // pid
                             0,                                   // tid
                             duration);

    LOG(INFO) << "MTK libpowerhal_LockAcq returned: " << handle;

    return handle;
}

void PlatformAdapter::mtkReleasePerfLock(int32_t handle) {
    typedef int (*mtk_perf_lock_rel_t)(int);
    auto lockRel = reinterpret_cast<mtk_perf_lock_rel_t>(mMtkFuncs.lockRel);

    if (!lockRel) {
        LOG(ERROR) << "MTK lockRel function not initialized";
        return;
    }

    LOG(INFO) << "MTK releasePerfLock: handle=" << handle;

    lockRel(handle);
}

void PlatformAdapter::mapEventToOpcodes(int32_t eventId, std::vector<int32_t> &opcodes) {
    // TODO: Implement event ID to opcode mapping
    // This will be filled in later with actual opcode tables

    // Placeholder: Use hardcoded test values
    if (mPlatform == PlatformType::QCOM) {
        // QCOM test opcodes
        opcodes.push_back(0x40800000);    // CPU Cluster0 Min Freq opcode
        opcodes.push_back(1400000);       // 1.4GHz
        opcodes.push_back(0x40800100);    // CPU Cluster1 Min Freq opcode
        opcodes.push_back(1800000);       // 1.8GHz
    } else if (mPlatform == PlatformType::MTK) {
        // MTK test opcodes
        opcodes.push_back(0x00001100);    // CPU Cluster0 Min Freq opcode
        opcodes.push_back(1400000);       // 1.4GHz
        opcodes.push_back(0x00001200);    // CPU Cluster1 Min Freq opcode
        opcodes.push_back(1800000);       // 1.8GHz
    }

    LOG(INFO) << "mapEventToOpcodes: eventId=" << eventId << ", mapped to " << opcodes.size()
              << " opcodes (placeholder)";
}

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor
