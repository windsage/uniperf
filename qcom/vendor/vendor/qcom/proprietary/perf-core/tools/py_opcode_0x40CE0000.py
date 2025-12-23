#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
高通Perflock Migrate值转换工具
支持upmigrate/downmigrate与reqval之间的双向转换
"""

def create_migrate_value(upmigrate, downmigrate):
    """
    将upmigrate和downmigrate编码为reqval

    Args:
        upmigrate (int): 上迁移阈值 (1-65535)
        downmigrate (int): 下迁移阈值 (1-65535)

    Returns:
        int: 编码后的reqval，失败返回0
    """
    # 输入验证
    if not (1 <= upmigrate <= 65535) or not (1 <= downmigrate <= 65535):
        print(f"错误: migrate值必须在1-65535范围内")
        return 0

    # migrate规则验证
    if upmigrate <= downmigrate:
        print(f"错误: upmigrate({upmigrate})必须大于downmigrate({downmigrate})")
        return 0

    # 位运算编码: 高16位为upmigrate, 低16位为downmigrate
    reqval = (upmigrate << 16) | downmigrate
    return reqval

def parse_migrate_value(reqval):
    """
    从reqval解析出upmigrate和downmigrate

    Args:
        reqval (int): 32位请求值

    Returns:
        tuple: (upmigrate, downmigrate)，失败返回(0, 0)
    """
    # 输入验证
    if not (0 <= reqval <= 0xFFFFFFFF):
        print(f"错误: reqval必须是32位无符号整数")
        return (0, 0)

    # 位运算解析
    upmigrate = (reqval >> 16) & 0xFFFF    # 提取高16位
    downmigrate = reqval & 0xFFFF          # 提取低16位

    # 值有效性检查
    if upmigrate == 0 or downmigrate == 0:
        print(f"错误: 解析出的migrate值不能为0")
        return (0, 0)

    if upmigrate <= downmigrate:
        print(f"警告: upmigrate({upmigrate}) <= downmigrate({downmigrate})")

    return (upmigrate, downmigrate)

def print_hex_format(value):
    """格式化输出16进制值"""
    return f"0x{value:08X}"

def main():
    """主函数"""
    print("=" * 50)
    print("高通Perflock Migrate转换工具")
    print("=" * 50)

    while True:
        print("\n请选择操作:")
        print("1. 编码 (upmigrate, downmigrate -> reqval)")
        print("2. 解析 (reqval -> upmigrate, downmigrate)")
        print("3. 批量测试")
        print("4. 退出")

        choice = input("\n请输入选择 (1-4): ").strip()

        if choice == '1':
            # 编码模式
            print("\n--- 编码模式 ---")
            try:
                upmigrate = int(input("请输入upmigrate值: "))
                downmigrate = int(input("请输入downmigrate值: "))

                reqval = create_migrate_value(upmigrate, downmigrate)
                if reqval > 0:
                    print(f"\n编码结果:")
                    print(f"upmigrate={upmigrate}, downmigrate={downmigrate}")
                    print(f"reqval = {reqval} ({print_hex_format(reqval)})")
                    print(f"perflock调用: 0x40CE0000, {print_hex_format(reqval)}")

            except ValueError:
                print("错误: 请输入有效的整数")

        elif choice == '2':
            # 解析模式
            print("\n--- 解析模式 ---")
            try:
                reqval_input = input("请输入reqval (支持十进制或0x开头的16进制): ").strip()

                # 支持16进制输入
                if reqval_input.startswith('0x') or reqval_input.startswith('0X'):
                    reqval = int(reqval_input, 16)
                else:
                    reqval = int(reqval_input)

                upmigrate, downmigrate = parse_migrate_value(reqval)
                if upmigrate > 0 and downmigrate > 0:
                    print(f"\n解析结果:")
                    print(f"reqval = {reqval} ({print_hex_format(reqval)})")
                    print(f"upmigrate = {upmigrate}")
                    print(f"downmigrate = {downmigrate}")

            except ValueError:
                print("错误: 请输入有效的数值")

        elif choice == '3':
            # 批量测试
            print("\n--- 批量测试 ---")
            test_cases = [
                (10, 5),      # 激进跑分设置
                (20, 5),      # 推荐跑分设置
                (40, 20),     # 平衡设置
                (60, 40),     # 游戏设置
                (94, 65),     # 默认值
            ]

            print("常用migrate配置:")
            print(f"{'场景':<12} {'upmigrate':<10} {'downmigrate':<12} {'reqval':<12} {'十六进制'}")
            print("-" * 60)

            scenarios = ["激进跑分", "推荐跑分", "平衡设置", "游戏设置", "系统默认"]
            for i, (up, down) in enumerate(test_cases):
                reqval = create_migrate_value(up, down)
                if reqval > 0:
                    print(f"{scenarios[i]:<12} {up:<10} {down:<12} {reqval:<12} {print_hex_format(reqval)}")

        elif choice == '4':
            print("\n感谢使用！")
            break

        else:
            print("无效选择，请重新输入")

if __name__ == "__main__":
    main()
