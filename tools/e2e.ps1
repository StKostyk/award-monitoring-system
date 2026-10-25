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
    exhaust the memory of the development machine. Below 6 GB free the default drops to 1; an explicit value stays.

.PARAMETER TimeoutMinutes
    Stops the Playwright run after this many minutes (Playwright --global-timeout), so a run interrupted
    by a sleeping machine fails instead of hanging. Default 45.

.PARAMETER RetryLimit
    When at most this many tests fail, they run once more with one worker (Playwright --last-failed) and the
    result of that run counts. More failures are retried too when one of them could not reach the dev server
    (it stopped during the run; the retry starts a new one). Default 3; 0 turns the retry off.

.OUTPUTS
    build/e2e-summary.txt: result, counts and every failed test with its retry outcome; the JSON reports in
    build/e2e-results.json and build/e2e-retry-results.json.

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
$freeGb = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB
if ($Workers -gt 1 -and -not $PSBoundParameters.ContainsKey('Workers') -and $freeGb -lt 6) {
    Write-Host ('{0:N1} GB free (under 6 GB): one Playwright worker.' -f $freeGb)
    $Workers = 1
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

function Get-E2eResults([string]$path) {
    if (-not (Test-Path $path)) { return }
    $pending = [System.Collections.Generic.Queue[object]]::new()
    (Get-Content $path -Raw | ConvertFrom-Json).suites | ForEach-Object { $pending.Enqueue($_) }
    while ($pending.Count) {
        $suite = $pending.Dequeue()
        foreach ($spec in @($suite.specs)) {
            foreach ($test in @($spec.tests)) {
                $last = @($test.results)[-1]
                [pscustomobject]@{
                    Name   = "$($spec.file):$($spec.line) $($spec.title)"
                    Status = if ($test.status -eq 'skipped') { 'skipped' } elseif ($last) { $last.status } else { 'unknown' }
                    Error  = (@($last.errors) | ForEach-Object { $_.message }) -join ' '
                }
            }
        }
        @($suite.suites) | Where-Object { $_ } | ForEach-Object { $pending.Enqueue($_) }
    }
}

function Write-E2eSummary([object[]]$first, [object[]]$failed, [object[]]$retried, [datetime]$since) {
    $span = (Get-Date) - $since
    $passed = @($first | Where-Object Status -eq 'passed').Count
    $skipped = @($first | Where-Object Status -eq 'skipped').Count
    $stillFailing = if ($retried) { @($retried | Where-Object { $_.Status -notin 'passed', 'skipped' }) } else { $failed }
    $lines = @(
        "E2E run:  $(Get-Date -Format 'yyyy-MM-dd HH:mm') ($([int]$span.TotalMinutes) min)"
        "Branch:   $(git -C $root rev-parse --abbrev-ref HEAD)"
        "Result:   $(if ($first -and -not $stillFailing) { 'PASS' } else { 'FAIL' }) - $passed passed, " +
            "$($failed.Count) failed, $skipped skipped$(if ($retried) { ", $($failed.Count - $stillFailing.Count) passed on retry" })"
    )
    if (-not $first) { $lines += 'No results: Playwright stopped before its report (see build/e2e-run.log).' }
    $lines += $failed | ForEach-Object {
        $again = $retried | Where-Object Name -eq $_.Name | Select-Object -First 1
        "  $($_.Status.PadRight(9)) $($_.Name)$(if ($again) { " -> retry $($again.Status)" })"
    }
    Set-Content -Path (Join-Path $root 'build\e2e-summary.txt') -Value $lines -Encoding utf8
    $lines | ForEach-Object { Write-Host $_ }
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
        $runStart = Get-Date
        $firstJson = Join-Path $root 'build\e2e-results.json'
        $retryJson = Join-Path $root 'build\e2e-retry-results.json'
        Remove-Item $firstJson, $retryJson -ErrorAction SilentlyContinue
        $arguments = @("--global-timeout=$($TimeoutMinutes * 60000)", "--workers=$Workers", '--reporter=list,json')
        if ($Grep) { $arguments += @('--grep', $Grep) }
        $env:PLAYWRIGHT_JSON_OUTPUT_NAME = $firstJson
        & npx playwright test @arguments
        $exit = $LASTEXITCODE
        $first = @(Get-E2eResults $firstJson)
        $failed = @($first | Where-Object { $_.Status -notin 'passed', 'skipped' })
        $serverDropped = [bool]($failed | Where-Object { $_.Error -match 'ERR_CONNECTION_REFUSED.*:4200' })
        $retried = @()
        if ($exit -and $failed.Count -ge 1 -and ($failed.Count -le $RetryLimit -or ($RetryLimit -and $serverDropped))) {
            $reason = if ($failed.Count -gt $RetryLimit) { ' (the dev server stopped answering during the run)' } else { '' }
            Write-Host "Retrying the $($failed.Count) failed test(s) once with one worker$reason."
            $env:PLAYWRIGHT_JSON_OUTPUT_NAME = $retryJson
            & npx playwright test --last-failed --workers=1 "--global-timeout=$($TimeoutMinutes * 60000)" `
                '--reporter=list,json'
            $exit = $LASTEXITCODE
            $retried = @(Get-E2eResults $retryJson)
            if (-not $exit) { Write-Host "Passed on retry: $($failed.Count) test(s) are flaky." }
        }
        Write-E2eSummary $first $failed $retried $runStart
    } finally {
        Remove-Item Env:PLAYWRIGHT_JSON_OUTPUT_NAME -ErrorAction SilentlyContinue
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
