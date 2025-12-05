#!/usr/bin/env bash
set -euo pipefail

# 本番シナリオ実行スクリプト（共有ONのみ、50回実行）
# トラフィックログを有効にして実行

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUT_DIR="evaluation_runs/${TIMESTAMP}_production_scenarios_ON_50runs"
mkdir -p "$OUT_DIR"

# 実行回数: 50回（評価シナリオと評価指標.mdに基づく）
RUN_COUNT=50

echo "================================================================================"
echo "本番シナリオ実行（共有ONのみ、50回実行）"
echo "================================================================================"
echo "出力ディレクトリ: $OUT_DIR"
echo "実行回数: 各シナリオ50回"
echo "トラフィックログ: 有効"
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
  
  # トラフィックログを有効化
  export FORERUNNER_TRAFFIC_LOG_ENABLE=true
  
  # 実行
  ./gradlew -q run --args="50 1000 10000" > "$OUT_DIR/${name}_run${run_num}.log" 2>&1
  
  # 実行時間を記録
  echo "[$(date '+%H:%M:%S')] Completed $name (Run $run_num/$RUN_COUNT)"
}

# 本番シナリオ定義（共有ONのみ）
production_scenarios_ON=(
  # 適用率10% - 共有ON
  "P10_10_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P10_50_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P10_90_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率40% - 共有ON
  "P40_10_ON:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P40_50_ON:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P40_90_ON:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率70% - 共有ON
  "P70_10_ON:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P70_50_ON:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P70_90_ON:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率100% - 共有ON
  "P100_10_ON:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P100_50_ON:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P100_90_ON:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:BROADCAST_SCOPE=NEIGHBORS_ONLY:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
)

# シナリオ実行
scenario_num=0
total_scenarios=${#production_scenarios_ON[@]}
total_runs=$((total_scenarios * RUN_COUNT))

echo "総シナリオ数: $total_scenarios"
echo "総実行数: $total_runs"
echo

for scenario_def in "${production_scenarios_ON[@]}"; do
  scenario_num=$((scenario_num + 1))
  IFS=':' read -ra PARTS <<< "$scenario_def"
  scenario_name="${PARTS[0]}"
  env_vars="${scenario_def#*:}"
  
  echo "=================================================================================="
  echo "[$scenario_num/$total_scenarios] シナリオ: $scenario_name"
  echo "=================================================================================="
  
  for run_num in $(seq 1 $RUN_COUNT); do
    run_one "$scenario_name" "$run_num" "$env_vars"
  done
  
  echo
done

echo "=================================================================================="
echo "実行完了"
echo "=================================================================================="
echo "完了時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "出力ディレクトリ: $OUT_DIR"
echo "合計実行数: $total_runs"
echo "=================================================================================="

