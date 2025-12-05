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

package simblock.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import simblock.util.DebugLogger;

/** The type Network configuration allows to configure network latency and bandwidth. */
public class NetworkConfiguration {
  /** Regions where nodes can exist. */
  public static final List<String> REGION_LIST =
      new ArrayList<>(
          Arrays.asList(
              "NORTH_AMERICA", "EUROPE", "SOUTH_AMERICA", "ASIA_PACIFIC", "JAPAN", "AUSTRALIA"));

  /**
   * LATENCY[i][j] is average latency from REGION_LIST[i] to REGION_LIST[j] Unit: millisecond, for
   * year 2015
   */
  private static final long[][] LATENCY_2015 = {
    {36, 119, 255, 310, 154, 208},
    {119, 12, 221, 242, 266, 350},
    {255, 221, 137, 347, 256, 269},
    {310, 242, 347, 99, 172, 278},
    {154, 266, 256, 172, 9, 163},
    {208, 350, 269, 278, 163, 22}
  };
  /**
   * LATENCY[i][j] is average latency from REGION_LIST[i] to REGION_LIST[j] Unit: millisecond, for
   * year 2019
   */
  private static final long[][] LATENCY_2019 = {
    {32, 124, 184, 198, 151, 189},
    {124, 11, 227, 237, 252, 294},
    {184, 227, 88, 325, 301, 322},
    {198, 237, 325, 85, 58, 198},
    {151, 252, 301, 58, 12, 126},
    {189, 294, 322, 198, 126, 16}
  };
  /**
   * LATENCY[i][j] is average latency from REGION_LIST[i] to REGION_LIST[j] Unit: millisecond, for
   * year 2024
   */
  private static final long[][] LATENCY_2024 = {
    {34, 120, 152, 219, 146, 184},
    {121, 20, 208, 212, 282, 279},
    {152, 208, 13, 351, 289, 317},
    {219, 206, 352, 109, 93, 188},
    {146, 283, 289, 93, 0, 123},
    {184, 278, 317, 188, 123, 10}
  };

  /**
   * LATENCY[i][j] is average latency from REGION_LIST[i] to REGION_LIST[j] Unit: millisecond, for
   * year 2024 修正版（より現実的な遅延値）
   */
  private static final long[][] LATENCY_2024_REVISED = {
    {15, 120, 152, 219, 146, 184},
    {121, 15, 208, 212, 282, 279},
    {152, 208, 15, 351, 289, 317},
    {219, 206, 352, 15, 93, 188},
    {146, 283, 289, 93, 15, 123},
    {184, 278, 317, 188, 123, 15}
  };

  /** List of latency assigned to each region. (unit: millisecond) */
  public static long[][] LATENCY = LATENCY_2024_REVISED;

  /**
   * List of download bandwidth assigned to each region, and last element is Inter-regional
   * bandwidth. (unit: bit per second) for year 2015
   */
  private static final long[] DOWNLOAD_BANDWIDTH_2015 = {
    25000000, 24000000, 6500000, 10000000, 17500000, 14000000, 6 * 1000000
  };
  /**
   * List of download bandwidth assigned to each region, and last element is Inter-regional
   * bandwidth. (unit: bit per second) for year 2019
   */
  private static final long[] DOWNLOAD_BANDWIDTH_2019 = {
    52000000, 40000000, 18000000, 22800000, 22800000, 29900000, 6 * 1000000
  };
  /** List of download bandwidth assigned to each region. (unit: bit per second) for year 2024 */
  private static final long[] DOWNLOAD_BANDWIDTH_2024 = {
    185000000, 123000000, 147000000, 135000000, 84000000, 154000000
  };

  /**
   * List of download bandwidth assigned to each region. (unit: bit per second) for year 2024 修正版
   */
  private static final long[] DOWNLOAD_BANDWIDTH_2024_REVISED = {
    50000000, 40000000, 30000000, 35000000, 25000000, 45000000, 2 * 1000000 // パラメータ変更前の状態に戻す（2Mbps）
  };

