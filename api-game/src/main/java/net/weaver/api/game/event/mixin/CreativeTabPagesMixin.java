package net.weaver.api.game.event.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.weaver.api.game.creative.CreativeTabPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mod tabs report the row and column the Weaver page layout assigns them. */
@Mixin(CreativeModeTab.class)
public abstract class CreativeTabPagesMixin {
    @Inject(method = "row", at = @At("RETURN"), cancellable = true)
    private void weaver$row(CallbackInfoReturnable<CreativeModeTab.Row> callback) {
        callback.setReturnValue(CreativeTabPages.row((CreativeModeTab) (Object) this, callback.getReturnValue()));
    }

    @Inject(method = "column", at = @At("RETURN"), cancellable = true)
    private void weaver$column(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(CreativeTabPages.column((CreativeModeTab) (Object) this, callback.getReturnValue()));
    }

    @Inject(method = "isAlignedRight", at = @At("RETURN"), cancellable = true)
    private void weaver$alignedRight(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CreativeTabPages.alignedRight((CreativeModeTab) (Object) this, callback.getReturnValue()));
    }
}
