/**
 * @file ServiceBridge.cpp
 * @brief DSE 服务桥接层实现 - 连接 AIDL 接口与 DSE 核心调度引擎
 *
 * 本文件实现 ServiceBridge 类，作为 Android Binder 服务与 DSE（确定性调度引擎）
 * 之间的桥接层。遵循"整机资源确定性调度抽象框架"的设计规范。
 *
 * ## 架构定位
 *
 * ServiceBridge 在框架中承担以下职责：
 * 1. **接口适配层**：将 AIDL 接口调用转换为 DSE 内部事件格式
 * 2. **双路径决策入口**：根据场景特征选择 FastPath 或 ConvergePath
 * 3. **降级兜底机制**：当 DSE 不可用时回退到 Legacy PerfLock 机制
 * 4. **灰度发布控制**：实现 DSE 功能的渐进式灰度上线
 *
 * ## 框架核心概念映射
 *
 * - **意图等级 (P1-P4)**：通过 eventId 和场景语义映射到资源交付契约
 * - **四维调度空间**：
 *   - 空间维度：资源供需匹配（CPU/GPU/Memory/IO）
 *   - 时间维度：租约管理与契约衰减
 *   - 意图维度：优先级仲裁与意图继承
 *   - 约束维度：热/电/内存压力等动态边界
 * - **双路径决策模型**：
 *   - FastPath：P1 首响应快速保障（亚毫秒级）
 *   - ConvergePath：完整决策链收敛（毫秒级）
 *
 * ## 关键流程
 *
 * 1. notifyEventStart → TryDseHandling → FastPath/ConvergePath → ExecuteDecision
 * 2. DSE 失败 → FallbackToLegacy → PerfLockCaller
 * 3. 灰度采样 → ShouldUseGrayscale → 决定是否启用 DSE
 *
 * @see 整机资源确定性调度抽象框架.md
 * @author PerfEngine Team
 */

#include "ServiceBridge.h"

#define ATRACE_TAG (ATRACE_TAG_POWER | ATRACE_TAG_HAL)
#include <android-base/properties.h>
#include <android/binder_ibinder.h>
#include <sched.h>
#include <sys/prctl.h>
#include <unistd.h>

#include <cerrno>
#include <chrono>
#include <cstring>
#include <string>
#include <thread>

#include "CallerValidator.h"
#include "PerfLockCaller.h"
#include "dse/config/ConfigLoader.h"
#include "dse/config/RuleTable.h"
#include "dse/core/profile/ProfileSpec.h"
#include "dse/core/service/ResourceScheduleService.h"
#include "dse/core/service/state/StateService.h"
#include "dse/core/types/CoreTypes.h"
#include "dse/trace/TraceRecorder.h"
#include "perf-utils/TranLog.h"
#include "perf-utils/TranTrace.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "TPE-Bridge"
namespace vendor {
namespace transsion {
namespace perfengine {

namespace {

/**
 * @brief 统一桥接层 fallback trace 写入。
 *
 * 该 helper 只负责桥接层已有的降级原因封装，避免不同调用点重复拼接
 * `FallbackContext` 并出现 session 绑定规则漂移。
 */
inline void RecordBridgeFallback(dse::SessionId sessionId, dse::DecisionFallbackReason reason,
                                 uint32_t detailBits) {
    dse::FallbackContext ctx;
    ctx.sessionId = sessionId;
    ctx.reason = reason;
    ctx.detailBits = detailBits;
    dse::TraceRecorder::Instance().RecordFallback(ctx);
}

inline void RecordBridgeFallback(dse::DecisionFallbackReason reason, uint32_t detailBits) {
    RecordBridgeFallback(0, reason, detailBits);
}

}    // namespace

/**
 * @brief 基于 StateService 的约束快照提供者
 *
 * 从 StateService 的缓存快照读取约束状态：
 * - 热约束：温度等级
 * - 电约束：电量等级、省电模式
 * - 内存约束：内存压力
 * - 显示约束：熄屏状态
 *
 * 遵循框架 §5.4 原子化状态更新原则：
 * - 热路径禁止直接读取底层节点
 * - 只读缓存快照，保证同轮决策一致性
 *
 * @note 约束维度始终高于意图等级
 */
class StateBasedConstraintProvider : public dse::ConstraintSnapshotProvider {
public:
    explicit StateBasedConstraintProvider(dse::StateService *stateService)
        : stateService_(stateService) {}

    dse::ConstraintSnapshot Read() override {
        if (!stateService_ || !stateService_->IsReady()) {
            return GetFallbackSnapshot();
        }

        auto &aggregator = stateService_->GetAggregator();
        return GetCachedConstraint(aggregator);
    }

private:
    dse::ConstraintSnapshot GetFallbackSnapshot() {
        dse::ConstraintSnapshot snapshot;
        snapshot.thermalLevel = 0;
        snapshot.thermalScaleQ10 = 1024;
        snapshot.batteryLevel = 100;
        snapshot.batteryScaleQ10 = 1024;
        snapshot.memoryPressure = 0;
        snapshot.psiLevel = 0;
        snapshot.psiScaleQ10 = 1024;
        snapshot.powerState = dse::PowerState::Active;
        snapshot.batterySaver = false;
        snapshot.screenOn = true;
        snapshot.thermalSevere = false;
        return snapshot;
    }

    dse::ConstraintSnapshot GetCachedConstraint(dse::StateAggregator &aggregator);

    dse::StateService *stateService_;
};

/**
 * @brief 基于 StateService 的能力提供者
 *
 * 从 StateService 的缓存快照读取平台能力：
 * - CPU 频率范围和算力上限
 * - 内存带宽和容量
 * - GPU 算力和频率
 * - 存储 I/O 能力
 *
 * 遵循框架"编译期平台配置"原则：
 * - 静态基线能力在编译期确定
 * - 运行时只读取热重载的动态部分
 *
 * @note 能力边界由 EnvelopeStage 在 pinned snapshot 上唯一生成
 */
class StateBasedCapabilityProvider : public dse::CapabilityProvider {
public:
    explicit StateBasedCapabilityProvider(dse::StateService *stateService)
        : stateService_(stateService) {}

    dse::CapabilitySnapshot Read() override {
        if (!stateService_ || !stateService_->IsReady()) {
            return GetFallbackSnapshot();
        }

        auto &aggregator = stateService_->GetAggregator();
        return GetCachedCapability(aggregator);
    }

private:
    dse::CapabilitySnapshot GetFallbackSnapshot() { return dse::CapabilitySnapshot{}; }

    dse::CapabilitySnapshot GetCachedCapability(dse::StateAggregator &aggregator);

