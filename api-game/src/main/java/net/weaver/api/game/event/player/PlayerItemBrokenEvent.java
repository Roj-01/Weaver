package net.weaver.api.game.event.player;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired on both sides when an item a player was holding or wearing ran out
 * of durability and broke ({@code LivingEntity#onEquippedItemBroken}): tools,
 * weapons, shields, armor, fishing rods. {@link #item()} is the broken stack,
 * already empty in most cases; check {@link #slot()} for where it was. Not
 * cancelable.
 */
public final class PlayerItemBrokenEvent extends Event {
    private final Player player;
    private final ItemStack item;
    private final EquipmentSlot slot;

    public PlayerItemBrokenEvent(Player player, ItemStack item, EquipmentSlot slot) {
        this.player = player;
        this.item = item;
        this.slot = slot;
    }

    public Player player() {
        return player;
    }

    public ItemStack item() {
        return item;
    }

    public EquipmentSlot slot() {
        return slot;
    }
}
