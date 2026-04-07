#pragma once

// DSE 聚合头：模块边界与双路径/执行链见
// docs/重构任务清单.md（v3.1）、docs/整机资源确定性调度抽象框架.md。

#include "config/ConfigLoader.h"
#include "config/RuleTable.h"
#include "core/exec/ActionCompiler.h"
#include "core/exec/ExecutorSet.h"
#include "core/exec/PlatformActionMapper.h"
#include "core/inheritance/IntentInheritance.h"
#include "core/orchestration/Orchestrator.h"
#include "core/profile/ProfileSpec.h"
#include "core/safety/SafetyController.h"
#include "core/service/ResourceScheduleService.h"
#include "core/stability/StabilityMechanism.h"
#include "core/stage/ArbitrationStage.h"
#include "core/stage/ConvergeFinalizeStage.h"
#include "core/stage/EnvelopeStage.h"
#include "core/stage/FastFinalizeStage.h"
#include "core/stage/IntentStage.h"
#include "core/stage/ResourceStage.h"
#include "core/stage/SceneStage.h"
#include "core/stage/StageRunner.h"
#include "core/state/StateVault.h"
#include "core/types/ConstraintTypes.h"
#include "core/types/CoreTypes.h"
#include "dse/Types.h"
#include "platform/PlatformBase.h"
#include "platform/PlatformRegistry.h"
#include "trace/TraceMacros.h"
#if PERFENGINE_ENABLE_DSE_TRACE
#include "trace/TraceBuffer.h"
#else
#include "trace/TraceBufferStub.h"
#endif
#include "trace/TraceRecorder.h"
