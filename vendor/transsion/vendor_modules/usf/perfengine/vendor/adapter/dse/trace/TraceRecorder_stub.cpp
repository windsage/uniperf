#include "TraceRecorder.h"

namespace vendor::transsion::perfengine::dse {

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

void TraceRecorder::RecordEvent(TracePoint, uint32_t, SessionId) {}

void TraceRecorder::RecordStage(TracePoint, uint32_t, uint32_t, uint32_t, SessionId) {}

void TraceRecorder::RecordAction(TracePoint, uint32_t, uint32_t, uint32_t, SessionId) {}

void TraceRecorder::RecordPathEnd(TracePoint, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t,
                                  IntentLevel, SessionId) {}

void TraceRecorder::RecordState(TracePoint, uint32_t, const char *, uint32_t, SessionId) {}

void TraceRecorder::RecordExecution(const ExecutionResult &) {}

void TraceRecorder::RecordReplayHash(const ReplayHash &) {}

void TraceRecorder::RecordFallback(const FallbackContext &) {}

void TraceRecorder::Flush() {}

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

ReplayResult TraceRecorder::Replay(SessionId, ReplayCallback) {
    ReplayResult result;
    result.success = true;
    return result;
}

ReplayResult TraceRecorder::ReplayRange(uint64_t, uint64_t, ReplayCallback) {
    ReplayResult result;
    result.success = true;
    return result;
}

bool TraceRecorder::ForEachBySessionId(SessionId, const ReplayCallback &) const {
    return true;
}

bool TraceRecorder::ForEachByEventId(uint32_t, const ReplayCallback &) const {
    return true;
}

bool TraceRecorder::ForEachByTimeRange(uint64_t, uint64_t, const ReplayCallback &) const {
    return true;
}

bool TraceRecorder::PersistToFile(const std::string &) {
    return false;
}

bool TraceRecorder::LoadFromFile(const std::string &) {
    return false;
}

void TraceRecorder::CheckAndAlert() {}

bool TraceRecorder::IsStageTraceEnabled() const {
    return false;
}

bool TraceRecorder::IsReplayHashEnabled() const {
    return false;
}

bool TraceRecorder::IsReplayHashTraceEnabled() const {
    return false;
}

bool TraceRecorder::IsReplayHashDeepVerifyEnabled() const {
    return false;
}

bool TraceRecorder::ShouldRecord(uint32_t) const {
    return false;
}

}    // namespace vendor::transsion::perfengine::dse
