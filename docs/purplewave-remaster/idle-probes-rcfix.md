# Live C idle probes — Position.None right-click (bridge_808c.log)

## Decoded fact

Spam pairs were **not** Move — they were SCR **select (0x63) + right-click (0x60)** every frame for 4–5 workers:

| Hex prefix | Meaning |
|---|---|
| `6301c82c0000` | Select unit handle `0x2cc8` |
| `60007d207d082d0000b00000` | Right-click target mineral type **176**, **x=32000 y=32032** |

`32000/32032` = JBWAPI `Position.None` (unit-targeted commands leave x/y unset). Pluto AI encode always fills real mineral pixels; the JBWAPI path did not → SCR ignored gather; PurpleWave re-issued every frame (`sleep(1)` + never saw `orderTarget`) → high APM + idle probes. Train (`1f4000`) still worked (no target xy).

## Fix (Pluto `jbwapi_server_v2_rcfix`)

Before encode, if command has a target unit and sentinel coords, copy `GameData.units[target].positionX/Y`. Log field `coords_patched` on `jbwapi_unit_commands_result`.

No PurpleWave change required for this bug (rightClick→Right_Click_Unit is correct BWAPI usage).

## Won retest

```powershell
cd C:\Games\Pluto-Remastered-v2
git pull origin cursor/purplewave-ai-command-path-e83e
& .\scripts\build.ps1
# Confirm bridge log: "revision":"jbwapi_server_v2_rcfix"

# Same C1/C2 as before (PurpleWave with 0001+0002 + sync Run-Remaster)
```

PASS signals: probes **mine**; right-click hex **not** `60007d207d…`; `coords_patched>0`; still no merge / no PASS from Train-only.
