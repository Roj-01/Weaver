package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.weaver.api.event.Event;

/**
 * Fired after a player's client stops receiving updates about an entity
 * ({@code ServerEntity#removePairing}): the entity left view range or was
 * removed.
 */
public final class PlayerStopTrackingEvent extends Event {
    private final ServerPlayer player;
    private final Entity target;

    public PlayerStopTrackingEvent(ServerPlayer player, Entity target) {
        this.player = player;
        this.target = target;
    }

    public ServerPlayer player() {
        return player;
    }

    /** The entity that is no longer tracked. */
    public Entity target() {
        return target;
    }
}
