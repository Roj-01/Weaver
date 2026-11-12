package net.weaver.minimap.api;

import java.util.Objects;

/**
 * One text line contributed to the HUD.
 *
 * @param text    the line text, never {@code null}
 * @param color   the packed ARGB color
 * @param anchor  the screen corner, never {@code null}
 * @param offsetX the horizontal offset from the anchored corner
 * @param offsetY the vertical offset from the anchored corner
 * @param zOrder  the draw order; lower values are drawn first
 */
public record HudOverlayLine(String text, int color, HudAnchor anchor, int offsetX,
                             int offsetY, int zOrder)
{
    /**
     * Validates the text and anchor.
     *
     * @throws NullPointerException when {@code text} or {@code anchor} is
     *                              {@code null}
     */
    public HudOverlayLine
    {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(anchor, "anchor");
    }
}
