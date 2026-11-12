package net.weaver.core.hook;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;
import net.weaver.api.event.Event;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.BlockBreakEvent;
import net.weaver.api.event.events.BlockPlaceEvent;
import net.weaver.api.event.events.ChatEvent;
import net.weaver.api.event.events.ClientPlayerTickEvent;
import net.weaver.api.event.events.ClientTickEvent;
import net.weaver.api.event.events.CommandRegisterEvent;
import net.weaver.api.event.events.EntityAttackEvent;
import net.weaver.api.event.events.EntityHurtEvent;
import net.weaver.api.event.events.EntityJoinLevelEvent;
import net.weaver.api.event.events.EntityTickEvent;
import net.weaver.api.event.events.LevelSaveEvent;
import net.weaver.api.event.events.LevelTickEvent;
import net.weaver.api.event.events.PlayerRespawnTargetEvent;
import net.weaver.api.event.events.PlayerTickEvent;
import net.weaver.api.event.events.ResourceReloadEvent;
import net.weaver.api.event.events.ServerSaveEvent;
import net.weaver.api.event.events.ServerTickEvent;
import net.weaver.core.LifecycleHooks;
import net.weaver.core.LifecycleInjection;
import net.weaver.core.capability.CapabilityPersistence;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Static entry points called from injected game bytecode. */
public final class GameHooks
{
    private static final AtomicLong CLIENT_TICK = new AtomicLong();
    private static final AtomicLong SERVER_TICK = new AtomicLong();
    private static final AtomicLong CLIENT_LEVEL_TICK = new AtomicLong();
    private static final AtomicLong SERVER_LEVEL_TICK = new AtomicLong();
    private static final AtomicLong PLAYER_TICK = new AtomicLong();

    private static final Object SERVER_LOCK = new Object();
    // Weak: a stopped integrated server (levels, players) must not stay reachable until the next world.
    private static final AtomicReference<java.lang.ref.WeakReference<Object>> CURRENT_SERVER =
            new AtomicReference<>(new java.lang.ref.WeakReference<>(null));
    private static final AtomicBoolean SERVER_STARTING = new AtomicBoolean();
    private static final AtomicBoolean SERVER_STARTED = new AtomicBoolean();
    private static final AtomicBoolean SERVER_STOPPING = new AtomicBoolean();
    private static final AtomicBoolean SERVER_STOPPED = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_LEVEL_SKIP_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean BLOCK_BREAK_SKIP_LOGGED = new AtomicBoolean();

    private static final ClassValue<LevelAccessor> LEVEL_ACCESSORS = new ClassValue<>()
    {
        @Override
        protected LevelAccessor computeValue(Class<?> type)
        {
            Field field = findField(type, "level");
            return field == null ? LevelAccessor.MISSING : new LevelAccessor(field);
        }
    };

    private static final ClassValue<ScreenAccessor> SCREEN_ACCESSORS = new ClassValue<>()
    {
        @Override
        protected ScreenAccessor computeValue(Class<?> type)
        {
            Field direct = findField(type, "screen");
            if (direct != null)
            {
                return new ScreenAccessor(direct, null);
            }
            Field gui = findField(type, "gui");
            if (gui != null)
            {
                Field screen = findField(gui.getType(), "screen");
                if (screen != null)
                {
                    return new ScreenAccessor(screen, gui);
                }
            }
            return ScreenAccessor.MISSING;
        }
    };

    private static final ClassValue<MenuAccessor> MENU_ACCESSORS = new ClassValue<>()
    {
        @Override
        protected MenuAccessor computeValue(Class<?> type)
        {
            Field containerMenu = findField(type, "containerMenu");
            Field inventoryMenu = findField(type, "inventoryMenu");
            if (containerMenu == null || inventoryMenu == null)
            {
                return MenuAccessor.MISSING;
            }
            return new MenuAccessor(containerMenu, inventoryMenu);
        }
    };

    private static final ClassValue<PlayerAccessor> PLAYER_ACCESSORS = new ClassValue<>()
    {
        @Override
        protected PlayerAccessor computeValue(Class<?> type)
        {
            Field player = findField(type, "player");
            return player == null ? PlayerAccessor.MISSING : new PlayerAccessor(player);
        }
    };

    private GameHooks()
    {
    }

    public static void clientTickEnter()
    {
        GameHookTransformer.logSummaryOnce();
        LifecycleInjection.enforce();
        long tick = CLIENT_TICK.incrementAndGet();
        post(new ClientTickEvent.Pre(tick), LogicalSide.CLIENT);
        tickLifecycle(true);
        forEachMod(LogicalSide.CLIENT, (mod, context) -> mod.onClientTick(context, tick));
    }

    public static void clientTickExit()
    {
        post(new ClientTickEvent.Post(CLIENT_TICK.get()), LogicalSide.CLIENT);
    }

