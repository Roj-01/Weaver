package net.weaver.minimap.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.weaver.minimap.ui.theme.Colors;
import net.weaver.minimap.ui.theme.ScissorGuard;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.ui.theme.UiMath;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Drawing toolkit for the minimap UI.
 *
 * <p>Everything is built from {@link GuiGraphicsExtractor} primitives
 * ({@code fill}, {@code fillGradient}, {@code text}, {@code blit},
 * {@code enableScissor}/{@code disableScissor}). Rounded corners are
 * rasterised as horizontal scanline spans; the outermost pixel of each span is
 * drawn with a coverage-scaled alpha so corners read as anti-aliased without a
 * shader. Hot paths allocate nothing beyond the occasional clipped string.</p>
 */
public final class UiDraw
{
    public static final int GRID = UiMath.GRID;
    public static final int SPACE_XS = 4;
    public static final int SPACE_S = 8;
    public static final int SPACE_M = 12;
    public static final int SPACE_L = 16;
    public static final int SPACE_XL = 24;
    public static final int SPACE_2XL = 32;
    public static final int RADIUS_SMALL = 8;
    public static final int RADIUS_MEDIUM = 12;
    public static final int RADIUS_LARGE = 16;
    public static final int RADIUS_PANEL = 20;
    public static final int RADIUS_PILL = 999;

    private static final String ELLIPSIS = UiMath.ELLIPSIS;

    private UiDraw()
    {
    }

    /** Rounds a value to the nearest 8 px grid line. */
    public static int grid(int value)
    {
        return UiMath.grid(value);
    }

    /** Rounds a value up to the next 8 px grid line. */
    public static int gridCeil(int value)
    {
        return UiMath.gridCeil(value);
    }

    /** Rounds a value down to the previous 8 px grid line. */
    public static int gridFloor(int value)
    {
        return UiMath.gridFloor(value);
    }

    /** Clamps an integer into an inclusive range. */
    public static int clamp(int value, int min, int max)
    {
        return UiMath.clamp(value, min, max);
    }

    /** Clamps a double into an inclusive range. */
    public static double clamp(double value, double min, double max)
    {
        return UiMath.clamp(value, min, max);
    }

    /** Clamps a double into 0..1. */
    public static double clamp01(double value)
    {
        return UiMath.clamp01(value);
    }

    /** Whether a point is inside an axis-aligned rectangle. */
    public static boolean inside(double px, double py, int x, int y, int width, int height)
    {
        return UiMath.inside(px, py, x, y, width, height);
    }

    /** Whether a point is inside a rounded rectangle. */
    public static boolean hitRounded(double px, double py, int x, int y, int width, int height,
                                     int radius)
    {
        return UiMath.hitRoundedRect(px, py, x, y, width, height, radius);
    }

    /** Whether a point hits a toggle switch. */
    public static boolean hitToggle(double px, double py, int x, int y, int width, int height)
    {
        return UiMath.inside(px, py, x, y, width, height);
    }

    /** The value a slider takes at a mouse position. */
    public static double sliderValue(double mouseX, int x, int width, double min, double max)
    {
        return UiMath.sliderValue(mouseX, x, width, min, max);
    }

    /** The track position for a slider value. */
    public static double sliderX(double value, int x, int width, double min, double max)
    {
        return UiMath.sliderX(value, x, width, min, max);
    }

    /** Whether a point hits a slider handle or its track. */
    public static boolean hitSlider(double px, double py, int x, int y, int width, int height,
                                    double value, double min, double max, int handleSize)
    {
        if (width <= 0 || height <= 0)
        {
            return false;
        }
        double handleX = UiMath.sliderX(value, x, width, min, max);
        int half = Math.max(2, handleSize / 2);
        return px >= handleX - half && px <= handleX + half
                && py >= y - 2 && py < y + height + 2;
    }

