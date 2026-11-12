# Examples and feature proofs

Every Weaver 2.2 feature has a runnable example and a real proof, or a
documented limitation. `examples/proof` is the acceptance baseline; the other
directories prove one feature each and add focused coverage.

Run the standalone proofs from the repository root. The game-backed proofs need
`work/game-26.3/classpath.txt` and `work/server-26.3`; the scripts read
`WEAVER_GAME_DIR` and `WEAVER_SERVER_DIR` when those defaults do not fit. Each
directory's README has the full per-example detail; this page is the index.

## Feature to proof matrix

All commands are exact repo-root PowerShell invocations. Multiple commands in
one cell run in order unless they are separated by `or`.

| Feature | Example | Exact command(s) | What the proof covers | Requirements | Current result marker |
|---|---|---|---|---|---|
| network | `examples/network-proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\network-proof\run.ps1` | Real two-process TCP handshake, PLAY phase, PING/PONG, handler thread, payload, and protocol-mismatch rejection | JDK 26; no game | `NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok` |
| config | `examples/config-proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\config-proof\run.ps1` | v1 to v2 migration, validation, save/load round trip, corrupt-file backup and defaults, client/server file split, manager identity, explicit reload with `ConfigReloadedEvent`, and `SERVER_TO_CLIENT` sync through a fake connection | JDK 26; `core/build/agent/lib` | `CONFIG PROOF RESULT ok=true checks=58` |
| capability | `examples/capability-proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\capability-proof\run.ps1` | Real `ItemStack` component data, copy, NBT round trip, runtime attachments, real `ChestBlockEntity` persistence round trip, isolated registry, corruption fallback, oversize rejection, concurrency, sync declaration | JDK 26; game classpath; API/core jars | `CAPABILITY PROOF RESULT ok=true checks=12` |
| client | `examples/client-proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\run-server.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\run-client.ps1` | Key mapping activation, HUD layer, tooltip provider, screen open, client setup, and server-side absence of client classes | JDK 26; game classpath; server dir; agent lib; API-game jar; client fixture world | `CLIENT PROOF RESULT ok=true side=client` (log) and `CLIENT PROOF CLIENT RESULT ok=true` (console) |
| command | `examples/client-proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\run-server.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\run-client.ps1` | Real Brigadier registration, permission allow/deny, and alias execution on a dedicated server and a client integrated server | Same as client | `CLIENT PROOF SERVER RESULT ok=true side=server` (log); `CLIENT PROOF COMMAND allowed ok=true`, `denied ok=true`, `alias ok=true` |
| datagen | `examples/datagen-proof`, `tools/datagen` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\datagen-proof\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\datagen-proof\run-server.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File tools\datagen\test.ps1` | Manifest generation, verify/fingerprint, generated pack on a real dedicated server, custom registry and tag reads, vanilla recipe/advancement load | JDK 26; game classpath; server dir; API/API-game/agent jars | `DATAGEN PROOF ok=true`; `DATAGEN SELFTEST ok=true checks=10` |
| hooks | `examples/hooks-proof`, `examples/proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\hooks-proof\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\hooks-proof\run-server.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\hooks-proof\run-client.ps1` | Entity hurt cancellation, living entity death, container close, anvil, enchant, plus the full acceptance hook set in `examples/proof` | JDK 26; game classpath; server dir; agent lib; API-game jar; client fixture world | `HOOKS SERVER RESULT ok=true` and `HOOKS CLIENT RESULT ok=true` (console); `PROOF RESULT ok=true side=server checks=6` and `... side=client checks=6` (log) |
| toolchain | `examples/toolchain-fixture` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\toolchain-fixture\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\toolchain-fixture\proof.ps1` | Maven range resolution, transitive dependency, optional dependency exclusion, nested jar-in-jar layout, loader discovery | JDK 26; Gradle wrapper; server dir | `TOOLCHAINFIXTURE PROOF ok=true`; `TOOLCHAINFIXTURE RESULT ok=true side=server` |
| acceptance baseline | `examples/proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\run-server.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\run-client.ps1` | Loader, game class space, Mixin/AT, registries, event bus, filters, typed events, cancelable and observation hooks | JDK 26; game classpath; server dir; agent lib; API-game jar; client fixture world | `PROOF RESULT ok=true side=server checks=20`; `PROOF RESULT ok=true side=client checks=20` |
| cross-feature compatibility | `examples/compat-suite` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\run.ps1` | Local bus plus listener plus filter, deferred and direct item registration, frozen-registry reads, typed API-game events, server tick/level load/server started hook callbacks | JDK 26; game classpath; server dir; agent lib; API-game jar | `COMPAT SUITE RESULT ok=true side=server checks=20`; `COMPAT SUITE PROOF ok=true` |
| benchmark | `examples/proof/bench` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\bench\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\bench\run.ps1` | Registry and event-bus performance budgets plus synthetic scanner fixtures | JDK 26; API/core jars; agent lib | `BENCH BUILD ok=true ...`; `PLATFORM_BENCH BUDGET ok=true` |
| patched game | `examples/patched-proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\patched-proof\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\patched-proof\run-all.ps1` | Patches the official client and server jars locally, proves the negative control (compiling against vanilla fails), verifies the loader sees patch level 1, and exercises the injected attachment contract on a real client and dedicated server | JDK 26; game classpath; server dir; agent lib; API/API-game jars; `tools\patcher\verify.ps1` | `PATCHED PROOF NEGATIVE ok=true`; `PATCHED PROOF ok=true side=server patchLevel=1 value=ok`; `PATCHED PROOF ok=true side=client patchLevel=1 value=ok`; `PATCHED PROOF RUN ok=true server=ok client=ok` |
| source-patched game | `examples/source-patch-proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\source-patch-proof\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\source-patch-proof\run-all.ps1` | Decompiles `ItemStack` with Vineflower, applies a unified-diff source patch, recompiles with `javac --release 25`, rebuilds the client and server jars, proves the negative compile control, and exercises the added member on a real client and dedicated server | JDK 26; game classpath; server dir; agent lib; API/API-game jars; Vineflower download | `SOURCE PATCH PROOF NEGATIVE ok=true`; `SOURCE PATCH PROOF ok=true side=server value=source:minecraft:stonex1`; `SOURCE PATCH PROOF ok=true side=client value=source:minecraft:stonex1`; `SOURCE PATCH PROOF RUN ok=true server=ok client=ok` |
| platform ops | `examples/ops-proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\ops-proof\run.ps1` | Class-space mode selection, mod policies, fatal policy, boot mod list, CLI text/JSON, version matrix, update contract, mod-change watch scan, multi-pack data-pack precedence, and data-registry reload refusal for frozen registries | JDK 26; agent artifacts | `OPS PROOF RESULT ok=true checks=13`; `OPS PROOF RUN ok=true` |
| shared class space (experimental) | `examples/proof` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\run-server-shared.ps1` or `powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\run-server-shared.ps1 -AllowDocumentedBlocker` | Experimental, environment-dependent behavior; observed proof pass on 2026-11-04; not a support claim and not a release gate. Either the documented system-loader blocker or a full shared-mode server proof. | JDK 26; game classpath; server dir; agent lib; API-game jar; built proof jar | `SHARED MODE RESULT ok=true side=server` and `SHARED FACTORY PROBE ok=true`, or `SHARED MODE RESULT ok=false blocker=...` / `SHARED MODE ACKNOWLEDGED ok=false ...` |
| starter | `examples/starter` | `cd examples\starter; ..\..\gradlew.bat weaverBuild` | Minimal Gradle-plugin mod packaging; not a proof | JDK 26; Gradle wrapper | `BUILD SUCCESSFUL`; `weaverBuild` stages the mod jar |
| first mod | `examples/first-mod` | `.\gradlew.bat -p examples/first-mod weaverBuild weaverGameTest` | The getting-started mod: an item, a creative tab, a join event, and a game test, built and tested with the Gradle plugin | JDK 26 as `JAVA_HOME`; game and server dirs; API/API-game/agent jars | `WEAVERBUILD ok=true`; `All 2 required tests passed`; `build/weaver/gametest/report.xml` |
| game tests | `examples/gametest` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\gametest\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\gametest\run.ps1` | Tests registered with `GameTests` run on vanilla's game test server with the agent; JUnit report; an optional failure does not fail the run, a required one does | JDK 26; server dir; agent lib; API-game jar | `GAMETEST RESULT ok=true exit=0` |
| multiplayer | `examples/multiplayer` | `powershell -NoProfile -ExecutionPolicy Bypass -File examples\multiplayer\build.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File examples\multiplayer\run.ps1` | A dedicated server and separate clients: a matching client receives registries, data maps, a capability, and a channel over the network; extra-item, other-version, and missing-mod clients are refused with the reason | JDK 26; game classpath; server dir; agent lib; API-game jar | `MP RESULT ok=true match=true extra-item=true version=true missing=true` |
| minimap | `examples/minimap` | `cd examples\minimap; ..\..\gradlew.bat clean test weaverBuild weaverStage; .\play.ps1` | Client-only minimap v2: cached circular HUD, fullscreen map, cave slices, waypoint v2, ten-tab settings, themes, world beams, public API; 181 unit tests | JDK 26; game classpath; API/API-game jars; fixture world | `BUILD SUCCESSFUL`; `MINIMAP HUD rendered`, `TEXTURE ok`, `SCREEN rendered`, `SETTINGS rendered`, `WAYPOINT ok`, `WORLD RENDER ok`, `PROOF done` |

