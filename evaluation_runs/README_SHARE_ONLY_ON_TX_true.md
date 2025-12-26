# FORERUNNER_SHARE_ONLY_ON_TX=true 評価実行ガイド

## 概要

本ガイドは、`FORERUNNER_SHARE_ONLY_ON_TX=true`での本番シナリオ評価（伝播時間のみ）の実行方法を説明します。

## 実行内容

- **シナリオ**: 本番シナリオ（共有ONのみ）12シナリオ
- **モード**: NEIGHBORS_ONLY と REQUEST_BASED の2つのモード
- **実行回数**: 各シナリオ50回 × 2モード = 1,200回実行
- **評価指標**: 伝播時間のみ（トラフィックは評価しない）
- **パラメータ**: `FORERUNNER_SHARE_ONLY_ON_TX=true`

## 実行方法

### 1. シミュレーション実行

```bash
cd /home/yutaro/simblock_提案手法実装中
nohup ./evaluation_runs/run_production_scenarios_SHARE_ONLY_ON_TX_true.sh > evaluation_runs/execution_log_SHARE_ONLY_ON_TX_true_$(date +%Y%m%d_%H%M%S).log 2>&1 &
```

### 2. 実行状況確認

```bash
# 実行中のプロセス確認
ps aux | grep run_production_scenarios_SHARE_ONLY_ON_TX_true

# 実行ログ確認
tail -f evaluation_runs/execution_log_SHARE_ONLY_ON_TX_true_*.log

# 完了した実行数確認
ls -1 evaluation_runs/*_production_scenarios_SHARE_ONLY_ON_TX_true_NEIGHBORS_ONLY_50runs/*.log | wc -l
ls -1 evaluation_runs/*_production_scenarios_SHARE_ONLY_ON_TX_true_REQUEST_BASED_50runs/*.log | wc -l
```

### 3. 分析実行

実行が完了したら、以下のコマンドで分析を実行します：

```bash
./evaluation_runs/analyze_after_completion_SHARE_ONLY_ON_TX_true.sh
```

または、手動で分析スクリプトを実行：

```bash
NEIGHBORS_DIR="evaluation_runs/YYYYMMDD_HHMMSS_production_scenarios_SHARE_ONLY_ON_TX_true_NEIGHBORS_ONLY_50runs"
REQUEST_DIR="evaluation_runs/YYYYMMDD_HHMMSS_production_scenarios_SHARE_ONLY_ON_TX_true_REQUEST_BASED_50runs"
OUTPUT_DIR="evaluation_runs/comparison_SHARE_ONLY_ON_TX_true_$(date +%Y%m%d_%H%M%S)"

python3 evaluation_runs/analyze_propagation_time_only.py "$NEIGHBORS_DIR" "$REQUEST_DIR" "$OUTPUT_DIR"
```

## 出力ファイル

### 実行結果

- `evaluation_runs/YYYYMMDD_HHMMSS_production_scenarios_SHARE_ONLY_ON_TX_true_NEIGHBORS_ONLY_50runs/`
  - 各シナリオのログファイル（600ファイル）
- `evaluation_runs/YYYYMMDD_HHMMSS_production_scenarios_SHARE_ONLY_ON_TX_true_REQUEST_BASED_50runs/`
  - 各シナリオのログファイル（600ファイル）

### 分析結果

- `evaluation_runs/comparison_SHARE_ONLY_ON_TX_true_YYYYMMDD_HHMMSS/comparison_results.json`
  - JSON形式の比較結果
- `evaluation_runs/comparison_SHARE_ONLY_ON_TX_true_YYYYMMDD_HHMMSS/comparison_report.md`
  - Markdown形式の比較レポート

## 評価指標

### 伝播時間（Block Propagation Time）

- **定義**: ブロックが最初のノードから最後のノードまで伝播するのに要する時間
- **統計量**: 平均値、中央値、標準偏差、最小値、最大値
- **単位**: ミリ秒（ms）

### ネットワーク効率（Network Efficiency）

- **定義**: ブロックがネットワークを拡散する際の平均進行速度
- **統計量**: 平均値、中央値、標準偏差、最小値、最大値
- **単位**: ミリ秒（ms）

## 注意事項

1. **実行時間**: 1,200回実行には長時間（数時間〜数日）を要する可能性があります
2. **ディスク容量**: ログファイルは大量のディスク容量を使用する可能性があります
3. **トラフィックログ**: 本評価ではトラフィックログは無効化されています（`FORERUNNER_TRAFFIC_LOG_ENABLE=false`）
4. **パラメータ**: `FORERUNNER_SHARE_ONLY_ON_TX=true`により、ブロック受信時の未来コンテキスト共有は抑止され、トランザクション受信時のみ共有が実行されます

## シナリオ一覧

| シナリオ | Forerunner適用率 | 低速ノード割合 |
|---------|----------------|---------------|
| P10_10_ON | 10% | 10% |
| P10_50_ON | 10% | 50% |
| P10_90_ON | 10% | 90% |
| P40_10_ON | 40% | 10% |
| P40_50_ON | 40% | 50% |
| P40_90_ON | 40% | 90% |
| P70_10_ON | 70% | 10% |
| P70_50_ON | 70% | 50% |
| P70_90_ON | 70% | 90% |
| P100_10_ON | 100% | 10% |
| P100_50_ON | 100% | 50% |
| P100_90_ON | 100% | 90% |

## 比較対象

- **NEIGHBORS_ONLY**: 隣接ノードのみに未来コンテキストを共有
- **REQUEST_BASED**: 要求ベースで未来コンテキストを共有

## 期待される結果

- **伝播時間**: REQUEST_BASEDモードはNEIGHBORS_ONLYモードと比較して同等またはそれ以上の性能を維持
- **統計的信頼性**: 50回実行による高精度な統計分析



