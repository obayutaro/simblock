package simblock.message;

import simblock.block.Transaction;

/** トランザクション伝搬メッセージ。 */
public class TxMessage extends Message {
  private final Transaction tx;

  public TxMessage(long senderId, Transaction tx) {
    super(senderId);
    this.tx = tx;
  }

  public Transaction getTx() {
    return tx;
  }
}
