#pragma once

/**
 * @file TraceMacros.h
 * @brief DSE Trace 编译期总开关（商用构建应关闭）
 *
 * - PERFENGINE_ENABLE_DSE_TRACE：是否编译环形 Trace 缓冲、执行/回放入口写入及热路径上的
 *   ReplayHash 辅助计算触发点（与 TraceRecorder::IsReplayHashTraceEnabled() 等配合）。
 * - 默认：非商用打开；当 PERFENGINE_PRODUCTION 非 0 时默认关闭（与 Android.bp / 商用编译一致）。
 * - CMake 目标 `perfengine_dse_core` 会 **PUBLIC** 注入 `PERFENGINE_ENABLE_DSE_TRACE=0|1`，与所选
 *   Trace 源文件（full+buffer vs stub）一致；若翻译单元未通过该目标编译，仍可用本头文件的默认逻辑。
 * - 可在任意翻译单元显式 `#define PERFENGINE_ENABLE_DSE_TRACE 0|1` 覆盖默认行为（须与链接的 Trace
 * 实现一致）。
 *
 * @note 单元测试目标应显式保持 Trace 开启（CMake 默认非 PRODUCTION；或强制定义 ENABLE=1）。
 */

#ifndef PERFENGINE_PRODUCTION
#define PERFENGINE_PRODUCTION 0
#endif

#if !defined(PERFENGINE_ENABLE_DSE_TRACE)
#if PERFENGINE_PRODUCTION
#define PERFENGINE_ENABLE_DSE_TRACE 0
#else
#define PERFENGINE_ENABLE_DSE_TRACE 1
#endif
#endif
