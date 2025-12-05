/*
 * Copyright 2019 Distributed Systems Group
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package simblock.node;

import static simblock.settings.SimulationConfiguration.BLOCK_SIZE;
import static simblock.settings.SimulationConfiguration.CBR_FAILURE_BLOCK_SIZE_DISTRIBUTION_FOR_CHURN_NODE;
import static simblock.settings.SimulationConfiguration.CBR_FAILURE_BLOCK_SIZE_DISTRIBUTION_FOR_CONTROL_NODE;
import static simblock.settings.SimulationConfiguration.CBR_FAILURE_RATE_FOR_CHURN_NODE;
import static simblock.settings.SimulationConfiguration.CBR_FAILURE_RATE_FOR_CONTROL_NODE;
import static simblock.settings.SimulationConfiguration.COMPACT_BLOCK_SIZE;
import static simblock.settings.SimulationConfiguration.PROPAGATION_PROCESSING_TIME;
import static simblock.simulator.Main.OUT_JSON_FILE;
import static simblock.simulator.Main.random;
import static simblock.simulator.Network.getBandwidth;
import static simblock.simulator.Simulator.arriveBlock;
import static simblock.simulator.Timer.getCurrentTime;
import static simblock.simulator.Timer.putTask;
import static simblock.simulator.Timer.removeTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import simblock.block.Block;
import simblock.forerunner.EnhancedFutureContext;
import simblock.forerunner.FutureContextSharingManager;
import simblock.node.consensus.AbstractConsensusAlgo;
import simblock.node.routing.AbstractRoutingTable;
import simblock.task.AbstractMessageTask;
import simblock.task.AbstractMintingTask;
import simblock.task.BlockMessageTask;
import simblock.task.CmpctBlockMessageTask;
import simblock.task.GetBlockTxnMessageTask;
import simblock.task.InvMessageTask;
import simblock.task.RecMessageTask;
import simblock.util.DebugLogger;

/** A class representing a node in the network. */
public class Node {
  /** Unique node ID. */
  private final int nodeID;

  /** Region assigned to the node. */
  private final int region;

  /** Mining power assigned to the node. */
  private final long miningPower;

  /** A nodes routing table. */
  private AbstractRoutingTable routingTable;

  /** The consensus algorithm used by the node. */
  private AbstractConsensusAlgo consensusAlgo;

  /** Whether the node uses compact block relay. */
  private boolean useCBR;

  /** The node causes churn. */
  private boolean isChurnNode;

  /** The current block. */
  private Block block;

  /** Orphaned blocks known to node. */
  private final Set<Block> orphans = new HashSet<>();

  /** The current minting task */
  private AbstractMintingTask mintingTask = null;

  /** 未来コンテキスト共有マネージャー */
  private final FutureContextSharingManager fcsm;

  /** In the process of sending blocks. */
  // TODO verify
  private boolean sendingBlock = false;

  // TODO
  private final ArrayList<AbstractMessageTask> messageQue = new ArrayList<>();
  // TODO
  private final Set<Block> downloadingBlocks = new HashSet<>();

  // 廃止予定：以前の固定検証時間は使用しない
  // private final long processingTime = BASE_PROCESSING_TIME;

  /** 共有はTx受信時のみ起動するか（伝播フェーズから分離） */
  private static final boolean SHARE_ONLY_ON_TX =
      Boolean.parseBoolean(System.getenv().getOrDefault("FORERUNNER_SHARE_ONLY_ON_TX", "false"));

