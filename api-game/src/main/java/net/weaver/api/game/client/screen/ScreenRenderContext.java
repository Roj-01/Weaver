package net.weaver.api.game.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;

/**
 * A snapshot of one screen render callback.
 *
 * <p>The screen and graphics objects are live render-thread objects and must not
 * be retained. The context is immutable, but the referenced graphics object is
 * not.
 *
 * @param screen      the screen being rendered, normally non-null in a live
 *                    game
 * @param graphics    the GUI graphics extractor, normally non-null in a live
 *                    game
 * @param mouseX      the scaled mouse x coordinate
 * @param mouseY      the scaled mouse y coordinate
 * @param partialTick the frame's partial tick
 * @param phase       the callback phase, never {@code null}
 * @param minecraft   the client instance, normally non-null in a live game
 */
public record ScreenRenderContext(Screen screen, GuiGraphicsExtractor graphics, int mouseX,
                                  int mouseY, float partialTick, ScreenRenderPhase phase,
                                  Minecraft minecraft)
{
    /**
     * Validates the phase and the partial tick.
     *
     * @throws NullPointerException     when {@code phase} is {@code null}
     */
    public ScreenRenderContext
    {
        Objects.requireNonNull(phase, "phase");
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
    }

    /**
     * Returns the graphics viewport width.
     *
     * @return the width, or {@code 0} when no graphics object is available
     */
    public int width()
    {
        return graphics == null ? 0 : graphics.guiWidth();
    }

    /**
     * Returns the graphics viewport height.
     *
     * @return the height, or {@code 0} when no graphics object is available
     */
    public int height()
    {
        return graphics == null ? 0 : graphics.guiHeight();
    }
}
