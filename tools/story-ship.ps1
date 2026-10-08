<#
.SYNOPSIS
    Ships a story: checks the last gate run, marks the tracker rows, commits, pushes, opens the pull request and
    moves Jira and GitHub to In Review.

.DESCRIPTION
    Refuses to ship when build/gate-summary.txt is missing, belongs to another branch or does not report PASS
    for the backend and the frontend (-SkipGate for documentation-only changes). A skipped backend counts as
    passed while the branch changes nothing under backend/ or docs/api/openapi.yml compared with -Base. The tracker row of the story
    under docs/epics becomes "In review" and its BACKLOG row "👀 In review (<sprint>)"; -Changelog adds one
    bullet at the top of the -ChangelogSection (default Added) under CHANGELOG.md [Unreleased]. All of these are
    part of the commit. The pull request targets -Base (default develop) with the title "<KEY>: <Title>" and the
    body file.

.EXAMPLE
    .\tools\story-ship.ps1 SCRUM-49 -Title "Reviewer queue with claim and hand-over" `
        -Commit "feat(reviews): add the reviewer queue" -BodyFile build/pr-scrum-49.md `
        -Changelog "Reviewer queue «На розгляді» ..."
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
    [string]$Changelog,
    [ValidateSet('Added', 'Changed', 'Fixed', 'Removed', 'Security')]
    [string]$ChangelogSection = 'Added',
    [switch]$SkipGate,
    [switch]$NoTracker
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
. "$PSScriptRoot\story-common.ps1"

$branch = git branch --show-current
if ($branch -notmatch [regex]::Escape($Key)) { throw "Branch $branch does not belong to $Key" }
if (-not (Test-Path $BodyFile)) { throw "No pull request body at $BodyFile" }

if (-not $SkipGate) {
    $summary = Join-Path $root 'build/gate-summary.txt'
    if (-not (Test-Path $summary)) { throw 'No gate run found; run .\tools\gate.ps1 first.' }
    $text = Get-Content $summary -Raw
    if ($text -notmatch "Branch:\s+$([regex]::Escape($branch))") { throw 'The last gate run belongs to another branch.' }
    if ($text -notmatch 'Backend:\s+(PASS|SKIPPED)' -or $text -notmatch 'Frontend:.*lint PASS, tests PASS') {
        throw "The last gate run did not pass:`n$text"
    }
    $backendChanged = @(git diff --name-only $Base -- backend docs/api/openapi.yml) +
        @(git status --porcelain -- backend docs/api/openapi.yml)
    if ($text -match 'Backend:\s+SKIPPED' -and ($backendChanged | Where-Object { $_ })) {
        throw 'The last gate run skipped the backend, but the branch now changes it; run .\tools\gate.ps1 again.'
    }
}

$row = Find-StoryRow $Key
$issue = if ($row) { $row.Issue } else { 0 }
$null = Set-TrackerStatus $Key 'In review'
$null = Set-BacklogStatus $Key 'In review'
if ($Changelog) { Add-ChangelogLine $ChangelogSection $Changelog }

git add -A
if ($CommitBody) { git commit -q -m $Commit -m $CommitBody } else { git commit -q -m $Commit }
if ($LASTEXITCODE) { throw 'Commit failed (hook or nothing to commit).' }
git push -u origin $branch
if ($LASTEXITCODE) { throw 'Push failed; is the SSH key loaded?' }
if ($Base -ne 'develop') {
    git ls-remote --exit-code --heads origin $Base *> $null
    if ($LASTEXITCODE) {
        Write-Host "Base $Base no longer exists on origin; the pull request targets develop."
        $Base = 'develop'
    }
}
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
