# Performance Opcode Test Tool

基于 qccom perf-core 的性能测试工具，专注于高效的 opcode 测试功能。支持多组 opcode 原子性合并操作和灵活的时长控制。

## 核心特性

- **统一的acquire命令** - 支持持久化和定时锁定
- **原子性合并** - 多个opcode合并到单个perflock请求
- **智能参数解析** - 自动检测时长参数和opcode格式
- **空格容错** - 自动处理头尾空格，提升用户体验

## 编译安装

```bash
# 编译
cd vnd/aosp
mm -j8 perf_opcode_test

# 安装到设备
adb push out/target/product/xxx/vendor/bin/perf_opcode_test /vendor/bin/
adb shell chmod +x /vendor/bin/perf_opcode_test
```

## 基本用法

### 命令格式

```bash
# 获取perflock（支持可选时长）
./perf_opcode_test acquire [duration_ms] <opcode:value> [opcode:value...]

# 释放perflock
./perf_opcode_test release <handle> [handle...]
```

### Opcode 格式
```
opcode:value1,value2,value3
```

**支持格式**：
- **Opcode**: 16进制 (`0x44004000`) 或 10进制 (`1140883456`)
- **Value**: 16进制 (`0x190`) 或 10进制 (`400`)
- **多值**: 逗号分隔 (`0x42800000:1,0,6`)
- **空格容错**: 自动处理头尾空格 (`" 0x44004000 : 400 "`)

## 使用示例

### 1. 持久化锁定
```bash
# 单个opcode持久锁定（需手动释放）
./perf_opcode_test acquire 0x44004000:400

# 多个opcode合并锁定（原子性操作）
./perf_opcode_test acquire 0x44004000:400 0x44008000:1

# 支持空格的格式
./perf_opcode_test acquire " 0x44004000 : 400 " " 0x44008000 : 1 "
```

### 2. 定时锁定（自动释放）
```bash
# 3秒后自动释放
./perf_opcode_test acquire 3000 0x44004000:400

# 多opcode定时锁定（5秒后自动释放）
./perf_opcode_test acquire 5000 0x44004000:400 0x44008000:1 0x40800000:1900000

# CPU + GPU + 调度器综合提升（10秒）
./perf_opcode_test acquire 10000 0x40800000:2400000 0x42800000:1 0x40C00000:3
```

### 3. 手动释放
```bash
# 释放单个handle
./perf_opcode_test release 634

# 释放多个handle
./perf_opcode_test release 634 635 636
```

## 实际应用场景

### 调试性能问题
```bash
# 测试您的自定义opcode
./perf_opcode_test acquire 3000 0x44004000:400

# 验证opcode是否生效（检查对应节点值变化）
cat /proc/sys/walt/input_boost/input_boost_ms
```

### 性能基准测试
```bash
# 设置基准性能状态
./perf_opcode_test acquire 0x40800000:1800000 0x42800000:2

# 执行性能测试...
# （运行您的benchmark）

# 测试完成后释放
./perf_opcode_test release 634
```

### 游戏性能调优
```bash
# 游戏启动时的性能提升（15秒后自动释放）
./perf_opcode_test acquire 15000 0x40800000:2400000 0x42800000:0 0x41000000:8 0x40C00000:3

# 或设置持久化游戏模式
./perf_opcode_test acquire 0x40800000:2200000 0x42800000:1 0x41000000:6
```

### 使用建议
1. **调试阶段** - 使用短时间的定时锁定验证功能
2. **性能测试** - 使用持久化锁定确保稳定的测试环境
3. **生产环境** - 根据实际需求选择合适的时长
4. **多opcode测试** - 利用合并特性测试opcode组合效果

## 注意事项

1. **权限要求** - 需要 system 或 root 权限
2. **库依赖** - 依赖 `libqti-perfd-client.so`
3. **架构支持** - 支持 ARM 和 ARM64
4. **持久化锁** - 不指定时长的锁需要手动释放
5. **系统影响** - 性能锁会影响功耗和发热，及时释放
6. **原子性** - 多个opcode要么全部成功，要么全部失败

## 故障排除

### 常见问题

**库加载失败**
```
ERROR: Failed to load libqti-perfd-client.so
```
**解决**: 确保 perf 服务运行，检查库文件路径

**权限不足**
```
ERROR: perflock failed, handle: -1
```
**解决**: 使用 root 权限或检查 SELinux 策略

**参数格式错误**
```
ERROR: Invalid format 'xxx'. Expected 'opcode:value'
```
**解决**: 检查opcode:value格式，确保使用冒号分隔

**opcode不生效**
```
SUCCESS: Handle = 634 (但节点值未改变)
```
**解决**: 检查opcode配置文件和处理函数实现

### 调试技巧

1. **验证opcode效果**：
```bash
# 获取perflock前查看节点值
cat /proc/sys/walt/input_boost/input_boost_ms

# 执行perflock
./perf_opcode_test acquire 0x44004000:500

# 再次查看节点值确认变化
cat /proc/sys/walt/input_boost/input_boost_ms
```

2. **使用短时间测试**：
```bash
# 使用1秒测试避免影响系统
./perf_opcode_test acquire 1000 0x44004000:400
```
