# Full PurpleWave AI command path (Remaster)

Connection Probe in this repo stays **read-only**. Full AI control uses:

1. **Pluto-Remastered-v2** `-JbwapiServer` (`jbwapi_server_v2`) — publishes IPC and **applies** client `unitCommands` after frame ACK
2. **PurpleWave** `Lifecycle.Main` / `scripts/Run-Remaster.ps1` — issues real BWAPI commands over JBWAPI

Exclusive control:

| Flag | Commander |
|---|---|
| `-JbwapiServer` | PurpleWave (or Probe diagnostics) |
| default / server OFF | Pluto AI only |

Operator live-run checklist lives in the Project store: `docs/purplewave-live-run.md`.

Probe A/B baseline remains valid for connect/read-only validation; do not treat Probe PASS as AI command PASS.
