<#
.SYNOPSIS
    Runs the quality gates and writes build/gate-summary.txt.

.DESCRIPTION
    Static analysis first (about half a minute), then the full build with tests and coverage, so a style
    violation never costs a five-minute run. -StaticOnly stops after the static checks; -SkipFrontend leaves
    the Angular lint and unit tests out.

.EXAMPLE
    .\tools\gate.ps1 -StaticOnly
    .\tools\gate.ps1
#>
[CmdletBinding()]
param(
    [switch]$StaticOnly,
    [switch]$SkipFrontend
)

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

if (-not (Invoke-Maven @('-o', '-q', 'checkstyle:check', 'pmd:check', 'spotbugs:check') 'static')) {
    Select-String -Path $log -Pattern '^\[WARN\].*\.java|violation|BugInstance' | Select-Object -First 20 |
        ForEach-Object { $_.Line }
    Write-Host 'Fix the violations above, then run the gate again.'
    exit 1
}
if ($StaticOnly) { exit 0 }

$backendOk = Invoke-Maven @('verify') 'verify'
if (-not $backendOk) {
    Select-String -Path $log -Pattern '^\[ERROR\]\s{3}|Tests run:.*(Failures: [1-9]|Errors: [1-9])' |
        Select-Object -First 20 | ForEach-Object { $_.Line }
}

$counts = Select-String -Path $log -Pattern '^\[INFO\] Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)$' |
    ForEach-Object { $_.Matches[0].Groups }
$unit = if ($counts.Count -ge 1) { $counts[0][1].Value } else { '?' }
$integration = if ($counts.Count -ge 2) { $counts[1][1].Value } else { '0' }

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

$frontend = 'skipped'
if (-not $SkipFrontend) {
    Push-Location (Join-Path $root 'frontend')
    & npm run lint *> $log
    $lintOk = $LASTEXITCODE -eq 0
    & npm run test:ci *> (Join-Path $logDir 'gate-frontend.log')
    $testOk = $LASTEXITCODE -eq 0
    Pop-Location
    $tests = (Select-String -Path (Join-Path $logDir 'gate-frontend.log') -Pattern 'Tests\s+(\d+) passed' |
        Select-Object -Last 1).Matches.Groups[1].Value
    $frontend = "lint $(if ($lintOk) { 'PASS' } else { 'FAIL' }), tests $(if ($testOk) { "PASS ($tests)" } else { 'FAIL' })"
}

$summary = @"
Gate run: $(Get-Date -Format 'yyyy-MM-dd HH:mm')
Branch:   $(git -C $root rev-parse --abbrev-ref HEAD)
Unit:     $unit run
IT/FT:    $integration run
Coverage: $coverage
Static:   checkstyle=0, pmd=0, spotbugs=0
Backend:  $(if ($backendOk) { 'PASS' } else { 'FAIL' })
Frontend: $frontend
"@
Set-Content -Path (Join-Path $logDir 'gate-summary.txt') -Value $summary
Write-Host $summary
if (-not $backendOk) { exit 1 }
