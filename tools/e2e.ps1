<#
.SYNOPSIS
    Runs the Playwright end-to-end suite against a locally booted backend.

.DESCRIPTION
    Starts the infrastructure containers (the malware scanner included, waited for until healthy) and the backend with the local profile (request limit raised so the suite
    is not rate limited) unless port 8080 is already in use (a port Docker still holds after `docker compose stop app` is waited for),
    runs `npx playwright test` in frontend/ (Playwright starts the dev server itself), then stops the backend it started.
    The frontend nginx configuration also runs in a throwaway container on port 4280 in front of the local backend
    (`E2E_NGINX_URL`), so that the upload limits are tested through it.
    A running award-backend container is stopped for the run and started again afterwards, also when the run fails
    or is interrupted (a finally block). A killed PowerShell process skips it; build\e2e.state then lets the next
    run stop the backend left behind and restart the container. Mailpit is emptied at the start, and the backend
    port must be free before the container starts again.
    Browsers come from PLAYWRIGHT_BROWSERS_PATH, D:\Tools\ms-playwright when it is not set.

.PARAMETER Grep
    Runs only the tests whose title or file matches the pattern (Playwright --grep).

.PARAMETER Workers
    Number of Playwright workers. Default 2: four workers next to the backend, ClamAV and the containers
    exhaust the memory of the development machine.

.PARAMETER TimeoutMinutes
    Stops the Playwright run after this many minutes (Playwright --global-timeout), so a run interrupted
    by a sleeping machine fails instead of hanging. Default 45.

.PARAMETER RetryLimit
    When at most this many tests fail, they run once more with one worker (Playwright --last-failed) and the
    result of that run counts. Default 3; 0 turns the retry off.

.EXAMPLE
    .\tools\e2e.ps1
    .\tools\e2e.ps1 -Grep 'award history'
#>
[CmdletBinding()]
param(
    [string]$Grep,
    [int]$Workers = 2,
    [int]$TimeoutMinutes = 45,
    [int]$RetryLimit = 3
)

$root = Split-Path -Parent $PSScriptRoot
$gateLock = Join-Path $root 'build\gate.lock'
if (Test-Path $gateLock) {
    $gate = Get-Process -Id ([int](Get-Content $gateLock -Raw)) -ErrorAction SilentlyContinue
    if ($gate -and $gate.ProcessName -match '^(pwsh|powershell)$') {
        Write-Host "A gate is running (PID $($gate.Id)); run the end-to-end suite after it."
        exit 1
    }
}
$started = $null
$nginx = 'award-e2e-nginx'
$stateFile = Join-Path $root 'build\e2e.state'
if (-not $env:PLAYWRIGHT_BROWSERS_PATH) {
    $env:PLAYWRIGHT_BROWSERS_PATH = 'D:\Tools\ms-playwright'
}

function Wait-PortClosed([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline -and (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
        Start-Sleep -Seconds 1
    }
}

function Save-State {
    $state = @{ BackendPid = if ($started) { $started.Id } else { 0 }; Container = $container }
    Set-Content -Path $stateFile -Value ($state | ConvertTo-Json -Compress)
}

$inherited = $false
if (Test-Path $stateFile) {
    Write-Host 'Cleaning up after a run that was killed before its own clean-up.'
    $previous = Get-Content $stateFile -Raw | ConvertFrom-Json
    if ($previous.BackendPid) {
        Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
            Where-Object { (Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName -eq 'java' } |
            ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $previous.BackendPid -Force -ErrorAction SilentlyContinue
        Wait-PortClosed 30
    }
    docker rm -f $nginx 2>$null | Out-Null
    $inherited = [bool]$previous.Container
    Remove-Item $stateFile
}

docker compose -f (Join-Path $root 'docker-compose.yaml') up -d postgres redis mailpit minio clamav | Out-Null
try { Invoke-RestMethod -Method Delete -Uri 'http://localhost:8025/api/v1/messages' -TimeoutSec 5 | Out-Null } catch {
    Write-Host 'Mailpit could not be emptied; old messages stay.'
}

$scannerDeadline = (Get-Date).AddMinutes(5)
while ((docker inspect --format '{{.State.Health.Status}}' award-clamav) -ne 'healthy' -and (Get-Date) -lt $scannerDeadline) {
    Start-Sleep -Seconds 5
}

$container = [bool](docker ps --filter 'name=^award-backend$' --filter 'status=running' --format '{{.Names}}')
if ($container) {
    Write-Host 'Stopping the award-backend container for the run; it is started again afterwards.'
    docker stop award-backend | Out-Null
}
$container = $container -or $inherited
Save-State

try {
    docker rm -f $nginx 2>$null | Out-Null
    docker run -d --name $nginx -p 127.0.0.1:4280:80 --add-host app:host-gateway `
        -v "$(Join-Path $root 'frontend\nginx.conf'):/etc/nginx/conf.d/default.conf:ro" `
        -v "$(Join-Path $root 'frontend\security-headers.conf'):/etc/nginx/snippets/security-headers.conf:ro" `
        -v "$(Join-Path $root 'frontend\backend-proxy.conf'):/etc/nginx/snippets/backend-proxy.conf:ro" `
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
        Save-State
        $deadline = (Get-Date).AddMinutes(3)
        do {
            Start-Sleep -Seconds 5
            try { $health = (Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3).status } catch { $health = 'starting' }
        } while ($health -ne 'UP' -and (Get-Date) -lt $deadline)
        if ($health -ne 'UP') {
            Write-Host "Backend did not start; see $log"
            exit 1
        }
    }

    Push-Location (Join-Path $root 'frontend')
    try {
        $arguments = @("--global-timeout=$($TimeoutMinutes * 60000)", "--workers=$Workers")
        if ($Grep) { $arguments += @('--grep', $Grep) }
        & npx playwright test @arguments
        $exit = $LASTEXITCODE
        $lastRun = 'test-results\.last-run.json'
        $failed = if ($exit -and (Test-Path $lastRun)) { @((Get-Content $lastRun -Raw | ConvertFrom-Json).failedTests).Count } else { 0 }
        if ($failed -ge 1 -and $failed -le $RetryLimit) {
            Write-Host "Retrying the $failed failed test(s) once with one worker."
            & npx playwright test --last-failed --workers=1 "--global-timeout=$($TimeoutMinutes * 60000)"
            $exit = $LASTEXITCODE
            if (-not $exit) { Write-Host "Passed on retry: $failed test(s) are flaky." }
        }
    } finally {
        Pop-Location
    }
} finally {
    if ($started) {
        $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($listener) { Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $started.Id -Force -ErrorAction SilentlyContinue
        Wait-PortClosed 30
    }
    docker rm -f $nginx 2>$null | Out-Null
    if ($container) { docker start award-backend | Out-Null }
    Remove-Item $stateFile -ErrorAction SilentlyContinue
}

exit $exit
