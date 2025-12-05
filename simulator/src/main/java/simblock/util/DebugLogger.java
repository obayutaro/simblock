package simblock.util;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** デバッグログを統一管理するユーティリティクラス 見やすさを優先したデバッグメッセージの出力を提供 */
public class DebugLogger {

  // デバッグモードの制御（既定は抑制）
  private static boolean DEBUG_ENABLED = false;
  private static boolean FILE_LOGGING_ENABLED = false;
  private static String LOG_FILE = null;
  // トラフィックログの制御（既定は抑制）
  private static boolean TRAFFIC_LOG_ENABLED = false;
  // 診断ログの制御（既定は抑制）
  private static boolean DIAG_LOG_ENABLED = false;
  // 秒ごとのトラフィック量を記録（秒単位のタイムスタンプ -> バイト数）
  // 送信と受信を分けて記録
  private static final Map<Long, Long> trafficPerSecondSend = new HashMap<>();
  private static final Map<Long, Long> trafficPerSecondRecv = new HashMap<>();

  // 色付きログのためのANSIエスケープコード
  private static final String ANSI_RESET = "\u001B[0m";
  private static final String ANSI_RED = "\u001B[31m";
  private static final String ANSI_GREEN = "\u001B[32m";
  private static final String ANSI_YELLOW = "\u001B[33m";
  private static final String ANSI_BLUE = "\u001B[34m";
  private static final String ANSI_PURPLE = "\u001B[35m";
  private static final String ANSI_CYAN = "\u001B[36m";
  private static final String ANSI_WHITE = "\u001B[37m";
  private static final String ANSI_BOLD = "\u001B[1m";

  // 環境変数による初期設定
  static {
    try {
      String suppress =
          System.getenv().getOrDefault("FORERUNNER_LOG_SUPPRESS", "true").trim().toLowerCase();
      String enable =
          System.getenv().getOrDefault("FORERUNNER_LOG_ENABLE", "false").trim().toLowerCase();
      boolean suppressOn = "1".equals(suppress) || "true".equals(suppress) || "on".equals(suppress);
      boolean enableOn = "1".equals(enable) || "true".equals(enable) || "on".equals(enable);
      // 抑制が優先、次に有効化
      DEBUG_ENABLED = enableOn && !suppressOn;
      // ファイル出力は既定OFF（必要時に明示的に有効化）
      FILE_LOGGING_ENABLED = false;
      // トラフィックログの制御
      String trafficLogEnable =
          System.getenv()
              .getOrDefault("FORERUNNER_TRAFFIC_LOG_ENABLE", "false")
              .trim()
              .toLowerCase();
      TRAFFIC_LOG_ENABLED =
          "1".equals(trafficLogEnable)
              || "true".equals(trafficLogEnable)
              || "on".equals(trafficLogEnable);
      // 診断ログの制御
      String diagLogEnable =
          System.getenv().getOrDefault("FORERUNNER_DIAG_LOG_ENABLE", "false").trim().toLowerCase();
      DIAG_LOG_ENABLED =
          "1".equals(diagLogEnable) || "true".equals(diagLogEnable) || "on".equals(diagLogEnable);
    } catch (Exception ignored) {
      DEBUG_ENABLED = false;
      FILE_LOGGING_ENABLED = false;
      TRAFFIC_LOG_ENABLED = false;
      DIAG_LOG_ENABLED = false;
    }
  }

  /** デバッグモードを有効/無効にする */
  public static void setDebugEnabled(boolean enabled) {
    DEBUG_ENABLED = enabled;
  }

  /** ファイルログを有効/無効にする */
  public static void setFileLoggingEnabled(boolean enabled) {
    FILE_LOGGING_ENABLED = enabled;
  }

  /** セクション区切りを出力 */
  public static void section(String title) {
    if (!DEBUG_ENABLED) return;

    String separator = "=".repeat(60);
    String message = String.format("\n%s\n%s\n%s", separator, title, separator);

    System.out.println(ANSI_BOLD + ANSI_CYAN + message + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      if (title.contains("開始")) {
        writeToFile("ログファイル: " + getLogFileName());
      }
      writeToFile("SECTION: " + title);
    }
  }

