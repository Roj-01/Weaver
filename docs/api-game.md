# Typed game API

`api-game` is the optional artifact that adds game-typed views to the Weaver
API without putting Minecraft classes into `api`.

- `api` stays game-free: it compiles with no Minecraft or Mojang classes and
  carries event payloads as `Object`.
- `api-game` (`net.weaver.api.game.*`) compiles against Minecraft 26.3 with the
  game classpath on `compileOnly`. It is never on a published POM and never
  bundles game classes.
- `core` has no compile dependency on `api-game`; it selects the typed factory
  reflectively at runtime.

The result is one base event model that still links for existing code, plus
covariant accessors for mods that compile against the game.

See [dispatch](dispatch.md) for bus semantics, [events](events.md) for the base
event catalog, and [registry](registry.md) for the registry API.

## Typed events

There are 49 typed events in `net.weaver.api.game.event`. Each extends the
same-named base event and narrows the accessors covariantly.

| Typed event | Accessors and narrowed types |
|---|---|
| `ChatEvent` | `playerList()` → `PlayerList`, `message()` → `PlayerChatMessage`, `sender()` → `ServerPlayer` |
| `CommandRegisterEvent` | `commands()` → `Commands`, `dispatcher()` → `CommandDispatcher<CommandSourceStack>` |
| `ContainerOpenEvent` | `player()` → `ServerPlayer`, `menuProvider()` → `MenuProvider` |
| `EntityHurtEvent` | `level()` → `ServerLevel`, `entity()` → `LivingEntity`, `source()` → `DamageSource` |
| `EntityJoinLevelEvent` | `level()` → `ServerLevel`, `entity()` → `Entity` |
| `LevelLoadEvent` | `level()` → `Level` |
| `LevelSaveEvent` | `level()` → `ServerLevel` |
| `LevelUnloadEvent` | `level()` → `Level` |
| `LootTableEvent` | `lootTable()` → `LootTable`, `params()` → `LootParams` |
| `PlayerDeathEvent` | `player()` → `ServerPlayer`, `source()` → `DamageSource` |
| `PlayerJoinEvent` | `playerList()` → `PlayerList`, `player()` → `ServerPlayer` |
| `PlayerLeaveEvent` | `playerList()` → `PlayerList`, `player()` → `ServerPlayer` |
| `PlayerLoginEvent` | `playerList()` → `PlayerList`, `player()` → `ServerPlayer` |
| `PlayerLogoutEvent` | `playerList()` → `PlayerList`, `player()` → `ServerPlayer` |
| `PlayerRespawnEvent` | `player()` → `ServerPlayer` |
| `ResourceReloadEvent` | No narrowing; `source()` stays `Object` because the source is `Minecraft`, `MinecraftServer`, or `PlayerList` depending on the path |
| `ScreenCloseEvent` | `minecraft()` → `Minecraft`, `screen()` → `Screen` (possibly `null`) |
| `ScreenOpenEvent` | `minecraft()` → `Minecraft`, `screen()` → `Screen` |
| `ServerSaveEvent` | `server()` → `MinecraftServer` |
| `ServerStartedEvent` | `server()` → `MinecraftServer` |
| `ServerStartingEvent` | `server()` → `MinecraftServer` |
| `ServerStoppedEvent` | `server()` → `MinecraftServer` |
| `ServerStoppingEvent` | `server()` → `MinecraftServer` |
| `AnvilUpdateEvent` | `menu()` → `AnvilMenu` |
| `BlockInteractEvent` | `player()` → `ServerPlayer`, `level()` → `Level`, `stack()` → `ItemStack`, `hand()` → `InteractionHand`, `hitResult()` → `BlockHitResult` |
| `ContainerCloseEvent` | `player()` → `ServerPlayer`, `menu()` → `AbstractContainerMenu` |
| `EnchantEvent` | `menu()` → `EnchantmentMenu`, `player()` → `Player` |
| `ItemUseEvent` | `player()` → `ServerPlayer`, `level()` → `Level`, `stack()` → `ItemStack`, `hand()` → `InteractionHand` |
| `LivingEntityDeathEvent` | `entity()` → `LivingEntity`, `source()` → `DamageSource` |
| `PlayerTeleportEvent` | `player()` → `ServerPlayer`, `transition()` → `TeleportTransition` |
| `PlayerRespawnTargetEvent` | `player()` → `ServerPlayer`, `postTeleport()` → `TeleportTransition`, `respawnTarget()` → `TeleportTransition` |
| `EntityInteractEvent` | `player()` → `Player`, `entity()` → `Entity`, `hand()` → `InteractionHand`, `hitVec()` → `Vec3` |
| `EntityAttackEvent` | `player()` → `Player`, `target()` → `Entity` |
| `BlockBreakEvent` | `player()` → `ServerPlayer`, `pos()` → `BlockPos` |
| `BlockPlaceEvent` | `placeContext()` → `BlockPlaceContext`, `resultOverride()` → `InteractionResult` |
| `ItemUseFinishEvent` | `livingEntity()` → `LivingEntity` |
| `ItemReleaseEvent` | `livingEntity()` → `LivingEntity` |
| `ProjectileLaunchEvent` | `projectile()` → `Projectile` |
| `ClientPlayerTickEvent` | `player()` → `LocalPlayer`; inherits `phase()` (`PRE`/`POST`) and `side()` (`CLIENT`) |
| `EntityTickEvent` | `level()` → `Level`, `entity()` → `Entity` |
| `ChunkLoadEvent` | `level()` → `ServerLevel`, `chunk()` → `LevelChunk` |
| `ChunkUnloadEvent` | `level()` → `ServerLevel`, `chunk()` → `LevelChunk` |
| `EntityRemovalEvent` | `entity()` → `Entity`, `reason()` → `RemovalReason` |
| `ContainerClosedEvent` | `player()` → `ServerPlayer`, `containerMenu()` → `AbstractContainerMenu` |
| `BlockEntityLoadEvent` | `blockEntity()` → `BlockEntity` |
| `BlockEntitySaveEvent` | `blockEntity()` → `BlockEntity` |
| `PlayerRespawnActionEvent` | `playerList()` → `PlayerList`, `oldPlayer()` → `ServerPlayer`, `reason()` → `RemovalReason` |

