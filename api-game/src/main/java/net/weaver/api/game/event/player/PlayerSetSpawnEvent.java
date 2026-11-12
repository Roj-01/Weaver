package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired when a player's respawn point is about to change
 * ({@code ServerPlayer#setRespawnPosition}): sleeping in a bed, charging a
 * respawn anchor, the {@code /spawnpoint} command, or clearing the spawn on
 * death. Cancelable: a canceled event keeps the old respawn point.
 */
public final class PlayerSetSpawnEvent extends Event {
    private final ServerPlayer player;
    private final ServerPlayer.RespawnConfig newSpawn;

    public PlayerSetSpawnEvent(ServerPlayer player, ServerPlayer.RespawnConfig newSpawn) {
        super(true);
        this.player = player;
        this.newSpawn = newSpawn;
    }

    public ServerPlayer player() {
        return player;
    }

    /** The new respawn point, or {@code null} when it is being cleared. */
    public ServerPlayer.RespawnConfig newSpawn() {
        return newSpawn;
    }
}
