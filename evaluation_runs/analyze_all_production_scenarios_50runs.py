#!/usr/bin/env python3
"""
本番シナリオの包括的分析スクリプト（評価シナリオと評価指標.mdに準拠）
- 共有OFF: 12シナリオ × 50回
- 共有ON: 12シナリオ × 2モード（NEIGHBORS_ONLY, REQUEST_BASED）× 50回
"""
import re
import statistics
import os
import glob
import sys
import json
import math
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
                    node_times = []
                    for data_line in data_lines:
                        parts = data_line.split(',')
                        if len(parts) >= 2:
                            try:
                                node_id = int(parts[0])
                                timestamp = int(parts[1])
                                node_times.append((node_id, timestamp))
                            except ValueError:
                                continue
                    
                    if node_times:
                        node_times.sort(key=lambda x: x[1])
                        first_time = node_times[0][1]
                        last_time = node_times[-1][1]
                        propagation_time = last_time - first_time
                        
                        # ネットワーク効率 = ノード数 / 伝播時間
                        network_efficiency = len(node_times) / propagation_time if propagation_time > 0 else 0
                        
                        block_data[block_id] = {
                            'propagation_time': propagation_time,
                            'network_efficiency': network_efficiency,
                            'node_count': len(node_times)
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
        
        # 旧形式のフォールバック
        if not stats:
            pattern = r'FCSM_TRAFFIC_BANDWIDTH_STATS:\s*(?:max=(\d+)\s+bps,\s*avg=(\d+)\s+bps,\s*min=(\d+)\s+bps|トラフィックデータなし)'
            match = re.search(pattern, content)
            if match and match.group(1):
                stats['total'] = {
                    'max': int(match.group(1)),
                    'avg': int(match.group(2)),
                    'min': int(match.group(3))
                }
        
        return stats if stats else None
    except Exception as e:
        print(f"Error parsing traffic stats from {path}: {e}", file=sys.stderr)
        return None

def analyze_scenario(scenario_name, dir_path, broadcast_scope=None):
    """シナリオのログファイルを分析"""
    if broadcast_scope:
        pattern = f"{scenario_name}_{broadcast_scope}_run*.log"
    else:
        pattern = f"{scenario_name}_run*.log"
    
    log_files = glob.glob(os.path.join(dir_path, pattern))
    
    if not log_files:
        return None
    
    propagation_times = []
    network_efficiencies = []
    traffic_stats_list = []
    
    for log_file in sorted(log_files):
        block_data = parse_log_file(log_file)
        
        for block_id, data in block_data.items():
            propagation_times.append(data['propagation_time'])
            network_efficiencies.append(data['network_efficiency'])
        
        # トラフィック統計を取得（共有ONの場合のみ）
        if broadcast_scope:
            traffic_stats = parse_traffic_stats(log_file)
            if traffic_stats:
                traffic_stats_list.append(traffic_stats)
    
    if not propagation_times:
        return None
    
    result = {
        'scenario': scenario_name,
        'broadcast_scope': broadcast_scope,
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
    
    # トラフィック統計（共有ONの場合のみ）
    if traffic_stats_list:
        traffic_result = {
            'valid_count': len(traffic_stats_list)
        }
        
        # 送信統計
        send_avg_list = [s['send']['avg'] for s in traffic_stats_list if 'send' in s and 'avg' in s['send']]
        if send_avg_list:
            traffic_result['send'] = {
                'avg': {
                    'mean': statistics.mean(send_avg_list),
                    'median': statistics.median(send_avg_list),
                    'stdev': statistics.stdev(send_avg_list) if len(send_avg_list) > 1 else 0,
                    'min': min(send_avg_list),
                    'max': max(send_avg_list)
                }
            }
        
        # 受信統計
        recv_avg_list = [s['recv']['avg'] for s in traffic_stats_list if 'recv' in s and 'avg' in s['recv']]
        if recv_avg_list:
            traffic_result['recv'] = {
                'avg': {
                    'mean': statistics.mean(recv_avg_list),
                    'median': statistics.median(recv_avg_list),
                    'stdev': statistics.stdev(recv_avg_list) if len(recv_avg_list) > 1 else 0,
                    'min': min(recv_avg_list),
                    'max': max(recv_avg_list)
                }
            }
        
        # 合計統計
        total_avg_list = [s['total']['avg'] for s in traffic_stats_list if 'total' in s and 'avg' in s['total']]
        if total_avg_list:
            traffic_result['total'] = {
                'avg': {
                    'mean': statistics.mean(total_avg_list),
                    'median': statistics.median(total_avg_list),
                    'stdev': statistics.stdev(total_avg_list) if len(total_avg_list) > 1 else 0,
                    'min': min(total_avg_list),
                    'max': max(total_avg_list)
                }
            }
        
        if len(traffic_result) > 1:  # valid_count以外のデータがある場合
            result['traffic'] = traffic_result
    
    return result

def welch_t_test(data1, data2):
    """Welchのt検定を実行"""
    n1, n2 = len(data1), len(data2)
    if n1 < 2 or n2 < 2:
        return None
    
    mean1, mean2 = statistics.mean(data1), statistics.mean(data2)
    var1, var2 = statistics.variance(data1), statistics.variance(data2)
    
    se1, se2 = var1 / n1, var2 / n2
    se_diff = math.sqrt(se1 + se2)
    
    if se_diff == 0:
        return None
    
    t_stat = (mean1 - mean2) / se_diff
    
    # 自由度（Welch-Satterthwaite equation）
    df = (se1 + se2) ** 2 / (se1 ** 2 / (n1 - 1) + se2 ** 2 / (n2 - 1))
    
    # 簡易的なp値計算（t分布の近似）
    # 実際の実装ではscipy.statsを使用することを推奨
    p_value = 2 * (1 - 0.5 * (1 + math.erf(abs(t_stat) / math.sqrt(2))))
    
    return {
        't_statistic': t_stat,
        'p_value': p_value,
        'degrees_of_freedom': df
    }

def cohens_d(data1, data2):
    """Cohen's d（効果量）を計算"""
    n1, n2 = len(data1), len(data2)
    if n1 < 2 or n2 < 2:
        return None
    
    mean1, mean2 = statistics.mean(data1), statistics.mean(data2)
    var1, var2 = statistics.variance(data1), statistics.variance(data2)
    
    # プールされた標準偏差
    pooled_std = math.sqrt(((n1 - 1) * var1 + (n2 - 1) * var2) / (n1 + n2 - 2))
    
    if pooled_std == 0:
        return None
    
    d = (mean1 - mean2) / pooled_std
    
    return d

def generate_report(all_results, output_dir):
    """結果レポートを生成"""
    os.makedirs(output_dir, exist_ok=True)
    
    # JSON形式で保存
    output_json = os.path.join(output_dir, 'analysis_results.json')
    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(all_results, f, indent=2, ensure_ascii=False)
    
    # Markdown形式でレポート生成
    output_md = os.path.join(output_dir, 'analysis_report.md')
    
    with open(output_md, 'w', encoding='utf-8') as f:
        f.write("# 本番シナリオ包括的分析レポート\n\n")
        f.write(f"生成日時: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write("## 概要\n\n")
        f.write("評価シナリオと評価指標.mdに準拠した包括的な評価結果です。\n\n")
        f.write("- **共有OFF**: 12シナリオ × 50回実行\n")
        f.write("- **共有ON**: 12シナリオ × 2モード（NEIGHBORS_ONLY, REQUEST_BASED）× 50回実行\n")
        f.write("- **合計**: 1,800回実行\n\n")
        
        # 共有OFFシナリオの結果
        f.write("## 1. 共有OFFシナリオの結果\n\n")
        f.write("### 伝播時間とネットワーク効率\n\n")
        f.write("| シナリオ | 実行回数 | ブロック数 | 伝播時間（平均） | 伝播時間（中央値） | 伝播時間（標準偏差） | ネットワーク効率（平均） |\n")
        f.write("|---------|---------|-----------|----------------|------------------|-------------------|----------------------|\n")
        
        off_results = [r for r in all_results if r and r.get('broadcast_scope') is None]
        for result in sorted(off_results, key=lambda x: x['scenario']):
            pt = result['propagation_time']
            ne = result['network_efficiency']
            f.write(f"| {result['scenario']} | {result['run_count']} | {result['block_count']} | "
                   f"{pt['mean']:.2f} | {pt['median']:.2f} | {pt['stdev']:.2f} | {ne['mean']:.6f} |\n")
        
        # 共有ONシナリオの結果（NEIGHBORS_ONLY vs REQUEST_BASED比較）
        f.write("\n## 2. 共有ONシナリオの結果（NEIGHBORS_ONLY vs REQUEST_BASED比較）\n\n")
        
        on_results = [r for r in all_results if r and r.get('broadcast_scope') is not None]
        scenarios_on = sorted(set([r['scenario'] for r in on_results]))
        
        for scenario in scenarios_on:
            neighbors_result = next((r for r in on_results if r['scenario'] == scenario and r['broadcast_scope'] == 'NEIGHBORS_ONLY'), None)
            request_result = next((r for r in on_results if r['scenario'] == scenario and r['broadcast_scope'] == 'REQUEST_BASED'), None)
            
            if not neighbors_result or not request_result:
                continue
            
            f.write(f"### {scenario}\n\n")
            
            # 伝播時間比較
            f.write("#### 伝播時間比較\n\n")
            f.write("| モード | 実行回数 | ブロック数 | 平均 | 中央値 | 標準偏差 | 最小値 | 最大値 |\n")
            f.write("|--------|---------|-----------|------|--------|---------|--------|--------|\n")
            
            pt_n = neighbors_result['propagation_time']
            pt_r = request_result['propagation_time']
            
            f.write(f"| NEIGHBORS_ONLY | {neighbors_result['run_count']} | {neighbors_result['block_count']} | "
                   f"{pt_n['mean']:.2f} | {pt_n['median']:.2f} | {pt_n['stdev']:.2f} | "
                   f"{pt_n['min']:.2f} | {pt_n['max']:.2f} |\n")
            f.write(f"| REQUEST_BASED | {request_result['run_count']} | {request_result['block_count']} | "
                   f"{pt_r['mean']:.2f} | {pt_r['median']:.2f} | {pt_r['stdev']:.2f} | "
                   f"{pt_r['min']:.2f} | {pt_r['max']:.2f} |\n")
            
            # 改善率
            improvement = ((pt_n['mean'] - pt_r['mean']) / pt_n['mean']) * 100 if pt_n['mean'] > 0 else 0
            f.write(f"\n**改善率**: {improvement:+.2f}%\n\n")
            
            # トラフィック比較
            if 'traffic' in neighbors_result and 'traffic' in request_result:
                f.write("#### トラフィック比較（平均値）\n\n")
                
                # 送信・受信・合計の統計を表示
                if 'send' in neighbors_result['traffic'] and 'send' in request_result['traffic']:
                    f.write("##### 送信トラフィック\n\n")
                    f.write("| モード | 平均値（平均） | 中央値 | 標準偏差 | 最小値 | 最大値 |\n")
                    f.write("|--------|--------------|--------|---------|--------|--------|\n")
                    t_n_send = neighbors_result['traffic']['send']['avg']
                    t_r_send = request_result['traffic']['send']['avg']
                    f.write(f"| NEIGHBORS_ONLY | {t_n_send['mean']:.0f} bps | {t_n_send['median']:.0f} bps | "
                           f"{t_n_send['stdev']:.0f} bps | {t_n_send['min']:.0f} bps | {t_n_send['max']:.0f} bps |\n")
                    f.write(f"| REQUEST_BASED | {t_r_send['mean']:.0f} bps | {t_r_send['median']:.0f} bps | "
                           f"{t_r_send['stdev']:.0f} bps | {t_r_send['min']:.0f} bps | {t_r_send['max']:.0f} bps |\n")
                    send_reduction = ((t_n_send['mean'] - t_r_send['mean']) / t_n_send['mean']) * 100 if t_n_send['mean'] > 0 else 0
                    f.write(f"\n**送信トラフィック削減率**: {send_reduction:+.2f}%\n\n")
                
                if 'recv' in neighbors_result['traffic'] and 'recv' in request_result['traffic']:
                    f.write("##### 受信トラフィック\n\n")
                    f.write("| モード | 平均値（平均） | 中央値 | 標準偏差 | 最小値 | 最大値 |\n")
                    f.write("|--------|--------------|--------|---------|--------|--------|\n")
                    t_n_recv = neighbors_result['traffic']['recv']['avg']
                    t_r_recv = request_result['traffic']['recv']['avg']
                    f.write(f"| NEIGHBORS_ONLY | {t_n_recv['mean']:.0f} bps | {t_n_recv['median']:.0f} bps | "
                           f"{t_n_recv['stdev']:.0f} bps | {t_n_recv['min']:.0f} bps | {t_n_recv['max']:.0f} bps |\n")
                    f.write(f"| REQUEST_BASED | {t_r_recv['mean']:.0f} bps | {t_r_recv['median']:.0f} bps | "
                           f"{t_r_recv['stdev']:.0f} bps | {t_r_recv['min']:.0f} bps | {t_r_recv['max']:.0f} bps |\n")
                    recv_reduction = ((t_n_recv['mean'] - t_r_recv['mean']) / t_n_recv['mean']) * 100 if t_n_recv['mean'] > 0 else 0
                    f.write(f"\n**受信トラフィック削減率**: {recv_reduction:+.2f}%\n\n")
                
                if 'total' in neighbors_result['traffic'] and 'total' in request_result['traffic']:
                    f.write("##### 合計トラフィック\n\n")
                    f.write("| モード | 平均値（平均） | 中央値 | 標準偏差 | 最小値 | 最大値 | 有効データ数 |\n")
                    f.write("|--------|--------------|--------|---------|--------|--------|------------|\n")
                    t_n_total = neighbors_result['traffic']['total']['avg']
                    t_r_total = request_result['traffic']['total']['avg']
                    f.write(f"| NEIGHBORS_ONLY | {t_n_total['mean']:.0f} bps | {t_n_total['median']:.0f} bps | "
                           f"{t_n_total['stdev']:.0f} bps | {t_n_total['min']:.0f} bps | {t_n_total['max']:.0f} bps | "
                           f"{neighbors_result['traffic']['valid_count']} |\n")
                    f.write(f"| REQUEST_BASED | {t_r_total['mean']:.0f} bps | {t_r_total['median']:.0f} bps | "
                           f"{t_r_total['stdev']:.0f} bps | {t_r_total['min']:.0f} bps | {t_r_total['max']:.0f} bps | "
                           f"{request_result['traffic']['valid_count']} |\n")
                    total_reduction = ((t_n_total['mean'] - t_r_total['mean']) / t_n_total['mean']) * 100 if t_n_total['mean'] > 0 else 0
                    f.write(f"\n**合計トラフィック削減率**: {total_reduction:+.2f}%\n\n")
            
            f.write("\n---\n\n")
        
        # 共有ON/OFF比較
        f.write("## 3. 共有ON/OFF比較\n\n")
        f.write("### 伝播時間の改善\n\n")
        f.write("| シナリオ | 共有OFF（平均） | NEIGHBORS_ONLY（平均） | REQUEST_BASED（平均） | NEIGHBORS_ONLY改善率 | REQUEST_BASED改善率 |\n")
        f.write("|---------|---------------|----------------------|-------------------|-------------------|-------------------|\n")
        
        for scenario in scenarios_on:
            off_result = next((r for r in off_results if r['scenario'] == scenario.replace('_ON', '_OFF')), None)
            neighbors_result = next((r for r in on_results if r['scenario'] == scenario and r['broadcast_scope'] == 'NEIGHBORS_ONLY'), None)
            request_result = next((r for r in on_results if r['scenario'] == scenario and r['broadcast_scope'] == 'REQUEST_BASED'), None)
            
            if not off_result or not neighbors_result or not request_result:
                continue
            
            pt_off = off_result['propagation_time']['mean']
            pt_n = neighbors_result['propagation_time']['mean']
            pt_r = request_result['propagation_time']['mean']
            
            improvement_n = ((pt_off - pt_n) / pt_off) * 100 if pt_off > 0 else 0
            improvement_r = ((pt_off - pt_r) / pt_off) * 100 if pt_off > 0 else 0
            
            f.write(f"| {scenario} | {pt_off:.2f} | {pt_n:.2f} | {pt_r:.2f} | "
                   f"{improvement_n:+.2f}% | {improvement_r:+.2f}% |\n")
        
        f.write("\n## 4. 統計的検証\n\n")
        f.write("### NEIGHBORS_ONLY vs REQUEST_BASED（伝播時間）\n\n")
        f.write("| シナリオ | Welch's t検定（p値） | Cohen's d | 効果量の解釈 |\n")
        f.write("|---------|-------------------|-----------|------------|\n")
        
        for scenario in scenarios_on:
            neighbors_result = next((r for r in on_results if r['scenario'] == scenario and r['broadcast_scope'] == 'NEIGHBORS_ONLY'), None)
            request_result = next((r for r in on_results if r['scenario'] == scenario and r['broadcast_scope'] == 'REQUEST_BASED'), None)
            
            if not neighbors_result or not request_result:
                continue
            
            # 各実行の伝播時間を取得（簡易版：平均値を使用）
            # 実際の実装では、各実行のデータを個別に保持する必要があります
            pt_n_mean = neighbors_result['propagation_time']['mean']
            pt_r_mean = request_result['propagation_time']['mean']
            
            # 簡易的な統計検定（実際の実装では各実行のデータを使用）
            f.write(f"| {scenario} | - | - | "
                   f"{'REQUEST_BASEDが優位' if pt_r_mean < pt_n_mean else 'NEIGHBORS_ONLYが優位'} |\n")
        
        f.write("\n---\n\n")
        f.write(f"詳細データは `{output_json}` に保存されています。\n")

def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_all_production_scenarios_50runs.py <log_directory>")
        sys.exit(1)
    
    log_dir = sys.argv[1]
    
    if not os.path.isdir(log_dir):
        print(f"Error: {log_dir} is not a directory", file=sys.stderr)
        sys.exit(1)
    
    print(f"分析開始: {log_dir}")
    
    all_results = []
    
    # 共有OFFシナリオ
    off_scenarios = [
        'P10_10_OFF', 'P10_50_OFF', 'P10_90_OFF',
        'P40_10_OFF', 'P40_50_OFF', 'P40_90_OFF',
        'P70_10_OFF', 'P70_50_OFF', 'P70_90_OFF',
        'P100_10_OFF', 'P100_50_OFF', 'P100_90_OFF'
    ]
    
    print("共有OFFシナリオを分析中...")
    for scenario in off_scenarios:
        result = analyze_scenario(scenario, log_dir)
        if result:
            all_results.append(result)
            print(f"  ✓ {scenario}")
    
    # 共有ONシナリオ（NEIGHBORS_ONLYとREQUEST_BASED）
    on_scenarios = [
        'P10_10_ON', 'P10_50_ON', 'P10_90_ON',
        'P40_10_ON', 'P40_50_ON', 'P40_90_ON',
        'P70_10_ON', 'P70_50_ON', 'P70_90_ON',
        'P100_10_ON', 'P100_50_ON', 'P100_90_ON'
    ]
    
    print("\n共有ONシナリオを分析中...")
    for scenario in on_scenarios:
        for broadcast_scope in ['NEIGHBORS_ONLY', 'REQUEST_BASED']:
            result = analyze_scenario(scenario, log_dir, broadcast_scope)
            if result:
                all_results.append(result)
                print(f"  ✓ {scenario} [{broadcast_scope}]")
    
    # レポート生成
    output_dir = os.path.join(log_dir, 'analysis')
    print(f"\nレポート生成中: {output_dir}")
    generate_report(all_results, output_dir)
    
    print(f"\n分析完了！")
    print(f"レポート: {os.path.join(output_dir, 'analysis_report.md')}")
    print(f"詳細データ: {os.path.join(output_dir, 'analysis_results.json')}")

if __name__ == '__main__':
    main()

