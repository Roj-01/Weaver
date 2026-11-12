# Documentation

Index for Weaver 2.2 on Minecraft 26.3. Start with the
[repository README](../README.md) for the product overview and build commands.

## API reference

- [Getting started](getting-started.md) - build Weaver and a first mod (item,
  creative tab, event, game test) step by step.
- [API stability](api-stability.md) - what the frozen API covers, what a 2.x
  release may change, and how the gate enforces it.
- [Release notes](release-notes.md) - what the beta contains,
  how it was tested, and its known limitations.
- [Event bus](events.md) - `EventBus`, listener registration, priorities,
  cancellation and results, the global and per-mod buses, filters,
  `@SubscribeEvent` and `@EventBusSubscriber`, the event catalog, lifecycle
  events, thread safety, and statistics.
- [Typed game API](api-game.md) - the optional `api-game` artifact, game-typed
  events and covariant accessors, `GameWeaverMod`, `GameRegistries`, the
  `GameEventFactory`/`TypedGameEventFactory` bridge, installation and fallback,
  classloader rules, and binary compatibility.
- [Registry API](registry.md) - `RegistryKey`, `RegistryView`, `RegistryEntry`,
  `DeferredRegister`, `RegisterEvent`, `RegistryFreezeEvent`, tags, typed
  registry helpers, `WeaverRegistries`/`GameRegistries`, and the data-driven
  `DataRegistry` subsystem.
- [Hook points](hooks.md) - the 59 injected hook specs and their events, tick
  and server/level/player lifecycle hooks, resource reload, commands, screens,
  entity join/hurt/attack/interaction/tick/removal, block break/place, chunk
  load/unload, block entity load/save, containers, anvil, enchant, teleport,
  respawn target/action, loot and saves, cancelable and return-value actions,
  side gating, strict mode, and the hook summary.
- [Loot, biome, and structure modifiers, data maps](world-modifiers.md) - data
  pack files that change loot, biome features and spawns, and structures, and
  per-entry registry values.
- [Game tests](gametest.md) - in-game tests registered from mod code and run
  headless with a JUnit-style report (`weaverGameTest`).
- [Block, item, and entity extensions](extensions.md) - methods a mod's own
  blocks, items, and entities override where vanilla hard-codes behavior.
- [Extensible enums](enums.md) - new constants for vanilla enums.
- [The NeoForge-shaped library](nf.md) - `net.weaver.nf` (`weaver-nf`):
  NeoForge class names and shapes (`DeferredRegister`, `ModConfigSpec`,
  `PartEntity`, ...) on Weaver, so a port changes its imports and rebuilds.
- [Entities](entities.md) - attributes, spawn rules, multipart entities, and
  control over damage and spawning.
- [Fluid types](fluids.md) - swimming, drag, drowning, currents, fog, and
  bucket sounds for modded fluids, and state ids for modded blocks and fluids.
- [Lookups](lookups.md) - item, fluid, and energy handlers found on blocks,
  entities, and item stacks through registered providers.
- [Capabilities](capabilities.md) - typed capability and attachment types,
  item custom-data persistence, entity/block-entity persistence, runtime
  holders, the capability sync channel, defaults, codecs, bounds, copy and
  sync declarations, threading, and diagnostics.
- [Client API](client.md) - key mappings, screen factories, injected HUD layers,
  tooltip providers, client tick and resource-reload listeners, runtime
  textures, client-thread rules, server safety, and model/color limits.
- [Command API](commands.md) - server-authoritative Brigadier command trees,
  execution, Weaver-owned permissions with JSON persistence, registration and
  threading, aliases, automatic client command-tree sync, late resend, and live
  removal.
- [Configuration API](config.md) - typed per-mod, per-side JSON configuration,
  builders and values, validation, schema migrations, load/save and backups,
  diagnostics, `ConfigLoadedEvent`, explicit reload, file watching, and
  server-to-client sync.
- [Networking API](network.md) - game-free payload and channel types, bounded
  buffers, TCP transport, handshake and play phases, network events, security
  limits, and the installed Minecraft 26.3 payload bridge.

## Guides

- [Architecture](architecture.md) - runtime layers and modules, the `SYSTEM`
  and `SHARED` class-space modes, boot and transform order, the mod lifecycle,
  discovery and dependency ordering, versioning, and failure modes.
- [Patched game artifact](patcher.md) - the binary patch pipeline: official
  jar to deterministic patched jar, injected interfaces and attachment
  contract, signature and digest stripping, launcher substitution, patch-level
  detection, fail-closed verification, and the path to source patches.
