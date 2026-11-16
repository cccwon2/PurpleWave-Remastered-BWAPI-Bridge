# First connection probe results

Date: 2026-11-16 (updated after Windows CI green on `c512dc5`)  
Branch: `feat/purplewave-connection-probe`  
HEAD: `c512dc5d6ad47cab057ca7e95088e3794260fc2a`  
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
| Without-bridge real jar (Linux) | exit **7** `FAILED` | `WINDOWS_API_UNAVAILABLE` (`kernel32`); **not** a Windows no-bridge result |
| Real Remastered match / frames / resources | **미실행** | No game/bridge in agent or CI |
| Windows CI game-less | **workflow added + CI green** on `c512dc5` | push https://github.com/cccwon2/PurpleWave-Remastered-BWAPI-Bridge/actions/runs/43895857432 ; PR https://github.com/cccwon2/PurpleWave-Remastered-BWAPI-Bridge/actions/runs/43895869049 — Java 8 x64 **baseline only**, not Remastered proof |
| Windows no-bridge real jar (CI) | exit **6** timeout | `WAITING_FOR_BRIDGE`; no `MATCH_STARTED`; step then `exit 0` after validation (`c512dc5` fix) |
| Bridge candidate audit | See `docs/bridge-selection.md` | Pluto-v2 = in-process only; needs server extension for JBWAPI |

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
