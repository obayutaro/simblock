# Forerunnerプロジェクト 全シナリオ実行スクリプト（PowerShell版）
# 評価シナリオと評価指標ファイルに従って24シナリオを実行

Write-Host "=== Forerunnerプロジェクト 全シナリオ実行開始 ===" -ForegroundColor Green
Write-Host "実行開始時刻: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan

# シナリオ定義（評価シナリオと評価指標ファイルに従う）
$scenarios = @(
    # シナリオA: Forerunner適用率の段階的評価（5シナリオ）
    @{name="scenario_a1"; forerunner=0.1; slow=0.5},  # 10%適用率, 50%低速ノード
    @{name="scenario_a2"; forerunner=0.3; slow=0.5},  # 30%適用率, 50%低速ノード
    @{name="scenario_a3"; forerunner=0.5; slow=0.5},  # 50%適用率, 50%低速ノード
    @{name="scenario_a4"; forerunner=0.7; slow=0.5},  # 70%適用率, 50%低速ノード
    @{name="scenario_a5"; forerunner=0.9; slow=0.5},  # 90%適用率, 50%低速ノード
    
    # シナリオB: ネットワーク構成の影響評価（3シナリオ）
    @{name="scenario_b1"; forerunner=0.7; slow=0.1},  # 70%適用率, 10%低速ノード
    @{name="scenario_b2"; forerunner=0.7; slow=0.5},  # 70%適用率, 50%低速ノード
    @{name="scenario_b3"; forerunner=0.7; slow=0.9},  # 70%適用率, 90%低速ノード
    
    # シナリオC: 統合評価シナリオ（16シナリオ）
    @{name="scenario_c1"; forerunner=0.9; slow=0.1},  # 90%適用率, 10%低速ノード
    @{name="scenario_c2"; forerunner=0.9; slow=0.3},  # 90%適用率, 30%低速ノード
    @{name="scenario_c3"; forerunner=0.9; slow=0.5},  # 90%適用率, 50%低速ノード
    @{name="scenario_c4"; forerunner=0.9; slow=0.7},  # 90%適用率, 70%低速ノード
    @{name="scenario_c5"; forerunner=0.7; slow=0.1},  # 70%適用率, 10%低速ノード
    @{name="scenario_c6"; forerunner=0.7; slow=0.3},  # 70%適用率, 30%低速ノード
    @{name="scenario_c7"; forerunner=0.7; slow=0.5},  # 70%適用率, 50%低速ノード
    @{name="scenario_c8"; forerunner=0.7; slow=0.7},  # 70%適用率, 70%低速ノード
    @{name="scenario_c9"; forerunner=0.5; slow=0.1},  # 50%適用率, 10%低速ノード
    @{name="scenario_c10"; forerunner=0.5; slow=0.3}, # 50%適用率, 30%低速ノード
    @{name="scenario_c11"; forerunner=0.5; slow=0.5}, # 50%適用率, 50%低速ノード
    @{name="scenario_c12"; forerunner=0.5; slow=0.7}, # 50%適用率, 70%低速ノード
    @{name="scenario_c13"; forerunner=0.3; slow=0.1}, # 30%適用率, 10%低速ノード
    @{name="scenario_c14"; forerunner=0.3; slow=0.3}, # 30%適用率, 30%低速ノード
    @{name="scenario_c15"; forerunner=0.3; slow=0.5}, # 30%適用率, 50%低速ノード
    @{name="scenario_c16"; forerunner=0.3; slow=0.7}  # 30%適用率, 70%低速ノード
)

$successCount = 0
$totalCount = $scenarios.Count
$results = @()

Write-Host "`n📊 実行予定シナリオ数: $totalCount" -ForegroundColor Yellow
Write-Host ("=" * 60) -ForegroundColor Gray

