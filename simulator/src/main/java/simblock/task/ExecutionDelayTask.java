package simblock.task;

import simblock.node.Node;

/**
 * 計算遅延をシミュレーションタイマに入れるだけのダミータスク。
 *
 * <p>遅延が満了してもネットワークイベントは発生しませんが、<br>
 * Timer キュー上で「そのノードが指定時間だけビジーになる」効果を再現できます。
 */
public class ExecutionDelayTask extends AbstractMessageTask {

  private final long delay; // 遅延時間（SimBlock は ms 単位で扱う）

  /* ------------------------------------------------------------ */
  public ExecutionDelayTask(Node owner, long delay) {
    /*
     * AbstractMessageTask のコンストラクタは (Node from, Node to) の 2 引数だけ。
     * delay はこのクラス自身で保持する。
     */
    super(owner, owner);
    this.delay = delay;
  }

  /* ------------------------------------------------------------ */
  /** Timer が参照できるよう遅延を返すユーティリティ */
  public long getDelay() { // ← @Override を付けない
    return delay;
  }

  /* ------------------------------------------------------------ */
  /** 実処理はサブクラス（DelayedBlockProcessTask）が上書き */
  public void run() {
    /* no-op */
  }
}