    dse::StateService *stateService_;
};

dse::ConstraintSnapshot StateBasedConstraintProvider::GetCachedConstraint(
    dse::StateAggregator &aggregator) {
    return aggregator.GetCachedConstraint();
}

dse::CapabilitySnapshot StateBasedCapabilityProvider::GetCachedCapability(
    dse::StateAggregator &aggregator) {
    return aggregator.GetCachedCapability();
}

// ==================== Constructor / Destructor ====================

/**
 * @brief ServiceBridge 构造函数 - 初始化 DSE 服务与 Legacy 兜底机制
 *
 * 初始化流程遵循框架 §5.4 原子化状态更新原则：
 *
 * 1. **Trace 系统初始化**
 *    - 配置 Trace 级别，用于决策链追踪与问题诊断
 *    - 遵循框架"可验证、可解释、可分级"的确定性原则
 *
 * 2. **DSE 核心服务初始化**
 *    - 根据编译期配置选择 Profile 规格（Entry/Main/Flagship）
 *    - Profile 决定资源掩码、决策链组成、Trace 级别等
 *    - 遵循框架"编译期平台配置"原则，避免运行时动态判定
 *
 * 3. **灰度发布配置**
 *    - 读取 persist.vendor.perfengine.dse.grayscale 属性
 *    - 默认关闭 DSE，通过灰度百分比渐进式放量
 *    - 遵循框架"安全第一、渐进放量"的商用原则
 *
 * 4. **Legacy 兜底机制初始化**
 *    - PerfLockCaller 作为 DSE 不可用时的降级路径
 *    - 确保系统在任何情况下都有可用的资源调度能力
 *
 * @note 约束维度（热/电/内存压力）由 SafetyController 管理
 * @note 意图等级（P1-P4）由 ResourceScheduleService 根据场景推导
 */
ServiceBridge::ServiceBridge() {
    TLOGI("ServiceBridge initializing...");

    const DseProfileSpec profileSpec{};
    auto &tracer = dse::TraceRecorder::Instance();
    dse::TraceConfig traceConfig;
    traceConfig.level = profileSpec.traceLevel;
#if PERFENGINE_PRODUCTION
    traceConfig.level = 0;
    traceConfig.enableStageTrace = false;
    traceConfig.enableDecisionTrace = false;
    traceConfig.enableActionTrace = false;
#endif
    tracer.Init(traceConfig);

    bool canonicalRulesReady = true;
    {
        std::string cfgPathProp =
            android::base::GetProperty("persist.vendor.perfengine.dse.config", "");
        const char *cfgPath = cfgPathProp.empty() ? nullptr : cfgPathProp.c_str();
        const bool configLoaded = dse::ConfigLoader::Instance().Load(cfgPath);
        std::string rulePathProp =
            android::base::GetProperty("persist.vendor.perfengine.dse.rules", "");
        const char *rulePath = rulePathProp.empty() ? nullptr : rulePathProp.c_str();
        const bool ruleLoaded = dse::RuleTable::Instance().Load(rulePath);
        canonicalRulesReady = configLoaded && ruleLoaded;
        if (!canonicalRulesReady) {
            TLOGE(
                "Canonical DSE config/rule source failed to load (config=%d rule=%d), disable DSE",
                static_cast<int>(configLoaded), static_cast<int>(ruleLoaded));
            RecordBridgeFallback(dse::DecisionFallbackReason::ServiceUnavailable,
                                 (configLoaded ? 0u : 0x01u) | (ruleLoaded ? 0u : 0x02u));
        }
    }

#if !PERFENGINE_PRODUCTION
    if (canonicalRulesReady) {
        const uint32_t runtimeTraceLevel = dse::ConfigLoader::Instance().GetParams().traceLevel;
        tracer.SetLevel(std::min(profileSpec.traceLevel, runtimeTraceLevel));
    }
#endif

    mDseService = std::make_unique<dse::ResourceScheduleService<DseProfileSpec>>();
    if (mDseService) {
        bool dseReady = canonicalRulesReady;
        int32_t grayscalePercent =
            android::base::GetIntProperty("persist.vendor.perfengine.dse.grayscale", 0);
        int32_t forceEnable =
            android::base::GetIntProperty("persist.vendor.perfengine.dse.force_enable", 0);

        auto &safetyController = mDseService->GetSafetyController();
        safetyController.SetGrayscalePercent(static_cast<uint32_t>(grayscalePercent));

        safetyController.AddUidToWhitelist(1000);
        safetyController.AddUidToWhitelist(1013);
        safetyController.AddUidToWhitelist(1023);
        safetyController.AddUidToWhitelist(1041);
        safetyController.AddUidToWhitelist(1076);
        safetyController.AddUidToWhitelist(1088);
        safetyController.AddUidToWhitelist(1099);

        int32_t aclMode =
            android::base::GetIntProperty("persist.vendor.perfengine.dse.acl_mode", 0);
        if (aclMode == 1) {
            safetyController.SetEnableUidWhitelist(true);
            TLOGI("DSE ACL whitelist mode enabled");
        } else if (aclMode == 2) {
            safetyController.SetEnableUidBlacklist(true);
            TLOGI("DSE ACL blacklist mode enabled");
        }

        if (forceEnable == 1) {
            safetyController.Enable();
            TLOGI("DSE force enabled via system property");
        } else if (grayscalePercent > 0) {
            TLOGI("DSE grayscale mode configured: %d%% (will sample per-event)", grayscalePercent);
        } else {
            TLOGI("DSE disabled by default (grayscale=0, force_enable=0)");
        }

        if (dseReady) {
            mStateService = std::make_unique<dse::StateService>(mDseService->GetStateVault());
        }
        if (dseReady && !mStateService) {
            TLOGE("StateService allocation failed; DSE will fall back to legacy only");
            RecordBridgeFallback(dse::DecisionFallbackReason::ServiceUnavailable, 0x04);
            dseReady = false;
        }
        if (dseReady && mStateService) {
            if (!mStateService->Init(mDseService->GetPlatformContext().capability.vendor)) {
                TLOGE("StateService::Init failed; DSE will fall back to legacy only");
                RecordBridgeFallback(dse::DecisionFallbackReason::StateBaselineSynthetic, 0x01);
                dseReady = false;
            } else if (!mStateService->Start()) {
                TLOGE("StateService::Start failed; DSE will fall back to legacy only");
                RecordBridgeFallback(dse::DecisionFallbackReason::StateBaselineSynthetic, 0x02);
                dseReady = false;
            } else if (!mStateService->WaitUntilReady(dse::StateService::kBootstrapHardTimeoutMs)) {
                TLOGE("StateService::WaitUntilReady timed out; DSE will fall back to legacy only");
                RecordBridgeFallback(
                    dse::DecisionFallbackReason::StateBootstrapTimeout,
                    static_cast<uint32_t>(dse::StateService::kBootstrapHardTimeoutMs));
                dseReady = false;
            }
        }

        if (dseReady) {
            mConstraintProvider =
                std::make_unique<StateBasedConstraintProvider>(mStateService.get());
            mCapabilityProvider =
                std::make_unique<StateBasedCapabilityProvider>(mStateService.get());
            mDseService->Start(mConstraintProvider.get(), mCapabilityProvider.get());
        } else {
            mConstraintProvider.reset();
            mCapabilityProvider.reset();
            mStateService.reset();
            mDseService.reset();
            TLOGW("DSE disabled; ServiceBridge will use legacy fallback only");
        }

#if !PERFENGINE_PRODUCTION
        RecordBridgeFallback(dse::DecisionFallbackReason::None,
                             (grayscalePercent << 8) | (forceEnable & 0xFF));
#endif

#if defined(DSE_PROFILE_FLAGSHIP)
        TLOGI("DSE ResourceScheduleService initialized with FlagshipProfileSpec");
#elif defined(DSE_PROFILE_MAIN)
        TLOGI("DSE ResourceScheduleService initialized with MainProfileSpec");
#else
        TLOGI("DSE ResourceScheduleService initialized with EntryProfileSpec");
#endif
    } else {
        TLOGW("Failed to initialize DSE service, will use legacy fallback only");
    }
    mPerfLockCaller = std::make_unique<PerfLockCaller>();
    if (!mPerfLockCaller || !mPerfLockCaller->init()) {
        TLOGE("Failed to initialize PerfLockCaller");
        mPerfLockCaller.reset();
        return;
    }

    startWorkerPool();
    TLOGI("ServiceBridge initialized successfully (DSE primary, legacy fallback)");
}

/**
 * @brief ServiceBridge 析构函数 - 清理活跃事件与监听器
 *
 * 清理流程确保：
 * 1. 所有活跃事件被正确释放（避免资源泄漏）
 * 2. 监听器的 Binder 死亡通知被正确解绑
 * 3. DSE 服务与 Legacy 机制被有序销毁
 *
 * @note 遵循 RAII 原则，确保异常路径下资源也能正确释放
 */
ServiceBridge::~ServiceBridge() {
    TLOGI("ServiceBridge destroying...");
    stopWorkerPool();
    {
        std::lock_guard<Mutex> lock(mEventLock);
        for (auto &pair : mActiveEvents) {
            if (!pair.second.dseHandled && mPerfLockCaller && pair.second.platformHandle > 0) {
                TLOGI("Releasing active event 0x%x handle=%d on destroy",
                      static_cast<unsigned>(pair.first),
                      pair.second.platformHandle);
                mPerfLockCaller->releasePerfLock(pair.second.platformHandle);
            }
        }
        mActiveEvents.clear();
    }

    {
        std::lock_guard<Mutex> lock(mListenerLock);
        for (auto &info : mListeners) {
            if (info.cookie) {
                AIBinder_unlinkToDeath(info.listener->asBinder().get(), info.deathRecipient.get(),
                                       info.cookie);
                delete info.cookie;
            }
        }
        mListeners.clear();
    }

    mDseService.reset();
    mPerfLockCaller.reset();

    TLOGI("ServiceBridge destroyed");
}

// ==================== Worker Thread Pool ====================

void ServiceBridge::startWorkerPool() {
    mShutdown.store(false, std::memory_order_relaxed);
    mWorkerThreads.reserve(kWorkerThreadCount);

    for (int i = 0; i < kWorkerThreadCount; ++i) {
        mWorkerThreads.emplace_back([this, i]() {
            workerLoop(i + 1);    // index 从 1 开始，命名为 tpe_worker_1 ~ tpe_worker_4
        });
    }
    TLOGI("Worker pool started: %d threads", kWorkerThreadCount);
}

void ServiceBridge::stopWorkerPool() {
    {
        std::lock_guard<std::mutex> lock(mQueueMutex);
        mShutdown.store(true, std::memory_order_relaxed);
    }
    mQueueCv.notify_all();    // 唤醒所有等待的工作线程

    for (auto &t : mWorkerThreads) {
        if (t.joinable())
            t.join();
    }
    mWorkerThreads.clear();
    TLOGI("Worker pool stopped");
}

void ServiceBridge::workerLoop(int index) {
    // Set thread name: "tpe_worker_1" ~ "tpe_worker_4", max 15 chars
    char name[16];
    snprintf(name, sizeof(name), "tpe_worker_%d", index);
    prctl(PR_SET_NAME, name, 0, 0, 0);

    struct sched_param param;
    param.sched_priority = 1;
    int res = sched_setscheduler(0, SCHED_FIFO, &param);
    if (res == 0) {
        TLOGI("worker thread '%s' started, tid=%d and elevated to SCHED_FIFO successfully", name,
              gettid());
    } else {
        TLOGW("worker thread '%s' elevated to SCHED_FIFO failed, tid=%d , errno=%d, error=%s", name,
              gettid(), errno, strerror(errno));
    }

    while (true) {
        EventTask task;
        {
            std::unique_lock<std::mutex> lock(mQueueMutex);
            // Block until there's a task or shutdown is requested
            mQueueCv.wait(lock, [this]() {
                return !mTaskQueue.empty() || mShutdown.load(std::memory_order_relaxed);
            });

            if (mShutdown.load(std::memory_order_relaxed) && mTaskQueue.empty()) {
                break;    // Drain remaining tasks before exit
            }

            task = std::move(mTaskQueue.front());
            mTaskQueue.pop();
            TTRACE_INT(TraceLevel::COARSE, LOG_TAG ":TaskQueue", (int64_t)mTaskQueue.size());
        }

        // Execute task outside the lock
        if (task.type == EventTask::Type::START) {
            processEventStart(task);
        } else {
            processEventEnd(task);
        }
    }

    TLOGI("Worker thread '%s' tid=%d exiting", name, gettid());
}

void ServiceBridge::enqueueTask(EventTask task) {
    {
        std::lock_guard<std::mutex> lock(mQueueMutex);
        mTaskQueue.push(std::move(task));
        TTRACE_INT(TraceLevel::COARSE, LOG_TAG ":TaskQueue", (int64_t)mTaskQueue.size());
    }
    mQueueCv.notify_one();
}

// ==================== Task Processing (runs on worker threads) ====================
void ServiceBridge::processEventStart(const EventTask &task) {
    TTRACE_SCOPED(TraceLevel::VERBOSE, LOG_TAG ":processEventStart id=0x%x ts=%lld",
                  static_cast<unsigned>(task.eventId), static_cast<long long>(task.timestamp));

    broadcastEventStart(task.eventId, task.timestamp, task.numParams, task.intParams,
                        task.extraStrings);
}

void ServiceBridge::processEventEnd(const EventTask &task) {
    TTRACE_SCOPED(TraceLevel::VERBOSE, LOG_TAG ":processEventEnd id=0x%x ts=%lld",
                  static_cast<unsigned>(task.eventId), static_cast<long long>(task.timestamp));

    broadcastEventEnd(task.eventId, task.timestamp, task.extraStrings);
}

void ServiceBridge::enqueueEventStartListenerBroadcast(
    int32_t eventId, int64_t timestamp, int32_t numParams, const std::vector<int32_t> &intParams,
    const std::optional<std::string> &extraStrings) {
    EventTask task;
    task.type = EventTask::Type::START;
    task.eventId = eventId;
    task.timestamp = timestamp;
    task.numParams = numParams;
    task.intParams = intParams;
    task.extraStrings = extraStrings;
    enqueueTask(std::move(task));
}

void ServiceBridge::enqueueEventEndListenerBroadcast(
    int32_t eventId, int64_t timestamp, const std::optional<std::string> &extraStrings) {
    EventTask task;
    task.type = EventTask::Type::END;
    task.eventId = eventId;
    task.timestamp = timestamp;
    task.extraStrings = extraStrings;
    enqueueTask(std::move(task));
}

// ==================== Event Notification Implementation ====================

/**
 * @brief 处理事件开始通知 - DSE 决策入口
 *
 * 这是 DSE 调度决策的主入口，遵循框架 §2.4 双路径决策模型：
 *
 * ## 处理流程
 *
 * 1. **事件解析与转换**
 *    - 将 AIDL 参数转换为 DSE 内部 SchedEvent 格式
 *    - 提取调用者 UID/PID 用于权限校验
 *
 * 2. **DSE 处理尝试**
 *    - 调用 TryDseHandling() 尝试走 DSE 决策链
 *    - 内部会根据灰度状态、安全检查决定是否启用 DSE
 *
 * 3. **Legacy 降级**
 *    - DSE 不可用或失败时，调用 FallbackToLegacy()
 *    - 使用传统 PerfLock 机制保障基本调度能力
 *
 * 4. **状态记录**
 *    - 记录事件处理结果（DSE 成功/Legacy 降级/失败）
 *    - 用于后续事件结束时的资源释放
 *
 * ## 框架映射
 *
 * - **意图等级推导**：由 IntentStage 根据 eventId 和场景语义推导 P1-P4
 * - **时间契约**：由 TemporalContract 管理租约起止与衰减
 * - **约束检查**：由 SafetyController 检查热/电/内存压力
 * - **资源仲裁**：由 ArbitrationStage 在多任务竞争时仲裁
 *
 * @param eventId 事件标识符，映射到具体场景类型
 * @param timestamp 事件时间戳（纳秒）
 * @param numParams 参数数量
 * @param intParams 整型参数数组
 * @param extraStrings 额外字符串参数
 * @return AIDL 状态码
 *
 * @note P1 场景优先走 FastPath，P2-P4 走 ConvergePath
 * @see TryDseHandling, FallbackToLegacy
 */
::ndk::ScopedAStatus ServiceBridge::notifyEventStart(
    int32_t eventId, int64_t timestamp, int32_t numParams, const std::vector<int32_t> &intParams,
    const std::optional<std::string> &extraStrings) {
    TTRACE_SCOPED(TraceLevel::COARSE, LOG_TAG ":notifyEventStart id=0x%x ts=%lld",
                  static_cast<unsigned>(eventId), static_cast<long long>(timestamp));
    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        return ::ndk::ScopedAStatus::ok();
    }