  /**
   * Instantiates a new Node.
   *
   * @param nodeID the node id
   * @param numConnection the number of connections a node can have
   * @param region the region
   * @param miningPower the mining power
   * @param routingTableName the routing table name
   * @param consensusAlgoName the consensus algorithm name
   * @param useCBR whether the node uses compact block relay
   * @param isChurnNode whether the node causes churn
   */
  public Node(
      int nodeID,
      int numConnection,
      int region,
      long miningPower,
      String routingTableName,
      String consensusAlgoName,
      boolean useCBR,
      boolean isChurnNode) {
    this.nodeID = nodeID;
    this.region = region;
    this.miningPower = miningPower;
    this.useCBR = useCBR;
    this.isChurnNode = isChurnNode;

    try {
      this.routingTable =
          (AbstractRoutingTable)
              Class.forName(routingTableName).getConstructor(Node.class).newInstance(this);
      this.consensusAlgo =
          (AbstractConsensusAlgo)
              Class.forName(consensusAlgoName).getConstructor(Node.class).newInstance(this);
      this.setNumConnection(numConnection);
    } catch (Exception e) {
      e.printStackTrace();
    }

    // 未来コンテキスト共有マネージャーを初期化
    this.fcsm = new FutureContextSharingManager(this);
  }

  /**
   * Gets the node id.
   *
   * @return the node id
   */
  public int getNodeID() {
    return this.nodeID;
  }

  /**
   * Gets the region ID assigned to a node.
   *
   * @return the region
   */
  public int getRegion() {
    return this.region;
  }

  /**
   * Gets mining power.
   *
   * @return the mining power
   */
  public long getMiningPower() {
    return this.miningPower;
  }

  /**
   * Gets the consensus algorithm.
   *
   * @return the consensus algorithm. See {@link AbstractConsensusAlgo}
   */
  @SuppressWarnings("unused")
  public AbstractConsensusAlgo getConsensusAlgo() {
    return this.consensusAlgo;
  }

  /**
   * Gets routing table.
   *
   * @return the routing table
   */
  public AbstractRoutingTable getRoutingTable() {
    return this.routingTable;
  }

  /**
   * Gets the current block.
   *
   * @return the block
   */
  public Block getBlock() {
    return this.block;
  }

  /** 未来コンテキスト共有マネージャーを取得 */
  public FutureContextSharingManager getFutureContextSharingManager() {
    return this.fcsm;
  }

  /**
   * Gets all orphans known to node.
   *
   * @return the orphans
   */
  public Set<Block> getOrphans() {
    return this.orphans;
  }

  /**
   * Gets the number of connections a node can have.
   *
   * @return the number of connection
   */
  @SuppressWarnings("unused")
  public int getNumConnection() {
    return this.routingTable.getNumConnection();
  }

  /**
   * Sets the number of connections a node can have.
   *
   * @param numConnection the n connection
   */
  public void setNumConnection(int numConnection) {
    this.routingTable.setNumConnection(numConnection);
  }

  /**
   * Gets the nodes neighbors.
   *
   * @return the neighbors
   */
  public ArrayList<Node> getNeighbors() {
    return this.routingTable.getNeighbors();
  }

  /**
   * Adds the node as a neighbor.
   *
   * @param node the node to be added as a neighbor
   * @return the success state of the operation
   */
  @SuppressWarnings("UnusedReturnValue")
  public boolean addNeighbor(Node node) {
    return this.routingTable.addNeighbor(node);
  }

  /**
   * Removes the neighbor form the node.
   *
   * @param node the node to be removed as a neighbor
   * @return the success state of the operation
   */
  @SuppressWarnings("unused")
  public boolean removeNeighbor(Node node) {
    return this.routingTable.removeNeighbor(node);
  }

  /** Initializes the routing table. */
  public void joinNetwork() {
    this.routingTable.initTable();
  }

  /** Mint the genesis block. */
  public void genesisBlock() {
    Block genesis = this.consensusAlgo.genesisBlock();
    this.receiveBlock(genesis);
  }