## Acceptance baseline: `examples/proof`

Build and run the real dedicated server and the real client:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\run-server.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\run-client.ps1
```

`run-all.ps1` runs build, server, then client and prints
`PROOF RESULT server=ok client=ok`. The acceptance result lines are:

```text
PROOF RESULT ok=true side=server checks=20
PROOF RESULT ok=true side=client checks=20
```

The client script copies `examples/proof/fixture-world` to
`work/game-26.3/saves/WeaverProofFixture`; set `WEAVER_CLIENT_WORLD` to use a
different world. Regenerate the fixture with
`examples/proof/prepare-client-world.ps1`. The dedicated server uses port 25566.
`run-server-shared.ps1` records either the documented system-loader blocker or a
full shared-mode proof pass. Shared class space is experimental,
environment-dependent, and not supported as a release gate; see
`examples/proof/README.md` for the two outcomes and the 2026-11-04 evidence.

## Standalone proofs

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\config-proof\run.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\network-proof\run.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\capability-proof\run.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\ops-proof\run.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\datagen\test.ps1
```

`config-proof`, `network-proof`, `capability-proof`, and `ops-proof` run their
own build step (network-proof reuses an existing jar). The explicit compile
steps for the first three are:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\config-proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\network-proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\capability-proof\build.ps1
```

Exact success markers:

```text
CONFIG PROOF RESULT ok=true checks=58
NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok
CAPABILITY PROOF RESULT ok=true checks=12
OPS PROOF RESULT ok=true checks=13
DATAGEN SELFTEST ok=true checks=10
```

`examples/network-proof` also writes the server, client, and mismatch markers
under `examples/network-proof/out/logs/`. `NETWORK SERVER CHECK ok=true
serverbound payload=hello thread=network-proof-server-game` is a log line, not a
console line printed by `run.ps1`.

## Game-backed proofs

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\hooks-proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\hooks-proof\run-server.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\hooks-proof\run-client.ps1

powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\run-server.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\run-client.ps1

powershell -NoProfile -ExecutionPolicy Bypass -File examples\datagen-proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\datagen-proof\run-server.ps1

powershell -NoProfile -ExecutionPolicy Bypass -File examples\toolchain-fixture\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\toolchain-fixture\proof.ps1
```

