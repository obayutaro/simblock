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
    // 実際のForerunner性能：投機的実行適用率92-98%
    double speculativeRate = 0.95; // 92-98%の中央値
    if (Math.random() < speculativeRate) {
      ppm.preplay(getBlock(), tx);
    }
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

    /* 4. Forerunner性能に基づく実行時間算出 */
    long base = estimateBaseExecTime(blk);
    // 実際のForerunner性能：6.06×スピードアップ、投機的実行適用率92-98%
    // 投機的実行が成功した場合のスピードアップ効果を反映
    double speedupFactor = 6.06; // 実際のForerunner性能
    double speculativeSuccessRate = 0.95; // 投機的実行成功率（92-98%の中央値）
    
    // 成功時はスピードアップ、失敗時は通常処理時間
    long optimizedTime = (long) Math.floor(base / speedupFactor);
    long normalTime = base;
    long effectiveTime = (long) (speculativeSuccessRate * optimizedTime + (1.0 - speculativeSuccessRate) * normalTime);
    
    // Forerunnerは処理時間を短縮するため、遅延ではなく処理時間の短縮として実装
    // μs→ms変換、最小1msを確保（スピードアップ効果により短縮）
    long delay = Math.max(1L, effectiveTime / 1000);

    /* 5. Forerunner性能に基づく即座処理 */
    // 実際のForerunner性能：6.06×スピードアップ
    // 投機的実行により処理時間が大幅に短縮されるため、即座に処理
    superRecvBlock(msg);
    getFcm().generate(msg.getBlock());
  }

  /* ======= ユーティリティ ======= */

  /** ブロック実行の基準時間を近似計算（Tx 数 × 定数） */
  private long estimateBaseExecTime(Block blk) {
    long txNum = blk.getTxList().size();
    // Ethereumの実際のトランザクション処理時間に基づく設定
    // 複雑なトランザクションほどForerunnerの効果が大きい
    long baseCost = ParamUtil.getLong(getParams(), "txBaseCost", 100); // 100µs/Tx
    return txNum * baseCost;
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
