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

package simblock.task;

import static simblock.simulator.Timer.getCurrentTime;

import simblock.block.Block;
import simblock.node.Node;

/** Task representing the validation of a received block. */
public class BlockApprovalTask implements Task {
  private final Node node;
  private final Block block;
  private final long startTime;

  /**
   * Instantiates a new Block approval task.
   *
   * @param node the node validating the block
   * @param block the block to validate
   */
  public BlockApprovalTask(Node node, Block block) {
    this.node = node;
    this.block = block;
    this.startTime = getCurrentTime();
  }

  @Override
  public long getInterval() {
    return this.node.getProcessingTime();
  }

  @Override
  public void run() {
    this.node.approveBlock(this.block, this.startTime);
  }
}
