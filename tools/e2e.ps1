<#
.SYNOPSIS
    Runs the Playwright end-to-end suite against a locally booted backend.

.DESCRIPTION
    Starts the infrastructure containers and the backend with the local profile (request limit raised so the suite
    is not rate limited) unless port 8080 is already in use (a port Docker still holds after `docker compose stop app` is waited for),
    runs `npx playwright test` in frontend/ (Playwright starts the dev server itself), then stops the backend it started.

.EXAMPLE
    .\tools\e2e.ps1
#>
[CmdletBinding()]
param()

$root = Split-Path -Parent $PSScriptRoot
$started = $null

docker compose -f (Join-Path $root 'docker-compose.yaml') up -d postgres redis mailpit minio | Out-Null

if (docker ps --filter 'name=^award-backend$' --filter 'status=running' --format '{{.Names}}') {
    Write-Host 'Port 8080 is served by the award-backend container, not by this branch.'
    Write-Host 'Stop it first: docker compose stop app'
    exit 1
}

$released = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $released -and (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
        Where-Object { (Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName -match '^(com\.docker|docker|wslrelay|vpnkit)' })) {
    Start-Sleep -Seconds 2
}

if (-not (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
    $log = Join-Path $root 'build\e2e-backend.log'
    New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
    $env:AUTH_RATE_LIMIT_PER_MINUTE = '1000'
    $started = Start-Process -FilePath (Join-Path $root 'backend\mvnw.cmd') `
        -ArgumentList 'spring-boot:run', '-Dspring-boot.run.profiles=local' `
        -WorkingDirectory (Join-Path $root 'backend') -RedirectStandardOutput $log -PassThru -WindowStyle Hidden
    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 5
        try { $health = (Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3).status } catch { $health = 'starting' }
    } while ($health -ne 'UP' -and (Get-Date) -lt $deadline)
    if ($health -ne 'UP') { Write-Host "Backend did not start; see $log"; exit 1 }
}

Push-Location (Join-Path $root 'frontend')
& npx playwright test
$exit = $LASTEXITCODE
Pop-Location

if ($started) {
    $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) { Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue }
    Stop-Process -Id $started.Id -Force -ErrorAction SilentlyContinue
}

exit $exit
