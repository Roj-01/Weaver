package net.weaver.api.game.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.weaver.api.game.client.render.GizmoWorldRenderSink;
import net.weaver.api.game.client.render.WorldRenderContext;
import net.weaver.api.game.client.render.WorldRenderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feeds the Weaver world render listeners into the Minecraft 26.3 gizmo
 * pipeline once per rendered frame.
 *
 * <p>The target is {@code LevelRenderer#addMainThreadGizmos(List)}, which the
 * client calls while extracting the frame's gizmos. The hook installs a
 * temporary {@link SimpleGizmoCollector}, lets the listeners draw through
 * {@link GizmoWorldRenderSink}, and appends the collected gizmos to the game's
 * main-thread list so they render in the same frame.
 */
@Mixin(LevelRenderer.class)
public abstract class WorldRenderMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_PROOF = new AtomicBoolean();

    @Inject(method = "addMainThreadGizmos", at = @At("HEAD"))
    private void weaverWorldRender(List<SimpleGizmoCollector.GizmoInstance> gizmos,
                                   CallbackInfo callback)
    {
        if (gizmos == null)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
        {
            return;
        }
        ClientLevel level = minecraft.level;
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (level == null || camera == null || !camera.isInitialized())
        {
            return;
        }
        try
        {
            SimpleGizmoCollector collector = new SimpleGizmoCollector();
            WorldRenderContext context = new WorldRenderContext(level, camera,
                    minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false),
                    camera.position(), new GizmoWorldRenderSink(),
                    minecraft.gameRenderer::projectPointToScreen,
                    minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
            try (Gizmos.TemporaryCollection ignored = Gizmos.withCollector(collector))
            {
                WorldRenderRegistry.render(context);
            }
            gizmos.addAll(collector.drainGizmos());
            if (Boolean.getBoolean("weaver.proof.worldRender")
                    && WorldRenderRegistry.listenerCount() > 0
                    && REPORTED_PROOF.compareAndSet(false, true))
            {
                System.out.println("[weaver] WORLD RENDER ok listeners="
                        + WorldRenderRegistry.listenerCount());
            }
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] WORLD RENDER HOOK FAILED reason=" + failure);
            }
        }
    }
}
