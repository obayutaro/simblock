package simblock.task;

import simblock.forerunner.EnhancedFutureContext;
import simblock.forerunner.FutureContextSharingManager;
import simblock.node.Node;
import simblock.util.DebugLogger;

/** 未来コンテキストを他のノードに共有するタスククラス 非対話型ブロードキャスト方式による共有を実装 */
public class FutureContextShareTask implements Task {
  private final Node sender;
  private final Node receiver;
  private final EnhancedFutureContext context;
  private final long delay;

  public FutureContextShareTask(
      Node sender, Node receiver, EnhancedFutureContext context, long delay) {
    this.sender = sender;
    this.receiver = receiver;
    this.context = context;
    this.delay = delay;
  }

  @Override
  public long getInterval() {
    return delay;
  }

  @Override
  public void run() {
    try {
      // 受信ノードに未来コンテキストを送信
      sendFutureContext();
    } catch (Exception e) {
      // エラーハンドリング
      System.err.println("Error sharing future context: " + e.getMessage());
    }
  }

  /** 未来コンテキストを送信 */
  private void sendFutureContext() {
    // 受信ノードがForerunnerNodeの場合のみ送信
    if (receiver instanceof simblock.node.ForerunnerNode) {
      simblock.node.ForerunnerNode forerunnerReceiver = (simblock.node.ForerunnerNode) receiver;

      // 受信ノードのFutureContextSharingManagerを取得
      FutureContextSharingManager receiverManager =
          forerunnerReceiver.getFutureContextSharingManager();

      if (receiverManager != null) {
        // 受信ノードにコンテキストを送信
        receiverManager.receiveSharedContext(context, sender);

        // 未来コンテキスト共有のデバッグ情報
        DebugLogger.nodeDebug(sender.getNodeID(), 
            String.format("未来コンテキスト共有: %s -> %s (ブロック%d, 信頼度%.2f%%)",
                sender.getNodeID(),
                receiver.getNodeID(),
                context.getBlockIndex(),
                context.getConfidence() * 100));
      }
    }
  }

  @Override
  public String toString() {
    return String.format(
        "FutureContextShareTask{sender=%s, receiver=%s, blockIndex=%d, delay=%dms}",
        sender.getNodeID(), receiver.getNodeID(), context.getBlockIndex(), delay);
  }
}
