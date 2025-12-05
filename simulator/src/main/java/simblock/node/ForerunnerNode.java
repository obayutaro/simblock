package simblock.node;

import static simblock.settings.SimulationConfiguration.TX_PROCESSING_TIME;
import static simblock.simulator.Timer.putTask;

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
import simblock.util.DebugLogger;

/** Forerunner 機能（Pre-play, Component Reuse, Future Context）を模擬するノード。 */
public class ForerunnerNode extends AbstractNode {
  // 期待値定数（予測精度の加重逆スピードアップ）
  private static final double WEIGHTED_INVERSE_SPEEDUP =
      simblock.settings.ForerunnerConfig.PERFECT_PREDICTION_RATE
              / simblock.settings.ForerunnerConfig.PERFECT_SPEEDUP
          + simblock.settings.ForerunnerConfig.IMPERFECT_PREDICTION_RATE
              / simblock.settings.ForerunnerConfig.IMPERFECT_SPEEDUP
          + simblock.settings.ForerunnerConfig.MISSED_PREDICTION_RATE
              / simblock.settings.ForerunnerConfig.MISSED_SPEEDUP;

  // 基準実行時間（μs）: 平均Tx数×Tx処理時間
  private static final long BASE_EXEC_US =
      simblock.settings.SimulationConfiguration.ASSUMED_TX_PER_BLOCK * TX_PROCESSING_TIME;

