# Release notes: 2.3.2

Weaver is a mod loader for Minecraft 26.3. This beta is for **mod developers
and testers**. It has been tested on one Windows machine and is not ready for
players or public servers yet.

## The API is frozen

Since 2.3.2 the API is stable: a mod built against it keeps linking
and working on every later 2.x release. Additions are allowed; removals and
changes are not, and the gate checks that against a recorded baseline (4789
types and members at 2.3.2). Entry points Weaver itself calls are
marked `@Internal` and are not part of it. See
[API stability](api-stability.md).

## Changes in 2.3.2

- **`weaver-nf`, the NeoForge-shaped library.** `net.weaver.nf` ships in the
  agent `lib`: NeoForge's class names and shapes for registration, config,
  packets, entity data, multipart entities, biome modifiers, and client
  registrations, built on Weaver's API. It takes the place of the per-mod `nf`
  copies. Its `DeferredHolder` is the registry's own holder for registries
  without intrusive holders, which fixes attribute and effect lookups that
  missed the copies' holders. See [the library](nf.md).
- **API additions** (recorded in the baseline, now 4807 lines):
  `EnvironmentRenderers` (per-dimension sky, clouds, weather),
  `BlockEntityValidBlocks`, `ModPacks.addExtraPack`, and a self-sizing
  `PartEntity(parent, name)` constructor.
- Mod ids no longer trip the data fixer schema check in development runs.
- The shared class space (experimental) defines `net.weaver.nf` classes
  itself, like `api-game`, so the library sees the game there too.
- **Fixes found by porting the AOSO mods onto `weaver-nf`:**
  - **`EntityAttributes.register` waited for nothing.** It ran at once, while the
    mod's entity types and vanilla's attributes were still unbound, so a mod that
    registered from `onInitialize` failed to load (`unbound value
    minecraft:max_health`). Registrations now wait for the first common setup, as
    NeoForge's event fires after the registries are filled.
  - **`ClientKeys` before the options existed.** A key registered from
    `onClientSetup` threw, because the game makes its options later. Keys now wait
    for the options, get their category, and read the binding saved in
    `options.txt`.
  - **`PersistentData` on death.** It copied the whole tag to the respawned
    player. It now copies only the `PlayerPersisted` sub-tag, as NeoForge does.

Tested with the full gate (`tools\verify-ci.ps1`, 19/19): 1,015 unit tests,
and the dedicated-server proof at 294 checks, among them a mod written the
NeoForge way against `weaver-nf` (holders, codecs, config, persistent data and
what a respawned player keeps, attributes registered from `onInitialize`,
multipart parts, a `neoforge:add_spawns` file). Not yet covered by a test:
`weaver-nf` packets, complex spawn data, client commands, and the sky,
cloud, and weather renderers (their mixins load in the client proof). The
`ClientKeys` fix was checked by reading the code, not by a test.

## Changes since earlier previews

Sixteen NeoForge mods were ported to Weaver; these are the gaps they found,
now closed in Weaver itself (on top of the entity APIs, raw mouse events,
structure modifiers, instancing, and stable mod worlds already added):

- **A cancelable death.** `LivingDeathEvent` (`api-game`, `.living`) fires at
  the start of `LivingEntity#die` and `ServerPlayer#die`; canceling it skips
  the death. `LivingEntityDeathEvent` and `PlayerDeathEvent` stay
  observation-only.
- **A respawn event on the new player.** `PlayerRespawnedEvent` (`.player`)
  fires when `PlayerList#respawn` is done, with the player that plays on.
  `PlayerRespawnEvent` still fires earlier, on the old player.
- **A dying player's items in `LivingDropsEvent`.** His inventory and what he
  wears are in `drops()`, as in NeoForge.
- **Larger saved data.** An attachment declared `sync(CapabilitySync.NONE)`
  is saved with its holder and never sent, so it may declare up to 1 MiB
  (others keep 16 KiB); entities, block entities, levels and chunks hold up to
  2 MiB of Weaver data in all. Item stacks keep their 16 KiB limits.
- **Readable mod failures.** A mod that fails to load, initialize or handle an
  event now logs its stack trace and causes under the one-line summary, not
  only the exception's name.