  /**
   * Adds a new block to the to chain. If node was minting that task instance is abandoned, and the
   * new block arrival is handled.
   *
   * @param newBlock the new block
   */
  public void addToChain(Block newBlock) {
    // If the node has been minting
    if (this.mintingTask != null) {
      removeTask(this.mintingTask);
      this.mintingTask = null;
    }
    // Update the current block
    this.block = newBlock;
    printAddBlock(newBlock);
    // Observe and handle new block arrival
    arriveBlock(newBlock, this);

    // Future Context Sharing: 新しいブロックが追加された後に、次のブロックのためのコンテキスト生成を開始
    startFutureContextGenerationForNextBlock(newBlock);
  }

  /**
   * Future Context Sharing: 次のブロックのためのコンテキスト生成を開始
   *
   * @param currentBlock the current block that was just added
   */
  private void startFutureContextGenerationForNextBlock(Block currentBlock) {
    try {
      if (!this.fcsm.isSharingEnabled()) {
        return; // 共有無効時は生成の起点を作らない
      }
      // 次のブロックのインデックス
      long nextBlockIndex = currentBlock.getIndex() + 1;

      // 既にコンテキストが存在するかチェック
      if (fcsm.getFutureContext(nextBlockIndex) != null) {
        return; // 既に存在する場合は何もしない
      }

      // 高速ノードの場合、次のブロックのためのコンテキスト生成を開始
      if (fcsm.isHighPerformanceNode()) {
        // デバッグログを無効化
        // System.out.println("Node " + this.getNodeID() + " starting future context generation for
        // block " + nextBlockIndex);

        // コンテキスト生成はTx受信/タイマーへ統一（ここでは何もしない）
      }
    } catch (Exception e) {
      // エラーが発生した場合はログを出力して処理を継続
      System.err.println("Error starting future context generation: " + e.getMessage());
    }
  }

  /**
   * Logs the provided block to the logfile.
   *
   * @param newBlock the block to be logged
   */
  private void printAddBlock(Block newBlock) {
    OUT_JSON_FILE.print("{");
    OUT_JSON_FILE.print("\"kind\":\"add-block\",");
    OUT_JSON_FILE.print("\"content\":{");
    OUT_JSON_FILE.print("\"timestamp\":" + getCurrentTime() + ",");
    OUT_JSON_FILE.print("\"node-id\":" + this.getNodeID() + ",");
    OUT_JSON_FILE.print("\"block-id\":" + newBlock.getId());
    OUT_JSON_FILE.print("}");
    OUT_JSON_FILE.print("},");
    OUT_JSON_FILE.flush();
  }

  /**
   * Add orphans.
   *
   * @param orphanBlock the orphan block
   * @param validBlock the valid block
   */
  // TODO check this out later
  public void addOrphans(Block orphanBlock, Block validBlock) {
    if (orphanBlock != validBlock) {
      this.orphans.add(orphanBlock);
      this.orphans.remove(validBlock);
      if (validBlock == null || orphanBlock.getHeight() > validBlock.getHeight()) {
        this.addOrphans(orphanBlock.getParent(), validBlock);
      } else if (orphanBlock.getHeight() == validBlock.getHeight()) {
        this.addOrphans(orphanBlock.getParent(), validBlock.getParent());
      } else {
        this.addOrphans(orphanBlock, validBlock.getParent());
      }
    }
  }

  /** Generates a new minting task and registers it */
  public void minting() {
    AbstractMintingTask task = this.consensusAlgo.minting();
    this.mintingTask = task;
    if (task != null) {
      putTask(task);
    }
  }

  /**
   * Send inv.
   *
   * @param block the block
   */
  public void sendInv(Block block) {
    for (Node to : this.routingTable.getNeighbors()) {
      AbstractMessageTask task = new InvMessageTask(this, to, block);
      putTask(task);
    }
  }

