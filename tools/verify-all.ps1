<#
.SYNOPSIS
    Runs every quality gate for backend and frontend and writes a short summary.

.DESCRIPTION
    Backend:  mvn clean verify (unit, integration, functional tests, coverage, static analysis)
    Frontend: npm run lint, npm run test:ci, optional Playwright e2e (-E2E)
    Summary:  build/gate-summary.txt (build/verify.log holds the full Maven output)

.EXAMPLE
    .\tools\verify-all.ps1
    .\tools\verify-all.ps1 -SkipFrontend
    .\tools\verify-all.ps1 -E2E
#>
[CmdletBinding()]
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$E2E
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$summary = @()
$failed = $false

function Add-Line([string]$text) { $script:summary += $text; Write-Host $text }

Add-Line ("Gate run: {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm'))
Add-Line ("Branch:   {0}" -f (git -C $root rev-parse --abbrev-ref HEAD))

if (-not $SkipBackend) {
    Push-Location (Join-Path $root 'backend')
    $log = Join-Path $root 'build\verify.log'
    New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
    & .\mvnw.cmd clean verify -B 2>&1 | Tee-Object -FilePath $log | Out-Null
    $backendOk = $LASTEXITCODE -eq 0
    $content = Get-Content $log -Raw

    $totals = [regex]::Matches($content, '(?m)^\[(?:INFO|WARNING|ERROR)\] Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)\s*$')
    if ($totals.Count -ge 1) { Add-Line ("Unit:     {0} run, {1} failed" -f $totals[0].Groups[1].Value, ([int]$totals[0].Groups[2].Value + [int]$totals[0].Groups[3].Value)) }
    if ($totals.Count -ge 2) { Add-Line ("IT/FT:    {0} run, {1} failed" -f $totals[1].Groups[1].Value, ([int]$totals[1].Groups[2].Value + [int]$totals[1].Groups[3].Value)) }

    $jacoco = Join-Path $PWD 'target\site\jacoco\jacoco.csv'
    if (Test-Path $jacoco) {
        $rows = Import-Csv $jacoco
        $missed = ($rows | Measure-Object -Property LINE_MISSED -Sum).Sum
        $covered = ($rows | Measure-Object -Property LINE_COVERED -Sum).Sum
        if (($missed + $covered) -gt 0) {
            Add-Line ("Coverage: {0:P1} lines ({1}/{2})" -f ($covered / ($missed + $covered)), $covered, ($missed + $covered))
        }
    }
    $static = @()
    if ($content -match 'You have (\d+) Checkstyle violations') { $static += "checkstyle=$($Matches[1])" }
    if ($content -match 'PMD Failure') { $static += 'pmd=violations' }
    if ($content -match 'BugInstance size is (\d+)') { $static += "spotbugs=$($Matches[1])" }
    Add-Line ("Static:   {0}" -f ($(if ($static.Count) { $static -join ', ' } else { 'clean' })))
    Add-Line ("Backend:  {0}" -f ($(if ($backendOk) { 'PASS' } else { 'FAIL (see build/verify.log)' })))
    if (-not $backendOk) { $failed = $true }
    Pop-Location
}

if (-not $SkipFrontend) {
    Push-Location (Join-Path $root 'frontend')
    & npm run lint 2>&1 | Out-Null
    $lintOk = $LASTEXITCODE -eq 0
    & npm run test:ci 2>&1 | Out-Null
    $testOk = $LASTEXITCODE -eq 0
    Add-Line ("Frontend: lint {0}, tests {1}" -f ($(if ($lintOk) { 'PASS' } else { 'FAIL' })), ($(if ($testOk) { 'PASS' } else { 'FAIL' })))
    if (-not ($lintOk -and $testOk)) { $failed = $true }
    if ($E2E) {
        & npx playwright test 2>&1 | Out-Null
        $e2eOk = $LASTEXITCODE -eq 0
        Add-Line ("E2E:      {0}" -f ($(if ($e2eOk) { 'PASS' } else { 'FAIL' })))
        if (-not $e2eOk) { $failed = $true }
    }
    Pop-Location
}

$summaryPath = Join-Path $root 'build\gate-summary.txt'
New-Item -ItemType Directory -Force -Path (Split-Path $summaryPath) | Out-Null
$summary | Set-Content -Path $summaryPath -Encoding UTF8
Add-Line ("Summary written to {0}" -f $summaryPath)

if ($failed) { exit 1 } else { exit 0 }
