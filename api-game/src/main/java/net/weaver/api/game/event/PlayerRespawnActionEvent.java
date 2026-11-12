package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity.RemovalReason;

/**
 * Typed variant of {@link net.weaver.api.event.events.PlayerRespawnActionEvent}.
 *
 * <p>Fired at method entry to {@code PlayerList#respawn}, before the new
 * server player is created. This is the respawn action, distinct from
 * {@link PlayerRespawnEvent}. Observation-only and not cancelable. The
 * payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class PlayerRespawnActionEvent
        extends net.weaver.api.event.events.PlayerRespawnActionEvent {

    /**
     * Creates a typed player-respawn-action event.
     *
     * @param playerList the player list performing the respawn
     * @param oldPlayer  the player instance being replaced
     * @param reason     the removal reason for the old player
     */
    public PlayerRespawnActionEvent(Object playerList, Object oldPlayer, Object reason) {
        super(playerList, oldPlayer, reason);
    }

    @Override
    public PlayerList playerList() {
        return (PlayerList) super.playerList();
    }

    @Override
    public ServerPlayer oldPlayer() {
        return (ServerPlayer) super.oldPlayer();
    }

    @Override
    public RemovalReason reason() {
        return (RemovalReason) super.reason();
    }
}
