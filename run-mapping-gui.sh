#!/bin/bash

echo "=========================================="
echo "  Anki Track Mapping with GUI"
echo "=========================================="
echo ""

# 编译
echo "📦 Compiling..."
mvn compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✓ Compilation successful"
echo ""
echo "🚀 Starting GUI application..."
echo ""
echo "说明："
echo "  1. 会打开一个可视化窗口"
echo "  2. 小车检测轨道时，窗口会实时显示轨道图片"
echo "  3. 使用真实的 PNG 图片拼接显示完整地图"
echo ""

# 运行 JavaFX 应用
mvn exec:java -Dexec.mainClass="de.pdbm.anki.example.TrackMappingWithGUI" -q
