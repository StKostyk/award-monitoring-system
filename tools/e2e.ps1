<#
.SYNOPSIS
    Runs the Playwright end-to-end suite against a locally booted backend.

.DESCRIPTION
    Starts the infrastructure containers (the malware scanner included, waited for until healthy) and the backend with the local profile (request limit raised so the suite
    is not rate limited) unless port 8080 is already in use (a port Docker still holds after `docker compose stop app` is waited for),
    runs `npx playwright test` in frontend/ (Playwright starts the dev server itself), then stops the backend it started.
    The frontend nginx configuration also runs in a throwaway container on port 4280 in front of the local backend
    (`E2E_NGINX_URL`), so that the upload limits are tested through it.
    A running award-backend container is stopped for the run and started again afterwards.
    Browsers come from PLAYWRIGHT_BROWSERS_PATH, D:\Tools\ms-playwright when it is not set.

.PARAMETER Grep
    Runs only the tests whose title or file matches the pattern (Playwright --grep).

.EXAMPLE
    .\tools\e2e.ps1
    .\tools\e2e.ps1 -Grep 'award history'
#>
[CmdletBinding()]
param(
    [string]$Grep
)

$root = Split-Path -Parent $PSScriptRoot
$started = $null
if (-not $env:PLAYWRIGHT_BROWSERS_PATH) {
    $env:PLAYWRIGHT_BROWSERS_PATH = 'D:\Tools\ms-playwright'
}

docker compose -f (Join-Path $root 'docker-compose.yaml') up -d postgres redis mailpit minio clamav | Out-Null

$scannerDeadline = (Get-Date).AddMinutes(5)
while ((docker inspect --format '{{.State.Health.Status}}' award-clamav) -ne 'healthy' -and (Get-Date) -lt $scannerDeadline) {
    Start-Sleep -Seconds 5
}

$container = [bool](docker ps --filter 'name=^award-backend$' --filter 'status=running' --format '{{.Names}}')
if ($container) {
    Write-Host 'Stopping the award-backend container for the run; it is started again afterwards.'
    docker stop award-backend | Out-Null
}

$nginx = 'award-e2e-nginx'
docker rm -f $nginx 2>$null | Out-Null
docker run -d --name $nginx -p 127.0.0.1:4280:80 --add-host app:host-gateway `
    -v "$(Join-Path $root 'frontend\nginx.conf'):/etc/nginx/conf.d/default.conf:ro" `
    -v "$(Join-Path $root 'frontend\security-headers.conf'):/etc/nginx/snippets/security-headers.conf:ro" `
    nginx:alpine | Out-Null
$env:E2E_NGINX_URL = 'http://127.0.0.1:4280'

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
    if ($health -ne 'UP') {
        docker rm -f $nginx 2>$null | Out-Null
        if ($container) { docker start award-backend | Out-Null }
        Write-Host "Backend did not start; see $log"
        exit 1
    }
}

Push-Location (Join-Path $root 'frontend')
$arguments = if ($Grep) { @('--grep', $Grep) } else { @() }
& npx playwright test @arguments
$exit = $LASTEXITCODE
Pop-Location

if ($started) {
    $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) { Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue }
    Stop-Process -Id $started.Id -Force -ErrorAction SilentlyContinue
}
docker rm -f $nginx 2>$null | Out-Null
if ($container) { docker start award-backend | Out-Null }

exit $exit
