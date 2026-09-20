<#
.SYNOPSIS
    Runs the Playwright end-to-end suite against a locally booted backend.

.DESCRIPTION
    Starts the infrastructure containers and the backend with the local profile unless port 8080 is already in use,
    runs `npx playwright test` in frontend/ (Playwright starts the dev server itself), then stops the backend it started.

.EXAMPLE
    .\tools\e2e.ps1
#>
[CmdletBinding()]
param()

$root = Split-Path -Parent $PSScriptRoot
$started = $null

docker compose -f (Join-Path $root 'docker-compose.yaml') up -d postgres redis mailpit minio | Out-Null

if (-not (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
    $log = Join-Path $root 'build\e2e-backend.log'
    New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
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
