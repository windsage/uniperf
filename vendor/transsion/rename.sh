#!/bin/bash
# perfengine_rename_dir_first.sh - 目录优先重命名脚本
# 策略：先处理所有目录和文件名，最后统一处理文件内容

set -e

echo "=========================================="
echo "PerfEngine 重命名脚本（目录优先版）"
echo "=========================================="

PROJECT_ROOT="."
cd "$PROJECT_ROOT"

if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "❌ 错误：当前不在git仓库中"
    exit 1
fi

echo ""
echo "[预览] 将要修改的内容..."
echo "=========================================="

# 统计信息
dir_count=$(find . -type d \( -name "*perfengine*" -o -name "*PerfEngine*" -o -name "*perfengine*" -o -name "*PerfEngine*" \) ! -path "*/.git/*" 2>/dev/null | wc -l || echo "0")
echo "📁 需要重命名的目录数量: $dir_count"

file_rename_count=$(find . -type f \( -name "*perfengine*" -o -name "*PerfEngine*" -o -name "*perfengine*" -o -name "*PerfEngine*" \) ! -path "*/.git/*" 2>/dev/null | wc -l || echo "0")
echo "📝 需要重命名的文件数量: $file_rename_count"

text_file_count=$(find . -type f \( \
    -name "*.java" -o -name "*.cpp" -o -name "*.h" -o -name "*.c" -o \
    -name "*.bp" -o -name "*.mk" -o -name "*.xml" -o -name "*.aidl" -o \
    -name "*.md" -o -name "*.gradle" -o -name "*.proto" -o -name "*.textproto" \
\) ! -path "*/.git/*" 2>/dev/null | wc -l || echo "0")
echo "📄 需要修改内容的文件数量（估算）: $text_file_count"

echo ""
read -p "是否继续执行重命名？(y/n): " confirm
if [ "$confirm" != "y" ]; then
    echo "操作已取消"
    exit 0
fi

# =====================================================
# 步骤1: 重命名顶层 perfengine 目录
# =====================================================
echo ""
echo "[步骤1/4] 重命名顶层 perfengine 目录..."
echo "=========================================="

# 查找并重命名所有顶层 perfengine 目录
find . -maxdepth 8 -type d -name "perfengine" ! -path "*/.git/*" 2>/dev/null | while read dir; do
    newdir=$(echo "$dir" | sed 's/perfengine/perfengine/')
    
    if [ "$dir" != "$newdir" ]; then
        echo "正在重命名: $dir -> $newdir"
        
        # 如果目标目录已存在，询问是否覆盖
        if [ -d "$newdir" ]; then
            echo "⚠️  目标目录已存在: $newdir"
            read -p "是否删除已存在的目录？(y/n): " del_confirm
            if [ "$del_confirm" = "y" ]; then
                rm -rf "$newdir"
            else
                echo "⏭️  跳过: $dir"
                continue
            fi
        fi
        
        # 两步法重命名避免冲突
        temp_dir="${dir}_temp_$(date +%s)"
        git mv "$dir" "$temp_dir" 2>/dev/null || mv "$dir" "$temp_dir"
        git mv "$temp_dir" "$newdir" 2>/dev/null || mv "$temp_dir" "$newdir"
        
        echo "✅ 重命名完成: $newdir"
    fi
done

echo "✅ 顶层目录重命名完成"

# =====================================================
# 步骤2: 重命名所有子目录（从深到浅）
# =====================================================
echo ""
echo "[步骤2/4] 重命名所有子目录（从深到浅）..."
echo "=========================================="

# 查找所有包含 perfengine/PerfEngine/perfengine/PerfEngine 的目录
# 按路径长度排序（深的在前）
find . -depth -type d \( \
    -name "*perfengine*" -o \
    -name "*PerfEngine*" -o \
    -name "*perfengine*" -o \
    -name "*PerfEngine*" \
\) ! -path "*/.git/*" 2>/dev/null | \
  awk '{ print length, $0 }' | sort -rn | cut -d' ' -f2- | while read dir; do
    
    # 跳过已经不存在的目录
    if [ ! -d "$dir" ]; then
        continue
    fi
    
    # 计算新目录名
    newdir=$(echo "$dir" | sed \
        -e 's/perfengine/perfengine/g' \
        -e 's/PerfEngine/PerfEngine/g' \
        -e 's/perfengine/perfengine/g' \
        -e 's/PerfEngine/PerfEngine/g')
    
    if [ "$dir" != "$newdir" ] && [ ! -e "$newdir" ]; then
        # 确保父目录存在
        parent=$(dirname "$newdir")
        if [ ! -d "$parent" ]; then
            echo "📁 创建父目录: $parent"
            mkdir -p "$parent"
        fi
        
        echo "📂 重命名目录: $dir -> $newdir"
        git mv "$dir" "$newdir" 2>/dev/null || mv "$dir" "$newdir"
    fi
