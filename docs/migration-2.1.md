# Migrating to Weaver 2.1

Weaver 2.1 keeps the base event and registry contracts but changes where mod
lifecycle events live, adds dispatch-time filters and typed game events, and
turns the entity-join and chat hooks into real actions. This page lists what
changed and how to upgrade a 2.0-era mod.

The current versions are `WeaverApi.VERSION = "2.1.0"`,
`WeaverGameApi.VERSION = "26.3"`, and project version `2.3.2`.

## What changed

### Per-mod local bus

`EventBus` now has a three-level hierarchy:

```
EventBus.GAME
└── EventBus.MOD
    └── EventBus.local(modId)      // mod:<modId>
```

- The loader creates one local bus per mod with
  `EventBus.local(modId)`.
- `ModContext.modEvents()` returns that local bus for loader-created contexts.
- `ModContext.globalModEvents()` returns `EventBus.MOD`.
- A post on the local bus reaches local listeners, then `MOD`, then `GAME`.
- A post on `MOD` or `GAME` never reaches local listeners.
- `Event.getBus()` is the posting bus. A lifecycle event posted on a local bus
  keeps that local bus reference while being delivered to parent listeners.

`EventBusSubscriber.Bus` gained `GLOBAL_MOD`:

| Value | Registration target |
|---|---|
| `GAME` | `EventBus.GAME` |
| `MOD` | the mod's local bus |
| `GLOBAL_MOD` | `EventBus.MOD` |

### Dispatch-time filters

`EventBus.addListener` accepts an `EventFilter`:

- `EventFilter.all()`
- `EventFilter.logicalSide(LogicalSide)`
- `EventFilter.modId(String)`
- `EventFilter.and(...)`, `EventFilter.or(...)`

Filters run after the cancellation check and before the listener. Filtered-out
listeners are not invoked and not counted in `EventBusStats.listenerInvocations`.
Annotated subscribers with `logicalSide = CLIENT` or `SERVER` now get a
dispatch-time filter, not only a load-time availability check.

`ModScopedEvent` is available for mod-owned events. Events posted on a
`mod:<modId>` bus also match `EventFilter.modId(modId)`.

### Typed events and the api-game module

A new `api-game` artifact provides `net.weaver.api.game.*`:

- `GameWeaverMod`
- 23 typed events in `net.weaver.api.game.event`
- `GameRegistries`
- `TypedGameEventFactory`

The base `api` artifact remains game-free. Typed events subclass the base
events with covariant accessors; the compiler's bridge methods preserve the
base `Object` descriptors. `LoaderAgent` installs
`TypedGameEventFactory` reflectively when the artifact is visible and logs
`TYPED EVENTS enabled`; otherwise it logs `TYPED EVENTS unavailable` and keeps
the base factory.

See [typed game API](api-game.md).

### Cancelable action hooks

Two injected hooks are now real actions, not observation only:

| Hook | Event | Cancel behavior |
|---|---|---|
| `ServerLevel.addFreshEntity(Entity)Z` | `EntityJoinLevelEvent` | Factory-created cancelable; cancellation returns `false` and the entity is not added. |
| `PlayerList.broadcastChatMessage(...)V` | `ChatEvent` | Factory-created cancelable; cancellation makes the injected call return `false`, so the message is not broadcast. |

The base two-argument `EntityJoinLevelEvent` and three-argument `ChatEvent`
constructors remain observation-only; use the explicit `cancelable` constructor
for custom events. Chat direct callbacks (`onChat`) still run even when the
event is canceled, so mods can observe the attempt.

### Five new observation hooks

Five server-side observation points were added:

| Target | Event |
|---|---|
| `LivingEntity.hurtServer(ServerLevel, DamageSource, float)Z` | `EntityHurtEvent` |
| `ServerPlayer.die(DamageSource)V` | `PlayerDeathEvent` |
| `ServerPlayer.openMenu(MenuProvider)OptionalInt` | `ContainerOpenEvent` |
| `ServerPlayer.findRespawnPositionAndUseSpawnBlock(...)TeleportTransition` | `PlayerRespawnEvent` |
| `LootTable.getRandomItems(LootParams, long)ObjectArrayList` | `LootTableEvent` |

They are observation-only. `openMenu`, the respawn lookup, and the loot-table
call are optional specs (`required(false)`), so their absence is reported as
skipped rather than a required miss.

### Reflection-free dispatch

