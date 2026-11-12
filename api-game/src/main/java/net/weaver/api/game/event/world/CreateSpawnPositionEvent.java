package net.weaver.api.game.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import net.weaver.api.event.Event;

/**
 * Fired once when a new world is created and the server picks its spawn
 * point ({@code MinecraftServer#setInitialSpawn}). Cancelable: a canceled
 * event skips vanilla's search (and the bonus chest), so the listener must
 * call {@code levelData().setSpawn(...)} itself. Listeners must be registered
 * before the world is created, during mod setup.
 */
public final class CreateSpawnPositionEvent extends Event {
    private final ServerLevel level;
    private final ServerLevelData levelData;

    public CreateSpawnPositionEvent(ServerLevel level, ServerLevelData levelData) {
        super(true);
        this.level = level;
        this.levelData = levelData;
    }

    public ServerLevel level() {
        return level;
    }

    public ServerLevelData levelData() {
        return levelData;
    }
}