Result markers (console lines unless marked log; the script prints the log
result on success):

```text
HOOKS SERVER RESULT ok=true                 console
PROOF RESULT ok=true side=server checks=6   log
HOOKS CLIENT RESULT ok=true                 console
PROOF RESULT ok=true side=client checks=6   log
CLIENT PROOF SERVER RESULT ok=true          console
CLIENT PROOF SERVER RESULT ok=true side=server   log
CLIENT PROOF CLIENT RESULT ok=true          console
CLIENT PROOF RESULT ok=true side=client     log
DATAGEN PROOF BUILD ok=true                 console
DATAGEN PROOF ok=true                       log
TOOLCHAINFIXTURE PROOF ok=true              console
TOOLCHAINFIXTURE RESULT ok=true side=server log
```

The `run-all.ps1` wrappers print `HOOKS PROOF RESULT server=ok client=ok` and
`CLIENT PROOF RESULT server=ok client=ok`. The 2026-11-04 acceptance run
reports `GAME HOOK SUMMARY applied=30 skipped=8 missing=0 invalid=0
required_misses=none` on both sides. Counts can change when hook specs change;
`missing=0`, `invalid=0`, and `required_misses=none` are the invariants.

## Compatibility suite: `examples/compat-suite`

This is a real Weaver mod jar loaded on a real Minecraft 26.3 dedicated server.
It combines the base event bus, registry, typed API-game events, and hooks in one
fixture so a single run detects cross-feature breakage.

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

