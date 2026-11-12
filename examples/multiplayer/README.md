# Multiplayer test

A real two-process test: a dedicated server and separate game clients that
join it over `127.0.0.1`, all with the Weaver agent. It covers what a
single-player world cannot show, because there the client and the
integrated server share one process (and one copy of every static value).

```powershell
powershell -ExecutionPolicy Bypass -File examples\multiplayer\build.ps1
powershell -ExecutionPolicy Bypass -File examples\multiplayer\run.ps1
```

`build.ps1` builds the `weavermp` mod into four mod folders: `mods-server`,
`mods-client` (the same jar), `mods-client-v101` (the mod at version
1.0.1), and `mods-none`. `run.ps1` starts the server once on port 25570 with
a fresh world `weaver-mp`, then runs four clients one after another:

| Client | Expected | Marker |
|---|---|---|
| `match`: same mod | Joins; the registry check passes before `PLAY`; the data registry and the synced data map arrive over the network (`DATA REGISTRIES synced`, `DATA MAPS synced`, not the same-process skip); a `RUNTIME` capability set on the server player reaches the client; a channel answers in both directions | `MP CLIENT RESULT ok=true` |
| `extra-item`: same mod with `WEAVER_MP_EXTRA_ITEM=1` (one more item) | Refused by the registry check, in configuration | `REGISTRY CHECK ok=false reason=registry minecraft:item differs from the server: not on the server weavermp:extra` |
| `version`: the mod at 1.0.1 | Refused by the handshake; the player sees the reason | `Client disconnected with reason: mod version mismatch: weavermp (client=1.0.1, server=1.0.0)` |
| `missing`: no mods | Refused by the handshake; the player sees the reason | `Client disconnected with reason: missing required mod: weavermp (server=1.0.0)` |

The run ends with `MP RESULT ok=true match=true extra-item=true version=true
missing=true`. Each client gets up to three attempts, because the game client
can crash natively on this machine (an access violation outside Java; see
docs/platform-ops.md). Logs are in `out/logs`. Gate step `H-multiplayer`
runs both scripts.

The server uses `work/server-26.3` (offline mode) with `--world weaver-mp`,
through the launcher's `-World` option, so the proof world is left alone.
