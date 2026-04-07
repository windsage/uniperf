#include "TraceRecorder.h"

#include <algorithm>
#include <chrono>
#include <fstream>

#include "platform/state/StateTypes.h"

namespace vendor::transsion::perfengine::dse {

namespace {

constexpr uint32_t kTraceFileMagic = 0x54524143;
constexpr uint32_t kTraceFileVersion = 2;

struct LegacyTraceRecord {
    uint64_t timestamp = 0;
    uint32_t tracePoint = 0;
    uint32_t eventId = 0;
    uint32_t sessionId = 0;
    uint32_t stageIndex = 0;
    uint32_t reasonBits = 0;
    uint32_t extra[4] = {0};
};

inline uint32_t TraceSessionLow(SessionId sessionId) noexcept {
    return static_cast<uint32_t>(static_cast<uint64_t>(sessionId) & 0xFFFFFFFFULL);
}

inline uint32_t TraceSessionHigh(SessionId sessionId) noexcept {
    return static_cast<uint32_t>((static_cast<uint64_t>(sessionId) >> 32) & 0xFFFFFFFFULL);
}

inline SessionId DecodeTraceSessionId(const TraceRecord &record) noexcept {
    return static_cast<SessionId>((static_cast<uint64_t>(record.sessionHi) << 32) |
                                  static_cast<uint64_t>(record.sessionId));
}

inline void EncodeTraceSession(TraceRecord &record, SessionId sessionId) noexcept {
    record.sessionId = TraceSessionLow(sessionId);
    record.sessionHi = TraceSessionHigh(sessionId);
}

inline bool ReplayHashEquals(const ReplayHash &lhs, const ReplayHash &rhs) noexcept {
    return lhs.decisionHash == rhs.decisionHash && lhs.inputHash == rhs.inputHash &&
           lhs.stateHash == rhs.stateHash && lhs.generation == rhs.generation &&
           lhs.timestamp == rhs.timestamp && lhs.snapshotToken == rhs.snapshotToken &&
           lhs.sessionId == rhs.sessionId && lhs.profileId == rhs.profileId &&
           lhs.ruleVersion == rhs.ruleVersion && lhs.effectiveIntent == rhs.effectiveIntent;
}

inline uint64_t ComposeU64(uint32_t lo, uint32_t hi) noexcept {
    return static_cast<uint64_t>(lo) | (static_cast<uint64_t>(hi) << 32);
}

bool DecodeLatestReplayHash(const TraceBuffer &buffer, ReplayHash &hash) {
    TraceRecord part2{};
    TraceRecord part1{};
    if (!buffer.PeekLatest(0, part2) || !buffer.PeekLatest(1, part1)) {
        return false;
    }
    if (part1.tracePoint != static_cast<uint32_t>(TracePoint::ReplayHashPart1) ||
        part2.tracePoint != static_cast<uint32_t>(TracePoint::ReplayHashPart2)) {
        return false;
    }

    const SessionId session1 = DecodeTraceSessionId(part1);
    const SessionId session2 = DecodeTraceSessionId(part2);
    if (session1 != session2) {
        return false;
    }

    hash.decisionHash = ComposeU64(part1.extra[0], part1.extra[1]);
    hash.inputHash = ComposeU64(part1.extra[2], part1.extra[3]);
    hash.stateHash = ComposeU64(part2.extra[0], part2.extra[1]);
    hash.generation = static_cast<Generation>(ComposeU64(part2.eventId, part2.stageIndex));
    hash.timestamp = static_cast<int64_t>(part2.timestamp);
    hash.snapshotToken = ComposeU64(part2.extra[2], part2.extra[3]);
    hash.sessionId = session1;
    hash.profileId = part1.eventId;
    hash.ruleVersion = part2.reasonBits;
    hash.effectiveIntent = static_cast<IntentLevel>(part1.reasonBits);
    return true;
}

template <typename Predicate, typename Visitor>
bool ForEachMatchingTraceRecord(const TraceBuffer &buffer, Predicate &&predicate,
                                Visitor &&visitor) {
    const TraceRecord *records = buffer.GetRecords();
    const size_t count = buffer.GetCount();
    for (size_t i = 0; i < count; ++i) {
        const TraceRecord &record = records[i];
        if (predicate(record) && !visitor(record)) {
            return false;
        }
    }
    return true;
}

}    // namespace

TraceRecorder &TraceRecorder::Instance() {
    static TraceRecorder instance;
    return instance;
}

void TraceRecorder::Init(const TraceConfig &config) {
    config_ = config;
    buffer_.Clear();
    lastAlertLostRecords_ = 0;
    lastAlertOverwrites_ = 0;
}

void TraceRecorder::Clear() {
    buffer_.Clear();
    lastAlertLostRecords_ = 0;
    lastAlertOverwrites_ = 0;
}

void TraceRecorder::RecordEvent(TracePoint point, uint32_t eventId, SessionId sessionId) {
    if (!ShouldRecord(1))
        return;

    TraceRecord record{};
    record.timestamp = GetTimestamp();
    record.tracePoint = static_cast<uint32_t>(point);
    record.eventId = eventId;
    EncodeTraceSession(record, sessionId);

    WriteResult result = buffer_.Write(record);
    if (!result.success || result.overwritten) {
        CheckAndAlert();
    }
}

void TraceRecorder::RecordStage(TracePoint point, uint32_t eventId, uint32_t stageIndex,
                                uint32_t reasonBits, SessionId sessionId) {
    if (!ShouldRecord(2))
        return;
    if (!config_.enableStageTrace)
        return;

    TraceRecord record{};
    record.timestamp = GetTimestamp();
    record.tracePoint = static_cast<uint32_t>(point);
    record.eventId = eventId;
    EncodeTraceSession(record, sessionId);
    record.stageIndex = stageIndex;
    record.reasonBits = reasonBits;

    buffer_.Write(record);
}

void TraceRecorder::RecordAction(TracePoint point, uint32_t eventId, uint32_t actionKey,
                                 uint32_t value, SessionId sessionId) {
    if (!ShouldRecord(2))
        return;
    if (!config_.enableActionTrace)
        return;

    TraceRecord record{};
    record.timestamp = GetTimestamp();
    record.tracePoint = static_cast<uint32_t>(point);
    record.eventId = eventId;
    EncodeTraceSession(record, sessionId);
    record.extra[0] = actionKey;
    record.extra[1] = value;

    buffer_.Write(record);
}

void TraceRecorder::RecordPathEnd(TracePoint point, uint32_t eventId, uint32_t latencyNs,
                                  uint32_t reasonBits, uint32_t profileId, uint32_t ruleVersion,
                                  IntentLevel effectiveIntent, SessionId sessionId) {
    if (!ShouldRecord(1))
        return;

    TraceRecord record{};
    record.timestamp = GetTimestamp();
    record.tracePoint = static_cast<uint32_t>(point);
    record.eventId = eventId;
    EncodeTraceSession(record, sessionId);
    record.reasonBits = reasonBits;
    if (config_.enableTimingTrace) {
        record.extra[0] = latencyNs;
    }
    record.extra[1] = profileId;
    record.extra[2] = ruleVersion;
    record.extra[3] = static_cast<uint32_t>(effectiveIntent);

    buffer_.Write(record);
}

void TraceRecorder::RecordState(TracePoint point, uint32_t eventId, const char *key, uint32_t value,
                                SessionId sessionId) {
    if (!ShouldRecord(3))
        return;
    if (!config_.enableStateTrace)
        return;

    TraceRecord record{};
    record.timestamp = GetTimestamp();
    record.tracePoint = static_cast<uint32_t>(point);
    record.eventId = eventId;
    EncodeTraceSession(record, sessionId);

    uint32_t keyHash = 0;
    if (key) {
        const char *p = key;
        while (*p) {
            keyHash = keyHash * 31 + static_cast<uint32_t>(*p);
            ++p;
        }
    }
    record.extra[0] = keyHash;
    record.extra[1] = value;

    buffer_.Write(record);
}

void TraceRecorder::RecordExecution(const ExecutionResult &result) {
    if (!ShouldRecord(1))
        return;

    TraceRecord record{};
    record.timestamp = GetTimestamp();
    record.tracePoint = static_cast<uint32_t>(TracePoint::PlatformExecute);
    record.eventId = TraceSessionLow(result.sessionId);
    EncodeTraceSession(record, result.sessionId);
    record.reasonBits = result.rejectReasons;
    record.extra[0] = result.executedMask;
    record.extra[1] = result.fallbackFlags;
    record.extra[2] = result.executionFlags;
    record.extra[3] = static_cast<uint32_t>(result.actualPath);

    buffer_.Write(record);
}

void TraceRecorder::RecordReplayHash(const ReplayHash &hash) {
    if (!IsReplayHashTraceEnabled())
        return;

    TraceRecord record1{};
    record1.timestamp = GetTimestamp();
    record1.tracePoint = static_cast<uint32_t>(TracePoint::ReplayHashPart1);
    record1.eventId = hash.profileId;
    EncodeTraceSession(record1, hash.sessionId);
    record1.reasonBits = static_cast<uint32_t>(hash.effectiveIntent);
    record1.extra[0] = static_cast<uint32_t>(hash.decisionHash & 0xFFFFFFFF);
    record1.extra[1] = static_cast<uint32_t>(hash.decisionHash >> 32);
    record1.extra[2] = static_cast<uint32_t>(hash.inputHash & 0xFFFFFFFF);
    record1.extra[3] = static_cast<uint32_t>(hash.inputHash >> 32);

    buffer_.Write(record1);

    TraceRecord record2{};
    record2.timestamp = hash.timestamp;
    record2.tracePoint = static_cast<uint32_t>(TracePoint::ReplayHashPart2);
    record2.eventId = static_cast<uint32_t>(hash.generation & 0xFFFFFFFF);
    EncodeTraceSession(record2, hash.sessionId);
    record2.stageIndex = static_cast<uint32_t>(hash.generation >> 32);
    record2.reasonBits = hash.ruleVersion;
    record2.extra[0] = static_cast<uint32_t>(hash.stateHash & 0xFFFFFFFF);
    record2.extra[1] = static_cast<uint32_t>(hash.stateHash >> 32);
    record2.extra[2] = static_cast<uint32_t>(hash.snapshotToken & 0xFFFFFFFF);
    record2.extra[3] = static_cast<uint32_t>(hash.snapshotToken >> 32);

    buffer_.Write(record2);
}

void TraceRecorder::RecordFallback(const FallbackContext &ctx) {
    if (!ShouldRecord(1))
        return;

    TraceRecord record{};
    record.timestamp = GetTimestamp();
    record.tracePoint = static_cast<uint32_t>(TracePoint::FallbackEnter);
    record.eventId = ctx.profileId;
    EncodeTraceSession(record, ctx.sessionId);
    record.reasonBits = static_cast<uint32_t>(ctx.reason);
    record.extra[0] = ctx.detailBits;
    record.extra[1] = static_cast<uint32_t>(ctx.generation);
    record.extra[2] = ctx.ruleVersion;
    record.extra[3] = 0;

    buffer_.Write(record);
}

void TraceRecorder::Flush() {
    if (config_.enablePersistence && !config_.persistencePath.empty()) {
        PersistToFile(config_.persistencePath);
    }
}

uint64_t TraceRecorder::GetTimestamp() const {
    return SystemTime::GetDeterministicNs();
}

bool TraceRecorder::ShouldRecord(uint32_t level) const {
    return config_.level >= level;
}

uint64_t TraceRecorder::ComputeInputHash(const SchedEvent &event) const {
    return event.Hash();
}

uint64_t TraceRecorder::ComputeStateHash(const ConstraintSnapshot &constraint,
                                         const CapabilityFeasible &capability) const {
    uint64_t hash = 0;

    hash = HashCombine(hash, static_cast<uint64_t>(constraint.thermalLevel));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.thermalScaleQ10));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.batteryLevel));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.batteryScaleQ10));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.memoryPressure));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.psiLevel));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.psiScaleQ10));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.powerState));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.batterySaver));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.screenOn));
    hash = HashCombine(hash, static_cast<uint64_t>(constraint.thermalSevere));
    hash = HashCombine(hash, static_cast<uint64_t>(capability.resourceMask));
    hash = HashCombine(hash, HashResourceVector(capability.feasible));
    hash = HashCombine(hash, static_cast<uint64_t>(capability.capabilityFlags));
    return hash;
}

