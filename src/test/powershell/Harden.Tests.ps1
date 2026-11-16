# Hardening regression tests for probe supervisor (MOCK where noted; no StarCraft).
# Run: pwsh -File src/test/powershell/Harden.Tests.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../../..")
. (Join-Path $repoRoot "scripts/ProbeSupervisor.ps1")

$failed = 0
function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        Write-Host "FAIL: $Message" -ForegroundColor Red
        $script:failed++
    }
    else {
        Write-Host "PASS: $Message" -ForegroundColor Green
    }
}

function New-ArgFile {
    param([string]$Path, [string[]]$ArgLines)
    $ArgLines | Set-Content -LiteralPath $Path -Encoding utf8
}

Write-Host "RepoRoot=$repoRoot"
Assert-True (Test-ProbeHostSupported) "PowerShell host supports ArgumentList.Add"

$mock = Join-Path $PSScriptRoot "mock-probe.ps1"
$pwsh = (Get-Command pwsh).Source
$runProbe = Join-Path $repoRoot "scripts/Run-Probe.ps1"

# --- Stale MATCH_STARTED must not false-trigger ---
$staleDir = Join-Path ([IO.Path]::GetTempPath()) ("stale-" + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $staleDir | Out-Null
$staleRun = "old-run"
$staleJsonl = Get-ProbeJsonlPath -LogDirectory $staleDir -RunId $staleRun
Set-Content -LiteralPath $staleJsonl -Value '{"event":"MATCH_STARTED","runId":"old-run","message":"stale"}' -Encoding utf8
$newRun = "new-run"
Assert-True (-not (Test-JsonlMatchStartedForRun -JsonlPath $staleJsonl -RunId $newRun)) `
    "Stale MATCH_STARTED for other runId does not false-trigger"
Assert-True (Test-JsonlMatchStartedForRun -JsonlPath $staleJsonl -RunId $staleRun) `
    "Same-run MATCH_STARTED still detected"

# --- Space/Korean argv preserved via ArgumentList.Add (verified by child dump) ---
$argvDir = Join-Path ([IO.Path]::GetTempPath()) ("argv 한글 " + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $argvDir | Out-Null
$dump = Join-Path $argvDir "out.txt"
$child = Join-Path $argvDir "dump.ps1"
@'
$outFile = $args[0]
$args[1..($args.Length-1)] | Set-Content -LiteralPath $outFile -Encoding utf8
'@ | Set-Content -LiteralPath $child -Encoding utf8
$spaceJar = Join-Path $argvDir "my jar.jar"
$koreanWork = Join-Path $argvDir "작업디렉터리"
New-Item -ItemType File -Path $spaceJar -Force | Out-Null
New-Item -ItemType Directory -Force -Path $koreanWork | Out-Null
$handle = Start-SafeProcess -FilePath $pwsh -ArgumentList @(
    "-NoProfile", "-File", $child, $dump, $spaceJar, $koreanWork, "hello world"
) -RedirectStandardOutput (Join-Path $argvDir "stdout.log") -RedirectStandardError (Join-Path $argvDir "stderr.log")
$handle.Process.WaitForExit(10000) | Out-Null
Complete-SafeProcessIO -Handle $handle
$lines = @(Get-Content -LiteralPath $dump -Encoding utf8)
Assert-True ($lines.Count -eq 3) "Argv dump has 3 lines (count=$($lines.Count))"
Assert-True ($lines[0] -eq $spaceJar) "Space path preserved as single argv"
Assert-True ($lines[1] -eq $koreanWork) "Korean path preserved as single argv"
Assert-True ($lines[2] -eq "hello world") "Spaced string preserved as single argv"

# --- Pre-match wait expires (MOCK; no real MATCH_STARTED) ---
$waitDir = Join-Path ([IO.Path]::GetTempPath()) ("wait-" + [guid]::NewGuid())
$waitRun = [guid]::NewGuid().ToString()
New-Item -ItemType Directory -Force -Path $waitDir | Out-Null
$waitArgs = Join-Path $waitDir "args.txt"
New-ArgFile -Path $waitArgs -ArgLines @(
    "-NoProfile", "-File", $mock,
    "-MockLogDirectory", $waitDir,
    "-MockRunId", $waitRun,
    "-MatchStartAfterMs", "-1",
    "-HoldMs", "20000"
)
& $pwsh -NoProfile -File $runProbe `
    -LogDirectory $waitDir `
    -MatchStartWaitMs 1500 `
    -OverallRunTimeoutMs 0 `
    -RunId $waitRun `
    -TestExecutable $pwsh `
    -TestArgumentFile $waitArgs | Out-Null
$waitCode = $LASTEXITCODE
Assert-True ($waitCode -eq 6) "Pre-match wait expires with exit 6 (code=$waitCode)"
Assert-True (-not (Test-JsonlMatchStartedForRun -JsonlPath (Get-ProbeJsonlPath -LogDirectory $waitDir -RunId $waitRun) -RunId $waitRun)) `
    "MOCK pre-match expiry did not emit MATCH_STARTED"

# --- After match start, survives past old match-start wait (MOCK MATCH_STARTED) ---
$aliveDir = Join-Path ([IO.Path]::GetTempPath()) ("alive-" + [guid]::NewGuid())
$aliveRun = [guid]::NewGuid().ToString()
New-Item -ItemType Directory -Force -Path $aliveDir | Out-Null
$aliveArgs = Join-Path $aliveDir "args.txt"
New-ArgFile -Path $aliveArgs -ArgLines @(
    "-NoProfile", "-File", $mock,
    "-MockLogDirectory", $aliveDir,
    "-MockRunId", $aliveRun,
    "-MatchStartAfterMs", "400",
    "-HoldMs", "4500",
    "-ExitCode", "0"
)
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& $pwsh -NoProfile -File $runProbe `
    -LogDirectory $aliveDir `
    -MatchStartWaitMs 1500 `
    -OverallRunTimeoutMs 0 `
    -RunId $aliveRun `
    -TestExecutable $pwsh `
    -TestArgumentFile $aliveArgs | Out-Null
$aliveCode = $LASTEXITCODE
$sw.Stop()
Assert-True ($aliveCode -eq 0) "After MOCK MATCH_STARTED, probe completes with 0 (code=$aliveCode)"
Assert-True ($sw.ElapsedMilliseconds -ge 3500) "Survived past match-start wait (elapsed=$($sw.ElapsedMilliseconds))"
Assert-True (Test-JsonlMatchStartedForRun -JsonlPath (Get-ProbeJsonlPath -LogDirectory $aliveDir -RunId $aliveRun) -RunId $aliveRun) `
    "MOCK MATCH_STARTED observed for this runId only"

# --- stderr present still yields timeout exit 6 ---
$errDir = Join-Path ([IO.Path]::GetTempPath()) ("stderr-" + [guid]::NewGuid())
$errRun = [guid]::NewGuid().ToString()
New-Item -ItemType Directory -Force -Path $errDir | Out-Null
$errArgs = Join-Path $errDir "args.txt"
New-ArgFile -Path $errArgs -ArgLines @(
    "-NoProfile", "-File", $mock,
    "-MockLogDirectory", $errDir,
    "-MockRunId", $errRun,
    "-MatchStartAfterMs", "-1",
    "-HoldMs", "20000",
    "-WriteStderrNoise"
)
& $pwsh -NoProfile -File $runProbe `
    -LogDirectory $errDir `
    -MatchStartWaitMs 1200 `
    -RunId $errRun `
    -TestExecutable $pwsh `
    -TestArgumentFile $errArgs 2>&1 | Out-Null
$errCode = $LASTEXITCODE
Assert-True ($errCode -eq 6) "stderr noise still yields timeout exit 6 (code=$errCode)"
$stderrFile = Join-Path $errDir ("probe-{0}-stderr.log" -f $errRun)
Assert-True (Test-Path -LiteralPath $stderrFile) "stderr log file exists"
$stderrText = ""
if (Test-Path -LiteralPath $stderrFile) {
    $stderrText = [string](Get-Content -LiteralPath $stderrFile -Raw -ErrorAction SilentlyContinue)
}
Assert-True ($stderrText -like "*MOCK stderr*") "stderr log captured mock stderr"

# --- After probe stop, other processes unaffected ---
$other = Start-Process -FilePath $pwsh -ArgumentList @("-NoProfile", "-Command", "Start-Sleep -Seconds 30") -PassThru
$otherPid = $other.Id
$isoDir = Join-Path ([IO.Path]::GetTempPath()) ("iso-" + [guid]::NewGuid())
$isoRun = [guid]::NewGuid().ToString()
New-Item -ItemType Directory -Force -Path $isoDir | Out-Null
$isoArgs = Join-Path $isoDir "args.txt"
New-ArgFile -Path $isoArgs -ArgLines @(
    "-NoProfile", "-File", $mock,
    "-MockLogDirectory", $isoDir,
    "-MockRunId", $isoRun,
    "-HoldMs", "20000"
)
& $pwsh -NoProfile -File $runProbe `
    -LogDirectory $isoDir `
    -MatchStartWaitMs 1000 `
    -RunId $isoRun `
    -TestExecutable $pwsh `
    -TestArgumentFile $isoArgs | Out-Null
Assert-True ($LASTEXITCODE -eq 6) "Isolation run timed out as expected"
Start-Sleep -Milliseconds 300
$otherAlive = $false
try {
    $p = Get-Process -Id $otherPid -ErrorAction Stop
    $otherAlive = -not $p.HasExited
}
catch { $otherAlive = $false }
Assert-True ($otherAlive) "Other process PID $otherPid unaffected after probe stop"
try { Stop-Process -Id $otherPid -Force -ErrorAction SilentlyContinue } catch { }

# --- JBWAPI SHA mismatch detection ---
$expected = "d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f"
Push-Location (Join-Path $repoRoot "3rdparty/jbwapi")
try { $actual = (git rev-parse HEAD).Trim() }
finally { Pop-Location }
Assert-True ($actual -eq $expected) "JBWAPI submodule SHA matches expected pin ($actual)"
Assert-True ($expected -ne "0000000000000000000000000000000000000000") "Mismatch detection: bogus SHA differs"

# --- Existing smoke: bad paths / ranges ---
& $pwsh -NoProfile -File $runProbe -JavaPath "/definitely/missing/java" -MatchStartWaitMs 1000 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -ne 0) "Bad JavaPath non-zero"
& $pwsh -NoProfile -File $runProbe -GameWorkingDirectory "/definitely/missing/sc" -MatchStartWaitMs 1000 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -eq 5) "Bad workdir exit 5"
& $pwsh -NoProfile -File $runProbe -MatchStartWaitMs 1 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -eq 3) "MatchStartWaitMs range exit 3"

if ($failed -gt 0) {
    Write-Host "$failed harden test(s) failed" -ForegroundColor Red
    exit 1
}
Write-Host "All harden regression tests passed (MOCK where labeled; not real IPC)" -ForegroundColor Green
exit 0
