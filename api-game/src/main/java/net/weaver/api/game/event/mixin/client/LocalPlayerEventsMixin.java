package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.MovementInputUpdateEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The local player's movement input, right after it is read from the keys. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerEventsMixin {
    @Inject(method = "aiStep()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
    private void weaver$movementInput(CallbackInfo callback) {
        if (GameEvents.hasListeners(MovementInputUpdateEvent.class)) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            GameEvents.post(new MovementInputUpdateEvent(self, self.input), LogicalSide.CLIENT);
        }
    }
}