### `PlayerRespawnEvent` is a respawn-position lookup

`PlayerRespawnEvent`, both the base event and the typed subclass, is fired when
`ServerPlayer.findRespawnPositionAndUseSpawnBlock` resolves a respawn position.
It is **not** the respawn action and does not mean the player has respawned.
The lookup can run more than once for the same player, so listeners may receive
the event multiple times, and it is posted on the server thread.

The name is unchanged for binary and source compatibility. Register for it when
you want to observe a respawn-position lookup, not a completed respawn.

Typed event constructors still take `Object` payloads. This keeps the bridge
usable from the game-free `GameEventFactory` SPI and lets the loader construct
typed events without naming game types in `core`.

`EntityJoinLevelEvent` has a two-argument constructor that creates a cancelable
event and a three-argument constructor with an explicit `cancelable` flag. The
two-argument form is what the typed factory uses for the injected
`addFreshEntity` hook. `ChatEvent` narrows the base cancelable event and is
created cancelable by the typed factory.

## Bridge strategy

The base events keep their `Object`-typed accessors. The typed subclasses
override them with covariant return types:

```java
// base
public Object level() { return level; }

// net.weaver.api.game.event.LevelLoadEvent
@Override
public Level level() {
    return (Level) super.level();
}
```

The Java compiler emits a synthetic bridge method with the original `Object`
return descriptor for every covariant override. Both call shapes therefore
link against the same typed class:

```java
net.weaver.api.event.events.LevelLoadEvent base = event;
Object value = base.level();                 // bridge, Object descriptor

net.weaver.api.game.event.LevelLoadEvent typed =
        (net.weaver.api.game.event.LevelLoadEvent) event;
Level level = typed.level();                 // typed descriptor
```

Bridge methods are filtered out by the subscriber scanner and by listener
signature resolution, so a typed bridge does not create a duplicate subscriber.

The base classes were de-finalized to allow the subclasses; their constructors
and accessors were not changed. Do not change a base constructor or accessor
descriptor: source and binary compatibility for base-only mods depends on
those descriptors staying exactly as they are.

