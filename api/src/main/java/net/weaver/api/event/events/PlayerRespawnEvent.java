package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is resolving a respawn position.
 *
 * <p><strong>This event is a respawn-position lookup, not a respawn.</strong>
 * It is fired on entry to
 * {@code ServerPlayer.findRespawnPositionAndUseSpawnBlock}, which resolves a
 * respawn position. It is not the respawn action and does not mean the player
 * has respawned.
 *
 * <p>The lookup may run more than once for the same player, so listeners can
 * receive this event multiple times for repeated lookups. The event is posted
 * on the server thread. Observation-only; the base payload is a game object
 * carried as {@link Object}.
 */
public class PlayerRespawnEvent extends Event {

    private final Object player;

    /**
     * Creates a player-respawn event.
     *
     * @param player the respawning player
     */
    public PlayerRespawnEvent(Object player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    /** The respawning player. */
    public Object player() {
        return player;
    }
}
