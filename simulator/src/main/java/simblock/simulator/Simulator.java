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

package simblock.simulator;

import static simblock.simulator.Timer.getCurrentTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import simblock.block.Block;
import simblock.node.Node;
import simblock.util.DebugLogger;

/**
 * The type Simulator is tasked with maintaining the list of simulated nodes and managing the block
 * interval. It observes and manages the arrival of new blocks at the simulation level.
 */
public class Simulator {

  /** A list of nodes that will be used in a simulation. */
  private static final ArrayList<Node> simulatedNodes = new ArrayList<>();

  /** The target block interval in milliseconds. */
  private static long targetInterval;

  /**
   * Get simulated nodes list.
   *
   * @return the array list
   */
  public static ArrayList<Node> getSimulatedNodes() {
    return simulatedNodes;
  }

  /**
   * Get target block interval.
   *
   * @return the target block interval in milliseconds
   */
  public static long getTargetInterval() {
    return targetInterval;
  }

  /**
   * Sets the target block interval.
   *
   * @param interval - block interval in milliseconds
   */
  public static void setTargetInterval(long interval) {
    targetInterval = interval;
  }

  /**
   * Add node to the list of simulated nodes.
   *
   * @param node the node
   */
  public static void addNode(Node node) {
    simulatedNodes.add(node);
  }

  /**
   * Remove node from the list of simulated nodes.
   *
   * @param node the node
   */
  @SuppressWarnings("unused")
  public static void removeNode(Node node) {
    simulatedNodes.remove(node);
  }

  /**
   * Add node to the list of simulated nodes and immediately try to add the new node as a neighbor
   * to all simulated nodes.
   *
   * @param node the node
   */
  @SuppressWarnings("unused")
  public static void addNodeWithConnection(Node node) {
    node.joinNetwork();
    addNode(node);
    for (Node existingNode : simulatedNodes) {
      existingNode.addNeighbor(node);
    }
  }

  /** A list of observed {@link Block} instances. */
  private static final ArrayList<Block> observedBlocks = new ArrayList<>();

  /**
   * A list of observed block propagation times. The map key represents the id of the node that has
   * seen the block, the value represents the difference between the current time and the block
   * minting time, effectively recording the absolute time it took for a node to witness the block.
   */
  private static final ArrayList<LinkedHashMap<Integer, Long>> observedPropagations =
      new ArrayList<>();

  /**
   * A map to track the last arrival time of each block at each node. Used to calculate hop-by-hop
   * delays.
   */
  private static final HashMap<Integer, HashMap<Integer, Long>> blockLastArrivalTime =
      new HashMap<>();

  /**
   * Handle the arrival of a new block. For every observed block, propagation information is
   * updated, and for a new block propagation information is created.
   *
   * @param block the block
   * @param node the node
   */
  public static void arriveBlock(Block block, Node node) {
    // If block is already seen by any node
    if (observedBlocks.contains(block)) {
      // Get the propagation information for the current block
      LinkedHashMap<Integer, Long> propagation =
          observedPropagations.get(observedBlocks.indexOf(block));
      // Update information for the new block
      long delta = getCurrentTime() - block.getTime();
      if (delta < 0) {
        delta = 0;
      }
      propagation.put(node.getNodeID(), delta);

      // ブロック伝播統計の更新
      updatePropagationStatistics(block, delta, node);
    } else {
      // If the block has not been seen by any node and there is no memory allocated
      // TODO move magic number to constant
      if (observedBlocks.size() > 10) {
        // After the observed blocks limit is reached, log and remove old blocks by FIFO
        // principle
        Block oldBlock = observedBlocks.get(0);
        printPropagation(oldBlock, observedPropagations.get(0));
        observedBlocks.remove(0);
        observedPropagations.remove(0);
        // ホップ遅延記録もクリーンアップ
        blockLastArrivalTime.remove(oldBlock.getId());
      }
      // If the block has not been seen by any node and there is additional memory
      LinkedHashMap<Integer, Long> propagation = new LinkedHashMap<>();
      long delta = getCurrentTime() - block.getTime();
      if (delta < 0) {
        delta = 0;
      }
      propagation.put(node.getNodeID(), delta);
      // Record the block as seen
      observedBlocks.add(block);
      // Record the propagation time
      observedPropagations.add(propagation);

      // 新しいブロックの伝播統計を初期化
      initializePropagationStatistics(block, delta, node);
    }
  }

