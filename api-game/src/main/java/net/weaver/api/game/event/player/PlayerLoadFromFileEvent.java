package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired when a joining player's saved data has been read
 * ({@code PrepareSpawnTask}), before the player is placed in the world. Fires
 * for new players too, with nothing loaded. Use it to read mod data stored
 * next to the player file.
 */
public final class PlayerLoadFromFileEvent extends Event {
    private final ServerPlayer player;

    public PlayerLoadFromFileEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer player() {
        return player;
    }

    /** The player's UUID as a string, the base name of the player file. */
    public String playerUuid() {
        return player.getStringUUID();
    }
}
