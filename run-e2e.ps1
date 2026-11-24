# run-e2e.ps1 - Automated lifecycle runner and end-to-end test suite

New-Item -ItemType Directory -Force -Path "logs" | Out-Null

$processes = @()

try {
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "Launching All 8 Microservices Locally..." -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan

    $services = @(
        @{ Name = "auth-service"; Port = 8081 },
        @{ Name = "asset-service"; Port = 8082 },
        @{ Name = "portfolio-service"; Port = 8083 },
        @{ Name = "trading-service"; Port = 8084 },
        @{ Name = "payment-service"; Port = 8085 },
        @{ Name = "analytics-service"; Port = 8086 },
        @{ Name = "notification-service"; Port = 8087 },
        @{ Name = "getaway"; Port = 8080 }
    )

    foreach ($s in $services) {
        $name = $s.Name
        $jar = "$name/target/$name-0.0.1-SNAPSHOT.jar"
        $outLog = "logs/$name.out.log"
        $errLog = "logs/$name.err.log"

        Write-Host "Starting $name..." -ForegroundColor Green
        $proc = Start-Process java -ArgumentList "-jar", $jar -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
        $processes += $proc
    }

    Write-Host "`nWaiting for all 8 microservices to be listening..." -ForegroundColor Yellow
    $allReady = $true
    foreach ($s in $services) {
        $p = $s.Port
        $name = $s.Name
        $listening = $false
        for ($i = 0; $i -lt 45; $i++) {
            $tcp = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
            if ($tcp) {
                $listening = $true
                break
            }
            Start-Sleep -Seconds 1
        }
        if ($listening) {
            Write-Host "  $name listening on port $p" -ForegroundColor Green
        } else {
            Write-Warning "  $name failed to listen on port $p within 45s!"
            $allReady = $false
        }
    }

    if (-not $allReady) {
        Write-Error "Not all services started successfully. Aborting test."
        exit 1
    }

    Write-Host "`nAll 8 services are running! Giving 3 seconds for gRPC registration settle..." -ForegroundColor Green
    Start-Sleep -Seconds 3

    # Run the test suite
    .\test-e2e.ps1

} finally {
    Write-Host "`nStopping all background microservice processes..." -ForegroundColor Cyan
    foreach ($p in $processes) {
        try {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        } catch {}
    }
    Write-Host "Cleanup completed." -ForegroundColor Green
}
