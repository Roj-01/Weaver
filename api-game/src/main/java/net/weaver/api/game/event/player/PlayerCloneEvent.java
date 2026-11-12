package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired after a new player object copied its state from the old one
 * ({@code ServerPlayer#restoreFrom}), on respawn after death or when returning
 * from the End. Copy mod data from {@link #original()} to {@link #player()} here.
 */
public final class PlayerCloneEvent extends Event {
    private final ServerPlayer player;
    private final ServerPlayer original;
    private final boolean wasDeath;

    public PlayerCloneEvent(ServerPlayer player, ServerPlayer original, boolean wasDeath) {
        this.player = player;
        this.original = original;
        this.wasDeath = wasDeath;
    }

    /** The new player object. */
    public ServerPlayer player() {
        return player;
    }

    /** The old player object the state was copied from. */
    public ServerPlayer original() {
        return original;
    }

    /** {@code true} for a respawn after death, {@code false} when returning from the End. */
    public boolean wasDeath() {
        return wasDeath;
    }
}
