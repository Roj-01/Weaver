# Real 26.3 proof

This mod is the Weaver 2.2 / Minecraft 26.3 acceptance proof. It exercises the
loader, the game class space, Mixin/AT, registries, the event bus, dispatch
filters, typed events, cancelable action hooks, observation hooks, and the
direct callbacks on a real client and a real dedicated server.

## What it covers

- imports `net.minecraft.*`;
- extends `Block`, `Item`, and `Entity`;
- registers a block, item, block item, and entity type at the registry
  freeze head;
- registers `weaverproof:proof_view_item` directly through a live
  `RegistryView` (`ModContext#registry`);
- registers `weaverproof:proof_event_item` and `weaverproof:proof_raw_item`
  from `RegisterEvent` (`register` and `registerRaw`);
- checks the typed `RegisterEvent.registry(GameRegistries.ITEM)` overload and
  the `GameRegistries.ITEM` id against `WeaverRegistries.ITEM.id()`;
- checks the registry read surface (`size`, `keySet`, `values`, `entries`,
  `getEntry`, `containsKey`, `get`) and `TagView` (`key`, `size`, `values`,
  `contents`);
- uses Mixin on a real game class (`Block.getName`);
- uses an access transformer on a real private field (`ItemStack.count`) and a
  real private final field (`Block.builtInRegistryHolder`);
- exercises `ItemStack` and `DataComponents.CUSTOM_NAME`;
- registers a deferred item through `ModContext#deferredRegister`;
- verifies the per-mod local bus:
  - `context.modEvents() != EventBus.MOD`;
  - the bus name is `mod:weaverproof`;
  - `context.globalModEvents() == EventBus.MOD`;
  - lifecycle events posted on the local bus carry
    `event.getBus() == context.modEvents()`;
  - a listener on the global aggregate bus sees the same lifecycle phases;
  - a `modId("weaverproof")` filter fires for this mod and rejects a synthetic
    event for another id;
- verifies dispatch-time logical-side filters for `LevelLoadEvent` on both
  `CLIENT` and `SERVER` (the client expects both; a dedicated server requires
  `SERVER` and no false `CLIENT` delivery);
- verifies typed `LevelLoadEvent`:
  - a typed listener calls `event.level()` and checks it is non-null;
  - a base `LevelLoadEvent` listener receives the typed instance and sees the
    same level object through the base and typed accessors;
- exercises the cancelable entity-join pilot:
  - a typed `EntityJoinLevelEvent` listener cancels only an identity-matched
    `ItemEntity`;
  - `level.addFreshEntity(target)` returns `false` and the entity is not in the
    level;
  - a second `ItemEntity` is added normally;
- exercises the `LivingEntity.hurtServer` observation hook with a real zombie
  (falling back to an armor stand, then to an explicit skipped marker if the
  environment cannot spawn):
  - a typed `EntityHurtEvent` listener matches the damaged entity;
  - `hurtServer(level, damageSources().generic(), 1.0F)` fires the event;
- exercises the cancelable chat hook on the client's integrated server:
  - a typed `ChatEvent` listener cancels only the marker message;
  - `PlayerList.broadcastChatMessage(...)` is invoked with a real
    `PlayerList`, `ServerPlayer`, and `ChatType.Bound`;
  - the event is observed and canceled (`ChatCancellation ... canceled=true`);
  - this is a hook-level integration test, not a real client chat packet; a
    dedicated server records `Chat cancellation audit-only reason=no-player`;
- exercises the optional observation hooks on the client's integrated server:
  - `ContainerOpenEvent` via `ServerPlayer.openMenu(SimpleMenuProvider)`;
  - `PlayerRespawnEvent` via
    `ServerPlayer.findRespawnPositionAndUseSpawnBlock` (a position lookup, not
    the respawn action);
  - `LootTableEvent` via `LootTable.getRandomItems` on the dedicated and
    integrated server; listeners must be thread-safe because loot generation
    can run on worker threads;
- discovers `@EventBusSubscriber` listeners from the mod jar;
- observes registry, tick, level, command, server, screen, resource reload, and
  player events;