  /**
   * List of download bandwidth assigned to each region, and last element is Inter-regional
   * bandwidth. (unit: bit per second)
   */
  public static long[] DOWNLOAD_BANDWIDTH = DOWNLOAD_BANDWIDTH_2024_REVISED;

  /**
   * List of upload bandwidth assigned to each region. (unit: bit per second), and last element is
   * Inter-regional bandwidth for year 2015
   */
  private static final long[] UPLOAD_BANDWIDTH_2015 = {
    4700000, 8100000, 1800000, 5300000, 3400000, 5200000, 6 * 1000000
  };

  /**
   * List of upload bandwidth assigned to each region. (unit: bit per second), and last element is
   * Inter-regional bandwidth for year 2019
   */
  private static final long[] UPLOAD_BANDWIDTH_2019 = {
    19200000, 20700000, 5800000, 15700000, 10200000, 11300000, 6 * 1000000
  };

  /** List of upload bandwidth assigned to each region. (unit: bit per second) for year 2024 */
  private static final long[] UPLOAD_BANDWIDTH_2024 = {
    36000000, 36000000, 54000000, 49000000, 23000000, 20000000, 6 * 1000000
  };

  /**
   * List of upload bandwidth assigned to each region. (unit: bit per second) for year 2024
   * 現実的Ethereum版
   */
  private static final long[] UPLOAD_BANDWIDTH_2024_REVISED = {
    15000000, 12000000, 10000000, 12000000, 8000000, 15000000, 1 * 1000000 // パラメータ変更前の状態に戻す（1Mbps）
  };

  /**
   * List of download bandwidth assigned to each region. (unit: bit per second) for year 2024
   * 現実的Ethereum版
   */
  private static final long[] DOWNLOAD_BANDWIDTH_2024_REALISTIC = {
    30000000, 25000000, 20000000, 22000000, 15000000, 28000000, 1 * 1000000
  };

  /**
   * List of upload bandwidth assigned to each region. (unit: bit per second) for year 2024
   * 現実的Ethereum版
   */
  private static final long[] UPLOAD_BANDWIDTH_2024_REALISTIC = {
    8000000, 6000000, 5000000, 6000000, 4000000, 8000000, 500000
  };

  /**
   * List of upload bandwidth assigned to each region. (unit: bit per second), and last element is
   * Inter-regional bandwidth.
   */
  public static long[] UPLOAD_BANDWIDTH = UPLOAD_BANDWIDTH_2024_REVISED;

  /** Region distribution Bitcoin 2015. */
  private static final double[] REGION_DISTRIBUTION_BITCOIN_2015 = {
    0.3869, 0.5159, 0.0113,
    0.0574, 0.0119, 0.0166
  };

  /** Region distribution Bitcoin 2019. */
  private static final double[] REGION_DISTRIBUTION_BITCOIN_2019 = {
    0.3316, 0.4998, 0.0090,
    0.1177, 0.0224, 0.0195
  };

  /** Region distribution Litecoin. */
  // TODO year
  private static final double[] REGION_DISTRIBUTION_LITECOIN = {
    0.3661, 0.4791, 0.0149, 0.1022, 0.0238, 0.0139
  };

  /** Region distribution Dogecoin. */
  // TODO year
  private static final double[] REGION_DISTRIBUTION_DOGECOIN = {
    0.3924, 0.4879, 0.0212, 0.0697, 0.0106, 0.0182
  };

  /** 地域分布イーサリアム 2025/06/17 oba */
  // TODO year
  /*private static final double[] REGION_DISTRIBUTION_ETHEREUM_2025 = {
    0.35, 0.30, 0.05, 0.25, 0.03, 0.02
  }*/
  private static final double[] REGION_DISTRIBUTION_ETHEREUM_2025 = {
    0.25, 0.13, 0.01, 0.55, 0.03, 0.03
  };

