<#
.SYNOPSIS
  Runs the read-only connection probe under a supervisor timeout.

.DESCRIPTION
  On connect timeout, stops only the probe Java process (not StarCraft, not other Java).
  Does not mutate global execution policy, PATH, or Java installs.
  JBWAPI BWClient has no public stop/shutdown API; the supervisor kills the probe process.
#>
[CmdletBinding()]
param(
    [string]$JavaPath = "",
    [string]$GameWorkingDirectory = "",
    [string]$LogDirectory = "logs",
    [int]$ConnectTimeoutMs = 60000,
    [int]$StatusIntervalMs = 1000,
    [string]$JarPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

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

function Write-ErrExit {
    param([int]$Code, [string]$Message)
    # Avoid Write-Error under $ErrorActionPreference=Stop (it becomes exit 1).
    [Console]::Error.WriteLine($Message)
    exit $Code
}

if ($ConnectTimeoutMs -lt 1000 -or $ConnectTimeoutMs -gt 3600000) {
    Write-ErrExit -Code 3 -Message "ConnectTimeoutMs out of range [1000, 3600000]"
}
if ($StatusIntervalMs -lt 100 -or $StatusIntervalMs -gt 60000) {
    Write-ErrExit -Code 3 -Message "StatusIntervalMs out of range [100, 60000]"
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

try {
    $java = Resolve-JavaExecutable -Preferred $JavaPath
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

$argList = @(
    "-jar", (Resolve-Path -LiteralPath $JarPath).Path,
    "--log-dir", (Resolve-Path -LiteralPath $LogDirectory).Path,
    "--connect-timeout-ms", "$ConnectTimeoutMs",
    "--status-interval-ms", "$StatusIntervalMs"
)
if ($GameWorkingDirectory) {
    $argList += @("--workdir", (Resolve-Path -LiteralPath $GameWorkingDirectory).Path)
}

Write-Host "Starting probe: $java $($argList -join ' ')"

# Supervisor timeout slightly above the probe's own watchdog so either layer can stop the probe only.
$supervisorMs = [Math]::Max($ConnectTimeoutMs + 5000, $ConnectTimeoutMs)

$stdoutLog = Join-Path $LogDirectory "probe-stdout.log"
$stderrLog = Join-Path $LogDirectory "probe-stderr.log"

$startInfo = @{
    FilePath               = $java
    ArgumentList           = $argList
    PassThru               = $true
    NoNewWindow            = $true
    RedirectStandardOutput = $stdoutLog
    RedirectStandardError  = $stderrLog
}
if ($GameWorkingDirectory) {
    $startInfo["WorkingDirectory"] = (Resolve-Path -LiteralPath $GameWorkingDirectory).Path
}

$proc = Start-Process @startInfo
$probePid = $proc.Id
Write-Host "Probe PID=$probePid (supervisor will only stop this PID on timeout)"

$exited = $proc.WaitForExit($supervisorMs)
if (-not $exited) {
    Write-Warning "Supervisor timeout reached; stopping probe PID $probePid only"
    try {
        Stop-Process -Id $probePid -Force -ErrorAction Stop
    }
    catch {
        Write-Warning "Stop-Process failed: $($_.Exception.Message)"
    }
    # Preserve timeout semantics for callers.
    Get-Content -LiteralPath $stdoutLog -ErrorAction SilentlyContinue | Write-Output
    Get-Content -LiteralPath $stderrLog -ErrorAction SilentlyContinue | Write-Error
    exit 6
}

Get-Content -LiteralPath $stdoutLog -ErrorAction SilentlyContinue | Write-Output
Get-Content -LiteralPath $stderrLog -ErrorAction SilentlyContinue | Write-Host
exit $proc.ExitCode
