# Weaver hooks proof

Real Minecraft 26.3 proof for the 2.2 hook set. It runs one mod on a dedicated
server and on a client with an integrated server.

## What it proves

The dedicated server runs five required checks on the first server tick after
a `ServerLevel` is known:

1. `Entity hurt cancellation` - a real zombie (falling back to an armor stand)
   is spawned, `hurtServer` is called with a typed `EntityHurtEvent` listener
   that cancels the event, and the proof requires `hurtServer` to return
   `false` with unchanged health.
2. `Entity hurt allowed` - a second call is not canceled, returns `true`, and
   reduces health.
3. `Living entity death hook` - `LivingEntity.die` is called and the typed
   `LivingEntityDeathEvent` listener observes the target.
4. `Chunk load hook` - a fresh chunk is loaded far from spawn and the typed
   `ChunkLoadEvent` listener observes it.
5. `Entity tick hook` - a spawned target is observed through
   `EntityTickEvent` at the end of the server tick.

The dedicated server has no player, so the player-dependent checks are
deliberately audit-only:

```text
Container close audit-only reason=no-player
Anvil update audit-only reason=no-player
Enchant hook audit-only reason=no-player
Entity attack audit-only reason=no-player
Block break audit-only reason=no-player
```

The client with an integrated server runs the same five required checks and
replaces the audit-only lines with real observations:

- `Container close hook` - opens a one-row chest menu through a real
  `ServerPlayer`, closes it, and observes a non-null menu in
  `ContainerCloseEvent`.
- `Anvil update hook` - creates a real `AnvilMenu` and calls `createResult`.
- `Enchant hook` - creates a real `EnchantmentMenu` and calls
  `clickMenuButton`.
- `Entity attack hook` - calls `Player.attack` on a spawned target and
  observes `EntityAttackEvent`.
- `Block break hook` - calls `ServerPlayerGameMode.destroyBlock` on a real
  block position and observes `BlockBreakEvent`.

The mod also installs listeners for `PlayerJoinEvent` and `PlayerTeleportEvent`
for observation markers, but neither is a counted check in this proof.

## Prerequisites

- JDK 26 through `JAVA_HOME` or `C:\Program Files\Java\jdk-26.0.1`.
- `work/game-26.3/classpath.txt` (override with `WEAVER_GAME_DIR`).
- The agent jar at `core/build/agent/weaver-agent.jar` and the agent lib at
  `core/build/agent/lib`.
- An `api-game` jar under `api-game/build/libs` or in the agent lib.
- `work/server-26.3` for the dedicated server (override with
  `WEAVER_SERVER_DIR`).
- `examples/proof/fixture-world/level.dat` for the deterministic client world.
  If it is absent, the client script falls back to `WEAVER_CLIENT_WORLD` or
  `New World (2)`.

## Build

From `examples\hooks-proof`:

```powershell
.\build.ps1
```

Or from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\hooks-proof\build.ps1
```

The script compiles against the real game classpath, the agent lib, and the
API-game jar, copies `weaver.mod.json`, and writes
`out/mods/hooks-proof.jar`. Expected marker:

```text
HOOKS PROOF BUILD ok=true jar=...\out\hooks-proof.jar mods=...\out\mods
```

## Run the dedicated server

```powershell
.\run-server.ps1
```

The server uses port 25566 by default; set `WEAVER_SERVER_PORT` to override it.
The runner waits up to 180 seconds. Expected markers:

```text
HOOKS SERVER START dir=... port=25566 log=...\out\logs\server.log
HOOKS SERVER RESULT ok=true log=...\out\logs\server.log
PROOF RESULT ok=true side=server checks=10 check_names=...
```

The server `check_names` are `Entity hurt cancellation`, `Entity hurt allowed`,
`Living entity death hook`, `Chunk load hook`, `Entity tick hook`, and the five
`audit-only reason=no-player` lines.

## Run the client

```powershell
.\run-client.ps1
```

The client uses the integrated server and quick-plays
`WeaverHooksProofFixture`, copied from `examples/proof/fixture-world` into
`work/game-26.3/saves` before launch. Set `WEAVER_CLIENT_WORLD` to use another
world. The runner waits up to 300 seconds. Expected markers:

```text
HOOKS CLIENT FIXTURE world=WeaverHooksProofFixture source=...\examples\proof\fixture-world
HOOKS CLIENT RESULT ok=true log=...\out\logs\client.log markers=...\out\logs\client-markers.log
PROOF RESULT ok=true side=client checks=10 check_names=...
```

The client `check_names` are `Entity hurt cancellation`, `Entity hurt allowed`,
`Living entity death hook`, `Chunk load hook`, `Entity tick hook`,
`Container close hook`, `Anvil update hook`, `Enchant hook`,
`Entity attack hook`, and `Block break hook`.

## Run both

```powershell
.\run-all.ps1
```

`run-all.ps1` builds, runs the server, then runs the client, and prints:

```text
HOOKS PROOF RESULT server=ok client=ok
```

A failed stage prints `HOOKS SERVER RESULT ok=false` or
`HOOKS CLIENT RESULT ok=false`, tails the relevant log, and exits 1.

## Ports and output layout

```text
examples/hooks-proof/out/
  hooks-proof.jar
  mods/hooks-proof.jar
  logs/server.log
  logs/server-markers.log
  logs/client.log
  logs/client-markers.log
  server-config/
  server-data/
  client-config/
  client-data/
```

The server port is 25566. The client runs an integrated server inside the
client process and does not take a port argument. Other proofs use 25566 and
25567; run the game-backed proofs sequentially because they share the
`work/server-26.3/weaver-test` world.

## Limitations

- The dedicated server has no player. Container close, anvil, enchant, entity
  attack, and block break are audit-only there; the client integrated-server
  run is required for the real player paths.
- The anvil and enchant checks call the real menu methods on real menu objects,
  but they do not simulate a player clicking through the anvil or enchanting
  screen. The container check does open and close a real chest menu.
- Entity damage and death observations cannot change the death result. Entity
  hurt is the cancelable check; the event listener cancels only the marked
  target.
- If both the zombie and armor-stand spawn fail, the hurt and death checks
  fail rather than silently passing. The proof does not treat a spawn failure as
  success.
- Block/item interaction, item use finish and release, projectile launch,
  teleport results, block place, entity removal, block entity load and save,
  and the respawn target/action have no dedicated check here. The teleport
  listener only emits an observation marker.
- Hooks target Minecraft 26.3 internal names and descriptors. The loader fails
  soft on a missing or invalid target, but the corresponding proof check then
  fails.
- The client run depends on the host graphics driver, offline authentication,
  and the fixture world. It is not rerun by the dedicated-server proof.
