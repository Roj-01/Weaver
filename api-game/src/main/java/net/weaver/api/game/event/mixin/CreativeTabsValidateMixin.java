package net.weaver.api.game.event.mixin;

import net.minecraft.world.item.CreativeModeTabs;
import net.weaver.api.game.creative.CreativeTabPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla rejects two tabs in one slot, and its own tabs fill every slot, so
 * any mod tab would fail. Weaver checks the vanilla tabs only; mod tabs get
 * their slot from {@link CreativeTabPages}.
 */
@Mixin(CreativeModeTabs.class)
public abstract class CreativeTabsValidateMixin {
    @Inject(method = "validate", at = @At("HEAD"), cancellable = true)
    private static void weaver$validate(CallbackInfo callback) {
        CreativeTabPages.validate();
        callback.cancel();
    }
}
