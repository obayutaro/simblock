package simblock.util;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * デバッグログを統一管理するユーティリティクラス
 * 見やすさを優先したデバッグメッセージの出力を提供
 */
public class DebugLogger {
    
    // デバッグモードの制御
    private static boolean DEBUG_ENABLED = true;
    private static boolean FILE_LOGGING_ENABLED = true;
    private static String LOG_FILE = null;
    
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
    
    /**
     * デバッグモードを有効/無効にする
     */
    public static void setDebugEnabled(boolean enabled) {
        DEBUG_ENABLED = enabled;
    }
    
    /**
     * ファイルログを有効/無効にする
     */
    public static void setFileLoggingEnabled(boolean enabled) {
        FILE_LOGGING_ENABLED = enabled;
    }
    
    /**
     * セクション区切りを出力
     */
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
    
    /**
     * 情報メッセージを出力
     */
    public static void info(String message) {
        if (!DEBUG_ENABLED) return;
        
        String timestamp = getTimestamp();
        String formattedMessage = String.format("[INFO] %s %s", timestamp, message);
        
        System.out.println(ANSI_BLUE + formattedMessage + ANSI_RESET);
        
        if (FILE_LOGGING_ENABLED) {
            writeToFile(formattedMessage);
        }
    }
    
    /**
     * 成功メッセージを出力
     */
    public static void success(String message) {
        if (!DEBUG_ENABLED) return;
        
        String timestamp = getTimestamp();
        String formattedMessage = String.format("[SUCCESS] %s %s", timestamp, message);
        
        System.out.println(ANSI_GREEN + formattedMessage + ANSI_RESET);
        
        if (FILE_LOGGING_ENABLED) {
            writeToFile(formattedMessage);
        }
    }
    
    /**
     * 警告メッセージを出力
     */
    public static void warning(String message) {
        if (!DEBUG_ENABLED) return;
        
        String timestamp = getTimestamp();
        String formattedMessage = String.format("[WARNING] %s %s", timestamp, message);
        
        System.out.println(ANSI_YELLOW + formattedMessage + ANSI_RESET);
        
        if (FILE_LOGGING_ENABLED) {
            writeToFile(formattedMessage);
        }
    }
    
    /**
     * エラーメッセージを出力
     */
    public static void error(String message) {
        if (!DEBUG_ENABLED) return;
        
        String timestamp = getTimestamp();
        String formattedMessage = String.format("[ERROR] %s %s", timestamp, message);
        
        System.err.println(ANSI_RED + formattedMessage + ANSI_RESET);
        
        if (FILE_LOGGING_ENABLED) {
            writeToFile(formattedMessage);
        }
    }
    
    /**
     * デバッグメッセージを出力
     */
    public static void debug(String message) {
        if (!DEBUG_ENABLED) return;
        
        String timestamp = getTimestamp();
        String formattedMessage = String.format("[DEBUG] %s %s", timestamp, message);
        
        System.out.println(ANSI_PURPLE + formattedMessage + ANSI_RESET);
        
        if (FILE_LOGGING_ENABLED) {
            writeToFile(formattedMessage);
        }
    }
    
    /**
     * ノード関連のデバッグメッセージを出力
     */
    public static void nodeDebug(int nodeId, String message) {
        if (!DEBUG_ENABLED) return;
        
        String timestamp = getTimestamp();
        String formattedMessage = String.format("[NODE-%d] %s %s", nodeId, timestamp, message);
        
        System.out.println(ANSI_CYAN + formattedMessage + ANSI_RESET);
        
        if (FILE_LOGGING_ENABLED) {
            writeToFile(formattedMessage);
        }
    }
    
    /**
     * ブロック関連のデバッグメッセージを出力
     */
    public static void blockDebug(String blockId, String message) {
        if (!DEBUG_ENABLED) return;
        
        String timestamp = getTimestamp();
        String formattedMessage = String.format("[BLOCK-%s] %s %s", blockId, timestamp, message);
        
        System.out.println(ANSI_GREEN + formattedMessage + ANSI_RESET);
        
        if (FILE_LOGGING_ENABLED) {
            writeToFile(formattedMessage);
        }
    }
    
    /**
     * メモリ使用量をログ出力
     */
    public static void logMemoryUsage(String context) {
        if (!DEBUG_ENABLED) return;
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        String message = String.format("メモリ使用量 (%s): 使用中=%dMB, 空き=%dMB, 最大=%dMB", 
            context, usedMemory / 1024 / 1024, freeMemory / 1024 / 1024, maxMemory / 1024 / 1024);
        
        info(message);
    }
    
    /**
     * パフォーマンス計測を開始
     */
    public static long startTimer(String operation) {
        if (!DEBUG_ENABLED) return System.currentTimeMillis();
        
        info("開始: " + operation);
        return System.currentTimeMillis();
    }
    
    /**
     * パフォーマンス計測を終了
     */
    public static void endTimer(String operation, long startTime) {
        if (!DEBUG_ENABLED) return;
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        success(String.format("完了: %s (実行時間: %dms)", operation, duration));
    }
    
    /**
     * タイムスタンプを取得
     */
    private static String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        return sdf.format(new Date());
    }
    
    /**
     * ログファイル名を取得
     */
    private static String getLogFileName() {
        if (LOG_FILE == null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            LOG_FILE = "simblock_debug_" + sdf.format(new Date()) + ".log";
        }
        return LOG_FILE;
    }
    
    /**
     * ファイルにログを書き込み
     */
    private static void writeToFile(String message) {
        try {
            FileWriter fw = new FileWriter(getLogFileName(), true);
            fw.write(message + "\n");
            fw.close();
        } catch (IOException e) {
            // ファイル出力エラーは無視
        }
    }
    
    /**
     * 設定情報を出力
     */
    public static void printConfig(String title, String... configs) {
        if (!DEBUG_ENABLED) return;
        
        section(title);
        for (String config : configs) {
            info(config);
        }
    }
    
    /**
     * 統計情報を出力
     */
    public static void printStats(String title, String... stats) {
        if (!DEBUG_ENABLED) return;
        
        section(title);
        for (String stat : stats) {
            success(stat);
        }
    }
}