The old annotation-invocation path used `Method.invoke` per post. 2.1 resolves
`@SubscribeEvent` methods once at registration into `LambdaMetafactory` /
`MethodHandle` invokers and dispatches to the cached `EventListener` directly.
The hot path does not use reflection, and the current path allocates no bytes
per post in the benchmark.

### Hook diagnostics, side gating, and strict mode

- Every spec has an `id`, `required`, `side`, `priority`, entry/exit/throw
  calls, and an `Action`. `Action` is `VOID`, `CANCEL_IF_FALSE`, or
  `RETURN_VALUE`.
- Each spec logs `[weaver] GAME HOOK APPLIED|SKIPPED|MISSING|INVALID id=...`.
- Specs are gated by the hosted side; client-only specs on a server are logged
  as skipped with `reason=side=CLIENT`.
- `-Dweaver.hooks.strict=true` makes the loader throw when a required spec is
  missing or invalid.
- The first client or server tick logs
  `[weaver] GAME HOOK SUMMARY applied=... skipped=... missing=... invalid=...
  required_misses=...`.

Measurements for this tree:

| Side | Applied | Skipped | Missing | Invalid | Required misses |
|---|---:|---:|---:|---:|---|
| Dedicated server | 22 | 8 | 0 | 0 | none |
| Client (snapshot at first client tick) | 24 | 6 | 0 | 0 | none |

The client snapshot is taken at the first client tick; targets that load later
in startup are applied after the summary. The proof log shows those later
`APPLIED` lines.

## Additive changes

These do not require changes to a working 2.0 mod:

- New event subclasses, new observation events, and new typed events.
- `EventFilter` and the filtering `addListener` overloads.
- `EventBus.local(String)`, `EventBus.global()`, `parent()`, `name()`, and
  `listenerCount()`.
- `Event.getBus()`/`setBus`, `logicalSide()`/`setLogicalSide`.
- `EventBusStats` accessors.
- `GameWeaverMod` typed overloads with default implementations.
- `GameRegistries` and the typed `registry(RegistryKey<T>)` helpers.
- Hook `id`, `required`, `side`, `priority`, and `Action` configuration.

## Behavior changes to review

1. **`ModContext.modEvents()` is now the local bus.** Code that expected it to
   return `EventBus.MOD` must switch to `context.globalModEvents()` when it
   needs the aggregate bus. The `ModContext` interface default still returns
   `EventBus.MOD` for custom contexts that do not carry a local bus, but every
   loader-created context returns a local bus.
2. **`@EventBusSubscriber(bus = Bus.MOD)` now registers on the local bus.** If
   the subscriber must observe every mod's lifecycle events, use
   `Bus.GLOBAL_MOD` or register directly on `EventBus.MOD`.
3. **Lifecycle events are posted on the local bus.** `EventBus.MOD` and
   `EventBus.GAME` listeners still receive them through the parent chain, but
   `event.getBus()` is the local bus and posting no longer starts at `MOD`.
4. **Entity join and chat are cancelable actions.** A listener can now stop
   `addFreshEntity` and `broadcastChatMessage`. Check `event.isCancelable()`
   before calling `setCanceled` if the event may be constructed by older code.
5. **Annotated logical-side subscribers are filtered at dispatch time.** A
   `logicalSide = SERVER` class on a client with an integrated server no longer
   receives client events.
6. **`GameEventFactory` gained five methods.** Custom implementations of this
   SPI must add the five observation methods. Mods normally use
   `DefaultGameEventFactory`/`TypedGameEventFactory` instead of implementing it.
7. **Hook summary counts changed.** The summary is a snapshot at the first tick
   and now includes the new specs. Do not compare it with a 2.0 count.

## What to override

A mod that wants typed callbacks implements `GameWeaverMod` and overrides the
typed methods:

```java
public final class ExampleMod implements GameWeaverMod {
    @Override
    public void onPlayerJoin(ModContext context, ServerPlayer player) {
        // typed payload; no cast needed
    }

    @Override
    public void onLevelLoad(ModContext context, Level level) {
        // typed payload; no cast needed
    }
}
```

The inherited `Object` methods check `instanceof ServerPlayer` /
`instanceof Level` and forward. Overriding the `Object` method still works and
keeps the old behavior; do not override both for the same payload unless you
intentionally want the override to be called once and the typed default not to
run.

Use the typed `registry` overload with `GameRegistries` when the element type
matters:

