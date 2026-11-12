# Weaver

Weaver is a mod loader for Minecraft 26.3. Mods load once at boot into one
shared class space: a mod can import `net.minecraft.*`, extend game classes,
use Mixin and access transformers, and see other mods. There is no per-mod
classloader and no isolation layer.

## Status

**Developer preview.** For mod developers and testers, not yet for players:
the APIs may change, and testing so far is one Windows machine. Start with
[getting started](docs/getting-started.md); what is tested and what is not is
in the [release notes](docs/release-notes.md).

Weaver 2.2 is the current feature level. The binary API version intentionally
stays `2.1.0` (`WeaverApi.FEATURE_LEVEL` is `2.2`, `WeaverApi.GAME_VERSION` and
`WeaverApi.HOOK_TARGET` are `26.3`, and the build version is
`2.3.2`). The API is frozen from 2.3.2: see
[API stability](docs/api-stability.md). Implemented in this tree:

- loader core: agent boot, mod discovery, metadata, dependency ordering,
  policies, mod-list reports, and version checks;
- one shared class space, with `SYSTEM` as the supported production mode and
  `SHARED` as an opt-in alternative;
- Mixin and access-transformer integration;
- event bus with global, aggregate, and per-mod local buses, filters, typed
  events, and annotation-based subscribers;
- game registry API and data-driven registries;
- game events for all 267 of NeoForge's event types, from
  59 core game hooks and about 520 mixin injection points: entities and
  combat, players, blocks and world, menus, client input and rendering, and
  registration time;
- NeoForge-style systems on Weaver's own API: creative tab pages, level and
  chunk capabilities with persistence, item/fluid/energy lookups, loot and
  biome modifiers, data maps, game tests, and a join-time mod and registry
  check;
- an optional binary patch pipeline that derives a patched official game
  artifact and a compile-time attachment contract;
- optional game-typed `api-game` artifact;
- `weaver-nf`, a library with NeoForge's class names and shapes
  (`DeferredRegister`, `DeferredHolder`, `ModConfigSpec`, `PartEntity`, packets,
  client registrations) built on Weaver's API: one copy for every ported mod
  instead of one per mod (see [the library](docs/nf.md));
- game-free networking API and TCP transport;
- typed configuration, capabilities, client, and command APIs;
- datagen, Gradle toolchain, launcher, installer, diagnostics, and sync.