  /** ブロック伝播統計の初期化 */
  private static void initializePropagationStatistics(Block block, long delta, Node node) {
    // 初回ホップ遅延の記録
    long hopDelay = calculateHopDelay(block, node);

    // 統計情報の初期化（実際の実装ではより詳細な統計を記録）
    DebugLogger.blockDebug(
        String.valueOf(block.getId()),
        String.format(
            "伝播開始: ノード%d, 遅延%dms, ホップ遅延%dms, 高さ%d",
            node.getNodeID(), delta, hopDelay, block.getHeight()));
  }

  /** ブロック伝播統計の更新 */
  private static void updatePropagationStatistics(Block block, long delta, Node node) {
    // ホップ間遅延の計算
    long hopDelay = calculateHopDelay(block, node);

    // 統計情報の更新（ホップ間遅延も記録）
    if (delta > 0) {
      DebugLogger.blockDebug(
          String.valueOf(block.getId()),
          String.format("伝播完了: ノード%d, 総遅延%dms, ホップ遅延%dms", node.getNodeID(), delta, hopDelay));
    }
  }

  /** ホップ間遅延を計算 */
  private static long calculateHopDelay(Block block, Node node) {
    int blockId = block.getId();
    int nodeId = node.getNodeID();
    long currentTime = getCurrentTime();

    // ブロックのマップを取得または作成
    HashMap<Integer, Long> nodeArrivalTimes =
        blockLastArrivalTime.computeIfAbsent(blockId, k -> new HashMap<>());

    // 前回の到着時刻を取得
    Long lastArrivalTime = nodeArrivalTimes.get(nodeId);
    long hopDelay;

    if (lastArrivalTime == null) {
      // 初回到着の場合、ブロック生成時刻からの遅延
      hopDelay = currentTime - block.getTime();
      if (hopDelay < 0) hopDelay = 0;
    } else {
      // 2回目以降の場合、前回到着からの差分（実際のホップ間遅延）
      hopDelay = currentTime - lastArrivalTime;
      if (hopDelay < 0) hopDelay = 0;
    }

    // 到着時刻を更新
    nodeArrivalTimes.put(nodeId, currentTime);

    return hopDelay;
  }

  /**
   * Print propagation information about the propagation of the provided block in the format:
   *
   * <p><em>node_ID, propagation_time</em>
   *
   * <p><em>propagation_time</em>: The time from when the block of the block ID is generated to when
   * the node of the <em>node_ID</em> is reached.
   *
   * @param block the block
   * @param propagation the propagation of the provided block as a list of {@link Node} IDs and
   *     propagation times
   */
  public static void printPropagation(Block block, LinkedHashMap<Integer, Long> propagation) {
    // Print block and its height
    // TODO block does not have a toString method, what is printed here
    System.out.println(block + ":" + block.getHeight());
    for (Map.Entry<Integer, Long> timeEntry : propagation.entrySet()) {
      System.out.println(timeEntry.getKey() + "," + timeEntry.getValue());
    }
    System.out.println();
  }

  /**
   * Print propagation information about all blocks, internally relying on {@link
   * Simulator#printPropagation(Block, LinkedHashMap)}.
   */
  public static void printAllPropagation() {
    for (int i = 0; i < observedBlocks.size(); i++) {
      printPropagation(observedBlocks.get(i), observedPropagations.get(i));
    }
  }
}
