# stop-all.ps1 - Stops all running microservices

$services = @("getaway", "notification-service", "analytics-service", "payment-service", "trading-service", "portfolio-service", "asset-service", "auth-service")

Write-Host "Stopping Trading Platform Services..." -ForegroundColor Cyan

foreach ($name in $services) {
    $pidFile = "logs/$name.pid"
    if (Test-Path $pidFile) {
        $targetPid = Get-Content $pidFile
        if ($targetPid) {
            try {
                Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
                Write-Host "Stopped $name (PID: $targetPid)" -ForegroundColor Yellow
            } catch {}
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "All services stopped." -ForegroundColor Green
