#!/bin/bash

# 送受信分離のテスト実行スクリプト

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# テスト用の出力ディレクトリ
TEST_DIR="evaluation_runs/test_traffic_separation_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$TEST_DIR"

echo "=========================================="
echo "送受信分離テスト実行"
echo "=========================================="
echo "出力ディレクトリ: $TEST_DIR"
echo ""

# 環境変数の設定
export SIM_SEED=10000
export SLOW_NODE_RATIO=0.5
export FORERUNNER_SHARE_ONLY_ON_TX=false
export FORERUNNER_TRAFFIC_LOG_ENABLE=true

# テストシナリオ: ON_0.1_0.1を1回だけ実行
SCENARIO_NAME="ON_0.1_0.1"
LOG_FILE="$TEST_DIR/${SCENARIO_NAME}_run1.log"

echo "シナリオ: $SCENARIO_NAME"
echo "ログファイル: $LOG_FILE"
echo ""

# シミュレーション実行
echo "シミュレーション実行中..."
./gradlew :simulator:run --args="--scenario $SCENARIO_NAME" > "$LOG_FILE" 2>&1

echo "実行完了"
echo ""

# ログファイルの確認
echo "=========================================="
echo "ログファイルの確認"
echo "=========================================="
echo ""

# トラフィック統計のログ行を抽出
echo "【送信トラフィック統計】"
grep "FCSM_TRAFFIC_BANDWIDTH_STATS_SEND" "$LOG_FILE" || echo "見つかりませんでした"

echo ""
echo "【受信トラフィック統計】"
grep "FCSM_TRAFFIC_BANDWIDTH_STATS_RECV" "$LOG_FILE" || echo "見つかりませんでした"

echo ""
echo "【合計トラフィック統計】"
grep "FCSM_TRAFFIC_BANDWIDTH_STATS_TOTAL" "$LOG_FILE" || echo "見つかりませんでした"

echo ""
echo "【旧形式（互換性確認）】"
grep "FCSM_TRAFFIC_BANDWIDTH_STATS:" "$LOG_FILE" | grep -v "SEND\|RECV\|TOTAL" || echo "旧形式は見つかりませんでした（正常）"

echo ""
echo "=========================================="
echo "分析スクリプトでの解析テスト"
echo "=========================================="
echo ""

# 分析スクリプトで解析（直接ログファイルを指定）
echo "ログファイルを直接解析..."
python3 -c "
import sys
sys.path.insert(0, 'evaluation_runs')
from analyze_traffic_stats import parse_traffic_stats
import json

stats = parse_traffic_stats('$LOG_FILE')
print(json.dumps(stats, indent=2, ensure_ascii=False))
"

echo ""
echo "=========================================="
echo "テスト完了"
echo "=========================================="
echo "ログファイル: $LOG_FILE"
echo "出力ディレクトリ: $TEST_DIR"

