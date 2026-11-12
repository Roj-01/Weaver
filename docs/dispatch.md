# Dispatch model

This page describes how `net.weaver.api.event.EventBus` dispatches events in
Weaver 2.1: the bus hierarchy, filters, typed events, the snapshot model, the
no-reflection hot path, the measured performance, and the API stability rules.

See the [event bus reference](events.md) for the listener and event catalogs,
the [typed game API](api-game.md) for `api-game`, and the
[hook points](hooks.md) page for where the built-in events are posted.

## Bus hierarchy

Weaver has three levels of bus:

```
EventBus.GAME                 root; game events and cross-mod traffic
└── EventBus.MOD              global aggregate mod bus
    └── EventBus.local(modId) one fresh child per loaded mod, named mod:<modId>
```

| Bus | Created by | Purpose |
|---|---|---|
| `EventBus.GAME` | static field | Root bus. The loader posts built-in game events and registry events here. |
| `EventBus.MOD` | static field, child of `GAME` | Global aggregate mod bus. Cross-mod observers that must see every mod's lifecycle traffic register here. |
| `EventBus.local(modId)` | `ModContext.modEvents()` for a loader-created context | One private child per loaded mod. Named `mod:<modId>`; events posted here reach this mod's listeners first, then `MOD`, then `GAME`. |

`EventBus.global()` returns `EventBus.GAME`. `ModContext.events()` also returns
`GAME`. A loader-created `ModContext.modEvents()` returns the mod's local bus,
and `ModContext.globalModEvents()` returns `EventBus.MOD`.

`EventBus.local(modId)` creates a new bus on every call. Calling it yourself
does not return the loader's bus for the mod and does not receive loader-posted
lifecycle events. Use `context.modEvents()` instead.

The bus name is metadata used in diagnostics. A bus can also be created
directly with `new EventBus(name)` or `new EventBus(name, parent)`; the root and
the two global buses are created that way.

## Per-mod bus lifecycle

The loader calls `EventBus.local(modId)` once per mod in
`LifecycleHooks.install`, before the entrypoint is instantiated. The same local
bus is stored in all three contexts for that mod (base, logical client, logical
server) and in the `LoadedMod` record exposed to hooks.

- The local bus lives for the process. Mods load once and there is no reload,
  generation, or swap; there is no bus destroy or per-mod unload.
- Lifecycle events are posted on the local bus with the phase's logical side.
  Listeners on the local bus see them first, `EventBus.MOD` listeners second,
  and `EventBus.GAME` listeners last.
- `@EventBusSubscriber(bus = Bus.MOD)` registers on the local bus of the mod
  that owns the jar. `Bus.GLOBAL_MOD` registers on `EventBus.MOD`, and
  `Bus.GAME` registers on `EventBus.GAME`.
- The mod entrypoint is registered on `EventBus.GAME` once by the loader. The
  subscriber scanner skips the entrypoint class and logs
  `SUBSCRIBER ENTRYPOINT SKIPPED` when it is annotated.

## Parent and child dispatch

`EventBus.post(Event)` sets the posting bus on the event, then dispatches to the
posting bus and every ancestor, nearest first:

```java
EventBus local = EventBus.local("examplemod");
local.addListener(ExampleEvent.class, event -> log("local"));
EventBus.MOD.addListener(ExampleEvent.class, event -> log("aggregate"));
EventBus.GAME.addListener(ExampleEvent.class, event -> log("game"));

local.post(new ExampleEvent()); // local, then aggregate, then game
```

Within one bus, listeners run in priority order. Across buses, every listener on
the child runs before any listener on its parent, regardless of priority.
`HIGHEST` on `GAME` still runs after `MONITOR` on the child.

Posting on a parent never reaches child listeners. Posting on `EventBus.GAME`
therefore does not reach `EventBus.MOD` or a mod-local bus, and posting on
`EventBus.MOD` does not reach a mod-local bus.

`Event.getBus()` is the bus that was posted on, not the bus whose listener is
currently running. A `MOD` listener observing an event posted on
`mod:examplemod` sees `event.getBus() == EventBus.local("examplemod")` (the
loader-owned instance). This is how `EventFilter.modId(...)` can identify the
posting mod even while dispatching to parents.

