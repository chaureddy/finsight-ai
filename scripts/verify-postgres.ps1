[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://127.0.0.1:8080',
    [string]$PostgresBin,
    [switch]$KeepBackend
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'backend'
$targetRoot = Join-Path $backendRoot 'target'
. (Join-Path $PSScriptRoot 'PostgresUrl.ps1')
$script:backendProcess = $null
$script:keepBackend = $KeepBackend.IsPresent
$script:verificationPassed = $false
$script:aliceEmail = $null
$script:bobEmail = $null
$exitCode = 0
$environmentNames = @(
    'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD', 'COOKIE_SECURE',
    'CORS_ORIGIN', 'PORT', 'MAVEN_USER_HOME', 'PGPASSWORD'
)
$originalEnvironment = @{}
foreach ($name in $environmentNames) {
    $item = Get-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    $originalEnvironment[$name] = if ($null -eq $item) { $null } else { $item.Value }
}

function Set-ProcessEnvironment([string]$Name, [AllowNull()][string]$Value) {
    if ($null -eq $Value) { Remove-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue }
    else { Set-Item -LiteralPath "Env:$Name" -Value $Value }
}

function Read-PlainSecret([string]$Prompt) {
    $secureValue = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Find-PostgresTools([string]$RequestedBin) {
    if ($RequestedBin) {
        $candidate = (Resolve-Path -LiteralPath $RequestedBin).Path
        if ((Test-Path -LiteralPath (Join-Path $candidate 'psql.exe')) -and (Test-Path -LiteralPath (Join-Path $candidate 'pg_isready.exe'))) { return $candidate }
        throw "PostgresBin must contain both psql.exe and pg_isready.exe: $candidate"
    }
    $psqlCommand = Get-Command psql.exe -ErrorAction SilentlyContinue
    $readyCommand = Get-Command pg_isready.exe -ErrorAction SilentlyContinue
    if ($psqlCommand -and $readyCommand) { return Split-Path -Parent $psqlCommand.Source }
    $installRoot = Join-Path $env:ProgramFiles 'PostgreSQL'
    if (Test-Path -LiteralPath $installRoot) {
        $matches = Get-ChildItem -LiteralPath $installRoot -Directory | Sort-Object Name -Descending
        foreach ($versionDirectory in $matches) {
            $candidate = Join-Path $versionDirectory.FullName 'bin'
            if ((Test-Path -LiteralPath (Join-Path $candidate 'psql.exe')) -and (Test-Path -LiteralPath (Join-Path $candidate 'pg_isready.exe'))) { return $candidate }
        }
    }
    throw 'PostgreSQL client tools were not found. Install PostgreSQL 18 or pass -PostgresBin with its bin directory.'
}

function Invoke-Psql([string]$Executable, [string]$HostName, [int]$Port, [string]$UserName, [string]$Database, [string]$Sql) {
    $arguments = @('-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-h', $HostName, '-p', "$Port", '-U', $UserName, '-d', $Database)
    # Capture client diagnostics for classification only: SQL errors may echo a
    # generated CREATE ROLE statement containing the app password.
    $output = $Sql | & $Executable @arguments 2>&1
    $commandExit = $LASTEXITCODE
    if ($commandExit -ne 0) {
        $diagnostic = ($output | ForEach-Object { [string]$_ }) -join "`n"
        if ($diagnostic -match '(?i)password authentication failed|no password supplied|authentication failed') {
            throw "PostgreSQL authentication failed for role '$UserName' at $HostName`:$Port database '$Database'. Check the role name/password and the selected PostgreSQL instance. Password and raw client output were suppressed."
        }
        if ($diagnostic -match '(?i)could not connect|connection refused|timeout expired|no route to host') {
            throw "PostgreSQL connectivity failed for $HostName`:$Port database '$Database'. Check that the service is listening at this DATABASE_URL host and port. Raw client output was suppressed."
        }
        throw "PostgreSQL command failed for role '$UserName' at $HostName`:$Port database '$Database' (psql exit $commandExit). Raw client output was suppressed to protect credentials."
    }
    return (($output | Out-String).Trim())
}

function Invoke-Api([string]$Method, [string]$Path, [Microsoft.PowerShell.Commands.WebRequestSession]$Session, [hashtable]$Headers, [object]$Body, [string]$ContentType) {
    $request = @{
        Uri = "$script:apiBaseUrl$Path"
        Method = $Method
        WebSession = $Session
        UseBasicParsing = $true
        ErrorAction = 'Stop'
    }
    if ($Headers) { $request.Headers = $Headers }
    if ($null -ne $Body) { $request.Body = $Body }
    if ($ContentType) { $request.ContentType = $ContentType }
    try {
        $response = Invoke-WebRequest @request
        return [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Content = [string]$response.Content }
    }
    catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
        $status = [int]$response.StatusCode
        $content = ''
        try {
            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        } catch { }
        return [pscustomobject]@{ StatusCode = $status; Content = $content }
    }
}

function Assert-Status($Response, [int]$Expected, [string]$Description) {
    if ($Response.StatusCode -ne $Expected) { throw "$Description expected HTTP $Expected but received HTTP $($Response.StatusCode). Response content was not printed." }
    Write-Host "[PASS] $Description (HTTP $Expected)" -ForegroundColor Green
}

function Get-JsonArrayCount([string]$Json, [string]$Description) {
    $payload = ConvertFrom-Json -InputObject $Json
    if ($payload -isnot [System.Array]) {
        $actualType = if ($null -eq $payload) { '<null>' } else { $payload.GetType().FullName }
        throw "$Description expected a JSON array but received $actualType. Response content was not printed."
    }
    return $payload.Count
}

function Get-Csrf([Microsoft.PowerShell.Commands.WebRequestSession]$Session) {
    $response = Invoke-Api 'GET' '/api/auth/csrf' $Session $null $null $null
    Assert-Status $response 200 'CSRF bootstrap'
    $token = $response.Content | ConvertFrom-Json
    if (-not $token.headerName -or -not $token.token) { throw 'CSRF response did not contain a header name and token.' }
    $headers = @{}
    $headers[[string]$token.headerName] = [string]$token.token
    return $headers
}

function Assert-TestUserRemoved([string]$Psql, [string]$HostName, [int]$Port, [string]$AdminUser, [string]$Database, [string[]]$Emails) {
    $literals = @($Emails | Where-Object { $_ } | ForEach-Object { "'" + $_.Replace("'", "''") + "'" })
    if ($literals.Count -eq 0) { return }
    $emailClause = $literals -join ', '
    $cleanupSql = "DELETE FROM app_user WHERE email IN ($emailClause);"
    [void](Invoke-Psql $Psql $HostName $Port $AdminUser $Database $cleanupSql)
}

try {
    $script:apiBaseUrl = $ApiBaseUrl.TrimEnd('/')
    $apiUri = [Uri]$script:apiBaseUrl
    if ($apiUri.Scheme -ne 'http' -and $apiUri.Scheme -ne 'https') { throw 'ApiBaseUrl must use HTTP or HTTPS.' }
    $apiPort = if ($apiUri.IsDefaultPort) { if ($apiUri.Scheme -eq 'https') { 443 } else { 80 } } else { $apiUri.Port }

    $databaseUrl = if ($env:DATABASE_URL) { $env:DATABASE_URL } else { $DefaultPostgresJdbcUrl }
    $databaseEndpoint = Get-PostgresEndpoint $databaseUrl
    $pgHost = $databaseEndpoint.Host
    $pgPort = $databaseEndpoint.Port
    $databaseName = $databaseEndpoint.DatabaseName
    $databaseUser = if ($env:DATABASE_USERNAME) { $env:DATABASE_USERNAME } else { 'finsight' }
    if ($databaseUser -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$') { throw 'DATABASE_USERNAME must be a simple PostgreSQL role name.' }

    $postgresServices = @(Get-Service -Name 'postgresql*' -ErrorAction SilentlyContinue)
    if ($pgHost -in @('localhost', '127.0.0.1', '::1')) {
        if ($postgresServices.Count -eq 0) { throw 'No native PostgreSQL Windows service was found. Install PostgreSQL 18 yourself, or start its existing service; this script will not install or start system services.' }
        $runningService = @($postgresServices | Where-Object Status -eq 'Running')
        if ($runningService.Count -eq 0) { throw "PostgreSQL service is installed but not running ($($postgresServices.Name -join ', ')). Start it manually from Windows Services, then rerun." }
        $versionTaggedServices = @($runningService | Where-Object { $_.Name -match '^postgresql.*(?:-|_)(\d+)$' })
        if ($versionTaggedServices.Count -gt 0 -and @($versionTaggedServices | Where-Object { $_.Name -match '(?:-|_)18$' }).Count -eq 0) {
            throw "This project requires PostgreSQL 18 (Docker Compose pins 18-alpine); running native service(s) report $($versionTaggedServices.Name -join ', '). Start PostgreSQL 18 before verification. No credentials were requested."
        }
        Write-Host "[PASS] PostgreSQL service running: $($runningService.Name -join ', ')" -ForegroundColor Green
    }

    $postgresBinPath = Find-PostgresTools $PostgresBin
    $psqlPath = Join-Path $postgresBinPath 'psql.exe'
    $pgIsReadyPath = Join-Path $postgresBinPath 'pg_isready.exe'
    $adminUser = if ($env:PG_ADMIN_USERNAME) { $env:PG_ADMIN_USERNAME } else { 'postgres' }
    $adminPassword = if ($env:PG_ADMIN_PASSWORD) { $env:PG_ADMIN_PASSWORD } else { Read-PlainSecret "PostgreSQL admin password for $adminUser" }
    $databasePassword = if ($env:DATABASE_PASSWORD) { $env:DATABASE_PASSWORD } else { Read-PlainSecret "Password to use for application role $databaseUser" }
    if ([string]::IsNullOrWhiteSpace($adminPassword) -or [string]::IsNullOrWhiteSpace($databasePassword)) { throw 'PostgreSQL passwords cannot be empty.' }

    $priorPgPassword = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $adminPassword
        & $pgIsReadyPath -h $pgHost -p $pgPort -U $adminUser -d postgres 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "PostgreSQL is not accepting connections at $pgHost`:$pgPort. Check the service, DATABASE_URL port, and local listen configuration." }
        Write-Host "[PASS] PostgreSQL accepts connections at $pgHost`:$pgPort" -ForegroundColor Green

        $serverVersion = Invoke-Psql $psqlPath $pgHost $pgPort $adminUser 'postgres' 'SHOW server_version_num;'
        $serverMajor = [math]::Floor(([double]$serverVersion) / 10000)
        if ($serverMajor -ne 18) { throw "This project requires PostgreSQL 18 exactly; found server version $serverVersion. No schema changes were applied." }
        Write-Host "[PASS] PostgreSQL major version 18" -ForegroundColor Green

        $escapedUser = $databaseUser.Replace("'", "''")
        $escapedPassword = $databasePassword.Replace("'", "''")
        $roleSql = "SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', '$escapedUser', '$escapedPassword') WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$escapedUser')`n\gexec"
        [void](Invoke-Psql $psqlPath $pgHost $pgPort $adminUser 'postgres' $roleSql)
        $escapedDatabase = $databaseName.Replace("'", "''")
        $createDatabaseSql = "SELECT format('CREATE DATABASE %I OWNER %I', '$escapedDatabase', '$escapedUser') WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_database WHERE datname = '$escapedDatabase')`n\gexec"
        [void](Invoke-Psql $psqlPath $pgHost $pgPort $adminUser 'postgres' $createDatabaseSql)
        $owner = Invoke-Psql $psqlPath $pgHost $pgPort $adminUser 'postgres' "SELECT pg_get_userbyid(datdba) FROM pg_catalog.pg_database WHERE datname = '$escapedDatabase';"
        if ($owner -ne $databaseUser) { throw "Database '$databaseName' exists but is owned by '$owner', not '$databaseUser'. No ownership was changed." }
        Write-Host "[PASS] Database '$databaseName' exists and is owned by the application role" -ForegroundColor Green
    }
    finally {
        if ($null -eq $priorPgPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue } else { $env:PGPASSWORD = $priorPgPassword }
    }

    $priorPgPassword = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $databasePassword
        $connectionCheck = Invoke-Psql $psqlPath $pgHost $pgPort $databaseUser $databaseName 'SELECT 1;'
        if ($connectionCheck -ne '1') { throw 'Application role connection test did not return 1.' }
        Write-Host '[PASS] Application database credentials can connect' -ForegroundColor Green
    }
    finally {
        if ($null -eq $priorPgPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue } else { $env:PGPASSWORD = $priorPgPassword }
    }

    $oldBackendUrl = $env:DATABASE_URL
    $env:DATABASE_URL = $databaseUrl
    $env:DATABASE_USERNAME = $databaseUser
    $env:DATABASE_PASSWORD = $databasePassword
    $env:COOKIE_SECURE = 'false'
    $env:CORS_ORIGIN = 'http://localhost:5173'
    $env:PORT = "$apiPort"
    $env:MAVEN_USER_HOME = Join-Path $backendRoot '.maven-cache'

    Push-Location $backendRoot
    try {
        & .\mvnw.cmd -B package
        if ($LASTEXITCODE -ne 0) { throw "Maven package/test failed with exit code $LASTEXITCODE." }
    }
    finally { Pop-Location }
    Write-Host '[PASS] Backend compiled and Maven tests passed' -ForegroundColor Green

    $jarPath = Join-Path $targetRoot 'finsight-api-0.1.0.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) { throw "Expected backend jar was not created: $jarPath" }
    $tcp = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $tcp.BeginConnect($apiUri.Host, $apiPort, $null, $null)
        if ($connect.AsyncWaitHandle.WaitOne(300) -and $tcp.Connected) {
            $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $apiPort -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
            $owners = @($listeners | ForEach-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue | Select-Object Id,ProcessName })
            $ownerSummary = if ($owners.Count) { ($owners | ForEach-Object { "$($_.ProcessName) (PID $($_.Id))" }) -join ', ' } else { 'process identity unavailable' }
            throw "Verification port $apiPort is already occupied by $ownerSummary. No process was stopped. Choose an unused port with -ApiBaseUrl 'http://127.0.0.1:8081'."
        }
    }
    finally { $tcp.Close() }

    $logId = [Guid]::NewGuid().ToString('N')
    $stdoutLog = Join-Path $targetRoot "postgres-verification-$logId.out.log"
    $stderrLog = Join-Path $targetRoot "postgres-verification-$logId.err.log"
    $javaPath = (Get-Command java.exe -ErrorAction Stop).Source
    $script:backendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar', "`"$jarPath`"") -WorkingDirectory $backendRoot -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
    Write-Host "Started verification API process $($script:backendProcess.Id). Logs are under backend\target." -ForegroundColor DarkGray

    $healthDeadline = (Get-Date).AddSeconds(90)
    $healthy = $false
    while ((Get-Date) -lt $healthDeadline) {
        if ($script:backendProcess.HasExited) { throw "Backend exited during startup. Review backend\target\$([IO.Path]::GetFileName($stderrLog)) and the matching output log." }
        try {
            $health = Invoke-WebRequest -Uri "$script:apiBaseUrl/api/health" -UseBasicParsing -TimeoutSec 2
            if ($health.StatusCode -eq 200) { $healthy = $true; break }
        } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $healthy) { throw "Backend did not become ready within 90 seconds. Review backend\target\$([IO.Path]::GetFileName($stderrLog))." }
    Write-Host '[PASS] Spring Boot started with PostgreSQL configuration and Flyway completed' -ForegroundColor Green

    $env:PGPASSWORD = $databasePassword
    $migration = Invoke-Psql $psqlPath $pgHost $pgPort $databaseUser $databaseName "SELECT string_agg(version || ':' || success::text, ',' ORDER BY installed_rank) FROM public.flyway_schema_history WHERE version IN ('1','2','3');"
    if ($migration -ne '1:true,2:true,3:true') { throw "Expected successful PostgreSQL Flyway V1, V2, and V3 (reported '$migration')." }
    Write-Host '[PASS] PostgreSQL Flyway history reports V1, V2, and V3 successful' -ForegroundColor Green
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue

    $script:aliceEmail = "verify-$([Guid]::NewGuid().ToString('N'))@example.invalid"
    $script:bobEmail = "verify-$([Guid]::NewGuid().ToString('N'))@example.invalid"
    $alicePassword = [Guid]::NewGuid().ToString('N') + 'Aa9!'
    $bobPassword = [Guid]::NewGuid().ToString('N') + 'Bb8!'
    $aliceSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $bobSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $aliceCsrf = Get-Csrf $aliceSession
    $bobCsrf = Get-Csrf $bobSession

    $aliceRegistration = @{ email = $script:aliceEmail; password = $alicePassword; displayName = 'Verification Alice' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/auth/register' $aliceSession $aliceCsrf $aliceRegistration 'application/json'
    Assert-Status $response 201 'Register verification user Alice'
    $bobRegistration = @{ email = $script:bobEmail; password = $bobPassword; displayName = 'Verification Bob' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/auth/register' $bobSession $bobCsrf $bobRegistration 'application/json'
    Assert-Status $response 201 'Register verification user Bob'

    $response = Invoke-Api 'POST' '/api/auth/login' $aliceSession $aliceCsrf @{ email = $script:aliceEmail; password = $alicePassword } 'application/x-www-form-urlencoded'
    Assert-Status $response 204 'Login Alice'
    $response = Invoke-Api 'POST' '/api/auth/login' $bobSession $bobCsrf @{ email = $script:bobEmail; password = $bobPassword } 'application/x-www-form-urlencoded'
    Assert-Status $response 204 'Login Bob'
    # Spring Security clears the CSRF token after authentication. Keep each
    # WebRequestSession (the cookie jar) and fetch a fresh token for that session.
    $aliceCsrf = Get-Csrf $aliceSession
    $bobCsrf = Get-Csrf $bobSession
    $response = Invoke-Api 'GET' '/api/auth/me' $aliceSession $null $null $null
    Assert-Status $response 200 'Read Alice current-user endpoint'
    if (($response.Content | ConvertFrom-Json).email -ne $script:aliceEmail) { throw 'Current-user endpoint returned the wrong account.' }
    $response = Invoke-Api 'GET' '/api/auth/me' $bobSession $null $null $null
    Assert-Status $response 200 'Read Bob current-user endpoint'
    if (($response.Content | ConvertFrom-Json).email -ne $script:bobEmail) { throw 'Bob session resolved to the wrong account.' }

    $date = Get-Date -Format 'yyyy-MM-dd'
    $incomeBody = @{ description = 'PostgreSQL verification income'; category = 'Verification'; amount = 1200.50; direction = 'INCOME'; occurredOn = $date } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/transactions' $aliceSession $aliceCsrf $incomeBody 'application/json'
    Assert-Status $response 201 'Create income transaction in PostgreSQL'
    $incomeId = [string](($response.Content | ConvertFrom-Json).id)

    $expenseBody = @{ description = 'PostgreSQL verification expense'; category = 'Verification'; amount = 275.25; direction = 'EXPENSE'; occurredOn = $date } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/transactions' $aliceSession $aliceCsrf $expenseBody 'application/json'
    Assert-Status $response 201 'Create expense transaction in PostgreSQL'
    $expenseId = [string](($response.Content | ConvertFrom-Json).id)

    $response = Invoke-Api 'GET' '/api/transactions' $aliceSession $null $null $null
    Assert-Status $response 200 'List Alice transactions'
    # TransactionController.list returns a plain JSON array (not a page or
    # wrapper). Keep ConvertFrom-Json's array as one assigned value: applying
    # .Count directly to the pipeline expression counts the pipeline result,
    # and under Windows PowerShell can incorrectly report 1 for multiple rows.
    $aliceTransactionPayload = ConvertFrom-Json -InputObject $response.Content
    if ($aliceTransactionPayload -isnot [System.Array]) {
        $actualType = if ($null -eq $aliceTransactionPayload) { '<null>' } else { $aliceTransactionPayload.GetType().FullName }
        throw "Alice transaction list expected a JSON array but received $actualType. Response content was not printed."
    }
    $aliceTransactions = @($aliceTransactionPayload)
    $aliceTransactionCount = $aliceTransactions.Count
    $aliceTransactionIds = @($aliceTransactions | ForEach-Object { [string]$_.id })
    $safeAliceIds = @($aliceTransactionIds | Where-Object { $_ -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' })
    Write-Host "[INFO] Alice transaction response: count=$aliceTransactionCount; transaction IDs=$($safeAliceIds -join ', ')" -ForegroundColor DarkGray
    if ($aliceTransactionCount -ne 2 -or $incomeId -notin $aliceTransactionIds -or $expenseId -notin $aliceTransactionIds) {
        throw "Alice transaction list did not contain exactly the two verification transaction IDs (returned count $aliceTransactionCount). Response content was not printed."
    }
    $response = Invoke-Api 'GET' '/api/transactions' $bobSession $null $null $null
    Assert-Status $response 200 'List Bob transactions'
    $bobTransactionPayload = ConvertFrom-Json -InputObject $response.Content
    if ($bobTransactionPayload -isnot [System.Array]) {
        $actualType = if ($null -eq $bobTransactionPayload) { '<null>' } else { $bobTransactionPayload.GetType().FullName }
        throw "Bob transaction list expected a JSON array but received $actualType. Response content was not printed."
    }
    $bobTransactions = @($bobTransactionPayload)
    $bobTransactionIds = @($bobTransactions | ForEach-Object { [string]$_.id })
    $safeBobIds = @($bobTransactionIds | Where-Object { $_ -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' })
    Write-Host "[INFO] Bob transaction response: count=$($bobTransactions.Count); transaction IDs=$($safeBobIds -join ', ')" -ForegroundColor DarkGray
    if ($incomeId -in $bobTransactionIds -or $expenseId -in $bobTransactionIds) { throw 'Bob received one or more Alice transaction IDs.' }
    if ($bobTransactions.Count -ne 0) { throw "Fresh Bob verification account unexpectedly has $($bobTransactions.Count) transaction(s); response content was not printed." }
    Write-Host '[PASS] Cross-user transaction list isolation' -ForegroundColor Green

    $updatedExpense = @{ description = 'Updated PostgreSQL verification expense'; category = 'Verification'; amount = 250.25; direction = 'EXPENSE'; occurredOn = $date } | ConvertTo-Json -Compress
    $response = Invoke-Api 'PUT' "/api/transactions/$expenseId" $aliceSession $aliceCsrf $updatedExpense 'application/json'
    Assert-Status $response 200 'Update Alice transaction'
    if (([decimal](($response.Content | ConvertFrom-Json).amount)) -ne [decimal]250.25) { throw 'Updated transaction amount did not persist.' }

    $response = Invoke-Api 'PUT' "/api/transactions/$incomeId" $bobSession $bobCsrf $incomeBody 'application/json'
    Assert-Status $response 404 'Reject Bob updating Alice transaction'
    $response = Invoke-Api 'DELETE' "/api/transactions/$expenseId" $bobSession $bobCsrf $null $null
    Assert-Status $response 404 'Reject Bob deleting Alice transaction'

    $response = Invoke-Api 'GET' '/api/analytics/cash-flow' $aliceSession $null $null $null
    Assert-Status $response 200 'Read PostgreSQL monthly cash-flow summary'
    $summary = $response.Content | ConvertFrom-Json
    if ([decimal]$summary.income -ne [decimal]1200.50 -or [decimal]$summary.expenses -ne [decimal]250.25 -or [decimal]$summary.netCashFlow -ne [decimal]950.25) { throw 'Monthly cash-flow totals did not match the persisted verification transactions.' }
    Write-Host '[PASS] Monthly cash-flow reflects persisted PostgreSQL records' -ForegroundColor Green

    # PostgreSQL V2 CRUD: response records are read back from the database.
    $today = (Get-Date).Date
    $date = $today.ToString('yyyy-MM-dd')
    $monthStart = (Get-Date -Day 1).ToString('yyyy-MM-dd')
    $monthEnd = (Get-Date -Day 1).AddMonths(1).AddDays(-1).ToString('yyyy-MM-dd')
    $futureGoalDate = $today.AddMonths(12).ToString('yyyy-MM-dd')

    $assetBody = @{ name='Verification cash'; category='Cash'; currentValue=500.40; purchaseValue=500.40; purchaseDate=$date; description='Temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/assets' $aliceSession $aliceCsrf $assetBody 'application/json'; Assert-Status $response 201 'Create PostgreSQL asset'
    $assetId = [string](($response.Content | ConvertFrom-Json).id)
    if ([decimal](($response.Content | ConvertFrom-Json).currentValue) -ne [decimal]500.40) { throw 'Asset monetary value did not persist exactly.' }
    $response = Invoke-Api 'GET' '/api/assets' $aliceSession $null $null $null; Assert-Status $response 200 'List Alice assets'
    $aliceAssetPayload = ConvertFrom-Json -InputObject $response.Content
    if ($aliceAssetPayload -isnot [System.Array]) { throw 'Alice asset list was not a JSON array; response content was not printed.' }
    $aliceAssets = @($aliceAssetPayload)
    $aliceAssetIds = @($aliceAssets | ForEach-Object { [string]$_.id })
    $safeAliceAssetIds = @($aliceAssetIds | Where-Object { $_ -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' })
    Write-Host "[INFO] Alice assets response: status=$($response.StatusCode); count=$($aliceAssets.Count); asset IDs=$($safeAliceAssetIds -join ', ')" -ForegroundColor DarkGray
    if ($aliceAssets.Count -ne 1 -or $assetId -notin $aliceAssetIds) { throw 'Alice asset list did not contain exactly the newly created verification asset ID.' }
    $response = Invoke-Api 'GET' '/api/assets' $bobSession $null $null $null; Assert-Status $response 200 'List Bob assets'
    $bobAssetPayload = ConvertFrom-Json -InputObject $response.Content
    if ($bobAssetPayload -isnot [System.Array]) { throw 'Bob asset list was not a JSON array; response content was not printed.' }
    $bobAssets = @($bobAssetPayload)
    $bobAssetIds = @($bobAssets | ForEach-Object { [string]$_.id })
    $safeBobAssetIds = @($bobAssetIds | Where-Object { $_ -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' })
    Write-Host "[INFO] Bob assets response: status=$($response.StatusCode); count=$($bobAssets.Count); asset IDs=$($safeBobAssetIds -join ', ')" -ForegroundColor DarkGray
    if ($assetId -in $bobAssetIds) { throw "Bob asset response contains Alice's verification asset UUID $assetId." }
    if ($bobAssets.Count -ne 0) { throw "Fresh Bob account unexpectedly returned $($bobAssets.Count) asset(s); UUIDs=$($safeBobAssetIds -join ', ')." }
    $assetBody = @{ name='Updated verification cash'; category='Cash'; currentValue=501.40; purchaseValue=500.40; purchaseDate=$date; description='Updated temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'PUT' "/api/assets/$assetId" $aliceSession $aliceCsrf $assetBody 'application/json'; Assert-Status $response 200 'Update PostgreSQL asset'
    if ([decimal](($response.Content | ConvertFrom-Json).currentValue) -ne [decimal]501.40) { throw 'Updated asset monetary value did not persist exactly.' }
    $response = Invoke-Api 'PUT' "/api/assets/$assetId" $bobSession $bobCsrf $assetBody 'application/json'; Assert-Status $response 404 'Reject Bob updating Alice asset'
    $response = Invoke-Api 'DELETE' "/api/assets/$assetId" $bobSession $bobCsrf $null $null; Assert-Status $response 404 'Reject Bob deleting Alice asset'

    $liabilityBody = @{ name='Verification loan'; category='Personal Loan'; principalAmount=200.50; outstandingAmount=125.10; interestRate=5.2500; monthlyEmi=25.50; startDate=$date; dueDate=$futureGoalDate; description='Temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/liabilities' $aliceSession $aliceCsrf $liabilityBody 'application/json'; Assert-Status $response 201 'Create PostgreSQL liability'
    $liabilityId = [string](($response.Content | ConvertFrom-Json).id)
    $response = Invoke-Api 'GET' '/api/liabilities' $aliceSession $null $null $null; Assert-Status $response 200 'List Alice liabilities'
    if ((Get-JsonArrayCount $response.Content 'Alice liability list') -ne 1) { throw 'Alice liability list did not return its record.' }
    $response = Invoke-Api 'GET' '/api/liabilities' $bobSession $null $null $null; Assert-Status $response 200 'List Bob liabilities'
    if ((Get-JsonArrayCount $response.Content 'Bob liability list') -ne 0) { throw 'Bob received Alice liabilities.' }
    $liabilityBody = @{ name='Updated verification loan'; category='Personal Loan'; principalAmount=200.50; outstandingAmount=120.10; interestRate=5.2500; monthlyEmi=25.50; startDate=$date; dueDate=$futureGoalDate; description='Updated temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'PUT' "/api/liabilities/$liabilityId" $aliceSession $aliceCsrf $liabilityBody 'application/json'; Assert-Status $response 200 'Update PostgreSQL liability'
    if ([decimal](($response.Content | ConvertFrom-Json).outstandingAmount) -ne [decimal]120.10) { throw 'Updated liability amount did not persist exactly.' }
    $response = Invoke-Api 'PUT' "/api/liabilities/$liabilityId" $bobSession $bobCsrf $liabilityBody 'application/json'; Assert-Status $response 404 'Reject Bob updating Alice liability'
    $response = Invoke-Api 'DELETE' "/api/liabilities/$liabilityId" $bobSession $bobCsrf $null $null; Assert-Status $response 404 'Reject Bob deleting Alice liability'

    $investmentBody = @{ name='Verification deposit'; category='Fixed Deposits'; investedAmount=250.30; currentValue=300.30; purchaseDate=$date; description='Temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/investments' $aliceSession $aliceCsrf $investmentBody 'application/json'; Assert-Status $response 201 'Create PostgreSQL investment'
    $investmentId = [string](($response.Content | ConvertFrom-Json).id)
    $response = Invoke-Api 'GET' '/api/investments' $aliceSession $null $null $null; Assert-Status $response 200 'List Alice investments'
    if ((Get-JsonArrayCount $response.Content 'Alice investment list') -ne 1) { throw 'Alice investment list did not return its record.' }
    $response = Invoke-Api 'GET' '/api/investments' $bobSession $null $null $null; Assert-Status $response 200 'List Bob investments'
    if ((Get-JsonArrayCount $response.Content 'Bob investment list') -ne 0) { throw 'Bob received Alice investments.' }
    $investmentBody = @{ name='Updated verification deposit'; category='Fixed Deposits'; investedAmount=250.30; currentValue=310.30; purchaseDate=$date; description='Updated temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'PUT' "/api/investments/$investmentId" $aliceSession $aliceCsrf $investmentBody 'application/json'; Assert-Status $response 200 'Update PostgreSQL investment'
    if ([decimal](($response.Content | ConvertFrom-Json).currentValue) -ne [decimal]310.30) { throw 'Updated investment value did not persist exactly.' }
    $response = Invoke-Api 'PUT' "/api/investments/$investmentId" $bobSession $bobCsrf $investmentBody 'application/json'; Assert-Status $response 404 'Reject Bob updating Alice investment'
    $response = Invoke-Api 'DELETE' "/api/investments/$investmentId" $bobSession $bobCsrf $null $null; Assert-Status $response 404 'Reject Bob deleting Alice investment'

    $insuranceBody = @{ provider='Verification Provider'; policyName='Verification cover'; category='Health'; coverageAmount=10000.00; premiumAmount=50.25; premiumFrequency='Monthly'; startDate=$date; renewalDate=$futureGoalDate; description='Temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/insurance' $aliceSession $aliceCsrf $insuranceBody 'application/json'; Assert-Status $response 201 'Create PostgreSQL insurance policy'
    $insuranceId = [string](($response.Content | ConvertFrom-Json).id)
    $response = Invoke-Api 'GET' '/api/insurance' $aliceSession $null $null $null; Assert-Status $response 200 'List Alice insurance policies'
    if ((Get-JsonArrayCount $response.Content 'Alice policy list') -ne 1) { throw 'Alice policy list did not return its record.' }
    $response = Invoke-Api 'GET' '/api/insurance' $bobSession $null $null $null; Assert-Status $response 200 'List Bob insurance policies'
    if ((Get-JsonArrayCount $response.Content 'Bob insurance list') -ne 0) { throw 'Bob received Alice insurance policies.' }
    $insuranceBody = @{ provider='Updated Verification Provider'; policyName='Updated verification cover'; category='Health'; coverageAmount=12000.00; premiumAmount=55.25; premiumFrequency='Monthly'; startDate=$date; renewalDate=$futureGoalDate; description='Updated temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'PUT' "/api/insurance/$insuranceId" $aliceSession $aliceCsrf $insuranceBody 'application/json'; Assert-Status $response 200 'Update PostgreSQL insurance policy'
    if ([decimal](($response.Content | ConvertFrom-Json).coverageAmount) -ne [decimal]12000.00) { throw 'Updated insurance coverage did not persist exactly.' }
    $response = Invoke-Api 'PUT' "/api/insurance/$insuranceId" $bobSession $bobCsrf $insuranceBody 'application/json'; Assert-Status $response 404 'Reject Bob updating Alice insurance policy'
    $response = Invoke-Api 'DELETE' "/api/insurance/$insuranceId" $bobSession $bobCsrf $null $null; Assert-Status $response 404 'Reject Bob deleting Alice insurance policy'

    $overviewResponse = Invoke-Api 'GET' '/api/financial-overview' $aliceSession $null $null $null; Assert-Status $overviewResponse 200 'Read PostgreSQL financial overview'
    $overview = $overviewResponse.Content | ConvertFrom-Json
    if ([decimal]$overview.totalAssets -ne [decimal]501.40 -or [decimal]$overview.totalInvestments -ne [decimal]310.30 -or [decimal]$overview.totalLiabilities -ne [decimal]120.10 -or [decimal]$overview.netWorth -ne [decimal]691.60 -or [decimal]$overview.monthlyIncome -ne [decimal]1200.50 -or [decimal]$overview.monthlyExpenses -ne [decimal]250.25 -or [decimal]$overview.monthlySavings -ne [decimal]950.25 -or [decimal]$overview.totalInsuranceCoverage -ne [decimal]12000.00) { throw 'Financial overview totals did not match persisted PostgreSQL records.' }
    Write-Host '[PASS] PostgreSQL financial overview monetary totals and investment accounting convention' -ForegroundColor Green

    $budgetBody = @{ name='Verification budget'; category='Verification'; monthlyLimit=300.00; startDate=$monthStart; endDate=$monthEnd; alertThresholdPercentage=80.00 } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/budgets' $aliceSession $aliceCsrf $budgetBody 'application/json'; Assert-Status $response 201 'Create PostgreSQL budget'
    $budgetId = [string](($response.Content | ConvertFrom-Json).id)
    $budget = $response.Content | ConvertFrom-Json
    if ([decimal]$budget.amountSpent -ne [decimal]250.25 -or [decimal]$budget.remainingAmount -ne [decimal]49.75 -or -not $budget.alertTriggered) { throw 'Budget expense-only arithmetic or threshold was incorrect.' }
    $response = Invoke-Api 'GET' '/api/budgets' $aliceSession $null $null $null; Assert-Status $response 200 'List Alice budgets'
    if ((Get-JsonArrayCount $response.Content 'Alice budget list') -ne 1) { throw 'Alice budget list did not return its record.' }
    $response = Invoke-Api 'GET' '/api/budgets' $bobSession $null $null $null; Assert-Status $response 200 'List Bob budgets'
    if ((Get-JsonArrayCount $response.Content 'Bob budget list') -ne 0) { throw 'Bob received Alice budgets.' }
    $response = Invoke-Api 'PUT' "/api/budgets/$budgetId" $bobSession $bobCsrf $budgetBody 'application/json'; Assert-Status $response 404 'Reject Bob updating Alice budget'
    $response = Invoke-Api 'DELETE' "/api/budgets/$budgetId" $bobSession $bobCsrf $null $null; Assert-Status $response 404 'Reject Bob deleting Alice budget'
    $response = Invoke-Api 'POST' '/api/budgets' $aliceSession $aliceCsrf $budgetBody 'application/json'; Assert-Status $response 409 'Reject duplicate overlapping PostgreSQL budget'
    $budgetBody = @{ name='Updated verification budget'; category='Verification'; monthlyLimit=350.00; startDate=$monthStart; endDate=$monthEnd; alertThresholdPercentage=75.00 } | ConvertTo-Json -Compress
    $response = Invoke-Api 'PUT' "/api/budgets/$budgetId" $aliceSession $aliceCsrf $budgetBody 'application/json'; Assert-Status $response 200 'Update PostgreSQL budget'
    if ([decimal](($response.Content | ConvertFrom-Json).monthlyLimit) -ne [decimal]350.00) { throw 'Updated budget limit did not persist exactly.' }

    $goalBody = @{ name='Verification goal'; category='Savings'; targetAmount=2000.00; currentAmount=500.00; targetDate=$futureGoalDate; priority='MEDIUM'; description='Temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/goals' $aliceSession $aliceCsrf $goalBody 'application/json'; Assert-Status $response 201 'Create PostgreSQL financial goal'
    $goalId = [string](($response.Content | ConvertFrom-Json).id)
    $goal = $response.Content | ConvertFrom-Json
    if ([decimal]$goal.progressPercentage -ne [decimal]25.00 -or [decimal]$goal.remainingAmount -ne [decimal]1500.00) { throw 'Goal progress or remaining amount was incorrect.' }
    $response = Invoke-Api 'GET' '/api/goals' $aliceSession $null $null $null; Assert-Status $response 200 'List Alice financial goals'
    if ((Get-JsonArrayCount $response.Content 'Alice goal list') -ne 1) { throw 'Alice goal list did not return its record.' }
    $response = Invoke-Api 'GET' '/api/goals' $bobSession $null $null $null; Assert-Status $response 200 'List Bob financial goals'
    if ((Get-JsonArrayCount $response.Content 'Bob financial goal list') -ne 0) { throw 'Bob received Alice financial goals.' }
    $response = Invoke-Api 'PUT' "/api/goals/$goalId" $bobSession $bobCsrf $goalBody 'application/json'; Assert-Status $response 404 'Reject Bob updating Alice financial goal'
    $response = Invoke-Api 'DELETE' "/api/goals/$goalId" $bobSession $bobCsrf $null $null; Assert-Status $response 404 'Reject Bob deleting Alice financial goal'
    $goalBody = @{ name='Updated verification goal'; category='Savings'; targetAmount=2000.00; currentAmount=600.00; targetDate=$futureGoalDate; priority='MEDIUM'; description='Updated temporary verification record' } | ConvertTo-Json -Compress
    $response = Invoke-Api 'PUT' "/api/goals/$goalId" $aliceSession $aliceCsrf $goalBody 'application/json'; Assert-Status $response 200 'Update PostgreSQL financial goal'
    if ([decimal](($response.Content | ConvertFrom-Json).currentAmount) -ne [decimal]600.00) { throw 'Updated goal progress did not persist exactly.' }

    $recurringBody = @{ title='Verification scheduled expense'; type='EXPENSE'; amount=25.25; category='Verification'; frequency='MONTHLY'; nextDueDate=$date; endDate=$futureGoalDate; active=$true } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' '/api/recurring-transactions' $aliceSession $aliceCsrf $recurringBody 'application/json'; Assert-Status $response 201 'Create PostgreSQL recurring transaction'
    $recurringId = [string](($response.Content | ConvertFrom-Json).id)
    $response = Invoke-Api 'GET' '/api/recurring-transactions' $aliceSession $null $null $null; Assert-Status $response 200 'List Alice recurring transactions'
    if ((Get-JsonArrayCount $response.Content 'Alice recurring list') -ne 1) { throw 'Alice recurring list did not return its record.' }
    $response = Invoke-Api 'GET' '/api/recurring-transactions' $bobSession $null $null $null; Assert-Status $response 200 'List Bob recurring transactions'
    if ((Get-JsonArrayCount $response.Content 'Bob recurring list') -ne 0) { throw 'Bob received Alice recurring transactions.' }
    $response = Invoke-Api 'PUT' "/api/recurring-transactions/$recurringId" $bobSession $bobCsrf $recurringBody 'application/json'; Assert-Status $response 404 'Reject Bob updating Alice recurrence'
    $response = Invoke-Api 'DELETE' "/api/recurring-transactions/$recurringId" $bobSession $bobCsrf $null $null; Assert-Status $response 404 'Reject Bob deleting Alice recurrence'
    $response = Invoke-Api 'PATCH' "/api/recurring-transactions/$recurringId/active" $aliceSession $aliceCsrf (@{ active=$false } | ConvertTo-Json -Compress) 'application/json'; Assert-Status $response 200 'Pause PostgreSQL recurring transaction'
    if (($response.Content | ConvertFrom-Json).active) { throw 'Recurring transaction did not pause.' }
    $response = Invoke-Api 'PATCH' "/api/recurring-transactions/$recurringId/active" $aliceSession $aliceCsrf (@{ active=$true } | ConvertTo-Json -Compress) 'application/json'; Assert-Status $response 200 'Resume PostgreSQL recurring transaction'
    if (-not ($response.Content | ConvertFrom-Json).active) { throw 'Recurring transaction did not resume.' }
    $occurrenceBody = @{ occurrenceDate=$date } | ConvertTo-Json -Compress
    $response = Invoke-Api 'POST' "/api/recurring-transactions/$recurringId/occurrences" $aliceSession $aliceCsrf $occurrenceBody 'application/json'; Assert-Status $response 201 'Record PostgreSQL recurring occurrence'
    $occurrenceResult = ConvertFrom-Json -InputObject $response.Content
    $occurrenceTransactionId = [string]$occurrenceResult.transactionId
    if ($occurrenceTransactionId -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') { throw 'Occurrence API response did not contain a valid transaction UUID; response content was not printed.' }
    Write-Host "[INFO] Occurrence response: status=$($response.StatusCode); transaction ID=$occurrenceTransactionId" -ForegroundColor DarkGray
    $response = Invoke-Api 'POST' "/api/recurring-transactions/$recurringId/occurrences" $aliceSession $aliceCsrf $occurrenceBody 'application/json'; Assert-Status $response 409 'Reject duplicate PostgreSQL recurring occurrence'
    $response = Invoke-Api 'GET' '/api/transactions' $aliceSession $null $null $null; Assert-Status $response 200 'Verify recorded recurring transaction'
    $aliceTransactionsAfterOccurrence = ConvertFrom-Json -InputObject $response.Content
    if ($aliceTransactionsAfterOccurrence -isnot [System.Array]) { throw 'Alice transaction endpoint did not return a JSON array; response content was not printed.' }
    $aliceTransactionRows = @($aliceTransactionsAfterOccurrence)
    $matchingOccurrenceTransactions = @($aliceTransactionRows | Where-Object { [string]$_.id -eq $occurrenceTransactionId })
    $aliceTransactionRowIds = @($aliceTransactionRows | ForEach-Object { [string]$_.id })
    $safeAliceTransactionRowIds = @($aliceTransactionRowIds | Where-Object { $_ -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' })
    Write-Host "[INFO] Occurrence readback: status=$($response.StatusCode); Alice transaction count=$($aliceTransactionRows.Count); matching ID count=$($matchingOccurrenceTransactions.Count); transaction IDs=$($safeAliceTransactionRowIds -join ', ')" -ForegroundColor DarkGray
    if ($matchingOccurrenceTransactions.Count -ne 1) { throw "Recorded occurrence transaction UUID was absent or duplicated in Alice's authenticated transaction list (matches=$($matchingOccurrenceTransactions.Count), rows=$($aliceTransactionRows.Count))." }

    $priorOccurrencePgPassword = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $databasePassword
        $escapedAliceEmail = $script:aliceEmail.Replace("'", "''")
        $occurrencePersistenceSql = "SELECT t.id::text FROM recurring_occurrence o JOIN recurring_transaction r ON r.id=o.recurring_transaction_id JOIN financial_transaction t ON t.id=o.transaction_id JOIN app_user u ON u.id=o.user_id WHERE o.recurring_transaction_id='$recurringId'::uuid AND o.occurrence_date='$date'::date AND o.transaction_id='$occurrenceTransactionId'::uuid AND u.email='$escapedAliceEmail' AND r.user_id=u.id AND t.user_id=u.id;"
        $persistedOccurrenceTransactionId = Invoke-Psql $psqlPath $pgHost $pgPort $databaseUser $databaseName $occurrencePersistenceSql
    }
    finally {
        if ($null -eq $priorOccurrencePgPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue } else { $env:PGPASSWORD = $priorOccurrencePgPassword }
    }
    if ($persistedOccurrenceTransactionId -ne $occurrenceTransactionId) { throw 'PostgreSQL occurrence row, linked transaction, or Alice ownership did not match the API response UUID.' }
    Write-Host '[PASS] PostgreSQL occurrence and associated Alice transaction are committed and linked' -ForegroundColor Green

    foreach ($endpoint in @('/api/forecast','/api/financial-health','/api/intelligence/spending')) {
        $response = Invoke-Api 'GET' $endpoint $aliceSession $null $null $null; Assert-Status $response 200 "Read PostgreSQL $endpoint"
        $bobResponse = Invoke-Api 'GET' $endpoint $bobSession $null $null $null; Assert-Status $bobResponse 200 "Read Bob-scoped $endpoint"
        $bobData = $bobResponse.Content | ConvertFrom-Json
        if ($endpoint -eq '/api/forecast' -and ([decimal]$bobData.averageHistoricalIncome -ne 0 -or [decimal]$bobData.averageHistoricalExpenses -ne 0 -or [decimal]$bobData.recurringMonthlyExpenses -ne 0)) { throw 'Bob forecast exposed Alice financial data.' }
        if ($endpoint -eq '/api/financial-health' -and $null -ne $bobData.score) { throw 'Bob financial health exposed Alice financial data.' }
        if ($endpoint -eq '/api/intelligence/spending' -and @($bobData.topCategories).Count -ne 0) { throw 'Bob spending intelligence exposed Alice financial data.' }
    }
    $forecast = (Invoke-Api 'GET' '/api/forecast' $aliceSession $null $null $null).Content | ConvertFrom-Json
    if (@($forecast.months).Count -ne 3) { throw 'Forecast did not return three projected months.' }
    $health = (Invoke-Api 'GET' '/api/financial-health' $aliceSession $null $null $null).Content | ConvertFrom-Json
    if ($null -eq $health.score -or @($health.components).Count -ne 5) { throw 'Financial health assessment was incomplete.' }
    $spending = (Invoke-Api 'GET' '/api/intelligence/spending' $aliceSession $null $null $null).Content | ConvertFrom-Json
    if (@($spending.topCategories | Where-Object category -eq 'Verification').Count -ne 1) { throw 'Spending intelligence did not include the persisted expense category.' }
    Write-Host '[PASS] PostgreSQL forecast, financial health, spending intelligence, and owner-scoped analytics' -ForegroundColor Green

    $response = Invoke-Api 'DELETE' "/api/assets/$assetId" $aliceSession $aliceCsrf $null $null; Assert-Status $response 204 'Delete Alice PostgreSQL asset'
    $response = Invoke-Api 'DELETE' "/api/liabilities/$liabilityId" $aliceSession $aliceCsrf $null $null; Assert-Status $response 204 'Delete Alice PostgreSQL liability'
    $response = Invoke-Api 'DELETE' "/api/investments/$investmentId" $aliceSession $aliceCsrf $null $null; Assert-Status $response 204 'Delete Alice PostgreSQL investment'
    $response = Invoke-Api 'DELETE' "/api/insurance/$insuranceId" $aliceSession $aliceCsrf $null $null; Assert-Status $response 204 'Delete Alice PostgreSQL insurance policy'
    $response = Invoke-Api 'DELETE' "/api/budgets/$budgetId" $aliceSession $aliceCsrf $null $null; Assert-Status $response 204 'Delete Alice PostgreSQL budget'
    $response = Invoke-Api 'DELETE' "/api/goals/$goalId" $aliceSession $aliceCsrf $null $null; Assert-Status $response 204 'Delete Alice PostgreSQL financial goal'
    $response = Invoke-Api 'DELETE' "/api/recurring-transactions/$recurringId" $aliceSession $aliceCsrf $null $null; Assert-Status $response 204 'Delete Alice PostgreSQL recurring transaction'

    $response = Invoke-Api 'DELETE' "/api/transactions/$expenseId" $aliceSession $aliceCsrf $null $null
    Assert-Status $response 204 'Delete Alice transaction'
    $response = Invoke-Api 'GET' '/api/transactions' $aliceSession $null $null $null
    Assert-Status $response 200 'Read Alice transactions after expense deletion'
    $transactionsAfterExpenseDelete = ConvertFrom-Json -InputObject $response.Content
    if ($transactionsAfterExpenseDelete -isnot [System.Array]) { throw 'Alice transaction endpoint did not return a JSON array after delete; response content was not printed.' }
    $rowsAfterExpenseDelete = @($transactionsAfterExpenseDelete)
    $idsAfterExpenseDelete = @($rowsAfterExpenseDelete | ForEach-Object { [string]$_.id })
    $safeIdsAfterExpenseDelete = @($idsAfterExpenseDelete | Where-Object { $_ -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' })
    Write-Host "[INFO] After expense DELETE: GET status=$($response.StatusCode); count=$($rowsAfterExpenseDelete.Count); transaction IDs=$($safeIdsAfterExpenseDelete -join ', ')" -ForegroundColor DarkGray
    if ($rowsAfterExpenseDelete.Count -ne 2 -or $expenseId -in $idsAfterExpenseDelete -or $incomeId -notin $idsAfterExpenseDelete -or $occurrenceTransactionId -notin $idsAfterExpenseDelete) {
        throw 'Expense deletion readback did not contain exactly the remaining income and recurring-occurrence transaction IDs.'
    }
    $response = Invoke-Api 'DELETE' "/api/transactions/$incomeId" $aliceSession $aliceCsrf $null $null
    Assert-Status $response 204 'Delete remaining Alice transaction'
    $response = Invoke-Api 'GET' '/api/transactions' $aliceSession $null $null $null
    Assert-Status $response 200 'Read Alice transactions after income deletion'
    $transactionsAfterIncomeDelete = ConvertFrom-Json -InputObject $response.Content
    if ($transactionsAfterIncomeDelete -isnot [System.Array]) { throw 'Alice transaction endpoint did not return a JSON array after income delete; response content was not printed.' }
    $rowsAfterIncomeDelete = @($transactionsAfterIncomeDelete)
    $idsAfterIncomeDelete = @($rowsAfterIncomeDelete | ForEach-Object { [string]$_.id })
    $safeIdsAfterIncomeDelete = @($idsAfterIncomeDelete | Where-Object { $_ -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' })
    Write-Host "[INFO] After income DELETE: GET status=$($response.StatusCode); count=$($rowsAfterIncomeDelete.Count); transaction IDs=$($safeIdsAfterIncomeDelete -join ', ')" -ForegroundColor DarkGray
    if ($rowsAfterIncomeDelete.Count -ne 1 -or $incomeId -in $idsAfterIncomeDelete -or $expenseId -in $idsAfterIncomeDelete -or $occurrenceTransactionId -notin $idsAfterIncomeDelete) {
        throw 'Income deletion readback did not leave exactly the expected recurring-occurrence transaction ID.'
    }

    $priorDeleteCheckPassword = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $databasePassword
        $escapedAliceEmailForDelete = $script:aliceEmail.Replace("'", "''")
        $deletedRowCountSql = "SELECT count(*) FROM financial_transaction t JOIN app_user u ON u.id=t.user_id WHERE u.email='$escapedAliceEmailForDelete' AND t.id IN ('$expenseId'::uuid,'$incomeId'::uuid);"
        $deletedRowCount = Invoke-Psql $psqlPath $pgHost $pgPort $databaseUser $databaseName $deletedRowCountSql
    }
    finally {
        if ($null -eq $priorDeleteCheckPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue } else { $env:PGPASSWORD = $priorDeleteCheckPassword }
    }
    if ($deletedRowCount -ne '0') { throw "PostgreSQL still contains $deletedRowCount verification transaction row(s) after HTTP 204 deletes." }
    Write-Host '[PASS] PostgreSQL confirms both explicitly deleted Alice transactions are absent; the occurrence transaction remains' -ForegroundColor Green

    $response = Invoke-Api 'POST' '/api/auth/logout' $aliceSession $aliceCsrf $null $null
    Assert-Status $response 204 'Logout Alice'
    $response = Invoke-Api 'GET' '/api/auth/me' $aliceSession $null $null $null
    Assert-Status $response 401 'Reject Alice after logout'
    $response = Invoke-Api 'POST' '/api/auth/logout' $bobSession $bobCsrf $null $null
    Assert-Status $response 204 'Logout Bob'
    $response = Invoke-Api 'GET' '/api/auth/me' $bobSession $null $null $null
    Assert-Status $response 401 'Reject Bob after logout'

    $script:verificationPassed = $true
    Write-Host 'PostgreSQL API verification passed.' -ForegroundColor Green
    if ($KeepBackend) {
        $pidFile = Join-Path $targetRoot 'postgres-verification.pid'
        Set-Content -LiteralPath $pidFile -Value $script:backendProcess.Id -NoNewline
        Write-Host "Backend left running (PID $($script:backendProcess.Id)). Stop it with: Stop-Process -Id $($script:backendProcess.Id)" -ForegroundColor Yellow
    }
}
catch {
    Write-Error $_.Exception.Message
    $exitCode = 1
}
finally {
    if ($script:aliceEmail -or $script:bobEmail) {
        try {
            $priorCleanupPassword = $env:PGPASSWORD
            # Test accounts are owned by the application schema; clean them up using its role.
            $env:PGPASSWORD = $databasePassword
            $emailsToRemove = @($script:aliceEmail, $script:bobEmail)
            Assert-TestUserRemoved $psqlPath $pgHost $pgPort $databaseUser $databaseName $emailsToRemove
            if ($null -eq $priorCleanupPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue } else { $env:PGPASSWORD = $priorCleanupPassword }
            Write-Host '[PASS] Temporary verification accounts and their transactions removed' -ForegroundColor Green
        } catch {
            Write-Warning 'Could not remove temporary verification accounts. Review the database manually; no password values were printed.'
            $exitCode = 1
        }
    }
    if ($script:backendProcess -and (-not $script:keepBackend -or -not $script:verificationPassed) -and -not $script:backendProcess.HasExited) {
        Stop-Process -Id $script:backendProcess.Id -Force -ErrorAction SilentlyContinue
        Write-Host 'Stopped the backend verification process.' -ForegroundColor DarkGray
    }
    foreach ($name in $environmentNames) { Set-ProcessEnvironment $name $originalEnvironment[$name] }
}
exit $exitCode
