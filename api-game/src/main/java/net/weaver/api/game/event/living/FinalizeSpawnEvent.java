package net.weaver.api.game.event.living;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.weaver.api.event.Event;

/**
 * Fired when a newly spawned mob is about to be finalized: random equipment,
 * baby status, spawn-group data, and similar ({@code Mob#finalizeSpawn}, for
 * natural spawns, spawn eggs, spawners, commands, and structures). Cancelable:
 * a canceled event skips the finalization, so the mob keeps its defaults.
 * Subclasses that override {@code finalizeSpawn} may run their own code before
 * calling {@code super}, which is where this fires.
 */
public final class FinalizeSpawnEvent extends Event {
    private final Mob mob;
    private final ServerLevelAccessor level;
    private final EntitySpawnReason reason;

    public FinalizeSpawnEvent(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason) {
        super(true);
        this.mob = mob;
        this.level = level;
        this.reason = reason;
    }

    public Mob mob() {
        return mob;
    }

    public ServerLevelAccessor level() {
        return level;
    }

    public EntitySpawnReason reason() {
        return reason;
    }

    private boolean spawnCancelled;

    /**
     * Keeps the mob out of the world: it is not added to the level (natural spawns,
     * spawners, spawn eggs, structures, commands). Canceling the event instead only
     * skips the mob's own spawn setup (equipment, variants) and still adds it.
     */
    public void setSpawnCancelled(boolean cancelled) {
        this.spawnCancelled = cancelled;
    }

    public boolean spawnCancelled() {
        return spawnCancelled;
    }
}
