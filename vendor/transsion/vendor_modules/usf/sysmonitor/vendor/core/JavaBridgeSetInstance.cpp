#define LOG_TAG "SMon-JBridge"

/**
 * JavaBridgeSetInstance.cpp
 *
 * Provides a C-linkage wrapper so SysMonitor.cpp can set the
 * JavaBridgeCollector singleton without including its full class definition.
 *
 * This is compiled as part of libsysmonitor together with
 * JavaBridgeCollector.cpp, so the cast is safe.
 */

// Forward declare the internal class method to avoid circular include
namespace vendor {
namespace transsion {
namespace sysmonitor {
class JavaBridgeCollector {
public:
    static void setInstance(JavaBridgeCollector *inst);
};
}    // namespace sysmonitor
}    // namespace transsion
}    // namespace vendor

extern "C" void javaBridgeSetInstance(void *inst) {
    using JBC = vendor::transsion::sysmonitor::JavaBridgeCollector;
    JBC::setInstance(static_cast<JBC *>(inst));
}
