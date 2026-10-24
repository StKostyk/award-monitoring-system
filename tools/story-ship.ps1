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
    part of the commit. On a branch that was never pushed, local commits since -Base (the backend commit of a
    split story) are folded into this one commit. The pull request targets -Base (default develop) with the title "<KEY>: <Title>" and the
    body file; a leading "<KEY>:" in -Title is dropped. Without a key (a chore or docs branch) no tracker row,
    Jira or GitHub item is touched and the title stays as given. A rerun after a failed push or pull request
    continues: the commit is skipped when the tree is clean and HEAD already carries -Commit, the pull request
    when one is open for the branch.

.EXAMPLE
    .\tools\story-ship.ps1 SCRUM-49 -Title "Reviewer queue with claim and hand-over" `
        -Commit "feat(reviews): add the reviewer queue" -BodyFile build/pr-scrum-49.md `
        -Changelog "Reviewer queue «На розгляді» ..."
    .\tools\story-ship.ps1 -Title "chore(tools): tooling fixes" -Commit "chore(tools): fix the story scripts" `
        -BodyFile build/pr-tools.md -SkipGate
#>
#Requires -Version 7

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidatePattern('^([A-Z]+-\d+)?$')]
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
if ($Key -and $branch -notmatch [regex]::Escape($Key)) { throw "Branch $branch does not belong to $Key" }
if ($Key) { $Title = $Title -replace "^\s*$([regex]::Escape($Key))\s*:\s*", '' }
$prTitle = if ($Key) { "${Key}: $Title" } else { $Title }
if (-not (Test-Path $BodyFile)) { throw "No pull request body at $BodyFile" }

if (-not $SkipGate) {
    $summary = Join-Path $root 'build/gate-summary.txt'
    if (-not (Test-Path $summary)) { throw 'No gate run found; run .\tools\gate.ps1 first.' }
    $text = Get-Content $summary -Raw
    if ($text -notmatch "Branch:\s+$([regex]::Escape($branch))") { throw 'The last gate run belongs to another branch.' }
    if ($text -notmatch 'Backend:\s+(PASS|SKIPPED)' -or
            $text -notmatch 'Frontend:.*lint PASS, tests PASS.*, prod build PASS') {
        throw "The last gate run did not pass:`n$text"
    }
    $backendChanged = @(git diff --name-only $Base -- backend docs/api/openapi.yml) +
        @(git status --porcelain -- backend docs/api/openapi.yml)
    if ($text -match 'Backend:\s+SKIPPED' -and ($backendChanged | Where-Object { $_ })) {
        throw 'The last gate run skipped the backend, but the branch now changes it; run .\tools\gate.ps1 again.'
    }
}

$issue = 0
if ($Key) {
    $row = Find-StoryRow $Key
    if ($row) { $issue = $row.Issue }
    $null = Set-TrackerStatus $Key 'In review'
    $null = Set-BacklogStatus $Key 'In review'
}
if ($Changelog) { Add-ChangelogLine $ChangelogSection $Changelog }

Remove-StaleGitLock
$resumed = -not (git status --porcelain) -and (git log -1 --format=%s) -eq $Commit
if ($resumed) {
    Write-Host "Commit:  already made ($Commit); continuing with push and pull request"
} else {
    git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' *> $null
    $pushed = -not $LASTEXITCODE
    $mergeBase = git merge-base HEAD $Base
    $local = @(git rev-list "$mergeBase..HEAD")
    if (-not $pushed -and $local.Count) {
        Write-Host "Squash:  $($local.Count) local commit(s) since $Base folded into the story commit"
        git reset -q --soft $mergeBase
        if ($LASTEXITCODE) { throw 'Squashing the local commits failed.' }
    }
    git add -A
    if ($CommitBody) { git commit -q -m $Commit -m $CommitBody } else { git commit -q -m $Commit }
    if ($LASTEXITCODE) { throw 'Commit failed (hook or nothing to commit).' }
}
git push -u origin $branch
if ($LASTEXITCODE) { throw 'Push failed; is the SSH key loaded?' }
if ($Base -ne 'develop') {
    git ls-remote --exit-code --heads origin $Base *> $null
    if ($LASTEXITCODE) {
        Write-Host "Base $Base no longer exists on origin; the pull request targets develop."
        $Base = 'develop'
    }
}
$url = gh pr view $branch --json url,state -q 'select(.state == "OPEN") | .url' 2>$null
if ($url) {
    Write-Host 'PR:      already open for this branch'
} else {
    $url = gh pr create --base $Base --head $branch --title $prTitle --body-file $BodyFile
    if ($LASTEXITCODE) { throw 'Pull request was not created.' }
}

if ($Key -and -not $NoTracker) {
    if ($issue) {
        Invoke-TrackerSync status -Jira $Key -GitHub $issue -Status InReview
    } else {
        Invoke-TrackerSync status -Jira $Key -Status InReview
    }
}
Write-Host "PR: $url"
