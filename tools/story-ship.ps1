<#
.SYNOPSIS
    Ships a story: checks the last gate run, marks the tracker rows, commits, pushes, opens the pull request and
    moves Jira and GitHub to In Review.

.DESCRIPTION
    Refuses to ship when build/gate-summary.txt is missing, belongs to another branch or does not report PASS
    for the backend and the frontend (-SkipGate for documentation-only changes). The tracker row of the story
    under docs/epics becomes "In review" and its BACKLOG row "👀 In review (<sprint>)"; both are part of the
    commit. The pull request targets -Base (default develop) with the title "<KEY>: <Title>" and the body file.

.EXAMPLE
    .\tools\story-ship.ps1 SCRUM-49 -Title "Reviewer queue with claim and hand-over" `
        -Commit "feat(reviews): add the reviewer queue" -BodyFile build/pr-scrum-49.md
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidatePattern('^[A-Z]+-\d+$')]
    [string]$Key,
    [Parameter(Mandatory = $true)]
    [string]$Title,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^(feat|fix|docs|style|refactor|test|chore)\([a-z0-9-]+\): ')]
    [string]$Commit,
    [string]$CommitBody,
    [Parameter(Mandatory = $true)]
    [string]$BodyFile,
    [string]$Base = 'develop',
    [switch]$SkipGate,
    [switch]$NoTracker
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$utf8 = [Text.UTF8Encoding]::new($false)

function Set-RowCell([string]$path, [string]$key, [scriptblock]$change) {
    $lines = [IO.File]::ReadAllLines($path, $utf8)
    $hit = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\|.*\|\s*$key\s*\|") {
            $lines[$i] = & $change $lines[$i]
            $hit = $true
        }
    }
    if ($hit) { [IO.File]::WriteAllText($path, ($lines -join "`n") + "`n", $utf8) }
    return $hit
}

$branch = git branch --show-current
if ($branch -notmatch [regex]::Escape($Key)) { throw "Branch $branch does not belong to $Key" }
if (-not (Test-Path $BodyFile)) { throw "No pull request body at $BodyFile" }

if (-not $SkipGate) {
    $summary = Join-Path $root 'build/gate-summary.txt'
    if (-not (Test-Path $summary)) { throw 'No gate run found; run .\tools\gate.ps1 first.' }
    $text = Get-Content $summary -Raw
    if ($text -notmatch "Branch:\s+$([regex]::Escape($branch))") { throw 'The last gate run belongs to another branch.' }
    if ($text -notmatch 'Backend:\s+PASS' -or $text -notmatch 'Frontend:.*lint PASS, tests PASS') {
        throw "The last gate run did not pass:`n$text"
    }
}

$issue = 0
foreach ($file in Get-ChildItem docs/epics -Filter 'EPIC-*.md') {
    $changed = Set-RowCell $file.FullName $Key { param($l) $l -replace '\|\s*[^|]+\|\s*$', '| In review |' }
    if ($changed -and -not $issue) {
        $row = Select-String -Path $file.FullName -Pattern "\|\s*$Key\s*\|\s*#(\d+)" | Select-Object -First 1
        if ($row) { $issue = [int]$row.Matches[0].Groups[1].Value }
    }
}
$backlog = 'docs/project-management/BACKLOG.md'
if (Test-Path $backlog) {
    $null = Set-RowCell (Resolve-Path $backlog) $Key {
        param($l)
        $cells = $l.Trim().Trim('|').Split('|')
        $sprint = $cells[3].Trim() -replace '^\S+\s+(In review|In progress|Done)\s*\((.*)\)$', '$2'
        $cells[3] = " 👀 In review ($sprint) "
        '|' + ($cells -join '|') + '|'
    }
}

git add -A
if ($CommitBody) { git commit -q -m $Commit -m $CommitBody } else { git commit -q -m $Commit }
if ($LASTEXITCODE) { throw 'Commit failed (hook or nothing to commit).' }
git push -u origin $branch
if ($LASTEXITCODE) { throw 'Push failed; is the SSH key loaded?' }
$url = gh pr create --base $Base --head $branch --title "${Key}: $Title" --body-file $BodyFile
if ($LASTEXITCODE) { throw 'Pull request was not created.' }

if (-not $NoTracker) {
    if ($issue) {
        & "$PSScriptRoot\tracker-sync.ps1" status -Jira $Key -GitHub $issue -Status InReview
    } else {
        & "$PSScriptRoot\tracker-sync.ps1" status -Jira $Key -Status InReview
    }
}
Write-Host "PR: $url"