  /**
   * Receive block.
   *
   * @param block the block
   */
  public void receiveBlock(Block block) {
    if (this.consensusAlgo.isReceivedBlockValid(block, this.block)) {
      if (this.block != null && !this.block.isOnSameChainAs(block)) {
        // If orphan mark orphan
        this.addOrphans(this.block, block);
      }

      // Future Context Sharing: 受信したブロックに対して適切なコンテキストを適用（非同期化）
      if (this.fcsm != null && this.fcsm.isSharingEnabled()) {
        simblock.task.ExecutionDelayTask applyTask =
            new simblock.task.ExecutionDelayTask(this, 0L) {
              @Override
              public void run() {
      applyFutureContextToBlock(block);
              }
            };
        putTask(applyTask);
      }

      // 重要: ブロック受信ログ（サンプリング）
      if (this.getNodeID() % 50 == 1) {
        String nodeTypeInfo = "Unknown";
        try {
          if (this.fcsm != null) {
            nodeTypeInfo = this.fcsm.getNodeType().toString();
          }
        } catch (Exception e) {
          // FCSMが初期化されていない場合
        }
        DebugLogger.info(
            String.format(
                "📥 Node %d (%s): ブロック%d受信→処理開始", this.getNodeID(), nodeTypeInfo, block.getId()));
      }

      // 共有フック（伝播フェーズ）はTx受信/タイマーに統一するためここでは起動しない

      // Else add to canonical chain
      this.addToChain(block);
      // Generates a new minting task
      this.minting();
      // Advertise received block
      this.sendInv(block);
    } else if (!this.orphans.contains(block) && !block.isOnSameChainAs(this.block)) {
      // TODO better understand - what if orphan is not valid?
      // If the block was not valid but was an unknown orphan and is not on the same
      // chain as the
      // current block
      this.addOrphans(block, this.block);
      arriveBlock(block, this);
    }
  }

  /**
   * Future Context Sharing: 受信したブロックに対して適切なコンテキストを適用
   *
   * @param block the block to apply context to
   */
  private void applyFutureContextToBlock(Block block) {
    try {
      // ブロックのインデックスに対応する未来コンテキストを取得
      long blockIndex = block.getIndex();
      EnhancedFutureContext context = fcsm.getFutureContext(blockIndex);

      if (context != null && fcsm.canApplyContext(context, block)) {
        // コンテキストが適用可能な場合、適用処理を実行
        DebugLogger.nodeDebug(this.getNodeID(), String.format("未来コンテキスト適用開始: ブロック %d", blockIndex));

        // ここで実際のコンテキスト適用処理を実行
        // 例: トランザクション順序の最適化、状態の事前計算結果の適用など

        // 適用成功のログ
        DebugLogger.nodeDebug(this.getNodeID(), String.format("未来コンテキスト適用成功: ブロック %d", blockIndex));
      } else if (context != null) {
        // コンテキストは存在するが適用できない場合
        DebugLogger.nodeDebug(
            this.getNodeID(),
            String.format(
                "未来コンテキスト適用不可: ブロック %d (信頼度: %.2f%%)", blockIndex, context.getConfidence() * 100));

        // フォールバック処理
        fcsm.fallbackToNormalExecution(blockIndex);
      }
    } catch (Exception e) {
      // エラーが発生した場合は通常の処理にフォールバック
      System.err.println(
          "Error applying future context to block " + block.getId() + ": " + e.getMessage());
      fcsm.fallbackToNormalExecution(block.getIndex());
    }
  }

