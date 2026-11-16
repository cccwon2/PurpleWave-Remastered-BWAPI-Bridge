<#
.SYNOPSIS
  Reports local toolchain and game-directory preconditions for the connection probe.

.DESCRIPTION
  Does not mutate PATH, Java installs, or execution policy.
  Does not start StarCraft or the probe.
#>
[CmdletBinding()]
param(
    [string]$JavaPath = "",
    [string]$GameWorkingDirectory = "",
    [string]$LogDirectory = "logs"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Check {
    param([string]$Name, [string]$Status, [string]$Detail)
    [pscustomobject]@{
        Name   = $Name
        Status = $Status
        Detail = $Detail
    }
}

$results = @()

function Resolve-Java {
    param([string]$Preferred)
    if ($Preferred) {
        if (-not (Test-Path -LiteralPath $Preferred)) {
            throw "JavaPath does not exist: $Preferred"
        }
        return (Resolve-Path -LiteralPath $Preferred).Path
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        return $null
    }
    return $cmd.Source
}

try {
    $java = Resolve-Java -Preferred $JavaPath
    if ($null -eq $java) {
        $results += Write-Check "Java" "FAIL" "java not found on PATH; pass -JavaPath"
    }
    else {
        $versionText = & $java -version 2>&1 | Out-String
        $results += Write-Check "Java" "OK" ("path={0}; version={1}" -f $java, ($versionText -replace "\r?\n", " | "))
        if ($versionText -match "64-Bit") {
            $results += Write-Check "JavaArchHint" "INFO" "Detected 64-bit Java. JBWAPI README claims 32/64 support; PurpleWave README asks for 32-bit JDK 8. Do not assume either without local evidence."
        }
        elseif ($versionText -match "32-Bit|x86") {
            $results += Write-Check "JavaArchHint" "INFO" "Detected 32-bit-looking Java output."
        }
        else {
            $results += Write-Check "JavaArchHint" "UNKNOWN" "Could not classify 32/64 from java -version text."
        }
    }
}
catch {
    $results += Write-Check "Java" "FAIL" $_.Exception.Message
}

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($null -eq $mvn) {
    $results += Write-Check "Maven" "FAIL" "mvn not found on PATH"
}
else {
    $mvnVersion = & mvn -version 2>&1 | Select-Object -First 1 | Out-String
    $results += Write-Check "Maven" "OK" $mvnVersion.Trim()
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$jbwapi = Join-Path $repoRoot "3rdparty/jbwapi"
if (Test-Path -LiteralPath (Join-Path $jbwapi "src/main/java/bwapi/BWClient.java")) {
    $gitSha = ""
    try {
        Push-Location $jbwapi
        $gitSha = (git rev-parse HEAD 2>$null)
    }
    finally {
        Pop-Location
    }
    $results += Write-Check "JBWAPISubmodule" "OK" ("path={0}; HEAD={1}; expected=d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f" -f $jbwapi, $gitSha)
}
else {
    $results += Write-Check "JBWAPISubmodule" "FAIL" "Missing 3rdparty/jbwapi sources. Run: git submodule update --init --recursive"
}

if ($GameWorkingDirectory) {
    if (Test-Path -LiteralPath $GameWorkingDirectory) {
        $results += Write-Check "GameWorkingDirectory" "OK" $GameWorkingDirectory
        $bwapiData = Join-Path $GameWorkingDirectory "bwapi-data"
        if (Test-Path -LiteralPath $bwapiData) {
            $results += Write-Check "bwapi-data" "OK" $bwapiData
        }
        else {
            $results += Write-Check "bwapi-data" "WARN" "bwapi-data not found under game dir (PurpleWave creates AI/read/write subdirs at run time)"
        }
    }
    else {
        $results += Write-Check "GameWorkingDirectory" "FAIL" "Path not found: $GameWorkingDirectory"
    }
}
else {
    $results += Write-Check "GameWorkingDirectory" "SKIP" "Not provided"
}

if ($LogDirectory) {
    try {
        New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
        $results += Write-Check "LogDirectory" "OK" ((Resolve-Path -LiteralPath $LogDirectory).Path)
    }
    catch {
        $results += Write-Check "LogDirectory" "FAIL" $_.Exception.Message
    }
}

$os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
$results += Write-Check "OS" "INFO" $os
if ($IsLinux -or $IsMacOS) {
    $results += Write-Check "RealEnv" "NO" "JBWAPI Client uses Windows Kernel32 OpenFileMapping + named pipes. This host cannot exercise Remastered IPC."
}
elseif ($IsWindows) {
    $results += Write-Check "RealEnv" "UNKNOWN" "Windows host: Remastered + compatible BWAPI bridge still required and not verified by this script."
}
else {
    $results += Write-Check "RealEnv" "UNKNOWN" "OS family unclear to script."
}

$results | Format-Table -AutoSize | Out-String | Write-Output

$failed = @($results | Where-Object { $_.Status -eq "FAIL" })
if ($failed.Count -gt 0) {
    exit 1
}
exit 0