- **Hiding parts of the HUD.** `HudLayerRenderEvent` with the
  `VanillaHudLayers` ids leaves out any part of the game's HUD (hearts, food,
  hotbar, crosshair, chat, ...) or another mod's `HudRenderRegistry` listener.
- **Stopping or replacing a screen before it opens.** `ScreenOpeningEvent`.
- **Critical damage.** `CriticalHitEvent#setDamageMultiplier`.
- **Render pipeline modifiers.** `PipelineModifiers` and `RenderPipelineCopy`
  change the pipelines the game draws with while a modifier is pushed (shader
  swaps, defines, blending).

An earlier preview was built and its unit tests (1,021) run on Linux. There the
dedicated-server game-events proof passed 254 checks, including the new ones;
two checks that depend on the test world's terrain (a forest at spawn, room
for `/spreadplayers`) failed the same way with the earlier preview on the same world.
The new events were also driven in a client with a test mod, and the sixteen
ported mods ran together on the new agent. The Windows release gate
(`tools\verify-ci.ps1`) was not run for that preview.

Version: build `2.3.2`, binary API `2.1.0`, feature level `2.2`
(see [compatibility](compatibility.md) for what those numbers promise).

## What is in it

**The loader.**

- A Java agent that discovers, orders, and loads mods into one shared class
  space.
- Mixin and access transformers.
- Mod metadata, dependencies, and side policies.

See [architecture](architecture.md).

**Game events.** All 267 event types NeoForge offers have a Weaver event; the
9 level render stages are the ten draw stages of `LevelDrawRegistry`. That is about 580 hook points: 59
core game hooks and about 520 mixin injection points. They cover:

- living entities and combat;
- players;
- blocks and the world, including mob griefing at all 38 vanilla sites;
- menus;
- the client (input, rendering stages, screens, sound, chat);
- registration-time events.

What the partly covered ones lack is in [hook inventory](hook-inventory.md).

**Systems for mod authors.**

