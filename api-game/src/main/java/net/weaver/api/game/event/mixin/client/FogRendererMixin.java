package net.weaver.api.game.event.mixin.client;

import java.util.List;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.weaver.api.game.event.support.FluidTypeFog;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the modded-fluid fog first in vanilla's (mutable) fog environment list. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    @Final
    private static List<FogEnvironment> FOG_ENVIRONMENTS;

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void weaver$fluidTypeFog(CallbackInfo callback) {
        for (FogEnvironment environment : FOG_ENVIRONMENTS) {
            if (environment instanceof FluidTypeFog) {
                return;
            }
        }
        try {
            FOG_ENVIRONMENTS.add(0, new FluidTypeFog());
        } catch (UnsupportedOperationException immutable) {
            System.out.println("[weaver] FLUID FOG UNAVAILABLE reason=fog environment list is immutable");
        }
    }
}
