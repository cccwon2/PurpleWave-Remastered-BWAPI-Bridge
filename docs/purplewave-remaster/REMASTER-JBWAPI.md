# PurpleWave on StarCraft: Remastered via Pluto JBWAPI server

PurpleWave is an unmodified JBWAPI client (`Lifecycle.Main` → `BWClient`). Against Remastered it attaches to Pluto-Remastered-v2 **exclusive** `jbwapi_server` mode (classic game list + GameData + 1-byte pipe).

## Pins / toolchain (do not assume Probe’s JVM)

| Item | Requirement | Notes |
|---|---|---|
| JBWAPI | `d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f` | Same pin as Connection Probe / PurpleWave submodule |
| Scala | 2.12.18 | `pom.xml` |
| Build JDK | 8+ able to compile Scala 2.12 | Maven is source of truth |
| Runtime JVM | **Prefer 32-bit JDK/JRE 8** | Upstream readme; Probe A/B used **64-bit** JRE for read-only connect only |
| Native | JNA 5.1.0 | Shared memory / named pipe |
| SCR | Remastered `1.23.10.13515` x86 | Pluto bridge gate |
| Server | Pluto `-JbwapiServer` (`jbwapi_server_v2`) | Applies client commands; **no** Pluto AIModule |

Discover bitness on the operator PC:

```powershell
& 'C:\Program Files (x86)\Java\jre1.8.0_XXX\bin\java.exe' -version
& 'C:\Program Files\Java\jre1.8.0_511\bin\java.exe' -version
```

## Exclusive control

| Mode | Who commands units |
|---|---|
| `-JbwapiServer` ON | PurpleWave (or Probe read-only) only |
| Server OFF (default Pluto) | Pluto AI only — do **not** start PurpleWave |

Never run Pluto DLL + JBWAPI client together.

## Build

```powershell
cd C:\Games\PurpleWave   # or your clone
git submodule update --init --recursive
mvn -DskipTests package
# Artifact: target\PurpleWave-1.0.jar
```

Gameless unit tests (optional, long): `mvn test` — not a live SCR claim.

## Run order (live — operator)

1. Build Pluto bridge with command-apply tip (see store `docs/purplewave-live-run.md`).
2. Start SCR + bridge with `-JbwapiServer` (no `-Pluto`).
3. Note SCR PID.
4. `pwsh -File scripts\Run-Remaster.ps1 -JavaPath <java> -GameWorkingDirectory <SCR\x86> -ServerProcessId <pid>`
5. Start / join a match in SCR.
6. Confirm bridge log stages `jbwapi_client_connected`, `jbwapi_client_commands_apply`, `jbwapi_unit_commands_result` with `packets_submitted` > 0 when the bot acts.

## MatchStart vs own units (Live C NPE)

JBWAPI processes **MatchStart → onStart** before **UnitDiscover**. Under Pluto Snapshot that means `game.self.getUnits()` is empty during `With.onStart`. Upstream PurpleWave used `maxBy` on that list, then `tryCatch` NPEd on a null `With.logger` (stack at `PurpleWave.scala:100`).

Apply `docs/purplewave-remaster/matchstart-npe/` before Live C retest. You do **not** need to delay the client until after MatchStart — you need the hardened init that uses start location + `With.ready` gating. MatchFrame before a successful onStart safely no-ops.

## Approximations / gaps

- Some `Use_Tech` / `Use_Tech_Position` / `Use_Tech_Unit` (non-Archon) orders need full TechType→Order tables; encoder **rejects** rather than faking success.
- Draw shapes from the client are ignored (no SCR overlay path this phase).
- Live PASS requires observed unit motion / production on operator PC — never from MOCK/build alone.
