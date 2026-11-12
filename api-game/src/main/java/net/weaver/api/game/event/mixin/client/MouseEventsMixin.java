package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientMouseScrollEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse wheel scrolling in the world (no screen open). */
@Mixin(MouseHandler.class)
public abstract class MouseEventsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void weaver$scroll(long window, double deltaX, double deltaY, CallbackInfo callback) {
        if (window != minecraft.getWindow().handle() || minecraft.gui.screen() != null || minecraft.player == null
                || !GameEvents.hasListeners(ClientMouseScrollEvent.class)) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new ClientMouseScrollEvent(deltaX, deltaY), LogicalSide.CLIENT))) {
            callback.cancel();
        }
    }
}
