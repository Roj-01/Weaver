package net.weaver.api.game.event.player;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.weaver.api.event.Event;

/**
 * Fired after a player moved to another dimension
 * ({@code ServerPlayer#teleport} into a different level).
 */
public final class PlayerChangedDimensionEvent extends Event {
    private final ServerPlayer player;
    private final ResourceKey<Level> from;
    private final ResourceKey<Level> to;

    public PlayerChangedDimensionEvent(ServerPlayer player, ResourceKey<Level> from,
                                       ResourceKey<Level> to) {
        this.player = player;
        this.from = from;
        this.to = to;
    }

    public ServerPlayer player() {
        return player;
    }

    public ResourceKey<Level> from() {
        return from;
    }

    public ResourceKey<Level> to() {
        return to;
    }
}
