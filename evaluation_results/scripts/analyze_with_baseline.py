#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ベースライン基準での全シナリオ分析スクリプト
scenario_Baseline_results.logを基準として全シナリオの性能を評価
"""

import re
import statistics
import json
import os
from datetime import datetime

def parse_log_file(log_file):
    """ログファイルを解析して各ブロックの伝播時間を計算"""
    print(f"=== {log_file} の解析開始 ===")
    
    try:
        with open(log_file, 'r', encoding='utf-8', errors='ignore') as f:
            lines = f.readlines()
        
        # ブロックごとの伝播データを収集
        blocks_data = {}
        current_block = None
        current_propagations = []
        
        for line in lines:
            line = line.strip()
            
            # ブロックIDの行を検出
            if "ProofOfWorkBlock@" in line:
                # 前のブロックのデータを保存
                if current_block and current_propagations:
                    blocks_data[current_block] = current_propagations
                
                # 新しいブロックの開始
                current_block = line
                current_propagations = []
                continue
            
            # 伝播データの行を解析
            match = re.match(r'^(\d+),(\d+)$', line)
            if match and current_block:
                node_id = int(match.group(1))
                timestamp = int(match.group(2))
                current_propagations.append((node_id, timestamp))
        
        # 最後のブロックのデータを保存
        if current_block and current_propagations:
            blocks_data[current_block] = current_propagations
        
        print(f"📊 解析完了: {len(blocks_data)} ブロックを発見")
        return blocks_data
        
    except Exception as e:
        print(f"❌ ファイル解析エラー: {e}")
        return {}

def calculate_propagation_times(blocks_data):
    """各ブロックの正しい伝播時間とネットワーク効率を計算"""
    print(f"\n=== 伝播時間とネットワーク効率の計算 ===")
    
    propagation_results = []
    
    for block_id, propagations in blocks_data.items():
        if not propagations:
            continue
        
        # タイムスタンプでソート
        sorted_propagations = sorted(propagations, key=lambda x: x[1])
        
        # 最初と最後のタイムスタンプを取得
        first_timestamp = sorted_propagations[0][1]
        last_timestamp = sorted_propagations[-1][1]
        
        # 正しい伝播時間を計算
        propagation_time = last_timestamp - first_timestamp
        
        # ネットワーク効率を計算（伝播時間 / ノード数）
        node_count = len(propagations)
        network_efficiency = propagation_time / node_count if node_count > 0 else 0
        
        # 統計情報を計算
        timestamps = [p[1] for p in propagations]
        non_zero_timestamps = [t for t in timestamps if t > 0]  # ジェネシスブロック以外
        
        result = {
            'block_id': block_id,
            'first_timestamp': first_timestamp,
            'last_timestamp': last_timestamp,
            'propagation_time': propagation_time,
            'node_count': node_count,
            'network_efficiency': network_efficiency,  # ms/ノード
            'non_genesis_nodes': len(non_zero_timestamps),
            'avg_timestamp': statistics.mean(timestamps) if timestamps else 0,
            'median_timestamp': statistics.median(timestamps) if timestamps else 0,
            'std_timestamp': statistics.stdev(timestamps) if len(timestamps) > 1 else 0
        }
        
        propagation_results.append(result)
        
        print(f"ブロック {block_id}: {propagation_time}ms ({node_count}ノード, {network_efficiency:.2f}ms/ノード)")
    
    return propagation_results

def analyze_scenario_results(log_file):
    """シナリオの結果を分析"""
    print(f"\n=== {log_file} の詳細分析 ===")
    
    # ファイル名からシナリオ情報を抽出
    scenario_name = os.path.basename(log_file).replace('_results.log', '')
    
    # ログファイルを解析
    blocks_data = parse_log_file(log_file)
    if not blocks_data:
        return None
    
    # 伝播時間を計算
    propagation_results = calculate_propagation_times(blocks_data)
    if not propagation_results:
        return None
    
    # 統計分析
    propagation_times = [r['propagation_time'] for r in propagation_results]
    network_efficiencies = [r['network_efficiency'] for r in propagation_results]
    non_zero_times = [t for t in propagation_times if t > 0]  # ジェネシスブロック以外
    non_zero_efficiencies = [e for e, t in zip(network_efficiencies, propagation_times) if t > 0]
    
    if not non_zero_times:
        print("❌ ジェネシスブロック以外の伝播時間がありません")
        return None
    
    # 基本統計
    stats = {
        'scenario_name': scenario_name,
        'total_blocks': len(propagation_results),
        'non_genesis_blocks': len(non_zero_times),
        'min_propagation_time': min(non_zero_times),
        'max_propagation_time': max(non_zero_times),
        'avg_propagation_time': statistics.mean(non_zero_times),
        'median_propagation_time': statistics.median(non_zero_times),
        'std_propagation_time': statistics.stdev(non_zero_times) if len(non_zero_times) > 1 else 0,
        'min_network_efficiency': min(non_zero_efficiencies),
        'max_network_efficiency': max(non_zero_efficiencies),
        'avg_network_efficiency': statistics.mean(non_zero_efficiencies),
        'median_network_efficiency': statistics.median(non_zero_efficiencies),
        'std_network_efficiency': statistics.stdev(non_zero_efficiencies) if len(non_zero_efficiencies) > 1 else 0,
        'propagation_times': non_zero_times,
        'network_efficiencies': non_zero_efficiencies,
        'detailed_results': propagation_results
    }
    
    # 結果表示
    print(f"\n📊 {scenario_name} の統計結果:")
    print(f"  総ブロック数: {stats['total_blocks']}")
    print(f"  非ジェネシスブロック数: {stats['non_genesis_blocks']}")
    print(f"  最小伝播時間: {stats['min_propagation_time']}ms")
    print(f"  最大伝播時間: {stats['max_propagation_time']}ms")
    print(f"  平均伝播時間: {stats['avg_propagation_time']:.2f}ms")
    print(f"  中央値伝播時間: {stats['median_propagation_time']:.2f}ms")
    print(f"  標準偏差: {stats['std_propagation_time']:.2f}ms")
    print(f"  最小ネットワーク効率: {stats['min_network_efficiency']:.2f}ms/ノード")
    print(f"  最大ネットワーク効率: {stats['max_network_efficiency']:.2f}ms/ノード")
    print(f"  平均ネットワーク効率: {stats['avg_network_efficiency']:.2f}ms/ノード")
    print(f"  中央値ネットワーク効率: {stats['median_network_efficiency']:.2f}ms/ノード")
    
    return stats

def calculate_improvement_rates(baseline_stats, scenario_stats):
    """ベースラインに対する改善率を計算"""
    if not baseline_stats or not scenario_stats:
        return None
    
    # 伝播時間の改善率
    propagation_improvement = ((baseline_stats['avg_propagation_time'] - scenario_stats['avg_propagation_time']) / 
                              baseline_stats['avg_propagation_time']) * 100
    
    # ネットワーク効率の改善率
    efficiency_improvement = ((baseline_stats['avg_network_efficiency'] - scenario_stats['avg_network_efficiency']) / 
                             baseline_stats['avg_network_efficiency']) * 100
    
    return {
        'propagation_improvement': propagation_improvement,
        'efficiency_improvement': efficiency_improvement,
        'is_improved': propagation_improvement > 0 and efficiency_improvement > 0
    }

def main():
    """メイン実行関数"""
    print("=== ベースライン基準での全シナリオ分析開始 ===")
    print(f"分析開始時刻: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    
    # ベースラインログファイルを確認
    baseline_file = "scenario_Baseline_results.log"
    if not os.path.exists(baseline_file):
        print(f"❌ ベースラインファイルが見つかりません: {baseline_file}")
        return
    
    print(f"📁 ベースラインファイル: {baseline_file}")
    
    # ベースラインを分析
    print(f"\n--- ベースライン分析 ---")
    baseline_stats = analyze_scenario_results(baseline_file)
    if not baseline_stats:
        print("❌ ベースライン分析に失敗しました")
        return
    
    print(f"\n🎯 ベースライン性能:")
    print(f"  平均伝播時間: {baseline_stats['avg_propagation_time']:.2f}ms")
    print(f"  平均ネットワーク効率: {baseline_stats['avg_network_efficiency']:.2f}ms/ノード")
    
    # 他のログファイルを取得
    log_files = [f for f in os.listdir('.') if f.endswith('_results.log') and f != baseline_file]
    
    if not log_files:
        print("❌ 比較対象のログファイルが見つかりません")
        return
    
    print(f"📁 比較対象ログファイル: {len(log_files)}")
    
    all_results = {
        'baseline': baseline_stats
    }
    
    # 各シナリオを分析
    for log_file in sorted(log_files):
        print(f"\n--- {log_file} の分析 ---")
        
        result = analyze_scenario_results(log_file)
        if result:
            # ベースラインとの比較
            improvement = calculate_improvement_rates(baseline_stats, result)
            if improvement:
                result['improvement'] = improvement
                print(f"📈 ベースライン比較:")
                print(f"  伝播時間改善率: {improvement['propagation_improvement']:+.2f}%")
                print(f"  ネットワーク効率改善率: {improvement['efficiency_improvement']:+.2f}%")
                print(f"  性能向上: {'✅' if improvement['is_improved'] else '❌'}")
            
            all_results[result['scenario_name']] = result
    
    # 比較結果を表示
    if len(all_results) > 1:
        print(f"\n=== ベースライン基準での全シナリオ比較結果 ===")
        print(f"{'シナリオ':<20} {'平均伝播時間':<12} {'平均ネットワーク効率':<15} {'伝播時間改善率':<12} {'効率改善率':<10}")
        print("-" * 85)
        
        for scenario_name, result in sorted(all_results.items()):
            if scenario_name == 'baseline':
                print(f"{scenario_name:<20} {result['avg_propagation_time']:>10.2f}ms {result['avg_network_efficiency']:>12.2f}ms/ノード {'基準':>12} {'基準':>10}")
            else:
                improvement = result.get('improvement', {})
                prop_improvement = improvement.get('propagation_improvement', 0)
                eff_improvement = improvement.get('efficiency_improvement', 0)
                print(f"{scenario_name:<20} {result['avg_propagation_time']:>10.2f}ms {result['avg_network_efficiency']:>12.2f}ms/ノード {prop_improvement:>+10.2f}% {eff_improvement:>+8.2f}%")
        
        # 最適シナリオの特定
        non_baseline_results = {k: v for k, v in all_results.items() if k != 'baseline'}
        
        if non_baseline_results:
            best_time_scenario = min(non_baseline_results.values(), key=lambda x: x['avg_propagation_time'])
            best_efficiency_scenario = min(non_baseline_results.values(), key=lambda x: x['avg_network_efficiency'])
            best_improvement_scenario = max(non_baseline_results.values(), 
                                          key=lambda x: x.get('improvement', {}).get('propagation_improvement', 0))
            
            print(f"\n🎯 最適シナリオ（ベースライン比較）:")
            print(f"  最短伝播時間: {best_time_scenario['scenario_name']} ({best_time_scenario['avg_propagation_time']:.2f}ms)")
            print(f"  最高ネットワーク効率: {best_efficiency_scenario['scenario_name']} ({best_efficiency_scenario['avg_network_efficiency']:.2f}ms/ノード)")
            print(f"  最大改善率: {best_improvement_scenario['scenario_name']} ({best_improvement_scenario.get('improvement', {}).get('propagation_improvement', 0):+.2f}%)")
            
            # 改善されたシナリオの一覧
            improved_scenarios = [name for name, result in non_baseline_results.items() 
                                if result.get('improvement', {}).get('is_improved', False)]
            
            if improved_scenarios:
                print(f"\n✅ ベースラインを上回るシナリオ:")
                for scenario in sorted(improved_scenarios):
                    result = non_baseline_results[scenario]
                    improvement = result.get('improvement', {})
                    print(f"  - {scenario}: 伝播時間{improvement.get('propagation_improvement', 0):+.2f}%, 効率{improvement.get('efficiency_improvement', 0):+.2f}%")
            else:
                print(f"\n❌ ベースラインを上回るシナリオはありません")
        
        # 結果をJSONファイルに保存
        output_file = "baseline_comparison_results.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(all_results, f, ensure_ascii=False, indent=2, default=str)
        
        print(f"\n💾 詳細結果ファイル: {output_file}")
        
        # 分析レポートの生成
        generate_baseline_report(all_results, baseline_stats)
    
    print(f"\n✅ 分析完了: {len(all_results)} シナリオを分析")
    print(f"分析終了時刻: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

def generate_baseline_report(all_results, baseline_stats):
    """ベースライン比較レポートを生成"""
    non_baseline_results = {k: v for k, v in all_results.items() if k != 'baseline'}
    improved_scenarios = [name for name, result in non_baseline_results.items() 
                         if result.get('improvement', {}).get('is_improved', False)]
    
    report = f"""# Forerunnerプロジェクト ベースライン比較分析レポート

