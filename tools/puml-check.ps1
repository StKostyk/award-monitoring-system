<#
.SYNOPSIS
    Checks the syntax of every PlantUML diagram under docs (English and Ukrainian copies).

.DESCRIPTION
    Runs plantuml.jar -checkonly over the tracked docs/*.puml files (templates excluded) in one JVM; when that
    fails, checks the files one by one and lists the broken ones with the line PlantUML reports. The jar is taken
    from PLANTUML_JAR, else from the Chocolatey package. The exit code is 1 when a diagram does not parse.

.EXAMPLE
    .\tools\puml-check.ps1
    .\tools\puml-check.ps1 -Path docs/diagrams/uml/class-diagram-domain.puml
#>
[CmdletBinding()]
param(
    [string[]]$Path
)

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $jar = $env:PLANTUML_JAR
    if (-not $jar) {
        $jar = 'C:\ProgramData\chocolatey\lib\plantuml\tools\plantuml.jar'
    }
    if (-not (Test-Path $jar)) {
        Write-Error "plantuml.jar not found at $jar; set PLANTUML_JAR"
        exit 2
    }

    $files = @(if ($Path) { $Path } else {
        git ls-files 'docs/*.puml' | Where-Object { $_ -notmatch '/templates/' }
    })
    if ($files.Count -eq 0) {
        Write-Host 'No diagrams to check.'
        exit 0
    }

    & java -jar $jar -checkonly -charset UTF-8 @files 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "$($files.Count) diagrams parse."
        exit 0
    }

    $broken = @()
    foreach ($file in $files) {
        $report = Get-Content -Raw -Encoding UTF8 $file | & java -jar $jar -syntax -charset UTF-8 2>&1
        if ($report -and ($report | Select-Object -First 1) -eq 'ERROR') {
            $line = $report | Select-Object -Skip 1 -First 1
            $broken += [pscustomobject]@{ Diagram = $file; Line = $line }
        }
    }
    Write-Host "$($broken.Count) diagrams do not parse:"
    $broken | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
    exit 1
} finally {
    Pop-Location
}