    if (static_cast<int32_t>(intParams.size()) != numParams) {
        TLOGE("Param count mismatch: expected=%d actual=%zu", numParams, intParams.size());
        return ::ndk::ScopedAStatus::ok();
    }

    const uid_t callerUid = AIBinder_getCallingUid();
    const pid_t callerPid = AIBinder_getCallingPid();

    if (!CallerValidator::isEventAllowed(callerUid, eventId)) {
        TLOGW("notifyEventStart DENIED: uid=%u pid=%d eventId=0x%x", callerUid, callerPid, eventId);
        return ::ndk::ScopedAStatus::ok();
    }
    TLOGI("notifyEventStart: uid=%u pid=%d eventId=0x%x", callerUid, callerPid, eventId);

    EventContext ctx = EventContext::parse(eventId, intParams, extraStrings.value_or(""));
    ctx.callerUid = static_cast<int32_t>(callerUid);
    ctx.callerPid = static_cast<int32_t>(callerPid);

    auto schedEvent = ConvertToSchedEvent(ctx, timestamp, numParams, intParams);
    const auto sessionId = dse::ResolveSchedSessionId(schedEvent);

    bool dseHandled = false;
    int32_t platformHandle = -1;

    if (mDseService) {
        dseHandled = TryDseHandling(schedEvent);
    }

