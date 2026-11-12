# Event bus

Weaver has a synchronous, in-process event bus in `net.weaver.api.event`. The
bus is plain Java: it does not reference `net.minecraft.*`, so event payloads
that belong to the game are carried as `Object` and cast in mod code.

The bus tree has three levels:

- `EventBus.GAME` — the root; game events and cross-mod traffic.
- `EventBus.MOD` — the global aggregate mod bus. It is a child of `GAME`.
- `EventBus.local(modId)` — a private bus per loaded mod, named
  `mod:<modId>`, child of `MOD`.

`EventBus.global()` returns `EventBus.GAME`. `ModContext.events()` returns
`GAME`, `ModContext.modEvents()` returns the mod's local bus, and
`ModContext.globalModEvents()` returns `EventBus.MOD`.

The [registry API](registry.md) adds `RegisterEvent` and `RegistryFreezeEvent`
to the event package. The [hook points](hooks.md) page lists when the built-in
game events fire. The [dispatch model](dispatch.md) page covers filters,
snapshots, typed events, and the performance model in more depth.

## Buses

A bus is an independent set of listeners with an optional parent:

```java
EventBus bus = new EventBus("examplemod", EventBus.GAME);
```

A root bus can be created with `new EventBus("examplemod")`. The bus name is
metadata only; it is used in diagnostics.

Dispatch rules:

- Posting on a child dispatches child listeners first, then each ancestor,
  nearest first.
- Posting on a parent never reaches child listeners.
- `EventBus.MOD` is a child of `EventBus.GAME`, so posting a mod lifecycle
  event on `MOD` also reaches `GAME` listeners. The reverse is not true.
- A local bus is a child of `MOD`, so posting on `mod:examplemod` reaches that
  mod's listeners, then `MOD`, then `GAME`. Posting on `MOD` or `GAME` does not
  reach the local bus.

Use a local bus for events whose listeners are scoped to one mod; remember
that a post on the local bus still reaches `MOD` and `GAME`. Game events and
cross-mod events belong on `EventBus.GAME`; cross-mod observation of lifecycle
traffic belongs on `EventBus.MOD` (`Bus.GLOBAL_MOD`).

The bus reference on an event is set when it is first posted:

```java
Event event = new Event();
event.getBus(); // null
EventBus.GAME.post(event);
event.getBus(); // EventBus.GAME
```

`Event.getBus()` is the bus that was posted on, not the bus whose listener is
currently running. A `MOD` listener observing an event posted on
`mod:examplemod` sees `event.getBus()` equal to that local bus. This is what
makes `EventFilter.modId("examplemod")` work on parent buses.

`Event.setBus` only keeps the first assignment. Do not call it yourself.

## Registering listeners

Register a lambda for one event type:

```java
ListenerHandle handle = EventBus.GAME.addListener(ServerTickEvent.Post.class,
        event -> context.log("server tick " + event.tick()));
```

The overloads take an optional priority, an optional `receiveCanceled` flag,
and an optional owner:

```java
ListenerHandle handle = EventBus.GAME.addListener(
        PlayerJoinEvent.class,
        this,
        this::onPlayerJoin,
        EventPriority.NORMAL,
        false);
```

`ListenerHandle` exposes `bus()`, `eventType()`, `owner()`, `isActive()`, and
`unregister()`. Unregistering a handle twice is harmless. You can also remove
every listener registered with the same owner:

```java
EventBus.GAME.unregister(this);
```

Owner removal uses reference identity (`owner == registeredOwner`), not
`equals`. The overloads without an explicit owner use the listener object as
the owner and return a handle that you can unregister individually.
`EventBus.unregister(handle)` does the same as `handle.unregister()`.

A listener can carry a dispatch-time `EventFilter`:

```java
context.events().addListener(LevelLoadEvent.class,
        event -> context.log("client level " + event.level()),
        EventFilter.logicalSide(LogicalSide.CLIENT));

context.modEvents().addListener(ModLifecycleEvent.class,
        event -> context.log("phase " + event.phase()),
        EventFilter.modId("examplemod"));
```

| Filter | Accepts |
|---|---|
| `EventFilter.all()` | Every event; also used when a `null` filter is passed. |
| `EventFilter.logicalSide(side)` | Events whose `logicalSide()` matches. `BOTH` as the expected side accepts everything; a `BOTH` event passes every side filter. |
| `EventFilter.modId(id)` | A `ModScopedEvent` or `ModLifecycleEvent` for that id, or an event whose `getBus()` is `mod:<id>` after posting. |
| `EventFilter.and(a, b, ...)` | All filters accept, evaluated in order. No arguments is `all()`. |
| `EventFilter.or(a, b, ...)` | Any filter accepts, evaluated in order. No arguments rejects everything. |

