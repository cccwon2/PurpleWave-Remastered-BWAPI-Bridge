# PurpleWave pylon stall — classify (rc3, refined)

**Status:** Not a live fix. **Not PASS.** No SCR build-encoder patches. Do not merge.

## Fix applied / running?

| Item | Result |
|---|---|
| Pluto tip (agent) | `dad28ad…` / source `jbwapi_server_v2_rc3` |
| Won loaded revision | **`jbwapi_server_v2_rc3` (pid 31376)** |
| Move→`0x14` | Running (Move/`cmd_detail` move present; contrast stale rc2 `0x61`+order 6) |
| Pylon / supply | Still **9/9**, high minerals, no pylon — **not resolved** |

## Observe-log caveat (important)

Bridge `packet_hex` / `jbwapi_client_cmd_detail` only see commands that already sit in **`GameData.unitCommands`** after the client frame ACK.

- **Absence of Build `0c` / `kind":"build"` ≠ proof** PurpleWave never executed `bwapiUnit.build(...)`.
- It **does** mean: for the observed frames, **no Build UCT was in the shared-memory queue** when the bridge applied (or the pasted window omitted those frames — treat full paste through disconnect as the window).
- JBWAPI `Unit.build` → `issueCommand` → **`canIssueCommand` / `canBuild` may return false** and never call `game.addUnitCommand` — identical bridge signature to “never called.”

So prior label “A = PW never issues Build” **over-claimed**. Split by boundary below.

## Three boundaries

### 1) PurpleWave construction branch (`Build.scala` → `Commander.build`)

Only reaches `bwapiUnit.build` if:

- `intent.startNow` (`hasSpent \|\| currencyLock.satisfied`)
- `tile.visible`
- distance to building center `< 112`
- minerals/gas ≥ price − 8
- then `Commander.build` distance to `tile.center` ≤ 5×32 (else **Move again**)

**Hold reasons to record (next run):** startNow, tile, dist to center / tile.center, mins/gas vs price, reserved currency, canBuild judgment, whether code path enters immediately before `bwapiUnit.build`.

### 2) JBWAPI call boundary (`Unit.build` → `issueCommand`)

```text
build(type, tile)
  → issueCommand(UnitCommand.build)
       → if (!canIssueCommand) return false;   // NO queue insert
       → game.addUnitCommand(...)              // queue insert
```

`canBuild(type, tile)` ends with:

```java
return game.canBuildHere(tilePos, uType, this, /* checkExplored */ true);
```

Priority hypotheses to **test** (not patch around):

| Hyp | Mechanism |
|---|---|
| **H-explored** | `checkExplored=true` fails if Snapshot `isExplored` disagrees with screen |
| **H-buildable** | static `isBuildable` / occupancy / creep / power disagree with Won’s view |
| **H-blocker** | `getUnitsInRectangle` sees a blocker; builder is excluded by reference (`builder != u`) — verify mapping/identity |
| **H-path** | `builder.hasPath` to site fails |
| **H-id** | wrong unit / tile args at call |

**Distinguish:** `build()` never entered vs entered with `canBuild=false` / `issued=false` vs `issued=true`.

### 3) Command queue + bridge

| Signal | Layer |
|---|---|
| `jbwapi_client_commands_apply` + `unitCommandCount` | Queue nonempty (any UCT) |
| `jbwapi_client_cmd_detail` `kind":"build"` | Build UCT present in queue at apply (rc3+) |
| `packet_hex` …`0c`… | Legacy Build encoded + submitted |
| reject / unsupported / translate_rejects | Bridge drop/encode fail |

**Observed (rc3 paste summary):** Move/gather/train + `coords_patched`; **no** Build `0c` / no `kind":"build"` in window → **queue had no Build UCT** for those frames. That rules out **B/C for the observed window** only if the paste is complete; it does **not** rule out boundary-2 reject.

Log-filter note: `cmd_detail` is rate-capped (≤4 build/move lines per frame) but would still show **some** builds if any Build UCTs existed. Zero across ~2.7k frames ⇒ no Build in queue for that match, not a single-frame miss.

## Refined stage (evidence now)

| Stage | Meaning | Status |
|---|---|---|
| **A1** | PW never reaches `bwapiUnit.build` (Move-only / gates) | **Open** — needs PW `build-gate` / pre-call log |
| **A2** | `build` called; JBWAPI rejects before queue (`canIssueCommand`/`canBuildHere`) | **Open** — needs `canBuild`/`issued` log; **compatible** with observe log |
| **B** | Build in queue; bridge drops/rejects | **Unlikely in observe window** (no `kind":"build"`, no reject-of-Build) |
| **C** | `0c` submitted; SCR no construction | **Unlikely in observe window** (no `0c`) |
| **D** | Bad construction state feedback → idle/wait | **Hypothesis only** (could feed A1/A2) |

**Confirmed:** rc3 loaded; economy train/gather work; **no pylon**; observe layer shows **no queued Build**.  
**Not confirmed:** A1 vs A2. **Do not** treat observe-only as “PW never called build.”

## Changes / tests this turn

- **No** SCR/build-encoder patch; **no** force `canBuild` / forced Build inject.
- Refined classify doc + scoped **three-boundary diagnostics** for Won (see companion `purplewave-remaster-pylon-build-gate-diag.md`).
- No new gameless encoder test (encoder not under suspicion until A2 ruled out and Build enters queue).

## Next live run — exact lines Won must capture

1. Bridge: `jbwapi_server_started` → must still be **`jbwapi_server_v2_rc3`**.
2. Apply PW diag (companion doc) → `mvn -DskipTests package` → retest C.
3. Grep PW log:

```text
build-gate
build-precall
build-call
```

Interpretation:

| Pattern | Boundary |
|---|---|
| `build-gate why=startNow=false\|tile.notVisible\|dist=…\|minerals\|gas` and **no** `build-precall` | **A1** |
| `build-precall` + `build-call canBuild=false` / `issued=false` + reason fields | **A2** |
| `build-call issued=true` + bridge still no `kind":"build"` / `0c` | Queue/IPC anomaly → revisit **B** with full unfiltered log |
| `kind":"build"` + `0c` but no pylon | **C** (then — and only then — SCR construction path) |

4. Also note one stalled probe: frame, unit id, goal tile, order next frame, minerals, reserved if logged.
5. **PASS only** when Won sees pylon warp + supply > 9/9.
