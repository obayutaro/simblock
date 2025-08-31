package simblock.task;

import java.util.List;
import simblock.block.Block;
import simblock.block.Transaction;
import simblock.forerunner.FutureContextSharingManager;

/** 未来コンテキスト生成を実行するタスククラス */
public class FutureContextGenerationTask implements Task {
  private final FutureContextSharingManager manager;
  private final Block currentBlock;
  private final List<Transaction> transactions;
  private final long delay;

  public FutureContextGenerationTask(
      FutureContextSharingManager manager,
      Block currentBlock,
      List<Transaction> transactions,
      long delay) {
    this.manager = manager;
    this.currentBlock = currentBlock;
    this.transactions = transactions;
    this.delay = delay;
  }

  @Override
  public long getInterval() {
    return delay;
  }

  @Override
  public void run() {
    try {
      // 未来コンテキストを生成
      manager.generateFutureContext(currentBlock, transactions);
    } catch (Exception e) {
      // エラーハンドリング
      System.err.println("Error generating future context: " + e.getMessage());
    }
  }

  @Override
  public String toString() {
    return String.format(
        "FutureContextGenerationTask{blockIndex=%d, txCount=%d, delay=%dms}",
        currentBlock.getIndex(), transactions != null ? transactions.size() : 0, delay);
  }
}
