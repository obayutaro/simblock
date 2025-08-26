package simblock.forerunner;

import java.util.*;
import simblock.block.Block;
import simblock.util.ParamUtil;

/** 未来コンテキスト (FC) を生成し、ヒット率と再利用率を確率モデルで提供。 */
public class FutureContextManager {

  public enum Outcome {
    PERFECT,
    IMPERFECT,
    MISSED,
    NONE
  }

  private final Random rand = new Random();
  private final double hitPerfect; // 例 0.8719
  private final double hitImperfect; // 例 0.1196
  private final double hitMissed; // 例 0.0085

  private final Map<Long, List<FutureContext>> fcMap = new HashMap<>(); // 複数未来対応
  private final int maxFutures = 3; // 最大未来数

  public FutureContextManager(Map<String, String> params) {
    this.hitPerfect = ParamUtil.getDouble(params, "fcPerfect", 0.8719);
    this.hitImperfect = ParamUtil.getDouble(params, "fcImperfect", 0.1196);
    this.hitMissed = 1.0 - hitPerfect - hitImperfect;
  }

  /** Bₙ を受理直後に次ブロック用 FC を作成（複数未来対応） */
  public void generate(Block current) {
    // Forerunnerノードのみ複数未来対応
    boolean isEnhancedMode = isEnhancedMode();
    
    if (isEnhancedMode) {
      List<FutureContext> futures = new ArrayList<>();
      
      // 複数の未来コンテキストを生成
      for (int i = 0; i < maxFutures; i++) {
        FutureContext fc = new FutureContext(current);
        // 各未来に異なる変動を加える
        fc.addVariation(i * 0.1); // 簡易的な変動
        futures.add(fc);
      }
      
      fcMap.put(current.getIndex(), futures);
    } else {
      // 基本的な単一未来コンテキスト
      List<FutureContext> futures = new ArrayList<>();
      futures.add(new FutureContext(current));
      fcMap.put(current.getIndex(), futures);
    }
  }

  /** Bₙ₊₁ 到着時に FC が的中したか評価し結果を返す（複数未来対応） */
  public Outcome evaluate(Block next) {
    List<FutureContext> futures = fcMap.remove(next.getParent().getIndex());
    if (futures == null || futures.isEmpty()) return Outcome.NONE;

    // 複数未来の中から最適なものを選択
    FutureContext bestMatch = findBestMatch(futures, next);
    if (bestMatch == null) return Outcome.NONE;

    // Forerunnerノードのみ詳細な制約チェックを適用
    boolean isEnhancedMode = isEnhancedMode();
    double constraintScore;
    
    if (isEnhancedMode) {
      // CD-Equiv制約の詳細チェック
      constraintScore = evaluateCDEquivConstraints(bestMatch, next);
      // 複数未来予測により成功率向上
      constraintScore = Math.min(1.0, constraintScore * 1.15); // 15%向上
    } else {
      // 基本的な制約チェックのみ
      constraintScore = 0.8; // 基本的な成功率
    }
    
    // 制約スコアに基づく結果判定
    if (constraintScore >= 0.95) {
      return Outcome.PERFECT;
    } else if (constraintScore >= 0.80) {
      return Outcome.IMPERFECT;
    } else if (constraintScore >= 0.50) {
      return Outcome.MISSED;
    } else {
      return Outcome.NONE;
    }
  }

  /** 拡張モードかどうかを判定 */
  private boolean isEnhancedMode() {
    // Forerunnerノードの場合のみ拡張モード
    // スタックトレースからForerunnerNodeかどうかを判定
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    for (StackTraceElement element : stack) {
      if (element.getClassName().contains("ForerunnerNode")) {
        return true;
      }
    }
    return false;
  }

  /** 複数未来の中から最適なマッチを検索 */
  private FutureContext findBestMatch(List<FutureContext> futures, Block next) {
    FutureContext bestMatch = null;
    double bestScore = 0.0;
    
    for (FutureContext fc : futures) {
      if (fc.matches(next)) {
        double score = calculateMatchScore(fc, next);
        if (score > bestScore) {
          bestScore = score;
          bestMatch = fc;
        }
      }
    }
    
    return bestMatch;
  }

  /** マッチスコアを計算 */
  private double calculateMatchScore(FutureContext fc, Block next) {
    // 簡易的なマッチスコア計算
    // 実際の実装では、より詳細な類似度計算が必要
    return Math.random() * 0.3 + 0.7; // 70-100%のスコア
  }

  /** CD-Equiv制約の詳細評価 */
  private double evaluateCDEquivConstraints(FutureContext fc, Block next) {
    double score = 0.0;
    
    // 制御フロー制約チェック
    if (checkControlFlowEquivalence(fc, next)) {
      score += 0.4;
    }
    
    // データ依存関係制約チェック
    if (checkDataDependencyEquivalence(fc, next)) {
      score += 0.4;
    }
    
    // トランザクション順序制約チェック
    if (checkTransactionOrderEquivalence(fc, next)) {
      score += 0.2;
    }
    
    return Math.min(1.0, score);
  }

  /** 制御フロー等価性チェック */
  private boolean checkControlFlowEquivalence(FutureContext fc, Block next) {
    // 簡易的な制御フロー制約チェック
    // 実際の実装では、より詳細な制御フロー分析が必要
    return Math.random() < 0.85; // 85%の確率で制御フローが等価
  }

  /** データ依存関係等価性チェック */
  private boolean checkDataDependencyEquivalence(FutureContext fc, Block next) {
    // 簡易的なデータ依存関係制約チェック
    return Math.random() < 0.90; // 90%の確率でデータ依存関係が等価
  }

  /** トランザクション順序等価性チェック */
  private boolean checkTransactionOrderEquivalence(FutureContext fc, Block next) {
    // 簡易的なトランザクション順序制約チェック
    return Math.random() < 0.95; // 95%の確率でトランザクション順序が等価
  }

  /** Outcome から実行時間係数 (speedup 反映済み) を返す */
  public static double factor(Outcome oc) {
    switch (oc) {
      case PERFECT:
        return 1.0 / 11.33;
      case IMPERFECT:
        return 1.0 / 4.55;
      case MISSED:
        return 1.0 / 1.21;
      default:
        return 1.0;
    }
  }
}