  private final DiffCache diffCache = new DiffCache();
  private final PreplayManager ppm = new PreplayManager(diffCache);
  private final FutureContextManager fcm;
  // FutureContextSharingManagerはAbstractNodeで既に定義されているため、ここでは定義しない

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
    // FutureContextSharingManagerはAbstractNodeのコンストラクタで既に初期化されているため、ここでは作成しない
  }

  /* ======= 受信イベント ======= */

  @Override
  public void recvTxMessage(TxMessage msg) {
    Transaction tx = msg.getTx();

    // Pre-play: 親ブロックは現 Tip とみなす
    // 実際のForerunner性能：投機的実行適用率99.15%（Perfect 87.19% + Imperfect 11.96%）
    // 共有ON時のみ前処理（ベースラインでの余計な負荷を避ける）
    boolean sharingEnabled =
        Boolean.parseBoolean(System.getenv().getOrDefault("FORERUNNER_SHARING_ENABLED", "true"));
    double speculativeRate = simblock.settings.ForerunnerConfig.TOTAL_SPECULATIVE_RATE;
    if (sharingEnabled && Math.random() < speculativeRate) {
      ppm.preplay(getBlock(), tx);
    }

      // 未来コンテキスト共有: トランザクションプールの変更を監視
      List<Transaction> newTransactions = new ArrayList<>();
      newTransactions.add(tx);
      if (sharingEnabled) {
        getFutureContextSharingManager().onTransactionPoolUpdate(newTransactions);
      }

    super.recvTxMessage(msg); // 既存処理（伝搬など）
  }

  @Override
  public void recvBlockMessage(BlockMessage msg) {
    Block blk = msg.getBlock();

    // 🔍 デバッグ: ForerunnerNode処理開始
    DebugLogger.debug(
        String.format("🔥 ForerunnerNode %d: ブロック%d処理開始", this.getNodeID(), blk.getId()));

    /* 1. Future Context 適用（期待値定数に吸収） */
    // FutureContextManager.Outcome oc = fcm.evaluate(blk);
    // double factorFC = simblock.forerunner.FutureContextManager.factor(oc);

    /* 2. Component Reuse（期待値定数に吸収） */
    // double reuseCR = diffCache.applyReusableDiff(blk);
    // double factorCR = 1.0 - reuseCR * 0.8;

    /* 3. 詳細計算は期待値定数化で省略 */
    // double factor = Math.min(factorFC, factorCR);

    /* 4. Forerunner性能に基づく実行時間算出（期待値定数で簡約） */
    long base = BASE_EXEC_US;

    // ノード種別別の計算速度係数（高速/低速）
    simblock.forerunner.FutureContextSharingManager.NodeType nodeType =
        this.getFutureContextSharingManager().getNodeType();
    double nodeSpeedFactor = 1.0;
    if (nodeType == simblock.forerunner.FutureContextSharingManager.NodeType.FORERUNNER_HIGH) {
      nodeSpeedFactor = simblock.settings.SimulationConfiguration.HIGH_NODE_SPEED_FACTOR;
    } else if (nodeType
        == simblock.forerunner.FutureContextSharingManager.NodeType.FORERUNNER_LOW) {
      nodeSpeedFactor = simblock.settings.SimulationConfiguration.LOW_NODE_SPEED_FACTOR;
    }

    // 期待値（加重逆スピードアップ）で短縮
    long effectiveTime =
        (long) Math.max(0L, Math.round(base * nodeSpeedFactor * WEIGHTED_INVERSE_SPEEDUP));

    // Forerunnerは処理時間を短縮するため、遅延ではなく処理時間の短縮として実装
    // μs→ms変換、最小遅延時間の制限を削除（スピードアップ効果を最大化）
    // 実測ガイド: 平均 0.56ms/Tx, 平均160Tx, Forerunnerは平均8.39×高速化
    // すでに base は μs 単位 (0.56ms=560μs) かつ speedup を反映しているため、そのまま μs→ms 変換
    long delay = Math.max(0L, Math.round(effectiveTime / 1000.0));

    // 🔍 デバッグ: 処理時間計算詳細
    DebugLogger.debug(
        String.format(
            "🔥 Node %d 計算詳細: base=%dμs, effectiveTime=%dμs, delay=%dms",
            this.getNodeID(), base, effectiveTime, delay));

    // 📊 単一ノード処理時間ログ（Forerunner適用時）
    DebugLogger.singleNodeProcTime(
        this.getNodeID(), blk.getId(), delay, nodeType.toString(), blk.getMinter() == this);

    // 重要ログは抑制設定下では生成しない（DebugLogger側で抑止）

    /* 5. Forerunner性能に基づく遅延処理（汎用タスクに統一） */
    ExecutionDelayTask task =
        new ExecutionDelayTask(this, delay) {
          @Override
          public void run() {
            // 遅延完了 → 親処理へ
            ForerunnerNode.this.superRecvBlock(msg);

            boolean sharingEnabled =
                Boolean.parseBoolean(
                    System.getenv().getOrDefault("FORERUNNER_SHARING_ENABLED", "true"));

            if (sharingEnabled) {
              // 低速は待機/適用のみ（生成しない）、高速は生成
              simblock.forerunner.FutureContextSharingManager.NodeType nt =
                  ForerunnerNode.this.getFutureContextSharingManager().getNodeType();
              if (nt == simblock.forerunner.FutureContextSharingManager.NodeType.FORERUNNER_HIGH) {
                ForerunnerNode.this.getFcm().generate(msg.getBlock());
              }
            } else {
              // 共有OFF時: 高速/低速とも自前生成
              ForerunnerNode.this.getFcm().generate(msg.getBlock());
            }
          }
        };
    putTask(task);

    // 未来コンテキスト共有: SHARE_ONLY_ON_TX=true の場合はブロック受信時の起動を抑止
    boolean shareOnlyOnTx =
        Boolean.parseBoolean(System.getenv().getOrDefault("FORERUNNER_SHARE_ONLY_ON_TX", "false"));
      if (!shareOnlyOnTx) {
      boolean sharingEnabled =
          Boolean.parseBoolean(System.getenv().getOrDefault("FORERUNNER_SHARING_ENABLED", "true"));
      if (sharingEnabled) {
        getFutureContextSharingManager().onTransactionPoolUpdate(msg.getBlock().getTxList());
      }
    }
  }

  // 予測タイプ関連の詳細計算は期待値定数化により不要（維持しない）

  /* ======= ユーティリティ ======= */

  // ブロック実行の基準時間は定数BASE_EXEC_USを使用（期待値化）

  // Getters
  // getFutureContextSharingManager()はAbstractNodeで既に定義されているため、ここでは定義しない

  /* ========== 内部クラス: 遅延後にブロック処理を行う ========== */
  private static class DelayedBlockProcessTask extends ExecutionDelayTask {

    private final ForerunnerNode owner;
    private final BlockMessage msg;
    private final long processingDelay;

    DelayedBlockProcessTask(ForerunnerNode owner, BlockMessage msg, long delay) {
      super(owner, delay); // ExecutionDelayTask(Node owner,long delay)
      this.owner = owner;
      this.msg = msg;
      this.processingDelay = delay;
    }

    @Override
    public void run() {
      /* 遅延が終了したのでチェーンに組み込み & 新FC生成 */
      // 🔍 デバッグ: 遅延処理完了
      DebugLogger.debug(
          String.format("🔥 Node %d: 遅延処理完了(%dms) → チェーン追加開始", owner.getNodeID(), processingDelay));

      owner.superRecvBlock(msg); // ★親クラスの既存処理を呼ぶ

      boolean sharingEnabled =
          Boolean.parseBoolean(System.getenv().getOrDefault("FORERUNNER_SHARING_ENABLED", "true"));

      if (sharingEnabled) {
        // Forerunner低速ノードの場合、共有された未来コンテキストをチェック
        if (owner.getFutureContextSharingManager().getNodeType()
            == simblock.forerunner.FutureContextSharingManager.NodeType.FORERUNNER_LOW) {

          long blockIndex = msg.getBlock().getIndex();

          // 共有された未来コンテキストがあるかチェック
          simblock.forerunner.EnhancedFutureContext sharedContext =
              owner.getFutureContextSharingManager().getFutureContext(blockIndex);

          if (sharedContext != null) {
            // 共有された未来コンテキストを適用
            DebugLogger.debug(
                String.format(
                    "🔥 Node %d: 共有された未来コンテキストを適用 (block=%d)", owner.getNodeID(), blockIndex));
          } else {
            // 共有が来ない場合は通常のForerunner未適用低速ノードと同様の処理
            DebugLogger.debug(
                String.format("🔥 Node %d: 共有なし → 通常処理 (block=%d)", owner.getNodeID(), blockIndex));
          }
        } else {
          // Forerunner高速ノードは従来通り未来コンテキストを生成
          owner.getFcm().generate(msg.getBlock());
        }
      } else {
        // 共有OFF時: 高速/低速いずれのForerunnerノードも自身で未来コンテキストを生成
        owner.getFcm().generate(msg.getBlock());
      }

      // 🔍 デバッグ: チェーン追加完了
      DebugLogger.debug(String.format("🔥 Node %d: チェーン追加完了 → 伝播開始", owner.getNodeID()));
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

  /* ========== タスクスケジューリング ========== */
  private void addTask(DelayedBlockProcessTask task) {
    // タスクを実行スケジューラに追加（Timer.putTaskを使用）
    putTask(task);
  }
}
