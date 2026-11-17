# PurpleWave Remaster operator files

Cloud agent could not push to `cccwon2/PurpleWave` (GitHub App install lacks that repo).
Copy these into a local PurpleWave checkout on branch `cursor/purplewave-ai-remaster-connect-e83e`:

| This folder | Destination in PurpleWave |
|---|---|
| `REMASTER-JBWAPI.md` | `docs/REMASTER-JBWAPI.md` |
| `Run-Remaster.ps1` | `scripts/Run-Remaster.ps1` |
| `aim-fix/` | Apply before `mvn package` (see that README) |
| `matchstart-npe/` | **Required for Live C** — MatchStart empty-units / tryCatch NPE (see that README) |

Also add a short remaster pointer to PurpleWave `readme.md` (see Pluto `docs/OPERATOR-PURPLEWAVE-LIVE.md`).

Local PurpleWave commits (unpushed) on `cursor/purplewave-ai-remaster-connect-e83e`: Aim `311dac45e…`; matchstart 0001 `cde246fb7…`; self-null 0002 `cdcb5d869…`.  
Pluto companion tip (pushable): `42e6fdd…` clears `GameData.isReplay` each Snapshot.