    if (!dseHandled) {
        platformHandle = FallbackToLegacy(schedEvent, ctx.package);
        if (platformHandle < 0) {
            TLOGE(
                "Both DSE and legacy failed for eventId=0x%x"
                " - CRITICAL: no resource allocation path available",
                static_cast<unsigned>(eventId));
            RecordBridgeFallback(sessionId, dse::DecisionFallbackReason::ExecutionFailed, 0xFF);
            {
                std::lock_guard<Mutex> lock(mEventLock);
                EventInfo info;
                info.platformHandle = -1;
                info.startTime = timestamp;
                info.packageName = ctx.package;
                info.dseHandled = false;
                info.failed = true;
                mActiveEvents[eventId] = info;
            }
            // Enqueue broadcast even on failure so listeners stay consistent
            enqueueEventStartListenerBroadcast(eventId, timestamp, numParams, intParams,
                                               extraStrings);
            return ::ndk::ScopedAStatus::fromExceptionCode(EX_TRANSACTION_FAILED);
        }
    }

    {
        std::lock_guard<Mutex> lock(mEventLock);
        auto it = mActiveEvents.find(eventId);
        if (it != mActiveEvents.end()) {
            TLOGW("Event 0x%x already active, releasing old handle=%d",
                  static_cast<unsigned>(eventId), it->second.platformHandle);
            if (!it->second.dseHandled && mPerfLockCaller && it->second.platformHandle > 0) {
                mPerfLockCaller->releasePerfLock(it->second.platformHandle);
            }
        }
        EventInfo info;
        info.platformHandle = platformHandle;
        info.startTime = timestamp;
        info.packageName = ctx.package;
        info.dseHandled = dseHandled;
        mActiveEvents[eventId] = info;
    }