`Event.setBus` keeps only the first assignment. The bus sets it; mod code should
not call it.

## Registration and removal

```java
ListenerHandle handle = EventBus.GAME.addListener(PlayerJoinEvent.class,
        event -> context.log("join"));
```

Overloads take an owner, a priority, a `receiveCanceled` flag, and/or an
`EventFilter`. A `null` filter is treated as `EventFilter.all()`.

```java
ListenerHandle handle = EventBus.GAME.addListener(
        PlayerJoinEvent.class,
        this,
        this::onPlayerJoin,
        EventPriority.NORMAL,
        false,
        EventFilter.logicalSide(LogicalSide.SERVER));
```

`ListenerHandle` exposes `bus()`, `eventType()`, `owner()`, `isActive()`, and
`unregister()`. Unregistering twice is harmless. `EventBus.unregister(handle)`
does the same as `handle.unregister()`.

`EventBus.unregister(Object owner)` removes every listener registered with that
exact owner (reference identity, not `equals`) on this bus only. It does not
walk parents or children. The overloads without an explicit owner use the
listener object as the owner.

`listenerCount()` is the number of local registrations on this bus, excluding
ancestors. `hasListeners(eventType)` checks this bus and its ancestors, not its
children.

Listeners registered for a superclass receive subclass events. A listener for
`LevelLoadEvent` receives the typed
`net.weaver.api.game.event.LevelLoadEvent` when the typed game API is installed,
because the snapshot walks the event class's superclass chain.

## Priorities

Listeners run in this order on each bus:

1. `HIGHEST`
2. `HIGH`
3. `NORMAL`
4. `LOW`
5. `LOWEST`
6. `MONITOR`

Within one priority, registration order decides. `MONITOR` is for observation;
do not change game state there. Dispatch is synchronous: `post` returns after
every matching listener on the posting bus and its ancestors has run.

## Cancellation

An event is cancelable only when constructed with `cancelable = true`;
`setCanceled` throws `IllegalStateException` otherwise. The bus skips canceled
events for listeners that did not opt into `receiveCanceled`, including
`MONITOR` listeners. A `receiveCanceled` listener may clear the flag, and later
listeners then run normally. `post` returns `false` for a canceled event.

A filter is evaluated **after** the cancellation check. A listener that rejects
an event through its filter, or that is skipped because the event is canceled,
is not counted as a listener invocation in `EventBusStats`.

The built-in cancelable events are the two injected action pilots:

| Event | Hook | Cancel effect |
|---|---|---|
| `EntityJoinLevelEvent` | `ServerLevel.addFreshEntity` | The event factory creates it cancelable; cancellation makes the injected call return `false`, so the entity is not added. |
| `ChatEvent` | `PlayerList.broadcastChatMessage` | The event factory creates it cancelable; cancellation makes the injected call return `false`, so the message is not broadcast. Direct `onChat` callbacks still run. |

The plain two-argument `EntityJoinLevelEvent` and three-argument `ChatEvent`
constructors remain observation-only for binary compatibility; the factories
and the typed events use the cancelable constructors. All other built-in events
are not cancelable.

## Filters

`EventFilter` is a dispatch-time predicate:

```java
@FunctionalInterface
public interface EventFilter {
    boolean accept(Event event);
}
```

| Factory | Semantics |
|---|---|
| `EventFilter.all()` | Accepts every event. Also used for a `null` filter. |
| `EventFilter.logicalSide(expected)` | Accepts when `event.logicalSide().matches(expected)`. `BOTH` as the expected side accepts everything; a `BOTH` event passes every side filter. |
| `EventFilter.modId(modId)` | Accepts a `ModScopedEvent` with that id, a `ModLifecycleEvent` for that id, or an event whose `getBus()` is named `mod:<modId>`. The bus check only works after posting, because `getBus()` is assigned by `post`. |
| `EventFilter.and(filters...)` | Logical AND, in argument order, short-circuiting on rejection. No arguments returns `all()`. |
| `EventFilter.or(filters...)` | Logical OR, in argument order, short-circuiting on acceptance. No arguments rejects everything. |

