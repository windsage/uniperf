#include <atomic>
#include <cassert>
#include <chrono>
#include <iostream>
#include <random>
#include <thread>
#include <vector>

#include "dse/Dse.h"
#include "tests/DseTests_TestSupport.h"

using namespace vendor::transsion::perfengine::dse;
void ConcurrencyTestStateVault() {
    std::cout << "Concurrency Testing StateVault (10 threads)..." << std::endl;

    StateVault vault;
    std::atomic<int> successCount{0};
    std::atomic<int> failCount{0};

    auto worker = [&vault, &successCount, &failCount](int threadId) {
        for (int i = 0; i < 1000; ++i) {
            ConstraintSnapshot snapshot;
            snapshot.thermalLevel = i % 10;
            (void)threadId;
            vault.UpdateConstraint(snapshot);

            auto view = vault.Snapshot();
            if (view.GetGeneration() > 0) {
                successCount++;
            } else {
                failCount++;
            }
        }
    };

    std::vector<std::thread> threads;
    for (int i = 0; i < 10; ++i) {
        threads.emplace_back(worker, i);
    }

    for (auto &t : threads) {
        t.join();
    }

    assert(successCount > 0);

    std::cout << "  StateVault Concurrency: PASS (success=" << successCount.load()
              << ", fail=" << failCount.load() << ")" << std::endl;
}

void ConcurrencyStressTestPinAndSnapshotOptimistic() {
    std::cout << "Concurrency Stress Testing PinAndSnapshot Optimistic Path (4W/8R, 3s)..."
              << std::endl;

    StateVault vault;
    std::atomic<bool> stop{false};
    std::atomic<uint64_t> readOps{0};
    std::atomic<uint64_t> writeOps{0};
    std::atomic<uint64_t> mismatches{0};
    std::atomic<uint64_t> regressions{0};

    auto writer = [&vault, &stop, &writeOps](uint32_t seed) {
        uint32_t v = seed;
        while (!stop.load(std::memory_order_relaxed)) {
            ConstraintSnapshot snapshot;
            snapshot.thermalLevel = v % 6;
            snapshot.batteryLevel = 20 + (v % 80);
            snapshot.memoryPressure = v % 4;
            snapshot.psiLevel = v % 5;
            snapshot.screenOn = ((v & 1u) == 0u);
            vault.UpdateConstraint(snapshot);
            ++v;
            writeOps.fetch_add(1, std::memory_order_relaxed);
        }
    };

    auto reader = [&vault, &stop, &readOps, &mismatches, &regressions]() {
        StateView view;
        Generation lastGen = 0;
        while (!stop.load(std::memory_order_relaxed)) {
            const auto token = vault.PinAndSnapshot(0, view);
            const Generation observed = view.GetGeneration();
            if (token.generation != observed) {
                mismatches.fetch_add(1, std::memory_order_relaxed);
            }
            if (observed < lastGen) {
                regressions.fetch_add(1, std::memory_order_relaxed);
            }
            lastGen = observed;
            readOps.fetch_add(1, std::memory_order_relaxed);
        }
    };

    std::vector<std::thread> writers;
    std::vector<std::thread> readers;
    for (uint32_t i = 0; i < 4; ++i) {
        writers.emplace_back(writer, 100u + i);
    }
    for (int i = 0; i < 8; ++i) {
        readers.emplace_back(reader);
    }

    std::this_thread::sleep_for(std::chrono::seconds(3));
    stop.store(true, std::memory_order_relaxed);

    for (auto &t : writers)
        t.join();
    for (auto &t : readers)
        t.join();

    assert(readOps.load(std::memory_order_relaxed) > 0);
    assert(writeOps.load(std::memory_order_relaxed) > 0);
    assert(mismatches.load(std::memory_order_relaxed) == 0);
    assert(regressions.load(std::memory_order_relaxed) == 0);

    std::cout << "  PinAndSnapshot Optimistic Stress: PASS (reads=" << readOps.load()
              << ", writes=" << writeOps.load() << ")" << std::endl;
}

void ConcurrencyBenchmarkStateVaultSnapshotVsWriter() {
    std::cout << "Concurrency Benchmark StateVault Snapshot vs Writers..." << std::endl;

    StateVault vault;
    std::atomic<bool> stop{false};
    std::atomic<uint64_t> snapOps{0};
    std::atomic<uint64_t> writeOps{0};

    constexpr int kReaders = 8;
    constexpr int kWriters = 2;
    constexpr auto kDuration = std::chrono::seconds(2);

    auto writer = [&vault, &stop, &writeOps]() {
        uint32_t v = 0;
        while (!stop.load(std::memory_order_relaxed)) {
            ConstraintSnapshot snapshot;
            snapshot.thermalLevel = v % 6;
            snapshot.batteryLevel = 20 + (v % 80);
            vault.UpdateConstraint(snapshot);
            ++v;
            writeOps.fetch_add(1, std::memory_order_relaxed);
        }
    };

    auto reader = [&vault, &stop, &snapOps]() {
        while (!stop.load(std::memory_order_relaxed)) {
            const auto view = vault.Snapshot();
            (void)view.GetGeneration();
            (void)view.Constraint().snapshot.thermalLevel;
            snapOps.fetch_add(1, std::memory_order_relaxed);
        }
    };

    std::vector<std::thread> threads;
    for (int i = 0; i < kWriters; ++i) {
        threads.emplace_back(writer);
    }
    for (int i = 0; i < kReaders; ++i) {
        threads.emplace_back(reader);
    }

    std::this_thread::sleep_for(kDuration);
    stop.store(true, std::memory_order_relaxed);
    for (auto &t : threads) {
        t.join();
    }

    const double sec = std::chrono::duration<double>(kDuration).count();
    const uint64_t sOps = snapOps.load(std::memory_order_relaxed);
    const uint64_t wOps = writeOps.load(std::memory_order_relaxed);
    assert(sOps > 0u);
    assert(wOps > 0u);

    std::cout << "  Snapshot+writer benchmark: snapshot_ops=" << sOps << " ("
              << static_cast<uint64_t>(static_cast<double>(sOps) / sec) << "/s), writes=" << wOps
              << " (" << static_cast<uint64_t>(static_cast<double>(wOps) / sec) << "/s)"
              << std::endl;
}

