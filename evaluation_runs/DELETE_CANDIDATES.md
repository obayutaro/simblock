# 削除候補ファイル・ディレクトリ一覧

## 1. 古い実行結果ディレクトリ（大容量）

以下のディレクトリは過去の実行結果で、現在は不要です。合計約2.3GBの容量を占めています。

- `evaluation_runs/20251126_165347_production_scenarios_50runs/` (1.1GB)
  - 2025年11月26日の実行結果（古い）
  
- `evaluation_runs/20251217_122413_production_scenarios_NEIGHBORS_ONLY_50runs/` (811MB)
  - 2025年12月17日のNEIGHBORS_ONLY実行結果（比較評価用、既に分析済み）
  
- `evaluation_runs/20251217_122413_production_scenarios_REQUEST_BASED_50runs/` (434MB)
  - 2025年12月17日のREQUEST_BASED実行結果（比較評価用、既に分析済み）

**注意**: これらのディレクトリには既に分析結果が `comparison_20251217_140428/` に保存されているため、削除しても問題ありません。

## 2. 古いログファイル

- `old_scenarios/` ディレクトリ全体
  - 古いシナリオのログファイル（10ファイル）
  - 現在は使用されていない

- `evaluation_runs/execution_log_*.log`
  - `execution_log_20251217_122413.log`
  - `execution_log_SHARE_ONLY_ON_TX_true_20251217_143538.log`
  - 実行ログ（既に実行結果は保存済み）

## 3. テスト用スクリプト（本番用ではない）

- `evaluation_runs/test_*.sh`
  - `test_neighbors_only.sh`
  - `test_request_based.sh`
  - `test_traffic_send_recv_separation.sh`
  - `test_NEIGHBORS_ONLY_vs_REQUEST_BASED.sh`
  - テスト用スクリプト（開発・検証用）

- `evaluation_runs/run_production_scenarios_OFF_test.sh`
- `evaluation_runs/run_production_scenarios_ON_test.sh`
  - テスト用実行スクリプト

## 4. 一時的な分析スクリプト（調査用）

- `evaluation_runs/analyze_traffic_increase.py`
  - 古いトラフィック分析スクリプト（現在は `analyze_traffic_stats.py` を使用）

- `evaluation_runs/investigate_traffic_calculation.py`
  - トラフィック計算の調査用スクリプト（一時的な調査用）

## 5. 古い比較結果ディレクトリ

- `evaluation_runs/comparison_20251217_140428/`
  - 古い比較結果（最新は `comparison_SHARE_ONLY_ON_TX_true_20251217_151655/`）
  - ただし、`comparison_summary.md` や `detailed_comparison_table.md` に要約されているため、削除可能

## 6. 一時的なドキュメント

- `evaluation_runs/execution_time_estimate.md`
  - 実行時間の見積もり（一時的な情報）

- `evaluation_runs/compatibility_test_summary.md`
  - 互換性テストのサマリー（テスト用）

- `evaluation_runs/test_request_based_summary.md`
  - テスト実行のサマリー（テスト用）

## 7. その他の一時ファイル

- `evaluation_runs/wait_and_analyze_*.sh`
  - `wait_and_analyze_slow90.sh`
  - `wait_and_analyze_traffic.sh`
  - 一時的な待機・分析スクリプト

- `evaluation_runs/run_slow90_scenarios_200runs.sh`
  - 特定シナリオの実行スクリプト（一時的な調査用）

## 削除推奨度

### 🔴 高（即座に削除可能）
- 古い実行結果ディレクトリ（2.3GB）
- `old_scenarios/` ディレクトリ
- 実行ログファイル（`.log`）

### 🟡 中（確認後に削除）
- テスト用スクリプト（`test_*.sh`）
- 古い比較結果ディレクトリ
- 一時的な分析スクリプト

### 🟢 低（保持を推奨）
- 最新の比較結果ディレクトリ
- 主要な分析スクリプト
- ドキュメントファイル（`.md`）

## 削除コマンド例

```bash
# 古い実行結果ディレクトリを削除（約2.3GB削減）
rm -rf evaluation_runs/20251126_165347_production_scenarios_50runs/
rm -rf evaluation_runs/20251217_122413_production_scenarios_NEIGHBORS_ONLY_50runs/
rm -rf evaluation_runs/20251217_122413_production_scenarios_REQUEST_BASED_50runs/

# 古いログファイルを削除
rm -rf old_scenarios/
rm -f evaluation_runs/execution_log_*.log

# テスト用スクリプトを削除
rm -f evaluation_runs/test_*.sh
rm -f evaluation_runs/run_production_scenarios_*_test.sh

# 一時的な分析スクリプトを削除
rm -f evaluation_runs/analyze_traffic_increase.py
rm -f evaluation_runs/investigate_traffic_calculation.py

# 古い比較結果を削除
rm -rf evaluation_runs/comparison_20251217_140428/

# 一時的なドキュメントを削除
rm -f evaluation_runs/execution_time_estimate.md
rm -f evaluation_runs/compatibility_test_summary.md
rm -f evaluation_runs/test_request_based_summary.md

# その他の一時ファイルを削除
rm -f evaluation_runs/wait_and_analyze_*.sh
rm -f evaluation_runs/run_slow90_scenarios_200runs.sh
```

## 削除後の容量削減見積もり

- **合計削減容量**: 約2.3GB以上
- **主な削減源**: 古い実行結果ディレクトリ（ログファイル）



