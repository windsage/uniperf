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
void BoundaryTestResourceVector() {
    std::cout << "Boundary Testing ResourceVector..." << std::endl;

    ResourceVector maxVec;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        maxVec.v[i] = UINT32_MAX;
    }

    ResourceVector minVec;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        minVec.v[i] = 0;
    }

    auto result = ResourceVector::Min(maxVec, minVec);
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        assert(result.v[i] == 0);
    }

    result = ResourceVector::Max(maxVec, minVec);
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        assert(result.v[i] == UINT32_MAX);
    }

    std::cout << "  ResourceVector Boundary: PASS" << std::endl;
}

void BoundaryTestIntentLevel() {
    std::cout << "Boundary Testing IntentLevel..." << std::endl;

    IntentLevel levels[] = {IntentLevel::P1, IntentLevel::P2, IntentLevel::P3, IntentLevel::P4};

    for (auto level : levels) {
        const char *str = IntentLevelToString(level);
        assert(str != nullptr);
    }

    std::cout << "  IntentLevel Boundary: PASS" << std::endl;
}

void BoundaryTestGeneration() {
    std::cout << "Boundary Testing Generation..." << std::endl;

    StateVault vault;

    for (int i = 0; i < 100; ++i) {
        vault.BeginTransaction();
        SceneSemantic semantic;
        semantic.kind = (i % 2 == 0) ? SceneKind::Launch : SceneKind::Playback;
        vault.UpdateScene(semantic, i + 1);
        vault.CommitTransaction();
    }

    Generation gen = vault.GetGeneration();
    assert(gen >= 100);

    std::cout << "  Generation Boundary: PASS" << std::endl;
}

void BoundaryTestArbitrationZeroResources() {
    std::cout << "Boundary Testing Arbitration with Zero Resources..." << std::endl;

    ResourceRequest request;
    request.resourceMask = 0;

    ConstraintAllowed allowed;
    allowed.resourceMask = 0;

    CapabilityFeasible feasible;
    feasible.resourceMask = 0;

    ArbitrationLayers layers =
        ComputeArbitrationLayers(request, allowed, feasible, IntentLevel::P3);

    bool allZero = true;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        if (layers.delivered.v[i] != 0) {
            allZero = false;
            break;
        }
    }
    assert(allZero);

    std::cout << "  Arbitration Zero Resources: PASS" << std::endl;
}

void BoundaryTestArbitrationMaxResources() {
    std::cout << "Boundary Testing Arbitration with Max Resources..." << std::endl;

    ResourceRequest request;
    request.resourceMask = 0xFFFFFFFF;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        request.target.v[i] = UINT32_MAX;
    }

    ConstraintAllowed allowed;
    allowed.resourceMask = 0xFFFFFFFF;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        allowed.allowed.v[i] = UINT32_MAX;
    }

    CapabilityFeasible feasible;
    feasible.resourceMask = 0xFFFFFFFF;
    for (size_t i = 0; i < kResourceDimCount; ++i) {
        feasible.feasible.v[i] = UINT32_MAX;
    }

    ArbitrationLayers layers =
        ComputeArbitrationLayers(request, allowed, feasible, IntentLevel::P3);

    for (size_t i = 0; i < kResourceDimCount; ++i) {
        assert(layers.delivered.v[i] == UINT32_MAX);
    }

    std::cout << "  Arbitration Max Resources: PASS" << std::endl;
}

void RunBoundaryTests() {
    std::cout << "\n=== Boundary Tests ===\n" << std::endl;
    BoundaryTestResourceVector();
    BoundaryTestIntentLevel();
    BoundaryTestGeneration();
    BoundaryTestArbitrationZeroResources();
    BoundaryTestArbitrationMaxResources();
}