- [Source patcher](source-patcher.md) - the NeoForm-style source phase:
  decompile selected official classes with Vineflower, apply unified-diff
  patches, recompile with `javac --release 25`, rebuild a deterministic jar,
  and verify the added members and the full class path.
- [Patcher reference](patcher-reference.md) - how NeoForm, NeoFormRuntime
  binary patching, interface injection, source patches, and FML launch the
  patched game, compared with Weaver's implementation and limits.
- [Datagen](datagen.md) - the standalone manifest-driven generator for
  blockstates, models, language files, loot, recipes, tags, advancements, data
  registries, and pack metadata, including verification, fingerprints, and pack
  merging.
- [Migration to 2.1](migration-2.1.md) - the upgrade from 2.0 to 2.1: per-mod
  local buses, dispatch filters, typed events, cancelable action hooks, hook
  diagnostics, behavior changes, and an upgrade checklist.

## Operations and release

- [Platform operations](platform-ops.md) - supported modes, the launcher,
  shared-mode operation, the mod-list CLI and boot report, class-space
  policies, the update contract, version checks, and the recorded verification
  status.
- [Toolchain](toolchain.md) - module artifacts, build and test commands, game
  classpath resolution, the Gradle plugin, mod dependency resolution,
  jar-in-jar, publishing, verification tasks, the toolchain fixture, and the
  installer.
- [Compatibility](compatibility.md) - the supported environment matrix, the
  2.x binary-compatibility contract, the CI matrix, the compatibility-suite
  fixture, and the accepted compatibility limits.
- [Release checklist](release-checklist.md) - the release validation checklist
  covering the build matrix, unit tests, proofs, performance budgets, hygiene,
  known limitations, and sign-off.

## Verification and performance evidence

Benchmark methodology and measured dispatch numbers are in the
[Dispatch model](dispatch.md). Build, packaging, and CI gates are documented in
`toolchain.md` and `compatibility.md`; `tools/verify-ci.ps1` is the full
19-step gate (A-L, including the `H-gametest`, `H-multiplayer`, and `L-patcher` steps), and its recorded summaries plus the sign-off checklist
are in `release-checklist.md`. The main acceptance proof is
the [real 26.3 proof](../examples/proof/README.md); the cross-feature suite is
the [compatibility suite](../examples/compat-suite/README.md); and the
platform budgets are in the [benchmark harness](../examples/proof/bench/README.md).
The ops proof is documented in `platform-ops.md` under Verification.

## Examples

- [Examples index](../examples/README.md) - the feature-to-proof matrix and
  top-level examples directory.
- [Real 26.3 proof](../examples/proof/README.md) - the main acceptance mod for
  the loader, class space, Mixin/AT, registries, events, hooks, and lifecycle on
  a client and dedicated server.
- [Ops proof](../examples/ops-proof/README.md) - loader discovery, mod-list
  CLI, policies, version checks, and the update contract without launching the
  game by default.
- [Hooks proof](../examples/hooks-proof/README.md) - the 2.2 hook-event and
  cancellation proof, with build and run instructions for the server and client
  stages.
- [Client proof](../examples/client-proof/README.md) - the client API and
  command API proof on a real client and dedicated server.
- [Minimap v2](minimap-v2.md) - a redesigned client-only HUD and fullscreen map
  with cave slices, advanced waypoints, ten-tab settings, themes, world beams,
  and a public mod API. The runnable mod is in
  [examples/minimap](../examples/minimap/README.md).
- [Capability proof](../examples/capability-proof/README.md) - the standalone
  capability proof using real `ItemStack` custom data and NBT round trips.
- [Config proof](../examples/config-proof/README.md) - the standalone
  configuration API and file-store proof without game classes.
- [Network proof](../examples/network-proof/README.md) - the standalone
  two-process TCP transport proof; it is not a Minecraft game-bridge proof.
- [Datagen proof](../examples/datagen-proof/README.md) - generates a data pack,
  verifies it, and reads it on a real dedicated server.
- [Compatibility suite](../examples/compat-suite/README.md) - a 20-check real
  dedicated-server fixture covering the local bus, registry registration,
  typed events, and hook callbacks across features.
- [Platform benchmark](../examples/proof/bench/README.md) - the standalone
  registry, hook-dispatch, and scanner benchmark with platform budget checks.
- [Starter](../examples/starter/README.md) - the minimal mod built with the
  Weaver Gradle plugin and its run tasks.
- [Toolchain fixture](../examples/toolchain-fixture/README.md) - the Gradle
  dependency-range, optional-dependency, jar-in-jar, loader, and server proof.

`modding.md` is not present in this tree; modding guidance is covered by the
architecture, event bus, registry, hook, and toolchain pages above.
