#!/usr/bin/env bash
set -euo pipefail

# 共有OFF本番シナリオテスト実行スクリプト（全12シナリオ、各1回実行）
# Forerunner適用率10/40/70/100%、低速ノード割合10/50/90%、共有OFF
# テスト用に1回実行

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUT_DIR="evaluation_runs/${TIMESTAMP}_production_scenarios_OFF_test"
mkdir -p "$OUT_DIR"

# 実行回数: 1回（テスト用）
RUN_COUNT=1

echo "================================================================================"
echo "共有OFF本番シナリオテスト実行（全12シナリオ、各1回実行）"
echo "================================================================================"
echo "出力ディレクトリ: $OUT_DIR"
echo "実行回数: 各シナリオ1回（テスト用）"
echo "パラメータ: TX_PROCESSING_TIME=20ms, ASSUMED_TX_PER_BLOCK=25"
echo "開始時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "================================================================================"
echo

run_one() {
  local name="$1"
  local run_num="$2"
  local env_vars="$3"
  
  echo "[$(date '+%H:%M:%S')] Running $name (Run $run_num/$RUN_COUNT)..."
  
  # 環境変数を設定
  export SIM_SEED=$((10000 + run_num))
  IFS=':' read -ra VARS <<< "$env_vars"
  for var in "${VARS[@]}"; do
    if [[ "$var" == *"="* ]]; then
      export "$var"
    fi
  done
  
  # 実行
  ./gradlew -q run --args="50 1000 10000" > "$OUT_DIR/${name}_run${run_num}.log" 2>&1
  
  # 実行時間を記録
  echo "[$(date '+%H:%M:%S')] Completed $name (Run $run_num/$RUN_COUNT)"
}

# 共有OFFシナリオ定義（全12シナリオ）
scenarios=(
  "P10_10_OFF:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=false"
  "P10_50_OFF:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=false"
  "P10_90_OFF:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=false"
  "P40_10_OFF:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=false"
  "P40_50_OFF:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=false"
  "P40_90_OFF:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=false"
  "P70_10_OFF:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=false"
  "P70_50_OFF:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=false"
  "P70_90_OFF:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=false"
  "P100_10_OFF:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=false"
  "P100_50_OFF:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=false"
  "P100_90_OFF:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=false"
)

# 各シナリオを実行
for scenario in "${scenarios[@]}"; do
  IFS=':' read -ra PARTS <<< "$scenario"
  name="${PARTS[0]}"
  env_vars="${scenario#*:}"
  
  for run_num in $(seq 1 $RUN_COUNT); do
    run_one "$name" "$run_num" "$env_vars"
  done
done

echo
echo "=================================================================================="
echo "実行完了"
echo "=================================================================================="
echo "完了時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "出力ディレクトリ: $OUT_DIR"
echo "合計実行数: $((${#scenarios[@]} * RUN_COUNT))"
echo "=================================================================================="

