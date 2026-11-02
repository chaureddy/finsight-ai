$DefaultPostgresJdbcUrl = 'jdbc:postgresql://127.0.0.1:5432/finsight'

function Get-PostgresEndpoint([Parameter(Mandatory = $true)][string]$DatabaseUrl) {
    if (-not $DatabaseUrl.StartsWith('jdbc:postgresql://', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'DATABASE_URL must be a PostgreSQL JDBC URL; H2 fallback is intentionally disabled.'
    }

    try {
        $uri = [Uri]($DatabaseUrl.Substring(5))
    }
    catch {
        throw 'DATABASE_URL is not a valid PostgreSQL JDBC URL.'
    }

    # DnsSafeHost omits IPv6 URL brackets, as expected by psql/pg_isready.
    $hostName = $uri.DnsSafeHost
    $port = if ($uri.IsDefaultPort -or $uri.Port -eq -1) { 5432 } else { $uri.Port }
    $databaseName = [Uri]::UnescapeDataString($uri.AbsolutePath.Trim('/'))

    if ([string]::IsNullOrWhiteSpace($hostName)) { throw 'DATABASE_URL must include a PostgreSQL host.' }
    if ($port -lt 1 -or $port -gt 65535) { throw 'DATABASE_URL must include a valid PostgreSQL port.' }
    if ($databaseName -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$') {
        throw 'DATABASE_URL must identify a simple PostgreSQL database name.'
    }

    return [pscustomobject]@{
        Uri = $uri
        Host = $hostName
        Port = [int]$port
        DatabaseName = $databaseName
    }
}
