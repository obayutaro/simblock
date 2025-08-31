package simblock.settings;

import java.util.Random;
import simblock.util.DebugLogger;

/**
 * Forerunner の適用比率とシードを管理し、ノードごとの適用判定を提供する。 既定は ratio=0.0 (無効) なので、設定が無ければ全ノード従来通り。
 *
 * <p>指定方法: -Dforerunner.ratio=0.4 （0.0〜1.0） -Dforerunner.seed=42 （任意の long） もしくは環境変数:
 * FORERUNNER_RATIO, FORERUNNER_SEED
 */
public final class ForerunnerConfig {
  public static final double RATIO;
  public static final long SEED;

  // Forerunnerの実際の予測精度データ（2025年測定値）
  /** Perfect prediction（完全予測）の割合 */
  public static final double PERFECT_PREDICTION_RATE = 0.8719; // 87.19%

  /** Imperfect prediction（不完全予測）の割合 */
  public static final double IMPERFECT_PREDICTION_RATE = 0.1196; // 11.96%

  /** Missed prediction（予測失敗）の割合 */
  public static final double MISSED_PREDICTION_RATE = 0.0085; // 0.85%

  /** Perfect predictionの平均スピードアップ */
  public static final double PERFECT_SPEEDUP = 11.33; // 11.33×

  /** Imperfect predictionの平均スピードアップ */
  public static final double IMPERFECT_SPEEDUP = 4.55; // 4.55×

  /** Missed predictionの平均スピードアップ（主にプリフェッチ効果） */
  public static final double MISSED_SPEEDUP = 1.21; // 1.21×

  /** 投機的実行の総適用率 */
  public static final double TOTAL_SPECULATIVE_RATE =
      PERFECT_PREDICTION_RATE + IMPERFECT_PREDICTION_RATE; // 99.15%

  /** 制約集合（CD-Equiv）の満足率 */
  public static final double CONSTRAINT_SATISFACTION_RATE =
      PERFECT_PREDICTION_RATE + IMPERFECT_PREDICTION_RATE; // 99.15%

  static {
    double r = 0.0;
    try {
      String v =
          System.getProperty(
              "forerunner.ratio", System.getenv().getOrDefault("FORERUNNER_RATIO", "0"));
      r = Double.parseDouble(v);
    } catch (Exception ignored) {
      r = 0.0;
    }
    if (r < 0.0) r = 0.0;
    if (r > 1.0) r = 1.0;
    RATIO = r;

    long s = 0L;
    try {
      String v =
          System.getProperty(
              "forerunner.seed", System.getenv().getOrDefault("FORERUNNER_SEED", "0"));
      s = Long.parseLong(v);
    } catch (Exception ignored) {
      s = 0L;
    }
    SEED = s;
  }

  private ForerunnerConfig() {}

  /** グローバルに Forerunner を有効化すべきか（比率>0） */
  public static boolean enabled() {
    return RATIO > 0.0;
  }

  /** ノードIDごとに決定的に Forerunner 適用を判定する。 トータル件数や読み順に依存しない。 */
  public static boolean isForerunnerNode(int nodeId) {
    if (!enabled()) return false;
    // nodeId と SEED から決定的な疑似乱数を生成
    long mixed = SEED ^ (0x9E3779B97F4A7C15L * (long) nodeId);
    Random rnd = new Random(mixed);
    return rnd.nextDouble() < RATIO;
  }

  /**
   * 予測精度に基づくスピードアップ係数を取得
   *
   * @param predictionType 予測タイプ（PERFECT, IMPERFECT, MISSED）
   * @return スピードアップ係数
   */
  public static double getSpeedupFactor(String predictionType) {
    switch (predictionType.toUpperCase()) {
      case "PERFECT":
        return PERFECT_SPEEDUP;
      case "IMPERFECT":
        return IMPERFECT_SPEEDUP;
      case "MISSED":
        return MISSED_SPEEDUP;
      default:
        return 1.0; // デフォルトは通常処理
    }
  }

  /** 予測精度の統計情報を出力 */
  public static void printPredictionStats() {
    DebugLogger.printStats("Forerunner 予測統計",
        String.format("Perfect Prediction: %.2f%% (Speedup: %.2f×)", 
            PERFECT_PREDICTION_RATE * 100, PERFECT_SPEEDUP),
        String.format("Imperfect Prediction: %.2f%% (Speedup: %.2f×)", 
            IMPERFECT_PREDICTION_RATE * 100, IMPERFECT_SPEEDUP),
        String.format("Missed Prediction: %.2f%% (Speedup: %.2f×)", 
            MISSED_PREDICTION_RATE * 100, MISSED_SPEEDUP),
        String.format("Total Speculative Rate: %.2f%%", TOTAL_SPECULATIVE_RATE * 100),
        String.format("Constraint Satisfaction Rate: %.2f%%", CONSTRAINT_SATISFACTION_RATE * 100)
    );
  }
}
