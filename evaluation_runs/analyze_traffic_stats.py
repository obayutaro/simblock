#!/usr/bin/bin/env python3
# -*- coding: utf-8 -*-
"""
トラフィック統計分析スクリプト
本番シナリオの共有ON実行結果からトラフィック帯域使用量統計を集計・分析
"""

import re
import os
import sys
import json
from pathlib import Path
from collections import defaultdict
import statistics

def parse_traffic_stats(log_file):
    """ログファイルからトラフィック統計を抽出（送受信を分けて）"""
    stats = {
        'max_bps_send': None,
        'avg_bps_send': None,
        'min_bps_send': None,
        'max_bps_recv': None,
        'avg_bps_recv': None,
        'min_bps_recv': None,
        'max_bps_total': None,
        'avg_bps_total': None,
        'min_bps_total': None,
        'has_data': False
    }
    
    try:
        with open(log_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # 送信トラフィック統計のパターンを検索
        pattern_send = r'FCSM_TRAFFIC_BANDWIDTH_STATS_SEND: max=(\d+) bps, avg=(\d+) bps, min=(\d+) bps'
        match_send = re.search(pattern_send, content)
        
        # 受信トラフィック統計のパターンを検索
        pattern_recv = r'FCSM_TRAFFIC_BANDWIDTH_STATS_RECV: max=(\d+) bps, avg=(\d+) bps, min=(\d+) bps'
        match_recv = re.search(pattern_recv, content)
        
        # 合計トラフィック統計のパターンを検索
        pattern_total = r'FCSM_TRAFFIC_BANDWIDTH_STATS_TOTAL: max=(\d+) bps, avg=(\d+) bps, min=(\d+) bps'
        match_total = re.search(pattern_total, content)
        
        # 旧形式との互換性（FCSM_TRAFFIC_BANDWIDTH_STATS）
        pattern_old = r'FCSM_TRAFFIC_BANDWIDTH_STATS: max=(\d+) bps, avg=(\d+) bps, min=(\d+) bps'
        match_old = re.search(pattern_old, content)
        
        if match_send:
            stats['max_bps_send'] = int(match_send.group(1))
            stats['avg_bps_send'] = int(match_send.group(2))
            stats['min_bps_send'] = int(match_send.group(3))
            stats['has_data'] = True
        
        if match_recv:
            stats['max_bps_recv'] = int(match_recv.group(1))
            stats['avg_bps_recv'] = int(match_recv.group(2))
            stats['min_bps_recv'] = int(match_recv.group(3))
            stats['has_data'] = True
        
        if match_total:
            stats['max_bps_total'] = int(match_total.group(1))
            stats['avg_bps_total'] = int(match_total.group(2))
            stats['min_bps_total'] = int(match_total.group(3))
            stats['has_data'] = True
        
        # 旧形式との互換性
        if match_old and not stats['has_data']:
            stats['max_bps_total'] = int(match_old.group(1))
            stats['avg_bps_total'] = int(match_old.group(2))
            stats['min_bps_total'] = int(match_old.group(3))
            stats['has_data'] = True
            
        if not stats['has_data'] and "トラフィックデータなし" in content:
            stats['has_data'] = False
            
    except Exception as e:
        print(f"Error parsing {log_file}: {e}", file=sys.stderr)
        
    return stats

def analyze_scenario(scenario_name, log_dir):
    """1つのシナリオの全実行結果を分析（データなしの実行は統計計算から除外）"""
    results = {
        'scenario': scenario_name,
        'runs': [],
        'runs_no_data': [],  # データなしの実行を記録
        'max_bps_send': [],
        'avg_bps_send': [],
        'min_bps_send': [],
        'max_bps_recv': [],
        'avg_bps_recv': [],
        'min_bps_recv': [],
        'max_bps_total': [],
        'avg_bps_total': [],
        'min_bps_total': [],
        'summary': {}
    }
    
    # 該当シナリオのログファイルを検索
    pattern = re.compile(rf'^{re.escape(scenario_name)}_run\d+\.log$')
    log_files = sorted([f for f in os.listdir(log_dir) if pattern.match(f)])
    
    if not log_files:
        return None
        
    for log_file in log_files:
        log_path = os.path.join(log_dir, log_file)
        stats = parse_traffic_stats(log_path)
        
        # データありの実行のみを統計計算に含める
        if stats['has_data']:
            run_data = {'file': log_file}
            
            # 送信トラフィック
            if stats['max_bps_send'] is not None:
                run_data['max_bps_send'] = stats['max_bps_send']
                run_data['avg_bps_send'] = stats['avg_bps_send']
                run_data['min_bps_send'] = stats['min_bps_send']
                results['max_bps_send'].append(stats['max_bps_send'])
                results['avg_bps_send'].append(stats['avg_bps_send'])
                results['min_bps_send'].append(stats['min_bps_send'])
            
            # 受信トラフィック
            if stats['max_bps_recv'] is not None:
                run_data['max_bps_recv'] = stats['max_bps_recv']
                run_data['avg_bps_recv'] = stats['avg_bps_recv']
                run_data['min_bps_recv'] = stats['min_bps_recv']
                results['max_bps_recv'].append(stats['max_bps_recv'])
                results['avg_bps_recv'].append(stats['avg_bps_recv'])
                results['min_bps_recv'].append(stats['min_bps_recv'])
            
            # 合計トラフィック
            if stats['max_bps_total'] is not None:
                run_data['max_bps_total'] = stats['max_bps_total']
                run_data['avg_bps_total'] = stats['avg_bps_total']
                run_data['min_bps_total'] = stats['min_bps_total']
                results['max_bps_total'].append(stats['max_bps_total'])
                results['avg_bps_total'].append(stats['avg_bps_total'])
                results['min_bps_total'].append(stats['min_bps_total'])
            
            results['runs'].append(run_data)
        else:
            # データなしの実行を記録（統計計算には含めない）
            results['runs_no_data'].append(log_file)
    
    # 統計サマリーを計算（データありの実行のみを使用）
    total_runs = len(log_files)
    data_count = len(results['runs'])
    no_data_count = len(results['runs_no_data'])
    
    if data_count > 0:
        summary = {
            'total_runs': total_runs,
            'data_count': data_count,
            'no_data_count': no_data_count
        }
        
        # 送信トラフィック統計
        if results['avg_bps_send']:
            summary['send'] = {
                'max_bps': {
                    'max': max(results['max_bps_send']),
                    'min': min(results['max_bps_send']),
                    'mean': statistics.mean(results['max_bps_send']),
                    'median': statistics.median(results['max_bps_send']),
                    'stdev': statistics.stdev(results['max_bps_send']) if len(results['max_bps_send']) > 1 else 0
                },
                'avg_bps': {
                    'max': max(results['avg_bps_send']),
                    'min': min(results['avg_bps_send']),
                    'mean': statistics.mean(results['avg_bps_send']),
                    'median': statistics.median(results['avg_bps_send']),
                    'stdev': statistics.stdev(results['avg_bps_send']) if len(results['avg_bps_send']) > 1 else 0
                },
                'min_bps': {
                    'max': max(results['min_bps_send']),
                    'min': min(results['min_bps_send']),
                    'mean': statistics.mean(results['min_bps_send']),
                    'median': statistics.median(results['min_bps_send']),
                    'stdev': statistics.stdev(results['min_bps_send']) if len(results['min_bps_send']) > 1 else 0
                }
            }
        
        # 受信トラフィック統計
        if results['avg_bps_recv']:
            summary['recv'] = {
                'max_bps': {
                    'max': max(results['max_bps_recv']),
                    'min': min(results['max_bps_recv']),
                    'mean': statistics.mean(results['max_bps_recv']),
                    'median': statistics.median(results['max_bps_recv']),
                    'stdev': statistics.stdev(results['max_bps_recv']) if len(results['max_bps_recv']) > 1 else 0
                },
                'avg_bps': {
                    'max': max(results['avg_bps_recv']),
                    'min': min(results['avg_bps_recv']),
                    'mean': statistics.mean(results['avg_bps_recv']),
                    'median': statistics.median(results['avg_bps_recv']),
                    'stdev': statistics.stdev(results['avg_bps_recv']) if len(results['avg_bps_recv']) > 1 else 0
                },
                'min_bps': {
                    'max': max(results['min_bps_recv']),
                    'min': min(results['min_bps_recv']),
                    'mean': statistics.mean(results['min_bps_recv']),
                    'median': statistics.median(results['min_bps_recv']),
                    'stdev': statistics.stdev(results['min_bps_recv']) if len(results['min_bps_recv']) > 1 else 0
                }
            }
        
        # 合計トラフィック統計
        if results['avg_bps_total']:
            summary['total'] = {
                'max_bps': {
                    'max': max(results['max_bps_total']),
                    'min': min(results['max_bps_total']),
                    'mean': statistics.mean(results['max_bps_total']),
                    'median': statistics.median(results['max_bps_total']),
                    'stdev': statistics.stdev(results['max_bps_total']) if len(results['max_bps_total']) > 1 else 0
                },
                'avg_bps': {
                    'max': max(results['avg_bps_total']),
                    'min': min(results['avg_bps_total']),
                    'mean': statistics.mean(results['avg_bps_total']),
                    'median': statistics.median(results['avg_bps_total']),
                    'stdev': statistics.stdev(results['avg_bps_total']) if len(results['avg_bps_total']) > 1 else 0
                },
                'min_bps': {
                    'max': max(results['min_bps_total']),
                    'min': min(results['min_bps_total']),
                    'mean': statistics.mean(results['min_bps_total']),
                    'median': statistics.median(results['min_bps_total']),
                    'stdev': statistics.stdev(results['min_bps_total']) if len(results['min_bps_total']) > 1 else 0
                }
            }
        
        results['summary'] = summary
    else:
        results['summary'] = {
            'total_runs': total_runs,
            'data_count': 0,
            'no_data_count': no_data_count,
            'note': 'データなし（すべての実行でトラフィックデータが記録されませんでした）'
        }
    
    return results

def print_summary_table(all_results):
    """サマリーテーブルを出力（データなしの実行は統計計算から除外）"""
    print("=" * 150)
    print("トラフィック帯域使用量統計サマリー（送受信を分けて記録）")
    print("=" * 150)
    print()
    
    # 送信トラフィック統計
    print("【送信トラフィック統計】")
    print(f"{'シナリオ':<15} {'データあり':<10} {'データなし':<10} {'最大(bps)':<20} {'平均(bps)':<20} {'最小(bps)':<20}")
    print("-" * 150)
    
    for result in sorted(all_results, key=lambda x: x['scenario']):
        summary = result['summary']
        data_count = summary.get('data_count', 0)
        
        if data_count > 0 and 'send' in summary:
            no_data_count = summary.get('no_data_count', 0)
            send_stats = summary['send']
            print(f"{result['scenario']:<15} {data_count:<10} {no_data_count:<10} "
                  f"{send_stats['max_bps']['mean']:<20,.0f} "
                  f"{send_stats['avg_bps']['mean']:<20,.0f} "
                  f"{send_stats['min_bps']['mean']:<20,.0f}")
        else:
            no_data_count = summary.get('no_data_count', 0)
            print(f"{result['scenario']:<15} {'0':<10} {no_data_count:<10} {'データなし':<20}")
    
    print()
    
    # 受信トラフィック統計
    print("【受信トラフィック統計】")
    print(f"{'シナリオ':<15} {'データあり':<10} {'データなし':<10} {'最大(bps)':<20} {'平均(bps)':<20} {'最小(bps)':<20}")
    print("-" * 150)
    
    for result in sorted(all_results, key=lambda x: x['scenario']):
        summary = result['summary']
        data_count = summary.get('data_count', 0)
        
        if data_count > 0 and 'recv' in summary:
            no_data_count = summary.get('no_data_count', 0)
            recv_stats = summary['recv']
            print(f"{result['scenario']:<15} {data_count:<10} {no_data_count:<10} "
                  f"{recv_stats['max_bps']['mean']:<20,.0f} "
                  f"{recv_stats['avg_bps']['mean']:<20,.0f} "
                  f"{recv_stats['min_bps']['mean']:<20,.0f}")
        else:
            no_data_count = summary.get('no_data_count', 0)
            print(f"{result['scenario']:<15} {'0':<10} {no_data_count:<10} {'データなし':<20}")
    
    print()
    
    # 合計トラフィック統計
    print("【合計トラフィック統計（送信+受信）】")
    print(f"{'シナリオ':<15} {'データあり':<10} {'データなし':<10} {'最大(bps)':<20} {'平均(bps)':<20} {'最小(bps)':<20}")
    print("-" * 150)
    
    for result in sorted(all_results, key=lambda x: x['scenario']):
        summary = result['summary']
        data_count = summary.get('data_count', 0)
        
        if data_count > 0 and 'total' in summary:
            no_data_count = summary.get('no_data_count', 0)
            total_stats = summary['total']
            print(f"{result['scenario']:<15} {data_count:<10} {no_data_count:<10} "
                  f"{total_stats['max_bps']['mean']:<20,.0f} "
                  f"{total_stats['avg_bps']['mean']:<20,.0f} "
                  f"{total_stats['min_bps']['mean']:<20,.0f}")
        else:
            no_data_count = summary.get('no_data_count', 0)
            print(f"{result['scenario']:<15} {'0':<10} {no_data_count:<10} {'データなし':<20}")
    
    print()
    print("=" * 150)
    print("詳細統計（平均値の統計）")
    print("=" * 150)
    print()
    
    # 送信トラフィックの詳細統計
    print("【送信トラフィック詳細統計】")
    print(f"{'シナリオ':<15} {'平均(bps)':<20} {'中央値(bps)':<20} {'標準偏差(bps)':<20} {'最小(bps)':<20} {'最大(bps)':<20}")
    print("-" * 150)
    
    for result in sorted(all_results, key=lambda x: x['scenario']):
        summary = result['summary']
        data_count = summary.get('data_count', 0)
        
        if data_count > 0 and 'send' in summary:
            avg_stats = summary['send']['avg_bps']
            print(f"{result['scenario']:<15} "
                  f"{avg_stats['mean']:<20,.0f} "
                  f"{avg_stats['median']:<20,.0f} "
                  f"{avg_stats['stdev']:<20,.0f} "
                  f"{avg_stats['min']:<20,.0f} "
                  f"{avg_stats['max']:<20,.0f}")
        else:
            print(f"{result['scenario']:<15} {'データなし（統計計算から除外）':<20}")
    
    print()
    
    # 受信トラフィックの詳細統計
    print("【受信トラフィック詳細統計】")
    print(f"{'シナリオ':<15} {'平均(bps)':<20} {'中央値(bps)':<20} {'標準偏差(bps)':<20} {'最小(bps)':<20} {'最大(bps)':<20}")
    print("-" * 150)
    
    for result in sorted(all_results, key=lambda x: x['scenario']):
        summary = result['summary']
        data_count = summary.get('data_count', 0)
        
        if data_count > 0 and 'recv' in summary:
            avg_stats = summary['recv']['avg_bps']
            print(f"{result['scenario']:<15} "
                  f"{avg_stats['mean']:<20,.0f} "
                  f"{avg_stats['median']:<20,.0f} "
                  f"{avg_stats['stdev']:<20,.0f} "
                  f"{avg_stats['min']:<20,.0f} "
                  f"{avg_stats['max']:<20,.0f}")
        else:
            print(f"{result['scenario']:<15} {'データなし（統計計算から除外）':<20}")
    
    print()
    print("=" * 150)
    print("単位変換（平均値の平均）")
    print("=" * 150)
    print()
    print(f"{'シナリオ':<15} {'送信平均(Mbps)':<20} {'受信平均(Mbps)':<20} {'合計平均(Mbps)':<20}")
    print("-" * 150)
    
    for result in sorted(all_results, key=lambda x: x['scenario']):
        summary = result['summary']
        data_count = summary.get('data_count', 0)
        
        if data_count > 0:
            send_mbps = summary.get('send', {}).get('avg_bps', {}).get('mean', 0) / 1000000 if 'send' in summary else 0
            recv_mbps = summary.get('recv', {}).get('avg_bps', {}).get('mean', 0) / 1000000 if 'recv' in summary else 0
            total_mbps = summary.get('total', {}).get('avg_bps', {}).get('mean', 0) / 1000000 if 'total' in summary else 0
            
            print(f"{result['scenario']:<15} "
                  f"{send_mbps:<20,.2f} "
                  f"{recv_mbps:<20,.2f} "
                  f"{total_mbps:<20,.2f}")
        else:
            print(f"{result['scenario']:<15} {'データなし（統計計算から除外）':<20}")

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 analyze_traffic_stats.py <log_directory>")
        sys.exit(1)
    
    log_dir = sys.argv[1]
    
    if not os.path.isdir(log_dir):
        print(f"Error: {log_dir} is not a directory", file=sys.stderr)
        sys.exit(1)
    
    # シナリオ名を抽出（P10_10_ON形式）
    scenarios = set()
    for f in os.listdir(log_dir):
        match = re.match(r'^(P\d+_\d+_ON)_run\d+\.log$', f)
        if match:
            scenarios.add(match.group(1))
    
    if not scenarios:
        print(f"Error: No scenario log files found in {log_dir}", file=sys.stderr)
        sys.exit(1)
    
    # 各シナリオを分析
    all_results = []
    for scenario in sorted(scenarios):
        result = analyze_scenario(scenario, log_dir)
        if result:
            all_results.append(result)
    
    # 結果を出力
    print_summary_table(all_results)
    
    # JSON形式でも出力
    output_json = os.path.join(log_dir, 'traffic_stats_summary.json')
    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(all_results, f, indent=2, ensure_ascii=False)
    
    print()
    print(f"詳細データは {output_json} に保存されました")

if __name__ == '__main__':
    main()

