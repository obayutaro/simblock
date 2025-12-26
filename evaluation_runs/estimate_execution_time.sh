#!/bin/bash

# 実行時間の逆算スクリプト

NEIGHBORS_DIR=$(ls -td evaluation_runs/*_production_scenarios_NEIGHBORS_ONLY_50runs 2>/dev/null | head -1)

if [ -z "$NEIGHBORS_DIR" ]; then
  echo "エラー: NEIGHBORS_ONLYの実行ディレクトリが見つかりません"
  exit 1
fi

COUNT=$(ls -1 "$NEIGHBORS_DIR"/*.log 2>/dev/null | wc -l)

if [ "$COUNT" -lt 2 ]; then
  echo "実行数が少なすぎます（$COUNT回）。もう少し待ってから再実行してください。"
  exit 1
fi

# 最初と最後のログファイルのタイムスタンプを取得
FIRST_LOG=$(ls -t "$NEIGHBORS_DIR"/*.log 2>/dev/null | tail -1)
LATEST_LOG=$(ls -t "$NEIGHBORS_DIR"/*.log 2>/dev/null | head -1)

FIRST_TIME=$(stat -c %Y "$FIRST_LOG" 2>/dev/null)
LATEST_TIME=$(stat -c %Y "$LATEST_LOG" 2>/dev/null)

if [ -z "$FIRST_TIME" ] || [ -z "$LATEST_TIME" ]; then
  echo "エラー: タイムスタンプの取得に失敗しました"
  exit 1
fi

ELAPSED=$((LATEST_TIME - FIRST_TIME))
AVG_TIME=$((ELAPSED / (COUNT - 1)))

REMAINING_NEIGHBORS=$((600 - COUNT))
REMAINING_REQUEST=600
TOTAL_REMAINING=$((REMAINING_NEIGHBORS + REMAINING_REQUEST))
ESTIMATED_SECONDS=$((TOTAL_REMAINING * AVG_TIME))

ESTIMATED_HOURS=$((ESTIMATED_SECONDS / 3600))
ESTIMATED_MINUTES=$(((ESTIMATED_SECONDS % 3600) / 60))
ESTIMATED_DAYS=$((ESTIMATED_HOURS / 24))
ESTIMATED_REMAINING_HOURS=$((ESTIMATED_HOURS % 24))

CURRENT_TIME=$(date +%s)
ESTIMATED_END_TIME=$((CURRENT_TIME + ESTIMATED_SECONDS))
ESTIMATED_END_DATE=$(date -d "@$ESTIMATED_END_TIME" '+%Y-%m-%d %H:%M:%S')

echo "=========================================="
echo "実行時間の逆算"
echo "=========================================="
echo ""
echo "【現在の状況】"
echo "  完了実行数（NEIGHBORS_ONLY）: $COUNT / 600"
echo "  残り実行数（NEIGHBORS_ONLY）: $REMAINING_NEIGHBORS"
echo "  残り実行数（REQUEST_BASED）: $REMAINING_REQUEST"
echo "  総残り実行数: $TOTAL_REMAINING"
echo ""
echo "【実行時間の分析】"
echo "  経過時間: ${ELAPSED}秒 ($(echo "scale=2; $ELAPSED / 60" | bc)分)"
echo "  1回あたりの平均実行時間: ${AVG_TIME}秒 ($(echo "scale=2; $AVG_TIME / 60" | bc)分)"
echo ""
echo "【推定残り時間】"
echo "  残り実行時間: ${ESTIMATED_SECONDS}秒"
if [ "$ESTIMATED_DAYS" -gt 0 ]; then
  echo "  = ${ESTIMATED_DAYS}日${ESTIMATED_REMAINING_HOURS}時間${ESTIMATED_MINUTES}分"
else
  echo "  = ${ESTIMATED_HOURS}時間${ESTIMATED_MINUTES}分"
fi
echo "  = $(echo "scale=2; $ESTIMATED_SECONDS / 3600" | bc)時間"
echo ""
echo "【推定完了時刻】"
echo "  $ESTIMATED_END_DATE"
echo ""
echo "【進捗率】"
PROGRESS=$(echo "scale=2; $COUNT / 1200 * 100" | bc)
echo "  現在の進捗: $PROGRESS%"
echo "=========================================="

