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

function Add-StoryRows([string]$key, [string]$summary, [int]$points, [string]$epic, [int]$issue, [string]$id = '',
        [string]$sprintHint = '') {
    $trackers = Get-ChildItem docs/epics -Filter 'EPIC-*.md'
    $tracker = if ($id) {
        $idFeature = [regex]::Escape(($id -replace '\.\d+$', ''))
        $trackers | Where-Object { Select-String -Path $_.FullName -Pattern "^\|[^|]+\|\s*$idFeature\.\d+\s" -Quiet } |
            Select-Object -First 1
    }
    if (-not $tracker) {
        $tracker = $trackers | Where-Object { Select-String -Path $_.FullName -Pattern "Jira epic\*\*:\s*$epic\b" -Quiet } |
            Select-Object -First 1
    }
    if (-not $tracker) { throw "No epic tracker for $epic in docs/epics" }
    $lines = [Collections.Generic.List[string]][IO.File]::ReadAllLines($tracker.FullName, $script:Utf8)
    $rowPattern = '^\|\s*(\d+[a-z]?)\s*\|\s*(\d+\.\d+)\.(\d+)\s.*\|\s*[A-Z]+-\d+\s*\|\s*#\d+\s*\|'
    $last = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $rowPattern -and $lines[$i] -notmatch '\|\s*(To do|Planned)\s*\|\s*$') { $last = $i }
    }
    if ($last -lt 0) { throw "No started story in $($tracker.Name)" }
    $null = $lines[$last] -match $rowPattern
    $previous, $feature, $number = $Matches[1], $Matches[2], [int]$Matches[3]
    $relative = [IO.Path]::GetRelativePath((Get-Location).Path, $tracker.FullName) -replace '\\', '/'
    git fetch -q origin develop 2>$null
    $rows = @($lines) + @(git show "origin/develop:$relative" 2>$null) | Where-Object { $_ -match $rowPattern } |
        ForEach-Object { [pscustomobject]@{ Order = $Matches[1]; Id = "$($Matches[2]).$($Matches[3])" } }
    $order = $previous
    do {
        $order = if ($order -match '[a-y]$') {
            $order.Substring(0, $order.Length - 1) + [char]([int][char]$order[-1] + 1)
        } else { "$($order)a" }
    } while ($order -in $rows.Order)
    if ($id) { $feature = $id -replace '\.\d+$', '' } else {
        do { $number++; $id = "$feature.$number" } while ($id -in $rows.Id)
    }
    $lines.Insert($last + 1, "| $order | $id $summary | $feature | $points | $key | #$issue | no | To do |")
    Write-Lines $tracker.FullName $lines.ToArray()

    if (-not (Test-Path $script:Backlog)) { return $id }
    $path = (Resolve-Path $script:Backlog).Path
    $backlog = [Collections.Generic.List[string]][IO.File]::ReadAllLines($path, $script:Utf8)
    $featureRow = "^\|\s*$([regex]::Escape($feature))\.\d+\s*\|"
    $after = -1
    $start = $backlog.FindIndex({ param($l) $l -match "^### Epic $($feature.Split('.')[0]) " })
    if ($start -ge 0) {
        $tableEnd = -1
        for ($i = $start + 1; $i -lt $backlog.Count -and $backlog[$i] -notmatch '^#'; $i++) {
            if ($backlog[$i] -match $featureRow) { $after = $i }
            if ($backlog[$i] -match '^\|') { $tableEnd = $i }
        }
        if ($after -lt 0) { $after = $tableEnd }
    }
    if ($after -lt 0) {
        for ($i = 0; $i -lt $backlog.Count; $i++) {
            if ($backlog[$i] -match $featureRow) { $after = $i }
        }
    }
    if ($after -lt 0) { throw "No BACKLOG row of feature $feature and no Epic table to add it to" }
    $sprint = if ($sprintHint) { $sprintHint }
        elseif ($backlog[$after] -match '\(((Sprint )?\d+)\)\s*\|') { $Matches[1] }
        elseif ($backlog[$after] -match '^\|[^|]+\|[^|]+\|[^|]+\|\s*((Sprint )?\d+)\s*\|') { $Matches[1] }
        elseif ($backlog[$after + 1] -match '^\|[^|]+\|[^|]+\|[^|]+\|\s*((Sprint )?\d+)\s*\|') { $Matches[1] }
    if (-not $sprint) { Write-Warning "BACKLOG row $id has no sprint; pass -Sprint or fill the cell" }
    $repo = gh repo view --json url -q .url
    $backlog.Insert($after + 1, "| $id | $summary | $points | $sprint | $key | [#$issue]($repo/issues/$issue) |")
    Write-Lines $path $backlog.ToArray()
    return $id
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

