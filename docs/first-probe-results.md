# First connection probe results

Date: 2026-11-16 (updated after harden pass)  
Branch: `feat/purplewave-connection-probe`  
PR: https://github.com/cccwon2/PurpleWave-Remastered-BWAPI-Bridge/pull/1  
JBWAPI pin: `d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f` (SHA check enforced)

## Legend

| Layer | Meaning |
|---|---|
| Build success | Maven compiles probe + pinned JBWAPI sources |
| Process run | JVM starts, JSONL with `runId` |
| IPC connect | Windows shared memory + pipe handshake |
| Match data | `onStart` + periodic frame/resources/units |
| Full bot | PurpleWave AI — **not attempted** |

## Offline / MOCK results (Linux cloud agent)

Host: Linux x86_64, OpenJDK 21 64-bit, Maven 3.8.7, PowerShell 7.7.  
StarCraft: Remastered / BWAPI bridge: **not present**.

| Test | Result | Notes |
|---|---|---|
| `mvn -q verify` | **PASS** | Unit + process IT |
| `pwsh …/Harden.Tests.ps1` | **PASS** | Pre-match wait→6; MOCK MATCH_STARTED survives wait; stale runId ignored; space/Korean argv; stderr+exit 6; other PID unaffected; SHA match |
| `pwsh …/ScriptSmoke.Tests.ps1` | **PASS** | Config / path failures |
| Without-bridge real jar | exit **7** `FAILED` | `WINDOWS_API_UNAVAILABLE` (`kernel32`); no `MATCH_STARTED` |
| Real Remastered match / frames / resources | **미실행** | No Windows host in this environment |
| Windows CI game-less path/exit | **미실행** | No Windows runner attached |

### Harden fixes covered by MOCK tests

- Match-start wait no longer kills after runId-scoped `MATCH_STARTED`
- Overall run limit is a **separate** optional param (default 0 = off)
- Stale `MATCH_STARTED` from another `runId` does not clear the wait
- `ProcessStartInfo.ArgumentList.Add` preserves spaces/Korean (child-verified)
- Timeout path writes stderr via `[Console]::Error` (not `Write-Error` under Stop) → exit **6**
- Stop only the supervised probe PID

MOCK `MATCH_STARTED` ≠ real IPC.

### Supply / CONNECTED / shutdown

- Supply fields remain BWAPI **internal** units (display ≈ /2) — not live-verified
- `CONNECTED` still not emitted (no public connect callback)
- `BWClient` still has no public stop; supervisor kills probe PID only

## Real-env checklist (Windows operator)

- [ ] Match-start wait while SCR + bridge idle/absent
- [ ] `MATCH_STARTED` for **this** `runId` when match begins
- [ ] Frame increases; resources/units vs screen (supply internal vs display)
- [ ] `MATCH_ENDED`
- [ ] Timeout stops probe only
- [ ] Record Java bitness used (32 vs 64)

## Distinction

| Achieved | Not achieved |
|---|---|
| Build + harden regressions (MOCK) | IPC connect |
| Offline failure classification | Match data on Remastered |
| | Full PurpleWave bot |
