<#
.SYNOPSIS
    Prepares the review of a story: the diff against the base, a review brief with the acceptance criteria and the
    reviewer that the changed paths call for.

.DESCRIPTION
    Writes build/<key>.diff (committed, staged, unstaged and new files against the merge base with -Base) and
    build/<key>-review.md (diff path, the PRD section of the story, the checklist and the finding limit). Prints
    "security-reviewer" with the paths that trigger it (auth/, authz/, config/*Security*, config/*Authorization*,
    db/migration/, @PreAuthorize, document upload and storage code) or "code-review" otherwise.
    Do not run it while gate.ps1 or e2e.ps1 is running: git leaves a stale index.lock.

.EXAMPLE
    .\tools\story-review.ps1 SCRUM-49
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidatePattern('^[A-Z]+-\d+$')]
    [string]$Key,
    [string]$Base = 'develop'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
. "$PSScriptRoot\story-common.ps1"

$name = $Key.ToLowerInvariant()
$null = New-Item -ItemType Directory -Force build
$diffPath = "build/$name.diff"
$briefPath = "build/$name-review.md"
$exclude = @(':!frontend/package-lock.json', ':!*.png', ':!*.jpg')

git add -N -- .
$diff = git diff --merge-base $Base -- . @exclude
if ($LASTEXITCODE) { throw "Cannot diff against $Base" }
[IO.File]::WriteAllText((Join-Path $root $diffPath), (($diff -join "`n") + "`n"), $Utf8)
$files = git diff --merge-base $Base --name-only -- . @exclude

$triggers = @($files | Where-Object {
    $_ -match '/(auth|authz)/|/config/[^/]*(Security|Authorization)[^/]*$|db/migration/|/document/|[Uu]pload|[Ss]torage'
})
$triggers += @(git diff --merge-base $Base -G '@PreAuthorize' --name-only -- '*.java' |
    ForEach-Object { "$_ (@PreAuthorize)" })
$reviewer = if ($triggers) { 'security-reviewer' } else { 'code-review' }

$prd = Get-PrdSection $Key
$brief = @(
    "# Review $Key",
    '',
    "Diff: $diffPath ($($files.Count) files, base $Base)",
    '',
    '## Acceptance criteria',
    ''
) + $(if ($prd) { $prd.Lines } else { '(no PRD section found)' }) + @(
    '',
    '## Checklist',
    '',
    '- OWASP A01 broken access control: every endpoint guarded, object-level checks, 404 over 403 where the PRD says so',
    '- OWASP A02 cryptographic failures: secrets, tokens, personal data in logs or responses',
    '- OWASP A03 injection: SQL/JPQL built from input, unescaped output, file names and content types',
    '- OWASP A07 identification and authentication: session, token and account state checks',
    '- Concurrency and transactions where the ACs name conflicts',
    '',
    'Report at most 8 findings of severity medium or higher, each with file:line, the failure scenario and a fix.',
    'No style findings: Checkstyle, PMD and SpotBugs cover them.'
)
[IO.File]::WriteAllText((Join-Path $root $briefPath), (($brief -join "`n") + "`n"), $Utf8)

Write-Host "Diff:     $diffPath ($($files.Count) files)"
Write-Host "Brief:    $briefPath"
Write-Host "Reviewer: $reviewer"
$triggers | Select-Object -First 10 | ForEach-Object { Write-Host "  $_" }
