<#
.SYNOPSIS
  Runs the read-only connection probe under a match-start / optional overall supervisor.

.DESCRIPTION
  MatchStartWaitMs limits waiting for MATCH_STARTED for THIS runId only (not IPC connect —
  public JBWAPI has no connect callback). After MATCH_STARTED, the match-start wait no longer
  kills the probe. OverallRunTimeoutMs (optional, 0=disabled) is the only post-match limit.

  On timeout, stops only the probe process this script started.

  Requires PowerShell 7+ for safe ArgumentList argv preservation (spaces / Korean paths).

.NOTES
  Do not use Windows PowerShell 5.1 for launches with spaces in paths.
#>
[CmdletBinding()]
param(
    [string]$JavaPath = "",
    [string]$GameWorkingDirectory = "",
    [string]$LogDirectory = "logs",
    [int]$MatchStartWaitMs = 60000,
    # Alias retained for callers; same meaning as MatchStartWaitMs (match-start wait, not IPC connect).
    [int]$ConnectTimeoutMs = -1,
    [int]$OverallRunTimeoutMs = 0,
    [int]$StatusIntervalMs = 1000,
    [string]$JarPath = "",
    [string]$RunId = "",
    # Test overrides: launch this executable instead of java -jar (mock probes).
    [string]$TestExecutable = "",
    # One UTF-8 argument per line (avoids array-expansion eating named params like -LogDirectory).
    [string]$TestArgumentFile = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "ProbeSupervisor.ps1")

function Write-ErrExit {
    param([int]$Code, [string]$Message)
    [Console]::Error.WriteLine($Message)
    exit $Code
}

if ($ConnectTimeoutMs -ge 0) {
    $MatchStartWaitMs = $ConnectTimeoutMs
}

if (-not (Test-ProbeHostSupported)) {
    Write-ErrExit -Code 1 -Message "Run-Probe requires PowerShell 7+ with ProcessStartInfo.ArgumentList. Current: $($PSVersionTable.PSVersion)"
}

if ($MatchStartWaitMs -lt 1000 -or $MatchStartWaitMs -gt 3600000) {
    Write-ErrExit -Code 3 -Message "MatchStartWaitMs/ConnectTimeoutMs out of range [1000, 3600000]"
}
if ($StatusIntervalMs -lt 100 -or $StatusIntervalMs -gt 60000) {
    Write-ErrExit -Code 3 -Message "StatusIntervalMs out of range [100, 60000]"
}
if ($OverallRunTimeoutMs -lt 0 -or $OverallRunTimeoutMs -gt 86400000) {
    Write-ErrExit -Code 3 -Message "OverallRunTimeoutMs out of range [0, 86400000] (0 disables)"
}
if ($OverallRunTimeoutMs -gt 0 -and $OverallRunTimeoutMs -lt $MatchStartWaitMs) {
    Write-ErrExit -Code 3 -Message "OverallRunTimeoutMs when set must be >= MatchStartWaitMs"
}

if ($GameWorkingDirectory) {
    if (-not (Test-Path -LiteralPath $GameWorkingDirectory)) {
        Write-ErrExit -Code 5 -Message "GameWorkingDirectory does not exist: $GameWorkingDirectory"
    }
}

if ((Test-Path -LiteralPath $LogDirectory) -and -not (Test-Path -LiteralPath $LogDirectory -PathType Container)) {
    Write-ErrExit -Code 4 -Message "LogDirectory exists but is not a directory: $LogDirectory"
}
try {
    New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
    if (-not (Test-Path -LiteralPath $LogDirectory -PathType Container)) {
        Write-ErrExit -Code 4 -Message "Failed to create LogDirectory (path is not a directory): $LogDirectory"
    }
}
catch {
    Write-ErrExit -Code 4 -Message ("Failed to create LogDirectory: {0}" -f $_.Exception.Message)
}

function Resolve-JavaExecutable {
    param([string]$Preferred)
    if ($Preferred) {
        if (-not (Test-Path -LiteralPath $Preferred)) {
            throw "JavaPath does not exist: $Preferred"
        }
        $item = Get-Item -LiteralPath $Preferred
        if ($item.PSIsContainer) {
            $candidate = Join-Path $item.FullName "bin/java"
            $candidateExe = Join-Path $item.FullName "bin/java.exe"
            if (Test-Path -LiteralPath $candidateExe) { return (Resolve-Path -LiteralPath $candidateExe).Path }
            if (Test-Path -LiteralPath $candidate) { return (Resolve-Path -LiteralPath $candidate).Path }
            throw "JavaPath directory has no bin/java: $Preferred"
        }
        return (Resolve-Path -LiteralPath $Preferred).Path
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        throw "java not found on PATH; pass -JavaPath"
    }
    return $cmd.Source
}

