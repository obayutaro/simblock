# 未来コンテキスト予測生成のタイミング

## 概要

未来コンテキスト予測生成は、**ブロック生成タイミングにのみ行われているわけではありません**。実際には、**2つの異なるタイミング**で未来コンテキストが生成されます。

## 未来コンテキスト生成の2つのタイプ

### 1. ブロック処理高速化のための未来コンテキスト生成

**メソッド**: `FutureContextManager.generate(Block current)`

**呼び出しタイミング**: **ブロック受信時**のみ

**実装場所**:
```java
// ForerunnerNode.java - recvBlockMessage()内
if (sharingEnabled) {
  if (nt == NodeType.FORERUNNER_HIGH) {
    ForerunnerNode.this.getFcm().generate(msg.getBlock());  // ← ブロック受信時に生成
  }
}
```

**目的**:
- ブロック処理の高速化
- 未来コンテキストを適用してブロック処理を高速化する
- **共有とは別の処理**

**特徴**:
- `FORERUNNER_SHARE_ONLY_ON_TX`の設定に関係なく実行される
- ブロック受信時に必ず実行される
- 共有のための未来コンテキスト生成とは独立している

### 2. 共有のための未来コンテキスト生成

**メソッド**: `FutureContextSharingManager.generateFutureContext(Block currentBlock, List<Transaction> transactions)`

**呼び出しタイミング**: **トランザクション受信時**と**ブロック受信時**の両方

**実装場所**:
```java
// FutureContextSharingManager.java - onTransactionPoolUpdate()内
// Forerunner高速ノードのみが未来コンテキストを生成（タイマーで遅延・非同期実行）
scheduleContextGenerationViaTimer(newTransactions);

// scheduleContextGenerationViaTimer()内
generateFutureContext(currentBlock, transactions);
```

**呼び出しタイミング**:

1. **トランザクション受信時** (`recvTxMessage()`)
   ```java
   // ForerunnerNode.java
   getFutureContextSharingManager().onTransactionPoolUpdate(newTransactions);
   ```
   - 個別のトランザクションが受信されたとき
   - `onTransactionPoolUpdate()`が呼ばれる
   - `scheduleContextGenerationViaTimer()`が呼ばれる
   - `generateFutureContext()`が実行される

2. **ブロック受信時** (`recvBlockMessage()`)
   ```java
   // ForerunnerNode.java
   if (!shareOnlyOnTx) {  // FORERUNNER_SHARE_ONLY_ON_TX=falseの場合のみ
     getFutureContextSharingManager().onTransactionPoolUpdate(msg.getBlock().getTxList());
   }
   ```
   - ブロックが受信されたとき（`FORERUNNER_SHARE_ONLY_ON_TX=false`の場合のみ）
   - `onTransactionPoolUpdate()`が呼ばれる
   - `scheduleContextGenerationViaTimer()`が呼ばれる
   - `generateFutureContext()`が実行される

**目的**:
- 未来コンテキストの共有
- 他のノードに未来コンテキストを共有する
- `shareContext()`が呼ばれて共有が実行される

**特徴**:
- `FORERUNNER_SHARE_ONLY_ON_TX`の設定によって、ブロック受信時の呼び出しが制御される
- トランザクション受信時には常に実行される
- ブロック受信時には`FORERUNNER_SHARE_ONLY_ON_TX=false`の場合のみ実行される

## 2つの未来コンテキスト生成の違い

| 項目 | ブロック処理高速化用 | 共有用 |
|------|-------------------|--------|
| **メソッド** | `FutureContextManager.generate()` | `FutureContextSharingManager.generateFutureContext()` |
| **呼び出しタイミング** | ブロック受信時のみ | トランザクション受信時 + ブロック受信時（条件付き） |
| **目的** | ブロック処理の高速化 | 未来コンテキストの共有 |
| **共有** | 共有しない（ローカルのみ） | 共有する（`shareContext()`が呼ばれる） |
| **FORERUNNER_SHARE_ONLY_ON_TXの影響** | 影響を受けない | ブロック受信時の呼び出しが制御される |

