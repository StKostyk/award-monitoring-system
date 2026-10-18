<#
.SYNOPSIS
    Lists English documents that changed after their Ukrainian copy in docs/ua was last updated.

.DESCRIPTION
    Every file under docs/ua (*_ua.md, *-ua.md and the diagrams) mirrors an English document under docs/. For
    each pair, the English file in the working tree is compared with the commit that last touched the Ukrainian
    copy; any change since then, committed or not, is drift. With -Missing, English documents in mirrored folders that have no Ukrainian copy are listed as
    well. The exit code is 1 when drift is found, so the check can gate an epic close.
    docs/ua/drift-baseline.txt holds "<English path> <commit>" lines: a change I reviewed and decided needs no
    translation (wording, a table row the copy leaves out). Drift is then counted from that commit when it is
    newer than the copy. -Accept <English path> records the current commit for that document.

.EXAMPLE
    .\tools\ua-drift.ps1
    .\tools\ua-drift.ps1 -Missing
    .\tools\ua-drift.ps1 -Accept docs/security/AUTHENTICATION_AUTHORIZATION.md
#>
[CmdletBinding()]
param(
    [switch]$Missing,
    [string[]]$Accept
)

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $baselineFile = 'docs/ua/drift-baseline.txt'
    $baseline = @{}
    if (Test-Path $baselineFile) {
        Get-Content $baselineFile | Where-Object { $_ -match '^\S+\s+[0-9a-f]+$' } |
            ForEach-Object { $parts = $_ -split '\s+'; $baseline[$parts[0]] = $parts[1] }
    }
    if ($Accept) {
        $head = git rev-parse --short HEAD
        foreach ($path in $Accept) { $baseline[($path -replace '\\', '/')] = $head }
        $baseline.Keys | Sort-Object | ForEach-Object { "$_ $($baseline[$_])" } | Set-Content $baselineFile
        Write-Host "Accepted at ${head}: $($Accept -join ', ')"
    }
    $uaFiles = git ls-files docs/ua | Where-Object {
        ($_ -match '[_-]ua\.md$' -or $_ -match '\.puml$') -and $_ -notmatch '/templates/'
    }
    $drift = @()
    $mirrored = @{}
    foreach ($ua in $uaFiles) {
        $en = ($ua -replace '^docs/ua/', 'docs/') -replace '[_-]ua\.md$', '.md'
        $mirrored[$en] = $true
        if (-not (Test-Path $en)) {
            continue
        }
        $base = git log -1 --format=%h -- $ua
        if ($baseline.ContainsKey($en)) {
            git merge-base --is-ancestor $base $baseline[$en] 2>$null
            if ($LASTEXITCODE -eq 0) { $base = $baseline[$en] }
        }
        $stat = git diff --shortstat $base -- $en
        if ($stat) {
            $drift += [pscustomobject]@{
                English = $en
                Ukrainian = $ua
                Since = $base
                Change = ($stat.Trim() -replace '^1 file changed, ', '')
            }
        }
    }

    if ($drift.Count -eq 0) {
        Write-Host 'No drift: every Ukrainian copy is newer than its English source.'
    } else {
        Write-Host "$($drift.Count) Ukrainian copies are behind their English source:"
        $drift | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
        Write-Host 'Show one: git diff <Since> -- <English>'
    }

    if ($Missing) {
        $folders = $uaFiles | ForEach-Object { Split-Path -Parent ($_ -replace '^docs/ua/', 'docs/') } |
            ForEach-Object { $_ -replace '\\', '/' } | Sort-Object -Unique
        $absent = git ls-files docs | Where-Object {
            $_ -notmatch '^docs/ua/' -and ($_ -match '\.md$' -or $_ -match '\.puml$') -and
            -not $mirrored.ContainsKey($_) -and ((Split-Path -Parent $_) -replace '\\', '/') -in $folders
        }
        if ($absent) {
            Write-Host 'English documents in mirrored folders without a Ukrainian copy:'
            $absent | ForEach-Object { Write-Host "  $_" }
        }
    }

    if ($drift.Count -gt 0) {
        exit 1
    }
} finally {
    Pop-Location
}
