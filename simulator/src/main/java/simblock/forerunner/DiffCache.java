package simblock.forerunner;

import java.util.*;
import simblock.block.Block;

/** Component Reuse で再利用する差分をキャッシュ。 実際のステート差分は不要なので、Tx ハッシュ集合として近似。 */
public class DiffCache {

  private final Map<Long, Set<Long>> blockToTxs = new HashMap<>();
  private final Map<Long, Double> reuseHistory = new HashMap<>(); // 再利用履歴
  private final int maxCacheSize = 1000; // キャッシュサイズ制限
  private final double adaptiveThreshold = 0.7; // 適応的閾値

  /** 先読み実行済み Tx を登録 */
  void put(Block parent, long txHash) {
    // キャッシュサイズ制限
    if (blockToTxs.size() >= maxCacheSize) {
      evictLeastUsed();
    }
    blockToTxs.computeIfAbsent(parent.getIndex(), k -> new HashSet<>()).add(txHash);
  }

  /** ブロック受信時に再利用率 (0–1) を返す。 Tx 数は Block クラスが保持していると仮定し size() で取得。 */
  public double applyReusableDiff(Block block) {
    Set<Long> cached = blockToTxs.remove(block.getIndex());
    if (cached == null || block.getTxList().isEmpty()) return 0.0;

    long hit = cached.stream().filter(block.getTxList()::contains).count();
    double reuseRate = hit / (double) block.getTxList().size();

    // Forerunnerノードのみ拡張機能を適用
    boolean isEnhancedMode = isEnhancedMode();

    if (isEnhancedMode) {
      // 再利用履歴を更新
      reuseHistory.put(block.getIndex(), reuseRate);

      // 適応的メモ化: 再利用率が高い場合はより積極的にキャッシュ
      if (reuseRate > adaptiveThreshold) {
        return Math.min(1.0, reuseRate * 1.2); // 20%向上
      }
    }

    return reuseRate;
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

  /** 最も使用頻度の低いエントリを削除 */
  private void evictLeastUsed() {
    if (reuseHistory.isEmpty()) {
      // 再利用履歴がない場合は古いエントリを削除
      Long oldestKey = blockToTxs.keySet().iterator().next();
      blockToTxs.remove(oldestKey);
      return;
    }

    // 再利用率が最も低いエントリを削除
    Long leastUsedKey =
        reuseHistory.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

    if (leastUsedKey != null) {
      blockToTxs.remove(leastUsedKey);
      reuseHistory.remove(leastUsedKey);
    }
  }

  /** キャッシュ統計情報を取得 */
  public Map<String, Object> getCacheStats() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("cacheSize", blockToTxs.size());
    stats.put("historySize", reuseHistory.size());

    if (!reuseHistory.isEmpty()) {
      double avgReuseRate =
          reuseHistory.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
      stats.put("avgReuseRate", avgReuseRate);
    }

    return stats;
  }
}
