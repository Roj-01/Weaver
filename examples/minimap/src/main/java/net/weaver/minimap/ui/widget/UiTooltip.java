package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.ui.theme.ThemeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Themed tooltip panel drawn directly by the screen.
 *
 * <p>Tooltips are not widgets: the integrator draws them after the widget pass
 * and supplies the cursor position. Lines wrap to at most half the screen width
 * and the panel is clamped inside the screen.</p>
 */
public final class UiTooltip
{
    private static final int PADDING = 6;
    private static final int MAX_LINES = 12;

    private UiTooltip()
    {
    }

    /** Draws a single-line tooltip. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, String text,
                            int mouseX, int mouseY, int screenWidth, int screenHeight)
    {
        draw(graphics, font, Component.literal(text == null ? "" : text), mouseX, mouseY,
                screenWidth, screenHeight);
    }

    /** Draws a tooltip. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, Component content,
                            int mouseX, int mouseY, int screenWidth, int screenHeight)
    {
        if (content == null)
        {
            return;
        }
        draw(graphics, font, List.of(content), mouseX, mouseY, screenWidth, screenHeight);
    }

    /**
     * Draws a multi-line tooltip.
     *
     * @param graphics     the graphics sink
     * @param font         the font
     * @param lines        the lines
     * @param mouseX       the cursor x
     * @param mouseY       the cursor y
     * @param screenWidth  the screen width
     * @param screenHeight the screen height
     */
    public static void draw(GuiGraphicsExtractor graphics, Font font, List<Component> lines,
                            int mouseX, int mouseY, int screenWidth, int screenHeight)
    {
        if (graphics == null || font == null || lines == null || lines.isEmpty())
        {
            return;
        }
        Theme theme = ThemeManager.get().current();
        int maxTextWidth = Math.max(80, Math.min(280, screenWidth / 2));
        List<FormattedCharSequence> wrapped = new ArrayList<>(lines.size() * 2);
        for (Component line : lines)
        {
            if (line == null)
            {
                continue;
            }
            for (FormattedCharSequence sequence : font.split(line, maxTextWidth))
            {
                wrapped.add(sequence);
                if (wrapped.size() >= MAX_LINES)
                {
                    break;
                }
            }
            if (wrapped.size() >= MAX_LINES)
            {
                break;
            }
        }
        if (wrapped.isEmpty())
        {
            return;
        }
        int textWidth = 0;
        for (FormattedCharSequence sequence : wrapped)
        {
            textWidth = Math.max(textWidth, font.width(sequence));
        }
        int width = textWidth + PADDING * 2;
        int height = wrapped.size() * font.lineHeight + PADDING * 2;
        int x = mouseX + 12;
        int y = mouseY + 12;
        if (x + width > screenWidth - 4)
        {
            x = mouseX - width - 8;
        }
        if (y + height > screenHeight - 4)
        {
            y = mouseY - height - 8;
        }
        x = UiDraw.clamp(x, 4, Math.max(4, screenWidth - width - 4));
        y = UiDraw.clamp(y, 4, Math.max(4, screenHeight - height - 4));
        UiDraw.shadow(graphics, x, y, width, height, UiDraw.RADIUS_SMALL, theme.shadow(), 3);
        UiDraw.roundRect(graphics, x, y, width, height, UiDraw.RADIUS_SMALL,
                theme.surfaceElevated());
        UiDraw.roundRectBorder(graphics, x, y, width, height, UiDraw.RADIUS_SMALL, 1,
                theme.borderStrong());
        int lineY = y + PADDING;
        for (FormattedCharSequence sequence : wrapped)
        {
            graphics.text(font, sequence, x + PADDING, lineY, theme.text(), false);
            lineY += font.lineHeight;
        }
    }
}
