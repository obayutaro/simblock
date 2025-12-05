#!/bin/bash
# 最大メモリ設定スクリプト
# システムメモリ15GB中、利用可能10GBを考慮して12GBヒープを設定

echo "=== 最大メモリ設定 ==="
echo "システムメモリ: 15GB"
echo "利用可能メモリ: 10GB"
echo "設定ヒープサイズ: 12GB"

# 最大メモリ設定
export JAVA_OPTS="-Xmx12g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+UseZGC"

# その他の最適化設定
export BROADCAST_SCOPE=NEIGHBORS_ONLY
export FORERUNNER_SHARE_ONLY_ON_TX=true
export FORERUNNER_FAST_SHARE=true

echo "JAVA_OPTS設定完了: $JAVA_OPTS"
echo "=== 設定完了 ==="