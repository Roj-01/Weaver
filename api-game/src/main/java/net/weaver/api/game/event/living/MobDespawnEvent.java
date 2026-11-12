package net.weaver.api.game.event.living;

import net.minecraft.world.entity.Mob;
import net.weaver.api.event.Event;

/**
 * Fired when the game checks whether a mob should despawn
 * ({@code Mob#checkDespawn}). Set a {@link Decision}: {@code DENY} keeps the
 * mob, {@code ALLOW} removes it now, {@code DEFAULT} runs the vanilla check.
 */
public final class MobDespawnEvent extends Event {
    /** What to do with the mob. */
    public enum Decision {
        DEFAULT,
        ALLOW,
        DENY
    }

    private final Mob mob;
    private Decision decision = Decision.DEFAULT;

    public MobDespawnEvent(Mob mob) {
        this.mob = mob;
    }

    public Mob mob() {
        return mob;
    }

    public Decision decision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = java.util.Objects.requireNonNull(decision, "decision");
    }
}
