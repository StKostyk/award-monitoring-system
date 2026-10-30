<#
.SYNOPSIS
    Closes every story that is still "In review" in the epic trackers although its pull request is merged.

.DESCRIPTION
    The same step story-start.ps1 runs before a new story, for the end of a feature when no story follows:
    Jira and GitHub move to Done, the tracker row becomes "Done", the BACKLOG row "✅ Done" and the merged local
    branch is deleted. When that branch is checked out, develop is checked out and pulled first. The row edits stay
    in the working tree for the next commit.

.EXAMPLE
    .\tools\story-close.ps1
    .\tools\story-close.ps1 -DryRun
#>
#Requires -Version 7

[CmdletBinding()]
param(
    [switch]$NoTracker,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
. "$PSScriptRoot\story-common.ps1"

$closing = @(Find-MergedStories)
if (-not $closing) {
    Write-Host 'No merged story is still In review.'
    return
}
$current = git branch --show-current
if (-not $DryRun -and $current -in $closing.Branch) {
    if (git status --porcelain) { throw "$current is merged but has uncommitted changes; commit or stash first" }
    git checkout -q develop
    if ($LASTEXITCODE) { throw 'Cannot check out develop' }
    Invoke-WithRetry { git pull --ff-only -q } | Out-Null
    Write-Host "Switched from the merged $current to develop"
}
foreach ($story in $closing) {
    if ($DryRun) { Write-Host "Would close: $($story.Key) (PR #$($story.Pr) merged)" }
    else { Close-Story $story -NoTracker:$NoTracker }
}
