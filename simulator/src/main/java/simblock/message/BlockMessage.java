package simblock.message;

import simblock.block.Block;

/** ブロック伝搬メッセージ。 */
public class BlockMessage extends Message {
  private final Block block;

  public BlockMessage(long senderId, Block block) {
    super(senderId);
    this.block = block;
  }

  public Block getBlock() {
    return block;
  }
}
