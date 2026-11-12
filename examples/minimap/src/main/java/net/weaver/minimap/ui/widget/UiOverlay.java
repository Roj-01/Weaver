package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * A widget that renders a popup above the normal widget layer.
 *
 * <p>Dropdowns and context menus implement this interface. The integrator
 * routes mouse and key events to the open overlay first, then calls
 * {@link #drawAll} after the regular widget pass so the popup is never
 * obscured.</p>
 */
public interface UiOverlay
{
    /** Whether the popup is currently visible. */
    boolean isOpen();

    /** Closes the popup without firing a selection. */
    void close();

    /**
     * Draws the popup.
     *
     * @param graphics the graphics sink
     * @param mouseX   the cursor x
     * @param mouseY   the cursor y
     */
    void drawOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    /**
     * Draws every open overlay in list order.
     *
     * @param graphics the graphics sink
     * @param mouseX   the cursor x
     * @param mouseY   the cursor y
     * @param overlays the overlays, possibly empty
     */
    static void drawAll(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                        List<? extends UiOverlay> overlays)
    {
        if (overlays == null)
        {
            return;
        }
        for (UiOverlay overlay : overlays)
        {
            if (overlay != null && overlay.isOpen())
            {
                overlay.drawOverlay(graphics, mouseX, mouseY);
            }
        }
    }

    /**
     * Draws every open overlay in argument order.
     *
     * @param graphics the graphics sink
     * @param mouseX   the cursor x
     * @param mouseY   the cursor y
     * @param overlays the overlays, possibly {@code null}
     */
    static void drawAll(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                        UiOverlay... overlays)
    {
        if (overlays == null)
        {
            return;
        }
        for (UiOverlay overlay : overlays)
        {
            if (overlay != null && overlay.isOpen())
            {
                overlay.drawOverlay(graphics, mouseX, mouseY);
            }
        }
    }
}
