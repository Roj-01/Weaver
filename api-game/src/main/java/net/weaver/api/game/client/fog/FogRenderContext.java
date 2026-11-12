package net.weaver.api.game.client.fog;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;

import java.util.Objects;

/**
 * A mutable view of one fog setup pass.
 *
 * <p>The callback runs at the return of {@code FogRenderer#setupFog}. Listeners
 * may write the public fields of {@link #data()} to change the current frame's
 * fog. The referenced camera, level, and client are live render-thread objects
 * and must not be retained.
 *
 * @param camera         the active camera, normally non-null in a live game
 * @param data           the mutable fog data, never {@code null}
 * @param level          the client level, normally non-null in a live game
 * @param renderDistance the render distance value supplied by the caller
 * @param partialTick    the frame's partial tick
 * @param minecraft      the client instance, normally non-null in a live game
 */
public record FogRenderContext(Camera camera, FogData data, ClientLevel level, int renderDistance,
                               float partialTick, Minecraft minecraft)
{
    /**
     * Validates the fog data and the partial tick.
     *
     * @throws NullPointerException     when {@code data} is {@code null}
     */
    public FogRenderContext
    {
        Objects.requireNonNull(data, "data");
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
    }
}
