# start-all.ps1 - Starts all 8 microservices locally

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

New-Item -ItemType Directory -Force -Path "logs" | Out-Null

Write-Host "Starting Trading Platform Services..." -ForegroundColor Cyan

foreach ($s in $services) {
    $name = $s.Name
    $jar = "$name/target/$name-0.0.1-SNAPSHOT.jar"
    $outLog = "logs/$name.out.log"
    $errLog = "logs/$name.err.log"

    if (-not (Test-Path $jar)) {
        Write-Warning "JAR not found: $jar. Run 'mvn package -DskipTests' first."
        continue
    }

    Write-Host "Starting $name..." -ForegroundColor Green
    $proc = Start-Process java -ArgumentList "-jar", $jar -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
    Set-Content -Path "logs/$name.pid" -Value $proc.Id
}

Write-Host "Waiting for Gateway on port 8080 to become ready..." -ForegroundColor Yellow
$ready = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    try {
        $res = Invoke-WebRequest -Uri "http://localhost:8080/graphiql" -Method Get -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($res.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {}
}

if ($ready) {
    Write-Host "All services are up! GraphQL endpoint: http://localhost:8080/graphql | GraphiQL: http://localhost:8080/graphiql" -ForegroundColor Green
} else {
    Write-Warning "Gateway took longer than expected to start. Check logs in the 'logs/' folder."
}
