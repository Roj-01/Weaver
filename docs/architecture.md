# Architecture

This page describes the Weaver 2.2 runtime architecture for Minecraft 26.3:
the module and layer layout, the two class-space modes, the boot sequence, the
transform order, the mod lifecycle, mod discovery and ordering, and version
handling. Operational commands are in [Platform operations](platform-ops.md).

## Overview

Weaver is a Java agent attached to the game JVM with `-javaagent`. The agent
runs before the game main class and prepares one shared class space, discovers
and validates mods, installs bytecode transformers, and arms mod Mixin
configurations. Mods and the game then see one another's classes: there is no
per-mod classloader and no isolation layer.

The normal runtime is the default `SYSTEM` class-space mode. `SHARED` mode is
an explicit, opt-in alternative that hosts the game and mods on one
`SharedModClassLoader`; it is verified on the dedicated server and the client
(see [Verification status](platform-ops.md#verification-status-22-workstream))
and is not a claim of classloader isolation.

Key code:

| Concern | Main classes |
|---|---|
| Agent entry and boot | `net.weaver.core.LoaderAgent` |
| Class-space selection and setup | `net.weaver.core.ClassSpace`, `SharedModClassLoader`, `SharedMain` |
| Mod discovery, validation, ordering | `net.weaver.core.ModLoader`, `ModJar`, `ModMetadata`, `VersionRange` |
| Mods-directory change reporting | `net.weaver.core.watch.ModWatchService`, `ModChangeReport` |
| Data-pack reading and precedence | `net.weaver.core.registry.DataPackRepository`, `DataPackLoader` |
| Mod-list reporting and policies | `net.weaver.core.modops.*` |
| Version checks and compatibility | `net.weaver.api.WeaverApi`, `net.weaver.core.version.*` |
| Lifecycle phases | `net.weaver.core.LifecycleHooks` and `net.weaver.core.lifecycle.*` |
| Injected game hooks | `net.weaver.core.hook.GameHookTransformer`, `GameHooks` |
| Mixin integration | `net.weaver.mixin.core.MixinEngine`, `WeaverMixinTransformer` |
| Access transformers | `net.weaver.core.at.AccessTransformer`, `AccessTransformerTransformer` |

## Layers

The Gradle build has six modules (`settings.gradle.kts`). `tools/` is not a
runtime layer; it contains launchers and build-time tooling.

| Module | Runtime role | Notes |
|---|---|---|
| `api` | Game-free mod API: lifecycle contracts, event bus, registry API, SPI. | No `net.minecraft.*` references. |
| `api-game` | Optional typed game API: `GameWeaverMod`, typed events, `GameRegistries`, `TypedGameEventFactory`. | Compiled against Minecraft 26.3 with the game classpath on `compileOnly`; game classes are never bundled. `core` has no compile dependency and installs the typed factory reflectively. |
| `core` | Loader agent: class space, metadata, lifecycle, event scanner, hooks, registry backend, access transformers, mod ops, version checks. | Packaged as `core/build/agent/weaver-agent.jar` plus `core/build/agent/lib/`. The agent jar manifest `Class-Path` lists the agent lib. |
| `mixin` | Mixin engine integration (`MixinEngine`, `MixinBridge`, `WeaverMixinTransformer`). | Upstream Mixin 0.8.7 integration; mod configurations are armed by the agent. |
| `diagnostics` | Crash-report diagnostics. | Not on the boot path. |
| `sync` | Manifest, mirror, and resume support. | Not on the boot path. |
| `tools` | Launcher, Gradle plugin, datagen, project generator, installer, game fetcher. | Build-time and operator tooling, not loader classes. |

The optional `api-game` artifact is what makes typed events possible without
putting Minecraft types into `api`. `LoaderAgent.installGameEventFactory`
resolves `net.weaver.api.game.TypedGameEventFactory` through the game loader and
installs it when present; otherwise it logs `TYPED EVENTS unavailable` and keeps
the base factory (`LoaderAgent.java:217-232`). See [Typed game API](api-game.md)
for the event bridge and binary-compatibility rules.

## Class-space modes

`ClassSpace.Mode` declares `SYSTEM` and `SHARED`. `ClassSpace.selectMode`
returns `SHARED` only when an explicit request is present; every other
combination resolves to `SYSTEM` (`ClassSpace.java:140-145`).

| Mode | How it is selected | Game classes | Mod jars | Classloader used for transforms | Status |
|---|---|---|---|---|---|
| `SYSTEM` | Default; no request. | On the system classpath. | Appended to the system classloader with `Instrumentation.appendToSystemClassLoaderSearch`. | Game loader hosting the game classes (the system loader in the supported layout). | Supported production mode and the 2.1 baseline. |
| `SHARED` | `-Dweaver.classspace.shared=true` (launcher `-SharedClassSpace` or `WEAVER_SHARED_CLASS_SPACE=true`) plus a game classpath. | Listed in a classpath file; loaded by `SharedModClassLoader`, not by the system loader. | Same shared loader, together with the game entries and the `api-game` jar. | The shared loader itself. | Opt-in; verified on the dedicated server and the client. |

### SYSTEM mode

`ClassSpace.create` resolves the loader that hosts the game, creates the space,
then appends every accepted mod jar to the system loader
(`ClassSpace.java:115-119`). It logs:

```text
[weaver] CLASS SPACE mode=system game-loader=<loader> mods=<n>
```

Mods, the game, and the loader therefore share one set of classes. The hook and
lifecycle transformers only transform classes whose defining loader equals the
resolved game loader (a `null` loader is also accepted); see
`GameHookTransformer.isHostLoader` (`GameHookTransformer.java:355-358`),
`RegistryTransform.isHostLoader` (`RegistryTransform.java:84-86`),
`ClientInitTransform.isHostLoader` (`ClientInitTransform.java:69-71`), and
`ServerInitTransform.isHostLoader` (`ServerInitTransform.java:66-68`).

If shared mode is not requested and the game main class is not visible to the
system loader, `ClassSpace` logs
`CLASS SPACE UNSUPPORTED reason=game-hosted-by-non-system-loader action=continue-system`
and continues in system mode rather than creating a shared loader that cannot
transform the game (`ClassSpace.java:111-114`).

### SHARED mode

Shared mode is never selected implicitly. When requested, `ClassSpace`:

1. Reads the game classpath from `-Dweaver.shared.classpathFile=<file>` or
   `-Dweaver.shared.classpath=<entries>` (`ClassSpace.java:152-175`). Entries
   are split on semicolons, the platform path separator, or line breaks.
2. Builds URLs from the game entries, the accepted mod jars, and the
   `api-game` jar when it is visible to the system loader
   (`ClassSpace.java:90-102`).
3. Creates `SharedModClassLoader` with the system loader as parent
   (`SharedModClassLoader.java:13-17`; name `weaver-shared`).
4. Resolves `net.weaver.api.game.*` child-first: `SharedModClassLoader.loadClass`
   defines those classes itself, on first use, and falls back to the parent only
   when its own URLs do not contain the class. This keeps typed events
   linkable (their accessors resolve game types through the shared loader, and
   an `api-game` supertype is defined by the same loader as its subtypes),
   while base `net.weaver.api` and `net.weaver.core` classes stay shared from
   the agent library through the parent. Nothing is defined eagerly, so no
   game supertype is loaded before the transformers are installed.
5. Sets both `gameLoader` and `modLoader` to the shared loader and logs:

```text
[weaver] CLASS SPACE mode=shared game-entries=<n> mods=<n> loader=weaver-shared api-game=<child-first|absent>
```

`SharedMain` is the bootstrap main class. It obtains the class space from
`LoaderAgent.classSpace()`, reads `-Dweaver.shared.main`, sets the thread
context classloader to the shared loader for the duration of the call, and
loads and invokes the game main class through that loader
(`SharedMain.java:28-64`). It logs `SHARED MAIN class=<class> loader=weaver-shared`
and restores the previous context loader in a `finally` block.

Because the game main is loaded through the shared loader, the loader-gated
hook and lifecycle transformers see `loader == gameLoader` and apply. The
launcher still runs `net.weaver.core.SharedMain`, so the system classpath is the
agent jar and its manifest class path (the agent lib), not the game.

`SharedModClassLoader` uses normal parent-first `URLClassLoader` delegation for
every package except `net.weaver.api.game`.
Shared mode is one class space, not isolation: every mod shares the game and
the loader classes. See [Platform operations](platform-ops.md#shared-mode) for
launch commands, prerequisites, and log signals.

## Boot sequence

`LoaderAgent.premain` performs the following steps. All of them run before the
game main class in both modes.

1. Parse agent options. Recognized keys are `side`, `modsDir` (`modulesDir` is
   an alias), `configDir`, `dataDir`, `markersFile`, `integratedServer`,
   `modListFile`, and `modListFormat`. System-property fallbacks are
   `weaver.side`, `weaver.modsDir`, `weaver.configDir`, `weaver.dataDir`,
   `weaver.markersFile`, `weaver.modListFile`, and `weaver.modListFormat`
   (`LoaderAgent.java:57-95`). Options are separated by `;` or `,` in the
   `-javaagent` argument.
2. Open the markers sink when configured and log `[weaver] AGENT premain ...`.
3. Discover mods with `ModLoader.discover(..., ModPolicies.fromSystemProperties())`
   (`LoaderAgent.java:94`). See [Mod discovery and ordering](#mod-discovery-and-ordering).
4. Write the optional mod-list report (`LoaderAgent.java:95`,
   `LoaderAgent.java:182-201`). Write errors inside `ModReportWriter.write` are
   logged as `MOD LIST FAILED` and do not fail boot.
5. Create the class space (`LoaderAgent.java:96`). This is where SYSTEM appends
   mod jars and SHARED builds the shared loader. No game or `api-game` class is
   defined at this point.
6. Run `VersionChecks.check(space.gameLoader(), WeaverApi.GAME_VERSION)` and log
   the returned line (`LoaderAgent.java:97-99`).
7. Install the typed event factory reflectively when `api-game` is visible
   (`LoaderAgent.java:100`).
8. Install `LifecycleHooks` with the accepted mods, the mod loader, side,
   integrated-server expectation, config root, data root, and logger
   (`LoaderAgent.java:101-102`). This creates per-mod config/data directories,
   a local event bus, and the three mod contexts.
9. Install the Minecraft registry backend (`LoaderAgent.java:105-110`). A
   failure is logged as `REGISTRY BACKEND INSTALL FAILED` and boot continues.
10. Parse access-transformer rules from all accepted mods and install
    `AccessTransformerTransformer` when at least one rule exists
    (`LoaderAgent.java:112-116`).
11. Create the Mixin engine, set the side and loaded-class probe, call
    `engine.boot(space.gameLoader())`, pass `Instrumentation` to `MixinBridge`,
    and install `WeaverMixinTransformer` (`LoaderAgent.java:118-130`).
12. Install `GameHookTransformer`, `RegistryTransform`, `ClientInitTransform`
    (client side only), and `ServerInitTransform` (`LoaderAgent.java:132-137`).
13. Arm each mod's Mixin configurations through `engine.addConfig(...)`. This
    runs right after the transformers are installed and before anything that
    loads game classes (the version check, the typed event factory, network and
    capability activation), so a mod mixin that targets a class those steps
    load (for example `Connection` or a packet class) is not excluded as
    `MIXIN LIVE UNSUPPORTED`. The index and statistics are emitted after the
    activation steps.
14. Log `[weaver] AGENT READY side=<side> mods=<n> configs=<n> class-space=<system|shared>`
    (`LoaderAgent.java:149-150`).
15. In SYSTEM mode the JVM invokes the game main class directly. In SHARED
    mode the JVM invokes `net.weaver.core.SharedMain`, which loads the game main
    through the shared loader.

`ModPolicyException` is rethrown after logging `MOD POLICY FATAL`; see
[Failure modes](#failure-modes). Any other exception is logged (to standard
output and the markers file) as `[weaver] AGENT FAILED <reason>` followed by
`[weaver] AGENT FAILURE POLICY policy=fatal action=abort-jvm`, and is rethrown
so the JVM stops instead of running the game without Weaver. With
`-Dweaver.agent.failurePolicy=warn` the agent logs
`AGENT FAILURE POLICY policy=warn action=continue-without-weaver` and returns.

The version check is diagnostic only: `LoaderAgent` logs
`compatible=false` but does not abort the boot. The same is true for a missing
optional `api-game` artifact, which is simply reported as absent.

### Launcher responsibilities

`tools/launcher/launch.ps1` sits outside the agent. It finds JDK 26, checks that
the agent jar exists, creates the config/data/log directories, and builds the
JVM command. For the client it reads `<GameDir>\classpath.txt` and passes the
natives/assets paths; for the server it builds the classpath from
`<ServerDir>\libraries` plus `versions\26.3\server-26.3.jar`. In shared mode it
writes the server classpath to `shared-classpath.txt`, selects
`net.weaver.core.SharedMain`, and sets `-Dweaver.classspace.shared=true`,
`-Dweaver.shared.main`, and `-Dweaver.shared.classpathFile`. It then passes the
agent options (`side`, `integratedServer`, `modsDir`, `configDir`, `dataDir`,
`markersFile`), waits for the optional `-WaitFor` regex, and stops the process.

The launcher does not discover mods, validate dependencies, or transform
classes; the agent does those in both modes. See
[Platform operations](platform-ops.md#launcher) for parameters, defaults, and
result codes.

## Patched game artifact

The patcher (`tools/patcher/patch-game.ps1 -OutDir <dir>`) turns the official
Minecraft 26.3 artifacts into `<dir>\client-patched.jar` and
`<dir>\server-patched.jar`. The patched jars add
`net.weaver.patched.PatchedGame` with the `weaver$patchLevel()I` probe and
`net.weaver.patched.WeaverAttachmentHolder` to `ItemStack`, `Entity`,
`BlockEntity`, `Level`, and `LevelChunk`; `ItemStack` and `Level` also
implement `PatchedGame`. The official 26.3 artifacts are unobfuscated, so the
patcher applies the patches directly without a remap step.

`tools/launcher/launch.ps1` substitutes the patched artifact at launch time:

- `-PatchedClientJar <path>` or `WEAVER_PATCHED_CLIENT_JAR` replaces the game
  jar entry from `<GameDir>\classpath.txt` (the first entry, `client.jar`, or
  an equivalent official client jar) and keeps every other entry in order.
- `-PatchedServerJar <path>` or `WEAVER_PATCHED_SERVER_JAR` replaces the
  `versions\26.3\server-26.3.jar` entry in the classpath built from
  `<ServerDir>\libraries` and the server jar.
- In `-SharedClassSpace` mode the patched client classpath is written to
  `shared-classpath.txt` next to the log so `SharedModClassLoader` sees the
  patched entry.

The launcher prints `LAUNCH PATCH side=<side> patched=true jar=<path>` or
`LAUNCH PATCH side=<side> patched=false` before it starts the JVM, and it fails
when a supplied path does not exist. Without the flags and environment
variables the classpath is left exactly as it was.

`LoaderAgent.premain` detects the artifact after the class space is created and
before `VersionChecks` runs. `PatchDetection.classify(space.gameLoader())`
resolves `net.weaver.patched.PatchedGame` with
`Class.forName(name, false, space.gameLoader())` and reflects the
`weaver$patchLevel()` method, so neither `core` nor `api` links against patched
classes at compile time. A static probe is invoked directly; when the injected
method is an instance method, the reported level comes from the optional
`patch.json` marker. The agent logs one of:

```text
[weaver] GAME PATCH level=<n> artifact=patched
[weaver] GAME PATCH level=0 artifact=unpatched action=runtime-only
[weaver] GAME PATCH probe=unavailable reason=<reason>
```

When the marker class is present and `META-INF/weaver/patch.json` is readable,
the patched line appends `patch-set=<id>`; a missing or malformed marker is
ignored. Detection catches every failure and never aborts the boot.

An unpatched jar remains fully supported. The runtime transformers
(`GameHookTransformer`, `RegistryTransform`, `ClientInitTransform`, and
`ServerInitTransform`) still inject the hook and lifecycle entry points, and
the loader reports `artifact=unpatched action=runtime-only`. The patched
artifact is an additive path, not a replacement for the transformer path, and
`WeaverApi.PATCH_LEVEL` (`1`) identifies the patch level this build expects.

## Transform order

Transformers are registered in this order during `premain`, and the JVM applies
the applicable ones in registration order for each class definition:

| Order | Transformer | Scope and trigger | Failure behavior |
|---|---|---|---|
| 1 | `AccessTransformerTransformer` | Any non-JDK class with a matching AT rule. Access transformers run before Mixin (`AccessTransformerTransformer.java` class comment). | Logs `AT FAILED`, returns `null`, original bytes are used. |
| 2 | `WeaverMixinTransformer` | Classes indexed by an armed Mixin configuration, plus mixin classes themselves. Skips JDK, ASM, Sponge, and `net/weaver/mixin/` prefixes. | Logs `MIXIN FAILSAFE`, returns `null`, original bytes are used. |
| 3 | `GameHookTransformer` | Classes named by `HookSpec`s, when `loader == gameLoader` (or `loader == null`). | Logs `GAME HOOK TRANSFORM FAILED`, returns `null`; the first tick logs `GAME HOOK SUMMARY`. |
| 4 | `RegistryTransform` | `net/minecraft/core/registries/BuiltInRegistries`, when the loader is the game loader. Injects `onInitialize` at `bootStrap` entry, `onCommonSetup` at `bootStrap` return, and `onRegister` at `freeze` entry. | Logs `REGISTRY TRANSFORM FAILED`, returns `null`. |
| 5 | `ClientInitTransform` | Client side only. `net/minecraft/client/Minecraft.<init>`, immediately before `new Options`. Injects `onClientSetup`. | Logs `CLIENT TRANSFORM FAILED`, returns `null`. |
| 6 | `ServerInitTransform` | `net/minecraft/server/MinecraftServer.runServer` entry. Installed for dedicated and integrated servers. Injects `onServerSetup`. | Logs `SERVER TRANSFORM FAILED`, returns `null`. |

`GameHookTransformer.install` side-gates its spec list from
`LifecycleHooks.clientAvailable()` and `serverAvailable()`
(`GameHookTransformer.java:84`, `GameHookTransformer.java:101-120`). A spec
whose side is not hosted is recorded as skipped and removed from the transform
index. When neither side is hosted, `BOTH` specs are still treated as hosted so
side-agnostic test processes keep working. A hosted required spec whose target
class resource is absent from the game loader is recorded as `MISSING`
(`reason=class-absent`) at install, so strict mode can fail on it; a
`net/minecraft/client/` target on a process without a client is recorded as
skipped.

The AT and Mixin transformers do not perform an explicit `loader == gameLoader`
check of the kind the hook and lifecycle transformers use. AT applies the
parsed rules to matching non-JDK classes; the Mixin transformer asks the engine
whether a class is a declared target or mixin class. See
[Hook points](hooks.md) for the hook-spec catalog and [Toolchain](toolchain.md)
for Mixin/AT build details.

## Lifecycle

`LifecycleHooks` implements six lifecycle phases. Each phase is guarded by an
`AtomicBoolean`, so duplicate triggers are no-ops (`LifecycleHooks.java:22-40`,
`LifecycleHooks.java:68-179`).

| Phase | Trigger | What happens |
|---|---|---|
| `onInitialize` | `BuiltInRegistries.bootStrap` entry (`RegistryTransform`). | Each mod main class is loaded through the mod loader and instantiated. An instance that is not a `WeaverMod` is logged as `MOD ENTRY not-a-WeaverMod` and dropped. After all instances are registered on `EventBus.GAME`, each `onInitialize` is called and a `ModLifecycleEvent` is posted on that mod's local bus. After all mods, `AutoSubscriberScanner` scans the mod jar. |
| `onRegister` | `BuiltInRegistries.freeze` entry (`RegistryTransform`). | `RegistryManager.beginRegistration`, a first `flushAll`, `RegisterEvent` per registry when listeners exist, `onRegister` callbacks, a second `flushAll`, `markFrozen`, `RegistryFreezeEvent` per registry when listeners exist, and `endRegistration`. |
| `onCommonSetup` | `BuiltInRegistries.bootStrap` return (`RegistryTransform`). | Calls `onCommonSetup` on every loaded mod. |
| `onClientSetup` | `Minecraft.<init>` before `new Options` (`ClientInitTransform`, client only). | Calls `onClientSetup` on every loaded mod. |
| `onServerSetup` | `MinecraftServer.runServer` entry (`ServerInitTransform`). | Calls `onServerSetup` on every loaded mod. |
| `onLoadComplete` | First client and/or server tick, after every expected side is ready. | Calls `onLoadComplete` and logs `LIFECYCLE COMPLETE expected-client=... expected-server=...`. |

`expectedClient` is true when the agent side is `client`. `expectedServer` is
true when the side is `server` or when `integratedServer` is true; the agent
defaults `integratedServer` to true for the client and false otherwise
(`LoaderAgent.java:90-93`, `LifecycleHooks.java:50-51`). Completion waits until
each expected side has both run its setup phase and reached its first tick
(`LifecycleHooks.java:270-285`). Known gap: because a client expects its
integrated server by default, a client that only joins remote servers never
reaches `onLoadComplete`. Changing that would move client completion before
any world exists, which the acceptance proof's completion checks rely on, so it
is an open lifecycle decision rather than a silent fix. The client and
server tick flags are set by
`LifecycleHooks.onClientTick` and `onServerTick`, called from the injected tick
hooks.

`LifecycleHooks.install` creates `<configDir>/<modId>` and `<dataDir>/<modId>`
for each mod, creates `EventBus.local(modId)`, and exposes base, client, and
server contexts (`LifecycleHooks.java:44-66`). The entrypoint instance is
registered on `EventBus.GAME` before `onInitialize`; `AutoSubscriberScanner`
runs after all `onInitialize` calls and skips the entrypoint class with
`SUBSCRIBER ENTRYPOINT SKIPPED` (`AutoSubscriberScanner.java:132-134`).

Most phase failures are isolated: `MOD LOAD FAILED`, `MOD INIT FAILED`,
`MOD REGISTER FAILED`, and the other `MOD <PHASE> FAILED` lines are logged per
mod, and the remaining mods still run. `LifecycleHooks.reset()` clears the
phase state and registry manager for tests.

## Mod discovery and ordering

`ModLoader.discover` is the single discovery path used by the agent and by the
standalone `ModListCommand` (`ModLoader.java:47-158`). It performs these stages:

1. **Scan.** List `*.jar` directly under the mods directory (non-recursive) and
   sort by jar file name (`ModLoader.java:68-77`). A missing directory logs
   `MODS DIR missing path=...` and returns an empty report
   (`ModLoader.java:64-67`).
2. **Read.** Open each jar and parse `weaver.mod.json` (`ModJar.read`). Schema 1
   and 2 are accepted. The id must match `[a-z0-9_-]+`; `version` defaults to
   `0.0.0`, `name` to the id, `side` to `both`, and `minecraft` to `26.3`. The
   main class comes from `mainClass` or `entrypoints.main`. The loader also
   reads declared mixin configs, access-transformer files (or
   `META-INF/accesstransformer.cfg`), and the regular class entries used for
   policy checks (`ModJar.java:33-75`, `ModMetadata.java`).
3. **Side filter.** A mod whose declared side is neither `both` nor the process
   side is skipped and recorded as `WRONG_SIDE` with a WARN diagnostic
   (`ModLoader.java:92-102`).
4. **Duplicate id.** The jar with the highest version wins; on a version tie a
   top-level jar beats a nested (jar-in-jar) one, and otherwise the first jar in
   filename order wins. The other jars with the same id are skipped as
   `DUPLICATE_ID` with a WARN diagnostic that names the kept jar. Nested jars
   are extracted only from parents that were read and match the process side,
   into `<jijDir>/<parent-name>-<hash>`, and a nested jar is dropped when its
   parent is not accepted.
5. **Read failures.** A jar that cannot be opened or parsed is recorded as
   `READ_FAILED` with a WARN diagnostic and does not stop other jars
   (`ModLoader.java:122-135`).
6. **Validation.** A matching `breaks` entry rejects the declaring mod as
   `REJECTED_BREAKS`. A missing required dependency is `REJECTED_DEPENDENCY`.
   A present dependency outside its version range is `REJECTED_VERSION`. An
   optional dependency with an incompatible version produces an
   `OPTIONAL_VERSION` WARN but does not reject the mod. Rejection propagates:
   a mod whose required dependency was rejected is itself rejected as
   `REJECTED_DEPENDENCY` (`reason=dependency-rejected`), and validation repeats
   until nothing changes. `VersionRange.matches` supports `*`, exact
   equality, comparison operators (`>=`, `<=`, `>`, `<`, `=`, with optional
   whitespace before the version, as in `>= 1.2.0`), tilde (`~1.2.3` means
   `>=1.2.3 <1.3`, `~1.2` means `>=1.2 <1.3`, `~1` means `>=1 <2`), caret (`^` pins the first
   non-zero component: `^1.2` means `<2`, `^0.2` means `<0.3`), Maven ranges
   (`[1.0,2.0)`, `[1.0,)`, `(,2.0]`, `[1.0]`, and comma-separated unions), and
   comma/space-separated conjunctive constraints. Versions compare by numeric
   core first; a pre-release (`-SNAPSHOT`) sorts below the release with the same
   core only. A dependency or `breaks` range that cannot be parsed rejects the
   declaring mod (`MOD DEPENDENCY RANGE INVALID` / `MOD BREAKS RANGE INVALID`)
   instead of being matched exactly.
7. **Class policies.** Accepted mods are scanned for class entries shipped by
   more than one mod (`DUPLICATE_CLASS`) and for class entries physically under
   `net/weaver/api/` (`API_SHADOW`) (`ModLoader.java:175-241`). Nested jars and
   multi-release `META-INF/versions/**` entries are not part of this scan. See
   [Platform operations](platform-ops.md#policies).
8. **Order.** Required dependencies, and optional dependencies whose target is
   present and version-compatible, add edges. Kahn's algorithm uses a
   lexicographically sorted ready set and sorted dependent traversal, so
   dependencies precede dependents and the order is deterministic
   (`ModLoader.java:336-398`). If the graph contains a cycle,
   the loader logs
   `MOD ORDER cycle=true remaining=<n> action=filename-order`, records a
   `CYCLE` WARN per strongly connected component, and still accepts the
   remaining members in deterministic order (the implementation sorts them by
   mod id). `ModDependencyGraph.cycles()` reports the components
   (`ModDependencyGraph.java:125-127`).
9. **Report.** `ModLoader.report()` returns an immutable `ModList` containing
   every discovered candidate, including rejected jars. Entries are sorted by
   jar file name and then id; loaded entries carry a zero-based `loadOrder`
   and rejected entries carry `-1` (`ModList.java:53-59`,
   `ModLoader.java:140-157`).

Only `LOADED` entries participate in the class path (`ModStatus.java:10-35`).
The report schema and the CLI are documented in
[Platform operations](platform-ops.md#mod-operations).

## Versioning

Weaver separates the binary API version, the platform feature level, and the
Minecraft/game-hook version.

| Constant | Value | Meaning |
|---|---|---|
| `WeaverApi.VERSION` | `2.1.0` | Binary API version. Intentionally unchanged for the additive 2.2 feature level. |
| `WeaverApi.API_MAJOR` | `2` | Major version of the binary API contract. |
| `WeaverApi.API_MINOR` | `1` | Minor version of the binary API contract. |
| `WeaverApi.MIN_SUPPORTED_API` | `2.1.0` | Oldest accepted API version within major version 2. |
| `WeaverApi.FEATURE_LEVEL` | `2.2` | Platform capability level; consumers gate new 2.2 capabilities on this, not on `VERSION`. |
| `WeaverApi.GAME_VERSION` | `26.3` | Minecraft version this build targets. |
| `WeaverApi.HOOK_TARGET` | `26.3` | Hook-spec target this build implements. |
| `WeaverGameApi.VERSION` | `26.3` | Version marker of the optional typed game API. |

`WeaverApi` (`api/src/main/java/net/weaver/api/WeaverApi.java`) provides
`isCompatibleApi`, `isCompatibleGame`, `requireCompatibleApi`,
`requireCompatibleGame`, `isGameApiCompatible(ClassLoader)`, and
`stabilityPolicy`. API compatibility requires the same major version and a
version greater than or equal to `MIN_SUPPORTED_API`; `v` prefixes and
`-suffix`/`+build` suffixes are ignored. Game compatibility accepts `26.3` and
numeric equivalents such as `26.3.0`; no other game version is supported.
`requireCompatibleApi` and `requireCompatibleGame` throw `IllegalStateException`
on a mismatch.

The core compatibility layer is `net.weaver.core.version`:

| Class | Role |
|---|---|
| `SemVer` | Tolerant `major.minor.patch` parser; one to three components, optional `v`, suffixes stripped. |
| `GameVersion` | Game id plus hook target; `CURRENT` is `26.3`/`26.3`. |
| `PlatformTarget` | API version plus `GameVersion`; `current()` and `supported()`. |
| `CompatibilityMatrix` | `SUPPORTED`, `EXPERIMENTAL`, `UNSUPPORTED`; rows for API `2.1` and `2.2` on game/hook `26.3`. API rows match by `major.minor`; game and hook rows match by exact id or numeric equality. `supports` accepts `SUPPORTED` and `EXPERIMENTAL`. |
| `VersionChecks` | Boot-time result and log line; never throws. |

The current build reports the `2.1` matrix row because `VERSION` remains
`2.1.0`, while `FEATURE_LEVEL` is `2.2`. `CompatibilityMatrix.currentLevel()` is
`SUPPORTED`, and `CompatibilityMatrix.describe()` renders the matrix for logs
and documentation.

`VersionChecks.check(loader, gameVersion)` combines the API level, the
game/hook level, and the optional typed game API probe. The typed artifact is
optional: when `net.weaver.api.game.WeaverGameApi` is absent, the probe is
reported as absent and the base check is unaffected; when present, its
`VERSION` must be compatible. The returned message is logged at boot:

```text
weaver version check: api=2.1.0 (SUPPORTED) game=26.3 (SUPPORTED) typedGameApi=26.3 (compatible) compatible=true
```

Only Minecraft 26.3 is implemented. A second game version is not a new matrix
row alone: it requires new `HookSpec` sets, Mixin refmaps/remapping for the new
runtime, an `api-game` recompilation against the new game, and new proofs. That
work is out of scope for 2.2 (see the `CompatibilityMatrix` and `VersionChecks`
class comments).

## Failure modes

| Situation | Signal | Effect |
|---|---|---|
| Mods directory missing | `MODS DIR missing path=...` | Empty report; boot continues. |
| Jar or descriptor unreadable | `MOD READ FAILED path=...`; `READ_FAILED` WARN | Entry is rejected; other jars continue. |
| Wrong side or duplicate id | `MOD SKIP ...`; WARN | Jar is not loaded; boot continues. |
| Missing required dependency, incompatible version, or matching `breaks` | `MOD DEPENDENCY MISSING`, `MOD DEPENDENCY VERSION`, `MOD BREAKS`; WARN | The declaring mod is rejected; other jars continue. |
| Dependency cycle | `MOD ORDER cycle=true remaining=... action=filename-order`; `CYCLE` WARN | Cycle members are still accepted and ordered deterministically after the acyclic prefix. |
| Duplicate class under `warn` | `MOD CLASS CONFLICT ...`; `DUPLICATE_CLASS` WARN | The first duplicate on the load-ordered classpath supplies the class; later copies are ignored by the JVM; jars still load. |
| API shadow under `warn` | `MOD API SHADOW class=... mod=... action=warn`; `API_SHADOW` WARN | The jar still loads; the report records the shadowing class. |
| Duplicate class or API shadow under `fatal` | `action=fatal`; `ModPolicyException` | Open jars are closed; the agent logs `MOD POLICY FATAL` plus each diagnostic and rethrows, aborting agent startup (JVM fail-fast). |
| Shared mode requested without a classpath | `IllegalStateException` from `ClassSpace.readGameClasspath` | The agent logs `AGENT FAILED` and aborts the JVM. Under `weaver.agent.failurePolicy=warn`, `SharedMain` sees no class space and exits `2` with `SHARED MAIN FAILED`. |
| Version check fails | `weaver version check: ... compatible=false` | Diagnostic only; the boot is not aborted by `VersionChecks`. |
| Transform failure | `AT FAILED`, `MIXIN FAILSAFE`, `GAME HOOK TRANSFORM FAILED`, `REGISTRY TRANSFORM FAILED`, `CLIENT TRANSFORM FAILED`, `SERVER TRANSFORM FAILED` | The failing transformer returns `null`; original bytes are used. A lifecycle transform failure is also recorded as `FATAL LIFECYCLE INJECTION MISSING method=<class>#transform-failed`, so the default `fatal` lifecycle policy stops the game on the next tick. Failed game-hook specs are `INVALID`, never `APPLIED`. |
| Required hook missing under strict mode | `GAME HOOK STRICT FAILURE required_misses=...` | Throws `IllegalStateException` from the tick path when `weaver.hooks.strict=true`, on the first tick or, for a spec that turns missing or invalid later (`GAME HOOK LATE MISSING`/`LATE INVALID`), on the next tick. |

The standard system-mode and opt-in shared-mode proofs pass on both the
dedicated server and the client in the 2.2 verification run. See
[Verification status](platform-ops.md#verification-status-22-workstream).

A generic `Throwable` in `LoaderAgent.premain` is logged as `AGENT FAILED` and,
by default, rethrown so the JVM aborts. Only with
`-Dweaver.agent.failurePolicy=warn` does the game keep running without a fully
initialized Weaver agent (in SHARED mode `SharedMain` then detects the missing
class space and exits).

## Out of scope

- **Classloader isolation.** Weaver has one shared class space. `SHARED` mode
  is one loader for the game and all mods, not one loader per mod. Mods see one
  another and the game.
- **Hot reload of mods, generation swap, or applying watched jar changes.**
  Mods are discovered and loaded once at boot; adding, removing, or replacing a
  mod needs a restart. `net.weaver.core.watch.ModWatchService` only reports that
  a restart is required (`appendToSystemClassLoaderSearch` cannot remove a
  jar). In development runs the hot reload
  ([toolchain](toolchain.md#developer-hot-swap)) applies a project's changes
  live where the JVM allows it and otherwise restarts the game by itself into
  the same world or server, mods-folder changes included.
- **Multiple Minecraft versions.** Only 26.3 is implemented. Adding another
  version requires the hook-spec, Mixin/refmap, `api-game`, and proof work
  described under [Versioning](#versioning).
- **A mod marketplace or network update service.** `UpdateChecks` defines a
  contract and a default `NOT_CONFIGURED` holder only; no network
  implementation exists. See [Platform operations](platform-ops.md#update-checks).

The mods-directory watch is not part of `LoaderAgent.premain`. When opted in
with `-Dweaver.mods.watch=true`, an integration starts `ModWatchService` after
discovery; it posts `ModsChangedEvent` and logs
`[weaver] MODS CHANGED ... action=restart-required`. It is deliberately outside
the boot path so normal boot order and I/O remain deterministic. Data-pack
reload is the same kind of honest boundary: `DataRegistryManager.reloadAll`
applies to open registries only and refuses frozen ones instead of performing a
generation swap. See [Registry API](registry.md#reload-semantics).

Both class-space modes are verified on the dedicated server and the client;
see [Verification status](platform-ops.md#verification-status-22-workstream).

## Related

- [Documentation index](README.md)
- [Event bus](events.md) and [dispatch model](dispatch.md)
- [Registry API](registry.md)
- [Typed game API](api-game.md)
- [Hook points](hooks.md)
- [Toolchain](toolchain.md)
- [Migration to 2.1](migration-2.1.md)
- [Platform operations](platform-ops.md)
- [Ops proof](../examples/ops-proof/README.md) and [real 26.3 proof](../examples/proof/README.md)
