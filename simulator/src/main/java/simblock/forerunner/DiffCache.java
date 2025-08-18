package simblock.forerunner;

import java.util.*;
import simblock.block.Block;

/** Component Reuse で再利用する差分をキャッシュ。 実際のステート差分は不要なので、Tx ハッシュ集合として近似。 */
public class DiffCache {

  private final Map<Long, Set<Long>> blockToTxs = new HashMap<>();

  /** 先読み実行済み Tx を登録 */
  void put(Block parent, long txHash) {
    blockToTxs.computeIfAbsent(parent.getIndex(), k -> new HashSet<>()).add(txHash);
  }

  /** ブロック受信時に再利用率 (0–1) を返す。 Tx 数は Block クラスが保持していると仮定し size() で取得。 */
  public double applyReusableDiff(Block block) {
    Set<Long> cached = blockToTxs.remove(block.getIndex());
    if (cached == null || block.getTxList().isEmpty()) return 0.0;
    long hit = cached.stream().filter(block.getTxList()::contains).count();
    return hit / (double) block.getTxList().size();
  }
}
