# NEIGHBORS_ONLY仕様書

## 概要

`NEIGHBORS_ONLY`は、未来コンテキスト共有のブロードキャスト範囲を「隣接ノードのみ」に限定するモードです。環境変数`BROADCAST_SCOPE=NEIGHBORS_ONLY`で設定されます。

## 設定方法

```bash
export BROADCAST_SCOPE=NEIGHBORS_ONLY
```

デフォルト値は`NEIGHBORS_ONLY`です（環境変数が設定されていない場合も`NEIGHBORS_ONLY`が使用されます）。

## 動作仕様

### 1. ブロードキャスト範囲の決定

```java:1174:1187:simulator/src/main/java/simblock/forerunner/FutureContextSharingManager.java
private List<Node> getTargetNodesForBroadcast() {
  switch (broadcastScope) {
    case NEIGHBORS_ONLY:
      return getNeighborNodes();
    // ... 他のケース
    default:
      return getNeighborNodes(); // デフォルトは隣接ノードのみ
  }
}
```

### 2. 隣接ノードの取得

```java:828:845:simulator/src/main/java/simblock/forerunner/FutureContextSharingManager.java
private List<Node> getNeighborNodes() {
  List<Node> neighbors = new ArrayList<>();
  
  try {
    // ノードの隣接ノードリストを取得
    Method getNeighborsMethod = ownerNode.getClass().getMethod("getNeighbors");
    @SuppressWarnings("unchecked")
    List<Node> neighborList = (List<Node>) getNeighborsMethod.invoke(ownerNode);
    
    if (neighborList != null) {
      neighbors.addAll(neighborList);
    }
  } catch (Exception e) {
    System.err.println("Warning: Could not get neighbor nodes: " + e.getMessage());
  }
  
  return neighbors;
}
```

**重要なポイント**:
- **直接接続されているノードのみ**が対象（1ホップ先）
- ノードタイプ（FORERUNNER_HIGH、FORERUNNER_LOW、NON_FORERUNNER等）によるフィルタリングは**行わない**
- Forerunner適用の有無に関わらず、すべての隣接ノードに送信される

### 3. 未来コンテキスト共有の実行

```java:532:604:simulator/src/main/java/simblock/forerunner/FutureContextSharingManager.java
private void shareContext(EnhancedFutureContext context) {
  if (!sharingEnabled) {
    return; // 共有無効時は何もしない
  }

  // REQUEST_BASEDの場合は要求ベースの送信
  if (broadcastScope == BroadcastScope.REQUEST_BASED) {
    // ... REQUEST_BASEDの処理
    return;
  }

  // 既存のブロードキャスト方式（NEIGHBORS_ONLY等）
  // レート制限（共有嵐の抑制）
  long now = System.currentTimeMillis();
  if (now - lastShareEpochMs < minShareIntervalMs) {
    return;
  }
  lastShareEpochMs = now;
  
  // 共有コンテキストとして保存
  sharedContexts.put(context.getBlockIndex(), context);

  // ブロードキャスト範囲に応じて送信
  List<Node> targetNodes = getTargetNodesForBroadcast();
  
  // ブロードキャスト先が空の場合でも、ブロードキャストの試行自体はトラフィックとして記録
  if (targetNodes.isEmpty()) {
    // ブロードキャスト試行をトラフィックとして記録
    long currentTime = getCurrentTime();
    long blockSize = simblock.settings.SimulationConfiguration.BLOCK_SIZE;
    simblock.util.DebugLogger.recordTrafficSend(blockSize, currentTime);
    return;
  }

  for (Node targetNode : targetNodes) {
    // トラフィックログ: 各送信イベントごとに1回出力
    simblock.util.DebugLogger.trafficLogSend();
    // トラフィック量を記録（BLOCK_SIZEを使用）
    long currentTime = getCurrentTime();
    long blockSize = simblock.settings.SimulationConfiguration.BLOCK_SIZE;
    simblock.util.DebugLogger.recordTrafficSend(blockSize, currentTime);
    // 反射の直同期呼び出しを避け、タイマー遅延で送信
    long jitter = 1L + (long) (random.nextDouble() * 4L); // 1〜5msジッタ
    ExecutionDelayTask sendTask =
        new ExecutionDelayTask(ownerNode, jitter) {
          @Override
          public void run() {
            sendContextToNode(context, targetNode);
          }
        };
    putTask(sendTask);
  }
}
```