| System | What it does | Docs |
|---|---|---|
| Registries | Vanilla registries plus `DeferredRegister`, tags, and data-driven Weaver registries loaded from data packs and synced to clients | [registry](registry.md) |
| Conventional tags | Vanilla entries of the shared `c:` tags (ingots, ores, dyes, storage blocks, water, ...) in Weaver's own built-in pack, so mods find each other's materials | [registry](registry.md#conventional-tags) |
| Load conditions | Data files skipped unless their conditions hold (mod loaded, item exists, and/or/not); NeoForge, Forge, and Fabric condition files work unchanged | [registry](registry.md#load-conditions) |
| Mod jars as packs | Each mod's `data/` and `assets/` load as built-in packs, just above vanilla | [registry](registry.md#mod-jars-as-packs) |
| Creative tabs | Mod tabs on their own pages; a builder mod code can compile against | [registry](registry.md#creative-tabs) |
| Capabilities | Typed values on item stacks, entities, block entities, chunks, and levels. Saved with the world, copied on respawn (`copyOnDeath`), optionally synced; attached on first use, versioned with migration, and requested from the client with a server check | [capabilities](capabilities.md) |
| Extensions | `BlockExtension`, `ItemExtension`, and `EntityExtension`: climbing, friction, plant soil, explosion resistance, fire sources, sticky blocks, redstone connection, dropped item lifespan and ticking, piglin currency, sneak-use, undroppable items, re-equip animation, farmland trampling, powder snow, shearing with any item (`ItemAction.SHEAR`), trapdoor ladders, flammability, swing and attack cancel, mining progress, crafting remainders, custom item entities, mob path types, enchanting power, block particle effects, beacon colors, stopping item use, harvesting by position, entity tracking, standing riders, hotbar names, fluid overlays, block sounds by position, neighbor face hiding, item arm poses and added poses, custom armor models | [extensions](extensions.md) |
| Extensible enums | New constants for vanilla enums from `META-INF/enumextensions.json`, in `values()`, `valueOf`, codecs, and id maps | [enums](enums.md) |
| Entities | Attributes and spawn rules for any type, multipart entities, incoming damage changes, cancelled spawns | [entities](entities.md) |
| Fluid types | Swimming, drag, drowning, currents, fog, and bucket sounds for modded fluids; state ids and caches for modded blocks and fluids | [fluids](fluids.md) |
| Lookups | Item, fluid, and energy handlers on blocks, entities, and stacks; vanilla containers and cauldrons included; buckets through `FluidContainers` | [lookups](lookups.md) |
| Loot, biome, and structure modifiers, data maps | Data pack files that change loot, biome features and spawns, and structures (biomes, mobs inside, step, terrain) without replacing vanilla files; per-entry registry values | [world modifiers](world-modifiers.md) |
| Game tests | Tests registered from code, run headless by `weaverGameTest`, JUnit-style report | [game tests](gametest.md) |
| Networking | Channels over the game connection. A join check refuses missing or mismatched mods and mismatched registries, before world data, with a readable reason | [network](network.md) |
| Config, commands, client | Typed config with sync and a generated in-game settings screen (translated labels), a Mods list on the title screen and pause menu, client commands, raw mouse events, sending to the players that see an entity, Brigadier commands with permissions, and client registries (keys, screens, HUD, rendering, ten draw stages, GPU instancing, guide screens, item tints, custom model loaders, block entity renderers, machine and contraption rendering) | [config](config.md), [commands](commands.md), [client](client.md) |
| Toolchain | Gradle plugin (`weaverBuild`, `runClient`, `runServer`, `weaverGameTest`, jar-in-jar), project generator, installer, datagen | [toolchain](toolchain.md) |

Start with [getting started](getting-started.md).

**Robustness.** A full audit fixed about thirty ways a broken mod, a hostile
peer, or an odd setup could crash the game or lose data: one bad mixin config or
jar no longer stops the boot, listener values are validated before vanilla uses
them, log floods are throttled, world saves survive failing mod data, a crash
shield keeps one failing entity, block entity, world-generation feature,
renderer, or screen from stopping the game (it is suspended or skipped and
reported), each mod's problems are summarized in the log and in crash reports,
and the launcher passes vanilla's native-stack flag (it removed random client crashes on
the test machine) and works from folders with spaces. The list is in
[hooks: what a broken mod can no longer take down](hooks.md#what-a-broken-mod-can-no-longer-take-down).

## How it was tested

The release gate is `tools\verify-ci.ps1`. Its 19 steps:

- build with and without the game;
- unit tests (about 1,020: `api` 301, `core` 506, `api-game` 172,
  Gradle plugin 32, `sync` 11);
- a dedicated-server proof with 232 in-game checks;
- a client proof in a single-player world;
- game tests (`examples/gametest`);
- a two-process multiplayer test (`examples/multiplayer`);
- performance budgets, the patcher, and hygiene checks.

The multiplayer test runs a dedicated server and separate clients over
`127.0.0.1`:

- a matching client joins, and registries, data maps, a capability, and a
  channel all arrive over the network;
- a client with an extra item, another mod version, or no mod is refused
  with the right reason.

## Known limitations

- **One machine.** The preview was tested only on Windows 11 with JDK 26,
  Minecraft 26.3, and an NVIDIA RTX 5070 Ti. Nothing has run on Linux,
  macOS, another GPU, or a server across a real network.
- **Client crashes on the test machine.** The client used to die now and then
  with a native access violation and no `hs_err` file. The launcher now passes
  vanilla's `-XX:StackShadowPages=32`, and no such crash has happened since, but
  that is a handful of runs on one machine, not proof. A JVM crash file is now
  written next to the log if it happens again.
- **Few real mods yet.** Besides the test mods written alongside Weaver,
  sixteen real mods (the AOSO family, Kazanov, Aosium, and others) have run on
  it together. Porting more will find more gaps in the APIs.
- **Versions on join.** The join check needs the same version of each shared
  mod unless the mod declares `compatibleVersions`.
- **Not compatible with NeoForge or Fabric mods.** Weaver has its own API.
  Code written the NeoForge way ports by moving to `weaver-nf`
  ([the library](nf.md)); NeoForge's event bus (`NeoForge.EVENT_BUS`) is not
  part of it yet.
- **No launcher integration.** No Minecraft Launcher profile, and no Prism,
  Modrinth, or CurseForge support. Games start through
  `tools/launcher/launch.ps1`.
- Each guide's Limitations section lists the rest; for example, biome
  feature changes need a restart.

## Reporting problems

Include:
- the game log and `weaver` lines from it;
- the `[weaver] MOD READ` lines (your mod list);
- what you expected.

For a join refusal, include the reason the client showed and the server's
`NETWORK BRIDGE` lines.
