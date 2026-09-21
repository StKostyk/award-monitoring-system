<#
.SYNOPSIS
    Starts everything needed for manual testing: infrastructure containers, the backend (local profile) and the frontend dev server.

.DESCRIPTION
    Backend and frontend each open in their own PowerShell window so their logs stay visible.
    Backend:  http://localhost:8080  (login page at /login, Swagger at /swagger-ui.html)
    Frontend: http://localhost:4200
    Mailpit:  http://localhost:8025   MinIO console: http://localhost:9001

.EXAMPLE
    .\tools\dev-up.ps1
    .\tools\dev-up.ps1 -NoFrontend
#>
[CmdletBinding()]
param(
    [switch]$NoFrontend
)

$root = Split-Path -Parent $PSScriptRoot

docker compose -f (Join-Path $root 'docker-compose.yaml') up -d postgres redis mailpit minio

function Get-RedisRunId([string]$reply) {
    if ($reply -match 'run_id:([0-9a-f]+)') { return $Matches[1] }
    return ''
}
$containerRun = Get-RedisRunId (docker compose -f (Join-Path $root 'docker-compose.yaml') exec -T redis redis-cli INFO server 2>$null | Out-String)
try {
    $client = [System.Net.Sockets.TcpClient]::new('127.0.0.1', 6379)
    $stream = $client.GetStream()
    $bytes = [System.Text.Encoding]::ASCII.GetBytes("INFO server`r`nQUIT`r`n")
    $stream.Write($bytes, 0, $bytes.Length)
    Start-Sleep -Milliseconds 300
    $buffer = New-Object byte[] 4096
    $read = $stream.Read($buffer, 0, $buffer.Length)
    $hostRun = Get-RedisRunId ([System.Text.Encoding]::ASCII.GetString($buffer, 0, $read))
    $client.Close()
} catch {
    $hostRun = ''
}
if ($containerRun -and $hostRun -and $containerRun -ne $hostRun) {
    Write-Warning 'localhost:6379 is answered by a Redis that is not the compose container (another server on the loopback interface, e.g. redis-server inside WSL). The backend will use that one; stop it or lock-outs and throttles will not be visible through docker compose exec redis.'
}

if (-not (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
    Start-Process pwsh -ArgumentList '-NoExit', '-Command', "Set-Location '$root\backend'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'"
    Write-Host 'Backend starting in a new window (about a minute)...'
} else {
    Write-Host 'Backend already listening on 8080'
}

if (-not $NoFrontend) {
    if (-not (Get-NetTCPConnection -LocalPort 4200 -State Listen -ErrorAction SilentlyContinue)) {
        Start-Process pwsh -ArgumentList '-NoExit', '-Command', "Set-Location '$root\frontend'; npm start"
        Write-Host 'Frontend starting in a new window...'
    } else {
        Write-Host 'Frontend already listening on 4200'
    }
}

$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 5
    try { $health = (Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3).status } catch { $health = 'starting' }
    Write-Host "backend: $health"
} while ($health -ne 'UP' -and (Get-Date) -lt $deadline)

if ($health -eq 'UP') {
    Write-Host 'Ready: open http://localhost:4200 (sign in with employee.fmi@chnu.edu.ua / Passw0rd-demo)'
} else {
    Write-Host 'Backend did not report UP in time; check its window for errors'
}
