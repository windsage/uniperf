#pragma once

#include <android/log.h>
#include <stdlib.h>
#include <sys/system_properties.h>

/**
 * 1. Allow defining LOG_TAG before including this header.
 * If not defined (e.g., via Android.bp or source file), provide a default.
 */
#ifndef LOG_TAG
#define LOG_TAG "TPE-LOG"
#endif

namespace vendor {
namespace transsion {
namespace perfengine {

enum class Level : int {
    V = ANDROID_LOG_VERBOSE,
    D = ANDROID_LOG_DEBUG,
    I = ANDROID_LOG_INFO,
    W = ANDROID_LOG_WARN,
    E = ANDROID_LOG_ERROR,
    SILENT = 99
};

class TranLogger {
public:
    /**
     * High-performance check to determine if logging is enabled via system properties.
     * This allows dynamic log level control without restarting processes.
     */
    static bool isLoggable(Level level) {
// Global kill-switch defined via Android.bp cflags
#ifdef TRAN_LOG_DISABLED
        return false;
#endif
        /**
         * System property: persist.transsion.log.level
         * Values: 2(V), 3(D), 4(I), 5(W), 6(E), 99(SILENT)
         */
        char prop[92];    // PROP_VALUE_MAX is 92
        if (__system_property_get("debug.tr_log.log.level", prop) > 0) {
            int sysLevel = atoi(prop);
            if (static_cast<int>(level) < sysLevel)
                return false;
        }
        return true;
    }
};

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor

// --- Core Macro Definitions: Prefixed with TRAN_ to prevent naming collisions ---

#define TRAN_PRINT_LOG(level, tag, fmt, ...)                                \
    do {                                                                    \
        if (vendor::transsion::perfengine::TranLogger::isLoggable(level)) { \
            __android_log_print((int)level, tag, fmt, ##__VA_ARGS__);       \
        }                                                                   \
    } while (0)

/**
 * Detailed Logs: Includes [File:Line] and Function name.
 * Best used for Debugging and Verbose levels.
 */
#define TRAN_LOGV(tag, fmt, ...)                                                               \
    TRAN_PRINT_LOG(vendor::transsion::perfengine::Level::V, tag, "[%s:%d] %s: " fmt, __FILE__, \
                   __LINE__, __FUNCTION__, ##__VA_ARGS__)
#define TRAN_LOGD(tag, fmt, ...)                                                               \
    TRAN_PRINT_LOG(vendor::transsion::perfengine::Level::D, tag, "[%s:%d] %s: " fmt, __FILE__, \
                   __LINE__, __FUNCTION__, ##__VA_ARGS__)

/**
 * Standard Logs: Simple output without metadata.
 * Suitable for Info, Warn, and Error levels.
 */
#define TRAN_LOGI(tag, fmt, ...) \
    TRAN_PRINT_LOG(vendor::transsion::perfengine::Level::I, tag, fmt, ##__VA_ARGS__)
#define TRAN_LOGW(tag, fmt, ...) \
    TRAN_PRINT_LOG(vendor::transsion::perfengine::Level::W, tag, fmt, ##__VA_ARGS__)
#define TRAN_LOGE(tag, fmt, ...) \
    TRAN_PRINT_LOG(vendor::transsion::perfengine::Level::E, tag, fmt, ##__VA_ARGS__)

/**
 * Shorthand Macros: Automatically uses the LOG_TAG defined in the current scope.
 */
#define TLOGV(fmt, ...) TRAN_LOGV(LOG_TAG, fmt, ##__VA_ARGS__)
#define TLOGD(fmt, ...) TRAN_LOGD(LOG_TAG, fmt, ##__VA_ARGS__)
#define TLOGI(fmt, ...) TRAN_LOGI(LOG_TAG, fmt, ##__VA_ARGS__)
#define TLOGW(fmt, ...) TRAN_LOGW(LOG_TAG, fmt, ##__VA_ARGS__)
#define TLOGE(fmt, ...) TRAN_LOGE(LOG_TAG, fmt, ##__VA_ARGS__)