  /**
   * Receive message.
   *
   * @param message the message
   */
  public void receiveMessage(AbstractMessageTask message) {
    Node from = message.getFrom();

    if (message instanceof InvMessageTask) {
      Block block = ((InvMessageTask) message).getBlock();
      if (!this.orphans.contains(block) && !this.downloadingBlocks.contains(block)) {
        if (this.consensusAlgo.isReceivedBlockValid(block, this.block)) {
          AbstractMessageTask task = new RecMessageTask(this, from, block);
          putTask(task);
          downloadingBlocks.add(block);
        } else if (!block.isOnSameChainAs(this.block)) {
          // get new orphan block
          AbstractMessageTask task = new RecMessageTask(this, from, block);
          putTask(task);
          downloadingBlocks.add(block);
        }
      }
    }

    if (message instanceof RecMessageTask) {
      this.messageQue.add((RecMessageTask) message);
      if (!sendingBlock) {
        this.sendNextBlockMessage();
      }
    }

    if (message instanceof GetBlockTxnMessageTask) {
      this.messageQue.add((GetBlockTxnMessageTask) message);
      if (!sendingBlock) {
        this.sendNextBlockMessage();
      }
    }

    if (message instanceof CmpctBlockMessageTask) {
      Block block = ((CmpctBlockMessageTask) message).getBlock();
      float CBRfailureRate =
          this.isChurnNode ? CBR_FAILURE_RATE_FOR_CHURN_NODE : CBR_FAILURE_RATE_FOR_CONTROL_NODE;
      boolean success = random.nextDouble() > CBRfailureRate ? true : false;
      if (success) {
        downloadingBlocks.remove(block);
        this.receiveBlock(block);
      } else {
        AbstractMessageTask task = new GetBlockTxnMessageTask(this, from, block);
        putTask(task);
      }
    }

    if (message instanceof BlockMessageTask) {
      Block block = ((BlockMessageTask) message).getBlock();
      downloadingBlocks.remove(block);

      // 🔍 デバッグ: ブロックメッセージ受信
      if (this.getNodeID() % 100 == 1) {
        String nodeClass = this.getClass().getSimpleName();
        DebugLogger.debug(
            String.format(
                "🔥 %s Node %d: BlockMessage受信 → recvBlockMessage呼び出し",
                nodeClass, this.getNodeID()));
      }

      // ★重要: ForerunnerNodeのrecvBlockMessageを正しく呼び出す
      if (this instanceof AbstractNode) {
        // BlockMessageTaskからBlockMessageを作成
        simblock.message.BlockMessage blockMsg =
            new simblock.message.BlockMessage(from.getNodeID(), block);

        // 非Forerunnerノードの計算速度係数（高速/低速）を伝播前に適用
        if (!(this instanceof ForerunnerNode)) {
          simblock.forerunner.FutureContextSharingManager.NodeType nodeType =
              this.getFutureContextSharingManager().getNodeType();
          double nodeSpeedFactor = 1.0;
          if (nodeType
              == simblock.forerunner.FutureContextSharingManager.NodeType.NON_FORERUNNER_HIGH) {
            nodeSpeedFactor = simblock.settings.SimulationConfiguration.HIGH_NODE_SPEED_FACTOR;
          } else if (nodeType
              == simblock.forerunner.FutureContextSharingManager.NodeType.NON_FORERUNNER_LOW) {
            nodeSpeedFactor = simblock.settings.SimulationConfiguration.LOW_NODE_SPEED_FACTOR;
          }

          // 非Forerunnerノードの実行フェーズ遅延を Tx 数 × 0.56ms / 速度係数 に是正
          // 実装上Txは生成していないため、常に平均160Txと仮定
          long txCount = simblock.settings.SimulationConfiguration.ASSUMED_TX_PER_BLOCK;
          long perTxUs = simblock.settings.SimulationConfiguration.TX_PROCESSING_TIME; // μs/Tx
          long execUs = (long) Math.max(0L, Math.round(txCount * perTxUs * nodeSpeedFactor));
          long delayMs = Math.max(0L, Math.round(execUs / 1000.0)); // μs→ms（切り上げず、最小1msクランプ撤廃）

          // 📊 単一ノード処理時間ログ（非Forerunner適用時）
          DebugLogger.singleNodeProcTime(
              this.getNodeID(),
              blockMsg.getBlock().getId(),
              delayMs,
              nodeType.toString(),
              blockMsg.getBlock().getMinter() == this);

          simblock.task.ExecutionDelayTask delayTask =
              new simblock.task.ExecutionDelayTask(this, delayMs) {
                @Override
                public void run() {
                  ((AbstractNode) Node.this).recvBlockMessage(blockMsg);
                }
              };
          putTask(delayTask);
        } else {
          ((AbstractNode) this).recvBlockMessage(blockMsg);
        }
      } else {
        // Nodeクラス（非Forerunner、非AbstractNode）の場合
        simblock.forerunner.FutureContextSharingManager.NodeType nodeType =
            this.getFutureContextSharingManager().getNodeType();
        double nodeSpeedFactor = 1.0;
        if (nodeType
            == simblock.forerunner.FutureContextSharingManager.NodeType.NON_FORERUNNER_HIGH) {
          nodeSpeedFactor = simblock.settings.SimulationConfiguration.HIGH_NODE_SPEED_FACTOR;
        } else if (nodeType
            == simblock.forerunner.FutureContextSharingManager.NodeType.NON_FORERUNNER_LOW) {
          nodeSpeedFactor = simblock.settings.SimulationConfiguration.LOW_NODE_SPEED_FACTOR;
        }

        // 非Forerunnerノードの実行フェーズ遅延を Tx 数 × 0.56ms / 速度係数 に是正
        long txCount = simblock.settings.SimulationConfiguration.ASSUMED_TX_PER_BLOCK;
        long perTxUs = simblock.settings.SimulationConfiguration.TX_PROCESSING_TIME; // μs/Tx
        long execUs = (long) Math.max(0L, Math.round(txCount * perTxUs * nodeSpeedFactor));
        long delayMs = Math.max(0L, Math.round(execUs / 1000.0)); // μs→ms

        // 📊 単一ノード処理時間ログ（非Forerunner適用時、Nodeクラス）
        DebugLogger.singleNodeProcTime(
            this.getNodeID(),
            block.getId(),
            delayMs,
            nodeType.toString(),
            block.getMinter() == this);

        simblock.task.ExecutionDelayTask delayTask =
            new simblock.task.ExecutionDelayTask(this, delayMs) {
              @Override
              public void run() {
                Node.this.receiveBlock(block);
              }
            };
        putTask(delayTask);
      }
    }
  }

