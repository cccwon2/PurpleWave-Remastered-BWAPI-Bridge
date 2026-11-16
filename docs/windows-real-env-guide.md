# Windows real-env run guide (operator checklist)

Purpose: run the **read-only** connection probe against StarCraft: Remastered **after** a BWAPI-compatible shared-memory server is available.  
This guide does **not** invent bridge start commands for unknown servers. Pluto’s documented launcher is listed separately as a **Pluto AI** path — it does **not** currently expose JBWAPI attach surfaces (see `docs/bridge-selection.md`).

## Preconditions (fill before claiming success)

| Input | Your value | Notes |
|---|---|---|
| Bridge repo / commit **or** binary version | | Must publish classic mappings (below) |
| SCR version | | Pluto docs pin `1.23.10.13515` x86; confirm your install |
| SCR install / exe path | | Example shape only: `...\StarCraft\x86\StarCraft.exe` |
| Game working directory | | Where `bwapi-data` / runtime logs should live |
| Java path + bitness | | Record 32 vs 64; do not assume |
| Bridge start command | | **Operator-supplied** if not Pluto; do not invent |
| Probe start command | | See below |

### Required server surfaces (for this probe / JBWAPI)

- `Local\bwapi_shared_memory_game_list`
- `Local\bwapi_shared_memory_<serverPid>`
- `\\.\pipe\bwapi_pipe_<serverPid>`
- `client_version == 10003`
- GameData layout compatible with JBWAPI `d6003b0` (`SIZE` 33017048)

If your bridge does not publish these, **stop** — the probe can only report match-start wait / failure, not Remastered success.

## Probe build (Windows)

```powershell
git clone https://github.com/cccwon2/PurpleWave-Remastered-BWAPI-Bridge.git
cd PurpleWave-Remastered-BWAPI-Bridge
git checkout feat/purplewave-connection-probe
git submodule update --init --recursive
# Confirm: git -C 3rdparty/jbwapi rev-parse HEAD
# expect d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f
pwsh -File scripts/Test-Environment.ps1 -GameWorkingDirectory '<YOUR_SC_WORKDIR>'
pwsh -File scripts/Build-Probe.ps1 -JavaPath '<YOUR_JAVA_HOME_OR_java.exe>'
```

Requires **PowerShell 7+**.

## Probe run (after bridge is ready)

```powershell
pwsh -File scripts/Run-Probe.ps1 `
  -JavaPath '<YOUR_java.exe>' `
  -GameWorkingDirectory '<YOUR_SC_WORKDIR>' `
  -LogDirectory '.\logs' `
  -MatchStartWaitMs 120000 `
  -OverallRunTimeoutMs 0 `
  -StatusIntervalMs 1000
```

Logs: `logs/connection-probe-<runId>.jsonl`, `logs/probe-<runId>-stdout.log`, `logs/probe-<runId>-stderr.log`.

## Pluto-specific note (not a JBWAPI start recipe)

Pluto-Remastered-v2 documents starting SCR + injecting `pluto-scr.dll` via `scripts/start-pluto.ps1` (hashes and `-AllowUnvalidatedV2` gates apply). That path loads **Pluto’s AIModule in-process**. It is **not**, as audited, a recipe to attach this Java probe. Extending Pluto to host classic shared memory is future work in that repo.

Control Center only wraps the same bridge script (`InvokeBridge.ps1`) — still not a JBWAPI server.

## Screen-vs-log checklist

Mark only when observed:

- [ ] Bridge/server process running; SCR at expected version
- [ ] Probe: `PROCESS_STARTED` → `WAITING_FOR_BRIDGE` (match-start wait wording)
- [ ] Probe: `MATCH_STARTED` for **this** `runId` when match begins
- [ ] `frame` increases across status lines
- [ ] `minerals` / `gas` / supply / `selfUnitCount` match screen (**supply is BWAPI internal ≈ display×2**)
- [ ] `MATCH_ENDED` on game over
- [ ] Timeout kills **probe only** (SCR still running unless you close it)
- [ ] Record Java bitness used

## Failure interpretation

| Symptom | Likely meaning |
|---|---|
| Linux `WINDOWS_API_UNAVAILABLE` / kernel32 | Wrong OS for IPC — not a Windows no-bridge result |
| Windows exit 6, `MATCH_START_WAIT_EXCEEDED`, no MATCH_STARTED | No usable server / no match start observed |
| Version mismatch logs from JBWAPI | Server `client_version` ≠ 10003 |
| MOCK tests green | **Not** Remastered compatibility |

## What this guide will not invent

- Exact Pluto-to-JBWAPI attach commands (unsupported until server extension exists)
- Drive letters, usernames, or unverified SCR paths
- Bypass of Pluto hash / version gates
