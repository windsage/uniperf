#pragma once

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <utility>

#include <sys/system_properties.h>
#include <utils/Timers.h>

// Compile-time default when system property debug.tr_log.trace.level is unset.
// Override from Soong: -DPERFENGINE_TRACE_DEFAULT_LEVEL=0|1|2
#ifndef PERFENGINE_TRACE_DEFAULT_LEVEL
#define PERFENGINE_TRACE_DEFAULT_LEVEL 2
#endif

// Keep tag consistent across the whole adapter.
#ifndef ATRACE_TAG
#define ATRACE_TAG (ATRACE_TAG_POWER | ATRACE_TAG_HAL)
#endif
#include <utils/Trace.h>

namespace vendor {
namespace transsion {
namespace perfengine {

enum class TraceLevel : int {
    OFF = 0,
    COARSE = 1,
    VERBOSE = 2,
};

class TranTracer {
public:
    static bool isTraceOn(TraceLevel wantLevel) {
        if (!ATRACE_ENABLED()) {
            return false;
        }
        const int level = getTraceLevel();
        return level >= static_cast<int>(wantLevel);
    }

    static int getTraceLevel() {
        // Property: debug.tr_log.trace.level
        // Values: 0(off), 1(coarse), 2(verbose)
        //
        // Throttled reads to keep the hot path cheap while still supporting runtime toggling.
        static std::atomic<int> sCachedLevel{-1};
        static std::atomic<int64_t> sLastReadNs{0};

        const int64_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
        const int64_t lastNs = sLastReadNs.load(std::memory_order_relaxed);
        constexpr int64_t kThrottleNs = 500LL * 1000 * 1000; // 500ms

        int cached = sCachedLevel.load(std::memory_order_relaxed);
        if (cached >= 0 && (nowNs - lastNs) < kThrottleNs) {
            return cached;
        }

        char prop[PROP_VALUE_MAX];
        int level = PERFENGINE_TRACE_DEFAULT_LEVEL;
        const int len = __system_property_get("debug.tr_log.trace.level", prop);
        if (len > 0) {
            level = atoi(prop);
        }

        if (level < 0)
            level = 0;
        if (level > 2)
            level = 2;

        sCachedLevel.store(level, std::memory_order_relaxed);
        sLastReadNs.store(nowNs, std::memory_order_relaxed);
        return level;
    }
};

/**
 * Keeps ATRACE_BEGIN/END open until the enclosing block ends so nested callees
 * appear indented under this slice in Perfetto (same thread, stack nesting).
 *
 * Note: The old pattern `if (TTRACE_ON) { ATRACE_NAME(...); }` ends the slice
 * immediately after the if-body, so parent/child never nested.
 */
class TtraceScoped {
public:
    explicit TtraceScoped(TraceLevel want, const char *name)
        : on_(TranTracer::isTraceOn(want)) {
        if (on_) {
            ATRACE_BEGIN(name);
        }
    }

    ~TtraceScoped() {
        if (on_) {
            ATRACE_END();
        }
    }

    TtraceScoped(const TtraceScoped &) = delete;
    TtraceScoped &operator=(const TtraceScoped &) = delete;

private:
    bool on_;
};

/**
 * Like TtraceScoped but slice name is built with snprintf (dynamic fields).
 * Name is truncated to fit an internal buffer; keep format strings short.
 */
class TtraceScopedFmt {
public:
    template <typename... Args>
    explicit TtraceScopedFmt(TraceLevel want, const char *fmt, Args &&...args)
        : on_(TranTracer::isTraceOn(want)) {
        if (on_) {
            snprintf(buf_, sizeof(buf_), fmt, std::forward<Args>(args)...);
            ATRACE_BEGIN(buf_);
        }
    }

    ~TtraceScopedFmt() {
        if (on_) {
            ATRACE_END();
        }
    }

    TtraceScopedFmt(const TtraceScopedFmt &) = delete;
    TtraceScopedFmt &operator=(const TtraceScopedFmt &) = delete;

private:
    bool on_;
    char buf_[256];
};

} // namespace perfengine
} // namespace transsion
} // namespace vendor

// -------------------- Trace macros --------------------

#define TTRACE_ON(level) (vendor::transsion::perfengine::TranTracer::isTraceOn(level))

#define TTRACE_PP_CAT(a, b) TTRACE_PP_CAT_I(a, b)
#define TTRACE_PP_CAT_I(a, b) a##b

// One statement: RAII object lives until end of enclosing { } (e.g. whole function).
// Usage:
//   TTRACE_SCOPED(level, "literal_name");
//   TTRACE_SCOPED(level, "fmt id=%d ts=%lld", id, ts);
#define TTRACE_SCOPED_LIT(level, nameLiteral)                                                          \
    ::vendor::transsion::perfengine::TtraceScoped TTRACE_PP_CAT(_ttrace_sc_, __LINE__)(level, nameLiteral)

#define TTRACE_SCOPED_FMT(level, fmt, ...)                                                             \
    ::vendor::transsion::perfengine::TtraceScopedFmt TTRACE_PP_CAT(_ttrace_fmt_, __LINE__)(            \
        level, fmt, ##__VA_ARGS__)

// Macro overload: 2 args -> LIT, 3+ args -> FMT.
// Max supported argument count for TTRACE_SCOPED(...) is 5:
//   - Literal form: TTRACE_SCOPED(level, "name")                         (2 args)
//   - Fmt form:     TTRACE_SCOPED(level, "fmt %d %d %d", a, b, c)        (up to 5 args)
#define TTRACE_SCOPED_CHOOSER(_1, _2, _3, _4, _5, NAME, ...) NAME
#define TTRACE_SCOPED(...)                                                                             \
    TTRACE_SCOPED_CHOOSER(__VA_ARGS__,                                                                 \
                          TTRACE_SCOPED_FMT, TTRACE_SCOPED_FMT, TTRACE_SCOPED_FMT, TTRACE_SCOPED_LIT)  \
    (__VA_ARGS__)

// android::ScopedTrace via ATRACE_NAME; effective only inside the if-body (typically one line).
// For call-stack nesting across a whole function, prefer TTRACE_SCOPED.
#define TTRACE_NAME(level, nameLiteral)   \
    do {                                  \
        if (TTRACE_ON(level)) {           \
            ATRACE_NAME(nameLiteral);     \
        }                                 \
    } while (0)

#define TTRACE_BEGIN(level, nameLiteral) \
    do {                                 \
        if (TTRACE_ON(level)) {          \
            ATRACE_BEGIN(nameLiteral);   \
        }                                \
    } while (0)

#define TTRACE_END(level)       \
    do {                        \
        if (TTRACE_ON(level)) { \
            ATRACE_END();       \
        }                       \
    } while (0)

#define TTRACE_INT(level, nameLiteral, value)            \
    do {                                                 \
        if (TTRACE_ON(level)) {                          \
            ATRACE_INT64(nameLiteral, (int64_t)(value)); \
        }                                                \
    } while (0)

