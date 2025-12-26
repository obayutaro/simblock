#!/bin/bash

# 実行状況確認スクリプト

echo "=========================================="
echo "実行状況確認"
echo "=========================================="
echo ""

# 実行中のプロセスを確認
echo "【実行中のプロセス】"
ps aux | grep "run_production_scenarios_NEIGHBORS_ONLY_vs_REQUEST_BASED" | grep -v grep || echo "実行中のプロセスはありません"
echo ""

# 最新の実行ディレクトリを確認
echo "【最新の実行ディレクトリ】"
NEIGHBORS_DIR=$(ls -td evaluation_runs/*_production_scenarios_NEIGHBORS_ONLY_50runs 2>/dev/null | head -1)
REQUEST_DIR=$(ls -td evaluation_runs/*_production_scenarios_REQUEST_BASED_50runs 2>/dev/null | head -1)

if [ -n "$NEIGHBORS_DIR" ]; then
  echo "NEIGHBORS_ONLY: $NEIGHBORS_DIR"
  NEIGHBORS_COUNT=$(ls -1 "$NEIGHBORS_DIR"/*.log 2>/dev/null | wc -l)
  echo "  完了した実行数: $NEIGHBORS_COUNT / 600"
else
  echo "NEIGHBORS_ONLY: 実行ディレクトリが見つかりません"
fi

if [ -n "$REQUEST_DIR" ]; then
  echo "REQUEST_BASED: $REQUEST_DIR"
  REQUEST_COUNT=$(ls -1 "$REQUEST_DIR"/*.log 2>/dev/null | wc -l)
  echo "  完了した実行数: $REQUEST_COUNT / 600"
else
  echo "REQUEST_BASED: 実行ディレクトリが見つかりません"
fi

echo ""
echo "【進捗率】"
if [ -n "$NEIGHBORS_DIR" ] && [ -n "$REQUEST_DIR" ]; then
  TOTAL=$((NEIGHBORS_COUNT + REQUEST_COUNT))
  PROGRESS=$(echo "scale=2; $TOTAL / 1200 * 100" | bc)
  echo "  総進捗: $TOTAL / 1200 ($PROGRESS%)"
fi

echo ""
echo "【最新のログファイル（各5件）】"
if [ -n "$NEIGHBORS_DIR" ]; then
  echo "NEIGHBORS_ONLY:"
  ls -lt "$NEIGHBORS_DIR"/*.log 2>/dev/null | head -5 | awk '{print "  " $9 " (" $6 " " $7 " " $8 ")"}'
fi

if [ -n "$REQUEST_DIR" ]; then
  echo "REQUEST_BASED:"
  ls -lt "$REQUEST_DIR"/*.log 2>/dev/null | head -5 | awk '{print "  " $9 " (" $6 " " $7 " " $8 ")"}'
fi

echo ""
echo "=========================================="

