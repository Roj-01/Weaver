package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link ItemAction#SHEAR}: honey from beehives and carving pumpkins. */
@Mixin({BeehiveBlock.class, PumpkinBlock.class})
public abstract class ShearsBlockExtensionsMixin {
    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean weaver$shears(ItemStack stack, Object item, Operation<Boolean> original) {
        return ExtensionHooks.isOrShears(stack, item, original.call(stack, item));
    }
}
