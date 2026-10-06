<#
.SYNOPSIS
    Helpers shared by story-start.ps1, story-ship.ps1 and story-review.ps1 (dot-sourced, not run directly).
#>

$script:Utf8 = [Text.UTF8Encoding]::new($false)
$script:Backlog = 'docs/project-management/BACKLOG.md'

function Write-Lines([string]$path, [string[]]$lines) {
    $eol = if ([IO.File]::ReadAllText($path, $script:Utf8).Contains("`r`n")) { "`r`n" } else { "`n" }
    [IO.File]::WriteAllText($path, ($lines -join $eol) + $eol, $script:Utf8)
}

function Set-RowCell([string]$path, [string]$key, [scriptblock]$change) {
    $lines = [IO.File]::ReadAllLines($path, $script:Utf8)
    $hit = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\|.*\|\s*$key\s*\|") {
            $lines[$i] = & $change $lines[$i]
            $hit = $true
        }
    }
    if ($hit) { Write-Lines $path $lines }
    return $hit
}

function Find-StoryRow([string]$key) {
    foreach ($file in Get-ChildItem docs/epics -Filter 'EPIC-*.md') {
        foreach ($line in [IO.File]::ReadAllLines($file.FullName, $script:Utf8)) {
            if ($line -match "^\|.*\|\s*$key\s*\|\s*#(\d+)\s*\|") {
                $cells = $line.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
                return [pscustomobject]@{
                    File = $file.FullName; Line = $line; Title = $cells[1]; Issue = [int]$Matches[1]; Status = $cells[-1]
                }
            }
        }
    }
    return $null
}

function Find-StoriesWithStatus([string]$status) {
    foreach ($file in Get-ChildItem docs/epics -Filter 'EPIC-*.md') {
        foreach ($line in [IO.File]::ReadAllLines($file.FullName, $script:Utf8)) {
            if ($line -match "^\|.*\|\s*([A-Z]+-\d+)\s*\|\s*#(\d+)\s*\|.*\|\s*$status\s*\|\s*$") {
                [pscustomobject]@{ Key = $Matches[1]; Issue = [int]$Matches[2] }
            }
        }
    }
}

function Set-TrackerStatus([string]$key, [string]$status) {
    $hit = $false
    foreach ($file in Get-ChildItem docs/epics -Filter 'EPIC-*.md') {
        if (Set-RowCell $file.FullName $key { param($l) $l -replace '\|\s*[^|]+\|\s*$', "| $status |" }) { $hit = $true }
    }
    return $hit
}

function Set-BacklogStatus([string]$key, [string]$status) {
    if (-not (Test-Path $script:Backlog)) { return $false }
    return Set-RowCell (Resolve-Path $script:Backlog) $key {
        param($l)
        $cells = $l.Trim().Trim('|').Split('|')
        $sprint = $cells[3].Trim() -replace '^\S+\s+(In review|In progress|Done)\s*\((.*)\)$', '$2' `
            -replace '^\S+\s+(In review|In progress|Done)$', ''
        $cells[3] = switch ($status) {
            'Done' { ' ✅ Done ' }
            'In review' { if ($sprint) { " 👀 In review ($sprint) " } else { ' 👀 In review ' } }
            default { " $sprint " }
        }
        '|' + ($cells -join '|') + '|'
    }
}

function Add-ChangelogLine([string]$section, [string]$text) {
    $path = (Resolve-Path 'CHANGELOG.md').Path
    $lines = [Collections.Generic.List[string]][IO.File]::ReadAllLines($path, $script:Utf8)
    $top = $lines.IndexOf('## [Unreleased]')
    if ($top -lt 0) { throw 'CHANGELOG.md has no [Unreleased] section' }
    $next = $top + 1
    while ($next -lt $lines.Count -and $lines[$next] -notmatch '^## ') { $next++ }
    $heading = $lines.IndexOf("### $section", $top)
    $bullet = '- ' + ($text -replace '^-\s*', '')
    if ($heading -gt $top -and $heading -lt $next) {
        $lines.Insert($heading + 1, $bullet)
    } else {
        $lines.InsertRange($top + 1, [string[]]@('', "### $section", $bullet))
    }
    Write-Lines $path $lines.ToArray()
}

function Get-PrdSection([string]$key) {
    $hit = Get-ChildItem docs/features -Recurse -Filter '*.md' |
        Select-String -Pattern "^###\s.*\($key\)" -List | Select-Object -First 1
    if (-not $hit) { return $null }
    $lines = [IO.File]::ReadAllLines($hit.Path, $script:Utf8)
    $start = $hit.LineNumber - 1
    $end = $start + 1
    while ($end -lt $lines.Count -and $lines[$end] -notmatch '^#{1,3}\s') { $end++ }
    return [pscustomobject]@{ Path = $hit.Path; Lines = $lines[$start..($end - 1)] }
}
