package net.weaver.api.game.client.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.weaver.api.game.client.level.LevelRenderContext;
import net.weaver.api.game.client.level.LevelRenderRegistry;
import net.weaver.api.game.client.level.LevelRenderStage;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches the Weaver level render stages.
 *
 * <p>Targets verified against Minecraft 26.3:
 * <ul>
 *     <li>{@code LevelRenderer#render(GraphicsResourceAllocator, boolean,
 *     CameraRenderState, GpuBufferSlice, Vector4f, boolean, boolean)V} at
 *     {@code HEAD} and {@code RETURN} for {@link LevelRenderStage#START} and
 *     {@link LevelRenderStage#END};</li>
 *     <li>the private {@code addSkyPass(FrameGraphBuilder, CameraRenderState,
 *     GpuBufferSlice)V} at {@code HEAD} for {@link LevelRenderStage#SKY};</li>
 *     <li>the private {@code submitEntities(PoseStack, LevelRenderState,
 *     SubmitNodeCollector)V} at {@code HEAD} for
 *     {@link LevelRenderStage#ENTITIES};</li>
 *     <li>the private {@code submitBlockEntities(PoseStack, LevelRenderState,
 *     SubmitNodeCollector)V} at {@code HEAD} for
 *     {@link LevelRenderStage#BLOCK_ENTITIES}.</li>
 * </ul>
 *
 * <p>This hook is separate from {@code WorldRenderMixin}: the level stages are
 * observation points, while the gizmo-backed world render registry supplies
 * the drawing sink. The pose stack and submit collector are only available for
 * the two entity stages.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRenderMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void weaverLevelStart(GraphicsResourceAllocator allocator, boolean unused,
                                  CameraRenderState cameraState, GpuBufferSlice buffer,
                                  Vector4f clearColor, boolean unused2, boolean unused3,
                                  CallbackInfo callback)
    {
        dispatch(LevelRenderStage.START, cameraState, levelRenderState, null, null);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void weaverLevelEnd(GraphicsResourceAllocator allocator, boolean unused,
                                CameraRenderState cameraState, GpuBufferSlice buffer,
                                Vector4f clearColor, boolean unused2, boolean unused3,
                                CallbackInfo callback)
    {
        dispatch(LevelRenderStage.END, cameraState, levelRenderState, null, null);
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), require = 1)
    private void weaverLevelSky(FrameGraphBuilder builder, CameraRenderState cameraState,
                                GpuBufferSlice buffer, CallbackInfo callback)
    {
        dispatch(LevelRenderStage.SKY, cameraState, levelRenderState, null, null);
    }

    @Inject(method = "submitEntities", at = @At("HEAD"), require = 1)
    private void weaverLevelEntities(PoseStack poseStack, LevelRenderState state,
                                     SubmitNodeCollector collector, CallbackInfo callback)
    {
        dispatchWithState(LevelRenderStage.ENTITIES, state, poseStack, collector);
    }

    @Inject(method = "submitBlockEntities", at = @At("HEAD"), require = 1)
    private void weaverLevelBlockEntities(PoseStack poseStack, LevelRenderState state,
                                          SubmitNodeCollector collector, CallbackInfo callback)
    {
        dispatchWithState(LevelRenderStage.BLOCK_ENTITIES, state, poseStack, collector);
    }

    private static void dispatchWithState(LevelRenderStage stage, LevelRenderState state,
                                          PoseStack poseStack, SubmitNodeCollector collector)
    {
        dispatch(stage, state == null ? null : state.cameraRenderState, state, poseStack,
                collector);
    }

    private static void dispatch(LevelRenderStage stage, CameraRenderState cameraState,
                                 LevelRenderState state, PoseStack poseStack,
                                 SubmitNodeCollector collector)
    {
        try
        {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft == null ? null : minecraft.level;
            LevelRenderContext context = new LevelRenderContext(level, cameraState, state,
                    poseStack, collector, partialTick(minecraft, state), stage, minecraft);
            LevelRenderRegistry.dispatch(context);
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] LEVEL RENDER HOOK FAILED stage=" + stage
                        + " reason=" + failure);
            }
        }
    }

    private static float partialTick(Minecraft minecraft, LevelRenderState state)
    {
        if (minecraft != null)
        {
            try
            {
                return minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            }
            catch (Throwable ignored)
            {
            }
        }
        if (state != null && state.worldPartialTicks >= 0.0F)
        {
            return state.worldPartialTicks;
        }
        return 0.0F;
    }
}
