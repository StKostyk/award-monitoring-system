<#
.SYNOPSIS
    Lists English documents that changed after their Ukrainian copy in docs/ua was last updated.

.DESCRIPTION
    Every file under docs/ua (*_ua.md, *-ua.md and the diagrams) mirrors an English document under docs/. For
    each pair, the English file in the working tree is compared with the commit that last touched the Ukrainian
    copy; any change since then, committed or not, is drift. With -Missing, English documents in mirrored folders that have no Ukrainian copy are listed as
    well. The exit code is 1 when drift is found, so the check can gate an epic close.

.EXAMPLE
    .\tools\ua-drift.ps1
    .\tools\ua-drift.ps1 -Missing
#>
[CmdletBinding()]
param(
    [switch]$Missing
)

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
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
