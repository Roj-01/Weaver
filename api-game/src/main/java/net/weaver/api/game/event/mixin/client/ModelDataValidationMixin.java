package net.weaver.api.game.event.mixin.client;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The vanilla client data generator makes vanilla's item models and checks that
 * every registered item got one. Mod items have their models in the mod's own
 * files, so with a mod installed the check failed ("Missing item model
 * definitions"). It now checks items in the {@code minecraft} namespace only.
 */
@Mixin(targets = "net.minecraft.client.data.models.ModelProvider$ItemInfoCollector")
public abstract class ModelDataValidationMixin {
    @Inject(method = "lambda$finalizeAndValidate$2", at = @At("HEAD"), cancellable = true)
    private void weaver$vanillaItemsOnly(Holder.Reference<?> item, CallbackInfoReturnable<Boolean> callback) {
        if (!"minecraft".equals(item.key().identifier().getNamespace())) {
            callback.setReturnValue(false);
        }
    }
}
