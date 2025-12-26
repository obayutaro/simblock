# NEIGHBORS_ONLY vs REQUEST_BASED 比較評価 実行ガイド

## 実行状況

**実行開始時刻**: 2025年12月17日 12:24頃

**実行内容**:
- NEIGHBORS_ONLYモード: 12シナリオ × 50回 = 600回実行
- REQUEST_BASEDモード: 12シナリオ × 50回 = 600回実行
- **総実行数**: 1,200回実行

## 実行状況の確認

### 実行状況確認コマンド

```bash
./evaluation_runs/check_execution_status.sh
```

このコマンドで以下を確認できます：
- 実行中のプロセス
- 完了した実行数
- 進捗率
- 最新のログファイル

### 実行ログの確認

```bash
# 最新の実行ログを確認
tail -f evaluation_runs/execution_log_*.log

# 特定のシナリオのログを確認
ls -lt evaluation_runs/*_production_scenarios_NEIGHBORS_ONLY_50runs/P10_10_ON_*.log | head -5
```

## 実行完了後の分析

### 自動分析

実行が完了したら、以下のコマンドで分析を実行します：

```bash
./evaluation_runs/analyze_after_completion.sh
```

このスクリプトは：
1. 最新の実行ディレクトリを自動検出
2. 実行数の確認（600回×2モード）
3. 比較分析の実行
4. レポートの生成

### 手動分析

実行ディレクトリが分かっている場合：

```bash
python3 evaluation_runs/analyze_NEIGHBORS_ONLY_vs_REQUEST_BASED.py \
  <neighbors_dir> \
  <request_dir> \
  <output_dir>
```

例：
```bash
python3 evaluation_runs/analyze_NEIGHBORS_ONLY_vs_REQUEST_BASED.py \
  evaluation_runs/20251217_122413_production_scenarios_NEIGHBORS_ONLY_50runs \
  evaluation_runs/20251217_122413_production_scenarios_REQUEST_BASED_50runs \
  evaluation_runs/comparison_results
```

## 実行パラメータ

### シード値管理
- **シナリオ間統一**: 同じ実行順序（run_num）で同じシード値を使用
- **シード値**: `SIM_SEED=$((10000 + run_num * 1000))` (run_num=1..50)
- **公平比較**: ランダム性の影響を排除した公平な比較

### 共通パラメータ
- `FORERUNNER_SHARE_ONLY_ON_TX=false`
- `FORERUNNER_TRAFFIC_LOG_ENABLE=true`
- `FORERUNNER_FAST_SHARE=true`
- `FORERUNNER_LOG_ENABLE=false`
- `FORERUNNER_LOG_SUPPRESS=true`
- `FORERUNNER_DIAG_LOG_ENABLE=false`

### モード別パラメータ
- **NEIGHBORS_ONLY**: `BROADCAST_SCOPE=NEIGHBORS_ONLY`
- **REQUEST_BASED**: `BROADCAST_SCOPE=REQUEST_BASED`

## 評価シナリオ

本番シナリオ（共有ONのみ）: 12シナリオ

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

## 評価指標

1. **ブロック伝播時間**: ブロックが最初のノードから最後のノードまで伝播するのに要する時間
2. **ネットワーク効率**: ブロックがネットワークを拡散する際の平均進行速度
3. **トラフィック帯域使用量**: 未来コンテキスト共有によって発生するネットワークトラフィック

## 期待される結果

- **トラフィック削減**: REQUEST_BASEDモードはNEIGHBORS_ONLYモードと比較してトラフィックを大幅に削減
- **性能維持**: トラフィック削減しながら、同等またはそれ以上の性能を維持
- **統計的信頼性**: 50回実行による高精度な統計分析

## 注意事項

1. **実行時間**: 1,200回実行には長時間（数時間〜数日）を要する可能性があります
2. **ディスク容量**: ログファイルは大量のディスク容量を使用する可能性があります
3. **実行の中断**: 実行を中断した場合、再開するにはスクリプトを修正する必要があります

## トラブルシューティング

### 実行が停止した場合

1. 実行状況を確認: `./evaluation_runs/check_execution_status.sh`
2. ログを確認: `tail -100 evaluation_runs/execution_log_*.log`
3. プロセスを確認: `ps aux | grep run_production_scenarios`

### 実行を再開する場合

完了した実行数を確認し、未完了のシナリオから再開する必要があります。

