package simblock.forerunner;

import simblock.block.Block;
import simblock.block.Transaction;

/** 非確定 Tx を“疑似的に”先読み実行するコンポーネント。 実際の EVM を動かさず、Tx ハッシュを DiffCache に登録するのみ。 */
public class PreplayManager {

  private final DiffCache diffCache;

  public PreplayManager(DiffCache diffCache) {
    this.diffCache = diffCache;
  }

  /** Block parent を基準に Tx を先読み実行したことにする */
  public void preplay(Block parent, Transaction tx) {
    diffCache.put(parent, tx.getHash());
  }
}
