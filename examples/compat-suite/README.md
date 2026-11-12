# Weaver compatibility suite

A real Weaver mod, `weavercompatsuite`, loaded on a real Minecraft 26.3
dedicated server. It combines the base event bus, the registry API, typed
`api-game` events, and game hooks in one fixture so a single run detects
cross-feature breakage at the integration boundary.

## What it proves

The run ends with 20 checks:

1. The mod's local bus is distinct from `EventBus.MOD`.
2. The local bus name is `mod:weavercompatsuite`.
3. A listener receives the event with `event.getBus()` set to the local bus.
4. A listener registered with `EventFilter.modId("weavercompatsuite")` runs.
5. The same listener accepts the synthetic payload.
6. A listener registered with `EventFilter.modId("weavercompatsuite-other")`
   does not run for the same local-bus event.
7. `RegisterEvent` is observed for `minecraft:item`.
8. `RegistryFreezeEvent` is observed for `minecraft:item` after the freeze.
9. The deferred item entry is bound at `onRegister` time.
10. A deferred item is readable through the live `RegistryView`.
11. A direct registration through `ModContext#registry` is visible.
12. The item registry reports `isFrozen()` at `onLoadComplete`.
13. Both registered items can be read back after the freeze.
14. A typed `net.weaver.api.game.event.LevelLoadEvent` listener uses the
    covariant `Level level()` accessor.
15. A base `net.weaver.api.event.events.LevelLoadEvent` listener receives the
    typed instance and sees a non-null level through the typed accessor.
16. A typed `net.weaver.api.game.event.ServerStartedEvent` listener uses the
    covariant `MinecraftServer server()` accessor and sees a running server.
17. The `GameWeaverMod#onLevelLoad(ModContext, Level)` callback runs on the
    logical server.
18. The `GameWeaverMod#onServerStarted` callback runs on the dedicated server.
19. A `ServerTickEvent.Pre` listener sees a server-side tick.
20. The `GameWeaverMod#onServerTick` callback sees the same server-side tick.

The fixture registers two real items:

- `weavercompatsuite:compat_item` through
  `ModContext#deferredRegister(GameRegistries.ITEM)`;
- `weavercompatsuite:compat_direct_item` through
  `ModContext#registry(GameRegistries.ITEM)`.

Both are registered before the item registry freezes and are read back after
it freezes.

## Layout

```text
examples/compat-suite/
  build.ps1                                     compile the mod jar
  run.ps1                                       launch the dedicated server
  src/main/java/net/weaver/compatsuite/
    CompatSuiteMod.java                         entrypoint, lifecycle, hooks
    CompatSuiteState.java                       check and result markers
    CompatPingEvent.java                        synthetic local-bus event
  src/main/resources/weaver.mod.json            schemaVersion 2 descriptor
```

## Build

Requirements:

- JDK 26 from `JAVA_HOME` or `C:\Program Files\Java\jdk-26.0.1`.
- `work/game-26.3/classpath.txt` (override with `WEAVER_GAME_DIR`).
- The agent lib under `core/build/agent/lib`.
- An `api-game` jar under `api-game/build/libs` or the agent lib.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\build.ps1
```

The build mirrors `examples/proof/build.ps1`: it compiles against the real game
classpath, the agent lib, and the API-game jar, copies the resources, and
writes `out/mods/compat-suite.jar`.

Expected marker, consumed by CI:

```text
COMPAT SUITE BUILD ok=true mods=<repo>\examples\compat-suite\out\mods
```

## Run

Requirements: `work/server-26.3` (override with `WEAVER_SERVER_DIR`) and the
agent jar at `core/build/agent/weaver-agent.jar`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\run.ps1
```

`run.ps1` starts `tools/launcher/launch.ps1` with a dedicated server on port
25568 (override with `WEAVER_COMPAT_SUITE_PORT`), waits for the result marker,
then verifies the exact success line. The launcher wait pattern accepts
`ok=true` or `ok=false` so a deterministic failure is reported immediately
instead of waiting for the full timeout; `run.ps1` still requires the exact
`ok=true side=server` line before printing `COMPAT SUITE PROOF ok=true`. It
writes the server log and marker file under `examples/compat-suite/out/logs/`
and never touches another proof's output directory. On failure it prints the
markers it found and tails `server.log` and `server.log.err`.

Expected markers, consumed by CI:

```text
COMPAT SUITE RESULT ok=true side=server checks=20
COMPAT SUITE PROOF ok=true
```

Successful checks are printed once each:

```text
COMPAT SUITE CHECK ok=true Local bus distinct
COMPAT SUITE CHECK ok=true Local bus name
COMPAT SUITE CHECK ok=true Local bus event bus
COMPAT SUITE CHECK ok=true Local bus listener
COMPAT SUITE CHECK ok=true Local bus filter accepts
COMPAT SUITE CHECK ok=true Local bus filter rejects
COMPAT SUITE CHECK ok=true Register event item
COMPAT SUITE CHECK ok=true Deferred entry bound
COMPAT SUITE CHECK ok=true Direct item registered
COMPAT SUITE CHECK ok=true Freeze event item
COMPAT SUITE CHECK ok=true Base listener receives typed level event
COMPAT SUITE CHECK ok=true Typed level event
COMPAT SUITE CHECK ok=true Level load callback
COMPAT SUITE CHECK ok=true Typed server started event
COMPAT SUITE CHECK ok=true Server started callback
COMPAT SUITE CHECK ok=true Server tick event
COMPAT SUITE CHECK ok=true Deferred item readable
COMPAT SUITE CHECK ok=true Registry frozen
COMPAT SUITE CHECK ok=true Registry read-back
COMPAT SUITE CHECK ok=true Server tick callback
COMPAT SUITE RESULT ok=true side=server checks=20 check_names=...
```

To validate the failure path, pass a server directory without the server jar or
a port already in use; `run.ps1` then prints
`COMPAT SUITE PROOF ok=false log=...` and exits 1 after the launcher reports the
failure.

## CI contract

`tools/verify-ci.ps1` may call these paths and match these exact prefixes:

```text
examples\compat-suite\build.ps1   ->  COMPAT SUITE BUILD ok=true mods=
examples\compat-suite\run.ps1     ->  COMPAT SUITE RESULT ok=true side=server checks=
                                      COMPAT SUITE PROOF ok=true
```

Keep the script names, the port default, and the marker strings stable.

## Limitations

- Only a dedicated server is exercised. Client-only features, client screens,
  and client hook paths are not covered by this fixture.
- The hooks covered are server tick, server level load, and server started.
  Cancelable entity/chat hooks and the observation hooks are covered by
  `examples/hooks-proof` and `examples/proof`.
- The registered items are real item registry entries but ship no models,
  textures, or lang entries. The fixture proves registration and reads, not
  rendering.
- The event-bus checks use a synthetic mod event posted on the local bus. They
  prove the local bus and filter semantics, not a game-payload event.
- All dedicated-server proofs share `work/server-26.3/weaver-test`; run them
  sequentially.
- The default port is 25568 so other proofs on 25566 and 25567 can run without
  a port clash.
