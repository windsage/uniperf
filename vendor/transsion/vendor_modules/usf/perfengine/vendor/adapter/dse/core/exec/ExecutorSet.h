#pragma once

#include <cstdint>

#include "PlatformActionMapper.h"

namespace vendor::transsion::perfengine::dse {

struct PlatformExecutionResult {
    uint32_t appliedMask = 0;
    uint32_t rejectedMask = 0;
    uint32_t fallbackMask = 0;
    uint32_t reasonBits = 0;
    bool success = false;
    bool hadFallback = false;
    bool hadRejection = false;
    uint32_t silentFallbackBlocked = 0;
};

}    // namespace vendor::transsion::perfengine::dse
