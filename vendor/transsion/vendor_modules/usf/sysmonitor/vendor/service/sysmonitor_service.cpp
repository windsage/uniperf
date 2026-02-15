#define LOG_TAG "sysmonitor-service"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include <csignal>
#include <cstring>

#include "SysMonLog.h"
#include "SysMonitor.h"

/**
 * sysmonitor_service.cpp - Standalone vendor daemon entry point
 *
 * Startup sequence:
 *   1. Create SysMonitor facade
 *   2. init() — create ashmem, instantiate collectors, run NodeProbe
 *   3. Register AIDL service with ServiceManager
 *   4. start() — launch sampling thread
 *   5. joinRpcThreadpool() — serve AIDL calls until SIGTERM
 *
 * Service name: "vendor.transsion.hardware.sysmonitor.ISysMonitor/default"
 */

using vendor::transsion::sysmonitor::SysMonitor;

static const char *kServiceName = "vendor.transsion.hardware.sysmonitor.ISysMonitor/default";

// ---------------------------------------------------------------------------
// Signal handler: graceful shutdown on SIGTERM / SIGINT
// ---------------------------------------------------------------------------
static std::shared_ptr<SysMonitor> *gSysMonitor = nullptr;

static void onSignal(int sig) {
    SMLOGI("Received signal %d, shutting down...", sig);
    if (gSysMonitor && *gSysMonitor) {
        (*gSysMonitor)->stop();
    }
    // Let the process exit naturally after joinRpcThreadpool returns
    // (ABinderProcess_joinRpcThreadpool does not return on normal operation,
    //  so we explicitly exit here from signal context)
    _exit(0);
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------
int main(int /*argc*/, char * /*argv*/[]) {
    android::base::SetDefaultTag(LOG_TAG);
    android::base::InitLogging(nullptr, android::base::LogdLogger());

    SMLOGI("sysmonitor-service starting...");

    // Allow Binder thread pool to handle concurrent AIDL calls
    // 4 threads: enough for typical consumer count (perfengine + stats + 2 spare)
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();

    // Create and initialize the main service object
    auto sysmon = SysMonitor::create();
    if (!sysmon) {
        SMLOGE("Failed to allocate SysMonitor");
        return 1;
    }

    gSysMonitor = &sysmon;    // expose to signal handler

    if (!sysmon->init()) {
        SMLOGE("SysMonitor::init() failed");
        return 1;
    }

    // Register with AIDL ServiceManager
    binder_status_t status = AServiceManager_addService(sysmon->asBinder().get(), kServiceName);
    if (status != STATUS_OK) {
        SMLOGE("AServiceManager_addService failed: status=%d", status);
        return 1;
    }
    SMLOGI("Service registered: %s", kServiceName);

    // Start sampling thread
    if (!sysmon->start()) {
        SMLOGE("SysMonitor::start() failed");
        return 1;
    }

    // Register signal handlers for graceful shutdown
    signal(SIGTERM, onSignal);
    signal(SIGINT, onSignal);

    SMLOGI("sysmonitor-service ready, joining thread pool");

    // Block until the service is shut down
    ABinderProcess_joinRpcThreadpool();

    // Cleanup (reached only via explicit exit path)
    sysmon->stop();
    SMLOGI("sysmonitor-service exiting");
    return 0;
}
