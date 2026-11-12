# Compatibility

This page is the support and binary-compatibility reference for Weaver 2.2.
It covers the supported environment, the 2.x binary contract, the CI matrix in
`tools/verify-ci.ps1`, the compatibility-suite fixture, and the compatibility
limits that are accepted rather than hidden.

Use [Platform operations](platform-ops.md) for runtime modes and launcher
details, [Toolchain](toolchain.md) for build and publishing commands, and
[Examples](../examples/README.md) for the feature-to-proof index.

## Status

- Minecraft 26.3 only. The game version and hook target are both `26.3`.
- Java 26 only. The Gradle toolchain, `options.release`, and every proof script
  target JDK 26.
- The base `api` artifact stays game-free and is the required API.
- The typed `api-game` artifact is optional at build time, but a mod that
  compiles against typed events needs it at runtime.
- The 2.2 workstream is additive against the 2.1 binary surface. `WeaverApi`
  keeps `VERSION = "2.1.0"` and reports the new capabilities through
  `FEATURE_LEVEL = "2.2"`.

## Supported environment matrix

| Dimension | Supported | Verified in this pass | Notes |
|---|---|---|---|
| Minecraft | 26.3 | Yes, real dedicated server and client | No other game version is accepted by `WeaverApi.isCompatibleGame`. |
| Weaver API | `2.1.0` binary surface, feature level `2.2` | Yes | `WeaverGameApi.VERSION = 26.3`. Project version is `2.3.2`. |
| Java | 26 | Yes, JDK 26.0.1 | Older and newer major JDKs are not supported for builds or game launch. |
| Gradle | Wrapper in the repository | Yes, Gradle 9.7.1 | Use `gradlew.bat` or `./gradlew`; do not rely on a system Gradle. |
| Windows | PowerShell 5.1 | Yes, Windows 11 | All proof and CI scripts were exercised on this platform. |
| Linux | Gradle build and JUnit tests are platform-neutral | Not exercised end to end | The proof and CI scripts are Windows PowerShell 5.1 scripts that invoke `powershell.exe`; a Linux run would need a port and has not been recorded. |
| `api` | Required, game-free | Yes | No `net.minecraft.*` or `com.mojang.*` references. |
| `api-game` | Optional, compile-only game dependency | Yes | Built when a classpath is present; skipped by explicit build properties when absent. |
| `core` agent | Runtime loader | Yes | Not a compile dependency of a mod; distributed through the agent lib. |

The build accepts three explicit game-API modes:

| Mode | Property | Result |
|---|---|---|
| Normal | none | `api-game` builds when a game classpath exists. |
| No game classpath | `-Pweaver.forceNoGameApi=true` | `api-game` is skipped; the log says `reason=no-game-classpath`. |
| Explicit skip | `-Pweaver.skipGameApi=true` | `api-game` is skipped; the log says `reason=skip-game-api`. |
| Required | `-Pweaver.requireGameApi=true` | The build fails with remediation text when no classpath is available. |

## Binary compatibility policy for 2.x

The contract is additive-first. A mod compiled against the 2.1 `api` surface
must link against 2.2 without recompiling. `WeaverApi.stabilityPolicy()`
states the same rule at runtime.

### Guaranteed for 2.x

- Public `api` types, constructors, methods, and fields that existed in 2.1
  keep their descriptors.
- New types, overloads, constants, default methods, event subclasses, and
  packages may be added in a minor release.
- Removal or a descriptor change requires a new major version.
- A deprecated public member remains for at least one minor release before
  removal.
- `EventBus` keeps its constants, constructors, registration overloads,
  `post`/`postFast`, `fire`, `unregister`, stats, and exception-handler
  surface.
- `Event`, `EventPriority`, `EventFilter`, `EventBusStats`, `ListenerHandle`,
  and `EventBusSubscriber` keep their public descriptors and enum order.
- `RegistryKey`, `RegistryView`, `RegistryEntry`, `DeferredRegister`,
  `RegisterEvent`, `RegistryFreezeEvent`, and `WeaverRegistries` keep their
  public descriptors and registry ids.
- The config, capability, network, command, data, and client packages keep
  their public type names and key method descriptors.
- `HookEventFactory` keeps its 28 Object/enum-parameter methods and
  `GameEventFactory` keeps its 23 Object/enum-parameter methods.
