# Hook points

Weaver injects a set of hooks into Minecraft 26.3. The hooks are bytecode entry,
exit, and throw calls into `net.weaver.core.hook.GameHooks`, installed with ASM
by the loader. The built-in set currently has 59 hook specs. Most are
observation points around the game's own lifecycle; five are cancelable action
hooks (entity join, chat, entity hurt, entity attack, block break) and two are
result-value hooks (respawn target, block place). They are not a replacement
for Mixin when you need to change general game behavior.

The [event bus](events.md) reference describes the event types, the
[dispatch model](dispatch.md) describes filters and typed events, and the
[registry API](registry.md) reference describes registration.

## Delivery model

Each hook posts its event on `EventBus.GAME`. The installed `GameEventFactory`
decides whether the event is a base event or the typed event from `api-game`.
The hook events use a separate `HookEventFactory`: core resolves the typed
`TypedHookEventFactory` reflectively when `api-game` is present and falls back
to `DefaultHookEventFactory`. Hooks with a matching `WeaverMod`/`GameWeaverMod`
method then call that method on every loaded mod, in load order.

Login, logout, level ticks, entity join, entity hurt, and the observation hooks
are event-only; they have no direct `WeaverMod` callback. The hook expansion
events (block and item interaction, living entity death, container close and
completion, anvil, enchant, teleport, respawn target and action, entity
interaction and attack, block break and place, item use finish and release,
projectile launch, client player tick, entity tick, chunk load and unload,
entity removal, block entity load and save, entity load and save) are also event-only. Every other
hook fires both.

Event listeners therefore run before direct callbacks for the same hook.
Exceptions from listeners are isolated by the bus; exceptions from direct mod
methods are logged per mod and never stop other mods.

