# PurpleWave pylon stall — classify (state-parity priority)

**Status:** Not a live fix. **Not PASS.** No SCR **build-encoder** patch until state parity shows Build entering the queue. Do not merge.

Canonical store copy: `/cursor/stores/self/docs/purplewave-pylon-stall-classify.md`

## Priority shift (Won)

| Prefer | Deprioritize |
|---|---|
| Bridge **state delivery** (SCR → GameData → JBWAPI) for build-hold fields | PW strategy / build-logic edits |
| Keep the **already-validated PW JAR** (path + SHA-256) | New PW jar that changes construction AI |
| Fix **wrong input data in the bridge** | Force `canBuild` true / inject Build |

**“No Build” in the observe log is a symptom**, not proof of a PurpleWave defect. It only shows Build was absent from `GameData.unitCommands` for the observed window (or that JBWAPI rejected before queue insert because of **state**).

## Fix applied / running?

| Item | Result |
|---|---|
| Tip | Pluto `c523b7b` / **`jbwapi_server_v2_rc3_state1`** |
| Correct log | `bridge_5970` pid **31524** state1 + hold_* (store: `internal/bridge_5970.log`) |
| Stale pid 38364 | rc3 — ignore |
| S0 (5970) | Probe **id=2** Move-to-site while mins briefly &lt;100 = expected; minerals climb; site vis/exp/build OK, occ false; **0 Build** in queue. **No bridge fix** — not confirmed bad GameData |
| Next | Separate diag JAR for A1/A2; keep validated JAR. **Not PASS** |

## Pin the validated JAR (every retest)

Do **not** rebuild PW unless making a **separate diagnostic** jar.

```powershell
cd C:\Games\PurpleWave
$jar = (Resolve-Path .\target\PurpleWave-1.0.jar).Path
Get-FileHash $jar -Algorithm SHA256 | Format-List
# Record: path + hash. Run-Remaster -JarPath $jar
```

`Run-Remaster.ps1` now prints `jar_sha256` on start — keep that same artifact for retests.

## Build-hold fields only

### A) PurpleWave gates before `bwapiUnit.build`

| Condition | Reads |
|---|---|
| `intent.startNow` | PW currency/reservation |
| `tile.visible` | `Game.isVisible` ← `GameData.isVisible` |
| dist to building center `< 112` | worker `positionX/Y` |
| minerals/gas ≥ price−8 | `GameData.players[self]` |
| `Commander` dist ≤ 5×32 else Move | worker position vs `tile.center` |

### B) JBWAPI `canBuildHere(..., checkExplored=true)`

explored / buildable / occupied / creep / power / path — from Snapshot→GameData. Builder excluded by object identity.

## Three-way compare (same frame when possible)

1. Raw SCR (what Snapshot reads)  
2. Bridge serialize → **`jbwapi_build_hold_state` / `jbwapi_build_hold_worker`**  
3. JBWAPI/PW read (same shared GameData; optional separate diag JAR only if (2) looks correct)

Compare: probe pos/tile/order/completed/owner; minerals; site buildable/explored/occupied/visible; self-obstacle; frame mix.

## Refined stages

| Stage | Meaning | When |
|---|---|---|
| **S0** | Bad/mismatched **state** for build-hold fields | **Investigate first** |
| **A1** | PW never reaches `build()` on good state | After S0 OK |
| **A2** | `build()` called; JBWAPI rejects on correct state | After S0 OK |
| **B/C** | Queue / SCR construction | Only after Build enters `unitCommands` |

Prior “A confirmed from no 0c” is **withdrawn** as a PW-defect claim.

## Won retest

```powershell
cd C:\Games\Pluto-Remastered-v2
git pull origin cursor/purplewave-ai-command-path-e83e
& .\scripts\build.ps1
# Must print: jbwapi_server_v2_rc3_state1

cd C:\Games\PurpleWave
Get-FileHash .\target\PurpleWave-1.0.jar -Algorithm SHA256
# C1/C2 with that same -JarPath
```

Greps: `jbwapi_server_started`, `jbwapi_build_hold_state`, `jbwapi_build_hold_worker`, `kind":"build"`, `packet_hex":"0c`

**PASS** only when Won sees pylon warp + supply leave 9/9.
