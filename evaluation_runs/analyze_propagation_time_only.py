#!/usr/bin/env python3
"""
NEIGHBORS_ONLY vs REQUEST_BASED 比較分析スクリプト（伝播時間のみ）
本番シナリオ（共有ONのみ、FORERUNNER_SHARE_ONLY_ON_TX=true）の2つのモードを比較・評価
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
    """シナリオのログファイルを分析（伝播時間のみ）"""
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

def compare_modes(neighbors_dir, request_dir, output_dir):
    """2つのモードを比較（伝播時間のみ）"""
    scenarios = [
        'P10_10_ON', 'P10_50_ON', 'P10_90_ON',
        'P40_10_ON', 'P40_50_ON', 'P40_90_ON',
        'P70_10_ON', 'P70_50_ON', 'P70_90_ON',
        'P100_10_ON', 'P100_50_ON', 'P100_90_ON'
    ]
    
    results = {}
    
    for scenario in scenarios:
        neighbors_result = analyze_scenario(scenario, neighbors_dir)
        request_result = analyze_scenario(scenario, request_dir)
        
        if neighbors_result and request_result:
            comparison = {
                'scenario': scenario,
                'neighbors_only': neighbors_result,
                'request_based': request_result,
                'improvement': {}
            }
            
            # 伝播時間の改善率
            if neighbors_result['propagation_time']['mean'] > 0:
                improvement = ((neighbors_result['propagation_time']['mean'] - 
                              request_result['propagation_time']['mean']) / 
                             neighbors_result['propagation_time']['mean']) * 100
                comparison['improvement']['propagation_time'] = improvement
            
            # ネットワーク効率の改善率
            if neighbors_result['network_efficiency']['mean'] > 0:
                improvement = ((neighbors_result['network_efficiency']['mean'] - 
                              request_result['network_efficiency']['mean']) / 
                             neighbors_result['network_efficiency']['mean']) * 100
                comparison['improvement']['network_efficiency'] = improvement
            
            results[scenario] = comparison
    
    return results

def generate_report(results, output_dir):
    """比較レポートを生成（伝播時間のみ）"""
    report_path = os.path.join(output_dir, 'comparison_report.md')
    
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write("# NEIGHBORS_ONLY vs REQUEST_BASED 比較評価レポート（伝播時間のみ）\n\n")
        f.write(f"生成日時: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        
        f.write("## 概要\n\n")
        f.write("本レポートは、本番シナリオ（共有ONのみ、FORERUNNER_SHARE_ONLY_ON_TX=true）において、")
        f.write("NEIGHBORS_ONLYモードとREQUEST_BASEDモードの伝播時間を比較したものです。\n\n")
        
        f.write("## 評価指標\n\n")
        f.write("1. **ブロック伝播時間**: ブロックが最初のノードから最後のノードまで伝播するのに要する時間\n")
        f.write("2. **ネットワーク効率**: ブロックがネットワークを拡散する際の平均進行速度\n\n")
        
        f.write("## 比較結果\n\n")
        f.write("| シナリオ | モード | 伝播時間平均(ms) | 伝播時間中央値(ms) | 伝播時間標準偏差(ms) | ネットワーク効率平均(ms) | 改善率(%) |\n")
        f.write("|---------|--------|-----------------|-------------------|-------------------|------------------------|----------|\n")
        
        for scenario, comparison in sorted(results.items()):
            neighbors = comparison['neighbors_only']
            request = comparison['request_based']
            improvements = comparison.get('improvement', {})
            improvement_rate = improvements.get('propagation_time', 0)
            
            f.write(f"| {scenario} | NEIGHBORS_ONLY | {neighbors['propagation_time']['mean']:.2f} | {neighbors['propagation_time']['median']:.2f} | {neighbors['propagation_time']['stdev']:.2f} | {neighbors['network_efficiency']['mean']:.2f} | - |\n")
            f.write(f"| {scenario} | REQUEST_BASED | {request['propagation_time']['mean']:.2f} | {request['propagation_time']['median']:.2f} | {request['propagation_time']['stdev']:.2f} | {request['network_efficiency']['mean']:.2f} | **{improvement_rate:.2f}%** |\n")
            f.write("| | | | | | | |\n")
        
        f.write("\n## 詳細分析\n\n")
        for scenario, comparison in sorted(results.items()):
            f.write(f"### {scenario}\n\n")
            
            neighbors = comparison['neighbors_only']
            request = comparison['request_based']
            improvements = comparison.get('improvement', {})
            
            f.write("#### NEIGHBORS_ONLYモード\n")
            f.write(f"- 伝播時間: {neighbors['propagation_time']['mean']:.2f}ms (中央値: {neighbors['propagation_time']['median']:.2f}ms)\n")
            f.write(f"- 伝播時間標準偏差: {neighbors['propagation_time']['stdev']:.2f}ms\n")
            f.write(f"- 伝播時間範囲: {neighbors['propagation_time']['min']:.0f}ms ～ {neighbors['propagation_time']['max']:.0f}ms\n")
            f.write(f"- ネットワーク効率: {neighbors['network_efficiency']['mean']:.2f}ms\n")
            f.write(f"- 実行回数: {neighbors['run_count']}回\n")
            f.write(f"- ブロック数: {neighbors['block_count']}ブロック\n")
            
            f.write("\n#### REQUEST_BASEDモード\n")
            f.write(f"- 伝播時間: {request['propagation_time']['mean']:.2f}ms (中央値: {request['propagation_time']['median']:.2f}ms)\n")
            f.write(f"- 伝播時間標準偏差: {request['propagation_time']['stdev']:.2f}ms\n")
            f.write(f"- 伝播時間範囲: {request['propagation_time']['min']:.0f}ms ～ {request['propagation_time']['max']:.0f}ms\n")
            f.write(f"- ネットワーク効率: {request['network_efficiency']['mean']:.2f}ms\n")
            f.write(f"- 実行回数: {request['run_count']}回\n")
            f.write(f"- ブロック数: {request['block_count']}ブロック\n")
            
            if improvements:
                f.write("\n#### 改善率\n")
                if 'propagation_time' in improvements:
                    f.write(f"- 伝播時間改善率: {improvements['propagation_time']:.2f}%\n")
                if 'network_efficiency' in improvements:
                    f.write(f"- ネットワーク効率改善率: {improvements['network_efficiency']:.2f}%\n")
            
            f.write("\n")
        
        f.write("## 結論\n\n")
        f.write("REQUEST_BASEDモードは、NEIGHBORS_ONLYモードと比較して、")
        f.write("伝播時間において同等またはそれ以上の性能を維持できる可能性があります。\n")

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python3 analyze_propagation_time_only.py <neighbors_dir> <request_dir> [output_dir]")
        sys.exit(1)
    
    neighbors_dir = sys.argv[1]
    request_dir = sys.argv[2]
    output_dir = sys.argv[3] if len(sys.argv) > 3 else os.path.dirname(neighbors_dir)
    
    if not os.path.exists(neighbors_dir):
        print(f"Error: {neighbors_dir} does not exist")
        sys.exit(1)
    
    if not os.path.exists(request_dir):
        print(f"Error: {request_dir} does not exist")
        sys.exit(1)
    
    # 出力ディレクトリを作成
    os.makedirs(output_dir, exist_ok=True)
    
    print(f"Analyzing NEIGHBORS_ONLY results from: {neighbors_dir}")
    print(f"Analyzing REQUEST_BASED results from: {request_dir}")
    print(f"Output directory: {output_dir}")
    
    results = compare_modes(neighbors_dir, request_dir, output_dir)
    
    # JSON形式で保存
    json_path = os.path.join(output_dir, 'comparison_results.json')
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    
    # レポート生成
    generate_report(results, output_dir)
    
    print(f"\nAnalysis complete!")
    print(f"Results saved to: {json_path}")
    print(f"Report saved to: {os.path.join(output_dir, 'comparison_report.md')}")