- runs all six lifecycle phases on a real client and a real dedicated server;
- implements `GameWeaverMod` and overrides the typed `onPlayerJoin` and
  `onLevelLoad` methods.

## Build

```powershell
.\build.ps1
```

## Run

```powershell
.\run-server.ps1
.\run-client.ps1
.\run-server-shared.ps1
```

`run-client.ps1` uses the deterministic fixture world in `fixture-world/` by
default. It copies the fixture to
`work/game-26.3/saves/WeaverProofFixture` before every run, removes only that
copy and its stale `session.lock`, and never modifies the source fixture or any
other save. Regenerate the fixture from a known-good world with:

```powershell
.\prepare-client-world.ps1
```

Set `WEAVER_CLIENT_WORLD=<name>` to use a different existing world instead of
the fixture. The fixture is stored in the working tree; it is not committed.

Or both:

```powershell
$env:WEAVER_CLIENT_WORLD="New World (2)"
.\run-all.ps1
```

The scripts read the game from `WEAVER_GAME_DIR` (default
`work/game-26.3`) and the server from `WEAVER_SERVER_DIR` (default
`work/server-26.3`), both relative to the repository root.

Both scripts use the self-contained `tools/launcher/launch.ps1`.

## Expected evidence

Server log: `out/logs/server.log`

```
PROOF PHASE initialize
PROOF CHECK ok=true Local bus distinct
PROOF CHECK ok=true Local bus name
PROOF CHECK ok=true Global aggregate bus
PROOF CHECK ok=true RegisterEvent typed registry
PROOF PHASE register
[weaver] MOD weaverproof PROOF CONTENT ok=true block=weaverproof:proof_block ...
PROOF PHASE common
PROOF PHASE server
PROOF PHASE complete
...
PROOF CHECK ok=true GameRegistries ITEM
PROOF CHECK ok=true Lifecycle event bus is local
PROOF CHECK ok=true Local and global lifecycle phases match
PROOF CHECK ok=true Mod ID filter fires for this mod
PROOF CHECK ok=true Mod ID filter excludes other ids
PROOF CHECK ok=true Cancelable entity join
PROOF CHECK ok=true Entity join allowed
PROOF CHECK ok=true Entity hurt hook
PROOF CHECK ok=true Chat cancellation audit-only reason=no-player
PROOF CHECK ok=true Container open audit-only reason=no-player
PROOF CHECK ok=true Respawn position lookup audit-only reason=no-player
PROOF CHECK ok=true Loot table hook
PROOF CHECK ok=true Logical side filter CLIENT
PROOF CHECK ok=true Logical side filter SERVER
PROOF CHECK ok=true Typed event accessor
PROOF CHECK ok=true Base listener receives typed event
PROOF CHECK ok=true Server level captured
[weaver] MOD weaverproof PROOF RESULT ok=true side=server checks=20 ...
```

The client proof runs the same checks and additionally observes
`ChatCancellation`, `ContainerOpen`, and `PlayerRespawnLookup` on the
integrated server. The result line reports `checks=20` and
`check_names=...` for both sides.

`run-server-shared.ps1` requests `-SharedClassSpace` and records the shared
class-space outcome. Shared class space is experimental, opt-in, and
environment-dependent. It is not supported as a release gate.

The script accepts two outcomes.

1. Full proof pass. With a shared-loading launch, the game classes are hosted by
   the shared loader and the proof initializes normally. The script requires a
   `[weaver] CLASS SPACE mode=shared` marker and `[weaver] TYPED EVENTS enabled`
   without linkage errors. When the proof posts `PROOF RESULT ok=true
   side=server`, it prints `SHARED MODE RESULT ok=true side=server`, then runs
   the factory probe; it exits 0 only when the probe also prints
   `SHARED FACTORY PROBE ok=true`. The 2026-11-04 compatibility run observed two
   such passes on this machine:

   ```text
   [weaver] CLASS SPACE mode=shared game-entries=40 mods=1 loader=weaver-shared api-game=36
   [weaver] AGENT READY side=server mods=1 configs=1 class-space=shared
   [weaver] MOD weaverproof PROOF RESULT ok=true side=server checks=20 ...
   SHARED MODE RESULT ok=true side=server log=...\out\logs\server-shared.log
   SHARED FACTORY PROBE ok=true
   ```

   Do not generalize this observation. The same script produced the documented
   blocker on earlier runs, and the result depends on how the host loader hosts
   the game classes.

