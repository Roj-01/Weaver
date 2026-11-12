package net.weaver.api.game.event.living;

import net.minecraft.world.entity.Mob;
import net.weaver.api.event.Event;

/**
 * Fired when natural spawning asks how many of a mob may spawn together in one pack
 * ({@code Mob#getMaxSpawnClusterSize}, from {@code NaturalSpawner#spawnCategoryForPosition}).
 * The size can be changed. Server side.
 */
public final class SpawnClusterSizeEvent extends Event {
    private final Mob mob;
    private int size;

    public SpawnClusterSizeEvent(Mob mob, int size) {
        super(false);
        this.mob = mob;
        this.size = size;
    }

    /** The mob about to spawn. */
    public Mob mob() {
        return mob;
    }

    /** The largest pack size. */
    public int size() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