`and` and `or` reject a `null` array or a `null` element with
`NullPointerException`.

Filters run inside the dispatch loop, after the cancellation check and before
the listener. They must be fast and side-effect free. A filter that throws is
treated like a listener failure: the bus counts an exception, calls the
exception handler, and continues with the next listener.

A filtered listener is only counted as invoked when its filter accepts and the
listener is called. Rejected and canceled registrations do not increment
`listenerInvocations()`.

`ModScopedEvent` is the explicit form for a mod-owned event:

```java
public final class RubyUseEvent extends Event implements ModScopedEvent {
    @Override public String modId() { return "examplemod"; }
}
```

Events that do not implement it still match a `modId` filter when they are
posted on a bus named `mod:<modId>`.

## Logical-side filtering for annotated subscribers

An `@EventBusSubscriber` whose `logicalSide` is `CLIENT` or `SERVER` is
registered with `EventFilter.logicalSide(...)`. This is a dispatch-time filter,
not only a load-time availability filter:

- `logicalSide = BOTH` registers with `EventFilter.all()`.
- `logicalSide = CLIENT` requires the logical client to be available and filters
  out server-only events.
- `logicalSide = SERVER` requires the logical server to be available and
  filters out client-only events; on a client with an integrated server it is
  therefore a server-side subscriber.

The physical `side` member remains a load-time filter and is independent. This
split lets a client process with an integrated server load logical server
subscribers without also delivering client events to them.

Custom listeners do the same explicitly:

```java
context.events().addListener(LevelLoadEvent.class, listener,
        EventFilter.logicalSide(LogicalSide.CLIENT));
```

## Typed events

The base `api` events carry game payloads as `Object`. The optional `api-game`
artifact adds subclasses in `net.weaver.api.game.event` with covariant
accessors. Because the snapshot walks superclasses, a listener registered for
the base class receives the typed event:

```java
EventBus.GAME.addListener(net.weaver.api.event.events.LevelLoadEvent.class,
        event -> {
            if (event instanceof net.weaver.api.game.event.LevelLoadEvent typed) {
                Level level = typed.level();
            }
        });
```

Registering for the typed class receives only typed instances. See
[typed game API](api-game.md) for the bridge and binary-compatibility rules.

## Exception isolation and stats

One listener throwing `Throwable` never stops the remaining listeners. The bus
increments `exceptions()`, invokes the bus's `EventExceptionHandler` with the
event, listener, and failure, and continues. The default handler writes one
line to `System.err` without a stack trace. An exception from the handler itself
is ignored.

`stats()` returns an immutable `EventBusStats` snapshot for one bus:

| Field | Meaning |
|---|---|
| `posts()` | Posts made with `post` on this bus. Parent/child dispatches do not increment another bus's post count. |
| `listenerInvocations()` | Listeners actually invoked by `post` on this bus. Filtered and canceled skips are excluded. |
| `canceledPosts()` | `post` calls whose event was canceled when dispatch finished. |
| `exceptions()` | Filter or listener failures during `post` on this bus. |
| `totalNanos()` | Accumulated wall time of this bus's `post` calls. |
| `maxNanos()` | Longest single `post` on this bus. |

`postFast` does not update any of these counters; they describe `post` traffic
only.

`resetStats()` clears the counters. Statistics are per bus. The posting bus
records the post, total time, and max time; each bus in the chain records its
own listener invocations and exceptions. A local post therefore increments the
local bus's post counter and the `MOD`/`GAME` invocation counters for listeners
that actually ran there. Only `post` records these values; `postFast` records
nothing on any bus in the chain.

## Thread safety and snapshots

- Registration, removal, and dispatch are thread-safe. A read/write lock guards
  the registration map and the snapshot cache.
- A dispatch snapshot is an immutable array of registrations for one event
  class, built from that class and its superclasses and sorted by priority and
  registration sequence. It is cached per bus and invalidated on any
  registration or removal.
- A post reads the current snapshot once per bus. Adding or removing a listener
  during a post does not affect events already being dispatched.
