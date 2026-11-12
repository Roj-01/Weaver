package net.weaver.api.game.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * An immutable snapshot of one world render pass.
 *
 * <p>The client level, camera, and camera position are live game objects and
 * must only be used on the render thread. Do not retain them past the
 * {@link WorldRenderListener#render(WorldRenderContext)} call. The level and
 * camera are non-null in a live game and may be {@code null} only in a test or
 * a headless proof.
 *
 * @param level          the client level, or {@code null} in a test
 * @param camera         the active camera, or {@code null} in a test
 * @param partialTick    the current frame's partial tick
 * @param cameraPosition the camera position, never {@code null}
 * @param sink           the world-space drawing sink, never {@code null}
 * @param projector      the world-to-clip projector, never {@code null}
 * @param screenWidth    the framebuffer width in pixels
 * @param screenHeight   the framebuffer height in pixels
 */
public record WorldRenderContext(ClientLevel level, Camera camera, float partialTick,
                                 Vec3 cameraPosition, WorldRenderSink sink,
                                 WorldToScreen projector, int screenWidth, int screenHeight)
{
    /**
     * Validates the immutable values.
     *
     * @throws NullPointerException     when a non-null value is {@code null}
     * @throws IllegalArgumentException when the partial tick or a screen
     *                                  dimension is negative
     */
    public WorldRenderContext
    {
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(projector, "projector");
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
        if (screenWidth < 0 || screenHeight < 0)
        {
            throw new IllegalArgumentException("screen size must be >= 0 but was "
                    + screenWidth + "x" + screenHeight);
        }
    }

    /**
     * Projects a world position to clip space.
     *
     * @param worldPosition the world position, never {@code null}
     * @return the clip-space position, never {@code null}
     */
    public Vec3 project(Vec3 worldPosition)
    {
        return projector.project(worldPosition);
    }

    /**
     * Projects a world position to framebuffer pixels.
     *
     * <p>The result is visible when the clip-space depth is in {@code [0, 1]}.
     * Points outside the viewport are still returned with their computed pixel
     * coordinates so callers can clamp or discard them.
     *
     * @param worldPosition the world position, never {@code null}
     * @return the projected screen point, never {@code null}
     */
    public ScreenPoint projectToScreen(Vec3 worldPosition)
    {
        Vec3 clip = projector.project(worldPosition);
        float x = (float) ((clip.x() + 1.0) * 0.5 * screenWidth);
        float y = (float) ((1.0 - (clip.y() + 1.0) * 0.5) * screenHeight);
        boolean visible = clip.z() >= 0.0 && clip.z() <= 1.0;
        return new ScreenPoint(x, y, visible);
    }

    /**
     * One projected framebuffer point.
     *
     * @param x       the x pixel coordinate
     * @param y       the y pixel coordinate
     * @param visible whether the point's depth is inside the clip range
     */
    public record ScreenPoint(float x, float y, boolean visible)
    {
    }
}
