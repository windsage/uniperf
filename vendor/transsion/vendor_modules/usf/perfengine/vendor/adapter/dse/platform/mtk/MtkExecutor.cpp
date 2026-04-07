#include "MtkExecutor.h"

#include "MtkActionMap.h"
#include "platform/PlatformRegistry.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "DSE-MtkExec"

namespace vendor::transsion::perfengine::dse {

namespace {
constexpr const char *kPerfCmdPath = "/proc/perfmgr/tuning/cmd";
constexpr const char *kMemoryMinPath = "/sys/fs/cgroup/dse/memory.min";
constexpr const char *kStorageBwPath = "/sys/fs/cgroup/dse/io.bandwidth.max";
constexpr const char *kStorageIopsPath = "/sys/fs/cgroup/dse/io.iops.max";
constexpr const char *kNetworkBwPath = "/sys/fs/bpf/dse/net_bw.max";
}    // namespace

MtkExecutor::MtkExecutor(const PlatformCapability &capability) : capability_(capability) {}

PlatformExecutionResult MtkExecutor::Execute(const PlatformCommandBatch &batch) {
    return platform_executor_detail::ExecutePlatformBatch(
        batch, [this](const PlatformCommand &cmd, uint32_t &reasonCode) {
            switch (cmd.backend) {
                case 1:
                    return WritePerfCmd(cmd.commandKey, cmd.intValue, cmd.flags, reasonCode);
                case 2:
                    return WriteResourceControl(static_cast<ResourceDim>(cmd.resourceDim),
                                                cmd.uintValue, reasonCode);
                case 3:
                    return WriteNetworkControl(static_cast<ResourceDim>(cmd.resourceDim),
                                               cmd.uintValue, reasonCode);
                default:
                    reasonCode = 0xFF;
                    return WriteResult::RejectInvalidCommand;
            }
        });
}

MtkExecutor::WriteResult MtkExecutor::WritePerfCmd(uint16_t cmdId, int32_t value, uint32_t flags,
                                                   uint32_t &reasonCode) {
    if (cmdId == 0 || cmdId > 0x0FFF) {
        reasonCode = static_cast<uint32_t>(WriteResult::RejectInvalidCommand);
        return WriteResult::RejectInvalidCommand;
    }

    if (value < 0) {
        reasonCode = static_cast<uint32_t>(WriteResult::RejectInvalidValue);
        return WriteResult::RejectInvalidValue;
    }

    char buf[32];
    const int len = snprintf(buf, sizeof(buf), "%d %d %u", cmdId, value, flags);
    return platform_executor_detail::WriteBufferToPath(kPerfCmdPath, buf, len, reasonCode);
}

MtkExecutor::WriteResult MtkExecutor::WriteCgroupValue(const char *path, uint32_t value,
                                                       uint32_t &reasonCode) {
    return platform_executor_detail::WriteUnsignedValueToPath(path, value, reasonCode);
}

MtkExecutor::WriteResult MtkExecutor::WriteResourceControl(ResourceDim dim, uint32_t value,
                                                           uint32_t &reasonCode) {
    return WriteCgroupValue(ResolveControlPath(dim), value, reasonCode);
}

MtkExecutor::WriteResult MtkExecutor::WriteNetworkControl(ResourceDim dim, uint32_t value,
                                                          uint32_t &reasonCode) {
    return WriteCgroupValue(ResolveControlPath(dim), value, reasonCode);
}

const char *MtkExecutor::ResolveControlPath(ResourceDim dim) const {
    return platform_executor_detail::ResolveSharedControlPath(dim, kMemoryMinPath, kStorageBwPath,
                                                              kStorageIopsPath, kNetworkBwPath);
}

namespace {
IPlatformExecutor *CreateMtkExecutor(const PlatformCapability &cap) {
    return new MtkExecutor(cap);
}

IPlatformActionMap *CreateMtkActionMap(const PlatformCapability &cap) {
    return new MtkActionMap(cap);
}
}    // namespace

DSE_REGISTER_PLATFORM(Mtk, CreateMtkExecutor, CreateMtkActionMap);

}    // namespace vendor::transsion::perfengine::dse
