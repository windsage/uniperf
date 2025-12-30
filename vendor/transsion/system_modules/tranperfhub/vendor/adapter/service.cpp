#define LOG_TAG "TranPerfHub-Service"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include "TranPerfHubAdapter.h"

using ::vendor::transsion::hardware::perfhub::ITranPerfHub;
using ::vendor::transsion::hardware::perfhub::TranPerfHubAdapter;

int main() {
    LOG(INFO) << "TranPerfHub Service starting...";

    // Initialize binder thread pool
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();

    // Create service instance
    std::shared_ptr<TranPerfHubAdapter> service = ndk::SharedRefBase::make<TranPerfHubAdapter>();

    if (!service) {
        LOG(ERROR) << "Failed to create TranPerfHub service";
        return 1;
    }

    // Register service
    const std::string instance = std::string() + ITranPerfHub::descriptor + "/default";

    binder_status_t status =
        AServiceManager_addService(service->asBinder().get(), instance.c_str());

    if (status != STATUS_OK) {
        LOG(ERROR) << "Failed to register TranPerfHub service: " << status;
        return 1;
    }

    LOG(INFO) << "TranPerfHub Service registered: " << instance;

    // Join thread pool
    ABinderProcess_joinThreadPool();

    LOG(INFO) << "TranPerfHub Service exiting...";
    return 0;
}
