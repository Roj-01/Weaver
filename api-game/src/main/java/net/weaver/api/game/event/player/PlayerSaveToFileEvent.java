package net.weaver.api.game.event.player;

import java.io.File;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired after a player's data was written to disk
 * ({@code PlayerDataStorage#save}): periodic saves, logout, and server stop.
 * Use it to save mod data next to the player file
 * ({@code <playerDirectory>/<uuid>.<ext>}).
 */
public final class PlayerSaveToFileEvent extends Event {
    private final Player player;
    private final File playerDirectory;

    public PlayerSaveToFileEvent(Player player, File playerDirectory) {
        this.player = player;
        this.playerDirectory = playerDirectory;
    }

    public Player player() {
        return player;
    }

    /** The world's player data directory. */
    public File playerDirectory() {
        return playerDirectory;
    }

    /** The player's UUID as a string, the base name of the player file. */
    public String playerUuid() {
        return player.getStringUUID();
    }
}
