package simblock.forerunner;

import simblock.block.Block;

/** Future Context の最小表現。親ブロックのハッシュのみ保持。 */
public class FutureContext {
  private final long parentHash;

  FutureContext(Block current) {
    this.parentHash = current.getId();
  }

  boolean matches(Block next) {
    return parentHash == next.getParent().getId();
  }
}
