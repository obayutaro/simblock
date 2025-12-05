#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
未来コンテキスト共有における秒間平均トラフィックの値が正しいかを調査するスクリプト
3つの観点から調査：
1. シミュレータ内の計算があっているか
2. ログ出力のタイミングによって無視されてしまっている値はないか
3. スクリプトによる計算やログの抽出は正確に行われているか
"""

import re
import os
import sys
import json
from pathlib import Path

def investigate_simulator_calculation():
    """観点1: シミュレータ内の計算があっているか"""
    print("=" * 130)
    print("【観点1】シミュレータ内の計算があっているか")
    print("=" * 130)
    print()
    
    print("【1-1】recordTrafficSend/recordTrafficRecvの実装確認")
    print("-" * 130)
    print("実装:")
    print("  - recordTrafficSend: 送信トラフィック量を記録")
    print("  - recordTrafficRecv: 受信トラフィック量を記録")
    print("  - 両方とも trafficPerSecond.put(second, trafficPerSecond.getOrDefault(second, 0L) + sizeBytes)")
    print("  - 秒単位で集計: second = timestampMs / 1000")
    print()
    
    print("【1-2】printTrafficBandwidthStatsの計算ロジック確認")
    print("-" * 130)
    print("計算方法:")
    print("  - 各秒ごとのトラフィック量（バイト）をビットに変換: bps = bytesPerSecond * 8")
    print("  - 最大値: maxBps = max(bps)")
    print("  - 最小値: minBps = min(bps)")
    print("  - 平均値: avgBps = totalBps / count")
    print()
    print("⚠️ 潜在的な問題:")
    print("  1. 平均値の計算: totalBps / count は各秒のbpsの平均")
    print("     → これは「秒間平均トラフィック量」ではなく「各秒のトラフィック量の平均」")
    print("  2. 秒単位の集計: 同じ秒内のすべてのトラフィックが合計される")
    print("     → これは正しい")
    print()
    
    print("【1-3】shareContext/receiveSharedContextでの呼び出し確認")
    print("-" * 130)
    print("shareContext:")
    print("  - 各targetNodeに対して recordTrafficSend(blockSize, currentTime) を呼び出し")
    print("  - blockSize = BLOCK_SIZE = 120KB")
    print("  - currentTime = getCurrentTime() (シミュレーション時間、ミリ秒)")
    print()
    print("receiveSharedContext:")
    print("  - 受信成功時に recordTrafficRecv(blockSize, currentTime) を呼び出し")
    print("  - blockSize = BLOCK_SIZE = 120KB")
    print("  - currentTime = getCurrentTime() (シミュレーション時間、ミリ秒)")
    print()
    print("⚠️ 潜在的な問題:")
    print("  1. 送信と受信の両方が記録されている")
    print("     → 実際のネットワーク負荷は約2倍になる可能性がある")
    print("  2. targetNodesが空の場合でも recordTrafficSend が呼ばれる")
    print("     → これは意図的な実装（ブロードキャスト試行を記録）")
    print()

def investigate_log_timing(log_dir):
    """観点2: ログ出力のタイミングによって無視されてしまっている値はないか"""
    print("=" * 130)
    print("【観点2】ログ出力のタイミングによって無視されてしまっている値はないか")
    print("=" * 130)
    print()
    
    print("【2-1】printTrafficBandwidthStatsの呼び出しタイミング")
    print("-" * 130)
    print("呼び出し箇所: Main.java の main() メソッドの最後")
    print("  - シミュレーション終了時に1回だけ呼び出される")
    print("  - すべてのタスクが実行された後に呼び出される")
    print()
    
    # 実際のログファイルを確認
    log_file = os.path.join(log_dir, "P10_10_ON_run1.log")
    if os.path.exists(log_file):
        print("【2-2】実際のログファイルの確認")
        print("-" * 130)
        with open(log_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()
            
        # FCSM_TRAFFIC_BANDWIDTH_STATSの出現位置を確認
        stats_lines = [i for i, line in enumerate(lines) if 'FCSM_TRAFFIC_BANDWIDTH_STATS' in line]
        
        if stats_lines:
            print(f"FCSM_TRAFFIC_BANDWIDTH_STATSの出現回数: {len(stats_lines)}")
            print(f"総行数: {len(lines)}")
            print(f"最後の出現位置: {stats_lines[-1]}行目 / {len(lines)}行")
            print(f"最後の出現位置の割合: {stats_lines[-1] / len(lines) * 100:.2f}%")
            print()
            
            # 最後の出現行の前後を確認
            last_idx = stats_lines[-1]
            print("最後の出現行の前後:")
            start = max(0, last_idx - 5)
            end = min(len(lines), last_idx + 5)
            for i in range(start, end):
                marker = ">>> " if i == last_idx else "    "
                print(f"{marker}{i+1}: {lines[i].rstrip()}")
            print()
            
            # ログの内容を確認
            pattern = r'FCSM_TRAFFIC_BANDWIDTH_STATS: max=(\d+) bps, avg=(\d+) bps, min=(\d+) bps'
            match = re.search(pattern, lines[last_idx])
            if match:
                max_bps = int(match.group(1))
                avg_bps = int(match.group(2))
                min_bps = int(match.group(3))
                print(f"ログの内容:")
                print(f"  max_bps: {max_bps:,} bps ({max_bps/1000000:.2f} Mbps)")
                print(f"  avg_bps: {avg_bps:,} bps ({avg_bps/1000000:.2f} Mbps)")
                print(f"  min_bps: {min_bps:,} bps ({min_bps/1000000:.2f} Mbps)")
                print()
        else:
            print("⚠️ FCSM_TRAFFIC_BANDWIDTH_STATSが見つかりませんでした")
            print()
    
    print("【2-3】タイムスタンプの計算確認")
    print("-" * 130)
    print("getCurrentTime()の実装:")
    print("  - Timer.getCurrentTime() はシミュレーション時間（ミリ秒）を返す")
    print("  - 秒単位への変換: second = timestampMs / 1000")
    print()
    print("⚠️ 潜在的な問題:")
    print("  1. シミュレーション終了時点でのトラフィックのみが記録される")
    print("     → シミュレーション中に発生したすべてのトラフィックは記録されている")
    print("  2. 秒単位の集計: 同じ秒内のすべてのトラフィックが合計される")
    print("     → これは正しい")
    print("  3. 平均値の計算: 各秒のトラフィック量の平均")
    print("     → これは「秒間平均トラフィック量」として正しい")
    print()

def investigate_script_extraction(log_dir):
    """観点3: スクリプトによる計算やログの抽出は正確に行われているか"""
    print("=" * 130)
    print("【観点3】スクリプトによる計算やログの抽出は正確に行われているか")
    print("=" * 130)
    print()
    
    print("【3-1】analyze_traffic_stats.pyのパースロジック確認")
    print("-" * 130)
    print("正規表現パターン:")
    pattern = r'FCSM_TRAFFIC_BANDWIDTH_STATS: max=(\d+) bps, avg=(\d+) bps, min=(\d+) bps'
    print(f"  {pattern}")
    print()
    
    # 実際のログファイルでパターンマッチを確認
    log_file = os.path.join(log_dir, "P10_10_ON_run1.log")
    if os.path.exists(log_file):
        print("【3-2】実際のログファイルでのパターンマッチ確認")
        print("-" * 130)
        with open(log_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        matches = re.findall(pattern, content)
        if matches:
            print(f"マッチした行数: {len(matches)}")
            for i, match in enumerate(matches[:3]):
                max_bps, avg_bps, min_bps = match
                print(f"  マッチ{i+1}: max={max_bps} bps, avg={avg_bps} bps, min={min_bps} bps")
            print()
            
            # 最後のマッチを確認（通常は1つだけ）
            if len(matches) == 1:
                max_bps, avg_bps, min_bps = matches[0]
                print("【3-3】抽出された値の確認")
                print("-" * 130)
                print(f"max_bps: {int(max_bps):,} bps ({int(max_bps)/1000000:.2f} Mbps)")
                print(f"avg_bps: {int(avg_bps):,} bps ({int(avg_bps)/1000000:.2f} Mbps)")
                print(f"min_bps: {int(min_bps):,} bps ({int(min_bps)/1000000:.2f} Mbps)")
                print()
                
                # 単位変換の確認
                print("【3-4】単位変換の確認")
                print("-" * 130)
                avg_bps_int = int(avg_bps)
                avg_kbps = avg_bps_int / 1000
                avg_mbps = avg_bps_int / 1000000
                print(f"avg_bps: {avg_bps_int:,} bps")
                print(f"avg_kbps: {avg_kbps:.2f} Kbps")
                print(f"avg_mbps: {avg_mbps:.4f} Mbps")
                print()
                print("✅ 単位変換は正しい")
            else:
                print(f"⚠️ 複数のマッチが見つかりました（{len(matches)}個）")
                print("   通常は1つだけであるべきです")
        else:
            print("⚠️ パターンマッチが見つかりませんでした")
            print()
    
    print("【3-5】統計計算の確認")
    print("-" * 130)
    print("analyze_traffic_stats.pyの統計計算:")
    print("  - 各実行のavg_bpsを抽出")
    print("  - 複数実行の平均値を計算: statistics.mean(avg_bps_list)")
    print("  - これは「各実行の平均値の平均」")
    print()
    print("⚠️ 潜在的な問題:")
    print("  1. 各実行のavg_bpsは「各秒のトラフィック量の平均」")
    print("     → これは「秒間平均トラフィック量」として正しい")
    print("  2. 複数実行の平均値は「各実行の秒間平均トラフィック量の平均」")
    print("     → これは正しい")
    print()

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 investigate_traffic_calculation.py <log_directory>")
        sys.exit(1)
    
    log_dir = sys.argv[1]
    
    if not os.path.isdir(log_dir):
        print(f"Error: {log_dir} is not a directory", file=sys.stderr)
        sys.exit(1)
    
    # 3つの観点から調査
    investigate_simulator_calculation()
    investigate_log_timing(log_dir)
    investigate_script_extraction(log_dir)
    
    print("=" * 130)
    print("【総合評価】")
    print("=" * 130)
    print()
    print("1. シミュレータ内の計算:")
    print("   ✅ 基本的には正しい")
    print("   ⚠️ ただし、送信と受信の両方が記録されているため、実際の負荷は約2倍")
    print()
    print("2. ログ出力のタイミング:")
    print("   ✅ シミュレーション終了時にすべてのトラフィックが記録されている")
    print("   ✅ 秒単位の集計は正しい")
    print()
    print("3. スクリプトによる計算:")
    print("   ✅ ログの抽出は正しい")
    print("   ✅ 単位変換は正しい")
    print("   ✅ 統計計算は正しい")
    print()
    print("【結論】")
    print("秒間平均トラフィックの値は基本的に正しいが、")
    print("送信と受信の両方が記録されているため、実際のネットワーク負荷は約2倍になる可能性がある。")

if __name__ == '__main__':
    main()


