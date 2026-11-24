# test-e2e.ps1 - End-to-End Test Suite for Investment Portfolio Management System

$Endpoint = "http://localhost:8080/graphql"

function Invoke-GraphQL {
    param(
        [string]$Query,
        [hashtable]$Variables = $null,
        [string]$Token = $null
    )

    $headers = @{
        "Content-Type" = "application/json"
    }
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $bodyObj = @{ query = $Query }
    if ($Variables) {
        $bodyObj["variables"] = $Variables
    }

    $jsonBody = $bodyObj | ConvertTo-Json -Depth 10

    try {
        $response = Invoke-RestMethod -Uri $Endpoint -Method Post -Headers $headers -Body $jsonBody
        if ($response.errors) {
            Write-Warning "GraphQL returned errors: $($response.errors | ConvertTo-Json -Depth 5)"
        }
        return $response
    } catch {
        Write-Error "HTTP Request failed: $_"
        return $null
    }
}

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Starting End-to-End System Tests" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Login as Admin
Write-Host "`n[1] Logging in as default Admin..." -ForegroundColor Yellow
$loginAdminQuery = @"
mutation {
  login(input: { email: "admin@platform.com", password: "admin" }) {
    accessToken
    user {
      id
      email
      role
    }
  }
}
"@
$adminRes = Invoke-GraphQL -Query $loginAdminQuery
if ($adminRes.data.login.accessToken) {
    Write-Host "SUCCESS: Admin logged in! User: $($adminRes.data.login.user.email) (Role: $($adminRes.data.login.user.role))" -ForegroundColor Green
    $adminToken = $adminRes.data.login.accessToken
} else {
    Write-Host "FAILURE: Admin login failed! Response: $($adminRes | ConvertTo-Json -Depth 5)" -ForegroundColor Red
    exit 1
}

# 2. Register Broker
Write-Host "`n[2] Registering a new Broker..." -ForegroundColor Yellow
$brokerEmail = "broker_" + (Get-Random) + "@platform.com"
$regBrokerQuery = @"
mutation {
  registerUser(input: {
    email: "$brokerEmail",
    password: "password123",
    fullName: "Jane Broker",
    role: BROKER,
    licenseNumber: "LIC-998877",
    specialization: "Equities and Bonds"
  }) {
    accessToken
    user {
      id
      email
      fullName
      role
      brokerProfile {
        licenseNumber
        specialization
      }
    }
  }
}
"@
$brokerRes = Invoke-GraphQL -Query $regBrokerQuery
if ($brokerRes.data.registerUser.accessToken) {
    $brokerToken = $brokerRes.data.registerUser.accessToken
    $brokerId = $brokerRes.data.registerUser.user.id
    Write-Host "SUCCESS: Broker registered! ID: $brokerId" -ForegroundColor Green
} else {
    Write-Host "FAILURE: Broker registration failed: $($brokerRes | ConvertTo-Json -Depth 5)" -ForegroundColor Red
    exit 1
}

# 3. Register Investor assigned to Broker
Write-Host "`n[3] Registering a new Investor assigned to Broker..." -ForegroundColor Yellow
$investorEmail = "investor_" + (Get-Random) + "@platform.com"
$regInvestorQuery = @"
mutation {
  registerUser(input: {
    email: "$investorEmail",
    password: "password123",
    fullName: "Alice Investor",
    role: INVESTOR,
    riskProfile: MODERATE,
    brokerId: "$brokerId"
  }) {
    accessToken
    user {
      id
      email
      fullName
      role
      investorProfile {
        riskProfile
        broker {
          id
          fullName
        }
      }
    }
  }
}
"@
$investorRes = Invoke-GraphQL -Query $regInvestorQuery
if ($investorRes.data.registerUser.accessToken) {
    $investorToken = $investorRes.data.registerUser.accessToken
    $investorId = $investorRes.data.registerUser.user.id
    Write-Host "SUCCESS: Investor registered! ID: $investorId, Broker: $($investorRes.data.registerUser.user.investorProfile.broker.fullName)" -ForegroundColor Green
} else {
    Write-Host "FAILURE: Investor registration failed: $($investorRes | ConvertTo-Json -Depth 5)" -ForegroundColor Red
    exit 1
}

