package net.weaver.api.game.event.living;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a living entity's equipment in one slot changed since its last
 * check ({@code LivingEntity#collectEquipmentChanges}, once per tick per changed
 * slot, on the server). Covers armor, hands, and the body slot.
 */
public final class LivingEquipmentChangeEvent extends Event {
    private final LivingEntity entity;
    private final EquipmentSlot slot;
    private final ItemStack from;
    private final ItemStack to;

    public LivingEquipmentChangeEvent(LivingEntity entity, EquipmentSlot slot, ItemStack from, ItemStack to) {
        this.entity = entity;
        this.slot = slot;
        this.from = from;
        this.to = to;
    }

    public LivingEntity entity() {
        return entity;
    }

    public EquipmentSlot slot() {
        return slot;
    }

    /** The previous stack (a copy kept by vanilla). */
    public ItemStack from() {
        return from;
    }

    /** The stack now in the slot. */
    public ItemStack to() {
        return to;
    }
}
