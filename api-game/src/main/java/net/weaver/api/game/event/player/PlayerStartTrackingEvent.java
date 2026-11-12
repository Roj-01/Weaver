package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.weaver.api.event.Event;

/**
 * Fired after a player's client starts receiving updates about an entity
 * ({@code ServerEntity#addPairing}): the entity came into view range. Use it
 * to send mod data about the entity to that player.
 */
public final class PlayerStartTrackingEvent extends Event {
    private final ServerPlayer player;
    private final Entity target;

    public PlayerStartTrackingEvent(ServerPlayer player, Entity target) {
        this.player = player;
        this.target = target;
    }

    public ServerPlayer player() {
        return player;
    }

    /** The tracked entity. */
    public Entity target() {
        return target;
    }
}
