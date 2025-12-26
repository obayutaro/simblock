# ブロック受信時の共有が抑止されると結果が変わらない理由（詳細解説）

## 核心的な理由

`FORERUNNER_SHARE_ONLY_ON_TX=true`でブロック受信時の共有が抑止されると、REQUEST_BASEDモードとNEIGHBORS_ONLYモードの**伝播時間が同じになる**理由は、**未来コンテキスト生成のタイミングと共有のタイミングの関係**にあります。

## 重要な理解ポイント

### 1. 未来コンテキスト生成のタイミング

**高速ノード（FORERUNNER_HIGH）の動作**:

```java
// ForerunnerNode.java - recvBlockMessage()内
if (sharingEnabled) {
  if (nt == NodeType.FORERUNNER_HIGH) {
    ForerunnerNode.this.getFcm().generate(msg.getBlock());  // ← ブロック受信時に生成
  }
}
```

**重要な点**:
- 高速ノードは**ブロック受信時**に未来コンテキストを生成する
- これは`FORERUNNER_SHARE_ONLY_ON_TX`の設定に関係なく実行される
- ただし、**共有（shareContext）**は`onTransactionPoolUpdate()`が呼ばれたときに実行される

### 2. 未来コンテキスト共有のタイミング

**共有が実行されるタイミング**:

1. **トランザクション受信時** (`recvTxMessage()`)
   ```java
   getFutureContextSharingManager().onTransactionPoolUpdate(newTransactions);
   ```

2. **ブロック受信時** (`recvBlockMessage()`)
   ```java
   if (!shareOnlyOnTx) {  // falseの場合のみ
     getFutureContextSharingManager().onTransactionPoolUpdate(msg.getBlock().getTxList());
   }
   ```

### 3. REQUEST_BASEDとNEIGHBORS_ONLYの違いが現れる場所

**`shareContext()`メソッド内**:

```java
// REQUEST_BASEDの場合
if (broadcastScope == BroadcastScope.REQUEST_BASED) {
  shareContextToRequestingNodes(context);  // ← 要求してきたノードにのみ送信
  return;
}

// NEIGHBORS_ONLYの場合
List<Node> targetNodes = getTargetNodesForBroadcast();  // ← 隣接ノードすべてにブロードキャスト
// 各targetNodeに送信
```

**重要な点**:
- この違いは、`shareContext()`が呼ばれたときに現れる
- `shareContext()`は、`generateFutureContext()`内で呼ばれる
- `generateFutureContext()`は、`onTransactionPoolUpdate()`が呼ばれたときに実行される

## なぜ結果が変わらないのか（詳細）

### シナリオ1: FORERUNNER_SHARE_ONLY_ON_TX = false

**動作フロー**:

1. **トランザクション受信時**:
   - `recvTxMessage()`が呼ばれる
   - `onTransactionPoolUpdate(newTransactions)`が呼ばれる（1つのトランザクション）
   - 高速ノード: 未来コンテキストを生成 → `shareContext()`が呼ばれる
   - **REQUEST_BASED**: 要求してきたノードにのみ送信
   - **NEIGHBORS_ONLY**: 隣接ノードすべてにブロードキャスト

2. **ブロック受信時**:
   - `recvBlockMessage()`が呼ばれる
   - 高速ノード: 未来コンテキストを生成（`getFcm().generate()`）
   - `onTransactionPoolUpdate(msg.getBlock().getTxList())`が呼ばれる（すべてのトランザクション）
   - `shareContext()`が呼ばれる
   - **REQUEST_BASED**: 要求してきたノードにのみ送信（効率的）
   - **NEIGHBORS_ONLY**: 隣接ノードすべてにブロードキャスト（非効率的）

**結果**: 
- ブロック受信時に、両モードの違いが明確に現れる
- REQUEST_BASEDは要求してきたノードにのみ送信するため、トラフィックが削減される
- 伝播時間にも影響が現れる可能性がある

### シナリオ2: FORERUNNER_SHARE_ONLY_ON_TX = true

**動作フロー**:

