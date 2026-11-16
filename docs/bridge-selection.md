# Remaster bridge candidate selection (read-only audit)

Audit date: 2026-11-16  
Probe target client: JBWAPI pin `d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f`, `SUPPORTED_BWAPI_VERSION = 10003`, `GameData.SIZE = 33017048`  
Method: GitHub API file reads (private repos). Local `git clone` without owner token failed (`Repository not found`); no files in those repos were modified.

**Same version number alone ≠ compatible.** Layout/offsets and whether a classic shared-memory **server** is published must match.

## Classification key

| Label | Meaning |
|---|---|
| existing server reusable, needs live validation | Publishes classic BWAPI game table + mapping + pipe; needs SCR live test |
| needs some compatibility layer | Close but incomplete vs JBWAPI expectations |
| Pluto-only IPC → adapter or server extension | In-process / custom path; external JBWAPI cannot attach without new work |
| insufficient source/binary → cannot judge | Not enough evidence |

---

## Candidate A — `cccwon2/Pluto-Remastered-v2`

| Field | Finding | Evidence |
|---|---|---|
| URL | https://github.com/cccwon2/Pluto-Remastered-v2 | repo metadata |
| Inspected commit | `867f7f1a697041cb3d2ec299dfa6f71e876430fd` (`main` tip at audit) | `list_commits` |
| License / provenance | Vendored BWAPI under LGPL v3 retained in tree; MinHook; Pluto DLL external | `THIRD_PARTY.md` |
| SCR version + arch | **StarCraft: Remastered `1.23.10.13515` x86**; bridge built **Win32/x86** | `README.md` |
| Build / run | `scripts/build.ps1` → `bin/pluto-scr.dll` + `scr-loader.exe`; `scripts/start-pluto.ps1` loads DLL into SCR | `README.md`, `scripts/start-pluto.ps1` |
| BWAPI shared-memory **server** present? | **No (for external clients)** | Bridge injects `pluto-scr.dll` and builds `GameImpl` on local `Snapshot` memory |
| `Local\bwapi_shared_memory_game_list`? | **Not published by bridge path** | No `CreateFileMapping`/`bwapi_shared_memory_game_list` in `src/bridge.cpp`; classic names appear only in vendored **client** `vendor/bwapi/BWAPIClient/Source/Client.cpp` |
| Per-server mapping + named pipe? | **Not as external server** | Same: client connector code vendored; runtime uses in-process pointer |
| Client version 10003? | **Yes in vendored headers** | `vendor/bwapi/include/BWAPI.h`: `const int CLIENT_VERSION = 10003;` |
| GameData size + field layout match? | **Layout source matches classic BWAPI 4.4.0 client struct**; exact `sizeof` vs JBWAPI `33017048` **not measured in this audit** | `vendor/bwapi/include/BWAPI/Client/GameData.h` (fields start with `client_version`, units[10000], etc.); BWAPI commit `7687da8…` per `THIRD_PARTY.md` |
| Frame sync + match events? | **Yes in-process** | `bridge.cpp` `load_bot()` / `run_bot()`: `api_game->onMatchStart()`, `onMatchFrame()`, AIModule events |
| Pluto-only vs generic BWAPI? | **Pluto-only runtime IPC** | `BWAPI::BWAPIClient.data = snapshot->data.get(); api_game = make_unique<GameImpl>(snapshot->data.get());` then `LoadLibrary` Pluto DLL + `newAIModule` — no external JBWAPI attach surface |

### Classification

**Pluto-only IPC → adapter or server extension**

### Why JBWAPI cannot attach today

JBWAPI `Client.connect()` requires:

1. `OpenFileMapping("Local\\bwapi_shared_memory_game_list")`
2. `\\.\pipe\bwapi_pipe_<pid>`
3. `Local\bwapi_shared_memory_<pid>`
4. version `10003`

Pluto v2 satisfies (4) in headers and builds a compatible-looking `GameData` in process memory, but does **not** expose (1)–(3) to another process. Wiring `BWAPIClient.data` to the snapshot is an in-process shortcut for Pluto’s AIModule, not a shared-memory server.

### Minimal design if reusing Pluto (proposal only — not implemented)

1. **Inside `pluto-scr.dll` (Pluto-Remastered-v2):** after Snapshot updates, optionally publish classic server surfaces: create game table mapping, per-PID GameData mapping (or mirror), and named pipe with frame `1`/`2` protocol; set `client_version = 10003`.
2. **Keep Pluto AIModule path unchanged** so Control Center / Pluto DLL keep working.
3. **Validate** with this repo’s probe (read-only) before any PurpleWave AI.
4. **Repos to change:** primarily `Pluto-Remastered-v2` (`bridge.cpp` / new server module). Control Center only if launch flags are needed. This probe repo stays client-side.
5. **Risks:** x86 SCR + possibly x64 Java (JBWAPI claims both; unverified); `sizeof(GameData)` vs JBWAPI `SIZE`; dual consumers (Pluto DLL + external client) on one GameData; injection/hooks already exist in Pluto — do not invent new unauthorized hooks elsewhere.

---

## Candidate B — `cccwon2/Pluto-Remastered-ControlCenter`

| Field | Finding | Evidence |
|---|---|---|
| URL | https://github.com/cccwon2/Pluto-Remastered-ControlCenter | repo metadata |
| Inspected commit | `149954852fe395eaea230ea0fcd6d643c1f3fbda` | `list_commits` |
| License | **MIT** | `LICENSE` |
| Role | Korean WPF GUI launcher / consent / telemetry | `README.md` description; `InvokeBridge.ps1` |
| SCR / arch | Delegates to bridge script; does not implement BWAPI | `src/PlutoControlCenter.App/Scripts/InvokeBridge.ps1` calls `$BridgeScript` (`start-pluto.ps1`) |
| Shared-memory server? | **No** | No BWAPI GameData/server sources in tree |
| Client 10003 / GameData? | **N/A** (launcher) | — |

### Classification

**insufficient as a BWAPI server** (launcher only). Useful later for operator UX, not for JBWAPI IPC.

---

## Candidate C — this repo / docs-mentioned “BWAPI-Bridge”

| Field | Finding |
|---|---|
| `PurpleWave-Remastered-BWAPI-Bridge` | Name only; contains **probe client**, no Remaster server binaries |
| Docs | `compatibility-audit.md` already records absent bridge |

### Classification

**insufficient source/binary → cannot judge** as a server (by design this PR is client foundation).

---

## Recommended connection approach

1. **Near-term:** Treat **Pluto-Remastered-v2** as the only inspected SCR-native runtime with BWAPI-shaped `GameData` + `CLIENT_VERSION 10003`, but require a **server extension** (or separate classic BWAPI Remaster host) before JBWAPI/PurpleWave can connect.
2. **Do not** assume Control Center or repo naming provides IPC.
3. **Do not** large-implement the server in this PR. Next implementation scope belongs in Pluto-v2 (or a dedicated bridge) after operator confirms SCR build + willingness to extend.
4. **Validation order:** (a) Windows game-less CI for probe, (b) Pluto build on operator PC, (c) decide server-extension vs alternate bridge, (d) probe live against published mappings, (e) only then PurpleWave AI.

## Remaining uncertainty

- Exact `sizeof(BWAPI::GameData)` in Pluto’s MSVC x86 build vs JBWAPI `33017048`
- Whether mirroring Snapshot to shared memory is frame-safe with Pluto DLL also attached
- Java 32 vs 64 against x86 SCR session objects (docs disagree; untested)
- Live Remastered behavior of Pluto tip `867f7f1…` (README still marks gameplay validation limits)
