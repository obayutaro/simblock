package simblock.settings;

import java.util.Random;

/**
 * Forerunner の適用比率とシードを管理し、ノードごとの適用判定を提供する。 既定は ratio=0.0 (無効) なので、設定が無ければ全ノード従来通り。
 *
 * <p>指定方法: -Dforerunner.ratio=0.4 （0.0〜1.0） -Dforerunner.seed=42 （任意の long） もしくは環境変数:
 * FORERUNNER_RATIO, FORERUNNER_SEED
 */
public final class ForerunnerConfig {
  public static final double RATIO;
  public static final long SEED;

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
}