2. Documented blocker. On a host where the standard launcher hosts the game
   classes on the system loader, the shared-loader-gated transforms do not run
   and the proof cannot initialize. The script computes the blocker and runs
   the factory probe:

   ```text
   SHARED MODE RESULT ok=false blocker=game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init log=... launcher_exit=...
   SHARED FACTORY PROBE ok=true
   ```

   Without `-AllowDocumentedBlocker`, this path exits 1. With
   `-AllowDocumentedBlocker`, after the probe passes, the script prints:

   ```text
   SHARED MODE ACKNOWLEDGED ok=false blocker=... probe=ok
   Shared class space is experimental and is not a passing proof gate.
   ```

   and exits 0. That is an acknowledgment of the documented architectural
   blocker, not a passing proof. The flag only affects a non-passing run that
   produced a computed blocker; it does not make `PROOF RESULT ok=true` appear.
   A linkage error or a missing shared-mode marker still exits 1, and a proof
   pass still requires the typed-events and factory-probe checks above.

The probe proves the typed factory links through a shared-style loader with a
single base-API identity; it does not by itself claim that game classes are
hosted by the shared loader. The full proof pass above is the evidence for that
claim on this host. `tools/verify-ci.ps1` runs the shared step with
`-AllowDocumentedBlocker` and accepts either outcome: it labels the mode
`documented-blocker` or `proof-pass`, records the observed facts, and never
claims shared mode is supported. See `docs/api-game.md` for the full analysis.

The hook summary is one audit line per side:

```text
[weaver] GAME HOOK SUMMARY applied=<n> skipped=<n> missing=<n> invalid=<n> required_misses=<ids|none>
```

The 2026-11-04 verification run observed the same current value on the
dedicated server and the client:

```
[weaver] GAME HOOK SUMMARY applied=30 skipped=8 missing=0 invalid=0 required_misses=none
```

Counts can change when the built-in hook specs or class-load timing change.
Treat `missing=0`, `invalid=0`, and `required_misses=none` as the invariants; a
non-empty miss list must be explained.

Client log: `out/logs/client.log`

```
PROOF PHASE initialize
PROOF PHASE register
[weaver] MOD weaverproof PROOF CONTENT ok=true ...
PROOF PHASE common
PROOF PHASE client
PROOF PHASE server
PROOF PHASE complete
...
PROOF CHECK ok=true Logical side filter CLIENT
PROOF CHECK ok=true Logical side filter SERVER
PROOF CHECK ok=true Typed event accessor
PROOF CHECK ok=true Base listener receives typed event
[weaver] MOD weaverproof PROOF RESULT ok=true side=client ...
[weaver] LIFECYCLE COMPLETE expected-client=true expected-server=true
```

The client hook summary is a snapshot at the first client tick:

```
[weaver] GAME HOOK SUMMARY applied=30 skipped=8 missing=0 invalid=0 required_misses=none
```

Targets that load later in client startup (the player list and command
dispatcher classes) appear as later `GAME HOOK APPLIED` lines.

The proof also verifies `[weaver] TYPED EVENTS enabled
factory=net.weaver.api.game.TypedGameEventFactory`. A
`TYPED EVENTS unavailable` line means `api-game` is missing from the agent lib
or cannot link, and the typed proof checks will fail.

## Worlds and client recovery

`New World (2)` is the known-working quick-play world for this repository. The
older saves contain content from earlier experiments (`ruby:*`), so the log may
print unrelated "unknown registry key" warnings while loading. Those warnings do
not affect the proof.

If a previous client run was killed before it could release the save, Minecraft
may stay at the title screen instead of quick-playing. To recover:

1. Confirm no stale proof Java process is still running.
2. Delete only the stale lock file, not the world data:

   ```powershell
   Remove-Item work\game-26.3\saves\*\session.lock -Force
   ```

3. Rerun `run-client.ps1`, optionally with a different
   `WEAVER_CLIENT_WORLD`.