- Listeners run on the thread that calls `post`. Game hooks post on the thread
  that reaches the injected game method; listeners invoked by those hooks may
  touch game state only as the game's own threading rules allow. Do not touch
  game objects from a listener invoked on a background thread.
- The event's `logicalSide` and `canceled` flags are volatile. `getBus()` is
  assigned once by `post` before dispatch.

## No-reflection hot path

The hot path does not use `Method.invoke`:

- `@SubscribeEvent` methods are validated and converted once, at registration,
  to an `EventListener` through `MethodHandles.privateLookupIn` +
  `MethodHandle.unreflect` + `LambdaMetafactory.metafactory`.
- If that path is unavailable, registration falls back to a `MethodHandle`
  adapted to the listener signature and invoked with `invokeExact`.
- Dispatch calls `registration.listener.invoke(event)` directly on the cached
  lambda or handle wrapper.
- Listener discovery itself is reflective (class and method scanning), but that
  happens at registration time, not per post.

A registration failure during `register(Object)` or `register(Class)` throws
`IllegalArgumentException` and registers nothing for that target.

## Performance model

`post` is the default dispatch entry point. `postFast` runs the same dispatch
with all `EventBusStats` bookkeeping skipped:

- It assigns the event bus, walks the cached ancestor chain, and uses the
  immutable per-event snapshots exactly like `post`.
- Cancellation, `receiveCanceled`, filters, and listener exception handling are
  unchanged.
- It does not update `posts()`, `listenerInvocations()`, `canceledPosts()`,
  `exceptions()`, `totalNanos()`, or `maxNanos()`, on the posting bus or on any
  ancestor. `stats()` and `resetStats()` therefore describe only `post` traffic.
- It returns `!event.isCanceled()` and allocates nothing per post after
  warm-up.

Use `postFast` for high-frequency internal events where per-post statistics are
not wanted. Normal game and mod events should keep using `post` so the stats
stay meaningful.

The dedicated benchmark lives in `api/src/test/java/net/weaver/api/event/EventBusBenchmark.java`
and runs with:

```powershell
.\gradlew.bat :api:benchmark
```

Methodology:

- Every configuration is warmed with 200,000 posts, then measured over
  1,000,000 posts.
- Listener counts are 1, 10, 100, and 1000.
- `BENCH` measures `post` with direct `EventListener` instances.
- `BENCH_FAST` measures `postFast` with the same listener sets.
- `BENCH_LEGACY` is a deliberately reconstructed pre-2.1 dispatch path: every
  post allocates a per-post ancestor list, looks up a per-event snapshot, and
  calls `Method.invoke` for every listener, with the same posts / invocations /
  total-nanos / max-nanos bookkeeping as the current bus.
- `BENCH_REFLECTION` measures one listener whose lambda performs a
  `Method.invoke` call, as a baseline for reflection cost.
- `BENCH_FILTER_ACCEPT` and `BENCH_FILTER_REJECT` register every listener with
  `EventFilter.logicalSide(LogicalSide.SERVER)`. The accept path posts an event
  whose logical side is `SERVER`; the reject path posts one whose logical side
  is `CLIENT`. `BENCH_FILTER_FAST_ACCEPT` and `BENCH_FILTER_FAST_REJECT` are
  the same two paths with `postFast`.
- Allocation is read with `com.sun.management.ThreadMXBean#getThreadAllocatedBytes`
  on the measuring thread; `bytes_per_post` is total bytes divided by the
  measured post count.

Wave 3 measured run (nanoseconds per post, 1,000,000 posts each) on an Intel
Core i5-10300H (4 cores / 8 threads, 2.50 GHz), 25.6 GB RAM, Windows 11 Home
10.0.26200, JDK 26.0.1 (HotSpot 64-Bit Server VM). The laptop was not isolated
and other builds ran on the machine; treat single-run differences of tens of
percent as noise.

| Listeners | `post` | `postFast` | `postFast` speed-up | Legacy | `post` vs legacy | `post` B/post | `postFast` B/post | Legacy B/post |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 112.89 | 36.78 | 3.07x | 139.20 | 1.23x | 0 | 0 | 72 |
| 10 | 204.69 | 56.80 | 3.60x | 319.92 | 1.56x | 0 | 0 | 72 |
| 100 | 1254.64 | 326.40 | 3.84x | 2663.56 | 2.12x | 0 | 0 | 72 |
| 1000 | 12197.72 | 4239.57 | 2.88x | 27296.25 | 2.24x | 0 | 0 | 72 |

