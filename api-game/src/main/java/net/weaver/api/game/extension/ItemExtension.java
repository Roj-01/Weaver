package net.weaver.api.game.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Behavior a mod's item can override where vanilla hard-codes an item or a
 * constant. An {@code Item} subclass implements this interface and overrides the
 * methods it needs; every default is vanilla's own behavior.
 *
 * <pre>{@code
 * public class WrenchItem extends Item implements ItemExtension {
 *     public boolean usesBlockWhileSneaking(ItemStack stack, Level level, BlockPos pos, Player player) {
 *         return true; // sneak-click still rotates the machine
 *     }
 * }
 * }</pre>
 */
public interface ItemExtension {
    /**
     * Called every tick of a dropped stack of this item, before vanilla's tick.
     * Return true to replace the whole tick (movement, merging, despawning
     * included); false lets vanilla tick it. Default: false.
     */
    default boolean onItemEntityTick(ItemStack stack, ItemEntity entity) {
        return false;
    }

    /** Ticks a dropped stack of this item lasts before it despawns. Default: 6000 (five minutes). */
    default int getItemEntityLifespan(ItemStack stack, Level level) {
        return 6000;
    }

    /** Whether piglins take this stack to barter. Default: it is a gold ingot. */
    default boolean isPiglinCurrency(ItemStack stack) {
        return stack.is(Items.GOLD_INGOT);
    }

    /**
     * Whether a sneaking player holding this item still uses the block clicked
     * (vanilla skips the block's use while sneaking with an item in hand). A
     * wrench returns true. Asked on both sides. Default: false.
     */
    default boolean usesBlockWhileSneaking(ItemStack stack, Level level, BlockPos pos, Player player) {
        return false;
    }

    /**
     * Whether the player may drop this stack from the selected hotbar slot (the
     * drop key). Asked on both sides. Default: true.
     */
    default boolean canBeDropped(ItemStack stack, Player player) {
        return true;
    }

    /**
     * Whether the first-person hand plays the lower-and-raise animation when the
     * held stack changes from {@code from} to {@code to}, both of this item. Asked
     * only when vanilla would play it, so it can only keep the hand still (an
     * energy bar that changes every tick). Client only. Default: true.
     */
    default boolean causesReequipAnimation(ItemStack from, ItemStack to) {
        return true;
    }

    /**
     * Whether a living entity wearing this stack on its feet walks on powder snow
     * instead of sinking, like leather boots. Default: false.
     */
    default boolean allowsWalkingOnPowderSnow(ItemStack stack, LivingEntity wearer) {
        return false;
    }

    /**
     * Whether this stack can do {@code action} where vanilla only lets one item
     * do it, for example {@link ItemAction#SHEAR} for shears. Default: false
     * (vanilla's own item keeps working).
     */
    default boolean canPerformAction(ItemStack stack, ItemAction action) {
        return false;
    }

    /**
     * Called when {@code entity} starts swinging its arm with this stack in
     * {@code hand}. Return true to cancel the swing (no animation, no attack
     * reset). Both sides. Default: false.
     */
    default boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return false;
    }

    /**
     * Called when {@code player} attacks {@code target} with this stack in the main
     * hand, before any damage. Return true to cancel the attack. Both sides.
     * Default: false.
     */
    default boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        return false;
    }

    /**
     * Whether mining progress starts over when the held stack changes from
     * {@code from} to {@code to}, both of this item (vanilla restarts on any
     * component change). Asked only when vanilla would restart. Client only.
     * Default: true.
     */
    default boolean shouldResetBlockBreaking(ItemStack from, ItemStack to) {
        return true;
    }

    /**
     * What stays in the crafting grid in place of this stack after a craft (a
     * bucket after a cake, a hammer that lost durability). Return a new stack and
     * leave {@code stack} unchanged: vanilla takes it from the grid afterwards.
     * Default: the item's fixed remainder.
     */
    default ItemStack getCraftingRemainder(ItemStack stack) {
        net.minecraft.world.item.ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
        return remainder == null ? ItemStack.EMPTY : remainder.create();
    }

    /**
     * An entity to add in place of {@code entity}, a plain dropped stack of this
     * item about to be added to {@code level} (a fireproof or floating item
     * entity). Return null to add {@code entity} itself. The replacement is added
     * as is; it is not asked again. Copy what it needs from {@code entity} (motion,
     * pickup delay, owner): changes vanilla makes to the plain entity after adding
     * it (a longer lifetime, for example) stay on the plain entity. Server only.
     * Default: null.
     */
    default Entity createItemEntity(net.minecraft.server.level.ServerLevel level, ItemEntity entity, ItemStack stack) {
        return null;
    }

    /**
     * Called when {@code entity} stops using this stack for any reason: released,
     * finished, or interrupted (vanilla only reports a release). Both sides.
     * Default: nothing.
     */
    default void onStopUsing(ItemStack stack, LivingEntity entity, int remainingTicks) {
    }

    /**
     * The name shown above the hotbar when this stack is selected. {@code name} is
     * vanilla's (the hover name). Client only. Default: {@code name}.
     */
    default net.minecraft.network.chat.Component getHighlightTip(ItemStack stack, net.minecraft.network.chat.Component name) {
        return name;
    }
}