ReplayHash TraceRecorder::BuildReplayHash(uint64_t decisionHash, const SchedEvent &event,
                                          const ConstraintSnapshot &constraint,
                                          const CapabilityFeasible &capability,
                                          Generation generation, uint64_t snapshotToken,
                                          SessionId sessionId, uint32_t profileId,
                                          uint32_t ruleVersion, IntentLevel effectiveIntent,
                                          ReplayHashInputs *outInputs) const {
    const uint64_t inputHash = ComputeInputHash(event);
    const uint64_t stateHash = ComputeStateHash(constraint, capability);
    return BuildReplayHashFromInputs(
        decisionHash, inputHash, stateHash, static_cast<int64_t>(event.timestamp), generation,
        snapshotToken, sessionId, profileId, ruleVersion, effectiveIntent, outInputs);
}

ReplayHash TraceRecorder::BuildReplayHashFromInputs(
    uint64_t decisionHash, uint64_t inputHash, uint64_t stateHash, int64_t eventTimestamp,
    Generation generation, uint64_t snapshotToken, SessionId sessionId, uint32_t profileId,
    uint32_t ruleVersion, IntentLevel effectiveIntent, ReplayHashInputs *outInputs) const {
    ReplayHash hash;
    hash.decisionHash = decisionHash;
    hash.inputHash = inputHash;
    hash.stateHash = stateHash;
    hash.generation = generation;
    hash.timestamp = eventTimestamp;
    hash.snapshotToken = snapshotToken;
    hash.sessionId = sessionId;
    hash.profileId = profileId;
    hash.ruleVersion = ruleVersion;
    hash.effectiveIntent = effectiveIntent;
    if (outInputs != nullptr) {
        outInputs->inputHash = inputHash;
        outInputs->stateHash = stateHash;
    }
    return hash;
}

