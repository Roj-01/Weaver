package net.weaver.api.spi;

import net.weaver.api.event.Side;
import net.weaver.api.event.events.ChatEvent;
import net.weaver.api.event.events.ContainerOpenEvent;
import net.weaver.api.event.events.CommandRegisterEvent;
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

/**
 * Game-free event factory used when the typed {@code api-game} artifact is not
 * available.
 *
 * <p>Every method returns the plain event type declared in
 * {@code net.weaver.api.event.events}. The two cancelable hooks, entity join and
 * chat, create cancelable events so cancellation still works in fallback mode.
 * The observation events carry their payloads through the inherited
 * {@code Object} accessors; only the typed {@code api-game} factory exposes
 * game-typed accessors.
 */
public final class DefaultGameEventFactory implements GameEventFactory {

    /** Creates the shared fallback factory. */
    public DefaultGameEventFactory() {
    }

    @Override
    public PlayerLoginEvent playerLogin(Object playerList, Object player) {
        return new PlayerLoginEvent(playerList, player);
    }

    @Override
    public PlayerJoinEvent playerJoin(Object playerList, Object player) {
        return new PlayerJoinEvent(playerList, player);
    }

    @Override
    public PlayerLogoutEvent playerLogout(Object playerList, Object player) {
        return new PlayerLogoutEvent(playerList, player);
    }

    @Override
    public PlayerLeaveEvent playerLeave(Object playerList, Object player) {
        return new PlayerLeaveEvent(playerList, player);
    }

    @Override
    public LevelLoadEvent levelLoad(Object level) {
        return new LevelLoadEvent(level);
    }

    @Override
    public LevelUnloadEvent levelUnload(Object level) {
        return new LevelUnloadEvent(level);
    }

    @Override
    public EntityJoinLevelEvent entityJoinLevel(Object level, Object entity) {
        return new EntityJoinLevelEvent(level, entity, true);
    }

    @Override
    public ChatEvent chat(Object playerList, Object message, Object sender) {
        return new ChatEvent(playerList, message, sender, true);
    }

    @Override
    public ScreenOpenEvent screenOpen(Object minecraft, Object screen) {
        return new ScreenOpenEvent(minecraft, screen);
    }

    @Override
    public ScreenCloseEvent screenClose(Object minecraft, Object screen) {
        return new ScreenCloseEvent(minecraft, screen);
    }

    @Override
    public CommandRegisterEvent commandRegister(Object commands) {
        return new CommandRegisterEvent(commands);
    }

    @Override
    public ResourceReloadEvent resourceReload(Object source, Side side,
                                              ResourceReloadEvent.Phase phase) {
        return new ResourceReloadEvent(source, side, phase);
    }

    @Override
    public ServerStartingEvent serverStarting(Object server) {
        return new ServerStartingEvent(server);
    }

    @Override
    public ServerStartedEvent serverStarted(Object server) {
        return new ServerStartedEvent(server);
    }

    @Override
    public ServerStoppingEvent serverStopping(Object server) {
        return new ServerStoppingEvent(server);
    }

    @Override
    public ServerStoppedEvent serverStopped(Object server) {
        return new ServerStoppedEvent(server);
    }

    @Override
    public ServerSaveEvent serverSave(Object server, ServerSaveEvent.Phase phase) {
        return new ServerSaveEvent(server, phase);
    }

    @Override
    public LevelSaveEvent levelSave(Object level, LevelSaveEvent.Phase phase) {
        return new LevelSaveEvent(level, phase);
    }

    @Override
    public EntityHurtEvent entityHurt(Object level, Object entity, Object source) {
        return new EntityHurtEvent(level, entity, source);
    }

    @Override
    public PlayerDeathEvent playerDeath(Object player, Object source) {
        return new PlayerDeathEvent(player, source);
    }

    @Override
    public ContainerOpenEvent containerOpen(Object player, Object menuProvider) {
        return new ContainerOpenEvent(player, menuProvider);
    }

    @Override
    public PlayerRespawnEvent playerRespawn(Object player) {
        return new PlayerRespawnEvent(player);
    }

    @Override
    public LootTableEvent lootTable(Object lootTable, Object params) {
        return new LootTableEvent(lootTable, params);
    }
}
