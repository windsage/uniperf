#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "StringUtils.h"

namespace vendor {
namespace transsion {
namespace perfengine {

/**
 * Parsed context from AIDL notifyEventStart.
 *
 * Both intParams and extraStrings are positional; per-event semantics
 * are defined in resolveInts() and resolveStrings() respectively.
 *
 * To add a new event:
 *   1. Add a case in resolveInts()   — document intParams[0..n] meaning.
 *   2. Add a case in resolveStrings() — document strings[0..n] meaning.
 *   3. Keep in sync with the Java call site.
 */
struct EventContext {
    int32_t eventId = 0;

    // 这里是以后需要补充的参数，用于转换ints中的每个元素
    int32_t intParam0 = 0;
    int32_t intParam1 = 0;

    // Well-known string fields — populated by resolveStrings()
    std::string package;
    std::string activity;

    // Raw positional storage
    static constexpr size_t MAX_INTS = 4;
    static constexpr size_t MAX_STRINGS = 3;

    int32_t ints[MAX_INTS] = {};
    size_t intCount = 0;
    std::string strings[MAX_STRINGS] = {};
    size_t stringCount = 0;

    // Bounds-safe raw accessors
    int32_t getInt(size_t i) const { return i < intCount ? ints[i] : 0; }
    const std::string &getString(size_t i) const {
        static const std::string kEmpty;
        return i < stringCount ? strings[i] : kEmpty;
    }

    // ---- factory ----

    static EventContext parse(int32_t eventId, const std::vector<int32_t> &intParams,
                              const std::string &extraStrings) {
        EventContext ctx;
        ctx.eventId = eventId;

        // Store raw ints
        ctx.intCount = std::min(intParams.size(), MAX_INTS);
        for (size_t i = 0; i < ctx.intCount; ++i) {
            ctx.ints[i] = intParams[i];
        }

        // Store raw strings
        if (!extraStrings.empty()) {
            std::vector<std::string> tokens = utils::splitStrings(extraStrings);
            ctx.stringCount = std::min(tokens.size(), MAX_STRINGS);
            for (size_t i = 0; i < ctx.stringCount; ++i) {
                ctx.strings[i] = std::move(tokens[i]);
            }
        }

        ctx.resolveInts();
        ctx.resolveStrings();
        return ctx;
    }

private:
    /**
     * Map positional intParams to named fields per eventId.
     *
     * Default convention (used when no specific case matches):
     *   ints[0] = intParam0 ms
     *   ints[1] = intParam1 Hz
     */
    void resolveInts() {
        switch (eventId) {
            // --------------------------------------------------------
            // APP_LAUNCH (id=1)
            // ints[0] = intParam0
            // ints[1] = intParam1
            // --------------------------------------------------------
            case 1:
                intParam0 = getInt(0);
                intParam1 = getInt(1);
                break;

            // --------------------------------------------------------
            // FOREGROUND_APP (id=7)
            // ints[0] = intParam0
            // ints[1] = intParam1
            // --------------------------------------------------------
            case 7:
                intParam0 = getInt(0);
                intParam1 = getInt(1);
                break;

                // --------------------------------------------------------
                // Future events: add cases here.
                // Named fields left at default (0) if not applicable.
                // --------------------------------------------------------

            default:
                // Fallback: treat [0]=intParam0, [1]=intParam1 universally
                intParam0 = getInt(0);
                intParam1 = getInt(1);
                break;
        }
    }

    /**
     * Map positional extraStrings tokens to named fields per eventId.
     */
    void resolveStrings() {
        switch (eventId) {
            // --------------------------------------------------------
            // APP_LAUNCH (id=1)
            // strings[0] = package name
            // --------------------------------------------------------
            case 1:
                package = getString(0);
                break;

            // --------------------------------------------------------
            // FOREGROUND_APP (id=7)
            // strings[0] = package name
            // strings[1] = activity class name (optional)
            // --------------------------------------------------------
            case 7:
                package = getString(0);
                activity = getString(1);
                break;

                // --------------------------------------------------------
                // Future events: add cases here.
                // --------------------------------------------------------

            default:
                // No named mapping; caller uses getString(n) directly.
                break;
        }
    }
};

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
