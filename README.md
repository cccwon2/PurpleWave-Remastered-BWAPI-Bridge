# PurpleWave Remastered — BWAPI connection probe

Minimal **read-only** connection diagnostic for running a JBWAPI client against a BWAPI-compatible bridge while testing StarCraft: Remastered locally.

This repository is **not** a full PurpleWave port and does **not** ship Remastered bridge sources/binaries. See [`docs/compatibility-audit.md`](docs/compatibility-audit.md).

## What it does

- Pins **the same JBWAPI commit PurpleWave uses**: `d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f` (verified at runtime)
- Aligns JNA to **PurpleWave 5.1.0** (not JBWAPI pom’s 5.6.0) for baseline reproducibility
- Per-run JSONL (`connection-probe-<runId>.jsonl`) with `runId` on every line
- Match-start wait (not a verified IPC-connect wait — public JBWAPI has no connect callback)
- Optional overall run timeout (separate; 0 = disabled)
- On match start: frame, self player id, race, minerals, gas, supply, self unit count
- Field-read failures log field name + cause at a rate-limited interval (values still `null`)
- **Probe-only** shutdown on timeout

## What it does not do

- Issue move/attack/gather/train/build commands
- Change game speed, auto-start/end matches, or run PurpleWave AI
- Invent IPC names/structs or upgrade JBWAPI past PurpleWave’s pin
- Bypass version/hash checks or add hooks/injection

## Requirements for scripts

- **PowerShell 7+** (uses `ProcessStartInfo.ArgumentList.Add` for safe argv)
- Windows PowerShell 5.1 is **not** supported for path-safe launches (`Start-Process -ArgumentList` array joins/splits unsafely — verified)

## Build

```powershell
git submodule update --init --recursive
pwsh -File scripts/Test-Environment.ps1
pwsh -File scripts/Build-Probe.ps1
```

Or:

```bash
git submodule update --init --recursive
mvn verify
```

Artifact: `target/connection-probe-0.1.0.jar`

## Run (Windows + bridge + game)

```powershell
pwsh -File scripts/Run-Probe.ps1 `
  -JavaPath "C:\Path\To\java.exe" `
  -GameWorkingDirectory "C:\Path\To\StarCraft" `
  -LogDirectory ".\logs" `
  -MatchStartWaitMs 60000 `
  -OverallRunTimeoutMs 0 `
  -StatusIntervalMs 1000
```

`-ConnectTimeoutMs` remains an alias for `-MatchStartWaitMs` (match-start wait, **not** IPC connect).

Paths with spaces or Korean are preserved via `ArgumentList.Add` — do not hardcode drive letters in the repo.

### Exit codes

| Code | Meaning |
|---|---|
| 0 | Completed without probe failure (e.g. match ended and client returned) |
| 1 | Process start / host unsupported / bad Java |
| 2 | Usage / missing jar |
| 3 | Config range / parse error |
| 4 | Log directory create failure |
| 5 | Bad working directory |
| 6 | Match-start wait or overall-run timeout |
| 7 | Failed (e.g. Windows APIs unavailable on Linux) |
| 8 | JBWAPI submodule SHA mismatch vs expected pin |

## JSONL

`logs/connection-probe-<runId>.jsonl` fields: `timestamp`, `level`, `event`, `runId`, `elapsedMs`, `frame`, `playerId`, `race`, `minerals`, `gas`, `supplyUsed`, `supplyTotal`, `selfUnitCount`, `errorCode`, `message`.

Unreadable values are `null` (with rate-limited WARN naming the field).  
`supplyUsed` / `supplyTotal` are **BWAPI internal** units (display ≈ internal/2).

Events: `PROCESS_STARTED`, `WAITING_FOR_BRIDGE`, `MATCH_STARTED`, `MATCH_ENDED`, `FAILED`.  
`CONNECTED` is **not** emitted.

Error codes when classifiable: `WINDOWS_API_UNAVAILABLE`, `MATCH_START_WAIT_EXCEEDED`, `NO_BRIDGE_OR_WAITING`, `OVERALL_RUN_TIMEOUT`, `JBWAPI_PIN_MISMATCH`.

## Tests

```bash
mvn test          # unit / in-process offline
mvn verify        # + process IT without bridge
pwsh -File src/test/powershell/Harden.Tests.ps1   # supervisor/argv/timeout regressions (MOCK labeled)
pwsh -File src/test/powershell/ScriptSmoke.Tests.ps1
```

**MOCK / offline pass ≠ Remastered compatibility.** Real match checks: [`docs/first-probe-results.md`](docs/first-probe-results.md) (미실행 until Windows + SCR + bridge).

## Limitations

- Requires a **Windows** BWAPI server exposing JBWAPI mappings/pipes and client version `10003`
- No bridge implementation is included here
- `BWClient` has no public stop API — scripts kill **only** the probe PID this script started
- Java 32-bit vs 64-bit for Remastered is **unverified**