The full release gate is `tools\verify-ci.ps1` (19 steps), which covers the build matrix,
standalone proofs, server and compatibility proofs, the client fixture proof,
game tests, a two-process multiplayer test,
shared-mode behavior, performance budgets, and hygiene. The main acceptance
proof remains the `SYSTEM`-mode client and dedicated-server proof; recorded
results are in [platform operations](docs/platform-ops.md#verification) and the
[release checklist](docs/release-checklist.md). Shared class space is
experimental and is not a supported release gate. Standalone and integration
proofs cover ops, hooks, client, compatibility, capabilities, config, network,
datagen, platform budgets, and the toolchain. See [Proof](#proof).

The event, registry, config, capability, client, and command APIs are
Weaver-specific convenience layers, and the network API is a standalone
transport. None is a NeoForge-compatible API, and direct game access remains the
primary path.

## Features

| Area | What it provides | Reference |
|---|---|---|
| Loader and class space | Agent boot, mod discovery and ordering, side/duplicate/dependency policies, mod-list CLI and reports, version checks. `SYSTEM` is the default; `SHARED` is opt-in. | [Architecture](docs/architecture.md), [Platform operations](docs/platform-ops.md) |
| Patched game artifact | Two-stage patch pipeline: a deterministic binary phase injects the `net.weaver.patched` attachment contract into the official 26.3 jars; a NeoForm-style source phase decompiles selected classes with Vineflower, applies unified-diff patches, and recompiles them with `javac --release 25`. Both strip signatures and manifest digests and verify with `-Xverify:all`; the launcher substitutes the patched jar and the loader reports the patch level. | [Patcher](docs/patcher.md), [Source patcher](docs/source-patcher.md), [Reference](docs/patcher-reference.md) |
| Mixin and access transformers | Load-time Mixin configurations and access-transformer rules; access transformers run before Mixin. | [Architecture](docs/architecture.md), [Toolchain](docs/toolchain.md) |
| Event bus | `EventBus.GAME`, `EventBus.MOD`, and one local bus per mod; priorities, cancellation/results, dispatch filters, `@SubscribeEvent`, `@EventBusSubscriber`, typed events. | [Event bus](docs/events.md), [Dispatch model](docs/dispatch.md) |
| Registry API | `RegistryKey`, live `RegistryView`, `RegistryEntry`, `DeferredRegister`, `RegisterEvent`, `RegistryFreezeEvent`, tags, typed helpers, and data-driven `DataRegistry`. | [Registry API](docs/registry.md) |
| Hooks | Typed game events for all 267 of NeoForge's event types ([inventory](docs/hook-inventory.md)), plus 59 injected specs for ticks, server/level/player lifecycle, resource reload, commands, screens, entity join/hurt/death/interact/attack/remove/tick, block break/place, chunks, block-entity load/save, containers, anvil, enchant, teleport, respawn, projectiles, loot, and saves. | [Hook points](docs/hooks.md) |
| Typed game API | Optional `api-game` artifact with game-typed events and accessors, `GameWeaverMod`, `GameRegistries`, and reflective factory installation. | [Typed game API](docs/api-game.md) |
| Networking | Game-free payload/channel API, bounded buffers, TCP transport, handshake/play phases, network events, and a real Minecraft 26.3 payload bridge with codec registration, login/configuration/play handshake, and in-game delivery when `api-game` and the game classes are present. | [Networking API](docs/network.md) |
| Configuration | Typed per-mod, per-side JSON configuration with validation, schema migrations, atomic saves, backups, `ConfigLoadedEvent`, explicit `reload()`, `ConfigReloadedEvent`, an opt-in file watcher (`-Dweaver.config.watch=true`), and server-authoritative `SERVER_TO_CLIENT` sync over the bridge. | [Configuration API](docs/config.md) |
| Loot and biome modifiers, data maps | Data pack files that change loot tables and biome features and spawns without replacing vanilla files, and per-entry registry values (data maps). | [Loot and biome modifiers](docs/world-modifiers.md) |
| Game tests | In-game tests registered from mod code, run headless by `weaverGameTest` with a JUnit-style report. | [Game tests](docs/gametest.md) |
| Lookups | Item, fluid, and energy handlers on blocks, entities, and stacks, found through registered providers; vanilla containers included. | [Lookups](docs/lookups.md) |
| Capabilities | Typed attachments for `ItemStack` custom data and runtime holders, with codecs, defaults, limits, copy/sync declarations, diagnostics, entity/block-entity persistence, and an opt-in capability sync channel. | [Capabilities](docs/capabilities.md) |
| Client API | Key mappings, screen factories, HUD layers, tooltips, client tick and resource-reload listeners, client-thread enforcement, and game-typed hand, level, screen, HUD-stage, block-color, fog/sky/camera/FOV, entity-renderer, and model-layer registries. | [Client API](docs/client.md) |
| Command API | Server-authoritative Brigadier command trees, aliases, Weaver-owned permissions with JSON persistence, automatic client tree sync, late-registration resend, and live removal by dispatcher rebuild. | [Command API](docs/commands.md) |
| Loader watch and data packs | Opt-in mods-directory watcher with `ModsChangedEvent` (restart-required), multi-pack `DataPackRepository` with `pack.mcmeta` precedence, and data-registry reload that refuses frozen registries. | [Toolchain](docs/toolchain.md), [Registry API](docs/registry.md) |
| Datagen | Standalone manifest-driven generator for blockstates, models, language files, loot, recipes, tags, advancements, data registries, and pack metadata, with verify/fingerprint/merge. | [Datagen](docs/datagen.md) |
| Toolchain | Gradle plugin with dependency resolution, jar-in-jar, run tasks, launcher, project generator, installer, and game fetcher. | [Toolchain](docs/toolchain.md) |
| Compatibility and release | Supported environment matrix, 2.x binary contract, CI matrix, compatibility-suite fixture, release checklist, and accepted limits. | [Compatibility](docs/compatibility.md), [Release checklist](docs/release-checklist.md) |
| Diagnostics and sync | Crash-report diagnostics and manifest, mirror, and resume support; both are off the boot path. | [Architecture](docs/architecture.md) |

## Build and test

Requirements: JDK 26 and the Gradle wrapper in this repository.

```powershell
.\gradlew.bat clean build --no-build-cache --rerun-tasks --console=plain
.\gradlew.bat :api:test :core:test :api-game:test --rerun-tasks --console=plain
.\gradlew.bat :api:benchmark
powershell -ExecutionPolicy Bypass -File tools\verify.ps1
powershell -ExecutionPolicy Bypass -File tools\verify-ci.ps1
```

On Unix, use `./gradlew` with the same tasks. `tools/verify.ps1` runs ten
build and packaging checks, prints `PASS`, `FAIL`, or `SKIP` per step, and ends
with `WEAVER VERIFY ok=true steps=10` or `ok=false`; it exits non-zero on
failure.

`tools/verify-ci.ps1` is the full reproducibility gate. It covers the build
matrix A-K, standalone proofs, the server and compatibility-suite proofs, the
client fixture proof, shared-mode behavior, the benchmarks, and diff/marker
hygiene. It prints `PASS`, `FAIL`, or `SKIP` per step and a final summary.
Flags such as `-Quick`, `-SkipGameProofs`, `-SkipClientProof`,
`-SkipSharedProof`, `-SkipBenchmarks`, and `-SkipBuildMatrix` are documented in
the script.

Recorded on 2026-11-05 (recorded evidence, not a permanent guarantee): 881
module tests passed (`api` 280, `core` 451, `api-game` 150). The 2026-11-05
gate run below skipped the build matrix, benchmarks, and shared mode because
those were verified separately by `tools/verify.ps1` and dedicated runs:

```text
WEAVER VERIFY-CI ok=true steps=17 pass=9 fail=0 skip=8 shared_mode=skipped elapsed=154.0s
```

The full 2026-11-04 gate remains the recorded baseline:

```text
WEAVER VERIFY-CI ok=true steps=16 pass=16 fail=0 skip=0 shared_mode=proof-pass elapsed=337.3s
```

The game jars are not vendored. Development tasks read a game directory from
`WEAVER_GAME_DIR` or from the repository-local `work/game-26.3` directory.
`api-game` compiles against that classpath with `compileOnly`. When no game
classpath is present, `api-game` is skipped by default
(`[weaver] api-game SKIPPED reason=no-game-classpath`) so a fresh tree still
builds. Use `-Pweaver.requireGameApi=true` to fail instead of skipping, or
`-Pweaver.skipGameApi=true` to skip explicitly even when the classpath is
present.

The standalone Gradle plugin has its own build and TestKit suite:

```powershell
.\gradlew.bat -p tools\gradle-plugin build
```

## Proof

The main proof mod exercises the loader, Mixin/AT, registries, local and global
mod buses, dispatch filters, typed events, cancelable entity join, entity hurt,
the hook summary, and all six lifecycle phases on a real client and dedicated
server. Build the agent first, then run:

```powershell
cd examples\proof
.\build.ps1
.\run-server.ps1
.\run-client.ps1
```

`run-all.ps1` builds the proof and runs the server and client in sequence.
`run-client.ps1` uses the deterministic fixture world in
`examples/proof/fixture-world/` by default, copying it to
`saves/WeaverProofFixture` before each run. Set `WEAVER_CLIENT_WORLD` to use a
different world. The proof scripts wait for `PROOF RESULT ok=true side=server`
and `PROOF RESULT ok=true side=client`. See
[examples/proof/README.md](examples/proof/README.md) for the current check
names and expected log output.

Shared mode is experimental and environment-dependent:

```powershell
.\run-server-shared.ps1
```

The 2026-11-04 verification runs observed a full shared-mode server proof pass
on this machine: `CLASS SPACE mode=shared`, `PROOF RESULT ok=true side=server
checks=20`, and `SHARED FACTORY PROBE ok=true`. Other hosts can produce the
documented blocker. `tools\verify-ci.ps1` accepts either outcome and records
`shared_mode=documented-blocker` or `shared_mode=proof-pass`; neither outcome is
a support claim or a release gate. `SYSTEM` remains the supported production
mode.

Standalone, integration, and performance proofs:

| Proof | Covers | Run |
|---|---|---|
| [Hooks](examples/hooks-proof/run-all.ps1) | 2.2 hook events and cancellations on client and dedicated server. | `cd examples\hooks-proof; .\run-all.ps1` |
| [Client](examples/client-proof/README.md) | Client API and command API on a real client and dedicated server. | `cd examples\client-proof; .\run-all.ps1` |
| [Ops](examples/ops-proof/README.md) | Loader discovery, mod-list CLI, policies, version and update contracts; no game launch by default. | `powershell -ExecutionPolicy Bypass -File examples\ops-proof\run.ps1` |
| [Capabilities](examples/capability-proof/README.md) | Real `ItemStack` custom-data round trip, bounds, corruption fallback, and runtime attachments. | `cd examples\capability-proof; .\run.ps1` |
| [Config](examples/config-proof/README.md) | Config API and file store without game classes. | `cd examples\config-proof; .\run.ps1` |
| [Network](examples/network-proof/README.md) | Two-process TCP transport, handshake, payload dispatch, and protocol mismatch; not a Minecraft bridge proof. | `cd examples\network-proof; .\run.ps1` |
| [Datagen](examples/datagen-proof/README.md) | Generated data pack loaded and read on a real dedicated server. | `cd examples\datagen-proof; .\run-server.ps1` |
| [Compatibility suite](examples/compat-suite/README.md) | 20-check cross-feature fixture on a real dedicated server: local bus and filters, deferred/direct registration, frozen reads, typed events, and hook callbacks. | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\build.ps1` then `powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\run.ps1`; markers `COMPAT SUITE RESULT ok=true side=server checks=20` and `COMPAT SUITE PROOF ok=true` |
| [Platform benchmark](examples/proof/bench/README.md) | Registry reads, hook dispatch, and scanner budgets without Minecraft classes. | `powershell -ExecutionPolicy Bypass -File examples\proof\bench\run.ps1`; marker `PLATFORM_BENCH BUDGET ok=true` |
| [Toolchain](examples/toolchain-fixture/README.md) | Gradle dependency ranges, optional dependencies, jar-in-jar, loader discovery, and a server run. | `cd examples\toolchain-fixture; .\build.ps1` |

The platform benchmark's measured numbers are intentionally not repeated here;
see [Dispatch model](docs/dispatch.md#performance-model) and the
[platform benchmark section](docs/dispatch.md#platform-benchmark) for the
methodology and tables.

## Documented limitations

- **Shared class space is experimental.** `SYSTEM` is the supported production
  mode. Shared-mode behavior is environment-dependent; CI accepts the
  documented blocker or an observed full proof pass, labels the mode, and does
  not treat either outcome as support or a release gate.
- **The Minecraft network bridge requires `api-game` and the game classes.**
  The bridge registers `weaver:bridge` inside the 26.3 payload codec
  initializers, performs a server-first login/configuration handshake, and
  delivers channels in `PLAY`. Without `api-game`, the transport-only TCP path
  remains. Vanilla peers are detected and left untouched.
- **Client proofs need a fixture world.** `run-client.ps1` copies the
  deterministic fixture by default; use `WEAVER_CLIENT_WORLD` to point at
  another existing world. HUD, tooltip, hand-render, level-stage, screen,
  fog/sky/camera, entity-renderer, model-layer, and block-color entry points
  are injected by the built-in client mixin config when `api-game` is present.
- **`api-game` requires a game classpath.** Without one, the module is skipped
  and typed events are unavailable; the base event factory remains in use.
- **Mods are trusted code.** There is no sandbox, permission layer, or
  compatibility layer for other loaders.
- **Only Minecraft 26.3 is implemented.** Mods and classes load once at boot.
  `-Dweaver.mods.watch=true` starts a change watcher that posts
  `ModsChangedEvent`, but changes require a restart: the supported `SYSTEM`
  class space cannot remove jars or replace loaded classes. Per-mod classloader
  isolation remains a deliberate non-goal.
- **The patched artifact has binary and selected-class source phases.**
  `tools\patcher\patch-game.ps1 -OutDir <dir>` injects interfaces and methods
  into the official jars and strips their signatures, and `tools\sourcepatcher`
  decompiles selected classes with Vineflower, applies unified-diff patches,
  and recompiles them (see [Source patcher](docs/source-patcher.md)). Whole-jar
  source patching remains out of scope. Generate locally from
  your own official jars and never redistribute the patched Minecraft jars.
  `tools\patcher\verify.ps1` is part of the verification gate. On hosts where
  the client world proof hits the documented C2/GPU crashes, the proof retries
  with `-XX:TieredStopAtLevel=1`; the patched classes themselves are proven by
  the GPU-free full class load.
- **Capability persistence covers entities and block entities.** Entity and
  block-entity attachments round-trip through a `weaver:capabilities` data
  root. Level and chunk attachments require a caller-supplied value carrier
  because 26.3 exposes no public per-holder save/load entry point. The
  `weaver:capability/sync/v1` channel is installed with the game provider and
  bridge recipients when `api-game` is present; it is clientbound only and
  covers `RUNTIME` changes for entities, block entities, chunks, and levels.
- **Configuration hot reload is explicit.** `ConfigManager.reload()` re-reads
  and re-validates a file and posts `ConfigReloadedEvent`; the file watcher is
  opt-in with `-Dweaver.config.watch=true`. `SERVER_TO_CLIENT` specs are pushed
  on `PLAY` over the bridge; the client applies them read-only with no upload,
  acknowledgement, retry, or conflict resolution.
- **Command trees synchronize automatically.** The client records the received
  tree (`source=client`), late registrations resend, `unregister()` rebuilds the
  live dispatcher, and permission decisions persist to `weaver/data` JSON when
  the loader installer runs. The 26.3 `Commands.dispatcher` field is `final`, so
  the rebuild mutates the existing dispatcher instead of swapping it.
- **Data registries freeze permanently.** `DataRegistryManager.reloadAll`
  applies snapshots only to open registries and reports frozen ids by name; it
  never unfreezes a production registry.

## Layout

```text
api/          game-free mod API: lifecycle, event bus, registry, SPI,
              config, capability, client, command, and network contracts
api-game/     optional game-typed API: GameWeaverMod, typed events,
              GameRegistries, typed factories (compiled against 26.3)
core/         loader agent, class space, metadata, lifecycle, event scanner,
              hooks, registry backend, access transformers, mod ops, version
              checks, config, capabilities, client, command, and network backends
mixin/        Mixin engine integration
diagnostics/  crash-report diagnostics
sync/         manifest, mirror, and resume support
tools/        datagen/, fetch-game/, generator/, gradle-plugin/, installer/,
              launcher/, verify.ps1, verify-ci.ps1
examples/     main proof plus compatibility suite, platform benchmark, hooks,
              client, ops, capability, config, network, datagen,
              toolchain-fixture, and starter examples
docs/         complete documentation index
spike/        small, runnable proofs used before core changes
```

## Documentation

The complete documentation index is [docs/README.md](docs/README.md). Start
with [architecture](docs/architecture.md), [event bus](docs/events.md),
[hook points](docs/hooks.md), [registry API](docs/registry.md),
[typed game API](docs/api-game.md), [toolchain](docs/toolchain.md),
[platform operations](docs/platform-ops.md),
[compatibility](docs/compatibility.md), and the
[release checklist](docs/release-checklist.md).

The examples index is [examples/README.md](examples/README.md); the main
acceptance proof is documented in
[examples/proof/README.md](examples/proof/README.md).

## Design invariants

- One shared class space. Mods and the game see one another; there is no
  classloader isolation.
- Mods load once at boot. There is no reload, watch, generation, or swap.
- Access transformers run before Mixin; both are applied at load time.
- Direct game access remains the primary path. The convenience APIs do not
  replace it.
- No sandbox and no compatibility layer for other loaders.
- Only Minecraft 26.3 is implemented.

## License

LGPL-2.1-only. See `LICENSE` and `README-LICENSE.md`.
