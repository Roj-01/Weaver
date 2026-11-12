package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ToastAddEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toasts queued for display. */
@Mixin(ToastManager.class)
public abstract class ToastManagerEventsMixin {
    @Inject(method = "addToast(Lnet/minecraft/client/gui/components/toasts/Toast;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$toast(Toast toast, CallbackInfo callback) {
        if (GameEvents.canceled(GameEvents.post(new ToastAddEvent(toast), LogicalSide.CLIENT))) {
            callback.cancel();
        }
    }
}
