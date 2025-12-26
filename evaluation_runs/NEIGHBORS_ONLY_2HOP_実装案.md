# NEIGHBORS_ONLYを2ホップ先まで拡張する実装案

## 実装可能性

**可能です。** 既存の`getSlowNodes()`メソッドで2ホップ先までのノード取得が実装されているため、同様の方法でNEIGHBORS_ONLYを拡張できます。

## 実装方法

### 方法1: 新しいメソッドを追加（推奨）

`FutureContextSharingManager.java`に新しいメソッド`getNeighborsWithinTwoHops()`を追加します。

```java
/** 2ホップ先までの隣接ノードを取得（ノードタイプによるフィルタリングなし） */
private List<Node> getNeighborsWithinTwoHops() {
  List<Node> neighbors = new ArrayList<>();
  Set<Node> visitedNodes = new HashSet<>(); // 重複を防ぐためのセット

  try {
    // 送信元ノード自身を訪問済みとしてマーク（自分自身は送信先に含めない）
    visitedNodes.add(ownerNode);

    // 1ホップ先（直接の隣接ノード）を取得
    List<Node> oneHopNeighbors = getNeighborNodes();
    
    // 2ホップ先までのノードを収集
    Set<Node> nodesWithinTwoHops = new HashSet<>();
    
    // 1ホップ先のノードを追加
    nodesWithinTwoHops.addAll(oneHopNeighbors);
    
    // 2ホップ先のノードを追加（各隣接ノードの隣接ノード）
    for (Node neighbor : oneHopNeighbors) {
      try {
        Method getNeighborsMethod = neighbor.getClass().getMethod("getNeighbors");
        @SuppressWarnings("unchecked")
        List<Node> neighborNeighbors = (List<Node>) getNeighborsMethod.invoke(neighbor);
        
        if (neighborNeighbors != null) {
          nodesWithinTwoHops.addAll(neighborNeighbors);
        }
      } catch (Exception e) {
        // 隣接ノードの隣接ノード取得に失敗した場合はスキップ
        System.err.println("Warning: Could not get neighbors of neighbor node " 
            + neighbor.getNodeID() + ": " + e.getMessage());
      }
    }

    // 送信元ノード自身を除外
    nodesWithinTwoHops.remove(ownerNode);

    // すべてのノードを追加（ノードタイプによるフィルタリングなし）
    for (Node node : nodesWithinTwoHops) {
      // 既に追加済みのノードはスキップ（重複排除）
      if (visitedNodes.contains(node)) {
        continue;
      }
      neighbors.add(node);
      visitedNodes.add(node);
    }
  } catch (Exception e) {
    System.err.println("Warning: Could not get neighbors within two hops: " + e.getMessage());
  }

  return neighbors;
}
```

### 方法2: 環境変数で制御可能にする

環境変数`NEIGHBORS_HOP_COUNT`でホップ数を指定できるようにする。

```java
/** ブロードキャスト範囲を判定 */
private BroadcastScope determineBroadcastScope() {
  String broadcastScopeStr = System.getenv("BROADCAST_SCOPE");
  // ... 既存のコード ...
}

/** 隣接ノードのホップ数を取得 */
private int getNeighborsHopCount() {
  String hopCountStr = System.getenv("NEIGHBORS_HOP_COUNT");
  if (hopCountStr != null && !hopCountStr.isEmpty()) {
    try {
      int hopCount = Integer.parseInt(hopCountStr.trim());
      return Math.max(1, Math.min(hopCount, 3)); // 1〜3ホップまで制限
    } catch (NumberFormatException e) {
      return 1; // デフォルトは1ホップ
    }
  }
  return 1; // デフォルトは1ホップ
}

/** ブロードキャスト範囲に応じて対象ノードを取得 */
private List<Node> getTargetNodesForBroadcast() {
  switch (broadcastScope) {
    case NEIGHBORS_ONLY:
      int hopCount = getNeighborsHopCount();
      if (hopCount == 1) {
        return getNeighborNodes();
      } else if (hopCount == 2) {
        return getNeighborsWithinTwoHops();
      } else {
        // 3ホップ以上の場合は拡張可能
        return getNeighborsWithinNHops(hopCount);
      }
    // ... 他のケース ...
  }
}
```

## 実装手順

### ステップ1: メソッド追加

