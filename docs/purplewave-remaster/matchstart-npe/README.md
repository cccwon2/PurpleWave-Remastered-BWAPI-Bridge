# Live C NPE fix — MatchStart before units / tryCatch null logger

## Symptom (Won Live C)

```
Exception in thread "JBWAPI Client" java.lang.RuntimeException: java.lang.NullPointerException
 at bwapi.BotWrapper.onFrame
Caused by: java.lang.NullPointerException
 at Lifecycle.PurpleWave.tryCatch(PurpleWave.scala:100)
 at Lifecycle.PurpleWave.$anonfun$onFrame$1(PurpleWave.scala:35)
 ...
```

Game list showed `0 | <SCR_PID> | 0 | …` then Connected. 64-bit Temurin JDK 8 still reaches this path (IPC OK).

## Root cause

1. Pluto Snapshot + JBWAPI correctly emit **MatchStart before UnitDiscover** (required so `Game.init` runs before `unitCreate`).
2. `EventHandler` → `onStart` runs while `Game.allUnits` still holds only static/initial units → **`game.self.getUnits()` is empty**.
3. `With.onStart` did `self.getUnits.maxBy(_.getHitPoints)` → empty `maxBy` throws.
4. That happened **before** `logger = new Logger`, so `PurpleWave.tryCatch` → `With.logger.onException` **NPEs**.
5. `PurpleEventListener` already set `bot = Some(...)` and swallowed the failure → next `MatchFrame` → `onFrame` → same tryCatch NPE (stack you saw).

**Not** “must wait forever for MatchStart” — MatchStart did fire. The bot must tolerate **onStart with no own units yet**, then run frames after init (`With.ready`).

## Exact Won steps (retest C)

```powershell
cd C:\Games\PurpleWave

# 0) Aim fix still required if not already applied (Decisionmaking path only)
Remove-Item -LiteralPath '.\src\Micro\Actions\Combat\Techniques.scala' -Force -ErrorAction SilentlyContinue
$bridge = 'C:\Games\PurpleWave-Remastered-BWAPI-Bridge\docs\purplewave-remaster'
Copy-Item "$bridge\aim-fix\Techniques.scala" `
  '.\src\Micro\Actions\Combat\Decisionmaking\Techniques.scala' -Force
Copy-Item "$bridge\aim-fix\Fight.scala" `
  '.\src\Micro\Actions\Combat\Fight.scala' -Force

# 1) Apply MatchStart NPE fix (prefer patch; or copy the three Lifecycle files)
git apply "$bridge\matchstart-npe\0001-Harden-MatchStart-init-for-Remaster-JBWAPI.patch"
# If apply fails (dirty tree), copy instead:
# Copy-Item "$bridge\matchstart-npe\With.scala" '.\src\Lifecycle\With.scala' -Force
# Copy-Item "$bridge\matchstart-npe\PurpleWave.scala" '.\src\Lifecycle\PurpleWave.scala' -Force
# Copy-Item "$bridge\matchstart-npe\PurpleEventListener.scala" '.\src\Lifecycle\PurpleEventListener.scala' -Force

# 2) Rebuild jar
git submodule update --init --recursive
mvn -DskipTests package

# 3) Prefer 32-bit JRE 8 for runtime (64-bit Temurin connected but is not the documented path)
$java32 = 'C:\Program Files (x86)\Java\jre1.8.0_XXX\bin\java.exe'   # edit

# 4) SCR + exclusive server (Pluto tip fab92626… / PR #44) — note SCR PID
cd C:\Games\Pluto-Remastered-v2
& .\scripts\start-pluto.ps1 `
  -StarCraft 'C:\Program Files (x86)\StarCraft\x86\StarCraft.exe' `
  -JbwapiServer -AllowUnvalidatedV2 -SpeedMs 42

# 5) PurpleWave client
cd C:\Games\PurpleWave
pwsh -File .\scripts\Run-Remaster.ps1 `
  -JavaPath $java32 `
  -GameWorkingDirectory 'C:\Program Files (x86)\StarCraft\x86' `
  -ServerProcessId <SCR_PID>

# 6) Start/join a match in SCR. Expect: no tryCatch NPE; bot continues past frame 0.
```

### PASS / FAIL signals

| Signal | Meaning |
|---|---|
| No `PurpleWave.tryCatch` NPE | Init + logging path fixed |
| Bridge `jbwapi_client_connected` | IPC OK (already seen on Live C) |
| Units move / produce; `jbwapi_unit_commands_result` `packets_submitted` > 0 | Full Live C PASS |
| `[PurpleWave] onFrame skipped: With.ready=false` spam | onStart still failing — paste full stderr stack |

PurpleWave push remains **403** for the cloud agent; keep this Bridge vendor folder as source of truth until Won pushes `cccwon2/PurpleWave`.

Agent local PurpleWave commit (unpushed): `cde246fb7612b669957184a322aa08fa5ab84530` on `cursor/purplewave-ai-remaster-connect-e83e`.
