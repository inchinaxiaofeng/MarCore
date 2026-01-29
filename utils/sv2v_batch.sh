#!/usr/bin/env bash
set -e

# 修改参数检查：允许 2 个或 3 个参数
if [ $# -lt 2 ]; then
	echo "用法: $0 <源目录> <目标根目录> [可选:宏名称]"
	echo "示例: $0 ./buildv ./output SYNTHESIS"
	exit 1
fi

SRC_DIR="$(realpath "$1")"
DST_ROOT="$(realpath "$2")"
MACRO_NAME="$3" # 获取第三个参数

# 处理宏定义参数
SV2V_FLAGS=""
if [ -n "$MACRO_NAME" ]; then
	SV2V_FLAGS="-D $MACRO_NAME"
	echo "🔧 已启用宏定义: $SV2V_FLAGS (将移除仿真代码)"
else
	echo "ℹ️ 未指定宏定义 (保留原样)"
fi

# 检查源目录
if [ ! -d "$SRC_DIR" ]; then
	echo "❌ 源目录不存在: $SRC_DIR"
	exit 1
fi

# 遍历源目录中的所有 .sv 文件
find "$SRC_DIR" -type f -name "*.sv" | while read -r SRC_FILE; do
	# 相对路径（相对于源目录）
	REL_PATH="${SRC_FILE#$SRC_DIR/}"
	REL_DIR="$(dirname "$REL_PATH")"

	# 在目标根目录下重建目录
	mkdir -p "$DST_ROOT/$REL_DIR"

	# 输出文件名（后缀改成 .v）
	BASENAME="$(basename "$SRC_FILE" .sv)"
	DST_FILE="$DST_ROOT/$REL_DIR/$BASENAME.v"

	echo "➡️ 转换: $SRC_FILE → $DST_FILE"

	# 调用 sv2v (加上了 SV2V_FLAGS)
	sv2v $SV2V_FLAGS "$SRC_FILE" >"$DST_FILE"
done

echo "✅ 转换完成，输出在: $DST_ROOT"
