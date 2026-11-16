# PurpleWave Remastered — BWAPI connection probe

Minimal **read-only** connection diagnostic for running a JBWAPI client against a BWAPI-compatible bridge while testing StarCraft: Remastered locally.

This repository is **not** a full PurpleWave port and (at the time of the first probe) does **not** ship Remastered bridge sources/binaries. See [`docs/compatibility-audit.md`](docs/compatibility-audit.md).

## What it does

- Pins **the same JBWAPI commit PurpleWave uses**: `d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f`
- Prints environment / dependency info
- Waits for a BWAPI bridge (shared memory game table)
- On match start: logs frame, self player id, race, minerals, gas, supply, self unit count
- Periodic JSONL status (~1s default)
- Connect timeout + clean **probe-only** shutdown

## What it does not do

- Issue move/attack/gather/train/build commands
- Change game speed, auto-start/end matches, or run PurpleWave AI
- Invent IPC names/structs or upgrade JBWAPI past PurpleWave’s pin
- Bypass version/hash checks or add hooks/injection

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
  -ConnectTimeoutMs 60000 `
  -StatusIntervalMs 1000
```

Paths with spaces or non-ASCII (e.g. Korean) are passed as literal arguments — do not hardcode drive letters in the repo.

### Exit codes

| Code | Meaning |
|---|---|
| 0 | Completed without probe failure (e.g. match ended and client returned) |
| 2 | Usage / missing jar |
| 3 | Config range / parse error |
| 4 | Log directory create failure |
| 5 | Bad working directory |
| 6 | Connect timeout |
| 7 | Failed (including environments where Windows IPC APIs are unavailable) |

## JSONL

`logs/connection-probe.jsonl` fields: `timestamp`, `level`, `event`, `elapsedMs`, `frame`, `playerId`, `race`, `minerals`, `gas`, `supplyUsed`, `supplyTotal`, `selfUnitCount`, `errorCode`, `message`.

Unreadable values are `null`.  
`supplyUsed` / `supplyTotal` are **BWAPI internal** units (display ≈ internal/2).

Events emitted: `PROCESS_STARTED`, `WAITING_FOR_BRIDGE`, `MATCH_STARTED`, `MATCH_ENDED`, `FAILED`.  
`CONNECTED` is **not** emitted — JBWAPI’s public listener API has no IPC-connect callback.

## Tests

```bash
mvn test          # unit / in-process offline
mvn verify        # + process IT without bridge
pwsh -File src/test/powershell/ScriptSmoke.Tests.ps1
```

Mock/offline pass ≠ Remastered compatibility. Real match checks are documented in [`docs/first-probe-results.md`](docs/first-probe-results.md) (미실행 until a Windows Remastered + bridge environment is available).

## Limitations

- Requires a **Windows** BWAPI server that exposes JBWAPI’s expected mappings/pipes and client version `10003`
- No bridge implementation is included here
- `BWClient` has no public stop API — scripts kill **only** the probe process on timeout
- Java 32-bit vs 64-bit for Remastered is **unverified**; PurpleWave and JBWAPI docs disagree (see audit)
