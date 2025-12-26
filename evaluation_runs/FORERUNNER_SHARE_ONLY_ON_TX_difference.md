# FORERUNNER_SHARE_ONLY_ON_TX の true/false の違い

## 概要

`FORERUNNER_SHARE_ONLY_ON_TX`パラメータは、未来コンテキスト共有を実行するタイミングを制御します。

## パラメータの動作

### FORERUNNER_SHARE_ONLY_ON_TX = false（デフォルト）

**動作**:
- **トランザクション受信時**: 未来コンテキスト共有を実行 ✅
- **ブロック受信時**: 未来コンテキスト共有を実行 ✅

**実装コード**:
```java
// recvTxMessage() - トランザクション受信時
public void recvTxMessage(TxMessage msg) {
  // ...
  if (sharingEnabled) {
    getFutureContextSharingManager().onTransactionPoolUpdate(newTransactions);
  }
  // ...
}

// recvBlockMessage() - ブロック受信時
public void recvBlockMessage(BlockMessage msg) {
  // ...
  boolean shareOnlyOnTx = Boolean.parseBoolean(
    System.getenv().getOrDefault("FORERUNNER_SHARE_ONLY_ON_TX", "false"));
  
  if (!shareOnlyOnTx) {  // falseの場合、ブロック受信時にも共有を実行
    if (sharingEnabled) {
      getFutureContextSharingManager().onTransactionPoolUpdate(msg.getBlock().getTxList());
    }
  }
  // ...
}
```

**特徴**:
- トランザクション受信時とブロック受信時の両方で未来コンテキスト共有が実行される
- より頻繁に未来コンテキストが生成・共有される
- トラフィックが増加する可能性がある
- ブロック受信時に含まれるトランザクションリストを基に未来コンテキストを生成

### FORERUNNER_SHARE_ONLY_ON_TX = true

**動作**:
- **トランザクション受信時**: 未来コンテキスト共有を実行 ✅
- **ブロック受信時**: 未来コンテキスト共有を抑止 ❌

**実装コード**:
```java
// recvTxMessage() - トランザクション受信時（変更なし）
public void recvTxMessage(TxMessage msg) {
  // ...
  if (sharingEnabled) {
    getFutureContextSharingManager().onTransactionPoolUpdate(newTransactions);
  }
  // ...
}

// recvBlockMessage() - ブロック受信時
public void recvBlockMessage(BlockMessage msg) {
  // ...
  boolean shareOnlyOnTx = Boolean.parseBoolean(
    System.getenv().getOrDefault("FORERUNNER_SHARE_ONLY_ON_TX", "false"));
  
  if (!shareOnlyOnTx) {  // trueの場合、このブロックは実行されない
    // ブロック受信時の共有処理はスキップされる
  }
  // ...
}
```

**特徴**:
- トランザクション受信時のみ未来コンテキスト共有が実行される
- ブロック受信時には未来コンテキスト共有が実行されない
- トラフィックが削減される可能性がある
- ブロック受信時に含まれるトランザクションリストを基にした未来コンテキスト生成が行われない

## 動作の違いの詳細

### 1. 未来コンテキスト生成のタイミング

| イベント | false | true |
|---------|-------|------|
| トランザクション受信時 | ✅ 実行 | ✅ 実行 |
| ブロック受信時 | ✅ 実行 | ❌ 抑止 |

### 2. トラフィックへの影響

**falseの場合**:
- トランザクション受信時とブロック受信時の両方で共有が実行される
- より頻繁に未来コンテキストが生成・共有される
- トラフィックが増加する可能性がある

**trueの場合**:
- トランザクション受信時のみ共有が実行される
- ブロック受信時の共有が抑止されるため、トラフィックが削減される可能性がある

### 3. 伝播時間への影響

**falseの場合**:
- ブロック受信時にも未来コンテキストが生成・共有される
- より多くのノードが未来コンテキストを利用できる可能性がある
- 伝播時間が改善される可能性がある

**trueの場合**:
- ブロック受信時の共有が抑止される
- トランザクション受信時のみ未来コンテキストが生成・共有される
- 伝播時間への影響が限定的になる可能性がある

## 実際の評価結果からの知見

### FORERUNNER_SHARE_ONLY_ON_TX = false の場合

**評価結果**:
- REQUEST_BASEDモードは多くのシナリオでトラフィックを大幅に削減（最大93.01%）
- 伝播時間はほぼ同等またはわずかに改善（0-0.5%）
- 低速ノード割合が90%のシナリオ（P*_90_ON）でトラフィックが増加

**特徴**:
- ブロック受信時にも未来コンテキスト共有が実行される
- より頻繁に未来コンテキストが生成・共有される
- REQUEST_BASEDモードの効果が発揮される

### FORERUNNER_SHARE_ONLY_ON_TX = true の場合

**評価結果**:
- REQUEST_BASEDモードとNEIGHBORS_ONLYモードの伝播時間が完全に同等（0.00%）
- すべてのシナリオで伝播時間に差がない

**特徴**:
- ブロック受信時の共有が抑止される
- トランザクション受信時のみ未来コンテキスト共有が実行される
- REQUEST_BASEDモードとNEIGHBORS_ONLYモードの違いが伝播時間に現れない

## 使い分けの指針

### false を使用する場合

- **トラフィック削減効果を評価したい場合**
  - REQUEST_BASEDモードの効果を最大限に発揮できる
  - ブロック受信時にも未来コンテキスト共有が実行される

- **伝播時間の改善を重視する場合**
  - より頻繁に未来コンテキストが生成・共有される
  - より多くのノードが未来コンテキストを利用できる

### true を使用する場合

- **トラフィックを最小限に抑えたい場合**
  - ブロック受信時の共有が抑止される
  - トランザクション受信時のみ共有が実行される

- **トランザクション受信時のみの動作を評価したい場合**
  - ブロック受信時の影響を排除できる
  - より制限された環境での評価が可能

## まとめ

| 項目 | false | true |
|------|-------|------|
| **トランザクション受信時** | ✅ 共有実行 | ✅ 共有実行 |
| **ブロック受信時** | ✅ 共有実行 | ❌ 共有抑止 |
| **トラフィック** | 増加傾向 | 削減傾向 |
| **伝播時間への影響** | 改善の可能性 | 限定的 |
| **REQUEST_BASEDの効果** | 発揮される | 伝播時間には現れない |

**推奨**:
- トラフィック削減効果を評価する場合: `false`を使用
- トランザクション受信時のみの動作を評価する場合: `true`を使用



