package net.weaver.api.game.event.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.weaver.api.event.Event;

/**
 * Fired once per level, the first time it runs its custom spawners
 * ({@code ServerLevel#tickCustomSpawners}: phantoms, patrols, cats, village
 * sieges, wandering traders). {@link #spawners()} is a mutable copy; add a
 * spawner to have it ticked with the others, or remove one to disable it.
 * Register listeners during mod setup; the event fires in the level's first
 * tick.
 */
public final class ModifyCustomSpawnersEvent extends Event {
    private final ServerLevel level;
    private final List<CustomSpawner> spawners;

    public ModifyCustomSpawnersEvent(ServerLevel level, List<CustomSpawner> spawners) {
        this.level = level;
        this.spawners = new ArrayList<>(spawners);
    }

    public ServerLevel level() {
        return level;
    }

    /** The spawners, mutable. */
    public List<CustomSpawner> spawners() {
        return spawners;
    }
}
