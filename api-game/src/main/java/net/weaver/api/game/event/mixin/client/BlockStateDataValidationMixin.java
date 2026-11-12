package net.weaver.api.game.event.mixin.client;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Like {@link ModelDataValidationMixin}, for block states: the vanilla client data
 * generator checks blocks in the {@code minecraft} namespace only, so mod blocks
 * (whose block states are in the mod's own files) do not fail it.
 */
@Mixin(targets = "net.minecraft.client.data.models.ModelProvider$BlockStateGeneratorCollector")
public abstract class BlockStateDataValidationMixin {
    @Inject(method = "lambda$validate$0", at = @At("HEAD"), cancellable = true)
    private void weaver$vanillaBlocksOnly(Holder.Reference<?> block, CallbackInfoReturnable<Boolean> callback) {
        if (!"minecraft".equals(block.key().identifier().getNamespace())) {
            callback.setReturnValue(false);
        }
    }
}