  /** Gets block size when the node fails compact block relay. */
  private long getFailedBlockSize() {
    if (this.isChurnNode) {
      int index = random.nextInt(CBR_FAILURE_BLOCK_SIZE_DISTRIBUTION_FOR_CHURN_NODE.length);
      return (long) (BLOCK_SIZE * CBR_FAILURE_BLOCK_SIZE_DISTRIBUTION_FOR_CHURN_NODE[index]);
    } else {
      int index = random.nextInt(CBR_FAILURE_BLOCK_SIZE_DISTRIBUTION_FOR_CONTROL_NODE.length);
      return (long) (BLOCK_SIZE * CBR_FAILURE_BLOCK_SIZE_DISTRIBUTION_FOR_CONTROL_NODE[index]);
    }
  }

  /** Send next block message. */
  // send a block to the sender of the next queued recMessage
  public void sendNextBlockMessage() {
    if (this.messageQue.size() > 0) {
      // メッセージキューから最適なメッセージを選択
      AbstractMessageTask selectedMessage = selectOptimalMessage();
      if (selectedMessage == null) {
        return;
      }

      Node to = selectedMessage.getFrom();
      long bandwidth = getBandwidth(this.getRegion(), to.getRegion());

      AbstractMessageTask messageTask;

      if (selectedMessage instanceof RecMessageTask) {
        Block block = ((RecMessageTask) selectedMessage).getBlock();

        // Future Context Sharing: 伝播前共有は SHARE_ONLY_ON_TX=true のとき抑止
        if (!SHARE_ONLY_ON_TX) {
        shareContextBeforePropagation(block, to);
        }

        // If use compact block relay.
        if (selectedMessage.getFrom().useCBR && this.useCBR) {
          // Convert bytes to bits and divide by the bandwidth expressed as bit per
          // millisecond, add propagation processing time.
          long delay = COMPACT_BLOCK_SIZE * 8 / (bandwidth / 1000) + PROPAGATION_PROCESSING_TIME;

          // Send compact block message.
          messageTask = new CmpctBlockMessageTask(this, to, block, delay);
        } else {
          // Else use lagacy protocol.
          // 伝播処理時間は検証時間より短い（チェーン適用・再伝播のみ）
          long effectivePropagationTime = PROPAGATION_PROCESSING_TIME;
          long networkDelay = BLOCK_SIZE * 8 / (bandwidth / 1000);
          long delay = networkDelay + effectivePropagationTime;

          // 簡潔な伝播ログ（サンプリング）
          if (this.getNodeID() % 100 == 1) { // 100ノードに1つだけログ出力
            DebugLogger.info(String.format("Node %d 伝播: %dms", this.getNodeID(), delay));
          }

          messageTask = new BlockMessageTask(this, to, block, delay);
        }
      } else if (selectedMessage instanceof GetBlockTxnMessageTask) {
        // Else from requests missing transactions.
        Block block = ((GetBlockTxnMessageTask) selectedMessage).getBlock();
        // 伝播処理時間のみ使用
        long effectivePropagationTime = PROPAGATION_PROCESSING_TIME;
        long delay = getFailedBlockSize() * 8 / (bandwidth / 1000) + effectivePropagationTime;
        messageTask = new BlockMessageTask(this, to, block, delay);
      } else {
        throw new UnsupportedOperationException();
      }

      sendingBlock = true;
      // 選択されたメッセージをキューから削除
      this.messageQue.remove(selectedMessage);
      putTask(messageTask);
    } else {
      sendingBlock = false;
    }
  }

