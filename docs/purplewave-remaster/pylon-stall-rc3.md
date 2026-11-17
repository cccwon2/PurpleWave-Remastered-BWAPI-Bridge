# Live C pylon / natural probe stall — Move encoding (v2_rc3)

## After rcfix

Gather + train OK. Pylon builder and a probe at natural still stalled (not PASS).

## Cause (confirmed vs Pluto Remaster path)

PurpleWave `Commander.move` → JBWAPI `UnitCommandType.Move` was encoded as legacy **`0x15` + `Orders::Move`**.

Pluto’s own opening encode (`src/opening/encode.h`) sends Move as **`0x14` ground right-click** (null target, type None) — the path proven on Remaster SCR. Attack-move stays `0x15`+AttackMove.

Builders approach the pylon tile via Move; scouts/expo probes use Move to the natural. Wrong opcode → walk/stall before `0x0c` PlaceProtossBuilding can matter.

Build packet format already matched Pluto (`0x0c` PlaceProtossBuilding + tiles). Position.None RC-fix path unchanged.

## Fix

Pluto revision **`jbwapi_server_v2_rc3`**: Move → `0x14` ground RC. Rate-limited `jbwapi_client_cmd_detail` for Build/Move.

## Won retest

```powershell
cd C:\Games\Pluto-Remastered-v2
git pull origin cursor/purplewave-ai-command-path-e83e
& .\scripts\build.ps1
# bridge: "revision":"jbwapi_server_v2_rc3"
# same C1/C2 PurpleWave (0001+0002 + sync)
```

Expect: mining still OK; builder walks + pylon warps; natural probe moves with purpose; Move hex as `60…` null-handle ground RC; `jbwapi_client_cmd_detail` build/move lines. Upload bridge.log if still stall. No merge / no PASS claim here.
