#pragma once

#include <fcntl.h>
#include <unistd.h>

#include <cstdio>

#include "PlatformBase.h"

namespace vendor::transsion::perfengine::dse::platform_executor_detail {

/**
 * @brief 平台执行器通用写入结果。
 *
 * 该枚举描述平台写入层面的标准失败语义，避免各 vendor executor
 * 在同一抽象层重复维护一套语义一致、名字不同的状态码。
 */
enum class WriteResult : uint8_t {
    Success = 0,
    RejectInvalidCommand = 1,
    RejectInvalidValue = 2,
    RejectHalUnavailable = 3,
    RejectPermissionDenied = 4,
    FallbackTimeout = 5,
    FallbackResourceBusy = 6
};

/**
 * @brief 统一执行平台命令批次并归并执行结果。
 *
 * 平台专属 executor 只需要提供单条命令的处理逻辑；mask 聚合、fallback 标记、
 * reasonBits 编码等通用逻辑在此保持单一实现。
 */
template <typename CommandWriter>
inline PlatformExecutionResult ExecutePlatformBatch(const PlatformCommandBatch &batch,
                                                    CommandWriter &&writer) {
    PlatformExecutionResult result;
    result.success = true;
    result.reasonBits = batch.reasonBits;
    result.appliedMask = 0;
    result.rejectedMask = 0;
    result.fallbackMask = 0;

    for (uint32_t i = 0; i < batch.commandCount; ++i) {
        const auto &cmd = batch.commands[i];
        uint32_t reasonCode = 0;
        const WriteResult writeResult = writer(cmd, reasonCode);
        const uint32_t dimMask = 1u << cmd.resourceDim;

        if (writeResult == WriteResult::Success) {
            result.appliedMask |= dimMask;
            continue;
        }

        result.rejectedMask |= dimMask;
        result.success = false;
        result.reasonBits |= (reasonCode << (i * 4));
        if (writeResult == WriteResult::FallbackTimeout ||
            writeResult == WriteResult::FallbackResourceBusy) {
            result.fallbackMask |= dimMask;
            result.hadFallback = true;
        }
    }

    result.hadRejection = (result.rejectedMask != 0);
    return result;
}

/** @brief 将格式化后的缓冲区统一写入平台控制节点。 */
inline WriteResult WriteBufferToPath(const char *path, const char *buffer, int length,
                                     uint32_t &reasonCode) {
    if (path == nullptr || path[0] == '\0' || buffer == nullptr || length <= 0) {
        reasonCode = static_cast<uint32_t>(WriteResult::RejectInvalidCommand);
        return WriteResult::RejectInvalidCommand;
    }

    const int fd = open(path, O_WRONLY);
    if (fd < 0) {
        reasonCode = 1;
        return WriteResult::FallbackTimeout;
    }

    const ssize_t ret = write(fd, buffer, static_cast<size_t>(length));
    close(fd);
    if (ret < 0) {
        reasonCode = 2;
        return WriteResult::FallbackResourceBusy;
    }

    reasonCode = static_cast<uint32_t>(WriteResult::Success);
    return WriteResult::Success;
}

/** @brief 将无符号数值写入 cgroup / kernel control 节点。 */
inline WriteResult WriteUnsignedValueToPath(const char *path, uint32_t value,
                                            uint32_t &reasonCode) {
    char buf[32];
    const int len = std::snprintf(buf, sizeof(buf), "%u", value);
    return WriteBufferToPath(path, buf, len, reasonCode);
}

/** @brief 解析当前首期平台共用的资源控制路径。 */
inline const char *ResolveSharedControlPath(ResourceDim dim, const char *memoryMinPath,
                                            const char *storageBwPath, const char *storageIopsPath,
                                            const char *networkBwPath) noexcept {
    switch (dim) {
        case ResourceDim::MemoryCapacity:
            return memoryMinPath;
        case ResourceDim::StorageBandwidth:
            return storageBwPath;
        case ResourceDim::StorageIops:
            return storageIopsPath;
        case ResourceDim::NetworkBandwidth:
            return networkBwPath;
        default:
            return "";
    }
}

}    // namespace vendor::transsion::perfengine::dse::platform_executor_detail