## GameWeaverMod

`GameWeaverMod` extends `WeaverMod` and adds typed extension points:

```java
public interface GameWeaverMod extends WeaverMod {
    default void onPlayerJoin(ModContext context, ServerPlayer player) { }
    default void onPlayerLeave(ModContext context, ServerPlayer player) { }
    default void onLevelLoad(ModContext context, Level level) { }
    default void onLevelUnload(ModContext context, Level level) { }

    @Override
    default void onPlayerJoin(ModContext context, Object player) {
        if (player instanceof ServerPlayer serverPlayer) {
            onPlayerJoin(context, serverPlayer);
        }
    }
    // ... the other Object overloads delegate the same way
}
```

- The typed overloads are the extension point for new mods.
- The inherited `Object` overloads check the payload with `instanceof` and
  forward. A payload of a different type is ignored, not cast.
- A mod that overrides the `Object` method keeps the old behavior. Its override
  is called directly and the typed default is not reached unless the override
  calls it.
- `ProofMod` in the repository implements `GameWeaverMod` and overrides only the
  typed `onPlayerJoin` and `onLevelLoad` methods.

## Typed registry helpers

`RegisterEvent` and `RegistryFreezeEvent` expose both forms:

```java
RegistryView<?> untyped = event.registry();

RegistryView<Item> items = event.registry(GameRegistries.ITEM);
```

The typed overload:

- rejects a `null` key with `NullPointerException`;
- rejects a key for a different registry with `IllegalArgumentException` before
  touching the registry backend;
- otherwise opens the same live view as `event.registry()`.

This is validation and type inference only. Registration timing, freeze
semantics, and the `RegistryException` failure behavior are unchanged. See
[registry](registry.md).

## GameRegistries

`GameRegistries` holds typed `RegistryKey<T>` constants for the built-in
Minecraft registries. Every key uses the `minecraft` namespace and the exact
resource path the game uses.

| Constant | Id | Element type |
|---|---|---|
| `ITEM` | `minecraft:item` | `Item` |
| `BLOCK` | `minecraft:block` | `Block` |
| `ENTITY_TYPE` | `minecraft:entity_type` | `EntityType<?>` |
| `FLUID` | `minecraft:fluid` | `Fluid` |
| `MOB_EFFECT` | `minecraft:mob_effect` | `MobEffect` |
| `ENCHANTMENT` | `minecraft:enchantment` | `Enchantment` |
| `BLOCK_ENTITY_TYPE` | `minecraft:block_entity_type` | `BlockEntityType<?>` |
| `MENU` | `minecraft:menu` | `MenuType<?>` |
| `RECIPE_TYPE` | `minecraft:recipe_type` | `RecipeType<?>` |
| `ATTRIBUTE` | `minecraft:attribute` | `Attribute` |
| `PARTICLE_TYPE` | `minecraft:particle_type` | `ParticleType<?>` |
| `SOUND_EVENT` | `minecraft:sound_event` | `SoundEvent` |
| `POTION` | `minecraft:potion` | `Potion` |

These ids are verified by `GameRegistriesTest` and by the real-game proof
(`PROOF CHECK ok=true GameRegistries ITEM` and
`PROOF CHECK ok=true RegisterEvent typed registry`).

`WeaverRegistries` remains the game-free equivalent in `api`, with
`RegistryKey<Object>` constants. A base-only mod can keep using it; a mod that
compiles against `api-game` can use `GameRegistries` when it wants the element
type carried by the key. Both resolve to the same registry id.

## GameEventFactory SPI

`net.weaver.api.spi.GameEventFactory` is the game-free construction SPI used by
`core` hooks. It has one method per typed event (23 methods). Every parameter
and return type is expressed without naming a game class:

```java
PlayerJoinEvent playerJoin(Object playerList, Object player);
LevelLoadEvent levelLoad(Object level);
EntityJoinLevelEvent entityJoinLevel(Object level, Object entity);
ChatEvent chat(Object playerList, Object message, Object sender);
// ...
```

There are two implementations:

- `DefaultGameEventFactory` in `api` returns the plain base events. It creates
  the cancelable `EntityJoinLevelEvent` and `ChatEvent` forms, so the two action
  pilots still work without `api-game`.