for ($i = 0; $i -lt $scenarios.Count; $i++) {
    $scenario = $scenarios[$i]
    $scenarioNumber = $i + 1
    
    Write-Host "`n[$($scenarioNumber.ToString('00'))/$totalCount] === $($scenario.name) を実行中 ===" -ForegroundColor Yellow
    Write-Host "Forerunner適用率: $($scenario.forerunner*100)%" -ForegroundColor Cyan
    Write-Host "低速ノード割合: $($scenario.slow*100)%" -ForegroundColor Cyan
    
    $startTime = Get-Date
    
    try {
        # 環境変数を設定してコマンドを実行
        $env:FORERUNNER_RATIO = $scenario.forerunner
        $env:SLOW_NODE_RATIO = $scenario.slow
        $env:BROADCAST_SCOPE = "NEIGHBORS_ONLY"
        
        $result = & .\gradlew :simulator:run --args="100 1000 10000" 2>&1
        
        $endTime = Get-Date
        $executionTime = ($endTime - $startTime).TotalSeconds
        
        # 結果をログファイルに保存
        $logFilename = "$($scenario.name)_results.log"
        $result | Out-File -FilePath $logFilename -Encoding UTF8
        
        Write-Host "✅ $($scenario.name) 完了 (実行時間: $executionTime 秒)" -ForegroundColor Green
        Write-Host "  ログファイル: $logFilename" -ForegroundColor Cyan
        $successCount++
        
        # 結果を記録
        $results += @{
            name = $scenario.name
            forerunner_ratio = $scenario.forerunner
            slow_node_ratio = $scenario.slow
            success = $true
            log_file = $logFilename
            execution_time = $executionTime
            timestamp = $endTime.ToString("yyyy-MM-dd HH:mm:ss")
        }
        
    } catch {
        Write-Host "❌ $($scenario.name) エラー: $($_.Exception.Message)" -ForegroundColor Red
        
        # 結果を記録
        $results += @{
            name = $scenario.name
            forerunner_ratio = $scenario.forerunner
            slow_node_ratio = $scenario.slow
            success = $false
            log_file = $null
            execution_time = 0
            timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
            error = $_.Exception.Message
        }
    }
    
    # シナリオ間の待機時間
    if ($i -lt ($scenarios.Count - 1)) {
        Write-Host "  次のシナリオまで5秒待機..." -ForegroundColor Gray
        Start-Sleep -Seconds 5
    }
}

# 実行結果の保存
$executionSummary = @{
    execution_info = @{
        start_time = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        end_time = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        total_scenarios = $totalCount
        success_count = $successCount
        failure_count = ($totalCount - $successCount)
        success_rate = [math]::Round($successCount/$totalCount*100, 1)
        environment = "Windows PowerShell"
    }
    scenarios = $results
}

$executionSummary | ConvertTo-Json -Depth 10 | Out-File -FilePath "scenario_execution_results.json" -Encoding UTF8

Write-Host "`n" + ("=" * 60) -ForegroundColor Gray
Write-Host "=== 実行完了 ===" -ForegroundColor Green
Write-Host "実行終了時刻: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan
Write-Host "成功: $successCount/$totalCount シナリオ" -ForegroundColor Cyan
Write-Host "成功率: $([math]::Round($successCount/$totalCount*100, 1))%" -ForegroundColor Cyan
Write-Host "結果ファイル: scenario_execution_results.json" -ForegroundColor Cyan

# 成功したシナリオの一覧
if ($successCount -gt 0) {
    Write-Host "`n✅ 成功したシナリオ:" -ForegroundColor Green
    foreach ($result in $results) {
        if ($result.success) {
            Write-Host "  - $($result.name)" -ForegroundColor Green
        }
    }
}

# 失敗したシナリオの一覧
if ($successCount -lt $totalCount) {
    Write-Host "`n❌ 失敗したシナリオ:" -ForegroundColor Red
    foreach ($result in $results) {
        if (-not $result.success) {
            Write-Host "  - $($result.name)" -ForegroundColor Red
        }
    }
}
