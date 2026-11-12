package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.weaver.api.game.event.support.FluidTypeFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notes the modded fluid at the camera for {@link FluidTypeFog}; vanilla's answer is unchanged. */
@Mixin(Camera.class)
public abstract class FluidTypeFogMixin {
    @Shadow
    private Level level;

    @Inject(method = "getFluidInCamera()Lnet/minecraft/world/level/material/FogType;", at = @At("HEAD"))
    private void weaver$modFluidFog(CallbackInfoReturnable<FogType> callback) {
        FluidTypeFog.update((Camera) (Object) this, level);
    }
}
