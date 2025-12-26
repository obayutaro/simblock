package simblock.forerunner;

import static simblock.simulator.Timer.getCurrentTime;
import static simblock.simulator.Timer.putTask;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import simblock.block.Block;
import simblock.block.Transaction;
import simblock.node.ForerunnerNode;
import simblock.node.Node;
import simblock.task.ExecutionDelayTask;
import simblock.util.DebugLogger;

/** 未来コンテキストの生成、共有、管理を行うマネージャークラス 非対話型ブロードキャスト方式による未来コンテキスト共有を実装 */
public class FutureContextSharingManager {
  private final Node ownerNode;
  private final Map<Long, EnhancedFutureContext> localContexts;
  private final Map<Long, EnhancedFutureContext> sharedContexts;
  private final Map<String, Long> nodePerformanceRatings;
  private final Random random;

  // 設定パラメータ
  private final double sharingThreshold;
  private final int maxContextsPerBlock;
  private final long contextGenerationDelay;
  private final boolean isHighPerformanceNode;
  private final NodeType nodeType;
  private final BroadcastScope broadcastScope;
  private final boolean sharingEnabled;
  private final boolean fastShareEnabled;
  private final long minShareIntervalMs;
  private long lastShareEpochMs = 0L;

  // 待機メカニズム用のフィールド
  private final Map<Long, Boolean> waitingForSharedContext = new ConcurrentHashMap<>();
  private final Map<Long, Long> waitingStartTime = new ConcurrentHashMap<>();

  // 要求ベース共有用のフィールド
  private final Map<Long, Set<Node>> pendingRequests = new ConcurrentHashMap<>(); // ブロックインデックス -> 要求元ノードのセット
  private Node preferredHighNode = null; // 優先的に要求を送る高速ノード（低速ノード側）
  private final Map<Long, Set<Node>> requestingLowNodesByBlock = new ConcurrentHashMap<>(); // ブロックインデックス -> 要求してきた低速ノードのセット（高速ノード側、ブロックインデックスごとに管理）

  /** ブロードキャスト範囲の列挙 */
  public enum BroadcastScope {
    NEIGHBORS_ONLY, // 隣接ノードのみ
    SLOW_NODES_ONLY, // 低速ノードのみ
    ALL_NODES, // 全ノード
    CUSTOM_RANGE, // カスタム範囲（環境変数で指定）
    REQUEST_BASED // 要求ベース共有（新規追加）
  }

  /** ノードタイプの列挙 */
  public enum NodeType {
    FORERUNNER_HIGH, // Forerunner適用高速ノード
    FORERUNNER_LOW, // Forerunner適用低速ノード
    NON_FORERUNNER_HIGH, // Forerunner非適用高速ノード
    NON_FORERUNNER_LOW // Forerunner非適用低速ノード
  }

  public FutureContextSharingManager(Node ownerNode) {
    this.ownerNode = ownerNode;
    this.localContexts = new ConcurrentHashMap<>();
    this.sharedContexts = new ConcurrentHashMap<>();
    this.nodePerformanceRatings = new ConcurrentHashMap<>();
    
    // SIM_SEEDを使用してRandomオブジェクトを初期化
    String seedStr = System.getenv("SIM_SEED");
    long seed;
    if (seedStr != null && !seedStr.trim().isEmpty()) {
      try {
        seed = Long.parseLong(seedStr.trim());
      } catch (NumberFormatException e) {
        seed = System.nanoTime();
      }
    } else {
      seed = System.nanoTime();
    }
    
    // ノードIDも含めてシード値を決定（同じSIM_SEEDでも異なるノードで異なる乱数シーケンス）
    this.random = new Random(seed + ownerNode.getNodeID());

    // ノードタイプと性能を判定
    this.nodeType = determineNodeType();
    this.isHighPerformanceNode =
        (nodeType == NodeType.FORERUNNER_HIGH || nodeType == NodeType.NON_FORERUNNER_HIGH);

    // 設定パラメータの初期化
    this.sharingThreshold = determineSharingThreshold(); // 環境変数で上書き可能
    this.fastShareEnabled = determineFastShareEnabled();
    this.maxContextsPerBlock = 3; // ブロックあたり最大3つのコンテキスト
    this.contextGenerationDelay = getContextGenerationDelay();
    this.broadcastScope = determineBroadcastScope();
    this.sharingEnabled = determineSharingEnabled();
    this.minShareIntervalMs = determineMinShareIntervalMs();

    // ノード初期化の簡潔なログ（初回のみ）
    if (ownerNode.getNodeID() <= 10) { // 最初の10ノードのみログ出力
      DebugLogger.nodeDebug(
          ownerNode.getNodeID(),
          String.format(
              "Node%d: %s (%s性能)",
              ownerNode.getNodeID(), nodeType, isHighPerformanceNode ? "High" : "Low"));
    }
    
    // トラフィックログ有効時は、高速ノードの存在を確認
    String trafficLogEnable =
        System.getenv()
            .getOrDefault("FORERUNNER_TRAFFIC_LOG_ENABLE", "false")
            .trim()
            .toLowerCase();
    boolean trafficLogEnabled =
        "1".equals(trafficLogEnable)
            || "true".equals(trafficLogEnable)
            || "on".equals(trafficLogEnable);
    
    if (trafficLogEnabled && nodeType == NodeType.FORERUNNER_HIGH) {
      System.out.println("DEBUG_TRAFFIC: Node " + ownerNode.getNodeID() + " is FORERUNNER_HIGH");
    }
    
    // 低速ノード割合90%の場合、高速ノードの存在を確認
    String slowNodeRatioStr = System.getenv("SLOW_NODE_RATIO");
    if (slowNodeRatioStr != null && "0.9".equals(slowNodeRatioStr.trim())) {
      if (nodeType == NodeType.FORERUNNER_HIGH && ownerNode.getNodeID() <= 100) {
        System.out.println("DEBUG_SLOW90: Node " + ownerNode.getNodeID() + " is FORERUNNER_HIGH");
      }
    }

    // 設定情報の出力を削除（冗長なため）
  }

  /** 共有機能の有効/無効を判定（環境変数 FORERUNNER_SHARING_ENABLED） */
  private boolean determineSharingEnabled() {
    String v = System.getenv("FORERUNNER_SHARING_ENABLED");
    if (v == null || v.isEmpty()) return true; // 既定: 共有有効
    v = v.trim().toLowerCase();
    return !("0".equals(v) || "false".equals(v) || "off".equals(v) || "no".equals(v));
  }

  /** 品質無視で即共有するモード（FORERUNNER_FAST_SHARE=true） */
  private boolean determineFastShareEnabled() {
    String v = System.getenv("FORERUNNER_FAST_SHARE");
    if (v == null || v.isEmpty()) return false;
    v = v.trim().toLowerCase();
    return !("0".equals(v) || "false".equals(v) || "off".equals(v) || "no".equals(v));
  }