    public static void serverTickEnter()
    {
        GameHookTransformer.logSummaryOnce();
        LifecycleInjection.enforce();
        long tick = SERVER_TICK.incrementAndGet();
        post(new ServerTickEvent.Pre(tick), LogicalSide.SERVER);
        tickLifecycle(false);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onServerTick(context, tick));
    }

    public static void serverTickExit()
    {
        post(new ServerTickEvent.Post(SERVER_TICK.get()), LogicalSide.SERVER);
    }

    public static void serverStarting(Object server)
    {
        if (server == null)
        {
            return;
        }
        enterServerCycle(server);
        if (!SERVER_STARTING.compareAndSet(false, true))
        {
            return;
        }
        post(GameEventFactoryHolder.events().serverStarting(server), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onServerStarting(context));
    }

    public static void serverStarted(Object server)
    {
        if (server == null)
        {
            return;
        }
        enterServerCycle(server);
        if (!SERVER_STARTED.compareAndSet(false, true))
        {
            return;
        }
        post(GameEventFactoryHolder.events().serverStarted(server), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onServerStarted(context));
    }

    public static void serverStopping(Object server)
    {
        if (server == null)
        {
            return;
        }
        enterServerCycle(server);
        if (!SERVER_STOPPING.compareAndSet(false, true))
        {
            return;
        }
        post(GameEventFactoryHolder.events().serverStopping(server), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onServerStopping(context));
    }

    public static void serverStopped(Object server)
    {
        if (server == null)
        {
            return;
        }
        enterServerCycle(server);
        if (!SERVER_STOPPED.compareAndSet(false, true))
        {
            return;
        }
        post(GameEventFactoryHolder.events().serverStopped(server), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onServerStopped(context));
    }

    public static void playerLogin(Object playerList, Object player)
    {
        if (playerList == null || player == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().playerLogin(playerList, player), LogicalSide.SERVER);
    }

    public static void playerJoin(Object playerList, Object player)
    {
        if (playerList == null || player == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().playerJoin(playerList, player), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onPlayerJoin(context, player));
    }

    public static void playerLogout(Object playerList, Object player)
    {
        if (playerList == null || player == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().playerLogout(playerList, player), LogicalSide.SERVER);
    }

    public static void playerLeave(Object playerList, Object player)
    {
        if (playerList == null || player == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().playerLeave(playerList, player), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onPlayerLeave(context, player));
    }

    public static void levelLoad(Object level)
    {
        if (level == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().levelLoad(level), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onLevelLoad(context, level));
    }

    public static void levelUnload(Object level)
    {
        if (level == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().levelUnload(level), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onLevelUnload(context, level));
    }

    public static void serverLevelTickEnter(Object level)
    {
        if (level == null)
        {
            return;
        }
        long tick = SERVER_LEVEL_TICK.incrementAndGet();
        post(new LevelTickEvent.Pre(level, Side.SERVER, tick), LogicalSide.SERVER);
    }

    public static void serverLevelTickExit(Object level)
    {
        if (level == null)
        {
            return;
        }
        post(new LevelTickEvent.Post(level, Side.SERVER, SERVER_LEVEL_TICK.get()),
                LogicalSide.SERVER);
    }

    public static void clientLevelTickEnter(Object level)
    {
        if (level == null)
        {
            return;
        }
        long tick = CLIENT_LEVEL_TICK.incrementAndGet();
        post(new LevelTickEvent.Pre(level, Side.CLIENT, tick), LogicalSide.CLIENT);
    }

    public static void clientLevelTickExit(Object level)
    {
        if (level == null)
        {
            return;
        }
        post(new LevelTickEvent.Post(level, Side.CLIENT, CLIENT_LEVEL_TICK.get()),
                LogicalSide.CLIENT);
    }

    public static void clientLevelLoad(Object level)
    {
        clientLevelLoad(null, level);
    }

    public static void clientLevelLoad(Object minecraft, Object level)
    {
        if (level == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().levelLoad(level), LogicalSide.CLIENT);
        forEachMod(LogicalSide.CLIENT, (mod, context) -> mod.onLevelLoad(context, level));
    }

    /**
     * Client level unload hook. The injected call passes the {@code Minecraft} instance because
     * {@code clearClientLevel} receives a screen, not the level. The real {@code ClientLevel} is
     * read from the public {@code level} field at method entry, before the game clears it.
     */
    public static void clientLevelUnload(Object minecraft)
    {
        if (minecraft == null)
        {
            return;
        }
        Object level = readLevel(minecraft);
        if (level == null)
        {
            if (CLIENT_LEVEL_SKIP_LOGGED.compareAndSet(false, true))
            {
                LifecycleHooks.logLine("[weaver] GAME HOOK CLIENT LEVEL UNLOAD SKIPPED"
                        + " reason=level-unavailable class=" + minecraft.getClass().getName());
            }
            return;
        }
        post(GameEventFactoryHolder.events().levelUnload(level), LogicalSide.CLIENT);
        forEachMod(LogicalSide.CLIENT, (mod, context) -> mod.onLevelUnload(context, level));
    }

    public static void clientResourceReloadStart(Object source)
    {
        if (source == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().resourceReload(source, Side.CLIENT,
                ResourceReloadEvent.Phase.START), LogicalSide.CLIENT);
        forEachMod(LogicalSide.CLIENT, (mod, context) -> mod.onResourceReload(context, source, true));
    }

    public static void clientResourceReloadFinished(Object source)
    {
        if (source == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().resourceReload(source, Side.CLIENT,
                ResourceReloadEvent.Phase.FINISHED), LogicalSide.CLIENT);
        forEachMod(LogicalSide.CLIENT, (mod, context) -> mod.onResourceReload(context, source, true));
    }

    public static void serverResourceReloadStart(Object source)
    {
        if (source == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().resourceReload(source, Side.SERVER,
                ResourceReloadEvent.Phase.START), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onResourceReload(context, source, false));
    }

    public static void serverResourceReloadFinished(Object source)
    {
        if (source == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().resourceReload(source, Side.SERVER,
                ResourceReloadEvent.Phase.FINISHED), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onResourceReload(context, source, false));
    }

    /**
     * Command dispatcher registration hook.
     *
     * <p>Runs on the thread that constructs the game's {@code Commands} object, which is often a
     * worker thread (for example {@code Worker-Main}) rather than the server thread. Listeners may
     * mutate the supplied dispatcher, but must not touch world, player, or other game state. The
     * loader does not schedule this hook onto the server thread.
     */
    public static void commandRegister(Object commands)
    {
        if (commands == null)
        {
            return;
        }
        CommandRegisterEvent event = GameEventFactoryHolder.events().commandRegister(commands);
        post(event, LogicalSide.BOTH);
        Object dispatcher = commands;
        try
        {
            Object resolved = event.dispatcher();
            if (resolved != null)
            {
                dispatcher = resolved;
            }
        }
        catch (Throwable failure)
        {
            LifecycleHooks.logLine("[weaver] COMMAND DISPATCHER LOOKUP FAILED reason=" + failure);
        }
        Object target = dispatcher;
        forEachMod(LogicalSide.BOTH, (mod, context) -> mod.onCommandRegister(context, target));
    }

    public static void screenChange(Object minecraft, Object newScreen)
    {
        if (minecraft == null)
        {
            return;
        }
        Object oldScreen = readScreen(minecraft);
        if (oldScreen != null)
        {
            post(GameEventFactoryHolder.events().screenClose(minecraft, oldScreen),
                    LogicalSide.CLIENT);
            forEachMod(LogicalSide.CLIENT, (mod, context) -> mod.onScreenClose(context, oldScreen));
        }
        if (newScreen != null)
        {
            post(GameEventFactoryHolder.events().screenOpen(minecraft, newScreen),
                    LogicalSide.CLIENT);
            forEachMod(LogicalSide.CLIENT, (mod, context) -> mod.onScreenOpen(context, newScreen));
        }
    }

    /**
     * Server-side entity-join hook. The injected call uses the boolean result
     * to gate {@code ServerLevel#addFreshEntity}; a canceled event returns
     * {@code false} so the entity is not added.
     *
     * @param level  the level receiving the entity
     * @param entity the entity being added
     * @return {@code true} when the addition may proceed
     */
    public static boolean entityJoinLevel(Object level, Object entity)
    {
        if (level == null || entity == null)
        {
            return true;
        }
        EntityJoinLevelEvent event = GameEventFactoryHolder.events().entityJoinLevel(level, entity);
        post(event, LogicalSide.SERVER);
        return event == null || !event.isCanceled();
    }

    public static void playerTickEnter(Object player)
    {
        if (player == null)
        {
            return;
        }
        long tick = PLAYER_TICK.incrementAndGet();
        post(new PlayerTickEvent.Pre(player, tick), LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onPlayerTick(context, player));
    }

    public static void playerTickExit(Object player)
    {
        if (player == null)
        {
            return;
        }
        post(new PlayerTickEvent.Post(player, PLAYER_TICK.get()), LogicalSide.SERVER);
    }

    /**
     * Server-side chat hook. The injected call uses the boolean result to gate
     * {@code PlayerList#broadcastChatMessage}; a canceled event returns
     * {@code false} so the message is not broadcast. Mod chat callbacks still
     * run so listeners always observe the attempt.
     *
     * @param playerList the player list broadcasting the message
     * @param message    the chat message
     * @param sender     the sending player
     * @return {@code true} when the broadcast may proceed
     */
    public static boolean chat(Object playerList, Object message, Object sender)
    {
        if (playerList == null || message == null || sender == null)
        {
            return true;
        }
        ChatEvent event = GameEventFactoryHolder.events().chat(playerList, message, sender);
        post(event, LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onChat(context, message, sender));
        return event == null || !event.isCanceled();
    }

    /**
     * Server-side entity-hurt hook, fired before
     * {@code LivingEntity#hurtServer} applies damage. The injected call uses
     * the boolean result to gate {@code hurtServer}; a canceled event returns
     * {@code false} so the damage is not applied.
     *
     * @param entity the entity being hurt
     * @param level  the server level
     * @param source the damage source
     * @return {@code true} when the damage may proceed
     */
    public static boolean entityHurt(Object entity, Object level, Object source)
    {
        if (entity == null || level == null || source == null)
        {
            return true;
        }
        EntityHurtEvent event = GameEventFactoryHolder.hookEvents()
                .entityHurt(level, entity, source);
        post(event, LogicalSide.SERVER);
        return event == null || !event.isCanceled();
    }

    /**
     * Server-side block-interaction hook, fired at the start of
     * {@code ServerPlayerGameMode#useItemOn} before the block interaction is
     * resolved. Runs on the server thread. Payload: the player, level, held
     * stack, interaction hand, and block hit result. Observation-only; the
     * original interaction result is unchanged.
     *
     * @param player    the interacting player
     * @param level     the level the interaction targets
     * @param stack     the held item stack
     * @param hand      the interaction hand
     * @param hitResult the block hit result
     */
    public static void blockInteract(Object player, Object level, Object stack, Object hand,
                                     Object hitResult)
    {
        if (player == null || level == null || stack == null || hand == null || hitResult == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().blockInteract(player, level, stack, hand,
                hitResult), LogicalSide.SERVER);
    }

    /**
     * Server-side item-use hook, fired at the start of
     * {@code ServerPlayerGameMode#useItem} before the item is used. Runs on
     * the server thread. Payload: the player, level, held stack, and
     * interaction hand. Observation-only; the original interaction result is
     * unchanged.
     *
     * @param player the using player
     * @param level  the level the item is used in
     * @param stack  the held item stack
     * @param hand   the interaction hand
     */
    public static void itemUse(Object player, Object level, Object stack, Object hand)
    {
        if (player == null || level == null || stack == null || hand == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().itemUse(player, level, stack, hand),
                LogicalSide.SERVER);
    }

    /**
     * Server-side living-entity-death hook, fired at the start of
     * {@code LivingEntity#die} before the entity begins dying. Runs on the
     * server thread. Payload: the dying entity and the damage source.
     * Observation-only; death handling is unchanged.
     *
     * @param entity the dying entity
     * @param source the damage source
     */
    public static void livingEntityDeath(Object entity, Object source)
    {
        if (entity == null || source == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().livingEntityDeath(entity, source),
                LogicalSide.SERVER);
    }

    /**
     * Server-side container-close hook, fired at the start of
     * {@code ServerPlayer#closeContainer} before the player's menu is closed.
     * Runs on the server thread. Payload: the player and the container menu
     * being closed, read from the player's {@code containerMenu} field. The
     * hook is skipped when that field is unavailable, is null, or still
     * points at the player's own {@code inventoryMenu}. Observation-only; the
     * close is unchanged.
     *
     * @param player the player closing a container
     */
    public static void containerClose(Object player)
    {
        if (player == null)
        {
            return;
        }
        MenuAccessor accessor = MENU_ACCESSORS.get(player.getClass());
        Object containerMenu = accessor.readContainerMenu(player);
        Object inventoryMenu = accessor.readInventoryMenu(player);
        if (containerMenu == null || inventoryMenu == null || containerMenu == inventoryMenu)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().containerClose(player, containerMenu),
                LogicalSide.SERVER);
    }

    /**
     * Server-side anvil-update hook, fired at the start of
     * {@code AnvilMenu#createResult} before the anvil result slot is
     * recomputed. Runs on the server thread. Payload: the anvil menu.
     * Observation-only; the recomputed result is unchanged.
     *
     * @param menu the anvil menu updating its result
     */
    public static void anvilUpdate(Object menu)
    {
        if (menu == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().anvilUpdate(menu), LogicalSide.SERVER);
    }

    /**
     * Server-side enchant hook, fired at the start of
     * {@code EnchantmentMenu#clickMenuButton} before the selected enchantment
     * button is applied. Runs on the server thread. Payload: the enchanting
     * menu and the clicking player. Observation-only; the button result is
     * unchanged.
     *
     * @param menu   the enchanting menu
     * @param player the player clicking the enchantment button
     */
    public static void enchant(Object menu, Object player)
    {
        if (menu == null || player == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().enchant(menu, player), LogicalSide.SERVER);
    }

    /**
     * Server-side player-teleport hook, fired at the start of
     * {@code ServerPlayer#teleport} before the teleport transition is
     * applied. Runs on the server thread. Payload: the player and the
     * teleport transition. Observation-only; the returned player is
     * unchanged.
     *
     * @param player     the teleporting player
     * @param transition the teleport transition
     */
    public static void playerTeleport(Object player, Object transition)
    {
        if (player == null || transition == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().playerTeleport(player, transition),
                LogicalSide.SERVER);
    }

    /**
     * Server-side respawn-target result hook, fired at the start of
     * {@code ServerPlayer#findRespawnPositionAndUseSpawnBlock} before the game
     * resolves the respawn position. Runs on the server thread. Payload: the
     * respawning player and the post-teleport transition, which may be null.
     * Result: a non-null returned transition replaces the game's resolved
     * transition; a null return means "no override" and the original method
     * body runs.
     *
     * @param player       the respawning player
     * @param postTeleport the post-teleport transition, possibly null
     * @return the replacement transition, or {@code null} to keep the
     *         original resolution
     */
    public static Object playerRespawnTarget(Object player, Object postTeleport)
    {
        if (player == null)
        {
            return null;
        }
        PlayerRespawnTargetEvent event = GameEventFactoryHolder.hookEvents()
                .playerRespawnTarget(player, postTeleport);
        post(event, LogicalSide.SERVER);
        return checkedResult(event, "net.minecraft.world.level.portal.TeleportTransition", "PlayerRespawnTargetEvent");
    }

    /**
     * Server-side player-death observation hook, fired before
     * {@code ServerPlayer#die} runs.
     *
     * @param player the dying player
     * @param source the damage source
     */
    public static void playerDeath(Object player, Object source)
    {
        if (player == null || source == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().playerDeath(player, source), LogicalSide.SERVER);
    }

    /**
     * Server-side container-open observation hook, fired before
     * {@code ServerPlayer#openMenu} builds the menu.
     *
     * @param player       the player opening the menu
     * @param menuProvider the provider that builds the menu
     */
    public static void containerOpen(Object player, Object menuProvider)
    {
        if (player == null || menuProvider == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().containerOpen(player, menuProvider),
                LogicalSide.SERVER);
    }

    /**
     * Server-side respawn observation hook, fired when a player resolves a
     * respawn position.
     *
     * @param player the respawning player
     */
    public static void playerRespawn(Object player)
    {
        if (player == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().playerRespawn(player), LogicalSide.SERVER);
    }

    /**
     * Server-side loot-table observation hook, fired before a loot table
     * generates its items.
     *
     * @param lootTable the loot table
     * @param params    the loot parameters
     */
    public static void lootTable(Object lootTable, Object params)
    {
        if (lootTable == null || params == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().lootTable(lootTable, params), LogicalSide.SERVER);
    }

    public static void serverSaveStart(Object server)
    {
        if (server == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().serverSave(server, ServerSaveEvent.Phase.START),
                LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onServerSave(context, true));
    }

    public static void serverSaveFinished(Object server)
    {
        if (server == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().serverSave(server, ServerSaveEvent.Phase.FINISHED),
                LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onServerSave(context, false));
    }

    public static void levelSaveStart(Object level)
    {
        if (level == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().levelSave(level, LevelSaveEvent.Phase.START),
                LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onLevelSave(context, level, true));
    }

    public static void levelSaveFinished(Object level)
    {
        if (level == null)
        {
            return;
        }
        post(GameEventFactoryHolder.events().levelSave(level, LevelSaveEvent.Phase.FINISHED),
                LogicalSide.SERVER);
        forEachMod(LogicalSide.SERVER, (mod, context) -> mod.onLevelSave(context, level, false));
    }

    /**
     * Server-side entity-interaction hook, fired at the start of
     * {@code Player#interactOn} before the interaction is resolved.
     * Observation-only and not cancelable. The spec is installed on the
     * logical server, but {@code Player} is a shared class, so an integrated
     * client process can also route client-side calls through the injected
     * body; listeners should check the logical context before mutating shared
     * state.
     *
     * @param player the interacting player
     * @param entity the entity being interacted with
     * @param hand   the hand used for the interaction
     * @param hitVec the interaction position
     */
    public static void entityInteract(Object player, Object entity, Object hand, Object hitVec)
    {
        if (player == null || entity == null || hand == null || hitVec == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().entityInteract(player, entity, hand, hitVec),
                LogicalSide.SERVER);
    }

    /**
     * Server-side entity-attack hook, fired at the start of {@code Player#attack}.
     * The injected call uses the boolean result to gate the attack; a canceled
     * event returns {@code false} and skips the attack body. The spec is
     * installed on the logical server, but {@code Player} is a shared class,
     * so an integrated client process can also route client-side calls through
     * the injected body.
     *
     * @param player the attacking player
     * @param target the entity being attacked
     * @return {@code true} when the attack may proceed
     */
    public static boolean entityAttack(Object player, Object target)
    {
        if (player == null || target == null)
        {
            return true;
        }
        EntityAttackEvent event = GameEventFactoryHolder.hookEvents().entityAttack(player, target);
        post(event, LogicalSide.SERVER);
        return event == null || !event.isCanceled();
    }

    /**
     * Server-side block-break hook, fired at the start of
     * {@code ServerPlayerGameMode#destroyBlock}. The player payload is read
     * from the game mode's {@code player} field. When that field is
     * unavailable the hook logs once and lets the break proceed. A canceled
     * event returns {@code false} and skips the block break.
     *
     * @param gameMode the server player game mode performing the break
     * @param pos      the block position being broken
     * @return {@code true} when the block break may proceed
     */
    public static boolean blockBreak(Object gameMode, Object pos)
    {
        if (gameMode == null || pos == null)
        {
            return true;
        }
        Object player = readPlayer(gameMode);
        if (player == null)
        {
            if (BLOCK_BREAK_SKIP_LOGGED.compareAndSet(false, true))
            {
                LifecycleHooks.logLine("[weaver] GAME HOOK BLOCK BREAK SKIPPED"
                        + " reason=player-unavailable class=" + gameMode.getClass().getName());
            }
            return true;
        }
        BlockBreakEvent event = GameEventFactoryHolder.hookEvents().blockBreak(player, pos);
        post(event, LogicalSide.SERVER);
        return event == null || !event.isCanceled();
    }

    /**
     * Server-side block-place result hook, fired at the start of
     * {@code BlockItem#place}. A listener may set a non-null result override;
     * the injected call then returns it and skips the vanilla placement body.
     * A {@code null} return means no override and the original body runs.
     * {@code BlockItem} is a shared class, so an integrated client process can
     * also route client-side calls through the injected body.
     *
     * @param placeContext the placement context
     * @return the replacement interaction result, or {@code null} to keep the
     *         original placement
     */
    public static Object blockPlace(Object placeContext)
    {
        if (placeContext == null)
        {
            return null;
        }
        BlockPlaceEvent event = GameEventFactoryHolder.hookEvents().blockPlace(placeContext);
        post(event, LogicalSide.SERVER);
        return checkedResult(event, "net.minecraft.world.InteractionResult", "BlockPlaceEvent");
    }

    /**
     * Reads a listener-supplied result and checks it has the type the injected
     * code returns. A wrong type would throw ClassCastException inside vanilla
     * (on the server thread); it is reported and ignored instead.
     */
    private static Object checkedResult(Object event, String expectedType, String eventName)
    {
        if (event == null)
        {
            return null;
        }
        Object result;
        try
        {
            result = event instanceof PlayerRespawnTargetEvent respawn ? respawn.respawnTarget()
                    : ((BlockPlaceEvent) event).resultOverride();
        }
        catch (RuntimeException failure)
        {
            LifecycleHooks.logLine("[weaver] GAME HOOK RESULT IGNORED event=" + eventName
                    + " reason=" + failure);
            return null;
        }
        if (result == null || isA(result.getClass(), expectedType) || !gameTypeExists(expectedType, result))
        {
            return result;
        }
        LifecycleHooks.logLine("[weaver] GAME HOOK RESULT IGNORED event=" + eventName
                + " reason=wrong-type expected=" + expectedType + " got=" + result.getClass().getName());
        return null;
    }

    private static boolean gameTypeExists(String name, Object result)
    {
        try
        {
            Class.forName(name, false, result.getClass().getClassLoader());
            return true;
        }
        catch (ClassNotFoundException | LinkageError missing)
        {
            return false;
        }
    }

    private static boolean isA(Class<?> type, String name)
    {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
        {
            if (current.getName().equals(name))
            {
                return true;
            }
            for (Class<?> implemented : current.getInterfaces())
            {
                if (isA(implemented, name))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Server-side item-use-finish hook, fired at the start of
     * {@code LivingEntity#completeUsingItem}. Observation-only. The spec
     * instruments the base class body only; a subclass override that does not
     * call {@code super} bypasses the hook.
     *
     * @param livingEntity the living entity finishing the item use
     */
    public static void itemUseFinish(Object livingEntity)
    {
        if (livingEntity == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().itemUseFinish(livingEntity), LogicalSide.SERVER);
    }

    /**
     * Server-side item-release hook, fired at the start of
     * {@code LivingEntity#releaseUsingItem}. Observation-only. The spec
     * instruments the base class body only; a subclass override that does not
     * call {@code super} bypasses the hook.
     *
     * @param livingEntity the living entity releasing the item
     */
    public static void itemRelease(Object livingEntity)
    {
        if (livingEntity == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().itemRelease(livingEntity), LogicalSide.SERVER);
    }

    /**
     * Server-side projectile-launch hook, fired at the start of
     * {@code Projectile#shoot(DDDFF)V}. Observation-only. The numeric launch
     * coordinates are not exposed because the injected call can pass only
     * reference parameters; read the projectile's position and delta movement
     * instead. The spec instruments the base class body only.
     *
     * @param projectile the projectile being launched
     */
    public static void projectileLaunch(Object projectile)
    {
        if (projectile == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().projectileLaunch(projectile),
                LogicalSide.SERVER);
    }

    /**
     * Client-side local-player pre-tick hook, fired at the start of
     * {@code LocalPlayer#tick}. Observation-only and event-only. This is the
     * client counterpart of {@link #playerTickEnter(Object)} and must not be
     * confused with the server-gated {@code PlayerTickEvent}.
     *
     * @param player the ticking local player
     */
    public static void clientPlayerTickEnter(Object player)
    {
        if (player == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().clientPlayerTickPre(player), LogicalSide.CLIENT);
    }

    /**
     * Client-side local-player post-tick hook, fired on normal exit from
     * {@code LocalPlayer#tick}. Observation-only and event-only.
     *
     * @param player the ticking local player
     */
    public static void clientPlayerTickExit(Object player)
    {
        if (player == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().clientPlayerTickPost(player), LogicalSide.CLIENT);
    }

    /**
     * Server-side entity-tick hook, fired at the start of
     * {@code ServerLevel#tickNonPassenger} before the entity's own tick runs.
     * Passenger entities use a different path and do not fire this event.
     *
     * @param level  the server level ticking the entity
     * @param entity the ticking entity
     */
    public static void serverEntityTick(Object level, Object entity)
    {
        if (level == null || entity == null)
        {
            return;
        }
        EntityTickEvent event = GameEventFactoryHolder.hookEvents()
                .entityTick(level, entity, Side.SERVER);
        post(event, LogicalSide.SERVER);
    }

    /**
     * Client-side entity-tick hook, fired at the start of
     * {@code ClientLevel#tickNonPassenger} before the entity's own tick runs.
     * Passenger entities use a different path and do not fire this event.
     *
     * @param level  the client level ticking the entity
     * @param entity the ticking entity
     */
    public static void clientEntityTick(Object level, Object entity)
    {
        if (level == null || entity == null)
        {
            return;
        }
        EntityTickEvent event = GameEventFactoryHolder.hookEvents()
                .entityTick(level, entity, Side.CLIENT);
        post(event, LogicalSide.CLIENT);
    }

    /**
     * Server-side chunk-load hook, fired when
     * {@code ServerLevel#startTickingChunk} moves a chunk into the ticking
     * state. This is not a per-tick event. Observation-only.
     *
     * @param level the server level that started ticking the chunk
     * @param chunk the chunk that started ticking
     */
    public static void chunkLoad(Object level, Object chunk)
    {
        if (level == null || chunk == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().chunkLoad(level, chunk), LogicalSide.SERVER);
    }

    /**
     * Server-side chunk-unload hook, fired at the start of
     * {@code ServerLevel#unload}. Observation-only.
     *
     * @param level the server level unloading the chunk
     * @param chunk the chunk being unloaded
     */
    public static void chunkUnload(Object level, Object chunk)
    {
        if (level == null || chunk == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().chunkUnload(level, chunk), LogicalSide.SERVER);
    }

    /**
     * Server-side entity-removal hook, fired at the start of
     * {@code Entity#remove}. Observation-only. The spec is installed on the
     * logical server, but {@code Entity} is a shared class, so an integrated
     * client process can also route client-side removals through the injected
     * body; listeners should check the logical context.
     *
     * @param entity the entity being removed
     * @param reason the removal reason
     */
    public static void entityRemoval(Object entity, Object reason)
    {
        if (entity == null || reason == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().entityRemoval(entity, reason),
                LogicalSide.SERVER);
    }

    /**
     * Server-side container-closed hook, fired at the start of
     * {@code ServerPlayer#doCloseContainer}, before the container menu is
     * removed. This is the completion path and is distinct from
     * {@link #containerClose(Object)}. The hook is skipped when no
     * non-inventory menu is open.
     *
     * @param player the player whose container menu is closing
     */
    public static void containerClosed(Object player)
    {
        if (player == null)
        {
            return;
        }
        MenuAccessor accessor = MENU_ACCESSORS.get(player.getClass());
        Object containerMenu = accessor.readContainerMenu(player);
        Object inventoryMenu = accessor.readInventoryMenu(player);
        if (containerMenu == null || inventoryMenu == null || containerMenu == inventoryMenu)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().containerClosed(player, containerMenu),
                LogicalSide.SERVER);
    }

    /**
     * Server-side block-entity-load hook, fired at the start of
     * {@code BlockEntity#loadWithComponents}. Observation-only. The spec
     * instruments the base class body only.
     *
     * @param blockEntity the block entity loading its components
     */
    public static void blockEntityLoad(Object blockEntity)
    {
        if (blockEntity == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().blockEntityLoad(blockEntity),
                LogicalSide.SERVER);
    }

    /**
     * Server-side block-entity-save hook, fired at the start of
     * {@code BlockEntity#saveWithFullMetadata(ValueOutput)}. Observation-only.
     * The spec instruments the base class body only; an override that does not
     * call {@code super} bypasses the hook.
     *
     * @param blockEntity the block entity saving its metadata
     */
    public static void blockEntitySave(Object blockEntity)
    {
        if (blockEntity == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().blockEntitySave(blockEntity),
                LogicalSide.SERVER);
    }

    /**
     * Server-side entity-save hook, fired at the start of
     * {@code Entity#saveWithoutId(ValueOutput)}. The concrete wrapper is
     * instrumented because {@code Entity#addAdditionalSaveData(ValueOutput)} is
     * abstract in 26.3. The event is posted before Weaver persists capability
     * values, so listeners can adjust them. Observation-only.
     *
     * @param entity the entity saving its additional data
     * @param output the game value output
     */
    public static void entitySave(Object entity, Object output)
    {
        if (entity == null || output == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents().entitySave(entity), LogicalSide.SERVER);
        CapabilityPersistence.saveEntity(entity, output);
    }

    /**
     * Server-side entity-load hook, fired at the start of
     * {@code Entity#load(ValueInput)}. The concrete wrapper is instrumented
     * because {@code Entity#readAdditionalSaveData(ValueInput)} is abstract in
     * 26.3. Weaver restores capability values before the event is posted, so
     * listeners observe the restored state. Observation-only.
     *
     * @param entity the entity loading its additional data
     * @param input  the game value input
     */
    public static void entityLoad(Object entity, Object input)
    {
        if (entity == null || input == null)
        {
            return;
        }
        CapabilityPersistence.loadEntity(entity, input);
        post(GameEventFactoryHolder.hookEvents().entityLoad(entity), LogicalSide.SERVER);
    }

    /**
     * Server-side block-entity capability-save hook, fired at the start of
     * {@code BlockEntity#saveWithFullMetadata(ValueOutput)} by a priority-10
     * companion spec. It runs after the {@code BlockEntitySaveEvent} entry
     * call and writes the Weaver capability root.
     *
     * @param blockEntity the block entity saving its metadata
     * @param output      the game value output
     */
    public static void blockEntityPersistenceSave(Object blockEntity, Object output)
    {
        if (blockEntity == null || output == null)
        {
            return;
        }
        CapabilityPersistence.saveBlockEntity(blockEntity, output);
    }

    /**
     * Server-side block-entity capability-load hook, fired at the start of
     * {@code BlockEntity#loadWithComponents(ValueInput)} before the
     * observation event. It reads the Weaver capability root.
     *
     * @param blockEntity the block entity loading its components
     * @param input       the game value input
     */
    public static void blockEntityPersistenceLoad(Object blockEntity, Object input)
    {
        if (blockEntity == null || input == null)
        {
            return;
        }
        CapabilityPersistence.loadBlockEntity(blockEntity, input);
    }

    /**
     * Server-side player-respawn-action hook, fired at the start of
     * {@code PlayerList#respawn}, before the new server player is created.
     * Entry-only: the event exposes the old player and the removal reason but
     * cannot observe or replace the returned player. Observation-only.
     *
     * @param playerList the player list performing the respawn
     * @param oldPlayer  the player instance being replaced
     * @param reason     the removal reason for the old player
     */
    public static void playerRespawnAction(Object playerList, Object oldPlayer, Object reason)
    {
        if (playerList == null || oldPlayer == null || reason == null)
        {
            return;
        }
        post(GameEventFactoryHolder.hookEvents()
                .playerRespawnAction(playerList, oldPlayer, reason), LogicalSide.SERVER);
    }

    private static void tickLifecycle(boolean client)
    {
        try
        {
            if (client)
            {
                LifecycleHooks.onClientTick();
            }
            else
            {
                LifecycleHooks.onServerTick();
            }
        }
        catch (Throwable failure)
        {
            LifecycleHooks.logLine("[weaver] GAME TICK LIFECYCLE FAILED side="
                    + (client ? "client" : "server") + " reason=" + failure);
        }
    }

    private static void enterServerCycle(Object server)
    {
        if (CURRENT_SERVER.get().get() == server)
        {
            return;
        }
        synchronized (SERVER_LOCK)
        {
            if (CURRENT_SERVER.get().get() == server)
            {
                return;
            }
            CURRENT_SERVER.set(new java.lang.ref.WeakReference<>(server));
            SERVER_STARTING.set(false);
            SERVER_STARTED.set(false);
            SERVER_STOPPING.set(false);
            SERVER_STOPPED.set(false);
        }
    }

    private static Object readLevel(Object minecraft)
    {
        try
        {
            return LEVEL_ACCESSORS.get(minecraft.getClass()).read(minecraft);
        }
        catch (Throwable failure)
        {
            return null;
        }
    }

    private static Object readScreen(Object minecraft)
    {
        try
        {
            return SCREEN_ACCESSORS.get(minecraft.getClass()).read(minecraft);
        }
        catch (Throwable failure)
        {
            return null;
        }
    }

    private static Object readPlayer(Object gameMode)
    {
        try
        {
            return PLAYER_ACCESSORS.get(gameMode.getClass()).read(gameMode);
        }
        catch (Throwable failure)
        {
            return null;
        }
    }

    private static void post(Event event, LogicalSide logicalSide)
    {
        if (event == null)
        {
            return;
        }
        try
        {
            event.setLogicalSide(logicalSide);
            EventBus.GAME.post(event);
        }
        catch (Throwable failure)
        {
            net.weaver.api.event.FailureThrottle.Decision decision = net.weaver.api.event.FailureThrottle.record(
                    "game-event|" + event.getName() + "|" + failure.getClass().getName());
            if (decision.report())
            {
                LifecycleHooks.logLine("[weaver] GAME EVENT FAILED event=" + event.getName()
                        + " reason=" + failure + decision.suffix());
            }
        }
    }

    private static void forEachMod(LogicalSide logicalSide, ModCall call)
    {
        List<LifecycleHooks.LoadedMod> mods;
        try
        {
            mods = LifecycleHooks.mods();
        }
        catch (Throwable failure)
        {
            LifecycleHooks.logLine("[weaver] GAME HOOK MOD LOOKUP FAILED reason=" + failure);
            return;
        }
        for (LifecycleHooks.LoadedMod loaded : mods)
        {
            WeaverMod mod = loaded.mod();
            if (mod == null)
            {
                continue;
            }
            try
            {
                call.accept(mod, loaded.context(logicalSide));
            }
            catch (Throwable failure)
            {
                net.weaver.api.event.FailureThrottle.Decision decision = net.weaver.api.event.FailureThrottle.record(
                        "game-hook|" + loaded.id() + "|" + failure.getClass().getName());
                if (decision.report())
                {
                    LifecycleHooks.logLine("[weaver] GAME HOOK FAILED id=" + loaded.id()
                            + " reason=" + failure + decision.suffix());
                }
            }
        }
    }

    private static Field findField(Class<?> type, String name)
    {
        Class<?> current = type;
        while (current != null && current != Object.class)
        {
            try
            {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException missing)
            {
                current = current.getSuperclass();
            }
            catch (Throwable failure)
            {
                return null;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface ModCall
    {
        void accept(WeaverMod mod, ModContext context);
    }

    private static final class LevelAccessor
    {
        private static final LevelAccessor MISSING = new LevelAccessor(null);

        private final Field level;

        private LevelAccessor(Field level)
        {
            this.level = level;
        }

        private Object read(Object target)
        {
            if (level == null || target == null)
            {
                return null;
            }
            try
            {
                return level.get(target);
            }
            catch (Throwable failure)
            {
                return null;
            }
        }
    }

    private static final class ScreenAccessor
    {
        private static final ScreenAccessor MISSING = new ScreenAccessor(null, null);

        private final Field screen;
        private final Field parent;

        private ScreenAccessor(Field screen, Field parent)
        {
            this.screen = screen;
            this.parent = parent;
        }

        private Object read(Object target)
        {
            if (screen == null || target == null)
            {
                return null;
            }
            try
            {
                Object holder = parent == null ? target : parent.get(target);
                return holder == null ? null : screen.get(holder);
            }
            catch (Throwable failure)
            {
                return null;
            }
        }
    }

    private static final class PlayerAccessor
    {
        private static final PlayerAccessor MISSING = new PlayerAccessor(null);

        private final Field player;

        private PlayerAccessor(Field player)
        {
            this.player = player;
        }

        private Object read(Object target)
        {
            if (player == null || target == null)
            {
                return null;
            }
            try
            {
                return player.get(target);
            }
            catch (Throwable failure)
            {
                return null;
            }
        }
    }

    private static final class MenuAccessor
    {
        private static final MenuAccessor MISSING = new MenuAccessor(null, null);

        private final Field containerMenu;
        private final Field inventoryMenu;

        private MenuAccessor(Field containerMenu, Field inventoryMenu)
        {
            this.containerMenu = containerMenu;
            this.inventoryMenu = inventoryMenu;
        }

        private Object readContainerMenu(Object target)
        {
            return read(containerMenu, target);
        }

        private Object readInventoryMenu(Object target)
        {
            return read(inventoryMenu, target);
        }

        private static Object read(Field field, Object target)
        {
            if (field == null || target == null)
            {
                return null;
            }
            try
            {
                return field.get(target);
            }
            catch (Throwable failure)
            {
                return null;
            }
        }
    }
}