- `api-game` typed events keep extending their same-named base event and keep
  their Object-parameter constructors.
- Covariant typed accessors keep their compiler-generated Object bridge
  methods, so code compiled against the base descriptors still links.
- `EventBusSubscriber.Bus` ordinals stay `GAME = 0`, `MOD = 1`,
  `GLOBAL_MOD = 2`. `EventPriority` and `EventResult` order is unchanged.
- `ResourceReloadEvent.source()` intentionally stays `Object`; it is not
  narrowed.

### De-finalized base events

The following base events are non-final so `api-game` can subclass them. Their
constructors and accessors were not changed:

`PlayerJoinEvent`, `PlayerLeaveEvent`, `PlayerLoginEvent`, `PlayerLogoutEvent`,
`LevelLoadEvent`, `LevelUnloadEvent`, `EntityJoinLevelEvent`, `ChatEvent`,
`ScreenOpenEvent`, `ScreenCloseEvent`, `CommandRegisterEvent`,
`ResourceReloadEvent`, `ServerStartingEvent`, `ServerStartedEvent`,
`ServerStoppingEvent`, `ServerStoppedEvent`, `ServerSaveEvent`,
`LevelSaveEvent`, `EntityHurtEvent`, `PlayerDeathEvent`, `ContainerOpenEvent`,
`PlayerRespawnEvent`, `LootTableEvent`, `BlockInteractEvent`, `ItemUseEvent`,
`LivingEntityDeathEvent`, `ContainerCloseEvent`, `AnvilUpdateEvent`,
`EnchantEvent`, `PlayerTeleportEvent`, `PlayerRespawnTargetEvent`,
`EntityInteractEvent`, `EntityAttackEvent`, `BlockBreakEvent`, `BlockPlaceEvent`,
`ItemUseFinishEvent`, `ItemReleaseEvent`, `ProjectileLaunchEvent`,
`ClientPlayerTickEvent`, `EntityTickEvent`, `ChunkLoadEvent`, `ChunkUnloadEvent`,
`EntityRemovalEvent`, `ContainerClosedEvent`, `BlockEntityLoadEvent`,
`BlockEntitySaveEvent`, `PlayerRespawnActionEvent`.

`ModLifecycleEvent` remains final and mod-scoped.

### Typed event catalog

`net.weaver.api.game.event` contains 49 unique typed event classes: 23 game
events created by `TypedGameEventFactory` and 27 hook events created by
`TypedHookEventFactory`, with `EntityHurtEvent` shared by both factories.
Every typed class extends the same-named base event.

The two factories keep their exact catalogs:

| Factory | Methods | Purpose |
|---|---:|---|
| `GameEventFactory` / `TypedGameEventFactory` | 23 | Game lifecycle, player, level, server, screen, chat, command, save, respawn lookup, loot. |
| `HookEventFactory` / `TypedHookEventFactory` | 26 | Block interact, item use, living death, container close and completion, anvil, enchant, teleport, respawn target and action, entity hurt/interact/attack/removal/tick, item use finish/release, block break/place, projectile launch, client player tick, chunk load/unload, block entity load/save. |

### Not guaranteed

- Internal and implementation types under `net.weaver.core`, package-private
  constructors, and non-public members are not a compatibility surface.
- `api-game` is rebuilt for each Minecraft version. Binary compatibility is
  guaranteed across Weaver 2.x releases for one game version, not across
  Minecraft versions.
- Minecraft class shapes outside the accessors that `api-game` exposes are not
  covered; a new game version can change them.
- Wire compatibility across different Weaver protocol versions is not implied
  by the API version. `NetworkProtocol.NAME`, `VERSION`, and `CHANNEL_VERSION`
  are public constants, but cross-version protocol interoperability is not a
  2.x promise.
- Performance numbers and benchmark ratios are machine-specific regression
  guards, not compatibility promises. See [Dispatch](dispatch.md).
- Shared class space is not a supported compatibility mode. See the
  limitations below.
- Client visuals, window behavior, and host graphics availability are not part
  of the API contract.
- There is no Java 25 or earlier support, no cross-major API support, and no
  NeoForge compatibility.

### Tests that enforce the contract

