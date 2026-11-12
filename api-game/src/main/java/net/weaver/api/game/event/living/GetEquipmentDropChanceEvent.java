package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when the chance that a dying mob drops a piece of its equipment is worked out
 * ({@code EnchantmentHelper#processEquipmentDropChance}, after enchantment effects such as Looting).
 * The chance (0 to 1) can be changed. Server side.
 */
public final class GetEquipmentDropChanceEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private float dropChance;

    public GetEquipmentDropChanceEvent(LivingEntity entity, DamageSource source, float dropChance) {
        super(false);
        this.entity = entity;
        this.source = source;
        this.dropChance = dropChance;
    }

    /** The dying mob. */
    public LivingEntity entity() {
        return entity;
    }

    /** What killed it. */
    public DamageSource source() {
        return source;
    }

    /** The drop chance. */
    public float dropChance() {
        return dropChance;
    }

    public void setDropChance(float dropChance) {
        net.weaver.api.game.event.support.Finite.check(dropChance, "dropChance");
        this.dropChance = dropChance;
    }
}
