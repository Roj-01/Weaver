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
 * Creates the event objects that the loader posts for built-in game hooks.
 *
 * <p>The API module cannot reference game classes, so every factory method
 * takes and returns game payloads as {@link Object}. The base implementation,
 * {@link DefaultGameEventFactory}, creates the plain event types declared in
 * {@code net.weaver.api.event.events}. The optional typed implementation lives
 * in the {@code api-game} artifact and creates the typed subclasses from
 * {@code net.weaver.api.game.event}, which expose covariant game-typed
 * accessors.
 *
 * <p>The loader installs one factory per process. Implementations must be
 * stateless and thread-safe: the same factory is called from client, server,
 * and integrated-server threads.
 *
 * @see DefaultGameEventFactory
 */
public interface GameEventFactory {

    /** Creates a login event. */
    PlayerLoginEvent playerLogin(Object playerList, Object player);

    /** Creates a join event. */
    PlayerJoinEvent playerJoin(Object playerList, Object player);

    /** Creates a logout event. */
    PlayerLogoutEvent playerLogout(Object playerList, Object player);

    /** Creates a leave event. */
    PlayerLeaveEvent playerLeave(Object playerList, Object player);

    /** Creates a level-load event. */
    LevelLoadEvent levelLoad(Object level);

    /** Creates a level-unload event. */
    LevelUnloadEvent levelUnload(Object level);

    /**
     * Creates a cancelable entity-join event. A listener may cancel it to stop
     * the entity from being added to the level.
     */
    EntityJoinLevelEvent entityJoinLevel(Object level, Object entity);

    /**
     * Creates a cancelable chat event. A listener may cancel it to stop the
     * message from being broadcast.
     */
    ChatEvent chat(Object playerList, Object message, Object sender);

    /** Creates a screen-open event. */
    ScreenOpenEvent screenOpen(Object minecraft, Object screen);

    /** Creates a screen-close event. */
    ScreenCloseEvent screenClose(Object minecraft, Object screen);

    /** Creates a command-registration event. */
    CommandRegisterEvent commandRegister(Object commands);

    /** Creates a resource-reload event for one phase. */
    ResourceReloadEvent resourceReload(Object source, Side side, ResourceReloadEvent.Phase phase);

    /** Creates a server-starting event. */
    ServerStartingEvent serverStarting(Object server);

    /** Creates a server-started event. */
    ServerStartedEvent serverStarted(Object server);

    /** Creates a server-stopping event. */
    ServerStoppingEvent serverStopping(Object server);

    /** Creates a server-stopped event. */
    ServerStoppedEvent serverStopped(Object server);

    /** Creates a server-save event for one phase. */
    ServerSaveEvent serverSave(Object server, ServerSaveEvent.Phase phase);

    /** Creates a level-save event for one phase. */
    LevelSaveEvent levelSave(Object level, LevelSaveEvent.Phase phase);

    /**
     * Creates a server-side entity-hurt observation event.
     *
     * <p>{@link DefaultGameEventFactory} creates the base
     * {@link EntityHurtEvent}; the typed factory creates the {@code api-game}
     * subclass with covariant accessors.
     */
    EntityHurtEvent entityHurt(Object level, Object entity, Object source);

    /** Creates a server-side player-death observation event. */
    PlayerDeathEvent playerDeath(Object player, Object source);

    /** Creates a server-side container-open observation event. */
    ContainerOpenEvent containerOpen(Object player, Object menuProvider);

    /** Creates a server-side respawn-position lookup event. */
    PlayerRespawnEvent playerRespawn(Object player);

    /** Creates a server-side loot-table-generation observation event. */
    LootTableEvent lootTable(Object lootTable, Object params);
}
