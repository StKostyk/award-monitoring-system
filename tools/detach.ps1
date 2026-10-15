<#
.SYNOPSIS
    Runs a long tool script (gate, e2e) as a detached PowerShell process and returns at once.

.DESCRIPTION
    The script runs in its own hidden pwsh process, outside the shell that started it, so it keeps running when
    that shell ends or is stopped (for example by a memory-pressure clean-up of background shells). Its output goes
    to build/<Name>-run.log; when it finishes, build/<Name>.exit holds its exit code. A run of the same name that
    is still going is refused. Wait for the exit file, then read the log or build/gate-summary.txt.

.PARAMETER Name
    Short name of the run (gate, e2e); names the log and the exit file.

.PARAMETER Script
    Path of the script to run, relative to the repository root.

.PARAMETER Arguments
    Arguments passed on to the script.

.EXAMPLE
    .\tools\detach.ps1 gate tools\gate.ps1
    .\tools\detach.ps1 e2e tools\e2e.ps1 -Grep 'notifications'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)][string]$Name,
    [Parameter(Mandatory, Position = 1)][string]$Script,
    [Parameter(Position = 2, ValueFromRemainingArguments)][string[]]$Arguments
)

$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build'
New-Item -ItemType Directory -Force $build | Out-Null
$runLog = Join-Path $build "$Name-run.log"
$exitFile = Join-Path $build "$Name.exit"
$pidFile = Join-Path $build "$Name.pid"

if (Test-Path $pidFile) {
    $previous = Get-Process -Id ([int](Get-Content $pidFile -Raw)) -ErrorAction SilentlyContinue
    if ($previous -and $previous.ProcessName -match '^pwsh') {
        Write-Host "A detached $Name run is still going (PID $($previous.Id)); wait for $exitFile."
        exit 1
    }
}
Remove-Item $exitFile, $runLog -ErrorAction SilentlyContinue

$target = Join-Path $root $Script
$quoted = ($Arguments | ForEach-Object { "'" + ($_ -replace "'", "''") + "'" }) -join ' '
$command = "Set-Location '$root'; `$code = 1; try { & '$target' $quoted *> '$runLog'; " +
    "`$code = if (`$null -eq `$LASTEXITCODE) { 0 } else { `$LASTEXITCODE } } " +
    "finally { Set-Content -Path '$exitFile' -Value `$code }"
$process = Start-Process -FilePath 'pwsh' -ArgumentList '-NoProfile', '-NonInteractive', '-Command', $command `
    -WindowStyle Hidden -PassThru
Set-Content -Path $pidFile -Value $process.Id
Write-Host "Started $Name detached (PID $($process.Id)); log $runLog, exit code in $exitFile when done."
