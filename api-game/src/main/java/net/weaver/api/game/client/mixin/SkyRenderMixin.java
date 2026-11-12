package net.weaver.api.game.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.weaver.api.game.client.sky.SkyRenderContext;
import net.weaver.api.game.client.sky.SkyRenderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches the Weaver sky state listeners.
 *
 * <p>Target verified against Minecraft 26.3:
 * {@code SkyRenderer#extractRenderState(ClientLevel, float, Camera,
 * SkyRenderState)V} at {@code RETURN}. Listeners may mutate the public fields
 * of the supplied {@code SkyRenderState} before the sky is rendered.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRenderMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 1)
    private void weaverExtractSky(ClientLevel level, float partialTick, Camera camera,
                                  SkyRenderState state, CallbackInfo callback)
    {
        if (state == null)
        {
            return;
        }
        try
        {
            SkyRenderRegistry.dispatch(new SkyRenderContext(level, partialTick, camera, state,
                    Minecraft.getInstance()));
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] SKY RENDER HOOK FAILED reason=" + failure);
            }
        }
    }
}
