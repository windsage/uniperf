#pragma once

/**
 * SysMonLog.h - Unified logging for SysMonitor module
 *
 * Reuses TranLog design from PerfEngine, adapted to sysmonitor namespace.
 *
 * Usage:
 *   #define LOG_TAG "SMon-Cpu"
 *   #include "SysMonLog.h"
 *   SMLOGI("collector started, interval=%d ms", intervalMs);
 *
 * Compile-time kill-switch:
 *   Android.bp cflags: ["-DSMON_LOG_DISABLED"]  →  zero overhead in release
 *
 * Runtime level control:
 *   adb shell setprop debug.sysmon.log.level 3   (3=DEBUG, 4=INFO, 5=WARN, 6=ERROR)
 */

#include <android/log.h>
#include <stdlib.h>
#include <sys/system_properties.h>

#ifndef LOG_TAG
#define LOG_TAG "SysMon"
#endif

namespace vendor {
namespace transsion {
namespace sysmonitor {

enum class LogLevel : int {
    V      = ANDROID_LOG_VERBOSE,
    D      = ANDROID_LOG_DEBUG,
    I      = ANDROID_LOG_INFO,
    W      = ANDROID_LOG_WARN,
    E      = ANDROID_LOG_ERROR,
    SILENT = 99,
};

class SysMonLogger {
public:
    /**
     * Check if the given level passes the runtime filter.
     * Property: debug.sysmon.log.level (int, maps to ANDROID_LOG_* values)
     * Called only when SMON_LOG_DISABLED is not defined.
     */
    static bool isLoggable(LogLevel level) {
#ifdef SMON_LOG_DISABLED
        (void)level;
        return false;
#endif
        char prop[92];  // PROP_VALUE_MAX = 92
        if (__system_property_get("debug.sysmon.log.level", prop) > 0) {
            int sysLevel = atoi(prop);
            if (static_cast<int>(level) < sysLevel) {
                return false;
            }
        }
        return true;
    }
};

}  // namespace sysmonitor
}  // namespace transsion
}  // namespace vendor

// ---------------------------------------------------------------------------
// Internal print macro — not for direct use
// ---------------------------------------------------------------------------
#define SMON_PRINT_LOG(level, tag, fmt, ...)                                        \
    do {                                                                            \
        if (vendor::transsion::sysmonitor::SysMonLogger::isLoggable(level)) {      \
            __android_log_print(static_cast<int>(level), tag, fmt, ##__VA_ARGS__); \
        }                                                                           \
    } while (0)

// ---------------------------------------------------------------------------
// Verbose / Debug — include file, line, function for quick debugging
// ---------------------------------------------------------------------------
#define SMON_LOGV(tag, fmt, ...)                                                                 \
    SMON_PRINT_LOG(vendor::transsion::sysmonitor::LogLevel::V, tag,                             \
                   "[%s:%d] %s: " fmt, __FILE__, __LINE__, __FUNCTION__, ##__VA_ARGS__)

#define SMON_LOGD(tag, fmt, ...)                                                                 \
    SMON_PRINT_LOG(vendor::transsion::sysmonitor::LogLevel::D, tag,                             \
                   "[%s:%d] %s: " fmt, __FILE__, __LINE__, __FUNCTION__, ##__VA_ARGS__)

// ---------------------------------------------------------------------------
// Info / Warn / Error — clean output, no metadata overhead
// ---------------------------------------------------------------------------
#define SMON_LOGI(tag, fmt, ...) \
    SMON_PRINT_LOG(vendor::transsion::sysmonitor::LogLevel::I, tag, fmt, ##__VA_ARGS__)

#define SMON_LOGW(tag, fmt, ...) \
    SMON_PRINT_LOG(vendor::transsion::sysmonitor::LogLevel::W, tag, fmt, ##__VA_ARGS__)

#define SMON_LOGE(tag, fmt, ...) \
    SMON_PRINT_LOG(vendor::transsion::sysmonitor::LogLevel::E, tag, fmt, ##__VA_ARGS__)

// ---------------------------------------------------------------------------
// Shorthand macros — auto-use LOG_TAG defined in each .cpp
// ---------------------------------------------------------------------------
#define SMLOGV(fmt, ...) SMON_LOGV(LOG_TAG, fmt, ##__VA_ARGS__)
#define SMLOGD(fmt, ...) SMON_LOGD(LOG_TAG, fmt, ##__VA_ARGS__)
#define SMLOGI(fmt, ...) SMON_LOGI(LOG_TAG, fmt, ##__VA_ARGS__)
#define SMLOGW(fmt, ...) SMON_LOGW(LOG_TAG, fmt, ##__VA_ARGS__)
#define SMLOGE(fmt, ...) SMON_LOGE(LOG_TAG, fmt, ##__VA_ARGS__)