| Test | Module | Tests | What it guards |
|---|---|---:|---|
| `PublicApiBinaryCompatibilityTest` | `api` | 16 | Public descriptors, constants, enum order, event bus, registry, config, capability, network, command, data, client, and both factories. |
| `BaseEventCompatibilityTest` | `api` | 7 | De-finalized base events, original Object constructors, Object accessors, bus ordinals, and the current API version. |
| `TypedEventCatalogCompatibilityTest` | `api-game` | 7 | Typed package enumeration, base parentage, Object constructors, bridges, 23/26 factory catalogs, `GameWeaverMod`, `GameRegistries`, and `WeaverGameApi`. |
| `TypedBinaryCompatibilityTest` | `api-game` | 6 | Typed accessors, Object bridges, and typed constructor erasure. |

The tests intentionally use reflection descriptors rather than source-level
calls, so a changed parameter, return type, constant, or removed member fails
before a compiled mod reaches the game.

## CI matrix

`tools/verify-ci.ps1` is the reproducibility gate. It prints PASS, FAIL, or
SKIP for every step, captures one log per command under the system temporary
directory, prints a final summary, and exits `0` only when no step failed.
Every PowerShell script row is invoked as
`powershell -NoProfile -ExecutionPolicy Bypass -File <script>`; the table lists
the script and its arguments.

| Step | Exact command | What it proves | PASS / SKIP semantics |
|---|---|---|---|
| A | `.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon` | The normal with-game build succeeds and produces an `api-game` jar. | PASS requires exit 0, `BUILD SUCCESSFUL`, no skip line, and at least one `api-game-*.jar`. SKIP when no game classpath exists or `-SkipBuildMatrix` is set. |
| B | `.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon -Pweaver.forceNoGameApi=true` | The no-game build succeeds, skips `api-game`, and leaves no `api-game` jar in the module or agent lib. | PASS requires exit 0, `BUILD SUCCESSFUL`, `[weaver] api-game SKIPPED reason=no-game-classpath`, and zero `api-game` jars. SKIP with `-SkipBuildMatrix`. |
| C | `.\gradlew.bat build -Pweaver.forceNoGameApi=true -Pweaver.requireGameApi=true` | Required-but-absent `api-game` fails clearly. | PASS requires non-zero exit, no `BUILD SUCCESSFUL`, and all three remediation options (`WEAVER_GAME_DIR`, `server-26.3.jar`, `-Pweaver.skipGameApi=true`). SKIP with `-SkipBuildMatrix`. |
| D | `.\gradlew.bat build -Pweaver.skipGameApi=true` | Explicit skip removes the `api-game` jar and manifest entry while the base API jar remains. | PASS requires exit 0, `BUILD SUCCESSFUL`, `[weaver] api-game SKIPPED reason=skip-game-api`, no agent-lib `api-game` jar, no manifest `api-game` entry, and exactly one base `api` entry. SKIP with `-SkipBuildMatrix`. |
| E | `.\gradlew.bat :core:agentJar` | The with-game agent artifacts are restored after the skip/no-game builds. | PASS requires exit 0, `BUILD SUCCESSFUL`, exactly one agent-lib `api-game` jar, and exactly one manifest entry. SKIP when no game classpath exists or `-SkipBuildMatrix` is set. |
| F | `.\gradlew.bat :api:test :core:test :api-game:test --rerun-tasks --no-daemon` | The full unit-test suite passes in with-game mode. | PASS requires exit 0, `BUILD SUCCESSFUL`, and zero failures/errors from the JUnit XML. SKIP when no game classpath exists. |
| G | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\config-proof\run.ps1` and `... examples\network-proof\run.ps1` | Config migration/validation/save path and the two-process TCP transport with phase, payload, handler-thread, and mismatch rejection. | PASS requires the exact markers `CONFIG PROOF RESULT ok=true checks=58` and `NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok`. No flag currently skips this step in the default run. |
| H | `examples\proof\run-server.ps1`, `examples\compat-suite\run.ps1`, `examples\client-proof\run-client.ps1` after their builds | Real 26.3 dedicated-server acceptance, the cross-feature compatibility fixture, and the real client fixture. | PASS requires `PROOF RESULT ok=true side=server checks=20`, `COMPAT SUITE RESULT ok=true side=server checks=20` with `COMPAT SUITE PROOF ok=true`, and `CLIENT PROOF RESULT ok=true side=client` with the fixture line. The client step retries once because the first launch can time out before the marker. SKIP when prerequisites are missing, with `-SkipGameProofs`, or with `-SkipClientProof` for the client only. |
| I | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\run-server-shared.ps1 -AllowDocumentedBlocker` | Shared-class-space behavior is observed without asserting support. The step accepts either the precise documented blocker or a full shared-mode proof pass and labels the observed mode. | PASS as `shared_mode=documented-blocker` when the output contains `SHARED MODE ACKNOWLEDGED ok=false blocker=game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init` and `SHARED FACTORY PROBE ok=true`. PASS as `shared_mode=proof-pass` when the server log contains `[weaver] CLASS SPACE mode=shared`, `[weaver] AGENT READY ... class-space=shared`, and `PROOF RESULT ok=true side=server checks=20`, and the output contains `SHARED MODE RESULT ok=true side=server` and `SHARED FACTORY PROBE ok=true`. Both labels say experimental, environment-dependent behavior observed; neither changes the unsupported support status. Exit non-zero, timeout, missing factory probe, `ok=false` without the exact blocker, a wrong blocker token, or output that matches neither outcome = FAIL. SKIP with `-SkipSharedProof` or `-SkipGameProofs`. |
| J | `.\gradlew.bat :api:benchmark` and `examples\proof\bench\run.ps1` | Event-bus dispatch benchmark and the platform registry/hook/scanner budgets. | PASS requires exit 0 and at least 4 `BENCH` lines for the Gradle benchmark, and `PLATFORM_BENCH BUDGET ok=true` for the platform harness. SKIP with `-SkipBenchmarks`. |
| K | `git diff --check` and a scan of tracked source/docs for unfinished-work markers | No whitespace errors and no unfinished-work markers in the scanned tree. | PASS requires no whitespace errors (LF/CRLF warnings ignored) and zero marker hits. Hits are reported as `file:line`. The policy page `docs/release-checklist.md` is excluded so it can name the markers. |
| L | `powershell -NoProfile -ExecutionPolicy Bypass -File tools\patcher\verify.ps1` | The binary and source patch pipelines: deterministic output across two runs, Mojang signature files and manifest digests stripped, injected and source-added members checked with `javap`, every class loaded through `URLClassLoader` with `-Xverify:all`, and no stale artifact on failure. | PASS requires exit 0 and the markers `PATCHER VERIFY ok=true`, `PATCHER CLASSLOAD`, `signatures=stripped`, and `PATCHER VERIFY source=ok`. SKIP when the official client/server jars or `tools/patcher` are missing. |