- `TypedGameEventFactory` in `api-game` returns the typed subclasses from
  `net.weaver.api.game.event`.

Factories must be stateless and thread-safe: one factory instance is shared by
the client, server, and integrated-server threads.

`net.weaver.api.event.events.HookEventFactory` is the separate game-free SPI for
the hook events. It has 26 methods: the nine original hook events plus entity
interact, entity attack, block break, block place, item use finish, item
release, projectile launch, client player tick pre/post, entity tick, chunk
load/unload, entity removal, container closed, block entity load/save, and
player respawn action. Every payload parameter is an `Object`; `entityTick`
also takes the side as a `Side` enum. `DefaultHookEventFactory` returns the base
events and `TypedHookEventFactory` returns the typed subclasses.

## Installation and fallback

`LoaderAgent` installs the factory after the class space is resolved:

1. Resolve `net.weaver.api.game.TypedGameEventFactory` through
   `classSpace.gameLoader()`.
2. Instantiate it and call
   `GameEventFactoryHolder.install((GameEventFactory) instance)`.
3. Log `[weaver] TYPED EVENTS enabled factory=net.weaver.api.game.TypedGameEventFactory`.

If the class or one of its game dependencies is missing, the loader catches
`ClassNotFoundException`/`LinkageError` and logs one line:

```
[weaver] TYPED EVENTS unavailable reason=...
```

The holder keeps `DefaultGameEventFactory`, so base events continue to work and
boot does not fail. The real proof expects the enabled line; a missing
`api-game` is a packaging error, not a supported runtime mode for a typed mod.

`GameEventFactoryHolder.events()` never returns `null`,
`GameEventFactoryHolder.isTyped()` tells whether a non-base factory is
installed, and `description()` returns the installed class name or `"base"` for
diagnostics.

## Classloader rules

`api-game` is a normal library, not a separate class-loading layer:

- In the default system class space, the agent jar's `Class-Path` puts exactly
  one `api` jar and one `api-game` jar on the system classloader. Mods are
  appended to the same system classloader.
- In the opt-in shared class space (`-Dweaver.classspace.shared=true`),
  `ClassSpace` builds a parent-first `SharedModClassLoader` over the game class
  path plus mod jars, with the JVM system classloader as its parent. The
  standard launcher still starts the game main through the system loader, so
  the game classes remain system-hosted. The agent lib (including `api` and
  `api-game`) is on the parent path, so `api-game` is loaded once and shares the
  same base `net.weaver.api.*` classes.
- The agent jar itself contains only `net.weaver.core` classes. The agent lib is
  the distribution unit; `tools/installer/install.ps1` verifies that the
  installed lib contains an `api-game-*.jar`.

Never add a second copy of `api` to a mod jar or a mod classpath. Two copies of
the base API classes would create two `EventBus.GAME` identities and break
dispatch. `:core:verifyAgentLayout` checks that the agent lib has exactly one
`api-game` jar, exactly one base `api` jar, that the `api-game` jar has no game
classes and no bundled base API classes, and that the manifest `Class-Path`
lists each exactly once.

## Shared class-space proof status

The launcher can request shared mode with `-SharedClassSpace` or
`WEAVER_SHARED_CLASS_SPACE=true`; both add
`-Dweaver.classspace.shared=true` to the client and server JVM arguments.
`examples/proof/run-server-shared.ps1` runs the proof in that mode and records
the outcome. Shared class space is **experimental and unsupported as a proof
gate**: the script exits 1 when the proof does not pass, and
`-AllowDocumentedBlocker` only acknowledges the documented blocker after the
factory probe passes.

`ClassSpace` creates a parent-first `SharedModClassLoader` whose parent is the
system loader and uses it as both `gameLoader` and `modLoader`. The launcher
still invokes `net.minecraft.server.Main` / `net.minecraft.client.main.Main`
through the system loader. Because the shared loader delegates parent-first,
the game classes are loaded by the system loader, not by the shared loader.
The loader's hook and lifecycle transformers (`GameHookTransformer`,
`RegistryTransform`, `ServerInitTransform`, `ClientInitTransform`) gate on
`loader == gameLoader`, so they skip those game classes. The run logs
`[weaver] CLASS SPACE mode=shared` and
`[weaver] TYPED EVENTS enabled factory=net.weaver.api.game.TypedGameEventFactory`,
starts the server, and then never logs `PROOF INIT`, `[weaver] MOD INIT`, or
`PROOF RESULT`. The observed blocker token is:

