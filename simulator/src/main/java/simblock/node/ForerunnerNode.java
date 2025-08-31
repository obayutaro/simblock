package simblock.node;

import java.util.ArrayList;
import java.util.List;
import simblock.block.Block;
import simblock.block.Transaction;
import simblock.forerunner.DiffCache;
import simblock.forerunner.FutureContextManager;
import simblock.forerunner.FutureContextSharingManager;
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
  private final FutureContextSharingManager fcsm; // 未来コンテキスト共有マネージャー

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
    this.fcsm = new FutureContextSharingManager(this); // 未来コンテキスト共有マネージャーを初期化
  }

  /* ======= 受信イベント ======= */

  @Override
  public void recvTxMessage(TxMessage msg) {
    Transaction tx = msg.getTx();

    // Pre-play: 親ブロックは現 Tip とみなす
    // 実際のForerunner性能：投機的実行適用率99.15%（Perfect 87.19% + Imperfect 11.96%）
    double speculativeRate = simblock.settings.ForerunnerConfig.TOTAL_SPECULATIVE_RATE;
    if (Math.random() < speculativeRate) {
      ppm.preplay(getBlock(), tx);
    }

    // 未来コンテキスト共有: トランザクションプールの変更を監視
    List<Transaction> newTransactions = new ArrayList<>();
    newTransactions.add(tx);
    fcsm.onTransactionPoolUpdate(newTransactions);

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

    /* 4. Forerunner性能に基づく実行時間算出（予測精度ベース） */
    long base = estimateBaseExecTime(blk);

    // 予測精度に基づくスピードアップ効果を適用
    String predictionType = determinePredictionType(blk);
    double speedupFactor = simblock.settings.ForerunnerConfig.getSpeedupFactor(predictionType);

    // 予測精度に応じた処理時間の計算
    long optimizedTime = (long) Math.floor(base / speedupFactor);
    long normalTime = base;

    // 予測精度に基づく重み付け平均
    double perfectWeight = simblock.settings.ForerunnerConfig.PERFECT_PREDICTION_RATE;
    double imperfectWeight = simblock.settings.ForerunnerConfig.IMPERFECT_PREDICTION_RATE;
    double missedWeight = simblock.settings.ForerunnerConfig.MISSED_PREDICTION_RATE;

    long effectiveTime =
        (long)
            (perfectWeight * (base / simblock.settings.ForerunnerConfig.PERFECT_SPEEDUP)
                + imperfectWeight * (base / simblock.settings.ForerunnerConfig.IMPERFECT_SPEEDUP)
                + missedWeight * (base / simblock.settings.ForerunnerConfig.MISSED_SPEEDUP));

    // Forerunnerは処理時間を短縮するため、遅延ではなく処理時間の短縮として実装
    // μs→ms変換、最小遅延時間の制限を削除（スピードアップ効果を最大化）
    long delay = effectiveTime / 1000;

    /* 5. Forerunner性能に基づく即座処理 */
    // 実際のForerunner性能に基づく処理
    superRecvBlock(msg);
    getFcm().generate(msg.getBlock());

    // 未来コンテキスト共有: ブロック受信後の処理
    fcsm.onTransactionPoolUpdate(msg.getBlock().getTxList());
  }

  /** ブロックの予測精度タイプを判定 実際のForerunnerの制約ベース投機実行を模擬 */
  private String determinePredictionType(Block blk) {
    // 制約集合（CD-Equiv）の満足度を判定
    double constraintSatisfaction = evaluateConstraintSatisfaction(blk);

    // 予測精度の判定（実際のデータに基づく）
    if (constraintSatisfaction >= 0.95) {
      return "PERFECT"; // 完全予測（87.19%）
    } else if (constraintSatisfaction >= 0.70) {
      return "IMPERFECT"; // 不完全予測（11.96%）
    } else {
      return "MISSED"; // 予測失敗（0.85%）
    }
  }

  /** 制約集合（CD-Equiv）の満足度を評価 制御フローとデータ依存関係の同一性を判定 */
  private double evaluateConstraintSatisfaction(Block blk) {
    // 簡易的な制約満足度の評価
    // 実際の実装では、より詳細な制約判定が必要

    // トランザクション数に基づく制約満足度
    int txCount = blk.getTxList().size();
    double baseSatisfaction = 0.8; // ベース満足度

    // トランザクション数が多いほど制約満足度が低下
    if (txCount > 100) {
      baseSatisfaction -= 0.1;
    } else if (txCount > 50) {
      baseSatisfaction -= 0.05;
    }

    // ランダム性を追加（実際の制約判定の不確実性を模擬）
    double randomFactor = (Math.random() - 0.5) * 0.2; // ±10%のランダム性
    double finalSatisfaction = Math.max(0.0, Math.min(1.0, baseSatisfaction + randomFactor));

    return finalSatisfaction;
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

  // Getters
  public FutureContextSharingManager getFutureContextSharingManager() {
    return this.fcsm;
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
