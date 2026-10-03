<#
.SYNOPSIS
    Runs the quality gates and writes build/gate-summary.txt.

.DESCRIPTION
    Static analysis first (about half a minute), then the full build with tests and coverage, so a style
    violation never costs a five-minute run. -StaticOnly stops after the static checks (backend, then the
    frontend ESLint and Prettier check); -SkipFrontend leaves the Angular checks and unit tests out. The backend
    verify runs only when the branch changes something under backend/ compared with develop (develop passed it
    when it was merged); -Full runs it regardless.

.EXAMPLE
    .\tools\gate.ps1 -StaticOnly
    .\tools\gate.ps1
#>
[CmdletBinding()]
param(
    [switch]$StaticOnly,
    [switch]$SkipFrontend,
    [switch]$Full
)

$runningBackend = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -like '*spring-boot:run*' }
if ($runningBackend) {
    Write-Host 'A backend started with spring-boot:run is running (e2e.ps1 or a manual run) and shares backend/target.'
    Write-Host "Stop it first (PID $($runningBackend.ProcessId -join ', ')), then run the gate again."
    exit 1
}

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$logDir = Join-Path $root 'build'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir 'gate.log'
$mvnw = Join-Path $backend 'mvnw.cmd'

function Invoke-Maven([string[]]$Goals, [string]$Label) {
    Write-Host "$Label..." -NoNewline
    & $mvnw -f (Join-Path $backend 'pom.xml') @Goals *> $log
    $ok = $LASTEXITCODE -eq 0
    Write-Host $(if ($ok) { ' PASS' } else { ' FAIL' })
    return $ok
}

if (-not (Invoke-Maven @('-o', '-q', 'test-compile', 'checkstyle:check', 'pmd:check', 'spotbugs:check') 'static')) {
    Select-String -Path $log -Pattern '^\[WARN\].*\.java|^\[ERROR\] (High|Medium|Low):|violation|BugInstance' | Select-Object -First 20 |
        ForEach-Object { $_.Line }
    $checkstyle = Join-Path $backend 'target\checkstyle-result.xml'
    if (Test-Path $checkstyle) {
        ([xml](Get-Content $checkstyle -Raw)).checkstyle.file | Where-Object { $_.error } | ForEach-Object {
            $file = Split-Path -Leaf $_.name
            $_.error | ForEach-Object { "checkstyle ${file}:$($_.line) $($_.message)" }
        } | Select-Object -First 20
    }
    Write-Host 'Fix the violations above, then run the gate again.'
    exit 1
}
function Invoke-FrontendStatic {
    Push-Location (Join-Path $root 'frontend')
    & npm run lint *> $log
    $ok = $LASTEXITCODE -eq 0
    & npm run format:check *>> $log
    $ok = $ok -and $LASTEXITCODE -eq 0
    Pop-Location
    if (-not $ok) {
        Select-String -Path $log -Pattern 'error|\[warn\]' | Select-Object -First 20 | ForEach-Object { $_.Line }
    }
    return $ok
}

if ($StaticOnly) {
    if ($SkipFrontend) { exit 0 }
    Write-Host 'frontend static...' -NoNewline
    $frontendStaticOk = Invoke-FrontendStatic
    Write-Host $(if ($frontendStaticOk) { ' PASS' } else { ' FAIL' })
    if ($frontendStaticOk) { exit 0 } else { exit 1 }
}

$backendChanged = @(git -C $root diff --name-only develop -- backend) + @(git -C $root status --porcelain -- backend)
$backendOk = $true
$unit = $integration = $coverage = '-'
$backendStatus = 'SKIPPED (no backend changes since develop; -Full runs it)'
if ($Full -or ($backendChanged | Where-Object { $_ })) {
    docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Docker is not running: the integration and functional tests need it (TestContainers). Start Docker Desktop and run the gate again.'
        exit 1
    }

    $backendOk = Invoke-Maven @('verify') 'verify'
    $backendStatus = if ($backendOk) { 'PASS' } else { 'FAIL' }
    if (-not $backendOk) {
        Select-String -Path $log -Pattern '^\[ERROR\]\s{3}|Tests run:.*(Failures: [1-9]|Errors: [1-9])' |
            Select-Object -First 20 | ForEach-Object { $_.Line }
    }

    $counts = @(Select-String -Path $log -Pattern 'Tests run: (\d+), Failures: \d+, Errors: \d+, Skipped: \d+\s*$' |
        ForEach-Object { $_.Matches[0].Groups[1].Value })
    $unit = if ($counts.Count -ge 1) { "$($counts[0]) run" } else { '?' }
    $integration = if ($counts.Count -ge 2) { "$($counts[-1]) run" } else { '0 run' }

    $coverage = '?'
    $report = Join-Path $backend 'target\site\jacoco\index.html'
    if (Test-Path $report) {
        $html = Get-Content $report -Raw
        if ($html -match '<tfoot>.*?</tfoot>') {
            $cells = [regex]::Matches($Matches[0], '<td[^>]*>(.*?)</td>')
            if ($cells.Count -gt 8) {
                $missed = [int]($cells[7].Groups[1].Value -replace '[^0-9]', '')
                $total = [int]($cells[8].Groups[1].Value -replace '[^0-9]', '')
                if ($total -gt 0) {
                    $coverage = '{0:N1}% lines ({1}/{2})' -f (100.0 * ($total - $missed) / $total), ($total - $missed), $total
                }
            }
        }
    }
}

$frontend = 'skipped'
$frontendOk = $true
if (-not $SkipFrontend) {
    $lintOk = Invoke-FrontendStatic
    Push-Location (Join-Path $root 'frontend')
    & npm run test:ci *> (Join-Path $logDir 'gate-frontend.log')
    $testOk = $LASTEXITCODE -eq 0
    Pop-Location
    $plain = (Get-Content (Join-Path $logDir 'gate-frontend.log') -Raw) -replace '\x1b\[[0-9;]*m', ''
    $tests = if ($plain -match '(?s).*Tests\s+(\d+) passed') { $Matches[1] } else { '?' }
    $frontend = "lint $(if ($lintOk) { 'PASS' } else { 'FAIL' }), tests $(if ($testOk) { "PASS ($tests)" } else { 'FAIL' })"
    $frontendOk = $lintOk -and $testOk
}

$summary = @"
Gate run: $(Get-Date -Format 'yyyy-MM-dd HH:mm')
Branch:   $(git -C $root rev-parse --abbrev-ref HEAD)
Unit:     $unit
IT/FT:    $integration
Coverage: $coverage
Static:   checkstyle=0, pmd=0, spotbugs=0
Backend:  $backendStatus
Frontend: $frontend
"@
Set-Content -Path (Join-Path $logDir 'gate-summary.txt') -Value $summary
Write-Host $summary
if (-not ($backendOk -and $frontendOk)) { exit 1 }
