package net.weaver.api.game.event.mixin.client;

import java.util.Map;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.world.level.material.Fluid;
import net.weaver.api.game.event.support.ClientRegistrations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mod fluid models. */
@Mixin(FluidStateModelSet.class)
public abstract class FluidModelsEventsMixin {
    @Inject(method = "bake(Lnet/minecraft/client/resources/model/sprite/MaterialBaker;)Ljava/util/Map;", at = @At("RETURN"), cancellable = true)
    private static void weaver$fluids(MaterialBaker baker, CallbackInfoReturnable<Map<Fluid, FluidModel>> callback) {
        callback.setReturnValue(ClientRegistrations.fluidModels(baker, callback.getReturnValue()));
    }
}
