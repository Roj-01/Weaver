package net.weaver.api.game.event.living;

import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired every server tick for each living entity when the game checks whether
 * its eyes are in water ({@code LivingEntity#baseTick}). Set
 * {@link #setCanBreathe(boolean)} to {@code true} to treat an underwater entity
 * as breathing (its air refills instead of running out), or {@code false} to
 * make it lose air. This fires very often; keep listeners cheap.
 */
public final class LivingBreatheEvent extends Event {
    private final LivingEntity entity;
    private final boolean underwater;
    private boolean canBreathe;

    public LivingBreatheEvent(LivingEntity entity, boolean underwater) {
        this.entity = entity;
        this.underwater = underwater;
        this.canBreathe = !underwater;
    }

    public LivingEntity entity() {
        return entity;
    }

    /** Whether the entity's eyes are in water. */
    public boolean underwater() {
        return underwater;
    }

    public boolean canBreathe() {
        return canBreathe;
    }

    public void setCanBreathe(boolean canBreathe) {
        this.canBreathe = canBreathe;
    }
}
