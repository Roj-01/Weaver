package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientKeyInputEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keyboard key events, after vanilla handled them. */
@Mixin(KeyboardHandler.class)
public abstract class InputEventsMixin {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("RETURN"))
    private void weaver$keyInput(long window, int action, KeyEvent key, CallbackInfo callback) {
        if (GameEvents.hasListeners(ClientKeyInputEvent.class)) {
            GameEvents.post(new ClientKeyInputEvent(key, action), LogicalSide.CLIENT);
        }
    }
}
