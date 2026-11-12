package net.weaver.api.game.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.weaver.api.game.client.level.EnvironmentRenderers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The per-dimension sky renderers of {@link EnvironmentRenderers}: vanilla's sky
 * draw is skipped when the dimension's renderer drew instead. Clouds and weather
 * are asked inside {@code LevelDrawMixin}, around the same calls it wraps.
 */
@Mixin(LevelRenderer.class)
public abstract class EnvironmentRenderMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @WrapOperation(method = "lambda$addSkyPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;render(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V"))
    private void weaver$sky(SkyRenderer renderer, GpuBufferSlice fog, SkyRenderState sky, Operation<Void> original) {
        if (!EnvironmentRenderers.sky(levelRenderState, sky, levelRenderState.cameraRenderState.viewRotationMatrix, fog)) {
            original.call(renderer, fog, sky);
        }
    }
}
