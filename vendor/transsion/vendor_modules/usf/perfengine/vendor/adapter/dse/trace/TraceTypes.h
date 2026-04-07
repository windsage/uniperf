#pragma once

// Trace 公共类型：全量/Stub 实现共用，避免重复定义。

#include <cstddef>
#include <cstdint>

namespace vendor::transsion::perfengine::dse {

enum class TracePoint : uint8_t {
    EventEntry = 0,
    FastPathStart = 1,
    FastPathEnd = 2,
    ConvergePathStart = 3,
    ConvergePathEnd = 4,
    StageEnter = 5,
    StageExit = 6,
    ActionCompile = 7,
    PlatformMap = 8,
    PlatformExecute = 9,
    ConstraintUpdate = 10,
    CapabilityUpdate = 11,
    SceneChange = 12,
    IntentChange = 13,
    LeaseRenew = 14,
    LeaseExpire = 15,
    FallbackEnter = 16,
    FallbackExit = 17,
    ReplayHashPart1 = 18,
    ReplayHashPart2 = 19
};

struct TraceRecord {
    uint64_t timestamp = 0;
    uint32_t tracePoint = 0;
    uint32_t eventId = 0;
    uint32_t sessionId = 0;
    uint32_t sessionHi = 0;
    uint32_t stageIndex = 0;
    uint32_t reasonBits = 0;
    uint32_t extra[4] = {0};
};

struct WriteResult {
    bool success = false;
    bool overwritten = false;
    size_t lostRecords = 0;
};

struct TraceStats {
    size_t totalWrites = 0;
    size_t totalOverwrites = 0;
    size_t totalLostRecords = 0;
    size_t currentCount = 0;
    size_t capacity = 0;
};

}    // namespace vendor::transsion::perfengine::dse
