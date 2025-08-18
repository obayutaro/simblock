package simblock.node;

import static simblock.simulator.Timer.putTask;

import simblock.block.Block;
import simblock.block.Transaction;
import simblock.forerunner.DiffCache;
import simblock.forerunner.FutureContextManager;
import simblock.forerunner.PreplayManager;
import simblock.message.BlockMessage;
import simblock.message.TxMessage;
import simblock.task.ExecutionDelayTask;
import simblock.util.ParamUtil;

/** Forerunner 機能（Pre-play, Component Reuse, Future Context）を模擬するノード。 */
public class ForerunnerNode extends AbstractNode {

  private final DiffCache diffCache = new DiffCache();
  private final PreplayManager ppm = new PreplayManager(diffCache);
  private final FutureContextManager fcm;

  /** シナリオパーサから渡されるパラメータを受け取るコンストラクタ */
  public ForerunnerNode(
      int nodeID,
      int numConn,
      int region,
      long miningPower,
      String rtName,
      String csName,
      boolean useCBR,
      boolean isChurn) {
    super(nodeID, numConn, region, miningPower, rtName, csName, useCBR, isChurn);
    this.fcm = new FutureContextManager(getParams()); // 空Mapでも動く
  }

  /* ======= 受信イベント ======= */

  @Override
  public void recvTxMessage(TxMessage msg) {
    Transaction tx = msg.getTx();
    // Pre-play: 親ブロックは現 Tip とみなす
    ppm.preplay(getBlock(), tx);
    super.recvTxMessage(msg); // 既存処理（伝搬など）
  }

  @Override
  public void recvBlockMessage(BlockMessage msg) {
    Block blk = msg.getBlock();

    /* 1. Future Context 適用 */
    FutureContextManager.Outcome oc = fcm.evaluate(blk);
    double factorFC = simblock.forerunner.FutureContextManager.factor(oc);

    /* 2. Component Reuse */
    double reuseCR = diffCache.applyReusableDiff(blk);
    double factorCR = 1.0 - reuseCR * 0.8; // α = 0.8

    /* 3. 短い方（高速）を採用 */
    double factor = Math.min(factorFC, factorCR);

    /* 4. ブロック実行遅延 (μs) を算出 */
    long base = estimateBaseExecTime(blk);
    long delay = (long) (base * factor);

    /* 5. 計算が終わるまで “ExecutionDelayTask” を登録 */
    putTask(new DelayedBlockProcessTask(this, msg, delay));
  }

  /* ======= ユーティリティ ======= */

  /** ブロック実行の基準時間を近似計算（Tx 数 × 定数） */
  private long estimateBaseExecTime(Block blk) {
    long txNum = blk.getTxList().size();
    // 定数 100µs/Tx と仮定（環境に合わせて調整可）
    return txNum * ParamUtil.getLong(getParams(), "txBaseCost", 100);
  }

  /* ========== 内部クラス: 遅延後にブロック処理を行う ========== */
  private static class DelayedBlockProcessTask extends ExecutionDelayTask {

    private final ForerunnerNode owner;
    private final BlockMessage msg;

    DelayedBlockProcessTask(ForerunnerNode owner, BlockMessage msg, long delay) {
      super(owner, delay); // ExecutionDelayTask(Node owner,long delay)
      this.owner = owner;
      this.msg = msg;
    }

    @Override
    public void run() {
      /* 遅延が終了したのでチェーンに組み込み & 新FC生成 */
      owner.superRecvBlock(msg); // ★親クラスの既存処理を呼ぶ
      owner.getFcm().generate(msg.getBlock());
    }
  }

  /* ========== 親クラスの recvBlockMessage を呼ぶためのラッパ ========== */
  private void superRecvBlock(BlockMessage msg) {
    super.recvBlockMessage(msg);
  }

  /* ========== Getter (内部クラス用) ========== */
  private FutureContextManager getFcm() {
    return this.fcm;
  }
}
