#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
增强版 TRAN_PERF 频率 Opcode 反解析器
====================================
支持2/3/4集群动态适配的反解析：
1. 0x7格式 - 动态多集群编码 (支持2/3/4集群)
2. 0x6格式 - 兼容3集群编码 (仅支持3集群)
3. 统一频率 - 所有CPU相同频率

Author: chao.xu5
"""

def parse_input_value(input_str: str) -> int:
    """解析输入值，支持16进制和10进制"""
    input_str = input_str.strip()

    try:
        # 尝试解析16进制 (0x前缀或纯16进制)
        if input_str.lower().startswith('0x'):
            return int(input_str, 16)
        elif all(c in '0123456789abcdefABCDEF' for c in input_str) and len(input_str) > 6:
            return int(input_str, 16)
        else:
            # 解析10进制
            return int(input_str, 10)
    except ValueError:
        raise ValueError(f"无法解析输入值: {input_str}")

def decode_7_format(value: int, cluster_count: int) -> dict:
    """解析0x7格式编码 (动态适配2/3/4集群)"""
    clusters = {}

    if cluster_count == 2:
        # 2集群: 0x7LLLLBBBB (16+12位)
        freq0_code = (value >> 12) & 0xFFFF  # 16位
        freq1_code = value & 0xFFF           # 12位

        clusters = {
            '集群0': {'code': freq0_code, 'freq_mhz': freq0_code * 100},
            '集群1': {'code': freq1_code, 'freq_mhz': freq1_code * 100}
        }
        format_desc = '0x7格式 (2集群: 16+12位)'

    elif cluster_count == 3:
        # 3集群: 0x7LLLBBPP (12+8+8位)
        freq0_code = (value >> 16) & 0xFFF   # 12位
        freq1_code = (value >> 8) & 0xFF     # 8位
        freq2_code = value & 0xFF            # 8位

        clusters = {
            '集群0': {'code': freq0_code, 'freq_mhz': freq0_code * 100},
            '集群1': {'code': freq1_code, 'freq_mhz': freq1_code * 100},
            '集群2': {'code': freq2_code, 'freq_mhz': freq2_code * 100}
        }
        format_desc = '0x7格式 (3集群: 12+8+8位)'

    elif cluster_count == 4:
        # 4集群: 0x7LLBBTTPP (8+6+6+8位)
        freq0_code = (value >> 20) & 0xFF    # 8位
        freq1_code = (value >> 14) & 0x3F    # 6位
        freq2_code = (value >> 8) & 0x3F     # 6位
        freq3_code = value & 0xFF            # 8位

        clusters = {
            '集群0': {'code': freq0_code, 'freq_mhz': freq0_code * 100},
            '集群1': {'code': freq1_code, 'freq_mhz': freq1_code * 100},
            '集群2': {'code': freq2_code, 'freq_mhz': freq2_code * 100},
            '集群3': {'code': freq3_code, 'freq_mhz': freq3_code * 100}
        }
        format_desc = '0x7格式 (4集群: 8+6+6+8位)'

    else:
        raise ValueError(f"0x7格式不支持{cluster_count}集群")

    return {
        'format': format_desc,
        'cluster_count': cluster_count,
        'clusters': clusters
    }

def decode_6_format(value: int) -> dict:
    """解析0x6格式编码 (固定3集群)"""
    little_code = (value >> 16) & 0xFF   # 8位
    big_code = (value >> 8) & 0xFF       # 8位
    prime_code = value & 0xFF            # 8位

    little_freq = little_code * 100
    big_freq = big_code * 100
    prime_freq = prime_code * 100

    return {
        'format': '0x6格式 (固定3集群: 8+8+8位)',
        'cluster_count': 3,
        'clusters': {
            'Little集群': {'code': little_code, 'freq_mhz': little_freq},
            'Big集群': {'code': big_code, 'freq_mhz': big_freq},
            'Prime集群': {'code': prime_code, 'freq_mhz': prime_freq}
        }
    }

def decode_unified_format(value: int) -> dict:
    """解析统一频率格式"""
    return {
        'format': '统一频率模式',
        'unified_freq_mhz': value
    }

def get_cluster_count_input() -> int:
    """获取集群数量输入"""
    while True:
        try:
            cluster_count = int(input("请输入平台集群数量 (2-4): "))
            if 2 <= cluster_count <= 4:
                return cluster_count
            else:
                print("集群数量必须在2-4之间!")
        except ValueError:
            print("请输入有效的数字!")

def get_cpu_topology_input(cluster_count: int) -> dict:
    """获取CPU拓扑信息"""
    print(f"\nCPU拓扑配置 ({cluster_count}集群):")

    while True:
        try:
            total_cores = int(input("总CPU核心数 (1-16): "))
            if 1 <= total_cores <= 16:
                break
            else:
                print("核心数必须在1-16之间!")
        except ValueError:
            print("请输入有效的数字!")

    print(f"\n请输入{cluster_count}个集群的核心分配 (总和必须等于{total_cores}):")

    clusters = {}
    total_assigned = 0

    cluster_names = [f"集群{i}" for i in range(cluster_count)]

    for i, cluster_name in enumerate(cluster_names):
        while True:
            try:
                remaining = total_cores - total_assigned
                cores = int(input(f"{cluster_name}核心数 (剩余{remaining}): "))
                if 0 <= cores <= remaining:
                    if cores > 0:
                        clusters[cluster_name] = cores
                        total_assigned += cores
                    break
                else:
                    print(f"核心数不能超过剩余数量{remaining}!")
            except ValueError:
                print("请输入有效的数字!")

    if total_assigned != total_cores:
        print(f"警告：分配的核心数({total_assigned})与总数({total_cores})不匹配!")

    return clusters

def print_cpu_allocation(decoded: dict, topology: dict):
    """打印CPU频率分配"""
    print(f"\nCPU频率分配结果:")
    print("=" * 60)

    if 'clusters' in decoded:
        # 集群模式
        cpu_index = 0
        cluster_count = decoded.get('cluster_count', len(decoded['clusters']))

        for i in range(cluster_count):
            cluster_name = f"集群{i}" if f"集群{i}" in topology else list(decoded['clusters'].keys())[i]

            if cluster_name in topology and cluster_name in decoded['clusters']:
                cluster_info = decoded['clusters'][cluster_name]
                core_count = topology[cluster_name]
                freq = cluster_info['freq_mhz']

                print(f"\n{cluster_name} ({core_count}核心):")
                print(f"   编码值: {cluster_info['code']} (0x{cluster_info['code']:02X})")
                print(f"   频率: {freq}MHz")
                print(f"   CPU分配: ", end="")

                cpu_range = []
                for j in range(core_count):
                    cpu_range.append(f"CPU{cpu_index}")
                    cpu_index += 1
                print(" ".join(cpu_range))
            elif list(decoded['clusters'].keys())[i] in decoded['clusters']:
                # 0x6格式的特殊处理
                original_cluster_name = list(decoded['clusters'].keys())[i]
                cluster_info = decoded['clusters'][original_cluster_name]

                # 尝试从topology中找到对应的集群
                topo_cluster_name = f"集群{i}" if f"集群{i}" in topology else None
                core_count = topology.get(topo_cluster_name, 1) if topo_cluster_name else 1

                freq = cluster_info['freq_mhz']

                print(f"\n{original_cluster_name} ({core_count}核心):")
                print(f"   编码值: {cluster_info['code']} (0x{cluster_info['code']:02X})")
                print(f"   频率: {freq}MHz")
                print(f"   CPU分配: ", end="")

                cpu_range = []
                for j in range(core_count):
                    cpu_range.append(f"CPU{cpu_index}")
                    cpu_index += 1
                print(" ".join(cpu_range))
    else:
        # 统一频率模式
        total_cores = sum(topology.values()) if topology else 8
        freq = decoded['unified_freq_mhz']
        print(f"\n统一频率: {freq}MHz")
        print(f"   所有CPU: ", end="")
        cpu_range = [f"CPU{i}" for i in range(total_cores)]
        print(" ".join(cpu_range))

def print_expected_output(decoded: dict, topology: dict):
    """打印input_boost_freq节点的预期输出"""
    print(f"\n/proc/sys/walt/input_boost/input_boost_freq 预期内容:")
    print("-" * 60)

    output_values = []

    if 'clusters' in decoded:
        # 集群模式 - 按CPU索引顺序输出
        cluster_count = decoded.get('cluster_count', len(decoded['clusters']))

        for i in range(cluster_count):
            cluster_name = f"集群{i}" if f"集群{i}" in topology else list(decoded['clusters'].keys())[i]

            if cluster_name in topology and cluster_name in decoded['clusters']:
                core_count = topology[cluster_name]
                freq_hz = decoded['clusters'][cluster_name]['freq_mhz'] * 1000
            elif list(decoded['clusters'].keys())[i] in decoded['clusters']:
                # 0x6格式的特殊处理
                original_cluster_name = list(decoded['clusters'].keys())[i]
                topo_cluster_name = f"集群{i}" if f"集群{i}" in topology else None
                core_count = topology.get(topo_cluster_name, 1) if topo_cluster_name else 1
                freq_hz = decoded['clusters'][original_cluster_name]['freq_mhz'] * 1000
            else:
                continue

            # 为该集群的每个核心添加频率值
            for _ in range(core_count):
                output_values.append(str(freq_hz))
    else:
        # 统一频率模式
        total_cores = sum(topology.values()) if topology else 8
        freq_hz = decoded['unified_freq_mhz'] * 1000

        for _ in range(total_cores):
            output_values.append(str(freq_hz))

    print(" ".join(output_values))

def decode_opcode_value(value: int, cluster_count: int = None) -> dict:
    """根据值的格式进行解码"""
    # 检查格式标识
    format_bits = value & 0xF0000000

    if format_bits == 0x70000000:
        if cluster_count is None:
            # 尝试自动检测集群数
            cluster_count = get_cluster_count_input()
        return decode_7_format(value, cluster_count)
    elif format_bits == 0x60000000:
        return decode_6_format(value)
    else:
        # 统一频率模式 (没有特殊前缀)
        return decode_unified_format(value)

def get_default_topology(cluster_count: int) -> dict:
    """获取默认拓扑配置"""
    if cluster_count == 2:
        return {"集群0": 4, "集群1": 4}  # 4+4
    elif cluster_count == 3:
        return {"集群0": 4, "集群1": 3, "集群2": 1}  # 4+3+1
    elif cluster_count == 4:
        return {"集群0": 2, "集群1": 2, "集群2": 2, "集群3": 2}  # 2+2+2+2
    else:
        return {}

def main():
    """主函数"""
    print("TRAN_PERF 频率 Opcode 反解析器")
    print("=" * 50)

    while True:
        print("\n请输入要解析的opcode值:")
        print("支持格式: 0x700C1214、700C1214、1879572948")

        try:
            input_str = input("\nopcode值: ").strip()
            if not input_str:
                continue

            if input_str.lower() in ['exit', 'quit', 'q']:
                print("再见!")
                break

            # 解析输入值
            value = parse_input_value(input_str)

            print(f"\n解析输入:")
            print(f"   十进制: {value}")
            print(f"   十六进制: 0x{value:08X}")
            print(f"   二进制: {value:032b}")

            # 解码opcode
            decoded = decode_opcode_value(value)

            print(f"\n检测到格式: {decoded['format']}")

            # 如果是集群模式，获取CPU拓扑
            topology = {}
            if 'clusters' in decoded:
                cluster_count = decoded.get('cluster_count', len(decoded['clusters']))
                default_topo = get_default_topology(cluster_count)

                use_default = input(f"\n使用默认CPU拓扑{list(default_topo.values())}? (y/n): ").lower()
                if use_default in ['y', 'yes', '']:
                    topology = default_topo
                    print(f"使用默认拓扑: {'+'.join(map(str, default_topo.values()))}")
                else:
                    topology = get_cpu_topology_input(cluster_count)

            # 打印结果
            print_cpu_allocation(decoded, topology)
            print_expected_output(decoded, topology)

        except ValueError as e:
            print(f"解析错误: {e}")
        except KeyboardInterrupt:
            print("\n\n用户中断")
            break
        except Exception as e:
            print(f"未知错误: {e}")

        # 询问是否继续
        continue_choice = input("\n继续解析其他值? (y/n): ").lower()
        if continue_choice not in ['y', 'yes', '']:
            break

if __name__ == "__main__":
    main()
