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

import static simblock.settings.NetworkConfiguration.DEGREE_DISTRIBUTION;
import static simblock.settings.NetworkConfiguration.DOWNLOAD_BANDWIDTH;
import static simblock.settings.NetworkConfiguration.LATENCY;
import static simblock.settings.NetworkConfiguration.REGION_DISTRIBUTION;
import static simblock.settings.NetworkConfiguration.REGION_LIST;
import static simblock.settings.NetworkConfiguration.UPLOAD_BANDWIDTH;
import static simblock.simulator.Main.STATIC_JSON_FILE;
import static simblock.simulator.Main.random;

import java.util.List;
import simblock.settings.NetworkConfiguration;

/**
 * The type Network represents a network split in regions, each node belonging to a region with an
 * upload bandwidth and a download bandwidth. Node degrees follow a predefined degree distribution.
 */
// TODO how is this degree distribution calculated and what does the double
// array mean
public class Network {

  /**
   * Gets latency according with 20% variance pallet distribution.
   *
   * @param from the from latency
   * @param to the to latency
   * @return the calculated latency
   */
  public static final long getLatency(int from, int to) {
    long mean = LATENCY[from][to];

    // より現実的な遅延計算
    if (mean <= 0) {
      mean = 15; // 最小遅延を15msに設定（より現実的）
    }

    // 地域間の距離に基づく遅延調整
    long adjustedMean = adjustLatencyByRegion(from, to, mean);

    // 20%の分散を持つ現実的な遅延分布
    double variance = 0.2 * adjustedMean;
    double minDelay = Math.max(1.0, adjustedMean - variance);
    double maxDelay = adjustedMean + variance;

    // 正規分布に近い遅延値を生成
    double randomValue = random.nextDouble();
    long calculatedDelay;

    if (randomValue < 0.68) {
      // 68%の確率で平均値付近
      calculatedDelay = Math.round(adjustedMean + (randomValue - 0.5) * variance);
    } else if (randomValue < 0.95) {
      // 27%の確率でやや離れた値
      calculatedDelay = Math.round(adjustedMean + (randomValue - 0.5) * variance * 1.5);
    } else {
      // 5%の確率で極端な値
      calculatedDelay = Math.round(adjustedMean + (randomValue - 0.5) * variance * 2.0);
    }

    // 遅延値の範囲を制限
    long finalDelay = Math.max(1L, Math.min(calculatedDelay, (long) (adjustedMean * 3)));

    return finalDelay;
  }

  /** 地域間の距離に基づく遅延調整 */
  private static long adjustLatencyByRegion(int from, int to, long baseLatency) {
    if (from == to) {
      // 同一地域内は基本遅延
      return baseLatency;
    }

    // 地域間の距離に基づく遅延増加
    int regionDistance = Math.abs(from - to);

    if (regionDistance == 1) {
      // 隣接地域
      return baseLatency + 5;
    } else if (regionDistance == 2) {
      // 中距離地域
      return baseLatency + 15;
    } else {
      // 遠距離地域
      return baseLatency + 30;
    }
  }

  /**
   * Gets the minimum between the <em>from</em> upload bandwidth and <em>to</em> download bandwidth.
   *
   * @param from the from index in the {@link NetworkConfiguration#UPLOAD_BANDWIDTH} array.
   * @param to the to index in the {@link NetworkConfiguration#UPLOAD_BANDWIDTH} array.
   * @return the bandwidth
   */
  public static final long getBandwidth(int from, int to) {
    // パラメータ変更前の実装に戻す: すべての通信で地域固有の帯域幅を使用
    return Math.min(UPLOAD_BANDWIDTH[from], DOWNLOAD_BANDWIDTH[to]);
  }

  /**
   * Gets region list.
   *
   * @return the {@link NetworkConfiguration#REGION_LIST} list.
   */
  public static List<String> getRegionList() {
    return REGION_LIST;
  }

  /**
   * Return the number of nodes in the corresponding region as a portion the number of all nodes.
   *
   * @return an array the distribution
   */
  public static double[] getRegionDistribution() {
    return REGION_DISTRIBUTION;
  }

  /**
   * Get degree distribution double [ ].
   *
   * @return the double [ ]
   */
  // TODO
  public static double[] getDegreeDistribution() {
    return DEGREE_DISTRIBUTION;
  }

  /** Prints the currently active regions to outfile. */
  // TODO
  public static void printRegion() {
    STATIC_JSON_FILE.print("{\"region\":[");

    int id = 0;
    for (; id < REGION_LIST.size() - 1; id++) {
      STATIC_JSON_FILE.print("{");
      STATIC_JSON_FILE.print("\"id\":" + id + ",");
      STATIC_JSON_FILE.print("\"name\":\"" + REGION_LIST.get(id) + "\"");
      STATIC_JSON_FILE.print("},");
    }

    STATIC_JSON_FILE.print("{");
    STATIC_JSON_FILE.print("\"id\":" + id + ",");
    STATIC_JSON_FILE.print("\"name\":\"" + REGION_LIST.get(id) + "\"");
    STATIC_JSON_FILE.print("}");
    STATIC_JSON_FILE.print("]}");
    STATIC_JSON_FILE.flush();
    STATIC_JSON_FILE.close();
  }
}
