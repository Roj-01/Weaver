package net.weaver.api.game.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.weaver.api.game.client.level.LevelDrawRegistry;
import net.weaver.api.game.client.level.LevelDrawStage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The fine-grained draw stages of {@link LevelDrawRegistry}, inside the frame
 * graph passes that vanilla 26.3 executes: the end of the sky pass, after the
 * solid terrain inside {@code executeSolid}, after
 * {@code executeSolid} and {@code executeClassicTransparency} in the main pass
 * (with its open render pass), after {@code executeOit} (improved transparency),
 * and the end of {@code render}. The cloud and weather draws ask the dimension's
 * {@code EnvironmentRenderers} first and skip vanilla when it drew instead.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelDrawMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "lambda$addSkyPass$0", at = @At("RETURN"))
    private void weaver$afterSky(CallbackInfo callback) {
        weaver$draw(LevelDrawStage.AFTER_SKY, null);
    }

    @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;executeSolid(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void weaver$afterSolid(LevelRenderer self, ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass, Operation<Void> original) {
        original.call(self, sections, frame, pass);
        weaver$draw(LevelDrawStage.AFTER_SOLID, pass);
    }

    @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void weaver$afterTranslucent(LevelRenderer self, ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass, Operation<Void> original) {
        original.call(self, sections, frame, pass);
        weaver$draw(LevelDrawStage.AFTER_TRANSLUCENT, pass);
    }

    @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;executeOit(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"))
    private void weaver$afterOit(LevelRenderer self, ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame frame, Operation<Void> original) {
        original.call(self, sections, frame);
        weaver$draw(LevelDrawStage.AFTER_TRANSLUCENT, null);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void weaver$afterLevel(CallbackInfo callback) {
        weaver$draw(LevelDrawStage.AFTER_LEVEL, null);
    }

    @WrapOperation(method = "executeSolid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"))
    private void weaver$afterOpaqueBlocks(ChunkSectionsToRender sections,
            net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup group, RenderPass pass,
            com.mojang.renderpearl.api.textures.GpuSampler sampler, com.mojang.renderpearl.api.textures.GpuTextureView view,
            boolean flag, Operation<Void> original) {
        original.call(sections, group, pass, sampler, view, flag);
        weaver$draw(LevelDrawStage.AFTER_OPAQUE_BLOCKS, pass, null);
    }

    // Classic transparency: inside executeClassicTransparency, with the open main pass.

    @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"))
    private void weaver$afterTranslucentBlocks(ChunkSectionsToRender sections,
            net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup group, RenderPass pass,
            com.mojang.renderpearl.api.textures.GpuSampler sampler, com.mojang.renderpearl.api.textures.GpuTextureView view,
            boolean flag, Operation<Void> original) {
        original.call(sections, group, pass, sampler, view, flag);
        weaver$draw(LevelDrawStage.AFTER_TRANSLUCENT_BLOCKS, pass, null);
    }

    @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucentAfterTerrain(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void weaver$afterTranslucentFeatures(FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass,
            Operation<Void> original) {
        original.call(frame, pass);
        weaver$draw(LevelDrawStage.AFTER_TRANSLUCENT_FEATURES, pass, null);
    }

    @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/CloudRenderer;render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void weaver$afterClouds(net.minecraft.client.renderer.CloudRenderer clouds, net.minecraft.client.CloudStatus status,
            RenderPass pass, Operation<Void> original) {
        if (!net.weaver.api.game.client.level.EnvironmentRenderers.clouds(levelRenderState, status,
                levelRenderState.cameraRenderState.viewRotationMatrix, pass)) {
            original.call(clouds, status, pass);
        }
        weaver$draw(LevelDrawStage.AFTER_CLOUDS, pass, null);
    }

    @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void weaver$afterWeather(net.minecraft.client.renderer.WeatherEffectRenderer weather,
            net.minecraft.client.renderer.state.level.WeatherRenderState state, RenderPass pass, Operation<Void> original) {
        if (!net.weaver.api.game.client.level.EnvironmentRenderers.weather(levelRenderState, state,
                levelRenderState.cameraRenderState.pos, pass)) {
            original.call(weather, state, pass);
        }
        weaver$draw(LevelDrawStage.AFTER_WEATHER, pass, null);
    }

    // Improved transparency: inside executeOit, once per transparency layer.

    @WrapOperation(method = "executeOit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderOit(Lcom/mojang/renderpearl/api/textures/GpuSampler;Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void weaver$afterOitBlocks(ChunkSectionsToRender sections, com.mojang.renderpearl.api.textures.GpuSampler sampler,
            net.minecraft.client.renderer.oit.OitStage layer, net.minecraft.client.renderer.oit.OitRenderPassProvider.Parameters parameters,
            com.mojang.renderpearl.api.textures.GpuTextureView first, com.mojang.renderpearl.api.textures.GpuTextureView second,
            Operation<Void> original) {
        original.call(sections, sampler, layer, parameters, first, second);
        weaver$draw(LevelDrawStage.AFTER_TRANSLUCENT_BLOCKS, null, String.valueOf(layer));
    }

    @WrapOperation(method = "executeOit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeOit(Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void weaver$afterOitFeatures(FeatureRenderDispatcher.PreparedFrame frame,
            net.minecraft.client.renderer.oit.OitStage layer, RenderPass pass, Operation<Void> original) {
        original.call(frame, layer, pass);
        weaver$draw(LevelDrawStage.AFTER_TRANSLUCENT_FEATURES, pass, String.valueOf(layer));
    }

    @WrapOperation(method = "executeOit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/CloudRenderer;renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"))
    private void weaver$afterOitClouds(net.minecraft.client.renderer.CloudRenderer clouds, net.minecraft.client.CloudStatus status,
            net.minecraft.client.renderer.oit.OitStage layer, com.mojang.renderpearl.api.textures.GpuTextureView view,
            net.minecraft.client.renderer.oit.OitRenderPassProvider.Parameters parameters, Operation<Void> original) {
        if (!net.weaver.api.game.client.level.EnvironmentRenderers.cloudsOit(levelRenderState, status,
                levelRenderState.cameraRenderState.viewRotationMatrix, layer, view, parameters)) {
            original.call(clouds, status, layer, view, parameters);
        }
        weaver$draw(LevelDrawStage.AFTER_CLOUDS, null, String.valueOf(layer));
    }

    @WrapOperation(method = "executeOit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;renderOit(Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void weaver$afterOitWeather(net.minecraft.client.renderer.WeatherEffectRenderer weather,
            net.minecraft.client.renderer.oit.OitStage layer, net.minecraft.client.renderer.state.level.WeatherRenderState state,
            RenderPass pass, Operation<Void> original) {
        if (!net.weaver.api.game.client.level.EnvironmentRenderers.weatherOit(levelRenderState, state,
                levelRenderState.cameraRenderState.pos, layer, pass)) {
            original.call(weather, layer, state, pass);
        }
        weaver$draw(LevelDrawStage.AFTER_WEATHER, pass, String.valueOf(layer));
    }

    private void weaver$draw(LevelDrawStage stage, RenderPass pass) {
        weaver$draw(stage, pass, null);
    }

    private void weaver$draw(LevelDrawStage stage, RenderPass pass, String oitLayer) {
        LevelDrawRegistry.hook(stage, pass, oitLayer, levelRenderState);
    }
}
