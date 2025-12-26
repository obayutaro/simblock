#!/usr/bin/env python3
"""
NEIGHBORS_ONLY vs REQUEST_BASED 比較分析スクリプト
本番シナリオ（共有ONのみ）の2つのモードを比較・評価
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

def parse_traffic_stats(path):
    """ログファイルからトラフィック統計を抽出"""
    try:
        with open(path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        stats = {}
        
        # 送信統計
        send_match = re.search(r'FCSM_TRAFFIC_BANDWIDTH_STATS_SEND:\s*max=(\d+)\s+bps,\s*avg=(\d+)\s+bps,\s*min=(\d+)\s+bps', content)
        if send_match:
            stats['send'] = {
                'max': int(send_match.group(1)),
                'avg': int(send_match.group(2)),
                'min': int(send_match.group(3))
            }
        
        # 受信統計
        recv_match = re.search(r'FCSM_TRAFFIC_BANDWIDTH_STATS_RECV:\s*max=(\d+)\s+bps,\s*avg=(\d+)\s+bps,\s*min=(\d+)\s+bps', content)
        if recv_match:
            stats['recv'] = {
                'max': int(recv_match.group(1)),
                'avg': int(recv_match.group(2)),
                'min': int(recv_match.group(3))
            }
        
        # 合計統計
        total_match = re.search(r'FCSM_TRAFFIC_BANDWIDTH_STATS_TOTAL:\s*max=(\d+)\s+bps,\s*avg=(\d+)\s+bps,\s*min=(\d+)\s+bps', content)
        if total_match:
            stats['total'] = {
                'max': int(total_match.group(1)),
                'avg': int(total_match.group(2)),
                'min': int(total_match.group(3))
            }
        
        return stats if stats else None
    except Exception as e:
        print(f"Error parsing traffic stats from {path}: {e}", file=sys.stderr)
        return None

def analyze_scenario(scenario_name, dir_path):
    """シナリオのログファイルを分析"""
    log_files = glob.glob(os.path.join(dir_path, f"{scenario_name}_run*.log"))
    
    if not log_files:
        return None
    
    propagation_times = []
    network_efficiencies = []
    traffic_stats_list = []
    
    for log_file in sorted(log_files):
        block_data = parse_log_file(log_file)
        traffic_stats = parse_traffic_stats(log_file)
        
        for block_id, data in block_data.items():
            propagation_times.append(data['propagation_time'])
            network_efficiencies.append(data['network_efficiency'])
        
        if traffic_stats:
            traffic_stats_list.append(traffic_stats)
    
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
    
    if traffic_stats_list:
        # トラフィック統計の平均を計算
        send_avg_list = [s['send']['avg'] for s in traffic_stats_list if 'send' in s]
        recv_avg_list = [s['recv']['avg'] for s in traffic_stats_list if 'recv' in s]
        total_avg_list = [s['total']['avg'] for s in traffic_stats_list if 'total' in s]
        
        if send_avg_list:
            result['traffic'] = {
                'send_avg': statistics.mean(send_avg_list),
                'recv_avg': statistics.mean(recv_avg_list) if recv_avg_list else None,
                'total_avg': statistics.mean(total_avg_list) if total_avg_list else None
            }
    
    return result

def compare_modes(neighbors_dir, request_dir, output_dir):
    """2つのモードを比較"""
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
            
            # トラフィック削減率
            if 'traffic' in neighbors_result and 'traffic' in request_result:
                if neighbors_result['traffic'].get('total_avg', 0) > 0:
                    reduction = ((neighbors_result['traffic']['total_avg'] - 
                                request_result['traffic']['total_avg']) / 
                               neighbors_result['traffic']['total_avg']) * 100
                    comparison['improvement']['traffic_reduction'] = reduction
            
            results[scenario] = comparison
    
    return results

def generate_report(results, output_dir):
    """比較レポートを生成"""
    report_path = os.path.join(output_dir, 'comparison_report.md')
    
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write("# NEIGHBORS_ONLY vs REQUEST_BASED 比較評価レポート\n\n")
        f.write(f"生成日時: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        
        f.write("## 概要\n\n")
        f.write("本レポートは、本番シナリオ（共有ONのみ）において、")
        f.write("NEIGHBORS_ONLYモードとREQUEST_BASEDモードの性能を比較したものです。\n\n")
        
        f.write("## 評価指標\n\n")
        f.write("1. **ブロック伝播時間**: ブロックが最初のノードから最後のノードまで伝播するのに要する時間\n")
        f.write("2. **ネットワーク効率**: ブロックがネットワークを拡散する際の平均進行速度\n")
        f.write("3. **トラフィック帯域使用量**: 未来コンテキスト共有によって発生するネットワークトラフィック\n\n")
        
        f.write("## 比較結果\n\n")
        f.write("| シナリオ | モード | 伝播時間平均(ms) | ネットワーク効率平均(ms) | トラフィック平均(bps) |\n")
        f.write("|---------|--------|-----------------|------------------------|---------------------|\n")
        
        for scenario, comparison in sorted(results.items()):
            neighbors = comparison['neighbors_only']
            request = comparison['request_based']
            
            neighbors_traffic = neighbors.get('traffic', {}).get('total_avg', 'N/A')
            request_traffic = request.get('traffic', {}).get('total_avg', 'N/A')
            
            if isinstance(neighbors_traffic, (int, float)):
                neighbors_traffic = f"{neighbors_traffic:,.0f}"
            if isinstance(request_traffic, (int, float)):
                request_traffic = f"{request_traffic:,.0f}"
            
            f.write(f"| {scenario} | NEIGHBORS_ONLY | {neighbors['propagation_time']['mean']:.2f} | {neighbors['network_efficiency']['mean']:.2f} | {neighbors_traffic} |\n")
            f.write(f"| {scenario} | REQUEST_BASED | {request['propagation_time']['mean']:.2f} | {request['network_efficiency']['mean']:.2f} | {request_traffic} |\n")
            
            if 'improvement' in comparison and comparison['improvement']:
                improvements = comparison['improvement']
                if 'propagation_time' in improvements:
                    f.write(f"| {scenario} | **改善率** | **{improvements['propagation_time']:.2f}%** | - | - |\n")
                if 'traffic_reduction' in improvements:
                    f.write(f"| {scenario} | **トラフィック削減率** | - | - | **{improvements['traffic_reduction']:.2f}%** |\n")
            f.write("| | | | | |\n")
        
        f.write("\n## 詳細分析\n\n")
        for scenario, comparison in sorted(results.items()):
            f.write(f"### {scenario}\n\n")
            
            neighbors = comparison['neighbors_only']
            request = comparison['request_based']
            
            f.write("#### NEIGHBORS_ONLYモード\n")
            f.write(f"- 伝播時間: {neighbors['propagation_time']['mean']:.2f}ms (中央値: {neighbors['propagation_time']['median']:.2f}ms)\n")
            f.write(f"- ネットワーク効率: {neighbors['network_efficiency']['mean']:.2f}ms\n")
            if 'traffic' in neighbors:
                f.write(f"- トラフィック: {neighbors['traffic'].get('total_avg', 'N/A'):,.0f} bps\n")
            
            f.write("\n#### REQUEST_BASEDモード\n")
            f.write(f"- 伝播時間: {request['propagation_time']['mean']:.2f}ms (中央値: {request['propagation_time']['median']:.2f}ms)\n")
            f.write(f"- ネットワーク効率: {request['network_efficiency']['mean']:.2f}ms\n")
            if 'traffic' in request:
                f.write(f"- トラフィック: {request['traffic'].get('total_avg', 'N/A'):,.0f} bps\n")
            
            if 'improvement' in comparison and comparison['improvement']:
                f.write("\n#### 改善率\n")
                improvements = comparison['improvement']
                if 'propagation_time' in improvements:
                    f.write(f"- 伝播時間改善率: {improvements['propagation_time']:.2f}%\n")
                if 'network_efficiency' in improvements:
                    f.write(f"- ネットワーク効率改善率: {improvements['network_efficiency']:.2f}%\n")
                if 'traffic_reduction' in improvements:
                    f.write(f"- トラフィック削減率: {improvements['traffic_reduction']:.2f}%\n")
            
            f.write("\n")
        
        f.write("## 結論\n\n")
        f.write("REQUEST_BASEDモードは、NEIGHBORS_ONLYモードと比較して、")
        f.write("トラフィックを大幅に削減しながら、同等またはそれ以上の性能を維持できる可能性があります。\n")

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python3 analyze_NEIGHBORS_ONLY_vs_REQUEST_BASED.py <neighbors_dir> <request_dir> [output_dir]")
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

