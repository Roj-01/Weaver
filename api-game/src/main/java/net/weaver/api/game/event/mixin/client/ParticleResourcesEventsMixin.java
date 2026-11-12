package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.particle.ParticleResources;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.RegisterParticleProvidersEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particle providers, after vanilla registered its own. */
@Mixin(ParticleResources.class)
public abstract class ParticleResourcesEventsMixin {
    @Inject(method = "registerProviders()V", at = @At("RETURN"))
    private void weaver$providers(CallbackInfo callback) {
        GameEvents.post(new RegisterParticleProvidersEvent((ParticleResources) (Object) this), LogicalSide.CLIENT);
    }
}
