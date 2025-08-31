#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SimBlock Forerunner シナリオ結果ログファイルのみを使用した分析スクリプト
各シナリオの結果からブロック伝播時間とネットワーク効率を分析
"""

import re
import os
from collections import defaultdict

def extract_node_delays(log_file):
    """ログファイルからノードの遅延時間を抽出"""
    delays = {
        'FORERUNNER_HIGH': [],
        'FORERUNNER_LOW': [],
        'NON_FORERUNNER_HIGH': [],
        'NON_FORERUNNER_LOW': []
    }
    
    try:
        with open(log_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # ノード初期化時の遅延時間を抽出
        pattern = r'Node \d+ initialized as (\w+) \(Performance: (\w+), Delay: (\d+)ms'
        matches = re.findall(pattern, content)
        
        for node_type, performance, delay in matches:
            if node_type in delays:
                delays[node_type].append(int(delay))
                
    except Exception as e:
        print(f"エラー: {log_file} の読み込みに失敗: {e}")
        
    return delays

def extract_propagation_stats(log_file):
    """ブロック伝播統計を抽出"""
    stats = {
        'total_blocks': 0,
        'propagation_delays': [],
        'avg_propagation_time': 0,
        'propagation_speed': 0
    }
    
    try:
        with open(log_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # ブロック伝播開始の行を検索
        pattern = r'Block \d+ \(index: \d+\) propagation started to node \d+ with delay: (\d+)ms'
        matches = re.findall(pattern, content)
        
        if matches:
            stats['total_blocks'] = len(matches)
            stats['propagation_delays'] = [int(delay) for delay in matches]
            stats['avg_propagation_time'] = sum(stats['propagation_delays']) / len(stats['propagation_delays'])
            
            # 伝播速度の計算（ブロック数/平均遅延時間）
            if stats['avg_propagation_time'] > 0:
                stats['propagation_speed'] = stats['total_blocks'] / stats['avg_propagation_time']
            else:
                stats['propagation_speed'] = float('inf')  # 遅延が0の場合は無限大
            
    except Exception as e:
        print(f"エラー: {log_file} の伝播統計抽出に失敗: {e}")
        
    return stats

def calculate_network_efficiency(delays, node_counts):
    """ネットワーク効率を計算（ノード構成を考慮）"""
    if not delays or not node_counts:
        return 0
    
    # 重み付き平均遅延時間を計算
    total_weighted_delay = 0
    total_nodes = 0
    
    for node_type, delay_list in delays.items():
        if delay_list and node_type in node_counts:
            avg_delay = sum(delay_list) / len(delay_list)
            node_count = node_counts[node_type]
            total_weighted_delay += avg_delay * node_count
            total_nodes += node_count
    
    if total_nodes == 0:
        return 0
    
    weighted_avg_delay = total_weighted_delay / total_nodes
    
    # 効率は遅延の逆数で表現（遅延が少ないほど効率が高い）
    efficiency = 1000 / weighted_avg_delay if weighted_avg_delay > 0 else 0
    
    return efficiency

def analyze_scenario(scenario_num, log_file):
    """個別シナリオの分析"""
    print(f"\n=== シナリオ{scenario_num} の分析 ===")
    
    # ノード遅延の抽出
    delays = extract_node_delays(log_file)
    
    # 伝播統計の抽出
    propagation_stats = extract_propagation_stats(log_file)
    
    # 結果の表示
    print(f"ログファイル: {log_file}")
    
    if delays:
        print("\nノードタイプ別遅延時間:")
        node_counts = {}
        for node_type, delay_list in delays.items():
            if delay_list:
                avg_delay = sum(delay_list) / len(delay_list)
                node_count = len(delay_list)
                node_counts[node_type] = node_count
                print(f"  {node_type}: 平均 {avg_delay:.1f}ms ({node_count}ノード)")
        
        # ネットワーク効率の計算
        efficiency = calculate_network_efficiency(delays, node_counts)
        print(f"\nネットワーク効率: {efficiency:.2f}")
    
    if propagation_stats['total_blocks'] > 0:
        print(f"\nブロック伝播統計:")
        print(f"  総ブロック数: {propagation_stats['total_blocks']}")
        print(f"  平均伝播時間: {propagation_stats['avg_propagation_time']:.1f}ms")
        
        if propagation_stats['propagation_speed'] == float('inf'):
            print(f"  伝播速度: 無限大 (遅延0ms)")
        else:
            print(f"  伝播速度: {propagation_stats['propagation_speed']:.2f} ブロック/ms")
    
    return delays, propagation_stats, node_counts

def calculate_improvement_metrics(baseline_delays, baseline_stats, scenario_delays, scenario_stats, baseline_node_counts, scenario_node_counts):
    """改善指標を計算"""
    metrics = {}
    
    # ブロック伝播時間の改善率
    if baseline_stats['avg_propagation_time'] > 0:
        propagation_improvement = ((baseline_stats['avg_propagation_time'] - 
                                   scenario_stats['avg_propagation_time']) / 
                                  baseline_stats['avg_propagation_time'] * 100)
        metrics['propagation_improvement'] = propagation_improvement
    else:
        metrics['propagation_improvement'] = 0
    
    # ネットワーク効率の改善率
    baseline_efficiency = calculate_network_efficiency(baseline_delays, baseline_node_counts)
    scenario_efficiency = calculate_network_efficiency(scenario_delays, scenario_node_counts)
    
    if baseline_efficiency > 0:
        efficiency_improvement = ((scenario_efficiency - baseline_efficiency) / 
                                baseline_efficiency * 100)
        metrics['efficiency_improvement'] = efficiency_improvement
    else:
        metrics['efficiency_improvement'] = 0
    
    return metrics

def main():
    """メイン処理"""
    print("SimBlock Forerunner シナリオ結果ログファイルのみを使用した分析")
    print("=" * 70)
    
    # シナリオ設定（シナリオ結果ログファイルのみ）
    scenarios = {
        1: "corrected_scenario1_results.log",
        2: "corrected_scenario2_results.log", 
        3: "corrected_scenario3_results.log",
        4: "corrected_scenario4_results.log",
        5: "corrected_scenario5_results.log",
        6: "corrected_scenario6_results.log",
        7: "corrected_scenario7_results.log",
        8: "corrected_scenario8_results.log",
        9: "corrected_scenario9_results.log"
    }
    
    # シナリオ設定の説明
    scenario_configs = {
        1: "Forerunner適用率90%, 高速ノード90%, 低速ノード10%",
        2: "Forerunner適用率90%, 高速ノード50%, 低速ノード50%",
        3: "Forerunner適用率90%, 高速ノード10%, 低速ノード90%",
        4: "Forerunner適用率50%, 高速ノード90%, 低速ノード10%",
        5: "Forerunner適用率50%, 高速ノード50%, 低速ノード50%",
        6: "Forerunner適用率50%, 高速ノード10%, 低速ノード90%",
        7: "Forerunner適用率10%, 高速ノード90%, 低速ノード10%",
        8: "Forerunner適用率10%, 高速ノード50%, 低速ノード50%",
        9: "Forerunner適用率10%, 高速ノード10%, 低速ノード90% (ベースライン)"
    }
    
    # 各シナリオの分析
    results = {}
    for scenario_num, log_file in scenarios.items():
        if os.path.exists(log_file):
            print(f"\nシナリオ{scenario_num}: {scenario_configs[scenario_num]}")
            delays, stats, node_counts = analyze_scenario(scenario_num, log_file)
            results[scenario_num] = {
                'config': scenario_configs[scenario_num],
                'delays': delays,
                'stats': stats,
                'node_counts': node_counts
            }
        else:
            print(f"\nシナリオ{scenario_num}: ログファイル {log_file} が見つかりません")
    
    # 総合分析
    print("\n" + "=" * 70)
    print("総合分析結果（シナリオ結果ログファイルのみを使用）")
    print("=" * 70)
    
    # ベースライン（シナリオ9）との比較
    if 9 in results:
        baseline = results[9]
        print(f"\nベースライン (シナリオ9):")
        print(f"  設定: {baseline['config']}")
        if baseline['stats']['total_blocks'] > 0:
            print(f"  平均伝播時間: {baseline['stats']['avg_propagation_time']:.1f}ms")
        
        baseline_efficiency = calculate_network_efficiency(baseline['delays'], baseline['node_counts'])
        print(f"  ネットワーク効率: {baseline_efficiency:.2f}")
        
        # 各シナリオとの比較
        print(f"\n各シナリオの改善効果:")
        print("-" * 70)
        
        for scenario_num in [1, 2, 3, 4, 5, 6, 7, 8]:
            if scenario_num in results:
                scenario = results[scenario_num]
                
                # 改善指標の計算
                metrics = calculate_improvement_metrics(
                    baseline['delays'], baseline['stats'],
                    scenario['delays'], scenario['stats'],
                    baseline['node_counts'], scenario['node_counts']
                )
                
                print(f"\nシナリオ{scenario_num}: {scenario['config']}")
                
                if metrics['propagation_improvement'] > 0:
                    print(f"  📈 ブロック伝播時間: {metrics['propagation_improvement']:.1f}% 短縮")
                else:
                    print(f"  📉 ブロック伝播時間: {abs(metrics['propagation_improvement']):.1f}% 増加")
                
                if metrics['efficiency_improvement'] > 0:
                    print(f"  🚀 ネットワーク効率: {metrics['efficiency_improvement']:.1f}% 向上")
                else:
                    print(f"  📉 ネットワーク効率: {abs(metrics['efficiency_improvement']):.1f}% 低下")
                
                # ノード構成の影響分析
                high_perf_ratio = 0
                if scenario['node_counts']:
                    total_nodes = sum(scenario['node_counts'].values())
                    high_perf_nodes = (scenario['node_counts'].get('FORERUNNER_HIGH', 0) + 
                                     scenario['node_counts'].get('NON_FORERUNNER_HIGH', 0))
                    high_perf_ratio = (high_perf_nodes / total_nodes * 100) if total_nodes > 0 else 0
                
                print(f"  📊 高速ノード比率: {high_perf_ratio:.1f}%")
    
    print("\n" + "=" * 70)
    print("分析完了（シナリオ結果ログファイルのみを使用）")
    print("\n📋 評価基準:")
    print("  • ブロック伝播時間の短縮: ベースラインとの比較")
    print("  • ネットワーク効率の改善: ノード構成と遅延時間の重み付き平均")
    print("\n📁 使用したファイル:")
    for scenario_num, log_file in scenarios.items():
        if os.path.exists(log_file):
            print(f"  • シナリオ{scenario_num}: {log_file}")

if __name__ == "__main__":
    main()