  /** 共有しきい値を環境変数 FORERUNNER_SHARING_THRESHOLD で指定（既定0.5） */
  private double determineSharingThreshold() {
    String v = System.getenv("FORERUNNER_SHARING_THRESHOLD");
    double fallback = 0.5; // 既定: 50%
    if (v == null || v.isEmpty()) return fallback;
    try {
      double parsed = Double.parseDouble(v.trim());
      if (parsed < 0.0 || parsed > 1.0) return fallback;
      return parsed;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** 共有の最小間隔（ミリ秒）を決定（環境変数 FORERUNNER_MIN_SHARE_INTERVAL_MS） */
  private long determineMinShareIntervalMs() {
    String v = System.getenv("FORERUNNER_MIN_SHARE_INTERVAL_MS");
    long fallback = 5L; // 既定: 5ms（共有嵐抑制のための軽いレート制限）
    if (v == null || v.isEmpty()) return fallback;
    try {
      long parsed = Long.parseLong(v.trim());
      if (parsed < 0L) return fallback;
      return parsed;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** ノードタイプを判定 */
  private NodeType determineNodeType() {
    // ノードのクラス名や設定からタイプを判定
    String nodeClassName = ownerNode.getClass().getSimpleName();

    // ForerunnerNodeの場合
    if (nodeClassName.equals("ForerunnerNode")) {
      // ForerunnerNodeの性能を判定（高速/低速）
      return determineForerunnerNodePerformance();
    }

    // 通常のNodeの場合
    return determineNonForerunnerNodeType();
  }

  /** ForerunnerNodeの性能を判定 */
  private NodeType determineForerunnerNodePerformance() {
    // 環境変数から低速ノード割合を取得
    String slowNodeRatioStr = System.getenv("SLOW_NODE_RATIO");
    double slowNodeRatio = 0.5; // デフォルト: 50%が低速ノード

    if (slowNodeRatioStr != null && !slowNodeRatioStr.isEmpty()) {
      try {
        slowNodeRatio = Double.parseDouble(slowNodeRatioStr);
        // 0.0から1.0の範囲に制限
        slowNodeRatio = Math.max(0.0, Math.min(1.0, slowNodeRatio));
      } catch (NumberFormatException e) {
        System.err.println(
            "Warning: Invalid SLOW_NODE_RATIO value: " + slowNodeRatioStr + ". Using default: 0.7");
      }
    }

    // ForerunnerNodeの性能を決定
    // slowNodeRatioが0.7の場合、30%が高速ノードになる
    double highPerformanceProbability = 1.0 - slowNodeRatio;
    double randomValue = random.nextDouble();
    boolean isHighPerformance = randomValue < highPerformanceProbability;
    
    // トラフィックログ有効時は、判定結果をログ出力
    String trafficLogEnable =
        System.getenv()
            .getOrDefault("FORERUNNER_TRAFFIC_LOG_ENABLE", "false")
            .trim()
            .toLowerCase();
    boolean trafficLogEnabled =
        "1".equals(trafficLogEnable)
            || "true".equals(trafficLogEnable)
            || "on".equals(trafficLogEnable);
    
    if (trafficLogEnabled && ownerNode.getNodeID() <= 100) {
      System.out.println(
          String.format(
              "DEBUG_NODE_TYPE: Node %d: slowRatio=%.2f, prob=%.2f, random=%.4f, isHigh=%b",
              ownerNode.getNodeID(),
              slowNodeRatio,
              highPerformanceProbability,
              randomValue,
              isHighPerformance));
    }

    return isHighPerformance ? NodeType.FORERUNNER_HIGH : NodeType.FORERUNNER_LOW;
  }

  /** 通常のNodeのタイプを判定 */
  private NodeType determineNonForerunnerNodeType() {
    // 環境変数から低速ノード割合を取得
    String slowNodeRatioStr = System.getenv("SLOW_NODE_RATIO");
    double slowNodeRatio = 0.5; // デフォルト: 50%が低速ノード

    if (slowNodeRatioStr != null && !slowNodeRatioStr.isEmpty()) {
      try {
        slowNodeRatio = Double.parseDouble(slowNodeRatioStr);
        // 0.0から1.0の範囲に制限
        slowNodeRatio = Math.max(0.0, Math.min(1.0, slowNodeRatio));
      } catch (NumberFormatException e) {
        System.err.println(
            "Warning: Invalid SLOW_NODE_RATIO value: " + slowNodeRatioStr + ". Using default: 0.7");
      }
    }

    // 通常のNodeの性能を決定
    // slowNodeRatioが0.7の場合、30%が高速ノードになる
    double highPerformanceProbability = 1.0 - slowNodeRatio;
    boolean isHighPerformance = random.nextDouble() < highPerformanceProbability;

    return isHighPerformance ? NodeType.NON_FORERUNNER_HIGH : NodeType.NON_FORERUNNER_LOW;
  }

  /** ブロードキャスト範囲を判定 */
  private BroadcastScope determineBroadcastScope() {
    String broadcastScopeStr = System.getenv("BROADCAST_SCOPE");

    if (broadcastScopeStr != null && !broadcastScopeStr.isEmpty()) {
      try {
        switch (broadcastScopeStr.toUpperCase()) {
          case "NEIGHBORS_ONLY":
            return BroadcastScope.NEIGHBORS_ONLY;
          case "SLOW_NODES_ONLY":
            return BroadcastScope.SLOW_NODES_ONLY;
          case "ALL_NODES":
            return BroadcastScope.ALL_NODES;
          case "CUSTOM_RANGE":
            return BroadcastScope.CUSTOM_RANGE;
          case "REQUEST_BASED":  // 新規追加
            return BroadcastScope.REQUEST_BASED;
          default:
            System.err.println(
                "Warning: Invalid BROADCAST_SCOPE value: "
                    + broadcastScopeStr
                    + ". Using default: NEIGHBORS_ONLY");
            return BroadcastScope.NEIGHBORS_ONLY;
        }
      } catch (Exception e) {
        System.err.println(
            "Warning: Error parsing BROADCAST_SCOPE: "
                + e.getMessage()
                + ". Using default: NEIGHBORS_ONLY");
        return BroadcastScope.NEIGHBORS_ONLY;
      }
    }

    // デフォルトは隣接ノードのみ
    return BroadcastScope.NEIGHBORS_ONLY;
  }

  /** ノードタイプに基づいてコンテキスト生成時間を取得 */
  public long getContextGenerationDelay() {
    switch (nodeType) {
      case FORERUNNER_HIGH:
        return 800; // 高性能ノード: 0.8秒（平均的な予測時間より短い）
      case FORERUNNER_LOW:
        return 1200; // 低性能ノード: 1.2秒（平均的な予測時間より長い）
      case NON_FORERUNNER_HIGH:
      case NON_FORERUNNER_LOW:
        return 0; // NON_FORERUNNERノードはコンテキスト生成しないため0
      default:
        return 0; // デフォルト: 0
    }
  }

  /** トランザクションプールの変更を監視し、未来コンテキスト生成を開始 */
  public void onTransactionPoolUpdate(List<Transaction> newTransactions) {
    if (!sharingEnabled) {
      return; // 共有OFF時は完全スキップ
    }
    int txCount = (newTransactions == null) ? -1 : newTransactions.size();
    simblock.util.DebugLogger.diagLog(
        "onTxPoolUpdate node=" + ownerNode.getNodeID() + ", txCount=" + txCount);

    // NON_FORERUNNERノードはコンテキスト生成しない
    if (nodeType == NodeType.NON_FORERUNNER_HIGH || nodeType == NodeType.NON_FORERUNNER_LOW) {
      return;
    }

    // Forerunner低速ノードの処理
    if (nodeType == NodeType.FORERUNNER_LOW) {
      // REQUEST_BASEDの場合、初回要求を送信
      if (broadcastScope == BroadcastScope.REQUEST_BASED) {
        Block currentBlock = ownerNode.getBlock();
        if (currentBlock != null) {
          long nextBlockIndex = currentBlock.getIndex() + 1;
          // 既に未来コンテキストを持っている場合は要求しない
          if (!localContexts.containsKey(nextBlockIndex) && 
              !sharedContexts.containsKey(nextBlockIndex)) {
            // 待機中でない場合のみ初回要求を送信（記憶方式により、再要求は不要）
            // ただし、隣接ノードにForerunner適用ノードがない場合は要求を送信しない
            if (!waitingForSharedContext.containsKey(nextBlockIndex)) {
              // 隣接ノードにForerunner適用ノードがあるか確認
              List<Node> adjacentForerunnerNodes = getAdjacentForerunnerNodes();
              Node preferredHigh = selectPreferredHighNode();
              
              // 隣接ノードにForerunner適用ノードがある場合のみ要求を送信
              if (preferredHigh != null || !adjacentForerunnerNodes.isEmpty()) {
                requestFutureContext(nextBlockIndex);
              }
              // 隣接ノードにForerunner適用ノードがない場合は要求を送信しない
              // （未来コンテキストなしで通常処理を続行）
            }
          } else {
            // 既にコンテキストを持っている場合、待機状態を終了
            if (waitingForSharedContext.containsKey(nextBlockIndex)) {
              stopWaitingForSharedContext(nextBlockIndex);
            }
          }
        }
        return;
      }
      
      // REQUEST_BASED以外の場合は既存の動作（待機）
      simblock.util.DebugLogger.diagLog(
          "onTxPoolUpdate: Node " + ownerNode.getNodeID() + " is FORERUNNER_LOW, waiting");
      startWaitingForSharedContext();
      return;
    }

    // Forerunner高速ノードのみが未来コンテキストを生成（タイマーで遅延・非同期実行）
    // 取引が空でも生成を試行（FAST_SHARE等のモードで共有可否を観測するため）
    simblock.util.DebugLogger.diagLog(
        "onTxPoolUpdate: Node " + ownerNode.getNodeID() + " is FORERUNNER_HIGH, scheduling generation");
    scheduleContextGenerationViaTimer(
        newTransactions == null ? java.util.Collections.emptyList() : newTransactions);
  }

  /** 遅延付きで未来コンテキスト生成をスケジュール */
  private void scheduleContextGenerationWithDelay(
      List<Transaction> transactions, Block currentBlock) {
    // NON_FORERUNNERノードはコンテキスト生成しない
    if (nodeType == NodeType.NON_FORERUNNER_HIGH || nodeType == NodeType.NON_FORERUNNER_LOW) {
      return;
    }

    // 既に生成済みのコンテキストがあるかチェック
    long nextBlockIndex = currentBlock.getIndex() + 1;
    if (localContexts.containsKey(nextBlockIndex)) {
      return;
    }

    // 遅延後にコンテキスト生成を実行
    // 実際の実装では、Timerを使用して遅延実行する必要がある
    // ここでは簡易的に直接実行
    try {
      Thread.sleep(contextGenerationDelay);
      EnhancedFutureContext context = generateFutureContext(currentBlock, transactions);
      if (context != null) {
        simblock.util.DebugLogger.diagLog(
            "gen context block="
                + context.getBlockIndex()
                + ", conf="
                + String.format("%.2f", context.getConfidence())
                + ", sharingEnabled="
                + sharingEnabled
                + ", fastShare="
                + fastShareEnabled);
        // FAST_SHARE: 品質条件を無視して即共有
        if (fastShareEnabled
            || (context.getConfidence() >= this.sharingThreshold
                && context.getConstraintScore() >= 0.6
                && context.getDetailLevel() >= 0.5)) {
          // デバッグログを無効化
          // System.out.println(
          //     "Node " + ownerNode.getNodeID() +
          //     " sharing context for block " + nextBlockIndex +
          //     " (confidence: " + String.format("%.3f", context.getConfidence()) +
          //     " (confidence: " + String.format("%.3f", context.getConfidence()) +
          //     ", constraintScore: " + String.format("%.3f", context.getConstraintScore()) +
          //     ", detailLevel: " + String.format("%.3f", context.getDetailLevel()) +
          //     ", predictionType: " + context.getPredictionType() + ")");
          simblock.util.DebugLogger.diagLog("before share block=" + context.getBlockIndex());
          shareContext(context);
        } else {
          // デバッグログを無効化
          // System.out.println(
          //     "Node " + ownerNode.getNodeID() +
          //     " NOT sharing context for block " + nextBlockIndex +
          //     " (confidence: " + String.format("%.3f", context.getConfidence()) +
          //     " < 0.7 OR constraintScore: " + String.format("%.3f", context.getConstraintScore())
          // +
          //     " < 0.6 OR detailLevel: " + String.format("%.3f", context.getDetailLevel()) +
          //     " < 0.6 OR detailLevel: " + String.format("%.3f", context.getDetailLevel()) +
          //     " < 0.5)");
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Context generation interrupted for node " + ownerNode.getNodeID());
    }
  }

  /** タイマーで未来コンテキスト生成をスケジュール（完全非同期） */
  private void scheduleContextGenerationViaTimer(List<Transaction> transactions) {
    Block currentBlock = ownerNode.getBlock();
    if (currentBlock == null) {
      return;
    }
    simblock.util.DebugLogger.diagLog(
        "scheduleGen node="
            + ownerNode.getNodeID()
            + ", block="
            + currentBlock.getIndex()
            + ", txCount="
            + transactions.size());
    long nextBlockIndex = currentBlock.getIndex() + 1;
    if (localContexts.containsKey(nextBlockIndex)) {
      return;
    }
    long delay = Math.max(0L, contextGenerationDelay);
    ExecutionDelayTask task =
        new ExecutionDelayTask(ownerNode, delay) {
          @Override
          public void run() {
    generateFutureContext(currentBlock, transactions);
          }
        };
    putTask(task);
  }

  /** 未来コンテキストを生成 */
  public EnhancedFutureContext generateFutureContext(
      Block currentBlock, List<Transaction> transactions) {
    long nextBlockIndex = currentBlock.getIndex() + 1;

    // 既存のコンテキストをチェック
    if (localContexts.containsKey(nextBlockIndex)) {
      return localContexts.get(nextBlockIndex);
    }

    // 新しい詳細化未来コンテキストを生成
    EnhancedFutureContext context =
        new EnhancedFutureContext(currentBlock, String.valueOf(ownerNode.getNodeID()));

    // ローカルコンテキストとして保存
    localContexts.put(nextBlockIndex, context);

    // 【重要】信頼度50%以上で即座に共有開始
    // 制約スコアが低くても基本信頼度(60%)により、ほとんどのコンテキストが共有対象となる
    if (fastShareEnabled || context.getConfidence() >= this.sharingThreshold) {
      // デバッグログを無効化
      // System.out.println(
      //     "Node " + ownerNode.getNodeID() +
      //     " sharing context for block " + nextBlockIndex +
      //     " (confidence: " + String.format("%.3f", context.getConfidence()) +
      //     ", constraintScore: " + String.format("%.3f", context.getConstraintScore()) +
      //     ", predictionType: " + context.getPredictionType() + ")");
      shareContext(context);
    } else {
      // デバッグログを無効化
      // System.out.println(
      //     "Node " + ownerNode.getNodeID() +
      //     " NOT sharing context for block " + nextBlockIndex +
      //     " (confidence: " + String.format("%.3f", context.getConfidence()) +
      //     " < threshold: " + sharingThreshold + ")");
    }

    // 生成されたコンテキストを返す
    return context;
  }

  /** アカウント残高を予測（簡略化版） */
  private void predictAccountBalances(
      EnhancedFutureContext context, List<Transaction> transactions) {
    // 簡略化: 実装なし（EnhancedFutureContextで不要になったため）
  }

  /** ストレージ値を予測（簡略化版） */
  private void predictStorageValues(EnhancedFutureContext context, List<Transaction> transactions) {
    // 簡略化: 実装なし（EnhancedFutureContextで不要になったため）
  }

  /** コンテキストの信頼度を更新（簡略化版） */
  private void updateContextConfidence(
      EnhancedFutureContext context, List<Transaction> transactions) {
    // 簡略化: 信頼度はEnhancedFutureContextのコンストラクタで自動計算されるため、ここでは何もしない
  }

  /** 未来コンテキストを共有 */
  private void shareContext(EnhancedFutureContext context) {
    if (!sharingEnabled) {
      simblock.util.DebugLogger.diagLog("share skipped: sharingEnabled=false");
      return; // 共有無効時は何もしない（ローカルのみ保持）
    }

    // REQUEST_BASEDの場合は要求ベースの送信
    if (broadcastScope == BroadcastScope.REQUEST_BASED) {
      // レート制限（共有嵐の抑制）- 要求ベースの送信でもレート制限を適用
      long now = System.currentTimeMillis();
      if (now - lastShareEpochMs < minShareIntervalMs) {
        return;
      }
      lastShareEpochMs = now;
      
      // 共有コンテキストとして保存（既存の動作と同様）
      sharedContexts.put(context.getBlockIndex(), context);
      // 要求ベースの送信（要求がある場合のみ送信）
      shareContextToRequestingNodes(context);
      return;
    }

    // 既存のブロードキャスト方式（NEIGHBORS_ONLY等）
    // レート制限（共有嵐の抑制）
    long now = System.currentTimeMillis();
    if (now - lastShareEpochMs < minShareIntervalMs) {
      return;
    }
    lastShareEpochMs = now;
    // 【重要】高速ノードは実際に生成した未来コンテキストが正解したかどうかに関わらずブロードキャストを行う
    // 正解率は受信側でForerunnerConfigの値に基づいて処理される

    // 共有コンテキストとして保存
    sharedContexts.put(context.getBlockIndex(), context);

    // ブロードキャスト範囲に応じて送信
    List<Node> targetNodes = getTargetNodesForBroadcast();
    
    // ブロードキャスト先が空の場合でも、ブロードキャストの試行自体はトラフィックとして記録
    // （ブロードキャスト先がなくても、ブロードキャスト処理は実行されるため）
    if (targetNodes.isEmpty()) {
      simblock.util.DebugLogger.diagLog("shareContext: targetNodes is empty, but recording broadcast attempt");
      // ブロードキャスト試行をトラフィックとして記録（送信先が0でもブロードキャスト処理は実行される）
      long currentTime = getCurrentTime();
      long blockSize = simblock.settings.SimulationConfiguration.BLOCK_SIZE;
      simblock.util.DebugLogger.recordTrafficSend(blockSize, currentTime);
      return;
    }

    for (Node targetNode : targetNodes) {
      // トラフィックログ: 各送信イベントごとに1回出力
      simblock.util.DebugLogger.trafficLogSend();
      // トラフィック量を記録（BLOCK_SIZEを使用）
      long currentTime = getCurrentTime();
      long blockSize = simblock.settings.SimulationConfiguration.BLOCK_SIZE;
      simblock.util.DebugLogger.recordTrafficSend(blockSize, currentTime);
      // 反射の直同期呼び出しを避け、タイマー遅延で送信
      long jitter = 1L + (long) (random.nextDouble() * 4L); // 1〜5msジッタ
      ExecutionDelayTask sendTask =
          new ExecutionDelayTask(ownerNode, jitter) {
            @Override
            public void run() {
      sendContextToNode(context, targetNode);
            }
          };
      putTask(sendTask);
    }

    // デバッグログを無効化
    // System.out.println(
    //     "Context shared to " + targetNodes.size() + " nodes (scope: " + broadcastScope + ")");
  }

  /** 即座に未来コンテキストを共有（ブロードキャスト範囲に応じて） */
  private void shareContextImmediately(EnhancedFutureContext context) {
    if (!sharingEnabled) {
      return; // 共有無効時は何もしない
    }
    // 共有済みとしてマーク（削除されたメソッド）
    // context.markAsShared();

    // 共有コンテキストとして保存
    sharedContexts.put(context.getBlockIndex(), context);

    // ブロードキャスト範囲に応じて送信
    List<Node> targetNodes = getTargetNodesForBroadcast();
    for (Node targetNode : targetNodes) {
      // トラフィックログ: 各送信イベントごとに1回出力
      simblock.util.DebugLogger.trafficLogSend();
      long jitter = 1L + (long) (random.nextDouble() * 4L);
      ExecutionDelayTask sendTask =
          new ExecutionDelayTask(ownerNode, jitter) {
            @Override
            public void run() {
      sendContextToNode(context, targetNode);
            }
          };
      putTask(sendTask);
    }

    // デバッグログを無効化
    // System.out.println(
    //     "Context shared immediately to "
    //         + targetNodes.size()
    //         + " nodes (scope: "
    //         + broadcastScope
    //         + ")");
  }

  /** 他のノードから未来コンテキストを受信 */
  public void receiveSharedContext(EnhancedFutureContext context, Node sender) {
    if (!sharingEnabled) {
      return; // 共有無効時は受信しない
    }
    // NON_FORERUNNERノードはコンテキストを受信しない
    if (nodeType == NodeType.NON_FORERUNNER_HIGH || nodeType == NodeType.NON_FORERUNNER_LOW) {
      return;
    }

    long blockIndex = context.getBlockIndex();

    // 【重要】各ブロック計算時に一度だけ未来コンテキストを受信
    // 既に受信済みの場合は、新しいコンテキストは受け取らない
    if (sharedContexts.containsKey(blockIndex)) {
      // デバッグログを無効化
      // System.out.println(
      //     "Node " + ownerNode.getNodeID() +
      //     " already received context for block " + blockIndex +
      //     " from another node, ignoring new context from Node " + sender.getNodeID());
      return;
    }

    // 待機中の低速ノードの場合、待機を終了（REQUEST_BASEDの場合も含む）
    if (waitingForSharedContext.containsKey(blockIndex)
        && waitingForSharedContext.get(blockIndex)) {
      stopWaitingForSharedContext(blockIndex);
    }

    // 既存のローカルコンテキストがあるかチェック
    EnhancedFutureContext existingContext = localContexts.get(blockIndex);

    if (existingContext != null) {
      // より詳細な優先順位判定
      if (shouldAdoptReceivedContext(context, existingContext, sender)) {
        // 受信したコンテキストを採用
        adoptReceivedContext(context, blockIndex);
      }
    } else {
      // 新しいコンテキストとして保存
      saveNewContext(context, blockIndex);
    }
    
    // REQUEST_BASEDの場合、低速ノードが受信した後、他の低速ノードからの要求に応じて送信可能にする
    if (broadcastScope == BroadcastScope.REQUEST_BASED && 
        nodeType == NodeType.FORERUNNER_LOW) {
      // 受信したコンテキストをローカルコンテキストとしても保存（要求に応じて送信可能にするため）
      // 既に`saveNewContext()`または`adoptReceivedContext()`で保存されている可能性があるが、
      // 念のため確認して保存
      if (!localContexts.containsKey(blockIndex)) {
        localContexts.put(blockIndex, context);
      }
      
      // 【重要】他の低速ノードからの要求に応じて送信（要求がある場合のみ）
      shareContextToRequestingNodes(context);
    }

    // 送信ノードの性能評価を更新
    updateNodePerformanceRating(String.valueOf(sender.getNodeID()), context.getConfidence());

    // トラフィックログ: 受信成功時に1回出力
    simblock.util.DebugLogger.trafficLogRecv();
    // トラフィック量を記録（BLOCK_SIZEを使用）
    long currentTime = getCurrentTime();
    long blockSize = simblock.settings.SimulationConfiguration.BLOCK_SIZE;
    simblock.util.DebugLogger.recordTrafficRecv(blockSize, currentTime);

    // デバッグログを無効化
    // System.out.println(
    //     "Node " + ownerNode.getNodeID() +
    //     " received context from Node " + sender.getNodeID() +
    //     " for block " + blockIndex +
    //     " (confidence: " + String.format("%.3f", context.getConfidence()) +
    //     ", predictionType: " + context.getPredictionType() +
    //     ", constraintScore: " + String.format("%.3f", context.getConstraintScore()) + ")");
    //
    // // 正解率の確認（ForerunnerConfigの値に基づく）
    // System.out.println(
    //     "  → Expected accuracy rates: Perfect=" +
    //     String.format("%.2f", simblock.settings.ForerunnerConfig.PERFECT_PREDICTION_RATE * 100) +
    //     "%, Imperfect=" +
    //     String.format("%.2f", simblock.settings.ForerunnerConfig.IMPERFECT_PREDICTION_RATE * 100)
    // +
    //     "%, Missed=" +
    //     String.format("%.2f", simblock.settings.ForerunnerConfig.MISSED_PREDICTION_RATE * 100) +
    // "%");
  }

  /** コンテキスト生成をキャンセル */
  private void cancelContextGeneration(long blockIndex) {
    // 生成中のコンテキストを削除
    localContexts.remove(blockIndex);

    // デバッグ出力
    // デバッグログを無効化
    // System.out.println("Context generation cancelled for block " + blockIndex);
  }

  /** ノードの性能評価を更新 */
  private void updateNodePerformanceRating(String nodeId, double confidence) {
    nodePerformanceRatings.put(nodeId, (long) (confidence * 100));
  }

  /** 特定のブロックの未来コンテキストを取得 */
  public EnhancedFutureContext getFutureContext(long blockIndex) {
    // 共有コンテキストを優先
    EnhancedFutureContext sharedContext = sharedContexts.get(blockIndex);
    if (sharedContext != null) {
      return sharedContext;
    }

    // ローカルコンテキストを返す
    return localContexts.get(blockIndex);
  }

  /** コンテキストの適用可能性をチェック */
  public boolean canApplyContext(EnhancedFutureContext context, Block actualBlock) {
    if (context == null || actualBlock == null) {
      return false;
    }

    // 【修正後】より厳格な信頼度チェック（70%以上）
    if (context.getConfidence() < this.sharingThreshold) {
      // デバッグログを無効化
      // System.out.println(
      //     "Node " + ownerNode.getNodeID() +
      //     " cannot apply context for block " + context.getBlockIndex() +
      //     " (confidence: " + String.format("%.3f", context.getConfidence()) + " < 0.7)");
      return false;
    }

    // 【修正後】制約スコアチェック（60%以上）
    if (context.getConstraintScore() < 0.6) {
      // デバッグログを無効化
      // System.out.println(
      //     "Node " + ownerNode.getNodeID() +
      //     " cannot apply context for block " + context.getBlockIndex() +
      //     " (constraintScore: " + String.format("%.3f", context.getConstraintScore()) + " <
      // 0.6)");
      return false;
    }

    // 【修正後】詳細度チェック（50%以上）
    if (context.getDetailLevel() < 0.5) {
      // デバッグログを無効化
      // System.out.println(
      //     "Node " + ownerNode.getNodeID() +
      //     " cannot apply context for block " + context.getBlockIndex() +
      //     " (detailLevel: " + String.format("%.3f", context.getDetailLevel()) + " < 0.5)");
      return false;
    }

    // デバッグログを無効化
    // System.out.println(
    //     "Node " + ownerNode.getNodeID() +
    //     " can apply context for block " + context.getBlockIndex() +
    //     " (confidence: " + String.format("%.3f", context.getConfidence()) +
    //     ", constraintScore: " + String.format("%.3f", context.getConstraintScore()) +
    //     ", detailLevel: " + String.format("%.3f", context.getDetailLevel()) +
    //     ", predictionType: " + context.getPredictionType() + ")");

    return true;
  }

  /** 予測タイプに基づく期待正解率を取得 */
  private double getExpectedAccuracy(String predictionType) {
    switch (predictionType.toUpperCase()) {
      case "PERFECT":
        return simblock.settings.ForerunnerConfig.PERFECT_PREDICTION_RATE * 100;
      case "IMPERFECT":
        return simblock.settings.ForerunnerConfig.IMPERFECT_PREDICTION_RATE * 100;
      case "MISSED":
        return simblock.settings.ForerunnerConfig.MISSED_PREDICTION_RATE * 100;
      default:
        return 0.0;
    }
  }

  /** フォールバック処理（通常のEVM実行に戻す） */
  public void fallbackToNormalExecution(long blockIndex) {
    // 該当するコンテキストを削除
    localContexts.remove(blockIndex);
    sharedContexts.remove(blockIndex);
  }

  /** 隣接ノードのリストを取得 */
  private List<Node> getNeighborNodes() {
    List<Node> neighbors = new ArrayList<>();

    try {
      // ノードの隣接ノードリストを取得
      Method getNeighborsMethod = ownerNode.getClass().getMethod("getNeighbors");
      @SuppressWarnings("unchecked")
      List<Node> neighborList = (List<Node>) getNeighborsMethod.invoke(ownerNode);

      if (neighborList != null) {
        neighbors.addAll(neighborList);
      }
    } catch (Exception e) {
      System.err.println("Warning: Could not get neighbor nodes: " + e.getMessage());
    }

    return neighbors;
  }

  /**
   * 隣接したForerunner適用ノード（高速・低速問わず）を取得
   */
  private List<Node> getAdjacentForerunnerNodes() {
    List<Node> adjacentForerunnerNodes = new ArrayList<>();
    List<Node> neighbors = getNeighborNodes();
    
    for (Node neighbor : neighbors) {
      if (neighbor instanceof ForerunnerNode) {
        try {
          Method getFcsmMethod = neighbor.getClass().getMethod("getFutureContextSharingManager");
          FutureContextSharingManager neighborFcsm = 
              (FutureContextSharingManager) getFcsmMethod.invoke(neighbor);
          
          if (neighborFcsm != null) {
            NodeType neighborType = neighborFcsm.getNodeType();
            // Forerunner適用ノード（高速・低速問わず）を追加
            if (neighborType == NodeType.FORERUNNER_HIGH || 
                neighborType == NodeType.FORERUNNER_LOW) {
              adjacentForerunnerNodes.add(neighbor);
            }
          }
        } catch (Exception e) {
          // スキップ
        }
      }
    }
    
    return adjacentForerunnerNodes;
  }

  /**
   * 優先的に要求を送る高速ノードを選択（低速ノード側）
   */
  private Node selectPreferredHighNode() {
    List<Node> adjacentForerunnerNodes = getAdjacentForerunnerNodes();
    
    // 高速ノードを優先的に選択
    for (Node node : adjacentForerunnerNodes) {
      try {
        Method getFcsmMethod = node.getClass().getMethod("getFutureContextSharingManager");
        FutureContextSharingManager neighborFcsm = 
            (FutureContextSharingManager) getFcsmMethod.invoke(node);
        
        if (neighborFcsm != null && 
            neighborFcsm.getNodeType() == NodeType.FORERUNNER_HIGH) {
          return node; // 最初に見つかった高速ノードを返す
        }
      } catch (Exception e) {
        // スキップ
      }
    }
    
    return null; // 高速ノードが見つからない場合はnull
  }

  /**
   * 優先高速ノードの有効性を確認
   */
  private boolean isPreferredHighNodeValid() {
    if (preferredHighNode == null) {
      return false;
    }
    
    // 隣接ノードにpreferredHighNodeが含まれているか確認
    List<Node> adjacentForerunnerNodes = getAdjacentForerunnerNodes();
    return adjacentForerunnerNodes.contains(preferredHighNode);
  }

  /**
   * 未来コンテキスト共有要求を送信（低速ノード側）
   * @param blockIndex 要求するブロックインデックス
   */
  public void requestFutureContext(long blockIndex) {
    // REQUEST_BASEDの場合のみ動作
    if (broadcastScope != BroadcastScope.REQUEST_BASED) {
      return;
    }
    
    if (nodeType != NodeType.FORERUNNER_LOW) {
      return; // 低速ノードのみ
    }
    
    // 既に未来コンテキストを持っている場合は要求しない
    if (localContexts.containsKey(blockIndex) || 
        sharedContexts.containsKey(blockIndex)) {
      return;
    }
    
    // 優先高速ノードを選択（初回のみ、または接続が切れた場合）
    // 有効性を確認してから選択
    if (preferredHighNode == null || !isPreferredHighNodeValid()) {
      preferredHighNode = selectPreferredHighNode();
    }
    
    // 【重要】Forerunner適用高速ノードに隣接する場合は高速ノードにのみ要求を送信
    if (preferredHighNode != null) {
      sendRequestToNode(preferredHighNode, blockIndex);
      // 要求を送信した後、待機状態を開始（未来コンテキストの到着を待つ）
      startWaitingForRequestedContext(blockIndex);
      return; // 高速ノードに要求を送った場合は終了（低速ノードには要求しない）
    }
    
    // 【重要】高速ノードが見つからない場合、隣接する同様の低速ノード（Forerunner適用低速ノード）に要求を送信
    List<Node> adjacentForerunnerNodes = getAdjacentForerunnerNodes();
    boolean requestSent = false;
    for (Node node : adjacentForerunnerNodes) {
      try {
        Method getFcsmMethod = node.getClass().getMethod("getFutureContextSharingManager");
        FutureContextSharingManager neighborFcsm = 
            (FutureContextSharingManager) getFcsmMethod.invoke(node);
        
        // 隣接するForerunner適用低速ノードにのみ要求を送信
        if (neighborFcsm != null && 
            neighborFcsm.getNodeType() == NodeType.FORERUNNER_LOW) {
          sendRequestToNode(node, blockIndex);
          requestSent = true;
        }
      } catch (Exception e) {
        // スキップ
      }
    }
    
    // 【重要】要求を送信できた場合のみ待機状態を開始
    // 隣接ノードにForerunner適用ノードがない場合は待機状態を開始しない（要求を送信できないため）
    if (requestSent) {
      startWaitingForRequestedContext(blockIndex);
    } else {
      // 隣接ノードにForerunner適用ノードがない場合、要求を送信できないことを記録
      markAsNoAdjacentForerunnerNodes(blockIndex);
    }
  }

  /**
   * 特定のノードに要求を送信
   */
  private void sendRequestToNode(Node targetNode, long blockIndex) {
    try {
      Method getFcsmMethod = targetNode.getClass().getMethod("getFutureContextSharingManager");
      FutureContextSharingManager targetFcsm = 
          (FutureContextSharingManager) getFcsmMethod.invoke(targetNode);
      
      if (targetFcsm != null) {
        targetFcsm.receiveFutureContextRequest(blockIndex, ownerNode);
      }
    } catch (Exception e) {
      System.err.println("Warning: Could not send request to node " 
          + targetNode.getNodeID() + ": " + e.getMessage());
    }
  }

  /**
   * 隣接ノードにForerunner適用ノードがないことを記録（要求を送信できない場合）
   * @param blockIndex ブロックインデックス
   */
  private void markAsNoAdjacentForerunnerNodes(long blockIndex) {
    // このブロックインデックスに対しては、隣接ノードにForerunner適用ノードがないことを記録
    // 以後の要求を抑制するため、待機状態を開始しない
    // （実装としては、特別なマーカーを設定するか、単に待機状態を開始しないことで対応）
    simblock.util.DebugLogger.diagLog(
        "no adjacent Forerunner nodes node=" + ownerNode.getNodeID() + ", block=" + blockIndex);
  }

  /**
   * 要求した未来コンテキストの待機状態を開始（REQUEST_BASED用）
   * @param blockIndex 待機するブロックインデックス
   */
  private void startWaitingForRequestedContext(long blockIndex) {
    // 既に待機中または既にコンテキストがある場合はスキップ
    if (waitingForSharedContext.containsKey(blockIndex)
        || sharedContexts.containsKey(blockIndex)
        || localContexts.containsKey(blockIndex)) {
      return;
    }
    
    // 待機状態を開始（未来コンテキストの到着を待つ）
    waitingForSharedContext.put(blockIndex, true);
    waitingStartTime.put(blockIndex, System.currentTimeMillis());
    
    simblock.util.DebugLogger.diagLog(
        "waiting for requested context node=" + ownerNode.getNodeID() + ", block=" + blockIndex);
  }

  /**
   * 未来コンテキスト共有要求を受信
   * @param blockIndex 要求するブロックインデックス
   * @param requester 要求元ノード
   */
  public void receiveFutureContextRequest(long blockIndex, Node requester) {
    // REQUEST_BASEDの場合のみ動作
    if (broadcastScope != BroadcastScope.REQUEST_BASED) {
      return;
    }
    
    // 要求元がForerunner適用ノードか確認
    if (!(requester instanceof ForerunnerNode)) {
      return; // Forerunner適用ノードからの要求のみ受け付ける
    }
    
    try {
      Method getFcsmMethod = requester.getClass().getMethod("getFutureContextSharingManager");
      FutureContextSharingManager requesterFcsm = 
          (FutureContextSharingManager) getFcsmMethod.invoke(requester);
      
      if (requesterFcsm == null) {
        return;
      }
      
      NodeType requesterType = requesterFcsm.getNodeType();
      
      // 要求元がForerunner適用ノード（高速・低速問わず）か確認
      if (requesterType != NodeType.FORERUNNER_HIGH && 
          requesterType != NodeType.FORERUNNER_LOW) {
        return;
      }
      
      // 【重要】要求を記憶しておき、未来コンテキストが生成されたら自動的に送信する
      // 未来コンテキストを持っていない場合でも要求を記録する（記憶方式）
      
      // 高速ノード側: 要求元をブロックインデックスごとに記録（改善版）
      if (nodeType == NodeType.FORERUNNER_HIGH) {
        requestingLowNodesByBlock.computeIfAbsent(blockIndex, k -> ConcurrentHashMap.newKeySet())
            .add(requester);
      }
      
      // 要求を記録（未来コンテキストを持っていない場合でも記録）
      pendingRequests.computeIfAbsent(blockIndex, k -> ConcurrentHashMap.newKeySet())
          .add(requester);
      
      // 既に未来コンテキストが生成済みの場合は即座に送信
      EnhancedFutureContext localContext = localContexts.get(blockIndex);
      EnhancedFutureContext sharedContext = sharedContexts.get(blockIndex);
      EnhancedFutureContext context = localContext != null ? localContext : sharedContext;
      
      if (context != null) {
        // 既に生成済みの場合は即座に送信
        shareContextToRequestingNodes(context);
      }
      // 未生成の場合は、未来コンテキスト生成時に`shareContext()`→`shareContextToRequestingNodes()`で自動送信される
    } catch (Exception e) {
      System.err.println("Warning: Could not process request from node " 
          + requester.getNodeID() + ": " + e.getMessage());
    }
  }

  /**
   * 要求元のノードにのみ送信
   */
  private void shareContextToRequestingNodes(EnhancedFutureContext context) {
    // REQUEST_BASEDの場合のみ動作
    if (broadcastScope != BroadcastScope.REQUEST_BASED) {
      return;
    }
    
    long blockIndex = context.getBlockIndex();
    Set<Node> requestingNodes = pendingRequests.getOrDefault(
        blockIndex, Collections.emptySet());
    
    if (requestingNodes.isEmpty()) {
      return; // 要求がない場合は送信しない
    }
    
    // 要求元のノードにのみ送信
    for (Node requestingNode : requestingNodes) {
      // 要求元がForerunner適用ノードか確認
      if (!(requestingNode instanceof ForerunnerNode)) {
        continue;
      }
      
      try {
        Method getFcsmMethod = requestingNode.getClass().getMethod("getFutureContextSharingManager");
        FutureContextSharingManager requesterFcsm = 
            (FutureContextSharingManager) getFcsmMethod.invoke(requestingNode);
        
        if (requesterFcsm == null) {
          continue;
        }
        
        NodeType requesterType = requesterFcsm.getNodeType();
        
        // 要求元がForerunner適用ノード（高速・低速問わず）か確認
        if (requesterType != NodeType.FORERUNNER_HIGH && 
            requesterType != NodeType.FORERUNNER_LOW) {
          continue;
        }
        
        // 高速ノード側: 要求してきた低速ノードのみに送信（ブロックインデックスごとに管理）
        if (nodeType == NodeType.FORERUNNER_HIGH) {
          Set<Node> requestingNodesForBlock = requestingLowNodesByBlock.getOrDefault(
              blockIndex, Collections.emptySet());
          if (!requestingNodesForBlock.contains(requestingNode)) {
            continue; // 要求していないノードには送信しない
          }
        }
        
        // トラフィックログ: 各送信イベントごとに1回出力
        simblock.util.DebugLogger.trafficLogSend();
        // トラフィック量を記録（BLOCK_SIZEを使用）
        long currentTime = getCurrentTime();
        long blockSize = simblock.settings.SimulationConfiguration.BLOCK_SIZE;
        simblock.util.DebugLogger.recordTrafficSend(blockSize, currentTime);
        
        // 送信
        long jitter = 1L + (long) (random.nextDouble() * 4L);
        ExecutionDelayTask sendTask = new ExecutionDelayTask(ownerNode, jitter) {
          @Override
          public void run() {
            sendContextToNode(context, requestingNode);
          }
        };
        putTask(sendTask);
      } catch (Exception e) {
        System.err.println("Warning: Could not send context to requesting node " 
            + requestingNode.getNodeID() + ": " + e.getMessage());
      }
    }
    
    // 要求をクリア（送信完了後）
    pendingRequests.remove(blockIndex);
    
    // 高速ノード側: このブロックインデックスに関連する要求元をクリア
    if (nodeType == NodeType.FORERUNNER_HIGH) {
      requestingLowNodesByBlock.remove(blockIndex);
    }
  }

  /** ブロードキャスト範囲に応じて対象ノードを取得 */
  private List<Node> getTargetNodesForBroadcast() {
    switch (broadcastScope) {
      case NEIGHBORS_ONLY:
        return getNeighborNodes();
      case SLOW_NODES_ONLY:
        return getSlowNodes();
      case ALL_NODES:
        return getAllNodes();
      case CUSTOM_RANGE:
        return getCustomRangeNodes();
      default:
        return getNeighborNodes(); // デフォルトは隣接ノードのみ
    }
  }

  /** 全ノードのリストを取得 */
  private List<Node> getAllNodes() {
    List<Node> allNodes = new ArrayList<>();

    try {
      // リフレクションを使用してSimulatorからノードリストを取得
      Class<?> simulatorClass = Class.forName("simblock.simulator.Simulator");
      Field nodesField = simulatorClass.getDeclaredField("nodes");
      nodesField.setAccessible(true);

      @SuppressWarnings("unchecked")
      List<Node> simulatorNodes = (List<Node>) nodesField.get(null);

      if (simulatorNodes != null) {
        allNodes.addAll(simulatorNodes);
      }
    } catch (Exception e) {
      System.err.println("Warning: Could not get all nodes: " + e.getMessage());
    }

    return allNodes;
  }

  /** カスタム範囲のノードを取得（環境変数で指定） */
  private List<Node> getCustomRangeNodes() {
    List<Node> customNodes = new ArrayList<>();

    try {
      String customRangeStr = System.getenv("CUSTOM_BROADCAST_RANGE");
      if (customRangeStr != null && !customRangeStr.isEmpty()) {
        // カスタム範囲の実装（例：特定のノードID範囲）
        // ここでは簡易的に隣接ノードを返す
        customNodes = getNeighborNodes();
      }
    } catch (Exception e) {
      System.err.println("Warning: Error in custom range: " + e.getMessage());
    }

    return customNodes;
  }

  /** 低速ノードのリストを取得（近接するForerunner適用低速ノード、2ホップ先まで） */
  private List<Node> getSlowNodes() {
    List<Node> slowNodes = new ArrayList<>();
    Set<Node> visitedNodes = new HashSet<>(); // 重複を防ぐためのセット

    try {
      // 送信元ノード自身を訪問済みとしてマーク（自分自身は送信先に含めない）
      visitedNodes.add(ownerNode);

      // 1ホップ先（直接の隣接ノード）を取得
      List<Node> oneHopNeighbors = getNeighborNodes();
      
      // 2ホップ先までのノードを収集
      Set<Node> nodesWithinTwoHops = new HashSet<>();
      
      // 1ホップ先のノードを追加
      nodesWithinTwoHops.addAll(oneHopNeighbors);
      
      // 2ホップ先のノードを追加（各隣接ノードの隣接ノード）
      for (Node neighbor : oneHopNeighbors) {
        try {
          Method getNeighborsMethod = neighbor.getClass().getMethod("getNeighbors");
          @SuppressWarnings("unchecked")
          List<Node> neighborNeighbors = (List<Node>) getNeighborsMethod.invoke(neighbor);
          
          if (neighborNeighbors != null) {
            nodesWithinTwoHops.addAll(neighborNeighbors);
          }
        } catch (Exception e) {
          // 隣接ノードの隣接ノード取得に失敗した場合はスキップ
          System.err.println("Warning: Could not get neighbors of neighbor node " 
              + neighbor.getNodeID() + ": " + e.getMessage());
        }
      }

      // 送信元ノード自身を除外
      nodesWithinTwoHops.remove(ownerNode);

      // ForerunnerNodeかつFORERUNNER_LOWタイプのノードのみをフィルタリング
      for (Node node : nodesWithinTwoHops) {
        // 既に追加済みのノードはスキップ（重複排除）
        if (visitedNodes.contains(node)) {
          continue;
        }

        // ForerunnerNodeであることを確認
        if (node instanceof ForerunnerNode) {
          try {
            // FutureContextSharingManagerを取得してノードタイプを確認
            Method getFcsmMethod = node.getClass().getMethod("getFutureContextSharingManager");
            FutureContextSharingManager targetFcsm =
                (FutureContextSharingManager) getFcsmMethod.invoke(node);
            
            // ノードタイプがFORERUNNER_LOWの場合のみ追加
            if (targetFcsm != null && targetFcsm.getNodeType() == NodeType.FORERUNNER_LOW) {
              slowNodes.add(node);
              visitedNodes.add(node);
            }
          } catch (Exception e) {
            // FutureContextSharingManagerの取得に失敗した場合はスキップ
            System.err.println("Warning: Could not get FutureContextSharingManager for node " 
                + node.getNodeID() + ": " + e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      System.err.println("Warning: Could not get slow nodes list: " + e.getMessage());
    }

    return slowNodes;
  }

  /** 特定のノードに未来コンテキストを送信 */
  private void sendContextToNode(EnhancedFutureContext context, Node targetNode) {
    try {
      // 対象ノードのFutureContextSharingManagerを取得
      Method getFcsmMethod = targetNode.getClass().getMethod("getFutureContextSharingManager");
      FutureContextSharingManager targetFcsm =
          (FutureContextSharingManager) getFcsmMethod.invoke(targetNode);

      // 未来コンテキストを受信処理
      targetFcsm.receiveSharedContext(context, ownerNode);

      // デバッグログを無効化
      // System.out.println("Context sent to node " + targetNode.getNodeID());
    } catch (Exception e) {
      System.err.println(
          "Warning: Could not send context to node "
              + targetNode.getNodeID()
              + ": "
              + e.getMessage());
    }
  }

  /** ノードタイプを取得 */
  public NodeType getNodeType() {
    return this.nodeType;
  }

  /** 受信したコンテキストを採用すべきか判定 */
  private boolean shouldAdoptReceivedContext(
      EnhancedFutureContext received, EnhancedFutureContext current, Node sender) {
    // 送信者が高速ノードの場合
    if (sender instanceof ForerunnerNode) {
      return true;
    }

    // 信頼度が大幅に高い場合
    if (received.getConfidence() > current.getConfidence() + 0.2) {
      return true;
    }

    // 生成完了している場合（信頼度で判定）
    if (received.getConfidence() > 0.9 && current.getConfidence() < 0.9) {
      return true;
    }

    return false;
  }

  /** 受信したコンテキストを採用 */
  private void adoptReceivedContext(EnhancedFutureContext context, long blockIndex) {
    // 既存のコンテキストを置き換え
    localContexts.put(blockIndex, context);
    sharedContexts.put(blockIndex, context);

    // 生成中のコンテキストを破棄
    cancelContextGeneration(blockIndex);

    // デバッグログを無効化
    // System.out.println(
    //     "Node " + ownerNode.getNodeID() + " adopted received context for block " + blockIndex);
  }

  /** 新しいコンテキストを保存 */
  private void saveNewContext(EnhancedFutureContext context, long blockIndex) {
    localContexts.put(blockIndex, context);
    sharedContexts.put(blockIndex, context);

    // デバッグログを無効化
    // System.out.println(
    //     "Node " + ownerNode.getNodeID() + " saved new context for block " + blockIndex);
  }

  /** 制約ベース投機実行の詳細化された制約判定 */
  private double evaluateDetailedConstraints(Block block) {
    double constraintScore = 0.0;

    // ブロックの実際の複雑性を考慮
    int txCount = block.getTxList().size();
    double complexityFactor = Math.min(1.0, txCount / 100.0); // トランザクション数による複雑性

    // 1. 制御フロー制約の評価（複雑性を考慮）
    double controlFlowScore = evaluateControlFlowConstraints(block) * complexityFactor;
    constraintScore += controlFlowScore * 0.3; // 30%の重み

    // 2. データ依存関係制約の評価（複雑性を考慮）
    double dataDependencyScore = evaluateDataDependencyConstraints(block) * complexityFactor;
    constraintScore += dataDependencyScore * 0.4; // 40%の重み

    // 3. 実行パス制約の評価（複雑性を考慮）
    double executionPathScore = evaluateExecutionPathConstraints(block) * complexityFactor;
    constraintScore += executionPathScore * 0.2; // 20%の重み

    // 4. 状態遷移制約の評価（複雑性を考慮）
    double stateTransitionScore = evaluateStateTransitionConstraints(block) * complexityFactor;
    constraintScore += stateTransitionScore * 0.1; // 10%の重み

    // 複雑性による最終調整
    constraintScore = constraintScore * complexityFactor;

    return Math.min(1.0, constraintScore);
  }

  /** 制御フロー制約の詳細評価 */
  private double evaluateControlFlowConstraints(Block block) {
    double score = 0.0;

    try {
      // ブロックの構造的複雑性を評価
      int transactionCount = block.getTxList().size();

      // トランザクション数に基づく制御フロー予測可能性
      if (transactionCount <= 10) {
        score += 0.8; // 少ないトランザクションは予測しやすい
      } else if (transactionCount <= 50) {
        score += 0.6; // 中程度のトランザクション
      } else {
        score += 0.4; // 多いトランザクションは予測が困難
      }

      // ブロックの深さに基づく評価
      long blockDepth = block.getIndex();
      if (blockDepth <= 100) {
        score += 0.2; // 初期ブロックは安定している
      } else if (blockDepth <= 1000) {
        score += 0.1; // 中期的なブロック
      }

    } catch (Exception e) {
      score = 0.5; // エラー時は中程度のスコア
    }

    return Math.min(1.0, score);
  }

  /** データ依存関係制約の詳細評価 */
  private double evaluateDataDependencyConstraints(Block block) {
    double score = 0.0;

    try {
      // トランザクション間の依存関係を評価
      List<Transaction> transactions = block.getTxList();

      if (transactions.size() <= 1) {
        score += 0.9; // 単一トランザクションは依存関係なし
      } else {
        // 複数トランザクションの場合、依存関係の複雑性を評価
        int dependencyComplexity = estimateDependencyComplexity(transactions);

        if (dependencyComplexity <= 2) {
          score += 0.7; // 低い依存関係
        } else if (dependencyComplexity <= 5) {
          score += 0.5; // 中程度の依存関係
        } else {
          score += 0.3; // 高い依存関係
        }
      }

    } catch (Exception e) {
      score = 0.5;
    }

    return Math.min(1.0, score);
  }

  /** 実行パス制約の詳細評価 */
  private double evaluateExecutionPathConstraints(Block block) {
    double score = 0.0;

    try {
      // ブロックの実行パスの予測可能性を評価
      long blockIndex = block.getIndex();

      // ブロックインデックスに基づく実行パスの安定性
      if (blockIndex % 100 == 0) {
        score += 0.3; // 100ブロックごとの特殊処理
      }

      if (blockIndex % 1000 == 0) {
        score += 0.2; // 1000ブロックごとの特殊処理
      }

      // 基本的な実行パスの安定性
      score += 0.5;

    } catch (Exception e) {
      score = 0.5;
    }

    return Math.min(1.0, score);
  }

  /** 状態遷移制約の詳細評価 */
  private double evaluateStateTransitionConstraints(Block block) {
    double score = 0.0;

    try {
      // 状態遷移の予測可能性を評価
      long blockIndex = block.getIndex();

      // ブロックインデックスに基づく状態遷移の安定性
      if (blockIndex <= 100) {
        score += 0.4; // 初期ブロックは状態遷移が安定
      } else if (blockIndex <= 1000) {
        score += 0.2; // 中期的なブロック
      }

      // 基本的な状態遷移の安定性
      score += 0.4;

    } catch (Exception e) {
      score = 0.5;
    }

    return Math.min(1.0, score);
  }

  /** 依存関係の複雑性を推定 */
  private int estimateDependencyComplexity(List<Transaction> transactions) {
    // 簡略化された依存関係複雑性の推定
    // 実際の実装では、トランザクションの内容を詳細に分析
    return Math.min(10, transactions.size() / 2);
  }

  /** 制約スコアと信頼度の関係を説明 */
  private static void explainConstraintScoreAndConfidence() {
    // デバッグログを無効化
    // System.out.println("=== 制約スコアと信頼度の関係 ===");
    // System.out.println("【修正後】信頼度計算式:");
    // System.out.println("  基本信頼度: 30%");
    // System.out.println("  制約ボーナス: 制約スコア × 50%");
    // System.out.println("  予測タイプボーナス: Perfect=+20%, Imperfect=+10%, Missed=+0%");
    // System.out.println("  ランダム要素: ±5%");
    // System.out.println("");
    // System.out.println("【修正後】共有条件:");
    // System.out.println("  信頼度: 70%以上");
    // System.out.println("  制約スコア: 60%以上");
    // System.out.println("  詳細度: 50%以上");
    // System.out.println("");
    // System.out.println("【修正後】実際の信頼度範囲:");
    // System.out.println("  最小値: 10%");
    // System.out.println("  最大値: 100%");
    // System.out.println("  平均値: 約40-70%");
    // System.out.println("  共有対象: 高品質なコンテキストのみ");
    // System.out.println("================================");
  }

  // Getters
  public boolean isHighPerformanceNode() {
    return this.isHighPerformanceNode;
  }

  public double getSharingThreshold() {
    return this.sharingThreshold;
  }

  public int getMaxContextsPerBlock() {
    return this.maxContextsPerBlock;
  }

  public Map<Long, EnhancedFutureContext> getLocalContexts() {
    return new HashMap<>(this.localContexts);
  }

  public Map<Long, EnhancedFutureContext> getSharedContexts() {
    return new HashMap<>(this.sharedContexts);
  }

  /** 現在の低速ノード割合を取得 */
  public double getSlowNodeRatio() {
    String slowNodeRatioStr = System.getenv("SLOW_NODE_RATIO");
    double slowNodeRatio = 0.7; // デフォルト値

    if (slowNodeRatioStr != null && !slowNodeRatioStr.isEmpty()) {
      try {
        slowNodeRatio = Double.parseDouble(slowNodeRatioStr);
        slowNodeRatio = Math.max(0.0, Math.min(1.0, slowNodeRatio));
      } catch (NumberFormatException e) {
        // エラーが発生した場合はデフォルト値を使用
      }
    }

    return slowNodeRatio;
  }

  /** 静的デバッグ出力メソッド（後方互換性のため残存） */
  private static void debugOutput(String message) {
    DebugLogger.debug(message);
  }

  /** 統計情報を取得 */
  public Map<String, Object> getStatistics() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("localContextsCount", localContexts.size());
    stats.put("sharedContextsCount", sharedContexts.size());
    stats.put("isHighPerformanceNode", isHighPerformanceNode);
    stats.put("sharingThreshold", sharingThreshold);
    stats.put("maxContextsPerBlock", maxContextsPerBlock);
    stats.put("contextGenerationDelay", contextGenerationDelay);
    stats.put("sharingEnabled", sharingEnabled);

    // 低速ノード割合の情報を追加
    String slowNodeRatioStr = System.getenv("SLOW_NODE_RATIO");
    double slowNodeRatio = 0.7; // デフォルト値
    if (slowNodeRatioStr != null && !slowNodeRatioStr.isEmpty()) {
      try {
        slowNodeRatio = Double.parseDouble(slowNodeRatioStr);
        slowNodeRatio = Math.max(0.0, Math.min(1.0, slowNodeRatio));
      } catch (NumberFormatException e) {
        // エラーが発生した場合はデフォルト値を使用
      }
    }
    stats.put("slowNodeRatio", slowNodeRatio);
    stats.put("highPerformanceNodeRatio", 1.0 - slowNodeRatio);

    return stats;
  }

  /** 共有機能が有効かを返す */
  public boolean isSharingEnabled() {
    return this.sharingEnabled;
  }

  /** Forerunner低速ノードが共有された未来コンテキストの到着を待機開始 */
  private void startWaitingForSharedContext() {
    Block currentBlock = ownerNode.getBlock();
    if (currentBlock == null) {
      return;
    }

    long nextBlockIndex = currentBlock.getIndex() + 1;

    // 既に待機中または既にコンテキストがある場合はスキップ
    if (waitingForSharedContext.containsKey(nextBlockIndex)
        || sharedContexts.containsKey(nextBlockIndex)
        || localContexts.containsKey(nextBlockIndex)) {
      return;
    }

    // 待機状態を開始
    waitingForSharedContext.put(nextBlockIndex, true);
    waitingStartTime.put(nextBlockIndex, System.currentTimeMillis());

    simblock.util.DebugLogger.diagLog(
        "waiting start node=" + ownerNode.getNodeID() + ", block=" + nextBlockIndex);
  }

  /** 共有された未来コンテキストの待機を終了 */
  private void stopWaitingForSharedContext(long blockIndex) {
    waitingForSharedContext.remove(blockIndex);
    waitingStartTime.remove(blockIndex);

    simblock.util.DebugLogger.diagLog(
        "waiting stop node=" + ownerNode.getNodeID() + ", block=" + blockIndex);
  }

  /** 指定されたブロックで待機中かどうかを確認 */
  public boolean isWaitingForSharedContext(long blockIndex) {
    return waitingForSharedContext.containsKey(blockIndex)
        && waitingForSharedContext.get(blockIndex);
  }

  /** 待機時間を取得（ミリ秒） */
  public long getWaitingTime(long blockIndex) {
    if (!waitingStartTime.containsKey(blockIndex)) {
      return 0L;
    }
    return System.currentTimeMillis() - waitingStartTime.get(blockIndex);
  }
}
