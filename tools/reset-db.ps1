<#
.SYNOPSIS
    Rebuilds the local development database from the migrations.

.DESCRIPTION
    Stops the Postgres container, removes its named volume and starts it again, so Flyway applies every
    migration to an empty database and the repeatable seeds come back. Use it when a migration that has not been
    merged yet is edited after it already ran locally (Flyway then refuses to start on the checksum), or when the
    local data has drifted. Everything typed into the local database by hand is lost; nothing outside the
    development stack is touched.

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
Write-Host 'Development database rebuilt; the next backend start applies every migration.'
