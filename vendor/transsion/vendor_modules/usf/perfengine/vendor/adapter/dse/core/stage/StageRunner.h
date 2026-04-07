#pragma once

// ProfileSpec::FastStages/ConvergeStages 的编译期 Stage 链展开（任务清单 §10.1）。
// 嵌入 trace/validation/replay 验证链（框架 §13）。

#include <cstddef>
#include <tuple>

#include "StageBase.h"

namespace vendor::transsion::perfengine::dse {

template <typename... Stages>
struct TypeList {};

template <typename StageList>
class StageRunner;

template <typename... Stages>
class StageRunner<TypeList<Stages...>> {
public:
    static constexpr size_t kStageCount = sizeof...(Stages);

    StageRunner() = default;

    bool Run(StageContext &ctx) {
        bool allSuccess = true;
        accumulatedDirtyBits_ = DirtyBits{};
        auto &tracer = ctx.GetTraceRecorder();
        const bool stageTraceEnabled = tracer.ShouldRecord(2) && tracer.IsStageTraceEnabled();
        const uint32_t eventId =
            stageTraceEnabled && ctx.event ? static_cast<uint32_t>(ctx.event->eventId) : 0;
        const SessionId sessionId = stageTraceEnabled ? ctx.GetResolvedSessionId() : 0;
        RunImpl<0>(ctx, allSuccess, tracer, stageTraceEnabled, eventId, sessionId);
        return allSuccess;
    }

    const char *GetStageName(size_t index) const { return GetStageNameImpl<0>(index); }

    size_t GetStageCount() const { return kStageCount; }

    DirtyBits GetAccumulatedDirtyBits() const { return accumulatedDirtyBits_; }

private:
    std::tuple<Stages...> stages_;
    DirtyBits accumulatedDirtyBits_{};

    template <size_t I>
    void RunImpl(StageContext &ctx, bool &success, TraceRecorder &tracer, bool stageTraceEnabled,
                 uint32_t eventId, SessionId sessionId) {
        if constexpr (I < kStageCount) {
            auto &stage = std::get<I>(stages_);

            if (stageTraceEnabled) {
                tracer.RecordStage(TracePoint::StageEnter, eventId, static_cast<uint32_t>(I), 0,
                                   sessionId);
            }

            auto out = stage.Execute(ctx);

            if (stageTraceEnabled) {
                tracer.RecordStage(TracePoint::StageExit, eventId, static_cast<uint32_t>(I),
                                   out.reasonBits, sessionId);
            }

            if (!out.success) {
                success = false;
                return;
            }
            accumulatedDirtyBits_.Merge(out.dirtyBits);
            ctx.stageMask |= (1 << I);
            RunImpl<I + 1>(ctx, success, tracer, stageTraceEnabled, eventId, sessionId);
        }
    }

    template <size_t I>
    const char *GetStageNameImpl(size_t target) const {
        if constexpr (I < kStageCount) {
            if (target == I) {
                return std::get<I>(stages_).Name();
            }
            return GetStageNameImpl<I + 1>(target);
        }
        return "Unknown";
    }
};

}    // namespace vendor::transsion::perfengine::dse
