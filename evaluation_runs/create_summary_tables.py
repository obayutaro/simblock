#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本番シナリオ評価結果サマリーテーブル生成スクリプト
"""

import json
import os
import sys

def create_tables(log_dir):
    """評価結果のサマリーテーブルを作成"""
    
    # トラフィック統計を読み込み
    traffic_file = os.path.join(log_dir, 'traffic_stats_summary.json')
    if os.path.exists(traffic_file):
        with open(traffic_file, 'r', encoding='utf-8') as f:
            traffic_data = json.load(f)
    else:
        traffic_data = []
    
    scenarios_dict = {r['scenario']: r for r in traffic_data} if isinstance(traffic_data, list) else {}
    
    print("=" * 130)
    print("本番シナリオ評価結果サマリー")
    print("=" * 130)
    print()
    
    # 表1: ブロック伝播時間
    print("【表1】ブロック伝播時間（平均値、単位: ms）")
    print("-" * 130)
    print(f"{'シナリオ':<15} {'適用率':<8} {'低速ノード':<10} {'共有OFF(ms)':<15} {'共有ON(ms)':<15} {'改善率(%)':<12} {'改善':<8}")
    print("-" * 130)
    
    propagation_results = [
        ("P10_10", "10%", "10%", 5598.68, 5570.34, 0.51, "✅"),
        ("P10_50", "10%", "50%", 5598.68, 5591.26, 0.13, "✅"),
        ("P10_90", "10%", "90%", 5598.68, 5588.64, 0.18, "✅"),
        ("P40_10", "40%", "10%", 5566.80, 5565.77, 0.02, "✅"),
        ("P40_50", "40%", "50%", 5566.80, 5586.78, -0.36, "❌"),
        ("P40_90", "40%", "90%", 5566.80, 5578.57, -0.21, "❌"),
        ("P70_10", "70%", "10%", 5585.30, 5607.15, -0.39, "❌"),
        ("P70_50", "70%", "50%", 5585.30, 5593.06, -0.14, "❌"),
        ("P70_90", "70%", "90%", 5585.30, 5572.56, 0.23, "✅"),
        ("P100_10", "100%", "10%", 5591.20, 5578.16, 0.23, "✅"),
        ("P100_50", "100%", "50%", 5591.20, 5592.31, -0.02, "❌"),
        ("P100_90", "100%", "90%", 5591.20, 5592.66, -0.03, "❌"),
    ]
    
    for scenario, ratio, slow, off, on, improvement, status in propagation_results:
        print(f"{scenario:<15} {ratio:<8} {slow:<10} {off:<15.2f} {on:<15.2f} {improvement:<12.2f} {status:<8}")
    
    print()
    print("=" * 130)
    print("【表2】トラフィック帯域使用量統計（共有ONシナリオのみ）")
    print("-" * 130)
    print(f"{'シナリオ':<15} {'適用率':<8} {'低速ノード':<10} {'データあり':<12} {'データなし':<12} {'最大(Mbps)':<15} {'平均(Mbps)':<15} {'最小(Mbps)':<15}")
    print("-" * 130)
    
    for scenario_name in sorted(scenarios_dict.keys()):
        result = scenarios_dict[scenario_name]
        summary = result.get('summary', {})
        data_count = summary.get('data_count', 0)
        no_data_count = summary.get('no_data_count', 0)
        
        # シナリオ名から適用率と低速ノード割合を抽出
        if scenario_name.startswith('P10_'):
            ratio = "10%"
            slow = scenario_name.split('_')[1].replace('ON', '')
        elif scenario_name.startswith('P40_'):
            ratio = "40%"
            slow = scenario_name.split('_')[1].replace('ON', '')
        elif scenario_name.startswith('P70_'):
            ratio = "70%"
            slow = scenario_name.split('_')[1].replace('ON', '')
        elif scenario_name.startswith('P100_'):
            ratio = "100%"
            slow = scenario_name.split('_')[1].replace('ON', '')
        else:
            ratio = "-"
            slow = "-"
        
        if data_count > 0:
            max_bps = summary.get('max_bps', {}).get('mean', 0) / 1000000
            avg_bps = summary.get('avg_bps', {}).get('mean', 0) / 1000000
            min_bps = summary.get('min_bps', {}).get('mean', 0) / 1000000
            print(f"{scenario_name:<15} {ratio:<8} {slow:<10} {data_count:<12} {no_data_count:<12} {max_bps:<15.2f} {avg_bps:<15.2f} {min_bps:<15.2f}")
        else:
            print(f"{scenario_name:<15} {ratio:<8} {slow:<10} {'0':<12} {no_data_count:<12} {'データなし':<15} {'データなし':<15} {'データなし':<15}")
    
    print()
    print("=" * 130)
    print("【表3】適用率別トラフィック帯域使用量（平均値、単位: Mbps）")
    print("-" * 130)
    print(f"{'適用率':<10} {'低速10%':<15} {'低速50%':<15} {'低速90%':<15}")
    print("-" * 130)
    
    for ratio in ['10%', '40%', '70%', '100%']:
        prefix = f"P{ratio.replace('%', '')}_"
        row = [ratio]
        
        for slow in ['10', '50', '90']:
            scenario_name = f"{prefix}{slow}_ON"
            if scenario_name in scenarios_dict:
                summary = scenarios_dict[scenario_name].get('summary', {})
                if summary.get('data_count', 0) > 0:
                    avg_mbps = summary.get('avg_bps', {}).get('mean', 0) / 1000000
                    row.append(f"{avg_mbps:.2f}")
                else:
                    row.append("データなし")
            else:
                row.append("-")
        
        print(f"{row[0]:<10} {row[1]:<15} {row[2]:<15} {row[3]:<15}")
    
    print()
    print("=" * 130)
    print("【表4】共有ON/OFF比較（伝播時間改善率とトラフィック負荷）")
    print("-" * 130)
    print(f"{'シナリオ':<15} {'適用率':<8} {'低速ノード':<10} {'改善率(%)':<12} {'トラフィック(Mbps)':<20} {'評価':<20}")
    print("-" * 130)
    
    for scenario_base, ratio, slow, off, on, improvement, status in propagation_results:
        scenario_name = f"{scenario_base}_ON"
        
        if scenario_name in scenarios_dict:
            summary = scenarios_dict[scenario_name].get('summary', {})
            if summary.get('data_count', 0) > 0:
                avg_mbps = summary.get('avg_bps', {}).get('mean', 0) / 1000000
                traffic_str = f"{avg_mbps:.2f}"
                
                # 評価: 改善率が正で、トラフィックが適切な範囲
                if improvement > 0 and avg_mbps < 500:
                    evaluation = "✅良好"
                elif improvement > 0:
                    evaluation = "⚠️高負荷"
                elif improvement < -0.2:
                    evaluation = "❌悪化"
                else:
                    evaluation = "➖変化なし"
            else:
                traffic_str = "データなし"
                evaluation = "⚠️データ不足"
        else:
            traffic_str = "-"
            evaluation = "-"
        
        print(f"{scenario_base:<15} {ratio:<8} {slow:<10} {improvement:<12.2f} {traffic_str:<20} {evaluation:<20}")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 create_summary_tables.py <log_directory>")
        sys.exit(1)
    
    log_dir = sys.argv[1]
    create_tables(log_dir)

