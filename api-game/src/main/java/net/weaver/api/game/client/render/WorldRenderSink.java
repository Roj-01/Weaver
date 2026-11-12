package net.weaver.api.game.client.render;

import net.minecraft.world.phys.Vec3;

/**
 * Draws world-space primitives for one rendered frame.
 *
 * <p>The built-in sink is backed by the Minecraft 26.3 gizmo system
 * ({@code Gizmos.line}, {@code Gizmos.point}, and
 * {@code Gizmos.billboardText}). A sink is only valid during the
 * {@link WorldRenderListener#render(WorldRenderContext)} call that supplied it;
 * it must not be retained.
 *
 * <p>All coordinates are world coordinates. Colors are ARGB integers.
 */
public interface WorldRenderSink
{
    /**
     * Draws a world-space line segment.
     *
     * @param from  the start position, never {@code null}
     * @param to    the end position, never {@code null}
     * @param argb  the ARGB color
     * @param width the line width in the gizmo renderer's units; values less
     *              than or equal to zero use {@code 1.0}
     */
    void line(Vec3 from, Vec3 to, int argb, float width);

    /**
     * Draws a world-space line with the renderer's default width.
     *
     * @param from the start position, never {@code null}
     * @param to   the end position, never {@code null}
     * @param argb the ARGB color
     */
    default void line(Vec3 from, Vec3 to, int argb)
    {
        line(from, to, argb, 3.0F);
    }

    /**
     * Draws a world-space point.
     *
     * @param position the point position, never {@code null}
     * @param argb     the ARGB color
     * @param size     the point size in the gizmo renderer's units; values less
     *                 than or equal to zero use {@code 1.0}
     */
    void point(Vec3 position, int argb, float size);

    /**
     * Draws camera-facing billboard text at a world position.
     *
     * @param text     the text, never {@code null}
     * @param position the world position, never {@code null}
     * @param argb     the ARGB color
     * @param scale    the text scale; values less than or equal to zero use
     *                 {@code 1.0}
     */
    void text(String text, Vec3 position, int argb, float scale);
}