done

echo "✅ 子目录重命名完成"

# =====================================================
# 步骤3: 重命名所有文件
# =====================================================
echo ""
echo "[步骤3/4] 重命名所有文件..."
echo "=========================================="

# 查找所有文件名包含 perfengine/PerfEngine/perfengine/PerfEngine 的文件
find . -type f \( \
    -name "*perfengine*" -o \
    -name "*PerfEngine*" -o \
    -name "*perfengine*" -o \
    -name "*PerfEngine*" \
\) ! -path "*/.git/*" 2>/dev/null | while read file; do
    
    # 跳过已经不存在的文件
    if [ ! -f "$file" ]; then
        continue
    fi
    
    dir=$(dirname "$file")
    base=$(basename "$file")
    
    # 计算新文件名
    newbase=$(echo "$base" | sed \
        -e 's/perfengine/perfengine/g' \
        -e 's/PerfEngine/PerfEngine/g' \
        -e 's/perfengine/perfengine/g' \
        -e 's/PerfEngine/PerfEngine/g')
    
    if [ "$base" != "$newbase" ]; then
        newfile="$dir/$newbase"
        
        # 确保目标目录存在
        if [ ! -d "$dir" ]; then
            mkdir -p "$dir"
        fi
        
        echo "📄 重命名文件: $base -> $newbase"
        git mv "$file" "$newfile" 2>/dev/null || mv "$file" "$newfile"
    fi
done

echo "✅ 文件重命名完成"

# =====================================================
# 步骤4: 修改所有文件内容（最后一步！）
# =====================================================
echo ""
echo "[步骤4/4] 修改所有文件内容..."
echo "=========================================="

echo "正在扫描并修改文件内容，这可能需要一些时间..."

# 4.1 使用 find 处理常见文本文件类型
echo "📝 处理代码和配置文件..."
find . -type f \( \
    -name "*.java" -o \
    -name "*.cpp" -o \
    -name "*.cc" -o \
    -name "*.cxx" -o \
    -name "*.h" -o \
    -name "*.hpp" -o \
    -name "*.c" -o \
    -name "*.aidl" -o \
    -name "*.bp" -o \
    -name "*.mk" -o \
    -name "*.xml" -o \
    -name "*.gradle" -o \
    -name "*.properties" -o \
    -name "*.proto" -o \
    -name "*.textproto" -o \
    -name "*.aconfig" -o \
    -name "*.rc" -o \
    -name "*.sh" -o \
    -name "*.py" -o \
    -name "*.md" -o \
    -name "*.txt" -o \
    -name "*.json" -o \
    -name "*.yaml" -o \
    -name "*.yml" -o \
    -name "Makefile" -o \
    -name "README" \
\) ! -path "*/.git/*" ! -name "*.jar" ! -name "*.so" 2>/dev/null | while read file; do
    # 跳过二进制文件（简单检查）
    if file "$file" | grep -q "text"; then
        # 执行替换
        sed -i \
            -e 's/vendor\.transsion\.hardware\.perfengine/vendor.transsion.hardware.perfengine/g' \
            -e 's/com\.transsion\.perfengine/com.transsion.perfengine/g' \
            -e 's/PerfEngine/PerfEngine/g' \
            -e 's/perfengine/perfengine/g' \
            -e 's/PerfEngine/PerfEngine/g' \
            -e 's/perfengine/perfengine/g' \
            -e 's/namespace perfengine/namespace perfengine/g' \
            -e 's/PERFENGINE/PERFENGINE/g' \
            -e 's/PERFENGINE/PERFENGINE/g' \
            "$file"
    fi
done

