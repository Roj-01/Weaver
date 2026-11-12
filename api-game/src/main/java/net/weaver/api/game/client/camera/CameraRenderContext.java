package net.weaver.api.game.client.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import java.util.Objects;

/**
 * A mutable view of one camera render state extraction.
 *
 * <p>The callback runs at the return of
 * {@code Camera#extractRenderState}. Listeners may write the public fields of
 * {@link #state()} to change the camera state for the current frame. The
 * referenced camera and client are live render-thread objects and must not be
 * retained.
 *
 * @param camera       the live camera, normally non-null in a live game
 * @param state        the mutable camera render state, never {@code null}
 * @param deltaTracker the frame delta tracker, normally non-null in a live game
 * @param partialTick  the frame's partial tick
 * @param minecraft    the client instance, normally non-null in a live game
 */
public record CameraRenderContext(Camera camera, CameraRenderState state,
                                  DeltaTracker deltaTracker, float partialTick,
                                  Minecraft minecraft)
{
    /**
     * Validates the camera state and the partial tick.
     *
     * @throws NullPointerException     when {@code state} is {@code null}
     */
    public CameraRenderContext
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
