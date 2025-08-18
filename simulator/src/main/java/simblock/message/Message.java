package simblock.message;

/** 送信元 ID だけを持つ極小抽象メッセージ。 */
public abstract class Message {
  private final long senderId;

  protected Message(long senderId) {
    this.senderId = senderId;
  }

  public long getSenderId() {
    return senderId;
  }
}