Hooks execute synchronously on the thread that reaches the injected method. The
loader does not schedule, hop, or move a hook to another thread. Do not assume
that a server-side hook runs on the server thread: `CommandRegisterEvent`, for
example, runs on the thread that builds the dispatcher, often a worker thread
(see [Commands](#commands)). Game objects passed as `Object` are only safe to
use on the invoking thread, subject to the game's own threading rules.

## Side gating and spec metadata

Every `HookSpec` carries:

| Field | Meaning |
|---|---|
| `id` | Diagnostic id, defaulting to `className#methodName+descriptor`. |
| `required` | Whether an absent or invalid target is a required miss. Defaults to `true`; optional specs report skipped. |
| `side` | `CLIENT`, `SERVER`, or `BOTH`. A spec is installed only when that side is hosted. |
| `priority` | Ordering among specs that target the same method; lower runs first. |
| `entry`, `exit`, `throwHook` calls | Calls injected at method entry, on normal returns, and before `ATHROW`. |
| `action` | `VOID`, `CANCEL_IF_FALSE`, or `RETURN_VALUE` for the entry call. |

On a dedicated server the nine client-only specs are skipped. On a client with
an integrated server both logical sides are hosted, so server specs are
installed too. A spec whose class has not loaded by the summary tick is counted
as skipped; it can still be applied later when the class loads.

## Hook table

All class names are Mojang names. `*` means any constructor descriptor.

| Target | Descriptor | Entry | Exit |
|---|---|---|---|
| `net.minecraft.client.Minecraft#tick` | `()V` | `clientTickEnter()` — `ClientTickEvent.Pre`, `onClientTick` | `clientTickExit()` — `ClientTickEvent.Post` |
| `net.minecraft.client.Minecraft#setLevel` | `(Lnet/minecraft/client/multiplayer/ClientLevel;)V` | — | `clientLevelLoad()` — `LevelLoadEvent` (client) for the new `ClientLevel` (argument 0) |
| `net.minecraft.client.Minecraft#clearClientLevel` | `(Lnet/minecraft/client/gui/screens/Screen;)V` | `clientLevelUnload()` — `LevelUnloadEvent` for the real `ClientLevel` read from `Minecraft.level` (client) | — |
| `net.minecraft.client.Minecraft#reloadResourcePacks` | `()Ljava/util/concurrent/CompletableFuture;` | `clientResourceReloadStart()` — `ResourceReloadEvent` (`START`, client) | — |
| `net.minecraft.client.Minecraft#onResourceLoadFinished` | `(Lnet/minecraft/client/GameLoadCookie;)V` | — | `clientResourceReloadFinished()` — `ResourceReloadEvent` (`FINISHED`, client) |
| `net.minecraft.client.Minecraft#setScreenAndShow` | `(Lnet/minecraft/client/gui/screens/Screen;)V` | `screenChange()` — `ScreenCloseEvent` for the previous screen and `ScreenOpenEvent` for the incoming screen | — |
| `net.minecraft.client.multiplayer.ClientLevel#tick` | `(Ljava/util/function/BooleanSupplier;)V` | `clientLevelTickEnter()` — `LevelTickEvent.Pre` (client) | `clientLevelTickExit()` — `LevelTickEvent.Post` (client) |
| `net.minecraft.server.MinecraftServer#tickServer` | `(Ljava/util/function/BooleanSupplier;)V` | `serverTickEnter()` — `ServerTickEvent.Pre`, `onServerTick` | `serverTickExit()` — `ServerTickEvent.Post` |
| `net.minecraft.server.MinecraftServer#runServer` | `()V` | `serverStarting()` — `ServerStartingEvent` | `serverStopped()` — `ServerStoppedEvent` |
| `net.minecraft.server.MinecraftServer#stopServer` | `()V` | `serverStopping()` — `ServerStoppingEvent` | — |
| `net.minecraft.server.dedicated.DedicatedServer#initServer` | `()Z` | — | `serverStarted()` — `ServerStartedEvent` |
| `net.minecraft.client.server.IntegratedServer#initServer` | `()Z` | — | `serverStarted()` — `ServerStartedEvent` |
| `net.minecraft.server.players.PlayerList#placeNewPlayer` | `(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V` | `playerLogin()` — `PlayerLoginEvent` | `playerJoin()` — `PlayerJoinEvent` |
| `net.minecraft.server.players.PlayerList#remove` | `(Lnet/minecraft/server/level/ServerPlayer;)V` | `playerLogout()` — `PlayerLogoutEvent` | `playerLeave()` — `PlayerLeaveEvent` |
| `net.minecraft.server.level.ServerLevel#<init>` | `*` | — | `levelLoad()` — `LevelLoadEvent` |
| `net.minecraft.server.level.ServerLevel#close` | `()V` | `levelUnload()` — `LevelUnloadEvent` | — |
| `net.minecraft.server.level.ServerLevel#tick` | `(Ljava/util/function/BooleanSupplier;)V` | `serverLevelTickEnter()` — `LevelTickEvent.Pre` (server) | `serverLevelTickExit()` — `LevelTickEvent.Post` (server) |
| `net.minecraft.server.level.ServerLevel#addFreshEntity` | `(Lnet/minecraft/world/entity/Entity;)Z` | `entityJoinLevel()` — cancelable `EntityJoinLevelEvent`; a canceled event makes the method return `false` | — |
| `net.minecraft.world.entity.LivingEntity#hurtServer` | `(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z` | `entityHurt()` — cancelable `EntityHurtEvent`; a canceled event makes the method return `false` and skips damage | — |
| `net.minecraft.server.level.ServerPlayer#die` | `(Lnet/minecraft/world/damagesource/DamageSource;)V` | `playerDeath()` — `PlayerDeathEvent` | — |
| `net.minecraft.server.level.ServerPlayer#openMenu` | `(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;` | `containerOpen()` — `ContainerOpenEvent` (optional) | — |
| `net.minecraft.server.level.ServerPlayerGameMode#useItemOn` | `(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;` | `blockInteract()` — `BlockInteractEvent` | — |
| `net.minecraft.server.level.ServerPlayerGameMode#useItem` | `(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;` | `itemUse()` — `ItemUseEvent` | — |
| `net.minecraft.world.entity.LivingEntity#die` | `(Lnet/minecraft/world/damagesource/DamageSource;)V` | `livingEntityDeath()` — `LivingEntityDeathEvent` | — |
| `net.minecraft.server.level.ServerPlayer#closeContainer` | `()V` | `containerClose()` — `ContainerCloseEvent` | — |
| `net.minecraft.world.inventory.AnvilMenu#createResult` | `()V` | `anvilUpdate()` — `AnvilUpdateEvent` | — |
| `net.minecraft.world.inventory.EnchantmentMenu#clickMenuButton` | `(Lnet/minecraft/world/entity/player/Player;I)Z` | `enchant()` — `EnchantEvent` | — |
| `net.minecraft.server.level.ServerPlayer#teleport` | `(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;` | `playerTeleport()` — `PlayerTeleportEvent` | — |
| `net.minecraft.server.level.ServerPlayer#findRespawnPositionAndUseSpawnBlock` | `(ZLnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;` | `playerRespawn()` — `PlayerRespawnEvent` (optional, priority 0) | — |
| `net.minecraft.server.level.ServerPlayer#findRespawnPositionAndUseSpawnBlock` | `(ZLnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;` | `playerRespawnTarget()` — result-based `PlayerRespawnTargetEvent` (optional, priority 10); a non-null result replaces the method result and skips the body | — |
| `net.minecraft.world.level.storage.loot.LootTable#getRandomItems` | `(Lnet/minecraft/world/level/storage/loot/LootParams;J)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;` | `lootTable()` — `LootTableEvent` (optional) | — |
| `net.minecraft.server.level.ServerPlayer#tick` | `()V` | `playerTickEnter()` — `PlayerTickEvent.Pre`, `onPlayerTick` | `playerTickExit()` — `PlayerTickEvent.Post` |
| `net.minecraft.server.players.PlayerList#broadcastChatMessage` | `(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V` | `chat()` — cancelable `ChatEvent`, `onChat`; a canceled event makes the injected call return `false` and skips the broadcast | — |
| `net.minecraft.server.MinecraftServer#saveEverything` | `(ZZZ)Z` | `serverSaveStart()` — `ServerSaveEvent` (`START`), `onServerSave(true)` | `serverSaveFinished()` — `ServerSaveEvent` (`FINISHED`), `onServerSave(false)` |
| `net.minecraft.server.level.ServerLevel#save` | `(Lnet/minecraft/util/ProgressListener;ZZ)V` | `levelSaveStart()` — `LevelSaveEvent` (`START`), `onLevelSave(true)` | `levelSaveFinished()` — `LevelSaveEvent` (`FINISHED`), `onLevelSave(false)` |
| `net.minecraft.server.MinecraftServer#reloadResources` | `(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;` | `serverResourceReloadStart()` — `ResourceReloadEvent` (`START`, server) | — |
| `net.minecraft.server.players.PlayerList#reloadResources` | `()V` | — | `serverResourceReloadFinished()` — `ResourceReloadEvent` (`FINISHED`, server) |
| `net.minecraft.commands.Commands#<init>` | `*` | — | `commandRegister()` — `CommandRegisterEvent` |
| `net.minecraft.world.entity.player.Player#interactOn` | `(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;` | `entityInteract()` — `EntityInteractEvent` | — |
| `net.minecraft.world.entity.player.Player#attack` | `(Lnet/minecraft/world/entity/Entity;)V` | `entityAttack()` — cancelable `EntityAttackEvent`; a canceled event returns `false` and skips the attack body | — |
| `net.minecraft.server.level.ServerPlayerGameMode#destroyBlock` | `(Lnet/minecraft/core/BlockPos;)Z` | `blockBreak()` — cancelable `BlockBreakEvent`; the player is read from the game mode's `player` field; a canceled event returns `false` and skips the break | — |
| `net.minecraft.world.item.BlockItem#place` | `(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;` | `blockPlace()` — result-based `BlockPlaceEvent`; a non-null override replaces the result and skips the body | — |
| `net.minecraft.world.entity.LivingEntity#completeUsingItem` | `()V` | `itemUseFinish()` — `ItemUseFinishEvent` | — |
| `net.minecraft.world.entity.LivingEntity#releaseUsingItem` | `()V` | `itemRelease()` — `ItemReleaseEvent` | — |
| `net.minecraft.world.entity.projectile.Projectile#shoot` | `(DDDFF)V` | `projectileLaunch()` — `ProjectileLaunchEvent`; only `this` is passed because the numeric arguments cannot be loaded by `HookSpec.Arg` | — |
| `net.minecraft.client.player.LocalPlayer#tick` | `()V` | `clientPlayerTickEnter()` — `ClientPlayerTickEvent.Pre` | `clientPlayerTickExit()` — `ClientPlayerTickEvent.Post` |
| `net.minecraft.server.level.ServerLevel#tickNonPassenger` | `(Lnet/minecraft/world/entity/Entity;)V` | `serverEntityTick()` — `EntityTickEvent` (`side=SERVER`) | — |
| `net.minecraft.client.multiplayer.ClientLevel#tickNonPassenger` | `(Lnet/minecraft/world/entity/Entity;)V` | `clientEntityTick()` — `EntityTickEvent` (`side=CLIENT`) | — |
| `net.minecraft.server.level.ServerLevel#startTickingChunk` | `(Lnet/minecraft/world/level/chunk/LevelChunk;)V` | `chunkLoad()` — `ChunkLoadEvent` | — |
| `net.minecraft.server.level.ServerLevel#unload` | `(Lnet/minecraft/world/level/chunk/LevelChunk;)V` | `chunkUnload()` — `ChunkUnloadEvent` | — |
| `net.minecraft.world.entity.Entity#remove` | `(Lnet/minecraft/world/entity/Entity$RemovalReason;)V` | `entityRemoval()` — `EntityRemovalEvent` | — |
| `net.minecraft.server.level.ServerPlayer#doCloseContainer` | `()V` | `containerClosed()` — `ContainerClosedEvent` | — |
| `net.minecraft.world.entity.Entity#saveWithoutId` | `(Lnet/minecraft/world/level/storage/ValueOutput;)V` | `entitySave()` - `EntitySaveEvent` and capability persistence; `addAdditionalSaveData` is abstract in 26.3, so the concrete wrapper is instrumented | - |
| `net.minecraft.world.entity.Entity#load` | `(Lnet/minecraft/world/level/storage/ValueInput;)V` | `entityLoad()` - capability restore then `EntityLoadEvent`; `readAdditionalSaveData` is abstract in 26.3, so the concrete wrapper is instrumented | - |
| `net.minecraft.world.level.block.entity.BlockEntity#loadWithComponents` | `(Lnet/minecraft/world/level/storage/ValueInput;)V` | `blockEntityLoad()` - `BlockEntityLoadEvent`; a priority -10 companion spec `blockEntityPersistenceLoad()` restores Weaver capabilities before the event | - |
| `net.minecraft.world.level.block.entity.BlockEntity#saveWithFullMetadata` | `(Lnet/minecraft/world/level/storage/ValueOutput;)V` | `blockEntitySave()` - `BlockEntitySaveEvent`; a priority 10 companion spec `blockEntityPersistenceSave()` writes Weaver capabilities after the event | - |
| `net.minecraft.server.players.PlayerList#respawn` | `(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;` | `playerRespawnAction()` — `PlayerRespawnActionEvent`; entry-only, the returned player is not observable, and argument 1 is primitive so it is not passed | — |

Ordinary entry hooks are inserted at method entry and do not alter arguments,
return values, or branch behavior. Exit hooks run on normal returns only; an
exception that exits the method skips them. Exit hooks on non-void methods
preserve the original return value. Throw hooks run immediately before an
`ATHROW` with the throwable still on the operand stack, so the original
exception propagates unchanged.

Five action hooks are different: they declare `Action.CANCEL_IF_FALSE`, and
the injected entry call's `boolean` result gates the method body. A canceled
entity-join event makes `addFreshEntity` return `false`; a canceled chat event
makes the injected call return `false` and skips the broadcast; a canceled
entity-hurt event makes `hurtServer` return `false` and skips damage for that
call; a canceled entity-attack event makes `Player.attack` return immediately;
and a canceled block-break event makes `destroyBlock` return `false` and skips
the break.

`findRespawnPositionAndUseSpawnBlock` carries two specs on the same method. The
priority-0 spec posts the `PlayerRespawnEvent` lookup observation. The
priority-10 spec uses `Action.RETURN_VALUE` for `PlayerRespawnTargetEvent`: a
non-null result replaces the resolved transition and skips the vanilla body;
`null` means no override and the original body runs. The lower priority
observation call still runs first on method entry.

`BlockItem#place` is the second `RETURN_VALUE` hook. `BlockPlaceEvent` is
result-based and not cancelable; a non-null `resultOverride()` replaces the
`InteractionResult` and skips the vanilla placement body. The game casts the
replacement to `InteractionResult`, so base-API listeners must set a value of
that type; the typed event narrows the accessor.

No other hook changes the target's return value.

## Hook actions and throw hooks

`HookSpec.Action` selects what the transformer does with the single action call:

| Action | Injected call | Effect |
|---|---|---|
| `VOID` | `(...)V` | Observe only. |
| `CANCEL_IF_FALSE` | `(...)Z` | A `false` result returns the target's zero/false value immediately. |
| `RETURN_VALUE` | target return type | A non-null value replaces the target result and skips the body; `null` means no override and the original body runs. |

A non-`VOID` action must declare exactly one entry call; exit and throw
observation calls are still allowed. `RETURN_VALUE` requires a concrete
descriptor and a reference-returning target.

The entity-hurt spec is one of the five `CANCEL_IF_FALSE` actions.
`GameHooks.entityHurt` returns `boolean`; the factory-created `EntityHurtEvent`
is cancelable, and a canceled event makes `hurtServer` return `false` and skips
damage for that call. The hook posts the event only; there is no direct
`WeaverMod` callback. The entity-attack and block-break specs use the same
contract: a canceled event skips the attack or block break for that call.

The respawn-target spec is the first `RETURN_VALUE` hook. It is event-only and posts
`PlayerRespawnTargetEvent`, which is result-based rather than cancelable. A
listener sets a non-null transition with `setRespawnTarget(...)`; the injected
call then returns that transition and the vanilla body is skipped, including
the spawn-block side effects performed by the body. Leaving the target `null`
(or clearing it) keeps the vanilla resolution. Because `null` is the
no-override sentinel, a listener cannot force a literal `null` replacement.

A throw hook is declared with `HookSpec.Builder.throwHook`:

```java
HookSpec.method("com.example.Target", "run", "()V")
        .throwHook("onThrow", HookSpec.Arg.THIS)
        .build();
```

The call is emitted before every `ATHROW` instruction in the method. The
throwable stays on the stack; only the declared arguments are pushed for the
call and popped by the invocation. A throw hook cannot suppress or replace the
exception. No built-in spec currently uses `throwHook`, but the transformer and
subject validation support it.

### Constructor and static-target guardrails

`HookSpec.Builder.build()` rejects unsafe specs before they reach the
transformer:

- A non-`VOID` action on `<init>` is rejected. Returning from a constructor
  before or after `super()` can produce a `VerifyError`.
- Any `throwHook` on `<init>` is rejected. The injected call would run while
  `this` is still uninitialized.
- `RETURN_VALUE` on `<init>` is rejected explicitly (a special case of the
  action rule).

The transformer validates specs that bypass the builder (for example, a
deserialized or legacy spec) and records `GAME HOOK INVALID id=... reason=...`
instead of crashing for:

- a non-`VOID` action on a constructor;
- any throw hook on a constructor;
- a non-`VOID` action on a static target.

`VOID` entry and exit observation on constructors remains valid; the two
built-in constructor specs still use it. The static-target rejection is
deliberately conservative: `CANCEL_IF_FALSE` on a static method can be
conceptually safe, but it is not supported.

## Ticks

Client ticks and server ticks each have a `Pre` and a `Post` event. The tick
counter is per side, is an `AtomicLong`, and starts at 1 on the first tick. The
same number is used for the `Pre` and `Post` event of that tick.

Level ticks are nested inside the side tick bracket:

- `ClientTickEvent.Pre` fires, then each client level tick fires `Pre` and
  `Post`, then `ClientTickEvent.Post` fires.
- `ServerTickEvent.Pre` fires, then each server level tick fires `Pre` and
  `Post`, then `ServerTickEvent.Post` fires.

`LevelTickEvent.side()` is `CLIENT` or `SERVER`. Level tick events use their own
counter per side, incremented on each level tick call; with multiple dimensions
the number is a sequence across level ticks, not the server tick number. A
server process has one `ServerLevel` per loaded dimension, so level events fire
once per dimension per tick.

The direct callbacks `onClientTick` and `onServerTick` are invoked once per
tick from the entry hook. Use the events when you need the `Post` phase or a
level-specific tick.

The loader also uses the first client and server tick to finish its own
lifecycle and call `onLoadComplete`. Tick events can fire before that happens;
guard code that depends on `onLoadComplete` state.

## Server lifecycle

| Event | Hook | Direct callback |
|---|---|---|
| `ServerStartingEvent` | `MinecraftServer.runServer` entry | `onServerStarting` |
| `ServerStartedEvent` | `DedicatedServer.initServer` or `IntegratedServer.initServer` exit | `onServerStarted` |
| `ServerStoppingEvent` | `MinecraftServer.stopServer` entry | `onServerStopping` |
| `ServerStoppedEvent` | `MinecraftServer.runServer` exit | `onServerStopped` |

`ServerStoppedEvent` fires when `runServer` returns normally, which is after
`stopServer` has saved and closed the server. The `stopServer` exit is not
hooked, so shutdown produces one stopped event at the run-loop exit rather than
an earlier one. The loader emits each server event at most once per
`MinecraftServer` instance. A new server instance, such as loading a different
world, starts a new cycle and emits the events again. The `server()` payload is
the `MinecraftServer` instance.

`onServerSetup` is a loader lifecycle phase, not this hook. It is injected at
`MinecraftServer.runServer` entry by the loader's server transformer;
`onServerStarted` is called when the game server has initialized. Do not treat
them as the same moment.

## Players

| Event | Hook | Payload |
|---|---|---|
| `PlayerLoginEvent` | `PlayerList.placeNewPlayer` entry | `playerList()`, `player()` |
| `PlayerJoinEvent` | `PlayerList.placeNewPlayer` exit | `playerList()`, `player()` |
| `PlayerLogoutEvent` | `PlayerList.remove` entry | `playerList()`, `player()` |
| `PlayerLeaveEvent` | `PlayerList.remove` exit | `playerList()`, `player()` |

The player is a `ServerPlayer`; the list is a `PlayerList`. Login fires before
the player is fully placed, join fires after. Logout fires before removal,
leave after.

Direct callbacks exist only for join and leave:

```java
@Override
public void onPlayerJoin(ModContext context, Object player)
{
    ServerPlayer serverPlayer = (ServerPlayer) player;
    // ...
}
```

There are no `onPlayerLogin` or `onPlayerLogout` methods on `WeaverMod`. Use
the events for those phases. Player events are informational and cannot cancel
the login or the removal.

Every server-side player tick posts `PlayerTickEvent.Pre` and
`PlayerTickEvent.Post`. The `onPlayerTick` direct callback runs once per tick
from the entry hook. The tick counter is global across players; use
`player()` to scope per-player state.

Chat is posted on `PlayerList.broadcastChatMessage` before the message is sent
to recipients. `ChatEvent` exposes the message, sender, and player list. The
direct callback is `onChat(context, message, sender)`.

The factory creates the cancelable form of `ChatEvent`. A listener may call
`setCanceled(true)`; the injected call then returns `false` and the message is
not broadcast. The `onChat` direct callback still runs after the event (even
when canceled), so direct callbacks observe the attempt. The base three-argument
constructor is observation-only; use the four-argument constructor for a custom
cancelable chat event.

Two 2.2 hooks cover player menu and movement. `ContainerCloseEvent` fires at
`ServerPlayer.closeContainer` entry when a non-inventory container menu is open;
the payload is the player and the menu being closed. `PlayerTeleportEvent` fires
at `ServerPlayer.teleport` entry for both same-dimension and cross-dimension
teleports; the payload is the player and the transition. Compare the source and
destination levels from the transition when the distinction matters. Both are
event-only and observation-only.

`PlayerDeathEvent` still fires at `ServerPlayer.die` entry. `LivingEntityDeathEvent`
fires earlier, at `LivingEntity.die` entry, for every living entity, so a
player death can produce both events. Login and logout are unchanged.

## Levels

| Event | Hook | Payload |
|---|---|---|
| `LevelLoadEvent` | `ServerLevel` constructor exit | The new `ServerLevel` |
| `LevelUnloadEvent` | `ServerLevel.close` entry | The closing `ServerLevel` |
| `LevelLoadEvent` | `Minecraft.setLevel` exit | The new `ClientLevel` (argument 0) |
| `LevelUnloadEvent` | `Minecraft.clearClientLevel` entry | The `ClientLevel` read from the public `Minecraft.level` field |
| `LevelTickEvent.Pre` / `Post` | `ClientLevel.tick`, `ServerLevel.tick` | The level, side, and tick |

`clearClientLevel` receives a `Screen`, not a level. The entry hook therefore
reads the public `Minecraft.level` field before the game clears it and reports
that real `ClientLevel`. `LevelUnloadEvent.level()` and the `onLevelUnload`
callback never receive the `Minecraft` instance. If the field is null or cannot
be read, the hook logs `GAME HOOK CLIENT LEVEL UNLOAD SKIPPED` once per process
and skips both the event and the direct callback.

Direct callbacks `onLevelLoad` and `onLevelUnload` receive the same payloads and
are called for both client and server levels. Cast based on the side:

```java
@Override
public void onLevelLoad(ModContext context, Object level)
{
    if (level instanceof ServerLevel serverLevel)
    {
        // ...
    }
}
```

Level load for a server level fires once per dimension when the level object is
constructed. Level tick events fire for every loaded level, every tick.

Saves post `ServerSaveEvent` and `LevelSaveEvent` with a `START`/`FINISHED`
phase. `MinecraftServer.saveEverything` brackets the server-wide save;
`ServerLevel.save` brackets one level's save. The direct callbacks are
`onServerSave(context, start)` and `onLevelSave(context, level, start)`. A
save can be triggered by autosave, a command, or shutdown.

## Resource reload

| Side | Phase | Hook | Source payload |
|---|---|---|---|
| Client | `START` | `Minecraft.reloadResourcePacks` entry | `Minecraft` |
| Client | `FINISHED` | `Minecraft.onResourceLoadFinished` exit | `Minecraft` |
| Server | `START` | `MinecraftServer.reloadResources` entry | `MinecraftServer` |
| Server | `FINISHED` | `PlayerList.reloadResources` exit | `PlayerList` |

The finished hooks run after the corresponding method body completes, not at
entry. The source payload is intentionally left as the object the hook receives
from the injected method, so it is not uniform: client start and finish report
`Minecraft`, server start reports `MinecraftServer`, and server finish reports
`PlayerList`. Use `ResourceReloadEvent.side()` and `.phase()` to distinguish the
events rather than casting the source to one type.

`ResourceReloadEvent.Phase` also defines `FAILED`, but the current hook set has
no injection point that posts it. Only `START` and `FINISHED` are reported.

The direct callback has no phase argument:

```java
@Override
public void onResourceReload(ModContext context, Object source, boolean client)
{
    // Called for both the start and the finished hook.
}
```

It is invoked from both start and finish hooks, so it cannot distinguish the two
phases. Use `ResourceReloadEvent` when the phase matters. Reload hooks can fire
while game state is changing; keep handlers read-only and idempotent.

## Commands

The `Commands` constructor builds the command dispatcher, so the exit hook runs
after the tree has been populated. `commands()` reports the completed `Commands`
object, and `dispatcher()` resolves its live dispatcher.

`CommandRegisterEvent` runs on the thread that constructs the dispatcher, which
is often a worker thread (the proof log shows `Worker-Main`), not the server
thread. Listeners may add commands or otherwise mutate the supplied dispatcher,
but must not touch world, player, or other game state from this hook. The loader
does not schedule the hook onto the server thread; schedule your own work if
you need to run later on the server thread.

```java
@SubscribeEvent
public static void onCommands(CommandRegisterEvent event)
{
    Commands commands = (Commands) event.commands();
    CommandDispatcher<CommandSourceStack> dispatcher =
            (CommandDispatcher<CommandSourceStack>) event.dispatcher();
    dispatcher.register(Commands.literal("ruby")
            .executes(source -> 1));
}
```

`CommandRegisterEvent.dispatcher()` is obtained reflectively from the
`Commands` object. `commands()` is the `Commands` instance itself. If the
`Commands` class has no public `getDispatcher()`, `dispatcher()` throws
`IllegalStateException`; the hook catches that, logs it, and passes the
`Commands` object to the direct callback instead. The direct callback receives
the dispatcher object:

```java
@Override
public void onCommandRegister(ModContext context, Object dispatcher)
{
    if (dispatcher instanceof CommandDispatcher<?> commandDispatcher)
    {
        // ...
    }
}
```

The event is informational and not cancelable. Use it to add commands; the
dispatcher is live when the hook runs.

## Screens

`Minecraft.setScreenAndShow` is hooked at entry. The hook reads the client's
current screen, then reports:

- If a previous screen exists, `ScreenCloseEvent` for it and an
  `onScreenClose` call.
- If the incoming screen is non-null, `ScreenOpenEvent` for it and an
  `onScreenOpen` call.

A replacement call therefore produces a close followed by an open. A call with
a null incoming screen produces only a close. If the current screen cannot be
read, the close event is skipped.

`ScreenEvent.screen()` is the screen involved: the old screen for a close and
the new screen for an open. `opening()` is true for open and false for close.
Direct callbacks receive the same screen object as their event.

Screen events are informational. The caller has already committed to the screen
change when the hook runs, and the events are not cancelable. Calling `setScreen`
or `setScreenAndShow` from a handler causes another screen change, so guard
against recursion.

To stop or replace a screen before anything changes, listen to the typed
`net.weaver.api.game.event.client.ScreenOpeningEvent` (`api-game`). It fires at
`Gui#setScreen` entry for a non-null screen while the current screen is still
open. A canceled event leaves the current screen (or the world) as it is;
`setNewScreen` opens another screen instead (`null` closes the current one).
The replacement is made by calling `setScreen` once more with the new screen,
which does not post the event again.

## Client feature mixins

The client also ships a small built-in Mixin config,
`weaver.client.mixins.json`, from the optional `api-game` artifact. These
mixins are separate from the ASM `GameHooks` specs above: they do not post
`GameHooks` events and they are armed only on the client side.

| Mixin | Target | Injection | Purpose |
|---|---|---|---|
| `HudLayerMixin` | `net.minecraft.client.gui.Hud#extractRenderState(GuiGraphicsExtractor, DeltaTracker)V` | `TAIL` | `ClientRegistry.renderHud(HudRenderContext)` |
| `TooltipMixin` | `net.minecraft.client.gui.screens.Screen#getTooltipFromItem(Minecraft, ItemStack)Ljava/util/List;` | `RETURN` | `ClientTooltipHook.appendTooltip`, bound to `CoreClientHooks.appendTooltip` |
| `ClientChunkMixin` | `net.minecraft.client.multiplayer.ClientLevel#onChunkLoaded(ChunkPos)V` | `TAIL` | Client chunk-load listeners |
| `ClientChunkMixin` | `net.minecraft.client.multiplayer.ClientLevel#unload(LevelChunk)V` | `HEAD` | Client chunk-unload listeners |
| `ClientChunkMixin` | `net.minecraft.client.multiplayer.ClientLevel#setServerVerifiedBlockState(BlockPos, BlockState, I)V` | `HEAD` | Server-confirmed block-update listeners |
| `WorldRenderMixin` | `net.minecraft.client.renderer.LevelRenderer#addMainThreadGizmos(List)V` | `HEAD` | `WorldRenderRegistry.render(WorldRenderContext)` through the 26.3 gizmo system |
| `ArabicEditBoxMixin` | `net.minecraft.client.gui.components.EditBox#extractWidgetRenderState(...)` | redirects and `TAIL` | Arabic shaping and bidi text |

`LoaderAgent` arms the config with `engine.addConfig("weaver-client",
"weaver.client.mixins.json", ...)` and logs
`[weaver] CLIENT FEATURE arabic-input armed`. The log label is historical; the
same config now carries the tooltip, chunk, and world render mixins. A failed
arm is logged as `[weaver] CLIENT FEATURE arabic-input FAILED`.

The config is armed from the optional `api-game` artifact, so a no-game build
never ships these client mixins. The [client API](client.md) page documents the
registration contracts, threading, proof markers, and limitations.

## Entity join

`ServerLevel.addFreshEntity` is hooked at entry:

```java
@SubscribeEvent
public void onEntityJoin(EntityJoinLevelEvent event)
{
    ServerLevel level = (ServerLevel) event.level();
    Entity entity = (Entity) event.entity();
    // ...
}
```

The factory creates the cancelable form of `EntityJoinLevelEvent`. A listener
that calls `setCanceled(true)` makes `addFreshEntity` return `false` without
adding the entity. The base two-argument constructor is observation-only; use
the three-argument constructor for a custom cancelable join event.

The hook fires only on the server; client spawn packets do not trigger it.
There is no direct `WeaverMod` callback for this hook; use the event.

Adding an entity from the handler re-enters `addFreshEntity` and posts the same
event again. Use a guard if you spawn entities in response. Canceling one event
does not affect a later, separate add call.

## Observation hooks

The 2.1 set added five server-side observation hooks. The 2.2 set added the
block/item interaction, living entity death, container close, anvil, enchant,
and teleport observations, plus the result-based respawn target. The expansion
set adds entity interaction, entity attack, block break and place, item use
finish and release, projectile launch, client player tick, entity tick, chunk
load and unload, entity removal, container closed, block entity load and save, entity load and save,
and the player respawn action. All of them are event-only.

| Event | Target | Fires |
|---|---|---|
| `EntityHurtEvent` | `LivingEntity.hurtServer` entry | Before damage is applied. Payloads: server level, living entity, damage source. Cancelable factory form. |
| `PlayerDeathEvent` | `ServerPlayer.die` entry | Before the player death logic runs. Payloads: player, damage source. |
| `ContainerOpenEvent` | `ServerPlayer.openMenu` entry | Before the menu is built. Payloads: player, menu provider. Optional spec. |
| `PlayerRespawnEvent` | `ServerPlayer.findRespawnPositionAndUseSpawnBlock` entry | When a respawn position is resolved. Payload: player. Optional spec. |
| `LootTableEvent` | `LootTable.getRandomItems` entry | Before a loot table generates items. Payloads: loot table, loot params. Optional spec. |
| `BlockInteractEvent` | `ServerPlayerGameMode.useItemOn` entry | Before the block interaction is resolved. Payloads: player, level, stack, hand, block hit result. |
| `ItemUseEvent` | `ServerPlayerGameMode.useItem` entry | Before the item is used. Payloads: player, level, stack, hand. |
| `LivingEntityDeathEvent` | `LivingEntity.die` entry | Before death handling runs. Payloads: entity, damage source. Players may also fire `PlayerDeathEvent` on `ServerPlayer.die`. |
| `ContainerCloseEvent` | `ServerPlayer.closeContainer` entry | Before a non-inventory container menu is closed. Payloads: player, menu being closed. |
| `AnvilUpdateEvent` | `AnvilMenu.createResult` entry | Before the anvil result is recomputed. Payload: anvil menu. |
| `EnchantEvent` | `EnchantmentMenu.clickMenuButton` entry | Before the enchantment is applied. Payloads: enchantment menu, player. |
| `PlayerTeleportEvent` | `ServerPlayer.teleport` entry | Before a same- or cross-dimension teleport is applied. Payloads: player, transition. |
| `PlayerRespawnTargetEvent` | `ServerPlayer.findRespawnPositionAndUseSpawnBlock` entry | Result-based, not cancelable. Payloads: player, post-teleport transition (may be `null`). |

The optional specs report `GAME HOOK SKIPPED` instead of a required miss when
their target is absent. The observation-only specs cannot change the death,
menu, respawn lookup, loot, interaction, anvil, enchant, or teleport result.
Entity hurt, entity attack, and block break can cancel their action for the
call; the respawn target and block place can replace the resolved result.

`ContainerCloseEvent` is posted only when `ServerPlayer.closeContainer` is
entered while the player's `containerMenu` field is non-null and is not the
player's own `inventoryMenu`. The hook reads both fields reflectively and skips
the event when either cannot be read. The payload is the player and the menu
being closed.

Listen for the typed forms to avoid casting:

```java
@SubscribeEvent
public static void onHurt(EntityHurtEvent event)
{
    ServerLevel level = event.level();
    LivingEntity entity = event.entity();
    DamageSource source = event.source();
    // ...
}
```

### Hook threading and frequency

| Hook | Thread | Frequency |
|---|---|---|
| `ContainerOpenEvent` | Server thread at `ServerPlayer.openMenu` entry | Once per menu-open attempt. |
| `PlayerRespawnEvent` | Server thread at `findRespawnPositionAndUseSpawnBlock` entry | Once per respawn-position lookup; the game may call the lookup more than once for one respawn. |
| `PlayerRespawnTargetEvent` | Server thread at the same method entry, priority 10 | Once per respawn-position lookup, after `PlayerRespawnEvent`. |
| `LootTableEvent` | The thread that calls `LootTable.getRandomItems`; this may be a worker thread | Potentially high frequency. Listeners must not touch world, player, or other game state and must be thread-safe. |
| `ChatEvent` | Server thread at `PlayerList.broadcastChatMessage` entry | Once per broadcast attempt. |
| `BlockInteractEvent` / `ItemUseEvent` | Server thread at `ServerPlayerGameMode.useItemOn` / `useItem` entry | Once per interaction attempt. |
| `LivingEntityDeathEvent` | Server thread at `LivingEntity.die` entry | Once per death handling call. |
| `ContainerCloseEvent` | Server thread at `ServerPlayer.closeContainer` entry | Once per close call with a non-inventory menu open. |
| `AnvilUpdateEvent` | Server thread at `AnvilMenu.createResult` entry | Once per result recompute; can repeat while an anvil menu is open. |
| `EnchantEvent` | Server thread at `EnchantmentMenu.clickMenuButton` entry | Once per enchant-button click. |
| `PlayerTeleportEvent` | Server thread at `ServerPlayer.teleport` entry | Once per teleport transition. |
| `EntityInteractEvent` | Server thread at `Player.interactOn` entry; in an integrated-server process the shared `Player` class can also route client calls | Once per interaction attempt. |
| `EntityAttackEvent` | Server thread at `Player.attack` entry; the shared-class caveat applies | Once per attack attempt. |
| `BlockBreakEvent` | Server thread at `ServerPlayerGameMode.destroyBlock` entry | Once per block-break attempt. |
| `BlockPlaceEvent` | Server thread at `BlockItem.place` entry; the shared-class caveat applies | Once per block-placement attempt, including placements later rejected by vanilla. |
| `ItemUseFinishEvent` / `ItemReleaseEvent` | Server thread at the `LivingEntity` entry point; the shared-class caveat applies | Once per finish or release call. |
| `ProjectileLaunchEvent` | Server thread at `Projectile.shoot` entry; the shared-class caveat applies | Once per launch call. |
| `ClientPlayerTickEvent.Pre` / `Post` | Client thread at `LocalPlayer.tick` entry and normal exit | Once per local player tick. |
| `EntityTickEvent` | Thread that ticks the level: server thread for `ServerLevel`, client thread for `ClientLevel` | Once per non-passenger entity tick; potentially high frequency, so keep listeners short. |
| `ChunkLoadEvent` | Thread that moves the chunk into the ticking state; this can be a server or chunk-loading worker thread | Once per transition into the ticking state. |
| `ChunkUnloadEvent` | Server thread at `ServerLevel.unload` entry | Once per chunk unload call. |
| `EntityRemovalEvent` | Thread that calls `Entity.remove`; usually the server thread, but the shared-class caveat applies | Once per removal call. |
| `ContainerClosedEvent` | Server thread at `ServerPlayer.doCloseContainer` entry | Once per completion call with a non-inventory menu open. |
| `BlockEntityLoadEvent` / `BlockEntitySaveEvent` | The thread that loads or saves the block entity; this can be a worker thread | Once per load or save call. |
| `EntityLoadEvent` / `EntitySaveEvent` | The thread that loads or saves the entity; usually the server thread | Once per load or save call. |
| `PlayerRespawnActionEvent` | Server thread at `PlayerList.respawn` entry | Once per respawn action. |

`PlayerRespawnEvent` is a respawn-position *lookup*, not the respawn action.
The return value may be `null`, and the game may look the position up several
times; do not treat the event as proof that a respawn completed. The
respawn-target spec can replace that lookup, but it still does not perform or
confirm the respawn action.

### Proof coverage for the observation hooks

The real 26.3 proof exercises the optional hooks once, from the first server
tick after its level and player state are known:

- On the client's integrated server, the proof has a real `ServerPlayer` and
  records `PROOF CHECK ok=true Chat cancellation`, `Container open hook`,
  `Respawn position lookup hook`, and `Loot table hook`.
- On the dedicated server there is no player, so it records
  `Chat cancellation audit-only reason=no-player`,
  `Container open audit-only reason=no-player`, and
  `Respawn position lookup audit-only reason=no-player`.
  The loot-table hook is still called directly and records
  `PROOF CHECK ok=true Loot table hook` because it only needs the server level.
- Each optional check runs once and is exception-guarded. A construction or
  call failure records `PROOF CHECK ok=false <name> reason=...` instead of
  aborting the proof.

The 2.1 proof totals 20 checks on each side (`checks=20`). The additional
checks are the four above; the dedicated-server chat, container, and respawn
lines are explicitly audit-only and do not claim a player-path execution.

### 2.2 proof coverage

The existing `examples/proof` still passes 20 checks on each side with
`checks=20`. The `examples/hooks-proof` proof passes on a real dedicated server
with `checks=10`:

- `Entity hurt cancellation`
- `Entity hurt allowed`
- `Living entity death hook`
- `Chunk load hook`
- `Entity tick hook`
- `Container close audit-only reason=no-player`
- `Anvil update audit-only reason=no-player`
- `Enchant hook audit-only reason=no-player`
- `Entity attack audit-only reason=no-player`
- `Block break audit-only reason=no-player`

The cancellation check is a real call: `hurtServer` returned `false`, the
listener observed the event, and health was unchanged (`before=20.0 after=20.0`).
The allowed check returned `true` and reduced health (`before=20.0 after=19.0`).
The death check calls `LivingEntity.die` and observes the event once. The chunk
check loads a fresh far chunk and observes its `ChunkLoadEvent`, and the entity
tick check spawns a target and observes its first `EntityTickEvent` on the
following server-tick post.

On a real client with an integrated server, `examples/hooks-proof` reports
`checks=10` with the same first five names plus `Container close hook`,
`Anvil update hook`, `Enchant hook`, `Entity attack hook`, and
`Block break hook`. The container check opens a real one-row chest menu, closes
it, and observes the event with a non-null menu. The anvil and enchant checks
call a real `AnvilMenu.createResult` and `EnchantmentMenu.clickMenuButton` and
observe the corresponding events. The attack check calls `Player.attack` on a
spawned target and the block-break check calls `ServerPlayerGameMode.destroyBlock`
on a real block position, observing both events.

Not every expansion hook is proven on both sides. Container close, anvil,
enchant, entity attack, and block break are exercised on the client's
integrated server; on the dedicated server they are audit-only lines because
there is no player. Block/item interaction, item use, teleport, block place,
projectile launch, entity removal, block entity load and save, entity load and save, and the respawn
target/action have no dedicated proof check.

### 2.2 limitations

- Anvil, enchant, teleport, and living entity death are observation-only; the
  events cannot change the result.
- Container close depends on the `containerMenu` and `inventoryMenu` field
  names. A rename or access failure silently skips the event.
- The `LivingEntity.die` and `LivingEntity.hurtServer` hooks instrument the
  `LivingEntity` class body only. A subclass override that does not call
  `super` bypasses the hook.
- The `Player.interactOn`, `Player.attack`, `Entity.remove`, `BlockItem.place`,
  `LivingEntity.completeUsingItem`, `LivingEntity.releaseUsingItem`,
  `Projectile.shoot`, and the block-entity hooks instrument the base class body
  only. An override that does not call `super` bypasses the hook.
- `HookSpec.Arg` can load only `this` and reference arguments. The
  `Projectile.shoot(DDDFF)` hook therefore passes only the projectile; the
  numeric launch coordinates are not in `ProjectileLaunchEvent`. The
  `PlayerList.respawn` hook skips the primitive boolean argument for the same
  reason.
- `Player`, `Entity`, `BlockItem`, `LivingEntity`, `Projectile`, and
  `BlockEntity` are shared classes. The specs are installed only when the
  logical server is hosted, but an integrated-server process also loads client
  classes and can route client-side calls through the same injected body.
  Listeners must check the logical context before mutating shared state.
- `RETURN_VALUE` uses `null` as the no-override sentinel, so a listener cannot
  force a literal `null` replacement.
- A non-null respawn target replaces the resolved transition and skips the
  vanilla body, including its spawn-block side effects.
- A non-null `BlockPlaceEvent` override must be an `InteractionResult`; the
  game casts it before returning. The typed event enforces the type, but the
  base event accepts any `Object`.
- Overload traps: `BlockEntity#saveWithFullMetadata` has both a
  `HolderLookup.Provider` overload returning `CompoundTag` and the targeted
  `ValueOutput` void overload; only the exact `ValueOutput` descriptor is
  hooked. `Projectile#shoot(DDDFF)V` is hooked, and `shootFromRotation` is not
  double-hooked. `PlayerList#respawn` has a primitive boolean argument, so the
  hook passes only the player list, the old player, and the removal reason.

### Chat cancellation proof scope

The client proof invokes the injected
`PlayerList.broadcastChatMessage(PlayerChatMessage, ServerPlayer, ChatType.Bound)`
method directly on the integrated server after a real player has joined. It
builds a `PlayerChatMessage.system("weaver-proof-chat")` marker and a
`ChatType.Bound` from the real chat-type registry, registers a typed
`ChatEvent` listener that cancels only that marker object, and asserts the
listener observed and canceled the event. The proof listener never cancels
ordinary chat.

This is a hook-level integration test, not a real client chat packet. It does
not exercise chat signing, signature validation, the chat screen, or a network
chat packet. It does prove that the injected method exists on a real
`PlayerList`/`ServerPlayer`, that the typed listener receives the event on the
server thread, and that cancellation makes the injected call skip the
broadcast. A dedicated server has no player, so the server proof records the
audit-only line described above; the client proof is the run that must execute
the real call.

## What can and cannot be canceled

Most injected events are not cancelable. They do not enable the cancelable
flag, so calling `setCanceled` throws `IllegalStateException`.

| Event family | Cancelable | Effect of trying |
|---|---|---|
| Tick, server, player, level, resource reload, command, lifecycle | No | Throws; the game action is unaffected. |
| Screen open and close | No | Informational; the screen change continues. |
| Entity join | Yes (factory-created form) | Cancels the addition; `addFreshEntity` returns `false`. |
| Chat | Yes (factory-created form) | Cancels the broadcast; the injected call returns `false` and the body is skipped. |
| Entity hurt | Yes (factory/hook-created form) | Cancels damage for that call; `hurtServer` returns `false`. |
| Entity attack and block break | Yes (factory/hook-created form) | Cancels the attack or block break for that call. |
| Block place | No; result-based | A non-null result override replaces the placement result and skips the vanilla body. |
| Player death, container open, respawn lookup and action, loot table, and the observation events | No | Observation only. |
| Respawn target | No; result-based | A non-null target replaces the lookup result and skips the vanilla body. |
| Register and freeze | No | Registration and freezing are facts, not vetoable actions. |
| Custom event built with `Event(true)` | Yes | The posting code must check the `post` return value. |

The base observation constructors of `EntityJoinLevelEvent` (two arguments),
`ChatEvent` (three arguments), `EntityHurtEvent` (three arguments),
`EntityAttackEvent` (two arguments), and `BlockBreakEvent` (two arguments)
remain non-cancelable. The factories and typed events use the explicit
cancelable constructors, so the injected actions honor cancellation in both
typed and fallback factory modes. The cancelable flag is set at construction
time; the hooks never toggle it themselves. `PlayerRespawnTargetEvent` and
`BlockPlaceEvent` are constructed with a result but not cancelable; use
`setRespawnTarget`/`respawnTarget()` or
`setResultOverride`/`resultOverride()`.

The bus never undoes a game action. To change general behavior at other points,
use Mixin or the game's own APIs. Use the event system to observe, coordinate
between mods, and implement your own cancelable actions.

`MONITOR` listeners are intended for observation only. They run after all other
priorities. If an event is canceled, a `MONITOR` listener only runs when it was
registered with `receiveCanceled = true`.

## Direct callbacks versus the event bus

Every hook posts an event. Hooks with a matching callback then call the
`WeaverMod`/`GameWeaverMod` method. For those hooks both paths run:

- Event listeners run first, in priority order, across all mods.
- Direct methods run afterward, in mod load order, for every loaded mod.

Use the event bus when you need priorities, cross-mod coordination, side
filtering, automatic discovery, or cancellation of a custom event. Use direct
overrides when the handler is local to one mod and a plain method is simpler.
Overriding a direct method and also subscribing to the same event runs the
logic twice.

Direct callback coverage:

| Hook area | Direct `WeaverMod` methods |
|---|---|
| Server lifecycle | `onServerStarting`, `onServerStarted`, `onServerStopping`, `onServerStopped` |
| Ticks | `onClientTick`, `onServerTick` |
| Players | `onPlayerJoin`, `onPlayerLeave`, `onPlayerTick` |
| Chat | `onChat` |
| Levels | `onLevelLoad`, `onLevelUnload` |
| Saves | `onServerSave`, `onLevelSave` |
| Resource reload | `onResourceReload` |
| Commands | `onCommandRegister` |
| Screens | `onScreenOpen`, `onScreenClose` |
| Entity join, entity hurt | None; events only |
| Player death, container open, respawn, loot table, respawn target | None; events only |
| Block/item interaction, living entity death, container close, anvil, enchant, teleport | None; events only |
| Registry events | None; events only |

Direct methods have no priority and no cancellation. They receive the mod's
`ModContext` and the payload. Exceptions thrown by one mod's direct method are
logged and do not stop the other mods.

## Fail-soft behavior

The hook targets are Minecraft 26.3 internal names and descriptors. They are
version-specific and can change in any game update.

- Each hook spec is required by default. If a class loads but the target method
  is absent, the loader logs `GAME HOOK MISSING` and continues. A target that
  matches but cannot accept the configured arguments logs `GAME HOOK INVALID`.
- A spec marked `.required(false)` reports an absent target as skipped rather
  than a required miss. The container-open, respawn lookup, respawn target, and
  loot-table hooks are optional.
- Specs are gated by the hosted side. A spec whose side is not hosted is
  recorded as skipped with `reason=side=CLIENT` or `reason=side=SERVER`. Client
  targets are absent on a dedicated server; server targets are present on both
  dedicated and integrated servers.
- Every spec logs a per-spec marker:
  `[weaver] GAME HOOK APPLIED|SKIPPED|MISSING|INVALID id=...`. The default id
  is `className#methodName+descriptor`; specs may override it with `.id(...)`.
- The first client or server tick logs one audit line:
  `[weaver] GAME HOOK SUMMARY applied=... skipped=... missing=... invalid=...
  required_misses=... pending=...`. The miss list contains the ids of required
  specs that were missing or invalid. The summary is a snapshot at that tick; a
  spec whose class has not loaded by then is counted as `pending` (not
  skipped) and can still be applied later.
- At install, a hosted required spec whose target class resource is absent
  from the game loader is recorded as `MISSING` (`GAME HOOK MISSING id=...
  reason=class-absent`); an optional one is skipped. Client-package targets on
  a process without a client are skipped (`reason=client-class-absent-on-server`).
- A spec that becomes `MISSING` or `INVALID` after the summary logs
  `[weaver] GAME HOOK LATE MISSING|INVALID id=... required=... phase=after-summary`.
- A spec is recorded as `APPLIED` only after the transformed class bytes were
  produced; if writing the class fails (for example a method grows past the
  class-file size limit), the spec is `INVALID`.
- Current proof measurements use the same summary line against the 59
  built-in specs. The skipped entries are side-gating effects: the
  dedicated-server snapshot includes the nine client-only specs. Server specs
  whose classes had not loaded at the client's first tick are now counted as
  `pending`. The summary is a snapshot; later `APPLIED` lines appear in both
  logs when those classes load. Do not read the skipped or pending count as
  absent required targets.
- `-Dweaver.hooks.strict=true` enables strict mode. If a required spec is
  missing or invalid when the summary is evaluated, the loader logs
  `GAME HOOK STRICT FAILURE required_misses=...` and throws
  `IllegalStateException`. A required spec that becomes missing or invalid
  after the summary fails strict mode on the next tick
  (`GAME HOOK STRICT FAILURE required_misses=... phase=after-summary`).
  Default (non-strict) mode only reports.
- At install time the loader checks that every hook call name exists as a
  public static method on `GameHooks` with the emitted descriptor. The check
  covers entry, exit, and throw calls; `CANCEL_IF_FALSE` calls must return
  `boolean`, and `RETURN_VALUE` calls must return a reference. A mismatch logs
  `GAME HOOK SPEC INVALID` and boot continues.
- If bytecode transformation fails, the transformer returns `null` so the
  class loads unmodified.
- Exceptions from a hook's event listeners or direct methods are isolated. They
  do not abort the tick, the server start, or boot.
- `GameHooks` is internal loader code. Mods should not call it directly.

The cancelable action hooks are the exception to "observation only": their
events are constructed cancelable by both the typed and fallback factories, and
the injected call honors the result. The respawn-target and block-place specs
are result-based rather than cancelable, but the injected call still honors the
result. All other injected events are observation-only.

After a game update, check the loader log for missing, invalid, or skipped hook
targets and for the hook summary. Use strict mode in CI when a missing required
hook must fail the run. The hooks remain a targeted integration layer; use Mixin
when the goal is to change general game behavior.

### What a broken mod can no longer take down

A mod that fails costs itself (or the one thing that failed), never the game:

- **Boot.** A mod mixin config that is unreadable (a truncated class, a class
  newer than ASM supports, a throwing plugin) is rejected with
  `MIXIN CONFIG REJECTED`; the game starts. A mod jar whose descriptor, mixin
  config, access transformer, or nested jar is too large (a zip bomb) is reported
  as unreadable instead of running out of memory. One bad nested jar no longer
  drops its valid siblings. A missing or read-only markers file only disables the
  markers copy of the log. A mod built for another game version is rejected with
  `MOD GAME VERSION MISMATCH` (override: `-Dweaver.ignoreMinecraftVersion=true`).
- **Mixins.** Weaver's own event hooks use `defaultRequire: 0`: a mod that
  overwrites a hooked method loses Weaver that one hook (reported as
  `MIXIN ANCHOR ABSENT`; the gate fails on any such line for Weaver's configs)
  instead of the whole class losing every mixin. A failure that names no config
  is pinned through the stack frames, then by isolating third-party configs one
  at a time (`MIXIN BLAME GUESS`). Anonymous and local classes inside mod mixins
  are defined next to their target (`MIXIN SYNTHETIC DEFINED`); they used to fail
  with `NoClassDefFoundError`.
- **Listeners.** Values a listener sets are checked before vanilla sees them:
  NaN and infinite numbers are refused, nulls are removed from lists (drops,
  spawners, explosion blocks and entities, spawn candidates, baked models),
  loot enchantment levels are kept in 0..255, a block-place or respawn result
  of the wrong type is ignored, and a chat message is trimmed to what the packet
  holds. Render listeners cannot leave the pose stack unbalanced. Canceling an
  anvil or grindstone take now refuses the take (it used to duplicate items).
- **Logs.** A listener that fails every tick is reported three times, then once
  every 30 seconds with a count (`repeats_suppressed=`); the first report has the
  stack trace.
- **Saves.** A mod codec that throws (including `NoClassDefFoundError` and
  `StackOverflowError`) skips that value; the world still saves and loads. A
  registry object whose constructor threw is dropped at freeze with
  `REGISTRY ORPHAN` naming its class, instead of stopping the game.
- **Network.** The per-session byte budget refills (1 MiB a second), synced
  capability values go only to players near the holder and never echo back from a
  single-player client, the registry check covers only mods installed on both
  sides, a server's config values are restored to the player's own when leaving,
  and handshakes hold up to 1024 mods and 4096 channels.
- **Crash shield** (on by default; `-Dweaver.crashShield=false` turns it off).
  Where vanilla turns one failing object into a crash report that stops the
  game, Weaver logs `CRASH PREVENTED` with the class most likely at fault and:
  suspends an entity or block entity whose tick throws (it stays in the world and
  the save but no longer ticks until the world is loaded again), disconnects a
  player whose own tick throws instead of stopping the server, skips a feature or
  structure that throws while generating that chunk, skips drawing an entity or
  block entity that fails for the frame, and closes a screen that throws while
  ticking, drawing, or handling input. Errors that mean the JVM itself is failing
  (out of memory) are never caught.
- **One place for problems.** Every rejected config, dropped mixin, mod not
  loaded, and prevented crash is collected per mod: the log prints
  `PROBLEMS SUMMARY` once the game runs, and any crash report gets a
  "Weaver mod problems" section. A mixin config that fails because of one mixin
  is armed again without it (`MIXIN CONFIG PARTIAL`), and mixins Mixin drops
  while preparing are reported the same way. A library bundled by two mods stays
  loaded when only one of them is rejected. A section-geometry listener that
  throws mid-vertex no longer stops the section build.
- **Launcher.** The client gets `-XX:StackShadowPages=32` like vanilla (native
  crashes in `jvm.dll` without an `hs_err` file were seen without it), JVM crash
  reports go next to the log, and paths with spaces or commas work.

## Shared class-space status

`tools/launcher/launch.ps1` accepts `-SharedClassSpace`, and
`WEAVER_SHARED_CLASS_SPACE=true` enables the same mode. Both paths add
`-Dweaver.classspace.shared=true` to the client and server JVM arguments and
print `shared=true` in the `LAUNCH` line. The default remains the system class
space.

In shared mode `ClassSpace` builds a parent-first `SharedModClassLoader` over
`java.class.path` plus the mod jars, with the JVM system class loader as its
parent, and uses it for both the game and mod loader. The standard launcher
still starts `net.minecraft.server.Main` or `net.minecraft.client.main.Main`
through the system loader. Parent-first resolution therefore loads game classes
from the system loader. The loader's `GameHookTransformer`, `RegistryTransform`,
`ServerInitTransform`, and `ClientInitTransform` gate on
`loader == gameLoader` (the shared loader), so they do not transform those
system-hosted game classes.

Shared class space is **experimental and unsupported as a proof gate**. The
script exits 1 when the proof does not pass. `-AllowDocumentedBlocker` makes it
exit 0 only to acknowledge the documented blocker after the factory probe
passes; it does not turn the run into a passing proof.

The server run reproduces this exactly:

```text
[weaver] CLASS SPACE mode=shared game-loader=jdk.internal.loader.ClassLoaders$AppClassLoader@... shared-loader=weaver-shared
[weaver] TYPED EVENTS enabled factory=net.weaver.api.game.TypedGameEventFactory
... server starts, but no [weaver] MOD INIT, PROOF INIT, or PROOF RESULT appears
SHARED MODE RESULT ok=false blocker=game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init
SHARED FACTORY PROBE ok=true
```

`MIXIN APPLIED` and the access-transformer rules still appear where their
transformers do not use the same loader gate; that does not compensate for the
missing lifecycle and game-hook injections. The factory probe proves that
`TypedGameEventFactory` is linkable through a parent-first loader and that
`net.weaver.api.event.EventBus` resolves to the same class object, but it does
not prove that game classes are hosted by the shared loader.

A true shared-mode transform proof requires launching the game main *only*
through the shared loader with a non-game parent (a core/launcher redesign).
That is out of scope for this hardening pass. Do not claim that shared mode
passes, and do not weaken the blocker to "the factory loads": the proof only
passes when `PROOF RESULT ok=true` appears in a `CLASS SPACE mode=shared` log.

## Game events (typed, api-game)

Some events need what the ASM hook specs above cannot express: an injection
point in the middle of a method, or a listener that changes an argument such
as a heal amount. Those events live in `api-game` as typed events in the
`net.weaver.api.game.event.living`, `.player`, and `.world` packages and are
fired by Mixin, from
`weaver.events.mixins.json`. The loader arms that config on both sides right
after the Mixin engine boots (`[weaver] GAME EVENTS armed`), before any game
class is activated. Without `api-game` the config is absent and these events
never fire.

Listen to them like any other event:

```java
EventBus.GAME.addListener(LivingHealEvent.class, event -> {
    if (event.entity() instanceof Zombie) {
        event.setAmount(event.amount() * 2.0F);
    }
});
```

Each event is posted on `EventBus.GAME` with the logical side of the level it
happens in (`net.weaver.api.game.event.support.GameEvents.post`). Listener failures are isolated by the bus.

| Event | Fired from | Cancel | Changeable |
|---|---|---|---|
| `LivingHealEvent` | `LivingEntity#heal` entry | heals nothing | `amount` |
| `LivingFallEvent` | `LivingEntity#causeFallDamage` entry | no fall damage | `distance`, `damageMultiplier` |
| `LivingJumpEvent` | `LivingEntity#jumpFromGround`, after the jump motion is set | no | entity motion |
| `LivingKnockBackEvent` | `LivingEntity#knockback` entry | no knockback | `strength`, `ratioX`, `ratioZ` |
| `LivingExperienceDropEvent` | `LivingEntity#dropExperience`, the `ExperienceOrb.award` amount | no experience | `amount` |
| `MobEffectApplicableEvent` | `LivingEntity#addEffect` entry | effect not applied | no |
| `MobEffectAddedEvent` | `LivingEntity#addEffect` returned `true` | no | no |
| `MobEffectRemoveEvent` | `LivingEntity#removeEffect` entry (not natural expiry) | effect kept | no |
| `PlayerCloneEvent` | `ServerPlayer#restoreFrom` exit | no | copy data to `player()` |
| `PlayerRespawnedEvent` | `PlayerList#respawn` exit, with the new player in its level (after a death and when leaving the End) | no | no |
| `PlayerChangedDimensionEvent` | `ServerPlayer#teleport` exit, when the level changed | no | no |
| `PlayerChangeGameModeEvent` | `ServerPlayer#setGameMode` entry | mode kept | `newMode` |
| `PlayerWakeUpEvent` | `Player#stopSleepInBed` entry, while sleeping | no | no |
| `ItemTossEvent` | `ServerPlayer#drop` returned an item entity | no | the item entity |
| `ItemPickupEvent` | `ItemEntity#playerTouch` entry, server only | item stays | no |
| `ExperiencePickupEvent` | `ExperienceOrb#playerTouch` entry, server only | orb stays | no |
| `ItemCraftedEvent` | `ResultSlot#onTake` entry | no | no |
| `ItemSmeltedEvent` | `FurnaceResultSlot#onTake` entry | no | no |
| `ExplosionStartEvent` | `ServerLevel#explode` entry | no explosion | no |
| `EntityStruckByLightningEvent` | the `thunderHit` call in `LightningBolt#tick` | entity spared | no |
| `EntityMountEvent` | `Entity#startRiding` and `Entity#removeVehicle` entry | mount or voluntary dismount blocked | no |
| `AnimalTameEvent` | `TamableAnimal#tame` and `AbstractHorse#tameWithName` entry | stays untamed | no |
| `AdvancementProgressEvent` | `PlayerAdvancements#award` returned `true` | no | no (`completed()` tells if earned) |
| `BlockDropsEvent` | the `getDrops` result inside every `Block#dropResources` overload | no item drops | `drops()` list |
| `CropGrowPreEvent` | `randomTick` entry of crops, stems, bamboo, cactus, sugar cane, cocoa, nether wart, berries, growing vines, chorus | growth tick skipped | no |
| `CropGrowPostEvent` | the same `randomTick` exit, when the block state changed | no | no |
| `BonemealEvent` | `BoneMealItem#growCrop` entry (players and dispensers) | nothing grows, no bone meal used | no |
| `BlockGrowFeatureEvent` | `TreeGrower#growTree`, `MushroomBlock#growMushroom`, `NetherFungusBlock#performBonemeal` entry | nothing grows | no |
| `FarmlandTrampleEvent` | the `turnToBaseBlock` call in `FarmlandBlock#fallOn` | farmland kept | no |
| `CreateFluidSourceEvent` | the `canConvertToSource` check in `FlowingFluid#getNewLiquid` | no | `canConvert` |
| `NeighborNotifyEvent` | `ServerLevel#updateNeighborsAt` and `updateNeighborsAtExceptFromFacing` entry | neighbor updates skipped | no |
| `NoteBlockChangeEvent` | `NoteBlock#useWithoutItem` entry, server only | note kept | `newNote` |
| `PistonMovePreEvent` | `PistonBaseBlock#triggerEvent` entry for extend and retract | piston does not move | no |
| `PistonMovePostEvent` | `PistonBaseBlock#triggerEvent` returned `true` | no | no |
| `PotionBrewPreEvent` | `BrewingStandBlockEntity#doBrew` entry | nothing brewed | no |
| `PotionBrewPostEvent` | `BrewingStandBlockEntity#doBrew` exit | no | result bottles in the stand |
| `PortalSpawnEvent` | the `PortalShape.findEmptyPortalShape` result in `BaseFireBlock#onPlace` | no portal | no |
| `ExplosionDetonateEvent` | after `ServerExplosion` computed its blocks and entities | no | `blocks()` and `entities()` lists |
| `ExplosionKnockbackEvent` | each `Entity#pushFromExplosion` call in `ServerExplosion#hurtEntities` | no | `knockback` vector |

| `LivingDamagePreEvent` | the `getDamageAfterMagicAbsorb` result in `actuallyHurt` of `LivingEntity` and `Player` | no damage | `damage` |
| `LivingDamagePostEvent` | `actuallyHurt` exit, when damage was dealt | no | no (`healthLost()`) |
| `LivingShieldBlockEvent` | the `BlocksAttacks.resolveBlockedDamage` result in `LivingEntity#applyItemBlocking` | nothing blocked | `blockedDamage` |
| `LivingUseTotemEvent` | `LivingEntity#checkTotemDeathProtection` entry, holding a death-protection item | entity dies, totem kept | no |
| `LivingDropsEvent` | `LivingEntity#dropAllDeathLoot` exit, with the captured item entities (for a player also his inventory and what he wears) | no drops | `drops()` list |
| `LivingDeathEvent` | `LivingEntity#die` entry, and `ServerPlayer#die` entry for players, server only | no death (give the entity health back) | no |
| `LivingUseItemStartEvent` | `LivingEntity#startUsingItem` entry | use not started | `duration` |
| `LivingUseItemTickEvent` | `LivingEntity#updateUsingItem` entry | no | `remaining` |
| `LivingChangeTargetEvent` | `Mob#setTarget` entry, when the target changes | target kept | `newTarget` |
| `MobDespawnEvent` | `Mob#checkDespawn` entry | no | `decision` (`ALLOW`, `DENY`, `DEFAULT`) |
| `MobEffectExpiredEvent` | the `onEffectsRemoved` call in `LivingEntity#tickEffects` | no | no |
| `LivingConversionPreEvent` | `Mob#convertTo` entry (also slime splitting) | no conversion, returns `null` | no |
| `LivingConversionPostEvent` | `Mob#convertTo` returned the new mob | no | no |
| `MobSplitEvent` | `AbstractCubeMob#remove` of a dying slime or magma cube larger than 1 | no children | no |
| `EndermanAngerEvent` | `Enderman#isBeingStaredBy` returned `true` | not angered | no |
| `EntityInvulnerabilityCheckEvent` | `Entity#isInvulnerableToBase` exit | no | `invulnerable` |
| `ProjectileImpactEvent` | `Projectile#hitTargetOrDeflectSelf` entry (every vanilla projectile) | hit ignored | no |
| `CriticalHitEvent` | the `canCriticalAttack` result in `Player#attack` (full-strength attacks) | no | `critical`, `damageMultiplier` |
| `SweepAttackEvent` | the `isSweepAttack` result in `Player#attack` | no | `sweeping` |
| `ArrowNockEvent` | `BowItem#use` entry | bow not drawn | no |
| `ArrowLooseEvent` | `BowItem#releaseUsing` entry | nothing shot | `charge` |
| `EntityTeleportEvent` | `LivingEntity#randomTeleport` entry (endermen, shulkers, chorus fruit) and `ThrownEnderpearl#onHit` entry | no teleport (pearl removed) | `target` (random teleports) |
| `EntityTravelToDimensionEvent` | `Entity#teleport` and `ServerPlayer#teleport` entry, into another level | stays put, returns `null` | no |
| `LivingEquipmentChangeEvent` | `LivingEntity#collectEquipmentChanges`, for each slot whose stack changed | no | no |
| `ArmorHurtEvent` | the `hurtAndBreak` call in `LivingEntity#doHurtEquipment` (players; vanilla mobs do not wear armor down) | armor spared | `damage` |
| `LivingBreatheEvent` | the `isEyeInFluid(WATER)` read in `LivingEntity#baseTick` | no | `canBreathe` |
| `LivingDrownEvent` | the drowning `hurtServer` call in `LivingEntity#baseTick` | no drowning damage | no |
| `LivingDestroyBlockEvent` | the `destroyBlock` call in `WitherBoss#customServerAiStep` | block kept | no |
| `FinalizeSpawnEvent` | `Mob#finalizeSpawn` entry | mob keeps its defaults | no |
| `PlayerBreakSpeedEvent` | `Player#getDestroySpeed` exit (both sides) | no | `speed` |
| `PlayerHarvestCheckEvent` | `Player#hasCorrectToolForDrops` exit (both sides) | no | `canHarvest` |
| `PlayerNameFormatEvent` | the name passed to `PlayerTeam.formatNameForTeam` in `Player#getDisplayName` | no | `displayName` |
| `PlayerTabListNameFormatEvent` | `ServerPlayer#getTabListDisplayName` exit | no | `displayName` (`null` = regular name) |
| `PlayerFlyableFallEvent` | `Player#causeFallDamage` entry, when the player may fly | no | no |
| `PlayerSetSpawnEvent` | `ServerPlayer#setRespawnPosition` entry | old respawn point kept | no |
| `CanPlayerSleepEvent` | `ServerPlayer#startSleepInBed`, once: before `super`, or at an early refusal | no (set a problem) | `problem` |
| `CanContinueSleepingEvent` | `LivingEntity#checkBedExists` exit, and the bed-rule check in `Player#tick` | no | `continueSleeping` |
| `PlayerSpawnPhantomsEvent` | the per-player spectator check in `PhantomSpawner#tick` | no (`DENY`) | `decision`, `phantomCount` |
| `StatAwardEvent` | `StatsCounter#setValue` entry | old value kept | `value` |
| `PermissionsChangedEvent` | `PlayerList#op` and `PlayerList#deop` entry | operator list unchanged | no |
| `PlayerStartTrackingEvent` | `ServerEntity#addPairing` exit | no | no |
| `PlayerStopTrackingEvent` | `ServerEntity#removePairing` exit | no | no |
| `PlayerLoadFromFileEvent` | `PrepareSpawnTask.Ready#spawn`, after the saved data is read | no | no |
| `PlayerSaveToFileEvent` | `PlayerDataStorage#save` exit | no | no |
| `ItemExpireEvent` | the despawn `discard` in `ItemEntity#tick` (age 6000) | no | `extraLife` |
| `ItemPickupPostEvent` | the successful `Inventory#add` in `ItemEntity#playerTouch` | no | no |
| `AnvilCraftPreEvent` | `AnvilMenu#onTake` entry | cost and inputs not used up | no |
| `AnvilCraftPostEvent` | `AnvilMenu#onTake` exit, with copies of the inputs | no | no |
| `ItemSmithedEvent` | `SmithingMenu#onTake` entry, non-empty result | no | no |
| `GrindstoneResultEvent` | the `computeResult` call in `GrindstoneMenu#createResult` | no result | `result` |
| `GrindstoneTakeEvent` | the grindstone result slot `onTake` entry | no experience, inputs kept | no |
| `PlayerBrewedPotionEvent` | the brewing stand potion slot `onTake` entry (server) | no | no |
| `ItemStackedOnOtherEvent` | `AbstractContainerMenu#tryItemClickBehaviourOverride` entry | click handled, nothing else happens | no |
| `TradeWithVillagerEvent` | `AbstractVillager#notifyTrade` exit | no | no |
| `UseItemOnBlockEvent` | `BlockStateBase#useItemOn` entry (`BLOCK`) and `ItemStack#useOn` entry (`ITEM`) | phase skipped, returns `result` | `result` |
| `PlayerSwitchHotbarSlotPreEvent` | `handleSetCarriedItem`, after the thread check, for a valid new slot | slot kept, client told to reselect it | no |
| `PlayerSwitchHotbarSlotPostEvent` | the `Inventory#setSelectedSlot` call in `handleSetCarriedItem` | no | no |
| `LivingSwapHandsEvent` | the two `setItemInHand` calls of the swap action in `handlePlayerAction` | hands unchanged | `toMainHand`, `toOffHand` |
| `ChunkDataLoadEvent` | `SerializableChunkData#read` exit (may be off the server thread) | no | no |
| `ChunkDataSaveEvent` | `SerializableChunkData#copyOf` exit | no | no |
| `ChunkTicketLevelUpdatedEvent` | `ChunkMap#updateChunkScheduling` exit | no | no |
| `ChunkWatchEvent` | `ChunkMap#markChunkPendingToSend` exit (`WATCH`), `PlayerChunkSender#sendChunk` exit (`SENT`), `ChunkMap#dropChunk` entry (`UNWATCH`) | no | no |
| `CustomClickActionEvent` | the `MinecraftServer#handleCustomClickAction` call in `ServerCommonPacketListenerImpl` | vanilla does not see it | no |
| `EnchantmentLevelSetEvent` | the `getEnchantmentCost` call in the `EnchantmentMenu#slotsChanged` lambda | no | `enchantLevel` |
| `GameRuleChangedEvent` | `MinecraftServer#onGameRuleChanged` exit | no | no |
| `CreateSpawnPositionEvent` | `MinecraftServer#setInitialSpawn` entry (new worlds) | vanilla spawn search skipped | no |
| `PotentialSpawnsEvent` | `NaturalSpawner#mobsAt` exit | no | `spawns()` list |
| `ModifyCustomSpawnersEvent` | the first `List#iterator` call in `ServerLevel#tickCustomSpawners` | no | `spawners()` list |
| `DatapackSyncEvent` | `PlayerList#placeNewPlayer` exit (player) and `PlayerList#reloadResources` exit (`null`) | no | no |
| `SleepFinishedEvent` | the `moveToTimeMarker` call in `ServerLevel#tick` after a night is slept through | time unchanged | `marker` |
| `TagsUpdatedEvent` | `ReloadableServerResources#updateComponentsAndStaticRegistryTags` exit | no | no |
| `VanillaGameEvent` | `ServerLevel#gameEvent` entry | nothing hears it | no |
| `VillageSiegeEvent` | the `findRandomSpawnPos` result in `VillageSiege#tryToSetupSiege` | spot discarded | no |
| `ClientKeyInputEvent` | `KeyboardHandler#keyPress` exit | no | no |
| `ClientMouseScrollEvent` | `MouseHandler#onScroll` entry, no screen open | hotbar not scrolled | no |
| `ClientMouseButtonEvent` | `MouseHandler#onButton` entry (`PRE`) and every return (`POST`), with or without a screen | `PRE`: click ignored | no |
| `ClientChatSendEvent` | `ClientPacketListener#sendChat` entry | not sent | `message` |
| `ClientChatReceivedEvent` | `ChatListener#handleSystemMessage` entry (`SYSTEM`, `OVERLAY`) and `#showMessageToPlayer` entry (`PLAYER`) | not shown | `message` |
| `ClientPlayerNetworkEvent` | `handleLogin` exit (`LOGGED_IN`), `handleRespawn` exit (`RESPAWNED`), the `resetData` call in `Minecraft#disconnect` and `#clearClientLevel` (`LOGGED_OUT`) | no | no |
| `FovModifierEvent` | `AbstractClientPlayer#getFieldOfViewModifier` exit | no | `modifier` |
| `MovementInputUpdateEvent` | after `ClientInput#tick` in `LocalPlayer#aiStep` | no | the `ClientInput` |
| `RenderFrameEvent` | around `GameRenderer#render` in `Minecraft#renderFrame` | no | no |
| `WindowResizeEvent` | `Minecraft#resizeGui` exit | no | no |
| `GameShuttingDownEvent` | `Minecraft#stop` entry and `DedicatedServer#stopServer` entry | no | no |
| `ToastAddEvent` | `ToastManager#addToast` entry | not shown | no |
| `SelectMusicEvent` | the `getSituationalMusic` result in `MusicManager#tick` | no | `music` |
| `PlaySoundEvent` | `SoundEngine#play` entry | no (set `null`) | `sound` |
| `PlayLevelSoundEvent` | both `playSeededSound` overloads of `ServerLevel` and `ClientLevel` | not played or sent | `sound`, `source`, `volume`, `pitch` |
| `ScreenInitEvent` | around the screen's own `init()` in `Screen#init(int, int)` and `#rebuildWidgets` | `PRE`: widgets not built | `addWidget` in `POST` |
| `ScreenOpeningEvent` | `Gui#setScreen` entry with a screen, before the current screen closes | screen not opened | `newScreen` |
| `RenderLivingEvent` | `LivingEntityRenderer#submit` entry (`PRE`) and exit (`POST`) | `PRE`: not drawn | no |
| `ClientLeftClickBlockEvent` | `MultiPlayerGameMode#startDestroyBlock` and `#continueDestroyBlock` entry | nothing mined or sent | no |
| `ClientDifficultyChangeEvent` | `ClientLevel.ClientLevelData#setDifficulty` exit | no | no |
| `ClientGameModeChangeEvent` | `PlayerInfo#setGameMode` exit | no | no |
| `ClientTagsUpdatedEvent` | the play-listener handover in `handleConfigurationFinished`, and `ClientPacketListener#handleUpdateTags` exit | no | no |
| `ClientPauseChangedEvent` | after `Minecraft#runTick` writes the pause flag, when it changed | no | no |
| `ClientInformationUpdatedEvent` | the `ServerPlayer#updateOptions` call in `handleClientInformation` (server) | no | no |
| `ClientInteractionInputEvent` | `Minecraft#startAttack`, `#startUseItem`, and `#pickBlockOrEntity` entry | nothing happens for that press | no |
| `ClientClickEmptyEvent` | `startAttack` entry aiming at nothing (`LEFT`), `startUseItem` entry aiming at nothing with an empty main hand (`RIGHT`) | no | no |
| `FlipFrameEvent` | after `LevelRenderer#endFrame` in `Minecraft#renderFrame` | no | no |
| `PlayerTurnEvent` | the `LocalPlayer#turn` call in `MouseHandler#turnPlayer` | no | `yawDelta`, `pitchDelta` |
| `CameraDistanceEvent` | the `getMaxZoom` call in `Camera#alignWithEntity` | no | `distance` |
| `HeartTypeEvent` | `Hud.HeartType#forPlayer` exit | no | `type` (by name) |
| `HudLayerRenderEvent` | the head of each `Hud#extract...` part method (crosshair and hotbar after their `HudRenderRegistry` stage), the contextual bar and experience level calls in `Hud#extractHotbarAndDecorations`, and each `HudRenderRegistry` listener | part not drawn this frame | no |
| `BossBarRenderEvent` | the `extractBar` call in `BossHealthOverlay#extractRenderState` | bar and name not drawn | no |
| `ScreenshotEvent` | the write task in `Screenshot#grab` (I/O thread) | not saved, message shown | `cancelMessage` |
| `SoundSourceEvent` | the channel set-up task in `SoundEngine#play` (sound thread) | no | no |
| `PlayerItemBrokenEvent` | `LivingEntity#onEquippedItemBroken` entry, for players (both sides) | no | no |
| `RenderItemInFrameEvent` | the map or item submit call in `ItemFrameRenderer#submit` | contents not drawn | no |
| `RenderTooltipEvent` | `GuiGraphicsExtractor#tooltip` entry (`PRE`) and its background call (`BACKGROUND`) | `PRE`: not drawn | `x`, `y`, `font`; `background` |
| `InventoryEffectsRenderEvent` | `EffectsInInventory#extractRenderState` entry | effects not drawn | no |
| `ExtractLevelRenderStateEvent` | `LevelExtractor#extract` exit | no | no |
| `MobGriefingEvent` | every `GameRules#get` read of `mobGriefing` in vanilla (38 sites in 30 classes, `mixin.griefing`) | no | `canGrief` |
| `AddServerReloadListenersEvent` | `ReloadableServerResources#listeners` exit, on every data pack load | no | `addListener` |
| `AddClientReloadListenersEvent` | the first `createReload` call in the `Minecraft` constructor | no | `addListener` |
| `BuildCreativeTabContentsEvent` | `CreativeModeTab#buildContents` exit | no | `accept`, `acceptTabOnly`, `acceptSearchOnly` |
| `RegisterParticleProvidersEvent` | `ParticleResources#registerProviders` exit (client startup) | no | `register`, `registerSprites` |
| `TextureAtlasStitchedEvent` | `TextureAtlas#upload` exit | no | no |
| `ModelBakingCompletedEvent` | `ModelManager#apply` exit | no | no |

Registration-time events: `AddServerReloadListenersEvent` and
`BuildCreativeTabContentsEvent` are in `.world`, the others in `.client`. The
client ones fire during startup (`Minecraft` constructor, first resource
load), so register their listeners during mod setup. Creative tabs are built
by the client only, and the event is posted as client side; added stacks go
to the end of the tab and skip duplicates. Loot and biome modifier files are read by a Weaver reload listener and parsed
when tags are bound (`LootTableModifiersMixin` on `LootTable#getRandomItemsRaw`;
spawn changes on the biome's spawn list in `ChunkGeneratorSpawnsMixin` and, for new-chunk creatures, `NaturalSpawnerEventsMixin`; docs/world-modifiers.md). New mod tabs get their own pages
(`CreativeTabPagesMixin`, `CreativeTabsValidateMixin`, and the client
`CreativeScreenPagesMixin`; docs/registry.md, "Creative tabs"). `registerSprites` reaches the
private `ParticleResources#register` overload through reflection because its
sprite interface is package-private. Deferred registration events need
NeoForge-only systems (render-state modifiers, mod mismatch tracking, data
generation) or deep render plumbing (feature renderers, picture-in-picture,
fluid models, extra atlases, baking-result edits, skull models).

`MobGriefingEvent` (`.world`) replaces the `mobGriefing` rule value wherever
vanilla reads it, with the entity that is about to change the world: the mob
itself (item pickup, snow golems, ravagers, the wither and dragon, sulfur
cubes, allays, piglins), the mob of an AI goal (breaking doors, removing
blocks, rabbits, endermen, silverfish, evokers, sheep eating grass, foxes),
the entity passed to the method (villager harvesting, wither roses, weaving
cobwebs, goat and nautilus ramming, crop, farmland, turtle egg, and powder
snow trampling), or the explosion source (mob explosions, item frames, armor
stands, items, and vehicles hit by them). Projectiles report their owner.
Vanilla reads the rule every tick for item pickup, so the hooks only build an
event when a listener exists. Each site is a `GameRules#get` wrap filtered by
the rule; sites whose entity is a parameter push it on method entry
(`support/Griefing`).

Batch 6b adds the rest of the client events Weaver hooks. `PlayerItemBrokenEvent`
(`.player`, both sides) covers items that break from durability, which is
where most of NeoForge's `PlayerDestroyItemEvent` sites are; an item used up
by other means fires nothing. `ScreenshotEvent` and `SoundSourceEvent` run off
the render thread. The heart type is a package-private enum and is exchanged
by constant name. Deferred: section geometry, the main render target, frame
graph setup, render buffers, block screen effects, and debug subscriptions
(render-pipeline plumbing), and effect-screen tooltips.

Batch 6a client events are in `net.weaver.api.game.event.client` and their
mixins sit in the config's `client` list (`mixin.client`), so a dedicated server
never loads them; `PlayLevelSoundEvent` and `GameShuttingDownEvent` are in
`.world` and fire on both sides, `ClientInformationUpdatedEvent` (server) in
`.player`. Every client event is posted with `LogicalSide.CLIENT`. Players are
rendered by `LivingEntityRenderer#submit` too, so `RenderLivingEvent` covers
NeoForge's `RenderPlayerEvent` (check for an `AvatarRenderState`).
`ClientPauseChangedEvent` fires after the change and cannot stop it. The level
render stages and screen render phases stay in `LevelRenderRegistry` and
`ScreenRenderRegistry` ([Client API](client.md)). The client proof
(`ClientGameEventsProof`) checks screen init, frames, login, movement input, FOV,
music selection, client tags, and cancels a chat message, a system message, and
a toast; it logs `CLIENT PROOF GAME EVENTS ok=true`.

Batch 5 events are in `.world` (`ChunkWatchEvent`, `CustomClickActionEvent`, and
`DatapackSyncEvent` in `.player`). `ChunkDataLoadEvent` can fire on a
chunk-loading worker thread. `ModifyCustomSpawnersEvent` and
`CreateSpawnPositionEvent` fire early (first level tick, world creation), so
register their listeners during mod setup. `EnchantmentLevelSetEvent` fires
before vanilla clears offers below their row number plus one.
`VanillaGameEvent` and `PotentialSpawnsEvent` are hot; both build no event
when nothing listens.

Batch 4b events are in `.player` (`LivingSwapHandsEvent` in `.living`). Menu
events fire on both logical sides, because menus run on the client too; the
grindstone result is posted as client side when the menu has no level
access. The grindstone result slot is the anonymous class
`GrindstoneMenu$4`; the mixin-applied check fails if an update renumbers it.
`UseItemOnBlockEvent` fires once per phase: canceling `BLOCK` with a
`PASS` result still lets the `ITEM` phase run.

Batch 4a events are in `.player` (`CanContinueSleepingEvent` in `.living`,
`ItemExpireEvent` in `.world`). `PlayerBreakSpeedEvent` and
`PlayerHarvestCheckEvent` fire on both logical sides; return the same answer
on both, or mining desyncs. `CanPlayerSleepEvent` can clear only the time-of-day
and monster problems; vanilla records both instead of returning early when a
listener exists, then posts the event once before the player lies down.
`CanContinueSleepingEvent` fires twice a tick for a sleeping player (bed and
bed rule). `PlayerSpawnPhantomsEvent` `ALLOW` skips the difficulty and
time-since-rest rolls only. `StatAwardEvent` sees the new total, not the
increment.

Batch 3b events are in `.living`. `LivingBreatheEvent` fires every tick for
every living entity while it has a listener; setting `canBreathe` to `false`
on land makes the entity lose air as if underwater. `FinalizeSpawnEvent` fires
where `Mob#finalizeSpawn` starts, so subclass code that runs before `super`
is not skipped. Mob griefing (`EntityMobGriefingEvent`) has no single vanilla
call to hook and is planned as its own pass; hand swapping moves to batch 4.

Combat events (batch 3) are in `.living`, `.player`, and `.world`. Notes:
`LivingDamagePreEvent` fires after armor and enchantment reductions and before
absorption hearts; to stop a hit before any reduction use `EntityHurtEvent`.
`LivingDropsEvent` captures what a dying entity spawns through
`Entity#spawnAtLocation`, and for a player also what `Inventory#dropAll` and
`EntityEquipment#dropAll` add to the level (his inventory and what he wears,
when `keepInventory` is off); a canceled event for a player loses those items.
`CriticalHitEvent` fires only for full-strength attacks, where vanilla evaluates
criticals; `setDamageMultiplier` replaces vanilla's 1.5 for that hit.
`LivingDeathEvent` is the cancelable death event: the loader's own
`LivingEntityDeathEvent` and `PlayerDeathEvent` hooks run at the very start of
`die`, before it, and so report the attempt even when it is canceled. A
listener that cancels must restore the entity's health, or the game removes the
still-dying entity a second later. `PlayerRespawnedEvent` is the one to use for
the player that plays on after a respawn; `PlayerRespawnEvent` fires earlier,
on the old player, while the respawn position is looked up. Mixin code must not use `switch` on an
enum: javac emits a synthetic `$SwitchMap` class that Mixin cannot carry into
the target class (it fails with `NoClassDefFoundError: ...$Anonymous$...`).

Block events (batch 2) are in `net.weaver.api.game.event.block`; the explosion
events are in `.world`. The hooks on hot paths (block drops, crop ticks, fluid
source checks, neighbor updates, explosion lists, explosion knockback) build no
event when nothing listens (`GameEvents.hasListeners`). `CropGrowPreEvent`
fires on every random tick of a growing plant, before the plant checks light
or space, so "may grow" is the meaning. `BlockDropsEvent` covers item drops
only; experience from ores still drops through `spawnAfterBreak`.

Notes:

- Events that change an argument cancel the original call and re-invoke the
  method with the new values; a per-thread marker keeps the re-entered call
  from posting the event again. Subclass overrides still run normally.
- Lightning is hooked at the call site because several entities override
  `thunderHit` without calling `super`. A dismount is never cancelable when the
  rider or vehicle is being removed.
- `ItemTossEvent` is not cancelable: the stack has already left the inventory.
- The server proof checks that every target class received its mixin and
  drives the events that need no player (heal, knockback, fall, jump, effects,
  mount, lightning, explosion, block drops, crop growth, bone meal, tree growth,
  farmland, neighbor updates, piston, portal, explosion lists and knockback,
  damage, invulnerability, totems, death drops, item use, targeting, despawn,
  effect expiry, conversion, slime splitting, bow release, random teleport,
  dimension travel, equipment change, breathing, spawn finalization) and, with a
  detached `ServerPlayer` that has no connection, the player events that send
  no packets (break speed, harvest, names, spawn point, flyable fall, stats,
  op, sleeping, continue sleeping, item expiry, pickup, player save, anvil,
  smithing, grindstone, cursor stacking, brewing, trading, item use on blocks,
  enchanting levels) and the world events that need no player (game rules,
  vibration events, potential spawns, chunk save, chunk tickets, datapack
  sync),
  logging `GAME EVENTS PROOF RESULT ok=true`;
  `tools/verify-ci.ps1` requires that marker. Joining, tracking, phantom
  spawning, hotbar switches, hand swaps, chunk watching, custom clicks,
  world creation, sleeping through the night, and sieges need a connected
  player or a moment the proof cannot reach and are covered by the class check.

Batch 7a events (from the list once deferred) are in `.world`, `.living`, and `.block`:

| Event | Fired from | Cancel | Changeable |
|---|---|---|---|
| `EntityConstructingEvent` | end of the `Entity` constructor (subclass fields are not set yet) | no | attach data |
| `EntitySizeEvent` | `Entity#refreshDimensions`, after `getDimensions` | no | `newDimensions` |
| `EntityEnteringSectionEvent` | the level's entity-section callback, when an entity moves into another 16-block section (server and client) | no | no |
| `LivingVisibilityEvent` | `LivingEntity#getVisibilityPercent` exit | no | `visibility`, `modifyVisibility(factor)` |
| `EffectParticleModificationEvent` | `LivingEntity#updateSynchronizedMobEffectParticles`, once per active effect | no | `options`, `visible` |
| `LivingGetProjectileEvent` | `getProjectile` exit on `LivingEntity`, `Player`, and `Monster` | no | `projectile` |
| `XpOrbTargetingEvent` | `ExperienceOrb#followNearbyPlayer`, after the nearest-player search | no | `target` (null: no target) |
| `SpawnClusterSizeEvent` | natural spawning, where the cluster size is read | no | `size` |
| `GetEquipmentDropChanceEvent` | `EnchantmentHelper#processEquipmentDropChance` exit | no | `dropChance` |
| `EnchantedBlockLootEvent` | `ApplyBonusCount` (Fortune-style bonuses), the tool's level | no | `level` |
| `EnchantedEntityLootEvent` | `EnchantedCountIncreaseFunction` (Looting-style bonuses), the attacker's level | no | `level` |
| `AlterGroundEvent` | `AlterGroundDecorator`, the block placed under a tree (world generation threads; posted as server side) | no | `newState` |
| `FluidPlaceBlockEvent` | `LiquidBlock#shouldSpreadLiquid` (obsidian, cobblestone, basalt), lava setting fire, lava flowing into water | block not placed | `newState` |
| `EntityTeleportEvent` (cause `SPREAD_PLAYERS`) | `/spreadplayers`, per entity | entity not moved | `target` |
| `ModMismatchEvent` | overworld creation, when the mod list recorded in the world (`weaver:mod_list` saved data) differs from the loaded mods | no | no |

Installed mods are listed at runtime by `net.weaver.api.WeaverMods`. The
server proof drives construction, size, section entry, visibility, particles,
projectiles, drop chance, block loot, fluid placement, `/spreadplayers`, and
the recorded mod list; orb targeting, cluster size, ground alteration,
looting, and lava spreading are covered by the class check.

Batch 7b (client render pipeline) and 7c (client registration) events are in `.client`,
except `DefaultDataComponentsBoundEvent` in `.world`:

| Event | Fired from | Thread | What a mod does |
|---|---|---|---|
| `AddDebugSubscriptionFlagsEvent` | `ClientDebugSubscriber#requestedSubscriptions`, every client tick | client | `add(subscription)` to receive debug values |
| `AddSectionGeometryEvent` | `SectionCompiler#compile`, after vanilla blocks and fluids | section workers | `buffer(layer)` for static geometry, section-relative positions |
| `ConfigureMainRenderTargetEvent` | `MainTarget` constructor, once | render | `enableStencil()` (depth becomes `D32_FLOAT_S8_UINT`) |
| `ExtractBlockScreenEffectEvent` | `LevelExtractor#extractPlayerState`, per overlay (`BLOCK`, `FIRE`, `WATER`) | render | cancel to hide; `setBlockState` for `BLOCK` |
| `FrameGraphSetupEvent` | `LevelRenderer#render`, before the sky pass | render | add passes and targets to `builder()` |
| `PrepareRenderBuffersEvent` | `LevelRenderer#render`, before features are prepared | render | submit more geometry to `collector()` |
| `GatherEffectScreenTooltipsEvent` | `EffectsInInventory`, while an effect is hovered | render | edit `tooltip()`; drawn only with a listener and a non-empty list |
| `CreateSkullModelsEvent` | `SkullBlockRenderer` class load, once | render | `registerModel(type, factory)`, `registerSkin(type, texture)` |
| `ModifyBakingResultEvent` | model reload, after baking and before publishing | reload worker | wrap or replace block and item models (mutable maps) |
| `RegisterFluidModelsEvent` | `FluidStateModelSet#bake`, each reload | reload worker | `register(fluid, unbakedModel)` |
| `RegisterPictureInPictureRenderersEvent` | `GameRenderer` constructor, once | render | `register(renderer)` |
| `RegisterRenderStateModifiersEvent` | the first entity render-state extraction, once | render | `register(entityClass, stateClass, modifier)`; modifiers run after vanilla fills a state |
| `RegisterTextureAtlasesEvent` | `AtlasManager` constructor, once, early in startup | render | `register(textureId, definition, mipmaps)` |
| `DefaultDataComponentsBoundEvent` | server: `ReloadableServerResources#updateComponentsAndStaticRegistryTags`; client: `RegistryDataCollector#updateComponents` | server / network | read data-driven default components |
| `RegisterFeatureRenderersEvent` | `FeatureRenderDispatcher` constructor, once | render | `register(type, renderer)`; submit nodes of that type to `PrepareRenderBuffersEvent.phases()` |
| `InitializeClientRegistriesEvent` | vanilla client data generator (`net.minecraft.client.data.Main --client`), before its providers | datagen | set up client state, add providers to `generator()` |
| `RegisterStructureConversionsEvent` (`.world`) | first upgrade of a pre-1.18.2 chunk (`StructuresBecomeConfiguredFix`) | server / upgrade | `register(oldId, newId)`, `registerBiomeMapped(...)` |

One-time registration events fire before any world exists: register their
listeners in mod initialization. The client proof requires all of them except
the block screen effects and effect tooltips, which need the camera in a block
or a hovered effect. The server proof checks `DefaultDataComponentsBoundEvent`
with a listener added in mod initialization.

The full map of NeoForge events against Weaver, and the batches still planned,
is in [Hook inventory](hook-inventory.md).

## Related

- [Hook inventory](hook-inventory.md)
- [Event bus](events.md)
- [Dispatch model](dispatch.md)
- [Typed game API](api-game.md)
- [Registry API](registry.md)
- [Migration to 2.1](migration-2.1.md)
- [Documentation index](README.md)
