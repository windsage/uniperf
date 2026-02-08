#ifndef PERF_LOCK_CALLER_H
#define PERF_LOCK_CALLER_H

#define LOG_TAG "PerfHub-PerfLockCaller"

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

// ==================== QCOM 平台定义 ====================

// QCOM MPCTL 命令宏定义 (from mp-ctl.h and VendorIPerf.h)
#define MPCTL_CMD_PERFLOCKACQ 2
#define MPCTL_CMD_PERFLOCKREL 3
#define MPCTL_CMD_PERFLOCKPOLL 4
#define MPCTL_CMD_PERFLOCKRESET 5
#define MPCTL_CMD_EXIT 6
#define MPCTL_CMD_PERFLOCK_RESTORE_GOV_PARAMS 7
#define MPCTL_CMD_PERFLOCKHINTACQ 8
#define MPCTL_CMD_PERFGETFEEDBACK 9
#define MPCTL_CMD_PERFEVENT 10

// QCOM 参数数组限制 (from mp-ctl.h)
#define MAX_ARGS_PER_REQUEST 64
#define MAX_RESERVE_ARGS_PER_REQUEST 32
#define MAX_MSG_APP_NAME_LEN 128

// MTK 参数数组限制
#define MTK_MAX_ARGS_PER_REQUEST 600

// QCOM mpctl_msg_t 结构体
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

// ==================== MTK 平台定义  ====================

// MTK Command IDs (from mtkperf_resource.h) - 完整枚举
enum MtkPerfResource {
    // CPU Frequency (Unit: KHz)
    PERF_RES_CPUFREQ_MIN_CLUSTER_0 = 0x00400000,
    PERF_RES_CPUFREQ_MIN_CLUSTER_1 = 0x00400100,
    PERF_RES_CPUFREQ_MIN_CLUSTER_2 = 0x00400200,
    PERF_RES_CPUFREQ_MAX_CLUSTER_0 = 0x00404000,
    PERF_RES_CPUFREQ_MAX_CLUSTER_1 = 0x00404100,
    PERF_RES_CPUFREQ_MAX_CLUSTER_2 = 0x00404200,

    // CPU Core Control
    PERF_RES_CPUCORE_MIN_CLUSTER_0 = 0x00800000,
    PERF_RES_CPUCORE_MIN_CLUSTER_1 = 0x00800100,
    PERF_RES_CPUCORE_MIN_CLUSTER_2 = 0x00800200,
    PERF_RES_CPUCORE_MAX_CLUSTER_0 = 0x00804000,
    PERF_RES_CPUCORE_MAX_CLUSTER_1 = 0x00804100,
    PERF_RES_CPUCORE_MAX_CLUSTER_2 = 0x00804200,

    // GPU Frequency (Unit: OPP) 注意:不是KHz!
    PERF_RES_GPU_FREQ_MIN = 0x00c00000,
    PERF_RES_GPU_FREQ_MAX = 0x00c04000,

    // DRAM (Unit: OPP) 注意:不是KHz!
    PERF_RES_DRAM_OPP_MIN = 0x01000000,

    // Scheduler
    PERF_RES_SCHED_PREFER_IDLE_TA = 0x01404300,
    PERF_RES_SCHED_UCLAMP_MIN_FG = 0x01408100,
    PERF_RES_SCHED_UCLAMP_MIN_TA = 0x01408300,

    // Power Management
    PERF_RES_PM_QOS_CPUIDLE_MCDI_ENABLE = 0x01c3c100,
    PERF_RES_PM_QOS_CPU_DMA_LATENCY_VALUE = 0x01c3c200,
};

// ==================== PerfLockCaller 类 ====================

/**
 * PerfLockCaller - 性能锁调用器
 *
 * 职责(平台调用和资源管理):
 *  1. 检测平台 (QCOM/MTK/UNISOC)
 *  2. 使用 dlsym(RTLD_DEFAULT) 查找平台函数
 *  3. 调用平台 API (perfmodule_submit_request / libpowerhal_LockAcq)
 *  4. 管理性能锁生命周期和超时
 */
class PerfLockCaller {
public:
    PerfLockCaller();
    ~PerfLockCaller();

    /**
     * 初始化
     * @return true 成功, false 失败
     */
    bool init();

    /**
     * 申请性能锁
     *
     * @param eventId 事件 ID
     * @param duration 持续时间(ms)
     * @param intParams 运行时参数数组
     * @param packageName 包名
     * @return 平台句柄 (>0 成功, <=0 失败)
     */
    int32_t acquirePerfLock(int32_t eventId, int32_t duration,
                            const std::vector<int32_t> &intParams, const std::string &packageName);

    /**
     * 释放性能锁
     *
     * @param handle 平台句柄
     */
    void releasePerfLock(int32_t handle);

private:
    // ==================== 平台初始化 ====================

    Platform detectPlatform();
    bool initQcom();
    bool initMtk();
    bool initUnisoc();

    // ==================== QCOM 平台实现 ====================

    int32_t qcomAcquirePerfLock(int32_t eventId, int32_t duration,
                                const std::vector<int32_t> &intParams,
                                const std::string &packageName);

    void qcomReleasePerfLock(int32_t handle);

    // ==================== MTK 平台实现 ====================

    int32_t mtkAcquirePerfLock(int32_t eventId, int32_t duration,
                               const std::vector<int32_t> &intParams,
                               const std::string &packageName);

    void mtkReleasePerfLock(int32_t handle);

    // ==================== 超时管理 ====================

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

    std::map<int32_t, TimeoutInfo> mActiveTimers;    // platformHandle → TimeoutInfo
    std::mutex mTimerMutex;

    // ==================== 成员变量 ====================

    Platform mPlatform;
    bool mInitialized;

    // QCOM 函数指针
    struct {
        void *submitRequest;    // perfmodule_submit_request
    } mQcomFuncs;

    // MTK 函数指针
    struct {
        void *lockAcq;    // libpowerhal_LockAcq
        void *lockRel;    // libpowerhal_LockRel
    } mMtkFuncs;
};

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor

#endif    // PERF_LOCK_CALLER_H
