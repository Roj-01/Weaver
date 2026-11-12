package net.weaver.api.game.event.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.weaver.api.event.Event;

/**
 * Fired when a mob is about to change its attack target ({@code Mob#setTarget}).
 * Cancelable: a canceled event keeps the current target. The new target can be
 * changed; {@code null} clears the target.
 */
public final class LivingChangeTargetEvent extends Event {
    private final Mob mob;
    private final LivingEntity currentTarget;
    private LivingEntity newTarget;

    public LivingChangeTargetEvent(Mob mob, LivingEntity currentTarget, LivingEntity newTarget) {
        super(true);
        this.mob = mob;
        this.currentTarget = currentTarget;
        this.newTarget = newTarget;
    }

    public Mob mob() {
        return mob;
    }

    /** The current target, or {@code null}. */
    public LivingEntity currentTarget() {
        return currentTarget;
    }

    /** The target about to be set, or {@code null} to clear it. */
    public LivingEntity newTarget() {
        return newTarget;
    }

    public void setNewTarget(LivingEntity newTarget) {
        this.newTarget = newTarget;
    }
}