  /** 地域分布イーサリアム 2025 修正版（より現実的で均衡の取れた分布） */
  private static final double[] REGION_DISTRIBUTION_ETHEREUM_2025_REVISED = {
    0.32, 0.28, 0.08, 0.28, 0.02, 0.02
  };

  /** 地域分布イーサリアム 2025 現実的版（実際のEthereumネットワーク分布） */
  private static final double[] REGION_DISTRIBUTION_ETHEREUM_2025_REALISTIC = {
    0.35, 0.25, 0.15, 0.20, 0.03, 0.02
  };

  /** Region distribution Bitcoin 2024. */
  private static final double[] REGION_DISTRIBUTION_BITCOIN_2024 = {
    0.2944, 0.5718, 0.0162,
    0.0822, 0.0166, 0.0188
  };

  /**
   * The distribution of node's region. Each value means the rate of the number of nodes in the
   * corresponding region to the number of all nodes.
   */
  // public static final double[] REGION_DISTRIBUTION = REGION_DISTRIBUTION_BITCOIN_2019;
  public static double[] REGION_DISTRIBUTION =
      REGION_DISTRIBUTION_ETHEREUM_2025; // 地域分布をイーサリアムに設定 oba

  /** The cumulative distribution of number of outbound links for Bitcoin 2015. */
  private static final double[] DEGREE_DISTRIBUTION_BITCOIN_2015 = {
    0.025, 0.050, 0.075, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.85, 0.90, 0.95, 0.97,
    0.97, 0.98, 0.99, 0.995, 1.0
  };

  /** The cumulative distribution of number of outbound links for Litecoin. */
  // TODO year
  private static final double[] DEGREE_DISTRIBUTION_LITECOIN = {
    0.01, 0.02, 0.04, 0.07, 0.09, 0.14, 0.20, 0.28, 0.39, 0.5, 0.6, 0.69, 0.76, 0.81, 0.85, 0.87,
    0.89, 0.92, 0.93, 1.0
  };

  /** The cumulative distribution of number of outbound links for Dogecoin. */
  private static final double[] DEGREE_DISTRIBUTION_DOGECOIN = {
    0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 1.0, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00,
    1.00, 1.00, 1.00, 1.0
  };

  /** The cumulative distribution of number of outbound links for Ethereum. */
  private static final double[] DEGREE_DISTRIBUTION_ETHEREUM = {
    0.01, 0.02, 0.05, 0.10, 0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.82, 0.87, 0.91, 0.94, 0.96,
    0.97, 0.98, 0.99, 1.0
  };

  /** The cumulative distribution of number of outbound links for Ethereum 修正版（より現実的な分布） */
  private static final double[] DEGREE_DISTRIBUTION_ETHEREUM_REVISED = {
    0.005, 0.015, 0.035, 0.065, 0.105, 0.155, 0.215, 0.285, 0.365, 0.455, 0.555, 0.665, 0.775,
    0.865, 0.925, 0.965, 0.985, 0.995, 0.999, 1.0
  };

  /**
   * The cumulative distribution of number of outbound links for High-Performance Network (25-50
   * connections)
   */
  private static final double[] DEGREE_DISTRIBUTION_HIGH_PERFORMANCE = {
    0.02, 0.04, 0.06, 0.08, 0.12, 0.16, 0.22, 0.28, 0.36, 0.44, 0.52, 0.60, 0.68, 0.76, 0.82, 0.86,
    0.90, 0.93, 0.95, 0.96, 0.97, 0.98, 0.985, 0.99, 0.992, 0.994, 0.995, 0.996, 0.997, 0.998,
    0.9985, 0.999, 0.9992, 0.9994, 0.9995, 0.9996, 0.9997, 0.9998, 0.9999, 0.99992, 0.99994,
    0.99996, 0.99997, 0.99998, 0.99999, 0.999992, 0.999994, 0.999996, 0.999998, 1.0
  };