if (-not $RunId) {
    $RunId = New-ProbeRunId
}

$logDirResolved = (Resolve-Path -LiteralPath $LogDirectory).Path
$jsonlPath = Get-ProbeJsonlPath -LogDirectory $logDirResolved -RunId $RunId
$stdoutLog = Join-Path $logDirResolved ("probe-{0}-stdout.log" -f $RunId)
$stderrLog = Join-Path $logDirResolved ("probe-{0}-stderr.log" -f $RunId)

$filePath = $null
$argList = @()

if ($TestExecutable) {
    if (-not (Test-Path -LiteralPath $TestExecutable)) {
        Write-ErrExit -Code 2 -Message "TestExecutable not found: $TestExecutable"
    }
    $filePath = (Resolve-Path -LiteralPath $TestExecutable).Path
    if (-not $TestArgumentFile) {
        Write-ErrExit -Code 3 -Message "TestExecutable requires -TestArgumentFile (one argv per line)"
    }
    if (-not (Test-Path -LiteralPath $TestArgumentFile)) {
        Write-ErrExit -Code 2 -Message "TestArgumentFile not found: $TestArgumentFile"
    }
    $argList = @(Get-Content -LiteralPath $TestArgumentFile -Encoding utf8)
}
else {
    try {
        $filePath = Resolve-JavaExecutable -Preferred $JavaPath
    }
    catch {
        Write-ErrExit -Code 1 -Message $_.Exception.Message
    }
    if (-not $JarPath) {
        $JarPath = Join-Path $repoRoot "target/connection-probe-0.1.0.jar"
    }
    if (-not (Test-Path -LiteralPath $JarPath)) {
        Write-ErrExit -Code 2 -Message "Jar not found: $JarPath — run scripts/Build-Probe.ps1 first"
    }
    $jarResolved = (Resolve-Path -LiteralPath $JarPath).Path
    $argList = @(
        "-jar", $jarResolved,
        "--log-dir", $logDirResolved,
        "--match-start-wait-ms", "$MatchStartWaitMs",
        "--overall-run-timeout-ms", "$OverallRunTimeoutMs",
        "--status-interval-ms", "$StatusIntervalMs",
        "--run-id", $RunId
    )
    if ($GameWorkingDirectory) {
        $argList += @("--workdir", (Resolve-Path -LiteralPath $GameWorkingDirectory).Path)
    }
}

Write-Host ("Starting probe runId={0} exe={1} matchStartWaitMs={2} overallRunTimeoutMs={3}" -f `
        $RunId, $filePath, $MatchStartWaitMs, $OverallRunTimeoutMs)
Write-Host ("JSONL={0}" -f $jsonlPath)

$workDir = ""
if ($GameWorkingDirectory) {
    $workDir = (Resolve-Path -LiteralPath $GameWorkingDirectory).Path
}

try {
    $handle = Start-SafeProcess -FilePath $filePath -ArgumentList $argList `
        -WorkingDirectory $workDir `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog
}
catch {
    Write-ErrExit -Code 1 -Message ("Process start failure: {0}" -f $_.Exception.Message)
}

Write-Host ("Probe PID={0} (supervisor stops only this PID on timeout)" -f $handle.Pid)

$result = Wait-SupervisedProbe -Handle $handle -JsonlPath $jsonlPath -RunId $RunId `
    -MatchStartWaitMs $MatchStartWaitMs -OverallRunTimeoutMs $OverallRunTimeoutMs

Write-StreamToHost -Path $stdoutLog -Stream stdout
Write-StreamToHost -Path $stderrLog -Stream stderr

if ($result.KilledForMatchWait) {
    [Console]::Error.WriteLine("Match-start wait exceeded; stopped probe PID $($result.Pid) only")
    exit 6
}
if ($result.KilledForOverall) {
    [Console]::Error.WriteLine("Overall run timeout; stopped probe PID $($result.Pid) only")
    exit 6
}

if ($null -eq $result.ExitCode) {
    Write-ErrExit -Code 1 -Message "Probe process ended without an exit code"
}
exit [int]$result.ExitCode
