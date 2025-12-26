#!/usr/bin/env bash
set -euo pipefail

# 本番シナリオ実行スクリプト（評価シナリオと評価指標.mdに準拠）
# - 共有OFF: 12シナリオ × 50回 = 600回実行
# - 共有ON: 12シナリオ × 2モード（NEIGHBORS_ONLY, REQUEST_BASED）× 50回 = 1,200回実行
# 合計: 1,800回実行

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUT_DIR="evaluation_runs/${TIMESTAMP}_all_production_scenarios_50runs"
mkdir -p "$OUT_DIR"

# 実行回数: 50回（評価シナリオと評価指標.mdに準拠）
RUN_COUNT=50

echo "================================================================================"
echo "本番シナリオ実行（評価シナリオと評価指標.mdに準拠）"
echo "================================================================================"
echo "出力ディレクトリ: $OUT_DIR"
echo "実行回数: 各シナリオ${RUN_COUNT}回"
echo "共有OFF: 12シナリオ × ${RUN_COUNT}回 = $((12 * RUN_COUNT))回"
echo "共有ON: 12シナリオ × 2モード × ${RUN_COUNT}回 = $((12 * 2 * RUN_COUNT))回"
echo "合計: $((12 * RUN_COUNT + 12 * 2 * RUN_COUNT))回"
echo "開始時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "================================================================================"
echo

run_one() {
  local name="$1"
  local run_num="$2"
  local env_vars="$3"
  local broadcast_scope="$4"  # NEIGHBORS_ONLY, REQUEST_BASED, or empty for OFF
  local out_dir="$5"
  
  echo "[$(date '+%H:%M:%S')] Running $name (Run $run_num/$RUN_COUNT)${broadcast_scope:+ [BROADCAST_SCOPE=$broadcast_scope]}..."
  
  # 環境変数を設定
  # シード値管理: 評価シナリオと評価指標.mdに準拠
  # SIM_SEED=$((10000 + i*1000)) (i=1..50)
  export SIM_SEED=$((10000 + run_num * 1000))
  IFS=':' read -ra VARS <<< "$env_vars"
  for var in "${VARS[@]}"; do
    if [[ "$var" == *"="* ]]; then
      export "$var"
    fi
  done
  
  # BROADCAST_SCOPEを設定（共有ONの場合のみ）
  if [[ -n "$broadcast_scope" ]]; then
    export BROADCAST_SCOPE="$broadcast_scope"
  fi
  
  # トラフィックログ: 共有ONの場合は有効化、共有OFFの場合は無効化
  if [[ "$env_vars" == *"FORERUNNER_SHARING_ENABLED=true"* ]]; then
    export FORERUNNER_TRAFFIC_LOG_ENABLE=true
  else
    export FORERUNNER_TRAFFIC_LOG_ENABLE=false
  fi
  
  # 実行
  ./gradlew -q run --args="50 1000 10000" > "$out_dir/${name}${broadcast_scope:+_${broadcast_scope}}_run${run_num}.log" 2>&1
  
  echo "[$(date '+%H:%M:%S')] Completed $name (Run $run_num/$RUN_COUNT)${broadcast_scope:+ [BROADCAST_SCOPE=$broadcast_scope]}"
}

# 本番シナリオ定義（共有OFF）
production_scenarios_OFF=(
  # 適用率10% - 共有OFF
  "P10_10_OFF:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P10_50_OFF:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P10_90_OFF:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率40% - 共有OFF
  "P40_10_OFF:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P40_50_OFF:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P40_90_OFF:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率70% - 共有OFF
  "P70_10_OFF:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P70_50_OFF:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P70_90_OFF:FORERUNNER_RATIO=0.7:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率100% - 共有OFF
  "P100_10_OFF:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P100_50_OFF:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P100_90_OFF:FORERUNNER_RATIO=1.0:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=false:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
)

# 本番シナリオ定義（共有ON）
production_scenarios_ON=(
  # 適用率10% - 共有ON
  "P10_10_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P10_50_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P10_90_ON:FORERUNNER_RATIO=0.1:SLOW_NODE_RATIO=0.9:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  
  # 適用率40% - 共有ON
  "P40_10_ON:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.1:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
  "P40_50_ON:FORERUNNER_RATIO=0.4:SLOW_NODE_RATIO=0.5:FORERUNNER_SHARING_ENABLED=true:FORERUNNER_SHARE_ONLY_ON_TX=false:FORERUNNER_FAFAST_SHARE=true:FORERUNNER_LOG_ENABLE=false:FORERUNNER_LOG_SUPPRESS=true:FORERUNNER_DIAG_LOG_ENABLE=false"
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

# 共有OFFシナリオ実行
echo "=================================================================================="
echo "共有OFFシナリオ実行"
echo "=================================================================================="
scenario_num=0
total_off_scenarios=${#production_scenarios_OFF[@]}

for scenario_def in "${production_scenarios_OFF[@]}"; do
  scenario_num=$((scenario_num + 1))
  IFS=':' read -ra PARTS <<< "$scenario_def"
  scenario_name="${PARTS[0]}"
  env_vars="${scenario_def#*:}"
  
  echo "----------------------------------------------------------------------------------"
  echo "[$scenario_num/$total_off_scenarios] シナリオ: $scenario_name"
  echo "----------------------------------------------------------------------------------"
  
  for run_num in $(seq 1 $RUN_COUNT); do
    run_one "$scenario_name" "$run_num" "$env_vars" "" "$OUT_DIR"
  done
  
  echo
done

# 共有ONシナリオ実行（NEIGHBORS_ONLYとREQUEST_BASEDの両方）
echo "=================================================================================="
echo "共有ONシナリオ実行（NEIGHBORS_ONLYとREQUEST_BASEDの両方）"
echo "=================================================================================="
scenario_num=0
total_on_scenarios=${#production_scenarios_ON[@]}

for scenario_def in "${production_scenarios_ON[@]}"; do
  scenario_num=$((scenario_num + 1))
  IFS=':' read -ra PARTS <<< "$scenario_def"
  scenario_name="${PARTS[0]}"
  env_vars="${scenario_def#*:}"
  
  echo "----------------------------------------------------------------------------------"
  echo "[$scenario_num/$total_on_scenarios] シナリオ: $scenario_name"
  echo "----------------------------------------------------------------------------------"
  
  # NEIGHBORS_ONLYで実行
  echo "  [NEIGHBORS_ONLY]"
  for run_num in $(seq 1 $RUN_COUNT); do
    run_one "$scenario_name" "$run_num" "$env_vars" "NEIGHBORS_ONLY" "$OUT_DIR"
  done
  
  # REQUEST_BASEDで実行
  echo "  [REQUEST_BASED]"
  for run_num in $(seq 1 $RUN_COUNT); do
    run_one "$scenario_name" "$run_num" "$env_vars" "REQUEST_BASED" "$OUT_DIR"
  done
  
  echo
done

echo "=================================================================================="
echo "実行完了"
echo "=================================================================================="
echo "完了時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "出力ディレクトリ: $OUT_DIR"
echo "合計実行数: $((total_off_scenarios * RUN_COUNT + total_on_scenarios * 2 * RUN_COUNT))"
echo "=================================================================================="