  /**
   * The cumulative distribution of number of outbound links. Cf. Andrew Miller et al., "Discovering
   * bitcoin's public topology and influential nodes", 2015.
   */
  // 平均接続数を約4にする累積分布（度数0..8）
  private static final double[] DEGREE_DISTRIBUTION_AVG4 = {
    0.02, // 0
    0.07, // 1
    0.17, // 2
    0.35, // 3
    0.65, // 4
    0.83, // 5
    0.93, // 6
    0.98, // 7
    1.00 // 8
  };

  public static double[] DEGREE_DISTRIBUTION = DEGREE_DISTRIBUTION_AVG4; // 平均4接続

  /** ネットワーク設定を動的に調整するメソッド 環境変数による設定変更に対応 */
  public static void initializeNetworkSettings() {
    // 環境変数から設定を読み込み
    String regionDistributionType = System.getenv("REGION_DISTRIBUTION_TYPE");
    String latencyType = System.getenv("LATENCY_TYPE");
    String bandwidthType = System.getenv("BANDWIDTH_TYPE");
    String degreeDistributionType = System.getenv("DEGREE_DISTRIBUTION_TYPE");

    // 地域分布の設定
    if ("BITCOIN_2024".equals(regionDistributionType)) {
      REGION_DISTRIBUTION = REGION_DISTRIBUTION_BITCOIN_2024;
    } else if ("ETHEREUM_2025".equals(regionDistributionType)) {
      REGION_DISTRIBUTION = REGION_DISTRIBUTION_ETHEREUM_2025;
    } else if ("ETHEREUM_2025_REVISED".equals(regionDistributionType)) {
      REGION_DISTRIBUTION = REGION_DISTRIBUTION_ETHEREUM_2025_REVISED;
    } else if ("ETHEREUM_2025_REALISTIC".equals(regionDistributionType)) {
      REGION_DISTRIBUTION = REGION_DISTRIBUTION_ETHEREUM_2025_REALISTIC;
    }

    // 遅延の設定
    if ("2024_REVISED".equals(latencyType)) {
      LATENCY = LATENCY_2024_REVISED;
    } else if ("2024".equals(latencyType)) {
      LATENCY = LATENCY_2024;
    } else if ("2019".equals(latencyType)) {
      LATENCY = LATENCY_2019;
    }

    // 帯域幅の設定
    if ("2024_REVISED".equals(bandwidthType)) {
      DOWNLOAD_BANDWIDTH = DOWNLOAD_BANDWIDTH_2024_REVISED;
      UPLOAD_BANDWIDTH = UPLOAD_BANDWIDTH_2024_REVISED;
    } else if ("2024_REALISTIC".equals(bandwidthType)) {
      DOWNLOAD_BANDWIDTH = DOWNLOAD_BANDWIDTH_2024_REALISTIC;
      UPLOAD_BANDWIDTH = UPLOAD_BANDWIDTH_2024_REALISTIC;
    } else if ("2024".equals(bandwidthType)) {
      DOWNLOAD_BANDWIDTH = DOWNLOAD_BANDWIDTH_2024;
      UPLOAD_BANDWIDTH = UPLOAD_BANDWIDTH_2024;
    }

    // 接続数分布の設定
    if ("HIGH_PERFORMANCE".equals(degreeDistributionType)) {
      DEGREE_DISTRIBUTION = DEGREE_DISTRIBUTION_HIGH_PERFORMANCE;
    } else if ("ETHEREUM_REVISED".equals(degreeDistributionType)) {
      DEGREE_DISTRIBUTION = DEGREE_DISTRIBUTION_ETHEREUM_REVISED;
    } else if ("ETHEREUM".equals(degreeDistributionType)) {
      DEGREE_DISTRIBUTION = DEGREE_DISTRIBUTION_ETHEREUM;
    } else if ("BITCOIN_2015".equals(degreeDistributionType)) {
      DEGREE_DISTRIBUTION = DEGREE_DISTRIBUTION_BITCOIN_2015;
    } else if ("AVG4".equals(degreeDistributionType)) {
      DEGREE_DISTRIBUTION = DEGREE_DISTRIBUTION_AVG4;
    }

    // 設定の検証とログ出力
    validateAndLogSettings();
  }

