package net.weaver.api.game.client.render;

import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * {@link WorldRenderSink} backed by the Minecraft 26.3 gizmo system.
 *
 * <p>Each call adds one gizmo to the collector installed by the built-in
 * {@code LevelRenderer#addMainThreadGizmos} hook. The sink is stateless and
 * must only be used while that collector is active.
 */
public final class GizmoWorldRenderSink implements WorldRenderSink
{
    /** {@inheritDoc} */
    @Override
    public void line(Vec3 from, Vec3 to, int argb, float width)
    {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Gizmos.line(from, to, argb, width <= 0.0F ? 1.0F : width);
    }

    /** {@inheritDoc} */
    @Override
    public void point(Vec3 position, int argb, float size)
    {
        Objects.requireNonNull(position, "position");
        Gizmos.point(position, argb, size <= 0.0F ? 1.0F : size);
    }

    /** {@inheritDoc} */
    @Override
    public void text(String text, Vec3 position, int argb, float scale)
    {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        TextGizmo.Style style = TextGizmo.Style.forColorAndCentered(argb)
                .withScale(scale <= 0.0F ? 1.0F : scale);
        Gizmos.billboardText(text, position, style);
    }
}
