package net.weaver.api.game;

import net.weaver.api.event.Side;
import net.weaver.api.event.events.ChatEvent;
import net.weaver.api.event.events.CommandRegisterEvent;
import net.weaver.api.event.events.ContainerOpenEvent;
import net.weaver.api.event.events.EntityHurtEvent;
import net.weaver.api.event.events.EntityJoinLevelEvent;
import net.weaver.api.event.events.LevelLoadEvent;
import net.weaver.api.event.events.LevelSaveEvent;
import net.weaver.api.event.events.LevelUnloadEvent;
import net.weaver.api.event.events.LootTableEvent;
import net.weaver.api.event.events.PlayerDeathEvent;
import net.weaver.api.event.events.PlayerJoinEvent;
import net.weaver.api.event.events.PlayerLeaveEvent;
import net.weaver.api.event.events.PlayerLoginEvent;
import net.weaver.api.event.events.PlayerLogoutEvent;
import net.weaver.api.event.events.PlayerRespawnEvent;
import net.weaver.api.event.events.ResourceReloadEvent;
import net.weaver.api.event.events.ScreenCloseEvent;
import net.weaver.api.event.events.ScreenOpenEvent;
import net.weaver.api.event.events.ServerSaveEvent;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.event.events.ServerStartingEvent;
import net.weaver.api.event.events.ServerStoppedEvent;
import net.weaver.api.event.events.ServerStoppingEvent;
import net.weaver.api.spi.GameEventFactory;

/**
 * Game-typed implementation of {@link GameEventFactory}.
 *
 * <p>Every method creates the typed subclass from
 * {@code net.weaver.api.game.event}, which narrows the accessors to game types.
 * The factory is stateless and can be shared by every thread that posts game
 * events. This class references game classes and is therefore only loadable when
 * the game classpath is present.
 */
public final class TypedGameEventFactory implements GameEventFactory {

    /** Creates the typed factory. */
    public TypedGameEventFactory() {
    }

    /** {@inheritDoc} */
    @Override
    public PlayerLoginEvent playerLogin(Object playerList, Object player) {
        return new net.weaver.api.game.event.PlayerLoginEvent(playerList, player);
    }

    /** {@inheritDoc} */
    @Override
    public PlayerJoinEvent playerJoin(Object playerList, Object player) {
        return new net.weaver.api.game.event.PlayerJoinEvent(playerList, player);
    }

    /** {@inheritDoc} */
    @Override
    public PlayerLogoutEvent playerLogout(Object playerList, Object player) {
        return new net.weaver.api.game.event.PlayerLogoutEvent(playerList, player);
    }

    /** {@inheritDoc} */
    @Override
    public PlayerLeaveEvent playerLeave(Object playerList, Object player) {
        return new net.weaver.api.game.event.PlayerLeaveEvent(playerList, player);
    }

    /** {@inheritDoc} */
    @Override
    public LevelLoadEvent levelLoad(Object level) {
        return new net.weaver.api.game.event.LevelLoadEvent(level);
    }

    /** {@inheritDoc} */
    @Override
    public LevelUnloadEvent levelUnload(Object level) {
        return new net.weaver.api.game.event.LevelUnloadEvent(level);
    }

    /** {@inheritDoc} */
    @Override
    public EntityJoinLevelEvent entityJoinLevel(Object level, Object entity) {
        return new net.weaver.api.game.event.EntityJoinLevelEvent(level, entity);
    }

    /** {@inheritDoc} */
    @Override
    public ChatEvent chat(Object playerList, Object message, Object sender) {
        return new net.weaver.api.game.event.ChatEvent(playerList, message, sender);
    }

    /** {@inheritDoc} */
    @Override
    public ScreenOpenEvent screenOpen(Object minecraft, Object screen) {
        return new net.weaver.api.game.event.ScreenOpenEvent(minecraft, screen);
    }

    /** {@inheritDoc} */
    @Override
    public ScreenCloseEvent screenClose(Object minecraft, Object screen) {
        return new net.weaver.api.game.event.ScreenCloseEvent(minecraft, screen);
    }

    /** {@inheritDoc} */
    @Override
    public CommandRegisterEvent commandRegister(Object commands) {
        return new net.weaver.api.game.event.CommandRegisterEvent(commands);
    }

    /** {@inheritDoc} */
    @Override
    public ResourceReloadEvent resourceReload(Object source, Side side,
                                              ResourceReloadEvent.Phase phase) {
        return new net.weaver.api.game.event.ResourceReloadEvent(source, side, phase);
    }

    /** {@inheritDoc} */
    @Override
    public ServerStartingEvent serverStarting(Object server) {
        return new net.weaver.api.game.event.ServerStartingEvent(server);
    }

    /** {@inheritDoc} */
    @Override
    public ServerStartedEvent serverStarted(Object server) {
        return new net.weaver.api.game.event.ServerStartedEvent(server);
    }

    /** {@inheritDoc} */
    @Override
    public ServerStoppingEvent serverStopping(Object server) {
        return new net.weaver.api.game.event.ServerStoppingEvent(server);
    }

    /** {@inheritDoc} */
    @Override
    public ServerStoppedEvent serverStopped(Object server) {
        return new net.weaver.api.game.event.ServerStoppedEvent(server);
    }

    /** {@inheritDoc} */
    @Override
    public ServerSaveEvent serverSave(Object server, ServerSaveEvent.Phase phase) {
        return new net.weaver.api.game.event.ServerSaveEvent(server, phase);
    }

    /** {@inheritDoc} */
    @Override
    public LevelSaveEvent levelSave(Object level, LevelSaveEvent.Phase phase) {
        return new net.weaver.api.game.event.LevelSaveEvent(level, phase);
    }

    /** {@inheritDoc} */
    @Override
    public EntityHurtEvent entityHurt(Object level, Object entity, Object source) {
        return new net.weaver.api.game.event.EntityHurtEvent(level, entity, source);
    }

    /** {@inheritDoc} */
    @Override
    public PlayerDeathEvent playerDeath(Object player, Object source) {
        return new net.weaver.api.game.event.PlayerDeathEvent(player, source);
    }

    /** {@inheritDoc} */
    @Override
    public ContainerOpenEvent containerOpen(Object player, Object menuProvider) {
        return new net.weaver.api.game.event.ContainerOpenEvent(player, menuProvider);
    }

    /**
     * Creates the typed respawn-position lookup event.
     *
     * <p>The base event is fired by
     * {@code ServerPlayer.findRespawnPositionAndUseSpawnBlock} while it resolves
     * a respawn position; it is not the respawn action.
     */
    @Override
    public PlayerRespawnEvent playerRespawn(Object player) {
        return new net.weaver.api.game.event.PlayerRespawnEvent(player);
    }

    /** {@inheritDoc} */
    @Override
    public LootTableEvent lootTable(Object lootTable, Object params) {
        return new net.weaver.api.game.event.LootTableEvent(lootTable, params);
    }
}