```java
RegistryView<Item> items = event.registry(GameRegistries.ITEM);
```

## What not to do

- **Do not cast a typed accessor if the payload may not match.** A base event
  created by `DefaultGameEventFactory` is not a typed event. Use `instanceof` or
  a base listener that checks the class before casting.
- **Do not call `GameHooks`.** It is internal loader code called from injected
  bytecode. Public events and `WeaverMod`/`GameWeaverMod` callbacks are the
  supported entry points.
- **Do not rely on `Method.invoke` for listener dispatch.** The bus no longer
  uses it on the hot path, and listener methods are converted to handles at
  registration.
- **Do not package Minecraft or Mojang classes.** `api-game` is compile-only;
  the game jars are provided by the game. Duplicating game classes breaks the
  shared class space and the agent layout checks.
- **Do not change a base event constructor or accessor descriptor.** Typed
  events depend on the compiler bridges and base-only mods depend on the
  existing descriptors.
- **Do not add a second API jar to a mod.** Two `api` copies create two
  `EventBus.GAME` identities. Use the agent lib.
- **Do not register the entrypoint through `@EventBusSubscriber`.** The
  entrypoint is already registered on `GAME`; the scanner skips it.

## Proof and limitations in 2.1

The real 26.3 proof now records 20 checks on both the dedicated server and the
client integrated server. The added checks are `Chat cancellation`,
`Container open hook`, `Respawn position lookup hook`, and `Loot table hook`.

- **Chat scope.** The client proof calls
  `PlayerList.broadcastChatMessage` directly with a real `PlayerList`,
  `ServerPlayer`, and `ChatType.Bound`, and cancels only a marker
  `PlayerChatMessage`. It is a hook-level integration test, not a client chat
  packet: it does not exercise signing, validation, or the chat screen. The
  dedicated server has no player and records
  `Chat cancellation audit-only reason=no-player`.
- **Optional hooks.** On the client all four optional checks execute against a
  real player. On the dedicated server, container-open and respawn are
  audit-only because they need a player; the loot-table hook still runs
  directly against the server level and records `Loot table hook`. A
  `PlayerRespawnEvent` is a respawn-position lookup, not the respawn action,
  and may fire more than once.
- **Shared class space.** `-SharedClassSpace` or
  `WEAVER_SHARED_CLASS_SPACE=true` adds
  `-Dweaver.classspace.shared=true`. The game main is still launched through
  the system loader, so game classes are system-hosted while the hook and
  lifecycle transformers expect the shared loader. The proof cannot reach
  `PROOF INIT` or `PROOF RESULT`; `run-server-shared.ps1` reports
  `SHARED MODE RESULT ok=false blocker=game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init`
  and proves only factory linkability with `SHARED FACTORY PROBE ok=true`.
  The script exits 1 in this state; `-AllowDocumentedBlocker` acknowledges the
  blocker without making it a passing proof. Shared class space is
  experimental and unsupported as a gate. Do not claim shared mode passes until
  the proof itself passes in that mode. A future major may add a
  `PlayerRespawnPositionEvent` alias for the lookup event; 2.1 keeps the
  `PlayerRespawnEvent` name for compatibility.

## Upgrade checklist

1. Build with `.\gradlew.bat clean build --no-build-cache --rerun-tasks --console=plain`.
2. Replace `context.modEvents()` with `context.globalModEvents()` wherever you
   need the aggregate `EventBus.MOD`.
3. Change cross-mod `@EventBusSubscriber` classes from `Bus.MOD` to
   `Bus.GLOBAL_MOD` if they must see other mods' lifecycle events.
4. Add an `EventFilter.logicalSide(...)` or check `event.logicalSide()` where a
   shared bus carries both sides.
5. Move game-typed event handling to `GameWeaverMod` typed methods or the
   `net.weaver.api.game.event` classes.
6. Replace direct `Method`/reflection listener invocation with
   `EventListener`/`EventBus` registration.
7. Review entity-join and chat listeners: cancellation now has a real effect.
8. Review the hook summary and any optional spec that reports skipped.
9. Run the proof (`examples/proof`) on a client and dedicated server and check
   for `PROOF RESULT ok=true` on each side.

## Related

- [Dispatch model](dispatch.md)
- [Event bus reference](events.md)
- [Typed game API](api-game.md)
- [Hook points](hooks.md)
- [Documentation index](README.md)
