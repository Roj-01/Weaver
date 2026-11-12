package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is about to respawn.
 *
 * <p>Fired at method entry to {@code PlayerList#respawn}, before the new
 * server player is created. This is the respawn action, distinct from
 * {@link PlayerRespawnEvent}, which reports a respawn-position lookup. The
 * event is entry-only and cannot observe or replace the returned player;
 * {@link #oldPlayer()} is the player instance being replaced.
 * Observation-only and not cancelable.
 *
 * <p>The base payloads are game objects carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class PlayerRespawnActionEvent extends Event {

    private final Object playerList;
    private final Object oldPlayer;
    private final Object reason;

    /**
     * Creates a player-respawn-action event.
     *
     * @param playerList the player list performing the respawn
     * @param oldPlayer  the player instance being replaced
     * @param reason     the removal reason for the old player
     */
    public PlayerRespawnActionEvent(Object playerList, Object oldPlayer, Object reason) {
        this.playerList = Objects.requireNonNull(playerList, "playerList");
        this.oldPlayer = Objects.requireNonNull(oldPlayer, "oldPlayer");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** The player list performing the respawn. */
    public Object playerList() {
        return playerList;
    }

    /** The player instance being replaced. */
    public Object oldPlayer() {
        return oldPlayer;
    }

    /** The removal reason for the old player. */
    public Object reason() {
        return reason;
    }
}
