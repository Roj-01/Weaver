package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientMouseButtonEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raw mouse buttons, before and after vanilla. */
@Mixin(MouseHandler.class)
public abstract class MouseButtonEventsMixin {
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void weaver$buttonPre(long window, MouseButtonInfo info, int action, CallbackInfo callback) {
        if (GameEvents.hasListeners(ClientMouseButtonEvent.class) && GameEvents.canceled(GameEvents.post(
                new ClientMouseButtonEvent(ClientMouseButtonEvent.Phase.PRE, info.button(), action, info.modifiers()),
                LogicalSide.CLIENT))) {
            callback.cancel();
        }
    }

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("RETURN"))
    private void weaver$buttonPost(long window, MouseButtonInfo info, int action, CallbackInfo callback) {
        if (GameEvents.hasListeners(ClientMouseButtonEvent.class)) {
            GameEvents.post(new ClientMouseButtonEvent(ClientMouseButtonEvent.Phase.POST, info.button(), action,
                    info.modifiers()), LogicalSide.CLIENT);
        }
    }
}
