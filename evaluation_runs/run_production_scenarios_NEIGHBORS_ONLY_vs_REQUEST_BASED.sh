#!/usr/bin/env bash
set -euo pipefail

# 本番シナリオ実行スクリプト（共有ONのみ）
# NEIGHBORS_ONLYとREQUEST_BASEDの2つのモードで比較実行

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUT_DIR_NEIGHBORS="evaluation_runs/${TIMESTAMP}_production_scenarios_NEIGHBORS_ONLY_50runs"
OUT_DIR_REQUEST="evaluation_runs/${TIMESTAMP}_production_scenarios_REQUEST_BASED_50runs"
mkdir -p "$OUT_DIR_NEIGHBORS"
mkdir -p "$OUT_DIR_REQUEST"

# 実行回数: 50回（評価シナリオと評価指標.mdに準拠）
RUN_COUNT=50

echo "================================================================================"
echo "本番シナリオ実行（共有ONのみ、NEIGHBORS_ONLY vs REQUEST_BASED比較）"
echo "================================================================================"
echo "出力ディレクトリ（NEIGHBORS_ONLY）: $OUT_DIR_NEIGHBORS"
echo "出力ディレクトリ（REQUEST_BASED）: $OUT_DIR_REQUEST"
echo "実行回数: 各シナリオ50回"
echo "トラフィックログ: 有効"
echo "開始時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "================================================================================"
echo

run_one() {
  local name="$1"
  local run_num="$2"
  local env_vars="$3"
  local broadcast_scope="$4"
  local out_dir="$5"
  
  echo "[$(date '+%H:%M:%S')] Running $name (Run $run_num/$RUN_COUNT) [BROADCAST_SCOPE=$broadcast_scope]..."
  
  # 環境変数を設定
  # シード値管理: シナリオ間統一（同じ実行順序で同じシード値を使用）
  # 評価シナリオと評価指標.mdに準拠: SIM_SEED=$((10000 + i*1000)) (i=1..50)
  export SIM_SEED=$((10000 + run_num * 1000))
  IFS=':' read -ra VARS <<< "$env_vars"
  for var in "${VARS[@]}"; do
    if [[ "$var" == *"="* ]]; then
      export "$var"
    fi
  done
  
  # BROADCAST_SCOPEを設定（既存の設定を上書き）
  export BROADCAST_SCOPE="$broadcast_scope"
  
  # トラフィックログを有効化
  export FORERUNNER_TRAFFIC_LOG_ENABLE=true
  
  # 実行
  ./gradlew -q run --args="50 1000 10000" > "$out_dir/${name}_run${run_num}.log" 2>&1
  
  # 実行時間を記録
  echo "[$(date '+%H:%M:%S')] Completed $name (Run $run_num/$RUN_COUNT) [BROADCAST_SCOPE=$broadcast_scope]"
}

# 本番シナリオ定義（共有ONのみ）
production_scenarios_ON=(
  # 適用率10% - 共有ON
  "P10_10_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P10_50_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P10_90_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率40% - 共有ON
  "P40_10_ON:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P40_50_ON:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P40_90_ON:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率70% - 共有ON
  "P70_10_ON:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P70_50_ON:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P70_90_ON:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率100% - 共有ON
  "P100_10_ON:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P100_50_ON:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P100_90_ON:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
)

# シナリオ実行
scenario_num=0
total_scenarios=${#production_scenarios_ON[@]}
total_runs=$((total_scenarios * RUN_COUNT * 2))  # 2つのモードで実行

echo "総シナリオ数: $total_scenarios"
echo "総実行数: $total_runs (各シナリオ × 50回 × 2モード)"
echo

# NEIGHBORS_ONLYモードで実行
echo "=================================================================================="
echo "NEIGHBORS_ONLYモードで実行開始"
echo "=================================================================================="
echo

for scenario_def in "${production_scenarios_ON[@]}"; do
  scenario_num=$((scenario_num + 1))
  IFS=':' read -ra PARTS <<< "$scenario_def"
  scenario_name="${PARTS[0]}"
  env_vars="${scenario_def#*:}"
  
  echo "=================================================================================="
  echo "[$scenario_num/$total_scenarios] シナリオ: $scenario_name [NEIGHBORS_ONLY]"
  echo "=================================================================================="
  
  for run_num in $(seq 1 $RUN_COUNT); do
    run_one "$scenario_name" "$run_num" "$env_vars" "NEIGHBORS_ONLY" "$OUT_DIR_NEIGHBORS"
  done
  
  echo
done

# REQUEST_BASEDモードで実行
echo "=================================================================================="
echo "REQUEST_BASEDモードで実行開始"
echo "=================================================================================="
echo

scenario_num=0
for scenario_def in "${production_scenarios_ON[@]}"; do
  scenario_num=$((scenario_num + 1))
  IFS=':' read -ra PARTS <<< "$scenario_def"
  scenario_name="${PARTS[0]}"
  env_vars="${scenario_def#*:}"
  
  echo "=================================================================================="
  echo "[$scenario_num/$total_scenarios] シナリオ: $scenario_name [REQUEST_BASED]"
  echo "=================================================================================="
  
  for run_num in $(seq 1 $RUN_COUNT); do
    run_one "$scenario_name" "$run_num" "$env_vars" "REQUEST_BASED" "$OUT_DIR_REQUEST"
  done
  
  echo
done

echo "=================================================================================="
echo "実行完了"
echo "=================================================================================="
echo "完了時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "出力ディレクトリ（NEIGHBORS_ONLY）: $OUT_DIR_NEIGHBORS"
echo "出力ディレクトリ（REQUEST_BASED）: $OUT_DIR_REQUEST"
echo "合計実行数: $total_runs"
echo "=================================================================================="