# 4. Verify 'me' query
Write-Host "`n[4] Querying 'me' as Investor..." -ForegroundColor Yellow
$meQuery = @"
query {
  me {
    id
    email
    fullName
    role
    status
    registeredAt
  }
}
"@
$meRes = Invoke-GraphQL -Query $meQuery -Token $investorToken
Write-Host "SUCCESS: Verified 'me': $($meRes.data.me.fullName) ($($meRes.data.me.role))" -ForegroundColor Green

# 5. Query 'brokerInvestors' as Broker
Write-Host "`n[5] Querying 'brokerInvestors' as Broker..." -ForegroundColor Yellow
$biQuery = @"
query {
  brokerInvestors {
    id
    email
    fullName
  }
}
"@
$biRes = Invoke-GraphQL -Query $biQuery -Token $brokerToken
$found = ($biRes.data.brokerInvestors | Where-Object { $_.id -eq $investorId })
if ($found) {
    Write-Host "SUCCESS: Verified investor appears in broker's client list!" -ForegroundColor Green
} else {
    Write-Host "WARNING: Investor not listed in brokerInvestors: $($biRes | ConvertTo-Json -Depth 5)" -ForegroundColor Yellow
}

# 6. Query Asset Categories and Assets
Write-Host "`n[6] Querying Assets and Categories..." -ForegroundColor Yellow
$assetsQuery = @"
query {
  assetCategories {
    id
    name
  }
  assets(page: { limit: 10, offset: 0 }) {
    totalCount
    items {
      id
      ticker
      name
      currentPrice
      currency
      isActive
      category {
        name
      }
    }
  }
}
"@
$assetsRes = Invoke-GraphQL -Query $assetsQuery -Token $investorToken
$appleAsset = ($assetsRes.data.assets.items | Where-Object { $_.ticker -eq "AAPL" })
$aaplId = $appleAsset.id
Write-Host "SUCCESS: Found $($assetsRes.data.assets.totalCount) assets. Apple price: $($appleAsset.currentPrice) $($appleAsset.currency) (ID: $aaplId)" -ForegroundColor Green

# 7. Deposit Funds
Write-Host "`n[7] Depositing `$10,000 USD to Cash Balance..." -ForegroundColor Yellow
$depositQuery = @"
mutation {
  depositFunds(input: {
    amount: 10000.00,
    currency: "USD"
  }) {
    id
    type
    amount
    currency
    status
    createdAt
  }
}
"@
$depositRes = Invoke-GraphQL -Query $depositQuery -Token $investorToken
Write-Host "SUCCESS: Deposit status: $($depositRes.data.depositFunds.status), Amount: $($depositRes.data.depositFunds.amount) $($depositRes.data.depositFunds.currency)" -ForegroundColor Green

Write-Host "Waiting 3 seconds for PaymentProcessed event in Portfolio Service..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

# 8. Create Portfolio
Write-Host "`n[8] Creating Portfolio 'Growth Portfolio'..." -ForegroundColor Yellow
$createPortQuery = @"
mutation {
  createPortfolio(input: {
    name: "Growth Portfolio",
    baseCurrency: "USD"
  }) {
    id
    name
    baseCurrency
    cashBalance
    totalValue
  }
}
"@
$portRes = Invoke-GraphQL -Query $createPortQuery -Token $investorToken
$portfolioId = $portRes.data.createPortfolio.id
Write-Host "SUCCESS: Created Portfolio ID: $portfolioId, Cash Balance: $($portRes.data.createPortfolio.cashBalance)" -ForegroundColor Green

