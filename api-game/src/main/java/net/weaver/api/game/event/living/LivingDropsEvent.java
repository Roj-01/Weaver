package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.weaver.api.event.Event;

import java.util.List;

/**
 * Fired after a dying living entity produced its death drops (loot table,
 * custom drops, and equipment from {@code LivingEntity#dropAllDeathLoot}) and
 * before they enter the world. For a player this includes everything in the
 * inventory and everything worn ({@code Inventory#dropAll}, when the
 * {@code keepInventory} rule is off). Cancelable: a canceled event drops
 * nothing, so a canceled player death loses those items. {@link #drops()} is
 * mutable. Experience is {@code LivingExperienceDropEvent}.
 */
public final class LivingDropsEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private final List<ItemEntity> drops;

    public LivingDropsEvent(LivingEntity entity, DamageSource source, List<ItemEntity> drops) {
        super(true);
        this.entity = entity;
        this.source = source;
        this.drops = drops;
    }

    public LivingEntity entity() {
        return entity;
    }

    public DamageSource source() {
        return source;
    }

    /** The item entities that will be added to the level; mutable. */
    public List<ItemEntity> drops() {
        return drops;
    }
}