  /** ネットワーク設定の検証とログ出力 */
  private static void validateAndLogSettings() {
    DebugLogger.printConfig(
        "ネットワーク設定",
        "Region Distribution: " + getRegionDistributionName(),
        "Latency Type: " + getLatencyTypeName(),
        "Bandwidth Type: " + getBandwidthTypeName(),
        "Degree Distribution: " + getDegreeDistributionTypeName());

    // 設定の妥当性チェック
    validateSettings();
  }

  /** 設定の妥当性チェック */
  private static void validateSettings() {
    // 地域分布の合計が1.0になることを確認
    double sum = 0.0;
    for (double d : REGION_DISTRIBUTION) {
      sum += d;
    }
    if (Math.abs(sum - 1.0) > 0.001) {
      System.err.println("Warning: Region distribution sum is " + sum + " (should be 1.0)");
    }

    // 遅延値が適切な範囲にあることを確認
    for (int i = 0; i < LATENCY.length; i++) {
      for (int j = 0; j < LATENCY[i].length; j++) {
        if (LATENCY[i][j] < 0) {
          System.err.println(
              "Warning: Negative latency detected at [" + i + "][" + j + "]: " + LATENCY[i][j]);
        }
      }
    }

    // 帯域幅が適切な範囲にあることを確認
    for (long bandwidth : DOWNLOAD_BANDWIDTH) {
      if (bandwidth <= 0) {
        System.err.println("Warning: Invalid download bandwidth: " + bandwidth);
      }
    }
    for (long bandwidth : UPLOAD_BANDWIDTH) {
      if (bandwidth <= 0) {
        System.err.println("Warning: Invalid upload bandwidth: " + bandwidth);
      }
    }
  }

  /** 現在の設定タイプ名を取得 */
  private static String getRegionDistributionName() {
    if (REGION_DISTRIBUTION == REGION_DISTRIBUTION_ETHEREUM_2025_REVISED)
      return "ETHEREUM_2025_REVISED";
    if (REGION_DISTRIBUTION == REGION_DISTRIBUTION_ETHEREUM_2025) return "ETHEREUM_2025";
    if (REGION_DISTRIBUTION == REGION_DISTRIBUTION_BITCOIN_2024) return "BITCOIN_2024";
    return "UNKNOWN";
  }

  private static String getLatencyTypeName() {
    if (LATENCY == LATENCY_2024_REVISED) return "2024_REVISED";
    if (LATENCY == LATENCY_2024) return "2024";
    if (LATENCY == LATENCY_2019) return "2019";
    return "UNKNOWN";
  }

  private static String getBandwidthTypeName() {
    if (DOWNLOAD_BANDWIDTH == DOWNLOAD_BANDWIDTH_2024_REVISED) return "2024_REVISED";
    if (DOWNLOAD_BANDWIDTH == DOWNLOAD_BANDWIDTH_2024) return "2024";
    return "UNKNOWN";
  }

  private static String getDegreeDistributionTypeName() {
    if (DEGREE_DISTRIBUTION == DEGREE_DISTRIBUTION_HIGH_PERFORMANCE)
      return "HIGH_PERFORMANCE (25-50 connections)";
    if (DEGREE_DISTRIBUTION == DEGREE_DISTRIBUTION_ETHEREUM_REVISED) return "ETHEREUM_REVISED";
    if (DEGREE_DISTRIBUTION == DEGREE_DISTRIBUTION_ETHEREUM) return "ETHEREUM";
    if (DEGREE_DISTRIBUTION == DEGREE_DISTRIBUTION_BITCOIN_2015) return "BITCOIN_2015";
    if (DEGREE_DISTRIBUTION == DEGREE_DISTRIBUTION_AVG4) return "AVG4 (~4 connections)";
    return "UNKNOWN";
  }
}