    /** Whether a point hits a scrollbar thumb. */
    public static boolean hitScrollbar(double px, double py, int x, int y, int width, int height,
                                       int thumbY, int thumbHeight)
    {
        return UiMath.hitScrollbar(px, py, x, y, width, height, thumbY, thumbHeight);
    }

    /**
     * Draws a filled anti-aliased rounded rectangle.
     *
     * @param graphics the graphics sink
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @param radius   the corner radius, clamped to half the smaller side
     * @param argb     the ARGB colour
     */
    public static void roundRect(GuiGraphicsExtractor graphics, int x, int y, int width,
                                 int height, int radius, int argb)
    {
        if (graphics == null || width <= 0 || height <= 0 || Colors.alpha(argb) == 0)
        {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0)
        {
            graphics.fill(x, y, x + width, y + height, argb);
            return;
        }
        int runStart = -1;
        for (int row = 0; row < height; row++)
        {
            double top = row < r ? edgeInset(r, row) : 0.0D;
            double bottom = height - 1 - row < r ? edgeInset(r, height - 1 - row) : 0.0D;
            double edge = Math.max(top, bottom);
            int inset = (int) Math.ceil(edge - 1.0E-7D);
            if (inset <= 0)
            {
                if (runStart < 0)
                {
                    runStart = row;
                }
                continue;
            }
            if (runStart >= 0)
            {
                graphics.fill(x, y + runStart, x + width, y + row, argb);
                runStart = -1;
            }
            roundSpan(graphics, x, y + row, width, inset, argb, inset - edge);
        }
        if (runStart >= 0)
        {
            graphics.fill(x, y + runStart, x + width, y + height, argb);
        }
    }

