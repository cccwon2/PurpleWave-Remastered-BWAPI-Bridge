# PurpleWave ↔ Remastered connection compatibility audit

Status date: 2026-11-16 (cloud agent investigation).  
Scope: connection foundation only — not a full bot port, not a Remastered bridge implementation.

## 1. Repos / commits inspected

| Artifact | Location | Commit / ref | Role |
|---|---|---|---|
| This repo | `https://github.com/cccwon2/PurpleWave-Remastered-BWAPI-Bridge` | local `feat/purplewave-connection-probe` (from empty `main`) | Probe + docs |
| PurpleWave fork (read-only) | `https://github.com/cccwon2/PurpleWave` | `88c1056aac722fd648ca81e5746da25ed8123c3e` (`master`) | Bot lifecycle / pin source |
| Upstream PurpleWave | `https://github.com/dgant/PurpleWave` | same tip as fork at audit time | Origin of fork |
| JBWAPI (pinned) | `https://github.com/JavaBWAPI/JBWAPI` | **`d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f`** | Exact submodule SHA in PurpleWave |
| Remaster bridge sources/binaries in this repo | — | **absent** | No bridge to compare |

Evidence for JBWAPI pin:

```text
# PurpleWave .gitmodules
[submodule "3rdparty/jbwapi"]
	path = 3rdparty/jbwapi
	url = https://github.com/JavaBWAPI/JBWAPI

# git ls-tree HEAD 3rdparty/jbwapi
160000 commit d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f  3rdparty/jbwapi
```

This probe repo vendors the same commit via submodule `3rdparty/jbwapi`.

## 2. Pre-work check (this workspace)