function Find-MergedStories {
    $inReview = @(Find-StoriesWithStatus 'In review')
    if (-not $inReview) { return }
    $merged = Invoke-WithRetry { gh pr list --state merged --limit 100 --json number,headRefName } | ConvertFrom-Json
    foreach ($story in $inReview) {
        $pr = $merged | Where-Object { $_.headRefName -like "feature/$($story.Key)-*" } | Select-Object -First 1
        if ($pr) { $story | Add-Member -PassThru Pr $pr.number | Add-Member -PassThru Branch $pr.headRefName }
    }
}

function Close-Story($story, [switch]$NoTracker) {
    if (-not $NoTracker) {
        Invoke-TrackerSync status -Jira $story.Key -GitHub $story.Issue -Status Done
    }
    $null = Set-TrackerStatus $story.Key 'Done'
    $null = Set-BacklogStatus $story.Key 'Done'
    if (git branch --list $story.Branch) {
        git branch -d $story.Branch
        if ($LASTEXITCODE) { Write-Warning "Branch $($story.Branch) kept: not merged locally" }
    }
    Write-Host "Closed:  $($story.Key) (PR #$($story.Pr) merged)"
}

function Invoke-WithRetry([scriptblock]$command) {
    $output = & $command
    if (-not $LASTEXITCODE) { return $output }
    Write-Warning 'Command failed (network or DNS?); retrying once in 10 s'
    Start-Sleep -Seconds 10
    $output = & $command
    if ($LASTEXITCODE) { throw "Command failed twice: $command" }
    return $output
}

function Get-PrdFacts([string]$key) {
    $prd = Get-PrdSection $key
    if (-not $prd) { return $null }
    $number = if ($prd.Lines[0] -match '^###\s+(\d+\.\d+\.\d+)\s') { $Matches[1] } else { '' }
    $head = [IO.File]::ReadAllLines($prd.Path, $script:Utf8) | Select-Object -First 15
    $sprint = ($head | Where-Object { $_ -match '\*\*Sprint\*\*:\s*(\d+)' } | Select-Object -First 1) -replace '.*\*\*Sprint\*\*:\s*(\d+).*', 'Sprint $1'
    return [pscustomobject]@{ Number = $number; Sprint = [string]$sprint }
}

function Set-StoryPoints([string]$key, [int]$points) {
    foreach ($file in Get-ChildItem docs/epics -Filter 'EPIC-*.md') {
        $null = Set-RowCell $file.FullName $key {
            param($l)
            $cells = $l.Trim().Trim('|').Split('|')
            $cells[3] = " $points "
            '|' + ($cells -join '|') + '|'
        }
    }
    if (Test-Path $script:Backlog) {
        $null = Set-RowCell (Resolve-Path $script:Backlog) $key {
            param($l)
            $cells = $l.Trim().Trim('|').Split('|')
            $cells[2] = " $points "
            '|' + ($cells -join '|') + '|'
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
            'In progress' { if ($sprint) { " 🔨 In progress ($sprint) " } else { ' 🔨 In progress ' } }
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
    $existing = $lines.IndexOf($bullet, $top)
    if ($existing -gt $top -and $existing -lt $next) { return }
    if ($heading -gt $top -and $heading -lt $next) {
        $lines.Insert($heading + 1, $bullet)
    } else {
        $lines.InsertRange($top + 1, [string[]]@('', "### $section", $bullet))
    }
    Write-Lines $path $lines.ToArray()
}

function Invoke-TrackerSync {
    try {
        & "$PSScriptRoot\tracker-sync.ps1" @args
    } catch {
        Write-Warning "tracker-sync failed ($($_.Exception.Message)); retrying once in 5 s"
        Start-Sleep -Seconds 5
        & "$PSScriptRoot\tracker-sync.ps1" @args
    }
}

function Remove-StaleGitLock {
    $lock = Join-Path (git rev-parse --git-dir) 'index.lock'
    if ((Test-Path $lock) -and -not (Get-Process git -ErrorAction SilentlyContinue)) {
        Remove-Item $lock -Force
        Write-Host 'Removed a stale .git/index.lock'
    }
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
