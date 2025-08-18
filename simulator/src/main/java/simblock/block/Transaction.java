package simblock.block;

/** SimBlock 拡張用の簡易トランザクションクラス。 先読み投機（Pre‑play）の粒度を満たすために 「ハッシュ」と「ガス量」だけを持たせる。 */
public class Transaction {

  private final long hash;
  private final long gas;

  public Transaction(long hash, long gas) {
    this.hash = hash;
    this.gas = gas;
  }

  public long getHash() {
    return hash;
  }

  public long getGas() {
    return gas;
  }
}
