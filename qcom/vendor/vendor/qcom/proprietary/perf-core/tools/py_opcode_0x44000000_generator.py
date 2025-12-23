#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
增强版 TRAN_PERF 频率 Opcode 生成器
=====================================
支持2/3/4集群动态适配的编码方案：
1. 0x7格式 - 动态多集群编码 (支持2/3/4集群)
2. 0x6格式 - 兼容3集群编码 (仅支持3集群)
3. 统一频率 - 所有CPU相同频率

Author: chao.xu5
"""

# TRAN_PERF opcode
TRAN_INPUT_BOOST_FREQ_OPCODE = 0x44000000

def generate_7_format_2_cluster(freq0_mhz: int, freq1_mhz: int) -> int:
    """
    生成0x7格式2集群编码
    格式: 0x7LLLLBBBB (16+12位)
    """
    freq0_code = min(freq0_mhz // 100, 0xFFFF)  # 16位，最大6553.5GHz
    freq1_code = min(freq1_mhz // 100, 0xFFF)   # 12位，最大409.5GHz

    value = 0x70000000 | (freq0_code << 12) | freq1_code
    return value

def generate_7_format_3_cluster(freq0_mhz: int, freq1_mhz: int, freq2_mhz: int) -> int:
    """
    生成0x7格式3集群编码
    格式: 0x7LLLBBPP (12+8+8位)
    """
    freq0_code = min(freq0_mhz // 100, 0xFFF)   # 12位，最大409.5GHz
    freq1_code = min(freq1_mhz // 100, 0xFF)    # 8位，最大25.5GHz
    freq2_code = min(freq2_mhz // 100, 0xFF)    # 8位，最大25.5GHz

    value = 0x70000000 | (freq0_code << 16) | (freq1_code << 8) | freq2_code
    return value

def generate_7_format_4_cluster(freq0_mhz: int, freq1_mhz: int, freq2_mhz: int, freq3_mhz: int) -> int:
    """
    生成0x7格式4集群编码
    格式: 0x7LLBBTTPP (8+6+6+8位)
    """
    freq0_code = min(freq0_mhz // 100, 0xFF)    # 8位，最大25.5GHz
    freq1_code = min(freq1_mhz // 100, 0x3F)    # 6位，最大6.3GHz
    freq2_code = min(freq2_mhz // 100, 0x3F)    # 6位，最大6.3GHz
    freq3_code = min(freq3_mhz // 100, 0xFF)    # 8位，最大25.5GHz

    value = 0x70000000 | (freq0_code << 20) | (freq1_code << 14) | (freq2_code << 8) | freq3_code
    return value

def generate_6_format(little_mhz: int, big_mhz: int, prime_mhz: int) -> int:
    """
    生成0x6格式编码 (仅支持3集群)
    格式: 0x6LLBBPP (8+8+8位)
    """
    little_code = min(little_mhz // 100, 0xFF)   # 8位，最大25.5GHz
    big_code = min(big_mhz // 100, 0xFF)         # 8位，最大25.5GHz
    prime_code = min(prime_mhz // 100, 0xFF)     # 8位，最大25.5GHz

    value = 0x60000000 | (little_code << 16) | (big_code << 8) | prime_code
    return value

def generate_unified(freq_mhz: int) -> int:
    """
    生成统一频率编码
    所有CPU使用相同频率
    """
    return freq_mhz

def get_int_input(prompt: str, min_val: int = 0, max_val: int = 9999) -> int:
    """获取整数输入"""
    while True:
        try:
            value = int(input(prompt))
            if min_val <= value <= max_val:
                return value
            else:
                print(f"请输入 {min_val}-{max_val} 之间的值!")
        except ValueError:
            print("请输入有效的数字!")

def print_result(opcode: int, value: int, mode: str):
    """打印结果"""
    print(f"\n生成结果 ({mode}):")
    print(f"opcode: 0x{opcode:08X}")
    print(f"value:  0x{value:08X} ({value})")
    print(f"\n测试命令:")
    print(f"/vendor/bin/perf_opcode_test acquire 0x{opcode:08X}:0x{value:08X}")

def handle_7_format():
    """处理0x7格式多集群模式"""
    cluster_count = get_int_input("请输入集群数量 (2-4): ", 2, 4)

    cluster_names = ["集群0", "集群1", "集群2", "集群3"]
    freqs = []

    print(f"\n请输入{cluster_count}个集群的频率 (MHz):")

    # 根据集群数设置最大频率限制
    if cluster_count == 2:
        max_freqs = [6553500, 4095000]  # 16位*100=655.35GHz, 12位*100=409.5GHz
        print("频率限制: 集群0最大655.35GHz, 集群1最大409.5GHz")
    elif cluster_count == 3:
        max_freqs = [409500, 25500, 25500]  # 12位*100, 8位*100, 8位*100
        print("频率限制: 集群0最大409.5GHz, 集群1-2最大25.5GHz")
    elif cluster_count == 4:
        max_freqs = [25500, 6300, 6300, 25500]  # 8位*100, 6位*100, 6位*100, 8位*100
        print("频率限制: 集群0,3最大25.5GHz, 集群1,2最大6.3GHz")

    for i in range(cluster_count):
        freq = get_int_input(f"{cluster_names[i]}频率 (MHz): ", 0, max_freqs[i])
        freqs.append(freq)

    # 生成对应格式的值
    if cluster_count == 2:
        value = generate_7_format_2_cluster(freqs[0], freqs[1])
        mode = f"0x7格式 2集群"
    elif cluster_count == 3:
        value = generate_7_format_3_cluster(freqs[0], freqs[1], freqs[2])
        mode = f"0x7格式 3集群"
    elif cluster_count == 4:
        value = generate_7_format_4_cluster(freqs[0], freqs[1], freqs[2], freqs[3])
        mode = f"0x7格式 4集群"

    print_result(TRAN_INPUT_BOOST_FREQ_OPCODE, value, mode)

def handle_6_format():
    """处理0x6格式3集群模式"""
    print("\n0x6格式固定3集群模式:")
    print("💡 频率限制: 所有集群最大25.5GHz")

    little = get_int_input("Little集群频率 (MHz): ", 0, 2550)
    big = get_int_input("Big集群频率 (MHz): ", 0, 2550)
    prime = get_int_input("Prime集群频率 (MHz): ", 0, 2550)

    value = generate_6_format(little, big, prime)
    mode = "0x6格式 3集群"

    print_result(TRAN_INPUT_BOOST_FREQ_OPCODE, value, mode)

def handle_unified():
    """处理统一频率模式"""
    print("\n统一频率模式:")
    freq = get_int_input("统一频率 (MHz): ", 0, 9999)
    value = generate_unified(freq)
    mode = "统一频率"

    print_result(TRAN_INPUT_BOOST_FREQ_OPCODE, value, mode)

def main():
    """主函数"""
    print(" TRAN_PERF 频率 Opcode 生成器")
    print("=" * 50)

    while True:
        print("\n请选择编码格式:")
        print("1. 0x7格式 - 动态多集群 (支持2/3/4集群)")
        print("2. 0x6格式 - 兼容3集群 (仅支持3集群)")
        print("3. 统一频率 - 所有CPU相同")
        print("0. 退出")

        choice = get_int_input("\n请选择 (0-3): ", 0, 3)

        if choice == 0:
            print("再见!")
            break
        elif choice == 1:
            handle_7_format()
        elif choice == 2:
            handle_6_format()
        elif choice == 3:
            handle_unified()

        # 询问是否继续
        continue_choice = input("\n继续生成? (y/n): ").lower()
        if continue_choice not in ['y', 'yes']:
            break

if __name__ == "__main__":
    main()
