package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link BlockExtension#getEnchantPowerBonus}: blocks around the table with their
 * own power. Vanilla counts one per bookshelf; the extra power is added to the
 * count before the costs are worked out.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantPowerExtensionsMixin {
    @Unique
    private float weaver$extraPower;

    @Inject(method = "lambda$slotsChanged$0", at = @At("HEAD"))
    private void weaver$resetPower(ItemStack stack, Level level, BlockPos table, CallbackInfo callback) {
        weaver$extraPower = 0.0F;
    }

    @WrapOperation(method = "lambda$slotsChanged$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/EnchantingTableBlock;isValidBookShelf(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean weaver$shelf(Level level, BlockPos table, BlockPos offset, Operation<Boolean> original) {
        BlockPos pos = table.offset(offset);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockExtension extension) {
            float bonus = extension.getEnchantPowerBonus(state, level, pos);
            if (bonus > 0.0F) {
                BlockPos between = table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
                if (level.getBlockState(between).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) {
                    weaver$extraPower += bonus;
                }
                return false;
            }
        }
        return original.call(level, table, offset);
    }

    @ModifyArg(method = "lambda$slotsChanged$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I"),
            index = 2)
    private int weaver$power(int shelves) {
        return shelves + Math.round(weaver$extraPower);
    }
}
