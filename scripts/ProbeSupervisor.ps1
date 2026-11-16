<#
.SYNOPSIS
  Shared helpers for supervised probe launch (safe argv, match-start wait, PID-only stop).

.NOTES
  Requires PowerShell 7+ / .NET Core-style ProcessStartInfo.ArgumentList (per-argument Add).
  Windows PowerShell 5.1 is NOT supported for path-safe launches (ArgumentList array joining is unsafe).
#>

Set-StrictMode -Version Latest

function Test-ProbeHostSupported {
    $psMajor = $PSVersionTable.PSVersion.Major
    if ($psMajor -lt 7) {
        return $false
    }
    # Note: use $null -ne on the LEFT. `$col -ne $null` filters collection items in PowerShell.
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    return ($null -ne $psi.ArgumentList)
}

function New-ProbeRunId {
    return [guid]::NewGuid().ToString()
}

function Get-ProbeJsonlPath {
    param(
        [Parameter(Mandatory = $true)][string]$LogDirectory,
        [Parameter(Mandatory = $true)][string]$RunId
    )
    return Join-Path $LogDirectory ("connection-probe-{0}.jsonl" -f $RunId)
}

function Test-JsonlMatchStartedForRun {
    param(
        [Parameter(Mandatory = $true)][string]$JsonlPath,
        [Parameter(Mandatory = $true)][string]$RunId
    )
    if (-not (Test-Path -LiteralPath $JsonlPath)) {
        return $false
    }
    # Only accept MATCH_STARTED lines that carry this run's runId (ignore stale logs).
    $escaped = [regex]::Escape($RunId)
    $pattern = '"event"\s*:\s*"MATCH_STARTED".*"runId"\s*:\s*"' + $escaped + '"|"runId"\s*:\s*"' + $escaped + '".*"event"\s*:\s*"MATCH_STARTED"'
    try {
        foreach ($line in Get-Content -LiteralPath $JsonlPath -ErrorAction Stop) {
            if ($line -match $pattern) {
                return $true
            }
        }
    }
    catch {
        return $false
    }
    return $false
}

function Start-SafeProcess {
    <#
    .SYNOPSIS
      Starts a process with per-argument ArgumentList (preserves spaces/Unicode).
      Uses ProcessStartInfo.ArgumentList.Add on PowerShell 7+ / .NET Core.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$ArgumentList,
        [string]$WorkingDirectory = "",
        [string]$RedirectStandardOutput = "",
        [string]$RedirectStandardError = ""
    )

    if (-not (Test-ProbeHostSupported)) {
        throw "Safe argv launch requires PowerShell 7+ with ProcessStartInfo.ArgumentList support. Current PS=$($PSVersionTable.PSVersion)"
    }
    if (-not (Test-Path -LiteralPath $FilePath)) {
        throw "Executable not found: $FilePath"
    }

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FilePath
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = -not [string]::IsNullOrEmpty($RedirectStandardOutput)
    $psi.RedirectStandardError = -not [string]::IsNullOrEmpty($RedirectStandardError)
    if ($WorkingDirectory) {
        $psi.WorkingDirectory = $WorkingDirectory
    }
    foreach ($arg in $ArgumentList) {
        [void]$psi.ArgumentList.Add([string]$arg)
    }

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    $proc.EnableRaisingEvents = $true

    $outWriter = $null
    $errWriter = $null
    $outLock = New-Object object
    $errLock = New-Object object
    if ($psi.RedirectStandardOutput -and $RedirectStandardOutput) {
        $outWriter = New-Object System.IO.StreamWriter($RedirectStandardOutput, $false, [System.Text.UTF8Encoding]::new($false))
        $outWriter.AutoFlush = $true
        $outHandler = {
            if ($null -ne $EventArgs.Data) {
                $w = $Event.MessageData.Writer
                $l = $Event.MessageData.Lock
                [System.Threading.Monitor]::Enter($l)
                try { $w.WriteLine($EventArgs.Data) }
                finally { [System.Threading.Monitor]::Exit($l) }
            }
        }
        Register-ObjectEvent -InputObject $proc -EventName OutputDataReceived -Action $outHandler `
            -MessageData ([pscustomobject]@{ Writer = $outWriter; Lock = $outLock }) | Out-Null
    }
    if ($psi.RedirectStandardError -and $RedirectStandardError) {
        $errWriter = New-Object System.IO.StreamWriter($RedirectStandardError, $false, [System.Text.UTF8Encoding]::new($false))
        $errWriter.AutoFlush = $true
        $errHandler = {
            if ($null -ne $EventArgs.Data) {
                $w = $Event.MessageData.Writer
                $l = $Event.MessageData.Lock
                [System.Threading.Monitor]::Enter($l)
                try { $w.WriteLine($EventArgs.Data) }
                finally { [System.Threading.Monitor]::Exit($l) }
            }
        }
        Register-ObjectEvent -InputObject $proc -EventName ErrorDataReceived -Action $errHandler `
            -MessageData ([pscustomobject]@{ Writer = $errWriter; Lock = $errLock }) | Out-Null
    }

    try {
        $started = $proc.Start()
    }
    catch {
        throw ("Failed to start process '{0}': {1}" -f $FilePath, $_.Exception.Message)
    }
    if (-not $started) {
        throw "Process.Start returned false for $FilePath"
    }
    if ($psi.RedirectStandardOutput) { $proc.BeginOutputReadLine() }
    if ($psi.RedirectStandardError) { $proc.BeginErrorReadLine() }

    return [pscustomobject]@{
        Process    = $proc
        Pid        = $proc.Id
        OutWriter  = $outWriter
        ErrWriter  = $errWriter
        StdoutPath = $RedirectStandardOutput
        StderrPath = $RedirectStandardError
    }
}

