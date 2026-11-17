# Live C NPE fixes — MatchStart tryCatch + null `game.self`

## Symptoms

### C1 — tryCatch crash (first Live C)

```
Caused by: java.lang.NullPointerException
 at Lifecycle.PurpleWave.tryCatch(PurpleWave.scala:100)
```

### C2 — after 0001 patch (second Live C)

```
[PurpleWave] exception before logger init:
java.lang.NullPointerException
 at ProxyBwapi.Players.Players$.get(Players.scala:8)
 at Lifecycle.With$.onStart(With.scala:146)
...
[PurpleWave] onStart returned without With.ready; MatchFrame will no-op until a successful init
[PurpleWave] onFrame skipped: With.ready=false
```

## Root cause

1. JBWAPI `Game.init` sets `Game.self = null` when `GameData.isReplay` is true.
2. Pluto Snapshot already rejects SCR replays but did **not** clear `data->isReplay` every frame; torn/stale shared-memory (worse with async unsafe) can leave the client in replay mode after MatchStart.
3. `Players.get(game.self)` NPEs on null → `With.ready` stays false → onFrame no-ops.
4. The first Live C stack at `tryCatch:100` was this same failure **masked**: original NPE before logger, then tryCatch NPEd on null logger.

MatchStart **did** fire. Do not “wait for MatchStart”; fix self resolution + clear `isReplay` on the server.

## Fixes

| Layer | Change |
|---|---|
| **Pluto** tip `42e6fdd…` | `Snapshot::update` sets `isReplay=false` (and `isBattleNet=false`) every in-game frame |
| **PurpleWave 0001** | empty-units `mapClock`, earlier logger, `With.ready`, null-safe tryCatch |
| **PurpleWave 0002** | null-safe `Players.get`; resolve self via mineral fingerprint when `game.self` null; rebuild enemies/allies; **retry full onStart from onFrame** until ready; `-Dpurplewave.jbwapi.sync=true` |
| **Run-Remaster.ps1** | always passes `-Dpurplewave.jbwapi.sync=true` (Probe-like sync) |

## Exact Won apply + rebuild (retest C)

```powershell
# --- Pluto (isReplay clear) ---
cd C:\Games\Pluto-Remastered-v2
git fetch origin
git checkout cursor/purplewave-ai-command-path-e83e
git pull
& .\scripts\build.ps1

# --- Bridge vendor (patches) ---
cd C:\Games\PurpleWave-Remastered-BWAPI-Bridge
git fetch origin
git checkout cursor/purplewave-ai-command-path-e83e
git pull
$b = 'C:\Games\PurpleWave-Remastered-BWAPI-Bridge\docs\purplewave-remaster'

# --- PurpleWave ---
cd C:\Games\PurpleWave

# Aim fix if still needed (Decisionmaking path only)
Remove-Item -LiteralPath '.\src\Micro\Actions\Combat\Techniques.scala' -Force -ErrorAction SilentlyContinue
Copy-Item "$b\aim-fix\Techniques.scala" '.\src\Micro\Actions\Combat\Decisionmaking\Techniques.scala' -Force
Copy-Item "$b\aim-fix\Fight.scala" '.\src\Micro\Actions\Combat\Fight.scala' -Force

# MatchStart patches (0001 then 0002; skip 0001 if already applied)
git apply "$b\matchstart-npe\0001-Harden-MatchStart-init-for-Remaster-JBWAPI.patch"
git apply "$b\matchstart-npe\0002-Resolve-null-game.self-at-MatchStart.patch"
# If apply fails, copy these over Lifecycle/ + Players.scala from matchstart-npe\:
#   With.scala PurpleWave.scala PurpleEventListener.scala Main.scala Players.scala
#   Players.scala → src\ProxyBwapi\Players\Players.scala
Copy-Item "$b\Run-Remaster.ps1" '.\scripts\Run-Remaster.ps1' -Force

git submodule update --init --recursive
mvn -DskipTests package

# Prefer 32-bit JRE 8
$java32 = 'C:\Program Files (x86)\Java\jre1.8.0_XXX\bin\java.exe'  # edit

# C1 — exclusive server (rebuild already done)
cd C:\Games\Pluto-Remastered-v2
& .\scripts\start-pluto.ps1 `
  -StarCraft 'C:\Program Files (x86)\StarCraft\x86\StarCraft.exe' `
  -JbwapiServer -AllowUnvalidatedV2 -SpeedMs 42
# note SCR PID

# C2 — client (Run-Remaster now forces sync)
cd C:\Games\PurpleWave
pwsh -File .\scripts\Run-Remaster.ps1 `
  -JavaPath $java32 `
  -GameWorkingDirectory 'C:\Program Files (x86)\StarCraft\x86' `
  -ServerProcessId <SCR_PID>

# Start/join match in SCR
```

### PASS signals

| Signal | Meaning |
|---|---|
| Console: `JBWAPI async=false forceSync=true` | Remaster sync path active |
| No `Players.get` / `tryCatch` NPE | self resolved |
| Optional warn: `game.self was null … using player id=` | fallback worked; Pluto isReplay tip still recommended |
| `With.ready` — no perpetual onFrame skip | init completed (possibly deferred one frame) |
| `packets_submitted` > 0 / visible orders | full Live C PASS |

PurpleWave push remains **403**. Local SHAs: `cde246fb7` (0001), `cdcb5d869` (0002) on `cursor/purplewave-ai-remaster-connect-e83e`.
Pluto tip: `42e6fddba801f0c9fe9749aceb3b3bbaf232b673`.
