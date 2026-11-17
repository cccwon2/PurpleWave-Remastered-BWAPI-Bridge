#Requires -Version 7.0
<#
.SYNOPSIS
  Launch PurpleWave against Pluto-Remastered-v2 JBWAPI server mode (exclusive AI control).

.DESCRIPTION
  Does NOT start StarCraft. Operator must already have SCR + bridge running with
  -JbwapiServer (Pluto AIModule not loaded). Working directory should be the SCR
  x86 folder so BWAPI data paths resolve.

  JVM bitness: PurpleWave upstream documents 32-bit JDK 8. Probe A/B used 64-bit
  JRE successfully for read-only connect — for full AI, prefer 32-bit JDK 8 first;
  if only 64-bit is available, try it and record the result (do not invent PASS).

.PARAMETER JavaPath
  Absolute path to java.exe (prefer 32-bit JDK/JRE 8).

.PARAMETER JarPath
  PurpleWave fat JAR (default: target/PurpleWave-1.0.jar under repo root).

.PARAMETER GameWorkingDirectory
  SCR x86 folder (StarCraft.exe directory).

.PARAMETER ServerProcessId
  Optional SCR PID to pin via -Dbwapi.serverProcessId (avoids wrong game table row).

.PARAMETER Xmx
  Max heap (default 1024m).
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$JavaPath,
  [string]$JarPath = '',
  [Parameter(Mandatory = $true)][string]$GameWorkingDirectory,
  [int]$ServerProcessId = 0,
  [string]$Xmx = '1024m',
  [string]$Xms = '1024m',
  [switch]$DebugPurple
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $JarPath) {
  $JarPath = Join-Path $repoRoot 'target\PurpleWave-1.0.jar'
}
if (-not (Test-Path -LiteralPath $JavaPath)) { throw "Java not found: $JavaPath" }
if (-not (Test-Path -LiteralPath $JarPath)) { throw "JAR not found: $JarPath (run mvn package first)" }
if (-not (Test-Path -LiteralPath $GameWorkingDirectory)) {
  throw "Game working directory missing: $GameWorkingDirectory"
}

# Create standard BWAPI bot data dirs under the SCR workdir (non-destructive mkdir).
foreach ($rel in @('bwapi-data\AI', 'bwapi-data\read', 'bwapi-data\write')) {
  $dir = Join-Path $GameWorkingDirectory $rel
  if (-not (Test-Path -LiteralPath $dir)) {
    New-Item -ItemType Directory -Path $dir | Out-Null
  }
}

$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $JavaPath
$psi.WorkingDirectory = $GameWorkingDirectory
$psi.UseShellExecute = $false
$null = $psi.ArgumentList.Add("-Xms$Xms")
$null = $psi.ArgumentList.Add("-Xmx$Xmx")
# Sync JBWAPI like ConnectionProbe — avoids async GameData tears (isReplay/self null).
$null = $psi.ArgumentList.Add('-Dpurplewave.jbwapi.sync=true')
if ($DebugPurple) { $null = $psi.ArgumentList.Add('-Dpurpledebug=true') }
if ($ServerProcessId -gt 0) {
  $null = $psi.ArgumentList.Add("-Dbwapi.serverProcessId=$ServerProcessId")
}
$null = $psi.ArgumentList.Add('-jar')
$null = $psi.ArgumentList.Add($JarPath)

$jarHash = (Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Starting PurpleWave Remaster client"
Write-Host "  java: $JavaPath"
Write-Host "  jar:  $JarPath"
Write-Host "  jar_sha256: $jarHash"
Write-Host "  cwd:  $GameWorkingDirectory"
if ($ServerProcessId -gt 0) { Write-Host "  pin:  bwapi.serverProcessId=$ServerProcessId" }
Write-Host "  Keep this jar_sha256 for retests; do not swap the validated JAR mid-triage."

$proc = [System.Diagnostics.Process]::Start($psi)
Write-Host "PurpleWave PID $($proc.Id)"
exit 0
