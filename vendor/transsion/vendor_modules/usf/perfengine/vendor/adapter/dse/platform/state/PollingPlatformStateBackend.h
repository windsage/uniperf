#pragma once

#include <atomic>
#include <chrono>
#include <thread>

#include "IPlatformStateBackend.h"
#include "platform/PlatformRegistry.h"
#include "platform/state/PlatformStateCommon.h"

namespace vendor::transsion::perfengine::dse {

/**
 * @brief 统一封装轮询型平台状态 backend 的公共生命周期与提交主循环。
 *
 * 根据状态监听抽象层设计稿，platform/state 可以承载通用 backend 抽象；
 * 平台目录只保留节点路径、vendor traits 和实际读取逻辑。
 */
class PollingPlatformStateBackend : public IPlatformStateBackend {
public:
    bool Init() noexcept override {
        traits_ = BuildTraits();
        return true;
    }

    bool ReadInitial(ConstraintSnapshot *constraint,
                     CapabilitySnapshot *capability) noexcept override {
        if (!constraint) {
            return false;
        }

        InitializeConstraintSnapshotDefaults(*constraint);
        const bool anyRead = ReadConstraintSnapshot(*constraint);
        if (capability) {
            PlatformCapability platformCapability;
            PlatformRegistry::FillPlatformCapability(GetTraits().vendor, platformCapability);
            FillCapabilitySnapshotFromPlatformCapability(platformCapability, *capability);
        }
        return anyRead;
    }

    bool Start(IStateSink *sink) noexcept override {
        if (!sink) {
            return false;
        }

        sink_ = sink;
        hasLastPollConstraint_ = false;
        running_.store(true, std::memory_order_release);
        monitorThread_ = std::thread(&PollingPlatformStateBackend::MonitorThreadMain, this);
        return true;
    }

    void Stop() noexcept override {
        running_.store(false, std::memory_order_release);
        if (monitorThread_.joinable()) {
            monitorThread_.join();
        }
        sink_ = nullptr;
    }

    void UpdateMonitoringContext(const MonitoringContext &ctx) noexcept override {
        monitoringCtx_ = ctx;
    }

    PlatformStateTraits GetTraits() const noexcept override {
        if (traits_.vendor != PlatformVendor::Unknown) {
            return traits_;
        }
        return BuildTraits();
    }

protected:
    virtual PlatformStateTraits BuildTraits() const noexcept = 0;
    virtual bool ReadThermalState(ConstraintSnapshot &) = 0;
    virtual bool ReadBatteryState(ConstraintSnapshot &) = 0;
    virtual bool ReadMemoryState(ConstraintSnapshot &) = 0;
    virtual bool ReadScreenState(ConstraintSnapshot &) = 0;
    virtual bool ReadPowerState(ConstraintSnapshot &) = 0;

    void EmitDelta(const StateDelta &delta) {
        if (sink_) {
            sink_->OnDelta(delta);
        }
    }

private:
    bool ReadConstraintSnapshot(ConstraintSnapshot &constraint) {
        const bool thermalRead = ReadThermalState(constraint);
        const bool batteryRead = ReadBatteryState(constraint);
        const bool memoryRead = ReadMemoryState(constraint);
        const bool screenRead = ReadScreenState(constraint);
        const bool powerRead = ReadPowerState(constraint);
        return thermalRead || batteryRead || memoryRead || screenRead || powerRead;
    }

    void MonitorThreadMain() {
        while (running_.load(std::memory_order_acquire)) {
            const uint64_t captureNs = SystemTime::GetDeterministicNs();
            const uint32_t pollMs = GetStandardPollMs(monitoringCtx_);

            ConstraintSnapshot constraint;
            InitializeConstraintSnapshotDefaults(constraint);
            const bool anyRead = ReadConstraintSnapshot(constraint);
            if (!anyRead) {
                std::this_thread::sleep_for(std::chrono::milliseconds(pollMs));
                continue;
            }

            if (hasLastPollConstraint_ &&
                CanonicalConstraintSnapshotEquals(constraint, lastPollConstraint_)) {
                std::this_thread::sleep_for(std::chrono::milliseconds(pollMs));
                continue;
            }

            hasLastPollConstraint_ = true;
            lastPollConstraint_ = constraint;
            EmitDelta(BuildStandardConstraintDelta(constraint, captureNs));
            std::this_thread::sleep_for(std::chrono::milliseconds(pollMs));
        }
    }

    PlatformStateTraits traits_{};
    IStateSink *sink_ = nullptr;
    std::thread monitorThread_;
    std::atomic<bool> running_{false};
    MonitoringContext monitoringCtx_{};
    bool hasLastPollConstraint_ = false;
    ConstraintSnapshot lastPollConstraint_{};
};

}    // namespace vendor::transsion::perfengine::dse
