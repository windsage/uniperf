#ifndef PERF_LOCK_CALLER_H
#define PERF_LOCK_CALLER_H

#include <sys/types.h>
#include <unistd.h>

#include <atomic>
#include <cstdint>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "EventContext.h"
#include "PerfEngineTypes.h"

namespace vendor {
namespace transsion {
namespace perfengine {

#define MPCTL_CMD_PERFLOCKACQ 2
#define MPCTL_CMD_PERFLOCKREL 3
#define MAX_ARGS_PER_REQUEST 64
#define MAX_RESERVE_ARGS_PER_REQUEST 32
#define MAX_MSG_APP_NAME_LEN 128
#define MTK_MAX_ARGS_PER_REQUEST 600

struct mpctl_msg_t {
    uint16_t data;        // Number of parameters in pl_args
    int32_t pl_handle;    // Performance lock handle (0 for new)
    uint8_t req_type;     // Command type (MPCTL_CMD_xxx)
    int32_t pl_time;      // Duration in milliseconds (0=indefinite)
    int32_t
        pl_args[MAX_ARGS_PER_REQUEST];    // Parameter array [opcode1, value1, opcode2, value2, ...]
    int32_t reservedArgs[MAX_RESERVE_ARGS_PER_REQUEST];    // Reserved for tid/pid/flags
    int32_t numRArgs;                                      // Number of reserved args
    pid_t client_pid;                                      // Client process ID
    pid_t client_tid;                                      // Client thread ID
    uint32_t hint_id;                          // Hint ID (for MPCTL_CMD_PERFLOCKHINTACQ)
    int32_t hint_type;                         // Hint type
    void *userdata;                            // User data pointer (used internally by mp-ctl)
    char usrdata_str[MAX_MSG_APP_NAME_LEN];    // Package name string
    char propDefVal[92];                       // Property default value (for queries)
    bool renewFlag;                            // Renew existing lock flag
    bool offloadFlag;                          // Offload to thread pool flag
    int32_t app_workload_type;                 // App workload type hint
    int32_t app_pid;                           // App PID
    int16_t version;                           // Message version (use 1)
    int16_t size;                              // Message size
};

class PerfLockCaller {
public:
    PerfLockCaller();
    ~PerfLockCaller();

    bool init();

    int32_t acquirePerfLock(const EventContext &ctx);

    void releasePerfLock(int32_t handle);

private:
    Platform mPlatform;
    bool mInitialized;

    bool initQcom();
    bool initMtk();
    bool initUnisoc();

    std::string getPlatformMappingFile();

    struct {
        void *submitRequest;
    } mQcomFuncs;

    int32_t qcomAcquirePerfLockWithParams(int32_t eventId, int32_t duration,
                                          const std::vector<int32_t> &platformParams);

    void qcomReleasePerfLock(int32_t handle);

    struct {
        void *lockAcq;
        void *lockRel;
    } mMtkFuncs;

    int32_t mtkAcquirePerfLockWithParams(int32_t eventId, int32_t duration,
                                         const std::vector<int32_t> &platformParams);

    void mtkReleasePerfLock(int32_t handle);

    struct TimeoutInfo {
        int32_t eventId;
        int32_t platformHandle;
        int32_t duration;
        std::shared_ptr<std::thread> thread;
        std::shared_ptr<std::atomic<bool>> cancelled;
    };

    void startTimeoutTimer(int32_t eventId, int32_t platformHandle, int32_t duration);
    void cancelTimeoutTimer(int32_t platformHandle);
    void handleTimeout(int32_t eventId, int32_t platformHandle);

    std::map<int32_t, TimeoutInfo> mActiveTimers;
    std::mutex mTimerMutex;
};

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor

#endif    // PERF_LOCK_CALLER_H