bool TraceRecorder::VerifyReplayHash(const ReplayHash &hash) const {
    ReplayHash latest{};
    return DecodeLatestReplayHash(buffer_, latest) && ReplayHashEquals(hash, latest);
}

bool TraceRecorder::VerifyReplayHash(const ReplayHash &hash, uint64_t expectedDecisionHash,
                                     const ReplayHashInputs &expectedInputs) const {
    return VerifyReplayHash(hash) && hash.decisionHash == expectedDecisionHash &&
           hash.inputHash == expectedInputs.inputHash && hash.stateHash == expectedInputs.stateHash;
}

bool TraceRecorder::RecordReplayHashChecked(const ReplayHash &hash, uint64_t expectedDecisionHash,
                                            const ReplayHashInputs &expectedInputs,
                                            const SchedEvent &event) {
    bool verified = (hash.decisionHash == expectedDecisionHash);
    if (verified && IsReplayHashDeepVerifyEnabled()) {
        verified = (hash.inputHash == expectedInputs.inputHash) &&
                   (hash.stateHash == expectedInputs.stateHash);
    }
    if (!IsReplayHashTraceEnabled()) {
        return verified;
    }
    if (verified) {
        RecordReplayHash(hash);
        return true;
    }

    RecordState(TracePoint::FallbackExit, static_cast<uint32_t>(event.eventId),
                "replay_verify_failed", 1, hash.sessionId);
    return false;
}

