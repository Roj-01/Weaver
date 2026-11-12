package net.weaver.api.game.event.mixin.extension;

import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link ItemAction#SHEAR} in a dispenser: an item with no dispenser behavior of
 * its own that can shear gets the shears behavior.
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserExtensionsMixin {
    @Inject(method = "getDefaultDispenseMethod", at = @At("HEAD"), cancellable = true)
    private static void weaver$shears(ItemStack stack, CallbackInfoReturnable<DispenseItemBehavior> callback) {
        if (ExtensionHooks.performs(stack, ItemAction.SHEAR)) {
            DispenseItemBehavior shears = DispenserBlock.DISPENSER_REGISTRY.get(Items.SHEARS);
            if (shears != null) {
                callback.setReturnValue(shears);
            }
        }
    }
}
