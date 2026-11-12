package net.weaver.api.client;

import java.util.Objects;

/**
 * An immutable snapshot of one HUD render pass.
 *
 * <p>The record is safe to share between threads, but the referenced graphics
 * and client objects are not. Use the context only on the render thread and do
 * not retain it past the {@link HudLayer#render(HudRenderContext)} call.
 *
 * @param graphics    the graphics or GUI rendering object, never {@code null}
 * @param minecraft   the client instance, normally non-null in a live game
 * @param width       the logical HUD width in pixels, zero or greater
 * @param height      the logical HUD height in pixels, zero or greater
 * @param partialTick the partial tick for the current frame
 */
public record HudRenderContext(Object graphics, Object minecraft, int width, int height,
                               float partialTick)
{
    /**
     * Validates the graphics handle and the viewport size.
     *
     * @throws NullPointerException     when {@code graphics} is {@code null}
     * @throws IllegalArgumentException when {@code width} or {@code height} is
     *                                  negative
     */
    public HudRenderContext
    {
        Objects.requireNonNull(graphics, "graphics");
        width = ClientValidation.requireNonNegative(width, "width");
        height = ClientValidation.requireNonNegative(height, "height");
    }
}