The single-reflection baseline measured 158.99 ns/post in the same run.

Filtered dispatch in the same run (nanoseconds per post):

| Listeners | `post` accept | `post` reject | `postFast` accept | `postFast` reject |
|---:|---:|---:|---:|---:|
| 1 | 109.85 | 109.64 | 34.53 | 51.21 |
| 10 | 229.58 | 144.63 | 84.96 | 60.83 |
| 100 | 1449.48 | 455.55 | 665.56 | 460.94 |
| 1000 | 15373.62 | 7798.65 | 6475.96 | 4688.43 |

Every filtered configuration also reported 0.00 bytes per post after warm-up.
The reject path is cheaper than the accept path because the listener bodies do
not run; with one listener the two paths are indistinguishable, which is
expected because the filter call and the empty listener body have similar cost.

The `postFast` win is largest at low listener counts, where the removed stats
atomics and timestamp reads dominate dispatch time, and remains measurable at
high counts, where the skipped per-invocation counters accumulate. Both paths
still evaluate filters and invoke listeners, so the remaining cost scales with
the number of registrations.

The current path allocates nothing per post after warm-up, because the ancestor
chain and listener snapshots are cached and immutable. `postFast` also
allocates nothing. The legacy path allocates 72 bytes per post for its ancestor
list. Use these numbers as an order of magnitude and a regression baseline, not
as a portable guarantee: JIT state, CPU, listener bodies, and concurrent load
change the absolute values. Repeated runs on this machine varied from about
9.4 to 12.2 microseconds per post at 1000 listeners, so the budgets below are
deliberately loose.

### Performance regression budgets

`api/src/test/java/net/weaver/api/event/EventBusPerformanceBudgetTest.java` runs
as part of `:api:test`. It warms each configuration with 50,000 posts and
measures 200,000 posts with these ceilings:

| Configuration | Ceiling | Measured in the benchmark run | Headroom |
|---|---:|---:|---:|
| `post`, 1 listener | 10,000 ns/post | 112.89 ns/post | about 88x |
| `postFast`, 1 listener | 10,000 ns/post | 36.78 ns/post | about 270x |
| `post`, 100 listeners | 100,000 ns/post | 1254.64 ns/post | about 80x |
| filter accept, 100 listeners | 100,000 ns/post | 1449.48 ns/post | about 69x |
| filter reject, 100 listeners | 50,000 ns/post | 455.55 ns/post | about 110x |
| `postFast` allocation, 1 listener | 64 B/post | 0 B/post | detects any per-post allocation |

The allocation assertion is skipped when
`com.sun.management.ThreadMXBean#getThreadAllocatedBytes` is unavailable. The
time ceilings are regression guards for shared CI machines, not performance
promises.

### Platform benchmark

The event bus benchmark covers dispatch only. The standalone harness in
`examples/proof/bench` covers registry reads, built-in hook dispatch, and
class/jar scanning without a live game:

```powershell
.\gradlew.bat :api:jar :core:jar :core:agentJar
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\bench\run.ps1
```

`run.ps1` calls `build.ps1` when the benchmark source or the API/core jars are
newer than the compiled harness. `build.ps1` locates JDK 26 (`JAVA_HOME` or
`C:\Program Files\Java\jdk-26.0.1`), compiles the harness against the real
`api`/`core` jars and the agent dependency jars, generates a fixture jar with
100 annotated subscriber classes and 100 plain classes, and prints
`BENCH BUILD ok=true`. `run.ps1` prints `PLATFORM_BENCH` lines and exits
non-zero when any budget is exceeded.

What is measured:

- **Registry reads.** The harness installs a fake `RegistryBackend` through the
  real `RegistryManager` and measures the real `RegistryImpl` read surface
  against a 256-entry registry: `RegistryKey.parse`, `get(RegistryKey)`,
  `get(String)`, `containsKey(RegistryKey)`, `keySet().size()`,
  `values().size()`, `size()`, and a missing-key `get`. Each is warmed with
  200,000 operations and measured over 1,000,000 operations. The backend is an
  in-memory map, so these numbers describe the API/cache layer, not Minecraft's
  registry implementation.
