<#
.SYNOPSIS
    Keeps Jira and GitHub issues for a story in step.

.DESCRIPTION
    Actions:
      status       move a story: -Jira SCRUM-nn and/or -GitHub nn, -Status Ready|InProgress|InReview|Done
      create-epic  create a Jira epic: -Summary
      create-story create a Jira story: -Summary, -Points, -Epic SCRUM-nn, optional -GitHub nn (link added to the description)
      set-epic     attach a story to an epic: -Jira SCRUM-nn -Epic SCRUM-nn
      fields       print the Jira field ids used (story points, epic link)

    Jira uses the REST API with an API token:
      JIRA_SITE   e.g. awardmonitoring1.atlassian.net
      JIRA_EMAIL  Atlassian account email
      JIRA_TOKEN  API token from id.atlassian.com
    GitHub uses the gh CLI (already authenticated).

.EXAMPLE
    .\tools\tracker-sync.ps1 status -Jira SCRUM-12 -GitHub 42 -Status InProgress
    .\tools\tracker-sync.ps1 create-story -Summary "1.1.0 User domain entities and auth schema" -Points 3 -Epic SCRUM-1 -GitHub 42
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet('status', 'create-epic', 'create-story', 'set-epic', 'fields')]
    [string]$Action,
    [string]$Jira,
    [int]$GitHub,
    [ValidateSet('Ready', 'InProgress', 'InReview', 'Done')]
    [string]$Status,
    [string]$Summary,
    [int]$Points,
    [string]$Epic,
    [string]$Project = 'SCRUM',
    [string]$Repo = 'StKostyk/award-monitoring-system'
)

$ErrorActionPreference = 'Stop'

$site = $env:JIRA_SITE
if (-not $site) { $site = 'awardmonitoring1.atlassian.net' }
$base = "https://$site/rest/api/3"

function Jira-Headers {
    if (-not $env:JIRA_EMAIL -or -not $env:JIRA_TOKEN) { throw 'JIRA_EMAIL and JIRA_TOKEN must be set' }
    $pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$($env:JIRA_EMAIL):$($env:JIRA_TOKEN)"))
    return @{ Authorization = "Basic $pair"; 'Content-Type' = 'application/json'; Accept = 'application/json' }
}

function Jira-Get([string]$path) { Invoke-RestMethod -Uri "$base$path" -Headers (Jira-Headers) -Method Get }
function Jira-Post([string]$path, $body) {
    Invoke-RestMethod -Uri "$base$path" -Headers (Jira-Headers) -Method Post -Body ($body | ConvertTo-Json -Depth 12)
}
function Jira-Put([string]$path, $body) {
    Invoke-RestMethod -Uri "$base$path" -Headers (Jira-Headers) -Method Put -Body ($body | ConvertTo-Json -Depth 12)
}

function Adf([string]$text) {
    return @{ type = 'doc'; version = 1; content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = $text }) }) }
}

function Story-Points-Field {
    $fields = Jira-Get '/field'
    $f = $fields | Where-Object { $_.name -in @('Story point estimate', 'Story Points') } | Select-Object -First 1
    if (-not $f) { throw 'No story points field found' }
    return $f.id
}

$jiraStatus = @{ Ready = 'To Do'; InProgress = 'In Progress'; InReview = 'In Review'; Done = 'Done' }
$ghStatus = @{ Ready = 'Status: Ready'; InProgress = 'Status: In Progress'; InReview = 'Status: Needs Review'; Done = 'Status: Completed' }

switch ($Action) {
    'fields' {
        Write-Host ("story points: {0}" -f (Story-Points-Field))
    }
    'status' {
        if (-not $Status) { throw '-Status is required' }
        if ($Jira) {
            $target = $jiraStatus[$Status]
            $t = (Jira-Get "/issue/$Jira/transitions").transitions | Where-Object { $_.to.name -eq $target } | Select-Object -First 1
            if (-not $t) { throw "No transition to '$target' from the current status of $Jira" }
            Jira-Post "/issue/$Jira/transitions" @{ transition = @{ id = $t.id } } | Out-Null
            Write-Host "$Jira -> $target"
        }
        if ($GitHub) {
            $remove = ($ghStatus.Values | Where-Object { $_ -ne $ghStatus[$Status] }) -join ','
            & gh issue edit $GitHub --repo $Repo --add-label $ghStatus[$Status] --remove-label $remove | Out-Null
            if ($Status -eq 'Done') { & gh issue close $GitHub --repo $Repo | Out-Null }
            Write-Host "#$GitHub -> $($ghStatus[$Status])"
        }
    }
    'set-epic' {
        if (-not $Jira -or -not $Epic) { throw '-Jira and -Epic are required' }
        Jira-Put "/issue/$Jira" @{ fields = @{ parent = @{ key = $Epic } } } | Out-Null
        Write-Host "$Jira -> $Epic"
    }
    'create-epic' {
        if (-not $Summary) { throw '-Summary is required' }
        $r = Jira-Post '/issue' @{ fields = @{ project = @{ key = $Project }; issuetype = @{ name = 'Epic' }; summary = $Summary } }
        Write-Output $r.key
    }
    'create-story' {
        if (-not $Summary) { throw '-Summary is required' }
        $fields = @{ project = @{ key = $Project }; issuetype = @{ name = 'Story' }; summary = $Summary }
        if ($Epic) { $fields.parent = @{ key = $Epic } }
        if ($Points) { $fields[(Story-Points-Field)] = $Points }
        if ($GitHub) { $fields.description = Adf "GitHub: https://github.com/$Repo/issues/$GitHub" }
        $r = Jira-Post '/issue' @{ fields = $fields }
        Write-Output $r.key
    }
}
