package net.weaver.api.game.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.weaver.api.game.client.fog.FogRenderContext;
import net.weaver.api.game.client.fog.FogRenderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches the Weaver fog setup listeners.
 *
 * <p>Target verified against Minecraft 26.3:
 * {@code FogRenderer#setupFog(Camera, int, DeltaTracker, float,
 * ClientLevel)Lnet/minecraft/client/renderer/fog/FogData;} at {@code RETURN}.
 * Listeners may mutate the returned mutable {@code FogData} fields before the
 * data is uploaded for the frame.
 */
@Mixin(FogRenderer.class)
public abstract class FogRenderMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "setupFog", at = @At("RETURN"), require = 1)
    private void weaverSetupFog(Camera camera, int renderDistance, DeltaTracker deltaTracker,
                                float partialTick, ClientLevel level,
                                CallbackInfoReturnable<FogData> callback)
    {
        FogData data = callback.getReturnValue();
        if (data == null)
        {
            return;
        }
        try
        {
            FogRenderContext context = new FogRenderContext(camera, data, level, renderDistance,
                    partialTick, Minecraft.getInstance());
            FogRenderRegistry.dispatch(context);
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] FOG RENDER HOOK FAILED reason=" + failure);
            }
        }
    }
}
