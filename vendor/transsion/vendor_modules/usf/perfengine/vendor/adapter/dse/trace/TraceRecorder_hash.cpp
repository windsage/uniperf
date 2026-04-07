#include "TraceRecorder.h"
#include "platform/state/StateTypes.h"

namespace vendor::transsion::perfengine::dse {

namespace {

inline bool ReplayHashEquals(const ReplayHash &lhs, const ReplayHash &rhs) noexcept {
    return lhs.decisionHash == rhs.decisionHash && lhs.inputHash == rhs.inputHash &&
           lhs.stateHash == rhs.stateHash && lhs.generation == rhs.generation &&
           lhs.timestamp == rhs.timestamp && lhs.snapshotToken == rhs.snapshotToken &&
           lhs.sessionId == rhs.sessionId && lhs.profileId == rhs.profileId &&
           lhs.ruleVersion == rhs.ruleVersion && lhs.effectiveIntent == rhs.effectiveIntent;
}

inline SessionId DecodeTraceSessionId(const TraceRecord &record) noexcept {
    return static_cast<SessionId>((static_cast<uint64_t>(record.sessionHi) << 32) |
                                  static_cast<uint64_t>(record.sessionId));
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

}    // namespace

TraceRecorder &TraceRecorder::Instance() {
    static TraceRecorder instance;
    return instance;
}

uint64_t TraceRecorder::GetTimestamp() const {
    return SystemTime::GetDeterministicNs();
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

}    // namespace vendor::transsion::perfengine::dse
