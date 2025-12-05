#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
トラフィック負荷増加の詳細分析スクリプト
"""

import json
import os
import sys

def analyze_traffic_increase(log_dir):
    """トラフィック負荷増加を分析"""
    
    traffic_file = os.path.join(log_dir, 'traffic_stats_summary.json')
    if os.path.exists(traffic_file):
        with open(traffic_file, 'r', encoding='utf-8') as f:
            traffic_data = json.load(f)
    else:
        traffic_data = []
    
    scenarios_dict = {r['scenario']: r for r in traffic_data} if isinstance(traffic_data, list) else {}
    
    print("=" * 130)
    print("トラフィック負荷増加の詳細分析")
    print("=" * 130)
    print()
    
    # 共有OFFのトラフィックは0と仮定（未来コンテキスト共有が行われないため）
    print("【前提】")
    print("- 共有OFF: トラフィック負荷 = 0 Mbps（未来コンテキスト共有が行われないため）")
    print("- 共有ON: 以下の表に示すトラフィック負荷")
    print("- 注意: 送信と受信の両方が記録されているため、実際のネットワーク負荷は約2倍になる可能性があります")
    print()
    
    print("=" * 130)
    print("【表1】シナリオ別トラフィック負荷増加（共有OFF → 共有ON）")
    print("-" * 130)
    print(f"{'シナリオ':<15} {'適用率':<8} {'低速ノード':<10} {'共有OFF(Mbps)':<15} {'共有ON(Mbps)':<15} {'増加量(Mbps)':<15} {'増加率':<15}")
    print("-" * 130)
    
    propagation_results = [
        ("P10_10", "10%", "10%"),
        ("P10_50", "10%", "50%"),
        ("P10_90", "10%", "90%"),
        ("P40_10", "40%", "10%"),
        ("P40_50", "40%", "50%"),
        ("P40_90", "40%", "90%"),
        ("P70_10", "70%", "10%"),
        ("P70_50", "70%", "50%"),
        ("P70_90", "70%", "90%"),
        ("P100_10", "100%", "10%"),
        ("P100_50", "100%", "50%"),
        ("P100_90", "100%", "90%"),
    ]
    
    for scenario_base, ratio, slow in propagation_results:
        scenario_name = f"{scenario_base}_ON"
        off_traffic = 0.0  # 共有OFFは0と仮定
        
        if scenario_name in scenarios_dict:
            summary = scenarios_dict[scenario_name].get('summary', {})
            if summary.get('data_count', 0) > 0:
                avg_mbps = summary.get('avg_bps', {}).get('mean', 0) / 1000000
                increase = avg_mbps - off_traffic
                increase_rate = "∞" if off_traffic == 0 else f"{(increase / off_traffic) * 100:.1f}%"
                print(f"{scenario_base:<15} {ratio:<8} {slow:<10} {off_traffic:<15.2f} {avg_mbps:<15.2f} {increase:<15.2f} {increase_rate:<15}")
            else:
                print(f"{scenario_base:<15} {ratio:<8} {slow:<10} {off_traffic:<15.2f} {'データなし':<15} {'-':<15} {'-':<15}")
        else:
            print(f"{scenario_base:<15} {ratio:<8} {slow:<10} {off_traffic:<15.2f} {'-':<15} {'-':<15} {'-':<15}")
    
    print()
    print("=" * 130)
    print("【表2】適用率別トラフィック負荷増加の平均")
    print("-" * 130)
    print(f"{'適用率':<10} {'低速10%':<15} {'低速50%':<15} {'低速90%':<15} {'平均':<15}")
    print("-" * 130)
    
    for ratio in ['10%', '40%', '70%', '100%']:
        prefix = f"P{ratio.replace('%', '')}_"
        row = [ratio]
        values = []
        
        for slow in ['10', '50', '90']:
            scenario_name = f"{prefix}{slow}_ON"
            if scenario_name in scenarios_dict:
                summary = scenarios_dict[scenario_name].get('summary', {})
                if summary.get('data_count', 0) > 0:
                    avg_mbps = summary.get('avg_bps', {}).get('mean', 0) / 1000000
                    row.append(f"{avg_mbps:.2f}")
                    values.append(avg_mbps)
                else:
                    row.append("データなし")
            else:
                row.append("-")
        
        if values:
            avg = sum(values) / len(values)
            row.append(f"{avg:.2f}")
        else:
            row.append("-")
        
        print(f"{row[0]:<10} {row[1]:<15} {row[2]:<15} {row[3]:<15} {row[4]:<15}")
    
    print()
    print("=" * 130)
    print("【表3】低速ノード割合別トラフィック負荷増加の平均")
    print("-" * 130)
    print(f"{'低速ノード':<10} {'適用10%':<15} {'適用40%':<15} {'適用70%':<15} {'適用100%':<15} {'平均':<15}")
    print("-" * 130)
    
    for slow in ['10', '50', '90']:
        row = [f"{slow}%"]
        values = []
        
        for ratio in ['10%', '40%', '70%', '100%']:
            prefix = f"P{ratio.replace('%', '')}_"
            scenario_name = f"{prefix}{slow}_ON"
            if scenario_name in scenarios_dict:
                summary = scenarios_dict[scenario_name].get('summary', {})
                if summary.get('data_count', 0) > 0:
                    avg_mbps = summary.get('avg_bps', {}).get('mean', 0) / 1000000
                    row.append(f"{avg_mbps:.2f}")
                    values.append(avg_mbps)
                else:
                    row.append("データなし")
        else:
            row.append("-")
        
        if values:
            avg = sum(values) / len(values)
            row.append(f"{avg:.2f}")
        else:
            row.append("-")
        
        print(f"{row[0]:<10} {row[1]:<15} {row[2]:<15} {row[3]:<15} {row[4]:<15} {row[5]:<15}")
    
    print()
    print("=" * 130)
    print("【表4】全体統計")
    print("-" * 130)
    
    all_values = []
    for scenario_name in scenarios_dict.keys():
        summary = scenarios_dict[scenario_name].get('summary', {})
        if summary.get('data_count', 0) > 0:
            avg_mbps = summary.get('avg_bps', {}).get('mean', 0) / 1000000
            all_values.append(avg_mbps)
    
    if all_values:
        min_val = min(all_values)
        max_val = max(all_values)
        avg_val = sum(all_values) / len(all_values)
        median_val = sorted(all_values)[len(all_values) // 2]
        
        print(f"最小値: {min_val:.2f} Mbps")
        print(f"最大値: {max_val:.2f} Mbps")
        print(f"平均値: {avg_val:.2f} Mbps")
        print(f"中央値: {median_val:.2f} Mbps")
        print(f"データありシナリオ数: {len(all_values)}")
        print()
        print(f"共有OFF（0 Mbps）からの増加:")
        print(f"  - 最小増加: {min_val:.2f} Mbps")
        print(f"  - 最大増加: {max_val:.2f} Mbps")
        print(f"  - 平均増加: {avg_val:.2f} Mbps")
        print(f"  - 中央値増加: {median_val:.2f} Mbps")
        print()
        print("【実際のネットワーク負荷（送受信両方記録のため約2倍）】")
        print(f"  - 最小増加（実測）: {min_val * 2:.2f} Mbps")
        print(f"  - 最大増加（実測）: {max_val * 2:.2f} Mbps")
        print(f"  - 平均増加（実測）: {avg_val * 2:.2f} Mbps")
        print(f"  - 中央値増加（実測）: {median_val * 2:.2f} Mbps")
    
    print()
    print("=" * 130)
    print("【注意事項】")
    print("-" * 130)
    print("1. 送信と受信の両方が記録されているため、実際のネットワーク負荷は約2倍になる可能性があります")
    print("2. 共有OFFのシナリオでは未来コンテキスト共有が行われないため、トラフィック負荷は0と仮定しています")
    print("3. 低速ノード割合90%のシナリオでは、データなしの実行が多数あり、統計の信頼性が低い可能性があります")
    print("4. トラフィック量はBLOCK_SIZE（120KB）を基準に計算されています")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 analyze_traffic_increase.py <log_directory>")
        sys.exit(1)
    
    log_dir = sys.argv[1]
    analyze_traffic_increase(log_dir)


