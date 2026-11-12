package net.weaver.api.game.client.sky;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.SkyRenderState;

import java.util.Objects;

/**
 * A mutable view of one sky state extraction.
 *
 * <p>The callback runs at the return of
 * {@code SkyRenderer#extractRenderState}. Listeners may write the public fields
 * of {@link #state()} to change the sky for the current frame. The referenced
 * level, camera, and client are live render-thread objects and must not be
 * retained.
 *
 * @param level       the client level, normally non-null in a live game
 * @param partialTick the frame's partial tick
 * @param camera      the active camera, normally non-null in a live game
 * @param state       the mutable sky render state, never {@code null}
 * @param minecraft   the client instance, normally non-null in a live game
 */
public record SkyRenderContext(ClientLevel level, float partialTick, Camera camera,
                               SkyRenderState state, Minecraft minecraft)
{
    /**
     * Validates the sky state and the partial tick.
     *
     * @throws NullPointerException     when {@code state} is {@code null}
     */
    public SkyRenderContext
    {
        Objects.requireNonNull(state, "state");
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
    }
}
