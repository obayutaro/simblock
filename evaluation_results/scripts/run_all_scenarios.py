#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Forerunnerプロジェクト 全シナリオ実行スクリプト
評価シナリオと評価指標ファイルに従って24シナリオを実行
"""

import subprocess
import os
import time
import json
from datetime import datetime

def run_scenario(scenario_name, forerunner_ratio, slow_node_ratio, broadcast_scope="NEIGHBORS_ONLY"):
    """シナリオを実行"""
    print(f"=== {scenario_name} を実行中 ===")
    print(f"Forerunner適用率: {forerunner_ratio*100:.0f}%")
    print(f"低速ノード割合: {slow_node_ratio*100:.0f}%")
    
    # 環境変数を設定
    env = os.environ.copy()
    env['FORERUNNER_RATIO'] = str(forerunner_ratio)
    env['SLOW_NODE_RATIO'] = str(slow_node_ratio)
    env['BROADCAST_SCOPE'] = broadcast_scope
    
    # コマンドを実行
    cmd = ["./gradlew", ":simulator:run", "--args=100 1000 10000"]
    
    try:
        start_time = time.time()
        result = subprocess.run(
            cmd,
            env=env,
            capture_output=True,
            text=True,
            timeout=300  # 5分のタイムアウト
        )
        end_time = time.time()
        
        # 結果をログファイルに保存
        log_filename = f"{scenario_name}_results.log"
        with open(log_filename, 'w', encoding='utf-8') as f:
            f.write(result.stdout)
            if result.stderr:
                f.write("\n=== STDERR ===\n")
                f.write(result.stderr)
        
        execution_time = end_time - start_time
        print(f"✅ {scenario_name} 完了 (実行時間: {execution_time:.2f}秒)")
        
        if result.returncode == 0:
            print(f"  ログファイル: {log_filename}")
            return True, log_filename
        else:
            print(f"❌ {scenario_name} 失敗 (終了コード: {result.returncode})")
            return False, None
            
    except subprocess.TimeoutExpired:
        print(f"❌ {scenario_name} タイムアウト")
        return False, None
    except Exception as e:
        print(f"❌ {scenario_name} エラー: {e}")
        return False, None

def main():
    """メイン実行関数"""
    print("=== Forerunnerプロジェクト 全シナリオ実行開始 ===")
    print(f"実行開始時刻: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    
    # シナリオ定義（評価シナリオと評価指標ファイルに従う）
    scenarios = [
        # シナリオA: Forerunner適用率の段階的評価（5シナリオ）
        ("scenario_a1", 0.1, 0.5),  # 10%適用率, 50%低速ノード
        ("scenario_a2", 0.3, 0.5),  # 30%適用率, 50%低速ノード
        ("scenario_a3", 0.5, 0.5),  # 50%適用率, 50%低速ノード
        ("scenario_a4", 0.7, 0.5),  # 70%適用率, 50%低速ノード
        ("scenario_a5", 0.9, 0.5),  # 90%適用率, 50%低速ノード
        
        # シナリオB: ネットワーク構成の影響評価（3シナリオ）
        ("scenario_b1", 0.7, 0.1),  # 70%適用率, 10%低速ノード
        ("scenario_b2", 0.7, 0.5),  # 70%適用率, 50%低速ノード
        ("scenario_b3", 0.7, 0.9),  # 70%適用率, 90%低速ノード
        
        # シナリオC: 統合評価シナリオ（16シナリオ）
        ("scenario_c1", 0.9, 0.1),  # 90%適用率, 10%低速ノード
        ("scenario_c2", 0.9, 0.3),  # 90%適用率, 30%低速ノード
        ("scenario_c3", 0.9, 0.5),  # 90%適用率, 50%低速ノード
        ("scenario_c4", 0.9, 0.7),  # 90%適用率, 70%低速ノード
        ("scenario_c5", 0.7, 0.1),  # 70%適用率, 10%低速ノード
        ("scenario_c6", 0.7, 0.3),  # 70%適用率, 30%低速ノード
        ("scenario_c7", 0.7, 0.5),  # 70%適用率, 50%低速ノード
        ("scenario_c8", 0.7, 0.7),  # 70%適用率, 70%低速ノード
        ("scenario_c9", 0.5, 0.1),  # 50%適用率, 10%低速ノード
        ("scenario_c10", 0.5, 0.3), # 50%適用率, 30%低速ノード
        ("scenario_c11", 0.5, 0.5), # 50%適用率, 50%低速ノード
        ("scenario_c12", 0.5, 0.7), # 50%適用率, 70%低速ノード
        ("scenario_c13", 0.3, 0.1), # 30%適用率, 10%低速ノード
        ("scenario_c14", 0.3, 0.3), # 30%適用率, 30%低速ノード
        ("scenario_c15", 0.3, 0.5), # 30%適用率, 50%低速ノード
        ("scenario_c16", 0.3, 0.7), # 30%適用率, 70%低速ノード
    ]
    
    # 実行結果を記録
    results = {
        'execution_info': {
            'start_time': datetime.now().isoformat(),
            'total_scenarios': len(scenarios),
            'environment': 'Windows'
        },
        'scenarios': {}
    }
    
    success_count = 0
    total_count = len(scenarios)
    
    print(f"\n📊 実行予定シナリオ数: {total_count}")
    print("=" * 60)
    
    for i, (scenario_name, forerunner_ratio, slow_node_ratio) in enumerate(scenarios, 1):
        print(f"\n[{i:2d}/{total_count}] ", end="")
        
        success, log_file = run_scenario(scenario_name, forerunner_ratio, slow_node_ratio)
        
        # 結果を記録
        results['scenarios'][scenario_name] = {
            'forerunner_ratio': forerunner_ratio,
            'slow_node_ratio': slow_node_ratio,
            'success': success,
            'log_file': log_file,
            'execution_time': datetime.now().isoformat()
        }
        
        if success:
            success_count += 1
        
        # シナリオ間の待機時間
        if i < total_count:
            print("  次のシナリオまで5秒待機...")
            time.sleep(5)
    
    # 実行結果の保存
    results['execution_info']['end_time'] = datetime.now().isoformat()
    results['execution_info']['success_count'] = success_count
    results['execution_info']['failure_count'] = total_count - success_count
    results['execution_info']['success_rate'] = success_count / total_count * 100
    
    # JSONファイルに保存
    with open('scenario_execution_results.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    
    print(f"\n" + "=" * 60)
    print(f"=== 実行完了 ===")
    print(f"実行終了時刻: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"成功: {success_count}/{total_count} シナリオ")
    print(f"成功率: {success_count/total_count*100:.1f}%")
    print(f"結果ファイル: scenario_execution_results.json")
    
    # 成功したシナリオの一覧
    if success_count > 0:
        print(f"\n✅ 成功したシナリオ:")
        for scenario_name, info in results['scenarios'].items():
            if info['success']:
                print(f"  - {scenario_name}")
    
    # 失敗したシナリオの一覧
    if success_count < total_count:
        print(f"\n❌ 失敗したシナリオ:")
        for scenario_name, info in results['scenarios'].items():
            if not info['success']:
                print(f"  - {scenario_name}")

if __name__ == "__main__":
    main()
