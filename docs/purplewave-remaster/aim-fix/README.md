# PurpleWave Aim compile fix (tip 88c1056aa)

Upstream tip removed `Techniques.Aim` and added invalid `Aim(unit)` in `Fight.scala`, breaking:

```
Combat.scala: not found: value Aim
Fight.scala: not found: value Aim
```

## Correct destinations (do not shorten these paths)

There is **one** `Techniques.scala` in the repo. Package = `Micro.Actions.Combat.Decisionmaking`.

| Source in this folder | **Exact** destination under PurpleWave |
|---|---|
| `Techniques.scala` | `src\Micro\Actions\Combat\Decisionmaking\Techniques.scala` |
| `Fight.scala` | `src\Micro\Actions\Combat\Fight.scala` |

### Wrong path (causes `Techniques is already defined`)

```
src\Micro\Actions\Combat\Techniques.scala   ← WRONG — must not exist
```

If Maven reports `Techniques is already defined as object Techniques` at `Combat\Techniques.scala`, you copied into `Combat\` instead of `Combat\Decisionmaking\`.

## Exact fix for Won (after bad copy)

```powershell
cd C:\Games\PurpleWave

# 1) Delete the stray duplicate (wrong path)
Remove-Item -LiteralPath '.\src\Micro\Actions\Combat\Techniques.scala' -Force -ErrorAction SilentlyContinue

# 2) Copy into the Decisionmaking folder (correct path)
$src = 'C:\Games\PurpleWave-Remastered-BWAPI-Bridge\docs\purplewave-remaster\aim-fix'
Copy-Item "$src\Techniques.scala" `
  '.\src\Micro\Actions\Combat\Decisionmaking\Techniques.scala' -Force
Copy-Item "$src\Fight.scala" `
  '.\src\Micro\Actions\Combat\Fight.scala' -Force

# 3) Confirm only one Techniques.scala remains
Get-ChildItem -Recurse -Filter Techniques.scala .\src
# Expect exactly: ...\Combat\Decisionmaking\Techniques.scala

mvn -DskipTests package
```

## Option A — patch (avoids path mistakes)

```powershell
git -C C:\Games\PurpleWave apply `
  C:\Games\PurpleWave-Remastered-BWAPI-Bridge\docs\purplewave-remaster\aim-fix\0001-Restore-Aim-combat-Technique-removed-by-tip-88c1056a.patch
# If a stray Combat\Techniques.scala already exists, delete it first (step 1 above).
```

## What changed

1. Restore `object Aim extends Technique` in `Decisionmaking\Techniques.scala`
2. Allow `Fight` → `Aim` transition so `Combat.transition(Aim, !canMove)` can fire
3. Remove erroneous `Aim(unit)` from `Fight.scala` (Aim is not an `Action`; stand-and-shoot is `Combat.aim()`)

Agent local PurpleWave commit (unpushed): `311dac45ecd0e0c1747bf56b39945905c54020f8` on `cursor/purplewave-ai-remaster-connect-e83e`.
