#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Forerunnerプロジェクト ログ分析スクリプト（新しい計算方法）
新しい伝播速度計算方法: network_efficiency = ((n)th_row_node_timestamp - (n-1)th_row_node_timestamp)/ Participation_node_count
"""

import os
import json
import re
from collections import defaultdict
import statistics

def parse_log_file(log_file_path):
    """ログファイルを解析してブロック伝播データを抽出"""
    blocks_data = {}
    current_block = None
    current_propagations = []
    
    with open(log_file_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            
            # ブロックID行の検出
            if line.startswith('simblock.block.ProofOfWorkBlock@'):
                if current_block and current_propagations:
                    blocks_data[current_block] = current_propagations
                current_block = line
                current_propagations = []
                continue
            
            # 伝播データ行の解析 (node_id,timestamp形式)
            if ',' in line and not line.startswith('>'):
                try:
                    node_id, timestamp = line.split(',')
                    node_id = int(node_id)
                    timestamp = int(timestamp)
                    current_propagations.append((node_id, timestamp))
                except ValueError:
                    continue
    
    # 最後のブロックを追加
    if current_block and current_propagations:
        blocks_data[current_block] = current_propagations
    
    return blocks_data

def calculate_propagation_metrics(blocks_data):
    """新しい計算方法で伝播指標を計算"""
    results = {}
    
    for block_id, propagations in blocks_data.items():
        if len(propagations) < 2:
            continue
            
        # タイムスタンプ順でソート
        sorted_propagations = sorted(propagations, key=lambda x: x[1])
        
        # 基本指標
        first_timestamp = sorted_propagations[0][1]
        last_timestamp = sorted_propagations[-1][1]
        participation_node_count = len(sorted_propagations)
        
        # ブロック伝播時間（従来の方法）
        propagation_time = last_timestamp - first_timestamp
        
        # 新しいネットワーク効率計算
        # 隣接するノード間の伝播時間の合計を計算
        total_adjacent_time = 0
        for i in range(1, len(sorted_propagations)):
            current_timestamp = sorted_propagations[i][1]
            previous_timestamp = sorted_propagations[i-1][1]
            adjacent_time = current_timestamp - previous_timestamp
            total_adjacent_time += adjacent_time
        
        # 新しいネットワーク効率 = 隣接時間合計 / 参加ノード数
        network_efficiency = total_adjacent_time / participation_node_count if participation_node_count > 0 else 0
        
        results[block_id] = {
            'propagation_time': propagation_time,
            'network_efficiency': network_efficiency,
            'participation_node_count': participation_node_count,
            'first_timestamp': first_timestamp,
            'last_timestamp': last_timestamp,
            'total_adjacent_time': total_adjacent_time
        }
    
    return results

def calculate_statistics(results):
    """統計量を計算"""
    if not results:
        return {}
    
    propagation_times = [r['propagation_time'] for r in results.values()]
    network_efficiencies = [r['network_efficiency'] for r in results.values()]
    participation_counts = [r['participation_node_count'] for r in results.values()]
    
    stats = {
        'propagation_time': {
            'mean': statistics.mean(propagation_times),
            'median': statistics.median(propagation_times),
            'std': statistics.stdev(propagation_times) if len(propagation_times) > 1 else 0,
            'min': min(propagation_times),
            'max': max(propagation_times),
            'count': len(propagation_times)
        },
        'network_efficiency': {
            'mean': statistics.mean(network_efficiencies),
            'median': statistics.median(network_efficiencies),
            'std': statistics.stdev(network_efficiencies) if len(network_efficiencies) > 1 else 0,
            'min': min(network_efficiencies),
            'max': max(network_efficiencies),
            'count': len(network_efficiencies)
        },
        'participation_node_count': {
            'mean': statistics.mean(participation_counts),
            'median': statistics.median(participation_counts),
            'std': statistics.stdev(participation_counts) if len(participation_counts) > 1 else 0,
            'min': min(participation_counts),
            'max': max(participation_counts),
            'count': len(participation_counts)
        }
    }
    
    return stats

def analyze_all_scenarios():
    """全シナリオを分析"""
    logs_dir = "logs"
    results = {}
    
    # ログファイル一覧を取得
    log_files = [f for f in os.listdir(logs_dir) if f.endswith('.log')]
    
    for log_file in sorted(log_files):
        scenario_name = log_file.replace('_results.log', '')
        log_path = os.path.join(logs_dir, log_file)
        
        print(f"分析中: {scenario_name}")
        
        try:
            # ログファイル解析
            blocks_data = parse_log_file(log_path)
            
            # 指標計算
            scenario_results = calculate_propagation_metrics(blocks_data)
            
            # 統計量計算
            statistics = calculate_statistics(scenario_results)
            
            results[scenario_name] = {
                'blocks_data': scenario_results,
                'statistics': statistics,
                'total_blocks': len(scenario_results)
            }
            
            print(f"  - 処理済みブロック数: {len(scenario_results)}")
            
        except Exception as e:
            print(f"  - エラー: {e}")
            results[scenario_name] = {'error': str(e)}
    
    return results

def generate_comparison_report(results):
    """比較レポートを生成"""
    report = "# Forerunnerプロジェクト 評価結果レポート（新しい計算方法）\n\n"
    report += "## 📊 評価概要\n\n"
    report += f"- **評価日時**: 2025-08-31\n"
    report += f"- **評価シナリオ数**: {len(results)}\n"
    report += f"- **計算方法**: 新しいネットワーク効率計算\n"
    report += f"  - ネットワーク効率 = 隣接ノード間伝播時間合計 / 参加ノード数\n\n"
    
    # シナリオ別統計
    report += "## 📈 シナリオ別統計\n\n"
    
    # ベースラインを特定
    baseline_scenario = None
    for scenario in results.keys():
        if 'baseline' in scenario.lower():
            baseline_scenario = scenario
            break
    
    if baseline_scenario and baseline_scenario in results:
        baseline_stats = results[baseline_scenario]['statistics']
        report += f"### ベースライン（{baseline_scenario}）\n\n"
        report += f"- **平均伝播時間**: {baseline_stats['propagation_time']['mean']:.2f}ms\n"
        report += f"- **平均ネットワーク効率**: {baseline_stats['network_efficiency']['mean']:.4f}ms/ノード\n"
        report += f"- **平均参加ノード数**: {baseline_stats['participation_node_count']['mean']:.1f}\n\n"
    
    # 全シナリオの比較表
    report += "### 全シナリオ比較\n\n"
    report += "| シナリオ | 平均伝播時間(ms) | 平均ネットワーク効率(ms/ノード) | 平均参加ノード数 | 処理ブロック数 |\n"
    report += "|---------|------------------|----------------------------------|------------------|----------------|\n"
    
    for scenario_name, scenario_data in sorted(results.items()):
        if 'error' in scenario_data:
            report += f"| {scenario_name} | エラー | エラー | エラー | エラー |\n"
            continue
            
        stats = scenario_data['statistics']
        if not stats:
            report += f"| {scenario_name} | データなし | データなし | データなし | 0 |\n"
            continue
            
        report += f"| {scenario_name} | {stats['propagation_time']['mean']:.2f} | {stats['network_efficiency']['mean']:.4f} | {stats['participation_node_count']['mean']:.1f} | {scenario_data['total_blocks']} |\n"
    
    # 改善率計算（ベースラインがある場合）
    if baseline_scenario and baseline_scenario in results:
        report += "\n### ベースライン比較\n\n"
        report += "| シナリオ | 伝播時間改善率(%) | ネットワーク効率改善率(%) |\n"
        report += "|---------|-------------------|---------------------------|\n"
        
        baseline_prop_time = baseline_stats['propagation_time']['mean']
        baseline_network_eff = baseline_stats['network_efficiency']['mean']
        
        for scenario_name, scenario_data in sorted(results.items()):
            if 'error' in scenario_data or scenario_name == baseline_scenario:
                continue
                
            stats = scenario_data['statistics']
            if not stats:
                continue
                
            prop_improvement = ((baseline_prop_time - stats['propagation_time']['mean']) / baseline_prop_time) * 100
            network_improvement = ((baseline_network_eff - stats['network_efficiency']['mean']) / baseline_network_eff) * 100
            
            report += f"| {scenario_name} | {prop_improvement:+.2f} | {network_improvement:+.2f} |\n"
    
    # 最適シナリオの特定
    report += "\n### 🏆 最適シナリオ\n\n"
    
    best_prop_scenario = None
    best_network_scenario = None
    best_prop_time = float('inf')
    best_network_eff = float('inf')
    
    for scenario_name, scenario_data in results.items():
        if 'error' in scenario_data:
            continue
            
        stats = scenario_data['statistics']
        if not stats:
            continue
            
        if stats['propagation_time']['mean'] < best_prop_time:
            best_prop_time = stats['propagation_time']['mean']
            best_prop_scenario = scenario_name
            
        if stats['network_efficiency']['mean'] < best_network_eff:
            best_network_eff = stats['network_efficiency']['mean']
            best_network_scenario = scenario_name
    
    if best_prop_scenario:
        report += f"- **最短伝播時間**: {best_prop_scenario} ({best_prop_time:.2f}ms)\n"
    if best_network_scenario:
        report += f"- **最高ネットワーク効率**: {best_network_scenario} ({best_network_eff:.4f}ms/ノード)\n"
    
    report += "\n## 🔍 詳細分析\n\n"
    report += "### 新しい計算方法の特徴\n\n"
    report += "1. **隣接ノード間伝播時間**: タイムスタンプ順でソートした隣接ノード間の時間差を計算\n"
    report += "2. **参加ノード数**: 実際にブロックを受信したノード数で除算\n"
    report += "3. **効率性指標**: より細かい粒度での伝播効率を測定\n\n"
    
    report += "### 従来方法との違い\n\n"
    report += "- **従来**: 伝播時間 / ノード数\n"
    report += "- **新方法**: 隣接時間合計 / 参加ノード数\n"
    report += "- **利点**: より詳細な伝播パターンの分析が可能\n\n"
    
    return report

def main():
    """メイン処理"""
    print("=== Forerunnerプロジェクト ログ分析開始 ===")
    print("新しい計算方法: network_efficiency = 隣接時間合計 / 参加ノード数")
    
    # 全シナリオ分析
    results = analyze_all_scenarios()
    
    # 結果をJSONファイルに保存
    output_file = "new_method_analysis_results.json"
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    
    print(f"\n結果を保存: {output_file}")
    
    # レポート生成
    report = generate_comparison_report(results)
    report_file = "new_method_analysis_report.md"
    with open(report_file, 'w', encoding='utf-8') as f:
        f.write(report)
    
    print(f"レポートを保存: {report_file}")
    
    # サマリー表示
    print("\n=== 分析サマリー ===")
    print(f"処理済みシナリオ数: {len(results)}")
    
    # 最適シナリオの表示
    best_scenarios = []
    for scenario_name, scenario_data in results.items():
        if 'error' not in scenario_data and 'statistics' in scenario_data:
            stats = scenario_data['statistics']
            if stats:
                best_scenarios.append((
                    scenario_name,
                    stats['propagation_time']['mean'],
                    stats['network_efficiency']['mean']
                ))
    
    if best_scenarios:
        best_scenarios.sort(key=lambda x: x[1])  # 伝播時間でソート
        print(f"最短伝播時間: {best_scenarios[0][0]} ({best_scenarios[0][1]:.2f}ms)")
        
        best_scenarios.sort(key=lambda x: x[2])  # ネットワーク効率でソート
        print(f"最高ネットワーク効率: {best_scenarios[0][0]} ({best_scenarios[0][2]:.4f}ms/ノード)")
    
    print("\n=== 分析完了 ===")

if __name__ == "__main__":
    main()

