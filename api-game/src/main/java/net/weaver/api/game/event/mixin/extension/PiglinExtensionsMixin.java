package net.weaver.api.game.event.mixin.extension;

import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link ItemExtension#isPiglinCurrency}: what piglins take to barter. */
@Mixin(PiglinAi.class)
public abstract class PiglinExtensionsMixin {
    @Inject(method = "isBarterCurrency", at = @At("HEAD"), cancellable = true)
    private static void weaver$currency(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack.getItem() instanceof ItemExtension extension) {
            callback.setReturnValue(extension.isPiglinCurrency(stack));
        }
    }
}