### 4. レート制限

- **最小共有間隔**: `FORERUNNER_MIN_SHARE_INTERVAL_MS`（デフォルト: 100ms）
- この間隔より短い間隔での共有は無視される（共有嵐の抑制）

### 5. 送信タイミング

- **ジッタ**: 1〜5msのランダム遅延を追加して送信
- **非同期送信**: `ExecutionDelayTask`を使用して非同期に送信

## 動作フロー

### 高速ノード（FORERUNNER_HIGH）の動作

1. **未来コンテキスト生成**
   - トランザクションプール更新時（`onTransactionPoolUpdate()`）
   - ブロック受信時（`FORERUNNER_SHARE_ONLY_ON_TX=false`の場合）

2. **未来コンテキスト共有**
   - `shareContext()`が呼ばれる
   - レート制限チェック（`minShareIntervalMs`）
   - `getTargetNodesForBroadcast()`で隣接ノードリストを取得
   - **すべての隣接ノード**に未来コンテキストを送信

### 低速ノード（FORERUNNER_LOW）の動作

1. **未来コンテキスト受信**
   - 隣接ノードから送信された未来コンテキストを受信
   - `receiveSharedContext()`で処理

2. **未来コンテキスト生成**
   - トランザクションプール更新時（`onTransactionPoolUpdate()`）
   - ブロック受信時（`FORERUNNER_SHARE_ONLY_ON_TX=false`の場合）

3. **未来コンテキスト共有**
   - 生成した未来コンテキストを**すべての隣接ノード**に送信

## REQUEST_BASEDとの違い

| 項目 | NEIGHBORS_ONLY | REQUEST_BASED |
|------|----------------|---------------|
| **送信先** | すべての隣接ノード | 要求してきたノードのみ |
| **送信タイミング** | 未来コンテキスト生成時 | 要求を受けた時 |
| **トラフィック** | 隣接ノード数に比例 | 要求数に比例 |
| **受動的動作** | なし | 低速ノードが要求を送信 |

## トラフィック特性

### 送信トラフィック

- **送信先数**: 隣接ノード数に比例
- **送信頻度**: 未来コンテキスト生成頻度に比例
- **レート制限**: `FORERUNNER_MIN_SHARE_INTERVAL_MS`で制限

### 受信トラフィック

- **受信数**: 隣接ノード数 × 各ノードの送信頻度
- **フィルタリング**: なし（すべての隣接ノードから受信）

## 評価結果からの知見

### 伝播時間

- REQUEST_BASEDと比較して、伝播時間の差は**非常に小さい**（±0.5%程度）
- 多くのシナリオでREQUEST_BASEDがやや優位だが、差は統計的に有意ではない可能性がある

### トラフィック

- **送信トラフィック**: REQUEST_BASEDと比較して**大幅に多い**
  - 例: P10_10_ONで96.06%削減（REQUEST_BASEDが優位）
  - 例: P100_90_ONで92.80%増加（NEIGHBORS_ONLYが劣位）
- **受信トラフィック**: 同様にREQUEST_BASEDと比較して多い傾向

### 適用シナリオ

- **NEIGHBORS_ONLYが適している場合**:
  - ネットワークトポロジーが密接している場合
  - 隣接ノード数が少ない場合
  - リアルタイム性が重視される場合

- **REQUEST_BASEDが適している場合**:
  - トラフィック削減が重視される場合
  - 隣接ノード数が多い場合
  - 低速ノード割合が高い場合（ただし、P*_90_ONでは逆転する場合あり）

## 実装上の注意点

1. **ノードタイプによるフィルタリングなし**
   - `getNeighborNodes()`はノードタイプを考慮しない
   - Forerunner非適用ノードにも送信される（ただし、受信側で処理されない）

2. **トラフィック記録**
   - 送信先が空の場合でも、ブロードキャスト試行はトラフィックとして記録される

3. **レート制限**
   - `minShareIntervalMs`によるレート制限が適用される
   - 共有嵐を抑制するための重要な機能

4. **非同期送信**
   - `ExecutionDelayTask`を使用して非同期に送信
   - 1〜5msのジッタを追加してネットワーク負荷を分散

