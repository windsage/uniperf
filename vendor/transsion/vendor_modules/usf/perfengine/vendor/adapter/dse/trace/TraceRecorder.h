#pragma once

#include "TraceMacros.h"
#if PERFENGINE_ENABLE_DSE_TRACE
#include "TraceBuffer.h"
#else
#include "TraceBufferStub.h"
#endif
#include <cstdint>
#include <functional>
#include <string>

#include "dse/Types.h"
#include "types/ConstraintTypes.h"
#include "types/CoreTypes.h"

namespace vendor::transsion::perfengine::dse {

enum class FallbackDomain : uint8_t {
    None = 0,
    Safety = 1,
    Platform = 2,
    Resource = 3,
    Constraint = 4,
    Capability = 5,
    Execution = 6,
    Config = 7,
    Input = 8
};

enum class FallbackCategory : uint8_t {
    None = 0,
    Unavailable = 1,
    Blocked = 2,
    Insufficient = 3,
    Violation = 4,
    Mismatch = 5,
    Failed = 6,
    Timeout = 7,
    Rejected = 8,
    Noop = 9
};

enum class DecisionFallbackReason : uint32_t {
    None = 0,

    SafetyBlocked = 0x0100,
    SafetyDisabled = 0x0101,
    SafetyGrayscale = 0x0102,
    SafetyUidBlocked = 0x0103,

    PlatformUnavailable = 0x0200,
    PlatformNotRegistered = 0x0201,
    PlatformCapabilityMismatch = 0x0202,
    PlatformActionMapFailed = 0x0203,
    PlatformExecutorFailed = 0x0204,
    PlatformHalError = 0x0205,
    PlatformSysfsError = 0x0206,

    ResourceInsufficient = 0x0300,
    ResourceCpuInsufficient = 0x0301,
    ResourceMemoryInsufficient = 0x0302,
    ResourceGpuInsufficient = 0x0303,
    ResourceIoInsufficient = 0x0304,

    ConstraintViolation = 0x0400,
    ConstraintThermalViolation = 0x0401,
    ConstraintBatteryViolation = 0x0402,
    ConstraintMemoryViolation = 0x0403,
    ConstraintPowerViolation = 0x0404,

    CapabilityMismatch = 0x0500,
    CapabilityUnsupported = 0x0501,
    CapabilityActionUnsupported = 0x0502,
    CapabilityResourceUnsupported = 0x0503,

    ExecutionFailed = 0x0600,
    ExecutionRejected = 0x0601,
    ExecutionTimeout = 0x0602,
    ExecutionPartialFailure = 0x0603,
    ExecutionNoAction = 0x0604,

    ConfigError = 0x0700,
    ConfigLoadFailed = 0x0701,
    ConfigParseError = 0x0702,
    ConfigVersionMismatch = 0x0703,
    StateBaselineSynthetic = 0x0704,    ///< StateService 未就绪或合成基线（约束/能力非真机）
    StateBootstrapTimeout = 0x0705,    ///< WaitUntilReady 超时

    InputError = 0x0800,
    InputInvalidEvent = 0x0801,
    InputInvalidSession = 0x0802,

    SilentBlocked = 0x4000,
    ServiceUnavailable = 0x8000,
    NoopFallback = 0x10000,
    LegacyFallback = 0x20000
};

struct FallbackDetailBits {
    uint32_t domain : 4;
    uint32_t category : 4;
    uint32_t resourceDim : 8;
    uint32_t stageIndex : 8;
    uint32_t errorCode : 8;

    static uint32_t Encode(FallbackDomain dom, FallbackCategory cat, uint8_t resDim = 0,
                           uint8_t stage = 0, uint8_t err = 0) {
        return (static_cast<uint32_t>(dom) << 0) | (static_cast<uint32_t>(cat) << 4) |
               (static_cast<uint32_t>(resDim) << 8) | (static_cast<uint32_t>(stage) << 16) |
               (static_cast<uint32_t>(err) << 24);
    }

    static FallbackDomain GetDomain(uint32_t bits) {
        return static_cast<FallbackDomain>(bits & 0xF);
    }

    static FallbackCategory GetCategory(uint32_t bits) {
        return static_cast<FallbackCategory>((bits >> 4) & 0xF);
    }

    static uint8_t GetResourceDim(uint32_t bits) {
        return static_cast<uint8_t>((bits >> 8) & 0xFF);
    }

    static uint8_t GetStageIndex(uint32_t bits) {
        return static_cast<uint8_t>((bits >> 16) & 0xFF);
    }

    static uint8_t GetErrorCode(uint32_t bits) { return static_cast<uint8_t>((bits >> 24) & 0xFF); }
};

struct FallbackContext {
    SessionId sessionId = 0;
    uint32_t profileId = 0;
    Generation generation = 0;
    uint32_t ruleVersion = 0;
    DecisionFallbackReason reason = DecisionFallbackReason::None;
    uint32_t detailBits = 0;
    uint32_t resourceMask = 0;
    uint32_t constraintMask = 0;
    int64_t timestamp = 0;

