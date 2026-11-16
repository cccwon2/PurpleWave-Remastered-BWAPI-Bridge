# Pester-style smoke tests for probe scripts (offline; no StarCraft).
# Run: pwsh -File src/test/powershell/ScriptSmoke.Tests.ps1
# Prefer Harden.Tests.ps1 for supervisor/argv/timeout regressions.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../../..")
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

Write-Host "RepoRoot=$repoRoot"

& pwsh -NoProfile -File (Join-Path $repoRoot "scripts/Run-Probe.ps1") -JavaPath "/definitely/missing/java" -MatchStartWaitMs 1000 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -ne 0) "Run-Probe bad JavaPath exits non-zero (code=$LASTEXITCODE)"

& pwsh -NoProfile -File (Join-Path $repoRoot "scripts/Run-Probe.ps1") -GameWorkingDirectory "/definitely/missing/sc" -MatchStartWaitMs 1000 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -eq 5) "Run-Probe bad workdir exit 5 (code=$LASTEXITCODE)"

& pwsh -NoProfile -File (Join-Path $repoRoot "scripts/Run-Probe.ps1") -MatchStartWaitMs 1 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -eq 3) "Run-Probe MatchStartWaitMs range exit 3 (code=$LASTEXITCODE)"

& pwsh -NoProfile -File (Join-Path $repoRoot "scripts/Run-Probe.ps1") -StatusIntervalMs 1 -MatchStartWaitMs 1000 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -eq 3) "Run-Probe StatusIntervalMs range exit 3 (code=$LASTEXITCODE)"

$blocker = Join-Path ([IO.Path]::GetTempPath()) ("probe-log-blocker-" + [guid]::NewGuid().ToString() + ".tmp")
New-Item -ItemType File -Path $blocker -Force | Out-Null
& pwsh -NoProfile -File (Join-Path $repoRoot "scripts/Run-Probe.ps1") -LogDirectory $blocker -MatchStartWaitMs 1000 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -eq 4) "Run-Probe log dir failure exit 4 (code=$LASTEXITCODE)"
Remove-Item -LiteralPath $blocker -Force -ErrorAction SilentlyContinue

& pwsh -NoProfile -File (Join-Path $repoRoot "scripts/Test-Environment.ps1") 2>&1 | Out-Null
Assert-True ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq 1) "Test-Environment exits 0/1 (code=$LASTEXITCODE)"

$jar = Join-Path $repoRoot "target/connection-probe-0.1.0.jar"
if (Test-Path -LiteralPath $jar) {
    $logDir = Join-Path ([IO.Path]::GetTempPath()) ("probe-ps-timeout-" + [guid]::NewGuid().ToString())
    & pwsh -NoProfile -File (Join-Path $repoRoot "scripts/Run-Probe.ps1") `
        -LogDirectory $logDir `
        -MatchStartWaitMs 3000 `
        -StatusIntervalMs 500 2>&1 | Out-Null
    $code = $LASTEXITCODE
    Assert-True ($code -eq 6 -or $code -eq 7) "Run-Probe without bridge returns timeout/fail (code=$code)"
}
else {
    Write-Host "SKIP: jar missing for timeout cleanup check"
}

if ($failed -gt 0) {
    Write-Host "$failed script smoke test(s) failed" -ForegroundColor Red
    exit 1
}
Write-Host "All script smoke tests passed" -ForegroundColor Green
exit 0