# 4.2 使用 git grep 再次确认（兜底）
echo "🔍 检查并处理剩余文件..."
if git grep -l -i -E "(perfengine|PerfEngine|perfengine|PerfEngine)" 2>/dev/null | grep -v ".git"; then
    git grep -l -i -E "(perfengine|PerfEngine|perfengine|PerfEngine)" 2>/dev/null | \
        grep -v ".git" | \
        xargs -r sed -i \
            -e 's/vendor\.transsion\.hardware\.perfengine/vendor.transsion.hardware.perfengine/g' \
            -e 's/com\.transsion\.perfengine/com.transsion.perfengine/g' \
            -e 's/PerfEngine/PerfEngine/g' \
            -e 's/perfengine/perfengine/g' \
            -e 's/PerfEngine/PerfEngine/g' \
            -e 's/perfengine/perfengine/g' \
            -e 's/PERFENGINE/PERFENGINE/g' \
            -e 's/PERFENGINE/PERFENGINE/g'
fi

echo "✅ 文件内容修改完成"

# =====================================================
# 步骤5: 清理和验证
# =====================================================
echo ""
echo "[清理] 删除空目录..."
find . -type d -empty ! -path "*/.git/*" -delete 2>/dev/null || true

echo ""
echo "=========================================="
echo "[验证] 检查重命名结果..."
echo "=========================================="

# 检查目录名
echo ""
echo "🔍 检查目录名..."
remaining_dirs=$(find . -type d \( -name "*perfengine*" -o -name "*PerfEngine*" \) ! -path "*/.git/*" 2>/dev/null | wc -l || echo "0")
if [ "$remaining_dirs" -gt 0 ]; then
    echo "⚠️  警告: 发现 $remaining_dirs 个目录仍使用旧命名:"
    find . -type d \( -name "*perfengine*" -o -name "*PerfEngine*" \) ! -path "*/.git/*" 2>/dev/null | head -10
else
    echo "✅ 目录名: 全部替换完成"
fi

# 检查文件名
echo ""
echo "🔍 检查文件名..."
remaining_files=$(find . -type f \( -name "*perfengine*" -o -name "*PerfEngine*" -o -name "*perfengine*" -o -name "*PerfEngine*" \) ! -path "*/.git/*" ! -name "*.jar" ! -name "*.so" 2>/dev/null | wc -l || echo "0")
if [ "$remaining_files" -gt 0 ]; then
    echo "⚠️  警告: 发现 $remaining_files 个文件仍使用旧命名:"
    find . -type f \( -name "*perfengine*" -o -name "*PerfEngine*" -o -name "*perfengine*" -o -name "*PerfEngine*" \) ! -path "*/.git/*" ! -name "*.jar" ! -name "*.so" 2>/dev/null | head -10
else
    echo "✅ 文件名: 全部替换完成"
fi

# 检查文件内容
echo ""
echo "🔍 检查文件内容..."
remaining_content=$(git grep -i -E "(perfengine|PerfEngine)" 2>/dev/null | grep -v -i "perfengine" | wc -l || echo "0")
if [ "$remaining_content" -gt 0 ]; then
    echo "⚠️  警告: 发现 $remaining_content 处文件内容仍包含旧命名:"
    echo ""
    git grep -i -E "(perfengine|PerfEngine)" 2>/dev/null | grep -v -i "perfengine" | head -20
    echo ""
    echo "提示：如果是 .jar 或 .so 文件，需要手动重新编译"
else
    echo "✅ 文件内容: 全部替换完成"
fi

# 显示 git 状态
echo ""
echo "=========================================="
echo "Git 状态摘要:"
echo "=========================================="
modified_count=$(git status --short | wc -l)
echo "📊 修改的文件总数: $modified_count"
echo ""
git status --short | head -30
echo ""
if [ "$modified_count" -gt 30 ]; then
    echo "...（共 $modified_count 个文件被修改，使用 'git status' 查看完整列表）"
fi

echo ""
echo "=========================================="
echo "✅ 重命名完成！"
echo "=========================================="
echo ""
echo "📋 后续操作建议:"
echo ""
echo "  1️⃣  查看所有修改:"
echo "     git status"
echo ""
echo "  2️⃣  查看具体差异:"
echo "     git diff --stat"
echo "     git diff | less"
echo ""
echo "  3️⃣  编译验证:"
echo "     m -j"
echo ""
echo "  4️⃣  如果编译通过，提交修改:"
echo "     git add ."
echo "     git commit -m 'refactor: rename PerfEngine to PerfEngine"
echo ""
echo "     - Rename all directories from perfengine to perfengine"
echo "     - Rename all files containing perfengine to perfengine"
echo "     - Update all code references and imports"
echo "     - Update AIDL interfaces and package names'"
echo ""
echo "  5️⃣  如果发现遗漏，可以手动处理或重新运行脚本"
echo ""
echo "=========================================="