## 📊 分析概要

- **分析日時**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
- **分析対象**: {len(all_results)} シナリオ（ベースライン含む）
- **分析指標**: ブロック伝播時間、ネットワーク効率（ベースライン比較）

## 🎯 ベースライン性能

- **シナリオ**: Baseline
- **平均伝播時間**: {baseline_stats['avg_propagation_time']:.2f}ms
- **平均ネットワーク効率**: {baseline_stats['avg_network_efficiency']:.2f}ms/ノード
- **総ブロック数**: {baseline_stats['total_blocks']}
- **非ジェネシスブロック数**: {baseline_stats['non_genesis_blocks']}

## 🏆 最適シナリオ

"""
    
    if non_baseline_results:
        best_time_scenario = min(non_baseline_results.values(), key=lambda x: x['avg_propagation_time'])
        best_efficiency_scenario = min(non_baseline_results.values(), key=lambda x: x['avg_network_efficiency'])
        best_improvement_scenario = max(non_baseline_results.values(), 
                                      key=lambda x: x.get('improvement', {}).get('propagation_improvement', 0))
        
        report += f"""### 最短伝播時間
- **シナリオ**: {best_time_scenario['scenario_name']}
- **平均伝播時間**: {best_time_scenario['avg_propagation_time']:.2f}ms
- **改善率**: {best_time_scenario.get('improvement', {}).get('propagation_improvement', 0):+.2f}%