    void SetDetail(FallbackDomain dom, FallbackCategory cat, uint8_t resDim = 0, uint8_t stage = 0,
                   uint8_t err = 0) {
        detailBits = FallbackDetailBits::Encode(dom, cat, resDim, stage, err);
    }
};

struct TraceConfig {
    uint32_t level = 0;
    bool enableStageTrace = true;
    bool enableActionTrace = true;
    bool enableStateTrace = true;
    bool enableTimingTrace = true;
    bool enableDecisionTrace = true;
    bool enableReplayHash = true;
    bool enablePersistence = false;
    std::string persistencePath;
    uint32_t lostRecordAlertThreshold = 100;
    uint32_t overwriteAlertThreshold = 50;
};

struct ReplayResult {
    bool success = false;
    uint32_t recordsProcessed = 0;
    uint32_t hashMismatches = 0;
    uint32_t missingRecords = 0;
    std::string errorMessage;
};

struct ReplayHashInputs {
    uint64_t inputHash = 0;
    uint64_t stateHash = 0;
};

class TraceRecorder {
public:
    static TraceRecorder &Instance();

    void Init(const TraceConfig &config);

    void RecordEvent(TracePoint point, uint32_t eventId, SessionId sessionId = 0);
    void RecordStage(TracePoint point, uint32_t eventId, uint32_t stageIndex,
                     uint32_t reasonBits = 0, SessionId sessionId = 0);
    void RecordAction(TracePoint point, uint32_t eventId, uint32_t actionKey, uint32_t value,
                      SessionId sessionId = 0);
    void RecordPathEnd(TracePoint point, uint32_t eventId, uint32_t latencyNs,
                       uint32_t reasonBits = 0, uint32_t profileId = 0, uint32_t ruleVersion = 0,
                       IntentLevel effectiveIntent = IntentLevel::P4, SessionId sessionId = 0);
    void RecordState(TracePoint point, uint32_t eventId, const char *key, uint32_t value,
                     SessionId sessionId = 0);
    void RecordExecution(const ExecutionResult &result);
    void RecordReplayHash(const ReplayHash &hash);
    void RecordFallback(const FallbackContext &ctx);

    void SetLevel(uint32_t level) { config_.level = level; }
    uint32_t GetLevel() const { return config_.level; }
    bool IsStageTraceEnabled() const;
    bool IsReplayHashEnabled() const;
    bool IsReplayHashTraceEnabled() const;
    bool IsReplayHashDeepVerifyEnabled() const;

    TraceBuffer &GetBuffer() { return buffer_; }
    const TraceBuffer &GetBuffer() const { return buffer_; }

    void Flush();
    void Clear();

    uint64_t GetTimestamp() const;
    bool ShouldRecord(uint32_t level) const;

    uint64_t ComputeInputHash(const SchedEvent &event) const;
    uint64_t ComputeStateHash(const ConstraintSnapshot &constraint,
                              const CapabilityFeasible &capability) const;
    ReplayHash BuildReplayHashFromInputs(uint64_t decisionHash, uint64_t inputHash,
                                         uint64_t stateHash, int64_t eventTimestamp,
                                         Generation generation, uint64_t snapshotToken,
                                         SessionId sessionId, uint32_t profileId,
                                         uint32_t ruleVersion, IntentLevel effectiveIntent,
                                         ReplayHashInputs *outInputs = nullptr) const;
    ReplayHash BuildReplayHash(uint64_t decisionHash, const SchedEvent &event,
                               const ConstraintSnapshot &constraint,
                               const CapabilityFeasible &capability, Generation generation,
                               uint64_t snapshotToken, SessionId sessionId, uint32_t profileId,
                               uint32_t ruleVersion, IntentLevel effectiveIntent,
                               ReplayHashInputs *outInputs = nullptr) const;

    bool VerifyReplayHash(const ReplayHash &hash) const;
    bool VerifyReplayHash(const ReplayHash &hash, uint64_t expectedDecisionHash,
                          const ReplayHashInputs &expectedInputs) const;
    bool RecordReplayHashChecked(const ReplayHash &hash, uint64_t expectedDecisionHash,
                                 const ReplayHashInputs &expectedInputs, const SchedEvent &event);

    using ReplayCallback = std::function<bool(const TraceRecord &)>;
    ReplayResult Replay(SessionId sessionId, ReplayCallback callback);
    ReplayResult ReplayRange(uint64_t startTs, uint64_t endTs, ReplayCallback callback);

    bool ForEachBySessionId(SessionId sessionId, const ReplayCallback &callback) const;
    bool ForEachByEventId(uint32_t eventId, const ReplayCallback &callback) const;
    bool ForEachByTimeRange(uint64_t startTs, uint64_t endTs, const ReplayCallback &callback) const;

    bool PersistToFile(const std::string &path);
    bool LoadFromFile(const std::string &path);

    void SetPersistenceEnabled(bool enable) { config_.enablePersistence = enable; }
    void SetPersistencePath(const std::string &path) { config_.persistencePath = path; }

    /**
     * @brief 检查并输出Trace写入告警
     *
     * 当写入失败达到阈值时输出告警日志：
     * - 丢失记录数超过阈值
     * - 覆盖次数超过阈值
     *
     * @note 遵循框架"可验证、可解释"原则
     */
    void CheckAndAlert();

    /**
     * @brief 获取Trace统计信息
     * @return Trace统计结构
     */
    TraceStats GetTraceStats() const { return buffer_.GetStats(); }

    /**
     * @brief 重置Trace统计信息
     */
    void ResetTraceStats() {
        buffer_.ResetStats();
        lastAlertLostRecords_ = 0;
        lastAlertOverwrites_ = 0;
    }

private:
    TraceRecorder() = default;

    TraceBuffer buffer_;
    TraceConfig config_;
    uint32_t lastAlertLostRecords_ = 0;
    uint32_t lastAlertOverwrites_ = 0;
};

}    // namespace vendor::transsion::perfengine::dse
