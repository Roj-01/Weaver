package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EnchantmentLevelSetEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enchanting table offer levels. {@code slotsChanged} computes the offers in
 * a lambda run through the menu's level access; the lambda's own arguments
 * carry the level and table position, kept here for the cost hook.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuEventsMixin {
    @Unique
    private static final ThreadLocal<Object[]> weaver$table = new ThreadLocal<>();

    @Inject(method = "lambda$slotsChanged$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"))
    private void weaver$tableStart(ItemStack item, Level level, BlockPos pos, CallbackInfo callback) {
        weaver$table.set(new Object[] {level, pos});
    }

    @Inject(method = "lambda$slotsChanged$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("RETURN"))
    private void weaver$tableDone(ItemStack item, Level level, BlockPos pos, CallbackInfo callback) {
        weaver$table.remove();
    }

    @WrapOperation(method = "lambda$slotsChanged$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I"))
    private int weaver$cost(RandomSource random, int row, int power, ItemStack item, Operation<Integer> original) {
        int cost = original.call(random, row, power, item);
        Object[] table = weaver$table.get();
        if (table == null || !GameEvents.hasListeners(EnchantmentLevelSetEvent.class)) {
            return cost;
        }
        Level level = (Level) table[0];
        return GameEvents.post(new EnchantmentLevelSetEvent(level, (BlockPos) table[1], row, power, item, cost), level)
                .enchantLevel();
    }
}