```text
SHARED MODE RESULT ok=false blocker=game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init
```

Mixin and access-transformer output can still appear because those transformers
do not use the same loader gate; that does not restore the missing lifecycle or
game-hook injections.

`examples/proof/run-server-shared.ps1` then runs
`examples/proof/src/main/java/net/weaver/proof/SharedFactoryProbe.java`. The
probe builds a `URLClassLoader` over `java.class.path` plus the proof jar with
the system loader as parent, loads `net.weaver.api.game.TypedGameEventFactory`,
checks that it implements `net.weaver.api.spi.GameEventFactory`, instantiates it
and creates an `entityHurt` event, and checks
`loader.loadClass("net.weaver.api.event.EventBus") == EventBus.class`. It
prints:

```text
SHARED FACTORY PROBE ok=true
```

That is evidence of factory linkability and single base-class identity only. It
is not evidence that game classes are hosted by the shared loader. A true
shared-mode transform proof requires launching the game main exclusively
through the shared loader with a non-game parent, which is a core/launcher
redesign outside this hardening pass. Do not claim shared mode passes until
`PROOF RESULT ok=true` appears in a `CLASS SPACE mode=shared` log.

## No game classes in the artifact

`api-game` uses the game classpath only for compilation:

```kotlin
compileOnly(files(gameClasspath))
testImplementation(files(gameClasspath))
```

It is not on `api`, `implementation`, `runtimeOnly`, or a published POM.
`verifyNoGameClasses` fails the build if the jar contains `net/minecraft/` or
`com/mojang/` entries. `verifyPomHasNoGameDependencies` fails if the published
POM references game jars or Minecraft/Mojang coordinates.

This makes the artifact safe to publish and keeps the loader distribution
explicit: the game classes come from the game, the Weaver classes come from the
agent lib.

## Binary compatibility

Rules for the typed artifact:

- Additive changes are preferred. New typed events, new typed accessors on new
  classes, and new default methods ship in a minor release.
- Removing a type or changing an existing constructor, method, or field
  descriptor requires a major version bump.
- The base `Object` accessors and constructors are part of the compatibility
  surface. They must stay unchanged while typed subclasses exist.
- Covariant overrides must keep their compiler-generated `Object` bridges. Do
  not hand-write an accessor with the base descriptor in a typed subclass.
- `ResourceReloadEvent.source()` intentionally stays `Object`; do not narrow it.
- Base classes are de-finalized only to permit subclassing. Do not add state or
  change behavior in a way that changes the base event contract.
- A new Minecraft release rebuilds `api-game` for the new game version and
  leaves `api` unchanged.
- `LegacyFixtureLinkageTest` compiles its fixture at test runtime against the
  `api` classes only, then loads it and posts a typed event. It uses
  `sun.misc.Unsafe.allocateInstance` for the game-type identity payloads because
  the test does not start a world. That is test-only; production code never uses
  `Unsafe`. A future JDK may require an explicit unsafe-access flag for this
  test.

## Version constants

| Constant | Value | Meaning |
|---|---|---|
| `WeaverApi.VERSION` | `2.1.0` | Base API version. |
| `WeaverGameApi.VERSION` | `26.3` | Minecraft version targeted by the typed artifact. |
| Project version | `2.3.2` | Gradle artifact version for this tree. |
| Jar manifest | `Weaver-Game-Api-Version: 26.3` | Machine-readable typed-artifact version. |

The base API and the game API are separate artifacts with separate versions. A
new game version does not force a base API major bump.

## Related

- [Dispatch model](dispatch.md)
- [Event bus reference](events.md)
- [Registry API](registry.md)
- [Hook points](hooks.md)
- [Migration to 2.1](migration-2.1.md)
- [Toolchain](toolchain.md)
- [Documentation index](README.md)