`FutureContextSharingManager.java`の`getNeighborNodes()`メソッドの後に、`getNeighborsWithinTwoHops()`メソッドを追加します。

### ステップ2: getTargetNodesForBroadcast()の修正

```java
private List<Node> getTargetNodesForBroadcast() {
  switch (broadcastScope) {
    case NEIGHBORS_ONLY:
      // 環境変数で2ホップ先まで拡張可能にする
      String hopCountStr = System.getenv("NEIGHBORS_HOP_COUNT");
      if (hopCountStr != null && "2".equals(hopCountStr.trim())) {
        return getNeighborsWithinTwoHops();
      }
      return getNeighborNodes();
    // ... 他のケース ...
  }
}
```

## 影響と注意点

### メリット

1. **伝播速度の向上**: 2ホップ先まで送信することで、より多くのノードに未来コンテキストが届く
2. **柔軟性**: 環境変数で制御可能にすることで、1ホップと2ホップを切り替え可能

### デメリット・注意点

1. **トラフィック増加**: 送信先ノード数が大幅に増加する可能性がある
   - 1ホップ: 平均隣接ノード数（例: 8ノード）
   - 2ホップ: 平均隣接ノード数 × 平均隣接ノード数（例: 8 × 8 = 64ノード、重複を除いて約30-40ノード）

2. **重複送信**: 1ホップ先のノードにも2ホップ先のノードにも送信されるため、同じノードに複数回送信される可能性がある
   - 実装では`Set`を使用して重複を排除しているため、この問題は解決済み

3. **パフォーマンス**: 2ホップ先のノード取得には追加のリフレクション呼び出しが必要

4. **ネットワーク負荷**: トラフィックが大幅に増加する可能性がある

## 評価シナリオでの影響予測

### 伝播時間

- **改善が期待される**: より多くのノードに未来コンテキストが届くため、伝播時間が短縮される可能性がある
- **ただし**: トラフィック増加によるネットワーク負荷の影響も考慮が必要

### トラフィック

- **大幅な増加が予想される**: 
  - 送信トラフィック: 約3-5倍（隣接ノード数による）
  - 受信トラフィック: 同様に増加

### REQUEST_BASEDとの比較

- **2ホップ先まで拡張したNEIGHBORS_ONLY**は、REQUEST_BASEDと比較して：
  - 伝播時間: さらに短縮される可能性がある
  - トラフィック: さらに増加する可能性がある

## 実装例（完全版）

```java
/** 2ホップ先までの隣接ノードを取得 */
private List<Node> getNeighborsWithinTwoHops() {
  List<Node> neighbors = new ArrayList<>();
  Set<Node> visitedNodes = new HashSet<>();

  try {
    visitedNodes.add(ownerNode);
    List<Node> oneHopNeighbors = getNeighborNodes();
    Set<Node> nodesWithinTwoHops = new HashSet<>();
    nodesWithinTwoHops.addAll(oneHopNeighbors);
    
    for (Node neighbor : oneHopNeighbors) {
      try {
        Method getNeighborsMethod = neighbor.getClass().getMethod("getNeighbors");
        @SuppressWarnings("unchecked")
        List<Node> neighborNeighbors = (List<Node>) getNeighborsMethod.invoke(neighbor);
        if (neighborNeighbors != null) {
          nodesWithinTwoHops.addAll(neighborNeighbors);
        }
      } catch (Exception e) {
        System.err.println("Warning: Could not get neighbors of neighbor node " 
            + neighbor.getNodeID() + ": " + e.getMessage());
      }
    }

    nodesWithinTwoHops.remove(ownerNode);

    for (Node node : nodesWithinTwoHops) {
      if (!visitedNodes.contains(node)) {
        neighbors.add(node);
        visitedNodes.add(node);
      }
    }
  } catch (Exception e) {
    System.err.println("Warning: Could not get neighbors within two hops: " + e.getMessage());
  }

  return neighbors;
}
```

## 使用方法

実装後、環境変数で2ホップ先まで拡張できます：

```bash
export BROADCAST_SCOPE=NEIGHBORS_ONLY
export NEIGHBORS_HOP_COUNT=2  # 2ホップ先まで拡張
```

または、デフォルトで2ホップ先まで拡張する場合は、`getTargetNodesForBroadcast()`を直接修正します。

