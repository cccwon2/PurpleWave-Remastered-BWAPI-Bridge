# Live C bridge_8b8a — still `jbwapi_server_v2_rc2` (Move fix not loaded)

## Log facts (pid 29084)

| Check | Result |
|---|---|
| `jbwapi_server_started.revision` | **`jbwapi_server_v2_rc2`** (not rc3) |
| `coords_patched` | >0 (gather RC-fix path live) |
| Build packets `0c` | **0** |
| `jbwapi_client_cmd_detail` | **0** (rc3-only) |
| Move packets | **170×** SCR `0x61` with order **6** (`…e4000600`) = old Move encode |
| Ground RC Move (`0x60` null handle) | **0** |
| Screenshot context | 9/9 supply, ~552 minerals @ ~1:46 → no pylon |

**Conclusion:** tip `1e521a6` Move→`0x14` never entered the injected DLL. Zero Build packets because probes never got into PW’s build-range gate while Move used broken `0x15`+Orders::Move.

## Deploy path (correct)

```
git tip → scripts/build.ps1
  → build/Release/pluto-scr.dll
  → copy → bin/pluto-scr.dll (+ bridge-build.json with jbwapi_server_revision)
start-pluto.ps1 -JbwapiServer
  → Copy-Item bin/pluto-scr.dll → runtime-v2/pluto-scr.dll
  → inject runtime-v2/pluto-scr.dll into SCR
```

Stale if: pull without rebuild; rebuild other clone; StarCraft left running with old inject; start-pluto skipped after build.

## Won exact verify (before C1)

```powershell
cd C:\Games\Pluto-Remastered-v2
git fetch origin
git checkout cursor/purplewave-ai-command-path-e83e
git pull origin cursor/purplewave-ai-command-path-e83e
git rev-parse HEAD
# MUST be 1e521a6b5fd13efdc6e09e774e8609e115a8b6bd or later tip that still has rc3

# Confirm source string
Select-String -Path .\src\jbwapi_server.h -Pattern 'revision ='

# Exit ALL StarCraft / orphan SCR first, then:
& .\scripts\build.ps1
# Host must print: JBWAPI server revision in bin/pluto-scr.dll: jbwapi_server_v2_rc3

# Optional ASCII check
Select-String -Path .\bin\pluto-scr.dll -Pattern 'jbwapi_server_v2_rc3' -Encoding byte -ErrorAction SilentlyContinue
findstr /C:"jbwapi_server_v2_rc3" .\bin\pluto-scr.dll
findstr /C:"jbwapi_server_v2_rc3" .\bin\bridge-build.json

# Launch (start-pluto now refuses bin DLL missing marker revision)
& .\scripts\start-pluto.ps1 ... -JbwapiServer ...
# Console: JBWAPI server DLL revision: jbwapi_server_v2_rc3

# First bridge line after attach:
# {"stage":"jbwapi_server_started","revision":"jbwapi_server_v2_rc3",...}
# If still rc2 → STOP; wrong bin/runtime; do not retest gameplay.
```

After rc3 confirmed: expect Move as `60` null-handle, `jbwapi_client_cmd_detail` build/move, then Build `0c` when probe in range. No PASS until Won verifies pylon.
