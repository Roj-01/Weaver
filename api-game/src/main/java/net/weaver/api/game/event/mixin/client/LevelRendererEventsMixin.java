package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.FrameGraphSetupEvent;
import net.weaver.api.game.event.client.PrepareRenderBuffersEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The level frame: extra submitted geometry and frame-graph passes. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererEventsMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Shadow
    @Final
    private SubmitNodeStorage submitNodeStorage;

    @Shadow
    @Final
    private LevelTargetBundle targets;

    @Inject(method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
    private void weaver$prepareBuffers(GraphicsResourceAllocator allocator, boolean renderBlockOutline,
            CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean improvedTransparency,
            CallbackInfo callback) {
        if (GameEvents.hasListeners(PrepareRenderBuffersEvent.class)) {
            GameEvents.post(new PrepareRenderBuffersEvent(levelRenderState, submitNodeStorage), LogicalSide.CLIENT);
        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"))
    private void weaver$frameGraph(GraphicsResourceAllocator allocator, boolean renderBlockOutline,
            CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean improvedTransparency,
            CallbackInfo callback, @Local FrameGraphBuilder builder) {
        if (GameEvents.hasListeners(FrameGraphSetupEvent.class)) {
            GameEvents.post(new FrameGraphSetupEvent(builder, targets, camera, levelRenderState), LogicalSide.CLIENT);
        }
    }
}
