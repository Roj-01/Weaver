package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;

/**
 * Typed variant of {@link net.weaver.api.event.events.PlayerRespawnEvent}.
 *
 * <p><strong>This event is a respawn-position lookup, not a respawn.</strong>
 * The base event is fired on entry to
 * {@code ServerPlayer.findRespawnPositionAndUseSpawnBlock}, which resolves a
 * respawn position. It is not the respawn action and does not mean the player
 * has respawned.
 *
 * <p>The lookup may run more than once for the same player, so listeners can
 * receive this event multiple times for repeated lookups. The event is posted
 * on the server thread.
 *
 * <p>The player accessor is narrowed to {@link ServerPlayer}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class PlayerRespawnEvent extends net.weaver.api.event.events.PlayerRespawnEvent {

    /**
     * Creates a typed player-respawn event.
     *
     * @param player the respawning player
     */
    public PlayerRespawnEvent(Object player) {
        super(player);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }
}
