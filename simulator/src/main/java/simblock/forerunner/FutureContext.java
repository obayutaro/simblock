package simblock.forerunner;

import simblock.block.Block;

/** Future Context の最小表現。親ブロックのハッシュのみ保持。 */
public class FutureContext {
  private final long parentHash;
  private double variation = 0.0; // 未来の変動

  FutureContext(Block current) {
    this.parentHash = current.getId();
  }

  boolean matches(Block next) {
    return parentHash == next.getParent().getId();
  }

  /** 未来の変動を追加 */
  void addVariation(double variation) {
    this.variation = variation;
  }

  /** 変動値を取得 */
  double getVariation() {
    return variation;
  }
}