## 重要なポイント

### 1. ブロック処理高速化用の未来コンテキスト生成

**動作**:
- ブロック受信時に必ず実行される
- `FORERUNNER_SHARE_ONLY_ON_TX`の設定に関係なく実行される
- 共有とは別の処理

**影響**:
- ブロック処理の高速化に寄与する
- 伝播時間への影響は限定的（ブロック処理時間の短縮のみ）

### 2. 共有用の未来コンテキスト生成

**動作**:
- トランザクション受信時には常に実行される
- ブロック受信時には`FORERUNNER_SHARE_ONLY_ON_TX=false`の場合のみ実行される
- `shareContext()`が呼ばれて共有が実行される

**影響**:
- 未来コンテキストの共有に寄与する
- 伝播時間への影響が大きい（他のノードが未来コンテキストを利用できる）

## なぜブロック受信時の共有が抑止されると結果が変わらないのか（再考）

### 重要な理解

**ブロック処理高速化用の未来コンテキスト生成**:
- ブロック受信時に必ず実行される
- しかし、これは**共有とは別の処理**
- 伝播時間への影響は限定的

**共有用の未来コンテキスト生成**:
- トランザクション受信時には常に実行される
- ブロック受信時には`FORERUNNER_SHARE_ONLY_ON_TX=false`の場合のみ実行される
- **この共有が、REQUEST_BASEDとNEIGHBORS_ONLYの違いを生み出す**

### FORERUNNER_SHARE_ONLY_ON_TX = false の場合

**動作**:
1. **トランザクション受信時**: 共有用の未来コンテキストが生成・共有される
2. **ブロック受信時**: 
   - ブロック処理高速化用の未来コンテキストが生成される（共有とは別）
   - 共有用の未来コンテキストが生成・共有される（`onTransactionPoolUpdate()`が呼ばれる）

**REQUEST_BASEDとNEIGHBORS_ONLYの違い**:
- ブロック受信時に`onTransactionPoolUpdate()`が呼ばれる
- `shareContext()`が呼ばれる
- REQUEST_BASED: 要求してきたノードにのみ送信
- NEIGHBORS_ONLY: 隣接ノードすべてにブロードキャスト
- **この違いが明確に現れる**

### FORERUNNER_SHARE_ONLY_ON_TX = true の場合

**動作**:
1. **トランザクション受信時**: 共有用の未来コンテキストが生成・共有される
2. **ブロック受信時**: 
   - ブロック処理高速化用の未来コンテキストが生成される（共有とは別）
   - **共有用の未来コンテキスト生成・共有が行われない**（`onTransactionPoolUpdate()`が呼ばれない）

**REQUEST_BASEDとNEIGHBORS_ONLYの違い**:
- ブロック受信時に`onTransactionPoolUpdate()`が呼ばれない
- `shareContext()`が呼ばれない
- **両モードの違いが現れる機会がない**
- トランザクション受信時のみ共有が実行されるが、この時点では両モードの違いが伝播時間に現れにくい

## 結論

**未来コンテキスト予測生成はブロック生成タイミングにのみ行われているのか？**

**答え**: **いいえ、ブロック生成タイミングにのみ行われているわけではありません。**

**実際の動作**:

1. **ブロック処理高速化用の未来コンテキスト生成**:
   - ブロック受信時に必ず実行される
   - 共有とは別の処理

2. **共有用の未来コンテキスト生成**:
   - トランザクション受信時には常に実行される
   - ブロック受信時には`FORERUNNER_SHARE_ONLY_ON_TX=false`の場合のみ実行される
   - **この共有が、REQUEST_BASEDとNEIGHBORS_ONLYの違いを生み出す**

**重要なポイント**:
- ブロック受信時に`onTransactionPoolUpdate()`が呼ばれると、共有用の未来コンテキストが生成・共有される
- この時点で、REQUEST_BASEDとNEIGHBORS_ONLYの違いが明確に現れる
- ブロック受信時に`onTransactionPoolUpdate()`が呼ばれないと、両モードの違いが現れる機会が失われる



