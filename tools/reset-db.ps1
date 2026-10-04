<#
.SYNOPSIS
    Rebuilds the local development database from the migrations.

.DESCRIPTION
    Stops the Postgres container, removes its named volume and starts it again, so Flyway applies every
    migration to an empty database and the repeatable seeds come back. Use it when a migration that has not been
    merged yet is edited after it already ran locally (Flyway then refuses to start on the checksum), or when the
    local data has drifted. Everything typed into the local database by hand is lost; nothing outside the
    development stack is touched. A running award-backend container is restarted so it applies the migrations.

.EXAMPLE
    .\tools\reset-db.ps1
    .\tools\reset-db.ps1 -Force
#>
[CmdletBinding()]
param(
    [switch]$Force
)

$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root 'docker-compose.yaml'
$project = ((Split-Path -Leaf $root) -replace '[^a-zA-Z0-9]', '').ToLower()
$volume = docker volume ls -q `
    --filter "label=com.docker.compose.project=$project" `
    --filter 'label=com.docker.compose.volume=postgres_data' | Select-Object -First 1

if (-not $volume) {
    Write-Host "No Postgres volume of project '$project'; starting the container is enough."
} elseif (-not $Force) {
    Write-Host "This deletes the development database in volume '$volume'."
    if ((Read-Host 'Type the word reset to continue') -ne 'reset') {
        Write-Host 'Nothing was changed.'
        exit 1
    }
}

docker compose -f $compose stop postgres | Out-Null
docker compose -f $compose rm -f postgres | Out-Null
if ($volume) { docker volume rm $volume | Out-Null }
docker compose -f $compose up -d postgres | Out-Null

$deadline = (Get-Date).AddMinutes(2)
do {
    Start-Sleep -Seconds 2
    docker exec award-postgres pg_isready -q 2>$null
    $ready = $LASTEXITCODE -eq 0
} while (-not $ready -and (Get-Date) -lt $deadline)

if (-not $ready) {
    Write-Host 'Postgres did not become ready; check `docker compose logs postgres`.'
    exit 1
}

if (docker ps --filter 'name=^award-backend$' --filter 'status=running' --format '{{.Names}}') {
    docker restart award-backend | Out-Null
    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 3
        try { $health = (Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3).status } catch { $health = 'starting' }
    } while ($health -ne 'UP' -and (Get-Date) -lt $deadline)
    if ($health -ne 'UP') {
        Write-Host 'Database rebuilt, but the award-backend container is not healthy; check `docker logs award-backend`.'
        exit 1
    }
    Write-Host 'Development database rebuilt; the award-backend container restarted and applied every migration.'
} else {
    Write-Host 'Development database rebuilt; the next backend start applies every migration.'
}
