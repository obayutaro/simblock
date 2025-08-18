package simblock.node;

import java.util.Collections;
import java.util.Map;
import simblock.block.Block;
import simblock.message.BlockMessage;
import simblock.message.TxMessage;

/** Tx も Block も扱える拡張ノードの共通基底。 Vanilla SimBlock の Node を薄くラップして パラメータマップと汎用ハンドラを追加するだけ。 */
public abstract class AbstractNode extends Node {

  private final Map<String, String> params;

  /** 8 引数 + 任意パラメータを受け取る“完全版”コンストラクタ。 // ★追加 */
  public AbstractNode(
      int nodeID,
      int numConnection,
      int region,
      long miningPower,
      String routingTableName,
      String consensusAlgoName,
      boolean useCBR,
      boolean isChurnNode,
      Map<String, String> params) {

    super(
        nodeID,
        numConnection,
        region,
        miningPower,
        routingTableName,
        consensusAlgoName,
        useCBR,
        isChurnNode); // ★変更: Node(8 引数)
    this.params = params;
  }

  /** 既存 NodeFactory と互換にする“簡易版”。 // ★追加 空のパラメータマップを自動設定する。 */
  public AbstractNode(
      int nodeID,
      int numConnection,
      int region,
      long miningPower,
      String routingTableName,
      String consensusAlgoName,
      boolean useCBR,
      boolean isChurnNode) {

    this(
        nodeID,
        numConnection,
        region,
        miningPower,
        routingTableName,
        consensusAlgoName,
        useCBR,
        isChurnNode,
        Collections.emptyMap()); // ★追加
  }

  /* ---- 追加 API ---- */
  public Map<String, String> getParams() {
    return params;
  }

  /* ---- 拡張イベントハンドラ ---- */
  public void recvTxMessage(TxMessage msg) {
    /* 何もしない: 必要なノードがオーバーライド */
  }

  public void recvBlockMessage(BlockMessage msg) {
    /* デフォルトでは従来の receiveBlock() に委譲 */
    if (msg != null) {
      this.receiveBlock(msg.getBlock());
    }
  }

  /* 既存 Node に無いが必要な場合に備えたラッパ */
  protected Block getChainHead() {
    return this.getBlock();
  } // Node#getBlock が Tip を返す想定
}
