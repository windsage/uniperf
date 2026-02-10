#include "PerfLockCaller.h"
#include "TranLog.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "PerfEngine-PerfLockCaller-MTK"
namespace vendor {
namespace transsion {
namespace perfengine {

// ==================== MTK 平台实现 ====================

int32_t PerfLockCaller::mtkAcquirePerfLock(int32_t eventId, int32_t duration,
                                           const std::vector<int32_t> &intParams,
                                           const std::string &packageName) {
    // MTK 函数签名:
    // int libpowerhal_LockAcq(int *list, int hdl, int size, int pid, int tid, int duration)
    typedef int (*mtk_perf_lock_acq_t)(int *, int, int, int, int, int);
    auto lockAcq = reinterpret_cast<mtk_perf_lock_acq_t>(mMtkFuncs.lockAcq);

    if (!lockAcq) {
        TLOGE("MTK lockAcq function not initialized");
        return 0;    // MTK 失败返回 0, 不是 -1
    }

    // TODO: 这里需要将 intParams 转换为 MTK commands
    // 目前简化实现,直接使用 intParams (假设已经是 MTK commands)

    // MTK 参数验证
    if (intParams.size() % 2 != 0) {
        TLOGE("MTK: Invalid command size %zu (must be even pairs)", intParams.size());
        return 0;
    }

    if (intParams.size() > MTK_MAX_ARGS_PER_REQUEST) {
        TLOGE("MTK: Command size %zu exceeds limit %d", intParams.size(), MTK_MAX_ARGS_PER_REQUEST);
        return 0;
    }

    if (intParams.empty()) {
        TLOGE("MTK: Empty command list");
        return 0;
    }

    // 转换为非 const 数组 (MTK API 要求 int*)
    std::vector<int32_t> commands = intParams;

    TLOGI("MTK Lock Acquire: event=%d, timeout=%d ms, commands=%zu pairs", eventId, duration,
          commands.size() / 2);

    // 日志输出前几个命令对 (调试用)
    for (size_t i = 0; i < std::min(commands.size(), size_t(6)); i += 2) {
        TLOGD("MTK command[%zu]: 0x%08x = %d", i / 2, commands[i], commands[i + 1]);
    }

    // 调用 MTK API
    // 参数说明:
    // - commands.data(): 命令数组 [cmd1, val1, cmd2, val2, ...]
    // - 0: hdl (0 表示创建新锁)
    // - commands.size(): 数组大小 (总元素数,包括 cmd+value)
    // - getpid(): 进程 ID
    // - gettid(): 线程 ID
    // - duration: 持续时间(毫秒)
    int32_t handle = lockAcq(commands.data(),
                             0,                                    // hdl (0 = create new)
                             static_cast<int>(commands.size()),    // size
                             getpid(),                             // pid
                             gettid(),                             // tid
                             duration                              // duration in milliseconds
    );

    // MTK 返回值判断: 0=失败, >0=成功
    if (handle > 0) {
        TLOGI("MTK LockAcq SUCCESS: handle=%d, timeout=%d ms", handle, duration);

        // 启动超时定时器
        if (duration > 0) {
            startTimeoutTimer(eventId, handle, duration);
        }
    } else {
        TLOGE("MTK LockAcq FAILED: handle=%d (0=disabled/failed)", handle);
    }

    return handle;    // 返回 0 或正值
}

void PerfLockCaller::mtkReleasePerfLock(int32_t handle) {
    // MTK 函数签名: int libpowerhal_LockRel(int hdl)
    typedef int (*mtk_perf_lock_rel_t)(int);
    auto lockRel = reinterpret_cast<mtk_perf_lock_rel_t>(mMtkFuncs.lockRel);

    if (!lockRel) {
        TLOGE("MTK lockRel function not initialized");
        return;
    }

    TLOGI("MTK Lock Release: handle=%d", handle);

    // 调用 MTK API
    int result = lockRel(handle);

    // MTK 返回值: 0=成功, 非0=失败
    if (result == 0) {
        TLOGI("MTK LockRel SUCCESS: handle=%d", handle);
    } else {
        TLOGE("MTK LockRel FAILED: handle=%d, result=%d", handle, result);
    }

    // 取消超时定时器
    cancelTimeoutTimer(handle);
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
