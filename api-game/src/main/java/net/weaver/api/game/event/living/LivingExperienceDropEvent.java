package net.weaver.api.game.event.living;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when a dying living entity is about to drop experience
 * ({@code LivingEntity#dropExperience}). Cancelable: a canceled event drops no
 * experience. The amount can be changed.
 */
public final class LivingExperienceDropEvent extends Event {
    private final LivingEntity entity;
    private final Entity killer;
    private final int originalAmount;
    private int amount;

    public LivingExperienceDropEvent(LivingEntity entity, Entity killer, int amount) {
        super(true);
        this.entity = entity;
        this.killer = killer;
        this.originalAmount = amount;
        this.amount = amount;
    }

    public LivingEntity entity() {
        return entity;
    }

    /** The killing entity, or {@code null}. */
    public Entity killer() {
        return killer;
    }

    public int originalAmount() {
        return originalAmount;
    }

    public int amount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