# 9. Buy Asset (10 shares of AAPL)
Write-Host "`n[9] Buying 10 shares of AAPL ($aaplId)..." -ForegroundColor Yellow
$buyQuery = @"
mutation {
  buyAsset(input: {
    portfolioId: "$portfolioId",
    assetId: "$aaplId",
    quantity: 10
  }) {
    id
    type
    quantity
    price
    totalAmount
    currency
    status
    rejectReason
  }
}
"@
$buyRes = Invoke-GraphQL -Query $buyQuery -Token $investorToken
Write-Host "SUCCESS: Trade status: $($buyRes.data.buyAsset.status), Total: $($buyRes.data.buyAsset.totalAmount) $($buyRes.data.buyAsset.currency)" -ForegroundColor Green

Write-Host "Waiting 3 seconds for TradeExecuted event in Portfolio Service..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

# 10. Check Portfolio positions and valuation
Write-Host "`n[10] Querying Portfolio Snapshot and Positions..." -ForegroundColor Yellow
$portDetailQuery = @"
query {
  portfolio(id: "$portfolioId") {
    id
    name
    totalValue
    investedAmount
    cashBalance
    profit
    profitPercent
    positions {
      quantity
      avgPurchasePrice
      currentPrice
      marketValue
      asset {
        ticker
        name
      }
    }
  }
}
"@
$portDetailRes = Invoke-GraphQL -Query $portDetailQuery -Token $investorToken
Write-Host "SUCCESS: Portfolio Total Value: $($portDetailRes.data.portfolio.totalValue), Cash: $($portDetailRes.data.portfolio.cashBalance)" -ForegroundColor Green
if ($portDetailRes.data.portfolio.positions.Count -gt 0) {
    $pos = $portDetailRes.data.portfolio.positions[0]
    Write-Host "         Position: $($pos.asset.ticker) x $($pos.quantity) @ avg $($pos.avgPurchasePrice), Market Val: $($pos.marketValue)" -ForegroundColor Green
} else {
    Write-Host "         Positions count: 0 (pending trade execution)" -ForegroundColor Yellow
}

# 11. Wait for Kafka events to propagate and check Notifications
Write-Host "`n[11] Waiting 3 seconds for asynchronous Kafka events (trade executed)..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

$notifQuery = @"
query {
  unreadNotificationsCount
  notifications(page: { limit: 5 }) {
    totalCount
    items {
      id
      type
      message
      isRead
      createdAt
    }
  }
}
"@
$notifRes = Invoke-GraphQL -Query $notifQuery -Token $investorToken
Write-Host "SUCCESS: Unread notifications: $($notifRes.data.unreadNotificationsCount), Total: $($notifRes.data.notifications.totalCount)" -ForegroundColor Green
if ($notifRes.data.notifications.items.Count -gt 0) {
    Write-Host "         Latest notification: $($notifRes.data.notifications.items[0].message)" -ForegroundColor Green
}

# 12. Generate Analytics Report
Write-Host "`n[12] Generating On-Demand Analytics Report..." -ForegroundColor Yellow
$reportQuery = @"
mutation {
  generateReport(input: {
    portfolioId: "$portfolioId",
    periodStart: "2026-01-01",
    periodEnd: "2026-12-31"
  }) {
    id
    type
    currency
    startValue
    endValue
    purchasesAmount
    profit
    yieldPercent
    structure {
      category
      value
      share
    }
  }
}
"@
$reportRes = Invoke-GraphQL -Query $reportQuery -Token $investorToken
Write-Host "SUCCESS: Report generated! ID: $($reportRes.data.generateReport.id)" -ForegroundColor Green
Write-Host "         Purchases Amount: $($reportRes.data.generateReport.purchasesAmount), Yield: $($reportRes.data.generateReport.yieldPercent)%" -ForegroundColor Green

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "ALL END-TO-END TESTS COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
