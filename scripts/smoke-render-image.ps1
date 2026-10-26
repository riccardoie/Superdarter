param([string]$Image = 'superdarter-match-refactor:test')

$ErrorActionPreference = 'Stop'
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$network = "sd-smoke-net-$suffix"
$database = "sd-smoke-db-$suffix"
$backend = "sd-smoke-api-$suffix"
$secret = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    'disposable-smoke-test-secret-32-bytes-only'))

function Assert-Smoke([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Request-Json([string]$Path, [string]$Method = 'GET', $Body = $null) {
    $arguments = @{
        Uri = "$script:base$Path"
        Method = $Method
        Headers = $script:headers
        ContentType = 'application/json'
        TimeoutSec = 15
    }
    if ($null -ne $Body) { $arguments.Body = ConvertTo-Json -InputObject $Body -Depth 10 }
    Invoke-RestMethod @arguments
}

try {
    docker network create $network | Out-Null
    Assert-Smoke ($LASTEXITCODE -eq 0) 'Could not create disposable network'
    docker run -d --name $database --network $network `
        -e POSTGRES_DB=smoke -e POSTGRES_USER=smoke -e POSTGRES_PASSWORD=smoke-only `
        postgres:16-alpine | Out-Null
    Assert-Smoke ($LASTEXITCODE -eq 0) 'Could not start disposable PostgreSQL'
    $databaseReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $database pg_isready -U smoke -d smoke *> $null
        if ($LASTEXITCODE -eq 0) { $databaseReady = $true; break }
        Start-Sleep -Seconds 1
    }
    Assert-Smoke $databaseReady 'Disposable PostgreSQL did not become ready'

    # Local PostgreSQL is not TLS-enabled. Never use this URL override on Render.
    docker run -d --name $backend --network $network -p '127.0.0.1::18080' `
        -e SPRING_PROFILES_ACTIVE=render -e PORT=18080 `
        -e "POSTGRES_HOST=$database" -e POSTGRES_PORT=5432 -e POSTGRES_DB=smoke `
        -e POSTGRES_USER=smoke -e POSTGRES_PASSWORD=smoke-only -e "JWT_SECRET=$secret" `
        -e CORS_ALLOWED_ORIGINS=https://smoke.example `
        -e "SPRING_DATASOURCE_URL=jdbc:postgresql://${database}:5432/smoke?sslmode=disable" `
        $Image | Out-Null
    Assert-Smoke ($LASTEXITCODE -eq 0) 'Could not start the production image'
    $port = (docker port $backend '18080/tcp').Trim().Split(':')[-1]
    $script:base = "http://127.0.0.1:$port"
    $script:headers = @{}
    $healthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $health = Invoke-RestMethod "$script:base/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    Assert-Smoke $healthy 'Production image did not become healthy with the render profile'

    $registration = Request-Json '/api/auth/register' 'POST' @{
        username = 'smoke-alice'; password = 'smoke-password-strong'; email = 'smoke@example.test'
    }
    $script:headers = @{ Authorization = "Bearer $($registration.token)" }
    $game = Request-Json '/api/games' 'POST' @{ gametype = '301'; players = @() }
    Assert-Smoke ($game.status -eq 'PENDING') 'Creation did not return a pending game'
    $id = $game.gameId
    $game = Request-Json "/api/games/$id/start" 'POST'
    foreach ($score in @('20 3 20 3 20 3', '20 3 19 3 2 1', '', '1 2')) {
        $game = Request-Json "/api/games/$id/rounds" 'POST' @{
            username = 'smoke-alice'; score = $score
            legId = $game.currentLegId; revision = $game.revision
        }
    }
    $reloaded = Request-Json "/api/games/$id"
    Assert-Smoke ($reloaded.status -eq 'COMPLETED') 'Double-out did not finish the match'
    Assert-Smoke ($reloaded.winner -eq 'smoke-alice') 'Wrong winner after reload'
    Assert-Smoke ($reloaded.players[0].points -eq 0) 'Winner did not reach zero'
    $stats = @(Request-Json '/api/playerStats' 'POST' @(@{ username = 'smoke-alice' }))
    Assert-Smoke ($stats[0].wonMatches -eq 1 -and $stats[0].wonLegs -eq 1) 'Incorrect player totals'

    $pending = Request-Json '/api/games' 'POST' @{ gametype = '501'; players = @() }
    Request-Json "/api/games/$($pending.gameId)" 'DELETE' | Out-Null
    $other = Request-Json '/api/games' 'POST' @{ gametype = '501'; players = @() }
    Request-Json "/api/games/$($other.gameId)/start" 'POST' | Out-Null
    $abandoned = Request-Json "/api/games/$($other.gameId)/abandon" 'POST'
    Assert-Smoke ($abandoned.status -eq 'ABANDONED' -and $null -eq $abandoned.winner) `
        'Abandonment incorrectly awarded a winner'
    Write-Output 'PASS: production image, render profile/PORT, fresh migrations, registration, scoring, reload, totals, deletion and abandonment'
} catch {
    docker logs --tail 100 $backend 2>&1 | Write-Host
    throw
} finally {
    # These unique names belong only to this smoke test. No application volumes are mounted.
    docker rm -f -v $backend $database 2>$null | Out-Null
    docker network rm $network 2>$null | Out-Null
}
