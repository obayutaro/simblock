#!/usr/bin/env python3
"""
本番シナリオ（共有OFFのみ、1回実行）の結果を分析するスクリプト
"""
import re
import statistics
import os
import glob
import sys
import json
from collections import defaultdict
from datetime import datetime

def parse_log_file(path):
    """ログファイルから各ブロックの伝播情報を抽出"""
    try:
        with open(path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        block_data = {}
        block_pattern = r'simblock\.block\.ProofOfWorkBlock@[a-f0-9]+:(\d+)'
        processed_blocks = set()
        
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            match = re.match(block_pattern, line)
            if match:
                block_id = match.group(1)
                
                if block_id in processed_blocks:
                    i += 1
                    while i < len(lines):
                        if re.match(block_pattern, lines[i].strip()):
                            i -= 1
                            break
                        i += 1
                    i += 1
                    continue
                
                processed_blocks.add(block_id)
                
                data_lines = []
                j = i + 1
                while j < len(lines):
                    next_line = lines[j].strip()
                    if re.match(block_pattern, next_line):
                        break
                    if next_line and ',' in next_line:
                        data_lines.append(next_line)
                    j += 1
                
                if data_lines:
                    timestamps = []
                    for data_line in data_lines:
                        parts = data_line.split(',')
                        if len(parts) >= 2:
                            try:
                                timestamp = int(parts[1])
                                timestamps.append(timestamp)
                            except ValueError:
                                continue
                    
                    if timestamps:
                        timestamps.sort()
                        propagation_time = timestamps[-1] - timestamps[0] if len(timestamps) > 1 else 0
                        network_efficiency = statistics.mean([timestamps[i] - timestamps[i-1] for i in range(1, len(timestamps))]) if len(timestamps) > 1 else 0
                        block_data[block_id] = {
                            'propagation_time': propagation_time,
                            'network_efficiency': network_efficiency,
                            'node_count': len(timestamps),
                            'timestamps': timestamps
                        }
            
            i += 1
        
        return block_data
    except Exception as e:
        print(f"Error parsing {path}: {e}", file=sys.stderr)
        return {}

def analyze_scenario(scenario_name, dir_path):
    """シナリオのログファイルを分析"""
    log_files = glob.glob(os.path.join(dir_path, f"{scenario_name}_run*.log"))
    
    if not log_files:
        return None
    
    propagation_times = []
    network_efficiencies = []
    
    for log_file in sorted(log_files):
        block_data = parse_log_file(log_file)
        
        for block_id, data in block_data.items():
            propagation_times.append(data['propagation_time'])
            network_efficiencies.append(data['network_efficiency'])
    
    if not propagation_times:
        return None
    
    result = {
        'scenario': scenario_name,
        'run_count': len(log_files),
        'block_count': len(propagation_times),
        'propagation_time': {
            'mean': statistics.mean(propagation_times),
            'median': statistics.median(propagation_times),
            'stdev': statistics.stdev(propagation_times) if len(propagation_times) > 1 else 0,
            'min': min(propagation_times),
            'max': max(propagation_times)
        },
        'network_efficiency': {
            'mean': statistics.mean(network_efficiencies),
            'median': statistics.median(network_efficiencies),
            'stdev': statistics.stdev(network_efficiencies) if len(network_efficiencies) > 1 else 0,
            'min': min(network_efficiencies),
            'max': max(network_efficiencies)
        }
    }
    
    return result

def generate_report(results, output_dir):
    """結果レポートを生成"""
    report_path = os.path.join(output_dir, 'results_report.md')
    
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write("# 本番シナリオ（共有OFFのみ、1回実行）結果レポート\n\n")
        f.write(f"生成日時: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        
        f.write("## 概要\n\n")
        f.write("本レポートは、本番シナリオ（共有OFFのみ）を各シナリオ1回ずつ実行した結果です。\n\n")
        
        f.write("## 評価指標\n\n")
        f.write("1. **ブロック伝播時間**: ブロックが最初のノードから最後のノードまで伝播するのに要する時間\n")
        f.write("2. **ネットワーク効率**: ブロックがネットワークを拡散する際の平均進行速度\n\n")
        
        f.write("## 結果\n\n")
        f.write("| シナリオ | 伝播時間平均(ms) | 伝播時間中央値(ms) | 伝播時間標準偏差(ms) | 伝播時間範囲(ms) | ネットワーク効率平均(ms) | ブロック数 |\n")
        f.write("|---------|-----------------|-------------------|-------------------|----------------|------------------------|----------|\n")
        
        for scenario, result in sorted(results.items()):
            f.write(f"| {scenario} | {result['propagation_time']['mean']:.2f} | {result['propagation_time']['median']:.2f} | {result['propagation_time']['stdev']:.2f} | {result['propagation_time']['min']:.0f}～{result['propagation_time']['max']:.0f} | {result['network_efficiency']['mean']:.2f} | {result['block_count']} |\n")
        
        f.write("\n## 詳細分析\n\n")
        for scenario, result in sorted(results.items()):
            f.write(f"### {scenario}\n\n")
            f.write(f"- 伝播時間: {result['propagation_time']['mean']:.2f}ms (中央値: {result['propagation_time']['median']:.2f}ms)\n")
            f.write(f"- 伝播時間標準偏差: {result['propagation_time']['stdev']:.2f}ms\n")
            f.write(f"- 伝播時間範囲: {result['propagation_time']['min']:.0f}ms ～ {result['propagation_time']['max']:.0f}ms\n")
            f.write(f"- ネットワーク効率: {result['network_efficiency']['mean']:.2f}ms\n")
            f.write(f"- ブロック数: {result['block_count']}ブロック\n")
            f.write("\n")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 analyze_production_scenarios_OFF_1run.py <log_dir> [output_dir]")
        sys.exit(1)
    
    log_dir = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else log_dir
    
    if not os.path.exists(log_dir):
        print(f"Error: {log_dir} does not exist")
        sys.exit(1)
    
    # 出力ディレクトリを作成
    os.makedirs(output_dir, exist_ok=True)
    
    scenarios = [
        'P10_10_OFF', 'P10_50_OFF', 'P10_90_OFF',
        'P40_10_OFF', 'P40_50_OFF', 'P40_90_OFF',
        'P70_10_OFF', 'P70_50_OFF', 'P70_90_OFF',
        'P100_10_OFF', 'P100_50_OFF', 'P100_90_OFF'
    ]
    
    results = {}
    
    for scenario in scenarios:
        result = analyze_scenario(scenario, log_dir)
        if result:
            results[scenario] = result
    
    # JSON形式で保存
    json_path = os.path.join(output_dir, 'results.json')
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    
    # レポート生成
    generate_report(results, output_dir)
    
    print(f"\nAnalysis complete!")
    print(f"Results saved to: {json_path}")
    print(f"Report saved to: {os.path.join(output_dir, 'results_report.md')}")



