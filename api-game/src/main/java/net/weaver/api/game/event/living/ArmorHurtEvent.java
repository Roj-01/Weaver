package net.weaver.api.game.event.living;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired for each worn piece that is about to lose durability because its wearer
 * was hurt ({@code LivingEntity#doHurtEquipment}). Cancelable: a canceled event
 * spares that piece. The durability damage can be changed.
 */
public final class ArmorHurtEvent extends Event {
    private final LivingEntity entity;
    private final EquipmentSlot slot;
    private final ItemStack armor;
    private int damage;

    public ArmorHurtEvent(LivingEntity entity, EquipmentSlot slot, ItemStack armor, int damage) {
        super(true);
        this.entity = entity;
        this.slot = slot;
        this.armor = armor;
        this.damage = damage;
    }

    public LivingEntity entity() {
        return entity;
    }

    public EquipmentSlot slot() {
        return slot;
    }

    public ItemStack armor() {
        return armor;
    }

    public int damage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }
}