    TLOGI("Event 0x%x started: dseHandled=%d legacyHandle=%d", static_cast<unsigned>(eventId),
          dseHandled, platformHandle);

    // Same listener broadcast queue for DSE success and Legacy success — do not add a branch
    // that skips enqueueEventStartListenerBroadcast.
    // Dispatch on worker: listener Binder calls may be slow; keep binder thread unblocked.
    enqueueEventStartListenerBroadcast(eventId, timestamp, numParams, intParams, extraStrings);

    return ::ndk::ScopedAStatus::ok();
}

/**
 * @brief 处理事件结束通知 - 资源释放与契约终止
 *
 * 结束流程遵循框架"契约衰减"原则：
 *
 * 1. **资源释放**
 *    - DSE 处理的事件：由 DSE 内部管理资源生命周期
 *    - Legacy 处理的事件：显式释放 PerfLock handle
 *
 * 2. **状态清理**
 *    - 从活跃事件表中移除
 *    - 计算并记录事件持续时间
 *
 * 3. **监听器通知**
 *    - 广播事件结束给所有注册的监听器
 *
 * @param eventId 事件标识符
 * @param timestamp 结束时间戳（纳秒）
 * @param extraStrings 额外字符串参数
 * @return AIDL 状态码
 *
 * @note 租约到期由 DSE 内部 TemporalContract 管理，不依赖显式结束调用
 */
::ndk::ScopedAStatus ServiceBridge::notifyEventEnd(int32_t eventId, int64_t timestamp,
                                                   const std::optional<std::string> &extraStrings) {
    TTRACE_SCOPED(TraceLevel::COARSE, LOG_TAG ":notifyEventEnd id=0x%x ts=%lld",
                  static_cast<unsigned>(eventId), static_cast<long long>(timestamp));
    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        return ::ndk::ScopedAStatus::ok();
    }

    const uid_t callerUid = AIBinder_getCallingUid();
#if !PERFENGINE_PRODUCTION
    const pid_t callerPid = AIBinder_getCallingPid();
#endif
    if (!CallerValidator::isEventAllowed(callerUid, eventId)) {
#if !PERFENGINE_PRODUCTION
        TLOGW("notifyEventEnd DENIED: uid=%u pid=%d eventId=0x%x", callerUid, callerPid, eventId);
#else
        TLOGW("notifyEventEnd DENIED: uid=%u eventId=0x%x", callerUid, eventId);

#endif
        return ::ndk::ScopedAStatus::ok();
    }

#if !PERFENGINE_PRODUCTION
    TLOGI("notifyEventEnd: uid=%u pid=%d eventId=0x%x", callerUid, callerPid, eventId);
#else
    TLOGI("notifyEventEnd: uid=%u eventId=0x%x", callerUid, eventId);
#endif
    EventContext ctx = EventContext::parse(eventId, {}, extraStrings.value_or(""));
    TLOGI("notifyEventEnd: eventId=0x%x pkg='%s'", static_cast<unsigned>(ctx.eventId),
          ctx.package.c_str());

    {
        std::lock_guard<Mutex> lock(mEventLock);
        auto it = mActiveEvents.find(eventId);
        if (it == mActiveEvents.end()) {
            TLOGW("Event 0x%x not found in active events", static_cast<unsigned>(eventId));
            return ::ndk::ScopedAStatus::ok();
        }

        if (it->second.failed) {
            TLOGW("Event 0x%x ended but was in failed state, skipping release",
                  static_cast<unsigned>(eventId));
        } else if (it->second.dseHandled && mDseService) {
            mDseService->OnEventEnd(eventId);
            TLOGD("DSE lease ended for eventId=0x%x", static_cast<unsigned>(eventId));
        } else if (!it->second.dseHandled && mPerfLockCaller && it->second.platformHandle > 0) {
            mPerfLockCaller->releasePerfLock(it->second.platformHandle);
        }

        int64_t elapsedMs = (timestamp - it->second.startTime) / 1000000LL;
        TLOGI("Event 0x%x ended: dseHandled=%d handle=%d elapsed=%lldms",
              static_cast<unsigned>(eventId), it->second.dseHandled, it->second.platformHandle,
              (long long)elapsedMs);
        mActiveEvents.erase(it);
    }

    // DSE OnEventEnd and Legacy release both exit the lock above; one end-broadcast path.
    enqueueEventEndListenerBroadcast(eventId, timestamp, extraStrings);

