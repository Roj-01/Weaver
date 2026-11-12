package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.FovModifierEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The player's field-of-view multiplier. */
@Mixin(AbstractClientPlayer.class)
public abstract class ClientPlayerEventsMixin {
    @Inject(method = "getFieldOfViewModifier(ZF)F", at = @At("RETURN"), cancellable = true)
    private void weaver$fov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> callback) {
        if (!GameEvents.hasListeners(FovModifierEvent.class)) {
            return;
        }
        float modifier = callback.getReturnValue();
        FovModifierEvent event = GameEvents.post(new FovModifierEvent((AbstractClientPlayer) (Object) this, modifier),
                LogicalSide.CLIENT);
        if (event.modifier() != modifier) {
            callback.setReturnValue(event.modifier());
        }
    }
}