### 最高ネットワーク効率
- **シナリオ**: {best_efficiency_scenario['scenario_name']}
- **平均ネットワーク効率**: {best_efficiency_scenario['avg_network_efficiency']:.2f}ms/ノード
- **改善率**: {best_efficiency_scenario.get('improvement', {}).get('efficiency_improvement', 0):+.2f}%

### 最大改善率
- **シナリオ**: {best_improvement_scenario['scenario_name']}
- **伝播時間改善率**: {best_improvement_scenario.get('improvement', {}).get('propagation_improvement', 0):+.2f}%
- **効率改善率**: {best_improvement_scenario.get('improvement', {}).get('efficiency_improvement', 0):+.2f}%

"""
    
    report += f"""## 📈 シナリオ比較結果

| シナリオ | 平均伝播時間 | 平均ネットワーク効率 | 伝播時間改善率 | 効率改善率 |
|---------|-------------|-------------------|---------------|-----------|
| Baseline | {baseline_stats['avg_propagation_time']:.2f}ms | {baseline_stats['avg_network_efficiency']:.2f}ms/ノード | 基準 | 基準 |
"""
    
    for scenario_name, result in sorted(non_baseline_results.items()):
        improvement = result.get('improvement', {})
        prop_improvement = improvement.get('propagation_improvement', 0)
        eff_improvement = improvement.get('efficiency_improvement', 0)
        report += f"| {scenario_name} | {result['avg_propagation_time']:.2f}ms | {result['avg_network_efficiency']:.2f}ms/ノード | {prop_improvement:+.2f}% | {eff_improvement:+.2f}% |\n"
    
    report += f"""
## 🔍 詳細分析

### 改善されたシナリオ
"""
    
    if improved_scenarios:
        for scenario in sorted(improved_scenarios):
            result = non_baseline_results[scenario]
            improvement = result.get('improvement', {})
            report += f"- **{scenario}**: 伝播時間{improvement.get('propagation_improvement', 0):+.2f}%, 効率{improvement.get('efficiency_improvement', 0):+.2f}%\n"
    else:
        report += "- ベースラインを上回るシナリオはありません\n"
    
    report += f"""
### 統計サマリー
- **分析対象シナリオ数**: {len(non_baseline_results)}
- **改善シナリオ数**: {len(improved_scenarios)}
- **改善率**: {len(improved_scenarios)/len(non_baseline_results)*100:.1f}%

## 💡 結論

ベースライン性能を基準とした比較分析により、Forerunner技術の効果が明確に評価されました。

---
**レポート生成日**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
"""
    
    with open("ベースライン比較分析レポート.md", 'w', encoding='utf-8') as f:
        f.write(report)
    
    print(f"📋 分析レポート: ベースライン比較分析レポート.md")

if __name__ == "__main__":
    main()

