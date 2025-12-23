#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PerfLock Opcode 生成和解析工具 (交互式版本)
支持perfLock API的Opcode字段操作

位字段定义:
31-30: 版本号 (Version Number)
28-22: 主要类型 (Major Type)
19-14: 次要类型 (Minor Type)
13:    值映射类型 (Value Mapping)
11-8:  集群号 (Cluster Number)
6-4:   核心号 (Core Number)
其他:   保留位 (Reserved)
"""


class PerfLockOpcodeGenerator:
    def __init__(self):
        # 常用Opcode示例
        self.common_opcodes = {
            0x40800000: "CPU0 Min Frequency",
            0x40800010: "CPU1 Min Frequency",
            0x40804000: "CPU0 Max Frequency",
            0x40804010: "CPU1 Max Frequency",
            0x41000000: "Cluster 0 Cores Online",
            0x41000100: "Cluster 1 Cores Online",
            0x41004000: "Cluster 0 Max Cores",
            0x42800000: "GPU Power Level",
            0x42804000: "GPU Min Power Level",
            0x42808000: "GPU Max Power Level",
            0x40C00000: "Scheduler Boost",
            0x41800000: "CPU BW Min Frequency",
        }

    def generate_opcode(self, version=1, major_type=0, minor_type=0,
                        value_mapping=0, cluster=0, core=0):
        """生成Opcode"""
        # 验证参数范围
        if not (0 <= version <= 3):
            raise ValueError("版本号必须在0-3范围内")
        if not (0 <= major_type <= 127):
            raise ValueError("主要类型必须在0-127范围内")
        if not (0 <= minor_type <= 63):
            raise ValueError("次要类型必须在0-63范围内")
        if not (0 <= value_mapping <= 1):
            raise ValueError("值映射类型必须在0-1范围内")
        if not (0 <= cluster <= 15):
            raise ValueError("集群号必须在0-15范围内")
        if not (0 <= core <= 7):
            raise ValueError("核心号必须在0-7范围内")

        # 构建Opcode
        opcode = 0
        opcode |= (version & 0x3) << 30  # 位31-30: 版本号
        opcode |= (major_type & 0x7F) << 22  # 位28-22: 主要类型
        opcode |= (minor_type & 0x3F) << 14  # 位19-14: 次要类型
        opcode |= (value_mapping & 0x1) << 13  # 位13: 值映射类型
        opcode |= (cluster & 0xF) << 8  # 位11-8: 集群号
        opcode |= (core & 0x7) << 4  # 位6-4: 核心号

        return opcode

    def parse_opcode(self, opcode):
        """解析Opcode"""
        if isinstance(opcode, str):
            # 支持16进制字符串输入
            if opcode.startswith('0x') or opcode.startswith('0X'):
                opcode = int(opcode, 16)
            else:
                opcode = int(opcode)

        # 提取各个字段
        version = (opcode >> 30) & 0x3
        major_type = (opcode >> 22) & 0x7F
        minor_type = (opcode >> 14) & 0x3F
        value_mapping = (opcode >> 13) & 0x1
        cluster = (opcode >> 8) & 0xF
        core = (opcode >> 4) & 0x7
        reserved = opcode & 0xF

        # 获取类型描述

        result = {
            'opcode': f"0x{opcode:08X}",
            'version': version,
            'major_type': major_type,
            'minor_type': minor_type,
            'value_mapping': value_mapping,
            'cluster': cluster,
            'core': core,
            'reserved': reserved,
            'description': self.common_opcodes.get(opcode, "Custom Opcode")
        }

        return result

    def print_opcode_info(self, result):
        """格式化打印Opcode信息"""
        print(f"\n=== Opcode Parse Result ===")
        print(f"Opcode:       {result['opcode']}")
        print(f"Description:  {result['description']}")
        print(f"\nField Details:")
        print(f"  Version:      0x{result['version']:X}")
        print(f"  Major Type:   0x{result['major_type']:X}")
        print(f"  Minor Type:   0x{result['minor_type']:X}")
        print(f"  Value Mapping: 0x{result['value_mapping']:X}")
        print(f"  Cluster:      0x{result['cluster']:X}")
        print(f"  Core:         0x{result['core']:X}")
        print(f"  Reserved:     0x{result['reserved']:X}")

    def show_help(self):
        """显示帮助信息"""
        print("\n=== Help Information ===")
        print("Common Opcode Examples:")
        for opcode, desc in self.common_opcodes.items():
            print(f"  0x{opcode:08X} - {desc}")

        print("\nCommands:")
        print("  help    - Show this help information")
        print("  gen     - Generate Opcode (version fixed to 1)")
        print("  parse   - Parse Opcode")
        print("  exit    - Exit program")

        print("\nInput Format:")
        print("  Major/Minor Type: Use hex format (e.g. 0x2, 0x0)")
        print("  Opcode: Use hex format (e.g. 0x40800000)")

        print("\nBit Field Definition:")
        print("  31-30: Version Number")
        print("  28-22: Major Type")
        print("  19-14: Minor Type")
        print("  13:    Value Mapping")
        print("  11-8:  Cluster Number")
        print("  6-4:   Core Number")
        print("  Others: Reserved")

    def interactive_generate(self):
        """交互式生成Opcode"""
        print("\n=== Generate Opcode ===")

        try:
            # 版本号固定为1
            version = 1

            # 输入主要类型
            major_input = input("Major Type (hex format, e.g. 0x2): ").strip()
            if major_input.startswith('0x') or major_input.startswith('0X'):
                major_type = int(major_input, 16)
            else:
                print("错误: 请使用16进制格式输入 (例如: 0x2)")
                return

            # 输入次要类型
            minor_input = input("Minor Type (hex format, e.g. 0x0): ").strip()
            if minor_input.startswith('0x') or minor_input.startswith('0X'):
                minor_type = int(minor_input, 16)
            else:
                print("错误: 请使用16进制格式输入 (例如: 0x0)")
                return

            # 输入其他参数
            mapping_input = input("Value Mapping (0-1, default 0): ").strip()
            value_mapping = int(mapping_input) if mapping_input else 0

            cluster_input = input("Cluster Number (0-15, default 0): ").strip()
            cluster = int(cluster_input) if cluster_input else 0

            core_input = input("Core Number (0-7, default 0): ").strip()
            core = int(core_input) if core_input else 0

            # 生成Opcode
            opcode = self.generate_opcode(version, major_type, minor_type, value_mapping, cluster, core)

            print(f"\n=== Generation Result ===")
            print(f"Generated Opcode: 0x{opcode:08X}")
            print(f"Version: {version} (fixed)")

            # 自动解析生成的Opcode
            result = self.parse_opcode(opcode)
            self.print_opcode_info(result)

        except ValueError as e:
            print(f"Error: {e}")
        except Exception as e:
            print(f"Generation failed: {e}")

    def interactive_parse(self):
        """交互式解析Opcode"""
        print("\n=== Parse Opcode ===")

        opcode_input = input("Enter Opcode to parse (hex format, e.g. 0x40800000): ").strip()

        if not opcode_input:
            print("Error: Please enter a valid Opcode")
            return

        try:
            result = self.parse_opcode(opcode_input)
            self.print_opcode_info(result)
        except Exception as e:
            print(f"Parse failed: {e}")

    def run(self):
        """运行交互式程序"""
        print("=== PerfLock Opcode Generator and Parser ===")
        print("Enter 'help' for more information")

        while True:
            print("\n" + "=" * 50)
            command = input("Enter command (gen/parse/help/exit): ").strip().lower()

            if command == 'gen' or command == 'generate':
                self.interactive_generate()

            elif command == 'parse':
                self.interactive_parse()

            elif command == 'help':
                self.show_help()

            elif command == 'exit' or command == 'quit':
                print("Exit program")
                break

            elif command == '':
                continue

            else:
                print(f"Unknown command: '{command}'")
                print("Available commands: gen, parse, help, exit")


def main():
    generator = PerfLockOpcodeGenerator()
    generator.run()


if __name__ == "__main__":
    main()
