<#
.SYNOPSIS
    Starts work on a story: closes merged stories, creates the branch, moves the trackers and prints the
    acceptance criteria from the PRD.

.DESCRIPTION
    Finds the story row (Jira key, GitHub issue, title) in the epic trackers under docs/epics and creates
    feature/<KEY>-<slug> from the base branch after pulling it. Every tracker row still "In review" whose pull
    request (head feature/<KEY>-*) is merged is closed first: Jira and GitHub move to Done, the tracker row becomes
    "Done", the BACKLOG row "✅ Done" and the local branch is deleted; these row edits are left in the new branch.
    Then Jira and GitHub of the story move to In Progress, its tracker row is marked and the PRD section of the
    story (the "### ... (<KEY>)" heading up to the next heading) is printed.
    For an unplanned story (-Summary, -Points, -Epic, -GitHub) the tracker and BACKLOG rows are added on the new
    branch, numbered from a leading number in -Summary ("4.2.7 Stabilise ..."), the PRD heading of the story
    ("### 6.2.3 ...") or after the last started story of the epic. A number picks the tracker of its feature (also
    when -Epic names the current epic), and the row follows the last started row there (Planned and To do rows do
    not count). The BACKLOG sprint cell comes from -Sprint, the PRD's **Sprint** line or the neighbouring row; an
    empty cell is reported. A rerun on the story
    branch skips the checkout. -Carry takes uncommitted changes along to the new branch; a base without an
    upstream is not pulled, and a failed pull or GitHub query is retried once.

.EXAMPLE
    .\tools\story-start.ps1 SCRUM-49
    .\tools\story-start.ps1 SCRUM-49 -Base docs/epic-04-kickoff -Slug reviewer-queue
    .\tools\story-start.ps1 SCRUM-49 -DryRun    # prints branch, merged stories and PRD section only
    .\tools\story-start.ps1 SCRUM-61 -Summary "Refactor from the Feature 4.2 sweep" -Points 3 -Epic SCRUM-47 -GitHub 171
    .\tools\story-start.ps1 SCRUM-111 -Summary "4.2.7 Stabilise failing and flaky e2e specs" -Epic SCRUM-47 -GitHub 312 -Sprint 6
#>
#Requires -Version 7

[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidatePattern('^[A-Z]+-\d+$')]
    [string]$Key,
    [string]$Base = 'develop',
    [string]$Slug,
    [string]$Summary,
    [int]$Points = 3,
    [ValidatePattern('^[A-Z]+-\d+$')]
    [string]$Epic,
    [int]$GitHub,
    [string]$Sprint,
    [switch]$NoTracker,
    [switch]$NoClose,
    [switch]$Carry,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
. "$PSScriptRoot\story-common.ps1"

function New-Slug([string]$title) {
    $words = ($title -replace '^\d+(\.\d+)*\s+', '').ToLowerInvariant() -replace '[^a-z0-9 ]', ' ' -split '\s+' |
        Where-Object { $_ -and $_ -notin @('a', 'an', 'and', 'the', 'of', 'for', 'to', 'with') }
    return ($words | Select-Object -First 4) -join '-'
}

$row = Find-StoryRow $Key
if ($Summary -and -not ($Epic -and $GitHub)) { throw '-Summary needs -Epic and -GitHub' }
$number = ''
if ($Summary -match '^\s*(\d+\.\d+\.\d+)\s+') { $number = $Matches[1] }
$facts = if ($Summary) { Get-PrdFacts $Key }
if (-not $number -and $facts) { $number = $facts.Number }
if ($Summary) { $Summary = $Summary -replace '^\s*\d+\.\d+\.\d+\s+', '' }
if (-not ($row -or $Summary)) { throw "No tracker row for $Key in docs/epics" }
if (-not $Slug) { $Slug = New-Slug $(if ($row) { $row.Title } else { $Summary }) }
$branch = "feature/$Key-$Slug"
$closing = if ($NoClose) { @() } else { @(Find-MergedStories) }

if ($DryRun) {
    $closing | ForEach-Object { Write-Host "Would close: $($_.Key) (PR #$($_.Pr) merged)" }
    if (-not $row) { $row = [pscustomobject]@{ Title = "(new) $Summary"; Issue = $GitHub } }
} else {
    if ((git branch --show-current) -ne $branch) {
        if ((git status --porcelain) -and -not $Carry) {
            throw 'The working tree is not clean; commit or stash first, or carry the changes with -Carry.'
        }
        git checkout -q $Base
        if ($LASTEXITCODE) { throw "Cannot check out $Base" }
        git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' *> $null
        if ($LASTEXITCODE) {
            Write-Host "Base:    $Base has no upstream; not pulled"
        } else {
            Invoke-WithRetry { git pull --ff-only -q } | Out-Null
        }
        git checkout -b $branch
        if ($LASTEXITCODE) { throw "Cannot create $branch" }
    }
    $closing | ForEach-Object { Close-Story $_ -NoTracker:$NoTracker }
    if (-not $row) {
        $hint = if ($Sprint) { $Sprint } elseif ($facts) { $facts.Sprint } else { '' }
        $id = Add-StoryRows $Key $Summary $Points $Epic $GitHub $number $hint
        Write-Host "Rows:    $id added to the epic tracker and BACKLOG"
        $row = Find-StoryRow $Key
    }
}

if (-not ($NoTracker -or $DryRun)) {
    Invoke-TrackerSync status -Jira $Key -GitHub $row.Issue -Status InProgress
    $null = Set-TrackerStatus $Key 'In progress'
}

Write-Host "Branch:  $branch"
Write-Host "Story:   $($row.Title) ($Key, #$($row.Issue))"
$prd = Get-PrdSection $Key
if ($prd) {
    Write-Host "PRD:     $($prd.Path.Substring($root.Length + 1))"
    Write-Host ''
    $prd.Lines | ForEach-Object { Write-Host $_ }
} else {
    Write-Host 'PRD:     no section with this key under docs/features'
}
Write-Host ''
Write-Host 'Next:    tests first; .\tools\gate.ps1 -StaticOnly after writing code'
