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

  private final Map<Long, FutureContext> fcMap = new HashMap<>();

  public FutureContextManager(Map<String, String> params) {
    this.hitPerfect = ParamUtil.getDouble(params, "fcPerfect", 0.8719);
    this.hitImperfect = ParamUtil.getDouble(params, "fcImperfect", 0.1196);
    this.hitMissed = 1.0 - hitPerfect - hitImperfect;
  }

  /** Bₙ を受理直後に次ブロック用 FC を作成 */
  public void generate(Block current) {
    fcMap.put(current.getIndex(), new FutureContext(current));
  }

  /** Bₙ₊₁ 到着時に FC が的中したか評価し結果を返す */
  public Outcome evaluate(Block next) {
    FutureContext fc = fcMap.remove(next.getParent().getIndex());
    if (fc == null || !fc.matches(next)) return Outcome.NONE;

    double p = rand.nextDouble();
    if (p < hitPerfect) return Outcome.PERFECT;
    else if (p < hitPerfect + hitImperfect) return Outcome.IMPERFECT;
    else return Outcome.MISSED;
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
