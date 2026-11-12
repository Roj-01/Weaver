package net.weaver.api.game.event.living;

import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired after a living entity jumped ({@code LivingEntity#jumpFromGround}), once
 * its upward motion is set. Listeners may adjust the motion of the entity.
 */
public final class LivingJumpEvent extends Event {
    private final LivingEntity entity;

    public LivingJumpEvent(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity entity() {
        return entity;
    }
}
