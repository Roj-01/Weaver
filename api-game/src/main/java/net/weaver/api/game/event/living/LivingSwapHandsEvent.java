package net.weaver.api.game.event.living;

import java.util.Objects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a player swaps the items in their main hand and
 * off hand (the swap key, {@code ServerGamePacketListenerImpl#handlePlayerAction}).
 * {@link #toMainHand()} and {@link #toOffHand()} start as the swapped stacks;
 * set them to put other stacks in the hands. Cancelable: a canceled event
 * leaves both hands as they were.
 */
public final class LivingSwapHandsEvent extends Event {
    private final LivingEntity entity;
    private ItemStack toMainHand;
    private ItemStack toOffHand;

    public LivingSwapHandsEvent(LivingEntity entity, ItemStack toMainHand, ItemStack toOffHand) {
        super(true);
        this.entity = entity;
        this.toMainHand = toMainHand;
        this.toOffHand = toOffHand;
    }

    public LivingEntity entity() {
        return entity;
    }

    public ItemStack toMainHand() {
        return toMainHand;
    }

    public void setToMainHand(ItemStack stack) {
        this.toMainHand = Objects.requireNonNull(stack, "stack");
    }

    public ItemStack toOffHand() {
        return toOffHand;
    }

    public void setToOffHand(ItemStack stack) {
        this.toOffHand = Objects.requireNonNull(stack, "stack");
    }
}