1. **トランザクション受信時**:
   - `recvTxMessage()`が呼ばれる
   - `onTransactionPoolUpdate(newTransactions)`が呼ばれる（1つのトランザクション）
   - 高速ノード: 未来コンテキストを生成 → `shareContext()`が呼ばれる
   - **REQUEST_BASED**: 要求してきたノードにのみ送信
   - **NEIGHBORS_ONLY**: 隣接ノードすべてにブロードキャスト

2. **ブロック受信時**:
   - `recvBlockMessage()`が呼ばれる
   - 高速ノード: 未来コンテキストを生成（`getFcm().generate()`）
   - **`onTransactionPoolUpdate()`が呼ばれない**（抑止される）
   - **`shareContext()`が呼ばれない**
   - 未来コンテキストは生成されるが、共有されない

**結果**: 
- ブロック受信時に、両モードの違いが現れる機会がない
- トランザクション受信時のみ共有が実行されるが、この時点では両モードの違いが伝播時間に現れにくい

## なぜトランザクション受信時のみでは違いが現れないのか

### 理由1: 未来コンテキスト生成のタイミングの問題

**問題点**:
- 高速ノードは**ブロック受信時**に未来コンテキストを生成する
- しかし、`FORERUNNER_SHARE_ONLY_ON_TX=true`の場合、ブロック受信時に`onTransactionPoolUpdate()`が呼ばれない
- そのため、ブロック受信時に生成された未来コンテキストが共有されない

**トランザクション受信時の動作**:
- トランザクション受信時に`onTransactionPoolUpdate()`が呼ばれる
- しかし、この時点では**まだブロックが受信されていない**
- 未来コンテキストは、**次のブロック**に対する予測を生成する
- トランザクション受信時には、未来コンテキストが生成されるが、**ブロック受信時には共有されない**

### 理由2: ブロック伝播と未来コンテキスト共有のタイミングのずれ

**ブロック伝播のタイミング**:
- ブロックが受信されると、ブロック伝播が開始される
- ブロック伝播は、ネットワーク全体に広がる

**未来コンテキスト共有のタイミング**:
- `FORERUNNER_SHARE_ONLY_ON_TX=false`: ブロック受信時に共有される（ブロック伝播と同時）
- `FORERUNNER_SHARE_ONLY_ON_TX=true`: ブロック受信時に共有されない（トランザクション受信時のみ）

**影響**:
- ブロック伝播と同時に未来コンテキストが共有されないと、伝播時間への影響が限定的になる
- トランザクション受信時のみの共有では、ブロック伝播のタイミングとずれるため、効果が現れにくい

### 理由3: トランザクション受信時の共有の限界

**トランザクション受信時の特徴**:
- 1つのトランザクションが個別に受信される
- トランザクションは順次処理される
- 未来コンテキストの生成・共有も個別に行われる

**問題点**:
- トランザクション受信時には、**次のブロック**に対する予測を生成する
- しかし、ブロックが実際に受信される前に、未来コンテキストが共有される
- ブロック受信時には、未来コンテキストが既に共有されている可能性がある
- そのため、ブロック受信時の共有が抑止されても、影響が限定的になる

## 具体的な動作の違い（詳細）

### ケース1: ブロックに100個のトランザクションが含まれる場合

#### FORERUNNER_SHARE_ONLY_ON_TX = false

**タイムライン**:

1. **T1: トランザクション受信（Tx1）**
   - `onTransactionPoolUpdate([Tx1])`が呼ばれる
   - 高速ノード: 未来コンテキストを生成 → `shareContext()`が呼ばれる
   - REQUEST_BASED: 要求してきたノードにのみ送信
   - NEIGHBORS_ONLY: 隣接ノードすべてにブロードキャスト

2. **T2: トランザクション受信（Tx2）**
   - `onTransactionPoolUpdate([Tx2])`が呼ばれる
   - （同様の処理）

3. **...**

4. **T100: トランザクション受信（Tx100）**
   - `onTransactionPoolUpdate([Tx100])`が呼ばれる
   - （同様の処理）

5. **T101: ブロック受信（Block with Tx1-Tx100）**
   - 高速ノード: 未来コンテキストを生成（`getFcm().generate()`）
   - `onTransactionPoolUpdate([Tx1, Tx2, ..., Tx100])`が呼ばれる
   - `shareContext()`が呼ばれる
   - **REQUEST_BASED**: 要求してきたノードにのみ送信（効率的）
   - **NEIGHBORS_ONLY**: 隣接ノードすべてにブロードキャスト（非効率的）
   - **この時点で、両モードの違いが明確に現れる**

