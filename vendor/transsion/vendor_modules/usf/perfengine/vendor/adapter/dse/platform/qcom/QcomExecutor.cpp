#include "QcomExecutor.h"

#include "QcomActionMap.h"
#include "platform/PlatformRegistry.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "DSE-QcomExec"

namespace vendor::transsion::perfengine::dse {

namespace {
constexpr const char *kPerfHintPath = "/sys/class/devfreq/soc:qcom,cpu-llcc-ddr-bw/perf_hint";
constexpr const char *kMemoryMinPath = "/sys/fs/cgroup/dse/memory.min";
constexpr const char *kStorageBwPath = "/sys/fs/cgroup/dse/io.bandwidth.max";
constexpr const char *kStorageIopsPath = "/sys/fs/cgroup/dse/io.iops.max";
constexpr const char *kNetworkBwPath = "/sys/fs/bpf/dse/net_bw.max";
}    // namespace

QcomExecutor::QcomExecutor(const PlatformCapability &capability) : capability_(capability) {}

PlatformExecutionResult QcomExecutor::Execute(const PlatformCommandBatch &batch) {
    return platform_executor_detail::ExecutePlatformBatch(
        batch, [this](const PlatformCommand &cmd, uint32_t &reasonCode) {
            switch (cmd.backend) {
                case 1:
                    return WritePerfHint(cmd.commandKey, cmd.intValue, cmd.flags, reasonCode);
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

QcomExecutor::WriteResult QcomExecutor::WritePerfHint(uint16_t hintId, int32_t value,
                                                      uint32_t flags, uint32_t &reasonCode) {
    if (hintId == 0 || hintId > 0x0FFF) {
        reasonCode = static_cast<uint32_t>(WriteResult::RejectInvalidCommand);
        return WriteResult::RejectInvalidCommand;
    }

    if (value < 0) {
        reasonCode = static_cast<uint32_t>(WriteResult::RejectInvalidValue);
        return WriteResult::RejectInvalidValue;
    }

    char buf[32];
    const int len = snprintf(buf, sizeof(buf), "%d %d", hintId, value);
    return platform_executor_detail::WriteBufferToPath(kPerfHintPath, buf, len, reasonCode);
}

QcomExecutor::WriteResult QcomExecutor::WriteCgroupValue(const char *path, uint32_t value,
                                                         uint32_t &reasonCode) {
    return platform_executor_detail::WriteUnsignedValueToPath(path, value, reasonCode);
}

QcomExecutor::WriteResult QcomExecutor::WriteResourceControl(ResourceDim dim, uint32_t value,
                                                             uint32_t &reasonCode) {
    return WriteCgroupValue(ResolveControlPath(dim), value, reasonCode);
}

QcomExecutor::WriteResult QcomExecutor::WriteNetworkControl(ResourceDim dim, uint32_t value,
                                                            uint32_t &reasonCode) {
    return WriteCgroupValue(ResolveControlPath(dim), value, reasonCode);
}

const char *QcomExecutor::ResolveControlPath(ResourceDim dim) const {
    return platform_executor_detail::ResolveSharedControlPath(dim, kMemoryMinPath, kStorageBwPath,
                                                              kStorageIopsPath, kNetworkBwPath);
}

namespace {
IPlatformExecutor *CreateQcomExecutor(const PlatformCapability &cap) {
    return new QcomExecutor(cap);
}

IPlatformActionMap *CreateQcomActionMap(const PlatformCapability &cap) {
    return new QcomActionMap(cap);
}
}    // namespace

DSE_REGISTER_PLATFORM(Qcom, CreateQcomExecutor, CreateQcomActionMap);

}    // namespace vendor::transsion::perfengine::dse