function Stop-SafeProcess {
    param(
        [Parameter(Mandatory = $true)]$Handle
    )
    $proc = $Handle.Process
    if ($null -eq $proc) { return }
    try {
        if (-not $proc.HasExited) {
            # Only the PID this supervisor started.
            Stop-Process -Id $proc.Id -Force -ErrorAction Stop
        }
    }
    catch {
        [Console]::Error.WriteLine(("Stop-Process failed for PID {0}: {1}" -f $proc.Id, $_.Exception.Message))
    }
}

function Complete-SafeProcessIO {
    param(
        [Parameter(Mandatory = $true)]$Handle,
        [int]$WaitMs = 500
    )
    Start-Sleep -Milliseconds $WaitMs
    Get-EventSubscriber -ErrorAction SilentlyContinue |
        Where-Object { $_.SourceObject -eq $Handle.Process } |
        ForEach-Object {
            Unregister-Event -SubscriptionId $_.SubscriptionId -ErrorAction SilentlyContinue
        }
    try { if ($Handle.OutWriter) { $Handle.OutWriter.Flush(); $Handle.OutWriter.Dispose() } } catch { }
    try { if ($Handle.ErrWriter) { $Handle.ErrWriter.Flush(); $Handle.ErrWriter.Dispose() } } catch { }
}

function Wait-SupervisedProbe {
    <#
    .SYNOPSIS
      Wait for match start (by runId JSONL) then optional overall timeout; kill only supervised PID.
    #>
    param(
        [Parameter(Mandatory = $true)]$Handle,
        [Parameter(Mandatory = $true)][string]$JsonlPath,
        [Parameter(Mandatory = $true)][string]$RunId,
        [Parameter(Mandatory = $true)][int]$MatchStartWaitMs,
        [int]$OverallRunTimeoutMs = 0,
        [int]$PollMs = 200
    )

    $proc = $Handle.Process
    $start = [Environment]::TickCount64
    $matchStarted = $false
    $killedForMatchWait = $false
    $killedForOverall = $false

    while (-not $proc.HasExited) {
        $elapsed = [int]([Environment]::TickCount64 - $start)

        if (-not $matchStarted) {
            if (Test-JsonlMatchStartedForRun -JsonlPath $JsonlPath -RunId $RunId) {
                $matchStarted = $true
            }
            elseif ($elapsed -ge $MatchStartWaitMs) {
                Stop-SafeProcess -Handle $Handle
                $killedForMatchWait = $true
                break
            }
        }
        else {
            if ($OverallRunTimeoutMs -gt 0 -and $elapsed -ge $OverallRunTimeoutMs) {
                Stop-SafeProcess -Handle $Handle
                $killedForOverall = $true
                break
            }
        }

        Start-Sleep -Milliseconds $PollMs
    }

    try { [void]$proc.WaitForExit(5000) } catch { }
    Complete-SafeProcessIO -Handle $Handle

    $exitCode = $null
    try {
        if ($proc.HasExited) { $exitCode = $proc.ExitCode }
    }
    catch { }

    if ($killedForMatchWait -or $killedForOverall) {
        $exitCode = 6
    }

    return [pscustomobject]@{
        ExitCode            = $exitCode
        MatchStarted        = $matchStarted
        KilledForMatchWait  = $killedForMatchWait
        KilledForOverall    = $killedForOverall
        Pid                 = $Handle.Pid
    }
}

function Write-StreamToHost {
    param([string]$Path, [ValidateSet("stdout", "stderr")][string]$Stream)
    if (-not $Path -or -not (Test-Path -LiteralPath $Path)) { return }
    $lines = Get-Content -LiteralPath $Path -ErrorAction SilentlyContinue
    if ($null -eq $lines) { return }
    foreach ($line in $lines) {
        if ($Stream -eq "stdout") {
            [Console]::Out.WriteLine($line)
        }
        else {
            # Do NOT use Write-Error under ErrorActionPreference=Stop (would become exit 1).
            [Console]::Error.WriteLine($line)
        }
    }
}
