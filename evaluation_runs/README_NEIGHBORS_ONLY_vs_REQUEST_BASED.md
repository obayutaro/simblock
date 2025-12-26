# NEIGHBORS_ONLY vs REQUEST_BASED 比較評価

## 概要

本番シナリオ（共有ONのみ）において、NEIGHBORS_ONLYモードとREQUEST_BASEDモードの性能を比較・評価します。

## 実行方法

### 1. シミュレーション実行

```bash
./evaluation_runs/run_production_scenarios_NEIGHBORS_ONLY_vs_REQUEST_BASED.sh
```

このスクリプトは以下を実行します：
- **NEIGHBORS_ONLYモード**: 12シナリオ × 50回 = 600回実行
- **REQUEST_BASEDモード**: 12シナリオ × 50回 = 600回実行
- **総実行数**: 1,200回実行

### 2. 分析実行

実行完了後、以下のコマンドで分析を実行します：

```bash
python3 evaluation_runs/analyze_NEIGHBORS_ONLY_vs_REQUEST_BASED.py \
  <neighbors_dir> \
  <request_dir> \
  [output_dir]
```

例：
```bash
python3 evaluation_runs/analyze_NEIGHBORS_ONLY_vs_REQUEST_BASED.py \
  evaluation_runs/20251217_XXXXXX_production_scenarios_NEIGHBORS_ONLY_50runs \
  evaluation_runs/20251217_XXXXXX_production_scenarios_REQUEST_BASED_50runs \
  evaluation_runs/comparison_results
```

## 評価シナリオ

### 本番シナリオ（共有ONのみ）

| シナリオ | Forerunner適用率 | 低速ノード割合 | 共有設定 |
|---------|----------------|---------------|----------|
| P10_10_ON | 10% | 10% | ON |
| P10_50_ON | 10% | 50% | ON |
| P10_90_ON | 10% | 90% | ON |
| P40_10_ON | 40% | 10% | ON |
| P40_50_ON | 40% | 50% | ON |
| P40_90_ON | 40% | 90% | ON |
| P70_10_ON | 70% | 10% | ON |
| P70_50_ON | 70% | 50% | ON |
| P70_90_ON | 70% | 90% | ON |
| P100_10_ON | 100% | 10% | ON |
| P100_50_ON | 100% | 50% | ON |
| P100_90_ON | 100% | 90% | ON |

### 実行パラメータ

- **実行回数**: 各シナリオ50回（評価シナリオと評価指標.mdに準拠）
- **シード値**: `SIM_SEED=$((10000 + run_num * 1000))` (run_num=1..50)
  - シナリオ間統一: 同じ実行順序で同じシード値を使用
- **ブロック数**: 50ブロック
- **ノード数**: 1000ノード
- **共通パラメータ**:
  - `FORERUNNER_SHARE_ONLY_ON_TX=false`
  - `FORERUNNER_TRAFFIC_LOG_ENABLE=true`
  - `FORERUNNER_FAST_SHARE=true`
  - `FORERUNNER_LOG_ENABLE=false`
  - `FORERUNNER_LOG_SUPPRESS=true`
  - `FORERUNNER_DIAG_LOG_ENABLE=false`

### モード別パラメータ

- **NEIGHBORS_ONLY**: `BROADCAST_SCOPE=NEIGHBORS_ONLY`
- **REQUEST_BASED**: `BROADCAST_SCOPE=REQUEST_BASED`

## 評価指標

1. **ブロック伝播時間**: ブロックが最初のノードから最後のノードまで伝播するのに要する時間
2. **ネットワーク効率**: ブロックがネットワークを拡散する際の平均進行速度
3. **トラフィック帯域使用量**: 未来コンテキスト共有によって発生するネットワークトラフィック

## 出力ファイル

### 実行結果

- **NEIGHBORS_ONLY**: `evaluation_runs/<timestamp>_production_scenarios_NEIGHBORS_ONLY_50runs/`
- **REQUEST_BASED**: `evaluation_runs/<timestamp>_production_scenarios_REQUEST_BASED_50runs/`

### 分析結果

- **JSON形式**: `comparison_results.json`
- **レポート**: `comparison_report.md`

## 注意事項

1. **シード値の統一**: 同じ実行順序（run_num）で同じシード値を使用することで、公平な比較を実現
2. **パラメータの統一**: BROADCAST_SCOPE以外のパラメータは両モードで同一にする
3. **実行時間**: 1,200回実行には長時間を要する可能性があります（数時間〜数日）

## 期待される結果

- **トラフィック削減**: REQUEST_BASEDモードはNEIGHBORS_ONLYモードと比較してトラフィックを大幅に削減
- **性能維持**: トラフィック削減しながら、同等またはそれ以上の性能を維持
- **統計的信頼性**: 50回実行による高精度な統計分析