Flags:

```text
-SkipGameProofs    skip steps H and I
-SkipClientProof   skip only the client-proof part of step H
-SkipSharedProof   skip step I
-SkipBenchmarks    skip step J
-SkipBuildMatrix   skip steps A through E
-Quick             implies -SkipGameProofs, -SkipSharedProof, and -SkipBenchmarks
```

Every skip is printed in the step list and in the final summary with the flag
that caused it. A skipped step is never counted as a pass.

Recorded final run on 2026-11-04:

```text
WEAVER VERIFY-CI ok=true steps=16 pass=16 fail=0 skip=0 shared_mode=proof-pass elapsed=337.3s
```

The recorded run below predates step L (patcher); the current gate has 17
steps. Steps A through K passed in that run. No step was skipped. The client
proof passed on its
first attempt in that final run; an earlier run needed one retry, and one later
run hit an environment-level JVM crash (see the client limitation below). The
final summary line always records the observed shared-mode outcome.

### Shared-mode behavior check

Shared class space is experimental and unsupported as a release gate. Step I
observes behavior and accepts two distinct, legitimate outcomes; neither
outcome changes the support status.

| Outcome | Required evidence | Meaning |
|---|---|---|
| `shared_mode=documented-blocker` | `SHARED MODE ACKNOWLEDGED ok=false blocker=game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init` and `SHARED FACTORY PROBE ok=true` | The known limitation was reproduced precisely. Acknowledged limitation, not support. |
| `shared_mode=proof-pass` | `[weaver] CLASS SPACE mode=shared`, `[weaver] AGENT READY ... class-space=shared`, `PROOF RESULT ok=true side=server checks=20`, `SHARED MODE RESULT ok=true side=server`, and `SHARED FACTORY PROBE ok=true` | A full shared-mode proof pass was observed in this environment. Experimental, environment-dependent behavior, not support. |