| Item | Observation |
|---|---|
| cwd | `/agent/repos/PurpleWave-Remastered-BWAPI-Bridge` |
| remotes | `origin` → `https://github.com/cccwon2/PurpleWave-Remastered-BWAPI-Bridge` |
| initial branch / tree | `main`, clean; only `README.md` (“# PurpleWave-Remastered-BWAPI-Bridge”) |
| build files / sources / submodules / scripts (initial) | **none** |
| entry points (initial) | **none** — repo name does not imply an existing bridge |
| Java | OpenJDK **21** 64-bit (`/usr/lib/jvm/java-21-openjdk-amd64`) present |
| Maven | not present initially; installed in agent session for build |
| PowerShell | not present initially; PowerShell 7.7 installed in agent session for script smoke tests |
| OS | Linux x86_64 cloud VM |
| StarCraft: Remastered + BWAPI bridge on this VM | **No** — cannot run real IPC here |

## 3. PurpleWave run flow (evidence)

| File | Evidence |
|---|---|
| `src/Lifecycle/Main.scala` | Builds `BWClientConfiguration` (async frame buffer, `withDebugConnection(true)`, `withAutoContinue(false)`), then `PurpleBWClient.startGame(jbwapiConfiguration)`. |
| `src/Lifecycle/PurpleBWClient.scala` | `object PurpleBWClient extends BWClient(PurpleEventListener)` |
| `src/Lifecycle/PurpleEventListener.scala` | Implements `BWEventListener`; creates `PurpleWave` bot inside `onStart()`. |
| `pom.xml` | Compiles `3rdparty/jbwapi/src/main/java` as sources (not Maven Central artifact). Shade main class `Lifecycle.Main`. Java **1.8** compiler target. Scala 2.12.18. JNA 5.1.0. |
| `readme.md` | Requires **32-bit JDK 8** “because it connects to 32-bit BWAPI”. Working directory: sibling `../bw` or StarCraft dir; create `bwapi-data/{AI,read,write}`. |
| `PurpleWave.bat` | Hard-coded example Java path under `C:\Program Files (x86)\Java\...` (user-local; not used by this probe). |

## 4. JBWAPI (`d6003b0`) protocol / API facts

Per-item verdict: **confirmed** / **mismatch** / **unknown**.

### 4.1 BWClient construct / start / stop

| Item | Verdict | Evidence |
|---|---|---|
| Construct | **confirmed** | `new BWClient(BWEventListener)` — `BWClient.java` |
| `startGame()` / `startGame(BWClientConfiguration)` | **confirmed** | Validates config, `client.reconnect()`, then loops while `isInGame` / not in game |
| Public `stop()` / graceful disconnect API | **confirmed absent** | No public stop method on `BWClient`. `Client.disconnect()` is package-private and only used internally on pipe/version failure |
| Timeout support inside JBWAPI | **confirmed absent** | `Client.reconnect()` loops forever: `while (!connect()) sleep(1000)` |

Implication: match-start wait must be supervised externally (probe watchdog + PowerShell stop of **this script’s probe PID only**).  
Public API cannot observe IPC connect separately — supervisor wait is a **match-start wait** (`MATCH_STARTED` / `onStart`), not a pure connect wait.

### 4.2 Listener registration

| Item | Verdict | Evidence |
|---|---|---|
| Registration | **confirmed** | Listener passed to `BWClient` constructor; dispatched by `EventHandler.operation` from shared-memory events |
| Connect callback | **confirmed absent** | `BWEventListener` has match/unit/text callbacks only — no `onConnect` |
| Match start | **confirmed** | `EventType` → `onStart()` when game becomes active and events are processed via `BotWrapper` |
| Match end | **confirmed** | `onEnd(boolean)` |

**IPC connect vs match start:** public API cannot cleanly expose a separate `CONNECTED` state to bots. `Client.connect()` prints “Connection successful” and sets `connected=true` before `isInGame` becomes true, but that flag is not on the listener surface. This probe therefore emits `WAITING_FOR_BRIDGE` until `onStart` → `MATCH_STARTED`, and does **not** fabricate `CONNECTED`.

### 4.3 Wait loop / disconnect handling

| Item | Verdict | Evidence |
|---|---|---|
| Pre-game wait | **confirmed** | `while (!liveGameData.isInGame()) { sendFrameReceiveFrame(); ... }` |
| In-game loop | **confirmed** | `botWrapper.onFrame(); flush side effects; sendFrameReceiveFrame()` |
| Disconnect mid-game | **confirmed** | If `!client.isConnected()`, prints “Reconnecting...” and `client.reconnect()` |
| Auto next game | **confirmed** | Controlled by `configuration.getAutoContinue()` (PurpleWave sets false) |

### 4.4 Shared memory / pipe names (do not invent others)

| Object | Verdict | Exact name / formula |
|---|---|---|
| Game table mapping | **confirmed** | `Local\bwapi_shared_memory_game_list` |
| Per-server shared memory | **confirmed** | `Local\bwapi_shared_memory_<serverProcID>` |
| Per-server pipe | **confirmed** | `\\.\pipe\bwapi_pipe_<serverProcID>` |
| Optional server PID override | **confirmed** | Java system property `bwapi.serverProcessId` |
| Game table shape | **confirmed** | 8 × `GameInstance` (serverProcessID int, isConnected byte/bool, lastKeepAliveTime int) — `GameTable.java` |
| Sync object / mutex names beyond above | **unknown** in JBWAPI client sources | Client uses mapping + pipe bytes `1` (frame done) / `2` (frame ready) only |

### 4.5 IPC version checks

| Item | Verdict | Evidence |
|---|---|---|
| Client constant | **confirmed** | `Client.SUPPORTED_BWAPI_VERSION = 10003` |
| Server field | **confirmed** | `clientData.gameData().getClient_version()` compared; mismatch → disconnect |
| GameData size | **confirmed** | `ClientData.GameData.SIZE = 33017048` (matches PurpleWave `JBWAPIClientDataSizeBytes`) |

### 4.6 Read-only data shapes used by the probe

| Field | API | Notes | Verdict |
|---|---|---|---|
| Frame | `Game.getFrameCount()` | Does not increase while paused | **confirmed** |
| Player id | `Player.getID()` | From `game.self()` | **confirmed** |
| Race | `Player.getRace()` | Enum | **confirmed** |
| Minerals / gas | `Player.minerals()` / `gas()` | Current spendable amounts | **confirmed** |
| Supply used/total | `Player.supplyUsed()` / `supplyTotal()` | **BWAPI internal units**; Javadoc: display values are double what UI shows (Zergling 0.5) → display ≈ internal/2 | **confirmed** |
| Self unit count | `Player.getUnits().size()` | Accessible units for self | **confirmed** |

Commands / speed changes: **not used** by the probe (out of scope / disallowed).

### 4.7 Java arch / native dependency requirements

| Claim | Source | Verdict |
|---|---|---|
| “PurpleWave requires 32-bit JDK 8 because it connects to 32-bit BWAPI” | PurpleWave `readme.md` | **documented claim** (bot project policy) |
| “Supports both 32 and 64 bit Java” | JBWAPI `README.md` @ `d6003b0` | **documented claim** (library) |
| JBWAPI IPC uses Windows `Kernel32.OpenFileMapping` + `RandomAccessFile` on named pipes via JNA | `Client.java` | **confirmed** — Windows session APIs |
| “Game is x86 ⇒ Java must be x86 for this combo” | — | **not asserted here** — docs disagree; needs Windows Remastered experiment |
| “64-bit Java + this JBWAPI commit works against Remastered bridge” | — | **unknown** — not tested |
| JNA versions | PurpleWave pom **5.1.0**; JBWAPI pom @ d6003b0 declares **5.6.0** | **mismatch between upstream projects**; probe **aligned to PurpleWave 5.1.0** for baseline build/test reproducibility (JBWAPI commit unchanged). 5.6.0-vs-5.1.0 behavioral delta on Remastered: **unverified** |
| Works on Linux cloud VM | — | **confirmed no** for real IPC (no Kernel32 mappings) |
| Safe argv (spaces/Korean) | PS7 `ProcessStartInfo.ArgumentList.Add` | **confirmed** in harden tests; `Start-Process -ArgumentList` array on PS7 **splits** spaces (do not use) |
| Supervisor match-start vs overall | `scripts/ProbeSupervisor.ps1` + `Run-Probe.ps1` | **confirmed**: pre-match wait stops applying after runId-scoped `MATCH_STARTED`; overall limit only if `OverallRunTimeoutMs` > 0 |

## 5. Bridge-side comparison

| Question | Result |
|---|---|
| Does this repo contain Remastered bridge sources/binaries? | **No** |
| Are IPC names/structs invented here? | **No** — only names present in JBWAPI `Client.java` / `GameTable.java` are documented |
| Can client-only work proceed? | **Yes** — read-only probe against stock JBWAPI client protocol |
| Needed from a bridge repo (open questions) | See §8 |

## 6. Docs vs sources differences

1. **Java bitness:** PurpleWave README mandates 32-bit JDK 8; JBWAPI README advertises 32/64. Treat as unresolved for Remastered until measured.
2. **JNA version:** PurpleWave pins 5.1.0 while compiling JBWAPI that declares 5.6.0 — historical drift inside PurpleWave; probe uses 5.6.0 with JBWAPI sources.
3. **Repo name “BWAPI-Bridge”:** empty at start — naming ≠ implementation.
4. **CONNECTED state:** common bot docs imply “connect then play”; JBWAPI listener API only signals match start/end/frames.

## 7. Bot-side vs bridge-side change lists

### Bot-side (this repo / future PurpleWave Remastered work)

- [x] Minimal read-only connection probe on pinned JBWAPI
- [x] Scripts for env/build/run with timeouts
- [x] Offline tests for config/JSONL/timeout reporting
- [ ] Decide Java 8 x86 vs x64 runtime after real-env evidence
- [ ] Later: PurpleWave AI bring-up (explicitly out of this task)

### Bridge-side (external; **not in this repo**)

- Provide a BWAPI-compatible server that publishes:
  - `Local\bwapi_shared_memory_game_list`
  - `Local\bwapi_shared_memory_<pid>`
  - `\\.\pipe\bwapi_pipe_<pid>`
- Speak client version **10003** (or deliberately revise both sides together — out of scope to upgrade JBWAPI past pin)
- Populate `GameData` layout matching `ClientData.GameData.SIZE` / field offsets at this commit
- Target StarCraft: Remastered local process model (injection/hooks **disallowed** in this task — bridge approach must come from an authorized bridge project)
- Document Remastered binary arch and whether 32/64 Java clients map the same Local\ objects

## 8. Real-env testability

| Check | This cloud VM | Windows + SCR + bridge (future) |
|---|---|---|
| Build probe JAR | yes | yes |
| Script smoke tests | yes (pwsh) | yes |
| IPC connect | no | required |
| Match start / frame increase | 미실행 | required |
| Resources/units vs screen | 미실행 | required |
| Match end | 미실행 | required |
| No leftover probe process after timeout | partial (Linux fail-fast / script kill) | required |

## 9. Next blockers

1. **Obtain authorized Remastered BWAPI bridge** sources/binaries/version notes compatible with client version `10003` and the shared-memory layout above — or an explicit compatibility matrix if the bridge speaks a different version (would require a coordinated pin change, not done here).
2. **Windows machine** with StarCraft: Remastered + that bridge + Java for real probe runs.
3. **Resolve Java bitness** experimentally; do not trust either README alone.
4. Confirm working-directory / `bwapi-data` expectations for the Remastered install layout (PurpleWave assumes classic Brood War paths).
