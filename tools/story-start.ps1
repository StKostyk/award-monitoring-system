<#
.SYNOPSIS
    Starts work on a story: branch, tracker status and the acceptance criteria from the PRD.

.DESCRIPTION
    Finds the story row (Jira key, GitHub issue, title) in the epic trackers under docs/epics, creates
    feature/<KEY>-<slug> from the base branch after pulling it, moves Jira and GitHub to In Progress, marks the
    tracker row and prints the PRD section of the story (the "### ... (<KEY>)" heading up to the next heading).

.EXAMPLE
    .\tools\story-start.ps1 SCRUM-49
    .\tools\story-start.ps1 SCRUM-49 -Base docs/epic-04-kickoff -Slug reviewer-queue
    .\tools\story-start.ps1 SCRUM-49 -DryRun    # prints branch, story and PRD section only
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidatePattern('^[A-Z]+-\d+$')]
    [string]$Key,
    [string]$Base = 'develop',
    [string]$Slug,
    [switch]$NoTracker,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

function Find-StoryRow([string]$key) {
    foreach ($file in Get-ChildItem docs/epics -Filter 'EPIC-*.md') {
        foreach ($line in Get-Content $file.FullName -Encoding UTF8) {
            if ($line -match "^\|.*\|\s*$key\s*\|\s*#(\d+)\s*\|") {
                $cells = $line.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
                return [pscustomobject]@{ File = $file.FullName; Line = $line; Title = $cells[1]; Issue = [int]$Matches[1] }
            }
        }
    }
    throw "No tracker row for $key in docs/epics"
}

function New-Slug([string]$title) {
    $words = ($title -replace '^\d+(\.\d+)*\s+', '').ToLowerInvariant() -replace '[^a-z0-9 ]', ' ' -split '\s+' |
        Where-Object { $_ -and $_ -notin @('a', 'an', 'and', 'the', 'of', 'for', 'to', 'with') }
    return ($words | Select-Object -First 4) -join '-'
}

$row = Find-StoryRow $Key
if (-not $Slug) { $Slug = New-Slug $row.Title }
$branch = "feature/$Key-$Slug"

if (-not $DryRun) {
    if (git status --porcelain) { throw 'The working tree is not clean; commit or stash first.' }
    git checkout $Base
    if ($LASTEXITCODE) { throw "Cannot check out $Base" }
    git pull --ff-only
    if ($LASTEXITCODE) { throw "Cannot update $Base" }
    git checkout -b $branch
    if ($LASTEXITCODE) { throw "Cannot create $branch" }
}

if (-not ($NoTracker -or $DryRun)) {
    & "$PSScriptRoot\tracker-sync.ps1" status -Jira $Key -GitHub $row.Issue -Status InProgress
    $text = Get-Content $row.File -Raw -Encoding UTF8
    $updated = $text.Replace($row.Line, ($row.Line -replace '\|\s*[^|]+\|\s*$', '| In progress |'))
    [IO.File]::WriteAllText($row.File, $updated, [Text.UTF8Encoding]::new($false))
}

Write-Host "Branch:  $branch"
Write-Host "Story:   $($row.Title) ($Key, #$($row.Issue))"
$prd = Get-ChildItem docs/features -Recurse -Filter '*.md' | Select-String -Pattern "^###\s.*\($Key\)" -List | Select-Object -First 1
if ($prd) {
    Write-Host "PRD:     $($prd.Path.Substring($root.Length + 1))"
    Write-Host ''
    $lines = Get-Content $prd.Path -Encoding UTF8
    $start = $prd.LineNumber - 1
    $end = $start + 1
    while ($end -lt $lines.Count -and $lines[$end] -notmatch '^#{1,3}\s') { $end++ }
    $lines[$start..($end - 1)] | ForEach-Object { Write-Host $_ }
} else {
    Write-Host 'PRD:     no section with this key under docs/features'
}
