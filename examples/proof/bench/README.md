# Platform benchmark harness

This directory contains a standalone benchmark for the parts of Weaver that can
run without Minecraft: registry reads, built-in hook dispatch through
`GameHooks` and `EventBus`, and mod-jar/class scanning through `ModJar` and
`AutoSubscriberScanner`. It complements the event-bus-only benchmark in
`api/src/test/java/net/weaver/api/event/EventBusBenchmark.java`.

## Prerequisites

- JDK 26 (found through `JAVA_HOME`, or `C:\Program Files\Java\jdk-26.0.1`).
- Built API and core jars:
  `.\gradlew.bat :api:jar :core:jar :core:agentJar`.
- `core/build/agent/lib` with the runtime dependencies (ASM, Gson, and so on).

## Commands

```powershell
.\gradlew.bat :api:jar :core:jar :core:agentJar
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\bench\run.ps1
```

`run.ps1` builds the harness when the source or the API/core jars are newer than
the compiled classes. To force a build, run `build.ps1` directly:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\bench\build.ps1
```

`build.ps1` prints `BENCH BUILD ok=true`. `run.ps1` prints `PLATFORM_BENCH`
lines and exits non-zero when a budget is exceeded; the final line is
`PLATFORM_BENCH BUDGET ok=true` or `ok=false`.

## What is measured

All measurements warm up before the measured loop. Registry and hook
measurements use 200,000 warm-up operations and 1,000,000 measured operations.
Scanning uses 3 warm-up scans and 20 measured scans.

### Registry reads

The harness installs a fake `RegistryBackend` through the real
`RegistryManager`. The fake backend stores 256 entries in an in-memory map.
The measured object is the real `RegistryImpl`, so the numbers include the
public API layer, key parsing, backend dispatch, and the internal read caches.
Measured operations:

- `registry_key_parse` - `RegistryKey.parse("weaver_bench:entry_007")`;
- `registry_get` - `get(RegistryKey)`;
- `registry_get_string` - `get(String)` (includes parsing and validation);
- `registry_contains` - `containsKey(RegistryKey)`;
- `registry_keySet` - `keySet().size()` (cached set after the first call);
- `registry_values` - `values().size()` (cached list after the first call);
- `registry_size` - `size()`;
- `registry_get_missing` - `get` for an absent key.

This is not a measurement of Minecraft's registry implementation. It measures
the Weaver API/cache layer with a cheap backend.

### Hook dispatch

A listener is registered on `EventBus.GAME` for `TickEvent`,
`LevelTickEvent`, and `PlayerTickEvent`. The harness then measures:

- `hook_dispatch` - `EventBus.GAME.post` of a preallocated
  `ServerTickEvent.Pre`; this isolates event-bus dispatch for a built-in hook
  event type;
- `hook_gamelifecycle` - the real `GameHooks.serverTickEnter()`, which allocates
  the event, posts it on `EventBus.GAME`, calls
  `GameHookTransformer.logSummaryOnce()`, calls
  `LifecycleHooks.onServerTick()`, and iterates the loaded mod list;
- `hook_level_dispatch` - `GameHooks.serverLevelTickEnter(new Object())`;
- `hook_player_dispatch` - `GameHooks.playerTickEnter(new Object())`.

No game is running and no mods are loaded, so the mod-iteration loop is empty.
The numbers cover the Weaver-side hook and event-bus work only, not game method
body cost or real listener work.

### Class and jar scanning

`build.ps1` generates a fixture jar containing:

- `weaver.mod.json` with id `bench-fixture`;
- 100 classes annotated with
  `@EventBusSubscriber(bus = MOD, modId = "bench-fixture")`, each with a static
  `@SubscribeEvent` method for `ServerTickEvent`;
- 100 plain classes.

`modjar_read` measures `ModJar.read` over 20 reads (jar open, metadata parse,
class-entry scan). `scan_cold` is the first `AutoSubscriberScanner.scan` call;
it includes first-time class loading and JIT work. `scan` is the average of 20
later scans after 3 warm-up scans. Every scan creates a fresh
`EventBus.local("bench-scan-...")` bus so registrations do not accumulate on
`EventBus.MOD` or `EventBus.GAME`. The scanner runs the real ASM class-entry
probe and the real reflective registration path.

## Measured run

Machine: Intel Core i5-10300H (4 cores / 8 threads, 2.50 GHz), 25.6 GB RAM,
Windows 11 Home 10.0.26200, JDK 26.0.1 (HotSpot 64-Bit Server VM, max heap
6104 MB). The machine was not isolated and other builds ran concurrently, so
single-run differences of tens of percent are noise.

| Measurement | Result |
|---|---:|
| `registry_key_parse` | 226.40 ns/op |
| `registry_get` | 38.15 ns/op |
| `registry_get_string` | 235.21 ns/op |
| `registry_contains` | 42.16 ns/op |
| `registry_keySet` | 6.91 ns/op |
| `registry_values` | 5.03 ns/op |
| `registry_size` | 4.85 ns/op |
| `registry_get_missing` | 44.62 ns/op |
| `hook_dispatch` | 64.55 ns/post |
| `hook_gamelifecycle` | 75.57 ns/post |
| `hook_level_dispatch` | 109.90 ns/post |
| `hook_player_dispatch` | 97.23 ns/post |
| `modjar_read` | 1.28 ms/read |
| `scan_cold` | 151.71 ms/scan |
| `scan` (warm) | 18.02 ms/scan |
| fixture subscribers | 100 |

## Budgets

Budgets are defined in `PlatformBenchmark.java` and checked after every
measurement. A failed budget prints `ok=false` and makes `run.ps1` exit
non-zero.

| Budget | Ceiling | Measured | Headroom |
|---|---:|---:|---:|
| `registry_*` | 2,000 ns/op | at most 235.21 ns/op | about 8.5x |
| `hook_*` | 5,000 ns/post | at most 109.90 ns/post | about 45x |
| `modjar_read` | 2 s/read | 1.28 ms/read | about 1560x |
| `scan` (warm) | 2 s/scan | 18.02 ms/scan | about 111x |
| `scan_cold` | 10 s/scan | 151.71 ms/scan | about 66x |

Why these values:

- Registry and hook ceilings are one to two orders of magnitude above the
  measured values, so a shared CI machine or a noisy laptop does not fail the
  script. They still catch a lost cache, a per-post allocation that forces GC,
  or an accidental reflective path.
- The warm scan ceiling is 2 seconds against an 18 ms measurement. The fixture
  is small; a real mod jar with thousands of entries will take longer, so this
  is a regression guard for this fixture, not a promise for arbitrary jars.
- The cold scan ceiling is 10 seconds because the first scan pays class loading
  and first-time JIT costs. Observed cold scans ranged from about 152 ms to
  1.30 s across runs on the same machine. The warm scan keeps the 2-second
  guard.
- The mod-jar read ceiling is 2 seconds against about 1.3 ms, which is loose
  enough to absorb file-cache and antivirus variance.

All numbers are machine-specific and depend on CPU, JIT state, OS file cache,
and concurrent load. The budgets are regression guards, not performance
promises.

## Limitations

- No Minecraft classes are on the class path, so this does not measure game
  registry implementations, game hook targets, or real listener bodies.
- `GameHooks` runs with an empty loaded-mod list; the `forEachMod` callback loop
  is measured as empty.
- The scanner fixture is generated by `build.ps1` and contains only 200 class
  entries. Cold-scan numbers include URL class loading; warm-scan numbers
  benefit from already-loaded classes and cached lambda call sites.
- The registry backend is a deliberately simple in-memory map. It is useful for
  isolating Weaver's own overhead, not for predicting Minecraft registry
  performance.
- These are wall-clock measurements on a shared machine, not JMH results. They
  are intended for regression detection and order-of-magnitude evidence.
