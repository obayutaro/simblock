#!/bin/bash

# 実行完了後の分析スクリプト

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "実行完了後の分析スクリプト"
echo "=========================================="
echo ""

# 最新の実行ディレクトリを取得
NEIGHBORS_DIR=$(ls -td evaluation_runs/*_production_scenarios_NEIGHBORS_ONLY_50runs 2>/dev/null | head -1)
REQUEST_DIR=$(ls -td evaluation_runs/*_production_scenarios_REQUEST_BASED_50runs 2>/dev/null | head -1)

if [ -z "$NEIGHBORS_DIR" ] || [ -z "$REQUEST_DIR" ]; then
  echo "エラー: 実行ディレクトリが見つかりません"
  echo "NEIGHBORS_ONLY: $NEIGHBORS_DIR"
  echo "REQUEST_BASED: $REQUEST_DIR"
  exit 1
fi

# 実行数の確認
NEIGHBORS_COUNT=$(ls -1 "$NEIGHBORS_DIR"/*.log 2>/dev/null | wc -l)
REQUEST_COUNT=$(ls -1 "$REQUEST_DIR"/*.log 2>/dev/null | wc -l)

echo "実行数確認:"
echo "  NEIGHBORS_ONLY: $NEIGHBORS_COUNT / 600"
echo "  REQUEST_BASED: $REQUEST_COUNT / 600"
echo ""

if [ "$NEIGHBORS_COUNT" -lt 600 ] || [ "$REQUEST_COUNT" -lt 600 ]; then
  echo "警告: すべての実行が完了していません"
  echo "実行を続行しますか？ (y/n)"
  read -r answer
  if [ "$answer" != "y" ]; then
    exit 1
  fi
fi

# 出力ディレクトリ
OUTPUT_DIR="evaluation_runs/comparison_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUTPUT_DIR"

echo "分析を開始します..."
echo "  入力（NEIGHBORS_ONLY）: $NEIGHBORS_DIR"
echo "  入力（REQUEST_BASED）: $REQUEST_DIR"
echo "  出力: $OUTPUT_DIR"
echo ""

# 分析実行
python3 evaluation_runs/analyze_NEIGHBORS_ONLY_vs_REQUEST_BASED.py \
  "$NEIGHBORS_DIR" \
  "$REQUEST_DIR" \
  "$OUTPUT_DIR"

echo ""
echo "=========================================="
echo "分析完了"
echo "=========================================="
echo "結果ファイル:"
echo "  JSON: $OUTPUT_DIR/comparison_results.json"
echo "  レポート: $OUTPUT_DIR/comparison_report.md"
echo "=========================================="

