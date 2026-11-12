package net.weaver.api.game.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Shared logic of the extension mixins. Integration code: mod code must not call
 * it.
 */
@net.weaver.api.annotation.Internal
public final class ExtensionHooks {
    private ExtensionHooks() {
    }

    /** The friction an entity moving on {@code block} gets: the block's extension, or {@code vanilla}. */
    public static float friction(Block block, Entity entity, float vanilla) {
        if (block instanceof BlockExtension extension) {
            BlockPos pos = entity.getBlockPosBelowThatAffectsMyMovement();
            Level level = entity.level();
            return extension.getFriction(level.getBlockState(pos), level, pos, entity);
        }
        return vanilla;
    }

    /** Whether an item in either hand lets a sneaking player still use the block at {@code pos}. */
    public static boolean usesBlockWhileSneaking(Player player, Level level, BlockPos pos) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ItemExtension extension
                    && extension.usesBlockWhileSneaking(stack, level, pos, player)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code stack} can do {@code action} through its item's extension. */
    public static boolean performs(ItemStack stack, ItemAction action) {
        return stack.getItem() instanceof ItemExtension extension && extension.canPerformAction(stack, action);
    }

    /**
     * For a vanilla check {@code stack.is(item)}: true when it is that item, or when
     * {@code item} is shears and the stack can shear.
     */
    public static boolean isOrShears(ItemStack stack, Object item, boolean vanilla) {
        return vanilla || item == net.minecraft.world.item.Items.SHEARS && performs(stack, ItemAction.SHEAR);
    }

    /** Whether the player may drop the selected stack. */
    public static boolean canDropSelected(Player player) {
        ItemStack stack = player.getInventory().getSelectedItem();
        return !(stack.getItem() instanceof ItemExtension extension) || extension.canBeDropped(stack, player);
    }
}
