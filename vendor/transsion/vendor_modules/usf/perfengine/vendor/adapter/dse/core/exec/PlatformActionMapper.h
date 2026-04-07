#pragma once

// 抽象批次→平台命令；只做翻译、不重解释 Resource 语义（任务清单 §5.6.1、§7.5）。

#include <cstddef>
#include <cstdint>

#include "ActionCompiler.h"
#include "dse/Types.h"

namespace vendor::transsion::perfengine::dse {

struct PlatformCommand {
    uint16_t commandKey = 0;
    uint16_t backend = 0;
    uint16_t resourceDim = 0;
    int32_t intValue = 0;
    uint32_t uintValue = 0;
    uint32_t flags = 0;
};

struct PlatformCommandBatch {
    uint32_t commandCount = 0;
    uint32_t reasonBits = 0;
    PlatformActionPath path = PlatformActionPath::NoopFallback;
    uint32_t fallbackFlags = 0;
    static constexpr size_t kMaxCommands = kResourceDimCount;
    PlatformCommand commands[kMaxCommands];
};

struct PlatformTraits {
    uint32_t supportedResourceMask = 0;
    uint32_t supportedActionKeys = 0;
    bool hasPerfHintHal = false;
    bool hasPmQos = false;
    bool hasCgroupV2 = false;
    bool hasEbpf = false;
    bool hasGpuPreemption = false;
};

class PlatformActionMapper {
public:
    explicit PlatformActionMapper(const PlatformTraits &traits) : traits_(traits) {}
    virtual ~PlatformActionMapper() = default;

    virtual PlatformCommandBatch Map(const AbstractActionBatch &abstractBatch) {
        PlatformCommandBatch cmdBatch;
        cmdBatch.commandCount = 0;
        cmdBatch.reasonBits = abstractBatch.reasonBits;
        cmdBatch.path = DeterminePath(abstractBatch);
        return cmdBatch;
    }

    virtual uint16_t GetActionKey(ResourceDim dim, uint32_t actionType) const {
        (void)dim;
        (void)actionType;
        return 0;
    }

protected:
    virtual PlatformActionPath DeterminePath(const AbstractActionBatch &batch) const {
        (void)batch;
        return PlatformActionPath::NoopFallback;
    }

    PlatformTraits traits_;
};

}    // namespace vendor::transsion::perfengine::dse
