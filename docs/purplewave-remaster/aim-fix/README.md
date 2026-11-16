# PurpleWave Aim compile fix (tip 88c1056aa)

Upstream tip removed `Techniques.Aim` and added invalid `Aim(unit)` in `Fight.scala`, breaking:

```
Combat.scala: not found: value Aim
Fight.scala: not found: value Aim
```

## Apply on Windows (PurpleWave clone)

From `C:\Games\PurpleWave` (or your clone), on any branch based on `88c1056aa` / `master`:

### Option A — patch

```powershell
git apply docs\purplewave-remaster\aim-fix\0001-Restore-Aim-combat-Technique-removed-by-tip-88c1056a.patch
# If applying from Bridge checkout instead:
git -C C:\Games\PurpleWave apply C:\Games\PurpleWave-Remastered-BWAPI-Bridge\docs\purplewave-remaster\aim-fix\0001-Restore-Aim-combat-Technique-removed-by-tip-88c1056a.patch
```

### Option B — copy files

```powershell
Copy-Item ...\aim-fix\Techniques.scala `
  C:\Games\PurpleWave\src\Micro\Actions\Combat\Decisionmaking\Techniques.scala -Force
Copy-Item ...\aim-fix\Fight.scala `
  C:\Games\PurpleWave\src\Micro\Actions\Combat\Fight.scala -Force
```

Then:

```powershell
mvn -DskipTests package
```

## What changed

1. Restore `object Aim extends Technique` in `Techniques.scala`
2. Allow `Fight` → `Aim` transition so `Combat.transition(Aim, !canMove)` can fire
3. Remove erroneous `Aim(unit)` from `Fight.scala` (Aim is not an `Action`; stand-and-shoot is `Combat.aim()`)

Agent local PurpleWave commit (unpushed): `311dac45ecd0e0c1747bf56b39945905c54020f8` on `cursor/purplewave-ai-remaster-connect-e83e`.
