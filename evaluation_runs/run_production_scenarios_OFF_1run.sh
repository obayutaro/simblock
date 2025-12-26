#!/usr/bin/env bash
set -euo pipefail

# 本番シナリオ実行スクリプト（共有OFFのみ、各シナリオ1回実行）

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUT_DIR="evaluation_runs/${TIMESTAMP}_production_scenarios_OFF_1run"
mkdir -p "$OUT_DIR"

echo "================================================================================"
echo "本番シナリオ実行（共有OFFのみ、各シナリオ1回実行）"
echo "================================================================================"
echo "出力ディレクトリ: $OUT_DIR"
echo "実行回数: 各シナリオ1回"
echo "開始時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "================================================================================"
echo

run_one() {
  local name="$1"
  local env_vars="$2"
  local out_dir="$3"
  
  echo "[$(date '+%H:%M:%S')] Running $name..."
  
  # 環境変数を設定
  export SIM_SEED=10000  # 固定シード値
  IFS=':' read -ra VARS <<< "$env_vars"
  for var in "${VARS[@]}"; do
    if [[ "$var" == *"="* ]]; then
      export "$var"
    fi
  done
  
  # トラフィックログは無効化
  export FORERUNNER_TRAFFIC_LOG_ENABLE=false
  
  # 実行
  ./gradlew -q run --args="50 1000 10000" > "$out_dir/${name}_run1.log" 2>&1
  
  echo "[$(date '+%H:%M:%S')] Completed $name"
}

# 本番シナリオ定義（共有OFFのみ）
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

# シナリオ実行
scenario_num=0
total_scenarios=${#production_scenarios_OFF[@]}

echo "総シナリオ数: $total_scenarios"
echo

for scenario_def in "${production_scenarios_OFF[@]}"; do
  scenario_num=$((scenario_num + 1))
  IFS=':' read -ra PARTS <<< "$scenario_def"
  scenario_name="${PARTS[0]}"
  env_vars="${scenario_def#*:}"
  
  echo "=================================================================================="
  echo "[$scenario_num/$total_scenarios] シナリオ: $scenario_name"
  echo "=================================================================================="
  
  run_one "$scenario_name" "$env_vars" "$OUT_DIR"
  
  echo
done

echo "=================================================================================="
echo "実行完了"
echo "=================================================================================="
echo "完了時刻: $(date '+%Y-%m-%d %H:%M:%S')"
echo "出力ディレクトリ: $OUT_DIR"
echo "合計実行数: $total_scenarios"
echo "=================================================================================="



