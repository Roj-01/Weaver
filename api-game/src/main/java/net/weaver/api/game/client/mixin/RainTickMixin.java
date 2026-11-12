package net.weaver.api.game.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.weaver.api.game.client.level.EnvironmentRenderers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The rain tick of a dimension's {@link EnvironmentRenderers.Weather} renderer. */
@Mixin(ClientLevel.class)
public abstract class RainTickMixin {
    @Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true)
    private void weaver$tickRain(CallbackInfo callback) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (EnvironmentRenderers.tickRain(self, self.getGameTime(), Minecraft.getInstance().gameRenderer.mainCamera())) {
            callback.cancel();
        }
    }
}
