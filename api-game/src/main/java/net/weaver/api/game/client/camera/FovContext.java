package net.weaver.api.game.client.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/**
 * A snapshot of one field-of-view calculation.
 *
 * <p>The callback runs at the return of {@code Camera#calculateFov(float)}. The
 * camera and client are live render-thread objects and must not be retained.
 *
 * @param camera      the live camera, normally non-null in a live game
 * @param minecraft   the client instance, normally non-null in a live game
 * @param partialTick the partial tick passed to {@code calculateFov}
 * @param baseFov     the vanilla field of view before Weaver modifiers
 */
public record FovContext(Camera camera, Minecraft minecraft, float partialTick, float baseFov)
{
    /**
     * Validates the partial tick.
     *
     * @throws IllegalArgumentException when {@code partialTick} is negative or
     *                                  {@code baseFov} is not finite
     */
    public FovContext
    {
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
        if (!Float.isFinite(baseFov))
        {
            throw new IllegalArgumentException("baseFov must be finite but was " + baseFov);
        }
    }
}
