#!/usr/bin/env bash
set -euo pipefail

# 実行完了後の分析スクリプト（FORERUNNER_SHARE_ONLY_ON_TX=true）
# 伝播時間のみを比較

echo "=========================================="
echo "実行完了後の分析スクリプト"
echo "=========================================="
echo

# 最新の実行ディレクトリを検索
NEIGHBORS_DIR=$(ls -td evaluation_runs/*_production_scenarios_SHARE_ONLY_ON_TX_true_NEIGHBORS_ONLY_50runs 2>/dev/null | head -1)
REQUEST_DIR=$(ls -td evaluation_runs/*_production_scenarios_SHARE_ONLY_ON_TX_true_REQUEST_BASED_50runs 2>/dev/null | head -1)

if [ -z "$NEIGHBORS_DIR" ] || [ -z "$REQUEST_DIR" ]; then
  echo "エラー: 実行ディレクトリが見つかりません"
  echo "NEIGHBORS_ONLY: $NEIGHBORS_DIR"
  echo "REQUEST_BASED: $REQUEST_DIR"
  exit 1
fi

echo "実行数確認:"
NEIGHBORS_COUNT=$(ls -1 "$NEIGHBORS_DIR"/*.log 2>/dev/null | wc -l)
REQUEST_COUNT=$(ls -1 "$REQUEST_DIR"/*.log 2>/dev/null | wc -l)
echo "  NEIGHBORS_ONLY: $NEIGHBORS_COUNT / 600"
echo "  REQUEST_BASED: $REQUEST_COUNT / 600"
echo

if [ "$NEIGHBORS_COUNT" -lt 600 ] || [ "$REQUEST_COUNT" -lt 600 ]; then
  echo "警告: 実行が完了していない可能性があります"
  echo "続行しますか？ (y/n)"
  read -r answer
  if [ "$answer" != "y" ]; then
    exit 1
  fi
fi

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_DIR="evaluation_runs/comparison_SHARE_ONLY_ON_TX_true_${TIMESTAMP}"

echo "分析を開始します..."
echo "  入力（NEIGHBORS_ONLY）: $NEIGHBORS_DIR"
echo "  入力（REQUEST_BASED）: $REQUEST_DIR"
echo "  出力: $OUTPUT_DIR"
echo

python3 evaluation_runs/analyze_propagation_time_only.py "$NEIGHBORS_DIR" "$REQUEST_DIR" "$OUTPUT_DIR"

echo
echo "=========================================="
echo "分析完了"
echo "=========================================="
echo "結果ファイル:"
echo "  JSON: $OUTPUT_DIR/comparison_results.json"
echo "  レポート: $OUTPUT_DIR/comparison_report.md"
echo "=========================================="