ReplayResult TraceRecorder::Replay(SessionId sessionId, ReplayCallback callback) {
    ReplayResult result;
    const bool completed = ForEachMatchingTraceRecord(
        buffer_,
        [sessionId](const TraceRecord &record) {
            return DecodeTraceSessionId(record) == sessionId;
        },
        [&result, &callback](const TraceRecord &record) {
            result.recordsProcessed++;
            return callback(record);
        });

    if (!completed) {
        result.success = false;
        result.errorMessage = "Callback returned false";
        return result;
    }

    result.success = true;
    return result;
}

ReplayResult TraceRecorder::ReplayRange(uint64_t startTs, uint64_t endTs, ReplayCallback callback) {
    ReplayResult result;
    const bool completed = ForEachMatchingTraceRecord(
        buffer_,
        [startTs, endTs](const TraceRecord &record) {
            return record.timestamp >= startTs && record.timestamp <= endTs;
        },
        [&result, &callback](const TraceRecord &record) {
            result.recordsProcessed++;
            return callback(record);
        });

    if (!completed) {
        result.success = false;
        result.errorMessage = "Callback returned false";
        return result;
    }

    result.success = true;
    return result;
}

bool TraceRecorder::ForEachBySessionId(SessionId sessionId, const ReplayCallback &callback) const {
    return ForEachMatchingTraceRecord(
        buffer_,
        [sessionId](const TraceRecord &record) {
            return DecodeTraceSessionId(record) == sessionId;
        },
        [&callback](const TraceRecord &record) { return callback(record); });
}

