package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired on the server after a player has respawned: when
 * {@code PlayerList#respawn} returns, the new player object exists, has been
 * placed in its level and has its state copied from the old one (see
 * {@code PlayerCloneEvent}). Use this, not {@code PlayerRespawnEvent} (which
 * fires while the game is still looking for the respawn position, on the old
 * player), to act on the player that will play on.
 *
 * <p>Fired both after a death and when returning from the End
 * ({@link #endConquered()}). Not cancelable.</p>
 */
public final class PlayerRespawnedEvent extends Event {
    private final ServerPlayer player;
    private final boolean endConquered;

    public PlayerRespawnedEvent(ServerPlayer player, boolean endConquered) {
        this.player = player;
        this.endConquered = endConquered;
    }

    /** The new player object, already in its level. */
    public ServerPlayer player() {
        return player;
    }

    /** {@code true} when the player came back from the End rather than from a death. */
    public boolean endConquered() {
        return endConquered;
    }
}