- **Hook dispatch.** A listener is registered on `EventBus.GAME` for
  `TickEvent`, `LevelTickEvent`, and `PlayerTickEvent`. `hook_dispatch` posts a
  preallocated `ServerTickEvent.Pre` through `EventBus.GAME.post`.
  `hook_gamelifecycle` calls the real `GameHooks.serverTickEnter()`, which
  allocates the event, posts it on `EventBus.GAME`, calls
  `GameHookTransformer.logSummaryOnce()`, `LifecycleHooks.onServerTick()`, and
  iterates the loaded mod list. `hook_level_dispatch` and
  `hook_player_dispatch` call `GameHooks.serverLevelTickEnter(new Object())`
  and `GameHooks.playerTickEnter(new Object())`. No game is running and no mods
  are loaded, so the mod-iteration loop is empty; the measurement covers the
  Weaver-side hook and event-bus work only.
- **Class/jar scanning.** `build.ps1` generates a fixture jar containing
  `weaver.mod.json`, 100 classes annotated with
  `@EventBusSubscriber(bus = MOD, modId = "bench-fixture")` and a static
  `@SubscribeEvent` method, and 100 plain classes. `modjar_read` measures
  `ModJar.read` (jar open, metadata parse, class-entry scan) over 20 reads.
  `scan_cold` is the first `AutoSubscriberScanner.scan` call, including
  first-time class loading and JIT work. `scan` is the average of 20 later
  scans after 3 warm-up scans; each scan uses a fresh
  `EventBus.local("bench-scan-...")` bus so registrations do not accumulate.
  The scanner runs the real ASM class-entry probe and the real reflective
  registration path.

Wave 3 measured run on the machine described above:

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

Budgets enforced by `run.ps1`:

| Budget | Ceiling | Measured | Headroom |
|---|---:|---:|---:|
| `registry_*` | 2,000 ns/op | at most 235.21 ns/op | about 8.5x |
| `hook_*` | 5,000 ns/post | at most 109.90 ns/post | about 45x |
| `modjar_read` | 2 s/read | 1.28 ms/read | about 1560x |
| `scan` (warm) | 2 s/scan | 18.02 ms/scan | about 111x |
| `scan_cold` | 10 s/scan | 151.71 ms/scan | about 66x |

The cold-scan budget is higher because the first scan pays class loading and
first-time JIT costs; observed cold scans ranged from about 152 ms to 1.30 s
across runs on the same machine. The warm-scan budget is the 2-second guard.
The scanner fixture is deliberately small (200 classes, 100 subscribers); a
real mod jar with thousands of entries will take longer, and the budget is a
regression guard for this fixture, not a promise for arbitrary jars.

All platform numbers are machine-specific and vary with CPU, JIT state, OS file
cache, and concurrent load. The budgets are regression guards, not performance
promises. They fail the script rather than silently reporting a regression.

## API stability rules

The event API follows the policy in `WeaverApi`:

- Additive changes (new types, new overloads, new event subclasses, new default
  methods) are preferred and ship in a minor release.
- Removing a public type or changing an existing method, constructor, or field
  descriptor requires a major version bump.
- A public member is deprecated for at least one minor release before removal.
- The base event accessors intentionally stay `Object`-typed. Typed subclasses
  narrow them covariantly; the compiler emits bridge methods so code compiled
  against the base descriptors still links.
- `Event.setBus` only keeps the first assignment; do not call it or depend on
  replacing a bus.
- `EventBus.setExceptionHandler` returns the bus for chaining; the handler is
  process-local configuration, not an event.

The current versions are `WeaverApi.VERSION = "2.1.0"` and
`WeaverGameApi.VERSION = "26.3"`. The project version is `2.3.2`.

## Related

- [Event bus reference](events.md)
- [Typed game API](api-game.md)
- [Hook points](hooks.md)
- [Migration to 2.1](migration-2.1.md)
- [Documentation index](README.md)
