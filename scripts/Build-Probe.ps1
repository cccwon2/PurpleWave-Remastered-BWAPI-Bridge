<#
.SYNOPSIS
  Builds the read-only connection probe shaded JAR.

.DESCRIPTION
  Invokes Maven from the repo root. Does not change global Java/PATH settings.
#>
[CmdletBinding()]
param(
    [string]$JavaPath = "",
    [switch]$SkipTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    function Write-ErrExit {
        param([int]$Code, [string]$Message)
        [Console]::Error.WriteLine($Message)
        exit $Code
    }

    if (-not (Test-Path -LiteralPath "3rdparty/jbwapi/src/main/java/bwapi/BWClient.java")) {
        Write-ErrExit -Code 2 -Message "JBWAPI submodule sources missing. Run: git submodule update --init --recursive"
    }

    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -eq $mvn) {
        Write-ErrExit -Code 3 -Message "mvn not found on PATH"
    }

    $envBackup = @{
        JAVA_HOME = $env:JAVA_HOME
        PATH      = $env:PATH
    }

    try {
        if ($JavaPath) {
            if (-not (Test-Path -LiteralPath $JavaPath)) {
                Write-ErrExit -Code 4 -Message "JavaPath does not exist: $JavaPath"
            }
            $javaItem = Get-Item -LiteralPath $JavaPath
            # Accept either java executable or JAVA_HOME root.
            if ($javaItem.Name -match '^java(\.exe)?$') {
                $homeCandidate = $javaItem.Directory.Parent.FullName
                $env:JAVA_HOME = $homeCandidate
                $env:PATH = $javaItem.Directory.FullName + [IO.Path]::PathSeparator + $env:PATH
            }
            else {
                $env:JAVA_HOME = $javaItem.FullName
                $env:PATH = (Join-Path $env:JAVA_HOME "bin") + [IO.Path]::PathSeparator + $env:PATH
            }
            Write-Host "Using JavaPath override for this process only: $JavaPath"
        }

        $mvnArgs = @("-q", "verify")
        if ($SkipTests) {
            $mvnArgs = @("-q", "package", "-DskipTests")
        }
        & mvn @mvnArgs
        $code = $LASTEXITCODE
        if ($code -ne 0) {
            exit $code
        }

        $jar = Join-Path $repoRoot "target/connection-probe-0.1.0.jar"
        if (-not (Test-Path -LiteralPath $jar)) {
            Write-ErrExit -Code 5 -Message "Build finished but jar missing: $jar"
        }
        Write-Host "Built $jar"
        exit 0
    }
    finally {
        $env:JAVA_HOME = $envBackup.JAVA_HOME
        $env:PATH = $envBackup.PATH
    }
}
finally {
    Pop-Location
}
