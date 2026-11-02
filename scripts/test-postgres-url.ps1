$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'PostgresUrl.ps1')

$defaultEndpoint = Get-PostgresEndpoint $DefaultPostgresJdbcUrl
if ($defaultEndpoint.Host -ne '127.0.0.1' -or $defaultEndpoint.Port -ne 5432 -or $defaultEndpoint.DatabaseName -ne 'finsight') {
    throw 'Default PostgreSQL URL must select IPv4 loopback, port 5432, and the finsight database.'
}

$customEndpoint = Get-PostgresEndpoint 'jdbc:postgresql://db-dev.example.test:5544/custom_finance'
if ($customEndpoint.Host -ne 'db-dev.example.test' -or $customEndpoint.Port -ne 5544 -or $customEndpoint.DatabaseName -ne 'custom_finance') {
    throw 'DATABASE_URL custom host and port must be preserved.'
}

$ipv6Endpoint = Get-PostgresEndpoint 'jdbc:postgresql://[::1]:5545/finsight'
$ipv6Address = [System.Net.IPAddress]::Parse($ipv6Endpoint.Host)
if (-not [System.Net.IPAddress]::IPv6Loopback.Equals($ipv6Address) -or $ipv6Endpoint.Port -ne 5545) {
    throw 'DATABASE_URL explicit IPv6 host and port overrides must be preserved.'
}

Write-Host '[PASS] PostgreSQL URL defaults to IPv4 loopback and preserves explicit host/port overrides.' -ForegroundColor Green
