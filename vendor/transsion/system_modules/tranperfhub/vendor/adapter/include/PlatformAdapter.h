#ifndef PLATFORM_ADAPTER_H
#define PLATFORM_ADAPTER_H

#include <string>
#include <vector>
using vendor::transsion::perfhub::Platform;
namespace vendor {
namespace transsion {
namespace hardware {
namespace perfhub {

/**
 * Platform Adapter
 *
 * Responsibilities:
 *  - Load platform-specific functions via dlsym
 *  - Provide unified interface for performance lock operations
 *
 * Design:
 *  - Uses dlsym(RTLD_DEFAULT, ...) to find functions in current process
 *  - QCOM: Calls perfmodule_submit_request() directly
 *  - MTK: Calls libpowerhal_LockAcq/Rel() directly
 */
class PlatformAdapter {
public:
    PlatformAdapter();
    ~PlatformAdapter();

    /**
     * Initialize platform adapter
     * @return true on success
     */
    bool init();

    /**
     * Acquire performance lock
     *
     * @param eventId Event/Scene ID
     * @param duration Duration in milliseconds
     * @param intParams Full parameter array from AIDL
     * @param packageName Optional package name
     * @return Platform-specific handle (>0 on success, <0 on failure)
     */
    int32_t acquirePerfLock(int32_t eventId, int32_t duration,
                            const std::vector<int32_t> &intParams, const std::string &packageName);

    /**
     * Release performance lock
     *
     * @param handle Platform-specific handle
     */
    void releasePerfLock(int32_t handle);

private:
    bool initQcom();
    bool initMtk();
    bool initUnisoc();

    int32_t qcomAcquirePerfLock(int32_t eventId, int32_t duration,
                                const std::vector<int32_t> &intParams,
                                const std::string &packageName);
    void qcomReleasePerfLock(int32_t handle);

    int32_t mtkAcquirePerfLock(int32_t eventId, int32_t duration,
                               const std::vector<int32_t> &intParams,
                               const std::string &packageName);
    void mtkReleasePerfLock(int32_t handle);

    // TODO: Map eventId to platform-specific opcodes
    void mapEventToOpcodes(int32_t eventId, std::vector<int32_t> &opcodes);

private:
    Platform mPlatform;

    // QCOM function pointers
    struct QcomFunctions {
        void *submitRequest;    // perfmodule_submit_request
    } mQcomFuncs;

    // MTK function pointers
    struct MtkFunctions {
        void *lockAcq;    // libpowerhal_LockAcq
        void *lockRel;    // libpowerhal_LockRel
    } mMtkFuncs;
};

}    // namespace perfhub
}    // namespace hardware
}    // namespace transsion
}    // namespace vendor

#endif    // PLATFORM_ADAPTER_H