  /** 情報メッセージを出力 */
  public static void info(String message) {
    if (!DEBUG_ENABLED) return;

    String timestamp = getTimestamp();
    String formattedMessage = String.format("[INFO] %s %s", timestamp, message);

    System.out.println(ANSI_BLUE + formattedMessage + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** 成功メッセージを出力 */
  public static void success(String message) {
    if (!DEBUG_ENABLED) return;

    String timestamp = getTimestamp();
    String formattedMessage = String.format("[SUCCESS] %s %s", timestamp, message);

    System.out.println(ANSI_GREEN + formattedMessage + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** 警告メッセージを出力 */
  public static void warning(String message) {
    if (!DEBUG_ENABLED) return;

    String timestamp = getTimestamp();
    String formattedMessage = String.format("[WARNING] %s %s", timestamp, message);

    System.out.println(ANSI_YELLOW + formattedMessage + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** エラーメッセージを出力 */
  public static void error(String message) {
    if (!DEBUG_ENABLED) return;

    String timestamp = getTimestamp();
    String formattedMessage = String.format("[ERROR] %s %s", timestamp, message);

    System.err.println(ANSI_RED + formattedMessage + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** デバッグメッセージを出力 */
  public static void debug(String message) {
    if (!DEBUG_ENABLED) return;

    String timestamp = getTimestamp();
    String formattedMessage = String.format("[DEBUG] %s %s", timestamp, message);

    System.out.println(ANSI_PURPLE + formattedMessage + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** ノード関連のデバッグメッセージを出力 */
  public static void nodeDebug(int nodeId, String message) {
    if (!DEBUG_ENABLED) return;

    String timestamp = getTimestamp();
    String formattedMessage = String.format("[NODE-%d] %s %s", nodeId, timestamp, message);

    System.out.println(ANSI_CYAN + formattedMessage + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** ブロック関連のデバッグメッセージを出力 */
  public static void blockDebug(String blockId, String message) {
    if (!DEBUG_ENABLED) return;

    String timestamp = getTimestamp();
    String formattedMessage = String.format("[BLOCK-%s] %s %s", blockId, timestamp, message);

    System.out.println(ANSI_GREEN + formattedMessage + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** メモリ使用量をログ出力 */
  public static void logMemoryUsage(String context) {
    if (!DEBUG_ENABLED) return;

    Runtime runtime = Runtime.getRuntime();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;
    long maxMemory = runtime.maxMemory();

    String message =
        String.format(
            "メモリ使用量 (%s): 使用中=%dMB, 空き=%dMB, 最大=%dMB",
            context, usedMemory / 1024 / 1024, freeMemory / 1024 / 1024, maxMemory / 1024 / 1024);

    info(message);
  }

  /** パフォーマンス計測を開始 */
  public static long startTimer(String operation) {
    if (!DEBUG_ENABLED) return System.currentTimeMillis();

    info("開始: " + operation);
    return System.currentTimeMillis();
  }

  /** パフォーマンス計測を終了 */
  public static void endTimer(String operation, long startTime) {
    if (!DEBUG_ENABLED) return;

    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    success(String.format("完了: %s (実行時間: %dms)", operation, duration));
  }

  /** タイムスタンプを取得 */
  private static String getTimestamp() {
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
    return sdf.format(new Date());
  }

  /** ログファイル名を取得 */
  private static String getLogFileName() {
    if (LOG_FILE == null) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
      LOG_FILE = "simblock_debug_" + sdf.format(new Date()) + ".log";
    }
    return LOG_FILE;
  }

  /** ファイルにログを書き込み */
  private static void writeToFile(String message) {
    try {
      FileWriter fw = new FileWriter(getLogFileName(), true);
      fw.write(message + "\n");
      fw.close();
    } catch (IOException e) {
      // ファイル出力エラーは無視
    }
  }

  /** 設定情報を出力 */
  public static void printConfig(String title, String... configs) {
    if (!DEBUG_ENABLED) return;

    section(title);
    for (String config : configs) {
      info(config);
    }
  }

  /** 統計情報を出力 */
  public static void printStats(String title, String... stats) {
    if (!DEBUG_ENABLED) return;

    section(title);
    for (String stat : stats) {
      success(stat);
    }
  }

  /** 処理時間デバッグ情報を出力 */
  public static void processingTimeDebug(String nodeId, String operation, long processingTime) {
    if (!DEBUG_ENABLED) return;

    String timestamp = getTimestamp();
    String formattedMessage =
        String.format(
            "[PROCESSING] %s Node %s %s: %dms", timestamp, nodeId, operation, processingTime);

    System.out.println(ANSI_YELLOW + formattedMessage + ANSI_RESET);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** 単一ノード処理時間ログを出力 */
  public static void singleNodeProcTime(
      int nodeId, int blockId, long delay, String nodeType, boolean isGenerator) {
    if (!DEBUG_ENABLED) return;

    String formattedMessage =
        String.format(
            "SINGLE_NODE_PROC_TIME node=%d block=%d delay=%d nodeType=%s isGenerator=%b",
            nodeId, blockId, delay, nodeType, isGenerator);

    System.out.println(formattedMessage);

    if (FILE_LOGGING_ENABLED) {
      writeToFile(formattedMessage);
    }
  }

  /** トラフィックログを出力（送信） */
  public static void trafficLogSend() {
    if (!TRAFFIC_LOG_ENABLED) return;
    System.out.println("FCSM_TRAFFIC send");
  }

  /** トラフィックログを出力（受信） */
  public static void trafficLogRecv() {
    if (!TRAFFIC_LOG_ENABLED) return;
    System.out.println("FCSM_TRAFFIC recv");
  }

  /**
   * トラフィック量を記録（送信）
   *
   * @param sizeBytes トラフィック量（バイト）
   * @param timestampMs タイムスタンプ（ミリ秒）
   */
  public static void recordTrafficSend(long sizeBytes, long timestampMs) {
    // TRAFFIC_LOG_ENABLEDの状態を再確認（環境変数の変更に対応）
    String trafficLogEnable =
        System.getenv()
            .getOrDefault("FORERUNNER_TRAFFIC_LOG_ENABLE", "false")
            .trim()
            .toLowerCase();
    boolean enabled =
        "1".equals(trafficLogEnable)
            || "true".equals(trafficLogEnable)
            || "on".equals(trafficLogEnable);
    
    if (!enabled) return;
    long second = timestampMs / 1000;
    trafficPerSecondSend.put(second, trafficPerSecondSend.getOrDefault(second, 0L) + sizeBytes);
  }

  /**
   * トラフィック量を記録（受信）
   *
   * @param sizeBytes トラフィック量（バイト）
   * @param timestampMs タイムスタンプ（ミリ秒）
   */
  public static void recordTrafficRecv(long sizeBytes, long timestampMs) {
    // TRAFFIC_LOG_ENABLEDの状態を再確認（環境変数の変更に対応）
    String trafficLogEnable =
        System.getenv()
            .getOrDefault("FORERUNNER_TRAFFIC_LOG_ENABLE", "false")
            .trim()
            .toLowerCase();
    boolean enabled =
        "1".equals(trafficLogEnable)
            || "true".equals(trafficLogEnable)
            || "on".equals(trafficLogEnable);
    
    if (!enabled) return;
    long second = timestampMs / 1000;
    trafficPerSecondRecv.put(second, trafficPerSecondRecv.getOrDefault(second, 0L) + sizeBytes);
  }

  /**
   * 秒間帯域使用量の統計を計算してログ出力（最大、平均、最小）
   * 送信と受信を分けて出力
   * シミュレーション終了時に1回だけ呼び出す
   */
  public static void printTrafficBandwidthStats() {
    // TRAFFIC_LOG_ENABLEDの状態を再確認（環境変数の変更に対応）
    String trafficLogEnable =
        System.getenv()
            .getOrDefault("FORERUNNER_TRAFFIC_LOG_ENABLE", "false")
            .trim()
            .toLowerCase();
    boolean enabled =
        "1".equals(trafficLogEnable)
            || "true".equals(trafficLogEnable)
            || "on".equals(trafficLogEnable);
    
    if (!enabled) return;
    
    // 送信トラフィックの統計を計算
    TrafficStats sendStats = calculateTrafficStats(trafficPerSecondSend);
    // 受信トラフィックの統計を計算
    TrafficStats recvStats = calculateTrafficStats(trafficPerSecondRecv);
    
    // 合計トラフィックの統計を計算（送信と受信の合計）
    Map<Long, Long> trafficPerSecondTotal = new HashMap<>();
    for (Map.Entry<Long, Long> entry : trafficPerSecondSend.entrySet()) {
      trafficPerSecondTotal.put(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<Long, Long> entry : trafficPerSecondRecv.entrySet()) {
      trafficPerSecondTotal.put(entry.getKey(), 
          trafficPerSecondTotal.getOrDefault(entry.getKey(), 0L) + entry.getValue());
    }
    TrafficStats totalStats = calculateTrafficStats(trafficPerSecondTotal);
    
    // データがない場合
    if (sendStats.isEmpty() && recvStats.isEmpty()) {
      System.out.println("FCSM_TRAFFIC_BANDWIDTH_STATS: トラフィックデータなし");
      return;
    }
    
    // ログ出力（送信、受信、合計を分けて出力）
    if (!sendStats.isEmpty()) {
      System.out.println("FCSM_TRAFFIC_BANDWIDTH_STATS_SEND: max=" + sendStats.maxBps 
          + " bps, avg=" + sendStats.avgBps + " bps, min=" + sendStats.minBps + " bps");
    }
    if (!recvStats.isEmpty()) {
      System.out.println("FCSM_TRAFFIC_BANDWIDTH_STATS_RECV: max=" + recvStats.maxBps 
          + " bps, avg=" + recvStats.avgBps + " bps, min=" + recvStats.minBps + " bps");
    }
    if (!totalStats.isEmpty()) {
      System.out.println("FCSM_TRAFFIC_BANDWIDTH_STATS_TOTAL: max=" + totalStats.maxBps 
          + " bps, avg=" + totalStats.avgBps + " bps, min=" + totalStats.minBps + " bps");
    }
  }
  
  /**
   * トラフィック統計を計算する内部クラス
   */
  private static class TrafficStats {
    long maxBps = 0;
    long minBps = Long.MAX_VALUE;
    long avgBps = 0;
    boolean isEmpty = true;
    
    boolean isEmpty() {
      return isEmpty;
    }
  }
  
  /**
   * トラフィック統計を計算
   *
   * @param trafficMap トラフィック量のマップ（秒 -> バイト数）
   * @return トラフィック統計
   */
  private static TrafficStats calculateTrafficStats(Map<Long, Long> trafficMap) {
    TrafficStats stats = new TrafficStats();
    
    if (trafficMap.isEmpty()) {
      return stats;
    }
    
    stats.isEmpty = false;
    long totalBps = 0;
    int count = 0;

    for (Long bytesPerSecond : trafficMap.values()) {
      long bps = bytesPerSecond * 8; // バイトをビットに変換
      if (bps > stats.maxBps) {
        stats.maxBps = bps;
      }
      if (bps < stats.minBps) {
        stats.minBps = bps;
      }
      totalBps += bps;
      count++;
    }

    stats.avgBps = count > 0 ? totalBps / count : 0;
    stats.minBps = stats.minBps == Long.MAX_VALUE ? 0 : stats.minBps;
    
    return stats;
  }

  /** 診断ログを出力 */
  public static void diagLog(String message) {
    if (!DIAG_LOG_ENABLED) return;
    System.out.println("FCSM_DIAG " + message);
  }
}