  /** 最適なメッセージを選択（優先順位制御） */
  private AbstractMessageTask selectOptimalMessage() {
    if (this.messageQue.isEmpty()) {
      return null;
    }

    // 優先順位1: 新しいブロック（インデックスが高い）
    AbstractMessageTask highestPriority = null;
    long highestBlockIndex = -1;

    for (AbstractMessageTask message : this.messageQue) {
      if (message instanceof RecMessageTask) {
        Block block = ((RecMessageTask) message).getBlock();
        if (block.getIndex() > highestBlockIndex) {
          highestBlockIndex = block.getIndex();
          highestPriority = message;
        }
      }
    }

    // 優先順位2: GetBlockTxnMessageTask（トランザクション要求）
    if (highestPriority == null) {
      for (AbstractMessageTask message : this.messageQue) {
        if (message instanceof GetBlockTxnMessageTask) {
          highestPriority = message;
          break;
        }
      }
    }

    return highestPriority != null ? highestPriority : this.messageQue.get(0);
  }

  /** Future Context Sharing: ブロック伝播前にコンテキスト共有を試行 */
  private void shareContextBeforePropagation(Block block, Node targetNode) {
    try {
      if (SHARE_ONLY_ON_TX) {
        return; // 設計どおりTxフェーズのみで共有する
      }
      // ブロックのインデックスに対応する未来コンテキストを取得
      long blockIndex = block.getIndex();
      EnhancedFutureContext context = fcsm.getFutureContext(blockIndex);

      if (context != null && context.getConfidence() >= 0.5) {
        // 信頼度が十分な場合、伝播先ノードにコンテキストを共有
        // デバッグログを無効化
        // System.out.println("Node " + this.getNodeID() + " sharing context before propagation to
        // node " + targetNode.getNodeID() + " for block " + blockIndex);

        // 簡易的なコンテキスト共有（実際の実装ではより詳細な処理が必要）
        // ここではログ出力のみ
      }
    } catch (Exception e) {
      // エラーが発生した場合は処理を継続
      System.err.println("Error sharing context before propagation: " + e.getMessage());
    }
  }
}
