package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.TripWireBlock;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link ItemAction#SHEAR}: breaking tripwire without setting it off. */
@Mixin(TripWireBlock.class)
public abstract class ShearsTripwireExtensionsMixin {
    @WrapOperation(method = "playerWillDestroy", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean weaver$shears(ItemStack stack, Object item, Operation<Boolean> original) {
        return ExtensionHooks.isOrShears(stack, item, original.call(stack, item));
    }
}