bool TraceRecorder::ForEachByEventId(uint32_t eventId, const ReplayCallback &callback) const {
    return ForEachMatchingTraceRecord(
        buffer_, [eventId](const TraceRecord &record) { return record.eventId == eventId; },
        [&callback](const TraceRecord &record) { return callback(record); });
}

bool TraceRecorder::ForEachByTimeRange(uint64_t startTs, uint64_t endTs,
                                       const ReplayCallback &callback) const {
    return ForEachMatchingTraceRecord(
        buffer_,
        [startTs, endTs](const TraceRecord &record) {
            return record.timestamp >= startTs && record.timestamp <= endTs;
        },
        [&callback](const TraceRecord &record) { return callback(record); });
}

bool TraceRecorder::PersistToFile(const std::string &path) {
    std::ofstream file(path, std::ios::binary);
    if (!file.is_open()) {
        return false;
    }

    const TraceRecord *records = buffer_.GetRecords();
    size_t count = buffer_.GetCount();

    uint32_t magic = kTraceFileMagic;
    uint32_t version = kTraceFileVersion;
    uint64_t recordCount = static_cast<uint64_t>(count);

    file.write(reinterpret_cast<const char *>(&magic), sizeof(magic));
    file.write(reinterpret_cast<const char *>(&version), sizeof(version));
    file.write(reinterpret_cast<const char *>(&recordCount), sizeof(recordCount));

    if (count > 0) {
        file.write(reinterpret_cast<const char *>(records), count * sizeof(TraceRecord));
    }

    return file.good();
}

bool TraceRecorder::LoadFromFile(const std::string &path) {
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) {
        return false;
    }

    uint32_t magic = 0;
    uint32_t version = 0;
    uint64_t recordCount = 0;

    file.read(reinterpret_cast<char *>(&magic), sizeof(magic));
    file.read(reinterpret_cast<char *>(&version), sizeof(version));
    file.read(reinterpret_cast<char *>(&recordCount), sizeof(recordCount));

    if (magic != kTraceFileMagic || (version != 1 && version != kTraceFileVersion)) {
        return false;
    }

    buffer_.Clear();

    for (uint64_t i = 0; i < recordCount; ++i) {
        TraceRecord record;
        if (version == 1) {
            LegacyTraceRecord legacyRecord;
            file.read(reinterpret_cast<char *>(&legacyRecord), sizeof(LegacyTraceRecord));
            if (!file.good()) {
                break;
            }
            record.timestamp = legacyRecord.timestamp;
            record.tracePoint = legacyRecord.tracePoint;
            record.eventId = legacyRecord.eventId;
            record.sessionId = legacyRecord.sessionId;
            record.sessionHi = 0;
            record.stageIndex = legacyRecord.stageIndex;
            record.reasonBits = legacyRecord.reasonBits;
            for (size_t j = 0; j < 4; ++j) {
                record.extra[j] = legacyRecord.extra[j];
            }
        } else {
            file.read(reinterpret_cast<char *>(&record), sizeof(TraceRecord));
        }
        if (!file.good()) {
            break;
        }
        buffer_.Write(record);
    }

    return true;
}

void TraceRecorder::CheckAndAlert() {
    TraceStats stats = buffer_.GetStats();

    uint32_t newLostRecords = static_cast<uint32_t>(stats.totalLostRecords);
    uint32_t newOverwrites = static_cast<uint32_t>(stats.totalOverwrites);

    if (config_.lostRecordAlertThreshold > 0 &&
        newLostRecords - lastAlertLostRecords_ >= config_.lostRecordAlertThreshold) {
        lastAlertLostRecords_ = newLostRecords;
    }

    if (config_.overwriteAlertThreshold > 0 &&
        newOverwrites - lastAlertOverwrites_ >= config_.overwriteAlertThreshold) {
        lastAlertOverwrites_ = newOverwrites;
    }
}

}    // namespace vendor::transsion::perfengine::dse