Anything else fails: non-zero exit, timeout, a missing factory probe, `ok=false`
without the exact documented blocker, a different blocker token, or output
that matches neither outcome. Ambiguous output that matches both outcomes is
also a failure. The step label always records the observed mode, and the final
summary carries `shared_mode=<documented-blocker|proof-pass|fail|skipped>`.

On 2026-11-04, two consecutive reconciliation full runs (and earlier full runs
the same day) produced a complete shared-mode proof pass rather than the
documented blocker:

```text
[weaver] CLASS SPACE mode=shared game-entries=40 mods=1 loader=weaver-shared api-game=36
[weaver] AGENT READY side=server mods=1 configs=1 class-space=shared
[weaver] MOD weaverproof PROOF RESULT ok=true side=server checks=20
SHARED FACTORY PROBE ok=true
```

The shared run also applied hooks and completed the mod lifecycle. Its
recorded summary was produced against the earlier 38-spec set
(`GAME HOOK SUMMARY applied=30 skipped=8 missing=0 invalid=0
required_misses=none`); rerun the proof to record the current 59-spec counts
(the catalog size is asserted by `HookContractTest`).
This is an environment-dependent observation, not a support claim. Shared mode
remains unsuitable as a release gate or a production recommendation until the
project explicitly re-scopes it.

## Compatibility suite fixture

`examples/compat-suite` is a real Weaver mod jar loaded on a real Minecraft
26.3 dedicated server. It combines the base event bus, the registry, typed
`api-game` events, and hooks in one run, so a single fixture detects
cross-feature breakage that isolated tests can miss.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\run.ps1
```

Expected markers:

```text
COMPAT SUITE BUILD ok=true mods=<repo>\examples\compat-suite\out\mods
COMPAT SUITE RESULT ok=true side=server checks=20
COMPAT SUITE PROOF ok=true
```

What the fixture proves:

- **Events:** the mod-local bus exists, is distinct from `EventBus.MOD`, is
  named `mod:<modId>`, and has the expected parent chain. An accepted and a
  rejected `EventFilter.modId(...)` registration behave differently.
- **Registry and deferred registration:** `weavercompatsuite:compat_item` is
  registered through `ModContext#deferredRegister`, and
  `weavercompatsuite:compat_direct_item` is registered through
  `ModContext#registry`. Both are read after the item registry freezes.
- **Freeze and register hooks:** the fixture observes `RegisterEvent` and
  `RegistryFreezeEvent` and checks the frozen-registry read path.
- **Typed events:** `LevelLoadEvent` and `ServerStartedEvent` expose the typed
  `api-game` accessors, and a listener registered for the base
  `LevelLoadEvent` receives the typed instance.
- **Hook callbacks:** server tick, level load, and server-started callbacks
  arrive on the expected thread and logical side.

The default port is 25568; `WEAVER_COMPAT_SUITE_PORT` overrides it. The run
uses the shared dedicated-server directory `work/server-26.3`, so it must not
run concurrently with the main proof or any other dedicated-server proof.

## Known compatibility limitations

### Shared class space

Shared class space is experimental and unsupported as a release gate. Its
behavior is environment-dependent. The known limitation is
`game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init`,
but on this environment the proof completed instead: two consecutive
reconciliation full runs on 2026-11-04 (and earlier runs that day) applied
hooks, completed the mod lifecycle, reported all 20 checks, and passed the
shared factory probe. `tools/verify-ci.ps1` reports either
outcome as a PASS with a distinct `shared_mode` label, because the step
observes behavior rather than forcing a result. Neither outcome changes the
support status. Do not use shared mode as a release gate or as a production
recommendation.

### Network game bridge

The network proof covers both the game-free Weaver TCP transport and a
game-free bridge-contract check. When `api-game` and the 26.3 game classes are
present, the loader arms `weaver.network.mixins.json`, registers the
`weaver:bridge` payload inside the packet codec initializers, and performs a
server-first login/configuration/play handshake. The client proof exercises a
real client-to-server and server-to-client payload round trip
(`CLIENT PROOF NETWORK ok=true value=pong:ping`). Without `api-game` or the game
classes, `NetworkFeature.isInstalled()` returns `false` and only the TCP
transport is available. Vanilla peers are detected and never disconnected.