    return ::ndk::ScopedAStatus::ok();
}
// ==================== DSE Integration ====================

/**
 * @brief 将 AIDL 事件参数转换为 DSE 内部 SchedEvent 格式
 *
 * 转换逻辑遵循框架"场景映射"原则：
 *
 * - **eventId → action**：事件 ID 映射到场景动作类型
 * - **intParams[0] → rawHint**：第一个参数作为意图提示
 * - **timestamp**：保留原始时间戳，用于时间维度计算
 * - **uid/pid**：调用者身份，用于权限校验与意图继承
 *
 * @param eventId 事件标识符
 * @param timestamp 时间戳（纳秒）
 * @param numParams 参数数量
 * @param intParams 整型参数数组
 * @param packageName 包名
 * @param uid 调用者 UID
 * @param pid 调用者 PID
 * @return DSE 内部 SchedEvent 结构
 *
 * @note 场景语义由 SceneStage 进一步解析
 */
dse::SchedEvent ServiceBridge::ConvertToSchedEvent(const EventContext &ctx, int64_t timestamp,
                                                   int32_t numParams,
                                                   const std::vector<int32_t> &intParams) {
    const int32_t eventId = ctx.eventId;
    const int32_t uid = ctx.callerUid;
    const int32_t pid = ctx.callerPid;

    dse::SchedEvent event;
    event.eventId = static_cast<int64_t>(eventId);
    event.sessionId = static_cast<dse::SessionId>(eventId);
    event.pid = pid;
    event.tid = pid;
    event.uid = uid;
    event.timestamp = static_cast<uint64_t>(timestamp);
    if (uid >= 0 && uid < 10000) {
        event.source = 2;
    } else {
        event.source = 1;
    }

    uint32_t action = static_cast<uint32_t>(dse::EventSemanticAction::Unknown);
    uint32_t phaseFlags = 0;
    uint32_t visibilityFlags = 0;
    uint32_t audibilityFlags = 0;
    const uint32_t semanticHints = static_cast<uint32_t>(ctx.getInt(2)) & 0x1FFu;
    const uint32_t lifecycleHints = static_cast<uint32_t>(ctx.getInt(3)) & 0xFFFFu;
    const bool backgroundHint = (semanticHints & 0x100u) != 0;

    if (eventId >= 1 && eventId <= 10) {
        action = static_cast<uint32_t>(dse::EventSemanticAction::Launch);
        phaseFlags = 1;
    } else if (eventId >= 11 && eventId <= 20) {
        action = static_cast<uint32_t>(dse::EventSemanticAction::Transition);
        phaseFlags = 1;
    } else if (eventId >= 21 && eventId <= 30) {
        action = static_cast<uint32_t>(dse::EventSemanticAction::Scroll);
        phaseFlags = 2;
        visibilityFlags = 0x10;
    } else if (eventId >= 31 && eventId <= 40) {
        action = static_cast<uint32_t>(dse::EventSemanticAction::Animation);
        phaseFlags = 2;
        visibilityFlags = 0x10;
    } else if (eventId >= 41 && eventId <= 50) {
        action = static_cast<uint32_t>(dse::EventSemanticAction::Gaming);
        phaseFlags = 2;
        visibilityFlags = 0x10;
    } else if (eventId >= 51 && eventId <= 60) {
        action = static_cast<uint32_t>(dse::EventSemanticAction::Camera);
        phaseFlags = 2;
        visibilityFlags = 0x10;
    } else if (eventId >= 61 && eventId <= 70) {
        action = static_cast<uint32_t>(dse::EventSemanticAction::Playback);
        phaseFlags = 2;
        if (!backgroundHint) {
            visibilityFlags = 0x10;
        }
    } else if (eventId >= 71 && eventId <= 80) {
        action = static_cast<uint32_t>(dse::EventSemanticAction::Download);
        phaseFlags = 0;
    }

    if (backgroundHint) {
        if (action == static_cast<uint32_t>(dse::EventSemanticAction::Playback) ||
            action == static_cast<uint32_t>(dse::EventSemanticAction::Unknown)) {
            action = static_cast<uint32_t>(dse::EventSemanticAction::Background);
            phaseFlags = 2;
            audibilityFlags |= 0x20;
            visibilityFlags = 0;
        }
    }

    event.action = action;
    event.rawFlags = phaseFlags | visibilityFlags | audibilityFlags;
    {
        const uint32_t inputChannel = semanticHints & 0xFFu;
        const uint32_t backgroundBits = semanticHints & 0x100u;
        event.rawFlags |= backgroundBits | (inputChannel << 24) | (lifecycleHints << 8);
    }
    {
        const uint32_t a = static_cast<uint32_t>(ctx.intParam0);
        const uint32_t b = static_cast<uint32_t>(ctx.intParam1);
        event.rawHint = a | (b << 16);
    }
    event.hint = nullptr;
    event.hintSize = 0;

    strncpy(event.package, ctx.package.c_str(), sizeof(event.package) - 1);
    event.package[sizeof(event.package) - 1] = '\0';
    strncpy(event.activity, ctx.activity.c_str(), sizeof(event.activity) - 1);
    event.activity[sizeof(event.activity) - 1] = '\0';
    event.sceneId = dse::ResolveSchedSceneId(event);

    (void)numParams;
    (void)intParams;

    return event;
}

/**
 * @brief 尝试使用 DSE 处理事件 - 核心决策逻辑
 *
 * 本函数实现 DSE 的完整决策流程，遵循框架 §5 调度机制：
 *
 * ## 处理流程
 *
 * 1. **安全检查**
 *    - UID 访问控制：检查调用者是否有权限使用 DSE
 *    - 遵循框架"约束维度高于意图等级"原则
 *
 * 2. **灰度采样**
 *    - 根据灰度百分比决定是否启用 DSE
 *    - 使用 eventId 作为采样种子，确保同一事件的一致性
 *    - 遵循框架"灰度发布、渐进放量"原则
 *
 * 3. **事件注入**
 *    - 调用 ResourceScheduleService::OnEvent()
 *    - 内部会根据场景特征选择 FastPath 或 ConvergePath
 *
 * 4. **结果检查**
 *    - 检查执行路径是否为 NoopFallback
 *    - 记录 fallback 原因用于问题诊断
 *
 * ## 双路径决策模型（框架 §2.4）
 *
 * - **FastPath**：P1 首响应快速保障
 *   - 条件：action 匹配 fastActionMask + P1 意图预判
 *   - 特点：跳过 InheritanceStage/StabilityStage，亚毫秒级响应
 *   - 适用：冷启动、触控起手、滑动瞬间等 P1 场景
 *
 * - **ConvergePath**：完整决策链收敛
 *   - 流程：Scene → Intent → Stability → Resource → Inheritance → Envelope → Arbitration →
 * ConvergeFinalize
 *   - 特点：完整四维调度空间计算，毫秒级响应
 *   - 适用：P2 稳态场景、P3 弹性场景、P4 后台场景
 *
 * ## 意图等级映射（框架 §2）
 *
 * | 场景类型 | 意图等级 | 时间模式 | 典型场景 |
 * |---------|---------|---------|---------|
 * | 主交互型 | P1 | 突发型 | 冷启动、触控起手、首帧 |
 * | 连续感知型 | P2 | 稳态型 | 全屏视频、游戏运行期、后台音乐 |
 * | 弹性可见型 | P3 | 间歇型 | 前台静止、阅读停留、画中画 |
 * | 后台吞吐型 | P4 | 持续型 | 下载、同步、缓存整理 |
 *
 * @param event DSE 调度事件
 * @return true 表示 DSE 处理成功，false 表示需要降级到 Legacy
 *
 * @note 所有 fallback 路径都会记录到 TraceRecorder 用于问题诊断
 * @see ResourceScheduleService::OnEvent, SafetyController
 */
bool ServiceBridge::TryDseHandling(const dse::SchedEvent &event) {
    const auto sessionId = dse::ResolveSchedSessionId(event);
    if (!mDseService) {
        TLOGD("DSE service not available, skipping");
        RecordBridgeFallback(sessionId, dse::DecisionFallbackReason::ServiceUnavailable, 0x01);
        return false;
    }

    auto &safety = mDseService->GetSafetyController();
    const uint32_t grayscaleSampleKey = dse::GrayscaleSampleKey(event);

    if (!safety.IsUidAllowed(event.uid)) {
        TLOGW("UID %d blocked by access control for eventId=0x%llx", event.uid,
              (unsigned long long)event.eventId);
        RecordBridgeFallback(sessionId, dse::DecisionFallbackReason::SafetyBlocked, 0x02);
        return false;
    }

    if (!safety.IsUidAuthorizedForHighIntent(event.uid, event.action)) {
        TLOGW("UID %d not authorized for high-intent semantic action=%u eventId=0x%llx", event.uid,
              static_cast<unsigned>(event.action), (unsigned long long)event.eventId);
        RecordBridgeFallback(sessionId, dse::DecisionFallbackReason::SafetyUidBlocked,
                             static_cast<uint32_t>(event.action));
        return false;
    }

    if (!safety.IsEnabled()) {
        uint32_t grayscalePercent = safety.GetGrayscalePercent();
        if (grayscalePercent > 0 && safety.ShouldUseGrayscale(grayscaleSampleKey)) {
            TLOGD("DSE grayscale sample passed for eventId=0x%llx (%u%%)",
                  (unsigned long long)event.eventId, grayscalePercent);
        } else {
            TLOGD("DSE disabled for eventId=0x%llx (grayscale=%u%%, state=%s)",
                  (unsigned long long)event.eventId, grayscalePercent,
                  dse::SafetyController::StateToString(safety.GetState()));
            RecordBridgeFallback(
                sessionId, dse::DecisionFallbackReason::SafetyBlocked,
                (grayscalePercent << 8) | static_cast<uint32_t>(safety.GetState()));
            return false;
        }
    }

    if (mStateService) {
        if (!mStateService->IsReady()) {
            if (!mStateService->WaitUntilReady(dse::StateService::kDefaultWaitReadyTimeoutMs)) {
                TLOGW("StateService not ready after wait; refusing DSE for eventId=0x%llx",
                      (unsigned long long)event.eventId);
                RecordBridgeFallback(sessionId, dse::DecisionFallbackReason::StateBaselineSynthetic,
                                     0x10);
                return false;
            }
        }
    }

    mDseService->OnEvent(event, grayscaleSampleKey);

    const auto &result = mDseService->GetLastExecutionResult();
    if (result.actualPath == dse::PlatformActionPath::NoopFallback) {
        TLOGW("DSE returned fallback for eventId=0x%llx, reason=0x%x",
              (unsigned long long)event.eventId, result.fallbackFlags);
        RecordBridgeFallback(sessionId, dse::DecisionFallbackReason::NoopFallback,
                             result.fallbackFlags);
        return false;
    }

    TLOGI("DSE handled eventId=0x%llx successfully, path=%d", (unsigned long long)event.eventId,
          static_cast<int>(result.actualPath));
    return true;
}

/**
 * @brief 降级到 Legacy PerfLock 机制 - 兜底路径
 *
 * 当 DSE 不可用或处理失败时，使用传统 PerfLock 机制保障基本调度能力。
 *
 * ## 降级场景
 *
 * 1. DSE 服务未初始化
 * 2. 灰度采样未通过
 * 3. 安全检查被拦截
 * 4. DSE 执行异常
 * 5. 平台能力不支持
 *
 * ## 设计原则
 *
 * - **始终可用**：确保系统在任何情况下都有调度能力
 * - **平滑降级**：用户无感知地从 DSE 切换到 Legacy
 * - **问题追踪**：记录降级原因用于问题诊断
 *
 * @param event DSE 调度事件
 * @param packageName 包名
 * @return PerfLock handle，失败返回 -1
 *
 * @note Legacy 机制不区分 P1-P4，使用统一的 boost 策略
 */
int32_t ServiceBridge::FallbackToLegacy(const dse::SchedEvent &event,
                                        const std::string &packageName) {
    const auto sessionId = dse::ResolveSchedSessionId(event);
    if (!mPerfLockCaller) {
        TLOGE("PerfLockCaller not initialized");
        RecordBridgeFallback(sessionId, dse::DecisionFallbackReason::PlatformUnavailable, 0x01);
        return -1;
    }

    EventContext legacyCtx;
    legacyCtx.eventId = static_cast<int32_t>(event.eventId);
    legacyCtx.package = packageName;
    legacyCtx.intParam0 = static_cast<int32_t>(event.rawHint);

    int32_t handle = mPerfLockCaller->acquirePerfLock(legacyCtx);
    if (handle < 0) {
        TLOGE("Legacy PerfLockCaller failed for eventId=0x%llx", (unsigned long long)event.eventId);
        return -1;
    }

    RecordBridgeFallback(sessionId, dse::DecisionFallbackReason::LegacyFallback,
                         static_cast<uint32_t>(handle));

    TLOGI("Legacy fallback succeeded for eventId=0x%llx, handle=%d",
          (unsigned long long)event.eventId, handle);
    return handle;
}

// ==================== Listener Registration Implementation  ====================
/**
 * @brief 注册事件监听器 - 用于事件广播
 *
 * 监听器机制用于：
 * 1. 系统其他组件订阅性能事件
 * 2. 实现事件驱动的协同调度
 * 3. 支持调试与监控工具
 *
 * @param listener 监听器接口
 * @param eventFilter 事件过滤器（空表示订阅所有事件）
 * @return AIDL 状态码
 *
 * @note 使用 Binder 死亡通知自动清理失效监听器
 */
::ndk::ScopedAStatus ServiceBridge::registerEventListener(
    const std::shared_ptr<IEventListener> &listener, const std::vector<int32_t> &eventFilter) {
    TTRACE_SCOPED(TraceLevel::COARSE, LOG_TAG ":registerListener");

    if (!listener) {
        TLOGE("Null listener");
        return ::ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    std::lock_guard<Mutex> lock(mListenerLock);

    if (findListener(listener)) {
        TLOGW("Listener already registered");
        return ::ndk::ScopedAStatus::ok();
    }

    auto deathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(
        AIBinder_DeathRecipient_new(ServiceBridge::onListenerDied));

    auto *cookie = new DeathCookie{this, listener->asBinder().get()};
    binder_status_t status =
        AIBinder_linkToDeath(listener->asBinder().get(), deathRecipient.get(), cookie);

    if (status != STATUS_OK) {
        TLOGE("Failed to link to death: %d", status);
        delete cookie;
        return ::ndk::ScopedAStatus::fromStatus(status);
    }

    // Build filter set; empty vector means subscribe all
    std::unordered_set<int32_t> filterSet(eventFilter.begin(), eventFilter.end());

    if (filterSet.empty()) {
        TLOGI("Listener registered: subscribe ALL events, total=%zu", mListeners.size() + 1);
    } else {
        TLOGI("Listener registered: filter=%zu eventIds, total=%zu", filterSet.size(),
              mListeners.size() + 1);
    }

    mListeners.emplace_back(listener, std::move(deathRecipient), cookie, std::move(filterSet));

    return ::ndk::ScopedAStatus::ok();
}

/**
 * @brief 注销事件监听器
 *
 * @param listener 要注销的监听器
 * @return AIDL 状态码
 */
::ndk::ScopedAStatus ServiceBridge::unregisterEventListener(
    const std::shared_ptr<IEventListener> &listener) {
    TTRACE_SCOPED(TraceLevel::COARSE, LOG_TAG ":unregisterListener");

    if (!listener) {
        TLOGE("Null listener");
        return ::ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    std::lock_guard<Mutex> lock(mListenerLock);

    removeListener(listener);

    TLOGI("Listener unregistered, remaining listeners: %zu", mListeners.size());

    return ::ndk::ScopedAStatus::ok();
}

// ==================== Broadcasting Implementation  ====================

/**
 * @brief 广播事件开始通知给所有订阅的监听器
 *
 * 广播机制：
 * 1. 快照当前监听器列表（避免持锁调用）
 * 2. 根据事件过滤器筛选目标监听器
 * 3. 依次调用 onEventStart 回调
 *
 * @param eventId 事件标识符
 * @param timestamp 时间戳
 * @param numParams 参数数量
 * @param intParams 整型参数数组
 * @param extraStrings 额外字符串参数
 */
void ServiceBridge::broadcastEventStart(int32_t eventId, int64_t timestamp, int32_t numParams,
                                        const std::vector<int32_t> &intParams,
                                        const std::optional<std::string> &extraStrings) {
    std::vector<std::shared_ptr<IEventListener>> snapshot;
    {
        std::lock_guard<Mutex> lock(mListenerLock);
        if (mListeners.empty())
            return;
        snapshot.reserve(mListeners.size());
        for (auto &info : mListeners) {
            // Empty filter = subscribe all; otherwise check membership
            if (info.eventFilter.empty() || info.eventFilter.count(eventId) > 0) {
                snapshot.push_back(info.listener);
            }
        }
    }

    if (snapshot.empty()) {
        TLOGD("broadcastEventStart: no listeners for eventId=0x%x", static_cast<unsigned>(eventId));
        return;
    }

    TLOGI("Broadcasting eventStart eventId=0x%x to %zu/%zu listeners",
          static_cast<unsigned>(eventId), snapshot.size(), mListeners.size());

    for (auto &listener : snapshot) {
        auto status =
            listener->onEventStart(eventId, timestamp, numParams, intParams, extraStrings);
        TLOGI("Callback EVENT_START:eventId=0x%x, timestamp=%lld, numParams=%d",
              static_cast<unsigned>(eventId), (long long)timestamp, numParams);
        if (!status.isOk()) {
            TLOGW("Failed to notify listener onEventStart: %s", status.getMessage());
        }
    }
}

/**
 * @brief 广播事件结束通知给所有订阅的监听器
 *
 * @param eventId 事件标识符
 * @param timestamp 时间戳
 * @param extraStrings 额外字符串参数
 */
void ServiceBridge::broadcastEventEnd(int32_t eventId, int64_t timestamp,
                                      const std::optional<std::string> &extraStrings) {
    std::vector<std::shared_ptr<IEventListener>> snapshot;
    {
        std::lock_guard<Mutex> lock(mListenerLock);
        if (mListeners.empty())
            return;
        snapshot.reserve(mListeners.size());
        for (auto &info : mListeners) {
            if (info.eventFilter.empty() || info.eventFilter.count(eventId) > 0) {
                snapshot.push_back(info.listener);
            }
        }
    }

    if (snapshot.empty()) {
        TLOGD("broadcastEventEnd: no listeners for eventId=0x%x", static_cast<unsigned>(eventId));
        return;
    }

    TLOGI("Broadcasting eventEnd eventId=0x%x to %zu/%zu listeners",
          static_cast<unsigned>(eventId), snapshot.size(), mListeners.size());

    for (auto &listener : snapshot) {
        auto status = listener->onEventEnd(eventId, timestamp, extraStrings);
        TLOGI("Callback EVENT_END:eventId=0x%x, timestamp=%lld", static_cast<unsigned>(eventId),
              (long long)timestamp);
        if (!status.isOk()) {
            TLOGW("Failed to notify listener onEventEnd: %s", status.getMessage());
        }
    }
}

// ==================== Listener Management Helpers  ====================

/**
 * @brief 查找监听器是否已注册
 *
 * @param listener 要查找的监听器
 * @return true 表示已注册
 */
bool ServiceBridge::findListener(const std::shared_ptr<IEventListener> &listener) {
    // 必须在持有 mListenerLock 的情况下调用

    AIBinder *targetBinder = listener->asBinder().get();

    for (const auto &info : mListeners) {
        if (info.listener->asBinder().get() == targetBinder) {
            return true;
        }
    }

    return false;
}

/**
 * @brief 移除监听器并清理资源
 *
 * 清理内容：
 * 1. 解绑 Binder 死亡通知
 * 2. 释放 death cookie
 * 3. 从监听器列表中移除
 *
 * @param listener 要移除的监听器
 */
void ServiceBridge::removeListener(const std::shared_ptr<IEventListener> &listener) {
    AIBinder *targetBinder = listener->asBinder().get();
    for (auto it = mListeners.begin(); it != mListeners.end();) {
        if (it->listener->asBinder().get() == targetBinder) {
            AIBinder_unlinkToDeath(it->listener->asBinder().get(), it->deathRecipient.get(),
                                   it->cookie);
            delete it->cookie;
            it = mListeners.erase(it);
        } else {
            ++it;
        }
    }
}

// ==================== Death Notification Callback  ====================

/**
 * @brief Binder 死亡通知回调 - 自动清理失效监听器
 *
 * 当监听器进程死亡时，Binder 框架会调用此回调。
 * 自动从监听器列表中移除失效条目，避免后续调用失败。
 *
 * @param cookie 死亡通知 cookie，包含 ServiceBridge 和 Binder 指针
 */
void ServiceBridge::onListenerDied(void *cookie) {
    auto *dc = static_cast<DeathCookie *>(cookie);
    if (!dc || !dc->bridge) {
        delete dc;
        return;
    }

    TLOGW("Listener died, binder=%p, removing...", dc->binder);

    {
        std::lock_guard<Mutex> lock(dc->bridge->mListenerLock);
        auto &listeners = dc->bridge->mListeners;
        for (auto it = listeners.begin(); it != listeners.end();) {
            if (it->listener->asBinder().get() == dc->binder) {
                // 已死亡，无需 unlinkToDeath
                it = listeners.erase(it);
                TLOGI("Dead listener removed, remaining: %zu", listeners.size());
            } else {
                ++it;
            }
        }
    }

    delete dc;
}

}    // namespace perfengine
}    // namespace transsion
}    // namespace vendor