The mod registers `weavercompatsuite:compat_item` through
`ModContext#deferredRegister` and `weavercompatsuite:compat_direct_item` through
`ModContext#registry`, then reads both after the item registry freezes. It
checks the local bus name and parentage, an accepted and a rejected
`EventFilter.modId` registration, the `RegisterEvent` and `RegistryFreezeEvent`
hooks, typed `LevelLoadEvent` and `ServerStartedEvent` accessors, a base
`LevelLoadEvent` listener receiving the typed instance, and the server tick,
level load, and server started callbacks. The default port is 25568; override it
with `WEAVER_COMPAT_SUITE_PORT`.

## Starter and supporting tools

`examples/starter` is a minimal Gradle-plugin mod, not a proof:

```powershell
cd examples\starter
..\..\gradlew.bat weaverBuild
..\..\gradlew.bat runClient
..\..\gradlew.bat runServer
```

`tools/datagen` is the standalone generator used by `examples/datagen-proof`.
Run it directly with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\datagen\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\datagen\run.ps1 --manifest datagen.json --out out --verify --fingerprint
```

`examples/proof/bench` is a standalone performance benchmark, not a feature
proof. It builds synthetic scanner fixtures and checks the registry and event
budgets:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\bench\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\bench\run.ps1
```

It prints `BENCH BUILD ok=true ...` and ends with
`PLATFORM_BENCH BUDGET ok=true` when every budget passes.

## Command proof decision

There is no separate `examples/command-proof`. The command API is inherently
bound to the game's Brigadier dispatcher and server thread, so a standalone
proof would either duplicate `examples/client-proof` or replace the real
backend with a fake backend that proves less. `examples/client-proof` already
runs the real command backend on a real dedicated server and on a real client
integrated server: it verifies registration, one allowed and one denied
permission source, and execution through an alias. The remaining command gaps
are documented rather than hidden:

- The base loader does not install a client command-tree hook, so
  `CommandTreeSyncEvent` and the client tree merge are not proven.
- Permissions are in-memory Weaver decisions; no proxy or vanilla permission
  forwarding is proven.
- Live Brigadier node removal is not supported; `unregister()` only affects
  future dispatcher builds.

## Cross-cutting limitations

- Shared class space is experimental and environment-dependent. The 2026-11-04
  compatibility run observed a full proof pass on this machine:
  `[weaver] CLASS SPACE mode=shared`, `[weaver] AGENT READY side=server mods=1
  configs=1 class-space=shared`, `PROOF RESULT ok=true side=server checks=20`,
  `SHARED MODE RESULT ok=true side=server`, and `SHARED FACTORY PROBE ok=true`.
  Earlier runs observed the documented system-loader blocker. This is not a
  support claim and not a release gate; `tools/verify-ci.ps1` labels the
  observed mode and accepts either outcome.
- The network proof covers the Weaver TCP transport only. The Minecraft 26.3
  game bridge is not installed, so client/server payload exchange inside
  Minecraft is not proven.
- Client proofs need a prepared game directory with `classpath.txt`, `assets`,
  and `natives`, and a client fixture world. They are sensitive to host
  graphics drivers and offline authentication.
- All dedicated-server proofs use the same `work/server-26.3` world. Run them
  sequentially; concurrent servers would contend for the world lock.
- Ports are fixed by script: `examples/proof`, `examples/hooks-proof`, and
  `examples/proof/run-server-shared.ps1` use 25566; `examples/client-proof`,
  `examples/datagen-proof`, and `examples/toolchain-fixture` use 25567; and
  `examples/compat-suite` uses 25568.
- `examples/toolchain-fixture` proves Gradle-level jar-in-jar extraction and
  loader discovery, not LoaderAgent runtime extraction of nested jars.
- `examples/hooks-proof` has its own README. Block/item interaction, teleport,
  and the respawn target have no dedicated proof check; container close, anvil,
  and enchant need a client integrated server for a real player path.
- `examples/config-proof` runs standalone: it posts config events on a test bus
  and does not exercise a live game lifecycle.
- `examples/capability-proof` has no mod jar, loader, server, or network. The
  `CapabilitySync.ITEM_STACK` check is a declaration, not a sync channel.
- `examples/datagen-proof` does not execute a generated recipe, trigger an
  advancement, render client assets, or enumerate the server's enabled-pack
  list.
- Scripts resolve JDK 26 through `JAVA_HOME` or
  `C:\Program Files\Java\jdk-26.0.1`; a `java` on `PATH` alone is not enough for
  the build scripts.