#### FORERUNNER_SHARE_ONLY_ON_TX = true

**タイムライン**:

1. **T1: トランザクション受信（Tx1）**
   - `onTransactionPoolUpdate([Tx1])`が呼ばれる
   - 高速ノード: 未来コンテキストを生成 → `shareContext()`が呼ばれる
   - REQUEST_BASED: 要求してきたノードにのみ送信
   - NEIGHBORS_ONLY: 隣接ノードすべてにブロードキャスト

2. **T2: トランザクション受信（Tx2）**
   - `onTransactionPoolUpdate([Tx2])`が呼ばれる
   - （同様の処理）

3. **...**

4. **T100: トランザクション受信（Tx100）**
   - `onTransactionPoolUpdate([Tx100])`が呼ばれる
   - （同様の処理）

5. **T101: ブロック受信（Block with Tx1-Tx100）**
   - 高速ノード: 未来コンテキストを生成（`getFcm().generate()`）
   - **`onTransactionPoolUpdate()`が呼ばれない**（抑止される）
   - **`shareContext()`が呼ばれない**
   - **この時点で、両モードの違いが現れる機会がない**

**結果**: 
- ブロック受信時に、両モードの違いが現れる機会がない
- トランザクション受信時のみ共有が実行されるが、この時点では両モードの違いが伝播時間に現れにくい

## なぜ伝播時間に影響が現れないのか

### 伝播時間への影響のメカニズム

**未来コンテキスト共有が伝播時間に影響を与える仕組み**:
1. 高速ノードが未来コンテキストを生成
2. 未来コンテキストが低速ノードに共有される
3. 低速ノードが未来コンテキストを適用してブロック処理を高速化
4. ブロック伝播時間が短縮される

**重要なポイント**:
- 未来コンテキストは、**ブロック受信時**に適用される
- ブロック受信時に未来コンテキストが共有されると、ブロック伝播と同時に効果が現れる
- ブロック受信時に未来コンテキストが共有されないと、効果が現れにくい

### FORERUNNER_SHARE_ONLY_ON_TX = true の場合

**問題点**:
- ブロック受信時に`onTransactionPoolUpdate()`が呼ばれない
- そのため、ブロック受信時に生成された未来コンテキストが共有されない
- トランザクション受信時には、未来コンテキストが共有されるが、ブロック伝播のタイミングとずれる
- そのため、伝播時間への影響が限定的になる

**結果**:
- REQUEST_BASEDモードとNEIGHBORS_ONLYモードの違いが伝播時間に現れない
- 両モードの結果が同じになる

## 結論

**なぜブロック受信時の共有が抑止されると結果が変わらないのか**:

1. **ブロック受信時の共有が、REQUEST_BASEDとNEIGHBORS_ONLYの違いを最も明確に示すタイミング**
   - ブロックに含まれるすべてのトランザクションが一度に処理される
   - `shareContext()`が呼ばれ、両モードの違いが現れる
   - REQUEST_BASED: 要求してきたノードにのみ送信（効率的）
   - NEIGHBORS_ONLY: 隣接ノードすべてにブロードキャスト（非効率的）

2. **ブロック受信時の共有が抑止されると、両モードの違いが現れる機会が失われる**
   - `onTransactionPoolUpdate()`が呼ばれない
   - `shareContext()`が呼ばれない
   - 両モードの違いが現れない

3. **トランザクション受信時のみの共有では、伝播時間への影響が限定的**
   - ブロック伝播のタイミングとずれる
   - 未来コンテキストがブロック受信時に共有されない
   - 伝播時間への影響が限定的

**まとめ**:
- `FORERUNNER_SHARE_ONLY_ON_TX=false`: ブロック受信時にも共有が実行されるため、REQUEST_BASEDとNEIGHBORS_ONLYの違いが明確に現れる
- `FORERUNNER_SHARE_ONLY_ON_TX=true`: ブロック受信時の共有が抑止されるため、両モードの違いが現れる機会が失われ、結果が同じになる