Filters run after the cancellation check and before the listener. A rejected
event is not an invocation and is not counted in `EventBusStats`. Filters must
be fast and side-effect free; a filter that throws is handled like a listener
failure. See [dispatch](dispatch.md#filters) for the full rules.

`ModScopedEvent` marks an event that belongs to one mod:

```java
public final class RubyUseEvent extends Event implements ModScopedEvent {
    @Override
    public String modId() {
        return "examplemod";
    }
}
```

Listeners registered for a superclass receive subclass events:

```java
EventBus.GAME.addListener(Event.class, event -> log(event.getName()));
EventBus.GAME.addListener(TickEvent.class, event -> log("tick " + event.tick()));
```

The first listener sees every event that reaches `GAME`. The second sees client
and server tick events, including both `Pre` and `Post` variants.

`hasListeners(type)` tests whether a type (or a supertype) has listeners on
this bus or any parent, and `listenerCount()` returns the number of local
registrations on the bus.

## Annotation registration

Annotate a method with `@SubscribeEvent` and register its object:

```java
public final class TickLogger
{
    private final ModContext context;

    public TickLogger(ModContext context)
    {
        this.context = context;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onServerTick(ServerTickEvent.Post event)
    {
        if (event.tick() % 100 == 0)
        {
            context.log("server tick " + event.tick());
        }
    }
}
```

```java
List<ListenerHandle> handles = EventBus.GAME.register(new TickLogger(context));
```

`register(Object)` scans the target class and all of its superclasses, stopping
before `Object`. An annotated method must have exactly one parameter assignable
to `Event` and must return `void`; registration fails with
`IllegalArgumentException` otherwise. Methods may be private. Instance methods
are invoked on the target; static methods are invoked on their declaring class.

Method signatures are resolved per concrete class, walking from the concrete
class up to `Object`. Each signature (method name plus JVM descriptor) is owned
by its most-derived declaration. If a subclass declares a method with the same
signature as an annotated superclass method, the subclass declaration wins: an
annotated override registers exactly once, while an override without
`@SubscribeEvent` disables the inherited listener. Sibling subclasses do not
affect each other because resolution runs separately for each registered class.

`register(Class<?>)` registers static methods only. It throws if the class
declares annotated instance methods, and registers nothing in that case.
Register each subscriber once.

A mod entrypoint is registered with `EventBus.GAME` automatically, so
`@SubscribeEvent` methods declared directly on the `WeaverMod` implementation
work without any extra call.

## Automatic discovery

Add `@EventBusSubscriber` to a class to have the loader find it inside the mod
jar:

```java
@EventBusSubscriber(
        bus = EventBusSubscriber.Bus.GAME,
        side = Side.BOTH,
        logicalSide = LogicalSide.BOTH,
        modId = "examplemod")
public final class ExampleSubscribers
{
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        // ...
    }
}
```

| Annotation member | Default | Meaning |
|---|---|---|
| `bus` | `Bus.GAME` | Registration target: `GAME` → `EventBus.GAME`, `MOD` → the mod's local bus, `GLOBAL_MOD` → `EventBus.MOD`. |
| `side` | `Side.BOTH` | Physical load filter: process side that may load the class: `CLIENT`, `SERVER`, or `BOTH`. |
| `logicalSide` | `LogicalSide.BOTH` | Logical availability filter and dispatch-time filter: `CLIENT` requires a logical client, `SERVER` requires a logical server, `BOTH` never filters. |
| `modId` | `""` | When non-empty, the class is only loaded if this matches the jar's mod id. |

`Bus.MOD` means the local bus for the jar's mod, not the global aggregate bus.
Use `Bus.GLOBAL_MOD` to observe every mod's lifecycle events.

Discovery scans every class entry in each mod jar with ASM. The class is not
initialized during the scan. Classes whose side does not match the process, or
whose non-empty `modId` does not match the jar, are skipped. A class with
instance subscriber methods is instantiated through a no-argument constructor;
a class with only static methods is registered as a class. A failure while
loading or registering one subscriber class is logged and does not stop the
scan.

Discovery runs after every mod's `onInitialize`, so an auto-discovered subscriber
does not receive the `INITIALIZE` lifecycle event. Register a listener directly
in `onInitialize` or on the mod entrypoint when that phase is required.

`side` is the physical load filter. On a client process, `CLIENT` and `BOTH`
classes are loaded and `SERVER` classes are skipped even when an integrated
server runs. `logicalSide` is a separate availability filter and is the right
tool for code that must only run where a logical side exists. A non-`BOTH`
`logicalSide` also registers the subscriber with a dispatch-time
`EventFilter.logicalSide(...)`, so it does not receive events from the other
logical side on a process that hosts both.

## Physical and logical sides

Weaver distinguishes two side concepts:

| Concept | Types | Meaning | Used for |
|---|---|---|---|
| Physical side | `Side`, `ModContext.side()`, `isClient()`, `isServer()`, `isPhysicalClient()`, `isPhysicalServer()` | The process the mod was loaded into. | `@EventBusSubscriber.side` load filter. |
| Logical side | `LogicalSide`, `ModContext.logicalSide()`, `isLogicalClient()`, `isLogicalServer()`, `Event.logicalSide()` | The side that produced an event or is being invoked. | `@EventBusSubscriber.logicalSide` availability filter and event metadata. |

`Side` and `LogicalSide` both have `CLIENT`, `SERVER`, and `BOTH` values with
the same `isClient()`, `isServer()`, and `matches(...)` semantics. `BOTH`
matches either side. `LogicalSide.from(Side)` maps a physical side to the
matching logical side; `Side.BOTH` and a missing side map to
`LogicalSide.BOTH`.

On a client with an integrated server both logical sides exist in one process.
Use `logicalSide` instead of `side` when a subscriber serves only the logical
server:

```java
@EventBusSubscriber(side = Side.BOTH, logicalSide = LogicalSide.SERVER)
public final class IntegratedServerSubscribers
{
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        // Loaded on a dedicated server and on a client with an integrated
        // server; skipped on a client without one.
    }
}
```

`side = Side.BOTH` lets the class load in either physical process.
`logicalSide = LogicalSide.SERVER` then requires a logical server to be
available. Using `side = Side.SERVER` would skip the class on a client with an
integrated server, which is the bug this split fixes.

Inside callbacks, `context.isServer()` remains the physical check and is
`false` on a client with an integrated server. Use
`context.isLogicalServer()` and `context.isLogicalClient()` to distinguish the
callback's logical side there. The loader passes a client context to client
callbacks and a server context to server callbacks, so exactly one of those
methods is true for side-specific callbacks (`BOTH` contexts report both).

Every event also carries `logicalSide()` metadata. The built-in hooks tag
client events `CLIENT`, server events `SERVER`, and the command-registration
event `BOTH`. Under `ModLifecycleEvent`, `CLIENT_SETUP` is `CLIENT`,
`SERVER_SETUP` is `SERVER`, and every other phase is `BOTH`. `RegisterEvent`
and `RegistryFreezeEvent` are `BOTH`. Custom events default to
`LogicalSide.BOTH`; set a value with the fluent
`event.setLogicalSide(LogicalSide.SERVER)` before posting.

The bus does not filter by this metadata on its own. If both logical sides
exist in one process and a listener needs strict filtering, register it with
`EventFilter.logicalSide(...)` or check `event.logicalSide()` inside the
listener. Annotated subscribers with a non-`BOTH` `logicalSide` get the filter
automatically.

Do not put `@EventBusSubscriber` on the mod entrypoint. The entrypoint instance
is already registered with `GAME`, so the scanner would register the same
methods a second time. The scanner skips the entrypoint class; if that class is
annotated, the loader logs `SUBSCRIBER ENTRYPOINT SKIPPED`.

## Dispatch order

Listeners run in this order:

1. `HIGHEST`
2. `HIGH`
3. `NORMAL`
4. `LOW`
5. `LOWEST`
6. `MONITOR`

`HIGHEST` runs first and `MONITOR` runs last. Listeners with the same priority
run in registration order. Use `MONITOR` to observe the final state; do not
change game state there.

Dispatch uses cached immutable listener snapshots keyed by event class. A
snapshot contains registrations for the event class and its superclasses,
sorted by priority and registration sequence, and is invalidated when the bus
changes. Registration or removal during a post does not affect the post that is
already running.

For a child bus, all child listeners run first (in priority order), then all
parent listeners (in priority order), regardless of priority across buses.
Dispatch is synchronous: `post` returns after every matching listener on the
posting bus and its ancestors has run.

For each registration the bus checks cancellation, then the dispatch-time
filter, then invokes the listener. A canceled or filtered-out registration is
skipped and is not counted as an invocation in the bus stats. See
[dispatch](dispatch.md) for the snapshot and filter model.

## Cancellation and results

An event carries two independent flags set at construction:

```java
new Event();                    // not cancelable, no result
new Event(true);                // cancelable, no result
new Event(false, true);         // not cancelable, has a result
new Event(true, true);          // cancelable and has a result
```

`setCanceled` throws `IllegalStateException` when the event is not cancelable,
and `setResult` throws when it has no result. The built-in action events that
are constructed cancelable are `EntityJoinLevelEvent`, `ChatEvent`,
`EntityHurtEvent`, `EntityAttackEvent`, and `BlockBreakEvent`: the loader's
factories create them cancelable, and the injected hooks honor the result. The
base two-argument `EntityJoinLevelEvent`, three-argument `ChatEvent`,
three-argument `EntityHurtEvent`, two-argument `EntityAttackEvent`, and
two-argument `BlockBreakEvent` constructors remain observation-only; the
cancelable forms are the three-argument `EntityJoinLevelEvent`, four-argument
`ChatEvent`, four-argument `EntityHurtEvent`, three-argument
`EntityAttackEvent`, and three-argument `BlockBreakEvent` constructors.
Canceling `EntityHurtEvent` makes `hurtServer` return `false` and skips damage
for that call; canceling `EntityAttackEvent` skips the attack body; canceling
`BlockBreakEvent` makes `destroyBlock` return `false` and skips the break.

`PlayerRespawnTargetEvent` and `BlockPlaceEvent` are the built-in result-based
events. They are not cancelable: a non-null value set with
`setRespawnTarget(...)` or `setResultOverride(...)` replaces the game's result,
while `null` means no override and lets vanilla run. Because `null` is the
no-override sentinel, a listener cannot force a literal `null` replacement. The
hooks read the event-specific accessor, not `EventResult`, so `setResult` has no
effect on the game. All other built-in events are not cancelable.

Cancellation for a mod-defined event still requires the posting code to check
the `post` return value and honor it.

A canceled event is still delivered to listeners that were registered with
`receiveCanceled = true`. Other listeners are skipped while the event remains
canceled, including `MONITOR` listeners. A `receiveCanceled` listener may clear
the flag with `setCanceled(false)`; listeners after it then run normally.
`post` returns `false` for a canceled event:

```java
RubyUseEvent event = new RubyUseEvent();
if (EventBus.GAME.post(event))
{
    useRuby();
}
else
{
    // A listener denied the action.
}
```

`fire(event)` is a convenience wrapper that returns the same event:

```java
RubyUseEvent event = EventBus.GAME.fire(new RubyUseEvent());
if (event.getResult() == EventResult.DENY)
{
    // ...
}
```

The result starts at `EventResult.DEFAULT` and is not reset by the bus. Because
listeners run in priority order, a later listener overwrites an earlier result.
Read the result after `post` or `fire` returns.

## Event catalog

Base types in `net.weaver.api.event.events`:

| Type | Purpose | Accessors |
|---|---|---|
| `TickEvent` | Common supertype for tick events. | `side()`, `tick()` |
| `ClientTickEvent` | Client tick events; `side()` is `CLIENT`. | `side()`, `tick()` |
| `ServerTickEvent` | Server tick events; `side()` is `SERVER`. | `side()`, `tick()` |
| `ServerLifecycleEvent` | Common supertype for server lifecycle events. | `server()` |
| `PlayerEvent` | Common supertype for player connection events. | `playerList()`, `player()` |
| `LevelEvent` | Common supertype for level load and unload. | `level()` |
| `LevelTickEvent` | Common supertype for level ticks. | `level()`, `side()`, `tick()` |
| `ScreenEvent` | Common supertype for screen changes. | `minecraft()`, `screen()`, `opening()` |

Concrete events and their constructor payloads:

| Event | Constructor | Accessors | Posted when |
|---|---|---|---|
| `ClientTickEvent.Pre` | `Pre(long tick)` | `side()` (`CLIENT`), `tick()` | Start of a client tick. |
| `ClientTickEvent.Post` | `Post(long tick)` | `side()` (`CLIENT`), `tick()` | End of a client tick. |
| `ServerTickEvent.Pre` | `Pre(long tick)` | `side()` (`SERVER`), `tick()` | Start of a server tick. |
| `ServerTickEvent.Post` | `Post(long tick)` | `side()` (`SERVER`), `tick()` | End of a server tick. |
| `ServerStartingEvent` | `ServerStartingEvent(Object server)` | `server()` | The server is about to run. |
| `ServerStartedEvent` | `ServerStartedEvent(Object server)` | `server()` | Server initialization finished. |
| `ServerStoppingEvent` | `ServerStoppingEvent(Object server)` | `server()` | Server shutdown started. |
| `ServerStoppedEvent` | `ServerStoppedEvent(Object server)` | `server()` | The server stopped; reported once per server instance. |
| `PlayerLoginEvent` | `PlayerLoginEvent(Object playerList, Object player)` | `playerList()`, `player()` | A player is being placed into the world. |
| `PlayerJoinEvent` | `PlayerJoinEvent(Object playerList, Object player)` | `playerList()`, `player()` | The player was placed and joined. |
| `PlayerLogoutEvent` | `PlayerLogoutEvent(Object playerList, Object player)` | `playerList()`, `player()` | A player is being removed. |
| `PlayerLeaveEvent` | `PlayerLeaveEvent(Object playerList, Object player)` | `playerList()`, `player()` | The player was removed. |
| `LevelLoadEvent` | `LevelLoadEvent(Object level)` | `level()` | A server level was constructed or a client level was set. |
| `LevelUnloadEvent` | `LevelUnloadEvent(Object level)` | `level()` | A level is closing or was cleared. |
| `LevelTickEvent.Pre` | `Pre(Object level, Side side, long tick)` | `level()`, `side()`, `tick()` | Start of a level tick. |
| `LevelTickEvent.Post` | `Post(Object level, Side side, long tick)` | `level()`, `side()`, `tick()` | End of a level tick. |
| `ResourceReloadEvent` | `ResourceReloadEvent(Object source, Side side, Phase phase)` | `source()`, `side()`, `phase()` | A resource reload changes phase. |
| `CommandRegisterEvent` | `CommandRegisterEvent(Object commands)` | `commands()`, `dispatcher()` | The command dispatcher was rebuilt. |
| `ScreenOpenEvent` | `ScreenOpenEvent(Object minecraft, Object screen)` | `minecraft()`, `screen()`, `opening()` (`true`) | A client screen is being shown. |
| `ScreenCloseEvent` | `ScreenCloseEvent(Object minecraft, Object screen)` | `minecraft()`, `screen()`, `opening()` (`false`) | The previous client screen is being replaced or cleared. |
| `EntityJoinLevelEvent` | `EntityJoinLevelEvent(Object level, Object entity)` or `(..., boolean cancelable)` | `level()`, `entity()` | An entity is being added to a server level; the factory creates it cancelable. |
| `ChatEvent` | `ChatEvent(Object playerList, Object message, Object sender)` or `(..., boolean cancelable)` | `playerList()`, `message()`, `sender()` | A player chat message is about to be broadcast; the factory creates it cancelable. |
| `PlayerTickEvent.Pre` | `Pre(Object player, long tick)` | `player()`, `tick()`, `side()` (`SERVER`) | Start of a server-side player tick. |
| `PlayerTickEvent.Post` | `Post(Object player, long tick)` | `player()`, `tick()`, `side()` (`SERVER`) | End of a server-side player tick. |
| `ServerSaveEvent` | `ServerSaveEvent(Object server, Phase phase)` | `server()`, `phase()` | A server save starts or finishes. |
| `LevelSaveEvent` | `LevelSaveEvent(Object level, Phase phase)` | `level()`, `phase()` | A server level save starts or finishes. |
| `EntityHurtEvent` | `EntityHurtEvent(Object level, Object entity, Object source)` or `(..., boolean cancelable)` | `level()`, `entity()`, `source()` | A living entity is about to be hurt on the server; the factory/hook creates the cancelable form, and cancellation skips damage for that call. |
| `PlayerDeathEvent` | `PlayerDeathEvent(Object player, Object source)` | `player()`, `source()` | A server player is about to die. |
| `ContainerOpenEvent` | `ContainerOpenEvent(Object player, Object menuProvider)` | `player()`, `menuProvider()` | A server player is about to open a menu. |
| `PlayerRespawnEvent` | `PlayerRespawnEvent(Object player)` | `player()` | A server player is resolving a respawn position. |
| `LootTableEvent` | `LootTableEvent(Object lootTable, Object params)` | `lootTable()`, `params()` | A loot table is about to generate items. |
| `BlockInteractEvent` | `BlockInteractEvent(Object player, Object level, Object stack, Object hand, Object hitResult)` | `player()`, `level()`, `stack()`, `hand()`, `hitResult()` | A server player is about to use an item on a block (`ServerPlayerGameMode.useItemOn` entry). |
| `ItemUseEvent` | `ItemUseEvent(Object player, Object level, Object stack, Object hand)` | `player()`, `level()`, `stack()`, `hand()` | A server player is about to use an item (`ServerPlayerGameMode.useItem` entry). |
| `LivingEntityDeathEvent` | `LivingEntityDeathEvent(Object entity, Object source)` | `entity()`, `source()` | A living entity is about to die (`LivingEntity.die` entry). |
| `ContainerCloseEvent` | `ContainerCloseEvent(Object player, Object menu)` | `player()`, `menu()` | A server player is about to close a non-inventory container menu (`ServerPlayer.closeContainer` entry). |
| `AnvilUpdateEvent` | `AnvilUpdateEvent(Object menu)` | `menu()` | An anvil menu is about to recompute its result. |
| `EnchantEvent` | `EnchantEvent(Object menu, Object player)` | `menu()`, `player()` | An enchantment menu is about to apply an enchantment. |
| `PlayerTeleportEvent` | `PlayerTeleportEvent(Object player, Object transition)` | `player()`, `transition()` | A server player is about to teleport, same-dimension or cross-dimension. |
| `PlayerRespawnTargetEvent` | `PlayerRespawnTargetEvent(Object player, Object postTeleport)` | `player()`, `postTeleport()`, `respawnTarget()`, `setRespawnTarget(Object)` | A server player's respawn-position lookup can be replaced; result-based, not cancelable. |
| `EntityInteractEvent` | `EntityInteractEvent(Object player, Object entity, Object hand, Object hitVec)` | `player()`, `entity()`, `hand()`, `hitVec()` | A player is about to interact with an entity (`Player.interactOn` entry). |
| `EntityAttackEvent` | `EntityAttackEvent(Object player, Object target)` or `(..., boolean cancelable)` | `player()`, `target()` | A player is about to attack an entity; the factory/hook creates the cancelable form. |
| `BlockBreakEvent` | `BlockBreakEvent(Object player, Object pos)` or `(..., boolean cancelable)` | `player()`, `pos()` | A server player is about to break a block; the factory/hook creates the cancelable form. |
| `BlockPlaceEvent` | `BlockPlaceEvent(Object placeContext)` | `placeContext()`, `resultOverride()`, `setResultOverride(Object)` | A block item is about to place a block; result-based, not cancelable. |
| `ItemUseFinishEvent` | `ItemUseFinishEvent(Object livingEntity)` | `livingEntity()` | A living entity is about to finish using an item. |
| `ItemReleaseEvent` | `ItemReleaseEvent(Object livingEntity)` | `livingEntity()` | A living entity is about to release a used item. |
| `ProjectileLaunchEvent` | `ProjectileLaunchEvent(Object projectile)` | `projectile()` | A projectile is about to be launched by `Projectile.shoot`; the numeric launch arguments are not exposed. |
| `ClientPlayerTickEvent.Pre` | `Pre(Object player)` | `player()`, `phase()` (`PRE`), `side()` (`CLIENT`) | The client local player tick is starting. |
| `ClientPlayerTickEvent.Post` | `Post(Object player)` | `player()`, `phase()` (`POST`), `side()` (`CLIENT`) | The client local player tick finished normally. |
| `EntityTickEvent` | `EntityTickEvent(Object level, Object entity, Side side)` | `level()`, `entity()`, `side()` | A non-passenger entity is about to tick on the server or the client. |
| `ChunkLoadEvent` | `ChunkLoadEvent(Object level, Object chunk)` | `level()`, `chunk()` | A server level moved a chunk into the ticking state. |
| `ChunkUnloadEvent` | `ChunkUnloadEvent(Object level, Object chunk)` | `level()`, `chunk()` | A server level is about to unload a chunk. |
| `EntityRemovalEvent` | `EntityRemovalEvent(Object entity, Object reason)` | `entity()`, `reason()` | An entity is about to be removed. |
| `ContainerClosedEvent` | `ContainerClosedEvent(Object player, Object containerMenu)` | `player()`, `containerMenu()` | A server player completed a non-inventory container-menu close. |
| `BlockEntityLoadEvent` | `BlockEntityLoadEvent(Object blockEntity)` | `blockEntity()` | A block entity is about to load its persisted components. |
| `BlockEntitySaveEvent` | `BlockEntitySaveEvent(Object blockEntity)` | `blockEntity()` | A block entity is about to save its full metadata. |
| `EntityLoadEvent` | `EntityLoadEvent(Object entity)` | `entity()` | An entity is about to load its additional data; Weaver capabilities are restored before the event. |
| `EntitySaveEvent` | `EntitySaveEvent(Object entity)` | `entity()` | An entity is about to save its additional data; Weaver capabilities are persisted after the event. |
| `PlayerRespawnActionEvent` | `PlayerRespawnActionEvent(Object playerList, Object oldPlayer, Object reason)` | `playerList()`, `oldPlayer()`, `reason()` | A server player is about to respawn; entry-only, the new player is not observable. |
| `ModLifecycleEvent` | `ModLifecycleEvent(ModContext context, Phase phase)` | `context()`, `modId()`, `phase()`, `logicalSide()` | A mod finished a lifecycle phase. |
| `RegisterEvent` | `RegisterEvent(RegistryKey<?> registryKey)` | `registryKey()`, `registryId()`, `registry()`, `registry(RegistryKey<T>)`, `isFrozen()` | A registry is open for registration. |
| `RegistryFreezeEvent` | `RegistryFreezeEvent(RegistryKey<?> registryKey)` | `registryKey()`, `registryId()`, `registry()`, `registry(RegistryKey<T>)` | A registry was frozen. |

The observation events `EntityHurtEvent` (three-argument constructor),
`PlayerDeathEvent`, `ContainerOpenEvent`, `PlayerRespawnEvent`,
`LootTableEvent`, `BlockInteractEvent`, `ItemUseEvent`,
`LivingEntityDeathEvent`, `ContainerCloseEvent`, `ContainerClosedEvent`,
`AnvilUpdateEvent`, `EnchantEvent`, `PlayerTeleportEvent`, `EntityInteractEvent`
(two-argument form), `EntityAttackEvent` (two-argument form),
`BlockBreakEvent` (two-argument form), `ItemUseFinishEvent`, `ItemReleaseEvent`,
`ProjectileLaunchEvent`, `ClientPlayerTickEvent`, `EntityTickEvent`,
`ChunkLoadEvent`, `ChunkUnloadEvent`, `EntityRemovalEvent`,
`BlockEntityLoadEvent`, `BlockEntitySaveEvent`, `EntityLoadEvent`,
`EntitySaveEvent`, and `PlayerRespawnActionEvent`
are observation-only. The factory/hook-created four-argument `EntityHurtEvent`,
three-argument `EntityAttackEvent`, and three-argument `BlockBreakEvent` are
cancelable. `PlayerRespawnTargetEvent` and `BlockPlaceEvent` are result-based,
not cancelable. See [hook points](hooks.md).

### Typed events

When the optional `api-game` artifact is installed, the loader's event factory
creates the typed subclasses in `net.weaver.api.game.event` instead of the base
class. They extend the base event and narrow the accessors; a listener
registered for the base class still receives them. The typed game API also
includes typed forms for every hook event, including the 2.2 and expansion
sets, created by `TypedHookEventFactory`; core resolves that factory
reflectively through `GameEventFactoryHolder.hookEvents()` and falls back to
`DefaultHookEventFactory`. See [typed game API](api-game.md) for the bridge
rules.

`ResourceReloadEvent.Phase` is `START`, `FINISHED`, or `FAILED`. The built-in
reload hooks post `START` and `FINISHED`; `FAILED` is available to mod-posted
events. `ServerSaveEvent.Phase` and `LevelSaveEvent.Phase` are `START` and
`FINISHED`. `ModLifecycleEvent.Phase` is `INITIALIZE`, `REGISTER`,
`COMMON_SETUP`, `CLIENT_SETUP`, `SERVER_SETUP`, or `LOAD_COMPLETE`.

Every event inherits `logicalSide()`, `setLogicalSide(LogicalSide)`, and the
default `LogicalSide.BOTH`. See [Physical and logical sides](#physical-and-logical-sides).

`Side` is `CLIENT`, `SERVER`, or `BOTH`. `isClient()` and `isServer()` return
true for `BOTH`. `matches(other)` returns true when either side is `BOTH` or the
two values are equal. `LogicalSide` follows the same rules; it describes the
logical side rather than the physical process.

Game payloads are `Object` so that `api` stays free of `net.minecraft.*`.
Cast in the listener:

```java
@SubscribeEvent
public void onJoin(PlayerJoinEvent event)
{
    ServerPlayer player = (ServerPlayer) event.player();
    PlayerList list = (PlayerList) event.playerList();
    // ...
}
```

Tick numbers start at 1 and increase once per side. Level tick events use their
own counter per side, incremented on each level tick call; with multiple
dimensions it is a sequence across level ticks, not the server tick number. A
listener registered for `TickEvent` sees client and server ticks; check `side()`
before using the tick number across sides.

`RegisterEvent` and `RegistryFreezeEvent` are covered in the
[registry API](registry.md).

## Lifecycle phase events

The loader posts one `ModLifecycleEvent` on the mod's local bus for every
loaded mod after that mod's phase method returns:

| Phase | Posted after |
|---|---|
| `INITIALIZE` | `onInitialize` |
| `REGISTER` | `onRegister` |
| `COMMON_SETUP` | `onCommonSetup` |
| `CLIENT_SETUP` | `onClientSetup` |
| `SERVER_SETUP` | `onServerSetup` |
| `LOAD_COMPLETE` | `onLoadComplete` |

Each event carries the mod's `ModContext`, so `modId()` identifies the mod that
completed the phase. Because the event is posted per mod, a listener receives
one event for each mod. Filter on `modId()` when you only care about your own
mod. If a phase method throws, its lifecycle event for that phase is not
posted.

The events are posted on each mod's local bus (`mod:<modId>`), which is a child
of `EventBus.MOD`, which is a child of `EventBus.GAME`. GAME listeners therefore
also see them, and `EventBus.MOD` listeners see every mod's events. Register on
the local bus (`context.modEvents()`, or `@EventBusSubscriber(bus = Bus.MOD)`)
to see only your own mod's lifecycle events. Register on `EventBus.MOD` (or
`Bus.GLOBAL_MOD`) to aggregate all mods.

`event.getBus()` returns the local bus the event was posted on, even for a
listener running on `MOD` or `GAME`. Use `event.modId()` to identify the mod,
and `EventFilter.modId(...)` to filter.

`RegisterEvent` and `RegistryFreezeEvent` are different: they are posted on
`EventBus.GAME` once per registry during `onRegister`, not once per mod.

## Custom events

Define an event with game objects as `Object` payloads:

```java
package com.example.examplemod;

import net.weaver.api.event.Event;

public final class RubyChargeEvent extends Event
{
    private final Object player;
    private final int amount;

    public RubyChargeEvent(Object player, int amount)
    {
        super(true);
        this.player = player;
        this.amount = amount;
    }

    public Object player()
    {
        return player;
    }

    public int amount()
    {
        return amount;
    }
}
```

Listen with a priority and cancel when appropriate:

```java
@SubscribeEvent(priority = EventPriority.HIGH)
public void onRubyCharge(RubyChargeEvent event)
{
    ServerPlayer player = (ServerPlayer) event.player();
    if (player.isCreative())
    {
        event.setCanceled(true);
    }
}
```

Post it and honor the outcome:

```java
RubyChargeEvent event = new RubyChargeEvent(player, 10);
if (EventBus.GAME.post(event))
{
    charge(player, event.amount());
}
```

For a mod-private event, use the mod's local bus and keep its listeners inside
the mod. `context.modEvents()` is already a child of `EventBus.MOD`, so a post
also reaches aggregate and game listeners; a bus created with
`new EventBus("examplemod", EventBus.GAME)` is a separate child with only the
scope you choose:

```java
public final class RubyEvents
{
    public static final EventBus BUS = new EventBus("examplemod", EventBus.GAME);

    public static void postCharge(Object player, int amount)
    {
        BUS.post(new RubyChargeEvent(player, amount));
    }
}
```

To make the event participate in per-mod filtering, implement `ModScopedEvent`
or post it on `context.modEvents()`:

```java
context.modEvents().post(new RubyChargeEvent(player, 10));
```

A custom event with a result uses the two-argument constructor:

```java
public final class RubyUseEvent extends Event
{
    public RubyUseEvent()
    {
        super(false, true);
    }
}
```

Listeners call `setResult(EventResult.DENY)` or `setResult(EventResult.ALLOW)`.
The posting code decides what to do with the result. The bus does not perform
the action itself and cannot undo a game action that already happened.

## Thread safety and failure isolation

- Registration, removal, and dispatch are thread-safe.
- Dispatch uses cached snapshots, so adding or removing a listener during a
  post does not corrupt that post.
- Dispatch uses cached immutable listener snapshots and does not rescan
  classes. `@SubscribeEvent` methods are resolved once, at registration time,
  into `LambdaMetafactory`/`MethodHandle` invokers. The hot path does not call
  `Method.invoke`. See [dispatch](dispatch.md#no-reflection-hot-path).
- Listeners run on the thread that called `post`. Game hooks post from the
  game thread, so listeners invoked by hooks may touch game state. If you post
  from a background thread, do not touch game objects there; schedule the work
  back to the game thread.
- The hook expansion events are posted on the invoking game thread. Most
  server hooks run on the server thread, but chunk, block-entity, and loot-table
  hooks can run on a chunk-loading or other worker thread; check the
  [hook threading table](hooks.md#hook-threading-and-frequency)
  before touching game state. `ContainerCloseEvent` and `ContainerClosedEvent`
  also read the player's `containerMenu` and `inventoryMenu` fields reflectively
  before posting, and skip the event when they are unavailable.
- One listener throwing `Throwable` never stops the remaining listeners. The
  bus invokes its `EventExceptionHandler` with the event, the listener, and the
  failure, and continues with the next listener.
- Exceptions are counted in the bus statistics.
- Keep listeners short. Long work on a game-thread event increases tick time.

Install an exception handler to route failures into your own log:

```java
EventBus.GAME.setExceptionHandler((event, listener, failure) ->
        context.log("event listener failed: " + event.getName() + " " + failure));
```

`exceptionHandler()` returns the current handler. `setExceptionHandler` returns
the bus, so calls can be chained. The default handler writes a single-line
diagnostic to `System.err` with the event name, listener, and exception, without
a stack trace. Failures are counted in the bus statistics either way.

## Statistics

`stats()` returns an immutable snapshot for the bus:

```java
EventBusStats stats = EventBus.GAME.stats();
context.log("posts=" + stats.posts()
        + " invocations=" + stats.listenerInvocations()
        + " canceled=" + stats.canceledPosts()
        + " failures=" + stats.exceptions()
        + " totalNanos=" + stats.totalNanos()
        + " maxNanos=" + stats.maxNanos());
```

`resetStats()` clears the counters. Statistics are per bus; a post on a local
bus updates the local bus's counters, not `MOD` or `GAME`. Cancellation skips
and filter rejections do not increment `listenerInvocations()`.

## Related

- [Dispatch model](dispatch.md)
- [Typed game API](api-game.md)
- [Registry API](registry.md)
- [Hook points](hooks.md)
- [Migration to 2.1](migration-2.1.md)
- [Documentation index](README.md)