void ConcurrencyTestTraceBuffer() {
    std::cout << "Concurrency Testing TraceBuffer (10 threads)..." << std::endl;

    TraceBuffer buffer;
    std::atomic<int> writeCount{0};
    std::atomic<int> overwriteCount{0};

    auto writer = [&buffer, &writeCount, &overwriteCount](int threadId) {
        std::random_device rd;
        std::mt19937 gen(rd());
        std::uniform_int_distribution<> dis(1, 1000);

        for (int i = 0; i < 100; ++i) {
            TraceRecord record;
            record.timestamp = std::chrono::steady_clock::now().time_since_epoch().count();
            record.eventId = threadId * 100 + i;
            record.tracePoint = dis(gen);

            auto result = buffer.Write(record);
            writeCount++;
            if (result.overwritten) {
                overwriteCount++;
            }
        }
    };

    std::vector<std::thread> threads;
    for (int i = 0; i < 10; ++i) {
        threads.emplace_back(writer, i);
    }

    for (auto &t : threads) {
        t.join();
    }

    assert(writeCount == 1000);

    std::cout << "  TraceBuffer Concurrency: PASS (writes=" << writeCount.load()
              << ", overwrites=" << overwriteCount.load() << ")" << std::endl;
}

void ConcurrencyTestSafetyController() {
    std::cout << "Concurrency Testing SafetyController (10 threads)..." << std::endl;

    SafetyConfig config;
    config.defaultDisabled = false;
    SafetyController controller(config);

    std::atomic<int> enableCount{0};
    std::atomic<int> disableCount{0};
    std::atomic<int> fallbackCount{0};

    auto worker = [&controller, &enableCount, &disableCount, &fallbackCount](int threadId) {
        (void)threadId;
        for (int i = 0; i < 100; ++i) {
            switch (i % 3) {
                case 0:
                    if (controller.Enable())
                        enableCount++;
                    break;
                case 1:
                    if (controller.Disable())
                        disableCount++;
                    break;
                case 2:
                    if (controller.RequestFallback(FallbackReason::RuntimeError))
                        fallbackCount++;
                    break;
            }
        }
    };

    std::vector<std::thread> threads;
    for (int i = 0; i < 10; ++i) {
        threads.emplace_back(worker, i);
    }

    for (auto &t : threads) {
        t.join();
    }

    std::cout << "  SafetyController Concurrency: PASS (enable=" << enableCount.load()
              << ", disable=" << disableCount.load() << ", fallback=" << fallbackCount.load() << ")"
              << std::endl;
}

void ConcurrencyTestOrchestrator() {
    std::cout << "Concurrency Testing Orchestrator (10 threads)..." << std::endl;

    StateVault vault;
    Orchestrator<MainProfileSpec> orchestrator(vault);

    std::atomic<int> fastPathCount{0};
    std::atomic<int> convergePathCount{0};

    auto worker = [&orchestrator, &fastPathCount, &convergePathCount](int threadId) {
        for (int i = 0; i < 100; ++i) {
            SchedEvent event;
            event.action = threadId * 100 + i;
            event.sessionId = threadId * 1000 + i;

            auto fastGrant = orchestrator.RunFast(event);
            if (fastGrant.grantedMask != 0) {
                fastPathCount++;
            }

            auto decision = orchestrator.RunConverge(event);
            if (decision.grant.resourceMask != 0) {
                convergePathCount++;
            }
        }
    };

    std::vector<std::thread> threads;
    for (int i = 0; i < 10; ++i) {
        threads.emplace_back(worker, i);
    }

    for (auto &t : threads) {
        t.join();
    }

    std::cout << "  Orchestrator Concurrency: PASS (fast=" << fastPathCount.load()
              << ", converge=" << convergePathCount.load() << ")" << std::endl;
}

void RunConcurrencyTests() {
    std::cout << "\n=== Concurrency Tests ===\n" << std::endl;
    ConcurrencyTestStateVault();
    ConcurrencyStressTestPinAndSnapshotOptimistic();
    ConcurrencyBenchmarkStateVaultSnapshotVsWriter();
    ConcurrencyTestTraceBuffer();
    ConcurrencyTestSafetyController();
    ConcurrencyTestOrchestrator();
}
