#!/usr/bin/env python3
"""
本番シナリオの詳細分析スクリプト（全24シナリオ、50回実行）
"""
import re
import statistics
import os
import glob
import sys
from collections import defaultdict
from datetime import datetime

def parse_log_file(path):
    """ログファイルから各ブロックの伝播情報を抽出"""
    try:
        with open(path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        block_data = {}
        block_pattern = r'simblock\.block\.ProofOfWorkBlock@[a-f0-9]+:(\d+)'
        processed_blocks = set()  # 処理済みブロックIDを記録
        
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            match = re.match(block_pattern, line)
            if match:
                block_id = match.group(1)
                
                # 同じブロックIDが既に処理されている場合はスキップ（最初の出現のみを処理）
                if block_id in processed_blocks:
                    # 次のブロックID行までスキップ
                    i += 1
                    while i < len(lines):
                        if re.match(block_pattern, lines[i].strip()):
                            i -= 1  # 次のブロックID行に戻る
                            break
                        i += 1
                    i += 1
                    continue
                
                processed_blocks.add(block_id)
                
                # 次のブロックID行またはファイル終端までデータを収集
                data_lines = []
                j = i + 1
                while j < len(lines):
                    next_line = lines[j].strip()
                    if re.match(block_pattern, next_line):
                        break
                    if next_line and not next_line.startswith('Successfully'):
                        data_lines.append(next_line)
                    j += 1
                
                # ノードIDとタイムスタンプのペアを抽出
                pairs = []
                for data_line in data_lines:
                    pair_match = re.match(r'^(\d+),(\d+)$', data_line)
                    if pair_match:
                        pairs.append((int(pair_match.group(1)), int(pair_match.group(2))))
                
                if len(pairs) >= 2:
                    # タイムスタンプでソート
                    pairs.sort(key=lambda x: x[1])
                    first_time = pairs[0][1]
                    last_time = pairs[-1][1]
                    propagation_time = last_time - first_time
                    
                    block_data[block_id] = {
                        'propagation_time': propagation_time,
                        'first_time': first_time,
                        'last_time': last_time,
                        'node_count': len(pairs)
                    }
                
                i = j  # 次のブロックID行に移動
            else:
                i += 1
        
        return block_data
    except Exception as e:
        print(f"Error parsing {path}: {e}")
        import traceback
        traceback.print_exc()
        return {}

def calculate_statistics(values):
    """統計値を計算"""
    if not values:
        return {}
    n = len(values)
    sorted_vals = sorted(values)
    mean = statistics.mean(values)
    median = statistics.median(values)
    stdev = statistics.stdev(values) if n > 1 else 0
    
    return {
        'n': n,
        'mean': mean,
        'median': median,
        'stdev': stdev,
        'min': min(values),
        'max': max(values)
    }

def welch_ttest(data1, data2):
    """Welch's t-testを実行（簡易版）"""
    n1 = len(data1)
    n2 = len(data2)
    
    if n1 < 2 or n2 < 2:
        return None
    
    mean1 = statistics.mean(data1)
    mean2 = statistics.mean(data2)
    sd1 = statistics.stdev(data1)
    sd2 = statistics.stdev(data2)
    
    se_diff = ((sd1**2 / n1) + (sd2**2 / n2)) ** 0.5
    t_stat = (mean1 - mean2) / se_diff if se_diff > 0 else 0
    
    # 簡易的な有意性判定（|t| > 1.96でp < 0.05とみなす）
    significant = abs(t_stat) > 1.96
    
    pooled_sd = ((sd1**2 * (n1 - 1) + sd2**2 * (n2 - 1)) / (n1 + n2 - 2)) ** 0.5
    cohens_d = (mean1 - mean2) / pooled_sd if pooled_sd > 0 else 0
    
    return {
        'mean_diff': mean1 - mean2,
        't_stat': t_stat,
        'significant': significant,
        'cohens_d': cohens_d
    }

def main():
    if len(sys.argv) < 2:
        # 最新の50回実行ディレクトリを自動検出
        dirs = sorted([d for d in os.listdir("evaluation_runs") 
                       if os.path.isdir(os.path.join("evaluation_runs", d)) 
                       and "production_scenarios_50runs" in d], reverse=True)
        if not dirs:
            print("Error: No evaluation directory found")
            print("Usage: python3 analyze_production_scenarios_50runs.py <evaluation_directory>")
            sys.exit(1)
        eval_dir = os.path.join("evaluation_runs", dirs[0])
    else:
        eval_dir = sys.argv[1]
    
    if not os.path.exists(eval_dir):
        print(f"Error: Directory '{eval_dir}' not found")
        sys.exit(1)
    
    # 本番シナリオ名のリスト（全24シナリオ）
    production_scenarios = [
        # 共有OFF
        "P10_10_OFF", "P10_50_OFF", "P10_90_OFF",
        "P40_10_OFF", "P40_50_OFF", "P40_90_OFF",
        "P70_10_OFF", "P70_50_OFF", "P70_90_OFF",
        "P100_10_OFF", "P100_50_OFF", "P100_90_OFF",
        # 共有ON
        "P10_10_ON", "P10_50_ON", "P10_90_ON",
        "P40_10_ON", "P40_50_ON", "P40_90_ON",
        "P70_10_ON", "P70_50_ON", "P70_90_ON",
        "P100_10_ON", "P100_50_ON", "P100_90_ON",
    ]
    
    all_scenario_data = {}
    
    print("=" * 120)
    print("本番シナリオ詳細分析（全24シナリオ、50回実行）")
    print("=" * 120)
    print(f"分析対象ディレクトリ: {eval_dir}")
    print(f"分析開始時刻: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print()
    
    # データ収集
    for sc in production_scenarios:
        log_files = sorted(glob.glob(f"{eval_dir}/{sc}_run*.log"))
        
        if not log_files:
            print(f"Warning: No log files found for {sc}")
            continue
        
        print(f"Loading {sc}...", end='\r')
        
        # 全実行の伝播時間を収集
        all_propagation_times = []
        
        for log_file in log_files:
            block_data = parse_log_file(log_file)
            for bid, data in block_data.items():
                # ジェネシスブロック（伝播時間0）を除外
                if data['propagation_time'] > 0:
                    all_propagation_times.append(data['propagation_time'])
        
        if all_propagation_times:
            stats = calculate_statistics(all_propagation_times)
            all_scenario_data[sc] = stats
    
    print()
    print()
    
    # 1. 全シナリオ結果表（共有OFF）
    print("=" * 120)
    print("1. 共有OFFシナリオの伝播時間")
    print("=" * 120)
    print()
    
    print(f"{'シナリオ':<15} {'適用率':<10} {'低速ノード':<12} {'平均(ms)':<15} {'中央値(ms)':<15} {'標準偏差(ms)':<18} {'最小(ms)':<12} {'最大(ms)':<12} {'サンプル数':<12}")
    print("-" * 120)
    
    for sc in production_scenarios:
        if sc.endswith("_OFF") and sc in all_scenario_data:
            data = all_scenario_data[sc]
            parts = sc.split('_')
            app_ratio = parts[0].replace('P', '') + '%'
            slow_ratio = parts[1] + '%'
            
            print(f"{sc:<15} {app_ratio:<10} {slow_ratio:<12} {data['mean']:>12.2f} {data['median']:>12.2f} {data['stdev']:>15.2f} {data['min']:>10} {data['max']:>10} {data['n']:>10}")
    print()
    
    # 2. 全シナリオ結果表（共有ON）
    print("=" * 120)
    print("2. 共有ONシナリオの伝播時間")
    print("=" * 120)
    print()
    
    print(f"{'シナリオ':<15} {'適用率':<10} {'低速ノード':<12} {'平均(ms)':<15} {'中央値(ms)':<15} {'標準偏差(ms)':<18} {'最小(ms)':<12} {'最大(ms)':<12} {'サンプル数':<12}")
    print("-" * 120)
    
    for sc in production_scenarios:
        if sc.endswith("_ON") and sc in all_scenario_data:
            data = all_scenario_data[sc]
            parts = sc.split('_')
            app_ratio = parts[0].replace('P', '') + '%'
            slow_ratio = parts[1] + '%'
            
            print(f"{sc:<15} {app_ratio:<10} {slow_ratio:<12} {data['mean']:>12.2f} {data['median']:>12.2f} {data['stdev']:>15.2f} {data['min']:>10} {data['max']:>10} {data['n']:>10}")
    print()
    
    # 3. 共有ON/OFF比較表（統計検定付き）
    print("=" * 120)
    print("3. 共有ON/OFF比較（平均伝播時間、統計検定付き）")
    print("=" * 120)
    print()
    
    cohens_d_label = "Cohen's d"
    print(f"{'適用率':<10} {'低速ノード':<12} {'共有OFF(ms)':<18} {'共有ON(ms)':<18} {'差分(ms)':<18} {'改善率(%)':<15} {cohens_d_label:<15} {'有意性':<10}")
    print("-" * 120)
    
    for app_ratio in ["10", "40", "70", "100"]:
        for slow_ratio in ["10", "50", "90"]:
            off_sc = f"P{app_ratio}_{slow_ratio}_OFF"
            on_sc = f"P{app_ratio}_{slow_ratio}_ON"
            
            if off_sc in all_scenario_data and on_sc in all_scenario_data:
                off_data = all_scenario_data[off_sc]
                on_data = all_scenario_data[on_sc]
                
                # 各実行の平均伝播時間を計算（統計検定用）
                off_means = []
                on_means = []
                
                # ログファイルから各実行の平均を計算
                off_logs = sorted(glob.glob(f"{eval_dir}/{off_sc}_run*.log"))
                on_logs = sorted(glob.glob(f"{eval_dir}/{on_sc}_run*.log"))
                
                for log_file in off_logs:
                    block_data = parse_log_file(log_file)
                    run_times = [data['propagation_time'] for bid, data in block_data.items() if data['propagation_time'] > 0]
                    if run_times:
                        off_means.append(statistics.mean(run_times))
                
                for log_file in on_logs:
                    block_data = parse_log_file(log_file)
                    run_times = [data['propagation_time'] for bid, data in block_data.items() if data['propagation_time'] > 0]
                    if run_times:
                        on_means.append(statistics.mean(run_times))
                
                off_mean = off_data['mean']
                on_mean = on_data['mean']
                diff = off_mean - on_mean
                improvement = (diff / off_mean * 100) if off_mean > 0 else 0
                
                # 統計検定
                test_result = welch_ttest(off_means, on_means) if off_means and on_means else None
                cohens_d = test_result['cohens_d'] if test_result else 0
                significant = test_result['significant'] if test_result else False
                sig_mark = "***" if significant else ""
                
                print(f"{app_ratio + '%':<10} {slow_ratio + '%':<12} {off_mean:>15.2f} {on_mean:>15.2f} {diff:>15.2f} {improvement:>12.2f} {cohens_d:>12.3f} {sig_mark:<10}")
    print()
    print("***: p < 0.05 (統計的に有意)")
    print()
    
    # 4. 適用率別の平均伝播時間（低速ノード割合別）
    print("=" * 120)
    print("4. 適用率別の平均伝播時間（低速ノード割合別）")
    print("=" * 120)
    print()
    
    for slow_ratio in ["10%", "50%", "90%"]:
        print(f"【低速ノード割合: {slow_ratio}】")
        print(f"{'適用率':<10} {'共有OFF(ms)':<18} {'共有ON(ms)':<18} {'改善率(%)':<15}")
        print("-" * 120)
        
        for app_ratio in ["10", "40", "70", "100"]:
            off_sc = f"P{app_ratio}_{slow_ratio.replace('%', '')}_OFF"
            on_sc = f"P{app_ratio}_{slow_ratio.replace('%', '')}_ON"
            
            if off_sc in all_scenario_data and on_sc in all_scenario_data:
                off_mean = all_scenario_data[off_sc]['mean']
                on_mean = all_scenario_data[on_sc]['mean']
                improvement = ((off_mean - on_mean) / off_mean * 100) if off_mean > 0 else 0
                
                print(f"{app_ratio + '%':<10} {off_mean:>15.2f} {on_mean:>15.2f} {improvement:>12.2f}")
        print()
    
    # 5. 適用率間の比較（共有OFF）
    print("=" * 120)
    print("5. 適用率間の比較（共有OFF）")
    print("=" * 120)
    print()
    
    for slow_ratio in ["10%", "50%", "90%"]:
        print(f"【低速ノード割合: {slow_ratio}】")
        print(f"{'適用率':<10} {'平均伝播時間(ms)':<20} {'標準偏差(ms)':<18}")
        print("-" * 120)
        
        for app_ratio in ["10", "40", "70", "100"]:
            sc = f"P{app_ratio}_{slow_ratio.replace('%', '')}_OFF"
            if sc in all_scenario_data:
                data = all_scenario_data[sc]
                print(f"{app_ratio + '%':<10} {data['mean']:>17.2f} {data['stdev']:>15.2f}")
        print()
    
    # 6. 適用率間の比較（共有ON）
    print("=" * 120)
    print("6. 適用率間の比較（共有ON）")
    print("=" * 120)
    print()
    
    for slow_ratio in ["10%", "50%", "90%"]:
        print(f"【低速ノード割合: {slow_ratio}】")
        print(f"{'適用率':<10} {'平均伝播時間(ms)':<20} {'標準偏差(ms)':<18}")
        print("-" * 120)
        
        for app_ratio in ["10", "40", "70", "100"]:
            sc = f"P{app_ratio}_{slow_ratio.replace('%', '')}_ON"
            if sc in all_scenario_data:
                data = all_scenario_data[sc]
                print(f"{app_ratio + '%':<10} {data['mean']:>17.2f} {data['stdev']:>15.2f}")
        print()
    
    print("=" * 120)
    print("分析完了")
    print("=" * 120)
    print(f"分析完了時刻: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print()

if __name__ == "__main__":
    main()

