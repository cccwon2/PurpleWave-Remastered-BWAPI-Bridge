# First connection probe results

Date: 2026-11-16  
Branch: `feat/purplewave-connection-probe`  
JBWAPI pin: `d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f` (same as PurpleWave `3rdparty/jbwapi`)

## Legend (do not collapse these)

| Layer | Meaning |
|---|---|
| Build success | Maven compiles probe + pinned JBWAPI sources into shaded JAR |
| Process run | JVM starts, logs `PROCESS_STARTED` / config handling |
| IPC connect | Windows shared memory + pipe handshake with a BWAPI server |
| Match data | `onStart` + periodic frame/resources/units |
| Full bot | PurpleWave AI — **not attempted** |

## Offline results (this cloud agent VM)

Host: Linux x86_64, OpenJDK 21 64-bit, Maven 3.8.7, PowerShell 7.7.  
StarCraft: Remastered: **not present**. BWAPI bridge: **not present**.

| Test | Result | Notes |
|---|---|---|
| `mvn -q test` (unit) | **PASS** | ProbeConfig / JsonlLogger / offline exit codes |
| `mvn -q verify` (includes IT) | **PASS** | Process IT got `EXIT_FAILED` (7) without bridge on Linux |
| `pwsh scripts/Test-Environment.ps1` | **PASS** (exit 0) | Reports `RealEnv=NO` on Linux |
| `pwsh scripts/Build-Probe.ps1` | **PASS** | Produces `target/connection-probe-0.1.0.jar` |
| `pwsh src/test/powershell/ScriptSmoke.Tests.ps1` | **PASS** | Bad Java/workdir/ranges/log-dir; without-bridge exit 7 |
| Without-bridge probe run | **PASS as failure report** — exit **7** `FAILED` | `UnsatisfiedLinkError: Unable to load library 'kernel32'` within ~80ms. Emitted `PROCESS_STARTED` → `WAITING_FOR_BRIDGE` → `FAILED`. **No** `MATCH_STARTED`. |
| Real Remastered match start / frame / resources / units / match end | **미실행** | No game + no bridge on this VM |
| Leftover process check vs StarCraft | **미실행** | N/A without SCR; supervisor designed to kill **probe PID only** |

### Sample offline JSONL (abridged)

```text
PROCESS_STARTED  env … jbwapiCommit=d6003b0… jbwapiSupportedBwapiClientVersion=10003 dataModel=64
PROCESS_STARTED  Connection probe process started
WAITING_FOR_BRIDGE  Waiting for Local\bwapi_shared_memory_game_list …
FAILED  errorCode=java.lang.UnsatisfiedLinkError  Unable to load library 'kernel32'
```

### Supply units

JSONL fields `supplyUsed` / `supplyTotal` are **BWAPI internal** values from `Player.supplyUsed()` / `supplyTotal()`.  
In-game display supply is typically **internal / 2** (see JBWAPI `Player` javadoc). The probe does not convert.  
Not observed live (미실행).

### CONNECTED state

Not emitted. JBWAPI `BWEventListener` has no IPC-connect callback. See `docs/compatibility-audit.md`.

### Shutdown / timeout

`BWClient` has no public stop API (`Client.disconnect` is package-private).  
Probe watchdog interrupts / `System.exit(EXIT_TIMEOUT)` after `--connect-timeout-ms` if match never starts.  
`scripts/Run-Probe.ps1` additionally supervises and `Stop-Process` on the **probe PID only** — never StarCraft or unrelated Java.  
On this Linux host the probe failed before the timeout path (Kernel32 missing); Windows-without-bridge timeout remains the intended reconnect-loop case.

## Real-env checklist (for a Windows operator)

Mark each only when observed against screen:

- [ ] `WAITING_FOR_BRIDGE` while SCR + bridge idle/absent
- [ ] `MATCH_STARTED` when a match begins with the bot slot attached
- [ ] `frame` increases over status samples
- [ ] `minerals` / `gas` / supply / `selfUnitCount` match what is visible (remember supply internal vs display)
- [ ] `MATCH_ENDED` on game over
- [ ] Timeout stops probe only; StarCraft remains unless user closes it
- [ ] Java bitness used recorded here (32 vs 64) with evidence

## Distinction reminder

| Achieved here | Not achieved |
|---|---|
| Build success | IPC connect |
| Process run + JSONL failure reporting | Match data |
| Offline timeout/failure scripts | Remastered compatibility |
| | Full PurpleWave bot |

Offline timeout/failure reporting **≠** Remastered compatibility.
