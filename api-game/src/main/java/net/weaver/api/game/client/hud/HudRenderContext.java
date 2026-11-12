package net.weaver.api.game.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

import java.util.Objects;

/**
 * A snapshot of one HUD render stage.
 *
 * <p>The graphics object, HUD, and client are live render-thread objects and
 * must not be retained. The context is immutable, but the referenced graphics
 * object is not.
 *
 * <p>{@link #graphics()} is {@code null} for
 * {@link HudRenderStage#POST_GUI}, because {@code Gui#extractRenderState} does
 * not expose the extractor after it returns. Use
 * {@link #width()} and {@link #height()} for the scaled viewport size in every
 * stage.
 *
 * @param graphics    the GUI graphics extractor, or {@code null} at
 *                    {@link HudRenderStage#POST_GUI}
 * @param hud         the HUD instance, normally non-null in a live game
 * @param minecraft   the client instance, normally non-null in a live game
 * @param width       the logical width in pixels
 * @param height      the logical height in pixels
 * @param partialTick the frame's partial tick
 * @param stage       the current stage, never {@code null}
 */
public record HudRenderContext(GuiGraphicsExtractor graphics, Hud hud, Minecraft minecraft,
                               int width, int height, float partialTick, HudRenderStage stage)
{
    /**
     * Validates the stage, the viewport size, and the partial tick.
     *
     * @throws NullPointerException     when {@code stage} is {@code null}
     * @throws IllegalArgumentException when a dimension or the partial tick is
     *                                  negative
     */
    public HudRenderContext
    {
        Objects.requireNonNull(stage, "stage");
        if (width < 0 || height < 0)
        {
            throw new IllegalArgumentException("width and height must be >= 0 but were "
                    + width + "x" + height);
        }
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
    }

    /**
     * Whether a graphics extractor is available for drawing.
     *
     * @return {@code true} when {@link #graphics()} is non-null
     */
    public boolean canDraw()
    {
        return graphics != null;
    }
}