    /**
     * Draws a rounded hairline border.
     *
     * @param graphics  the graphics sink
     * @param x         the left edge
     * @param y         the top edge
     * @param width     the width
     * @param height    the height
     * @param radius    the corner radius
     * @param thickness the border thickness in pixels
     * @param argb      the ARGB colour
     */
    public static void roundRectBorder(GuiGraphicsExtractor graphics, int x, int y, int width,
                                       int height, int radius, int thickness, int argb)
    {
        if (graphics == null || width <= 0 || height <= 0 || thickness <= 0
                || Colors.alpha(argb) == 0)
        {
            return;
        }
        int t = Math.max(1, Math.min(thickness, Math.min(width, height) / 2));
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0)
        {
            graphics.fill(x, y, x + width, y + t, argb);
            graphics.fill(x, y + height - t, x + width, y + height, argb);
            graphics.fill(x, y, x + t, y + height, argb);
            graphics.fill(x + width - t, y, x + width, y + height, argb);
            return;
        }
        for (int row = 0; row < height; row++)
        {
            double top = row < r ? edgeInset(r, row) : 0.0D;
            double bottom = height - 1 - row < r ? edgeInset(r, height - 1 - row) : 0.0D;
            double edge = Math.max(top, bottom);
            int inset = (int) Math.ceil(edge - 1.0E-7D);
            if (inset <= 0)
            {
                graphics.fill(x, y + row, x + t, y + row + 1, argb);
                graphics.fill(x + width - t, y + row, x + width, y + row + 1, argb);
                continue;
            }
            graphics.fill(x + inset, y + row, x + inset + t, y + row + 1, argb);
            graphics.fill(x + width - inset - t, y + row, x + width - inset, y + row + 1,
                    argb);
            double coverage = inset - edge;
            if (coverage > 0.02D)
            {
                int alpha = Colors.scaleAlpha(argb, coverage);
                graphics.fill(x + inset - 1, y + row, x + inset, y + row + 1, alpha);
                graphics.fill(x + width - inset, y + row, x + width - inset + 1,
                        y + row + 1, alpha);
            }
        }
        int capInset = (int) Math.ceil(edgeInset(r, 0));
        graphics.fill(x + capInset, y, x + width - capInset, y + t, argb);
        graphics.fill(x + capInset, y + height - t, x + width - capInset, y + height,
                argb);
    }

    /** Draws a pill (fully rounded rectangle). */
    public static void pill(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            int argb)
    {
        roundRect(graphics, x, y, width, height, RADIUS_PILL, argb);
    }

    /** Draws a filled circle. */
    public static void circle(GuiGraphicsExtractor graphics, int centerX, int centerY,
                              int radius, int argb)
    {
        if (radius <= 0)
        {
            return;
        }
        roundRect(graphics, centerX - radius, centerY - radius, radius * 2, radius * 2,
                radius, argb);
    }

    /** Draws a ring (an annulus). */
    public static void ring(GuiGraphicsExtractor graphics, int centerX, int centerY,
                            int outerRadius, int innerRadius, int argb)
    {
        int outer = Math.max(0, outerRadius);
        int inner = Math.max(0, Math.min(innerRadius, outer));
        if (outer == 0 || Colors.alpha(argb) == 0)
        {
            return;
        }
        for (int dy = -outer; dy < outer; dy++)
        {
            double y = dy + 0.5D;
            double outerSpan = Math.sqrt(Math.max(0.0D, (double) outer * outer - y * y));
            int leftOuter = centerX - (int) Math.round(outerSpan);
            int rightOuter = centerX + (int) Math.round(outerSpan);
            if (Math.abs(y) < inner)
            {
                double innerSpan = Math.sqrt(Math.max(0.0D, (double) inner * inner - y * y));
                int leftInner = centerX - (int) Math.round(innerSpan);
                int rightInner = centerX + (int) Math.round(innerSpan);
                if (leftInner > leftOuter)
                {
                    graphics.fill(leftOuter, centerY + dy, leftInner, centerY + dy + 1, argb);
                }
                if (rightOuter > rightInner)
                {
                    graphics.fill(rightInner, centerY + dy, rightOuter, centerY + dy + 1,
                            argb);
                }
            }
            else
            {
                graphics.fill(leftOuter, centerY + dy, rightOuter, centerY + dy + 1, argb);
            }
        }
    }

    /** Draws a 1 px horizontal divider. */
    public static void dividerH(GuiGraphicsExtractor graphics, int x, int y, int width,
                                int argb)
    {
        graphics.fill(x, y, x + width, y + 1, argb);
    }

    /** Draws a 1 px vertical divider. */
    public static void dividerV(GuiGraphicsExtractor graphics, int x, int y, int height,
                                int argb)
    {
        graphics.fill(x, y, x + 1, y + height, argb);
    }

    /** Draws a layered soft shadow behind a rounded rectangle. */
    public static void shadow(GuiGraphicsExtractor graphics, int x, int y, int width,
                              int height, int radius, int argb, int layers)
    {
        int count = Math.max(1, Math.min(layers, 4));
        int baseAlpha = Colors.alpha(argb);
        if (baseAlpha == 0)
        {
            return;
        }
        for (int layer = count; layer >= 1; layer--)
        {
            int spread = layer * 2;
            double weight = (count - layer + 1) / (double) count;
            int alpha = Math.max(1, (int) Math.round(baseAlpha * weight * 0.35D));
            roundRect(graphics, x - spread, y - spread, width + spread * 2,
                    height + spread * 2, radius + spread, Colors.withAlpha(argb, alpha));
        }
    }

    /** Draws a horizontal progress bar. */
    public static void progressBar(GuiGraphicsExtractor graphics, int x, int y, int width,
                                   int height, double progress, int background, int foreground)
    {
        int radius = Math.min(height / 2, RADIUS_PILL);
        roundRect(graphics, x, y, width, height, radius, background);
        int filled = (int) Math.round(width * clamp01(progress));
        if (filled > 0)
        {
            roundRect(graphics, x, y, filled, height, Math.min(radius, filled / 2 + 1),
                    foreground);
        }
    }

    /** Draws a switch track with a knob. */
    public static void switchControl(GuiGraphicsExtractor graphics, int x, int y, int width,
                                     int height, double progress, int offColor, int onColor,
                                     int knobColor, int knobOutline)
    {
        int radius = Math.min(height / 2, RADIUS_PILL);
        double t = clamp01(progress);
        roundRect(graphics, x, y, width, height, radius, Colors.mix(offColor, onColor, t));
        int knob = Math.max(4, height - 6);
        int travel = Math.max(0, width - knob - 6);
        int knobX = x + 3 + (int) Math.round(travel * t);
        int knobY = y + (height - knob) / 2;
        circle(graphics, knobX + knob / 2, knobY + knob / 2, knob / 2, knobOutline);
        circle(graphics, knobX + knob / 2, knobY + knob / 2, Math.max(1, knob / 2 - 1),
                knobColor);
    }

    /** Draws a slider track and handle. */
    public static void sliderControl(GuiGraphicsExtractor graphics, int x, int y, int width,
                                     int height, double progress, int trackColor,
                                     int fillColor, int handleColor, int handleOutline,
                                     int handleSize)
    {
        int trackHeight = Math.max(2, Math.min(4, height));
        int trackY = y + (height - trackHeight) / 2;
        roundRect(graphics, x, trackY, width, trackHeight, trackHeight / 2, trackColor);
        int filled = (int) Math.round(width * clamp01(progress));
        if (filled > 0)
        {
            roundRect(graphics, x, trackY, filled, trackHeight, trackHeight / 2, fillColor);
        }
        int handleX = x + (int) Math.round(width * clamp01(progress));
        int handleY = y + height / 2;
        int size = Math.max(4, handleSize);
        circle(graphics, handleX, handleY, size / 2, handleOutline);
        circle(graphics, handleX, handleY, Math.max(1, size / 2 - 1), handleColor);
    }

    /**
     * Draws a pill badge and returns its width.
     *
     * @param graphics   the graphics sink
     * @param font       the font
     * @param value      the badge text
     * @param x          the left edge
     * @param y          the top edge
     * @param background the pill colour
     * @param foreground the text colour
     * @return the badge width
     */
    public static int badge(GuiGraphicsExtractor graphics, Font font, String value, int x,
                            int y, int background, int foreground)
    {
        if (graphics == null || font == null || value == null)
        {
            return 0;
        }
        int paddingX = 6;
        int width = font.width(value) + paddingX * 2;
        int height = font.lineHeight + 2;
        pill(graphics, x, y, width, height, background);
        graphics.text(font, value, x + paddingX, y + 1, foreground, false);
        return width;
    }

    /** Draws a vertical scrollbar. */
    public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int width,
                                 int height, int thumbY, int thumbHeight, int trackColor,
                                 int thumbColor)
    {
        int radius = Math.min(width / 2, RADIUS_PILL);
        roundRect(graphics, x, y, width, height, radius, trackColor);
        int clampedY = clamp(thumbY, y, y + Math.max(0, height - 1));
        int available = Math.max(1, height - (clampedY - y));
        int clampedHeight = Math.min(Math.max(6, thumbHeight), available);
        roundRect(graphics, x, clampedY, width, clampedHeight, radius, thumbColor);
    }

    /** Draws a vertical two-stop gradient. */
    public static void gradientVertical(GuiGraphicsExtractor graphics, int x, int y, int width,
                                        int height, int top, int bottom)
    {
        graphics.fillGradient(x, y, x + width, y + height, top, bottom);
    }

    /** Draws a left-to-right gradient using a horizontal shim. */
    public static void gradientHorizontal(GuiGraphicsExtractor graphics, int x, int y,
                                          int width, int height, int left, int right)
    {
        int steps = Math.max(1, Math.min(width, 64));
        double stepWidth = width / (double) steps;
        for (int step = 0; step < steps; step++)
        {
            int start = x + (int) Math.floor(step * stepWidth);
            int end = x + (int) Math.ceil((step + 1) * stepWidth);
            int color = Colors.mix(left, right, (step + 0.5D) / steps);
            graphics.fill(start, y, end, y + height, color);
        }
    }

    /** Draws left-aligned text. */
    public static void text(GuiGraphicsExtractor graphics, Font font, String value, int x,
                            int y, int argb, boolean shadow)
    {
        if (graphics == null || font == null || value == null || value.isEmpty())
        {
            return;
        }
        graphics.text(font, value, x, y, argb, shadow);
    }

    /** Draws centred text. */
    public static void textCentered(GuiGraphicsExtractor graphics, Font font, String value,
                                    int centerX, int y, int argb, boolean shadow)
    {
        if (graphics == null || font == null || value == null)
        {
            return;
        }
        graphics.text(font, value, centerX - font.width(value) / 2, y, argb, shadow);
    }

    /** Draws right-aligned text. */
    public static void textRight(GuiGraphicsExtractor graphics, Font font, String value,
                                 int rightX, int y, int argb, boolean shadow)
    {
        if (graphics == null || font == null || value == null)
        {
            return;
        }
        graphics.text(font, value, rightX - font.width(value), y, argb, shadow);
    }

    /** Draws left-aligned text clipped with an ellipsis. */
    public static void textClipped(GuiGraphicsExtractor graphics, Font font, String value,
                                   int x, int y, int maxWidth, int argb, boolean shadow)
    {
        text(graphics, font, ellipsize(font, value, maxWidth), x, y, argb, shadow);
    }

    /** Draws centred text clipped with an ellipsis. */
    public static void textCenteredClipped(GuiGraphicsExtractor graphics, Font font,
                                           String value, int centerX, int y, int maxWidth,
                                           int argb, boolean shadow)
    {
        textCentered(graphics, font, ellipsize(font, value, maxWidth), centerX, y, argb,
                shadow);
    }

    /** Draws right-aligned text clipped with an ellipsis. */
    public static void textRightClipped(GuiGraphicsExtractor graphics, Font font, String value,
                                        int rightX, int y, int maxWidth, int argb,
                                        boolean shadow)
    {
        textRight(graphics, font, ellipsize(font, value, maxWidth), rightX, y, argb, shadow);
    }

    /** Draws several lines of text with a fixed line height. */
    public static void textLines(GuiGraphicsExtractor graphics, Font font, List<String> lines,
                                 int x, int y, int lineHeight, int argb, boolean shadow)
    {
        if (graphics == null || font == null || lines == null)
        {
            return;
        }
        int lineY = y;
        for (int index = 0; index < lines.size(); index++)
        {
            String line = lines.get(index);
            if (line != null && !line.isEmpty())
            {
                graphics.text(font, line, x, lineY, argb, shadow);
            }
            lineY += lineHeight;
        }
    }

    /** Draws several lines of text from an array. */
    public static void textLines(GuiGraphicsExtractor graphics, Font font, String[] lines,
                                 int x, int y, int lineHeight, int argb, boolean shadow)
    {
        if (graphics == null || font == null || lines == null)
        {
            return;
        }
        int lineY = y;
        for (String line : lines)
        {
            if (line != null && !line.isEmpty())
            {
                graphics.text(font, line, x, lineY, argb, shadow);
            }
            lineY += lineHeight;
        }
    }

    /**
     * Draws text on a translucent rounded plate.
     *
     * @return the total plate width
     */
    public static int textPlate(GuiGraphicsExtractor graphics, Font font, String value,
                                int x, int y, int maxWidth, int textColor, int plateColor)
    {
        return textPlate(graphics, font, value, x, y, maxWidth, textColor, plateColor, false);
    }

    /**
     * Draws text on a translucent rounded plate with an optional shadow.
     *
     * @return the total plate width
     */
    public static int textPlate(GuiGraphicsExtractor graphics, Font font, String value,
                                int x, int y, int maxWidth, int textColor, int plateColor,
                                boolean shadow)
    {
        if (graphics == null || font == null || value == null)
        {
            return 0;
        }
        int paddingX = SPACE_XS;
        int paddingY = 3;
        String shown = ellipsize(font, value, Math.max(1, maxWidth - paddingX * 2));
        int plateWidth = font.width(shown) + paddingX * 2;
        int plateHeight = font.lineHeight + paddingY * 2;
        roundRect(graphics, x, y, plateWidth, plateHeight, SPACE_XS, plateColor);
        graphics.text(font, shown, x + paddingX, y + paddingY, textColor, shadow);
        return plateWidth;
    }

    /**
     * Draws a clipped label.
     *
     * @param font  the font
     * @param value the full label
     * @param width the available width
     * @return the rendered string, possibly ending in an ellipsis
     */
    public static String ellipsize(Font font, String value, int width)
    {
        if (font == null || value == null || value.isEmpty() || width <= 0)
        {
            return "";
        }
        if (font.width(value) <= width)
        {
            return value;
        }
        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth > width)
        {
            return "";
        }
        return font.plainSubstrByWidth(value, width - ellipsisWidth) + ELLIPSIS;
    }

    /**
     * Clips a label against a width function without needing a live font.
     *
     * @param value the full label
     * @param width the available width
     * @param widthOf measures a candidate string
     * @return the clipped label
     */
    public static String ellipsize(String value, int width, ToIntFunction<String> widthOf)
    {
        return UiMath.ellipsize(value, width, widthOf);
    }

    /** Draws an icon from the generated atlas. */
    public static void icon(GuiGraphicsExtractor graphics, IconAtlas.Icon icon, int x, int y,
                            int size, int argb)
    {
        IconAtlas.draw(graphics, icon, x, y, size, argb);
    }

    /**
     * Enables a scissor and returns a guard that always disables it.
     *
     * @param graphics the graphics sink
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @return an auto-closable scissor guard
     */
    public static ScissorGuard scissor(GuiGraphicsExtractor graphics, int x, int y, int width,
                                       int height)
    {
        if (graphics == null || width <= 0 || height <= 0)
        {
            return new ScissorGuard(() ->
            {
            }, () ->
            {
            });
        }
        return new ScissorGuard(() -> graphics.enableScissor(x, y, x + width, y + height),
                graphics::disableScissor);
    }

    private static void roundSpan(GuiGraphicsExtractor graphics, int x, int y, int width,
                                  int inset, int argb, double coverage)
    {
        if (width - inset * 2 > 0)
        {
            graphics.fill(x + inset, y, x + width - inset, y + 1, argb);
        }
        if (coverage > 0.02D)
        {
            int alpha = Colors.scaleAlpha(argb, coverage);
            if (inset > 0)
            {
                graphics.fill(x + inset - 1, y, x + inset, y + 1, alpha);
            }
            if (inset < width)
            {
                graphics.fill(x + width - inset, y, x + width - inset + 1, y + 1, alpha);
            }
        }
    }

    private static double edgeInset(int radius, int distance)
    {
        double dy = radius - distance - 0.5D;
        double squared = (double) radius * radius - dy * dy;
        return radius - Math.sqrt(Math.max(0.0D, squared));
    }

    /** Convenience colour mix used by widgets. */
    public static int mix(int from, int to, double t)
    {
        return Colors.mix(from, to, t);
    }

    /** Convenience alpha helper used by widgets. */
    public static int alpha(int argb)
    {
        return Colors.alpha(argb);
    }

    /** Convenience helper used by widgets. */
    public static int withAlpha(int argb, int alpha)
    {
        return Colors.withAlpha(argb, alpha);
    }

    /** Convenience helper used by widgets. */
    public static int contrastText(int argb)
    {
        return Colors.contrastText(argb);
    }

    /** Convenience helper used by widgets. */
    public static int textOn(Theme theme)
    {
        return theme == null ? Colors.WHITE : theme.text();
    }
}
