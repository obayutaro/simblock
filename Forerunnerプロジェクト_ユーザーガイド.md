# Forerunnerプロジェクト ユーザーガイド

## 📋 目次

1. [プロジェクト概要](#プロジェクト概要)
2. [システム構成](#システム構成)
3. [Forerunner技術の仕組み](#forerunner技術の仕組み)
4. [パラメータ設定](#パラメータ設定)
5. [シミュレーション実行](#シミュレーション実行)
6. [評価指標と分析](#評価指標と分析)
7. [トラブルシューティング](#トラブルシューティング)
8. [よくある質問](#よくある質問)

---

## 🎯 プロジェクト概要

### プロジェクトの目的
Forerunnerプロジェクトは、ブロックチェーンネットワークにおける**未来コンテキスト共有技術**の効果を評価するためのシミュレーション環境です。SimBlockをベースに、投機的実行によるブロック伝播時間の短縮効果を測定します。

### 主要機能
- **未来コンテキスト共有**: 将来のブロック情報を事前に共有
- **投機的実行**: 予測に基づく高速処理
- **コンポーネント再利用**: 差分キャッシュによる効率化
- **Pre-play機能**: トランザクション処理の事前実行

### 技術的特徴
- **予測精度**: 87.19%の完全予測率
- **スピードアップ**: 最大11.33倍の処理速度向上
- **制約満足**: 99.15%の制約集合満足率

---

## 🏗️ システム構成

### ディレクトリ構造
```
simblock_test/
├── simulator/                    # シミュレーター本体
│   ├── src/main/java/simblock/
│   │   ├── block/               # ブロック関連クラス
│   │   ├── forerunner/          # Forerunner技術実装
│   │   ├── message/            # メッセージ処理
│   │   ├── node/               # ノード実装
│   │   ├── settings/           # 設定管理
│   │   ├── simulator/          # シミュレーター本体
│   │   ├── task/               # タスク管理
│   │   └── util/               # ユーティリティ
│   └── build/                  # ビルド成果物
├── docs/                       # ドキュメント
├── 評価シナリオと評価指標.md    # 評価フレームワーク
├── ベースライン比較分析レポート.md # 分析結果
└── *.log                       # シミュレーション結果
```

### 主要コンポーネント

#### 1. ノード実装
- **`Node.java`**: 基本ノードクラス
- **`ForerunnerNode.java`**: Forerunner機能付きノード
- **`AbstractNode.java`**: 抽象ノードクラス

#### 2. Forerunner技術
- **`FutureContextManager.java`**: 未来コンテキスト管理
- **`FutureContextSharingManager.java`**: 未来コンテキスト共有
- **`PreplayManager.java`**: Pre-play機能
- **`DiffCache.java`**: 差分キャッシュ

#### 3. 設定管理
- **`SimulationConfiguration.java`**: シミュレーション設定
- **`ForerunnerConfig.java`**: Forerunner設定
- **`NetworkConfiguration.java`**: ネットワーク設定

---

## ⚙️ Forerunner技術の仕組み

### 基本概念

#### 未来コンテキスト共有
```
通常の処理:
ブロック受信 → 処理実行 → 結果出力

Forerunner処理:
ブロック受信 → 未来コンテキスト適用 → 高速処理 → 結果出力
```

#### 投機的実行の流れ
1. **予測**: 将来のブロック情報を予測
2. **共有**: 予測情報をネットワーク内で共有
3. **実行**: 予測に基づく高速処理
4. **検証**: 実際のブロックで予測を検証

### 技術的詳細

#### 予測精度データ（2025年測定値）
| 予測タイプ | 割合 | スピードアップ |
|-----------|------|---------------|
| Perfect Prediction | 87.19% | 11.33× |
| Imperfect Prediction | 11.96% | 4.55× |
| Missed Prediction | 0.85% | 1.21× |

#### 処理フロー
```java
// 1. トランザクション受信時
recvTxMessage() {
    // Pre-play実行
    if (Math.random() < 0.9915) {
        ppm.preplay(getBlock(), tx);
    }
    
    // 未来コンテキスト共有
    fcsm.onTransactionPoolUpdate(newTransactions);
}

// 2. ブロック受信時
recvBlockMessage() {
    // Future Context適用
    FutureContextManager.Outcome oc = fcm.evaluate(blk);
    double factorFC = FutureContextManager.factor(oc);
    
    // Component Reuse
    double reuseCR = diffCache.applyReusableDiff(blk);
    double factorCR = 1.0 - reuseCR * 0.8;
    
    // 最適化処理時間計算
    long effectiveTime = calculateOptimizedTime(blk, factorFC, factorCR);
}
```

---

## 🔧 パラメータ設定

### 環境変数による設定

#### Forerunner適用率
```bash
# 方法1: システムプロパティ
-Dforerunner.ratio=0.7

# 方法2: 環境変数
export FORERUNNER_RATIO=0.7
```

#### 低速ノード割合
```bash
# 環境変数
export SLOW_NODE_RATIO=0.3
```

#### ブロードキャスト範囲
```bash
# 環境変数
export BROADCAST_SCOPE=NEIGHBORS_ONLY
```

### シミュレーション設定

#### 基本パラメータ（SimulationConfiguration.java）
| パラメータ | 値 | 説明 |
|-----------|----|------|
| `NUM_OF_NODES` | 1000 | ノード数 |
| `INTERVAL` | 13000ms | ブロック生成間隔 |
| `END_BLOCK_HEIGHT` | 50 | 終了ブロック高 |
| `BLOCK_SIZE` | 50KB | ブロックサイズ |
| `AVERAGE_MINING_POWER` | 800000 | 平均マイニングパワー |

#### Forerunner設定（ForerunnerConfig.java）
| パラメータ | 値 | 説明 |
|-----------|----|------|
| `PERFECT_PREDICTION_RATE` | 0.8719 | 完全予測率 |
| `IMPERFECT_PREDICTION_RATE` | 0.1196 | 不完全予測率 |
| `MISSED_PREDICTION_RATE` | 0.0085 | 予測失敗率 |
| `PERFECT_SPEEDUP` | 11.33 | 完全予測時のスピードアップ |
| `IMPERFECT_SPEEDUP` | 4.55 | 不完全予測時のスピードアップ |
| `MISSED_SPEEDUP` | 1.21 | 予測失敗時のスピードアップ |

### 推奨設定

#### 最適パラメータ
```bash
# 最適設定（scenario_a4相当）
FORERUNNER_RATIO=0.7      # 70%適用率
SLOW_NODE_RATIO=0.3      # 30%低速ノード
BROADCAST_SCOPE=NEIGHBORS_ONLY
```

#### 段階的導入設定
```bash
# 第1段階: 30%適用率
FORERUNNER_RATIO=0.3

# 第2段階: 50%適用率  
FORERUNNER_RATIO=0.5

# 第3段階: 70%適用率（最適）
FORERUNNER_RATIO=0.7

# 第4段階: 90%適用率
FORERUNNER_RATIO=0.9
```

---

## 🚀 シミュレーション実行

### 基本的な実行方法

#### 1. 環境準備
```bash
# Java 11以上が必要
java -version

# Gradleの確認
./gradlew --version
```

#### 2. 単一シナリオ実行
```bash
# 基本実行
./gradlew :simulator:run --args="100 1000 10000"

# パラメータ付き実行
FORERUNNER_RATIO=0.7 SLOW_NODE_RATIO=0.3 ./gradlew :simulator:run --args="100 1000 10000"
```

#### 3. ログ出力
```bash
# ログファイルに出力
FORERUNNER_RATIO=0.7 SLOW_NODE_RATIO=0.3 ./gradlew :simulator:run --args="100 1000 10000" > scenario_a4_results.log 2>&1
```

### 一括シナリオ実行

#### Pythonスクリプト使用
```bash
# 全シナリオ実行
python run_all_scenarios.py

# 特定シナリオ実行
python run_all_scenarios.py --scenario a4
```

#### PowerShellスクリプト使用
```powershell
# Windows環境での実行
.\run_all_scenarios.ps1
```

### 実行パラメータ

#### コマンドライン引数
```bash
./gradlew :simulator:run --args="<node_count> <tx_count> <simulation_time>"
```

| 引数 | 説明 | 推奨値 |
|------|------|--------|
| `node_count` | ノード数 | 1000 |
| `tx_count` | トランザクション数 | 1000 |
| `simulation_time` | シミュレーション時間 | 10000 |

---

## 📊 評価指標と分析

### 主要評価指標

#### 1. ブロック伝播時間
```python
propagation_time = last_timestamp - first_timestamp
```
- **単位**: ミリ秒（ms）
- **目標**: 短縮（値が小さいほど良い）
- **測定方法**: 最初のノードから最後のノードまでの時間差

#### 2. ネットワーク効率
```python
network_efficiency = propagation_time / node_count
```
- **単位**: ms/ノード
- **目標**: 改善（値が小さいほど効率的）
- **測定方法**: 伝播時間をノード数で除算

### 分析スクリプト

#### ベースライン比較分析
```bash
# 全シナリオのベースライン比較
python analyze_with_baseline.py
```

#### 結果ファイル
- **`baseline_comparison_results.json`**: 詳細分析結果
- **`ベースライン比較分析レポート.md`**: 分析レポート

### 評価シナリオ

#### シナリオA: Forerunner適用率評価
| シナリオ | 適用率 | 期待効果 |
|---------|-------|----------|
| A1 | 10% | ベースライン比較 |
| A2 | 30% | 小幅改善 |
| A3 | 50% | 中程度改善 |
| A4 | 70% | **最大改善** |
| A5 | 90% | 高程度改善 |

#### シナリオB: ネットワーク構成評価
| シナリオ | 低速ノード割合 | 目的 |
|---------|---------------|------|
| B1 | 30% | 低負荷環境 |
| B2 | 50% | 標準環境 |
| B3 | 70% | 高負荷環境 |

#### シナリオC: 統合評価
| シナリオ | 適用率 | 低速ノード割合 | 目的 |
|---------|-------|---------------|------|
| C1-C16 | 10-90% | 30-70% | パラメータ最適化 |

---

## 🔧 トラブルシューティング

### よくある問題

#### 1. Javaバージョンエラー
```bash
# エラー: Java 11以上が必要
Error: Java version 8 is not supported

# 解決方法
java -version  # バージョン確認
# Java 11以上にアップグレード
```

#### 2. メモリ不足エラー
```bash
# エラー: OutOfMemoryError
java.lang.OutOfMemoryError: Java heap space

# 解決方法
export GRADLE_OPTS="-Xmx4g"
./gradlew :simulator:run --args="100 1000 10000"
```

#### 3. ログファイルが見つからない
```bash
# エラー: ログファイルが見つからない
FileNotFoundError: scenario_a4_results.log

# 解決方法
# 1. シミュレーションが正常に完了したか確認
# 2. ファイル名の確認
# 3. 権限の確認
```

#### 4. パラメータが反映されない
```bash
# 問題: 環境変数が反映されない
echo $FORERUNNER_RATIO  # 確認
export FORERUNNER_RATIO=0.7  # 再設定
```

### デバッグ方法

#### ログレベルの調整
```java
// ForerunnerConfig.javaでデバッグログを有効化
public static void printPredictionStats() {
    System.out.println("=== Forerunner Prediction Statistics ===");
    // デバッグ情報を出力
}
```

#### 詳細ログの確認
```bash
# 詳細な実行ログを確認
./gradlew :simulator:run --args="100 1000 10000" --debug
```

---

## ❓ よくある質問

### Q1: Forerunner技術とは何ですか？
**A**: Forerunner技術は、ブロックチェーンネットワークにおいて未来のブロック情報を予測し、投機的実行により処理速度を向上させる技術です。87.19%の完全予測率で最大11.33倍のスピードアップを実現します。

### Q2: 最適なパラメータ設定は？
**A**: 推奨設定は以下の通りです：
- Forerunner適用率: 70%
- 低速ノード割合: 30%
- ブロードキャスト範囲: NEIGHBORS_ONLY

### Q3: シミュレーションの実行時間は？
**A**: 標準的な設定（1000ノード、1000トランザクション）では約5-10分程度です。パラメータやハードウェア性能により変動します。

### Q4: 結果の解釈方法は？
**A**: 以下の指標を確認してください：
- **ブロック伝播時間**: 短いほど良い
- **ネットワーク効率**: 小さいほど効率的
- **改善率**: ベースラインとの比較

### Q5: 大規模ネットワークでの実行は可能ですか？
**A**: 現在は1000ノード規模で検証済みです。より大規模なネットワークでの検証は今後の研究課題です。

### Q6: 実環境での使用は可能ですか？
**A**: 現在はシミュレーション環境での評価のみです。実環境での性能検証は今後の課題となります。

---

## 📞 サポート

### 技術的な問題
- **GitHub Issues**: プロジェクトのIssuesページで報告
- **メール**: dsg-titech at googlegroups.com

### ドキュメント
- **評価シナリオと評価指標.md**: 詳細な評価フレームワーク
- **ベースライン比較分析レポート.md**: 最新の分析結果
- **README.md**: プロジェクト概要

### ライセンス
SimBlockはApache License, Version 2.0でライセンスされています。

---

**最終更新**: 2025-08-31  
**バージョン**: 1.0  
**作成者**: Forerunnerプロジェクトチーム
