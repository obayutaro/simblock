package simblock.forerunner;

import java.util.*;
import simblock.block.Block;
import simblock.block.Transaction;

/** Ethereumの投機的実行高速化手法「未来コンテキスト共有」に対応した 詳細化された未来コンテキストクラス */
public class EnhancedFutureContext {
  private final long blockIndex;
  private final long timestamp;
  private final double confidence;
  private final String generatorNodeId;
  private final boolean isShared;

  // 詳細化された状態管理
  private final Map<String, Object> stateContext;
  private final List<Transaction> transactionOrder;
  private final Map<String, Long> accountBalances;
  private final Map<String, Object> storageValues;
  private final long generationTime;
  private final double constraintScore;
  private final String predictionType;

  /** 新しい詳細化未来コンテキストを生成 */
  public EnhancedFutureContext(Block currentBlock, String generatorNodeId) {
    this.blockIndex = currentBlock.getIndex() + 1;
    this.timestamp = currentBlock.getTime() + 13000; // 13秒後を想定
    this.generatorNodeId = generatorNodeId;
    this.isShared = false;
    this.generationTime = System.currentTimeMillis();

    // 詳細化された状態管理の初期化
    this.stateContext = new HashMap<>();
    this.transactionOrder = new ArrayList<>();
    this.accountBalances = new HashMap<>();
    this.storageValues = new HashMap<>();

    // 制約スコアと予測タイプの初期化
    this.constraintScore = calculateDetailedConstraintScore(currentBlock);
    this.predictionType = determinePredictionType(this.constraintScore);

    // 簡略化された信頼度計算（基本値 + 制約スコア + ランダム要素）
    this.confidence = calculateDetailedConfidence();

    // 初期状態の設定
    initializeDetailedState(currentBlock);
  }

  /** 詳細化された制約スコアの計算 */
  private double calculateDetailedConstraintScore(Block currentBlock) {
    double score = 0.0;

    try {
      // ブロックの複雑性評価
      int txCount = currentBlock.getTxList().size();
      if (txCount <= 5) {
        score += 0.3; // 単純なブロック
      } else if (txCount <= 20) {
        score += 0.2; // 中程度のブロック
      } else {
        score += 0.1; // 複雑なブロック
      }

      // ブロックの深さ評価
      long depth = currentBlock.getIndex();
      if (depth <= 100) {
        score += 0.2; // 初期ブロック
      } else if (depth <= 1000) {
        score += 0.1; // 中期的ブロック
      }

      // 基本スコア
      score += 0.4;

    } catch (Exception e) {
      score = 0.5; // エラー時は中程度
    }

    return Math.min(1.0, score);
  }

  /** 予測タイプの判定 */
  private String determinePredictionType(double constraintScore) {
    if (constraintScore >= 0.8) {
      return "PERFECT";
    } else if (constraintScore >= 0.6) {
      return "IMPERFECT";
    } else {
      return "MISSED";
    }
  }

  /** 詳細化された信頼度計算 */
  private double calculateDetailedConfidence() {
    // 基本信頼度を下げる（30%に変更）
    double baseConfidence = 0.3;

    // 制約スコアの重みを増加（50%に変更）
    double constraintBonus = this.constraintScore * 0.5;

    // ランダム要素を減少（±5%に変更）
    double randomFactor = (Math.random() - 0.5) * 0.1;

    // 予測タイプによる調整
    double predictionBonus = 0.0;
    if ("Perfect".equals(this.predictionType)) {
      predictionBonus = 0.2; // Perfect予測で+20%
    } else if ("Imperfect".equals(this.predictionType)) {
      predictionBonus = 0.1; // Imperfect予測で+10%
    }

    return Math.max(
        0.1, Math.min(1.0, baseConfidence + constraintBonus + randomFactor + predictionBonus));
  }

  /** 詳細化された状態の初期化 */
  private void initializeDetailedState(Block currentBlock) {
    try {
      // トランザクション順序の設定
      List<Transaction> currentTxs = currentBlock.getTxList();
      if (currentTxs.size() > 0) {
        // 既存のトランザクションを基に順序を予測
        for (int i = 0; i < Math.min(currentTxs.size(), 5); i++) {
          this.transactionOrder.add(currentTxs.get(i));
        }
      }

      // アカウント残高の予測
      predictAccountBalances();

      // ストレージ値の予測
      predictStorageValues();

      // 状態コンテキストの設定
      this.stateContext.put("blockComplexity", currentBlock.getTxList().size());
      this.stateContext.put("predictionQuality", this.predictionType);
      this.stateContext.put("generationTimestamp", this.generationTime);

    } catch (Exception e) {
      // エラー時は基本的な状態のみ設定
      this.stateContext.put("error", "State initialization failed");
    }
  }

  /** アカウント残高の予測 */
  private void predictAccountBalances() {
    // 簡略化されたアカウント残高予測
    for (int i = 0; i < 10; i++) {
      String accountId = "account_" + i;
      long balance = 1000L + (long) (Math.random() * 9000L);
      this.accountBalances.put(accountId, balance);
    }
  }

  /** ストレージ値の予測 */
  private void predictStorageValues() {
    // 簡略化されたストレージ値予測
    for (int i = 0; i < 5; i++) {
      String storageKey = "storage_" + i;
      Object value = "value_" + i;
      this.storageValues.put(storageKey, value);
    }
  }

  // Getters
  public long getBlockIndex() {
    return blockIndex;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public double getConfidence() {
    return confidence;
  }

  public String getGeneratorNodeId() {
    return generatorNodeId;
  }

  public boolean isShared() {
    return isShared;
  }

  public double getConstraintScore() {
    return constraintScore;
  }

  public String getPredictionType() {
    return predictionType;
  }

  public long getGenerationTime() {
    return generationTime;
  }

  public Map<String, Object> getStateContext() {
    return stateContext;
  }

  public List<Transaction> getTransactionOrder() {
    return transactionOrder;
  }

  public Map<String, Long> getAccountBalances() {
    return accountBalances;
  }

  public Map<String, Object> getStorageValues() {
    return storageValues;
  }

  /** 詳細度レベルの計算 */
  public double getDetailLevel() {
    double detailScore = 0.0;
    detailScore += this.transactionOrder.size() * 0.1;
    detailScore += this.accountBalances.size() * 0.05;
    detailScore += this.storageValues.size() * 0.05;
    detailScore += this.stateContext.size() * 0.1;
    return Math.min(1.0, detailScore);
  }

  /** 共有状態の設定 */
  public void setShared(boolean shared) {
    // このフィールドはfinalなので、新しいインスタンスを作成する必要があります
    // 実際の実装では、共有状態を管理する別のメカニズムを使用
  }

  /** 文字列表現 */
  @Override
  public String toString() {
    return String.format(
        "EnhancedFutureContext{blockIndex=%d, confidence=%.3f, type=%s, detail=%.3f}",
        blockIndex, confidence, predictionType, getDetailLevel());
  }
}
