#!/usr/bin/env bash
# 低速ノード割合90%シナリオの実行完了を待ってトラフィック統計を分析するスクリプト

set -euo pipefail

echo "================================================================================"
echo "低速ノード割合90%シナリオのトラフィック統計分析（実行完了待機中）"
echo "================================================================================"
echo

# 最新の実行ディレクトリを検索
while true; do
  eval_dir=$(ls -d evaluation_runs/2025*_slow90_scenarios_200runs 2>/dev/null | tail -1)
  
  if [ -z "$eval_dir" ]; then
    echo "[$(date '+%H:%M:%S')] 実行ディレクトリが見つかりません。10秒待機..."
    sleep 10
    continue
  fi
  
  completed=$(ls "$eval_dir"/*.log 2>/dev/null | wc -l)
  total=800
  
  if [ $completed -eq 0 ]; then
    echo "[$(date '+%H:%M:%S')] ログファイルが見つかりません。10秒待機..."
    sleep 10
    continue
  fi
  
  pct=$((completed * 100 / total))
  echo "[$(date '+%H:%M:%S')] 進捗: $completed/$total ($pct%)"
  
  if [ $completed -ge $total ]; then
    echo ""
    echo "================================================================================"
    echo "実行が完了しました！トラフィック統計を分析します..."
    echo "================================================================================"
    echo ""
    break
  fi
  
  sleep 60
done

# トラフィック統計を分析
echo "分析中..."
python3 evaluation_runs/analyze_traffic_stats.py "$eval_dir"

echo ""
echo "================================================================================"
echo "分析完了"
echo "================================================================================"
echo "詳細データ: $eval_dir/traffic_stats_summary.json"

