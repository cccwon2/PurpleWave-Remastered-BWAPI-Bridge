<#
.SYNOPSIS
  MOCK probe child for supervisor regression tests (no real bridge / no MATCH from IPC).
  Writes run-scoped JSONL. Never claim this proves Remastered compatibility.
#>
param(
    [Parameter(Mandatory = $true)][string]$MockLogDirectory,
    [Parameter(Mandatory = $true)][string]$MockRunId,
    [int]$MatchStartAfterMs = -1,
    [int]$HoldMs = 8000,
    [int]$ExitCode = 0,
    [switch]$WriteStderrNoise
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $MockLogDirectory | Out-Null
$jsonl = Join-Path $MockLogDirectory ("connection-probe-{0}.jsonl" -f $MockRunId)
$start = [Environment]::TickCount64

function Write-Jsonl {
    param([string]$Event, [string]$Level = "INFO", [string]$Message = "", [string]$ErrorCode = $null)
    $elapsed = [int]([Environment]::TickCount64 - $start)
    $err = if ($null -eq $ErrorCode) { "null" } else { '"' + $ErrorCode + '"' }
    $msg = $Message.Replace('\', '\\').Replace('"', '\"')
    $line = '{"timestamp":"' + (Get-Date).ToUniversalTime().ToString("o") +
        '","level":"' + $Level +
        '","event":"' + $Event +
        '","runId":"' + $MockRunId +
        '","elapsedMs":' + $elapsed +
        ',"frame":null,"playerId":null,"race":null,"minerals":null,"gas":null,"supplyUsed":null,"supplyTotal":null,"selfUnitCount":null,"errorCode":' +
        $err + ',"message":"' + $msg + '"}'
    Add-Content -LiteralPath $jsonl -Value $line -Encoding utf8
    [Console]::Out.WriteLine($line)
}

Write-Jsonl -Event "PROCESS_STARTED" -Message "MOCK probe started"
Write-Jsonl -Event "WAITING_FOR_BRIDGE" -Message "MOCK waiting (not real IPC)"

if ($WriteStderrNoise) {
    [Console]::Error.WriteLine("MOCK stderr noise before timeout")
}

$matchWritten = $false
while ($true) {
    $elapsed = [int]([Environment]::TickCount64 - $start)
    if (-not $matchWritten -and $MatchStartAfterMs -ge 0 -and $elapsed -ge $MatchStartAfterMs) {
        Write-Jsonl -Event "MATCH_STARTED" -Message "MOCK match started (synthetic; not real IPC)"
        $matchWritten = $true
    }
    if ($elapsed -ge $HoldMs) {
        break
    }
    Start-Sleep -Milliseconds 100
}

if ($WriteStderrNoise) {
    [Console]::Error.WriteLine("MOCK stderr noise at end")
}
exit $ExitCode