### Client fixture and host requirements

Client proofs need a prepared game directory with `classpath.txt`, `assets`,
and `natives`, plus a fixture world. The client scripts copy
`examples/proof/fixture-world` into the game saves directory. They are
sensitive to host graphics drivers and offline authentication. On 2026-11-04,
one full run needed a retry, and a later run lost both client launches to a
Windows APPCRASH (`java.exe`, exception `0xc0000005`, faulting module
`jvm.dll`). A standalone client run then passed, and the final full run passed
on its first attempt. Treat this as environment/JVM flakiness rather than a
proof assertion failure. A missing fixture is a skip for the client step, not
evidence that the client API works.

### Optional `api-game`

The base `api` works without `api-game`. A mod that imports
`net.weaver.api.game.*`, `TypedGameEventFactory`, typed events, or
`GameWeaverMod` must package `api-game` and its Minecraft 26.3 dependency
expectations correctly. The no-game and skip builds prove that the base build
still works; they do not prove that a typed mod works without `api-game`.
The loader logs `TYPED EVENTS unavailable` and falls back to the base factory
when the typed artifact is missing.

### Trusted code and sandbox

Weaver is not a sandbox. Mods run in the same process as the game and can use
the full JVM, filesystem, and network surface available to that process. The
class space separates class loaders; it does not isolate malicious code.
Only run mods you trust.

### Game version and hook target

This build targets Minecraft 26.3 only. `WeaverApi.isCompatibleGame` accepts
26.3 (and numeric equality such as 26.3.0) and rejects other versions.
The hook target is also 26.3. A new game version requires a new `api-game`
build and hook review; the base `api` version does not move for that reason.

### No NeoForge compatibility

Weaver is its own loader and API. It does not provide NeoForge capability,
event-bus, mixin, or mod-file compatibility. The capability system is
Weaver-owned and deliberately smaller than NeoForge capabilities. A NeoForge
mod cannot be loaded as a Weaver mod, and a Weaver capability is not a
NeoForge capability.

### Sequential server proofs and fixed ports

All dedicated-server proofs use `work/server-26.3`. Run them sequentially.
Concurrent runs contend for the world lock. Ports are fixed by script:
`examples/proof` and `examples/hooks-proof` use 25566,
`examples/client-proof`, `examples/datagen-proof`, and
`examples/toolchain-fixture` use 25567, and `examples/compat-suite` uses 25568.

### Feature-surface gaps

The feature pages document smaller gaps that also matter for compatibility:
no automatic client command-tree sync, in-memory Weaver permissions, no live
Brigadier node removal, Gradle-level rather than loader-level nested-jar
extraction in the toolchain fixture, and no recipe/advancement execution or
client asset rendering in the datagen proof. These are documented limitations,
not silent pass claims.

## How to run

Full default gate from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-ci.ps1
```

Selected scopes:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-ci.ps1 -Quick
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-ci.ps1 -SkipBuildMatrix
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-ci.ps1 -SkipGameProofs
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-ci.ps1 -SkipClientProof
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-ci.ps1 -SkipSharedProof
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-ci.ps1 -SkipBenchmarks
```

The script prints the capture directory at the start. Each step writes one log
there. The final line has the form:

```text
WEAVER VERIFY-CI ok=<true|false> steps=<n> pass=<n> fail=<n> skip=<n> shared_mode=<documented-blocker|proof-pass|not-run> elapsed=<seconds>s
```

Exit code `0` means every non-skipped step passed. Exit code `1` means at
least one step failed. Build outputs under `build/`, `api-game/build/`,
`core/build/`, and the example `out/` directories are modified by the gate;
source files and git state are not. The gate never commits, resets, checks
out, or cleans the working tree.

## Related

- [Typed game API](api-game.md)
- [Dispatch model](dispatch.md)
- [Event bus](events.md)
- [Registry API](registry.md)
- [Hook points](hooks.md)
- [Platform operations](platform-ops.md)
- [Toolchain](toolchain.md)
- [Release checklist](release-checklist.md)
- [Examples and feature proofs](../examples/README.md)
- [Documentation index](README.md)
